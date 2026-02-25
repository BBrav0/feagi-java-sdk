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
 * {@code feagi stop} — Stop running FEAGI process.
 */
@Command(name = "stop", description = "Stop running FEAGI process.")
final class StopCommand implements Callable<Integer> {

    @Option(names = "--timeout", description = "Seconds to wait before force kill (default: 10).",
            defaultValue = "10.0")
    private double timeout;

    @Override
    public Integer call() {
        try {
            FeagiPaths paths = FeagiPaths.withDefaults();
            long totalMs = CliHelpers.secondsToMillis(timeout);
            long[] split = CliHelpers.splitStopTimeout(totalMs);
            long bvTimeoutMs = split[0];
            long feagiTimeoutMs = split[1];

            // Stop BV first (depends on FEAGI)
            BvProcessManager bvManager = new BvProcessManager(paths);
            if (bvManager.isRunning()) {
                System.out.println("Stopping Brain Visualizer...");
                try {
                    bvManager.stop(Duration.ofMillis(bvTimeoutMs));
                    System.out.println("Brain Visualizer stopped");
                } catch (Exception e) {
                    System.err.println("Warning: Failed to stop Brain Visualizer: " + CliHelpers.errorMessage(e));
                }
            }

            // Stop FEAGI
            FeagiProcessManager manager = new FeagiProcessManager(paths);
            boolean stopped = manager.stop(Duration.ofMillis(feagiTimeoutMs));
            if (stopped) {
                System.out.println("FEAGI stopped successfully");
            } else {
                System.out.println("FEAGI is not running");
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to stop FEAGI: " + CliHelpers.errorMessage(e));
            return 1;
        }
    }
}
