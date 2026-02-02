/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

/**
 * Language-agnostic motor unit identifiers aligned with FEAGI registration contracts.
 */
public enum MotorUnit {
    ROTARY_MOTOR,
    POSITIONAL_SERVO,
    GAZE,
    MISC_DATA,
    TEXT_ENGLISH_OUTPUT,
    COUNT_OUTPUT,
    OBJECT_SEGMENTATION,
    SIMPLE_VISION_OUTPUT
}
