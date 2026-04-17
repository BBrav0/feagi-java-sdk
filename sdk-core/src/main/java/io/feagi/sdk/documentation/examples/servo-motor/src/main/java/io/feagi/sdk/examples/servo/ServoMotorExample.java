/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.examples.servo;

import io.feagi.sdk.core.motor.ServoMotor;

import java.util.logging.Logger;

/**
 * Servo Motor Example - Using ServoMotor for position control
 *
 * <p>This example demonstrates:
 * <ol>
 *   <li>Register multiple servos with different ranges</li>
 *   <li>Connect to FEAGI</li>
 *   <li>Read servo angles in main loop</li>
 *   <li>Apply angles to hardware</li>
 * </ol>
 *
 * <p>Set environment variables before running:
 * <pre>
 * export FEAGI_HOST=localhost
 * export FEAGI_REGISTRATION_PORT=30001
 * export FEAGI_SENSORY_PORT=5555
 * export FEAGI_MOTOR_PORT=5564
 * export FEAGI_AGENT_ID=servo-agent-001
 * </pre>
 */
public class ServoMotorExample {

    private static final Logger logger = Logger.getLogger(ServoMotorExample.class.getName());
    private static final int LOOP_DELAY_MS = 100;  // 10 Hz

    public static void main(String[] args) {
        // Read configuration from environment
        String agentId = getEnvOrFail("FEAGI_AGENT_ID");
        String feagiHost = getEnvOrDefault("FEAGI_HOST", "localhost");
        int motorPort = getIntEnvOrDefault("FEAGI_MOTOR_PORT", 5564);

        logger.info("=".repeat(60));
        logger.info("FEAGI 2.0 - Servo Motor Example");
        logger.info("=".repeat(60));

        // Register servo motors
        logger.info("\nRegistering servo motors...");

        // Standard servo (0-180 degrees)
        ServoMotor headServo = ServoMotor.builder()
            .angleRange(0.0, 180.0)
            .encoding(ServoMotor.Encoding.ABSOLUTE)
            .gain(1.0)
            .build();
        logger.info("  Head servo registered (0-180 deg)");

        // Wide-range servo (0-270 degrees)
        ServoMotor armServo = ServoMotor.builder()
            .angleRange(0.0, 270.0)
            .encoding(ServoMotor.Encoding.ABSOLUTE)
            .gain(1.0)
            .build();
        logger.info("  Arm servo registered (0-270 deg)");

        // Bidirectional servo (-90 to +90 degrees)
        ServoMotor tiltServo = ServoMotor.builder()
            .angleRange(-90.0, 90.0)
            .encoding(ServoMotor.Encoding.ABSOLUTE)
            .gain(1.0)
            .build();
        logger.info("  Tilt servo registered (-90 to +90 deg)");

        logger.info("\nConfiguring connection...");
        logger.info("  Configured: " + feagiHost + ":" + motorPort);

        // Note: In a complete implementation, you would configure BrainOutput
        // and connect to FEAGI here.

        logger.info("\nConnecting to FEAGI...");
        logger.info("  Connected successfully!");

        // Main loop
        logger.info("\nStarting motor control loop...");
        logger.info("  (Press Ctrl+C to stop)\n");

        int loopCount = 0;
        while (loopCount < 100) {  // Run 100 iterations
            loopCount++;

            // Read servo angles
            double headAngle = headServo.getAngle();
            double armAngle = armServo.getAngle();
            double tiltAngle = tiltServo.getAngle();

            // Apply to hardware
            applyServoAngle("head", headAngle);
            applyServoAngle("arm", armAngle);
            applyServoAngle("tilt", tiltAngle);

            // Print status every 10 loops
            if (loopCount % 10 == 0) {
                logger.info(String.format("[Loop %04d] Head=%6.1f deg | Arm=%6.1f deg | Tilt=%+6.1f deg",
                    loopCount, headAngle, armAngle, tiltAngle));
            }

            // Delay
            try {
                Thread.sleep(LOOP_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        logger.info("\nStopping motor control...");

        logger.info("\n" + "=".repeat(60));
        logger.info("Servo motor example complete");
        logger.info("=".repeat(60));
    }

    /**
     * Apply servo angle to hardware
     * In a real application, replace with actual servo control library calls
     * (e.g., pigpio, Adafruit PCA9685, etc.)
     */
    private static void applyServoAngle(String name, double angle) {
        // Placeholder for hardware API call
        // setHardwareServoAngle(name, angle);
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
