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
        System.out.println("For more information: https://github.com/feagi/feagi/tree/main/docs");
        return 0;
    }

    /**
     * Set the usage footer to display FEAGI directory paths.
     *
     * <p>Mirrors the Python SDK's argparse {@code epilog}. Non-fatal on failure —
     * directories simply won't appear in help output.
     */
    static void applyDirectoryFooter(CommandLine cmd) {
        try {
            FeagiPaths paths = FeagiPaths.withDefaults();
            cmd.getCommandSpec().usageMessage().footer(
                    "",
                    "FEAGI Directories:",
                    "  Config:      " + paths.configDir,
                    "  Logs:        " + paths.logsDir,
                    "  Cache:       " + paths.cacheDir,
                    "  Genomes:     " + paths.genomesDir,
                    "  Connectomes: " + paths.connectomesDir
            );
        } catch (Exception ignored) {
            // Non-fatal — directories just won't appear in help
        }
    }

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new FeagiCli());
        applyDirectoryFooter(cmd);
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }
}
