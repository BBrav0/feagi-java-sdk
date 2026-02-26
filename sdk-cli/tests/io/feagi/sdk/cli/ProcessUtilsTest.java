/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ProcessUtilsTest {

    // ------------------------------------------------------------------
    // isProcessRunning
    // ------------------------------------------------------------------

    @Test
    void testCurrentProcessIsRunning() {
        long myPid = ProcessHandle.current().pid();
        assertTrue(ProcessUtils.isProcessRunning(myPid),
                "Current process should be reported as running");
    }

    @Test
    void testNonExistentProcessNotRunning() {
        // Very high PID that almost certainly doesn't exist
        assertFalse(ProcessUtils.isProcessRunning(999999999L));
    }

    // ------------------------------------------------------------------
    // forceKillProcess — error cases
    // ------------------------------------------------------------------

    @Test
    void testForceKillNonExistentProcessThrows() {
        assertThrows(IOException.class, () ->
                ProcessUtils.forceKillProcess(999999999L));
    }

    @Test
    void testForceKillNonExistentProcessIsProcessNotFoundException() {
        try {
            ProcessUtils.forceKillProcess(999999999L);
            fail("Should have thrown");
        } catch (ProcessUtils.ProcessNotFoundException e) {
            assertTrue(e.getMessage().contains("999999999"));
        } catch (IOException e) {
            // On Windows, taskkill may throw a different IOException
            // depending on exact error code — still acceptable
        }
    }
}
