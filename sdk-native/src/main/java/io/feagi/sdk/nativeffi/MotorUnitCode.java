/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.nativeffi;

import io.feagi.sdk.core.MotorUnit;

/**
 * Stable C ABI code mapping for {@link MotorUnit}.
 *
 * <p>These constants are explicitly pinned to match {@code FeagiMotorUnit} in
 * {@code feagi_java_ffi.h}. They are intentionally decoupled from
 * {@link MotorUnit#ordinal()} so that reordering the Java enum does not
 * silently break the native ABI.
 *
 * <p>Keep in sync with {@code FeagiMotorUnit} in the Rust/C header.
 */
public final class MotorUnitCode {
    private MotorUnitCode() {}

    public static int of(MotorUnit unit) {
        switch (unit) {
            case ROTARY_MOTOR:        return 0;
            case POSITIONAL_SERVO:    return 1;
            case GAZE:                return 2;
            case MISC_DATA:           return 3;
            case TEXT_ENGLISH_OUTPUT: return 4;
            case COUNT_OUTPUT:        return 5;
            case OBJECT_SEGMENTATION: return 6;
            case SIMPLE_VISION_OUTPUT:return 7;
            default:
                throw new IllegalArgumentException(
                        "Unknown MotorUnit: " + unit
                        + ". Update MotorUnitCode to match FeagiMotorUnit in feagi_java_ffi.h");
        }
    }
}