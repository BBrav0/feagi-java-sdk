/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link FeagiDiscovery}.
 */
class FeagiDiscoveryTest {

    /**
     * An explicit path pointing to a valid file should be returned as-is.
     */
    @Test
    void testExplicitPathFound(@TempDir Path tmp) throws IOException {
        Path fakeBinary = tmp.resolve(FeagiDiscovery.BINARY_NAME);
        Files.createFile(fakeBinary);
        if (!FeagiDiscovery.isWindows()) {
            fakeBinary.toFile().setExecutable(true);
        }

        Optional<Path> result = FeagiDiscovery.discover(fakeBinary);
        assertTrue(result.isPresent());
        assertEquals(fakeBinary, result.get());
    }

    /**
     * An explicit path that does not exist should return empty.
     */
    @Test
    void testExplicitPathNotFound(@TempDir Path tmp) {
        Path missing = tmp.resolve("no-such-feagi");
        Optional<Path> result = FeagiDiscovery.discover(missing);
        assertFalse(result.isPresent());
    }

    /**
     * Passing null to discover(Path) is a programming error and must throw.
     * Callers wanting auto-discovery should use the no-arg discover().
     */
    @Test
    void testNullExplicitThrows() {
        assertThrows(NullPointerException.class, () -> FeagiDiscovery.discover(null));
    }

    /**
     * The no-arg discover() must never throw and always returns a valid Optional.
     * If it finds something, that path must exist.
     */
    @Test
    void testDiscoverDoesNotThrow() {
        Optional<Path> result = FeagiDiscovery.discover();
        assertNotNull(result);
        result.ifPresent(p -> assertTrue(Files.exists(p),
                "Discovered path should exist: " + p));
    }

    /**
     * BINARY_NAME should end with .exe on Windows, and not on other platforms.
     */
    @Test
    void testBinaryNameMatchesPlatform() {
        if (FeagiDiscovery.isWindows()) {
            assertTrue(FeagiDiscovery.BINARY_NAME.endsWith(".exe"));
        } else {
            assertFalse(FeagiDiscovery.BINARY_NAME.contains(".exe"));
            assertEquals("feagi", FeagiDiscovery.BINARY_NAME);
        }
    }

    /**
     * platformDir() should return a non-null string on supported platforms.
     * Skipped on unsupported architectures (e.g., 32-bit x86, RISC-V).
     */
    @Test
    void testPlatformDirNotNull() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        Set<String> supported = Set.of("amd64", "x86_64", "aarch64", "arm64");
        assumeTrue(supported.contains(arch), "Skipping on unsupported architecture: " + arch);

        String dir = FeagiDiscovery.platformDir();
        assertNotNull(dir, "platformDir() returned null on a supported platform");
        assertTrue(dir.contains("-"), "platformDir() should be os-arch format: " + dir);
    }

    /**
     * A directory should not be considered usable (only regular files).
     */
    @Test
    void testDirectoryNotUsable(@TempDir Path tmp) {
        assertFalse(FeagiDiscovery.isUsable(tmp));
    }

    /**
     * A non-existent path should not be usable.
     */
    @Test
    void testNonExistentNotUsable(@TempDir Path tmp) {
        assertFalse(FeagiDiscovery.isUsable(tmp.resolve("nope")));
    }

    /**
     * A regular file that is executable (or on Windows) should be usable.
     */
    @Test
    void testRegularFileUsable(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve(FeagiDiscovery.BINARY_NAME);
        Files.createFile(file);
        if (!FeagiDiscovery.isWindows()) {
            file.toFile().setExecutable(true);
        }
        assertTrue(FeagiDiscovery.isUsable(file));
    }

    /**
     * sdkLocation() should return a present Optional when running from classes.
     */
    @Test
    void testSdkLocationResolvable() {
        Optional<Path> loc = FeagiDiscovery.sdkLocation();
        assertTrue(loc.isPresent(), "sdkLocation() should resolve when running from classes directory");
        assertTrue(Files.exists(loc.get()));
    }

    // ------------------------------------------------------------------
    // findOnPath tests
    // ------------------------------------------------------------------

    /**
     * findOnPath should discover a binary in a temp directory passed as the PATH string.
     */
    @Test
    void testFindOnPathWithFakeBinary(@TempDir Path tmp) throws IOException {
        Path fakeBinary = tmp.resolve(FeagiDiscovery.BINARY_NAME);
        Files.createFile(fakeBinary);
        if (!FeagiDiscovery.isWindows()) {
            fakeBinary.toFile().setExecutable(true);
        }

        Optional<Path> result = FeagiDiscovery.findOnPath(tmp.toString());
        assertTrue(result.isPresent(), "findOnPath should find the binary");
        assertEquals(fakeBinary, result.get());
    }

    /**
     * findOnPath should return empty when given an empty or null PATH string.
     */
    @Test
    void testFindOnPathEmpty() {
        assertFalse(FeagiDiscovery.findOnPath("").isPresent());
        assertFalse(FeagiDiscovery.findOnPath(null).isPresent());
    }

    // ------------------------------------------------------------------
    // findAtCommonLocations tests
    // ------------------------------------------------------------------

    /**
     * On Windows, findAtCommonLocations returns empty immediately (Unix-only step).
     */
    @Test
    void testFindAtCommonLocationsSkippedOnWindows() {
        assumeTrue(FeagiDiscovery.isWindows(), "Skipping — only runs on Windows");
        assertFalse(FeagiDiscovery.findAtCommonLocations().isPresent());
    }

    /**
     * On Unix, findAtCommonLocations returns a non-null Optional.
     * Cannot assert empty because FEAGI may legitimately be installed.
     * If it finds something, that path must exist.
     */
    @Test
    void testFindAtCommonLocationsReturnsOptionalOnUnix() {
        assumeTrue(!FeagiDiscovery.isWindows(), "Skipping — only runs on Unix");
        Optional<Path> result = FeagiDiscovery.findAtCommonLocations();
        assertNotNull(result);
        result.ifPresent(p -> assertTrue(Files.exists(p),
                "Discovered path should exist: " + p));
    }

    // ------------------------------------------------------------------
    // findBundledBinary tests
    // ------------------------------------------------------------------

    /**
     * findBundledBinary should discover a binary in the expected directory structure.
     */
    @Test
    void testFindBundledBinaryFound(@TempDir Path tmp) throws IOException {
        String platform = FeagiDiscovery.platformDir();
        assumeTrue(platform != null, "Skipping on unsupported platform");

        Path binDir = tmp.resolve("bin").resolve(platform);
        Files.createDirectories(binDir);
        Path binary = binDir.resolve(FeagiDiscovery.BINARY_NAME);
        Files.createFile(binary);
        if (!FeagiDiscovery.isWindows()) {
            binary.toFile().setExecutable(true);
        }

        Optional<Path> result = FeagiDiscovery.findBundledBinary(tmp);
        assertTrue(result.isPresent(), "findBundledBinary should find the binary");
        assertEquals(binary, result.get());
    }

    /**
     * findBundledBinary should return empty when the binary does not exist.
     */
    @Test
    void testFindBundledBinaryMissing(@TempDir Path tmp) {
        Optional<Path> result = FeagiDiscovery.findBundledBinary(tmp);
        assertFalse(result.isPresent());
    }

    // ------------------------------------------------------------------
    // platformDir cross-platform tests
    // ------------------------------------------------------------------

    /**
     * Exhaustive test of platformDir mapping logic across all supported combinations.
     * Uses the parameterized overload so these tests run on any CI platform.
     */
    @Test
    void testPlatformDirAllCombinations() {
        // Linux
        assertEquals("linux-x86_64", FeagiDiscovery.platformDir("linux", "amd64"));
        assertEquals("linux-x86_64", FeagiDiscovery.platformDir("linux", "x86_64"));
        assertEquals("linux-aarch64", FeagiDiscovery.platformDir("linux", "aarch64"));
        assertEquals("linux-aarch64", FeagiDiscovery.platformDir("linux", "arm64"));
        // macOS
        assertEquals("darwin-x86_64", FeagiDiscovery.platformDir("mac os x", "amd64"));
        assertEquals("darwin-x86_64", FeagiDiscovery.platformDir("mac os x", "x86_64"));
        assertEquals("darwin-aarch64", FeagiDiscovery.platformDir("mac os x", "aarch64"));
        assertEquals("darwin-aarch64", FeagiDiscovery.platformDir("mac os x", "arm64"));
        // Windows
        assertEquals("windows-x86_64", FeagiDiscovery.platformDir("windows 11", "amd64"));
        assertEquals("windows-x86_64", FeagiDiscovery.platformDir("windows 10", "x86_64"));
        assertEquals("windows-aarch64", FeagiDiscovery.platformDir("windows 11", "aarch64"));
        // Unsupported
        assertNull(FeagiDiscovery.platformDir("linux", "riscv64"));
        assertNull(FeagiDiscovery.platformDir("freebsd", "amd64"));
        assertNull(FeagiDiscovery.platformDir("sunos", "sparc"));
    }
}
