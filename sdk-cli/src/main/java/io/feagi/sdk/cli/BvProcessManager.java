/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import io.feagi.sdk.engine.FeagiPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Manages the Brain Visualizer process lifecycle via PID files.
 *
 * <p>Mirrors the Python SDK's {@code cli/bv_process.py}. The PID file is stored
 * at {@code ~/.feagi/cache/bv.pid}. Process output is redirected to
 * {@code ~/.feagi/logs/bv/run_YYYYMMDD_HHMMSS/}.
 */
public final class BvProcessManager {

    private static final int LOG_RETENTION = 10;
    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    private final FeagiPaths paths;
    private final Path pidFile;

    public BvProcessManager(FeagiPaths paths) {
        Objects.requireNonNull(paths, "paths");
        this.paths = paths;
        paths.ensureCacheDir();
        this.pidFile = paths.cacheDir.resolve("bv.pid");
    }

    /**
     * Start the Brain Visualizer process.
     *
     * @param binary     path to the BV executable
     * @param workingDir working directory for the process
     * @param env        environment variables for the process
     * @return PID of the started process
     * @throws IOException if the process cannot be started or a duplicate is running
     */
    public long start(Path binary, Path workingDir, Map<String, String> env) throws IOException {
        if (isRunning()) {
            throw new IOException("Brain Visualizer is already running (PID: "
                    + getPid().orElse(-1) + ")");
        }

        Path logDir = paths.createLogRunDir("bv", LOG_RETENTION);
        Path stdoutLog = logDir.resolve("bv.log");
        Path stderrLog = logDir.resolve("bv_error.log");

        ProcessBuilder pb = new ProcessBuilder(binary.toString())
                .directory(workingDir.toFile())
                .redirectOutput(stdoutLog.toFile())
                .redirectError(stderrLog.toFile());
        pb.environment().putAll(env);

        Process proc = pb.start();
        long pid = proc.pid();

        // Verify process survives initial startup
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (!proc.isAlive()) {
            throw new IOException("Brain Visualizer process died immediately after start. "
                    + "Check logs at: " + logDir);
        }

        writePid(pid);
        return pid;
    }

    /**
     * Stop the Brain Visualizer process.
     *
     * @param timeout maximum time to wait for graceful shutdown
     * @return {@code true} if stopped, {@code false} if not running
     * @throws IOException if the stop operation fails
     */
    public boolean stop(Duration timeout) throws IOException {
        OptionalLong maybePid = getPid();
        if (maybePid.isEmpty()) {
            return false;
        }
        long pid = maybePid.getAsLong();

        if (!ProcessUtils.isProcessRunning(pid)) {
            cleanupPidFile();
            return false;
        }

        ProcessHandle handle = ProcessHandle.of(pid).orElse(null);
        if (handle == null) {
            cleanupPidFile();
            return false;
        }
        handle.destroy();

        // Wait for graceful shutdown via OS-signalled future
        try {
            handle.onExit().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            cleanupPidFile();
            return true;
        } catch (TimeoutException e) {
            // Graceful shutdown timed out — force kill
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            // Process already exited
            cleanupPidFile();
            return true;
        }

        // Force kill
        try {
            ProcessUtils.forceKillProcess(pid);
            handle.onExit().get(5, TimeUnit.SECONDS);
        } catch (ProcessUtils.ProcessNotFoundException e) {
            // Already gone
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            // Ignore
        }

        cleanupPidFile();

        if (ProcessUtils.isProcessRunning(pid)) {
            throw new IOException("Failed to stop Brain Visualizer (PID: " + pid + ")");
        }
        return true;
    }

    public OptionalLong getPid() {
        if (!Files.exists(pidFile)) {
            return OptionalLong.empty();
        }
        try {
            String content = Files.readString(pidFile).strip();
            return OptionalLong.of(Long.parseLong(content));
        } catch (IOException | NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    public boolean isRunning() {
        OptionalLong pid = getPid();
        return pid.isPresent() && ProcessUtils.isProcessRunning(pid.getAsLong());
    }

    public ProcessStatus getStatus() {
        OptionalLong pid = getPid();
        boolean running = pid.isPresent() && ProcessUtils.isProcessRunning(pid.getAsLong());
        return new ProcessStatus(running, pid, pidFile);
    }

    /**
     * Restart the Brain Visualizer.
     *
     * @param binary      path to the BV executable
     * @param workingDir  working directory
     * @param env         environment variables
     * @param stopTimeout timeout for stopping the existing process
     * @return PID of the new process
     * @throws IOException if restart fails
     */
    public long restart(Path binary, Path workingDir, Map<String, String> env,
                        Duration stopTimeout) throws IOException {
        if (isRunning()) {
            stop(stopTimeout);
        }
        return start(binary, workingDir, env);
    }

    private void writePid(long pid) throws IOException {
        Files.writeString(pidFile, pid + "\n");
    }

    void cleanupPidFile() {
        try {
            Files.deleteIfExists(pidFile);
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
