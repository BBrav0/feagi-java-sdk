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

class FeagiProcessManagerTest {

    // ------------------------------------------------------------------
    // PID file I/O
    // ------------------------------------------------------------------

    @Test
    void testStorePidCreatesFile(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        FeagiProcessManager manager = new FeagiProcessManager(paths);

        manager.storePid(12345L);

        Path pidFile = paths.cacheDir.resolve("feagi.pid");
        assertTrue(Files.exists(pidFile), "PID file should exist");
        assertEquals("12345", Files.readString(pidFile).strip());
    }

    @Test
    void testGetPidReturnsStoredValue(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        FeagiProcessManager manager = new FeagiProcessManager(paths);

        manager.storePid(42L);

        OptionalLong pid = manager.getPid();
        assertTrue(pid.isPresent());
        assertEquals(42L, pid.getAsLong());
    }

    @Test
    void testGetPidEmptyWhenNoFile(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        FeagiProcessManager manager = new FeagiProcessManager(paths);

        OptionalLong pid = manager.getPid();
        assertTrue(pid.isEmpty());
    }

    @Test
    void testGetPidEmptyWhenFileContainsGarbage(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        paths.ensureCacheDir();
        Files.writeString(paths.cacheDir.resolve("feagi.pid"), "not-a-number");

        FeagiProcessManager manager = new FeagiProcessManager(paths);
        OptionalLong pid = manager.getPid();
        assertTrue(pid.isEmpty(), "Should return empty for non-numeric PID");
    }

    @Test
    void testStorePidOverwritesPrevious(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        FeagiProcessManager manager = new FeagiProcessManager(paths);

        manager.storePid(111L);
        manager.storePid(222L);

        assertEquals(222L, manager.getPid().getAsLong());
    }

    // ------------------------------------------------------------------
    // isRunning — dead PID
    // ------------------------------------------------------------------

    @Test
    void testIsRunningFalseWhenNoPidFile(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        FeagiProcessManager manager = new FeagiProcessManager(paths);
        assertFalse(manager.isRunning());
    }

    @Test
    void testIsRunningFalseForDeadPid(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        FeagiProcessManager manager = new FeagiProcessManager(paths);
        // PID that (almost certainly) doesn't exist
        manager.storePid(999999999L);
        assertFalse(manager.isRunning());
    }

    // ------------------------------------------------------------------
    // getStatus
    // ------------------------------------------------------------------

    @Test
    void testGetStatusWhenNotRunning(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        FeagiProcessManager manager = new FeagiProcessManager(paths);
        ProcessStatus status = manager.getStatus();

        assertFalse(status.running());
        assertTrue(status.pid().isEmpty());
        assertNotNull(status.pidFile());
    }

    @Test
    void testGetStatusWithStoredDeadPid(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        FeagiProcessManager manager = new FeagiProcessManager(paths);
        manager.storePid(999999999L);

        ProcessStatus status = manager.getStatus();
        assertFalse(status.running());
        assertTrue(status.pid().isPresent());
        assertEquals(999999999L, status.pid().getAsLong());
    }

    // ------------------------------------------------------------------
    // cleanupPidFile
    // ------------------------------------------------------------------

    @Test
    void testCleanupPidFileRemovesFile(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        FeagiProcessManager manager = new FeagiProcessManager(paths);
        manager.storePid(123L);

        Path pidFile = paths.cacheDir.resolve("feagi.pid");
        assertTrue(Files.exists(pidFile));

        manager.cleanupPidFile();
        assertFalse(Files.exists(pidFile));
    }

    @Test
    void testCleanupPidFileWhenNoFile(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        FeagiProcessManager manager = new FeagiProcessManager(paths);
        assertDoesNotThrow(manager::cleanupPidFile, "Should not throw when no PID file");
    }

    // ------------------------------------------------------------------
    // stop — when not running
    // ------------------------------------------------------------------

    @Test
    void testStopReturnsFalseWhenNoPid(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        FeagiProcessManager manager = new FeagiProcessManager(paths);

        assertFalse(manager.stop(java.time.Duration.ofSeconds(1)));
    }

    @Test
    void testStopReturnsFalseForDeadPid(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        FeagiProcessManager manager = new FeagiProcessManager(paths);
        manager.storePid(999999999L);

        assertFalse(manager.stop(java.time.Duration.ofSeconds(1)));
        // PID file should be cleaned up
        assertFalse(Files.exists(paths.cacheDir.resolve("feagi.pid")));
    }

    // ------------------------------------------------------------------
    // Null safety
    // ------------------------------------------------------------------

    @Test
    void testConstructorNullPathsThrows() {
        assertThrows(NullPointerException.class, () -> new FeagiProcessManager(null));
    }
}
