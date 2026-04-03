/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

/**
 * Dimensions of a FEAGI cortical area: width (x), height (y), and depth (z).
 *
 * <p>These values map directly to the XYZP coordinate system used by
 * {@link XyzpCodec#toFlatId(int, int, int, int, int)}: width is the x-dimension,
 * height is the y-dimension, and depth is the z-dimension.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Optional<CorticalDimensions> dims =
 *         CorticalAreaResolver.resolve("i__inf", "127.0.0.1", 8000);
 *
 * dims.ifPresent(d -> {
 *     int id = XyzpCodec.toFlatId(x, y, z, d.width(), d.height());
 * });
 * }</pre>
 *
 * <h2>Placement</h2>
 * {@code sdk-core/src/main/java/io/feagi/sdk/core/CorticalDimensions.java}
 */
public record CorticalDimensions(int width, int height, int depth) {

    /**
     * Compact constructor — validates that all dimensions are positive.
     *
     * @param width  x-dimension; must be {@code >= 1}
     * @param height y-dimension; must be {@code >= 1}
     * @param depth  z-dimension; must be {@code >= 1}
     */
    public CorticalDimensions {
        if (width  < 1) throw new IllegalArgumentException("width must be >= 1, got "  + width);
        if (height < 1) throw new IllegalArgumentException("height must be >= 1, got " + height);
        if (depth  < 1) throw new IllegalArgumentException("depth must be >= 1, got "  + depth);
    }

    /**
     * Total neuron count ({@code width * height * depth}).
     * Returns {@code long} to avoid 32-bit overflow for large cortical areas.
     */
    public long totalNeurons() {
        return (long) width * height * depth;
    }
}
