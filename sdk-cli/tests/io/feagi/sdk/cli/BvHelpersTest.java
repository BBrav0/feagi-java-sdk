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

import static org.junit.jupiter.api.Assertions.*;

class BvHelpersTest {

    // ------------------------------------------------------------------
    // readNetworkSettings — valid config
    // ------------------------------------------------------------------

    @Test
    void testReadNetworkSettingsValidConfig(@TempDir Path tmp) throws Exception {
        Path config = tmp.resolve("feagi.toml");
        Files.writeString(config, """
                [api]
                host = "127.0.0.1"
                port = 8000

                [websocket]
                host = "127.0.0.1"
                visualization_port = 8080
                """);

        NetworkSettings net = BvHelpers.readNetworkSettings(config);
        assertEquals("127.0.0.1", net.apiHost());
        assertEquals(8000, net.apiPort());
        assertEquals("127.0.0.1", net.wsHost());
        assertEquals(8080, net.wsPort());
    }

    // ------------------------------------------------------------------
    // readNetworkSettings — missing keys
    // ------------------------------------------------------------------

    @Test
    void testReadNetworkSettingsMissingApiHost(@TempDir Path tmp) throws Exception {
        Path config = tmp.resolve("feagi.toml");
        Files.writeString(config, """
                [api]
                port = 8000

                [websocket]
                host = "127.0.0.1"
                visualization_port = 8080
                """);

        assertThrows(IllegalStateException.class,
                () -> BvHelpers.readNetworkSettings(config));
    }

    @Test
    void testReadNetworkSettingsMissingPort(@TempDir Path tmp) throws Exception {
        Path config = tmp.resolve("feagi.toml");
        Files.writeString(config, """
                [api]
                host = "127.0.0.1"

                [websocket]
                host = "127.0.0.1"
                visualization_port = 8080
                """);

        assertThrows(IllegalStateException.class,
                () -> BvHelpers.readNetworkSettings(config));
    }

    @Test
    void testReadNetworkSettingsMissingWsPort(@TempDir Path tmp) throws Exception {
        Path config = tmp.resolve("feagi.toml");
        Files.writeString(config, """
                [api]
                host = "127.0.0.1"
                port = 8000

                [websocket]
                host = "127.0.0.1"
                """);

        assertThrows(IllegalStateException.class,
                () -> BvHelpers.readNetworkSettings(config));
    }

    @Test
    void testReadNetworkSettingsBlankHost(@TempDir Path tmp) throws Exception {
        Path config = tmp.resolve("feagi.toml");
        Files.writeString(config, """
                [api]
                host = "  "
                port = 8000

                [websocket]
                host = "127.0.0.1"
                visualization_port = 8080
                """);

        assertThrows(IllegalStateException.class,
                () -> BvHelpers.readNetworkSettings(config));
    }

    // ------------------------------------------------------------------
    // readNetworkSettings — out-of-range ports
    // ------------------------------------------------------------------

    @Test
    void testReadNetworkSettingsPortTooHigh(@TempDir Path tmp) throws Exception {
        Path config = tmp.resolve("feagi.toml");
        Files.writeString(config, """
                [api]
                host = "127.0.0.1"
                port = 99999

                [websocket]
                host = "127.0.0.1"
                visualization_port = 8080
                """);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> BvHelpers.readNetworkSettings(config));
        assertTrue(ex.getMessage().contains("99999"));
    }

    @Test
    void testReadNetworkSettingsPortZero(@TempDir Path tmp) throws Exception {
        Path config = tmp.resolve("feagi.toml");
        Files.writeString(config, """
                [api]
                host = "127.0.0.1"
                port = 0

                [websocket]
                host = "127.0.0.1"
                visualization_port = 8080
                """);

        assertThrows(IllegalStateException.class,
                () -> BvHelpers.readNetworkSettings(config));
    }

    @Test
    void testReadNetworkSettingsWsPortNegative(@TempDir Path tmp) throws Exception {
        Path config = tmp.resolve("feagi.toml");
        Files.writeString(config, """
                [api]
                host = "127.0.0.1"
                port = 8000

                [websocket]
                host = "127.0.0.1"
                visualization_port = -1
                """);

        assertThrows(IllegalStateException.class,
                () -> BvHelpers.readNetworkSettings(config));
    }

    // ------------------------------------------------------------------
    // readNetworkSettings — malformed TOML
    // ------------------------------------------------------------------

    @Test
    void testReadNetworkSettingsThrowsOnMalformedToml(@TempDir Path tmp) throws Exception {
        Path config = tmp.resolve("bad.toml");
        Files.writeString(config, "this is not valid [[ toml");

        assertThrows(IOException.class,
                () -> BvHelpers.readNetworkSettings(config));
    }

    // ------------------------------------------------------------------
    // checkFeagiRunning — connection refused
    // ------------------------------------------------------------------

    @Test
    void testCheckFeagiRunningReturnsFalseForClosedPort() {
        // Port 1 is almost certainly not running an HTTP server
        assertFalse(BvHelpers.checkFeagiRunning("http://127.0.0.1:1"));
    }

    // ------------------------------------------------------------------
    // buildBvEnv
    // ------------------------------------------------------------------

    @Test
    void testBuildBvEnvContainsRequiredKeys() {
        var env = BvHelpers.buildBvEnv("http://127.0.0.1:8000", "127.0.0.1", 8080);
        assertEquals("remote", env.get("FEAGI_MODE"));
        assertEquals("http://127.0.0.1:8000", env.get("FEAGI_API_URL"));
        assertEquals("127.0.0.1", env.get("FEAGI_WS_HOST"));
        assertEquals("8080", env.get("FEAGI_WS_PORT"));
    }

    @Test
    void testBuildBvEnvInheritsParentEnv() {
        var env = BvHelpers.buildBvEnv("http://127.0.0.1:8000", "127.0.0.1", 8080);
        // PATH should be inherited from parent environment
        assertTrue(env.containsKey("PATH") || env.containsKey("Path"),
                "Should inherit PATH from parent environment");
    }
}
