/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

class FeagiCliTest {

    private CommandLine newCli() {
        CommandLine cli = new CommandLine(new FeagiCli());
        FeagiCli.applyDirectoryFooter(cli);
        return cli;
    }

    // ------------------------------------------------------------------
    // Version / help flags
    // ------------------------------------------------------------------

    @Test
    void testVersionFlag() {
        CommandLine cli = newCli();
        StringWriter out = new StringWriter();
        cli.setOut(new PrintWriter(out));

        int exitCode = cli.execute("--version");

        assertEquals(0, exitCode);
        assertTrue(out.toString().contains("FEAGI CLI v0.0.1-beta.0"));
    }

    @Test
    void testHelpFlag() {
        CommandLine cli = newCli();
        StringWriter out = new StringWriter();
        cli.setOut(new PrintWriter(out));

        int exitCode = cli.execute("--help");

        assertEquals(0, exitCode);
        String output = out.toString();
        assertTrue(output.contains("feagi"), "Should contain command name");
        assertTrue(output.contains("start"), "Should list start subcommand");
        assertTrue(output.contains("stop"), "Should list stop subcommand");
        assertTrue(output.contains("status"), "Should list status subcommand");
        assertTrue(output.contains("restart"), "Should list restart subcommand");
        assertTrue(output.contains("init"), "Should list init subcommand");
        assertTrue(output.contains("config"), "Should list config subcommand");
        assertTrue(output.contains("bv"), "Should list bv subcommand");

        // Directory epilog (mirrors Python SDK's argparse epilog)
        assertTrue(output.contains("FEAGI Directories:"), "Should show directories header");
        assertTrue(output.contains("  Config:      "), "Should show Config directory");
        assertTrue(output.contains("  Logs:        "), "Should show Logs directory");
        assertTrue(output.contains("  Cache:       "), "Should show Cache directory");
        assertTrue(output.contains("  Genomes:     "), "Should show Genomes directory");
        assertTrue(output.contains("  Connectomes: "), "Should show Connectomes directory");
    }

    // ------------------------------------------------------------------
    // Subcommand registration
    // ------------------------------------------------------------------

    @Test
    void testStartSubcommandRegistered() {
        CommandLine cli = newCli();
        assertNotNull(cli.getSubcommands().get("start"));
    }

    @Test
    void testStopSubcommandRegistered() {
        CommandLine cli = newCli();
        assertNotNull(cli.getSubcommands().get("stop"));
    }

    @Test
    void testStatusSubcommandRegistered() {
        CommandLine cli = newCli();
        assertNotNull(cli.getSubcommands().get("status"));
    }

    @Test
    void testRestartSubcommandRegistered() {
        CommandLine cli = newCli();
        assertNotNull(cli.getSubcommands().get("restart"));
    }

    @Test
    void testInitSubcommandRegistered() {
        CommandLine cli = newCli();
        assertNotNull(cli.getSubcommands().get("init"));
    }

    @Test
    void testConfigSubcommandRegistered() {
        CommandLine cli = newCli();
        assertNotNull(cli.getSubcommands().get("config"));
    }

    @Test
    void testBvSubcommandRegistered() {
        CommandLine cli = newCli();
        assertNotNull(cli.getSubcommands().get("bv"));
    }

    // ------------------------------------------------------------------
    // BV nested subcommands
    // ------------------------------------------------------------------

    @Test
    void testBvSubcommandsRegistered() {
        CommandLine cli = newCli();
        CommandLine bv = cli.getSubcommands().get("bv");
        assertNotNull(bv);

        assertNotNull(bv.getSubcommands().get("start"), "bv start");
        assertNotNull(bv.getSubcommands().get("stop"), "bv stop");
        assertNotNull(bv.getSubcommands().get("status"), "bv status");
        assertNotNull(bv.getSubcommands().get("restart"), "bv restart");
    }

    // ------------------------------------------------------------------
    // Config nested subcommands
    // ------------------------------------------------------------------

    @Test
    void testConfigShowSubcommandRegistered() {
        CommandLine cli = newCli();
        CommandLine config = cli.getSubcommands().get("config");
        assertNotNull(config);
        assertNotNull(config.getSubcommands().get("show"), "config show");
    }

    // ------------------------------------------------------------------
    // Usage help for subcommands (via getUsageMessage)
    // ------------------------------------------------------------------

    @Test
    void testStartUsageContainsOptions() {
        CommandLine cli = newCli();
        String usage = cli.getSubcommands().get("start").getUsageMessage();
        assertTrue(usage.contains("--config"), "Should show --config option");
        assertTrue(usage.contains("--genome"), "Should show --genome option");
        assertTrue(usage.contains("--connectome"), "Should show --connectome option");
        assertTrue(usage.contains("--wait"), "Should show --wait option");
        assertTrue(usage.contains("--timeout"), "Should show --timeout option");
    }

    @Test
    void testInitUsageContainsOptions() {
        CommandLine cli = newCli();
        String usage = cli.getSubcommands().get("init").getUsageMessage();
        assertTrue(usage.contains("--config-only"), "Should show --config-only option");
        assertTrue(usage.contains("--force"), "Should show --force option");
        assertTrue(usage.contains("--output"), "Should show --output option");
    }

    @Test
    void testBvStartUsageContainsOptions() {
        CommandLine cli = newCli();
        CommandLine bv = cli.getSubcommands().get("bv");
        String usage = bv.getSubcommands().get("start").getUsageMessage();
        assertTrue(usage.contains("--config"), "Should show --config option");
    }

    @Test
    void testStopUsageContainsTimeoutOption() {
        CommandLine cli = newCli();
        String usage = cli.getSubcommands().get("stop").getUsageMessage();
        assertTrue(usage.contains("--timeout"), "Should show --timeout option");
    }

    // ------------------------------------------------------------------
    // No-args invocation (exercises call() code path)
    // ------------------------------------------------------------------

    @Test
    void testNoArgsShowsUsageWithFooter() {
        CommandLine cli = newCli();
        StringWriter out = new StringWriter();
        cli.setOut(new PrintWriter(out));

        int exitCode = cli.execute();

        assertEquals(0, exitCode);
        assertTrue(out.toString().contains("FEAGI Directories:"),
                "No-args should show footer via call()");
    }

    // ------------------------------------------------------------------
    // Footer failure path
    // ------------------------------------------------------------------

    @Test
    void testHelpFlagWithoutDirectoryFooter() {
        CommandLine cli = new CommandLine(new FeagiCli());
        StringWriter out = new StringWriter();
        cli.setOut(new PrintWriter(out));

        int exitCode = cli.execute("--help");

        assertEquals(0, exitCode);
        String output = out.toString();
        assertTrue(output.contains("feagi"), "Should contain command name");
        assertFalse(output.contains("FEAGI Directories:"),
                "Should not show directories when footer not applied");
    }
}
