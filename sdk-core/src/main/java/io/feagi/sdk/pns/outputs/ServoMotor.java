/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.pns.outputs;

import java.util.Objects;

/**
 * Servo motor output (positional servo).
 *
 * <p>This class represents a servo motor that receives position commands from FEAGI.
 * The servo reads values as a normalized range (-1.0 to 1.0) from FEAGI and
 * automatically maps them to the specified angle range.</p>
 *
 * <h2>Encoding Modes</h2>
 * <ul>
 *   <li>{@code ABSOLUTE} - Maps -1.0 to minAngle, 0.0 to center, 1.0 to maxAngle</li>
 *   <li>{@code INCREMENTAL} - Uses normalized delta-per-update for incremental changes</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ServoMotor servo = ServoMotor.builder()
 *     .angleRange(0.0, 180.0)
 *     .encoding(ServoMotor.Encoding.ABSOLUTE)
 *     .gain(1.0)
 *     .build();
 *
 * servo._registerWithCache();
 *
 * // In main loop
 * while (true) {
 *     servo._readFromCache();  // Update from FEAGI
 *     double angle = servo.getAngle();
 *     setServoHardware(angle);  // Your hardware API
 * }
 * }</pre>
 *
 * @see BaseOutput
 * @see RotaryMotor
 */
public class ServoMotor extends BaseOutput {

    /**
     * Encoding mode for servo motor commands.
     */
    public enum Encoding {
        /**
         * Absolute mode: -1.0 -> minAngle, 0.0 -> center, 1.0 -> maxAngle
         */
        ABSOLUTE,
        /**
         * Incremental mode: Uses normalized delta-per-update
         */
        INCREMENTAL
    }

    /**
     * Minimum angle in degrees.
     */
    private final double minAngle;

    /**
     * Maximum angle in degrees.
     */
    private final double maxAngle;

    /**
     * Encoding mode.
     */
    private final Encoding encoding;

    /**
     * Gain (amplification factor) for motor commands.
     */
    private final double gain;

    /**
     * Incremental step ratio for INCREMENTAL mode.
     */
    private final double incrementalStepRatio;

    /**
     * Current angle from FEAGI.
     */
    private double currentAngle;

    /**
     * Raw value from FEAGI for debugging.
     */
    private double rawValue;

    /**
     * Creates a new ServoMotor with the specified configuration.
     *
     * @param builder the builder containing configuration values
     */
    private ServoMotor(Builder builder) {
        this.minAngle = builder.minAngle;
        this.maxAngle = builder.maxAngle;
        this.encoding = builder.encoding;
        this.gain = builder.gain;
        this.incrementalStepRatio = builder.incrementalStepRatio;
        this.currentAngle = (minAngle + maxAngle) / 2.0;  // Start at center
        this.rawValue = 0.0;
    }

    /**
     * Create a builder for ServoMotor configuration.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the minimum angle in degrees.
     *
     * @return the minimum angle
     */
    public double minAngle() {
        return minAngle;
    }

    /**
     * Get the maximum angle in degrees.
     *
     * @return the maximum angle
     */
    public double maxAngle() {
        return maxAngle;
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
     * Get the gain (amplification factor).
     *
     * @return the gain
     */
    public double gain() {
        return gain;
    }

    /**
     * Get the incremental step ratio.
     *
     * @return the step ratio for INCREMENTAL mode
     */
    public double incrementalStepRatio() {
        return incrementalStepRatio;
    }

    /**
     * Get the current servo angle from FEAGI.
     *
     * @return the current angle in degrees
     */
    public double getAngle() {
        return currentAngle;
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
     * Get the angle range (maxAngle - minAngle).
     *
     * @return the angle range in degrees
     */
    public double angleRange() {
        return maxAngle - minAngle;
    }

    /**
     * Get the center angle.
     *
     * @return the center angle in degrees
     */
    public double centerAngle() {
        return (minAngle + maxAngle) / 2.0;
    }

    /**
     * Process a motor command from FEAGI.
     *
     * @param value the normalized value from FEAGI (-1.0 to 1.0)
     */
    public void processMotorCommand(double value) {
        // Store raw value for debugging
        this.rawValue = value;

        // Apply gain to amplify/dampen the signal
        double scaledValue = value * gain;

        // Clamp to [-1.0, 1.0] after gain application
        scaledValue = clamp(scaledValue, -1.0, 1.0);

        double center = centerAngle();
        double halfRange = angleRange() / 2.0;

        if (encoding == Encoding.INCREMENTAL) {
            // Incremental mode: value represents delta
            double step = halfRange * incrementalStepRatio;
            double nextAngle = currentAngle + (scaledValue * step);
            this.currentAngle = clamp(nextAngle, minAngle, maxAngle);
        } else {
            // Absolute mode: -1.0 -> min, 0.0 -> center, 1.0 -> max
            this.currentAngle = center + (scaledValue * halfRange);
        }
    }

    @Override
    protected void _registerWithCache() {
        // Register servo motor with FEAGI cache system
        // In a full implementation, this would:
        // 1. Register with NPU for motor output
        // 2. Configure callback handlers for motor commands
        // 3. Set up motor group and channel

        // For now, just mark as registered with default group
        markRegistered(0);
    }

    @Override
    protected void _readFromCache() {
        // Read servo position from FEAGI cache
        // In a full implementation, this would:
        // 1. Read value from shared memory region
        // 2. Process motor command through processMotorCommand()
        // 3. Update currentAngle

        // For now, this is a no-op
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ServoMotor that = (ServoMotor) o;

        if (Double.compare(that.minAngle, minAngle) != 0) return false;
        if (Double.compare(that.maxAngle, maxAngle) != 0) return false;
        if (Double.compare(that.gain, gain) != 0) return false;
        if (Double.compare(that.incrementalStepRatio, incrementalStepRatio) != 0) return false;
        if (encoding != that.encoding) return false;
        return Objects.equals(groupId, that.groupId);
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(minAngle);
        result = (int) (temp ^ (temp >>> 32));
        result = 31 * result + (int) (Double.doubleToLongBits(maxAngle) ^ (Double.doubleToLongBits(maxAngle) >>> 32));
        result = 31 * result + (encoding != null ? encoding.hashCode() : 0);
        temp = Double.doubleToLongBits(gain);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(incrementalStepRatio);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (groupId != null ? groupId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ServoMotor{" +
            "minAngle=" + minAngle +
            ", maxAngle=" + maxAngle +
            ", encoding=" + encoding +
            ", gain=" + gain +
            ", currentAngle=" + currentAngle +
            ", groupId=" + groupId +
            '}';
    }

    /**
     * Builder for ServoMotor configuration.
     */
    public static final class Builder {
        private double minAngle = Double.NaN;
        private double maxAngle = Double.NaN;
        private Encoding encoding = Encoding.ABSOLUTE;
        private double gain = 1.0;
        private double incrementalStepRatio = 0.05;

        private Builder() {}

        /**
         * Set the angle range.
         *
         * @param minAngle the minimum angle in degrees
         * @param maxAngle the maximum angle in degrees
         * @return this builder
         * @throws IllegalArgumentException if minAngle >= maxAngle
         */
        public Builder angleRange(double minAngle, double maxAngle) {
            if (minAngle >= maxAngle) {
                throw new IllegalArgumentException(
                    "minAngle must be less than maxAngle, got [" + minAngle + ", " + maxAngle + "]");
            }
            this.minAngle = minAngle;
            this.maxAngle = maxAngle;
            return this;
        }

        /**
         * Set the minimum angle.
         *
         * @param minAngle the minimum angle in degrees
         * @return this builder
         * @throws IllegalArgumentException if maxAngle is already set and minAngle >= maxAngle
         */
        public Builder minAngle(double minAngle) {
            if (!Double.isNaN(this.maxAngle) && minAngle >= this.maxAngle) {
                throw new IllegalArgumentException(
                    "minAngle must be less than maxAngle, got [" + minAngle + ", " + this.maxAngle + "]");
            }
            this.minAngle = minAngle;
            return this;
        }

        /**
         * Set the maximum angle.
         *
         * @param maxAngle the maximum angle in degrees
         * @return this builder
         * @throws IllegalArgumentException if minAngle is already set and minAngle >= maxAngle
         */
        public Builder maxAngle(double maxAngle) {
            if (!Double.isNaN(this.minAngle) && this.minAngle >= maxAngle) {
                throw new IllegalArgumentException(
                    "maxAngle must be greater than minAngle, got [" + this.minAngle + ", " + maxAngle + "]");
            }
            this.maxAngle = maxAngle;
            return this;
        }

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
         * Set the gain (amplification factor).
         *
         * @param gain the gain value (must be positive)
         * @return this builder
         * @throws IllegalArgumentException if gain is not positive
         */
        public Builder gain(double gain) {
            if (gain <= 0) {
                throw new IllegalArgumentException("gain must be positive, got: " + gain);
            }
            this.gain = gain;
            return this;
        }

        /**
         * Set the incremental step ratio.
         *
         * @param ratio the step ratio (must be in range (0.0, 1.0])
         * @return this builder
         * @throws IllegalArgumentException if ratio is not in valid range
         */
        public Builder incrementalStepRatio(double ratio) {
            if (ratio <= 0 || ratio > 1.0) {
                throw new IllegalArgumentException(
                    "incrementalStepRatio must be in range (0.0, 1.0], got: " + ratio);
            }
            this.incrementalStepRatio = ratio;
            return this;
        }

        /**
         * Build the ServoMotor instance.
         *
         * @return a new ServoMotor with the configured values
         * @throws IllegalArgumentException if minAngle >= maxAngle
         */
        public ServoMotor build() {
            // Apply defaults if not set
            double finalMinAngle = Double.isNaN(this.minAngle) ? 0.0 : this.minAngle;
            double finalMaxAngle = Double.isNaN(this.maxAngle) ? 180.0 : this.maxAngle;

            // Validate range
            if (finalMinAngle >= finalMaxAngle) {
                throw new IllegalArgumentException(
                    "minAngle must be less than maxAngle, got [" + finalMinAngle + ", " + finalMaxAngle + "]");
            }

            // Create a new builder with validated values for the constructor
            Builder validated = new Builder();
            validated.minAngle = finalMinAngle;
            validated.maxAngle = finalMaxAngle;
            validated.encoding = this.encoding;
            validated.gain = this.gain;
            validated.incrementalStepRatio = this.incrementalStepRatio;

            return new ServoMotor(validated);
        }
    }
}
