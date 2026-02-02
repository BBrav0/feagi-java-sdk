/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

/**
 * Simple width/height resolution pair.
 */
public final class FeagiResolution {
    private final int width;
    private final int height;

    /**
     * Create a resolution.
     *
     * @param width width in pixels
     * @param height height in pixels
     */
    public FeagiResolution(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width/height must be > 0");
        }
        this.width = width;
        this.height = height;
    }

    /**
     * Return width in pixels.
     */
    public int width() {
        return width;
    }

    /**
     * Return height in pixels.
     */
    public int height() {
        return height;
    }
}
