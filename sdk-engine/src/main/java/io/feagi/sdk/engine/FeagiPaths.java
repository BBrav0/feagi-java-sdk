/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.engine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Cross-platform path manager for FEAGI directories.
 *
 * <p>Mirrors the Python SDK's {@code feagi.paths.FeagiPaths}. Provides consistent
 * access to configuration, data, logs, and cache directories across Linux, macOS,
 * and Windows.
 *
 * <p>Directory structure:
 * <pre>
 * Hidden (all platforms):
 *   ~/.feagi/config/
 *   ~/.feagi/logs/
 *   ~/.feagi/cache/
 *
 * User content (macOS/Windows):
 *   ~/Documents/FEAGI/Genomes/
 *   ~/Documents/FEAGI/Connectomes/
 *
 * User content (Linux):
 *   ~/FEAGI/genomes/
 *   ~/FEAGI/connectomes/
 * </pre>
 *
 * <p>Example usage:
 * <pre>{@code
 * FeagiPaths paths = FeagiPaths.withDefaults();
 * paths.ensureAll();
 * Path config = paths.getDefaultConfig();
 * }</pre>
 */
public final class FeagiPaths {

    private static final DateTimeFormatter RUN_DIR_FORMAT =
            DateTimeFormatter.ofPattern("'run_'yyyyMMdd_HHmmss");

    /** Hidden system directory: {@code ~/.feagi/config/}. */
    public final Path configDir;

    /** Hidden system directory: {@code ~/.feagi/logs/}. */
    public final Path logsDir;

    /** Hidden system directory: {@code ~/.feagi/cache/}. */
    public final Path cacheDir;

    /** User content directory for genome files. Platform-specific casing. */
    public final Path genomesDir;

    /** User content directory for connectome files. Platform-specific casing. */
    public final Path connectomesDir;

    /**
     * Create a path manager rooted at the given home directory.
     *
     * <p>Platform detection uses {@code System.getProperty("os.name")}. For tests,
     * use the overload that accepts an explicit OS name.
     *
     * @param homeDir user home directory (e.g. {@code Path.of(System.getProperty("user.home"))})
     */
    public FeagiPaths(Path homeDir) {
        this(homeDir, System.getProperty("os.name"));
    }

    /**
     * Create a path manager with explicit home directory and OS name.
     *
     * <p>Primarily useful for testing with mock OS names and temp directories.
     *
     * @param homeDir user home directory
     * @param osName  operating system name (e.g. "Windows 11", "Linux", "Mac OS X")
     */
    public FeagiPaths(Path homeDir, String osName) {
        Objects.requireNonNull(homeDir, "homeDir");
        Objects.requireNonNull(osName, "osName");

        Path feagiHome = homeDir.resolve(".feagi");
        this.configDir = feagiHome.resolve("config");
        this.logsDir = feagiHome.resolve("logs");
        this.cacheDir = feagiHome.resolve("cache");

        String os = osName.toLowerCase();
        if (os.contains("win") || os.contains("mac") || os.contains("darwin")) {
            Path userContent = homeDir.resolve("Documents").resolve("FEAGI");
            this.genomesDir = userContent.resolve("Genomes");
            this.connectomesDir = userContent.resolve("Connectomes");
        } else {
            Path userContent = homeDir.resolve("FEAGI");
            this.genomesDir = userContent.resolve("genomes");
            this.connectomesDir = userContent.resolve("connectomes");
        }
    }

    /**
     * Create a path manager using system defaults.
     *
     * @return a {@code FeagiPaths} rooted at {@code user.home}
     */
    public static FeagiPaths withDefaults() {
        return new FeagiPaths(Path.of(System.getProperty("user.home")));
    }

    // ------------------------------------------------------------------
    // Path accessors
    // ------------------------------------------------------------------

    /**
     * Get the default configuration file path.
     *
     * @return path to {@code feagi_configuration.toml} in the config directory
     */
    public Path getDefaultConfig() {
        return configDir.resolve("feagi_configuration.toml");
    }

    /**
     * Get full path to a genome file in the genomes directory.
     *
     * @param filename genome filename (e.g. "my_brain.json")
     * @return full path to genome file
     */
    public Path getGenomePath(String filename) {
        return genomesDir.resolve(filename);
    }

    /**
     * Get full path to a connectome file in the connectomes directory.
     *
     * @param filename connectome filename (e.g. "trained_brain.connectome")
     * @return full path to connectome file
     */
    public Path getConnectomePath(String filename) {
        return connectomesDir.resolve(filename);
    }

    /**
     * Get full path to a log file in the logs directory.
     *
     * @param filename log filename (e.g. "feagi.log")
     * @return full path to log file
     */
    public Path getLogPath(String filename) {
        return logsDir.resolve(filename);
    }

    /**
     * Resolve a path, handling relative paths based on category.
     *
     * <p>If the path is absolute, it is returned as-is. If relative and a category
     * is specified, it resolves relative to that category's directory. Otherwise
     * it resolves relative to the current working directory.
     *
     * @param path     path to resolve
     * @param category optional category: "genome", "connectome", "config", or "log"
     * @return resolved absolute path
     */
    public Path resolvePath(Path path, String category) {
        if (path.isAbsolute()) {
            return path;
        }
        if (category != null) {
            switch (category.toLowerCase()) {
                case "genome":     return genomesDir.resolve(path);
                case "connectome": return connectomesDir.resolve(path);
                case "config":     return configDir.resolve(path);
                case "log":        return logsDir.resolve(path);
                default:           break;
            }
        }
        return Path.of("").toAbsolutePath().resolve(path);
    }

    // ------------------------------------------------------------------
    // Directory creation
    // ------------------------------------------------------------------

    /** Ensure config directory exists. Returns the config directory path. */
    public Path ensureConfigDir() {
        return ensureDir(configDir);
    }

    /** Ensure logs directory exists. Returns the logs directory path. */
    public Path ensureLogsDir() {
        return ensureDir(logsDir);
    }

    /** Ensure cache directory exists. Returns the cache directory path. */
    public Path ensureCacheDir() {
        return ensureDir(cacheDir);
    }

    /** Ensure genomes directory exists. Returns the genomes directory path. */
    public Path ensureGenomesDir() {
        return ensureDir(genomesDir);
    }

    /** Ensure connectomes directory exists. Returns the connectomes directory path. */
    public Path ensureConnectomesDir() {
        return ensureDir(connectomesDir);
    }

    /**
     * Ensure all FEAGI directories exist.
     *
     * <p>Creates config, logs, cache, genomes, and connectomes directories.
     */
    public void ensureAll() {
        ensureConfigDir();
        ensureLogsDir();
        ensureCacheDir();
        ensureGenomesDir();
        ensureConnectomesDir();
    }

    /**
     * Create a timestamped per-run log directory for a component.
     *
     * <p>Prunes old run directories beyond the retention count before creating
     * the new one. The directory name format is {@code run_YYYYMMDD_HHmmss}.
     *
     * @param component component name (e.g. "feagi", "bv")
     * @param retention number of most recent runs to keep
     * @return path to the newly created run directory
     */
    public Path createLogRunDir(String component, int retention) {
        ensureLogsDir();
        Path componentDir = ensureDir(logsDir.resolve(component));
        pruneLogRuns(componentDir, retention);
        return createUniqueRunDir(componentDir);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private static Path ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create directory: " + dir, e);
        }
        return dir;
    }

    private Path createUniqueRunDir(Path parentDir) {
        String timestamp = LocalDateTime.now().format(RUN_DIR_FORMAT);
        Path candidate = parentDir.resolve(timestamp);
        if (!Files.exists(candidate)) {
            return ensureDir(candidate);
        }
        for (int counter = 1; counter <= 1000; counter++) {
            candidate = parentDir.resolve(timestamp + "_" + counter);
            if (!Files.exists(candidate)) {
                return ensureDir(candidate);
            }
        }
        throw new UncheckedIOException(new IOException(
                "Could not create unique run directory in " + parentDir));
    }

    private void pruneLogRuns(Path parentDir, int retention) {
        List<Path> runDirs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parentDir,
                entry -> Files.isDirectory(entry) && entry.getFileName().toString().startsWith("run_"))) {
            for (Path entry : stream) {
                runDirs.add(entry);
            }
        } catch (IOException e) {
            return; // best-effort pruning
        }

        runDirs.sort(Comparator.comparing(p -> {
            try {
                return Files.getLastModifiedTime(p);
            } catch (IOException e) {
                return FileTime.fromMillis(0);
            }
        }));
        int excess = runDirs.size() - retention;
        if (excess <= 0) {
            return;
        }
        for (int i = 0; i < excess; i++) {
            deleteRecursively(runDirs.get(i));
        }
    }

    private static void deleteRecursively(Path dir) {
        try (var walker = Files.walk(dir)) {
            walker.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    @Override
    public String toString() {
        return "FeagiPaths("
                + "config=" + configDir
                + ", logs=" + logsDir
                + ", cache=" + cacheDir
                + ", genomes=" + genomesDir
                + ", connectomes=" + connectomesDir
                + ')';
    }
}
