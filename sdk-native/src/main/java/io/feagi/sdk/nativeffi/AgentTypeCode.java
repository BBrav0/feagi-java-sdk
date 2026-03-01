/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.nativeffi;

import io.feagi.sdk.core.AgentType;

/**
 * Stable C ABI code mapping for {@link AgentType}.
 *
 * <p>Constants are explicitly pinned to match {@code FeagiAgentType} in
 * {@code feagi_java_ffi.h}, decoupled from {@link AgentType#ordinal()} so that
 * reordering the Java enum does not silently break the native ABI.
 *
 * <p>Unlike {@link SensoryUnitCode} and {@link MotorUnitCode}, there is no
 * {@code FeagiNativeBindings.FeagiAgentType} enum to delegate to, so the constants
 * are maintained here directly.
 *
 * <p>TODO: When {@code FeagiNativeBindings.FeagiAgentType} is added, replace this
 * switch with name-based delegation:
 * {@code FeagiNativeBindings.FeagiAgentType.valueOf(type.name()).code()}
 * and remove the hand-written constants to restore single-source-of-truth.
 */
public final class AgentTypeCode {
    private AgentTypeCode() {}

    public static int of(AgentType type) {
        if (type == null) {
            throw new IllegalArgumentException("AgentType must not be null");
        }
        switch (type) {
            case SENSORY:        return 0;
            case MOTOR:          return 1;
            case BOTH:           return 2;
            case VISUALIZATION:  return 3;
            case INFRASTRUCTURE: return 4;
            default:
                throw new IllegalArgumentException(
                        "Unknown AgentType: " + type
                        + ". Update AgentTypeCode to match FeagiAgentType in feagi_java_ffi.h");
        }
    }
}
