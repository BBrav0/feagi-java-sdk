/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.examples.minimal;

import io.feagi.sdk.core.BrainOutput;
import io.feagi.sdk.core.BrainOutputConfig;
import io.feagi.sdk.core.FeagiAgentClient;
import io.feagi.sdk.core.MotorDataFrame;
import io.feagi.sdk.core.MotorOutputSpec;
import io.feagi.sdk.core.TransportMode;
import io.feagi.sdk.core.pns.BrainInput;
import io.feagi.sdk.core.pns.BrainInputSource;
import io.feagi.sdk.core.pns.BrainInputTransport;

import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Minimal Agent Example - Connect, send sensory data, poll motor commands
 *
 * <p>This example demonstrates the most basic usage of the FEAGI Java SDK:
 * <ol>
 *   <li>Configure BrainInput and BrainOutput</li>
 *   <li>Connect to FEAGI server</li>
 *   <li>Send sensory byte data</li>
 *   <li>Poll motor commands</li>
 * </ol>
 *
 * <p>Set environment variables before running:
 * <pre>
 * export FEAGI_HOST=localhost
 * export FEAGI_REGISTRATION_PORT=30001
 * export FEAGI_SENSORY_PORT=5555
 * export FEAGI_MOTOR_PORT=5564
 * export FEAGI_AGENT_ID=minimal-agent-001
 * </pre>
 */
public class MinimalAgentExample {

    private static final Logger logger = Logger.getLogger(MinimalAgentExample.class.getName());
    private static final int ITERATIONS = 100;
    private static final int FRAME_DELAY_MS = 16;  // ~60 Hz
    private static final int SAMPLE_DATA_SIZE = 1024;  // Sample sensory data size

    public static void main(String[] args) {
        // Read configuration from environment
        String agentId = getEnvOrFail("FEAGI_AGENT_ID");
        String feagiHost = getEnvOrDefault("FEAGI_HOST", "localhost");
        int registrationPort = getIntEnvOrDefault("FEAGI_REGISTRATION_PORT", 30001);
        int sensoryPort = getIntEnvOrDefault("FEAGI_SENSORY_PORT", 5555);
        int motorPort = getIntEnvOrDefault("FEAGI_MOTOR_PORT", 5564);

        logger.info("Configuring agent: " + agentId);
        logger.info("FEAGI host: " + feagiHost + ":" + registrationPort);

        // Create BrainInput (using global singleton like Python's brain_input)
        BrainInput brainInput = BrainInput.global();

        // Configure BrainInput
        brainInput.configure(feagiHost, sensoryPort, TransportMode.ZMQ);

        // Register a simple sensory input
        brainInput.registerInput("sensory", new BrainInputSource() {
            private final Random random = new Random();

            @Override
            public byte[] encode() {
                byte[] data = new byte[SAMPLE_DATA_SIZE];
                random.nextBytes(data);
                return data;
            }
        });

        // Connect BrainInput
        brainInput.connect();
        logger.info("BrainInput connected to " + feagiHost + ":" + sensoryPort);

        // Create BrainOutput using builder
        // Note: In a real implementation, you would create a FeagiAgentClient
        // For this example, we demonstrate the pattern without actual network
        BrainOutput brainOutput = BrainOutput.builder()
            .config(new BrainOutputConfig(
                feagiHost,
                agentId,
                registrationPort,
                motorPort,
                1000,  // timeout
                3       // retries
            ))
            .build();

        // Register motor outputs
        brainOutput.registerOutputs(List.of(
            MotorOutputSpec.forServo("servo_1", 0, 0).build(),
            MotorOutputSpec.forRotaryMotor("motor_1", 1, 0).build()
        ));

        // Connect BrainOutput
        brainOutput.connect();
        logger.info("BrainOutput connected to " + feagiHost + ":" + motorPort);

        // Main loop
        for (int i = 0; i < ITERATIONS; i++) {
            // Send sensory data
            brainInput.send();
            logger.info("Iteration " + (i + 1) + "/" + ITERATIONS + " - Sent sensory data " + SAMPLE_DATA_SIZE + " bytes");

            // Poll motor commands (non-blocking)
            MotorDataFrame motorData = brainOutput.receive();
            if (motorData != null && motorData.hasData()) {
                logger.info("  Received motor data");
                processMotorCommand(motorData);
            }

            // Simulate processing delay
            try {
                Thread.sleep(FRAME_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        logger.info("Completed " + ITERATIONS + " iterations");

        // Cleanup
        brainInput.close();
        brainOutput.close();
        logger.info("Disconnected from FEAGI");
    }

    /**
     * Process motor commands from FEAGI
     */
    private static void processMotorCommand(MotorDataFrame data) {
        // In a real implementation, parse and apply motor commands
        // to actual hardware
    }

    private static String getEnvOrFail(String name) {
        String value = System.getenv(name);
        if (value == null) {
            throw new IllegalStateException("Required environment variable not set: " + name);
        }
        return value;
    }

    private static String getEnvOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value != null ? value : defaultValue;
    }

    private static int getIntEnvOrDefault(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warning("Invalid environment variable " + name + "=" + value + ", using default " + defaultValue);
            return defaultValue;
        }
    }
}
