/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import io.feagi.sdk.core.motor.Motor;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Main entry point for receiving and decoding motor data from FEAGI.
 *
 * <p>This class provides a factory pattern for creating BrainOutput instances
 * via the builder pattern. Use {@link #builder()} to create instances.
 *
 * <p>BrainOutput manages motor output registration, connection to FEAGI, data polling,
 * and decoding of raw bytes into structured MotorDataFrame objects.
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
 *     MotorOutputSpec.forServo("arm_joint", 0, 0).build(),
 *     MotorOutputSpec.forRotaryMotor("wheel_left", 1, 0).build(),
 *     MotorOutputSpec.forRotaryMotor("wheel_right", 1, 1).build()
 * ));
 *
 * // Connect to FEAGI
 * brainOutput.connect();
 *
 * // Receive and use motor data
 * while (running) {
 *     MotorDataFrame frame = brainOutput.receive();
 *     if (frame != null && frame.hasData()) {
 *         ServoMotor arm = frame.getServo("arm_joint");
 *         if (arm != null) {
 *             double angle = arm.getAngle();
 *         }
 *
 *         RotaryMotor leftWheel = frame.getRotaryMotor("wheel_left");
 *         if (leftWheel != null) {
 *             double speed = leftWheel.getSpeed();
 *         }
 *     }
 * }
 *
 * // Cleanup
 * brainOutput.close();
 * }</pre>
 */
public final class BrainOutput implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(BrainOutput.class.getName());

    private final BrainOutputConfig config;
    private final FeagiAgentClient agentClient;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Motor registration - mutable, guarded by synchronized
    private final Map<String, MotorOutputSpec> registeredOutputs = new ConcurrentHashMap<>();
    private final Map<String, Motor> motorsByName = new ConcurrentHashMap<>();

    // Decoder - created when outputs are registered
    private volatile MotorDataDecoder decoder;

    /**
     * Private constructor - use builder to create instances.
     *
     * @param config      configuration settings
     * @param agentClient FEAGI agent client for receiving data
     */
    private BrainOutput(BrainOutputConfig config, FeagiAgentClient agentClient) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.agentClient = Objects.requireNonNull(agentClient, "agentClient must not be null");
        this.decoder = new MotorDataDecoder();
    }

    /**
     * Create a new BrainOutput builder.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Return the configuration.
     *
     * @return configuration settings
     */
    public BrainOutputConfig getConfig() {
        return config;
    }

    /**
     * Return a copy of the registered outputs.
     *
     * @return unmodifiable map of registered outputs
     */
    public Map<String, MotorOutputSpec> getRegisteredOutputs() {
        return Collections.unmodifiableMap(new HashMap<>(registeredOutputs));
    }

    /**
     * Return the decoder.
     *
     * @return motor data decoder
     */
    public MotorDataDecoder getDecoder() {
        return decoder;
    }

    /**
     * Return the agent client.
     *
     * @return FEAGI agent client
     */
    public FeagiAgentClient getAgentClient() {
        return agentClient;
    }

    /**
     * Return whether this instance is connected to FEAGI.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return connected.get() && !closed.get();
    }

    /**
     * Return whether this instance has been closed.
     *
     * @return true if closed
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Register motor outputs with this BrainOutput.
     *
     * <p>Motor outputs are registered by name and can be accessed
     * via {@link #receive()} after decoding.
     *
     * @param outputs list of motor output specifications
     * @throws IllegalArgumentException if outputs is null
     * @throws IllegalStateException    if BrainOutput is closed
     */
    public synchronized void registerOutputs(List<MotorOutputSpec> outputs) {
        if (closed.get()) {
            throw new IllegalStateException("BrainOutput is closed");
        }
        Objects.requireNonNull(outputs, "outputs must not be null");

        // Clear existing registrations
        registeredOutputs.clear();
        motorsByName.clear();

        // Register new outputs
        for (MotorOutputSpec spec : outputs) {
            if (spec != null) {
                registeredOutputs.put(spec.getName(), spec);
                Motor motor = spec.createMotor();
                motorsByName.put(spec.getName(), motor);
            }
        }

        // Recreate decoder with new motor instances
        this.decoder = new MotorDataDecoder(motorsByName);

        LOG.info("Registered " + registeredOutputs.size() + " motor outputs");
    }

    /**
     * Register a single motor output.
     *
     * @param output motor output specification
     * @throws IllegalArgumentException if output is null
     * @throws IllegalStateException    if BrainOutput is closed
     */
    public synchronized void registerOutput(MotorOutputSpec output) {
        if (closed.get()) {
            throw new IllegalStateException("BrainOutput is closed");
        }
        Objects.requireNonNull(output, "output must not be null");

        registeredOutputs.put(output.getName(), output);
        Motor motor = output.createMotor();
        motorsByName.put(output.getName(), motor);

        // Update decoder
        this.decoder = new MotorDataDecoder(motorsByName);
    }

    /**
     * Connect to FEAGI and start receiving motor data.
     *
     * @throws IllegalStateException if already connected or closed
     */
    public void connect() {
        if (closed.get()) {
            throw new IllegalStateException("BrainOutput is closed");
        }
        if (!connected.compareAndSet(false, true)) {
            throw new IllegalStateException("Already connected");
        }

        LOG.info("BrainOutput connected");
    }

    /**
     * Disconnect from FEAGI and stop receiving motor data.
     */
    public void disconnect() {
        if (connected.compareAndSet(true, false)) {
            LOG.info("BrainOutput disconnected");
        }
    }

    /**
     * Receive and decode motor data from FEAGI.
     *
     * <p>This method polls the agent client for motor data and returns
     * a decoded MotorDataFrame containing snapshots of motor values.
     * The returned frame contains immutable snapshots, safe to store
     * and use across multiple frames.
     *
     * <p>This is a non-blocking call - if no data is available, an empty frame is returned.
     *
     * @return decoded motor data frame with snapshots, or empty frame if no data
     * @throws IllegalStateException if not connected or closed
     */
    public MotorDataFrame receive() {
        if (closed.get()) {
            throw new IllegalStateException("BrainOutput is closed");
        }
        if (!connected.get()) {
            throw new IllegalStateException("Not connected. Call connect() first.");
        }

        // Poll motor data (non-blocking)
        byte[] rawData = agentClient.pollMotorBytes();
        if (rawData == null || rawData.length == 0) {
            return MotorDataFrame.empty();
        }

        // Decode - decoder returns snapshots
        return decoder.decode(rawData);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            disconnect();
            registeredOutputs.clear();
            motorsByName.clear();
            LOG.info("BrainOutput closed");
        }
    }

    /**
     * Builder for BrainOutput instances.
     */
    public static final class Builder {
        private BrainOutputConfig config = new BrainOutputConfig();
        private FeagiAgentClient agentClient;

        private Builder() {}

        /**
         * Set the FEAGI agent client.
         *
         * @param agentClient the FEAGI agent client
         * @return this builder
         */
        public Builder agentClient(FeagiAgentClient agentClient) {
            this.agentClient = Objects.requireNonNull(agentClient, "agentClient must not be null");
            return this;
        }

        /**
         * Set the configuration.
         *
         * @param config the configuration
         * @return this builder
         */
        public Builder config(BrainOutputConfig config) {
            this.config = Objects.requireNonNull(config, "config must not be null");
            return this;
        }

        /**
         * Build the BrainOutput instance.
         *
         * @return new BrainOutput instance
         * @throws IllegalStateException if agentClient is not set
         */
        public BrainOutput build() {
            if (agentClient == null) {
                throw new IllegalStateException("agentClient must be set");
            }
            return new BrainOutput(config, agentClient);
        }
    }
}