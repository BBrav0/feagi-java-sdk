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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MetricsCollector}.
 */
@DisplayName("MetricsCollector Tests")
class MetricsCollectorTest {

    @Nested
    @DisplayName("Input Statistics Tests")
    class InputStatisticsTests {

        @Test
        @DisplayName("should collect input statistics")
        void shouldCollectInputStatistics() {
            MetricsCollector collector = new MetricsCollector(true);

            Map<String, Object> sendData = new HashMap<>();
            sendData.put("neuron_count", 100);
            sendData.put("packet_size_bytes", 512);
            sendData.put("duration_ms", 10.5);

            collector.onSendComplete(sendData);
            collector.onSendComplete(sendData);
            collector.onSendComplete(sendData);

            MetricsCollector.InputStatistics stats = collector.getInputStatistics();

            assertEquals(3, stats.getTotalPackets());
            assertEquals(100 * 3, stats.getTotalNeurons());
            assertEquals(512 * 3, stats.getTotalBytes());

            collector.stop();
        }

        @Test
        @DisplayName("should calculate average packet size")
        void shouldCalculateAveragePacketSize() {
            MetricsCollector collector = new MetricsCollector(true);

            Map<String, Object> sendData1 = new HashMap<>();
            sendData1.put("neuron_count", 100);
            sendData1.put("packet_size_bytes", 500);
            sendData1.put("duration_ms", 10.0);

            Map<String, Object> sendData2 = new HashMap<>();
            sendData2.put("neuron_count", 100);
            sendData2.put("packet_size_bytes", 700);
            sendData2.put("duration_ms", 10.0);

            collector.onSendComplete(sendData1);
            collector.onSendComplete(sendData2);

            MetricsCollector.InputStatistics stats = collector.getInputStatistics();

            assertEquals(600.0, stats.getAvgPacketSize());
            assertEquals(100.0, stats.getAvgNeuronsPerPacket());
            assertEquals(10.0, stats.getAvgDurationMs());

            collector.stop();
        }

        @Test
        @DisplayName("should handle zero packets gracefully")
        void shouldHandleZeroPacketsGracefully() {
            MetricsCollector collector = new MetricsCollector(true);

            MetricsCollector.InputStatistics stats = collector.getInputStatistics();

            assertEquals(0, stats.getTotalPackets());
            assertEquals(0.0, stats.getAvgPacketSize());
            assertEquals(0.0, stats.getAvgNeuronsPerPacket());
            assertEquals(0.0, stats.getDataRateMbps());
            assertEquals(0.0, stats.getPacketsPerSec());

            collector.stop();
        }

        @Test
        @DisplayName("should calculate data rate")
        void shouldCalculateDataRate() {
            MetricsCollector collector = new MetricsCollector(true);

            Map<String, Object> sendData = new HashMap<>();
            sendData.put("neuron_count", 100);
            sendData.put("packet_size_bytes", 1024 * 1024); // 1 MB
            sendData.put("duration_ms", 1000.0); // 1 second

            collector.onSendComplete(sendData);

            MetricsCollector.InputStatistics stats = collector.getInputStatistics();

            // Data rate should be approximately 1 MB/s
            assertTrue(stats.getDataRateMbps() > 0);

            collector.stop();
        }

        @Test
        @DisplayName("should convert to map")
        void shouldConvertToMap() {
            MetricsCollector collector = new MetricsCollector(true);

            Map<String, Object> sendData = new HashMap<>();
            sendData.put("neuron_count", 100);
            sendData.put("packet_size_bytes", 512);
            sendData.put("duration_ms", 10.5);

            collector.onSendComplete(sendData);

            MetricsCollector.InputStatistics stats = collector.getInputStatistics();
            Map<String, Object> map = stats.toMap();

            assertTrue(map.containsKey("total_packets"));
            assertTrue(map.containsKey("total_bytes"));
            assertTrue(map.containsKey("total_neurons"));
            assertTrue(map.containsKey("total_duration_ms"));
            assertTrue(map.containsKey("avg_packet_size"));
            assertTrue(map.containsKey("avg_neurons_per_packet"));
            assertTrue(map.containsKey("data_rate_mbps"));
            assertTrue(map.containsKey("packets_per_sec"));

            collector.stop();
        }
    }

    @Nested
    @DisplayName("Output Statistics Tests")
    class OutputStatisticsTests {

        @Test
        @DisplayName("should collect output statistics")
        void shouldCollectOutputStatistics() {
            MetricsCollector collector = new MetricsCollector(true);

            Map<String, Object> receiveData = new HashMap<>();
            receiveData.put("command_count", 5);
            receiveData.put("duration_ms", 2.5);

            collector.onReceiveComplete(receiveData);
            collector.onReceiveComplete(receiveData);
            collector.onReceiveComplete(receiveData);

            MetricsCollector.OutputStatistics stats = collector.getOutputStatistics();

            assertEquals(3, stats.getTotalReceives());
            assertEquals(5 * 3, stats.getTotalCommands());

            collector.stop();
        }

        @Test
        @DisplayName("should calculate average commands per receive")
        void shouldCalculateAverageCommandsPerReceive() {
            MetricsCollector collector = new MetricsCollector(true);

            Map<String, Object> receiveData1 = new HashMap<>();
            receiveData1.put("command_count", 4);
            receiveData1.put("duration_ms", 2.0);

            Map<String, Object> receiveData2 = new HashMap<>();
            receiveData2.put("command_count", 6);
            receiveData2.put("duration_ms", 3.0);

            collector.onReceiveComplete(receiveData1);
            collector.onReceiveComplete(receiveData2);

            MetricsCollector.OutputStatistics stats = collector.getOutputStatistics();

            assertEquals(5.0, stats.getAvgCommandsPerReceive());
            assertEquals(2.5, stats.getAvgLatencyMs());

            collector.stop();
        }

        @Test
        @DisplayName("should handle zero receives gracefully")
        void shouldHandleZeroReceivesGracefully() {
            MetricsCollector collector = new MetricsCollector(true);

            MetricsCollector.OutputStatistics stats = collector.getOutputStatistics();

            assertEquals(0, stats.getTotalReceives());
            assertEquals(0, stats.getTotalCommands());
            assertEquals(0.0, stats.getAvgCommandsPerReceive());
            assertEquals(0.0, stats.getAvgLatencyMs());
            assertEquals(0.0, stats.getCommandsPerSec());

            collector.stop();
        }

        @Test
        @DisplayName("should convert to map")
        void shouldConvertOutputToMap() {
            MetricsCollector collector = new MetricsCollector(true);

            Map<String, Object> receiveData = new HashMap<>();
            receiveData.put("command_count", 5);
            receiveData.put("duration_ms", 2.5);

            collector.onReceiveComplete(receiveData);

            MetricsCollector.OutputStatistics stats = collector.getOutputStatistics();
            Map<String, Object> map = stats.toMap();

            assertTrue(map.containsKey("total_commands"));
            assertTrue(map.containsKey("total_receives"));
            assertTrue(map.containsKey("total_duration_ms"));
            assertTrue(map.containsKey("avg_commands_per_receive"));
            assertTrue(map.containsKey("avg_latency_ms"));
            assertTrue(map.containsKey("commands_per_sec"));

            collector.stop();
        }
    }

    @Nested
    @DisplayName("Combined Statistics Tests")
    class CombinedStatisticsTests {

        @Test
        @DisplayName("should get combined statistics")
        void shouldGetCombinedStatistics() {
            MetricsCollector collector = new MetricsCollector(true);

            Map<String, Object> sendData = new HashMap<>();
            sendData.put("neuron_count", 100);
            sendData.put("packet_size_bytes", 512);
            sendData.put("duration_ms", 10.5);

            Map<String, Object> receiveData = new HashMap<>();
            receiveData.put("command_count", 5);
            receiveData.put("duration_ms", 2.5);

            collector.onSendComplete(sendData);
            collector.onReceiveComplete(receiveData);

            MetricsCollector.Statistics stats = collector.getStatistics();

            assertNotNull(stats.getInput());
            assertNotNull(stats.getOutput());
            assertNotNull(stats.getStartTime());
            assertTrue(stats.getUptimeSeconds() >= 0);

            collector.stop();
        }

        @Test
        @DisplayName("should convert to JSON")
        void shouldConvertToJson() {
            MetricsCollector collector = new MetricsCollector(true);

            Map<String, Object> sendData = new HashMap<>();
            sendData.put("neuron_count", 100);
            sendData.put("packet_size_bytes", 512);
            sendData.put("duration_ms", 10.5);

            collector.onSendComplete(sendData);

            MetricsCollector.Statistics stats = collector.getStatistics();
            String json = stats.toJson();

            assertNotNull(json);
            assertTrue(json.contains("\"input\""));
            assertTrue(json.contains("\"output\""));
            assertTrue(json.contains("\"start_time\""));

            collector.stop();
        }

        @Test
        @DisplayName("should convert to map")
        void shouldConvertStatisticsToMap() {
            MetricsCollector collector = new MetricsCollector(true);

            Map<String, Object> sendData = new HashMap<>();
            sendData.put("neuron_count", 100);
            sendData.put("packet_size_bytes", 512);
            sendData.put("duration_ms", 10.5);

            collector.onSendComplete(sendData);

            MetricsCollector.Statistics stats = collector.getStatistics();
            Map<String, Object> map = stats.toMap();

            assertTrue(map.containsKey("input"));
            assertTrue(map.containsKey("output"));
            assertTrue(map.containsKey("start_time"));
            assertTrue(map.containsKey("uptime_seconds"));

            collector.stop();
        }
    }

    @Nested
    @DisplayName("Export Tests")
    class ExportTests {

        @Test
        @DisplayName("should export to JSON file")
        void shouldExportToJsonFile() throws IOException {
            Path tempFile = Files.createTempFile("metrics", ".json");

            MetricsCollector collector = new MetricsCollector(true);

            Map<String, Object> sendData = new HashMap<>();
            sendData.put("neuron_count", 100);
            sendData.put("packet_size_bytes", 512);
            sendData.put("duration_ms", 10.5);

            collector.onSendComplete(sendData);
            collector.exportJson(tempFile.toString());
            collector.stop();

            // Verify file content
            String content = Files.readString(tempFile);
            assertNotNull(content);
            assertTrue(content.contains("\"input\""));

            Files.deleteIfExists(tempFile);
        }

        @Test
        @DisplayName("should export to CSV file")
        void shouldExportToCsvFile() throws IOException {
            Path tempFile = Files.createTempFile("metrics", ".csv");

            MetricsCollector collector = new MetricsCollector(true);

            Map<String, Object> sendData = new HashMap<>();
            sendData.put("neuron_count", 100);
            sendData.put("packet_size_bytes", 512);
            sendData.put("duration_ms", 10.5);

            collector.onSendComplete(sendData);
            collector.exportCsv(tempFile.toString());
            collector.stop();

            // Verify file has content
            List<String> lines = Files.readAllLines(tempFile);
            assertTrue(lines.size() > 0);
            assertTrue(lines.get(0).contains("metric,value"));

            Files.deleteIfExists(tempFile);
        }
    }

    @Nested
    @DisplayName("Reset Tests")
    class ResetTests {

        @Test
        @DisplayName("should reset statistics")
        void shouldResetStatistics() {
            MetricsCollector collector = new MetricsCollector(true);

            Map<String, Object> sendData = new HashMap<>();
            sendData.put("neuron_count", 100);
            sendData.put("packet_size_bytes", 512);
            sendData.put("duration_ms", 10.5);

            collector.onSendComplete(sendData);
            collector.onSendComplete(sendData);

            assertEquals(2, collector.getInputStatistics().getTotalPackets());

            collector.reset();

            assertEquals(0, collector.getInputStatistics().getTotalPackets());
            assertEquals(0, collector.getOutputStatistics().getTotalCommands());

            collector.stop();
        }
    }

    @Nested
    @DisplayName("Enabled/Disabled Tests")
    class EnabledDisabledTests {

        @Test
        @DisplayName("should not collect metrics when disabled")
        void shouldNotCollectMetricsWhenDisabled() {
            MetricsCollector collector = new MetricsCollector(false);

            Map<String, Object> sendData = new HashMap<>();
            sendData.put("neuron_count", 100);
            sendData.put("packet_size_bytes", 512);
            sendData.put("duration_ms", 10.5);

            collector.onSendComplete(sendData);
            collector.onSendComplete(sendData);
            collector.onSendComplete(sendData);

            // Should have zero statistics when disabled
            assertEquals(0, collector.getInputStatistics().getTotalPackets());

            collector.stop();
        }
    }
}
