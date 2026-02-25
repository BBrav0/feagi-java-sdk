/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import io.feagi.sdk.engine.FeagiConfig;
import io.feagi.sdk.engine.FeagiPaths;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * {@code feagi config} — FEAGI configuration utilities.
 */
@Command(
        name = "config",
        description = "FEAGI configuration utilities.",
        subcommands = {ConfigShowCommand.class}
)
final class ConfigCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.err.println("Please specify a subcommand: feagi config show");
        return 1;
    }
}

/**
 * {@code feagi config show} — Show the active FEAGI configuration file.
 */
@Command(name = "show", description = "Show the active FEAGI configuration file.")
final class ConfigShowCommand implements Callable<Integer> {

    @Option(names = "--config", description = "Path to FEAGI configuration TOML file.")
    private Path configPath;

    @Override
    public Integer call() {
        try {
            FeagiPaths paths = FeagiPaths.withDefaults();
            Path target = (configPath != null) ? configPath : FeagiConfig.ensureDefaultConfig(paths);

            String contents = Files.readString(target);
            System.out.println("Config file: " + target);
            System.out.println(contents);
            return 0;
        } catch (NoSuchFileException e) {
            System.err.println("Config file not found: " + e.getFile());
            return 1;
        } catch (Exception e) {
            System.err.println("Failed to read config file: " + e.toString());
            return 1;
        }
    }
}
