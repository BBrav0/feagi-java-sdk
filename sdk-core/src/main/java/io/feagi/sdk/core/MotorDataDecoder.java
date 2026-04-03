/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import io.feagi.sdk.core.motor.Motor;
import io.feagi.sdk.core.motor.RotaryMotor;
import io.feagi.sdk.core.motor.ServoMotor;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Decodes raw motor data from FEAGI into structured motor values.
 *
 * <p>This decoder handles the conversion of raw byte payloads received
 * from FEAGI into MotorDataFrame objects. The decoded data can be
 * used to update registered Motor instances.
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
 *   <li>Grouped format: [groupId][outputCount][values...]</li>
 *   <li>JSON format: JSON-encoded motor data</li>
 * </ul>
 */
public final class MotorDataDecoder {

    private static final Logger LOG = Logger.getLogger(MotorDataDecoder.class.getName());

    private final Map<String, MotorOutputSpec> registeredOutputs;
    private final Map<String, Motor> motorInstances;
    private volatile boolean initialized;

    /**
     * Create a new decoder with no registered outputs.
     */
    public MotorDataDecoder() {
        this.registeredOutputs = Collections.emptyMap();
        this.motorInstances = Collections.emptyMap();
        this.initialized = false;
    }

    /**
     * Create a decoder with registered outputs.
     *
     * @param registeredOutputs map of output name to MotorOutputSpec
     */
    public MotorDataDecoder(Map<String, MotorOutputSpec> registeredOutputs) {
        this.registeredOutputs = registeredOutputs != null ?
                Collections.unmodifiableMap(new HashMap<>(registeredOutputs)) : Collections.emptyMap();
        this.motorInstances = new HashMap<>();
        initializeMotors();
        this.initialized = true;
    }

    /**
     * Register outputs with this decoder.
     *
     * @param outputs output specifications to register
     */
    public void registerOutputs(Map<String, MotorOutputSpec> outputs) {
        if (outputs == null) {
            this.registeredOutputs = Collections.emptyMap();
            this.motorInstances.clear();
            this.initialized = false;
            return;
        }
        for (Map.Entry<String, MotorOutputSpec> entry : outputs.entrySet()) {
            registerOutput(entry.getValue());
        }
        this.initialized = true;
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
    }

    /**
     * Decode raw bytes into a motor data frame.
     *
     * @param rawData raw byte payload from FEAGI
     * @return decoded MotorDataFrame, or empty frame if no data
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
            // Try JSON format first (most flexible)
            if (isJsonFormat(rawData)) {
                return decodeJsonFormat(rawData);
            }
            // Try grouped format
            if (isGroupedFormat(rawData)) {
                return decodeGroupedFormat(rawData);
            }
            // Fall back to simple format
            return decodeSimpleFormat(rawData);
        } catch (Exception e) {
            LOG.warning("Error decoding motor data: " + e.getMessage());
            return MotorDataFrame.empty();
        }
    }

    /**
     * Check if data appears to be JSON format.
     */
    private boolean isJsonFormat(byte[] data) {
        if (data.length < 2) return false;
        byte first = data[0];
        return first == '{' || first == '[';
    }

    /**
     * Check if data appears to be grouped format.
     * Grouped format: [groupId][outputCount][values...]
     */
    private boolean isGroupedFormat(byte[] data) {
        if (data.length < 2) return false;
        int outputCount = data[1] & 0xFF;
        int expectedLength = 2 + (outputCount * 4);
        return data.length >= expectedLength;
    }

    /**
     * Decode JSON format motor data.
     */
    private MotorDataFrame decodeJsonFormat(byte[] rawData) {
        String json = new String(rawData, java.nio.charset.StandardCharsets.UTF_8);
        try {
            Map<String, Motor> decoded = new HashMap<>();
            int start = json.indexOf('{');
            if (start < 0) {
                return MotorDataFrame.empty();
            }
            int end = json.lastIndexOf('}');
            if (end <= start) {
                return MotorDataFrame.empty();
            }
            String content = json.substring(start, end + 1);
            parseSimpleJson(content, decoded);
            if (decoded.isEmpty()) {
                return MotorDataFrame.empty();
            }
            return new MotorDataFrame(decoded, System.currentTimeMillis());
        } catch (Exception e) {
            LOG.warning("Failed to parse JSON motor data: " + e.getMessage());
            return MotorDataFrame.empty();
        }
    }

    /**
     * Parse simple JSON format without external dependencies.
     */
    private void parseSimpleJson(String json, Map<String, Motor> decoded) {
        // Remove outer braces
        int start = 0;
        int end = json.length() - 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        while (end >= 0 && Character.isWhitespace(json.charAt(end))) {
            end--;
        }
        if (start >= end) {
            return;
        }
        String content = json.substring(start, end);
        // Check for nested structure like {"motors": {...} }
        if (content.startsWith("\"motors\":") || content.startsWith("\"motor\":")) {
            int colonIndex = content.indexOf(':');
            if (colonIndex > 0) {
                String inner = content.substring(colonIndex + 1).trim();
                parseMotorValues(inner, decoded);
            }
        } else {
            // Direct motor values
            parseMotorValues(content, decoded);
        }
    }

    /**
     * Parse motor values from JSON content.
     */
    private void parseMotorValues(String content, Map<String, Motor> decoded) {
        // Simple key-value parsing
        int i = 0;
        while (i < content.length()) {
            // Skip whitespace
            while (i < content.length() && Character.isWhitespace(content.charAt(i))) {
                i++;
            }
            if (i >= content.length()) {
                break;
            }
            // Find key
            int keyStart = i;
            while (i < content.length() && content.charAt(i) != ':' && content.charAt(i) != ',') {
                i++;
            }
            if (i >= content.length()) {
                break;
            }
            String key = content.substring(keyStart, i).trim().replace("\"", "");
            // Skip colon and whitespace
            while (i < content.length() && (content.charAt(i) == ':' || Character.isWhitespace(content.charAt(i)))) {
                i++;
            }
            // Find value
            int valueStart = i;
            while (i < content.length() && content.charAt(i) != ',' && content.charAt(i) != '}') {
                i++;
            }
            String valueStr = content.substring(valueStart, i).trim();
            // Try to find corresponding motor
            Motor motor = motorInstances.get(key);
            if (motor != null) {
                try {
                    double value = Double.parseDouble(valueStr);
                    updateMotorValue(motor, value);
                    decoded.put(key, motor);
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
     */
    private MotorDataFrame decodeGroupedFormat(byte[] rawData) {
        ByteBuffer buffer = ByteBuffer.wrap(rawData);
        int groupId = buffer.get() & 0xFF;
        int outputCount = buffer.get() & 0xFF;
        long timestamp = System.currentTimeMillis();
        Map<String, Motor> decoded = new HashMap<>();
        for (int i = 0; i < outputCount && buffer.remaining() >= 4; i++) {
            float value = buffer.getFloat();
            // Find motors matching this group and index
            for (Map.Entry<String, Motor> entry : motorInstances.entrySet()) {
                Motor motor = entry.getValue();
                if (motor.getGroupId() == groupId && motor.getOutputIndex() == i) {
                    updateMotorValue(motor, value);
                    decoded.put(entry.getKey(), motor);
                }
            }
        }
        return new MotorDataFrame(decoded, timestamp);
    }

    /**
     * Decode simple format motor data (sequential floats).
     */
    private MotorDataFrame decodeSimpleFormat(byte[] rawData) {
        ByteBuffer buffer = ByteBuffer.wrap(rawData);
        long timestamp = System.currentTimeMillis();
        Map<String, Motor> decoded = new HashMap<>();
        int index = 0;
        while (buffer.remaining() >= 4) {
            float value = buffer.getFloat();
            // Find motor at this index
            for (Map.Entry<String, Motor> entry : motorInstances.entrySet()) {
                Motor motor = entry.getValue();
                if (motor.getOutputIndex() == index) {
                    updateMotorValue(motor, value);
                    decoded.put(entry.getKey(), motor);
                    break;
                }
            }
            index++;
        }
        return new MotorDataFrame(decoded, timestamp);
    }

    /**
     * Initialize motor instances from registered outputs.
     */
    private void initializeMotors() {
        for (Map.Entry<String, MotorOutputSpec> entry : registeredOutputs.entrySet()) {
            MotorOutputSpec spec = entry.getValue();
            Motor motor = spec.createMotor();
            motorInstances.put(entry.getKey(), motor);
        }
    }

    /**
     * Update a motor with a new value.
     */
    private void updateMotorValue(Motor motor, double value) {
        long timestamp = System.currentTimeMillis();
        if (motor instanceof ServoMotor) {
            ((ServoMotor) motor).updateValue(value, timestamp);
        } else if (motor instanceof RotaryMotor) {
            ((RotaryMotor) motor).updateValue(value, timestamp);
        }
    }
}