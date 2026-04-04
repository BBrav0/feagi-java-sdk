/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.pns.outputs;

import java.util.Objects;

/**
 * Rotary motor (DC motor) output.
 *
 * <p>This class represents a rotary/DC motor that receives speed commands from FEAGI.
 * The motor reads values as a normalized range (-1.0 to 1.0 for bidirectional,
 * or 0.0 to 1.0 for unidirectional).</p>
 *
 * <h2>Encoding Modes</h2>
 * <ul>
 *   <li>{@code ABSOLUTE} - Direct speed control</li>
 *   <li>{@code INCREMENTAL} - Incremental speed changes</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Differential drive robot
 * RotaryMotor motorLeft = RotaryMotor.builder()
 *     .bidirectional(true)
 *     .encoding(RotaryMotor.Encoding.ABSOLUTE)
 *     .build();
 *
 * RotaryMotor motorRight = RotaryMotor.builder()
 *     .bidirectional(true)
 *     .encoding(RotaryMotor.Encoding.ABSOLUTE)
 *     .build();
 *
 * motorLeft._registerWithCache();
 * motorRight._registerWithCache();
 *
 * // In main loop
 * while (true) {
 *     motorLeft._readFromCache();
 *     motorRight._readFromCache();
 *     double leftSpeed = motorLeft.getSpeed();   // -1.0 to 1.0
 *     double rightSpeed = motorRight.getSpeed(); // -1.0 to 1.0
 *     setMotorSpeeds(leftSpeed, rightSpeed);
 * }
 * }</pre>
 *
 * @see BaseOutput
 * @see ServoMotor
 */
public class RotaryMotor extends BaseOutput {

    /**
     * Encoding mode for rotary motor commands.
     */
    public enum Encoding {
        /**
         * Absolute mode: Direct speed control
         */
        ABSOLUTE,
        /**
         * Incremental mode: Incremental speed changes
         */
        INCREMENTAL
    }

    /**
     * Encoding mode.
     */
    private final Encoding encoding;

    /**
     * Whether the motor can reverse (bidirectional) or only go forward (unidirectional).
     */
    private final boolean bidirectional;

    /**
     * Current speed from FEAGI.
     * Range: -1.0 to 1.0 if bidirectional, 0.0 to 1.0 if unidirectional.
     */
    private double currentSpeed;

    /**
     * Raw value from FEAGI for debugging.
     */
    private double rawValue;

    /**
     * Creates a new RotaryMotor with the specified configuration.
     *
     * @param builder the builder containing configuration values
     */
    private RotaryMotor(Builder builder) {
        this.encoding = builder.encoding;
        this.bidirectional = builder.bidirectional;
        this.currentSpeed = 0.0;
        this.rawValue = 0.0;
    }

    /**
     * Create a builder for RotaryMotor configuration.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the encoding mode.
     *
     * @return the encoding mode
     */
    public Encoding encoding() {
        return encoding;
    }

    /**
     * Check if the motor is bidirectional.
     *
     * @return true if bidirectional (can reverse), false if unidirectional
     */
    public boolean isBidirectional() {
        return bidirectional;
    }

    /**
     * Get the current motor speed from FEAGI.
     *
     * @return the current speed: -1.0 to 1.0 (bidirectional) or 0.0 to 1.0 (unidirectional)
     */
    public double getSpeed() {
        return currentSpeed;
    }

    /**
     * Get the raw FEAGI value (-1.0 to 1.0).
     *
     * @return the raw value
     */
    public double rawValue() {
        return rawValue;
    }

    /**
     * Get the speed range.
     *
     * @return 2.0 for bidirectional (-1.0 to 1.0), 1.0 for unidirectional (0.0 to 1.0)
     */
    public double speedRange() {
        return bidirectional ? 2.0 : 1.0;
    }

    /**
     * Process a motor command from FEAGI.
     *
     * @param value the normalized value from FEAGI (-1.0 to 1.0)
     */
    public void processMotorCommand(double value) {
        // Store raw value for debugging
        this.rawValue = value;

        if (bidirectional) {
            // Bidirectional: use value directly (-1.0 to 1.0)
            this.currentSpeed = clamp(value, -1.0, 1.0);
        } else {
            // Unidirectional: map (-1.0 to 1.0) to (0.0 to 1.0)
            this.currentSpeed = (value + 1.0) / 2.0;
        }
    }

    @Override
    protected void _registerWithCache() {
        // Register rotary motor with FEAGI cache system
        // In a full implementation, this would:
        // 1. Register with NPU for motor output
        // 2. Configure callback handlers for motor commands
        // 3. Set up motor group and channel

        // For now, just mark as registered with default group
        markRegistered(0);
    }

    @Override
    protected void _readFromCache() {
        // Read motor speed from FEAGI cache
        // In a full implementation, this would:
        // 1. Read value from shared memory region
        // 2. Process motor command through processMotorCommand()
        // 3. Update currentSpeed

        // For now, this is a no-op
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RotaryMotor that = (RotaryMotor) o;

        if (bidirectional != that.bidirectional) return false;
        if (encoding != that.encoding) return false;
        return Objects.equals(groupId, that.groupId);
    }

    @Override
    public int hashCode() {
        int result;
        result = (encoding != null ? encoding.hashCode() : 0);
        result = 31 * result + (bidirectional ? 1 : 0);
        result = 31 * result + (groupId != null ? groupId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "RotaryMotor{" +
            "encoding=" + encoding +
            ", bidirectional=" + bidirectional +
            ", currentSpeed=" + currentSpeed +
            ", groupId=" + groupId +
            '}';
    }

    /**
     * Builder for RotaryMotor configuration.
     */
    public static final class Builder {
        private Encoding encoding = Encoding.ABSOLUTE;
        private boolean bidirectional = true;

        private Builder() {}

        /**
         * Set the encoding mode.
         *
         * @param encoding ABSOLUTE or INCREMENTAL
         * @return this builder
         * @throws NullPointerException if encoding is null
         */
        public Builder encoding(Encoding encoding) {
            this.encoding = Objects.requireNonNull(encoding, "encoding must not be null");
            return this;
        }

        /**
         * Set whether the motor is bidirectional.
         *
         * @param bidirectional true for bidirectional (can reverse), false for unidirectional
         * @return this builder
         */
        public Builder bidirectional(boolean bidirectional) {
            this.bidirectional = bidirectional;
            return this;
        }

        /**
         * Build the RotaryMotor instance.
         *
         * @return a new RotaryMotor with the configured values
         */
        public RotaryMotor build() {
            return new RotaryMotor(this);
        }
    }
}
