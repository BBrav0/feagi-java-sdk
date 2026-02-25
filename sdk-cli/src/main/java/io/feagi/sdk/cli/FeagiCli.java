/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import io.feagi.sdk.engine.FeagiPaths;
import picocli.CommandLine;
import picocli.CommandLine.Command;

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
        version = "FEAGI CLI v0.0.1-beta.0",
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
public final class FeagiCli implements Runnable {

    @Override
    public void run() {
        FeagiPaths paths = FeagiPaths.withDefaults();
        System.out.println("FEAGI CLI v0.0.1-beta.0");
        System.out.println();
        System.out.println("Available commands:");
        System.out.println("  feagi start          - Start FEAGI");
        System.out.println("  feagi stop           - Stop FEAGI");
        System.out.println("  feagi status         - Check FEAGI status");
        System.out.println("  feagi restart        - Restart FEAGI");
        System.out.println("  feagi bv start       - Launch Brain Visualizer");
        System.out.println("  feagi bv stop        - Stop Brain Visualizer");
        System.out.println("  feagi bv status      - Check Brain Visualizer status");
        System.out.println("  feagi bv restart     - Restart Brain Visualizer");
        System.out.println("  feagi init           - Initialize FEAGI environment");
        System.out.println("  feagi config show    - Show configuration");
        System.out.println();
        System.out.println("FEAGI Directories:");
        System.out.println("  Config:      " + paths.configDir);
        System.out.println("  Logs:        " + paths.logsDir);
        System.out.println("  Cache:       " + paths.cacheDir);
        System.out.println("  Genomes:     " + paths.genomesDir);
        System.out.println("  Connectomes: " + paths.connectomesDir);
        System.out.println();
        System.out.println("For more information: https://github.com/feagi/feagi/tree/main/docs");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new FeagiCli()).execute(args);
        System.exit(exitCode);
    }
}
