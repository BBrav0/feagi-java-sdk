/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.examples.observability;

import io.feagi.sdk.observability.DataLogger;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Data Logging Example - Using DataLogger to log sensory and motor data
 *
 * <p>This example demonstrates:
 * <ol>
 *   <li>Create DataLogger in JSONL format</li>
 *   <li>Create DataLogger in CSV format</li>
 *   <li>Attach loggers to PNS events</li>
 *   <li>Analyze logged data</li>
 * </ol>
 *
 * <p>Set environment variables before running:
 * <pre>
 * export FEAGI_HOST=localhost
 * export FEAGI_REGISTRATION_PORT=30001
 * export FEAGI_SENSORY_PORT=5555
 * export FEAGI_MOTOR_PORT=5564
 * export FEAGI_AGENT_ID=logging-agent-001
 * </pre>
 */
public class DataLogging {

    private static final Logger logger = Logger.getLogger(DataLogging.class.getName());
    private static final int ITERATIONS = 50;
    private static final int FRAME_SIZE = 320 * 240 * 3;  // 320x240 RGB

    public static void main(String[] args) {
        logger.info("=".repeat(60));
        logger.info("Observability Example 2: Data Logging");
        logger.info("=".repeat(60));

        // Create data loggers
        logger.info("\nSetting up data logging...");

        // JSONL logger (recommended - streams to disk)
        DataLogger jsonlLogger = new DataLogger.Builder()
            .outputFile("data_log.jsonl")
            .format(DataLogger.Format.JSONL)
            .logInputs(true)
            .logOutputs(true)
            .sampleRate(1.0)  // Log 100% of packets
            .build();
        logger.info("  JSONL logger: data_log.jsonl");

        // CSV logger (for spreadsheet analysis)
        DataLogger csvLogger = new DataLogger.Builder()
            .outputFile("data_log.csv")
            .format(DataLogger.Format.CSV)
            .logInputs(true)
            .logOutputs(true)
            .sampleRate(1.0)
            .build();
        logger.info("  CSV logger: data_log.csv");

        Random random = new Random();

        // Simulate agent running
        logger.info("\nRunning agent (" + ITERATIONS + " iterations)...");

        for (int i = 0; i < ITERATIONS; i++) {
            // Generate sensory data
            byte[] sensoryData = new byte[FRAME_SIZE];
            random.nextBytes(sensoryData);

            // In real implementation:
            // brainInput.send(sensoryData);
            // jsonlLogger.onSendComplete(...);
            // csvLogger.onSendComplete(...);

            // Poll motor commands
            // MotorDataFrame motorData = brainOutput.receive();
            // jsonlLogger.onReceiveComplete(...);
            // csvLogger.onReceiveComplete(...);

            if ((i + 1) % 10 == 0) {
                logger.info("  Logged " + (i + 1) + "/" + ITERATIONS + " packets...");
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

        // Close loggers
        logger.info("\nClosing loggers...");
        jsonlLogger.close();
        csvLogger.close();
        logger.info("Loggers closed and data flushed");

        // Display sample data
        logger.info("\n" + "=".repeat(60));
        logger.info("Sample Log Data (JSONL)");
        logger.info("=".repeat(60));
        displaySampleData();

        logger.info("\n" + "=".repeat(60));
        logger.info("Example complete");
        logger.info("=".repeat(60));
        logger.info("\nCreated log files:");
        logger.info("  - data_log.jsonl (JSON Lines format)");
        logger.info("  - data_log.csv (CSV format)");
        logger.info("\nAnalyze these logs with standard tools:");
        logger.info("  - jq: cat data_log.jsonl | jq '.'");
        logger.info("  - Excel: Open data_log.csv");
        logger.info("  - Python pandas: pd.read_json('data_log.jsonl', lines=True)");
    }

    /**
     * Display sample data from log file
     */
    private static void displaySampleData() {
        try {
            List<String> lines = Files.readAllLines(Paths.get("data_log.jsonl"));

            logger.info("\nFirst 3 entries:");
            int count = 0;
            for (String line : lines) {
                if (count < 3) {
                    logger.info("  " + truncate(line, 100));
                    count++;
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            logger.warning("Could not read log file: " + e.getMessage());
        }
    }

    /**
     * Truncate long strings
     */
    private static String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen) + "...";
    }
}
