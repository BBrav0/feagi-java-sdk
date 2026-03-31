/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

/**
 * Immutable snapshot of one FEAGI motor frame delivered to
 * {@link BaseAgent#mapMotors(AgentFrame)}.
 *
 * <p>Mirrors the Python {@code feagi_output} dict passed to {@code map_motors(feagi_output)}.
 * A frame is "empty" ({@link #hasData()} returns {@code false}) when FEAGI has not
 * yet produced output for the current cycle. {@link BaseAgent#mapMotors(AgentFrame)} is
 * called every tick regardless — implementations must handle the empty case
 * (e.g., hold position, no-op).
 *
 * <p>The raw bytes are the FEAGI byte-container payload as returned by
 * {@link FeagiAgentClient#pollMotorBytes()}. Parsing is the responsibility of the
 * {@link BaseAgent} subclass, typically in {@link BaseAgent#mapMotors(AgentFrame)}.
 */
public final class AgentFrame {

    private static final AgentFrame EMPTY = new AgentFrame(null);

    private final byte[] motorBytes;

    private AgentFrame(byte[] motorBytes) {
        this.motorBytes = motorBytes == null ? null : motorBytes.clone();
    }

    /**
     * Create a frame carrying motor data.
     *
     * @param motorBytes raw FEAGI motor payload; must not be null
     */
    public static AgentFrame of(byte[] motorBytes) {
        if (motorBytes == null) {
            throw new IllegalArgumentException("motorBytes must not be null; use AgentFrame.empty()");
        }
        return new AgentFrame(motorBytes);
    }

    /**
     * Create an empty frame representing "no data this cycle".
     */
    public static AgentFrame empty() {
        return EMPTY;
    }

    /**
     * Return {@code true} if this frame carries motor data.
     */
    public boolean hasData() {
        return motorBytes != null;
    }

    /**
     * Return the raw motor payload bytes, or {@code null} if {@link #hasData()} is false.
     */
    public byte[] motorBytes() {
        return motorBytes == null ? null : motorBytes.clone();
    }
}
