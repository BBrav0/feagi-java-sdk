/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

/**
 * Sensory capability for non-vision modalities.
 */
public final class SensoryCapability {
    private final double rateHz;
    private final String shmPath;

    /**
     * Create a sensory capability.
     *
     * @param rateHz data rate in Hz (must be > 0)
     * @param shmPath optional shared memory path, or null
     */
    public SensoryCapability(double rateHz, String shmPath) {
        if (rateHz <= 0.0) {
            throw new IllegalArgumentException("rateHz must be > 0");
        }
        this.rateHz = rateHz;
        this.shmPath = (shmPath == null || shmPath.isEmpty()) ? null : shmPath;
    }

    /**
     * Return data rate in Hz.
     */
    public double rateHz() {
        return rateHz;
    }

    /**
     * Return optional shared memory path.
     */
    public String shmPath() {
        return shmPath;
    }
}
