/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.pns.inputs;

import java.util.Objects;

/**
 * Numeric stream input type for FEAGI PNS.
 *
 * <p>This class represents a stream of numeric data that can be used for
 * various sensor inputs, analog readings, or other continuous numeric
 * data sources. It supports configurable precision and scaling.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * NumericStream sensor = NumericStream.builder()
 *     .precision(0.001)
 *     .minValue(-10.0)
 *     .maxValue(10.0)
 *     .groupId(0)
 *     .build();
 *
 * sensor._registerWithCache();
 * sensor.writeValue(3.14159);
 * }</pre>
 *
 * @see BaseInput
 * @see InfraredInput
 */
public class NumericStream extends BaseInput<Double> {

    /**
     * Precision of the numeric value (smallest representable difference).
     */
    private final double precision;

    /**
     * Minimum allowed value.
     */
    private final double minValue;

    /**
     * Maximum allowed value.
     */
    private final double maxValue;

    /**
     * Scaling factor for normalization.
     */
    private final double scaleFactor;

    /**
     * Whether values should be clamped to range.
     */
    private final boolean clampToRange;

    /**
     * Current value.
     */
    private Double currentValue;

    /**
     * Creates a new NumericStream with the specified configuration.
     *
     * @param builder the builder containing configuration values
     */
    protected NumericStream(Builder builder) {
        super(builder.groupId);
        this.precision = builder.precision;
        this.minValue = builder.minValue;
        this.maxValue = builder.maxValue;
        this.scaleFactor = builder.scaleFactor;
        this.clampToRange = builder.clampToRange;
        this.currentValue = null;
    }

    /**
     * Creates a new NumericStream with specified values (for subclass use).
     *
     * @param groupId the group ID
     * @param precision the precision
     * @param minValue the minimum value
     * @param maxValue the maximum value
     * @param scaleFactor the scale factor
     * @param clampToRange whether to clamp values to range
     */
    protected NumericStream(int groupId, double precision, double minValue, double maxValue,
                            double scaleFactor, boolean clampToRange) {
        super(groupId);
        this.precision = precision;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.scaleFactor = scaleFactor;
        this.clampToRange = clampToRange;
        this.currentValue = null;
    }

    /**
     * Create a builder for NumericStream configuration.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the precision (smallest representable difference).
     *
     * @return the precision value
     */
    public double precision() {
        return precision;
    }

    /**
     * Get the minimum allowed value.
     *
     * @return the minimum value
     */
    public double minValue() {
        return minValue;
    }

    /**
     * Get the maximum allowed value.
     *
     * @return the maximum value
     */
    public double maxValue() {
        return maxValue;
    }

    /**
     * Get the scaling factor.
     *
     * @return the scale factor
     */
    public double scaleFactor() {
        return scaleFactor;
    }

    /**
     * Check if values should be clamped to range.
     *
     * @return true if clamping is enabled, false if out-of-range values throw
     */
    public boolean clampToRange() {
        return clampToRange;
    }

    /**
     * Get the current value.
     *
     * @return the current numeric value, or null if no value has been set
     */
    public Double getCurrentValue() {
        return currentValue;
    }

    /**
     * Get the range (maxValue - minValue).
     *
     * @return the range
     */
    public double range() {
        return maxValue - minValue;
    }

    /**
     * Write a new numeric value.
     *
     * @param value the numeric value to write
     * @throws IllegalArgumentException if value is out of range and clampToRange is false
     */
    public void writeValue(double value) {
        double processedValue = value;

        // Apply clamping or validation
        if (value < minValue || value > maxValue) {
            if (clampToRange) {
                processedValue = Math.max(minValue, Math.min(maxValue, value));
            } else {
                throw new IllegalArgumentException(
                    "Value " + value + " is out of range [" + minValue + ", " + maxValue + "]");
            }
        }

        // Apply precision rounding
        if (precision > 0) {
            processedValue = Math.round(processedValue / precision) * precision;
        }

        // Apply scaling
        double scaledValue = processedValue * scaleFactor;

        this.currentValue = scaledValue;

        // Write to cache if registered
        if (isRegistered()) {
            _writeToCache(scaledValue);
        }
    }

    /**
     * Normalize a value to [0, 1] range.
     *
     * @param value the value to normalize
     * @return the normalized value in [0, 1] range
     * @throws IllegalArgumentException if value is out of range
     */
    public double normalize(double value) {
        if (value < minValue || value > maxValue) {
            if (clampToRange) {
                value = Math.max(minValue, Math.min(maxValue, value));
            } else {
                throw new IllegalArgumentException(
                    "Value " + value + " is out of range [" + minValue + ", " + maxValue + "]");
            }
        }
        return (value - minValue) / (maxValue - minValue);
    }

    /**
     * Denormalize a value from [0, 1] range back to original scale.
     *
     * @param normalizedValue the normalized value in [0, 1] range
     * @return the denormalized value
     * @throws IllegalArgumentException if normalizedValue is not in [0, 1]
     */
    public double denormalize(double normalizedValue) {
        if (normalizedValue < 0.0 || normalizedValue > 1.0) {
            throw new IllegalArgumentException(
                "Normalized value must be in range [0, 1], got: " + normalizedValue);
        }
        return minValue + (normalizedValue * (maxValue - minValue));
    }

    @Override
    protected void _registerWithCache() {
        // Register numeric stream with FEAGI cache system
        // In a full implementation, this would:
        // 1. Allocate shared memory region for numeric buffer
        // 2. Register with NPU for processing
        // 3. Set up data type metadata

        // For now, just mark as registered
        markRegistered();
    }

    @Override
    protected void _writeToCache(Double data) {
        // Write numeric value to FEAGI cache
        // In a full implementation, this would:
        // 1. Copy value to shared memory region
        // 2. Signal NPU that new value is available
        // 3. Update value counters/timestamps

        // For now, this is a no-op as data is stored in currentValue
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NumericStream that = (NumericStream) o;

        if (Double.compare(that.precision, precision) != 0) return false;
        if (Double.compare(that.minValue, minValue) != 0) return false;
        if (Double.compare(that.maxValue, maxValue) != 0) return false;
        if (Double.compare(that.scaleFactor, scaleFactor) != 0) return false;
        if (clampToRange != that.clampToRange) return false;
        return groupId == that.groupId;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(precision);
        result = (int) (temp ^ (temp >>> 32));
        result = 31 * result + (int) (Double.doubleToLongBits(minValue) ^ (Double.doubleToLongBits(minValue) >>> 32));
        result = 31 * result + (int) (Double.doubleToLongBits(maxValue) ^ (Double.doubleToLongBits(maxValue) >>> 32));
        result = 31 * result + (int) (Double.doubleToLongBits(scaleFactor) ^ (Double.doubleToLongBits(scaleFactor) >>> 32));
        result = 31 * result + (clampToRange ? 1 : 0);
        result = 31 * result + groupId;
        return result;
    }

    @Override
    public String toString() {
        return "NumericStream{" +
            "precision=" + precision +
            ", minValue=" + minValue +
            ", maxValue=" + maxValue +
            ", scaleFactor=" + scaleFactor +
            ", clampToRange=" + clampToRange +
            ", groupId=" + groupId +
            ", range=" + range() +
            '}';
    }

    /**
     * Builder for NumericStream configuration.
     */
    public static final class Builder {
        private double precision = 0.001;
        private double minValue = 0.0;
        private double maxValue = 1.0;
        private double scaleFactor = 1.0;
        private boolean clampToRange = true;
        private int groupId = 0;

        private Builder() {}

        /**
         * Validate that a numeric value is positive.
         */
        private void validatePositive(double value, String fieldName) {
            if (value <= 0) {
                throw new IllegalArgumentException(fieldName + " must be positive, got: " + value);
            }
        }

        /**
         * Validate that a numeric value is within range [0, 255].
         */
        private void validateRange(int value, String fieldName) {
            if (value < 0 || value > 255) {
                throw new IllegalArgumentException(
                    fieldName + " must be in range [0, 255], got: " + value);
            }
        }

        /**
         * Set the precision (smallest representable difference).
         *
         * @param precision the precision value (must be positive)
         * @return this builder
         * @throws IllegalArgumentException if precision is not positive
         */
        public Builder precision(double precision) {
            validatePositive(precision, "precision");
            this.precision = precision;
            return this;
        }

        /**
         * Set the value range.
         *
         * @param minValue the minimum value
         * @param maxValue the maximum value
         * @return this builder
         * @throws IllegalArgumentException if minValue >= maxValue
         */
        public Builder range(double minValue, double maxValue) {
            if (minValue >= maxValue) {
                throw new IllegalArgumentException(
                    "minValue must be less than maxValue, got [" + minValue + ", " + maxValue + "]");
            }
            this.minValue = minValue;
            this.maxValue = maxValue;
            return this;
        }

        /**
         * Set the minimum value.
         *
         * @param minValue the minimum value
         * @return this builder
         */
        public Builder minValue(double minValue) {
            this.minValue = minValue;
            return this;
        }

        /**
         * Set the maximum value.
         *
         * @param maxValue the maximum value
         * @return this builder
         */
        public Builder maxValue(double maxValue) {
            this.maxValue = maxValue;
            return this;
        }

        /**
         * Set the scaling factor.
         *
         * @param scaleFactor the scale factor (must be positive)
         * @return this builder
         * @throws IllegalArgumentException if scaleFactor is not positive
         */
        public Builder scaleFactor(double scaleFactor) {
            validatePositive(scaleFactor, "scaleFactor");
            this.scaleFactor = scaleFactor;
            return this;
        }

        /**
         * Enable or disable clamping to range.
         *
         * @param clampToRange true to clamp out-of-range values, false to throw
         * @return this builder
         */
        public Builder clampToRange(boolean clampToRange) {
            this.clampToRange = clampToRange;
            return this;
        }

        /**
         * Set the group ID.
         *
         * @param groupId the group ID (0-255)
         * @return this builder
         * @throws IllegalArgumentException if groupId is out of range
         */
        public Builder groupId(int groupId) {
            validateRange(groupId, "groupId");
            this.groupId = groupId;
            return this;
        }

        /**
         * Build the NumericStream instance.
         *
         * @return a new NumericStream with the configured values
         */
        public NumericStream build() {
            return new NumericStream(this);
        }
    }
}
