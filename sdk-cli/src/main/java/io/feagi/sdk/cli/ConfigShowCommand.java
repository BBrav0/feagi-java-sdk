/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import io.feagi.sdk.engine.FeagiPaths;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

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
            Path target = (configPath != null) ? configPath : paths.getDefaultConfig();

            String contents = Files.readString(target);
            System.out.println("Config file: " + target);
            System.out.println(contents);
            return 0;
        } catch (NoSuchFileException e) {
            System.err.println("Config file not found: " + e.getFile()
                    + ". Run: feagi init");
            return 1;
        } catch (Exception e) {
            System.err.println("Failed to read config file: " + CliHelpers.errorMessage(e));
            return 1;
        }
    }
}
