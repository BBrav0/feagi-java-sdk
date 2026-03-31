/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package io.feagi.sdk.pns.xyzp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class XyzpDecodersMotorTest {
    private static List<Integer> toIntList(int[] arr) {
        List<Integer> out = new ArrayList<>(arr.length);
        for (int v : arr) {
            out.add(v);
        }
        return out;
    }

    private static List<Float> toFloatList(float[] arr) {
        List<Float> out = new ArrayList<>(arr.length);
        for (float v : arr) {
            out.add(v);
        }
        return out;
    }

    private static String makeCorticalId(byte[] unit, int dataTypeFlag, int group) {
        if (unit == null || unit.length != 3) {
            throw new IllegalArgumentException("unit must be length 3");
        }
        byte[] b = new byte[] {
                (byte) 'o',
                unit[0],
                unit[1],
                unit[2],
                (byte) (dataTypeFlag & 0xFF),
                (byte) ((dataTypeFlag >> 8) & 0xFF),
                0,
                (byte) (group & 0xFF)
        };
        return java.util.Base64.getEncoder().encodeToString(b);
    }

    private static XyzpNeuronSoA soa(int[] x, int[] y, int[] z, float[] p) {
        return new XyzpNeuronSoA(
                toIntList(x),
                toIntList(y),
                toIntList(z),
                toFloatList(p));
    }

    private static XyzpNeuronSoA soa2(int[] x, int[] y, int[] z, float[] p) {
        return soa(x, y, z, p);
    }

    @Test
    public void testDecodeMotorXyzpSignedLinearEndpoints() {
        // SignedPercentage + absolute + linear => variant=5, frame=0, pos=0
        String cid = makeCorticalId(new byte[] {'p', 's', 'e'}, 5, 0);

        // Channel 0 positive lane (x=0), z=0 -> +1.0
        Map<String, XyzpNeuronSoA> xyzp = new HashMap<>();
        xyzp.put(cid, soa(
                new int[] {0}, new int[] {0}, new int[] {0}, new float[] {1.0f}));
        Map<String, Float> out = XyzpDecoders.decodeMotorXyzp(xyzp, List.of(cid), true);
        assertEquals(1.0f, out.get("0:0:absolute"), 1e-6f);

        // Channel 0 negative lane (x=1), z=0 -> -1.0
        xyzp = new HashMap<>();
        xyzp.put(cid, soa(
                new int[] {1}, new int[] {0}, new int[] {0}, new float[] {1.0f}));
        out = XyzpDecoders.decodeMotorXyzp(xyzp, List.of(cid), true);
        assertEquals(-1.0f, out.get("0:0:absolute"), 1e-6f);
    }

    @Test
    public void testDecodeMotorXyzpSignedIncrementalKeys() {
        // Signed incremental keys => variant=5, frame=1, command_mode incremental
        String cid = makeCorticalId(new byte[] {'p', 's', 'e'}, (5 | (1 << 8)), 3);
        Map<String, XyzpNeuronSoA> xyzp = new HashMap<>();
        xyzp.put(cid, soa(
                new int[] {0}, new int[] {0}, new int[] {0}, new float[] {1.0f}));

        Map<String, Float> out = XyzpDecoders.decodeMotorXyzp(xyzp, List.of(cid), true);
        assertEquals(1.0f, out.get("3:0:incremental"), 1e-6f);
    }

    @Test
    public void testDecodeMotorXyzpUnsignedIncrementalTwoLaneDecode() {
        // Percentage + incremental + linear => variant=1, frame=1, pos=0
        String cid = makeCorticalId(new byte[] {'p', 's', 'e'}, (1 | (1 << 8)), 4);
        Map<String, XyzpNeuronSoA> xyzp = new HashMap<>();
        // even X forward lane: x=0, p=1.0 ; odd X backward lane: x=1, p=0.0 (skipped)
        xyzp.put(cid, soa2(
                new int[] {0, 1},
                new int[] {0, 0},
                new int[] {0, 0},
                new float[] {1.0f, 0.0f}));

        Map<String, Float> out = XyzpDecoders.decodeMotorXyzp(xyzp, List.of(cid), true);
        assertEquals(1.0f, out.get("4:0:incremental"), 1e-6f);
    }

    @Test
    public void testDecodeMotorXyzpUnsignedAbsoluteSingleLaneDecode() {
        // Percentage + absolute + linear => variant=1, frame=0, pos=0
        String cid = makeCorticalId(new byte[] {'p', 's', 'e'}, 1, 5);
        Map<String, XyzpNeuronSoA> xyzp = new HashMap<>();
        xyzp.put(cid, soa(
                new int[] {0}, new int[] {0}, new int[] {0}, new float[] {1.0f}));

        Map<String, Float> out = XyzpDecoders.decodeMotorXyzp(xyzp, List.of(cid), true);
        assertEquals(1.0f, out.get("5:0:absolute"), 1e-6f);
    }

    @Test
    public void testDecodeMotorXyzpUnsignedAbsoluteLinearSpanHitsFullRange() {
        String cid = makeCorticalId(new byte[] {'p', 's', 'e'}, 1, 6);

        // Top bin -> +1.0
        Map<String, XyzpNeuronSoA> xyzp = new HashMap<>();
        xyzp.put(cid, soa(
                new int[] {0}, new int[] {0}, new int[] {0}, new float[] {1.0f}));
        Map<String, Float> outHi = XyzpDecoders.decodeMotorXyzp(xyzp, List.of(cid), true);
        assertEquals(1.0f, outHi.get("6:0:absolute"), 1e-6f);

        // Bottom bin in 10-depth map -> -1.0
        xyzp = new HashMap<>();
        xyzp.put(cid, soa(
                new int[] {0}, new int[] {0}, new int[] {9}, new float[] {1.0f}));
        Map<String, Float> outLo = XyzpDecoders.decodeMotorXyzp(xyzp, List.of(cid), true);
        assertEquals(-1.0f, outLo.get("6:0:absolute"), 1e-6f);
    }

    @Test
    public void testDecodeMotorXyzpLegacyPScalingFallback() {
        // Fallback path supports legacy p-based scaling.
        String cid = makeCorticalId(new byte[] {'m', 'i', 's'}, 10, 2);
        Map<String, XyzpNeuronSoA> xyzp = new HashMap<>();
        xyzp.put(cid, new XyzpNeuronSoA(
                List.of(0, 1),
                List.of(0, 0),
                List.of(0, 0),
                List.of(50.0f, -100.0f)));

        Map<String, Float> out = XyzpDecoders.decodeMotorXyzp(xyzp, List.of(cid), true);
        assertEquals(0.5f, out.get("2:0"), 1e-6f);
        assertEquals(-1.0f, out.get("2:1"), 1e-6f);
    }
}

