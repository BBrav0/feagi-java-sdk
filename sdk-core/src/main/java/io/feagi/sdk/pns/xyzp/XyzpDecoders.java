/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package io.feagi.sdk.pns.xyzp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * XYZP SoA decoders with parity to the Python reference implementation
 * in {@code python sdk for reference/feagi/pns/xyzp_decoders.py}.
 */
public final class XyzpDecoders {
    private static final Logger LOG = Logger.getLogger(XyzpDecoders.class.getName());

    private XyzpDecoders() {}

    /**
     * Decode motor output from XYZP SoA format to motor command mapping.
     *
     * <p>Parity with Python `decode_motor_xyzp` output keys:
     * <ul>
     *   <li>Lane decode paths: {@code "{group}:{channel}:{absolute|incremental}"}</li>
     *   <li>Legacy/non-signed fallback: {@code "{group}:{channel}"} (no command_mode)</li>
     * </ul>
     *
     * @param xyzpData     cortical_id -> SoA neuron data
     * @param corticalIds Optional filter of cortical IDs (null = decode all)
     * @param includeGroups If true, always emit group-qualified keys when possible
     */
    public static Map<String, Float> decodeMotorXyzp(
            Map<String, XyzpNeuronSoA> xyzpData,
            List<String> corticalIds,
            boolean includeGroups) {

        Map<String, Float> motors = new HashMap<>();
        if (xyzpData == null || xyzpData.isEmpty()) {
            return motors;
        }

        Set<Integer> groupsFound = new HashSet<>();
        Map<String, Integer> corticalGroupMap = new HashMap<>();

        // Collect cortical group ids from all cortical IDs (Python does not filter here).
        for (String corticalId : xyzpData.keySet()) {
            Integer groupId = CorticalIdUtils.parseCorticalUnitIndex(corticalId);
            corticalGroupMap.put(corticalId, groupId);
            if (groupId != null) {
                groupsFound.add(groupId);
            }
        }

        // Match Python: when `include_groups` is enabled or any cortical_id parses to a
        // group/unit index, group-qualified keys are emitted.
        boolean useGroupKeys = includeGroups || !groupsFound.isEmpty();

        Set<String> corticalFilter = null;
        if (corticalIds != null) {
            corticalFilter = new HashSet<>(corticalIds);
        }

        for (Map.Entry<String, XyzpNeuronSoA> entry : xyzpData.entrySet()) {
            String corticalId = entry.getKey();
            if (corticalFilter != null && !corticalFilter.contains(corticalId)) {
                continue;
            }

            XyzpNeuronSoA neuronData = entry.getValue();
            if (neuronData == null) {
                continue;
            }

            try {
                List<Integer> xCoords = neuronData.x();
                List<Integer> yCoords = neuronData.y();
                List<Integer> zCoords = neuronData.z();
                List<Float> pValues = neuronData.p();

                int xLen = xCoords.size();
                int yLen = yCoords.size();
                int zLen = zCoords.size();
                int pLen = pValues.size();
                if (xLen != yLen || xLen != zLen || xLen != pLen) {
                    LOG.warning("Mismatched x/y/z/p lengths in " + corticalId);
                    continue;
                }

                Integer groupId = corticalGroupMap.get(corticalId);

                byte[] rawCid = CorticalIdUtils.parseRawCorticalIdBytes(corticalId);
                if (rawCid == null) {
                    // Robust fallback: decode by XYZ lanes/depth instead of p/100.
                    // Note: Python reference's fallback includes a bug where
                    // `positioning_fractional` is referenced before assignment.
                    // Real-world cortical IDs are expected to parse correctly;
                    // we conservatively default positioningFractional to false.
                    decodeMotorFallbackByXYZ(
                            motors,
                            xCoords,
                            yCoords,
                            zCoords,
                            pValues,
                            groupId,
                            useGroupKeys,
                            false);
                    continue;
                }

                byte[] unitRefBytes = new byte[] {rawCid[1], rawCid[2], rawCid[3]};
                String unitRef = new String(unitRefBytes, java.nio.charset.StandardCharsets.US_ASCII);

                int dataTypeFlag = (rawCid[4] & 0xFF) | ((rawCid[5] & 0xFF) << 8);
                int variant = dataTypeFlag & 0xFF;
                boolean frameIncremental = (((dataTypeFlag >> 8) & 0x01) == 1);
                boolean positioningFractional = (((dataTypeFlag >> 9) & 0x01) == 1);
                String commandMode = frameIncremental ? "incremental" : "absolute";

                boolean shouldDecodeLanes =
                        ("pse".equals(unitRef) || "mot".equals(unitRef))
                                && (variant == 5 || (variant == 1 && frameIncremental));

                if (shouldDecodeLanes) {
                    decodeMotorLanePairs(
                            motors,
                            xCoords,
                            yCoords,
                            zCoords,
                            pValues,
                            unitRef,
                            variant,
                            frameIncremental,
                            positioningFractional,
                            commandMode,
                            groupId,
                            useGroupKeys);
                    continue;
                }

                // Unsigned percentage absolute decode (single lane per channel):
                // X -> channel index, Z -> magnitude
                if (("pse".equals(unitRef) || "mot".equals(unitRef))
                        && variant == 1
                        && !frameIncremental) {
                    decodeMotorAbsoluteUnsignedSingleLane(
                            motors,
                            zCoords,
                            xCoords,
                            pValues,
                            unitRef,
                            positioningFractional,
                            commandMode,
                            groupId,
                            useGroupKeys);
                    continue;
                }

                // Fallback for non-signed-percentage motor formats.
                for (int i = 0; i < xLen; i++) {
                    int motorIdx = xCoords.get(i);
                    float power = pValues.get(i);
                    String channelKey = String.valueOf(motorIdx);
                    if (useGroupKeys && groupId != null) {
                        channelKey = groupId + ":" + channelKey;
                    }
                    motors.put(channelKey, power / 100.0f);
                }
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Error decoding motor data from " + corticalId + ": " + e.getMessage(), e);
            }
        }

        return motors;
    }

    /**
     * Decode sensory input from XYZP SoA to 3D grid format:
     * channel -> flat row-major array of size width * height.
     *
     * <p>Parity with Python `decode_sensor_xyzp_to_grid`.
     */
    public static Map<Integer, List<Float>> decodeSensorXyzpToGrid(
            Map<String, XyzpNeuronSoA> xyzpData,
            String corticalId,
            int width,
            int height,
            int channels) {

        int size = width * height;
        Map<Integer, List<Float>> grids = new LinkedHashMap<>();
        for (int ch = 0; ch < channels; ch++) {
            grids.put(ch, new ArrayList<>(size));
            List<Float> arr = grids.get(ch);
            for (int i = 0; i < size; i++) {
                arr.add(0.0f);
            }
        }

        if (xyzpData == null || corticalId == null || !xyzpData.containsKey(corticalId)) {
            return grids;
        }

        XyzpNeuronSoA neuronData = xyzpData.get(corticalId);
        if (neuronData == null) {
            return grids;
        }

        List<Integer> xCoords = neuronData.x();
        List<Integer> yCoords = neuronData.y();
        List<Integer> zCoords = neuronData.z();
        List<Float> pValues = neuronData.p();

        if (!(xCoords.size() == yCoords.size()
                && xCoords.size() == zCoords.size()
                && xCoords.size() == pValues.size())) {
            LOG.warning("Mismatched coordinate lengths in " + corticalId);
            return grids;
        }

        int n = xCoords.size();
        for (int i = 0; i < n; i++) {
            int x = xCoords.get(i);
            int y = yCoords.get(i);
            int z = zCoords.get(i);
            float p = pValues.get(i);

            if (0 <= x && x < width && 0 <= y && y < height && 0 <= z && z < channels) {
                int flatIdx = y * width + x; // row-major
                grids.get(z).set(flatIdx, p);
            }
        }

        return grids;
    }

    /**
     * Decode a 1D array (e.g., proximity sensors) from XYZP SoA.
     *
     * <p>Parity with Python `decode_1d_array_xyzp`.
     */
    public static List<Float> decode1dArrayXyzp(
            Map<String, XyzpNeuronSoA> xyzpData,
            String corticalId,
            int length) {

        List<Float> array = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            array.add(0.0f);
        }

        if (xyzpData == null || corticalId == null || !xyzpData.containsKey(corticalId)) {
            return array;
        }

        XyzpNeuronSoA neuronData = xyzpData.get(corticalId);
        if (neuronData == null) {
            return array;
        }

        List<Integer> xCoords = neuronData.x();
        List<Float> pValues = neuronData.p();

        if (xCoords.size() != pValues.size()) {
            LOG.warning("Mismatched x/p lengths in " + corticalId);
            return array;
        }

        int n = xCoords.size();
        for (int i = 0; i < n; i++) {
            int x = xCoords.get(i);
            if (0 <= x && x < length) {
                array.set(x, pValues.get(i));
            }
        }

        return array;
    }

    // ---------------------------------------------------------------------
    // Motor internals (parity with python reference)
    // ---------------------------------------------------------------------

    private static void decodeMotorFallbackByXYZ(
            Map<String, Float> motors,
            List<Integer> xCoords,
            List<Integer> yCoords,
            List<Integer> zCoords,
            List<Float> pValues,
            Integer groupId,
            boolean useGroupKeys,
            boolean positioningFractional /* currently defaulted to false in caller */) {

        // This fallback branch mirrors Python's structure-of-arrays lane decode:
        // even/odd X lanes => forward/backward, and Z depth stabilizes scaling.
        Map<Integer, List<Integer>> positiveByChannel = new HashMap<>();
        Map<Integer, List<Integer>> negativeByChannel = new HashMap<>();
        int maxZSeen = 0;

        int n = xCoords.size();
        for (int i = 0; i < n; i++) {
            float p = pValues.get(i);
            if (p == 0.0f) {
                continue;
            }

            int xInt = xCoords.get(i);
            int zInt = zCoords.get(i);
            maxZSeen = Math.max(maxZSeen, zInt);

            int channelIdx = xInt / 2;
            if (xInt % 2 == 0) {
                positiveByChannel.computeIfAbsent(channelIdx, k -> new ArrayList<>()).add(zInt);
            } else {
                negativeByChannel.computeIfAbsent(channelIdx, k -> new ArrayList<>()).add(zInt);
            }
        }

        if (!positiveByChannel.isEmpty() || !negativeByChannel.isEmpty()) {
            // Python fallback rule when CID parsing fails uses a fixed depth >= 10.
            int zDepth = Math.max(10, maxZSeen + 1);
            Set<Integer> channelIds = new HashSet<>();
            channelIds.addAll(positiveByChannel.keySet());
            channelIds.addAll(negativeByChannel.keySet());

            for (int channelIdx : channelIds) {
                List<Integer> zPos = positiveByChannel.getOrDefault(channelIdx, List.of());
                List<Integer> zNeg = negativeByChannel.getOrDefault(channelIdx, List.of());

                float decoded;
                if (positioningFractional) {
                    float pos = (float) decodeUnsignedPercentageFractional(zPos);
                    float neg = (float) decodeUnsignedPercentageFractional(zNeg);
                    decoded = (float) clamp(-1.0, 1.0, pos - neg);
                } else {
                    float pos = (float) decodeUnsignedPercentageLinear(zPos, zDepth);
                    float neg = (float) decodeUnsignedPercentageLinear(zNeg, zDepth);
                    decoded = (float) clamp(-1.0, 1.0, pos - neg);
                }

                String channelKey = String.valueOf(channelIdx);
                channelKey = channelKey + ":incremental";
                if (useGroupKeys && groupId != null) {
                    channelKey = groupId + ":" + channelKey;
                }
                motors.put(channelKey, decoded);
            }
            return;
        }

        // Last resort: if no usable XYZ lanes, emit motor_idx -> power/100 as incremental.
        for (int i = 0; i < xCoords.size(); i++) {
            int motorIdx = xCoords.get(i);
            float power = pValues.get(i);
            String channelKey = String.valueOf(motorIdx) + ":incremental";
            if (useGroupKeys && groupId != null) {
                channelKey = groupId + ":" + channelKey;
            }
            motors.put(channelKey, power / 100.0f);
        }
    }

    private static void decodeMotorLanePairs(
            Map<String, Float> motors,
            List<Integer> xCoords,
            List<Integer> yCoords,
            List<Integer> zCoords,
            List<Float> pValues,
            String unitRef,
            int variant,
            boolean frameIncremental,
            boolean positioningFractional,
            String commandMode,
            Integer groupId,
            boolean useGroupKeys) {

        Map<Integer, List<Integer>> positiveByChannel = new HashMap<>();
        Map<Integer, List<Integer>> negativeByChannel = new HashMap<>();
        int maxZSeen = 0;

        int n = xCoords.size();
        for (int i = 0; i < n; i++) {
            float p = pValues.get(i);
            if (p == 0.0f) {
                continue;
            }

            int xInt = xCoords.get(i);
            int zInt = zCoords.get(i);
            maxZSeen = Math.max(maxZSeen, zInt);

            int channelIdx = xInt / 2;
            if (xInt % 2 == 0) {
                positiveByChannel.computeIfAbsent(channelIdx, k -> new ArrayList<>()).add(zInt);
            } else {
                negativeByChannel.computeIfAbsent(channelIdx, k -> new ArrayList<>()).add(zInt);
            }
        }

        int zDepth = resolveMotorLinearDepth(unitRef, maxZSeen + 1);
        Set<Integer> channelIds = new HashSet<>();
        channelIds.addAll(positiveByChannel.keySet());
        channelIds.addAll(negativeByChannel.keySet());

        for (int channelIdx : channelIds) {
            List<Integer> zPos = positiveByChannel.getOrDefault(channelIdx, List.of());
            List<Integer> zNeg = negativeByChannel.getOrDefault(channelIdx, List.of());

            float decoded;
            if (variant == 5) {
                if (positioningFractional) {
                    decoded = (float) decodeSignedPercentageFractional(zPos, zNeg);
                } else {
                    decoded = (float) decodeSignedPercentageLinear(zPos, zNeg, zDepth);
                }
            } else {
                float positive;
                float negative;
                if (positioningFractional) {
                    positive = (float) decodeUnsignedPercentageFractional(zPos);
                    negative = (float) decodeUnsignedPercentageFractional(zNeg);
                } else {
                    positive = (float) decodeUnsignedPercentageLinear(zPos, zDepth);
                    negative = (float) decodeUnsignedPercentageLinear(zNeg, zDepth);
                }
                decoded = (float) clamp(-1.0, 1.0, positive - negative);
            }

            String channelKey = String.valueOf(channelIdx) + ":" + commandMode;
            if (useGroupKeys && groupId != null) {
                channelKey = groupId + ":" + channelKey;
            }
            motors.put(channelKey, decoded);
        }
    }

    private static void decodeMotorAbsoluteUnsignedSingleLane(
            Map<String, Float> motors,
            List<Integer> zCoords,
            List<Integer> xCoords,
            List<Float> pValues,
            String unitRef,
            boolean positioningFractional,
            String commandMode,
            Integer groupId,
            boolean useGroupKeys) {

        Map<Integer, List<Integer>> zByChannel = new HashMap<>();
        int maxZSeen = 0;

        int n = xCoords.size();
        for (int i = 0; i < n; i++) {
            float p = pValues.get(i);
            if (p == 0.0f) {
                continue;
            }

            int channelIdx = xCoords.get(i);
            int zInt = zCoords.get(i);
            maxZSeen = Math.max(maxZSeen, zInt);
            zByChannel.computeIfAbsent(channelIdx, k -> new ArrayList<>()).add(zInt);
        }

        int zDepth = resolveMotorLinearDepth(unitRef, maxZSeen + 1);
        for (Map.Entry<Integer, List<Integer>> e : zByChannel.entrySet()) {
            int channelIdx = e.getKey();
            List<Integer> zValues = e.getValue();

            float decodedUnsigned;
            if (positioningFractional) {
                decodedUnsigned = (float) decodeUnsignedPercentageFractional(zValues);
            } else {
                decodedUnsigned = (float) decodeUnsignedPercentageLinear(zValues, zDepth);
            }
            float decoded = (float) normalizeUnsignedToSigned(decodedUnsigned);

            String channelKey = String.valueOf(channelIdx) + ":" + commandMode;
            if (useGroupKeys && groupId != null) {
                channelKey = groupId + ":" + channelKey;
            }
            motors.put(channelKey, decoded);
        }
    }

    private static double decodeSignedPercentageLinear(
            List<Integer> zPositive,
            List<Integer> zNegative,
            int zDepth) {
        if (zDepth <= 0) {
            return 0.0;
        }

        int zSpan = Math.max(1, zDepth - 1);

        double positive = 0.0;
        if (!zPositive.isEmpty()) {
            double sum = 0.0;
            for (int z : zPositive) {
                sum += z;
            }
            positive = 1.0 - (sum / (zSpan * (double) zPositive.size()));
        }

        double negative = 0.0;
        if (!zNegative.isEmpty()) {
            double sum = 0.0;
            for (int z : zNegative) {
                sum += z;
            }
            negative = 1.0 - (sum / (zSpan * (double) zNegative.size()));
        }

        return clamp(-1.0, 1.0, positive - negative);
    }

    private static double decodeSignedPercentageFractional(
            List<Integer> zPositive,
            List<Integer> zNegative) {
        double positive = 0.0;
        for (int z : zPositive) {
            positive += Math.pow(0.5, z);
        }
        double negative = 0.0;
        for (int z : zNegative) {
            negative += Math.pow(0.5, z);
        }
        return clamp(-1.0, 1.0, positive - negative);
    }

    private static double decodeUnsignedPercentageLinear(
            List<Integer> zValues,
            int zDepth) {
        if (zDepth <= 0 || zValues.isEmpty()) {
            return 0.0;
        }
        int zSpan = Math.max(1, zDepth - 1);

        double sum = 0.0;
        for (int z : zValues) {
            sum += z;
        }
        double decodedUnsigned = 1.0 - (sum / (zSpan * (double) zValues.size()));
        return clamp(0.0, 1.0, decodedUnsigned);
    }

    private static double decodeUnsignedPercentageFractional(List<Integer> zValues) {
        if (zValues.isEmpty()) {
            return 0.0;
        }
        double decodedUnsigned = 0.0;
        for (int z : zValues) {
            decodedUnsigned += Math.pow(0.5, z);
        }
        return clamp(0.0, 1.0, decodedUnsigned);
    }

    private static double normalizeUnsignedToSigned(double value0to1) {
        double decoded = (value0to1 * 2.0) - 1.0;
        return clamp(-1.0, 1.0, decoded);
    }

    private static int resolveMotorLinearDepth(String unitRef, int observedDepth) {
        if ("pse".equals(unitRef) || "mot".equals(unitRef)) {
            return Math.max(10, observedDepth);
        }
        return Math.max(1, observedDepth);
    }

    private static double clamp(double min, double max, double value) {
        return Math.max(min, Math.min(max, value));
    }
}

