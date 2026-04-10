/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core.motor;

import java.util.Objects;

/**
 * Represents a positional servo motor controlled by FEAGI.
 *
 * <p>Servo motors are typically used for precise angular positioning,
 * such as robotic arm joints, steering mechanisms, or camera panning.
 * The angle is derived from the raw FEAGI output value and can be
 * configured for different angle ranges.
 */
public final class ServoMotor implements Motor {

    /**
     * Default minimum angle in degrees.
     */
    public static final double DEFAULT_MIN_ANGLE = 0.0;

    /**
     * Default maximum angle in degrees.
     */
    public static final double DEFAULT_MAX_ANGLE = 180.0;
    private final String name;
    private final int groupId;
    private final int outputIndex;
    private final double minAngle;
    private final double maxAngle;
    private final boolean invertDirection;

    // Immutable state holder for atomic updates
    private volatile MotorState state = new MotorState(0.0, false, 0);
    /**
     * Create a servo motor with default angle range (0-180 degrees).
     *
     * @param name        unique motor name/identifier
     * @param groupId     group ID (0-255)
     * @param outputIndex output index within the group
     */
    public ServoMotor(String name, int groupId, int outputIndex) {
        this(name, groupId, outputIndex, DEFAULT_MIN_ANGLE, DEFAULT_MAX_ANGLE, false);
    }
    /**
     * Create a servo motor with custom angle range.
     *
     * @param name           unique motor name/identifier
     * @param groupId        group ID (0-255)
     * @param outputIndex    output index within the group
     * @param minAngle       minimum angle in degrees
     * @param maxAngle       maximum angle in degrees
     * @param invertDirection if true, invert the angle mapping
     */
    public ServoMotor(String name, int groupId, int outputIndex,
                       double minAngle, double maxAngle, boolean invertDirection) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        if (groupId < 0 || groupId > 255) {
            throw new IllegalArgumentException("groupId must be in [0, 255]");
        }
        if (outputIndex < 0) {
            throw new IllegalArgumentException("outputIndex must be >= 0");
        }
        if (minAngle >= maxAngle) {
            throw new IllegalArgumentException("minAngle must be less than maxAngle");
        }
        this.groupId = groupId;
        this.outputIndex = outputIndex;
        this.minAngle = minAngle;
        this.maxAngle = maxAngle;
        this.invertDirection = invertDirection;
    }
    @Override
    public String getName() {
        return name;
    }
    @Override
    public int getGroupId() {
        return groupId;
    }
    @Override
    public int getOutputIndex() {
        return outputIndex;
    }
    @Override
    public double getRawValue() {
        return state.rawValue;
    }
    @Override
    public boolean hasData() {
        return state.hasData;
    }
    @Override
    public long getLastUpdateTimestamp() {
        return state.timestamp;
    }
    /**
     * Return the minimum configured angle.
     *
     * @return minimum angle in degrees
     */
    public double getMinAngle() {
        return minAngle;
    }
    /**
     * Return the maximum configured angle.
     *
     * @return maximum angle in degrees
     */
    public double getMaxAngle() {
        return maxAngle;
    }
    /**
     * Return whether direction is inverted.
     *
     * @return true if inverted
     */
    public boolean isInverted() {
        return invertDirection;
    }
    /**
     * Get the current angle in degrees.
     *
     * <p>The angle is computed by mapping the raw FEAGI value (typically 0.0-1.0)
     * to the configured angle range.
     *
     * @return current angle in degrees
     */
    public double getAngle() {
        return mapRawToAngle(state.rawValue);
    }
    /**
     * Get the current angle in radians.
     *
     * @return current angle in radians
     */
    public double getAngleRadians() {
        return Math.toRadians(getAngle());
    }
    /**
     * Get the angle as a normalized value between 0.0 and 1.0.
     *
     * @return normalized position (0.0 = min angle, 1.0 = max angle)
     */
    public double getNormalizedPosition() {
        if (invertDirection) {
            return 1.0 - clamp(state.rawValue);
        }
        return clamp(state.rawValue);
    }
    /**
     * Update the motor with a new raw value from FEAGI.
     *
     * <p>This method performs an atomic update of all three state fields
     * using a single volatile reference to the immutable MotorState record.
     *
     * @param rawValue  raw value from FEAGI (typically 0.0-1.0)
     * @param timestamp update timestamp in milliseconds
     */
    public void updateValue(double rawValue, long timestamp) {
        this.state = new MotorState(rawValue, true, timestamp);
    }
    /**
     * Clear the data flag (called when no data received in a frame).
     */
    public void clearData() {
        this.state = new MotorState(0.0, false, 0);
    }
    /**
     * Return the motor type.
     *
     * @return motor type
     */
    @Override
    public MotorType getMotorType() {
        return MotorType.SERVO;
    }
    /**
     * Create an immutable snapshot of this motor's current state.
     *
     * @param rawValue  the raw FEAGI value
     * @param timestamp the snapshot timestamp
     * @return immutable snapshot
     */
    @Override
    public Snapshot createSnapshot(double rawValue, long timestamp) {
        return new ServoSnapshotImpl(this, rawValue, timestamp);
    }
    /**
     * Map raw value to angle in degrees.
     */
    private double mapRawToAngle(double raw) {
        double normalized = clamp(raw);
        if (invertDirection) {
            normalized = 1.0 - normalized;
        }
        return minAngle + normalized * (maxAngle - minAngle);
    }
    /**
     * Clamp value to [0.0, 1.0].
     */
    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
    /**
     * Immutable holder for atomic motor state updates.
     */
    private static final class MotorState {
        final double rawValue;
        final boolean hasData;
        final long timestamp;
        MotorState(double rawValue, boolean hasData, long timestamp) {
            this.rawValue = rawValue;
            this.hasData = hasData;
            this.timestamp = timestamp;
        }
    }
    /**
     * Immutable snapshot implementation for servo motors.
     */
    private final class ServoSnapshotImpl implements ServoSnapshot {
        private final String name;
        private final double rawValue;
        private final long timestamp;
        private final double minAngle;
        private final double maxAngle;
        private final boolean invertDirection;
        private final double angle;
        ServoSnapshotImpl(ServoMotor motor, double rawValue, long timestamp) {
            this.name = motor.getName();
            this.rawValue = rawValue;
            this.timestamp = timestamp;
            this.minAngle = motor.getMinAngle();
            this.maxAngle = motor.getMaxAngle();
            this.invertDirection = motor.isInverted();
            this.angle = mapRawToAngle(rawValue, minAngle, maxAngle, invertDirection);
        }
        private static double mapRawToAngle(double raw, double minAngle, double maxAngle, boolean invert) {
            double normalized = clamp(raw);
            if (invert) {
                normalized = 1.0 - normalized;
            }
            return minAngle + normalized * (maxAngle - minAngle);
        }
        private static double clamp(double value) {
            return Math.max(0.0, Math.min(1.0, value));
        }
        @Override
        public String getName() { return name; }
        @Override
        public MotorType getMotorType() { return MotorType.SERVO; }
        @Override
        public double getRawValue() { return rawValue; }
        @Override
        public long getTimestamp() { return timestamp; }
        @Override
        public double getAngle() { return angle; }
        @Override
        public double getAngleRadians() {
            return Math.toRadians(angle);
        }
        @Override
        public double getNormalizedPosition() {
            if (invertDirection) {
                return 1.0 - clamp(rawValue);
            }
            return clamp(rawValue);
        }
        @Override
        public String toString() {
            return String.format("ServoMotor{name='%s', groupId=%d, outputIndex=%d, angle=%.2f°, raw=%.4f}",
                    name, groupId, outputIndex, angle, rawValue);
        }
    }
    @Override
    public String toString() {
        return String.format("ServoMotor{name='%s', groupId=%d, outputIndex=%d, angle=%.2f°, raw=%.4f}",
                name, groupId, outputIndex, getAngle(), getRawValue());
    }
    /**
     * Create a new builder for ServoMotor instances.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ServoMotor instances.
     */
    public static final class Builder {
        private String name;
        private int groupId;
        private int outputIndex;
        private double minAngle = DEFAULT_MIN_ANGLE;
        private double maxAngle = DEFAULT_MAX_ANGLE;
        private boolean invertDirection = false;
        private Builder() {}
        /**
         * Set the motor name.
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        /**
         * Set the group ID.
         */
        public Builder groupId(int groupId) {
            this.groupId = groupId;
            return this;
        }
        /**
         * Set the output index.
         */
        public Builder outputIndex(int outputIndex) {
            this.outputIndex = outputIndex;
            return this;
        }
        /**
         * Set the angle range.
         */
        public Builder angleRange(double minAngle, double maxAngle) {
            this.minAngle = minAngle;
            this.maxAngle = maxAngle;
            return this;
        }
        /**
         * Set whether to invert direction.
         */
        public Builder invertDirection(boolean invert) {
            this.invertDirection = invert;
            return this;
        }
        /**
         * Build the ServoMotor instance.
         */
        public ServoMotor build() {
            return new ServoMotor(name, groupId, outputIndex, minAngle, maxAngle, invertDirection);
        }
    }
}