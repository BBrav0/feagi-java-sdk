/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * {@code feagi bv} — Brain Visualizer utilities.
 */
@Command(
        name = "bv",
        description = "Brain Visualizer utilities.",
        subcommands = {
                BvStartCommand.class,
                BvStopCommand.class,
                BvStatusCommand.class,
                BvRestartCommand.class,
        }
)
final class BvCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.err.println("Please specify a subcommand: feagi bv {start|stop|status|restart}");
        return 1;
    }
}
