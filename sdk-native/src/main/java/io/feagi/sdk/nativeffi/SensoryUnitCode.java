/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.nativeffi;

import io.feagi.sdk.core.SensoryUnit;

/**
 * Stable C ABI code mapping for {@link SensoryUnit}.
 *
 * <p>Delegates to {@link FeagiNativeBindings.FeagiSensoryUnit} which holds the authoritative
 * pinned integer constants. This eliminates duplication while keeping the mapping decoupled
 * from {@link SensoryUnit#ordinal()} — reordering the Java enum will not silently break the ABI.
 *
 * <p>If a {@link SensoryUnit} value has no matching {@link FeagiNativeBindings.FeagiSensoryUnit},
 * an {@link IllegalArgumentException} is thrown at runtime, making the gap immediately visible.
 */
public final class SensoryUnitCode {
    private SensoryUnitCode() {}

    /**
     * Return the stable C ABI integer code for the given {@link SensoryUnit}.
     *
     * @throws IllegalArgumentException if the unit has no ABI mapping in
     *         {@link FeagiNativeBindings.FeagiSensoryUnit}
     */
    public static int of(SensoryUnit unit) {
        if (unit == null) {
            throw new IllegalArgumentException("SensoryUnit must not be null");
        }
        try {
            // Name-based lookup: SensoryUnit.VISION → FeagiSensoryUnit.VISION
            return FeagiNativeBindings.FeagiSensoryUnit.valueOf(unit.name()).code();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "SensoryUnit." + unit.name() + " has no corresponding FeagiSensoryUnit ABI mapping. "
                    + "Add it to FeagiNativeBindings.FeagiSensoryUnit with its pinned C ABI code.", e);
        }
    }
}
