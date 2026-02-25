/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import io.feagi.sdk.engine.FeagiPaths;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * {@code feagi status} — Check FEAGI process status.
 */
@Command(name = "status", description = "Check FEAGI process status.")
final class StatusCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        try {
            FeagiPaths paths = FeagiPaths.withDefaults();
            FeagiProcessManager manager = new FeagiProcessManager(paths);
            ProcessStatus status = manager.getStatus();

            if (status.running()) {
                long pid = status.pid().orElse(-1);
                System.out.println("FEAGI is running (PID: " + pid + ")");
            } else {
                System.out.println("FEAGI is not running");
            }
            System.out.println("PID file: " + status.pidFile());
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to get status: " + e.toString());
            return 1;
        }
    }
}
