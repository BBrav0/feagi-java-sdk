/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.examples.motor;

import io.feagi.sdk.core.*;
import io.feagi.sdk.core.motor.RotaryMotor;
import io.feagi.sdk.core.motor.ServoMotor;

import java.util.Random;
import java.util.logging.Logger;

/**
 * Motor Agent Example - Using ServoMotor and RotaryMotor for motor output
 *
 * <p>This example demonstrates:
 * <ol>
 *   <li>Configure MotorCapability</li>
 *   <li>Use ServoMotor for position control</li>
 *   <li>Use RotaryMotor for speed control</li>
 *   <li>Poll and apply motor commands</li>
 * </ol>
 *
 * <p>Set environment variables before running:
 * <pre>
 * export FEAGI_HOST=localhost
 * export FEAGI_REGISTRATION_PORT=30001
 * export FEAGI_SENSORY_PORT=5555
 * export FEAGI_MOTOR_PORT=5564
 * export FEAGI_AGENT_ID=motor-agent-001
 * </pre>
 */
public class MotorAgentExample {

    private static final Logger logger = Logger.getLogger(MotorAgentExample.class.getName());
    private static final int ITERATIONS = 100;
    private static final int LOOP_DELAY_MS = 50;  // 20 Hz

    public static void main(String[] args) {
        // Read configuration from environment
        String agentId = getEnvOrFail("FEAGI_AGENT_ID");
        String feagiHost = getEnvOrDefault("FEAGI_HOST", "localhost");
        int registrationPort = getIntEnvOrDefault("FEAGI_REGISTRATION_PORT", 30001);
        int sensoryPort = getIntEnvOrDefault("FEAGI_SENSORY_PORT", 5555);
        int motorPort = getIntEnvOrDefault("FEAGI_MOTOR_PORT", 5564);

        logger.info("Motor agent starting");
        logger.info("Config: " + agentId + " @ " + feagiHost + ":" + motorPort);

        // Configure motor capability
        MotorCapability motor = MotorCapability.builder()
            .modality("motor")
            .outputCount(4)  // 4 motor outputs
            .build();

        AgentCapabilities capabilities = AgentCapabilities.builder()
            .motor(motor)
            .build();

        // Register motor devices
        ServoMotor headServo = ServoMotor.builder()
            .angleRange(0.0, 180.0)
            .encoding(ServoMotor.Encoding.ABSOLUTE)
            .gain(1.0)
            .build();
        logger.info("Head servo registered: 0-180 degrees");

        ServoMotor armServo = ServoMotor.builder()
            .angleRange(-90.0, 90.0)
            .encoding(ServoMotor.Encoding.ABSOLUTE)
            .gain(1.0)
            .build();
        logger.info("Arm servo registered: -90 to +90 degrees");

        RotaryMotor leftWheel = RotaryMotor.builder()
            .maxSpeed(100.0)
            .build();
        logger.info("Left wheel registered: max speed 100");

        RotaryMotor rightWheel = RotaryMotor.builder()
            .maxSpeed(100.0)
            .build();
        logger.info("Right wheel registered: max speed 100");

        // Note: In a complete implementation, you would configure BrainOutput
        // and connect to FEAGI here. This example shows the device setup pattern.

        Random random = new Random();

        // Main loop - poll and apply motor commands
        for (int i = 0; i < ITERATIONS; i++) {
            // In real implementation:
            // MotorDataFrame motorData = brainOutput.receive();
            // if (motorData != null) { process motor commands }

            // Simulate reading device values
            double headAngle = 90.0 + (random.nextDouble() - 0.5) * 20;
            double armAngle = (random.nextDouble() - 0.5) * 180;
            double leftSpeed = random.nextDouble() * 100;
            double rightSpeed = random.nextDouble() * 100;

            // Apply motor commands
            applyMotorCommands(headServo, armServo, leftWheel, rightWheel,
                headAngle, armAngle, leftSpeed, rightSpeed);

            logger.info("Iteration " + (i + 1) + "/" + ITERATIONS +
                String.format(" - Head=%.1f deg | Arm=%.1f deg | Left=%.1f%% | Right=%.1f%%",
                    headAngle, armAngle, leftSpeed, rightSpeed));

            // Loop delay
            try {
                Thread.sleep(LOOP_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        logger.info("Completed " + ITERATIONS + " iterations");
    }

    /**
     * Apply motor commands to hardware
     * In a real application, call actual motor driver API
     */
    private static void applyMotorCommands(ServoMotor headServo, ServoMotor armServo,
                                         RotaryMotor leftWheel, RotaryMotor rightWheel,
                                         double headAngle, double armAngle,
                                         double leftSpeed, double rightSpeed) {
        // In real application:
        // setHeadServoAngle(headAngle);
        // setArmServoAngle(armAngle);
        // setLeftWheelSpeed(leftSpeed);
        // setRightWheelSpeed(rightSpeed);
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
