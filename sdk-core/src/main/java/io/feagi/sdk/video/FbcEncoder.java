/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Encodes raw RGB image frames into FEAGI Byte Container (FBC) format
 * for transmission via feagiClientSendSensoryBytes.
 *
 * Wire format (little-endian throughout):
 *
 * FBC Global Header (4 bytes):
 *   [0]      version = 4
 *   [1..2]   increment counter (u16 LE)
 *   [3]      struct count (u8) = 1
 *
 * Agent ID (48 bytes):
 *   [4..51]  48-byte AgentDescriptor (zeroed when not set)
 *
 * Structure Lookup Header (4 bytes per struct):
 *   [52..55] data length of struct (u32 LE)
 *
 * NeuronCategoricalXYZP Struct:
 *   [0]      struct type = 11 (NeuronCategoricalXYZP)
 *   [1]      struct version = 1
 *   [2..3]   cortical area count (u16 LE)
 *
 *   Per cortical area sub-header (16 bytes each):
 *     [0..7]   cortical ID (8 bytes)
 *     [8..11]  data start index (u32 LE, absolute within struct)
 *     [12..15] data byte count (u32 LE)
 *
 *   Per cortical area neuron data (structure-of-arrays):
 *     X values: count * 4 bytes (u32 LE each)
 *     Y values: count * 4 bytes (u32 LE each)
 *     Z values: count * 4 bytes (u32 LE each)
 *     P values: count * 4 bytes (f32 LE each)
 *
 * Pixel → neuron mapping (from feagi-core image_frame.rs):
 *   x = col + (channelIndex * imageWidth)
 *   y = imageHeight - 1 - row   (flip Y: image top-left → FEAGI bottom-left)
 *   z = colorChannel (0=R, 1=G, 2=B)
 *   p = pixelValue as float      (skip pixels with value <= 1)
 *
 * Cortical ID encoding (from feagi-core CorticalID):
 *   8 bytes: category(1) + subtype(3) + group(1) + channel(1) + padding(2)
 *   For iimg (Simple Vision): category=0x69('i'), subtype="img", group, channel=0, pad=0,0
 */
package io.feagi.sdk.video;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Encodes RGB image frames into FEAGI Byte Container (FBC) format.
 *
 * <p>This bridges the gap between raw RGB bytes (produced by
 * {@link io.feagi.sdk.core.VideoStreamAgent}) and the wire format
 * that FEAGI's burst engine expects.
 *
 * <p>Equivalent to Python's {@code brain_input.send()} →
 * {@code ConnectorAgent.sensors_encode_cached_sensor_data_to_bytes()}.
 */
public final class FbcEncoder {

    // FBC constants (from feagi-serialization feagi_byte_container.rs)
    private static final int FBC_VERSION           = 4;
    private static final int GLOBAL_HEADER_BYTES   = 4;
    private static final int AGENT_ID_BYTES        = 48;
    private static final int STRUCT_LOOKUP_BYTES   = 4;   // u32 per struct
    private static final int STRUCT_HEADER_BYTES   = 2;   // type(u8) + version(u8)
    private static final int CORTICAL_COUNT_BYTES  = 2;   // u16
    private static final int CORTICAL_ID_BYTES     = 8;
    private static final int CORTICAL_SUBHDR_BYTES = 16;  // 8 + 4 + 4
    private static final int BYTES_PER_NEURON_COMP = 4;   // u32 or f32

    // NeuronCategoricalXYZP struct type id (from FeagiByteStructureType)
    private static final int STRUCT_TYPE_XYZP      = 11;
    private static final int STRUCT_VERSION_XYZP   = 1;

    // Pixel epsilon — skip pixels at or below this value (from image_frame.rs EPSILON=1)
    private static final int PIXEL_EPSILON         = 1;

    private static final int CHANNEL_INDEX         = 0; // single channel

    private final byte[] agentId;
    private int incrementCounter = 0;

    /**
     * Create encoder with zeroed agent ID.
     * Suitable for demo use where FEAGI accepts any agent.
     */
    public FbcEncoder() {
        this.agentId = new byte[AGENT_ID_BYTES];
    }

    /**
     * Create encoder with explicit 48-byte agent descriptor.
     *
     * @param agentDescriptor 48-byte AgentDescriptor
     */
    public FbcEncoder(byte[] agentDescriptor) {
        if (agentDescriptor == null || agentDescriptor.length != AGENT_ID_BYTES) {
            throw new IllegalArgumentException(
                    "agentDescriptor must be exactly 48 bytes");
        }
        this.agentId = agentDescriptor.clone();
    }

    /**
     * Encode one RGB frame to FBC bytes ready for {@code feagiClientSendSensoryBytes}.
     *
     * @param rgbBytes   raw RGB bytes, row-major, 3 bytes per pixel (R,G,B)
     * @param width      image width in pixels
     * @param height     image height in pixels
     * @param corticalId 8-byte cortical area ID for the vision input
     * @return FBC-encoded bytes
     */
    public byte[] encodeFrame(byte[] rgbBytes, int width, int height, byte[] corticalId) {
        if (rgbBytes == null || rgbBytes.length != width * height * 3) {
            throw new IllegalArgumentException(
                    "rgbBytes length must equal width*height*3");
        }
        if (corticalId == null || corticalId.length != CORTICAL_ID_BYTES) {
            throw new IllegalArgumentException(
                    "corticalId must be exactly 8 bytes");
        }

        // ── 1. Collect active neurons ─────────────────────────────────────────
        // Use ArrayList<int[]> for x,y,z and ArrayList<Float> for p
        int maxNeurons = width * height * 3;
        int[] xArr = new int[maxNeurons];
        int[] yArr = new int[maxNeurons];
        int[] zArr = new int[maxNeurons];
        float[] pArr = new float[maxNeurons];
        int neuronCount = 0;

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int pixelBase = (row * width + col) * 3;
                for (int c = 0; c < 3; c++) {
                    int pixelValue = rgbBytes[pixelBase + c] & 0xFF;
                    if (pixelValue <= PIXEL_EPSILON) continue;

                    xArr[neuronCount] = col + (CHANNEL_INDEX * width);
                    yArr[neuronCount] = (height - 1 - row);
                    zArr[neuronCount] = c;
                    pArr[neuronCount] = (float) pixelValue;
                    neuronCount++;
                }
            }
        }

        // ── 2. Calculate sizes ────────────────────────────────────────────────
        int neuronDataBytes = neuronCount * BYTES_PER_NEURON_COMP * 4; // x+y+z+p

        // Struct size: struct_header(2) + cortical_count(2) + cortical_subhdr(16) + neuron_data
        int structSize = STRUCT_HEADER_BYTES
                + CORTICAL_COUNT_BYTES
                + CORTICAL_SUBHDR_BYTES
                + neuronDataBytes;

        // Total FBC size
        int totalSize = GLOBAL_HEADER_BYTES
                + AGENT_ID_BYTES
                + STRUCT_LOOKUP_BYTES  // 1 struct
                + structSize;

        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);

        // ── 3. FBC Global Header ──────────────────────────────────────────────
        buf.put((byte) FBC_VERSION);                          // [0] version
        buf.putShort((short) (incrementCounter++ & 0xFFFF));  // [1..2] increment
        buf.put((byte) 1);                                    // [3] struct count = 1

        // ── 4. Agent ID (48 bytes) ────────────────────────────────────────────
        buf.put(agentId);

        // ── 5. Structure Lookup Header (4 bytes = struct data size) ──────────
        buf.putInt(structSize);

        // ── 6. NeuronCategoricalXYZP Struct ──────────────────────────────────

        // Struct header
        buf.put((byte) STRUCT_TYPE_XYZP);    // type = 11
        buf.put((byte) STRUCT_VERSION_XYZP); // version = 1

        // Cortical area count
        buf.putShort((short) 1); // always 1 cortical area for single vision

        // Cortical area sub-header
        // Cortical ID (8 bytes)
        buf.put(corticalId);

        // Data start index (absolute position within struct where neuron data starts)
        int dataStartIndex = STRUCT_HEADER_BYTES
                + CORTICAL_COUNT_BYTES
                + CORTICAL_SUBHDR_BYTES; // = 2 + 2 + 16 = 20
        buf.putInt(dataStartIndex);

        // Data byte count
        buf.putInt(neuronDataBytes);

        // Neuron data: structure-of-arrays format (X[], Y[], Z[], P[])
        for (int i = 0; i < neuronCount; i++) buf.putInt(xArr[i]);
        for (int i = 0; i < neuronCount; i++) buf.putInt(yArr[i]);
        for (int i = 0; i < neuronCount; i++) buf.putInt(zArr[i]);
        for (int i = 0; i < neuronCount; i++) buf.putFloat(pArr[i]);

        return buf.array();
    }

    /**
     * Build the 8-byte cortical ID for a Simple Vision (iimg) input.
     *
     * <p>Cortical ID layout (from feagi-core CorticalID):
     * <pre>
     *   [0]     category = 'i' (0x69) — input
     *   [1..3]  subtype  = "img"
     *   [4]     group    (u8)
     *   [5]     channel  = 0
     *   [6..7]  padding  = 0, 0
     * </pre>
     *
     * @param group cortical group index (typically 0)
     * @return 8-byte cortical ID
     */
    public static byte[] simpleVisionCorticalId(int group) {
        return new byte[]{
            (byte) 'i',   // category: input
            (byte) 'i',   // subtype[0]
            (byte) 'm',   // subtype[1]
            (byte) 'g',   // subtype[2]
            (byte) group, // group
            (byte) 0,     // channel
            (byte) 0,     // padding
            (byte) 0      // padding
        };
    }

    /**
     * Build the 8-byte cortical ID for a Segmented Vision (isvi) input.
     *
     * @param group cortical group index (typically 0)
     * @return 8-byte cortical ID
     */
    public static byte[] segmentedVisionCorticalId(int groupIndex) {
        return new byte[]{
            (byte) 105,  // 'i'
            (byte) 115,  // 's'
            (byte) 118,  // 'v'
            (byte) 105,  // 'i'
            (byte) 9,    // unit type
            (byte) 0,    // always 0
            (byte) groupIndex, // 4 = vision_C
            (byte) 0
        };
    }

    /**
     * Encode one RGB frame to FBC bytes with multiple cortical areas.
     *
     * @param frames     list of (corticalId, rgbBytes, width, height) to encode
     * @return FBC-encoded bytes
     */
    public byte[] encodeMultipleFrames(List<CorticalFrame> frames) {
        if (frames == null || frames.isEmpty()) return null;

        // Collect neurons per cortical area
        int[][] xArrays = new int[frames.size()][];
        int[][] yArrays = new int[frames.size()][];
        int[][] zArrays = new int[frames.size()][];
        float[][] pArrays = new float[frames.size()][];
        int[] counts = new int[frames.size()];

        for (int f = 0; f < frames.size(); f++) {
            CorticalFrame cf = frames.get(f);
            int maxNeurons = cf.width * cf.height * 3;
            xArrays[f] = new int[maxNeurons];
            yArrays[f] = new int[maxNeurons];
            zArrays[f] = new int[maxNeurons];
            pArrays[f] = new float[maxNeurons];
            int n = 0;
            for (int row = 0; row < cf.height; row++) {
                for (int col = 0; col < cf.width; col++) {
                    int base = (row * cf.width + col) * 3;
                    for (int c = 0; c < 3; c++) {
                        int v = cf.rgb[base + c] & 0xFF;
                        if (v <= PIXEL_EPSILON) continue;
                        xArrays[f][n] = col + (CHANNEL_INDEX * cf.width);
                        yArrays[f][n] = cf.height - 1 - row;
                        zArrays[f][n] = c;
                        pArrays[f][n] = v;
                        n++;
                    }
                }
            }
            counts[f] = n;
        }

        // Calculate struct size
        int structSize = STRUCT_HEADER_BYTES + CORTICAL_COUNT_BYTES;
        for (int f = 0; f < frames.size(); f++) {
            structSize += CORTICAL_SUBHDR_BYTES + counts[f] * BYTES_PER_NEURON_COMP * 4;
        }

        int totalSize = GLOBAL_HEADER_BYTES + AGENT_ID_BYTES + STRUCT_LOOKUP_BYTES + structSize;
        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);

        // FBC Global Header
        buf.put((byte) FBC_VERSION);
        buf.putShort((short) (incrementCounter++ & 0xFFFF));
        buf.put((byte) 1);

        // Agent ID
        buf.put(agentId);

        // Structure lookup header
        buf.putInt(structSize);

        // Struct header
        buf.put((byte) STRUCT_TYPE_XYZP);
        buf.put((byte) STRUCT_VERSION_XYZP);

        // Cortical area count
        buf.putShort((short) frames.size());

        // Calculate data start positions
        int subhdrEnd = STRUCT_HEADER_BYTES + CORTICAL_COUNT_BYTES
                + frames.size() * CORTICAL_SUBHDR_BYTES;
        int[] dataStarts = new int[frames.size()];
        dataStarts[0] = subhdrEnd;
        for (int f = 1; f < frames.size(); f++) {
            dataStarts[f] = dataStarts[f-1] + counts[f-1] * BYTES_PER_NEURON_COMP * 4;
        }

        // Write cortical sub-headers
        for (int f = 0; f < frames.size(); f++) {
            buf.put(frames.get(f).corticalId);
            buf.putInt(dataStarts[f]);
            buf.putInt(counts[f] * BYTES_PER_NEURON_COMP * 4);
        }

        // Write neuron data
        for (int f = 0; f < frames.size(); f++) {
            int n = counts[f];
            for (int i = 0; i < n; i++) buf.putInt(xArrays[f][i]);
            for (int i = 0; i < n; i++) buf.putInt(yArrays[f][i]);
            for (int i = 0; i < n; i++) buf.putInt(zArrays[f][i]);
            for (int i = 0; i < n; i++) buf.putFloat(pArrays[f][i]);
        }

        return buf.array();
    }

    /** Holder for a single cortical area's frame data. */
    public static class CorticalFrame {
        public final byte[] corticalId;
        public final byte[] rgb;
        public final int width;
        public final int height;
        public CorticalFrame(byte[] corticalId, byte[] rgb, int width, int height) {
            this.corticalId = corticalId;
            this.rgb = rgb;
            this.width = width;
            this.height = height;
        }
    }
}
