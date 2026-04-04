/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JsonConfig}.
 */
@DisplayName("JsonConfig")
class JsonConfigTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("FeagiConfig Export/Load")
    class FeagiConfigExportLoadTests {

        @Test
        @DisplayName("should export and load config with default values")
        void shouldExportAndLoadConfigWithDefaultValues() throws IOException {
            FeagiConfig original = new FeagiConfig.Builder().build();
            Path jsonPath = tempDir.resolve("config.json");

            JsonConfig.exportConfig(original, jsonPath);
            FeagiConfig loaded = JsonConfig.loadConfig(jsonPath);

            assertEquals(original.getApiHost(), loaded.getApiHost());
            assertEquals(original.getApiPort(), loaded.getApiPort());
            assertEquals(original.getWebsocketHost(), loaded.getWebsocketHost());
            assertEquals(original.isWebsocketEnabled(), loaded.isWebsocketEnabled());
            assertEquals(original.getVisualizationPort(), loaded.getVisualizationPort());
            assertEquals(original.getSensoryPort(), loaded.getSensoryPort());
            assertEquals(original.getMotorPort(), loaded.getMotorPort());
            assertEquals(original.getBurstDuration(), loaded.getBurstDuration());
            assertEquals(original.getMaxBursts(), loaded.getMaxBursts());
            assertEquals(original.isGpuEnabled(), loaded.isGpuEnabled());
            assertEquals(original.getWorkerThreads(), loaded.getWorkerThreads());
            assertEquals(original.isProfilingEnabled(), loaded.isProfilingEnabled());
            assertEquals(original.getLogLevel(), loaded.getLogLevel());
            assertEquals(original.isFileLogging(), loaded.isFileLogging());
            assertEquals(original.getServiceStartupTimeout(), loaded.getServiceStartupTimeout());
            assertEquals(original.getNeuronSpace(), loaded.getNeuronSpace());
            assertEquals(original.getSynapseSpace(), loaded.getSynapseSpace());
        }

        @Test
        @DisplayName("should export and load config with custom values")
        void shouldExportAndLoadConfigWithCustomValues() throws IOException {
            FeagiConfig original = new FeagiConfig.Builder()
                .apiHost("192.168.1.100")
                .apiPort(9000)
                .websocketHost("0.0.0.0")
                .websocketEnabled(false)
                .visualizationPort(9090)
                .sensoryPort(6000)
                .motorPort(6001)
                .burstDuration(0.05)
                .maxBursts(100)
                .gpuEnabled(true)
                .workerThreads(8)
                .profilingEnabled(true)
                .logLevel("debug")
                .fileLogging(false)
                .serviceStartupTimeout(Duration.ofSeconds(10))
                .neuronSpace(500000)
                .synapseSpace(5000000)
                .build();

            Path jsonPath = tempDir.resolve("custom_config.json");
            JsonConfig.exportConfig(original, jsonPath);
            FeagiConfig loaded = JsonConfig.loadConfig(jsonPath);

            assertEquals(original.getApiHost(), loaded.getApiHost());
            assertEquals(original.getApiPort(), loaded.getApiPort());
            assertEquals(original.getWorkerThreads(), loaded.getWorkerThreads());
            assertEquals(original.getLogLevel(), loaded.getLogLevel());
        }

        @Test
        @DisplayName("should throw when file not found")
        void shouldThrowWhenFileNotFound() {
            Path nonExistentPath = tempDir.resolve("nonexistent.json");
            assertThrows(IOException.class, () -> JsonConfig.loadConfig(nonExistentPath));
        }

        @Test
        @DisplayName("should create parent directories if needed")
        void shouldCreateParentDirectories() throws IOException {
            FeagiConfig config = new FeagiConfig.Builder().build();
            Path jsonPath = tempDir.resolve("subdir/nested/config.json");

            JsonConfig.exportConfig(config, jsonPath);
            assertTrue(Files.exists(jsonPath));
        }
    }

    @Nested
    @DisplayName("JSON String Conversion")
    class JsonStringConversionTests {

        @Test
        @DisplayName("should serialize object to JSON string")
        void shouldSerializeObjectToJsonString() {
            FeagiConfig config = new FeagiConfig.Builder()
                .apiHost("localhost")
                .apiPort(8080)
                .build();

            String json = JsonConfig.toJson(config);

            assertNotNull(json);
            assertTrue(json.contains("localhost"));
            assertTrue(json.contains("8080"));
        }

        @Test
        @DisplayName("should deserialize JSON string to object")
        void shouldDeserializeJsonStringToObject() throws IOException {
            String json = """
                {
                    "api": {"host": "test", "port": 1234},
                    "websocket": {"host": "test", "enabled": true, "visualization_port": 8080, "sensory_port": 5558, "motor_port": 5564},
                    "burst_engine": {"burst_duration": 0.01, "max_bursts": 0, "gpu_enabled": false},
                    "performance": {"worker_threads": 4, "profiling_enabled": false},
                    "logging": {"level": "info", "file_logging": true},
                    "timeouts": {"service_startup": 3.0},
                    "connectome": {"neuron_space": 1000000, "synapse_space": 10000000}
                }
                """;

            Path jsonPath = tempDir.resolve("test_config.json");
            Files.writeString(jsonPath, json);
            FeagiConfig config = JsonConfig.loadConfig(jsonPath);

            assertEquals("test", config.getApiHost());
            assertEquals(1234, config.getApiPort());
        }
    }
}
