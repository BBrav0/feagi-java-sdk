/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.nativeffi;

import io.feagi.sdk.core.MotorUnit;

/**
 * Stable C ABI code mapping for {@link MotorUnit}.
 *
 * <p>Delegates to {@link FeagiNativeBindings.FeagiMotorUnit} which holds the authoritative
 * pinned integer constants. This eliminates duplication while keeping the mapping decoupled
 * from {@link MotorUnit#ordinal()} — reordering the Java enum will not silently break the ABI.
 *
 * <p>If a {@link MotorUnit} value has no matching {@link FeagiNativeBindings.FeagiMotorUnit},
 * an {@link IllegalArgumentException} is thrown at runtime, making the gap immediately visible.
 */
public final class MotorUnitCode {
    private MotorUnitCode() {}

    /**
     * Return the stable C ABI integer code for the given {@link MotorUnit}.
     *
     * @throws IllegalArgumentException if the unit has no ABI mapping in
     *         {@link FeagiNativeBindings.FeagiMotorUnit}
     */
    public static int of(MotorUnit unit) {
        if (unit == null) {
            throw new IllegalArgumentException("MotorUnit must not be null");
        }
        try {
            // Name-based lookup: MotorUnit.ROTARY_MOTOR → FeagiMotorUnit.ROTARY_MOTOR
            return FeagiNativeBindings.FeagiMotorUnit.valueOf(unit.name()).code();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "MotorUnit." + unit.name() + " has no corresponding FeagiMotorUnit ABI mapping. "
                    + "Add it to FeagiNativeBindings.FeagiMotorUnit with its pinned C ABI code.", e);
        }
    }
}
