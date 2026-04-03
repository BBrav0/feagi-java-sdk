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
 *     // Use with XyzpCodec to build neuron IDs
 *     int id = XyzpCodec.toFlatId(x, y, z, d.width(), d.height());
 * });
 * }</pre>
 *
 * <h2>Placement</h2>
 * {@code sdk-core/src/main/java/io/feagi/sdk/core/CorticalDimensions.java}
 */
public final class CorticalDimensions {

    private final int width;
    private final int height;
    private final int depth;

    /**
     * Create cortical dimensions.
     *
     * @param width  x-dimension; must be {@code >= 1}
     * @param height y-dimension; must be {@code >= 1}
     * @param depth  z-dimension; must be {@code >= 1}
     */
    public CorticalDimensions(int width, int height, int depth) {
        if (width  < 1) throw new IllegalArgumentException("width must be >= 1, got " + width);
        if (height < 1) throw new IllegalArgumentException("height must be >= 1, got " + height);
        if (depth  < 1) throw new IllegalArgumentException("depth must be >= 1, got " + depth);
        this.width  = width;
        this.height = height;
        this.depth  = depth;
    }

    /** Cortical area width (x-dimension). */
    public int width()  { return width; }

    /** Cortical area height (y-dimension). */
    public int height() { return height; }

    /** Cortical area depth (z-dimension). */
    public int depth()  { return depth; }

    /**
     * Total neuron count in this cortical area ({@code width * height * depth}).
     * Returns {@code long} to avoid 32-bit overflow for large cortical areas.
     */
    public long totalNeurons() { return (long) width * height * depth; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CorticalDimensions that)) return false;
        return width == that.width && height == that.height && depth == that.depth;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * width + height) + depth;
    }

    @Override
    public String toString() {
        return "CorticalDimensions{width=" + width
                + ", height=" + height
                + ", depth=" + depth + '}';
    }
}
