/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

/**
 * Motor socket configuration aligned with FEAGI agent settings.
 */
public final class MotorSocketConfig {
    private final int rcvHwm;
    private final int lingerMs;
    private final boolean conflate;

    /**
     * Create a motor socket configuration.
     *
     * @param rcvHwm ZMQ receive high-water mark (must be >= 0)
     * @param lingerMs linger duration in ms (must be >= 0)
     * @param conflate whether to conflate incoming messages (keep only the latest)
     */
    public MotorSocketConfig(int rcvHwm, int lingerMs, boolean conflate) {
        if (rcvHwm < 0) {
            throw new IllegalArgumentException("rcvHwm must be >= 0");
        }
        if (lingerMs < 0) {
            throw new IllegalArgumentException("lingerMs must be >= 0");
        }
        this.rcvHwm = rcvHwm;
        this.lingerMs = lingerMs;
        this.conflate = conflate;
    }

    /**
     * Return receive high-water mark.
     */
    public int rcvHwm() {
        return rcvHwm;
    }

    /**
     * Return linger duration in milliseconds.
     */
    public int lingerMs() {
        return lingerMs;
    }

    /**
     * Return whether conflate mode is enabled.
     */
    public boolean conflate() {
        return conflate;
    }
}
