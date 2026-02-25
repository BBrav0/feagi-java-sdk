/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import picocli.CommandLine.Command;

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
