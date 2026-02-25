/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import io.feagi.sdk.engine.FeagiConfig;
import io.feagi.sdk.engine.FeagiEngine;
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
                manager.stop(Duration.ofMillis((long) (timeout * 1000)));
            }

            // Resolve config
            Path configPath = config;
            if (configPath == null) {
                configPath = FeagiConfig.ensureDefaultConfig(paths);
            }

            double serviceStartup = StartCommand.readServiceStartupTimeout(configPath);

            // Build and start engine
            FeagiEngine.Builder builder = FeagiEngine.builder().config(configPath);
            if (genome != null) {
                builder.genome(genome);
            } else if (connectome != null) {
                builder.connectome(connectome);
            }
            FeagiEngine engine = builder.build();

            boolean started = engine.start(false, Duration.ofSeconds(60));
            if (!started) {
                System.err.println("Failed to restart FEAGI");
                return 1;
            }

            // Store PID
            OptionalLong pid = engine.pid();
            if (pid.isPresent()) {
                manager.storePid(pid.getAsLong());
            }

            // Verify process survives
            Thread.sleep((long) (serviceStartup * 1000));
            if (!manager.isRunning()) {
                System.err.println("FEAGI process died immediately after start. "
                        + "Check logs for errors.");
                manager.cleanupPidFile();
                return 1;
            }

            System.out.println("FEAGI restarted successfully (PID: "
                    + (pid.isPresent() ? pid.getAsLong() : "unknown") + ")");
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted during FEAGI restart");
            return 130;
        } catch (Exception e) {
            System.err.println("Failed to restart FEAGI: " + e.getMessage());
            return 1;
        }
    }
}
