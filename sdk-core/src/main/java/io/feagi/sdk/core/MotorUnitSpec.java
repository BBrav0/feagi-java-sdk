/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.util.Objects;

/**
 * Motor unit + group pair for registration contracts.
 */
public final class MotorUnitSpec {
    private final MotorUnit unit;
    private final int group;

    /**
     * Create a motor unit spec with group index.
     *
     * @param unit motor unit identifier
     * @param group cortical group index
     */
    public MotorUnitSpec(MotorUnit unit, int group) {
        this.unit = Objects.requireNonNull(unit, "unit must not be null");
        if (group < 0 || group > 255) {
            throw new IllegalArgumentException("group must be in [0, 255]");
        }
        this.group = group;
    }

    /**
     * Return the motor unit.
     */
    public MotorUnit unit() {
        return unit;
    }

    /**
     * Return the cortical group index.
     */
    public int group() {
        return group;
    }
}
