/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link FeagiDiscovery}.
 */
public class FeagiDiscoveryTest {

    /**
     * An explicit path pointing to a valid file should be returned as-is.
     */
    @Test
    public void testExplicitPathFound(@TempDir Path tmp) throws IOException {
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
    public void testExplicitPathNotFound(@TempDir Path tmp) {
        Path missing = tmp.resolve("no-such-feagi");
        Optional<Path> result = FeagiDiscovery.discover(missing);
        assertFalse(result.isPresent());
    }

    /**
     * Passing null as explicit path should fall through to the discovery chain.
     * We cannot control the chain result, but it must not throw.
     */
    @Test
    public void testNullExplicitFallsThrough() {
        Optional<Path> result = FeagiDiscovery.discover(null);
        assertNotNull(result);
    }

    /**
     * The no-arg discover() must return a non-null Optional (never throw).
     */
    @Test
    public void testDiscoverReturnsOptional() {
        Optional<Path> result = FeagiDiscovery.discover();
        assertNotNull(result);
    }

    /**
     * BINARY_NAME should end with .exe on Windows, and not on other platforms.
     */
    @Test
    public void testBinaryNameMatchesPlatform() {
        if (FeagiDiscovery.isWindows()) {
            assertTrue(FeagiDiscovery.BINARY_NAME.endsWith(".exe"));
        } else {
            assertFalse(FeagiDiscovery.BINARY_NAME.contains(".exe"));
            assertEquals("feagi", FeagiDiscovery.BINARY_NAME);
        }
    }

    /**
     * platformDir() should return a non-null string on supported platforms.
     */
    @Test
    public void testPlatformDirNotNull() {
        String dir = FeagiDiscovery.platformDir();
        // We are running on a supported platform (CI or dev machine)
        assertNotNull(dir, "platformDir() returned null on a supported platform");
        assertTrue(dir.contains("-"), "platformDir() should be os-arch format: " + dir);
    }

    /**
     * A directory should not be considered usable (only regular files).
     */
    @Test
    public void testDirectoryNotUsable(@TempDir Path tmp) {
        assertFalse(FeagiDiscovery.isUsable(tmp));
    }

    /**
     * A non-existent path should not be usable.
     */
    @Test
    public void testNonExistentNotUsable(@TempDir Path tmp) {
        assertFalse(FeagiDiscovery.isUsable(tmp.resolve("nope")));
    }

    /**
     * A regular file that is executable (or on Windows) should be usable.
     */
    @Test
    public void testRegularFileUsable(@TempDir Path tmp) throws IOException {
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
    public void testSdkLocationResolvable() {
        Path loc = FeagiDiscovery.sdkLocation();
        assertNotNull(loc, "sdkLocation() should resolve when running from classes directory");
        assertTrue(Files.exists(loc));
    }
}
