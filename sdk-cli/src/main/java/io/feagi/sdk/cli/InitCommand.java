/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import io.feagi.sdk.engine.FeagiConfig;
import io.feagi.sdk.engine.FeagiPaths;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * {@code feagi init} — Initialize FEAGI environment and generate default configuration.
 */
@Command(name = "init", description = "Initialize FEAGI environment and generate default configuration.")
final class InitCommand implements Callable<Integer> {

    @Option(names = "--config-only", description = "Only generate config file, don't create all directories.")
    private boolean configOnly;

    @Option(names = "--force", description = "Overwrite existing configuration file.")
    private boolean force;

    @Option(names = "--output", description = "Custom output path for config file.")
    private Path output;

    @Override
    public Integer call() {
        try {
            FeagiPaths paths = FeagiPaths.withDefaults();

            if (configOnly) {
                Path configPath = FeagiConfig.generateDefaultConfig(paths, output, force);
                System.out.println("Generated configuration file: " + configPath);
            } else {
                Map<String, Path> env = FeagiConfig.initEnvironment(paths);
                System.out.println("FEAGI environment initialized successfully!");
                System.out.println();
                System.out.println("Configuration: " + env.get("config_file"));
                System.out.println("Genomes:       " + env.get("genomes_dir"));
                System.out.println("Connectomes:   " + env.get("connectomes_dir"));
                System.out.println("Logs:          " + env.get("logs_dir"));
                System.out.println("Cache:         " + env.get("cache_dir"));
                System.out.println();
                System.out.println("Next steps:");
                System.out.println("  1. Edit configuration if needed:");
                System.out.println("     " + env.get("config_file"));
                System.out.println("  2. Start FEAGI:");
                System.out.println("     feagi start");
            }
            return 0;
        } catch (FileAlreadyExistsException e) {
            System.err.println("Error: " + e.toString());
            System.err.println("Use --force to overwrite existing files.");
            return 1;
        } catch (Exception e) {
            System.err.println("Failed to initialize FEAGI environment: " + e.toString());
            return 1;
        }
    }
}
