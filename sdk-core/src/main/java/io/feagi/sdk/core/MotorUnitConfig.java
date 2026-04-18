/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.util.Objects;

/**
 * FEAGI 2.0 motor unit configuration.
 */
public final class MotorUnitConfig {
    private final String type;
    private final double minValue;
    private final double maxValue;
    private final int unit;
    private final int group;

    private MotorUnitConfig(Builder builder) {
        this.type = requireNonEmpty(builder.type, "type");
        this.minValue = builder.minValue;
        this.maxValue = builder.maxValue;
        if (Double.isNaN(minValue) || Double.isNaN(maxValue)) {
            throw new IllegalArgumentException("range values must be valid numbers");
        }
        if (maxValue < minValue) {
            throw new IllegalArgumentException("maxValue must be >= minValue");
        }
        this.unit = requireByteRange(builder.unit, "unit");
        this.group = requireByteRange(builder.group, "group");
    }

    public static Builder builder() {
        return new Builder();
    }

    public String type() {
        return type;
    }

    public double minValue() {
        return minValue;
    }

    public double maxValue() {
        return maxValue;
    }

    public int unit() {
        return unit;
    }

    public int group() {
        return group;
    }

    public static final class Builder {
        private String type;
        private double minValue = 0.0;
        private double maxValue = 1.0;
        private int unit = 0;
        private int group = 0;

        private Builder() {}

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder range(double minValue, double maxValue) {
            this.minValue = minValue;
            this.maxValue = maxValue;
            return this;
        }

        public Builder minValue(double minValue) {
            this.minValue = minValue;
            return this;
        }

        public Builder maxValue(double maxValue) {
            this.maxValue = maxValue;
            return this;
        }

        public Builder unit(int unit) {
            this.unit = unit;
            return this;
        }

        public Builder group(int group) {
            this.group = group;
            return this;
        }

        public MotorUnitConfig build() {
            return new MotorUnitConfig(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MotorUnitConfig that)) return false;
        return Double.compare(that.minValue, minValue) == 0
            && Double.compare(that.maxValue, maxValue) == 0
            && unit == that.unit
            && group == that.group
            && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, minValue, maxValue, unit, group);
    }

    @Override
    public String toString() {
        return "MotorUnitConfig{"
            + "type='" + type + '\''
            + ", minValue=" + minValue
            + ", maxValue=" + maxValue
            + ", unit=" + unit
            + ", group=" + group
            + '}';
    }

    private static String requireNonEmpty(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return value;
    }

    private static int requireByteRange(int value, String name) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(name + " must be in [0, 255]");
        }
        return value;
    }
}
