/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
     * Passing null as explicit path should fall through to the discovery chain.
     * We cannot control the chain result, but it must not throw.
     */
    @Test
    void testNullExplicitFallsThrough() {
        Optional<Path> result = FeagiDiscovery.discover(null);
        assertNotNull(result);
    }

    /**
     * The no-arg discover() must return a non-null Optional (never throw).
     */
    @Test
    void testDiscoverReturnsOptional() {
        Optional<Path> result = FeagiDiscovery.discover();
        assertNotNull(result);
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
        Path file = tmp.resolve("feagi");
        Files.createFile(file);
        if (!FeagiDiscovery.isWindows()) {
            file.toFile().setExecutable(true);
        }
        assertTrue(FeagiDiscovery.isUsable(file));
    }

    /**
     * sdkLocation() should return a non-null path when running from classes.
     */
    @Test
    void testSdkLocationResolvable() {
        Path loc = FeagiDiscovery.sdkLocation();
        assertNotNull(loc, "sdkLocation() should resolve when running from classes directory");
        assertTrue(Files.exists(loc));
    }

    // ------------------------------------------------------------------
    // findOnPath happy-path tests
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
     * findOnPath should return empty when given an empty PATH string.
     */
    @Test
    void testFindOnPathEmpty() {
        assertFalse(FeagiDiscovery.findOnPath("").isPresent());
        assertFalse(FeagiDiscovery.findOnPath(null).isPresent());
    }
}
