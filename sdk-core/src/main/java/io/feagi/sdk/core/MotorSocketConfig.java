/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

/**
 * Motor socket configuration aligned with FEAGI agent settings.
 */
public final class MotorSocketConfig {
    private final int subscribeHwm;
    private final int lingerMs;
    private final boolean immediate;

    /**
     * Create a motor socket configuration.
     *
     * @param subscribeHwm ZMQ high-water mark for subscribe socket (must be >= 0)
     * @param lingerMs linger duration in ms (must be >= 0)
     * @param immediate whether to enable ZMQ immediate mode
     */
    public MotorSocketConfig(int subscribeHwm, int lingerMs, boolean immediate) {
        if (subscribeHwm < 0) {
            throw new IllegalArgumentException("subscribeHwm must be >= 0");
        }
        if (lingerMs < 0) {
            throw new IllegalArgumentException("lingerMs must be >= 0");
        }
        this.subscribeHwm = subscribeHwm;
        this.lingerMs = lingerMs;
        this.immediate = immediate;
    }

    /**
     * Return subscribe high-water mark.
     */
    public int subscribeHwm() {
        return subscribeHwm;
    }

    /**
     * Return linger duration in milliseconds.
     */
    public int lingerMs() {
        return lingerMs;
    }

    /**
     * Return immediate mode flag.
     */
    public boolean immediate() {
        return immediate;
    }
}
