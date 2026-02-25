/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

class PidFileManagerTest {

    // ------------------------------------------------------------------
    // writePid + getPid round-trip
    // ------------------------------------------------------------------

    @Test
    void testWriteAndReadPid(@TempDir Path tmp) throws IOException {
        PidFileManager mgr = new PidFileManager(tmp.resolve("test.pid"), "Test");
        mgr.writePid(12345);

        OptionalLong pid = mgr.getPid();
        assertTrue(pid.isPresent());
        assertEquals(12345, pid.getAsLong());
    }

    // ------------------------------------------------------------------
    // Atomic write — temp file cleaned up
    // ------------------------------------------------------------------

    @Test
    void testWritePidCleansUpTempFile(@TempDir Path tmp) throws IOException {
        Path pidFile = tmp.resolve("test.pid");
        PidFileManager mgr = new PidFileManager(pidFile, "Test");
        mgr.writePid(99999);

        Path tempFile = pidFile.resolveSibling(pidFile.getFileName() + ".tmp");
        assertFalse(Files.exists(tempFile),
                "Temp file should be removed after atomic move");
        assertTrue(Files.exists(pidFile));
    }

    // ------------------------------------------------------------------
    // cleanup removes PID file
    // ------------------------------------------------------------------

    @Test
    void testCleanupRemovesPidFile(@TempDir Path tmp) throws IOException {
        Path pidFile = tmp.resolve("test.pid");
        PidFileManager mgr = new PidFileManager(pidFile, "Test");
        mgr.writePid(12345);
        assertTrue(Files.exists(pidFile));

        mgr.cleanup();
        assertFalse(Files.exists(pidFile));
    }

    @Test
    void testCleanupNoOpWhenFileDoesNotExist(@TempDir Path tmp) {
        PidFileManager mgr = new PidFileManager(tmp.resolve("missing.pid"), "Test");
        assertDoesNotThrow(mgr::cleanup);
    }

    // ------------------------------------------------------------------
    // getPid — missing file
    // ------------------------------------------------------------------

    @Test
    void testGetPidReturnsEmptyForMissingFile(@TempDir Path tmp) {
        PidFileManager mgr = new PidFileManager(tmp.resolve("missing.pid"), "Test");
        assertTrue(mgr.getPid().isEmpty());
    }

    // ------------------------------------------------------------------
    // getPid — corrupt content
    // ------------------------------------------------------------------

    @Test
    void testGetPidReturnsEmptyForCorruptContent(@TempDir Path tmp) throws IOException {
        Path pidFile = tmp.resolve("test.pid");
        Files.writeString(pidFile, "not-a-number\n");

        PidFileManager mgr = new PidFileManager(pidFile, "Test");
        assertTrue(mgr.getPid().isEmpty());
    }

    @Test
    void testGetPidReturnsEmptyForEmptyFile(@TempDir Path tmp) throws IOException {
        Path pidFile = tmp.resolve("test.pid");
        Files.writeString(pidFile, "");

        PidFileManager mgr = new PidFileManager(pidFile, "Test");
        assertTrue(mgr.getPid().isEmpty());
    }

    // ------------------------------------------------------------------
    // isRunning — stale PID
    // ------------------------------------------------------------------

    @Test
    void testIsRunningReturnsFalseForStalePid(@TempDir Path tmp) throws IOException {
        Path pidFile = tmp.resolve("test.pid");
        // Use a PID that almost certainly doesn't exist
        Files.writeString(pidFile, "999999999\n");

        PidFileManager mgr = new PidFileManager(pidFile, "Test");
        assertFalse(mgr.isRunning());
    }

    @Test
    void testIsRunningReturnsFalseWhenNoPidFile(@TempDir Path tmp) {
        PidFileManager mgr = new PidFileManager(tmp.resolve("missing.pid"), "Test");
        assertFalse(mgr.isRunning());
    }

    // ------------------------------------------------------------------
    // getStatus
    // ------------------------------------------------------------------

    @Test
    void testGetStatusReturnsNotRunningForMissingFile(@TempDir Path tmp) {
        PidFileManager mgr = new PidFileManager(tmp.resolve("missing.pid"), "Test");
        ProcessStatus status = mgr.getStatus();
        assertFalse(status.running());
        assertTrue(status.pid().isEmpty());
    }

    // ------------------------------------------------------------------
    // writePid overwrites previous value
    // ------------------------------------------------------------------

    @Test
    void testWritePidOverwritesPrevious(@TempDir Path tmp) throws IOException {
        PidFileManager mgr = new PidFileManager(tmp.resolve("test.pid"), "Test");
        mgr.writePid(111);
        mgr.writePid(222);

        assertEquals(222, mgr.getPid().getAsLong());
    }
}
