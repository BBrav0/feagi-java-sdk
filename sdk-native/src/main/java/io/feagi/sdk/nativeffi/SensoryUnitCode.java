/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.nativeffi;

import io.feagi.sdk.core.SensoryUnit;

/**
 * Stable C ABI code mapping for {@link SensoryUnit}.
 *
 * <p>These constants are explicitly pinned to match {@code FeagiSensoryUnit} in
 * {@code feagi_java_ffi.h}. They are intentionally decoupled from
 * {@link SensoryUnit#ordinal()} so that reordering the Java enum does not
 * silently break the native ABI.
 *
 * <p>Keep in sync with {@code FeagiSensoryUnit} in the Rust/C header.
 */
public final class SensoryUnitCode {
    private SensoryUnitCode() {}

    public static int of(SensoryUnit unit) {
        switch (unit) {
            case INFRARED:           return 0;
            case PROXIMITY:          return 1;
            case SHOCK:              return 2;
            case BATTERY:            return 3;
            case SERVO:              return 4;
            case ANALOG_GPIO:        return 5;
            case DIGITAL_GPIO:       return 6;
            case MISC_DATA:          return 7;
            case TEXT_ENGLISH_INPUT: return 8;
            case COUNT_INPUT:        return 9;
            case VISION:             return 10;
            case SEGMENTED_VISION:   return 11;
            case ACCELEROMETER:      return 12;
            case GYROSCOPE:          return 13;
            default:
                throw new IllegalArgumentException(
                        "Unknown SensoryUnit: " + unit
                        + ". Update SensoryUnitCode to match FeagiSensoryUnit in feagi_java_ffi.h");
        }
    }
}
