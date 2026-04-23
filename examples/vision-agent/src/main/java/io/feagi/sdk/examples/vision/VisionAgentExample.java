/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.examples.vision;

import io.feagi.sdk.core.*;
import io.feagi.sdk.pns.inputs.Camera;

import java.util.Random;
import java.util.logging.Logger;

/**
 * Vision Agent Example - Using Camera for vision input processing
 *
 * <p>This example demonstrates:
 * <ol>
 *   <li>Configure VisionCapability</li>
 *   <li>Use Camera device to capture and send images</li>
 *   <li>Stream vision data to FEAGI</li>
 * </ol>
 *
 * <p>Set environment variables before running:
 * <pre>
 * export FEAGI_HOST=localhost
 * export FEAGI_REGISTRATION_PORT=30001
 * export FEAGI_SENSORY_PORT=5555
 * export FEAGI_MOTOR_PORT=5564
 * export FEAGI_AGENT_ID=vision-agent-001
 * </pre>
 */
public class VisionAgentExample {

    private static final Logger logger = Logger.getLogger(VisionAgentExample.class.getName());
    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;
    private static final int CHANNELS = 3;  // RGB
    private static final int FRAME_SIZE = WIDTH * HEIGHT * CHANNELS;
    private static final int ITERATIONS = 500;
    private static final int FRAME_DELAY_MS = 33;  // ~30 Hz

    public static void main(String[] args) {
        // Read configuration from environment
        String agentId = getEnvOrFail("FEAGI_AGENT_ID");
        String feagiHost = getEnvOrDefault("FEAGI_HOST", "localhost");
        int registrationPort = getIntEnvOrDefault("FEAGI_REGISTRATION_PORT", 30001);
        int sensoryPort = getIntEnvOrDefault("FEAGI_SENSORY_PORT", 5555);
        int motorPort = getIntEnvOrDefault("FEAGI_MOTOR_PORT", 5564);

        logger.info("Vision agent starting");
        logger.info("Config: " + agentId + " @ " + feagiHost + ":" + sensoryPort);

        // Configure vision capability
        VisionCapability vision = VisionCapability.builder()
            .modality("vision")
            .resolution(WIDTH, HEIGHT)
            .channels(CHANNELS)
            .build();

        AgentCapabilities capabilities = AgentCapabilities.builder()
            .vision(vision)
            .build();

        // Create camera device
        Camera camera = Camera.builder()
            .resolution(WIDTH, HEIGHT)
            .channels(CHANNELS)
            .encoding("RGB")
            .build();
        camera._registerWithCache();
        logger.info("Camera registered: " + WIDTH + "x" + HEIGHT + " RGB");

        // Note: In a complete implementation, you would configure BrainInput
        // and connect to FEAGI here. This example shows the device setup pattern.

        // Main loop - capture and send vision frames
        Random random = new Random();
        for (int i = 0; i < ITERATIONS; i++) {
            // Generate sample frame data (in real app, read from camera)
            byte[] frameData = generateFrameData(random);

            // Set camera frame
            camera.setFrame(frameData);

            // In real implementation:
            // brainInput.send(camera.toBytes());

            logger.info("Frame " + (i + 1) + "/" + ITERATIONS + " - Sent " + frameData.length + " bytes");

            // Simulate frame rate control
            try {
                Thread.sleep(FRAME_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        logger.info("Completed " + ITERATIONS + " frame transmissions");
    }

    /**
     * Generate sample frame data
     * In a real application, read frames from camera hardware
     */
    private static byte[] generateFrameData(Random random) {
        byte[] frame = new byte[FRAME_SIZE];
        random.nextBytes(frame);
        return frame;
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
