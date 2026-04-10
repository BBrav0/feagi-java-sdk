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
import io.feagi.sdk.pns.inputs.Camera;
import io.feagi.sdk.pns.inputs.InfraredInput;
import io.feagi.sdk.pns.inputs.NumericStream;
import io.feagi.sdk.pns.inputs.TextStream;
import io.feagi.sdk.pns.outputs.OutputNumericStream;
import io.feagi.sdk.pns.outputs.OutputTextStream;
import io.feagi.sdk.pns.outputs.RotaryMotor;
import io.feagi.sdk.pns.outputs.ServoMotor;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * JSON serialization utilities for PNS input/output types.
 *
 * <p>This class provides methods to serialize and deserialize PNS (Peripheral Nervous System)
 * input and output types to/from JSON format.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Export ServoMotor to JSON
 * ServoMotor servo = ServoMotor.builder()
 *     .angleRange(0.0, 180.0)
 *     .encoding(ServoMotor.Encoding.ABSOLUTE)
 *     .build();
 * PnsJsonUtils.exportMotor(servo, Path.of("servo.json"));
 *
 * // Load ServoMotor from JSON
 * ServoMotor loaded = PnsJsonUtils.loadServoMotor(Path.of("servo.json"));
 *
 * // Export Camera to JSON
 * Camera camera = Camera.builder()
 *     .resolution(640, 480)
 *     .channels(3)
 *     .build();
 * String json = PnsJsonUtils.toJson(camera);
 * }</pre>
 *
 * @see ServoMotor
 * @see RotaryMotor
 * @see OutputNumericStream
 * @see OutputTextStream
 * @see Camera
 * @see NumericStream
 * @see TextStream
 * @see InfraredInput
 */
public final class PnsJsonUtils {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(ServoMotor.class, new ServoMotorSerializer())
        .registerTypeAdapter(ServoMotor.class, new ServoMotorDeserializer())
        .registerTypeAdapter(RotaryMotor.class, new RotaryMotorSerializer())
        .registerTypeAdapter(RotaryMotor.class, new RotaryMotorDeserializer())
        .registerTypeAdapter(OutputNumericStream.class, new OutputNumericStreamSerializer())
        .registerTypeAdapter(OutputNumericStream.class, new OutputNumericStreamDeserializer())
        .registerTypeAdapter(OutputTextStream.class, new OutputTextStreamSerializer())
        .registerTypeAdapter(OutputTextStream.class, new OutputTextStreamDeserializer())
        .registerTypeAdapter(Camera.class, new CameraSerializer())
        .registerTypeAdapter(Camera.class, new CameraDeserializer())
        .registerTypeAdapter(NumericStream.class, new NumericStreamSerializer())
        .registerTypeAdapter(NumericStream.class, new NumericStreamDeserializer())
        .registerTypeAdapter(TextStream.class, new TextStreamSerializer())
        .registerTypeAdapter(TextStream.class, new TextStreamDeserializer())
        .registerTypeAdapter(InfraredInput.class, new InfraredInputSerializer())
        .registerTypeAdapter(InfraredInput.class, new InfraredInputDeserializer())
        .create();

    private PnsJsonUtils() {
        // Utility class - prevent instantiation
    }

    // ==================== Export Methods ====================

    /**
     * Export a servo motor configuration to a JSON file.
     *
     * @param motor the servo motor to export
     * @param outputPath the path to write the JSON file
     * @throws IOException if writing fails
     */
    public static void exportMotor(ServoMotor motor, Path outputPath) throws IOException {
        exportToObject(motor, outputPath);
    }

    /**
     * Export a rotary motor configuration to a JSON file.
     *
     * @param motor the rotary motor to export
     * @param outputPath the path to write the JSON file
     * @throws IOException if writing fails
     */
    public static void exportMotor(RotaryMotor motor, Path outputPath) throws IOException {
        exportToObject(motor, outputPath);
    }

    /**
     * Export an output numeric stream to a JSON file.
     *
     * @param stream the numeric stream to export
     * @param outputPath the path to write the JSON file
     * @throws IOException if writing fails
     */
    public static void exportStream(OutputNumericStream stream, Path outputPath) throws IOException {
        exportToObject(stream, outputPath);
    }

    /**
     * Export an output text stream to a JSON file.
     *
     * @param stream the text stream to export
     * @param outputPath the path to write the JSON file
     * @throws IOException if writing fails
     */
    public static void exportStream(OutputTextStream stream, Path outputPath) throws IOException {
        exportToObject(stream, outputPath);
    }

    /**
     * Export a camera input to a JSON file.
     *
     * @param camera the camera to export
     * @param outputPath the path to write the JSON file
     * @throws IOException if writing fails
     */
    public static void exportInput(Camera camera, Path outputPath) throws IOException {
        exportToObject(camera, outputPath);
    }

    /**
     * Export a numeric stream input to a JSON file.
     *
     * @param stream the numeric stream to export
     * @param outputPath the path to write the JSON file
     * @throws IOException if writing fails
     */
    public static void exportInput(NumericStream stream, Path outputPath) throws IOException {
        exportToObject(stream, outputPath);
    }

    /**
     * Export a text stream input to a JSON file.
     *
     * @param stream the text stream to export
     * @param outputPath the path to write the JSON file
     * @throws IOException if writing fails
     */
    public static void exportInput(TextStream stream, Path outputPath) throws IOException {
        exportToObject(stream, outputPath);
    }

    /**
     * Export an infrared input to a JSON file.
     *
     * @param input the infrared input to export
     * @param outputPath the path to write the JSON file
     * @throws IOException if writing fails
     */
    public static void exportInput(InfraredInput input, Path outputPath) throws IOException {
        exportToObject(input, outputPath);
    }

    // ==================== Load Methods ====================

    /**
     * Load a servo motor configuration from a JSON file.
     *
     * @param jsonPath the path to the JSON file
     * @return the loaded servo motor
     * @throws IOException if reading fails
     */
    public static ServoMotor loadServoMotor(Path jsonPath) throws IOException {
        return loadFromObject(jsonPath, ServoMotor.class);
    }

    /**
     * Load a rotary motor configuration from a JSON file.
     *
     * @param jsonPath the path to the JSON file
     * @return the loaded rotary motor
     */
    public static RotaryMotor loadRotaryMotor(Path jsonPath) throws IOException {
        return loadFromObject(jsonPath, RotaryMotor.class);
    }

    /**
     * Load an output numeric stream from a JSON file.
     *
     * @param jsonPath the path to the JSON file
     * @return the loaded numeric stream
     */
    public static OutputNumericStream loadOutputNumericStream(Path jsonPath) throws IOException {
        return loadFromObject(jsonPath, OutputNumericStream.class);
    }

    /**
     * Load an output text stream from a JSON file.
     *
     * @param jsonPath the path to the JSON file
     * @return the loaded text stream
     */
    public static OutputTextStream loadOutputTextStream(Path jsonPath) throws IOException {
        return loadFromObject(jsonPath, OutputTextStream.class);
    }

    /**
     * Load a camera input from a JSON file.
     *
     * @param jsonPath the path to the JSON file
     * @return the loaded camera
     */
    public static Camera loadCamera(Path jsonPath) throws IOException {
        return loadFromObject(jsonPath, Camera.class);
    }

    /**
     * Load a numeric stream input from a JSON file.
     *
     * @param jsonPath the path to the JSON file
     * @return the loaded numeric stream
     */
    public static NumericStream loadNumericStream(Path jsonPath) throws IOException {
        return loadFromObject(jsonPath, NumericStream.class);
    }

    /**
     * Load a text stream input from a JSON file.
     *
     * @param jsonPath the path to the JSON file
     * @return the loaded text stream
     */
    public static TextStream loadTextStream(Path jsonPath) throws IOException {
        return loadFromObject(jsonPath, TextStream.class);
    }

    /**
     * Load an infrared input from a JSON file.
     *
     * @param jsonPath the path to the JSON file
     * @return the loaded infrared input
     */
    public static InfraredInput loadInfraredInput(Path jsonPath) throws IOException {
        return loadFromObject(jsonPath, InfraredInput.class);
    }

    // ==================== Generic JSON Methods ====================

    /**
     * Serialize any PNS object to a JSON string.
     *
     * @param obj the object to serialize
     * @return JSON string representation
     */
    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    /**
     * Deserialize a JSON string to a PNS object of the specified type.
     *
     * @param json the JSON string
     * @param clazz the target class
     * @param <T> the type of the object
     * @return the deserialized object
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }

    // ==================== Private Helper Methods ====================

    private static <T> void exportToObject(T obj, Path outputPath) throws IOException {
        Objects.requireNonNull(obj, "obj must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");

        Path parent = outputPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        try (Writer writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            GSON.toJson(obj, writer);
        }
    }

    private static <T> T loadFromObject(Path jsonPath, Class<T> clazz) throws IOException {
        Objects.requireNonNull(jsonPath, "jsonPath must not be null");

        if (!Files.exists(jsonPath)) {
            throw new IOException("JSON file not found: " + jsonPath);
        }

        try (Reader reader = Files.newBufferedReader(jsonPath, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, clazz);
        }
    }

    // ==================== Custom Serializers/Deserializers ====================

    static class ServoMotorSerializer implements JsonSerializer<ServoMotor> {
        @Override
        public JsonElement serialize(ServoMotor src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("minAngle", src.minAngle());
            obj.addProperty("maxAngle", src.maxAngle());
            obj.addProperty("encoding", src.encoding().name());
            obj.addProperty("gain", src.gain());
            obj.addProperty("incrementalStepRatio", src.incrementalStepRatio());
            return obj;
        }
    }

    static class ServoMotorDeserializer implements JsonDeserializer<ServoMotor> {
        @Override
        public ServoMotor deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            ServoMotor.Builder builder = ServoMotor.builder()
                .angleRange(
                    obj.get("minAngle").getAsDouble(),
                    obj.get("maxAngle").getAsDouble())
                .encoding(ServoMotor.Encoding.valueOf(obj.get("encoding").getAsString()))
                .gain(obj.has("gain") ? obj.get("gain").getAsDouble() : 1.0)
                .incrementalStepRatio(obj.has("incrementalStepRatio") ?
                    obj.get("incrementalStepRatio").getAsDouble() : 0.05);
            return builder.build();
        }
    }

    static class RotaryMotorSerializer implements JsonSerializer<RotaryMotor> {
        @Override
        public JsonElement serialize(RotaryMotor src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("encoding", src.encoding().name());
            obj.addProperty("bidirectional", src.isBidirectional());
            return obj;
        }
    }

    static class RotaryMotorDeserializer implements JsonDeserializer<RotaryMotor> {
        @Override
        public RotaryMotor deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            RotaryMotor.Builder builder = RotaryMotor.builder()
                .encoding(RotaryMotor.Encoding.valueOf(obj.get("encoding").getAsString()))
                .bidirectional(obj.has("bidirectional") ? obj.get("bidirectional").getAsBoolean() : true);
            return builder.build();
        }
    }

    static class OutputNumericStreamSerializer implements JsonSerializer<OutputNumericStream> {
        @Override
        public JsonElement serialize(OutputNumericStream src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("dimensions", src.dimensions());
            return obj;
        }
    }

    static class OutputNumericStreamDeserializer implements JsonDeserializer<OutputNumericStream> {
        @Override
        public OutputNumericStream deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            OutputNumericStream.Builder builder = OutputNumericStream.builder()
                .dimensions(obj.get("dimensions").getAsInt());
            return builder.build();
        }
    }

    static class OutputTextStreamSerializer implements JsonSerializer<OutputTextStream> {
        @Override
        public JsonElement serialize(OutputTextStream src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("maxTextLength", src.maxTextLength());
            obj.addProperty("encoding", src.encoding());
            obj.addProperty("keepHistory", src.isKeepHistory());
            return obj;
        }
    }

    static class OutputTextStreamDeserializer implements JsonDeserializer<OutputTextStream> {
        @Override
        public OutputTextStream deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            OutputTextStream.Builder builder = OutputTextStream.builder()
                .maxTextLength(obj.has("maxTextLength") ? obj.get("maxTextLength").getAsInt() : 256)
                .encoding(obj.has("encoding") ? obj.get("encoding").getAsString() : "UTF-8")
                .keepHistory(obj.has("keepHistory") ? obj.get("keepHistory").getAsBoolean() : false);
            return builder.build();
        }
    }

    static class CameraSerializer implements JsonSerializer<Camera> {
        @Override
        public JsonElement serialize(Camera src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("width", src.width());
            obj.addProperty("height", src.height());
            obj.addProperty("channels", src.channels());
            obj.addProperty("encoding", src.encoding());
            if (src.position() != null) {
                obj.addProperty("position", src.position());
            }
            return obj;
        }
    }

    static class CameraDeserializer implements JsonDeserializer<Camera> {
        @Override
        public Camera deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            Camera.Builder builder = Camera.builder()
                .resolution(obj.has("width") ? obj.get("width").getAsInt() : 640,
                           obj.has("height") ? obj.get("height").getAsInt() : 480)
                .channels(obj.has("channels") ? obj.get("channels").getAsInt() : 3)
                .encoding(obj.has("encoding") ? obj.get("encoding").getAsString() : "RGB");
            if (obj.has("position")) {
                builder.position(obj.get("position").getAsString());
            }
            return builder.build();
        }
    }

    static class NumericStreamSerializer implements JsonSerializer<NumericStream> {
        @Override
        public JsonElement serialize(NumericStream src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("precision", src.precision());
            obj.addProperty("minValue", src.minValue());
            obj.addProperty("maxValue", src.maxValue());
            obj.addProperty("scaleFactor", src.scaleFactor());
            obj.addProperty("clampToRange", src.clampToRange());
            return obj;
        }
    }

    static class NumericStreamDeserializer implements JsonDeserializer<NumericStream> {
        @Override
        public NumericStream deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            NumericStream.Builder builder = NumericStream.builder()
                .precision(obj.has("precision") ? obj.get("precision").getAsDouble() : 0.001)
                .range(
                    obj.has("minValue") ? obj.get("minValue").getAsDouble() : -1.0,
                    obj.has("maxValue") ? obj.get("maxValue").getAsDouble() : 1.0)
                .scaleFactor(obj.has("scaleFactor") ? obj.get("scaleFactor").getAsDouble() : 1.0)
                .clampToRange(obj.has("clampToRange") ? obj.get("clampToRange").getAsBoolean() : true);
            return builder.build();
        }
    }

    static class TextStreamSerializer implements JsonSerializer<TextStream> {
        @Override
        public JsonElement serialize(TextStream src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("maxLength", src.maxLength());
            obj.addProperty("encoding", src.encoding());
            obj.addProperty("padToMaxLength", src.padToMaxLength());
            return obj;
        }
    }

    static class TextStreamDeserializer implements JsonDeserializer<TextStream> {
        @Override
        public TextStream deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            TextStream.Builder builder = TextStream.builder()
                .maxLength(obj.has("maxLength") ? obj.get("maxLength").getAsInt() : 256)
                .encoding(obj.has("encoding") ? obj.get("encoding").getAsString() : "UTF-8")
                .padToMaxLength(obj.has("padToMaxLength") ? obj.get("padToMaxLength").getAsBoolean() : false);
            return builder.build();
        }
    }

    static class InfraredInputSerializer implements JsonSerializer<InfraredInput> {
        @Override
        public JsonElement serialize(InfraredInput src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("minValue", src.minValue());
            obj.addProperty("maxValue", src.maxValue());
            obj.addProperty("fieldOfView", src.fieldOfView());
            if (src.position() != null) {
                obj.addProperty("position", src.position());
            }
            if (src.sensorModel() != null) {
                obj.addProperty("sensorModel", src.sensorModel());
            }
            return obj;
        }
    }

    static class InfraredInputDeserializer implements JsonDeserializer<InfraredInput> {
        @Override
        public InfraredInput deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            InfraredInput.InfraredBuilder builder = InfraredInput.createBuilder()
                .range(
                    obj.has("minValue") ? obj.get("minValue").getAsDouble() : 0.03,
                    obj.has("maxValue") ? obj.get("maxValue").getAsDouble() : 0.40)
                .fieldOfView(obj.has("fieldOfView") ? obj.get("fieldOfView").getAsDouble() : 25.0);
            if (obj.has("position")) {
                builder.position(obj.get("position").getAsString());
            }
            if (obj.has("sensorModel")) {
                builder.sensorModel(obj.get("sensorModel").getAsString());
            }
            return builder.build();
        }
    }
}
