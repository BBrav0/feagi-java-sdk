/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.observability;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Structured data logger for sensory and motor data packets.
 *
 * <p>Logs sensory input and motor output data in structured formats:
 * <ul>
 *   <li>JSON - One file with array of entries</li>
 *   <li>JSONL - JSON Lines (one JSON object per line)</li>
 *   <li>CSV - Tabular format</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * // Create logger
 * DataLogger logger = new DataLogger.Builder()
 *     .outputFile("agent_data.jsonl")
 *     .format(DataLogger.Format.JSONL)
 *     .logInputs(true)
 *     .logOutputs(true)
 *     .sampleRate(1.0)  // Log 100% of packets
 *     .build();
 *
 * // Attach to brain input/output
 * brainInput.attachMonitor(logger);
 * brainOutput.attachMonitor(logger);
 *
 * // ... run agent ...
 *
 * logger.close();  // Flush and close
 * }</pre>
 *
 * @see Monitor
 * @see MetricsCollector
 */
public class DataLogger implements Monitor {

    private static final Logger LOGGER = Logger.getLogger(DataLogger.class.getName());
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private final Path outputFile;
    private final Format format;
    private final boolean logInputs;
    private final boolean logOutputs;
    private final double sampleRate;
    private final boolean includeDataSamples;
    private final int maxSampleSize;
    private final boolean enabled;

    private final List<Map<String, Object>> entries;
    private PrintWriter fileHandle;
    private PrintWriter csvWriter;
    private long packetCounter;
    private final Random random;

    private boolean csvHeaderWritten;

    /**
     * Output format for data logging.
     */
    public enum Format {
        /** JSON array format */
        JSON,
        /** JSON Lines format (one JSON object per line) */
        JSONL,
        /** CSV tabular format */
        CSV
    }

    /**
     * Creates a new DataLogger.
     *
     * @param builder the builder
     * @throws IOException if file cannot be opened
     */
    private DataLogger(Builder builder) throws IOException {
        this.outputFile = Paths.get(builder.outputFile);
        this.format = builder.format != null ? builder.format : Format.JSONL;
        this.logInputs = builder.logInputs;
        this.logOutputs = builder.logOutputs;
        this.sampleRate = Math.max(0.0, Math.min(1.0, builder.sampleRate));
        this.includeDataSamples = builder.includeDataSamples;
        this.maxSampleSize = builder.maxSampleSize > 0 ? builder.maxSampleSize : 10;
        this.enabled = builder.enabled;

        this.entries = new ArrayList<>();
        this.random = new Random();
        this.packetCounter = 0;
        this.csvHeaderWritten = false;

        // Create output directory if needed
        Path parentDir = this.outputFile.getParent();
        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }

        // Open file based on format
        if (this.format == Format.JSONL) {
            this.fileHandle = new PrintWriter(new FileWriter(this.outputFile.toFile(), false));
        } else if (this.format == Format.CSV) {
            this.fileHandle = new PrintWriter(new FileWriter(this.outputFile.toFile(), false));
            // Write header
            writeCsvHeader();
        }
        // JSON format accumulates in memory (entries list)

        if (enabled) {
            LOGGER.info("Data logger initialized: " + outputFile + " (" + format + ")");
        }
    }

    /**
     * Writes CSV header row.
     */
    private void writeCsvHeader() {
        if (fileHandle != null && !csvHeaderWritten) {
            fileHandle.println("timestamp,type,cortical_areas,neuron_count,packet_size_bytes,duration_ms,command_count");
            fileHandle.flush();
            csvHeaderWritten = true;
        }
    }

    /**
     * Determines if this packet should be logged based on sample rate.
     *
     * @return true if packet should be logged
     */
    private boolean shouldLogPacket() {
        return random.nextDouble() < sampleRate;
    }

    /**
     * Formats current timestamp.
     *
     * @return ISO format timestamp string
     */
    private String formatTimestamp() {
        return ZonedDateTime.now(ZoneId.systemDefault()).format(TIMESTAMP_FORMATTER);
    }

    /**
     * Checks if monitoring is enabled.
     *
     * @return true if enabled
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Called when sensory data send completes.
     *
     * @param data event data containing neuron_count, packet_size_bytes, duration_ms, etc.
     */
    @Override
    public void onSendComplete(Map<String, Object> data) {
        if (!enabled || !logInputs) {
            return;
        }

        if (!shouldLogPacket()) {
            return;
        }

        packetCounter++;

        Map<String, Object> entry = new HashMap<>();
        entry.put("timestamp", formatTimestamp());
        entry.put("packet_id", packetCounter);
        entry.put("type", "sensory_input");

        // Extract fields
        Object neuronCountObj = data.get("neuron_count");
        Object packetSizeObj = data.get("packet_size_bytes");
        Object durationObj = data.get("duration_ms");
        Object corticalAreasObj = data.get("cortical_areas");

        if (neuronCountObj instanceof Number) {
            entry.put("neuron_count", ((Number) neuronCountObj).longValue());
        }
        if (packetSizeObj instanceof Number) {
            entry.put("packet_size_bytes", ((Number) packetSizeObj).longValue());
        }
        if (durationObj instanceof Number) {
            entry.put("duration_ms", ((Number) durationObj).doubleValue());
        }
        if (corticalAreasObj instanceof List) {
            entry.put("cortical_areas", new ArrayList<>((List<?>) corticalAreasObj));
        }

        // Include data sample if requested
        if (includeDataSamples && data.containsKey("data_sample")) {
            Object dataSample = data.get("data_sample");
            if (dataSample instanceof List) {
                List<?> sample = (List<?>) dataSample;
                int limit = Math.min(sample.size(), maxSampleSize);
                entry.put("data_sample", sample.subList(0, limit));
            }
        }

        writeEntry(entry);
    }

    /**
     * Called when motor command receive completes.
     *
     * @param data event data containing command_count, duration_ms, etc.
     */
    @Override
    public void onReceiveComplete(Map<String, Object> data) {
        if (!enabled || !logOutputs) {
            return;
        }

        if (!shouldLogPacket()) {
            return;
        }

        packetCounter++;

        Map<String, Object> entry = new HashMap<>();
        entry.put("timestamp", formatTimestamp());
        entry.put("packet_id", packetCounter);
        entry.put("type", "motor_output");

        // Extract fields
        Object commandCountObj = data.get("command_count");
        Object durationObj = data.get("duration_ms");

        if (commandCountObj instanceof Number) {
            entry.put("command_count", ((Number) commandCountObj).longValue());
        }
        if (durationObj instanceof Number) {
            entry.put("duration_ms", ((Number) durationObj).doubleValue());
        }

        // Include commands sample if requested
        if (includeDataSamples && data.containsKey("commands")) {
            Object commands = data.get("commands");
            if (commands instanceof List) {
                List<?> cmds = (List<?>) commands;
                int limit = Math.min(cmds.size(), maxSampleSize);
                entry.put("commands_sample", cmds.subList(0, limit));
            }
        }

        writeEntry(entry);
    }

    /**
     * Writes entry to file based on format.
     *
     * @param entry the log entry to write
     */
    private void writeEntry(Map<String, Object> entry) {
        Objects.requireNonNull(entry, "entry must not be null");

        if (format == Format.JSONL) {
            // JSON Lines format
            if (fileHandle != null) {
                fileHandle.println(mapToJsonLine(entry));
                fileHandle.flush();
            }
        } else if (format == Format.JSON) {
            // JSON array format (accumulate in memory)
            entries.add(entry);
        } else if (format == Format.CSV) {
            // CSV format
            writeCsvEntry(entry);
        }
    }

    /**
     * Writes entry in CSV format.
     *
     * @param entry the log entry
     */
    private void writeCsvEntry(Map<String, Object> entry) {
        if (fileHandle == null) return;

        // Ensure header is written
        if (!csvHeaderWritten) {
            writeCsvHeader();
        }

        Object corticalAreasObj = entry.get("cortical_areas");
        String corticalAreasStr = "";
        if (corticalAreasObj instanceof List) {
            corticalAreasStr = String.join(",", (List<String>) corticalAreasObj);
        }

        fileHandle.printf(Locale.US, "%s,%s,%s,%d,%d,%.2f,%d%n",
            entry.getOrDefault("timestamp", ""),
            entry.getOrDefault("type", ""),
            corticalAreasStr,
            getLongValue(entry, "neuron_count"),
            getLongValue(entry, "packet_size_bytes"),
            getDoubleValue(entry, "duration_ms"),
            getLongValue(entry, "command_count")
        );
        fileHandle.flush();
    }

    /**
     * Gets a long value from map safely.
     *
     * @param map the map
     * @param key the key
     * @return long value or 0
     */
    private long getLongValue(Map<String, Object> map, String key) {
        Object obj = map.get(key);
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        return 0L;
    }

    /**
     * Gets a double value from map safely.
     *
     * @param map the map
     * @param key the key
     * @return double value or 0.0
     */
    private double getDoubleValue(Map<String, Object> map, String key) {
        Object obj = map.get(key);
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        return 0.0;
    }

    /**
     * Converts map to JSON line string.
     *
     * @param map the map to convert
     * @return JSON string
     */
    private String mapToJsonLine(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;

            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            Object value = entry.getValue();
            sb.append(valueToJson(value));
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * Converts object to JSON value string.
     *
     * @param value the value
     * @return JSON string representation
     */
    @SuppressWarnings("unchecked")
    private String valueToJson(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + escapeJson((String) value) + "\"";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof List) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            boolean first = true;
            for (Object item : (List<?>) value) {
                if (!first) sb.append(",");
                first = false;
                sb.append(valueToJson(item));
            }
            sb.append("]");
            return sb.toString();
        } else if (value instanceof Map) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<String, Object>) value).entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(escapeJson((String) entry.getKey())).append("\":");
                sb.append(valueToJson(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        } else {
            return "\"" + escapeJson(value.toString()) + "\"";
        }
    }

    /**
     * Escapes special characters for JSON string.
     *
     * @param s the string to escape
     * @return escaped string
     */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Closes the log file and flushes data.
     *
     * @throws IOException if write fails
     */
    public void close() throws IOException {
        if (format == Format.JSON && !entries.isEmpty()) {
            // Write accumulated entries to JSON file
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile.toFile()))) {
                writer.println("[");
                for (int i = 0; i < entries.size(); i++) {
                    writer.print("  ");
                    writer.println(mapToJsonLine(entries.get(i)));
                    if (i < entries.size() - 1) {
                        writer.println(",");
                    } else {
                        writer.println();
                    }
                }
                writer.println("]");
            }
        }

        if (fileHandle != null) {
            fileHandle.close();
            fileHandle = null;
        }

        if (enabled) {
            LOGGER.info("Data logger closed: " + packetCounter + " packets logged");
        }
    }

    /**
     * Returns the number of packets logged.
     *
     * @return packet count
     */
    public long getPacketCount() {
        return packetCounter;
    }

    /**
     * Builder for DataLogger.
     */
    public static class Builder {
        private String outputFile = "agent_data.log";
        private Format format = Format.JSONL;
        private boolean logInputs = true;
        private boolean logOutputs = true;
        private double sampleRate = 1.0;
        private boolean includeDataSamples = false;
        private int maxSampleSize = 10;
        private boolean enabled = true;

        /**
         * Sets the output file path.
         *
         * @param path the file path
         * @return this builder
         */
        public Builder outputFile(String path) {
            this.outputFile = path;
            return this;
        }

        /**
         * Sets the output format.
         *
         * @param format the output format
         * @return this builder
         */
        public Builder format(Format format) {
            this.format = format;
            return this;
        }

        /**
         * Sets whether to log input data.
         *
         * @param logInputs true to log inputs
         * @return this builder
         */
        public Builder logInputs(boolean logInputs) {
            this.logInputs = logInputs;
            return this;
        }

        /**
         * Sets whether to log output data.
         *
         * @param logOutputs true to log outputs
         * @return this builder
         */
        public Builder logOutputs(boolean logOutputs) {
            this.logOutputs = logOutputs;
            return this;
        }

        /**
         * Sets the sample rate (fraction of packets to log).
         *
         * @param sampleRate rate between 0.0 and 1.0
         * @return this builder
         */
        public Builder sampleRate(double sampleRate) {
            this.sampleRate = sampleRate;
            return this;
        }

        /**
         * Sets whether to include actual data samples.
         *
         * @param includeDataSamples true to include samples
         * @return this builder
         */
        public Builder includeDataSamples(boolean includeDataSamples) {
            this.includeDataSamples = includeDataSamples;
            return this;
        }

        /**
         * Sets the maximum number of data points to include in samples.
         *
         * @param maxSampleSize maximum sample size
         * @return this builder
         */
        public Builder maxSampleSize(int maxSampleSize) {
            this.maxSampleSize = maxSampleSize;
            return this;
        }

        /**
         * Sets whether the logger is enabled.
         *
         * @param enabled true to enable
         * @return this builder
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Builds the DataLogger.
         *
         * @return new DataLogger instance
         * @throws IOException if file cannot be opened
         */
        public DataLogger build() throws IOException {
            return new DataLogger(this);
        }
    }
}
