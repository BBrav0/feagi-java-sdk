/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Shared PID file lifecycle management.
 *
 * <p>Encapsulates the common logic for reading, writing, and managing PID files
 * used by both {@link FeagiProcessManager} and {@link BvProcessManager}.
 */
final class PidFileManager {

    private final Path pidFile;
    private final String processName;

    /**
     * @param pidFile     path to the PID file
     * @param processName human-readable name for error messages (e.g. "FEAGI", "Brain Visualizer")
     */
    PidFileManager(Path pidFile, String processName) {
        this.pidFile = Objects.requireNonNull(pidFile, "pidFile");
        this.processName = Objects.requireNonNull(processName, "processName");
    }

    Path pidFile() {
        return pidFile;
    }

    OptionalLong getPid() {
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

    boolean isRunning() {
        OptionalLong pid = getPid();
        return pid.isPresent() && ProcessUtils.isProcessRunning(pid.getAsLong());
    }

    ProcessStatus getStatus() {
        OptionalLong pid = getPid();
        boolean running = pid.isPresent() && ProcessUtils.isProcessRunning(pid.getAsLong());
        return new ProcessStatus(running, pid, pidFile);
    }

    void writePid(long pid) throws IOException {
        Files.writeString(pidFile, pid + "\n");
    }

    /**
     * Remove the PID file (best-effort).
     *
     * <p>Package-private: called by command classes on startup failure.
     */
    void cleanup() {
        try {
            Files.deleteIfExists(pidFile);
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    /**
     * Stop the managed process gracefully, force-killing on timeout.
     *
     * @param timeout maximum time to wait for graceful shutdown
     * @return {@code true} if the process was stopped, {@code false} if not running
     * @throws IOException if the stop operation fails
     */
    boolean stop(Duration timeout) throws IOException {
        OptionalLong maybePid = getPid();
        if (maybePid.isEmpty()) {
            return false;
        }
        long pid = maybePid.getAsLong();

        ProcessHandle handle = ProcessHandle.of(pid).orElse(null);
        if (handle == null) {
            cleanup();
            return false;
        }
        handle.destroy();

        // Wait for graceful shutdown via OS-signalled future
        try {
            handle.onExit().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            cleanup();
            return true;
        } catch (TimeoutException e) {
            // Graceful shutdown timed out — force kill
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            // Process already exited
            cleanup();
            return true;
        }

        // Force kill
        try {
            ProcessUtils.forceKillProcess(pid);
            handle.onExit().get(5, TimeUnit.SECONDS);
        } catch (ProcessUtils.ProcessNotFoundException e) {
            // Already gone — success
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            // Ignore
        }

        if (ProcessUtils.isProcessRunning(pid)) {
            throw new IOException("Failed to stop " + processName + " (PID: " + pid + ")");
        }
        cleanup();
        return true;
    }
}
