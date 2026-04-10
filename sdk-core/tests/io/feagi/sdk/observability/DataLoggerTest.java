/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DataLogger}.
 */
@DisplayName("DataLogger Tests")
class DataLoggerTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("should create DataLogger with default settings")
        void shouldCreateDataLoggerWithDefaultSettings() throws IOException {
            Path tempFile = Files.createTempFile("test_logger", ".jsonl");

            DataLogger logger = new DataLogger.Builder()
                .outputFile(tempFile.toString())
                .build();

            assertNotNull(logger);
            assertTrue(logger.isEnabled());

            logger.close();
            Files.deleteIfExists(tempFile);
        }

        @Test
        @DisplayName("should create DataLogger with custom settings")
        void shouldCreateDataLoggerWithCustomSettings() throws IOException {
            Path tempFile = Files.createTempFile("test_logger", ".csv");

            DataLogger logger = new DataLogger.Builder()
                .outputFile(tempFile.toString())
                .format(DataLogger.Format.CSV)
                .logInputs(true)
                .logOutputs(false)
                .sampleRate(0.5)
                .includeDataSamples(true)
                .maxSampleSize(20)
                .build();

            assertNotNull(logger);
            assertTrue(logger.isEnabled());

            logger.close();
            Files.deleteIfExists(tempFile);
        }

        @Test
        @DisplayName("should clamp sample rate to valid range")
        void shouldClampSampleRateToValidRange() throws IOException {
            Path tempFile = Files.createTempFile("test_logger", ".jsonl");

            // Test negative sample rate (should be clamped to 0)
            DataLogger logger1 = new DataLogger.Builder()
                .outputFile(tempFile.toString())
                .sampleRate(-0.5)
                .build();
            assertNotNull(logger1);
            logger1.close();

            // Test sample rate > 1 (should be clamped to 1)
            DataLogger logger2 = new DataLogger.Builder()
                .outputFile(tempFile.toString())
                .sampleRate(1.5)
                .build();
            assertNotNull(logger2);
            logger2.close();

            Files.deleteIfExists(tempFile);
        }
    }

    @Nested
    @DisplayName("JSONL Format Tests")
    class JsonlFormatTests {

        @Test
        @DisplayName("should log sensory input to JSONL file")
        void shouldLogSensoryInputToJsonlFile() throws IOException {
            Path tempFile = Files.createTempFile("test_logger", ".jsonl");

            DataLogger logger = new DataLogger.Builder()
                .outputFile(tempFile.toString())
                .format(DataLogger.Format.JSONL)
                .logInputs(true)
                .logOutputs(false)
                .sampleRate(1.0)
                .build();

            Map<String, Object> sendData = new HashMap<>();
            sendData.put("neuron_count", 100);
            sendData.put("packet_size_bytes", 512);
            sendData.put("duration_ms", 10.5);
            sendData.put("cortical_areas", Arrays.asList("iic100", "iic200"));

            logger.onSendComplete(sendData);
            logger.close();

            // Verify file content
            String content = Files.readString(tempFile);
            assertNotNull(content);
            assertTrue(content.contains("\"type\":\"sensory_input\""));
            assertTrue(content.contains("\"neuron_count\":100"));
            assertTrue(content.contains("\"packet_size_bytes\":512"));
            assertTrue(content.contains("\"duration_ms\":10.5"));

            Files.deleteIfExists(tempFile);
        }

        @Test
        @DisplayName("should log motor output to JSONL file")
        void shouldLogMotorOutputToJsonlFile() throws IOException {
            Path tempFile = Files.createTempFile("test_logger", ".jsonl");

            DataLogger logger = new DataLogger.Builder()
                .outputFile(tempFile.toString())
                .format(DataLogger.Format.JSONL)
                .logInputs(false)
                .logOutputs(true)
                .sampleRate(1.0)
                .build();

            Map<String, Object> receiveData = new HashMap<>();
            receiveData.put("command_count", 5);
            receiveData.put("duration_ms", 2.5);

            logger.onReceiveComplete(receiveData);
            logger.close();

            // Verify file content
            String content = Files.readString(tempFile);
            assertNotNull(content);
            assertTrue(content.contains("\"type\":\"motor_output\""));
            assertTrue(content.contains("\"command_count\":5"));
            assertTrue(content.contains("\"duration_ms\":2.5"));

            Files.deleteIfExists(tempFile);
        }

        @Test
        @DisplayName("should include data samples when requested")
        void shouldIncludeDataSamplesWhenRequested() throws IOException {
            Path tempFile = Files.createTempFile("test_logger", ".jsonl");

            DataLogger logger = new DataLogger.Builder()
                .outputFile(tempFile.toString())
                .format(DataLogger.Format.JSONL)
                .logInputs(true)
                .sampleRate(1.0)
                .includeDataSamples(true)
                .maxSampleSize(3)
                .build();

            Map<String, Object> sendData = new HashMap<>();
            sendData.put("neuron_count", 50);
            sendData.put("packet_size_bytes", 256);
            sendData.put("duration_ms", 5.0);
            sendData.put("data_sample", Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0));

            logger.onSendComplete(sendData);
            logger.close();

            // Verify data sample is truncated to maxSampleSize
            String content = Files.readString(tempFile);
            assertTrue(content.contains("\"data_sample\""));

            Files.deleteIfExists(tempFile);
        }
    }

    @Nested
    @DisplayName("CSV Format Tests")
    class CsvFormatTests {

        @Test
        @DisplayName("should log sensory input to CSV file")
        void shouldLogSensoryInputToCsvFile() throws IOException {
            Path tempFile = Files.createTempFile("test_logger", ".csv");

            DataLogger logger = new DataLogger.Builder()
                .outputFile(tempFile.toString())
                .format(DataLogger.Format.CSV)
                .logInputs(true)
                .logOutputs(false)
                .sampleRate(1.0)
                .build();

            Map<String, Object> sendData = new HashMap<>();
            sendData.put("neuron_count", 100);
            sendData.put("packet_size_bytes", 512);
            sendData.put("duration_ms", 10.5);
            sendData.put("cortical_areas", Arrays.asList("iic100", "iic200"));

            logger.onSendComplete(sendData);
            logger.close();

            // Verify file has header and data
            List<String> lines = Files.readAllLines(tempFile);
            assertTrue(lines.size() >= 2, "Should have header and at least one data row");
            assertTrue(lines.get(0).contains("timestamp,type,cortical_areas"));

            Files.deleteIfExists(tempFile);
        }

        @Test
        @DisplayName("should log motor output to CSV file")
        void shouldLogMotorOutputToCsvFile() throws IOException {
            Path tempFile = Files.createTempFile("test_logger", ".csv");

            DataLogger logger = new DataLogger.Builder()
                .outputFile(tempFile.toString())
                .format(DataLogger.Format.CSV)
                .logInputs(false)
                .logOutputs(true)
                .sampleRate(1.0)
                .build();

            Map<String, Object> receiveData = new HashMap<>();
            receiveData.put("command_count", 5);
            receiveData.put("duration_ms", 2.5);

            logger.onReceiveComplete(receiveData);
            logger.close();

            // Verify file has header and data
            List<String> lines = Files.readAllLines(tempFile);
            assertTrue(lines.size() >= 2, "Should have header and at least one data row");

            Files.deleteIfExists(tempFile);
        }
    }

    @Nested
    @DisplayName("JSON Format Tests")
    class JsonFormatTests {

        @Test
        @DisplayName("should log entries to JSON array file")
        void shouldLogEntriesToJsonArrayFile() throws IOException {
            Path tempFile = Files.createTempFile("test_logger", ".json");

            DataLogger logger = new DataLogger.Builder()
                .outputFile(tempFile.toString())
                .format(DataLogger.Format.JSON)
                .logInputs(true)
                .sampleRate(1.0)
                .build();

            Map<String, Object> sendData1 = new HashMap<>();
            sendData1.put("neuron_count", 100);
            sendData1.put("packet_size_bytes", 512);
            sendData1.put("duration_ms", 10.5);

            Map<String, Object> sendData2 = new HashMap<>();
            sendData2.put("neuron_count", 200);
            sendData2.put("packet_size_bytes", 1024);
            sendData2.put("duration_ms", 15.0);

            logger.onSendComplete(sendData1);
            logger.onSendComplete(sendData2);
            logger.close();

            // Verify JSON array format
            String content = Files.readString(tempFile);
            assertTrue(content.contains("["), "Content should contain opening bracket");
            assertTrue(content.contains("]"), "Content should contain closing bracket");
            assertTrue(content.contains("\"neuron_count\":100"));
            assertTrue(content.contains("\"neuron_count\":200"));

            Files.deleteIfExists(tempFile);
        }
    }

    @Nested
    @DisplayName("Filtering Tests")
    class FilteringTests {

        @Test
        @DisplayName("should not log inputs when disabled")
        void shouldNotLogInputsWhenDisabled() throws IOException {
            Path tempFile = Files.createTempFile("test_logger", ".jsonl");

            DataLogger logger = new DataLogger.Builder()
                .outputFile(tempFile.toString())
                .logInputs(false)
                .logOutputs(true)
                .sampleRate(1.0)
                .build();

            Map<String, Object> sendData = new HashMap<>();
            sendData.put("neuron_count", 100);
            sendData.put("packet_size_bytes", 512);
            sendData.put("duration_ms", 10.5);

            logger.onSendComplete(sendData);
            logger.close();

            // Verify file is empty or doesn't contain sensory_input
            String content = Files.readString(tempFile);
            assertFalse(content.contains("sensory_input"));

            Files.deleteIfExists(tempFile);
        }

        @Test
        @DisplayName("should not log outputs when disabled")
        void shouldNotLogOutputsWhenDisabled() throws IOException {
            Path tempFile = Files.createTempFile("test_logger", ".jsonl");

            DataLogger logger = new DataLogger.Builder()
                .outputFile(tempFile.toString())
                .logInputs(true)
                .logOutputs(false)
                .sampleRate(1.0)
                .build();

            Map<String, Object> receiveData = new HashMap<>();
            receiveData.put("command_count", 5);
            receiveData.put("duration_ms", 2.5);

            logger.onReceiveComplete(receiveData);
            logger.close();

            // Verify file is empty or doesn't contain motor_output
            String content = Files.readString(tempFile);
            assertFalse(content.contains("motor_output"));

            Files.deleteIfExists(tempFile);
        }

        @Test
        @DisplayName("should respect sample rate")
        void shouldRespectSampleRate() throws IOException {
            Path tempFile = Files.createTempFile("test_logger", ".jsonl");

            // Use 0% sample rate - should log nothing
            DataLogger logger = new DataLogger.Builder()
                .outputFile(tempFile.toString())
                .logInputs(true)
                .logOutputs(true)
                .sampleRate(0.0)
                .build();

            // Send many packets
            for (int i = 0; i < 100; i++) {
                Map<String, Object> sendData = new HashMap<>();
                sendData.put("neuron_count", 100);
                sendData.put("packet_size_bytes", 512);
                sendData.put("duration_ms", 10.5);
                logger.onSendComplete(sendData);
            }
            logger.close();

            // With 0% sample rate, should have no entries
            String content = Files.readString(tempFile);
            assertTrue(content.isEmpty(), "Should have no entries with 0% sample rate");

            Files.deleteIfExists(tempFile);
        }
    }

    @Nested
    @DisplayName("Packet Counter Tests")
    class PacketCounterTests {

        @Test
        @DisplayName("should track packet count")
        void shouldTrackPacketCount() throws IOException {
            Path tempFile = Files.createTempFile("test_logger", ".jsonl");

            DataLogger logger = new DataLogger.Builder()
                .outputFile(tempFile.toString())
                .sampleRate(1.0)
                .build();

            assertEquals(0, logger.getPacketCount());

            Map<String, Object> sendData = new HashMap<>();
            sendData.put("neuron_count", 100);
            sendData.put("packet_size_bytes", 512);
            sendData.put("duration_ms", 10.5);

            logger.onSendComplete(sendData);
            assertEquals(1, logger.getPacketCount());

            logger.onSendComplete(sendData);
            assertEquals(2, logger.getPacketCount());

            Map<String, Object> receiveData = new HashMap<>();
            receiveData.put("command_count", 5);
            receiveData.put("duration_ms", 2.5);

            logger.onReceiveComplete(receiveData);
            assertEquals(3, logger.getPacketCount());

            logger.close();
            Files.deleteIfExists(tempFile);
        }
    }

    @Nested
    @DisplayName("Enabled/Disabled Tests")
    class EnabledDisabledTests {

        @Test
        @DisplayName("should not log when disabled")
        void shouldNotLogWhenDisabled() throws IOException {
            Path tempFile = Files.createTempFile("test_logger", ".jsonl");

            DataLogger logger = new DataLogger.Builder()
                .outputFile(tempFile.toString())
                .enabled(false)
                .sampleRate(1.0)
                .build();

            assertFalse(logger.isEnabled());

            Map<String, Object> sendData = new HashMap<>();
            sendData.put("neuron_count", 100);
            sendData.put("packet_size_bytes", 512);
            sendData.put("duration_ms", 10.5);

            logger.onSendComplete(sendData);
            logger.close();

            // Verify file is empty
            String content = Files.readString(tempFile);
            assertTrue(content.isEmpty());

            Files.deleteIfExists(tempFile);
        }
    }
}
