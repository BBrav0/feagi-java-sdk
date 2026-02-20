/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.engine;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

    private static final String OS_NAME =
            System.getProperty("os.name", "").toLowerCase();
    private static final String OS_ARCH =
            System.getProperty("os.arch", "").toLowerCase();
    private static final boolean IS_WINDOWS = OS_NAME.contains("win");

    /** Binary name for the current platform. */
    public static final String BINARY_NAME = isWindows() ? "feagi.exe" : "feagi";

    private FeagiDiscovery() {}

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Validate an explicit FEAGI executable path.
     *
     * <p>This method does <b>not</b> fall through to the discovery chain — use the no-arg
     * {@link #discover()} for auto-discovery. Passing {@code null} is a programming error
     * and throws immediately, consistent with the project's fail-fast convention.
     *
     * @param explicitPath caller-supplied path to the FEAGI binary (must not be {@code null})
     * @return the path if usable, or empty if it does not exist or is not executable
     * @throws NullPointerException if {@code explicitPath} is {@code null}
     */
    public static Optional<Path> discover(Path explicitPath) {
        Objects.requireNonNull(explicitPath,
                "explicitPath must not be null; use discover() for auto-discovery");
        if (isUsable(explicitPath)) {
            LOG.info(() -> "Using explicit FEAGI path: " + explicitPath);
            return Optional.of(explicitPath);
        }
        LOG.warning(() -> "Explicit FEAGI path not usable: " + explicitPath);
        return Optional.empty();
    }

    /**
     * Discover the FEAGI executable using the full search chain.
     *
     * <p>Search order: bundled → dev build → system PATH → common locations.
     *
     * @return the resolved path, or empty if not found
     */
    public static Optional<Path> discover() {
        Optional<Path> sdkRoot = sdkLocation();

        Optional<Path> result = sdkRoot.flatMap(FeagiDiscovery::findBundledBinary)
                .or(() -> sdkRoot.flatMap(FeagiDiscovery::findDevBuild))
                .or(FeagiDiscovery::findOnPath)
                .or(FeagiDiscovery::findAtCommonLocations);

        if (result.isEmpty()) {
            LOG.warning("FEAGI executable not found. Please specify an explicit path.");
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Discovery steps
    // ------------------------------------------------------------------

    /**
     * Step 1 — look for a binary bundled alongside the SDK jar.
     *
     * <p>Expected layout: {@code {sdkRoot}/bin/{platformDir}/feagi}
     *
     * @param sdkRoot the directory containing the SDK jar or classes
     * @return the resolved path, or empty if not found
     */
    static Optional<Path> findBundledBinary(Path sdkRoot) {
        String platformDir = platformDir();
        if (platformDir == null) return Optional.empty();

        Path candidate = sdkRoot.resolve("bin").resolve(platformDir).resolve(BINARY_NAME);
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
     * relative to the parent of the SDK location.
     *
     * <p>Note: when running from a Gradle classes directory (e.g.,
     * {@code build/classes/java/main}), the parent will not be the project root
     * and this step will safely return empty, falling through to PATH discovery.
     * This step is most effective when running from a packaged jar.
     *
     * @param sdkRoot the directory containing the SDK jar or classes
     * @return the resolved path, or empty if not found
     */
    static Optional<Path> findDevBuild(Path sdkRoot) {
        Path parent = sdkRoot.getParent();
        if (parent == null) return Optional.empty();

        List<Path> candidates = List.of(
                parent.resolve("feagi").resolve("target").resolve("release").resolve(BINARY_NAME),
                parent.resolve("feagi").resolve("target").resolve("debug").resolve(BINARY_NAME),
                parent.resolve("feagi-core").resolve("target").resolve("release").resolve(BINARY_NAME),
                parent.resolve("feagi-core").resolve("target").resolve("debug").resolve(BINARY_NAME)
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
                LOG.info(() -> "Found FEAGI in system PATH: " + candidate);
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

        List<Path> locations = new ArrayList<>();
        locations.add(Path.of("/usr/local/bin").resolve(BINARY_NAME));
        locations.add(Path.of("/usr/bin").resolve(BINARY_NAME));
        String home = System.getProperty("user.home");
        if (home != null) {
            locations.add(Path.of(home).resolve(".cargo").resolve("bin").resolve(BINARY_NAME));
        }

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
     * @return directory path, or empty if it cannot be determined
     */
    static Optional<Path> sdkLocation() {
        try {
            var source = FeagiDiscovery.class.getProtectionDomain().getCodeSource();
            if (source == null) return Optional.empty();
            var loc = source.getLocation();
            if (loc == null) return Optional.empty();
            Path location = Path.of(loc.toURI());
            // If running from a jar, location is the jar file — return its parent.
            // If running from classes dir, location is the dir itself.
            return Optional.of(Files.isRegularFile(location) ? location.getParent() : location);
        } catch (URISyntaxException | SecurityException e) {
            LOG.fine(() -> "Could not determine SDK location: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Return the platform directory name matching the Python SDK convention,
     * or {@code null} if the current platform is unsupported.
     *
     * <p>Examples: {@code linux-x86_64}, {@code darwin-aarch64}, {@code windows-x86_64}.
     */
    public static String platformDir() {
        return platformDir(OS_NAME, OS_ARCH);
    }

    /**
     * Return the platform directory name for the given OS and architecture strings.
     *
     * <p>Package-private overload for cross-platform testing.
     *
     * @param osName lower-cased OS name (e.g., {@code "linux"}, {@code "mac os x"}, {@code "windows 11"})
     * @param osArch lower-cased architecture (e.g., {@code "amd64"}, {@code "aarch64"})
     * @return platform directory string, or {@code null} if unsupported
     */
    static String platformDir(String osName, String osArch) {
        String osPrefix;
        if (osName.contains("linux"))      osPrefix = "linux";
        else if (osName.contains("mac"))   osPrefix = "darwin";
        else if (osName.contains("win"))   osPrefix = "windows";
        else return null;

        String archSuffix;
        if (osArch.equals("amd64") || osArch.equals("x86_64"))       archSuffix = "x86_64";
        else if (osArch.equals("aarch64") || osArch.equals("arm64")) archSuffix = "aarch64";
        else return null;

        return osPrefix + "-" + archSuffix;
    }

    /** Return {@code true} if the current OS is Windows. */
    public static boolean isWindows() {
        return IS_WINDOWS;
    }
}
