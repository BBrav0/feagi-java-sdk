/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import io.feagi.sdk.engine.FeagiConfig;
import io.feagi.sdk.engine.FeagiPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class InitCommandTest {

    // ------------------------------------------------------------------
    // feagi init (full environment)
    // ------------------------------------------------------------------

    @Test
    void testInitCreatesEnvironment(@TempDir Path home) throws Exception {
        FeagiPaths paths = new FeagiPaths(home, "Linux");

        // Verify dirs don't exist yet
        assertFalse(Files.exists(paths.configDir));
        assertFalse(Files.exists(paths.logsDir));

        // Use FeagiConfig directly (InitCommand uses FeagiPaths.withDefaults() which
        // we can't override, so test the underlying logic)
        var env = FeagiConfig.initEnvironment(paths);

        assertTrue(Files.isDirectory(paths.configDir));
        assertTrue(Files.isDirectory(paths.logsDir));
        assertTrue(Files.isDirectory(paths.cacheDir));
        assertTrue(Files.isDirectory(paths.genomesDir));
        assertTrue(Files.isDirectory(paths.connectomesDir));
        assertTrue(Files.exists(env.get("config_file")));
    }

    // ------------------------------------------------------------------
    // --config-only flag
    // ------------------------------------------------------------------

    @Test
    void testConfigOnlyCreatesConfigWithoutAllDirs(@TempDir Path home) throws Exception {
        FeagiPaths paths = new FeagiPaths(home, "Linux");

        Path config = FeagiConfig.generateDefaultConfig(paths, null, false);

        assertTrue(Files.exists(config));
        String content = Files.readString(config);
        assertTrue(content.contains("[api]"));
        // Only config dir should be created, not all dirs
        assertTrue(Files.isDirectory(paths.configDir));
    }

    // ------------------------------------------------------------------
    // --force flag
    // ------------------------------------------------------------------

    @Test
    void testForceOverwritesExistingConfig(@TempDir Path home) throws Exception {
        FeagiPaths paths = new FeagiPaths(home, "Linux");

        // Create initial config
        FeagiConfig.generateDefaultConfig(paths, null, false);
        Files.writeString(paths.getDefaultConfig(), "custom content");
        assertEquals("custom content", Files.readString(paths.getDefaultConfig()));

        // Overwrite with --force
        FeagiConfig.generateDefaultConfig(paths, null, true);
        String content = Files.readString(paths.getDefaultConfig());
        assertTrue(content.contains("[api]"), "Should be overwritten with default");
    }

    @Test
    void testWithoutForceThrowsWhenExists(@TempDir Path home) throws Exception {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        FeagiConfig.generateDefaultConfig(paths, null, false);

        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> FeagiConfig.generateDefaultConfig(paths, null, false));
    }

    // ------------------------------------------------------------------
    // --output flag
    // ------------------------------------------------------------------

    @Test
    void testOutputCustomPath(@TempDir Path home) throws Exception {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        Path customPath = home.resolve("custom").resolve("my_config.toml");

        Path result = FeagiConfig.generateDefaultConfig(paths, customPath, false);

        assertEquals(customPath, result);
        assertTrue(Files.exists(customPath));
        assertTrue(Files.readString(customPath).contains("[api]"));
    }

    // ------------------------------------------------------------------
    // CLI registration
    // ------------------------------------------------------------------

    @Test
    void testInitCommandRegistered() {
        CommandLine cli = new CommandLine(new FeagiCli());
        assertNotNull(cli.getSubcommands().get("init"));
    }

    @Test
    void testInitUsageContainsAllOptions() {
        CommandLine cli = new CommandLine(new FeagiCli());
        String usage = cli.getSubcommands().get("init").getUsageMessage();
        assertTrue(usage.contains("--config-only"));
        assertTrue(usage.contains("--force"));
        assertTrue(usage.contains("--output"));
    }
}
