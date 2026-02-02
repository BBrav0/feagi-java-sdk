/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.util.Objects;

/**
 * Visualization capability for agents consuming neural activity streams.
 */
public final class VisualizationCapability {
    private final String visualizationType;
    private final FeagiResolution resolution;
    private final Double refreshRateHz;
    private final boolean bridgeProxy;

    /**
     * Create a visualization capability.
     *
     * @param visualizationType visualization type identifier
     * @param resolution optional display resolution
     * @param refreshRateHz optional refresh rate in Hz
     * @param bridgeProxy whether this agent is a bridge/proxy
     */
    public VisualizationCapability(
            String visualizationType,
            FeagiResolution resolution,
            Double refreshRateHz,
            boolean bridgeProxy
    ) {
        this.visualizationType = requireNonEmpty(visualizationType, "visualizationType");
        if (refreshRateHz != null && refreshRateHz <= 0.0) {
            throw new IllegalArgumentException("refreshRateHz must be > 0");
        }
        this.resolution = resolution;
        this.refreshRateHz = refreshRateHz;
        this.bridgeProxy = bridgeProxy;
    }

    /**
     * Return visualization type.
     */
    public String visualizationType() {
        return visualizationType;
    }

    /**
     * Return optional resolution.
     */
    public FeagiResolution resolution() {
        return resolution;
    }

    /**
     * Return optional refresh rate in Hz.
     */
    public Double refreshRateHz() {
        return refreshRateHz;
    }

    /**
     * Return whether this is a bridge/proxy agent.
     */
    public boolean bridgeProxy() {
        return bridgeProxy;
    }

    private static String requireNonEmpty(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return value;
    }
}
