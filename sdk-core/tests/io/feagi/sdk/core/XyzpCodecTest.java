/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link XyzpCodec} and {@link NeuronPotential}.
 */
class XyzpCodecTest {

    // ── NeuronPotential ───────────────────────────────────────────────────────

    @Test
    void neuronPotential_rejectsNegativeId() {
        assertThrows(IllegalArgumentException.class, () -> NeuronPotential.of(-1, 0.5f));
    }

    @Test
    void neuronPotential_zeroIdAllowed() {
        NeuronPotential n = NeuronPotential.of(0, 1.0f);
        assertEquals(0, n.neuronId());
        assertEquals(1.0f, n.potential());
    }

    @Test
    void neuronPotential_equalsAndHashCode() {
        NeuronPotential a = NeuronPotential.of(42, 0.75f);
        NeuronPotential b = NeuronPotential.of(42, 0.75f);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void neuronPotential_notEqualWhenDifferent() {
        assertNotEquals(NeuronPotential.of(1, 0.5f), NeuronPotential.of(2, 0.5f));
        assertNotEquals(NeuronPotential.of(1, 0.5f), NeuronPotential.of(1, 0.6f));
    }

    // ── Coordinate helpers ────────────────────────────────────────────────────

    @Test
    void toFlatId_originIsZero() {
        assertEquals(0, XyzpCodec.toFlatId(0, 0, 0, 10, 10));
    }

    @Test
    void toFlatId_rowMajorOrder() {
        // x=1, y=0, z=0 in 10×10 area → flat id = 1
        assertEquals(1, XyzpCodec.toFlatId(1, 0, 0, 10, 10));
        // x=0, y=1, z=0 → flat id = 10
        assertEquals(10, XyzpCodec.toFlatId(0, 1, 0, 10, 10));
        // x=0, y=0, z=1 → flat id = 100
        assertEquals(100, XyzpCodec.toFlatId(0, 0, 1, 10, 10));
    }

    @Test
    void toFlatId_rejectsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> XyzpCodec.toFlatId(-1, 0, 0, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> XyzpCodec.toFlatId(10, 0, 0, 10, 10)); // x == width
        assertThrows(IllegalArgumentException.class, () -> XyzpCodec.toFlatId(0, 10, 0, 10, 10)); // y == height
        assertThrows(IllegalArgumentException.class, () -> XyzpCodec.toFlatId(0, 0, -1, 10, 10));
    }

    @Test
    void fromFlatId_roundTrip() {
        int width = 8, height = 6;
        for (int z = 0; z < 3; z++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int flat = XyzpCodec.toFlatId(x, y, z, width, height);
                    int[] xyz = XyzpCodec.fromFlatId(flat, width, height);
                    assertEquals(x, xyz[0], "x mismatch at (" + x + "," + y + "," + z + ")");
                    assertEquals(y, xyz[1], "y mismatch");
                    assertEquals(z, xyz[2], "z mismatch");
                }
            }
        }
    }

    // ── encodeXyzp / decodeXyzp ───────────────────────────────────────────────

    @Test
    void encodeXyzp_emptyList_producesEmptyArray() {
        byte[] bytes = XyzpCodec.encodeXyzp(List.of());
        assertEquals(0, bytes.length);
    }

    @Test
    void encodeXyzp_singleEntry_correctBytes() {
        byte[] bytes = XyzpCodec.encodeXyzp(List.of(NeuronPotential.of(7, 0.5f)));
        assertEquals(8, bytes.length);

        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(7, bb.getInt());
        assertEquals(0.5f, bb.getFloat(), 1e-6f);
    }

    @Test
    void decodeXyzp_roundTrip() {
        List<NeuronPotential> original = List.of(
                NeuronPotential.of(0,  1.0f),
                NeuronPotential.of(1,  0.5f),
                NeuronPotential.of(99, 0.0f));
        byte[] encoded = XyzpCodec.encodeXyzp(original);
        List<NeuronPotential> decoded = XyzpCodec.decodeXyzp(encoded);
        assertEquals(original, decoded);
    }

    @Test
    void decodeXyzp_rejectsNonMultipleOf8() {
        assertThrows(IllegalArgumentException.class, () -> XyzpCodec.decodeXyzp(new byte[7]));
        assertThrows(IllegalArgumentException.class, () -> XyzpCodec.decodeXyzp(new byte[9]));
    }

    @Test
    void decodeXyzp_emptyArray_returnsEmptyList() {
        List<NeuronPotential> result = XyzpCodec.decodeXyzp(new byte[0]);
        assertTrue(result.isEmpty());
    }

    @Test
    void decodeXyzp_resultIsUnmodifiable() {
        byte[] bytes = XyzpCodec.encodeXyzp(List.of(NeuronPotential.of(1, 0.5f)));
        List<NeuronPotential> decoded = XyzpCodec.decodeXyzp(bytes);
        assertThrows(UnsupportedOperationException.class, () -> decoded.add(NeuronPotential.of(2, 0.1f)));
    }

    // ── encodeContainer (single channel) ─────────────────────────────────────

    @Test
    void encodeContainer_singleChannel_correctFormat() {
        List<NeuronPotential> neurons = List.of(NeuronPotential.of(3, 0.8f));
        byte[] container = XyzpCodec.encodeContainer("i__inf", neurons);

        ByteBuffer bb = ByteBuffer.wrap(container).order(ByteOrder.BIG_ENDIAN);
        int keyLen = bb.getInt();
        byte[] keyBytes = new byte[keyLen];
        bb.get(keyBytes);
        assertEquals("i__inf", new String(keyBytes, java.nio.charset.StandardCharsets.UTF_8));

        int valLen = bb.getInt();
        assertEquals(8, valLen); // 1 neuron × 8 bytes

        byte[] valBytes = new byte[valLen];
        bb.get(valBytes);
        ByteBuffer vb = ByteBuffer.wrap(valBytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(3, vb.getInt());
        assertEquals(0.8f, vb.getFloat(), 1e-6f);

        assertFalse(bb.hasRemaining(), "No trailing bytes expected");
    }

    // ── encodeContainer / decodeContainer round-trip ─────────────────────────

    @Test
    void containerRoundTrip_singleChannel() {
        List<NeuronPotential> neurons = List.of(
                NeuronPotential.of(0, 1.0f),
                NeuronPotential.of(5, 0.3f));
        byte[] container = XyzpCodec.encodeContainer("camera", neurons);
        Map<String, List<NeuronPotential>> decoded = XyzpCodec.decodeContainer(container);

        assertEquals(1, decoded.size());
        assertTrue(decoded.containsKey("camera"));
        assertEquals(neurons, decoded.get("camera"));
    }

    @Test
    void containerRoundTrip_multiChannel_preservesOrder() {
        Map<String, List<NeuronPotential>> channels = new LinkedHashMap<>();
        channels.put("i__inf", List.of(NeuronPotential.of(0, 1.0f)));
        channels.put("i__bat", List.of(NeuronPotential.of(1, 0.5f), NeuronPotential.of(2, 0.2f)));
        channels.put("camera", List.of());

        byte[] container = XyzpCodec.encodeContainer(channels);
        Map<String, List<NeuronPotential>> decoded = XyzpCodec.decodeContainer(container);

        assertEquals(channels.keySet().stream().toList(),
                decoded.keySet().stream().toList(),
                "Channel order must be preserved");
        assertEquals(channels.get("i__inf"), decoded.get("i__inf"));
        assertEquals(channels.get("i__bat"), decoded.get("i__bat"));
        assertTrue(decoded.get("camera").isEmpty());
    }

    @Test
    void decodeContainer_emptyBytes_returnsEmptyMap() {
        Map<String, List<NeuronPotential>> result = XyzpCodec.decodeContainer(new byte[0]);
        assertTrue(result.isEmpty());
    }

    @Test
    void decodeContainer_truncatedKeyLength_throws() {
        // 3 bytes — too short for a 4-byte key length prefix
        assertThrows(IllegalArgumentException.class,
                () -> XyzpCodec.decodeContainer(new byte[]{0x00, 0x00, 0x01}));
    }

    @Test
    void decodeContainer_invalidValueLength_throws() {
        // key = "x" (1 byte), then value length = 7 (not multiple of 8)
        ByteBuffer bb = ByteBuffer.allocate(4 + 1 + 4).order(ByteOrder.BIG_ENDIAN);
        bb.putInt(1);       // key length
        bb.put((byte) 'x'); // key
        bb.putInt(7);       // invalid value length
        assertThrows(IllegalArgumentException.class,
                () -> XyzpCodec.decodeContainer(bb.array()));
    }

    @Test
    void decodeContainer_resultIsUnmodifiable() {
        byte[] container = XyzpCodec.encodeContainer("ch", List.of(NeuronPotential.of(1, 0.5f)));
        Map<String, List<NeuronPotential>> decoded = XyzpCodec.decodeContainer(container);
        assertThrows(UnsupportedOperationException.class, () -> decoded.put("x", List.of()));
    }

    // ── encodeContainer multi-channel guards ──────────────────────────────────

    @Test
    void encodeContainer_emptyMap_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> XyzpCodec.encodeContainer(Collections.emptyMap()));
    }
}
