/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.util.Objects;

/**
 * FEAGI 2.0 vision unit configuration.
 */
public final class VisionUnitConfig {
    private final String modality;
    private final int width;
    private final int height;
    private final int channels;
    private final int unit;
    private final int group;

    private VisionUnitConfig(Builder builder) {
        this.modality = requireNonEmpty(builder.modality, "modality");
        this.width = requirePositive(builder.width, "width");
        this.height = requirePositive(builder.height, "height");
        this.channels = requirePositive(builder.channels, "channels");
        this.unit = requireByteRange(builder.unit, "unit");
        this.group = requireByteRange(builder.group, "group");
    }

    public static Builder builder() {
        return new Builder();
    }

    public String modality() {
        return modality;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int channels() {
        return channels;
    }

    public int unit() {
        return unit;
    }

    public int group() {
        return group;
    }

    /**
     * Convenience alias for matching docs that use resolution(w, h).
     */
    public static final class Builder {
        private String modality = "camera";
        private int width = 640;
        private int height = 480;
        private int channels = 3;
        private int unit = 0;
        private int group = 0;

        private Builder() {}

        public Builder modality(String modality) {
            this.modality = modality;
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder height(int height) {
            this.height = height;
            return this;
        }

        public Builder resolution(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder channels(int channels) {
            this.channels = channels;
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

        public VisionUnitConfig build() {
            return new VisionUnitConfig(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VisionUnitConfig that)) return false;
        return width == that.width
            && height == that.height
            && channels == that.channels
            && unit == that.unit
            && group == that.group
            && Objects.equals(modality, that.modality);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modality, width, height, channels, unit, group);
    }

    @Override
    public String toString() {
        return "VisionUnitConfig{"
            + "modality='" + modality + '\''
            + ", width=" + width
            + ", height=" + height
            + ", channels=" + channels
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

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
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
