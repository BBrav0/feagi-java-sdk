/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import io.feagi.sdk.engine.FeagiConfig;
import io.feagi.sdk.engine.FeagiPaths;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.time.Duration;
import java.util.OptionalLong;
import java.util.concurrent.Callable;

/**
 * {@code feagi restart} — Restart FEAGI process.
 */
@Command(name = "restart", description = "Restart FEAGI process.")
final class RestartCommand implements Callable<Integer> {

    @Option(names = "--config", description = "Path to FEAGI configuration TOML file.")
    private Path config;

    @Option(names = "--genome", description = "Path to genome JSON file.")
    private Path genome;

    @Option(names = "--connectome", description = "Path to connectome file.")
    private Path connectome;

    @Option(names = "--timeout", description = "Seconds to wait for stop before force kill (default: 10).",
            defaultValue = "10.0")
    private double timeout;

    @Override
    public Integer call() {
        try {
            FeagiPaths paths = FeagiPaths.withDefaults();
            FeagiProcessManager manager = new FeagiProcessManager(paths);

            // Stop existing instance
            if (manager.isRunning()) {
                System.out.println("Stopping FEAGI...");
                manager.stop(Duration.ofMillis(CliHelpers.secondsToMillis(timeout)));
            }

            // Resolve config
            Path configPath = config;
            if (configPath == null) {
                configPath = FeagiConfig.ensureDefaultConfig(paths);
            }

            OptionalLong pid = CliHelpers.launchFeagiEngine(
                    manager, configPath, genome, connectome, false, null);
            System.out.println("FEAGI restarted successfully (PID: "
                    + (pid.isPresent() ? pid.getAsLong() : "unknown") + ")");
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted during FEAGI restart");
            return 130;
        } catch (Exception e) {
            System.err.println("Failed to restart FEAGI: " + CliHelpers.errorMessage(e));
            return 1;
        }
    }
}
