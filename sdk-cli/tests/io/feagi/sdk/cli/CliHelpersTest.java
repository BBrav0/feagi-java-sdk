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
}
