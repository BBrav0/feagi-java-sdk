/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import io.feagi.sdk.engine.FeagiPaths;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Manages the FEAGI process lifecycle via PID files.
 *
 * <p>Mirrors the Python SDK's {@code cli/feagi_process.py}. The PID file is stored
 * at {@code ~/.feagi/cache/feagi.pid}.
 */
final class FeagiProcessManager {

    private final PidFileManager pidManager;

    FeagiProcessManager(FeagiPaths paths) {
        Objects.requireNonNull(paths, "paths");
        paths.ensureCacheDir();
        this.pidManager = new PidFileManager(
                paths.cacheDir.resolve("feagi.pid"), "FEAGI");
    }

    /**
     * Store a FEAGI process PID.
     *
     * @param pid the process ID to store
     * @throws IOException if the PID file cannot be written
     */
    void storePid(long pid) throws IOException {
        pidManager.writePid(pid);
    }

    OptionalLong getPid() {
        return pidManager.getPid();
    }

    boolean isRunning() {
        return pidManager.isRunning();
    }

    boolean stop(Duration timeout) throws IOException {
        return pidManager.stop(timeout);
    }

    ProcessStatus getStatus() {
        return pidManager.getStatus();
    }

    /** Remove the PID file. Package-private: called by StartCommand/RestartCommand on startup failure. */
    void cleanupPidFile() {
        pidManager.cleanup();
    }
}
