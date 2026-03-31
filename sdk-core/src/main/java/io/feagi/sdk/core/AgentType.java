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
     * Whether this role uses sensory (agent-to-FEAGI) byte transport.
     *
     * <p>Kept in sync with {@link io.feagi.sdk.core.AgentConfig} and
     * {@link io.feagi.sdk.core.transport.ZmqTransport} socket setup.
     */
    public boolean needsSensory() {
        return this == SENSORY || this == BOTH;
    }

    /**
     * Whether this role uses motor (FEAGI-to-agent) byte transport.
     */
    public boolean needsMotor() {
        return this == MOTOR || this == BOTH;
    }
}

