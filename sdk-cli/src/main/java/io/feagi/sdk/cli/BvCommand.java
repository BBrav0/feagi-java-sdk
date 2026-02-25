/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import io.feagi.sdk.engine.BvDiscovery;
import io.feagi.sdk.engine.FeagiConfig;
import io.feagi.sdk.engine.FeagiPaths;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * {@code feagi bv} — Brain Visualizer utilities.
 */
@Command(
        name = "bv",
        description = "Brain Visualizer utilities.",
        subcommands = {
                BvStartCommand.class,
                BvStopCommand.class,
                BvStatusCommand.class,
                BvRestartCommand.class,
        }
)
final class BvCommand implements Runnable {

    @Override
    public void run() {
        System.err.println("Please specify a subcommand: feagi bv {start|stop|status|restart}");
    }
}

// ------------------------------------------------------------------
// BV subcommands
// ------------------------------------------------------------------

@Command(name = "start", description = "Launch Brain Visualizer using FEAGI configuration.")
final class BvStartCommand implements Callable<Integer> {

    @Option(names = "--config", description = "Path to FEAGI configuration TOML file.")
    private Path config;

    @Override
    public Integer call() {
        try {
            if (BvDiscovery.isMacOs()) {
                BvHelpers.printMacOsInstructions();
                return 0;
            }

            FeagiPaths paths = FeagiPaths.withDefaults();
            Path configPath = config;
            if (configPath == null) {
                configPath = FeagiConfig.ensureDefaultConfig(paths);
            }

            NetworkSettings net = BvHelpers.readNetworkSettings(configPath);
            String apiUrl = "http://" + net.apiHost() + ":" + net.apiPort();

            // Check if FEAGI is running
            if (!BvHelpers.checkFeagiRunning(apiUrl)) {
                System.err.println("FEAGI is not running at " + apiUrl + ".");
                System.err.println("Start FEAGI first:");
                System.err.println("  feagi start                     (starts with barebones genome)");
                System.err.println("  feagi start --genome <path>     (starts with custom genome)");
                return 1;
            }

            Path binary = BvDiscovery.discoverOrThrow();
            Map<String, String> env = BvHelpers.buildBvEnv(apiUrl, net.wsHost(), net.wsPort());

            BvProcessManager manager = new BvProcessManager(paths);
            long pid = manager.start(binary, binary.getParent(), env);
            System.out.println("Brain Visualizer started (PID: " + pid + ")");
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to start Brain Visualizer: " + e.getMessage());
            return 1;
        }
    }
}

@Command(name = "stop", description = "Stop running Brain Visualizer process.")
final class BvStopCommand implements Callable<Integer> {

    @Option(names = "--timeout", description = "Seconds to wait before force kill (default: 10).",
            defaultValue = "10.0")
    private double timeout;

    @Override
    public Integer call() {
        try {
            FeagiPaths paths = FeagiPaths.withDefaults();
            BvProcessManager manager = new BvProcessManager(paths);
            boolean stopped = manager.stop(Duration.ofMillis((long) (timeout * 1000)));
            if (stopped) {
                System.out.println("Brain Visualizer stopped successfully");
            } else {
                System.out.println("Brain Visualizer is not running");
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to stop Brain Visualizer: " + e.getMessage());
            return 1;
        }
    }
}

@Command(name = "status", description = "Check Brain Visualizer process status.")
final class BvStatusCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        try {
            FeagiPaths paths = FeagiPaths.withDefaults();
            BvProcessManager manager = new BvProcessManager(paths);
            ProcessStatus status = manager.getStatus();

            if (status.running()) {
                long pid = status.pid().orElse(-1);
                System.out.println("Brain Visualizer is running (PID: " + pid + ")");
            } else {
                System.out.println("Brain Visualizer is not running");
            }
            System.out.println("PID file: " + status.pidFile());
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to get status: " + e.getMessage());
            return 1;
        }
    }
}

@Command(name = "restart", description = "Restart Brain Visualizer process.")
final class BvRestartCommand implements Callable<Integer> {

    @Option(names = "--config", description = "Path to FEAGI configuration TOML file.")
    private Path config;

    @Option(names = "--timeout", description = "Seconds to wait for stop before force kill (default: 10).",
            defaultValue = "10.0")
    private double timeout;

    @Override
    public Integer call() {
        try {
            if (BvDiscovery.isMacOs()) {
                BvHelpers.printMacOsInstructions();
                return 0;
            }

            FeagiPaths paths = FeagiPaths.withDefaults();
            Path configPath = config;
            if (configPath == null) {
                configPath = FeagiConfig.ensureDefaultConfig(paths);
            }

            NetworkSettings net = BvHelpers.readNetworkSettings(configPath);
            String apiUrl = "http://" + net.apiHost() + ":" + net.apiPort();

            Path binary = BvDiscovery.discoverOrThrow();
            Map<String, String> env = BvHelpers.buildBvEnv(apiUrl, net.wsHost(), net.wsPort());

            BvProcessManager manager = new BvProcessManager(paths);
            long pid = manager.restart(binary, binary.getParent(), env,
                    Duration.ofMillis((long) (timeout * 1000)));
            System.out.println("Brain Visualizer restarted (PID: " + pid + ")");
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to restart Brain Visualizer: " + e.getMessage());
            return 1;
        }
    }
}

// ------------------------------------------------------------------
// Shared BV helpers (package-private, used by BV subcommands)
// ------------------------------------------------------------------

record NetworkSettings(String apiHost, int apiPort, String wsHost, int wsPort) {}

final class BvHelpers {

    private static final String HEALTH_CHECK_PATH = "/v1/system/health_check";

    private BvHelpers() {}

    static NetworkSettings readNetworkSettings(Path configPath) throws Exception {
        TomlParseResult toml = Toml.parse(configPath);

        String apiHost = toml.getString("api.host");
        Long apiPort = toml.getLong("api.port");
        String wsHost = toml.getString("websocket.host");
        Long wsPort = toml.getLong("websocket.visualization_port");

        if (apiHost == null || wsHost == null) {
            throw new IllegalStateException("Config must define api.host and websocket.host.");
        }
        if (apiPort == null || wsPort == null) {
            throw new IllegalStateException(
                    "Config must define api.port and websocket.visualization_port.");
        }
        if (apiPort < 1 || apiPort > 65535) {
            throw new IllegalStateException("api.port must be 1-65535, got: " + apiPort);
        }
        if (wsPort < 1 || wsPort > 65535) {
            throw new IllegalStateException(
                    "websocket.visualization_port must be 1-65535, got: " + wsPort);
        }

        return new NetworkSettings(apiHost, apiPort.intValue(), wsHost, wsPort.intValue());
    }

    static Map<String, String> buildBvEnv(String apiUrl, String wsHost, int wsPort) {
        // Inherit parent env to propagate PATH, DISPLAY, WAYLAND_DISPLAY, etc.
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("FEAGI_MODE", "remote");
        env.put("FEAGI_API_URL", apiUrl);
        env.put("FEAGI_WS_HOST", wsHost);
        env.put("FEAGI_WS_PORT", String.valueOf(wsPort));
        return env;
    }

    static boolean checkFeagiRunning(String apiUrl) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + HEALTH_CHECK_PATH))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<Void> response = client.send(request,
                    HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
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
