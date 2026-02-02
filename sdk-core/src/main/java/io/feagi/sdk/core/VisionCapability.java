/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.util.Objects;

/**
 * Vision input capability with either target cortical area or semantic unit + group.
 */
public final class VisionCapability {
    private final String modality;
    private final int width;
    private final int height;
    private final int channels;
    private final String targetCorticalArea;
    private final SensoryUnit unit;
    private final Integer group;

    private VisionCapability(
            String modality,
            int width,
            int height,
            int channels,
            String targetCorticalArea,
            SensoryUnit unit,
            Integer group
    ) {
        this.modality = requireNonEmpty(modality, "modality");
        this.width = requirePositive(width, "width");
        this.height = requirePositive(height, "height");
        this.channels = requirePositive(channels, "channels");
        this.targetCorticalArea = targetCorticalArea;
        this.unit = unit;
        this.group = group;
        validateSelection();
    }

    /**
     * Create a vision capability using a target cortical area.
     */
    public static VisionCapability fromTargetArea(
            String modality,
            int width,
            int height,
            int channels,
            String targetCorticalArea
    ) {
        return new VisionCapability(
                modality,
                width,
                height,
                channels,
                requireNonEmpty(targetCorticalArea, "targetCorticalArea"),
                null,
                null
        );
    }

    /**
     * Create a vision capability using semantic unit + group (Option B contract).
     */
    public static VisionCapability fromUnit(
            String modality,
            int width,
            int height,
            int channels,
            SensoryUnit unit,
            int group
    ) {
        Objects.requireNonNull(unit, "unit must not be null");
        if (group < 0 || group > 255) {
            throw new IllegalArgumentException("group must be in [0, 255]");
        }
        return new VisionCapability(modality, width, height, channels, null, unit, group);
    }

    /**
     * Return modality identifier.
     */
    public String modality() {
        return modality;
    }

    /**
     * Return image width.
     */
    public int width() {
        return width;
    }

    /**
     * Return image height.
     */
    public int height() {
        return height;
    }

    /**
     * Return channel count.
     */
    public int channels() {
        return channels;
    }

    /**
     * Return target cortical area (nullable).
     */
    public String targetCorticalArea() {
        return targetCorticalArea;
    }

    /**
     * Return semantic sensory unit (nullable).
     */
    public SensoryUnit unit() {
        return unit;
    }

    /**
     * Return cortical group index (nullable).
     */
    public Integer group() {
        return group;
    }

    private void validateSelection() {
        boolean hasTarget = targetCorticalArea != null;
        boolean hasUnit = unit != null && group != null;
        if (hasTarget == hasUnit) {
            throw new IllegalArgumentException(
                    "VisionCapability must use either targetCorticalArea or unit+group");
        }
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
}
