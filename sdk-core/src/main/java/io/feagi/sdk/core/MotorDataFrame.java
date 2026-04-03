/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import io.feagi.sdk.core.motor.Motor;
import io.feagi.sdk.core.motor.RotaryMotor;
import io.feagi.sdk.core.motor.ServoMotor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Container for decoded motor data received from FEAGI in a single frame.
 *
 * <p>MotorDataFrame provides type-safe access to motor data after it has been
 * decoded from the raw FEAGI byte format. Motors can be accessed by name,
 * by type, or as a complete collection.
 *
 * <p>Example usage:
 * <pre>{@code
 * MotorDataFrame frame = brainOutput.receive();
 * if (frame != null && frame.hasData()) {
 *     // Access by name
 *     ServoMotor arm = frame.getServo("arm_joint");
 *     if (arm != null) {
 *         double angle = arm.getAngle();
 *     }
 *
 *     // Access all motors of a type
 *     Map<String, RotaryMotor> wheels = frame.getRotaryMotors();
 *     for (RotaryMotor wheel : wheels.values()) {
 *         System.out.println(wheel.getName() + ": " + wheel.getSpeed());
 *     }
 * }
 * }</pre>
 */
public final class MotorDataFrame {

    private final Map<String, Motor> motorsByName;
    private final Map<String, ServoMotor> servosByName;
    private final Map<String, RotaryMotor> rotaryMotorsByName;
    private final long timestamp;
    private final boolean hasData;

    /**
     * Create an empty frame with no data.
     */
    public MotorDataFrame() {
        this.motorsByName = Collections.emptyMap();
        this.servosByName = Collections.emptyMap();
        this.rotaryMotorsByName = Collections.emptyMap();
        this.timestamp = System.currentTimeMillis();
        this.hasData = false;
    }

    /**
     * Create a frame from a map of motors.
     *
     * @param motorsByName map of motor name to motor instance
     */
    public MotorDataFrame(Map<String, Motor> motorsByName) {
        this(motorsByName, System.currentTimeMillis());
    }

    /**
     * Create a frame from a map of motors with a specific timestamp.
     *
     * @param motorsByName map of motor name to motor instance
     * @param timestamp    frame timestamp in milliseconds
     */
    public MotorDataFrame(Map<String, Motor> motorsByName, long timestamp) {
        this.motorsByName = motorsByName != null ?
                Collections.unmodifiableMap(new HashMap<>(motorsByName)) :
                Collections.emptyMap();
        this.timestamp = timestamp;
        this.hasData = !this.motorsByName.isEmpty();

        // Index motors by type
        Map<String, ServoMotor> servos = new HashMap<>();
        Map<String, RotaryMotor> rotaries = new HashMap<>();

        for (Map.Entry<String, Motor> entry : this.motorsByName.entrySet()) {
            Motor motor = entry.getValue();
            if (motor instanceof ServoMotor) {
                servos.put(entry.getKey(), (ServoMotor) motor);
            } else if (motor instanceof RotaryMotor) {
                rotaries.put(entry.getKey(), (RotaryMotor) motor);
            }
        }

        this.servosByName = Collections.unmodifiableMap(servos);
        this.rotaryMotorsByName = Collections.unmodifiableMap(rotaries);
    }

    /**
     * Return true if this frame contains any motor data.
     *
     * @return true if data is present
     */
    public boolean hasData() {
        return hasData;
    }

    /**
     * Return the frame timestamp.
     *
     * @return timestamp in milliseconds since epoch
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Return the frame age in milliseconds.
     *
     * @return age in milliseconds
     */
    public long getAgeMillis() {
        return System.currentTimeMillis() - timestamp;
    }

    /**
     * Get a motor by name.
     *
     * @param name motor name
     * @return motor instance, or null if not found
     */
    public Motor getMotor(String name) {
        return motorsByName.get(name);
    }

    /**
     * Get a motor by name wrapped in Optional.
     *
     * @param name motor name
     * @return Optional containing motor, or empty if not found
     */
    public Optional<Motor> getMotorOptional(String name) {
        return Optional.ofNullable(motorsByName.get(name));
    }

    /**
     * Get a servo motor by name.
     *
     * @param name motor name
     * @return servo motor instance, or null if not found or not a servo
     */
    public ServoMotor getServo(String name) {
        return servosByName.get(name);
    }

    /**
     * Get a servo motor by name wrapped in Optional.
     *
     * @param name motor name
     * @return Optional containing servo motor, or empty if not found
     */
    public Optional<ServoMotor> getServoOptional(String name) {
        return Optional.ofNullable(servosByName.get(name));
    }

    /**
     * Get a rotary motor by name.
     *
     * @param name motor name
     * @return rotary motor instance, or null if not found or not a rotary motor
     */
    public RotaryMotor getRotaryMotor(String name) {
        return rotaryMotorsByName.get(name);
    }

    /**
     * Get a rotary motor by name wrapped in Optional.
     *
     * @param name motor name
     * @return Optional containing rotary motor, or empty if not found
     */
    public Optional<RotaryMotor> getRotaryMotorOptional(String name) {
        return Optional.ofNullable(rotaryMotorsByName.get(name));
    }

    /**
     * Get all motors in this frame.
     *
     * @return unmodifiable map of all motors by name
     */
    public Map<String, Motor> getAllMotors() {
        return motorsByName;
    }

    /**
     * Get all servo motors in this frame.
     *
     * @return unmodifiable map of servo motors by name
     */
    public Map<String, ServoMotor> getServos() {
        return servosByName;
    }

    /**
     * Get all rotary motors in this frame.
     *
     * @return unmodifiable map of rotary motors by name
     */
    public Map<String, RotaryMotor> getRotaryMotors() {
        return rotaryMotorsByName;
    }

    /**
     * Get the number of motors in this frame.
     *
     * @return motor count
     */
    public int getMotorCount() {
        return motorsByName.size();
    }

    /**
     * Get the number of servo motors in this frame.
     *
     * @return servo motor count
     */
    public int getServoCount() {
        return servosByName.size();
    }

    /**
     * Get the number of rotary motors in this frame.
     *
     * @return rotary motor count
     */
    public int getRotaryMotorCount() {
        return rotaryMotorsByName.size();
    }

    /**
     * Check if a motor with the given name exists in this frame.
     *
     * @param name motor name
     * @return true if motor exists
     */
    public boolean hasMotor(String name) {
        return motorsByName.containsKey(name);
    }

    /**
     * Check if a servo with the given name exists in this frame.
     *
     * @param name servo name
     * @return true if servo exists
     */
    public boolean hasServo(String name) {
        return servosByName.containsKey(name);
    }

    /**
     * Check if a rotary motor with the given name exists in this frame.
     *
     * @param name rotary motor name
     * @return true if rotary motor exists
     */
    public boolean hasRotaryMotor(String name) {
        return rotaryMotorsByName.containsKey(name);
    }

    /**
     * Create an empty frame with no data.
     *
     * @return empty motor data frame
     */
    public static MotorDataFrame empty() {
        return new MotorDataFrame();
    }

    /**
     * Create a builder for constructing MotorDataFrame instances.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return String.format("MotorDataFrame{motors=%d, servos=%d, rotary=%d, timestamp=%d, hasData=%s}",
                getMotorCount(), getServoCount(), getRotaryMotorCount(), timestamp, hasData);
    }

    /**
     * Builder for MotorDataFrame instances.
     */
    public static final class Builder {
        private final Map<String, Motor> motors = new HashMap<>();
        private long timestamp = System.currentTimeMillis();

        private Builder() {}

        /**
         * Add a motor to the frame.
         *
         * @param motor motor to add
         * @return this builder
         */
        public Builder addMotor(Motor motor) {
            if (motor != null) {
                motors.put(motor.getName(), motor);
            }
            return this;
        }

        /**
         * Add multiple motors to the frame.
         *
         * @param motors motors to add
         * @return this builder
         */
        public Builder addMotors(Map<String, Motor> motors) {
            if (motors != null) {
                this.motors.putAll(motors);
            }
            return this;
        }

        /**
         * Set the frame timestamp.
         *
         * @param timestamp timestamp in milliseconds
         * @return this builder
         */
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Build the MotorDataFrame instance.
         *
         * @return new motor data frame
         */
        public MotorDataFrame build() {
            return new MotorDataFrame(motors, timestamp);
        }
    }
}