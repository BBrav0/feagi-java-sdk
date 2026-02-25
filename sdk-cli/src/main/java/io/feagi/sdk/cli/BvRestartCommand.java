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
 * {@code feagi bv restart} — Restart Brain Visualizer process.
 */
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
                    Duration.ofMillis(CliHelpers.secondsToMillis(timeout)));
            System.out.println("Brain Visualizer restarted (PID: " + pid + ")");
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to restart Brain Visualizer: " + e.toString());
            return 1;
        }
    }
}
