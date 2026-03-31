/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package io.feagi.sdk.pns.xyzp;

import java.util.List;
import java.util.Objects;

/**
 * Structure-of-Arrays (SoA) representation of XYZP neuron voxel data.
 *
 * <p>This mirrors the JSON schema used by the Python reference decoders:
 * {@code {"x":[...], "y":[...], "z":[...], "p":[...]}}.
 */
public record XyzpNeuronSoA(
        List<Integer> x,
        List<Integer> y,
        List<Integer> z,
        List<Float> p) {

    public XyzpNeuronSoA {
        Objects.requireNonNull(x, "x must not be null");
        Objects.requireNonNull(y, "y must not be null");
        Objects.requireNonNull(z, "z must not be null");
        Objects.requireNonNull(p, "p must not be null");

        // Defensive copy: decoders should not observe caller mutations after construction.
        x = List.copyOf(x);
        y = List.copyOf(y);
        z = List.copyOf(z);
        p = List.copyOf(p);
    }
}

