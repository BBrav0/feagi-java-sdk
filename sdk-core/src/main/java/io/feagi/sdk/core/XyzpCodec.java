/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * XYZP encoder/decoder for FEAGI neuron (id, potential) data.
 *
 * <h2>XYZP wire format</h2>
 * Each neuron entry is 8 bytes, little-endian:
 * <pre>
 *   [0..3] int32  neuron_id   — flat neuron index (non-negative)
 *   [4..7] float32 potential  — firing potential (typically 0.0–1.0)
 * </pre>
 * A payload is a sequence of zero or more such 8-byte entries with no framing.
 *
 * <h2>Byte-container wrapping</h2>
 * FEAGI's sensory transport wraps channel payloads in a byte-container:
 * <pre>
 *   [4 bytes big-endian] channel name length
 *   [N bytes UTF-8]      channel name
 *   [4 bytes big-endian] XYZP payload length
 *   [M bytes]            XYZP payload
 * </pre>
 * Use {@link #encodeContainer(String, List)} to produce a ready-to-send
 * byte-container, and {@link #decodeContainer(byte[])} to parse one back out.
 *
 * <h2>Coordinate helpers</h2>
 * FEAGI cortical areas use 3-D {@code (x, y, z)} positions. Use
 * {@link #toFlatId(int, int, int, int, int)} to convert to a flat neuron index
 * and {@link #fromFlatId(int, int, int)} to go the other way.
 *
 * <h2>Usage — send sensory data</h2>
 * <pre>{@code
 * List<NeuronPotential> neurons = List.of(
 *         NeuronPotential.of(XyzpCodec.toFlatId(0, 0, 0, 10, 10), 1.0f),
 *         NeuronPotential.of(XyzpCodec.toFlatId(1, 2, 0, 10, 10), 0.5f));
 *
 * byte[] payload = XyzpCodec.encodeContainer("camera", neurons);
 * client.sendSensoryBytes(payload);
 * }</pre>
 *
 * <h2>Usage — receive motor data</h2>
 * <pre>{@code
 * byte[] raw = client.pollMotorBytes();
 * if (raw != null) {
 *     List<NeuronPotential> motors = XyzpCodec.decodeXyzp(raw);
 *     for (NeuronPotential m : motors) {
 *         applyMotor(m.neuronId(), m.potential());
 *     }
 * }
 * }</pre>
 *
 * <h2>Placement</h2>
 * {@code sdk-core/src/main/java/io/feagi/sdk/core/XyzpCodec.java}
 */
public final class XyzpCodec {

    /** Bytes per XYZP entry: 4 (int32 id) + 4 (float32 potential). */
    public static final int BYTES_PER_ENTRY = 8;

    private XyzpCodec() {}

    // ── Coordinate helpers ────────────────────────────────────────────────────

    /**
     * Convert a 3-D cortical position to a flat neuron index.
     *
     * <p>FEAGI uses row-major order: {@code id = z * (width * height) + y * width + x}.
     *
     * @param x     x position ({@code 0 <= x < width})
     * @param y     y position ({@code 0 <= y < height})
     * @param z     z position ({@code >= 0})
     * @param width  cortical area width (x dimension)
     * @param height cortical area height (y dimension)
     * @return non-negative flat neuron index
     * @throws IllegalArgumentException if any dimension is out of range
     */
    public static int toFlatId(int x, int y, int z, int width, int height) {
        if (width <= 0)  throw new IllegalArgumentException("width must be > 0");
        if (height <= 0) throw new IllegalArgumentException("height must be > 0");
        if (x < 0 || x >= width)   throw new IllegalArgumentException("x=" + x + " out of [0," + width + ")");
        if (y < 0 || y >= height)  throw new IllegalArgumentException("y=" + y + " out of [0," + height + ")");
        if (z < 0)                 throw new IllegalArgumentException("z must be >= 0");
        long flat = (long) z * width * height + (long) y * width + x;
        if (flat > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Computed flatId " + flat + " exceeds Integer.MAX_VALUE");
        }
        return (int) flat;
    }

    /**
     * Convert a flat neuron index back to a 3-D {@code [x, y, z]} position.
     *
     * @param flatId non-negative flat neuron index
     * @param width  cortical area width (x dimension)
     * @param height cortical area height (y dimension)
     * @return {@code int[3]} — {@code [x, y, z]}
     */
    public static int[] fromFlatId(int flatId, int width, int height) {
        if (flatId < 0)  throw new IllegalArgumentException("flatId must be >= 0");
        if (width <= 0)  throw new IllegalArgumentException("width must be > 0");
        if (height <= 0) throw new IllegalArgumentException("height must be > 0");
        long slice = (long) width * height;
        int  z     = (int)  (flatId / slice);
        int  rem   = (int)  (flatId % slice);
        int y = rem / width;
        int x = rem % width;
        return new int[]{x, y, z};
    }

    // ── XYZP raw encoding / decoding ─────────────────────────────────────────

    /**
     * Encode a list of {@link NeuronPotential} entries into raw XYZP bytes.
     *
     * <p>Each entry is 8 bytes little-endian: {@code [int32 neuronId][float32 potential]}.
     * The list may be empty, producing a zero-length array.
     *
     * @param neurons non-null list of neuron potentials
     * @return raw XYZP bytes (length = {@code neurons.size() * 8})
     */
    public static byte[] encodeXyzp(List<NeuronPotential> neurons) {
        Objects.requireNonNull(neurons, "neurons must not be null");
        byte[] buf = new byte[neurons.size() * BYTES_PER_ENTRY];
        ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
        for (NeuronPotential n : neurons) {
            bb.putInt(n.neuronId());
            bb.putFloat(n.potential());
        }
        return buf;
    }

    /**
     * Decode raw XYZP bytes into a list of {@link NeuronPotential} entries.
     *
     * <p>Bytes must be a multiple of 8. An empty array produces an empty list.
     *
     * @param xyzpBytes raw XYZP bytes; must not be null
     * @return unmodifiable list of decoded neuron potentials
     * @throws IllegalArgumentException if {@code xyzpBytes.length} is not a multiple of 8
     */
    public static List<NeuronPotential> decodeXyzp(byte[] xyzpBytes) {
        Objects.requireNonNull(xyzpBytes, "xyzpBytes must not be null");
        if (xyzpBytes.length % BYTES_PER_ENTRY != 0) {
            throw new IllegalArgumentException(
                    "xyzpBytes length must be a multiple of " + BYTES_PER_ENTRY
                    + ", got " + xyzpBytes.length);
        }
        if (xyzpBytes.length == 0) return Collections.emptyList();

        ByteBuffer bb = ByteBuffer.wrap(xyzpBytes).order(ByteOrder.LITTLE_ENDIAN);
        int count = xyzpBytes.length / BYTES_PER_ENTRY;
        List<NeuronPotential> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int   id  = bb.getInt();
            float pot = bb.getFloat();
            result.add(NeuronPotential.of(id, pot));
        }
        return Collections.unmodifiableList(result);
    }

    // ── Byte-container wrapping ───────────────────────────────────────────────

    /**
     * Encode neurons into a FEAGI byte-container payload ready for
     * {@link FeagiAgentClient#sendSensoryBytes(byte[])}.
     *
     * <p>Format:
     * <pre>
     *   [4 bytes big-endian] channel name UTF-8 length
     *   [N bytes]            channel name UTF-8
     *   [4 bytes big-endian] XYZP payload length
     *   [M bytes]            XYZP payload (neurons.size() * 8 bytes)
     * </pre>
     *
     * @param channelName FEAGI sensory channel name (e.g. {@code "i__inf"})
     * @param neurons     neurons to send; may be empty
     * @return byte-container payload
     */
    public static byte[] encodeContainer(String channelName, List<NeuronPotential> neurons) {
        Objects.requireNonNull(channelName, "channelName must not be null");
        Objects.requireNonNull(neurons, "neurons must not be null");

        byte[] keyBytes  = channelName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] xyzpBytes = encodeXyzp(neurons);

        byte[] buf = new byte[4 + keyBytes.length + 4 + xyzpBytes.length];
        ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN);
        bb.putInt(keyBytes.length);
        bb.put(keyBytes);
        bb.putInt(xyzpBytes.length);
        bb.put(xyzpBytes);
        return buf;
    }

    /**
     * Encode neurons for multiple channels into a single FEAGI byte-container payload.
     *
     * <p>Each channel occupies one {@code [key-length][key][value-length][value]} block,
     * concatenated in iteration order. Use this when multiple sensory modalities must be
     * sent in a single transport call.
     *
     * @param channels ordered map of channel name → neuron list; must not be null or empty
     * @return byte-container payload covering all channels
     */
    public static byte[] encodeContainer(Map<String, List<NeuronPotential>> channels) {
        Objects.requireNonNull(channels, "channels must not be null");
        if (channels.isEmpty()) {
             throw new IllegalArgumentException(
                     "channels must not be empty — omit the call rather than sending nothing");
        }
        for (Map.Entry<String, List<NeuronPotential>> e : channels.entrySet()) {
            Objects.requireNonNull(e.getKey(), "channel name must not be null");
            Objects.requireNonNull(e.getValue(), "neuron list for channel '" + e.getKey() + "' must not be null");
        }

        // Pre-compute total size
        int totalSize = 0;
        List<byte[]> keys  = new ArrayList<>(channels.size());
        List<byte[]> xyzps = new ArrayList<>(channels.size());
        for (Map.Entry<String, List<NeuronPotential>> e : channels.entrySet()) {
            byte[] k = e.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] v = encodeXyzp(e.getValue());
            keys.add(k);
            xyzps.add(v);
            totalSize += 4 + k.length + 4 + v.length;
        }

        byte[] buf = new byte[totalSize];
        ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN);
        for (int i = 0; i < keys.size(); i++) {
            bb.putInt(keys.get(i).length);
            bb.put(keys.get(i));
            bb.putInt(xyzps.get(i).length);
            bb.put(xyzps.get(i));
        }
        return buf;
    }

    /**
     * Decode a FEAGI byte-container payload into a channel → neuron-list map.
     *
     * <p>Reverses {@link #encodeContainer(Map)}. The returned map preserves
     * insertion order and is unmodifiable.
     *
     * @param containerBytes raw bytes from {@link FeagiAgentClient#pollMotorBytes()}
     *                       or a sensory echo; must not be null
     * @return unmodifiable map of channel name → decoded neuron potentials
     * @throws IllegalArgumentException if the bytes are malformed
     */
    public static Map<String, List<NeuronPotential>> decodeContainer(byte[] containerBytes) {
        Objects.requireNonNull(containerBytes, "containerBytes must not be null");
        if (containerBytes.length == 0) return Collections.emptyMap();

        ByteBuffer bb = ByteBuffer.wrap(containerBytes).order(ByteOrder.BIG_ENDIAN);
        Map<String, List<NeuronPotential>> result = new LinkedHashMap<>();

        while (bb.hasRemaining()) {
            if (bb.remaining() < 4) {
                throw new IllegalArgumentException(
                        "Truncated byte-container: expected 4 bytes for key length, "
                        + "got " + bb.remaining());
            }
            int keyLen = bb.getInt();
            if (keyLen < 0 || keyLen > bb.remaining()) {
                throw new IllegalArgumentException(
                        "Invalid key length " + keyLen
                        + " (remaining=" + bb.remaining() + ")");
            }
            byte[] keyBytes = new byte[keyLen];
            bb.get(keyBytes);
            String key = new String(keyBytes, java.nio.charset.StandardCharsets.UTF_8);

            if (bb.remaining() < 4) {
                throw new IllegalArgumentException(
                        "Truncated byte-container: expected 4 bytes for value length "
                        + "after key '" + key + "'");
            }
            int valLen = bb.getInt();
            if (valLen < 0 || valLen > bb.remaining()) {
                throw new IllegalArgumentException(
                        "Invalid value length " + valLen
                        + " for key '" + key + "' (remaining=" + bb.remaining() + ")");
            }
            if (valLen % BYTES_PER_ENTRY != 0) {
                throw new IllegalArgumentException(
                        "Value length " + valLen + " for key '" + key
                        + "' is not a multiple of " + BYTES_PER_ENTRY);
            }
            byte[] valBytes = new byte[valLen];
            bb.get(valBytes);
            result.put(key, decodeXyzp(valBytes));
        }

        return Collections.unmodifiableMap(result);
    }
}
