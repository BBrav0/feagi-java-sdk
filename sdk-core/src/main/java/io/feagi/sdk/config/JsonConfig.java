/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.feagi.sdk.core.AgentConfig;
import io.feagi.sdk.core.AgentType;
import io.feagi.sdk.core.FeagiEndpoints;
import io.feagi.sdk.pns.inputs.BaseInput;
import io.feagi.sdk.pns.outputs.BaseOutput;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

/**
 * JSON serialization and deserialization utilities for FEAGI SDK.
 *
 * <p>This class provides methods to load and export FEAGI configuration
 * and agent configurations from/to JSON files.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Export FeagiConfig to JSON
 * FeagiConfig config = new FeagiConfig.Builder().build();
 * JsonConfig.exportConfig(config, Path.of("config.json"));
 *
 * // Load FeagiConfig from JSON
 * FeagiConfig loaded = JsonConfig.loadConfig(Path.of("config.json"));
 *
 * // Export AgentConfig to JSON
 * JsonConfig.exportAgentConfig(agentConfig, Path.of("agent.json"));
 *
 * // Load AgentConfig from JSON
 * AgentConfig agent = JsonConfig.loadAgentConfig(Path.of("agent.json"));
 * }</pre>
 *
 * @see FeagiConfig
 * @see AgentConfig
 */
public final class JsonConfig {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(Duration.class, new DurationSerializer())
        .registerTypeAdapter(Duration.class, new DurationDeserializer())
        .registerTypeAdapter(AgentType.class, new AgentTypeSerializer())
        .registerTypeAdapter(AgentType.class, new AgentTypeDeserializer())
        .registerTypeAdapter(FeagiEndpoints.class, new FeagiEndpointsSerializer())
        .registerTypeAdapter(FeagiEndpoints.class, new FeagiEndpointsDeserializer())
        .create();

    private JsonConfig() {
        // Utility class - prevent instantiation
    }

    /**
     * Export FeagiConfig to a JSON file.
     *
     * @param config the configuration to export
     * @param outputPath the path to write the JSON file
     * @throws IOException if writing fails
     */
    public static void exportConfig(FeagiConfig config, Path outputPath) throws IOException {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");

        Path parent = outputPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("api", Map.of(
            "host", config.getApiHost(),
            "port", config.getApiPort()
        ));
        jsonMap.put("websocket", Map.of(
            "host", config.getWebsocketHost(),
            "enabled", config.isWebsocketEnabled(),
            "visualization_port", config.getVisualizationPort(),
            "sensory_port", config.getSensoryPort(),
            "motor_port", config.getMotorPort()
        ));
        jsonMap.put("burst_engine", Map.of(
            "burst_duration", config.getBurstDuration(),
            "max_bursts", config.getMaxBursts(),
            "gpu_enabled", config.isGpuEnabled()
        ));
        jsonMap.put("performance", Map.of(
            "worker_threads", config.getWorkerThreads(),
            "profiling_enabled", config.isProfilingEnabled()
        ));
        jsonMap.put("logging", Map.of(
            "level", config.getLogLevel(),
            "file_logging", config.isFileLogging()
        ));
        jsonMap.put("timeouts", Map.of(
            "service_startup", config.getServiceStartupTimeout().toMillis() / 1000.0
        ));
        jsonMap.put("connectome", Map.of(
            "neuron_space", config.getNeuronSpace(),
            "synapse_space", config.getSynapseSpace()
        ));

        try (FileWriter writer = new FileWriter(outputPath.toFile())) {
            GSON.toJson(jsonMap, writer);
        }
    }

    /**
     * Load FeagiConfig from a JSON file.
     *
     * @param jsonPath the path to the JSON file
     * @return the loaded configuration
     * @throws IOException if reading fails
     * @throws IllegalArgumentException if JSON is invalid
     */
    public static FeagiConfig loadConfig(Path jsonPath) throws IOException {
        Objects.requireNonNull(jsonPath, "jsonPath must not be null");

        if (!Files.exists(jsonPath)) {
            throw new IOException("JSON file not found: " + jsonPath);
        }

        try (FileReader reader = new FileReader(jsonPath.toFile())) {
            Map<?, ?> jsonMap = GSON.fromJson(reader, Map.class);

            if (jsonMap == null) {
                throw new IllegalArgumentException("Invalid JSON file: " + jsonPath);
            }

            return fromMap(toStringKeyMap(jsonMap), jsonPath.toString());
        }
    }

    /**
     * Export AgentConfig to a JSON file.
     *
     * @param config the agent configuration to export
     * @param outputPath the path to write the JSON file
     * @throws IOException if writing fails
     */
    public static void exportAgentConfig(AgentConfig config, Path outputPath) throws IOException {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");

        Path parent = outputPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        try (FileWriter writer = new FileWriter(outputPath.toFile())) {
            GSON.toJson(config, writer);
        }
    }

    /**
     * Load AgentConfig from a JSON file.
     *
     * @param jsonPath the path to the JSON file
     * @return the loaded agent configuration
     * @throws IOException if reading fails
     * @throws IllegalArgumentException if JSON is invalid
     */
    public static AgentConfig loadAgentConfig(Path jsonPath) throws IOException {
        Objects.requireNonNull(jsonPath, "jsonPath must not be null");

        if (!Files.exists(jsonPath)) {
            throw new IOException("JSON file not found: " + jsonPath);
        }

        try (FileReader reader = new FileReader(jsonPath.toFile())) {
            return GSON.fromJson(reader, AgentConfig.class);
        }
    }

    /**
     * Serialize any object to a JSON string.
     *
     * @param obj the object to serialize
     * @return JSON string representation
     */
    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    /**
     * Deserialize a JSON string to an object of the specified type.
     *
     * @param json the JSON string
     * @param clazz the target class
     * @param <T> the type of the object
     * @return the deserialized object
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toStringKeyMap(Map<?, ?> map) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = entry.getKey().toString();
            Object value = entry.getValue();
            if (value instanceof Map) {
                result.put(key, toStringKeyMap((Map<?, ?>) value));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static FeagiConfig fromMap(Map<String, Object> map, String source) {
        Map<String, Object> api = getSection(map, "api", source);
        Map<String, Object> websocket = getSection(map, "websocket", source);
        Map<String, Object> burstEngine = getSection(map, "burst_engine", source);
        Map<String, Object> performance = getSection(map, "performance", source);
        Map<String, Object> logging = getSection(map, "logging", source);
        Map<String, Object> timeouts = getSection(map, "timeouts", source);
        Map<String, Object> connectome = getSection(map, "connectome", source);

        return new FeagiConfig.Builder()
            .apiHost(getString(api, "host", source))
            .apiPort(getInt(api, "port", source))
            .websocketHost(getString(websocket, "host", source))
            .websocketEnabled(getBool(websocket, "enabled", source))
            .visualizationPort(getInt(websocket, "visualization_port", source))
            .sensoryPort(getInt(websocket, "sensory_port", source))
            .motorPort(getInt(websocket, "motor_port", source))
            .burstDuration(getDouble(burstEngine, "burst_duration", source))
            .maxBursts(getInt(burstEngine, "max_bursts", source))
            .gpuEnabled(getBool(burstEngine, "gpu_enabled", source))
            .workerThreads(getInt(performance, "worker_threads", source))
            .profilingEnabled(getBool(performance, "profiling_enabled", source))
            .logLevel(getString(logging, "level", source))
            .fileLogging(getBool(logging, "file_logging", source))
            .serviceStartupTimeout(Duration.ofSeconds((long) getDouble(timeouts, "service_startup", source)))
            .neuronSpace(getInt(connectome, "neuron_space", source))
            .synapseSpace(getInt(connectome, "synapse_space", source))
            .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getSection(Map<String, Object> map, String section, String source) {
        Object value = map.get(section);
        if (value == null) {
            throw new IllegalArgumentException("[" + source + "] Missing required section: [" + section + "]");
        }
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("[" + source + "] Section [" + section + "] must be a map");
        }
        return (Map<String, Object>) value;
    }

    private static String getString(Map<String, Object> map, String key, String source) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("[" + source + "] Missing required key: " + key);
        }
        return value.toString();
    }

    private static int getInt(Map<String, Object> map, String key, String source) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("[" + source + "] Missing required key: " + key);
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        throw new IllegalArgumentException("[" + source + "] Key " + key + " must be an integer");
    }

    private static double getDouble(Map<String, Object> map, String key, String source) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("[" + source + "] Missing required key: " + key);
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        throw new IllegalArgumentException("[" + source + "] Key " + key + " must be a number");
    }

    private static boolean getBool(Map<String, Object> map, String key, String source) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("[" + source + "] Missing required key: " + key);
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        throw new IllegalArgumentException("[" + source + "] Key " + key + " must be a boolean");
    }

    // Custom serializers/deserializers

    static class DurationSerializer implements JsonSerializer<Duration> {
        @Override
        public JsonElement serialize(Duration src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toMillis() / 1000.0);
        }
    }

    static class DurationDeserializer implements JsonDeserializer<Duration> {
        @Override
        public Duration deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            double seconds = json.getAsDouble();
            return Duration.ofMillis((long) (seconds * 1000));
        }
    }

    static class AgentTypeSerializer implements JsonSerializer<AgentType> {
        @Override
        public JsonElement serialize(AgentType src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.name());
        }
    }

    static class AgentTypeDeserializer implements JsonDeserializer<AgentType> {
        @Override
        public AgentType deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            return AgentType.valueOf(json.getAsString());
        }
    }

    static class FeagiEndpointsSerializer implements JsonSerializer<FeagiEndpoints> {
        @Override
        public JsonElement serialize(FeagiEndpoints src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("registrationEndpoint", src.registrationEndpoint());
            if (src.sensoryEndpoint() != null) {
                obj.addProperty("sensoryEndpoint", src.sensoryEndpoint());
            }
            if (src.motorEndpoint() != null) {
                obj.addProperty("motorEndpoint", src.motorEndpoint());
            }
            if (src.visualizationEndpoint() != null) {
                obj.addProperty("visualizationEndpoint", src.visualizationEndpoint());
            }
            if (src.controlEndpoint() != null) {
                obj.addProperty("controlEndpoint", src.controlEndpoint());
            }
            return obj;
        }
    }

    static class FeagiEndpointsDeserializer implements JsonDeserializer<FeagiEndpoints> {
        @Override
        public FeagiEndpoints deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String registration = obj.has("registrationEndpoint") ? obj.get("registrationEndpoint").getAsString() : null;
            String sensory = obj.has("sensoryEndpoint") ? obj.get("sensoryEndpoint").getAsString() : null;
            String motor = obj.has("motorEndpoint") ? obj.get("motorEndpoint").getAsString() : null;
            String visualization = obj.has("visualizationEndpoint") ? obj.get("visualizationEndpoint").getAsString() : null;
            String control = obj.has("controlEndpoint") ? obj.get("controlEndpoint").getAsString() : null;
            return new FeagiEndpoints(registration, sensory, motor, visualization, control);
        }
    }
}
