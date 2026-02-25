/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.engine;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Manages the lifecycle of a FEAGI engine instance.
 *
 * <p>Mirrors the Python SDK's {@code feagi.engine.FeagiEngine}. Typical usage:
 * <pre>{@code
 * try (FeagiEngine engine = FeagiEngine.builder()
 *         .config(Path.of("feagi_configuration.toml"))
 *         .genome(Path.of("my_genome.json"))
 *         .build()) {
 *     engine.start();
 *     // ... work with FEAGI ...
 * }
 * }</pre>
 *
 * <p>The builder auto-discovers the FEAGI executable via {@link FeagiDiscovery} if no
 * explicit path is provided. Genome and connectome are mutually exclusive — the last
 * one set on the builder wins (matching the Python SDK convention).
 */
public final class FeagiEngine implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(FeagiEngine.class.getName());

    private static final Duration DEFAULT_START_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration DEFAULT_STOP_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration HEALTH_CHECK_INTERVAL = Duration.ofMillis(500);
    private static final Duration HEALTH_CHECK_HTTP_TIMEOUT = Duration.ofSeconds(2);
    private static final int HEALTH_LOG_EVERY_N_ATTEMPTS = 10;
    private static final Duration FORCE_KILL_TIMEOUT = Duration.ofSeconds(5);

    private final Path feagiPath;
    private final Path workingDirectory;
    private final Path configPath;
    private final Path genomePath;
    private final Path connectomePath;
    private final String host;
    private final int restPort;
    private final boolean quiet;

    private Process process;
    private volatile boolean stopRequested;
    private volatile int lastExitCode = -1;
    private final HttpClient httpClient;

    private FeagiEngine(Builder builder) {
        this.feagiPath = builder.feagiPath;
        this.workingDirectory = builder.workingDirectory;
        this.configPath = builder.configPath;
        this.genomePath = builder.genomePath;
        this.connectomePath = builder.connectomePath;
        this.host = builder.host;
        this.restPort = builder.restPort;
        this.quiet = builder.quiet;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HEALTH_CHECK_HTTP_TIMEOUT)
                .build();
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Create a new builder for configuring a {@code FeagiEngine} instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Start the FEAGI engine, waiting for it to become ready.
     *
     * <p>Equivalent to {@code start(true, Duration.ofSeconds(60))}.
     *
     * @return {@code true} if the engine started and became ready
     * @throws IOException if the process cannot be spawned
     */
    public boolean start() throws IOException {
        return start(true, DEFAULT_START_TIMEOUT);
    }

    /**
     * Start the FEAGI engine.
     *
     * <p>The synchronized block is intentionally narrow — it covers only the
     * process-spawning section. The health-check polling runs outside the lock
     * so that {@link #stop()} can be called from another thread without blocking
     * for the full timeout.
     *
     * @param waitForReady if {@code true}, poll the REST health check until ready
     * @param timeout      maximum time to wait for the engine to become ready
     * @return {@code true} if the engine started (and became ready, if requested)
     * @throws IOException if the process cannot be spawned
     */
    public boolean start(boolean waitForReady, Duration timeout) throws IOException {
        Objects.requireNonNull(timeout, "timeout");

        Process proc;
        synchronized (this) {
            if (process != null && process.isAlive()) {
                LOG.warning("FEAGI is already running");
                return true;
            }

            stopRequested = false;
            lastExitCode = -1;

            LOG.info(() -> "Starting FEAGI engine: " + feagiPath);

            List<String> command = buildCommand();
            LOG.info(() -> "Command: " + String.join(" ", command));
            LOG.info(() -> "Working dir: " + workingDirectory);

            ProcessBuilder.Redirect outputRedirect = quiet
                    ? ProcessBuilder.Redirect.DISCARD
                    : ProcessBuilder.Redirect.INHERIT;

            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectOutput(outputRedirect)
                    .redirectError(outputRedirect);

            // Set RUST_LOG if not already present.
            pb.environment().putIfAbsent("RUST_LOG", "info");

            process = pb.start();
            proc = process;
            LOG.info(() -> "FEAGI started (PID: " + proc.pid() + ")");
        }

        if (waitForReady) {
            LOG.info(() -> "Waiting for FEAGI to be ready (timeout: " + timeout.toSeconds() + "s)...");
            LOG.info(() -> "Checking REST API at: http://" + host + ":" + restPort
                    + "/v1/system/health_check");

            if (awaitReady(proc, timeout)) {
                LOG.info("FEAGI is ready");
                return true;
            } else {
                LOG.severe("FEAGI failed to become ready");
                stop();
                return false;
            }
        }

        return true;
    }

    /**
     * Stop the FEAGI engine gracefully.
     *
     * <p>Equivalent to {@code stop(Duration.ofSeconds(10))}.
     *
     * @return {@code true} if the engine stopped (or was not running)
     */
    public boolean stop() {
        return stop(DEFAULT_STOP_TIMEOUT);
    }

    /**
     * Stop the FEAGI engine gracefully.
     *
     * <p>Sends a termination signal and waits up to {@code timeout} for the process
     * to exit. If the process does not exit within the timeout, it is forcefully killed.
     *
     * @param timeout maximum time to wait for graceful shutdown
     * @return {@code true} if the engine stopped
     */
    public synchronized boolean stop(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        stopRequested = true;
        if (process == null) {
            LOG.fine("FEAGI is not running");
            return true;
        }

        LOG.info("Stopping FEAGI engine...");

        try {
            // Graceful shutdown
            process.destroy();

            if (process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                lastExitCode = process.exitValue();
                LOG.info("FEAGI stopped gracefully");
                process = null;
                return true;
            }

            // Force kill
            LOG.warning("FEAGI did not stop gracefully, forcing...");
            process.destroyForcibly();

            if (process.waitFor(FORCE_KILL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                lastExitCode = process.exitValue();
                LOG.info("FEAGI stopped (forced)");
                process = null;
                return true;
            }

            LOG.severe("FEAGI could not be stopped");
            return false;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warning("Interrupted while stopping FEAGI");
            return false;
        }
    }

    /**
     * Return {@code true} if the FEAGI process is currently running.
     */
    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    /** Return the REST API host. */
    public String host() {
        return host;
    }

    /** Return the REST API port. */
    public int restPort() {
        return restPort;
    }

    /**
     * Return the FEAGI executable path.
     */
    public Path feagiPath() {
        return feagiPath;
    }

    /**
     * Return the PID of the running FEAGI process, or empty if no process
     * is currently alive.
     */
    public synchronized OptionalLong pid() {
        if (process == null || !process.isAlive()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(process.pid());
        } catch (UnsupportedOperationException e) {
            return OptionalLong.empty();
        }
    }

    /**
     * Return the exit code of the last FEAGI process, or empty if no process
     * has exited yet.
     */
    public synchronized OptionalInt lastExitCode() {
        return lastExitCode == -1 ? OptionalInt.empty() : OptionalInt.of(lastExitCode);
    }

    /** Package-private accessor for testing. */
    synchronized Process processForTesting() {
        return process;
    }

    @Override
    public void close() {
        if (!stop()) {
            LOG.warning("stop() returned false during close() — process may still be running");
        }
        // HttpClient implements AutoCloseable starting in Java 21.
        // Forward-compatible: on Java 17 the condition is false (no-op).
        if (httpClient instanceof AutoCloseable ac) {
            try {
                ac.close();
            } catch (Exception e) {
                LOG.fine(() -> "Error closing HttpClient: " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * Build the subprocess command line.
     *
     * <p>Package-private for testing.
     */
    List<String> buildCommand() {
        List<String> cmd = new ArrayList<>();
        cmd.add(feagiPath.toString());

        if (configPath != null) {
            cmd.add("--config");
            cmd.add(configPath.toString());
        }
        if (genomePath != null) {
            cmd.add("--genome");
            cmd.add(genomePath.toString());
        } else if (connectomePath != null) {
            cmd.add("--connectome");
            cmd.add(connectomePath.toString());
        }

        return List.copyOf(cmd);
    }

    /**
     * Poll the FEAGI REST health check endpoint until ready or timeout.
     *
     * <p>Runs outside the synchronized block so {@link #stop()} is not blocked.
     * Uses a local {@code proc} reference (safe — no field access) and checks
     * the {@code volatile stopRequested} flag each iteration.
     *
     * @param proc the process to monitor (captured before releasing the lock)
     */
    private boolean awaitReady(Process proc, Duration timeout) {
        long startNanos = System.nanoTime();
        long timeoutNanos = timeout.toNanos();
        int attempts = 0;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + host + ":" + restPort + "/v1/system/health_check"))
                .timeout(HEALTH_CHECK_HTTP_TIMEOUT)
                .GET()
                .build();

        while (System.nanoTime() - startNanos < timeoutNanos) {
            if (stopRequested) {
                LOG.info("Stop requested during health check");
                return false;
            }

            attempts++;

            if (attempts % HEALTH_LOG_EVERY_N_ATTEMPTS == 0) {
                long elapsedSec = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startNanos);
                long totalSec = timeout.toSeconds();
                LOG.info(() -> "  Still waiting... (" + elapsedSec + "s / " + totalSec + "s)");
            }

            // Check if the process died.
            if (!proc.isAlive()) {
                lastExitCode = proc.exitValue();
                LOG.severe(() -> "FEAGI process terminated unexpectedly (exit code: "
                        + lastExitCode + ")");
                return false;
            }

            // Try the health check.
            try {
                HttpResponse<Void> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200) {
                    return true;
                }
            } catch (IOException e) {
                LOG.fine(() -> "Health check attempt failed: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }

            try {
                Thread.sleep(HEALTH_CHECK_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    /**
     * Builder for {@link FeagiEngine}.
     *
     * <p>Genome and connectome are mutually exclusive — the last one set wins
     * (matching the Python SDK convention). A warning is logged when one
     * replaces the other.
     */
    public static final class Builder {

        private Path feagiPath;
        private Path workingDirectory;
        private Path configPath;
        private Path genomePath;
        private Path connectomePath;
        private String host = "localhost";
        private int restPort = 8000;
        private boolean quiet;

        private Builder() {}

        /**
         * Set an explicit path to the FEAGI executable.
         *
         * <p>If not set, the builder uses {@link FeagiDiscovery#discover()} at
         * {@link #build()} time.
         */
        public Builder feagiPath(Path feagiPath) {
            this.feagiPath = Objects.requireNonNull(feagiPath, "feagiPath");
            return this;
        }

        /** Set the working directory for the FEAGI process. Defaults to CWD. */
        public Builder workingDirectory(Path workingDirectory) {
            this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory");
            return this;
        }

        /** Set the path to the FEAGI TOML configuration file. */
        public Builder config(Path configPath) {
            this.configPath = Objects.requireNonNull(configPath, "configPath");
            return this;
        }

        /**
         * Set the genome file path (initial neural structure).
         *
         * <p>Mutually exclusive with {@link #connectome(Path)} — setting genome
         * clears any previously set connectome.
         */
        public Builder genome(Path genomePath) {
            Objects.requireNonNull(genomePath, "genomePath");
            if (this.connectomePath != null) {
                LOG.warning("Genome set — clearing previously set connectome");
                this.connectomePath = null;
            }
            this.genomePath = genomePath;
            return this;
        }

        /**
         * Set the connectome file path (trained neural state).
         *
         * <p>Mutually exclusive with {@link #genome(Path)} — setting connectome
         * clears any previously set genome.
         */
        public Builder connectome(Path connectomePath) {
            Objects.requireNonNull(connectomePath, "connectomePath");
            if (this.genomePath != null) {
                LOG.warning("Connectome set — clearing previously set genome");
                this.genomePath = null;
            }
            this.connectomePath = connectomePath;
            return this;
        }

        /** Set the REST API host (default: {@code "localhost"}). */
        public Builder host(String host) {
            this.host = Objects.requireNonNull(host, "host");
            return this;
        }

        /** Set the REST API port (default: {@code 8000}). */
        public Builder restPort(int restPort) {
            if (restPort < 1 || restPort > 65535) {
                throw new IllegalArgumentException("restPort must be 1–65535: " + restPort);
            }
            this.restPort = restPort;
            return this;
        }

        /**
         * Suppress FEAGI process output (default: {@code false}).
         *
         * <p>When {@code true}, stdout and stderr are discarded. When {@code false}
         * (the default), output is inherited by the parent process.
         */
        public Builder quiet(boolean quiet) {
            this.quiet = quiet;
            return this;
        }

        /**
         * Build the {@code FeagiEngine} instance.
         *
         * <p>Validates all paths and resolves the FEAGI executable if not explicitly set.
         *
         * @return a configured {@code FeagiEngine}
         * @throws IllegalStateException if the FEAGI executable cannot be found
         * @throws IllegalArgumentException if any provided path does not exist
         */
        public FeagiEngine build() {
            // Resolve FEAGI executable.
            if (feagiPath != null) {
                Optional<Path> validated = FeagiDiscovery.validate(feagiPath);
                if (validated.isEmpty()) {
                    throw new IllegalArgumentException(
                            "FEAGI executable not usable: " + feagiPath);
                }
                feagiPath = validated.get();
            } else {
                feagiPath = FeagiDiscovery.discover().orElseThrow(() ->
                        new IllegalStateException(
                                "FEAGI executable not found. "
                                + "Specify feagiPath or ensure FEAGI is on the PATH."));
            }

            // Default working directory.
            if (workingDirectory == null) {
                workingDirectory = Path.of(System.getProperty("user.dir"));
            }

            // Validate working directory exists.
            if (!Files.isDirectory(workingDirectory)) {
                throw new IllegalArgumentException(
                        "Working directory not found: " + workingDirectory);
            }

            // Validate host produces a legal URI.
            try {
                URI.create("http://" + host + ":" + restPort + "/");
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid host: " + host, e);
            }

            // Validate paths exist.
            if (configPath != null && !Files.exists(configPath)) {
                throw new IllegalArgumentException(
                        "Config file not found: " + configPath);
            }
            if (genomePath != null && !Files.exists(genomePath)) {
                throw new IllegalArgumentException(
                        "Genome file not found: " + genomePath);
            }
            if (connectomePath != null && !Files.exists(connectomePath)) {
                throw new IllegalArgumentException(
                        "Connectome file not found: " + connectomePath);
            }

            return new FeagiEngine(this);
        }
    }
}
