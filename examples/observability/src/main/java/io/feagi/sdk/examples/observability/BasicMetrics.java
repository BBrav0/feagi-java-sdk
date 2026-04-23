/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.examples.observability;

import io.feagi.sdk.observability.MetricsCollector;
import io.feagi.sdk.observability.MetricsCollector.InputStatistics;
import io.feagi.sdk.observability.MetricsCollector.OutputStatistics;

import java.util.Random;
import java.util.logging.Logger;

/**
 * Basic Metrics Example - Using MetricsCollector to collect PNS data flow statistics
 *
 * <p>This example demonstrates:
 * <ol>
 *   <li>Create and configure MetricsCollector</li>
 *   <li>Collect input/output packet statistics</li>
 *   <li>Calculate data rates and latencies</li>
 *   <li>Export to JSON and CSV formats</li>
 * </ol>
 *
 * <p>Set environment variables before running:
 * <pre>
 * export FEAGI_HOST=localhost
 * export FEAGI_REGISTRATION_PORT=30001
 * export FEAGI_SENSORY_PORT=5555
 * export FEAGI_MOTOR_PORT=5564
 * export FEAGI_AGENT_ID=metrics-agent-001
 * </pre>
 */
public class BasicMetrics {

    private static final Logger logger = Logger.getLogger(BasicMetrics.class.getName());
    private static final int ITERATIONS = 50;
    private static final int FRAME_SIZE = 640 * 480 * 3;  // 640x480 RGB

    public static void main(String[] args) {
        logger.info("=".repeat(60));
        logger.info("Observability Example 1: Basic Metrics Collection");
        logger.info("=".repeat(60));

        // Read configuration from environment
        String agentId = getEnvOrFail("FEAGI_AGENT_ID");

        // Create metrics collector
        logger.info("\nCreating metrics collector...");
        MetricsCollector metrics = new MetricsCollector();
        logger.info("  Metrics collector initialized");

        Random random = new Random();

        // Simulate agent running
        logger.info("\nRunning agent (" + ITERATIONS + " iterations)...");

        for (int i = 0; i < ITERATIONS; i++) {
            // Generate sensory data
            byte[] sensoryData = new byte[FRAME_SIZE];
            random.nextBytes(sensoryData);

            // In real implementation:
            // brainInput.send(sensoryData);
            // metrics.onSendComplete(...);

            // Poll motor commands
            // MotorDataFrame motorData = brainOutput.receive();
            // metrics.onReceiveComplete(...);

            if ((i + 1) % 10 == 0) {
                logger.info("  Processed " + (i + 1) + "/" + ITERATIONS + " iterations...");
            }

            // Simulate processing delay
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        logger.info("Agent run complete");

        // Stop metrics collection
        metrics.stop();

        // Display results
        logger.info("\nResults:");
        displayMetrics(metrics);

        // Export
        logger.info("\nExporting metrics...");
        exportMetrics(metrics);

        logger.info("\n" + "=".repeat(60));
        logger.info("Basic metrics example complete");
        logger.info("=".repeat(60));
    }

    /**
     * Display metrics summary
     */
    private static void displayMetrics(MetricsCollector metrics) {
        InputStatistics inputStats = metrics.getInputStatistics();
        OutputStatistics outputStats = metrics.getOutputStatistics();

        logger.info("Input Statistics:");
        logger.info("  Total packets: " + inputStats.getTotalPackets());
        logger.info("  Total bytes: " + inputStats.getTotalBytes());
        logger.info(String.format("  Data rate: %.4f MB/s", inputStats.getDataRateMbps()));
        logger.info(String.format("  Avg packet size: %.2f bytes", inputStats.getAvgPacketSize()));

        logger.info("Output Statistics:");
        logger.info("  Total commands: " + outputStats.getTotalCommands());
        logger.info("  Total receives: " + outputStats.getTotalReceives());
        logger.info(String.format("  Avg latency: %.2f ms", outputStats.getAvgLatencyMs()));
        logger.info(String.format("  Avg commands/receive: %.2f", outputStats.getAvgCommandsPerReceive()));
    }

    /**
     * Export metrics to files
     */
    private static void exportMetrics(MetricsCollector metrics) {
        try {
            metrics.exportJson("metrics.json");
            logger.info("  JSON exported: metrics.json");

            metrics.exportCsv("metrics.csv");
            logger.info("  CSV exported: metrics.csv");
        } catch (Exception e) {
            logger.severe("Export failed: " + e.getMessage());
        }
    }

    private static String getEnvOrFail(String name) {
        String value = System.getenv(name);
        if (value == null) {
            throw new IllegalStateException("Required environment variable not set: " + name);
        }
        return value;
    }
}
