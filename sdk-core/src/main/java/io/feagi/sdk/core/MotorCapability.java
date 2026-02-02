/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Motor output capability with either cortical areas or semantic units.
 */
public final class MotorCapability {
    private final String modality;
    private final int outputCount;
    private final List<String> sourceCorticalAreas;
    private final MotorUnit unit;
    private final Integer group;
    private final List<MotorUnitSpec> sourceUnits;

    private MotorCapability(
            String modality,
            int outputCount,
            List<String> sourceCorticalAreas,
            MotorUnit unit,
            Integer group,
            List<MotorUnitSpec> sourceUnits
    ) {
        this.modality = requireNonEmpty(modality, "modality");
        this.outputCount = requirePositive(outputCount, "outputCount");
        this.sourceCorticalAreas = sourceCorticalAreas;
        this.unit = unit;
        this.group = group;
        this.sourceUnits = sourceUnits;
        validateSelection();
    }

    /**
     * Create a motor capability using explicit cortical area identifiers.
     */
    public static MotorCapability fromCorticalAreas(
            String modality,
            int outputCount,
            List<String> sourceCorticalAreas
    ) {
        Objects.requireNonNull(sourceCorticalAreas, "sourceCorticalAreas must not be null");
        if (sourceCorticalAreas.isEmpty()) {
            throw new IllegalArgumentException("sourceCorticalAreas must not be empty");
        }
        List<String> copy = new ArrayList<>(sourceCorticalAreas.size());
        for (String area : sourceCorticalAreas) {
            copy.add(requireNonEmpty(area, "sourceCorticalAreas entry"));
        }
        return new MotorCapability(
                modality,
                outputCount,
                Collections.unmodifiableList(copy),
                null,
                null,
                null
        );
    }

    /**
     * Create a motor capability using a semantic unit + group (Option B contract).
     */
    public static MotorCapability fromUnit(
            String modality,
            int outputCount,
            MotorUnit unit,
            int group
    ) {
        Objects.requireNonNull(unit, "unit must not be null");
        if (group < 0 || group > 255) {
            throw new IllegalArgumentException("group must be in [0, 255]");
        }
        return new MotorCapability(modality, outputCount, null, unit, group, null);
    }

    /**
     * Create a motor capability using multiple semantic unit sources.
     */
    public static MotorCapability fromUnits(
            String modality,
            int outputCount,
            List<MotorUnitSpec> sourceUnits
    ) {
        Objects.requireNonNull(sourceUnits, "sourceUnits must not be null");
        if (sourceUnits.isEmpty()) {
            throw new IllegalArgumentException("sourceUnits must not be empty");
        }
        List<MotorUnitSpec> copy = new ArrayList<>(sourceUnits);
        return new MotorCapability(
                modality,
                outputCount,
                null,
                null,
                null,
                Collections.unmodifiableList(copy)
        );
    }

    /**
     * Return modality identifier.
     */
    public String modality() {
        return modality;
    }

    /**
     * Return output count.
     */
    public int outputCount() {
        return outputCount;
    }

    /**
     * Return cortical area list (nullable).
     */
    public List<String> sourceCorticalAreas() {
        return sourceCorticalAreas;
    }

    /**
     * Return semantic motor unit (nullable).
     */
    public MotorUnit unit() {
        return unit;
    }

    /**
     * Return cortical group index (nullable).
     */
    public Integer group() {
        return group;
    }

    /**
     * Return semantic motor unit sources (nullable).
     */
    public List<MotorUnitSpec> sourceUnits() {
        return sourceUnits;
    }

    private void validateSelection() {
        boolean hasAreas = sourceCorticalAreas != null;
        boolean hasUnit = unit != null && group != null;
        boolean hasUnits = sourceUnits != null;
        int active = (hasAreas ? 1 : 0) + (hasUnit ? 1 : 0) + (hasUnits ? 1 : 0);
        if (active != 1) {
            throw new IllegalArgumentException(
                    "MotorCapability must use exactly one of areas, unit+group, or sourceUnits");
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
