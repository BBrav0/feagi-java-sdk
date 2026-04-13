/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package io.feagi.sdk.pns.xyzp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class XyzpDecodersSensorTest {
    @Test
    public void testDecodeSensorXyzpToGridRowMajorMapping() {
        String corticalId = "cortical_test";

        // width=2, height=2 => flat size=4, channels=3 => grid[z][y*width+x]
        Map<String, XyzpNeuronSoA> xyzp = new HashMap<>();
        xyzp.put(corticalId, new XyzpNeuronSoA(
                List.of(0, 1), // x
                List.of(0, 1), // y
                List.of(0, 2), // z (channel)
                List.of(0.5f, 1.5f) // p
        ));

        Map<Integer, List<Float>> grids =
                XyzpDecoders.decodeSensorXyzpToGrid(xyzp, corticalId, 2, 2, 3);

        assertEquals(0.5f, grids.get(0).get(0), 1e-6f); // (x=0,y=0) => idx 0
        assertEquals(1.5f, grids.get(2).get(3), 1e-6f); // (x=1,y=1) => idx 1*2+1=3
        assertEquals(0.0f, grids.get(1).get(0), 1e-6f); // untouched
    }

    @Test
    public void testDecode1dArrayXyzpBasicMapping() {
        String corticalId = "cortical_test_1d";

        Map<String, XyzpNeuronSoA> xyzp = new HashMap<>();
        xyzp.put(corticalId, new XyzpNeuronSoA(
                List.of(0, 2),
                List.of(0, 0),
                List.of(0, 0),
                List.of(1.0f, 0.5f)));

        List<Float> out = XyzpDecoders.decode1dArrayXyzp(xyzp, corticalId, 4);
        assertEquals(1.0f, out.get(0), 1e-6f);
        assertEquals(0.5f, out.get(2), 1e-6f);
        assertEquals(0.0f, out.get(1), 1e-6f);
        assertEquals(0.0f, out.get(3), 1e-6f);
    }

    @Test
    public void testDecodeSensorXyzpToGridOutOfBoundsCoordinatesAreSkipped() {
        String corticalId = "cortical_oob";
        Map<String, XyzpNeuronSoA> xyzp = new HashMap<>();

        // x=2 is out of bounds for width=2 => should be silently skipped.
        xyzp.put(corticalId, new XyzpNeuronSoA(
                List.of(2),
                List.of(0),
                List.of(0),
                List.of(9.0f)));

        Map<Integer, List<Float>> grids =
                XyzpDecoders.decodeSensorXyzpToGrid(xyzp, corticalId, 2, 2, 2);

        assertEquals(0.0f, grids.get(0).get(0), 1e-6f);
        assertEquals(0.0f, grids.get(0).get(1), 1e-6f);
        assertEquals(0.0f, grids.get(1).get(0), 1e-6f);
    }

    @Test
    public void testDecodeSensorXyzpToGridMismatchedCoordinateLengthsReturnsZeros() {
        String corticalId = "cortical_mismatch";
        Map<String, XyzpNeuronSoA> xyzp = new HashMap<>();

        xyzp.put(corticalId, new XyzpNeuronSoA(
                List.of(0, 1), // x length 2
                List.of(0),    // y length 1
                List.of(0),    // z length 1
                List.of(1.0f)  // p length 1
        ));

        Map<Integer, List<Float>> grids =
                XyzpDecoders.decodeSensorXyzpToGrid(xyzp, corticalId, 2, 2, 1);

        assertEquals(0.0f, grids.get(0).get(0), 1e-6f);
        assertEquals(0.0f, grids.get(0).get(3), 1e-6f);
    }

    @Test
    public void testDecode1dArrayXyzpOutOfRangeIsSkipped() {
        String corticalId = "cortical_1d_oob";
        Map<String, XyzpNeuronSoA> xyzp = new HashMap<>();

        // x=3 is out of bounds for length=3 (valid indices: 0..2)
        xyzp.put(corticalId, new XyzpNeuronSoA(
                List.of(3),
                List.of(0),
                List.of(0),
                List.of(1.0f)));

        List<Float> out = XyzpDecoders.decode1dArrayXyzp(xyzp, corticalId, 3);
        assertEquals(0.0f, out.get(0), 1e-6f);
        assertEquals(0.0f, out.get(1), 1e-6f);
        assertEquals(0.0f, out.get(2), 1e-6f);
    }

    @Test
    public void testDecode1dArrayXyzpMismatchedXPLengthsReturnsZeros() {
        String corticalId = "cortical_1d_mismatch";
        Map<String, XyzpNeuronSoA> xyzp = new HashMap<>();

        xyzp.put(corticalId, new XyzpNeuronSoA(
                List.of(0, 1),
                List.of(0, 0),
                List.of(0, 0),
                List.of(1.0f))); // p length 1, mismatch with x length 2

        List<Float> out = XyzpDecoders.decode1dArrayXyzp(xyzp, corticalId, 3);
        assertEquals(0.0f, out.get(0), 1e-6f);
        assertEquals(0.0f, out.get(1), 1e-6f);
        assertEquals(0.0f, out.get(2), 1e-6f);
    }
}

