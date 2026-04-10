/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.pns.outputs;

import java.util.Arrays;
import java.util.Objects;

/**
 * Generic numeric stream output.
 *
 * <p>This class represents a stream of numeric values from FEAGI. It can be used
 * for game actions, trading signals, control commands, or any numeric output.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Trading bot - 3 signals (buy, sell, hold)
 * OutputNumericStream tradingSignal = OutputNumericStream.builder()
 *     .dimensions(3)
 *     .build();
 *
 * tradingSignal._registerWithCache();
 *
 * // In main loop
 * while (true) {
 *     tradingSignal._readFromCache();
 *     double[] signals = tradingSignal.getValues();
 *
 *     if (signals[0] > 0.7) {  // Buy signal
 *         executeTrade("buy");
 *     } else if (signals[1] > 0.7) {  // Sell signal
 *         executeTrade("sell");
 *     }
 * }
 * }</pre>
 *
 * @see BaseOutput
 * @see ServoMotor
 * @see RotaryMotor
 */
public class OutputNumericStream extends BaseOutput {

    /**
     * Number of numeric values in the stream.
     */
    private final int dimensions;

    /**
     * Current values from FEAGI.
     */
    private double[] currentValues;

    /**
     * Creates a new OutputNumericStream with the specified configuration.
     *
     * @param builder the builder containing configuration values
     */
    private OutputNumericStream(Builder builder) {
        this.dimensions = builder.dimensions;
        this.currentValues = new double[dimensions];
        Arrays.fill(this.currentValues, 0.0);
    }

    /**
     * Create a builder for OutputNumericStream configuration.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the number of dimensions.
     *
     * @return the number of numeric values
     */
    public int dimensions() {
        return dimensions;
    }

    /**
     * Get the current numeric values from FEAGI.
     *
     * @return a copy of the current values array
     */
    public double[] getValues() {
        return currentValues.clone();
    }

    /**
     * Get a specific value by index.
     *
     * @param index the value index (0 to dimensions-1)
     * @return the value at the specified index
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public double getValue(int index) {
        if (index < 0 || index >= dimensions) {
            throw new IndexOutOfBoundsException(
                "Index " + index + " out of bounds for dimensions " + dimensions);
        }
        return currentValues[index];
    }

    /**
     * Get the maximum value in the stream.
     *
     * @return the maximum value
     */
    public double getMaxValue() {
        double max = currentValues[0];
        for (int i = 1; i < currentValues.length; i++) {
            if (currentValues[i] > max) {
                max = currentValues[i];
            }
        }
        return max;
    }

    /**
     * Get the index of the maximum value.
     *
     * @return the index of the maximum value
     */
    public int getMaxIndex() {
        int maxIndex = 0;
        for (int i = 1; i < currentValues.length; i++) {
            if (currentValues[i] > currentValues[maxIndex]) {
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    /**
     * Process values from FEAGI.
     *
     * @param values the values from FEAGI
     */
    public void processValues(double[] values) {
        if (values == null) {
            throw new NullPointerException("values must not be null");
        }
        if (values.length != dimensions) {
            throw new IllegalArgumentException(
                "Expected " + dimensions + " values, got " + values.length);
        }
        this.currentValues = values.clone();
    }

    @Override
    protected void _registerWithCache() {
        // Register numeric stream with FEAGI cache system
        // In a full implementation, this would:
        // 1. Register with NPU for numeric output
        // 2. Configure data dimensions
        // 3. Set up motor group and channel

        // For now, just mark as registered with default group
        markRegistered(0);
    }

    @Override
    protected void _readFromCache() {
        // Read numeric values from FEAGI cache
        // In a full implementation, this would:
        // 1. Read values from shared memory region
        // 2. Process through processValues()
        // 3. Update currentValues

        // For now, this is a no-op
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OutputNumericStream that = (OutputNumericStream) o;

        if (dimensions != that.dimensions) return false;
        if (!Arrays.equals(currentValues, that.currentValues)) return false;
        return Objects.equals(groupId, that.groupId);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(groupId);
        result = 31 * result + dimensions;
        result = 31 * result + Arrays.hashCode(currentValues);
        return result;
    }

    @Override
    public String toString() {
        return "OutputNumericStream{" +
            "dimensions=" + dimensions +
            ", values=" + Arrays.toString(currentValues) +
            ", groupId=" + groupId +
            '}';
    }

    /**
     * Builder for OutputNumericStream configuration.
     */
    public static final class Builder {
        private int dimensions = 1;

        private Builder() {}

        /**
         * Validate that a numeric value is positive.
         */
        private void validatePositive(int value, String fieldName) {
            if (value <= 0) {
                throw new IllegalArgumentException(fieldName + " must be positive, got: " + value);
            }
        }

        /**
         * Set the number of dimensions.
         *
         * @param dimensions the number of numeric values (must be positive)
         * @return this builder
         * @throws IllegalArgumentException if dimensions is not positive
         */
        public Builder dimensions(int dimensions) {
            validatePositive(dimensions, "dimensions");
            this.dimensions = dimensions;
            return this;
        }

        /**
         * Build the OutputNumericStream instance.
         *
         * @return a new OutputNumericStream with the configured values
         */
        public OutputNumericStream build() {
            return new OutputNumericStream(this);
        }
    }
}
