/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import io.feagi.sdk.engine.FeagiPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

class BvProcessManagerTest {

    // ------------------------------------------------------------------
    // PID file I/O
    // ------------------------------------------------------------------

    @Test
    void testGetPidEmptyWhenNoFile(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        BvProcessManager manager = new BvProcessManager(paths);

        OptionalLong pid = manager.getPid();
        assertTrue(pid.isEmpty());
    }

    @Test
    void testGetPidReadsStoredValue(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        paths.ensureCacheDir();
        Files.writeString(paths.cacheDir.resolve("bv.pid"), "54321\n");

        BvProcessManager manager = new BvProcessManager(paths);
        OptionalLong pid = manager.getPid();
        assertTrue(pid.isPresent());
        assertEquals(54321L, pid.getAsLong());
    }

    @Test
    void testGetPidEmptyWhenFileContainsGarbage(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        paths.ensureCacheDir();
        Files.writeString(paths.cacheDir.resolve("bv.pid"), "garbage");

        BvProcessManager manager = new BvProcessManager(paths);
        assertTrue(manager.getPid().isEmpty());
    }

    // ------------------------------------------------------------------
    // isRunning
    // ------------------------------------------------------------------

    @Test
    void testIsRunningFalseWhenNoPidFile(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        BvProcessManager manager = new BvProcessManager(paths);
        assertFalse(manager.isRunning());
    }

    @Test
    void testIsRunningFalseForDeadPid(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        paths.ensureCacheDir();
        Files.writeString(paths.cacheDir.resolve("bv.pid"), "999999999\n");

        BvProcessManager manager = new BvProcessManager(paths);
        assertFalse(manager.isRunning());
    }

    // ------------------------------------------------------------------
    // getStatus
    // ------------------------------------------------------------------

    @Test
    void testGetStatusWhenNotRunning(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        BvProcessManager manager = new BvProcessManager(paths);
        ProcessStatus status = manager.getStatus();

        assertFalse(status.running());
        assertTrue(status.pid().isEmpty());
        assertNotNull(status.pidFile());
    }

    @Test
    void testGetStatusWithStoredDeadPid(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        paths.ensureCacheDir();
        Files.writeString(paths.cacheDir.resolve("bv.pid"), "999999999\n");

        BvProcessManager manager = new BvProcessManager(paths);
        ProcessStatus status = manager.getStatus();
        assertFalse(status.running());
        assertTrue(status.pid().isPresent());
        assertEquals(999999999L, status.pid().getAsLong());
    }

    // ------------------------------------------------------------------
    // stop — when not running
    // ------------------------------------------------------------------

    @Test
    void testStopReturnsFalseWhenNoPid(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        BvProcessManager manager = new BvProcessManager(paths);

        assertFalse(manager.stop(java.time.Duration.ofSeconds(1)));
    }

    @Test
    void testStopReturnsFalseForDeadPid(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        paths.ensureCacheDir();
        Files.writeString(paths.cacheDir.resolve("bv.pid"), "999999999\n");

        BvProcessManager manager = new BvProcessManager(paths);
        assertFalse(manager.stop(java.time.Duration.ofSeconds(1)));

        // PID file should be cleaned up
        assertFalse(Files.exists(paths.cacheDir.resolve("bv.pid")));
    }

    // ------------------------------------------------------------------
    // PID file cleanup (via stop)
    // ------------------------------------------------------------------

    @Test
    void testStopCleansUpPidFileForDeadProcess(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        paths.ensureCacheDir();
        Path pidFile = paths.cacheDir.resolve("bv.pid");
        Files.writeString(pidFile, "123\n");

        BvProcessManager manager = new BvProcessManager(paths);
        manager.stop(java.time.Duration.ofSeconds(1));

        assertFalse(Files.exists(pidFile));
    }

    @Test
    void testStopNoOpWhenNoPidFile(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        BvProcessManager manager = new BvProcessManager(paths);
        assertDoesNotThrow(() -> manager.stop(java.time.Duration.ofSeconds(1)));
    }

    // ------------------------------------------------------------------
    // Null safety
    // ------------------------------------------------------------------

    @Test
    void testConstructorNullPathsThrows() {
        assertThrows(NullPointerException.class, () -> new BvProcessManager(null));
    }
}
