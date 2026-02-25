/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import io.feagi.sdk.engine.FeagiPaths;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * FEAGI CLI entry point.
 *
 * <p>Mirrors the Python SDK's {@code feagi/cli/main.py}. Provides subcommands for
 * FEAGI and Brain Visualizer lifecycle management.
 */
@Command(
        name = "feagi",
        description = "FEAGI CLI utilities.",
        mixinStandardHelpOptions = true,
        version = FeagiCli.VERSION,
        subcommands = {
                StartCommand.class,
                StopCommand.class,
                StatusCommand.class,
                RestartCommand.class,
                InitCommand.class,
                ConfigCommand.class,
                BvCommand.class,
        }
)
public final class FeagiCli implements Callable<Integer> {

    static final String VERSION = "FEAGI CLI v0.0.1-beta.0";

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(System.out);
        System.out.println();
        try {
            FeagiPaths paths = FeagiPaths.withDefaults();
            System.out.println("FEAGI Directories:");
            System.out.println("  Config:      " + paths.configDir);
            System.out.println("  Logs:        " + paths.logsDir);
            System.out.println("  Cache:       " + paths.cacheDir);
            System.out.println("  Genomes:     " + paths.genomesDir);
            System.out.println("  Connectomes: " + paths.connectomesDir);
            System.out.println();
        } catch (Exception e) {
            System.err.println("Warning: Could not resolve FEAGI directories: "
                    + CliHelpers.errorMessage(e));
            return 1;
        }
        System.out.println("For more information: https://github.com/feagi/feagi/tree/main/docs");
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new FeagiCli()).execute(args);
        System.exit(exitCode);
    }
}
