/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BvDiscovery}.
 */
class BvDiscoveryTest {

    // ------------------------------------------------------------------
    // findOnPath — happy path
    // ------------------------------------------------------------------

    @Test
    void testFindOnPathWithFakeBinary(@TempDir Path tmp) throws IOException {
        List<String> names = BvDiscovery.binaryNames();
        Path fakeBinary = tmp.resolve(names.get(0));
        Files.createFile(fakeBinary);
        if (!FeagiDiscovery.isWindows()) {
            fakeBinary.toFile().setExecutable(true);
        }

        Optional<Path> result = BvDiscovery.findOnPath(tmp.toString());
        assertTrue(result.isPresent(), "findOnPath should find the binary");
        assertEquals(fakeBinary, result.get());
    }

    // ------------------------------------------------------------------
    // findOnPath — empty/null PATH
    // ------------------------------------------------------------------

    @Test
    void testFindOnPathEmpty() {
        assertFalse(BvDiscovery.findOnPath("").isPresent());
        assertFalse(BvDiscovery.findOnPath(null).isPresent());
    }

    // ------------------------------------------------------------------
    // findOnPath — blank segments
    // ------------------------------------------------------------------

    @Test
    void testFindOnPathSkipsBlankSegments(@TempDir Path tmp) throws IOException {
        List<String> names = BvDiscovery.binaryNames();
        Path fakeBinary = tmp.resolve(names.get(0));
        Files.createFile(fakeBinary);
        if (!FeagiDiscovery.isWindows()) {
            fakeBinary.toFile().setExecutable(true);
        }

        // Leading empty segment from path separator
        Optional<Path> result = BvDiscovery.findOnPath(File.pathSeparator + tmp.toString());
        assertTrue(result.isPresent(), "Should find binary after skipping blank segment");
        assertEquals(fakeBinary, result.get());
    }

    @Test
    void testFindOnPathAllInvalidDirs() {
        String invalidPath = "/nonexistent/dir1" + File.pathSeparator + "/nonexistent/dir2";
        assertFalse(BvDiscovery.findOnPath(invalidPath).isPresent());
    }

    // ------------------------------------------------------------------
    // binaryNames — platform-specific
    // ------------------------------------------------------------------

    @Test
    void testBinaryNamesNotEmpty() {
        List<String> names = BvDiscovery.binaryNames();
        assertFalse(names.isEmpty());
        if (FeagiDiscovery.isWindows()) {
            assertTrue(names.get(0).endsWith(".exe"));
        } else {
            assertFalse(names.get(0).contains(".exe"));
        }
    }

    // ------------------------------------------------------------------
    // isUsable
    // ------------------------------------------------------------------

    @Test
    void testDirectoryNotUsable(@TempDir Path tmp) {
        assertFalse(BvDiscovery.isUsable(tmp));
    }

    @Test
    void testNonExistentNotUsable(@TempDir Path tmp) {
        assertFalse(BvDiscovery.isUsable(tmp.resolve("nonexistent")));
    }

    @Test
    void testRegularFileUsable(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("TestBinary");
        Files.createFile(file);
        if (!FeagiDiscovery.isWindows()) {
            file.toFile().setExecutable(true);
        }
        assertTrue(BvDiscovery.isUsable(file));
    }

    // ------------------------------------------------------------------
    // discover — smoke test
    // ------------------------------------------------------------------

    @Test
    void testDiscoverDoesNotThrow() {
        Optional<Path> result = BvDiscovery.discover();
        assertNotNull(result);
        result.ifPresent(p -> assertTrue(Files.exists(p),
                "Discovered path should exist: " + p));
    }

    // ------------------------------------------------------------------
    // isMacOs — smoke test
    // ------------------------------------------------------------------

    @Test
    void testIsMacOsReturnsBoolean() {
        // Just verify it doesn't throw
        BvDiscovery.isMacOs();
    }
}
