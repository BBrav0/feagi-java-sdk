/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.engine;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * FEAGI configuration file management.
 *
 * <p>Mirrors the Python SDK's {@code feagi.config}. Handles default configuration
 * generation and environment initialization. This class only <em>generates</em>
 * TOML config files (writing a template string) — it does not parse TOML.
 * TOML reading is handled by the CLI module where the tomlj library lives.
 *
 * <p>Example usage:
 * <pre>{@code
 * FeagiPaths paths = FeagiPaths.withDefaults();
 * Path config = FeagiConfig.ensureDefaultConfig(paths);
 * }</pre>
 */
public final class FeagiConfig {

    private static final Logger LOG = Logger.getLogger(FeagiConfig.class.getName());

    /**
     * Default TOML configuration template.
     *
     * <p>Matches the Python SDK's {@code DEFAULT_CONFIG_CONTENT} in {@code feagi/config.py}.
     */
    public static final String DEFAULT_CONFIG_CONTENT = """
            # FEAGI Configuration File
            # This file is auto-generated. Modify as needed for your deployment.
            #
            # For more information, see: https://github.com/feagi/feagi/tree/main/docs

            [api]
            host = "127.0.0.1"  # Localhost only - secure by default, no firewall prompts
            port = 8000

            [websocket]
            host = "127.0.0.1"  # Localhost only - secure by default, no firewall prompts
            enabled = true
            visualization_port = 8080
            sensory_port = 5558
            motor_port = 5564

            [burst_engine]
            # Burst duration in seconds (0.01 = 10ms bursts)
            burst_duration = 0.01

            # Maximum bursts to execute (0 = unlimited)
            max_bursts = 0

            # Enable GPU acceleration if available
            gpu_enabled = false

            [performance]
            # Number of worker threads for parallel processing
            worker_threads = 4

            # Enable performance profiling
            profiling_enabled = false

            [logging]
            # Log level: debug, info, warning, error
            level = "info"

            # Enable file logging
            file_logging = true

            [timeouts]
            # Timeout settings in seconds
            service_startup = 3.0

            [connectome]
            # Maximum neuron space allocation
            neuron_space = 1000000

            # Maximum synapse space allocation
            synapse_space = 10000000
            """;

    private FeagiConfig() {}

    /** Best-effort: restrict file to owner-only (rw-------) on POSIX systems. */
    private static void setOwnerOnly(Path path) {
        try {
            Files.setPosixFilePermissions(path,
                    PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX (Windows) or permissions not supported — skip
        }
    }

    /**
     * Generate a default FEAGI configuration file.
     *
     * @param paths      FEAGI path manager
     * @param outputPath custom output path, or {@code null} for default location
     * @param force      if {@code true}, overwrite an existing file
     * @return path to the created configuration file
     * @throws FileAlreadyExistsException if the file exists and {@code force} is {@code false}
     * @throws IOException                if the file cannot be written
     */
    public static Path generateDefaultConfig(FeagiPaths paths, Path outputPath, boolean force)
            throws IOException {
        Objects.requireNonNull(paths, "paths");

        Path target;
        if (outputPath != null) {
            target = outputPath;
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } else {
            paths.ensureConfigDir();
            target = paths.getDefaultConfig();
        }

        if (Files.exists(target) && !force) {
            throw new FileAlreadyExistsException(target.toString(),
                    null, "Config file already exists. Use force=true to overwrite.");
        }

        Files.writeString(target, DEFAULT_CONFIG_CONTENT);
        setOwnerOnly(target);
        LOG.info("Generated default config: " + target);
        return target;
    }

    /**
     * Ensure the default configuration file exists, creating it if necessary.
     *
     * @param paths FEAGI path manager
     * @return path to the configuration file (existing or newly created)
     * @throws IOException if the file cannot be created
     */
    public static Path ensureDefaultConfig(FeagiPaths paths) throws IOException {
        Objects.requireNonNull(paths, "paths");

        Path configPath = paths.getDefaultConfig();
        if (Files.exists(configPath)) {
            return configPath;
        }
        paths.ensureConfigDir();
        Files.writeString(configPath, DEFAULT_CONFIG_CONTENT);
        setOwnerOnly(configPath);
        LOG.info("Created default config: " + configPath);
        return configPath;
    }

    /**
     * Initialize the FEAGI environment with all directories and default config.
     *
     * <p>Mirrors the Python SDK's {@code init_feagi_environment()}.
     *
     * @param paths FEAGI path manager
     * @return map of key directory/file paths
     * @throws IOException if directories or config cannot be created
     */
    public static Map<String, Path> initEnvironment(FeagiPaths paths) throws IOException {
        Objects.requireNonNull(paths, "paths");

        paths.ensureAll();
        Path configFile = ensureDefaultConfig(paths);

        LOG.info("FEAGI environment initialized");

        Map<String, Path> env = new LinkedHashMap<>();
        env.put("config_dir", paths.configDir);
        env.put("config_file", configFile);
        env.put("logs_dir", paths.logsDir);
        env.put("cache_dir", paths.cacheDir);
        env.put("genomes_dir", paths.genomesDir);
        env.put("connectomes_dir", paths.connectomesDir);
        return env;
    }
}
