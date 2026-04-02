/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

/**
 * Sensory socket configuration aligned with FEAGI agent settings.
 */
public final class SensorySocketConfig {
    private final int sendHwm;
    private final int lingerMs;
    private final boolean immediate;

    /**
     * Create a sensory socket configuration.
     *
     * @param sendHwm ZMQ high-water mark (must be >= 0)
     * @param lingerMs linger duration in ms (must be >= 0)
     * @param immediate whether to enable ZMQ immediate mode
     */
    public SensorySocketConfig(int sendHwm, int lingerMs, boolean immediate) {
        if (sendHwm < 0) {
            throw new IllegalArgumentException("sendHwm must be >= 0");
        }
        if (lingerMs < 0) {
            throw new IllegalArgumentException("lingerMs must be >= 0");
        }
        this.sendHwm = sendHwm;
        this.lingerMs = lingerMs;
        this.immediate = immediate;
    }

    /**
     * Return send high-water mark.
     */
    public int sendHwm() {
        return sendHwm;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SensorySocketConfig that)) return false;
        return sendHwm == that.sendHwm
            && lingerMs == that.lingerMs
            && immediate == that.immediate;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(sendHwm, lingerMs, immediate);
    }

    @Override
    public String toString() {
        return "SensorySocketConfig{sendHwm=" + sendHwm
                + ", lingerMs=" + lingerMs
                + ", immediate=" + immediate + '}';
    }
}
