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

    // Expected number of AgentType values this switch covers. Checked inside of()
    // rather than a static initializer — that pattern throws ExceptionInInitializerError
    // which wraps as NoClassDefFoundError in subsequent calls, producing confusing test
    // failures for code that has nothing to do with the missing mapping.
    private static final int EXPECTED_AGENT_TYPE_COUNT = 5;

    public static int of(AgentType type) {
        if (type == null) {
            throw new IllegalArgumentException("AgentType must not be null");
        }
        // Checked here rather than in a static initializer: if AgentType grows, this
        // fails with a clear IllegalStateException at the first actual mapping call,
        // not with a cryptic NoClassDefFoundError at class-load time.
        if (AgentType.values().length != EXPECTED_AGENT_TYPE_COUNT) {
            throw new IllegalStateException(
                    "AgentType has " + AgentType.values().length + " values but AgentTypeCode "
                    + "only maps " + EXPECTED_AGENT_TYPE_COUNT + ". "
                    + "Add the new value to AgentTypeCode.of() and update EXPECTED_AGENT_TYPE_COUNT.");
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
