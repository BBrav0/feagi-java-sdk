/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

/**
 * FEAGI agent type.
 *
 * <p>Mirrors FEAGI Rust SDK agent types and is used to drive socket creation and capability validation.
 */
public enum AgentType {
    SENSORY,
    MOTOR,
    BOTH,
    VISUALIZATION,
    INFRASTRUCTURE;

    /**
     * Check if this agent type requires sensory socket configuration.
     *
     * @return true if sensory socket is required (SENSORY or BOTH)
     */
    public boolean needsSensory() {
        return this == SENSORY || this == BOTH;
    }

    /**
     * Check if this agent type requires motor socket configuration.
     *
     * @return true if motor socket is required (MOTOR or BOTH)
     */
    public boolean needsMotor() {
        return this == MOTOR || this == BOTH;
    }
}

