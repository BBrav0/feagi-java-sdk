/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import io.feagi.sdk.engine.FeagiConfig;
import io.feagi.sdk.engine.FeagiPaths;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
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

            Path genome = (brainData != null) ? brainData.genome : null;
            Path connectome = (brainData != null) ? brainData.connectome : null;

            OptionalLong pid = CliHelpers.launchFeagiEngine(
                    manager, configPath, genome, connectome, wait, timeout);
            System.out.println("FEAGI started successfully (PID: "
                    + (pid.isPresent() ? pid.getAsLong() : "unknown") + ")");
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted during FEAGI startup");
            return 130;
        } catch (Exception e) {
            System.err.println("Failed to start FEAGI: " + e.toString());
            return 1;
        }
    }
}
