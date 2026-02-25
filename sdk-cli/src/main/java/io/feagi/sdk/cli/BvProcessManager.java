/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import io.feagi.sdk.engine.FeagiPaths;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Manages the Brain Visualizer process lifecycle via PID files.
 *
 * <p>Mirrors the Python SDK's {@code cli/bv_process.py}. The PID file is stored
 * at {@code ~/.feagi/cache/bv.pid}. Process output is redirected to
 * {@code ~/.feagi/logs/bv/run_YYYYMMDD_HHMMSS/}.
 */
public final class BvProcessManager {

    private static final int LOG_RETENTION = 10;
    private static final long STARTUP_VERIFY_DELAY_MS = 500;

    private final FeagiPaths paths;
    private final PidFileManager pidManager;

    public BvProcessManager(FeagiPaths paths) {
        Objects.requireNonNull(paths, "paths");
        this.paths = paths;
        paths.ensureCacheDir();
        this.pidManager = new PidFileManager(
                paths.cacheDir.resolve("bv.pid"), "Brain Visualizer");
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
        pidManager.writePid(pid);

        // Crash-immediately guard: detect processes that fail on launch.
        // This is NOT a readiness check — the process may crash after this window.
        try {
            Thread.sleep(STARTUP_VERIFY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (!proc.isAlive()) {
            pidManager.cleanup();
            throw new IOException("Brain Visualizer process died immediately after start. "
                    + "Check logs at: " + logDir);
        }

        return pid;
    }

    public boolean stop(Duration timeout) throws IOException {
        return pidManager.stop(timeout);
    }

    public OptionalLong getPid() {
        return pidManager.getPid();
    }

    public boolean isRunning() {
        return pidManager.isRunning();
    }

    public ProcessStatus getStatus() {
        return pidManager.getStatus();
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
}
