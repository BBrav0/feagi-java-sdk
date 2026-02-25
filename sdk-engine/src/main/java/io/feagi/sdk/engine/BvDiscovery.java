/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.engine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Discovers the Brain Visualizer executable on the local system.
 *
 * <p>Mirrors the Python SDK's {@code _resolve_bv_binary()} in {@code feagi/cli/bv.py}.
 * Uses the same discovery pattern as {@link FeagiDiscovery}: system PATH first,
 * then common installation locations.
 *
 * <p>Platform-specific binary names:
 * <ul>
 *   <li>Linux: {@code BrainVisualizer}, {@code BrainVisualizer-Remote}</li>
 *   <li>macOS: Not auto-discovered (download from GitHub releases)</li>
 *   <li>Windows: {@code BrainVisualizer.exe}, {@code BrainVisualizer-Remote.exe}</li>
 * </ul>
 *
 * <p>On macOS, the Python SDK prints download instructions instead of auto-discovering.
 * This class mirrors that behavior via {@link #isMacOs()}.
 */
public final class BvDiscovery {

    private static final Logger LOG = Logger.getLogger(BvDiscovery.class.getName());

    private static final String OS_NAME =
            System.getProperty("os.name", "").toLowerCase();
    private static final boolean IS_WINDOWS = OS_NAME.contains("win");
    private static final boolean IS_MAC = OS_NAME.contains("mac") || OS_NAME.contains("darwin");

    /** GitHub releases URL for Brain Visualizer. */
    public static final String BV_RELEASES_URL =
            "https://github.com/feagi/brain-visualizer/releases";

    private BvDiscovery() {}

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Discover the Brain Visualizer binary using the search chain.
     *
     * <p>Search order: system PATH → common locations.
     * Returns empty on macOS (BV not distributed via package managers).
     *
     * @return the resolved path, or empty if not found
     */
    public static Optional<Path> discover() {
        if (IS_MAC) {
            LOG.info("BV auto-discovery skipped on macOS. Download from: " + BV_RELEASES_URL);
            return Optional.empty();
        }
        return findOnPath()
                .or(BvDiscovery::findAtCommonLocations);
    }

    /**
     * Discover the Brain Visualizer binary, throwing if not found.
     *
     * @return the resolved path
     * @throws IllegalStateException if the binary cannot be found
     */
    public static Path discoverOrThrow() {
        return discover().orElseThrow(() -> {
            if (IS_MAC) {
                return new IllegalStateException(
                        "Brain Visualizer is not auto-discovered on macOS.\n"
                                + "Download from: " + BV_RELEASES_URL);
            }
            return new IllegalStateException(
                    "Brain Visualizer executable not found.\n"
                            + "Download from: " + BV_RELEASES_URL);
        });
    }

    /**
     * Return {@code true} if the current platform is macOS.
     *
     * <p>On macOS, the Python SDK prints download instructions rather than
     * auto-discovering BV. CLI commands should check this and handle accordingly.
     */
    public static boolean isMacOs() {
        return IS_MAC;
    }

    // ------------------------------------------------------------------
    // Discovery steps
    // ------------------------------------------------------------------

    /**
     * Scan the {@code PATH} environment variable for BV binaries.
     */
    static Optional<Path> findOnPath() {
        return findOnPath(System.getenv("PATH"));
    }

    /**
     * Scan the given PATH string for BV binaries.
     *
     * @param pathEnv a {@link File#pathSeparator}-delimited list of directories
     * @return the resolved path, or empty if not found
     */
    static Optional<Path> findOnPath(String pathEnv) {
        if (pathEnv == null || pathEnv.isEmpty()) return Optional.empty();

        for (String dir : pathEnv.split(File.pathSeparator)) {
            if (dir.isBlank()) continue;
            for (String name : binaryNames()) {
                Path candidate = Path.of(dir).resolve(name);
                if (isUsable(candidate)) {
                    LOG.info(() -> "Found Brain Visualizer in PATH: " + candidate);
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Check common installation locations.
     */
    static Optional<Path> findAtCommonLocations() {
        List<Path> searchDirs = new ArrayList<>();

        if (IS_WINDOWS) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                searchDirs.add(Path.of(localAppData, "Programs", "BrainVisualizer"));
                searchDirs.add(Path.of(localAppData, "BrainVisualizer"));
            }
            String programFiles = System.getenv("ProgramFiles");
            if (programFiles != null) {
                searchDirs.add(Path.of(programFiles, "BrainVisualizer"));
            }
        } else {
            // Linux
            searchDirs.add(Path.of("/usr/local/bin"));
            searchDirs.add(Path.of("/usr/bin"));
            searchDirs.add(Path.of("/opt/BrainVisualizer"));
            String home = System.getProperty("user.home");
            if (home != null) {
                searchDirs.add(Path.of(home, ".local", "bin"));
            }
        }

        for (Path dir : searchDirs) {
            for (String name : binaryNames()) {
                Path candidate = dir.resolve(name);
                if (isUsable(candidate)) {
                    LOG.info(() -> "Found Brain Visualizer at: " + candidate);
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Return the platform-specific binary names to search for.
     */
    static List<String> binaryNames() {
        if (IS_WINDOWS) {
            return List.of("BrainVisualizer.exe", "BrainVisualizer-Remote.exe");
        }
        return List.of("BrainVisualizer", "BrainVisualizer-Remote");
    }

    /**
     * Return {@code true} if the path exists and is plausibly executable.
     */
    static boolean isUsable(Path path) {
        if (!Files.exists(path)) return false;
        if (Files.isDirectory(path)) return false;
        return IS_WINDOWS || Files.isExecutable(path);
    }
}
