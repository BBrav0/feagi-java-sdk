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
 * decoded from the raw FEAGI byte format. This frame stores immutable snapshots
 * of motor state, making it safe to store and use across threads.
 *
 * <p>Example usage:
 * <pre>{@code
 * MotorDataFrame frame = brainOutput.receive();
 * if (frame != null && frame.hasData()) {
 *     // Access servo snapshot by name
 *     Motor.ServoSnapshot arm = frame.getServoSnapshot("arm_joint");
 *     if (arm != null) {
 *         double angle = arm.getAngle();
 *     }
 *
 *     // Access rotary snapshot by name
 *     Motor.RotarySnapshot wheel = frame.getRotarySnapshot("left_wheel");
 *     if (wheel != null) {
 *         double speed = wheel.getSpeed();
 *     }
 *
 *     // Access all servo snapshots
 *     for (Motor.ServoSnapshot servo : frame.getServoSnapshots().values()) {
 *         System.out.println(servo.getName() + ": " + servo.getAngle() + "°");
 *     }
 * }
 * }</pre>
 */
public final class MotorDataFrame {

    private final Map<String, Motor.Snapshot> snapshotsByName;
    private final Map<String, Motor.ServoSnapshot> servoSnapshotsByName;
    private final Map<String, Motor.RotarySnapshot> rotarySnapshotsByName;
    private final long timestamp;
    private final boolean hasData;

    /**
     * Create an empty frame with no data.
     */
    public MotorDataFrame() {
        this.snapshotsByName = Collections.emptyMap();
        this.servoSnapshotsByName = Collections.emptyMap();
        this.rotarySnapshotsByName = Collections.emptyMap();
        this.timestamp = System.currentTimeMillis();
        this.hasData = false;
    }

    /**
     * Create a frame from a map of snapshots.
     *
     * @param snapshotsByName map of motor name to snapshot
     * @param timestamp       frame timestamp in milliseconds
     */
    private MotorDataFrame(Map<String, Motor.Snapshot> snapshotsByName, long timestamp) {
        this.snapshotsByName = snapshotsByName != null ?
                Collections.unmodifiableMap(new HashMap<>(snapshotsByName)) :
                Collections.emptyMap();
        this.timestamp = timestamp;
        this.hasData = !this.snapshotsByName.isEmpty();

        // Index snapshots by type
        Map<String, Motor.ServoSnapshot> servos = new HashMap<>();
        Map<String, Motor.RotarySnapshot> rotaries = new HashMap<>();

        for (Map.Entry<String, Motor.Snapshot> entry : this.snapshotsByName.entrySet()) {
            Motor.Snapshot snapshot = entry.getValue();
            if (snapshot.isServo()) {
                servos.put(entry.getKey(), snapshot.asServo());
            } else if (snapshot.isRotary()) {
                rotaries.put(entry.getKey(), snapshot.asRotary());
            }
        }

        this.servoSnapshotsByName = Collections.unmodifiableMap(servos);
        this.rotarySnapshotsByName = Collections.unmodifiableMap(rotaries);
    }

    /**
     * Create a MotorDataFrame from a map of snapshots.
     *
     * @param snapshots map of motor name to snapshot
     * @param timestamp frame timestamp in milliseconds
     * @return new MotorDataFrame instance
     */
    public static MotorDataFrame fromSnapshots(Map<String, Motor.Snapshot> snapshots, long timestamp) {
        return new MotorDataFrame(snapshots, timestamp);
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
     * Get a motor snapshot by name.
     *
     * @param name motor name
     * @return snapshot, or null if not found
     */
    public Motor.Snapshot getSnapshot(String name) {
        return snapshotsByName.get(name);
    }

    /**
     * Get a motor snapshot by name wrapped in Optional.
     *
     * @param name motor name
     * @return Optional containing snapshot, or empty if not found
     */
    public Optional<Motor.Snapshot> getSnapshotOptional(String name) {
        return Optional.ofNullable(snapshotsByName.get(name));
    }

    /**
     * Get a servo snapshot by name.
     *
     * @param name motor name
     * @return servo snapshot, or null if not found or not a servo
     */
    public Motor.ServoSnapshot getServoSnapshot(String name) {
        return servoSnapshotsByName.get(name);
    }

    /**
     * Get a servo snapshot by name wrapped in Optional.
     *
     * @param name motor name
     * @return Optional containing servo snapshot, or empty if not found
     */
    public Optional<Motor.ServoSnapshot> getServoSnapshotOptional(String name) {
        return Optional.ofNullable(servoSnapshotsByName.get(name));
    }

    /**
     * Get a rotary snapshot by name.
     *
     * @param name motor name
     * @return rotary snapshot, or null if not found or not a rotary
     */
    public Motor.RotarySnapshot getRotarySnapshot(String name) {
        return rotarySnapshotsByName.get(name);
    }

    /**
     * Get a rotary snapshot by name wrapped in Optional.
     *
     * @param name motor name
     * @return Optional containing rotary snapshot, or empty if not found
     */
    public Optional<Motor.RotarySnapshot> getRotarySnapshotOptional(String name) {
        return Optional.ofNullable(rotarySnapshotsByName.get(name));
    }

    /**
     * Get all snapshots in this frame.
     *
     * @return unmodifiable map of all snapshots by name
     */
    public Map<String, Motor.Snapshot> getAllSnapshots() {
        return snapshotsByName;
    }

    /**
     * Get all servo snapshots in this frame.
     *
     * @return unmodifiable map of servo snapshots by name
     */
    public Map<String, Motor.ServoSnapshot> getServoSnapshots() {
        return servoSnapshotsByName;
    }

    /**
     * Get all rotary snapshots in this frame.
     *
     * @return unmodifiable map of rotary snapshots by name
     */
    public Map<String, Motor.RotarySnapshot> getRotarySnapshots() {
        return rotarySnapshotsByName;
    }

    /**
     * Get the number of motors in this frame.
     *
     * @return motor count
     */
    public int getMotorCount() {
        return snapshotsByName.size();
    }

    /**
     * Get the number of servo motors in this frame.
     *
     * @return servo motor count
     */
    public int getServoCount() {
        return servoSnapshotsByName.size();
    }

    /**
     * Get the number of rotary motors in this frame.
     *
     * @return rotary motor count
     */
    public int getRotaryMotorCount() {
        return rotarySnapshotsByName.size();
    }

    /**
     * Check if a motor with the given name exists in this frame.
     *
     * @param name motor name
     * @return true if motor exists
     */
    public boolean hasMotor(String name) {
        return snapshotsByName.containsKey(name);
    }

    /**
     * Check if a servo with the given name exists in this frame.
     *
     * @param name servo name
     * @return true if servo exists
     */
    public boolean hasServo(String name) {
        return servoSnapshotsByName.containsKey(name);
    }

    /**
     * Check if a rotary motor with the given name exists in this frame.
     *
     * @param name rotary motor name
     * @return true if rotary motor exists
     */
    public boolean hasRotaryMotor(String name) {
        return rotarySnapshotsByName.containsKey(name);
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
        private final Map<String, Motor.Snapshot> snapshots = new HashMap<>();
        private long timestamp = System.currentTimeMillis();

        private Builder() {}

        /**
         * Add a snapshot to the frame.
         *
         * @param snapshot snapshot to add
         * @return this builder
         */
        public Builder addSnapshot(Motor.Snapshot snapshot) {
            if (snapshot != null) {
                snapshots.put(snapshot.getName(), snapshot);
            }
            return this;
        }

        /**
         * Add multiple snapshots to the frame.
         *
         * @param snapshots snapshots to add
         * @return this builder
         */
        public Builder addSnapshots(Map<String, Motor.Snapshot> snapshots) {
            if (snapshots != null) {
                this.snapshots.putAll(snapshots);
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
            return new MotorDataFrame(snapshots, timestamp);
        }
    }
}