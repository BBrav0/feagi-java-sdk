/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import io.feagi.sdk.engine.BvDiscovery;
import io.feagi.sdk.engine.FeagiConfig;
import io.feagi.sdk.engine.FeagiPaths;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.time.Duration;
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
final class BvCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.err.println("Please specify a subcommand: feagi bv {start|stop|status|restart}");
        return 1;
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
            System.err.println("Failed to start Brain Visualizer: " + e.toString());
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
            System.err.println("Failed to stop Brain Visualizer: " + e.toString());
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
            System.err.println("Failed to get status: " + e.toString());
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

            // Verify FEAGI is running before restarting BV
            if (!BvHelpers.checkFeagiRunning(apiUrl)) {
                System.err.println("FEAGI is not running at " + apiUrl + ".");
                System.err.println("Start FEAGI first: feagi start");
                return 1;
            }

            Path binary = BvDiscovery.discoverOrThrow();
            Map<String, String> env = BvHelpers.buildBvEnv(apiUrl, net.wsHost(), net.wsPort());

            BvProcessManager manager = new BvProcessManager(paths);
            long pid = manager.restart(binary, binary.getParent(), env,
                    Duration.ofMillis((long) (timeout * 1000)));
            System.out.println("Brain Visualizer restarted (PID: " + pid + ")");
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to restart Brain Visualizer: " + e.toString());
            return 1;
        }
    }
}
