/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FeagiPathsTest {

    // ------------------------------------------------------------------
    // Platform-specific directory naming
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "OS \"{0}\" → Capitalized user content")
    @CsvSource({
            "Windows 11",
            "Windows 10",
            "Mac OS X",
            "Darwin",
    })
    void testWindowsMacOsCapitalizedDirs(String osName, @TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, osName);

        assertTrue(paths.genomesDir.toString().contains("Genomes"),
                "Expected Genomes (capitalized) for OS: " + osName);
        assertTrue(paths.connectomesDir.toString().contains("Connectomes"),
                "Expected Connectomes (capitalized) for OS: " + osName);
        assertTrue(paths.genomesDir.toString().contains("Documents"),
                "Expected Documents/ for OS: " + osName);
    }

    @ParameterizedTest(name = "OS \"{0}\" → Lowercase user content")
    @CsvSource({
            "Linux",
            "linux",
            "SunOS",
            "FreeBSD",
    })
    void testLinuxLowercaseDirs(String osName, @TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, osName);

        assertTrue(paths.genomesDir.toString().contains("genomes"),
                "Expected genomes (lowercase) for OS: " + osName);
        assertTrue(paths.connectomesDir.toString().contains("connectomes"),
                "Expected connectomes (lowercase) for OS: " + osName);
        assertFalse(paths.genomesDir.toString().contains("Documents"),
                "Linux should not use Documents/");
    }

    // ------------------------------------------------------------------
    // Hidden directories are consistent across platforms
    // ------------------------------------------------------------------

    @Test
    void testHiddenDirsConsistentAcrossPlatforms(@TempDir Path home) {
        FeagiPaths winPaths = new FeagiPaths(home, "Windows 11");
        FeagiPaths linPaths = new FeagiPaths(home, "Linux");

        assertEquals(winPaths.configDir, linPaths.configDir);
        assertEquals(winPaths.logsDir, linPaths.logsDir);
        assertEquals(winPaths.cacheDir, linPaths.cacheDir);
    }

    @Test
    void testHiddenDirStructure(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");

        assertEquals(home.resolve(".feagi").resolve("config"), paths.configDir);
        assertEquals(home.resolve(".feagi").resolve("logs"), paths.logsDir);
        assertEquals(home.resolve(".feagi").resolve("cache"), paths.cacheDir);
    }

    // ------------------------------------------------------------------
    // Path accessors
    // ------------------------------------------------------------------

    @Test
    void testGetDefaultConfig(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        Path expected = home.resolve(".feagi").resolve("config")
                .resolve("feagi_configuration.toml");
        assertEquals(expected, paths.getDefaultConfig());
    }

    @Test
    void testGetGenomePath(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Windows 11");
        Path expected = paths.genomesDir.resolve("my_brain.json");
        assertEquals(expected, paths.getGenomePath("my_brain.json"));
    }

    @Test
    void testGetConnectomePath(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        Path expected = paths.connectomesDir.resolve("trained.connectome");
        assertEquals(expected, paths.getConnectomePath("trained.connectome"));
    }

    @Test
    void testGetLogPath(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        Path expected = paths.logsDir.resolve("feagi.log");
        assertEquals(expected, paths.getLogPath("feagi.log"));
    }

    // ------------------------------------------------------------------
    // Path resolution
    // ------------------------------------------------------------------

    @Test
    void testResolveAbsolutePathReturnedAsIs(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        Path absolute = home.resolve("somewhere").resolve("file.json");
        assertEquals(absolute, paths.resolvePath(absolute, "genome"));
    }

    @Test
    void testResolveRelativeWithGenomeCategory(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        Path resolved = paths.resolvePath(Path.of("brain.json"), "genome");
        assertEquals(paths.genomesDir.resolve("brain.json"), resolved);
    }

    @Test
    void testResolveRelativeWithConnectomeCategory(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        Path resolved = paths.resolvePath(Path.of("trained.connectome"), "connectome");
        assertEquals(paths.connectomesDir.resolve("trained.connectome"), resolved);
    }

    @Test
    void testResolveRelativeWithConfigCategory(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        Path resolved = paths.resolvePath(Path.of("custom.toml"), "config");
        assertEquals(paths.configDir.resolve("custom.toml"), resolved);
    }

    @Test
    void testResolveRelativeWithLogCategory(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        Path resolved = paths.resolvePath(Path.of("feagi.log"), "log");
        assertEquals(paths.logsDir.resolve("feagi.log"), resolved);
    }

    @Test
    void testResolveRelativeWithNullCategory(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        Path resolved = paths.resolvePath(Path.of("file.txt"), null);
        assertTrue(resolved.isAbsolute(), "Should resolve to absolute path");
        assertTrue(resolved.endsWith("file.txt"));
    }

    @Test
    void testResolveCategoryIsCaseInsensitive(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        Path lower = paths.resolvePath(Path.of("brain.json"), "genome");
        Path upper = paths.resolvePath(Path.of("brain.json"), "GENOME");
        assertEquals(lower, upper);
    }

    @Test
    void testResolveUnknownCategoryResolvesRelativeToCwd(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        Path resolved = paths.resolvePath(Path.of("file.txt"), "bogus");
        assertTrue(resolved.isAbsolute(), "Should resolve to absolute path");
        assertTrue(resolved.endsWith("file.txt"));
        // Should NOT resolve under any FEAGI directory
        assertFalse(resolved.startsWith(home),
                "Unknown category should not resolve under home");
    }

    // ------------------------------------------------------------------
    // Directory creation
    // ------------------------------------------------------------------

    @Test
    void testEnsureConfigDir(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        assertFalse(Files.exists(paths.configDir));

        Path result = paths.ensureConfigDir();
        assertEquals(paths.configDir, result);
        assertTrue(Files.isDirectory(paths.configDir));
    }

    @Test
    void testEnsureLogsDir(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        Path result = paths.ensureLogsDir();
        assertEquals(paths.logsDir, result);
        assertTrue(Files.isDirectory(paths.logsDir));
    }

    @Test
    void testEnsureCacheDir(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        Path result = paths.ensureCacheDir();
        assertEquals(paths.cacheDir, result);
        assertTrue(Files.isDirectory(paths.cacheDir));
    }

    @Test
    void testEnsureGenomesDir(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        Path result = paths.ensureGenomesDir();
        assertEquals(paths.genomesDir, result);
        assertTrue(Files.isDirectory(paths.genomesDir));
    }

    @Test
    void testEnsureConnectomesDir(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        Path result = paths.ensureConnectomesDir();
        assertEquals(paths.connectomesDir, result);
        assertTrue(Files.isDirectory(paths.connectomesDir));
    }

    @Test
    void testEnsureAllCreatesAllDirs(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        paths.ensureAll();

        assertTrue(Files.isDirectory(paths.configDir));
        assertTrue(Files.isDirectory(paths.logsDir));
        assertTrue(Files.isDirectory(paths.cacheDir));
        assertTrue(Files.isDirectory(paths.genomesDir));
        assertTrue(Files.isDirectory(paths.connectomesDir));
    }

    @Test
    void testEnsureIsIdempotent(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        paths.ensureAll();
        assertDoesNotThrow(paths::ensureAll, "Calling ensureAll() twice should not throw");
    }

    // ------------------------------------------------------------------
    // Log run directories
    // ------------------------------------------------------------------

    @Test
    void testCreateLogRunDir(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        Path runDir = paths.createLogRunDir("feagi", 5);

        assertTrue(Files.isDirectory(runDir));
        assertTrue(runDir.getFileName().toString().startsWith("run_"),
                "Run directory should start with 'run_'");
        assertEquals(paths.logsDir.resolve("feagi"), runDir.getParent());
    }

    @Test
    void testCreateLogRunDirPrunesOldRuns(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        paths.ensureLogsDir();
        Path componentDir = Files.createDirectories(paths.logsDir.resolve("bv"));

        // Create 5 old run directories
        for (int i = 1; i <= 5; i++) {
            Files.createDirectory(componentDir.resolve("run_20240101_00000" + i));
        }

        // Create new run with retention=3 → prunes excess (5-3=2 oldest removed), then adds 1 new
        paths.createLogRunDir("bv", 3);

        long count;
        try (var stream = Files.list(componentDir)) {
            count = stream.filter(p -> p.getFileName().toString().startsWith("run_")).count();
        }
        // 3 kept + 1 new = 4
        assertEquals(4, count, "Should have 3 retained + 1 new run directory");
    }

    @Test
    void testCreateLogRunDirHandlesCollision(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");

        // Create two run dirs in quick succession (same second)
        Path first = paths.createLogRunDir("test", 10);
        Path second = paths.createLogRunDir("test", 10);

        assertNotEquals(first, second, "Concurrent run dirs should have unique names");
        assertTrue(Files.isDirectory(first));
        assertTrue(Files.isDirectory(second));
    }

    // ------------------------------------------------------------------
    // Constructor validation
    // ------------------------------------------------------------------

    @Test
    void testNullHomeDirThrows() {
        assertThrows(NullPointerException.class, () -> new FeagiPaths(null));
    }

    // ------------------------------------------------------------------
    // toString
    // ------------------------------------------------------------------

    @Test
    void testToString(@TempDir Path home) {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        String repr = paths.toString();
        assertTrue(repr.contains("FeagiPaths("));
        assertTrue(repr.contains("config="));
        assertTrue(repr.contains("genomes="));
    }
}
