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
import java.util.Map;
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
        Map<String, Object> status = manager.getStatus();

        assertFalse((Boolean) status.get("running"));
        assertNull(status.get("pid"));
        assertNotNull(status.get("pid_file"));
    }

    @Test
    void testGetStatusWithStoredDeadPid(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        paths.ensureCacheDir();
        Files.writeString(paths.cacheDir.resolve("bv.pid"), "999999999\n");

        BvProcessManager manager = new BvProcessManager(paths);
        Map<String, Object> status = manager.getStatus();
        assertFalse((Boolean) status.get("running"));
        assertEquals(999999999L, status.get("pid"));
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
    // cleanupPidFile
    // ------------------------------------------------------------------

    @Test
    void testCleanupPidFileRemovesFile(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        paths.ensureCacheDir();
        Files.writeString(paths.cacheDir.resolve("bv.pid"), "123\n");

        BvProcessManager manager = new BvProcessManager(paths);
        manager.cleanupPidFile();

        assertFalse(Files.exists(paths.cacheDir.resolve("bv.pid")));
    }

    @Test
    void testCleanupPidFileNoOpWhenMissing(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        BvProcessManager manager = new BvProcessManager(paths);
        assertDoesNotThrow(manager::cleanupPidFile);
    }

    // ------------------------------------------------------------------
    // Null safety
    // ------------------------------------------------------------------

    @Test
    void testConstructorNullPathsThrows() {
        assertThrows(NullPointerException.class, () -> new BvProcessManager(null));
    }
}
