/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import io.feagi.sdk.engine.FeagiPaths;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * {@code feagi bv status} — Check Brain Visualizer process status.
 */
@Command(name = "status", description = "Check Brain Visualizer process status.")
final class BvStatusCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        try {
            FeagiPaths paths = FeagiPaths.withDefaults();
            BvProcessManager manager = new BvProcessManager(paths);
            ProcessStatus status = manager.getStatus();

            if (status.running()) {
                long pid = status.pid().orElse(-1);
                System.out.println("Brain Visualizer is running (PID: " + pid + ")");
            } else {
                System.out.println("Brain Visualizer is not running");
            }
            System.out.println("PID file: " + status.pidFile());
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to get status: " + CliHelpers.errorMessage(e));
            return 1;
        }
    }
}
