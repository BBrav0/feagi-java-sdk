/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import io.feagi.sdk.engine.BvDiscovery;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared helpers for Brain Visualizer CLI subcommands.
 */
final class BvHelpers {

    private static final String HEALTH_CHECK_PATH = "/v1/system/health_check";

    // HttpClient is not AutoCloseable in Java 17; reuse a single instance.
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().build();

    private BvHelpers() {}

    static NetworkSettings readNetworkSettings(Path configPath) throws IOException {
        TomlParseResult toml = Toml.parse(configPath);
        if (toml.hasErrors()) {
            String errors = toml.errors().stream()
                    .map(Object::toString)
                    .collect(Collectors.joining("; "));
            throw new IOException("Failed to parse config " + configPath + ": " + errors);
        }

        String apiHost = toml.getString("api.host");
        Long apiPort = toml.getLong("api.port");
        String wsHost = toml.getString("websocket.host");
        Long wsPort = toml.getLong("websocket.visualization_port");

        if (apiHost == null || apiHost.isBlank() || wsHost == null || wsHost.isBlank()) {
            throw new IOException(
                    "Config must define non-blank api.host and websocket.host.");
        }
        if (apiPort == null || wsPort == null) {
            throw new IOException(
                    "Config must define api.port and websocket.visualization_port.");
        }
        if (apiPort < 1 || apiPort > 65535) {
            throw new IOException("api.port must be 1-65535, got: " + apiPort);
        }
        if (wsPort < 1 || wsPort > 65535) {
            throw new IOException(
                    "websocket.visualization_port must be 1-65535, got: " + wsPort);
        }

        return new NetworkSettings(apiHost, apiPort.intValue(), wsHost, wsPort.intValue());
    }

    /**
     * Build environment variables for the BV process.
     *
     * <p>Inherits the full parent environment to ensure PATH, DISPLAY,
     * WAYLAND_DISPLAY, and other platform-required variables are available
     * to the BV process. FEAGI-specific variables are then overlaid.
     *
     * <p><strong>Security note:</strong> This forwards the entire parent environment,
     * which may include sensitive variables (e.g. API keys, tokens). An allowlist
     * approach is impractical because the Brain Visualizer is a GUI application
     * requiring many platform-specific variables (PATH, DISPLAY, WAYLAND_DISPLAY,
     * XDG_*, DBUS_*, HOME, etc.) that differ across operating systems. Since the
     * BV process is a local, trusted application launched by the same user, the
     * risk is accepted. Callers needing tighter isolation can copy this map and
     * remove sensitive keys before launching the process.
     */
    static Map<String, String> buildBvEnv(String apiUrl, String wsHost, int wsPort) {
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("FEAGI_MODE", "remote");
        env.put("FEAGI_API_URL", apiUrl);
        env.put("FEAGI_WS_HOST", wsHost);
        env.put("FEAGI_WS_PORT", String.valueOf(wsPort));
        return env;
    }

    static boolean checkFeagiRunning(String apiUrl) {
        // Build the request outside the try-catch so malformed URLs
        // (IllegalArgumentException from URI.create) propagate to the caller
        // instead of being silently swallowed as "not running".
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + HEALTH_CHECK_PATH))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
        try {
            HttpResponse<Void> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.discarding());
            return response.statusCode() / 100 == 2;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    static void printMacOsInstructions() {
        System.out.println("Brain Visualizer is not auto-discovered on macOS.");
        System.out.println();
        System.out.println("Download the Brain Visualizer for macOS from:");
        System.out.println("  " + BvDiscovery.BV_RELEASES_URL);
        System.out.println();
        System.out.println("Use release v2.2.1 or above. Download the asset for your architecture");
        System.out.println("(macos-arm64 or macos-x86_64), extract the archive, and launch the .app file.");
    }
}
