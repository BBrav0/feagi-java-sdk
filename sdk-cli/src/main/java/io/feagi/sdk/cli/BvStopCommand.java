/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import io.feagi.sdk.engine.FeagiPaths;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * {@code feagi bv stop} — Stop running Brain Visualizer process.
 */
@Command(name = "stop", description = "Stop running Brain Visualizer process.")
final class BvStopCommand implements Callable<Integer> {

    @Option(names = "--timeout", description = "Seconds to wait before force kill (default: 10).",
            defaultValue = "10.0")
    private double timeout;

    @Override
    public Integer call() {
        try {
            FeagiPaths paths = FeagiPaths.withDefaults();
            BvProcessManager manager = new BvProcessManager(paths);
            boolean stopped = manager.stop(Duration.ofMillis(CliHelpers.secondsToMillis(timeout)));
            if (stopped) {
                System.out.println("Brain Visualizer stopped successfully");
            } else {
                System.out.println("Brain Visualizer is not running");
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to stop Brain Visualizer: " + CliHelpers.errorMessage(e));
            return 1;
        }
    }
}
