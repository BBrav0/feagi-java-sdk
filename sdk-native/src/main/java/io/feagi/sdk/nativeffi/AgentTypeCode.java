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

    // NOTE: The exhaustive switch expression in of() (Java 14+ switch expression) makes
    // the old runtime count-check unnecessary — the compiler enforces that every AgentType
    // value is handled. Adding a new enum value without updating the switch is a compile error.

    public static int of(AgentType type) {
        if (type == null) {
            throw new IllegalArgumentException("AgentType must not be null");
        }
        // Exhaustive switch expression — the compiler enforces that every AgentType value
        // is handled. Adding a new enum value without updating this switch is a compile
        // error, which is stronger than the runtime count-check it replaces.
        // ACTUAL_AGENT_TYPE_COUNT and EXPECTED_AGENT_TYPE_COUNT are therefore no longer
        // needed and have been removed.
        return switch (type) {
            case SENSORY        -> 0;
            case MOTOR          -> 1;
            case BOTH           -> 2;
            case VISUALIZATION  -> 3;
            case INFRASTRUCTURE -> 4;
        };
    }
}
