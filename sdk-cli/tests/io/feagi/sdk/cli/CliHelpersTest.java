/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CliHelpersTest {

    // ------------------------------------------------------------------
    // readServiceStartupTimeout — valid config
    // ------------------------------------------------------------------

    @Test
    void testReadsServiceStartupFromConfig(@TempDir Path tmp) throws Exception {
        Path config = tmp.resolve("feagi.toml");
        Files.writeString(config, """
                [timeouts]
                service_startup = 5.0
                """);

        double result = CliHelpers.readServiceStartupTimeout(config);
        assertEquals(5.0, result, 0.001);
    }

    // ------------------------------------------------------------------
    // readServiceStartupTimeout — missing key
    // ------------------------------------------------------------------

    @Test
    void testReturnsDefaultWhenKeyMissing(@TempDir Path tmp) throws Exception {
        Path config = tmp.resolve("feagi.toml");
        Files.writeString(config, """
                [api]
                host = "127.0.0.1"
                """);

        double result = CliHelpers.readServiceStartupTimeout(config);
        assertEquals(3.0, result, 0.001);
    }

    @Test
    void testReturnsDefaultWhenTimeoutsSectionMissing(@TempDir Path tmp) throws Exception {
        Path config = tmp.resolve("feagi.toml");
        Files.writeString(config, "");

        double result = CliHelpers.readServiceStartupTimeout(config);
        assertEquals(3.0, result, 0.001);
    }

    // ------------------------------------------------------------------
    // readServiceStartupTimeout — file does not exist
    // ------------------------------------------------------------------

    @Test
    void testReturnsDefaultWhenFileDoesNotExist(@TempDir Path tmp) {
        Path config = tmp.resolve("nonexistent.toml");

        double result = CliHelpers.readServiceStartupTimeout(config);
        assertEquals(3.0, result, 0.001);
    }

    // ------------------------------------------------------------------
    // readServiceStartupTimeout — malformed TOML
    // ------------------------------------------------------------------

    @Test
    void testReturnsDefaultOnMalformedToml(@TempDir Path tmp) throws Exception {
        Path config = tmp.resolve("bad.toml");
        Files.writeString(config, "this is not valid [[ toml");

        double result = CliHelpers.readServiceStartupTimeout(config);
        assertEquals(3.0, result, 0.001);
    }

    // ------------------------------------------------------------------
    // splitStopTimeout
    // ------------------------------------------------------------------

    @Test
    void testSplitStopTimeoutNormal() {
        // 10s → 30% = 3000 for BV, 70% = 7000 for FEAGI
        long[] split = CliHelpers.splitStopTimeout(10_000);
        assertEquals(3000, split[0], "BV timeout");
        assertEquals(7000, split[1], "FEAGI timeout");
    }

    @Test
    void testSplitStopTimeoutSmall() {
        // 1s → 30% = 300, clamped to 2000 for BV; FEAGI = max(0, 1000 - 2000) = 0
        long[] split = CliHelpers.splitStopTimeout(1000);
        assertEquals(2000, split[0], "BV timeout clamped to floor");
        assertEquals(0, split[1], "FEAGI timeout clamped to zero");
    }

    @Test
    void testSplitStopTimeoutLarge() {
        // 20s → 30% = 6000 for BV, 70% = 14000 for FEAGI
        long[] split = CliHelpers.splitStopTimeout(20_000);
        assertEquals(6000, split[0], "BV timeout");
        assertEquals(14_000, split[1], "FEAGI timeout");
    }

    @Test
    void testSplitStopTimeoutZero() {
        // 0 → BV clamped to 2000, FEAGI = max(0, 0 - 2000) = 0
        long[] split = CliHelpers.splitStopTimeout(0);
        assertEquals(2000, split[0], "BV timeout clamped to floor");
        assertEquals(0, split[1], "FEAGI timeout clamped to zero");
    }
}
