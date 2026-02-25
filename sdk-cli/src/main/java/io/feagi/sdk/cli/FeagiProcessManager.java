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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Manages the FEAGI process lifecycle via PID files.
 *
 * <p>Mirrors the Python SDK's {@code cli/feagi_process.py}. The PID file is stored
 * at {@code ~/.feagi/cache/feagi.pid}.
 */
public final class FeagiProcessManager {

    private final Path pidFile;

    public FeagiProcessManager(FeagiPaths paths) {
        Objects.requireNonNull(paths, "paths");
        paths.ensureCacheDir();
        this.pidFile = paths.cacheDir.resolve("feagi.pid");
    }

    /**
     * Store a FEAGI process PID.
     *
     * @param pid the process ID to store
     * @throws IOException if the PID file cannot be written
     */
    public void storePid(long pid) throws IOException {
        Files.writeString(pidFile, pid + "\n");
    }

    /**
     * Get the stored PID from the PID file.
     *
     * @return the PID, or empty if not found or invalid
     */
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

    /**
     * Check if the stored FEAGI process is running.
     *
     * @return {@code true} if a stored PID exists and the process is alive
     */
    public boolean isRunning() {
        OptionalLong pid = getPid();
        return pid.isPresent() && ProcessUtils.isProcessRunning(pid.getAsLong());
    }

    /**
     * Stop the FEAGI process gracefully.
     *
     * <p>Sends a destroy signal first, waits for the timeout, then force-kills
     * if necessary.
     *
     * @param timeout maximum time to wait for graceful shutdown
     * @return {@code true} if the process was stopped, {@code false} if not running
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

        // Try graceful shutdown
        ProcessHandle handle = ProcessHandle.of(pid).orElse(null);
        if (handle == null) {
            cleanupPidFile();
            return false;
        }
        handle.destroy();

        // Wait for graceful shutdown
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadlineNanos) {
            if (!ProcessUtils.isProcessRunning(pid)) {
                cleanupPidFile();
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Force kill
        try {
            ProcessUtils.forceKillProcess(pid);
            Thread.sleep(500);
        } catch (ProcessUtils.ProcessNotFoundException e) {
            // Already gone — success
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        cleanupPidFile();

        if (ProcessUtils.isProcessRunning(pid)) {
            throw new IOException("Failed to stop FEAGI (PID: " + pid + ")");
        }
        return true;
    }

    /**
     * Get detailed process status.
     *
     * @return map with "running" (Boolean), "pid" (Long or null), "pid_file" (String)
     */
    public Map<String, Object> getStatus() {
        OptionalLong pid = getPid();
        boolean running = pid.isPresent() && ProcessUtils.isProcessRunning(pid.getAsLong());

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("running", running);
        status.put("pid", pid.isPresent() ? pid.getAsLong() : null);
        status.put("pid_file", pidFile.toString());
        return status;
    }

    /** Remove the PID file. */
    void cleanupPidFile() {
        try {
            Files.deleteIfExists(pidFile);
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
