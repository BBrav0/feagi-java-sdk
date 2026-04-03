/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core.motor;

import java.util.Objects;

/**
 * Represents a rotary/continuous motor controlled by FEAGI.
 *
 * <p>Rotary motors are typically used for wheels, fans, or other continuously
 * rotating actuators. They provide speed and direction control based on
 * FEAGI output values.
 */
public final class RotaryMotor implements Motor {

    /**
     * Default maximum speed (units per second).
     */
    public static final double DEFAULT_MAX_SPEED = 100.0;

    /**
     * Speed unit types.
     */
    public enum SpeedUnit {
        /**
         * Speed as percentage (0-100%).
         */
        PERCENTAGE,
        /**
         * Speed as rotations per minute.
         */
        RPM,
        /**
         * Speed as meters per second (for wheeled robots).
         */
        METERS_PER_SECOND,
        /**
         * Raw normalized value (0.0-1.0).
         */
        NORMALIZED
    }

    private final String name;
    private final int groupId;
    private final int outputIndex;
    private final double maxSpeed;
    private final SpeedUnit speedUnit;
    private final boolean invertDirection;
    private final boolean bidirectional;

    private volatile double rawValue;
    private volatile boolean hasData;
    private volatile long lastUpdateTimestamp;

    /**
     * Create a rotary motor with default settings (100% max speed, percentage unit).
     *
     * @param name        unique motor name/identifier
     * @param groupId     group ID (0-255)
     * @param outputIndex output index within the group
     */
    public RotaryMotor(String name, int groupId, int outputIndex) {
        this(name, groupId, outputIndex, DEFAULT_MAX_SPEED, SpeedUnit.PERCENTAGE, false, false);
    }

    /**
     * Create a rotary motor with custom settings.
     *
     * @param name           unique motor name/identifier
     * @param groupId        group ID (0-255)
     * @param outputIndex    output index within the group
     * @param maxSpeed       maximum speed value
     * @param speedUnit      unit for speed interpretation
     * @param invertDirection if true, invert the speed/direction mapping
     * @param bidirectional   if true, motor supports negative speeds (forward/backward)
     */
    public RotaryMotor(String name, int groupId, int outputIndex,
                       double maxSpeed, SpeedUnit speedUnit,
                       boolean invertDirection, boolean bidirectional) {
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
        if (maxSpeed <= 0) {
            throw new IllegalArgumentException("maxSpeed must be > 0");
        }
        Objects.requireNonNull(speedUnit, "speedUnit must not be null");

        this.groupId = groupId;
        this.outputIndex = outputIndex;
        this.maxSpeed = maxSpeed;
        this.speedUnit = speedUnit;
        this.invertDirection = invertDirection;
        this.bidirectional = bidirectional;
        this.rawValue = 0.0;
        this.hasData = false;
        this.lastUpdateTimestamp = 0;
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
        return rawValue;
    }

    @Override
    public boolean hasData() {
        return hasData;
    }

    @Override
    public long getLastUpdateTimestamp() {
        return lastUpdateTimestamp;
    }

    /**
     * Return the maximum speed value.
     *
     * @return maximum speed
     */
    public double getMaxSpeed() {
        return maxSpeed;
    }

    /**
     * Return the speed unit.
     *
     * @return speed unit type
     */
    public SpeedUnit getSpeedUnit() {
        return speedUnit;
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
     * Return whether motor is bidirectional.
     *
     * @return true if bidirectional
     */
    public boolean isBidirectional() {
        return bidirectional;
    }

    /**
     * Get the current speed.
     *
     * <p>For unidirectional motors, the speed is mapped from raw value (0.0-1.0)
     * to (0 to maxSpeed). For bidirectional motors, the raw value (0.0-1.0) is
     * mapped to (-maxSpeed to +maxSpeed), where 0.5 represents zero speed.
     *
     * @return current speed in the configured unit
     */
    public double getSpeed() {
        return mapRawToSpeed(rawValue);
    }

    /**
     * Get the speed as a normalized value between 0.0 and 1.0.
     *
     * <p>For bidirectional motors, the range is -1.0 to 1.0.
     *
     * @return normalized speed
     */
    public double getNormalizedSpeed() {
        double normalized = clamp(rawValue);
        if (bidirectional) {
            // Map [0, 1] to [-1, 1]
            normalized = (normalized * 2.0) - 1.0;
        }
        if (invertDirection) {
            normalized = -normalized;
        }
        return normalized;
    }

    /**
     * Get the speed as a percentage of maximum speed.
     *
     * <p>For bidirectional motors, negative percentage indicates reverse direction.
     *
     * @return speed as percentage (-100 to 100 for bidirectional, 0 to 100 otherwise)
     */
    public double getSpeedPercentage() {
        double speed = getSpeed();
        return (speed / maxSpeed) * 100.0;
    }

    /**
     * Get the current direction.
     *
     * <p>For unidirectional motors, this always returns 1 (forward).
     * For bidirectional motors, returns 1 for forward, -1 for backward, 0 for stopped.
     *
     * @return direction indicator (-1, 0, or 1)
     */
    public int getDirection() {
        if (!bidirectional) {
            return 1;
        }
        double normalized = getNormalizedSpeed();
        if (normalized > 0.01) return 1;
        if (normalized < -0.01) return -1;
        return 0;
    }

    /**
     * Check if motor is currently stopped.
     *
     * @return true if speed is effectively zero
     */
    public boolean isStopped() {
        return Math.abs(getNormalizedSpeed()) < 0.01;
    }

    /**
     * Check if motor is moving forward.
     *
     * @return true if moving forward
     */
    public boolean isMovingForward() {
        return getDirection() > 0;
    }

    /**
     * Check if motor is moving backward.
     *
     * @return true if moving backward (bidirectional only)
     */
    public boolean isMovingBackward() {
        return bidirectional && getDirection() < 0;
    }

    /**
     * Update the motor with a new raw value from FEAGI.
     *
     * @param rawValue  raw value from FEAGI (typically 0.0-1.0)
     * @param timestamp update timestamp in milliseconds
     */
    public void updateValue(double rawValue, long timestamp) {
        this.rawValue = rawValue;
        this.hasData = true;
        this.lastUpdateTimestamp = timestamp;
    }

    /**
     * Clear the data flag (called when no data received in a frame).
     */
    public void clearData() {
        this.hasData = false;
    }

    /**
     * Create a builder for constructing RotaryMotor instances.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Map raw value to speed.
     */
    private double mapRawToSpeed(double raw) {
        double normalized = clamp(raw);

        double speed;
        if (bidirectional) {
            // Map [0, 1] to [-maxSpeed, +maxSpeed]
            speed = ((normalized * 2.0) - 1.0) * maxSpeed;
        } else {
            // Map [0, 1] to [0, maxSpeed]
            speed = normalized * maxSpeed;
        }

        if (invertDirection) {
            speed = -speed;
        }

        return speed;
    }

    /**
     * Clamp value to [0.0, 1.0].
     */
    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    @Override
    public String toString() {
        String direction = bidirectional ?
                (isMovingForward() ? "FWD" : (isMovingBackward() ? "REV" : "STOP")) : "FWD";
        return String.format("RotaryMotor{name='%s', groupId=%d, outputIndex=%d, speed=%.2f %s, dir=%s}",
                name, groupId, outputIndex, Math.abs(getSpeed()), speedUnit, direction);
    }

    /**
     * Builder for RotaryMotor instances.
     */
    public static final class Builder {
        private String name;
        private int groupId;
        private int outputIndex;
        private double maxSpeed = DEFAULT_MAX_SPEED;
        private SpeedUnit speedUnit = SpeedUnit.PERCENTAGE;
        private boolean invertDirection = false;
        private boolean bidirectional = false;

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
         * Set the maximum speed.
         */
        public Builder maxSpeed(double maxSpeed) {
            this.maxSpeed = maxSpeed;
            return this;
        }

        /**
         * Set the speed unit.
         */
        public Builder speedUnit(SpeedUnit speedUnit) {
            this.speedUnit = speedUnit;
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
         * Set whether motor is bidirectional.
         */
        public Builder bidirectional(boolean bidirectional) {
            this.bidirectional = bidirectional;
            return this;
        }

        /**
         * Build the RotaryMotor instance.
         */
        public RotaryMotor build() {
            return new RotaryMotor(name, groupId, outputIndex, maxSpeed, speedUnit,
                    invertDirection, bidirectional);
        }
    }
}