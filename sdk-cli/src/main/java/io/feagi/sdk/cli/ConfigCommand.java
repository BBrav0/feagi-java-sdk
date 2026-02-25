/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import io.feagi.sdk.engine.FeagiConfig;
import io.feagi.sdk.engine.FeagiPaths;
import picocli.CommandLine.Command;

/**
 * {@code feagi config} — FEAGI configuration utilities.
 */
@Command(
        name = "config",
        description = "FEAGI configuration utilities.",
        subcommands = {ConfigShowCommand.class}
)
final class ConfigCommand implements Runnable {

    @Override
    public void run() {
        System.err.println("Please specify a subcommand: feagi config show");
    }
}

/**
 * {@code feagi config show} — Show the active FEAGI configuration file.
 */
@Command(name = "show", description = "Show the active FEAGI configuration file.")
class ConfigShowCommand implements java.util.concurrent.Callable<Integer> {

    @picocli.CommandLine.Option(names = "--config", description = "Path to FEAGI configuration TOML file.")
    private java.nio.file.Path configPath;

    @Override
    public Integer call() {
        try {
            FeagiPaths paths = FeagiPaths.withDefaults();

            java.nio.file.Path target;
            if (configPath != null) {
                target = configPath;
            } else {
                target = FeagiConfig.ensureDefaultConfig(paths);
            }

            if (!java.nio.file.Files.exists(target)) {
                System.err.println("Config file not found: " + target);
                return 1;
            }

            String contents = java.nio.file.Files.readString(target);
            System.out.println("Config file: " + target);
            System.out.println(contents);
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to read config file: " + e.getMessage());
            return 1;
        }
    }
}
