/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.engine;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Discovers the FEAGI engine executable on the local system.
 *
 * <p>Discovery order (mirrors the Python SDK):
 * <ol>
 *   <li><b>Bundled binary</b> — shipped alongside the SDK jar under {@code bin/{platform}/feagi}</li>
 *   <li><b>Development build</b> — Rust {@code target/release} or {@code target/debug} in sibling
 *       {@code feagi/} or {@code feagi-core/} directories relative to the SDK root</li>
 *   <li><b>System PATH</b> — scans each directory in the {@code PATH} environment variable</li>
 *   <li><b>Common system locations</b> — {@code /usr/local/bin}, {@code /usr/bin},
 *       {@code ~/.cargo/bin} (Unix only)</li>
 * </ol>
 *
 * <p>An explicit path override bypasses the chain entirely.
 *
 * <p>Guardrail: no hardcoded host/port/timeout defaults belong here.
 */
public final class FeagiDiscovery {

    private static final Logger LOG = Logger.getLogger(FeagiDiscovery.class.getName());

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    /** Binary name for the current platform. */
    public static final String BINARY_NAME = isWindows() ? "feagi.exe" : "feagi";

    private FeagiDiscovery() {}

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Discover the FEAGI executable with an optional explicit override.
     *
     * <p>If {@code explicitPath} is non-null the method validates it and returns it directly
     * without searching. Pass {@code null} to use the full discovery chain.
     *
     * @param explicitPath caller-supplied path to the FEAGI binary, or {@code null}
     * @return the resolved path, or empty if not found
     */
    public static Optional<Path> discover(Path explicitPath) {
        if (explicitPath != null) {
            if (isUsable(explicitPath)) {
                LOG.info(() -> "Using explicit FEAGI path: " + explicitPath);
                return Optional.of(explicitPath);
            }
            LOG.warning(() -> "Explicit FEAGI path not usable: " + explicitPath);
            return Optional.empty();
        }
        return discover();
    }

    /**
     * Discover the FEAGI executable using the full search chain.
     *
     * <p>Search order: bundled → dev build → system PATH → common locations.
     *
     * @return the resolved path, or empty if not found
     */
    public static Optional<Path> discover() {
        Optional<Path> result;

        result = findBundledBinary();
        if (result.isPresent()) return result;

        result = findDevBuild();
        if (result.isPresent()) return result;

        result = findOnPath();
        if (result.isPresent()) return result;

        result = findAtCommonLocations();
        if (result.isPresent()) return result;

        LOG.warning("FEAGI executable not found. Please specify an explicit path.");
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // Discovery steps
    // ------------------------------------------------------------------

    /**
     * Step 1 — look for a binary bundled alongside the SDK jar.
     *
     * <p>Expected layout: {@code {jarDir}/bin/{platformDir}/feagi}
     */
    static Optional<Path> findBundledBinary() {
        String platformDir = platformDir();
        if (platformDir == null) return Optional.empty();

        Path jarDir = sdkLocation();
        if (jarDir == null) return Optional.empty();

        Path candidate = jarDir.resolve("bin").resolve(platformDir).resolve(BINARY_NAME);
        if (isUsable(candidate)) {
            LOG.info(() -> "Found bundled FEAGI binary: " + candidate);
            return Optional.of(candidate);
        }
        return Optional.empty();
    }

    /**
     * Step 2 — look for a Rust dev build in sibling directories.
     *
     * <p>Checks {@code feagi/target/release}, {@code feagi/target/debug},
     * {@code feagi-core/target/release}, and {@code feagi-core/target/debug}
     * relative to the SDK root.
     */
    static Optional<Path> findDevBuild() {
        Path sdkRoot = sdkLocation();
        if (sdkRoot == null) return Optional.empty();

        // Go up one level from the jar/classes directory to the project root
        Path projectRoot = sdkRoot.getParent();
        if (projectRoot == null) return Optional.empty();

        List<Path> candidates = List.of(
                projectRoot.resolve("feagi").resolve("target").resolve("release").resolve(BINARY_NAME),
                projectRoot.resolve("feagi").resolve("target").resolve("debug").resolve(BINARY_NAME),
                projectRoot.resolve("feagi-core").resolve("target").resolve("release").resolve(BINARY_NAME),
                projectRoot.resolve("feagi-core").resolve("target").resolve("debug").resolve(BINARY_NAME)
        );

        for (Path candidate : candidates) {
            if (isUsable(candidate)) {
                LOG.info(() -> "Found FEAGI dev build: " + candidate);
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Step 3 — scan the {@code PATH} environment variable.
     */
    static Optional<Path> findOnPath() {
        return findOnPath(System.getenv("PATH"));
    }

    /**
     * Scan the given PATH string for the FEAGI binary.
     *
     * @param pathEnv a {@link File#pathSeparator}-delimited list of directories
     * @return the resolved path, or empty if not found
     */
    static Optional<Path> findOnPath(String pathEnv) {
        if (pathEnv == null || pathEnv.isEmpty()) return Optional.empty();

        for (String dir : pathEnv.split(File.pathSeparator)) {
            Path candidate = Path.of(dir).resolve(BINARY_NAME);
            if (isUsable(candidate)) {
                LOG.info("Found FEAGI in system PATH");
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Step 4 — check common Unix install locations (skipped on Windows).
     */
    static Optional<Path> findAtCommonLocations() {
        if (isWindows()) return Optional.empty();

        List<Path> locations = List.of(
                Path.of("/usr/local/bin").resolve(BINARY_NAME),
                Path.of("/usr/bin").resolve(BINARY_NAME),
                Path.of(System.getProperty("user.home")).resolve(".cargo").resolve("bin").resolve(BINARY_NAME)
        );

        for (Path candidate : locations) {
            if (isUsable(candidate)) {
                LOG.info(() -> "Found FEAGI at: " + candidate);
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Return {@code true} if the path exists and is plausibly executable.
     *
     * <p>On Windows, {@link Files#isExecutable} is unreliable for {@code .exe} files,
     * so we only check existence.
     */
    static boolean isUsable(Path path) {
        if (!Files.exists(path)) return false;
        if (Files.isDirectory(path)) return false;
        return isWindows() || Files.isExecutable(path);
    }

    /**
     * Resolve the directory containing the SDK jar or classes.
     *
     * @return directory path, or {@code null} if it cannot be determined
     */
    static Path sdkLocation() {
        try {
            var source = FeagiDiscovery.class.getProtectionDomain().getCodeSource();
            if (source == null) return null;
            Path location = Path.of(source.getLocation().toURI());
            // If running from a jar, location is the jar file — return its parent.
            // If running from classes dir, location is the dir itself.
            return Files.isRegularFile(location) ? location.getParent() : location;
        } catch (URISyntaxException | SecurityException e) {
            LOG.fine(() -> "Could not determine SDK location: " + e.getMessage());
            return null;
        }
    }

    /**
     * Return the platform directory name matching the Python SDK convention,
     * or {@code null} if the current platform is unsupported.
     *
     * <p>Examples: {@code linux-x86_64}, {@code darwin-aarch64}, {@code windows-x86_64}.
     */
    public static String platformDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();

        String osPrefix;
        if (os.contains("linux"))      osPrefix = "linux";
        else if (os.contains("mac"))   osPrefix = "darwin";
        else if (os.contains("win"))   osPrefix = "windows";
        else return null;

        String archSuffix;
        if (arch.equals("amd64") || arch.equals("x86_64"))       archSuffix = "x86_64";
        else if (arch.equals("aarch64") || arch.equals("arm64")) archSuffix = "aarch64";
        else return null;

        return osPrefix + "-" + archSuffix;
    }

    /** Return {@code true} if the current OS is Windows. */
    static boolean isWindows() {
        return IS_WINDOWS;
    }
}
