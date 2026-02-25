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

    private CliHelpers() {}

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
                    + e.toString() + ". Using default 3.0s.");
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
            boolean wait, Double waitTimeout) throws Exception {

        double serviceStartup = readServiceStartupTimeout(configPath);

        FeagiEngine.Builder builder = FeagiEngine.builder().config(configPath);
        if (genome != null) {
            builder.genome(genome);
        } else if (connectome != null) {
            builder.connectome(connectome);
        }
        FeagiEngine engine = builder.build();

        boolean started;
        if (wait && waitTimeout != null) {
            started = engine.start(true, Duration.ofMillis((long) (waitTimeout * 1000)));
        } else {
            started = engine.start(false, Duration.ofSeconds(60));
        }

        if (!started) {
            throw new IOException("FEAGI engine failed to start");
        }

        OptionalLong pid = engine.pid();
        if (pid.isPresent()) {
            manager.storePid(pid.getAsLong());
        }

        Thread.sleep((long) (serviceStartup * 1000));
        if (!manager.isRunning()) {
            manager.cleanupPidFile();
            throw new IOException(
                    "FEAGI process died immediately after start. Check logs for errors.");
        }

        return pid;
    }
}
