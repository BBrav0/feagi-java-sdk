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

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigShowCommandTest {

    // ------------------------------------------------------------------
    // Config show — reads default config content
    // ------------------------------------------------------------------

    @Test
    void testDefaultConfigContentIsValid(@TempDir Path home) throws Exception {
        FeagiPaths paths = new FeagiPaths(home, "Linux");
        Path config = FeagiConfig.generateDefaultConfig(paths, null, false);

        String content = Files.readString(config);
        assertTrue(content.contains("[api]"));
        assertTrue(content.contains("host = \"127.0.0.1\""));
        assertTrue(content.contains("port = 8000"));
        assertTrue(content.contains("[websocket]"));
        assertTrue(content.contains("[timeouts]"));
    }

    // ------------------------------------------------------------------
    // CLI registration
    // ------------------------------------------------------------------

    @Test
    void testConfigShowCommandRegistered() {
        CommandLine cli = new CommandLine(new FeagiCli());
        CommandLine config = cli.getSubcommands().get("config");
        assertNotNull(config);
        assertNotNull(config.getSubcommands().get("show"));
    }

    @Test
    void testConfigShowUsageContainsConfigOption() {
        CommandLine cli = new CommandLine(new FeagiCli());
        CommandLine config = cli.getSubcommands().get("config");
        String usage = config.getSubcommands().get("show").getUsageMessage();
        assertTrue(usage.contains("--config"));
    }
}
