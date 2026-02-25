/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Cross-platform process utilities for FEAGI CLI.
 *
 * <p>Mirrors the Python SDK's {@code cli/process_utils.py}.
 */
final class ProcessUtils {

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    private ProcessUtils() {}

    /**
     * Force kill a process by PID.
     *
     * <p>On Windows, uses {@code taskkill /F /PID}. On Unix, uses
     * {@link ProcessHandle#destroyForcibly()}.
     *
     * @param pid the process ID to kill
     * @throws ProcessNotFoundException if the process does not exist
     * @throws IOException              if the kill operation fails
     */
    static void forceKillProcess(long pid) throws IOException {
        if (IS_WINDOWS) {
            forceKillWindows(pid);
        } else {
            forceKillUnix(pid);
        }
    }

    /**
     * Check if a process with the given PID is currently running.
     *
     * @param pid the process ID to check
     * @return {@code true} if the process is alive
     */
    static boolean isProcessRunning(long pid) {
        return ProcessHandle.of(pid)
                .map(ProcessHandle::isAlive)
                .orElse(false);
    }

    private static void forceKillWindows(long pid) throws IOException {
        Process proc = null;
        try {
            proc = new ProcessBuilder("taskkill", "/F", "/PID", String.valueOf(pid))
                    .redirectErrorStream(true)
                    .start();

            // Drain output concurrently to prevent pipe buffer deadlock
            Process drainTarget = proc;
            CompletableFuture<byte[]> outputFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return drainTarget.getInputStream().readAllBytes();
                } catch (IOException e) {
                    return new byte[0];
                }
            });

            boolean finished = proc.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                throw new IOException("taskkill timed out for PID " + pid);
            }
            int exitCode = proc.exitValue();
            if (exitCode == 0) {
                return; // outputFuture is abandoned — safe since process already exited
            }

            byte[] outputBytes;
            try {
                outputBytes = outputFuture.get(2, TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException e) {
                outputBytes = new byte[0];
            }
            String output = new String(outputBytes, StandardCharsets.UTF_8);
            String lower = output.toLowerCase();
            if (exitCode == 128 || exitCode == 1 || lower.contains("not found")) {
                throw new ProcessNotFoundException(pid);
            }
            throw new IOException("taskkill failed (exit " + exitCode + "): " + output.trim());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while killing PID " + pid, e);
        } finally {
            if (proc != null && proc.isAlive()) {
                proc.destroyForcibly();
            }
        }
    }

    private static void forceKillUnix(long pid) throws IOException {
        ProcessHandle handle = ProcessHandle.of(pid)
                .orElseThrow(() -> new ProcessNotFoundException(pid));
        handle.destroyForcibly();
        if (handle.isAlive()) {
            throw new IOException("Failed to force kill PID " + pid);
        }
    }

    /** Thrown when a process no longer exists. */
    static class ProcessNotFoundException extends IOException {
        ProcessNotFoundException(long pid) {
            super("Process not found: " + pid);
        }
    }
}
