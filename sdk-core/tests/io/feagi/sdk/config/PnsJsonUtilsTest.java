/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.config;

import io.feagi.sdk.pns.inputs.Camera;
import io.feagi.sdk.pns.inputs.InfraredInput;
import io.feagi.sdk.pns.inputs.NumericStream;
import io.feagi.sdk.pns.inputs.TextStream;
import io.feagi.sdk.pns.outputs.OutputNumericStream;
import io.feagi.sdk.pns.outputs.OutputTextStream;
import io.feagi.sdk.pns.outputs.RotaryMotor;
import io.feagi.sdk.pns.outputs.ServoMotor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PnsJsonUtils}.
 */
@DisplayName("PnsJsonUtils")
class PnsJsonUtilsTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("ServoMotor Export/Load")
    class ServoMotorExportLoadTests {

        @Test
        @DisplayName("should export and load servo motor with default values")
        void shouldExportAndLoadServoMotorWithDefaultValues() throws IOException {
            ServoMotor original = ServoMotor.builder().build();
            Path jsonPath = tempDir.resolve("servo.json");

            PnsJsonUtils.exportMotor(original, jsonPath);
            ServoMotor loaded = PnsJsonUtils.loadServoMotor(jsonPath);

            assertEquals(original.minAngle(), loaded.minAngle());
            assertEquals(original.maxAngle(), loaded.maxAngle());
            assertEquals(original.encoding(), loaded.encoding());
            assertEquals(original.gain(), loaded.gain());
            assertEquals(original.incrementalStepRatio(), loaded.incrementalStepRatio());
        }

        @Test
        @DisplayName("should export and load servo motor with custom values")
        void shouldExportAndLoadServoMotorWithCustomValues() throws IOException {
            ServoMotor original = ServoMotor.builder()
                .angleRange(0.0, 270.0)
                .encoding(ServoMotor.Encoding.INCREMENTAL)
                .gain(1.5)
                .incrementalStepRatio(0.1)
                .build();

            Path jsonPath = tempDir.resolve("servo_custom.json");
            PnsJsonUtils.exportMotor(original, jsonPath);
            ServoMotor loaded = PnsJsonUtils.loadServoMotor(jsonPath);

            assertEquals(original.minAngle(), loaded.minAngle());
            assertEquals(original.maxAngle(), loaded.maxAngle());
            assertEquals(original.encoding(), loaded.encoding());
        }

        @Test
        @DisplayName("should serialize to JSON string")
        void shouldSerializeToJsonString() {
            ServoMotor servo = ServoMotor.builder()
                .angleRange(0.0, 180.0)
                .encoding(ServoMotor.Encoding.ABSOLUTE)
                .build();

            String json = PnsJsonUtils.toJson(servo);

            assertNotNull(json);
            assertTrue(json.contains("ABSOLUTE"));
        }
    }

    @Nested
    @DisplayName("RotaryMotor Export/Load")
    class RotaryMotorExportLoadTests {

        @Test
        @DisplayName("should export and load rotary motor with default values")
        void shouldExportAndLoadRotaryMotorWithDefaultValues() throws IOException {
            RotaryMotor original = RotaryMotor.builder().build();
            Path jsonPath = tempDir.resolve("rotary.json");

            PnsJsonUtils.exportMotor(original, jsonPath);
            RotaryMotor loaded = PnsJsonUtils.loadRotaryMotor(jsonPath);

            assertEquals(original.encoding(), loaded.encoding());
            assertEquals(original.isBidirectional(), loaded.isBidirectional());
        }

        @Test
        @DisplayName("should export and load rotary motor with custom values")
        void shouldExportAndLoadRotaryMotorWithCustomValues() throws IOException {
            RotaryMotor original = RotaryMotor.builder()
                .encoding(RotaryMotor.Encoding.INCREMENTAL)
                .bidirectional(false)
                .build();

            Path jsonPath = tempDir.resolve("rotary_custom.json");
            PnsJsonUtils.exportMotor(original, jsonPath);
            RotaryMotor loaded = PnsJsonUtils.loadRotaryMotor(jsonPath);

            assertEquals(original.encoding(), loaded.encoding());
            assertFalse(loaded.isBidirectional());
        }
    }

    @Nested
    @DisplayName("OutputNumericStream Export/Load")
    class OutputNumericStreamExportLoadTests {

        @Test
        @DisplayName("should export and load numeric stream")
        void shouldExportAndLoadNumericStream() throws IOException {
            OutputNumericStream original = OutputNumericStream.builder()
                .dimensions(5)
                .build();

            Path jsonPath = tempDir.resolve("numeric_stream.json");
            PnsJsonUtils.exportStream(original, jsonPath);
            OutputNumericStream loaded = PnsJsonUtils.loadOutputNumericStream(jsonPath);

            assertEquals(original.dimensions(), loaded.dimensions());
        }
    }

    @Nested
    @DisplayName("OutputTextStream Export/Load")
    class OutputTextStreamExportLoadTests {

        @Test
        @DisplayName("should export and load text stream")
        void shouldExportAndLoadTextStream() throws IOException {
            OutputTextStream original = OutputTextStream.builder()
                .maxTextLength(512)
                .encoding("ASCII")
                .keepHistory(true)
                .build();

            Path jsonPath = tempDir.resolve("text_stream.json");
            PnsJsonUtils.exportStream(original, jsonPath);
            OutputTextStream loaded = PnsJsonUtils.loadOutputTextStream(jsonPath);

            assertEquals(original.maxTextLength(), loaded.maxTextLength());
            assertEquals(original.encoding(), loaded.encoding());
            assertTrue(loaded.isKeepHistory());
        }
    }

    @Nested
    @DisplayName("Camera Export/Load")
    class CameraExportLoadTests {

        @Test
        @DisplayName("should export and load camera with default values")
        void shouldExportAndLoadCameraWithDefaultValues() throws IOException {
            Camera original = Camera.builder().build();
            Path jsonPath = tempDir.resolve("camera.json");

            PnsJsonUtils.exportInput(original, jsonPath);
            Camera loaded = PnsJsonUtils.loadCamera(jsonPath);

            assertEquals(original.width(), loaded.width());
            assertEquals(original.height(), loaded.height());
            assertEquals(original.channels(), loaded.channels());
            assertEquals(original.encoding(), loaded.encoding());
        }

        @Test
        @DisplayName("should export and load camera with custom values")
        void shouldExportAndLoadCameraWithCustomValues() throws IOException {
            Camera original = Camera.builder()
                .resolution(1920, 1080)
                .channels(4)
                .encoding("RGBA")
                .position("front")
                .build();

            Path jsonPath = tempDir.resolve("camera_custom.json");
            PnsJsonUtils.exportInput(original, jsonPath);
            Camera loaded = PnsJsonUtils.loadCamera(jsonPath);

            assertEquals(1920, loaded.width());
            assertEquals(1080, loaded.height());
            assertEquals(4, loaded.channels());
            assertEquals("RGBA", loaded.encoding());
            assertEquals("front", loaded.position());
        }
    }

    @Nested
    @DisplayName("NumericStream Export/Load")
    class NumericStreamExportLoadTests {

        @Test
        @DisplayName("should export and load numeric stream input")
        void shouldExportAndLoadNumericStreamInput() throws IOException {
            NumericStream original = NumericStream.builder()
                .precision(0.01)
                .range(-5.0, 5.0)
                .scaleFactor(100.0)
                .clampToRange(true)
                .build();

            Path jsonPath = tempDir.resolve("numeric_input.json");
            PnsJsonUtils.exportInput(original, jsonPath);
            NumericStream loaded = PnsJsonUtils.loadNumericStream(jsonPath);

            assertEquals(original.precision(), loaded.precision());
            assertEquals(original.minValue(), loaded.minValue());
            assertEquals(original.maxValue(), loaded.maxValue());
        }
    }

    @Nested
    @DisplayName("TextStream Export/Load")
    class TextStreamExportLoadTests {

        @Test
        @DisplayName("should export and load text stream input")
        void shouldExportAndLoadTextStreamInput() throws IOException {
            TextStream original = TextStream.builder()
                .maxLength(1024)
                .encoding("UTF-8")
                .padToMaxLength(true)
                .build();

            Path jsonPath = tempDir.resolve("text_input.json");
            PnsJsonUtils.exportInput(original, jsonPath);
            TextStream loaded = PnsJsonUtils.loadTextStream(jsonPath);

            assertEquals(original.maxLength(), loaded.maxLength());
            assertEquals(original.encoding(), loaded.encoding());
            assertTrue(loaded.padToMaxLength());
        }
    }

    @Nested
    @DisplayName("InfraredInput Export/Load")
    class InfraredInputExportLoadTests {

        @Test
        @DisplayName("should export and load infrared input with default values")
        void shouldExportAndLoadInfraredInputWithDefaultValues() throws IOException {
            InfraredInput original = InfraredInput.createBuilder().build();
            Path jsonPath = tempDir.resolve("infrared.json");

            PnsJsonUtils.exportInput(original, jsonPath);
            InfraredInput loaded = PnsJsonUtils.loadInfraredInput(jsonPath);

            assertEquals(original.minValue(), loaded.minValue());
            assertEquals(original.maxValue(), loaded.maxValue());
            assertEquals(original.fieldOfView(), loaded.fieldOfView());
        }

        @Test
        @DisplayName("should export and load infrared input with custom values")
        void shouldExportAndLoadInfraredInputWithCustomValues() throws IOException {
            InfraredInput original = InfraredInput.createBuilder()
                .range(0.02, 0.50)
                .fieldOfView(30.0)
                .position("rear")
                .sensorModel("GP2Y0A21YK0F")
                .build();

            Path jsonPath = tempDir.resolve("infrared_custom.json");
            PnsJsonUtils.exportInput(original, jsonPath);
            InfraredInput loaded = PnsJsonUtils.loadInfraredInput(jsonPath);

            assertEquals(0.02, loaded.minValue());
            assertEquals(0.50, loaded.maxValue());
            assertEquals(30.0, loaded.fieldOfView());
            assertEquals("rear", loaded.position());
            assertEquals("GP2Y0A21YK0F", loaded.sensorModel());
        }
    }

    @Nested
    @DisplayName("JSON String Conversion")
    class JsonStringConversionTests {

        @Test
        @DisplayName("should serialize rotary motor to JSON string")
        void shouldSerializeRotaryMotorToJsonString() {
            RotaryMotor motor = RotaryMotor.builder()
                .bidirectional(true)
                .build();

            String json = PnsJsonUtils.toJson(motor);

            assertNotNull(json);
            assertTrue(json.contains("bidirectional"));
        }

        @Test
        @DisplayName("should deserialize JSON string to output text stream")
        void shouldDeserializeJsonStringToOutputTextStream() {
            String json = """
                {
                    "maxTextLength": 256,
                    "encoding": "UTF-8",
                    "keepHistory": false
                }
                """;

            OutputTextStream stream = PnsJsonUtils.fromJson(json, OutputTextStream.class);

            assertEquals(256, stream.maxTextLength());
            assertEquals("UTF-8", stream.encoding());
            assertFalse(stream.isKeepHistory());
        }
    }

    @Nested
    @DisplayName("File Operations")
    class FileOperationsTests {

        @Test
        @DisplayName("should throw when file not found")
        void shouldThrowWhenFileNotFound() {
            Path nonExistentPath = tempDir.resolve("nonexistent.json");
            assertThrows(IOException.class, () -> PnsJsonUtils.loadServoMotor(nonExistentPath));
        }

        @Test
        @DisplayName("should create parent directories if needed")
        void shouldCreateParentDirectories() throws IOException {
            ServoMotor servo = ServoMotor.builder().build();
            Path jsonPath = tempDir.resolve("subdir/nested/motor.json");

            PnsJsonUtils.exportMotor(servo, jsonPath);
            assertTrue(Files.exists(jsonPath));
        }
    }
}
