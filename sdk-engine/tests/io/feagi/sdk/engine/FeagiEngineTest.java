/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link FeagiEngine}.
 *
 * <p>These tests exercise the builder, command construction, and basic lifecycle
 * without requiring a live FEAGI instance. Process lifecycle tests use small
 * platform-specific scripts as stand-in executables.
 */
class FeagiEngineTest {

    // ------------------------------------------------------------------
    // Builder validation
    // ------------------------------------------------------------------

    /**
     * Explicit feagiPath pointing to a non-existent binary should throw.
     */
    @Test
    void testBuilderThrowsForUnusableFeagiPath() {
        Path missing = Path.of("/nonexistent/feagi-binary-" + System.nanoTime());
        assertThrows(IllegalArgumentException.class, () ->
                FeagiEngine.builder().feagiPath(missing).build());
    }

    /**
     * Explicit feagiPath that does not exist should throw.
     */
    @Test
    void testBuilderThrowsForMissingFeagiPath(@TempDir Path tmp) {
        Path missing = tmp.resolve("no-such-feagi");
        assertThrows(IllegalArgumentException.class, () ->
                FeagiEngine.builder().feagiPath(missing).build());
    }

    /**
     * Config path that does not exist should throw.
     */
    @Test
    void testBuilderThrowsForMissingConfig(@TempDir Path tmp) throws IOException {
        Path binary = createFakeBinary(tmp);
        Path missingConfig = tmp.resolve("no-such-config.toml");
        assertThrows(IllegalArgumentException.class, () ->
                FeagiEngine.builder()
                        .feagiPath(binary)
                        .config(missingConfig)
                        .build());
    }

    /**
     * Genome path that does not exist should throw.
     */
    @Test
    void testBuilderThrowsForMissingGenome(@TempDir Path tmp) throws IOException {
        Path binary = createFakeBinary(tmp);
        Path missingGenome = tmp.resolve("no-such-genome.json");
        assertThrows(IllegalArgumentException.class, () ->
                FeagiEngine.builder()
                        .feagiPath(binary)
                        .genome(missingGenome)
                        .build());
    }

    /**
     * Connectome path that does not exist should throw.
     */
    @Test
    void testBuilderThrowsForMissingConnectome(@TempDir Path tmp) throws IOException {
        Path binary = createFakeBinary(tmp);
        Path missingConnectome = tmp.resolve("no-such.connectome");
        assertThrows(IllegalArgumentException.class, () ->
                FeagiEngine.builder()
                        .feagiPath(binary)
                        .connectome(missingConnectome)
                        .build());
    }

    /**
     * Default host and port should be "localhost" and 8000.
     */
    @Test
    void testBuilderDefaultHostAndPort(@TempDir Path tmp) throws IOException {
        Path binary = createFakeBinary(tmp);
        FeagiEngine engine = FeagiEngine.builder().feagiPath(binary).build();
        assertEquals("localhost", engine.host());
        assertEquals(8000, engine.restPort());
    }

    /**
     * Custom host and port should be preserved.
     */
    @Test
    void testBuilderCustomHostAndPort(@TempDir Path tmp) throws IOException {
        Path binary = createFakeBinary(tmp);
        FeagiEngine engine = FeagiEngine.builder()
                .feagiPath(binary)
                .host("192.168.1.100")
                .restPort(9000)
                .build();
        assertEquals("192.168.1.100", engine.host());
        assertEquals(9000, engine.restPort());
    }

    /**
     * Invalid port numbers should throw.
     */
    @Test
    void testBuilderInvalidPort() {
        assertThrows(IllegalArgumentException.class, () ->
                FeagiEngine.builder().restPort(0));
        assertThrows(IllegalArgumentException.class, () ->
                FeagiEngine.builder().restPort(65536));
        assertThrows(IllegalArgumentException.class, () ->
                FeagiEngine.builder().restPort(-1));
    }

    /**
     * Setting genome after connectome should clear connectome (last-one-wins).
     */
    @Test
    void testGenomeClearsConnectome(@TempDir Path tmp) throws IOException {
        Path binary = createFakeBinary(tmp);
        Path genome = createFakeFile(tmp, "genome.json");
        Path connectome = createFakeFile(tmp, "brain.connectome");

        FeagiEngine engine = FeagiEngine.builder()
                .feagiPath(binary)
                .connectome(connectome)
                .genome(genome)
                .build();

        // Genome should be in command; connectome should not.
        List<String> cmd = engine.buildCommand();
        assertTrue(cmd.contains("--genome"), "Command should include --genome");
        assertFalse(cmd.contains("--connectome"), "Command should not include --connectome");
    }

    /**
     * Setting connectome after genome should clear genome (last-one-wins).
     */
    @Test
    void testConnectomeClearsGenome(@TempDir Path tmp) throws IOException {
        Path binary = createFakeBinary(tmp);
        Path genome = createFakeFile(tmp, "genome.json");
        Path connectome = createFakeFile(tmp, "brain.connectome");

        FeagiEngine engine = FeagiEngine.builder()
                .feagiPath(binary)
                .genome(genome)
                .connectome(connectome)
                .build();

        List<String> cmd = engine.buildCommand();
        assertTrue(cmd.contains("--connectome"), "Command should include --connectome");
        assertFalse(cmd.contains("--genome"), "Command should not include --genome");
    }

    /**
     * feagiPath() accessor should return the resolved path.
     */
    @Test
    void testFeagiPathAccessor(@TempDir Path tmp) throws IOException {
        Path binary = createFakeBinary(tmp);
        FeagiEngine engine = FeagiEngine.builder().feagiPath(binary).build();
        assertEquals(binary, engine.feagiPath());
    }

    // ------------------------------------------------------------------
    // Command construction
    // ------------------------------------------------------------------

    /**
     * Command with no config/genome/connectome should be just the binary path.
     */
    @Test
    void testCommandBareMinimum(@TempDir Path tmp) throws IOException {
        Path binary = createFakeBinary(tmp);
        FeagiEngine engine = FeagiEngine.builder().feagiPath(binary).build();

        List<String> cmd = engine.buildCommand();
        assertEquals(List.of(binary.toString()), cmd);
    }

    /**
     * Command with config should include --config flag.
     */
    @Test
    void testCommandWithConfig(@TempDir Path tmp) throws IOException {
        Path binary = createFakeBinary(tmp);
        Path config = createFakeFile(tmp, "feagi.toml");

        FeagiEngine engine = FeagiEngine.builder()
                .feagiPath(binary)
                .config(config)
                .build();

        List<String> cmd = engine.buildCommand();
        assertEquals(List.of(binary.toString(), "--config", config.toString()), cmd);
    }

    /**
     * Command with config + genome should include both flags.
     */
    @Test
    void testCommandWithConfigAndGenome(@TempDir Path tmp) throws IOException {
        Path binary = createFakeBinary(tmp);
        Path config = createFakeFile(tmp, "feagi.toml");
        Path genome = createFakeFile(tmp, "genome.json");

        FeagiEngine engine = FeagiEngine.builder()
                .feagiPath(binary)
                .config(config)
                .genome(genome)
                .build();

        List<String> cmd = engine.buildCommand();
        assertEquals(List.of(
                binary.toString(),
                "--config", config.toString(),
                "--genome", genome.toString()
        ), cmd);
    }

    /**
     * Command with config + connectome should include both flags.
     */
    @Test
    void testCommandWithConfigAndConnectome(@TempDir Path tmp) throws IOException {
        Path binary = createFakeBinary(tmp);
        Path config = createFakeFile(tmp, "feagi.toml");
        Path connectome = createFakeFile(tmp, "brain.connectome");

        FeagiEngine engine = FeagiEngine.builder()
                .feagiPath(binary)
                .config(config)
                .connectome(connectome)
                .build();

        List<String> cmd = engine.buildCommand();
        assertEquals(List.of(
                binary.toString(),
                "--config", config.toString(),
                "--connectome", connectome.toString()
        ), cmd);
    }

    // ------------------------------------------------------------------
    // Process lifecycle
    // ------------------------------------------------------------------

    /**
     * Starting a process that exits immediately — isRunning() should return false.
     */
    @Test
    void testProcessExitsImmediately(@TempDir Path tmp) throws Exception {
        Path script = createExitScript(tmp, 0);

        FeagiEngine engine = FeagiEngine.builder()
                .feagiPath(script)
                .build();

        // Start without waiting for ready (no health check endpoint).
        engine.start(false, Duration.ofSeconds(5));

        // Wait for the process to finish (it exits immediately).
        engine.processForTesting().waitFor(5, TimeUnit.SECONDS);

        assertFalse(engine.isRunning(), "Process should have exited");
    }

    /**
     * Starting a sleeping process then stopping it should work.
     */
    @Test
    void testStopRunningProcess(@TempDir Path tmp) throws Exception {
        Path script = createSleepScript(tmp, 60);

        FeagiEngine engine = FeagiEngine.builder()
                .feagiPath(script)
                .build();

        engine.start(false, Duration.ofSeconds(5));
        assertTrue(engine.isRunning(), "Process should be running");

        boolean stopped = engine.stop(Duration.ofSeconds(5));
        assertTrue(stopped, "stop() should return true");
        assertFalse(engine.isRunning(), "Process should be stopped");
    }

    /**
     * start() when already running should return true (idempotent).
     */
    @Test
    void testStartWhenAlreadyRunning(@TempDir Path tmp) throws Exception {
        Path script = createSleepScript(tmp, 60);

        FeagiEngine engine = FeagiEngine.builder()
                .feagiPath(script)
                .build();

        try {
            engine.start(false, Duration.ofSeconds(5));
            assertTrue(engine.isRunning());

            // Second start should be idempotent.
            boolean result = engine.start(false, Duration.ofSeconds(5));
            assertTrue(result, "start() on running engine should return true");
        } finally {
            engine.stop();
        }
    }

    /**
     * stop() when not running should return true (idempotent).
     */
    @Test
    void testStopWhenNotRunning(@TempDir Path tmp) throws IOException {
        Path binary = createFakeBinary(tmp);
        FeagiEngine engine = FeagiEngine.builder().feagiPath(binary).build();

        boolean result = engine.stop();
        assertTrue(result, "stop() on non-running engine should return true");
    }

    /**
     * isRunning() on a never-started engine should return false.
     */
    @Test
    void testIsRunningBeforeStart(@TempDir Path tmp) throws IOException {
        Path binary = createFakeBinary(tmp);
        FeagiEngine engine = FeagiEngine.builder().feagiPath(binary).build();
        assertFalse(engine.isRunning());
    }

    // ------------------------------------------------------------------
    // AutoCloseable
    // ------------------------------------------------------------------

    /**
     * try-with-resources should stop the engine on close.
     */
    @Test
    void testAutoCloseable(@TempDir Path tmp) throws Exception {
        Path script = createSleepScript(tmp, 60);
        Process processRef;

        try (FeagiEngine engine = FeagiEngine.builder()
                .feagiPath(script)
                .build()) {
            engine.start(false, Duration.ofSeconds(5));
            assertTrue(engine.isRunning());
            // Capture the process reference to check after close.
            processRef = engine.processForTesting();
        }

        // After close, the process should have been stopped.
        // processRef might be null (cleared by stop()) or not alive.
        assertTrue(processRef == null || !processRef.isAlive(),
                "Process should be stopped after close()");
    }

    /**
     * waitForReady should return false when process exits during health check.
     */
    @Test
    void testHealthCheckFailsWhenProcessDies(@TempDir Path tmp) throws Exception {
        // Script exits immediately with error code.
        Path script = createExitScript(tmp, 1);

        FeagiEngine engine = FeagiEngine.builder()
                .feagiPath(script)
                .build();

        // start with waitForReady — should return false because process dies.
        boolean result = engine.start(true, Duration.ofSeconds(3));
        assertFalse(result, "start() should return false when process dies during health check");
        assertFalse(engine.isRunning());
    }

    /**
     * waitForReady should return false when timeout expires with no health endpoint.
     */
    @Test
    void testHealthCheckTimesOut(@TempDir Path tmp) throws Exception {
        Path script = createSleepScript(tmp, 60);

        FeagiEngine engine = FeagiEngine.builder()
                .feagiPath(script)
                .restPort(19999) // Port with nothing listening.
                .build();

        try {
            // Short timeout to avoid slow tests.
            boolean result = engine.start(true, Duration.ofSeconds(2));
            assertFalse(result, "start() should return false on health check timeout");
            // stop() is called internally by start() on failure.
            assertFalse(engine.isRunning());
        } finally {
            engine.stop();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Create a fake binary (empty file with executable permission).
     * On Windows, uses a .bat file; on Unix, a shell script.
     */
    private static Path createFakeBinary(Path dir) throws IOException {
        if (FeagiDiscovery.isWindows()) {
            Path bat = dir.resolve("feagi.bat");
            Files.writeString(bat, "@echo off\r\n");
            return bat;
        } else {
            Path sh = dir.resolve("feagi");
            Files.writeString(sh, "#!/bin/sh\n");
            sh.toFile().setExecutable(true);
            return sh;
        }
    }

    /**
     * Create a script that exits with the given code.
     */
    private static Path createExitScript(Path dir, int exitCode) throws IOException {
        if (FeagiDiscovery.isWindows()) {
            Path bat = dir.resolve("feagi.bat");
            Files.writeString(bat, "@echo off\r\nexit /b " + exitCode + "\r\n");
            return bat;
        } else {
            Path sh = dir.resolve("feagi");
            Files.writeString(sh, "#!/bin/sh\nexit " + exitCode + "\n");
            sh.toFile().setExecutable(true);
            return sh;
        }
    }

    /**
     * Create a script that sleeps for the given number of seconds.
     */
    private static Path createSleepScript(Path dir, int seconds) throws IOException {
        if (FeagiDiscovery.isWindows()) {
            Path bat = dir.resolve("feagi.bat");
            // ping -n N localhost is a common Windows sleep substitute.
            Files.writeString(bat, "@echo off\r\nping -n " + (seconds + 1)
                    + " 127.0.0.1 > nul\r\n");
            return bat;
        } else {
            Path sh = dir.resolve("feagi");
            Files.writeString(sh, "#!/bin/sh\nsleep " + seconds + "\n");
            sh.toFile().setExecutable(true);
            return sh;
        }
    }

    /**
     * Create a fake file with the given name.
     */
    private static Path createFakeFile(Path dir, String name) throws IOException {
        Path file = dir.resolve(name);
        Files.createFile(file);
        return file;
    }

}
