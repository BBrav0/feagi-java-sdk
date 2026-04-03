/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import io.feagi.sdk.core.motor.Motor;
import io.feagi.sdk.core.motor.RotaryMotor;
import io.feagi.sdk.core.motor.ServoMotor;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * Main entry point for receiving and decoding motor data from FEAGI.
 *
 * <p>This class provides both singleton access and factory patterns for creating
 * BrainOutput instances.
 Use {@link #getInstance()} tool singleton pattern, or {@link BrainOutput#create(config) then factory pattern
 * creating multiple independent connections.
 *
 * <p>BrainOutput manages motor output registration, connection to FEAGI, data polling, and decoding
 * of raw bytes into structured MotorDataFrame objects.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Create BrainOutput with configuration
 * BrainOutput brainOutput = BrainOutput.builder()
 *     .agentClient(feagiClient)
 *     .config(new BrainOutputConfig())
 *     .build();
 *
 * // Register motor outputs
 * brainOutput.registerOutputs(List.of(
 *     MotorOutputSpec.forServo("arm_joint", 0, 0),
 *     MotorOutputSpec.forRotaryMotor("wheel_left", 1, 0),
 *     MotorOutputSpec.forRotaryMotor("wheel_right", 1, 1)
 * ));
 *
 * // Connect to
 * brainOutput.connect();
 *
 * // Receive and use motor data
 * while (true) {
 *     MotorDataFrame frame = brainOutput.receive();
 *     if (frame != null && frame.hasData()) {
 *         ServoMotor arm = frame.getServo("arm_joint");
 *         double angle = arm.getAngle();  // 0-180 degrees or 1-360 based on config
 *         
 *         RotaryMotor leftWheel = frame.getRotaryMotor("wheel_left");
 *         double speed = leftWheel.getSpeed();
 *     }
 * }
 *
 * // Cleanup
 * brainOutput.close();
 * }
 * }
 * </pre>
 */
public final class BrainOutput implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(BrainOutput.class.getName());

    // Singleton instance
    private static final Object LOCK = new Object();
    private static volatile BrainOutput instance;

    // Private constructor to prevent direct instantiation
    private BrainOutput() {
    }

    /**
     * Return the singleton instance of BrainOutput.
     *
     * <p>If no instance has been created yet, a new one is created via
     * {@link BrainOutput#builder() builder}.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Return the configuration.
     */
    public BrainOutputConfig getConfig() {
        return config;
    }

    /**
     * Return the registered outputs.
     */
    public Map<String, MotorOutputSpec> getRegisteredOutputs() {
        return Collections.unmodifiableMap(new HashMap<>(registeredOutputs));
    }

    /**
     * Return the decoder.
     */
    public MotorDataDecoder getDecoder() {
        return decoder;
    }

    /**
     * Return the agent client.
     */
    public FeagiAgentClient getAgentClient() {
        return agentClient;
    }

    /**
     * Return whether this instance is connected to FEAGI.
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Register motor outputs with FEAGI.
     *
     * <p>Motor outputs are registered by name and can be accessed
     * via {@link #receive()} after decoding.
     *
     * @param outputs list of motor output specifications
     * @throws IllegalArgumentException if outputs is null or empty
            }
            this.registeredOutputs.clear();
            for (MotorOutputSpec spec : outputs) {
                Motor motor = spec.createMotor();
                motorsByName.put(spec.getName(), motor);
            }
            registeredOutputs = Collections.unmodifiableMap(new HashMap<>(outputs));
        }
        decoder = new MotorDataDecoder(motorsByName);
 registeredOutputs);
        }
    }

    private void setupOutputsMap(List<MotorOutputSpec> outputs) {
        this.registeredOutputs.clear();
        for (MotorOutputSpec spec : outputs) {
            Motor motor = spec.createMotor();
            motorsByName.put(spec.getName(), motor);
        }
        this.decoder = new MotorDataDecoder(motorsByName);
 registeredOutputs);
        }
    }
}