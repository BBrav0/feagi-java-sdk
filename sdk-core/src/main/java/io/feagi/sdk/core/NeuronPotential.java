/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

/**
 * An immutable (neuron-id, firing-potential) pair for sensory input.
 *
 * <p>The neuron ID is a flat integer index derived from FEAGI's XYZP coordinate system.
 * Cortical area geometry maps a 3-D position {@code (x, y, z)} to a flat index via
 * {@link XyzpCodec#toFlatId(int, int, int, int, int)} — callers who work in cortical
 * coordinates should use that helper rather than computing the index manually.
 *
 * <p>The firing potential is a value in {@code [0.0, 1.0]} representing how strongly
 * the neuron is firing. Values outside this range are accepted but may be clamped by
 * the FEAGI runtime.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Flat-index form (e.g. from a sensor array)
 * NeuronPotential n = NeuronPotential.of(42, 0.75f);
 *
 * // Cortical-coordinate form — width=10, height=10 cortical area
 * int id = XyzpCodec.toFlatId(3, 2, 0, 10, 10);
 * NeuronPotential n = NeuronPotential.of(id, 0.75f);
 * }</pre>
 *
 * <h2>Placement</h2>
 * {@code sdk-core/src/main/java/io/feagi/sdk/core/NeuronPotential.java}
 */
public final class NeuronPotential {

    private final int   neuronId;
    private final float potential;

    private NeuronPotential(int neuronId, float potential) {
        if (neuronId < 0) {
            throw new IllegalArgumentException(
                    "neuronId must be >= 0, got " + neuronId);
        }
        this.neuronId  = neuronId;
        this.potential = potential;
    }

    /**
     * Create a (neuron-id, potential) pair.
     *
     * @param neuronId  non-negative flat neuron index
     * @param potential firing potential; typically in {@code [0.0, 1.0]}
     */
    public static NeuronPotential of(int neuronId, float potential) {
        return new NeuronPotential(neuronId, potential);
    }

    /** Return the flat neuron index. */
    public int   neuronId()  { return neuronId; }

    /** Return the firing potential. */
    public float potential() { return potential; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NeuronPotential that)) return false;
        return neuronId == that.neuronId
            && Float.floatToIntBits(potential) == Float.floatToIntBits(that.potential);
    }

    @Override
    public int hashCode() {
        return 31 * neuronId + Float.floatToIntBits(potential);
    }

    @Override
    public String toString() {
        return "NeuronPotential{id=" + neuronId + ", p=" + potential + '}';
    }
}
