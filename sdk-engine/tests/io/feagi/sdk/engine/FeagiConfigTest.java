/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FeagiConfigTest {

    // ------------------------------------------------------------------
    // generateDefaultConfig
    // ------------------------------------------------------------------

    @Test
    void testGenerateDefaultConfigCreatesFile(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        Path config = FeagiConfig.generateDefaultConfig(paths, null, false);

        assertTrue(Files.exists(config));
        assertEquals(paths.getDefaultConfig(), config);
        String content = Files.readString(config);
        assertTrue(content.contains("[api]"));
        assertTrue(content.contains("[timeouts]"));
    }

    @Test
    void testGenerateDefaultConfigCustomPath(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        Path customDir = home.resolve("custom");
        Path customPath = customDir.resolve("my_config.toml");

        Path config = FeagiConfig.generateDefaultConfig(paths, customPath, false);

        assertEquals(customPath, config);
        assertTrue(Files.exists(config));
        assertTrue(Files.isDirectory(customDir), "Parent directory should be created");
    }

    @Test
    void testGenerateDefaultConfigThrowsWhenExistsAndNotForced(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        FeagiConfig.generateDefaultConfig(paths, null, false);

        assertThrows(FileAlreadyExistsException.class, () ->
                FeagiConfig.generateDefaultConfig(paths, null, false));
    }

    @Test
    void testGenerateDefaultConfigOverwritesWithForce(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        FeagiConfig.generateDefaultConfig(paths, null, false);

        // Write different content to verify overwrite
        Files.writeString(paths.getDefaultConfig(), "modified");
        assertEquals("modified", Files.readString(paths.getDefaultConfig()));

        FeagiConfig.generateDefaultConfig(paths, null, true);
        String content = Files.readString(paths.getDefaultConfig());
        assertTrue(content.contains("[api]"), "Should be overwritten with default content");
    }

    // ------------------------------------------------------------------
    // ensureDefaultConfig
    // ------------------------------------------------------------------

    @Test
    void testEnsureDefaultConfigCreatesWhenMissing(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        assertFalse(Files.exists(paths.getDefaultConfig()));

        Path config = FeagiConfig.ensureDefaultConfig(paths);

        assertEquals(paths.getDefaultConfig(), config);
        assertTrue(Files.exists(config));
        String content = Files.readString(config);
        assertTrue(content.contains("[api]"));
    }

    @Test
    void testEnsureDefaultConfigPreservesExisting(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        paths.ensureConfigDir();
        Files.writeString(paths.getDefaultConfig(), "custom content");

        Path config = FeagiConfig.ensureDefaultConfig(paths);

        assertEquals("custom content", Files.readString(config),
                "Existing config should not be overwritten");
    }

    // ------------------------------------------------------------------
    // initEnvironment
    // ------------------------------------------------------------------

    @Test
    void testInitEnvironmentCreatesAllDirsAndConfig(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");

        Map<String, Path> env = FeagiConfig.initEnvironment(paths);

        // All directories should exist
        assertTrue(Files.isDirectory(paths.configDir));
        assertTrue(Files.isDirectory(paths.logsDir));
        assertTrue(Files.isDirectory(paths.cacheDir));
        assertTrue(Files.isDirectory(paths.genomesDir));
        assertTrue(Files.isDirectory(paths.connectomesDir));

        // Config file should exist
        assertTrue(Files.exists(env.get("config_file")));

        // Map should contain all expected keys
        assertEquals(6, env.size());
        assertNotNull(env.get("config_dir"));
        assertNotNull(env.get("config_file"));
        assertNotNull(env.get("logs_dir"));
        assertNotNull(env.get("cache_dir"));
        assertNotNull(env.get("genomes_dir"));
        assertNotNull(env.get("connectomes_dir"));
    }

    @Test
    void testInitEnvironmentIsIdempotent(@TempDir Path home) throws IOException {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        FeagiConfig.initEnvironment(paths);
        assertDoesNotThrow(() -> FeagiConfig.initEnvironment(paths));
    }

    // ------------------------------------------------------------------
    // DEFAULT_CONFIG_CONTENT validation
    // ------------------------------------------------------------------

    @Test
    void testDefaultConfigContentContainsAllSections() {
        String content = FeagiConfig.DEFAULT_CONFIG_CONTENT;
        assertTrue(content.contains("[api]"));
        assertTrue(content.contains("[websocket]"));
        assertTrue(content.contains("[burst_engine]"));
        assertTrue(content.contains("[performance]"));
        assertTrue(content.contains("[logging]"));
        assertTrue(content.contains("[timeouts]"));
        assertTrue(content.contains("[connectome]"));
    }

    @Test
    void testDefaultConfigContentContainsKeyDefaults() {
        String content = FeagiConfig.DEFAULT_CONFIG_CONTENT;
        assertTrue(content.contains("host = \"127.0.0.1\""));
        assertTrue(content.contains("port = 8000"));
        assertTrue(content.contains("service_startup = 3.0"));
    }

    // ------------------------------------------------------------------
    // Null safety
    // ------------------------------------------------------------------

    @Test
    void testGenerateDefaultConfigNullPathsThrows() {
        assertThrows(NullPointerException.class, () ->
                FeagiConfig.generateDefaultConfig(null, null, false));
    }

    @Test
    void testEnsureDefaultConfigNullPathsThrows() {
        assertThrows(NullPointerException.class, () ->
                FeagiConfig.ensureDefaultConfig(null));
    }

    @Test
    void testInitEnvironmentNullPathsThrows() {
        assertThrows(NullPointerException.class, () ->
                FeagiConfig.initEnvironment(null));
    }
}
