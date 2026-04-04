/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import io.feagi.sdk.core.motor.Motor;
import io.feagi.sdk.core.motor.RotaryMotor;
import io.feagi.sdk.core.motor.ServoMotor;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Decodes raw motor data from FEAGI into structured motor values.
 *
 * <p>This decoder handles the conversion of raw byte payloads received
 * from FEAGI into MotorDataFrame objects containing immutable snapshots
 * of motor values.
 *
 * <p>The FEAGI motor data protocol format (based on typical implementation):
 * <ul>
 *   <li>Message type byte (identifies the data type)</li>
 *   <li>Payload data (motor values encoded as normalized floats or bytes)</li>
 * </ul>
 *
 * <p>Supported formats:
 * <ul>
 *   <li>Simple format: Each motor value is a single float (4 bytes)</li>
 *   <li>Grouped format: Magic byte 0xFE prefix, then [groupId][outputCount][values...]</li>
 *   <li>JSON format: JSON-encoded motor data (starts with '{' or '[')</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class creates immutable snapshots during decode,
 * so returned MotorDataFrame objects are safe to use across threads and will not
 * be affected by subsequent decode calls.
 */
public final class MotorDataDecoder {

    private static final Logger LOG = Logger.getLogger(MotorDataDecoder.class.getName());

    /** Magic byte indicating grouped format */
    private static final byte GROUPED_FORMAT_MAGIC = (byte) 0xFE;

    /** Magic byte indicating simple binary format with header */
    private static final byte SIMPLE_BINARY_MAGIC = (byte) 0xFD;

    // Mutable maps for motor instances - not final to allow updates
    private final Map<String, MotorOutputSpec> registeredOutputs;
    private final Map<String, Motor> motorInstances;
    private volatile boolean initialized;

    /**
     * Create a new decoder with no registered outputs.
     *
     * <p>Use {@link #registerOutputs(Map)} or {@link #registerOutput(MotorOutputSpec)}
     * to add outputs before decoding.
     */
    public MotorDataDecoder() {
        this.registeredOutputs = new HashMap<>();
        this.motorInstances = new HashMap<>();
        this.initialized = false;
    }

    /**
     * Create a decoder with motor instances directly.
     *
     * <p>This constructor accepts the motor instances map from BrainOutput,
     * which maps motor names to Motor objects.
     *
     * @param motorInstances map of motor name to Motor instance
     */
    public MotorDataDecoder(Map<String, Motor> motorInstances) {
        this.registeredOutputs = new HashMap<>();
        this.motorInstances = motorInstances != null ?
                new HashMap<>(motorInstances) : new HashMap<>();
        this.initialized = !this.motorInstances.isEmpty();
    }

    /**
     * Register outputs with this decoder.
     *
     * @param outputs map of output name to MotorOutputSpec
     */
    public void registerOutputs(Map<String, MotorOutputSpec> outputs) {
        Objects.requireNonNull(outputs, "outputs must not be null");

        this.registeredOutputs.clear();
        this.motorInstances.clear();

        for (Map.Entry<String, MotorOutputSpec> entry : outputs.entrySet()) {
            registerOutput(entry.getValue());
        }
        this.initialized = !this.motorInstances.isEmpty();
    }

    /**
     * Register a single output.
     *
     * @param output output specification to register
     */
    public void registerOutput(MotorOutputSpec output) {
        Objects.requireNonNull(output, "output must not be null");
        Motor motor = output.createMotor();
        registeredOutputs.put(output.getName(), output);
        motorInstances.put(output.getName(), motor);
        this.initialized = true;
    }

    /**
     * Decode raw bytes into a motor data frame with immutable snapshots.
     *
     * <p>This method creates snapshots of motor values, so the returned
     * frame is not affected by subsequent decode calls.
     *
     * @param rawData raw byte payload from FEAGI
     * @return decoded MotorDataFrame with snapshots, or empty frame if no data
     */
    public MotorDataFrame decode(byte[] rawData) {
        if (rawData == null || rawData.length == 0) {
            return MotorDataFrame.empty();
        }

        if (!initialized || motorInstances.isEmpty()) {
            LOG.warning("No outputs registered, returning empty frame");
            return MotorDataFrame.empty();
        }

        try {
            // Try JSON format first (starts with '{' or '[')
            if (isJsonFormat(rawData)) {
                return decodeJsonFormat(rawData);
            }
            // Check for grouped format (magic byte 0xFE)
            if (isGroupedFormat(rawData)) {
                return decodeGroupedFormat(rawData);
            }
            // Check for simple binary with header (magic byte 0xFD)
            if (isSimpleBinaryFormat(rawData)) {
                return decodeSimpleBinaryFormat(rawData);
            }
            // Fall back to raw float format (legacy)
            return decodeRawFloatFormat(rawData);
        } catch (Exception e) {
            LOG.warning("Error decoding motor data: " + e.getMessage());
            return MotorDataFrame.empty();
        }
    }

    /**
     * Check if data appears to be JSON format.
     */
    private boolean isJsonFormat(byte[] data) {
        if (data.length < 1) return false;
        byte first = data[0];
        return first == '{' || first == '[';
    }

    /**
     * Check if data appears to be grouped format.
     * Grouped format starts with magic byte 0xFE.
     */
    private boolean isGroupedFormat(byte[] data) {
        if (data.length < 3) return false;
        return data[0] == GROUPED_FORMAT_MAGIC;
    }

    /**
     * Check if data appears to be simple binary format with header.
     * Simple binary format starts with magic byte 0xFD.
     */
    private boolean isSimpleBinaryFormat(byte[] data) {
        if (data.length < 5) return false;
        return data[0] == SIMPLE_BINARY_MAGIC;
    }

    /**
     * Decode JSON format motor data.
     *
     * <p>Supported JSON format:
     * <pre>{@code
     * {"motor_name": 0.5, "another_motor": 0.75}
     * }</pre>
     *
     * <p>Or with wrapper:
     * <pre>{@code
     * {"motors": {"motor_name": 0.5}}
     * }</pre>
     */
    private MotorDataFrame decodeJsonFormat(byte[] rawData) {
        String json = new String(rawData, StandardCharsets.UTF_8);
        try {
            // Parse values from JSON - create snapshots
            Map<String, Motor.Snapshot> snapshots = new HashMap<>();
            parseJsonToSnapshots(json, snapshots);
            if (snapshots.isEmpty()) {
                return MotorDataFrame.empty();
            }
            return MotorDataFrame.fromSnapshots(snapshots, System.currentTimeMillis());
        } catch (Exception e) {
            LOG.warning("Failed to parse JSON motor data: " + e.getMessage());
            return MotorDataFrame.empty();
        }
    }

    /**
     * Parse JSON and create motor snapshots.
     */
    private void parseJsonToSnapshots(String json, Map<String, Motor.Snapshot> snapshots) {
        // Find the object bounds
        int start = json.indexOf('{');
        if (start < 0) {
            return;
        }
        int end = json.lastIndexOf('}');
        if (end <= start) {
            return;
        }

        String content = json.substring(start + 1, end).trim();

        // Check for nested structure like {"motors": {...} }
        int colonIndex = content.indexOf(':');
        if (colonIndex > 0) {
            String key = content.substring(0, colonIndex).trim().replace("\"", "");
            if ("motors".equals(key) || "motor".equals(key)) {
                String inner = content.substring(colonIndex + 1).trim();
                if (inner.startsWith("{")) {
                    parseMotorValuesToSnapshots(inner, snapshots);
                    return;
                }
            }
        }

        // Direct motor values
        parseMotorValuesToSnapshots("{" + content + "}", snapshots);
    }

    /**
     * Parse motor values from JSON content and create snapshots.
     */
    private void parseMotorValuesToSnapshots(String content, Map<String, Motor.Snapshot> snapshots) {
        // Simple JSON key-value parsing
        int i = content.indexOf('{');
        if (i < 0) return;
        i++; // Skip opening brace

        while (i < content.length()) {
            // Skip whitespace
            while (i < content.length() && Character.isWhitespace(content.charAt(i))) {
                i++;
            }
            if (i >= content.length() || content.charAt(i) == '}') {
                break;
            }

            // Find key (between quotes)
            if (content.charAt(i) != '"') {
                i++;
                continue;
            }
            i++; // Skip opening quote
            int keyStart = i;
            while (i < content.length() && content.charAt(i) != '"') {
                i++;
            }
            if (i >= content.length()) break;
            String key = content.substring(keyStart, i);
            i++; // Skip closing quote

            // Skip whitespace and colon
            while (i < content.length() && (content.charAt(i) == ':' || Character.isWhitespace(content.charAt(i)))) {
                i++;
            }

            // Find value
            int valueStart = i;
            while (i < content.length() && content.charAt(i) != ',' && content.charAt(i) != '}') {
                i++;
            }
            String valueStr = content.substring(valueStart, i).trim();

            // Try to find corresponding motor and create snapshot
            Motor motor = motorInstances.get(key);
            if (motor != null) {
                try {
                    double value = Double.parseDouble(valueStr);
                    Motor.Snapshot snapshot = motor.createSnapshot(value, System.currentTimeMillis());
                    snapshots.put(key, snapshot);
                } catch (NumberFormatException e) {
                    LOG.warning("Failed to parse motor value for " + key + ": " + valueStr);
                }
            }

            // Skip comma
            while (i < content.length() && content.charAt(i) == ',') {
                i++;
            }
        }
    }

    /**
     * Decode grouped format motor data.
     * Format: [0xFE magic][groupId][outputCount][float values...]
     */
    private MotorDataFrame decodeGroupedFormat(byte[] rawData) {
        ByteBuffer buffer = ByteBuffer.wrap(rawData);

        // Skip magic byte
        buffer.get();

        int groupId = buffer.get() & 0xFF;
        int outputCount = buffer.get() & 0xFF;
        long timestamp = System.currentTimeMillis();

        Map<String, Motor.Snapshot> snapshots = new HashMap<>();

        for (int i = 0; i < outputCount && buffer.remaining() >= 4; i++) {
            float value = buffer.getFloat();

            // Find motors matching this group and index, create snapshots
            for (Map.Entry<String, Motor> entry : motorInstances.entrySet()) {
                Motor motor = entry.getValue();
                if (motor.getGroupId() == groupId && motor.getOutputIndex() == i) {
                    Motor.Snapshot snapshot = motor.createSnapshot(value, timestamp);
                    snapshots.put(entry.getKey(), snapshot);
                }
            }
        }

        return MotorDataFrame.fromSnapshots(snapshots, timestamp);
    }

    /**
     * Decode simple binary format with header.
     * Format: [0xFD magic][payload...]
     */
    private MotorDataFrame decodeSimpleBinaryFormat(byte[] rawData) {
        ByteBuffer buffer = ByteBuffer.wrap(rawData);

        // Skip magic byte
        buffer.get();

        long timestamp = System.currentTimeMillis();
        Map<String, Motor.Snapshot> snapshots = new HashMap<>();
        int index = 0;

        while (buffer.remaining() >= 4) {
            float value = buffer.getFloat();

            // Find motor at this index, create snapshot
            for (Map.Entry<String, Motor> entry : motorInstances.entrySet()) {
                Motor motor = entry.getValue();
                if (motor.getOutputIndex() == index) {
                    Motor.Snapshot snapshot = motor.createSnapshot(value, timestamp);
                    snapshots.put(entry.getKey(), snapshot);
                    break;
                }
            }
            index++;
        }

        return MotorDataFrame.fromSnapshots(snapshots, timestamp);
    }

    /**
     * Decode raw float format (legacy - no header).
     * Format: [float][float][float]...
     */
    private MotorDataFrame decodeRawFloatFormat(byte[] rawData) {
        ByteBuffer buffer = ByteBuffer.wrap(rawData);
        long timestamp = System.currentTimeMillis();
        Map<String, Motor.Snapshot> snapshots = new HashMap<>();
        int index = 0;

        while (buffer.remaining() >= 4) {
            float value = buffer.getFloat();

            // Find motor at this index, create snapshot
            for (Map.Entry<String, Motor> entry : motorInstances.entrySet()) {
                Motor motor = entry.getValue();
                if (motor.getOutputIndex() == index) {
                    Motor.Snapshot snapshot = motor.createSnapshot(value, timestamp);
                    snapshots.put(entry.getKey(), snapshot);
                    break;
                }
            }
            index++;
        }

        return MotorDataFrame.fromSnapshots(snapshots, timestamp);
    }

    /**
     * Return the number of registered motors.
     *
     * @return motor count
     */
    public int getMotorCount() {
        return motorInstances.size();
    }

    /**
     * Check if the decoder has any registered motors.
     *
     * @return true if motors are registered
     */
    public boolean hasMotors() {
        return !motorInstances.isEmpty();
    }
}