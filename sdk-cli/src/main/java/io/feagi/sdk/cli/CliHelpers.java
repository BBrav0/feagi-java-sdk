/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import io.feagi.sdk.engine.FeagiEngine;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.OptionalLong;

/**
 * Shared CLI helper utilities.
 */
final class CliHelpers {

    /** Brief pause after process launch to detect immediate crashes. */
    private static final long CRASH_GUARD_DELAY_MS = 1000;

    private CliHelpers() {}

    /** Convert a timeout in seconds (double) to milliseconds (long). */
    static long secondsToMillis(double seconds) {
        return (long) (seconds * 1000);
    }

    /** Return the exception message, falling back to {@code toString()} when null. */
    static String errorMessage(Exception e) {
        String msg = e.getMessage();
        return (msg != null) ? msg : e.toString();
    }

    /**
     * Split a total stop timeout into BV and FEAGI portions.
     *
     * <p>BV is stopped first (it depends on FEAGI), so it gets 30% of the budget
     * with a 2 000 ms floor. FEAGI gets the remainder, clamped to zero.
     *
     * @param totalMs total timeout in milliseconds
     * @return {@code {bvMs, feagiMs}}
     */
    static long[] splitStopTimeout(long totalMs) {
        long bvMs = Math.max(2000, totalMs * 3 / 10);
        long feagiMs = Math.max(0, totalMs - bvMs);
        return new long[]{bvMs, feagiMs};
    }

    /**
     * Read the {@code timeouts.service_startup} value from a FEAGI config file.
     *
     * @param configPath path to the TOML configuration file
     * @return the service startup timeout in seconds, or 3.0 if not configured
     */
    static double readServiceStartupTimeout(Path configPath) {
        try {
            if (Files.exists(configPath)) {
                TomlParseResult toml = Toml.parse(configPath);
                if (toml.hasErrors()) {
                    System.err.println("Warning: Config parse error: "
                            + toml.errors().get(0).toString() + ". Using default 3.0s.");
                    return 3.0;
                }
                Double value = toml.getDouble("timeouts.service_startup");
                if (value != null) {
                    return value;
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not read service_startup timeout from config: "
                    + errorMessage(e) + ". Using default 3.0s.");
        }
        return 3.0;
    }

    /**
     * Build, start, and verify a FEAGI engine process.
     *
     * <p>Shared logic used by both {@code StartCommand} and {@code RestartCommand}.
     * The caller is responsible for duplicate-instance checks and stop-before-restart.
     *
     * @param manager     process manager for PID storage and lifecycle
     * @param configPath  path to the TOML configuration file
     * @param genome      optional genome file path
     * @param connectome  optional connectome file path
     * @param wait        whether to wait for FEAGI readiness
     * @param waitTimeout seconds to wait for readiness (required when {@code wait} is true)
     * @return PID of the started process
     * @throws IOException          if the process fails to start or dies immediately
     * @throws InterruptedException if interrupted during startup verification
     */
    static OptionalLong launchFeagiEngine(FeagiProcessManager manager, Path configPath,
            Path genome, Path connectome,
            boolean wait, Double waitTimeout) throws IOException, InterruptedException {

        FeagiEngine.Builder builder = FeagiEngine.builder().config(configPath);
        if (genome != null) {
            builder.genome(genome);
        } else if (connectome != null) {
            builder.connectome(connectome);
        }
        FeagiEngine engine = builder.build();

        boolean started;
        if (wait && waitTimeout != null) {
            started = engine.start(true, Duration.ofMillis(secondsToMillis(waitTimeout)));
        } else {
            started = engine.start(false, Duration.ZERO);
        }

        if (!started) {
            throw new IOException("FEAGI engine failed to start");
        }

        OptionalLong pid = engine.pid();
        if (pid.isPresent()) {
            manager.storePid(pid.getAsLong());
        }

        // Crash-immediately guard: detect processes that fail on launch.
        // This is NOT a readiness check — the process may crash after this window.
        Thread.sleep(CRASH_GUARD_DELAY_MS);
        if (!manager.isRunning()) {
            manager.cleanupPidFile();
            throw new IOException(
                    "FEAGI process died immediately after start. Check logs for errors.");
        }

        return pid;
    }
}
