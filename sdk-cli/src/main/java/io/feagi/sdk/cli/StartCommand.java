/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import io.feagi.sdk.engine.FeagiConfig;
import io.feagi.sdk.engine.FeagiEngine;
import io.feagi.sdk.engine.FeagiPaths;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.OptionalLong;
import java.util.concurrent.Callable;

/**
 * {@code feagi start} — Start FEAGI using a config and genome/connectome.
 */
@Command(name = "start", description = "Start FEAGI using a config and genome/connectome.")
final class StartCommand implements Callable<Integer> {

    @Option(names = "--config", description = "Path to FEAGI configuration TOML file.")
    private Path config;

    @ArgGroup(exclusive = true)
    private BrainData brainData;

    static class BrainData {
        @Option(names = "--genome", description = "Path to genome JSON file.")
        Path genome;

        @Option(names = "--connectome", description = "Path to connectome file.")
        Path connectome;
    }

    @Option(names = "--wait", description = "Wait for FEAGI to report ready before returning.")
    private boolean wait;

    @Option(names = "--timeout", description = "Seconds to wait for readiness (required with --wait).")
    private Double timeout;

    @Override
    public Integer call() {
        try {
            if (wait && timeout == null) {
                System.err.println("Missing --timeout when using --wait");
                return 1;
            }

            FeagiPaths paths = FeagiPaths.withDefaults();
            FeagiProcessManager manager = new FeagiProcessManager(paths);

            // Check for duplicate instance
            if (manager.isRunning()) {
                OptionalLong pid = manager.getPid();
                System.err.println("FEAGI is already running (PID: "
                        + (pid.isPresent() ? pid.getAsLong() : "unknown") + ")");
                System.err.println("Stop it first with: feagi stop");
                return 1;
            }

            // Resolve config
            Path configPath = config;
            if (configPath == null) {
                configPath = FeagiConfig.ensureDefaultConfig(paths);
            }

            // Read service startup timeout from config
            double serviceStartup = readServiceStartupTimeout(configPath);

            // Build engine
            FeagiEngine.Builder builder = FeagiEngine.builder().config(configPath);
            if (brainData != null && brainData.genome != null) {
                builder.genome(brainData.genome);
            } else if (brainData != null && brainData.connectome != null) {
                builder.connectome(brainData.connectome);
            }
            FeagiEngine engine = builder.build();

            // Start
            boolean started;
            if (wait) {
                started = engine.start(true, Duration.ofMillis((long) (timeout * 1000)));
            } else {
                started = engine.start(false, Duration.ofSeconds(60));
            }

            if (!started) {
                System.err.println("Failed to start FEAGI");
                return 1;
            }

            // Store PID
            OptionalLong pid = engine.pid();
            if (pid.isPresent()) {
                manager.storePid(pid.getAsLong());
            }

            // Verify process is still running after service startup delay
            Thread.sleep((long) (serviceStartup * 1000));
            if (!manager.isRunning()) {
                System.err.println("FEAGI process died immediately after start. "
                        + "Check logs for errors.");
                manager.cleanupPidFile();
                return 1;
            }

            System.out.println("FEAGI started successfully (PID: "
                    + (pid.isPresent() ? pid.getAsLong() : "unknown") + ")");
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted during FEAGI startup");
            return 130;
        } catch (Exception e) {
            System.err.println("Failed to start FEAGI: " + e.getMessage());
            return 1;
        }
    }

    static double readServiceStartupTimeout(Path configPath) {
        try {
            if (Files.exists(configPath)) {
                TomlParseResult toml = Toml.parse(configPath);
                Double value = toml.getDouble("timeouts.service_startup");
                if (value != null) {
                    return value;
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not read service_startup timeout from config: "
                    + e.getMessage() + ". Using default 3.0s.");
        }
        return 3.0;
    }
}
