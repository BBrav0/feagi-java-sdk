/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.nio.file.Files;
import java.nio.file.Path;

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
                Double value = toml.getDouble("timeouts.service_startup");
                if (value != null) {
                    return value;
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not read service_startup timeout from config: "
                    + e.getMessage() + ". Using default 3.0s.");
        }
        return 3.0;
    }
}
