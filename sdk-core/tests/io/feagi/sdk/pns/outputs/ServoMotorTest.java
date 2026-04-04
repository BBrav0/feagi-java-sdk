/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.pns.outputs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ServoMotor}.
 */
@DisplayName("ServoMotor")
class ServoMotorTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should create with default values")
        void shouldCreateWithDefaultValues() {
            ServoMotor servo = ServoMotor.builder().build();

            assertEquals(0.0, servo.minAngle());
            assertEquals(180.0, servo.maxAngle());
            assertEquals(ServoMotor.Encoding.ABSOLUTE, servo.encoding());
            assertEquals(1.0, servo.gain());
            assertEquals(0.05, servo.incrementalStepRatio());
        }

        @Test
        @DisplayName("should create with custom angle range")
        void shouldCreateWithCustomAngleRange() {
            ServoMotor servo = ServoMotor.builder()
                .angleRange(-90.0, 90.0)
                .build();

            assertEquals(-90.0, servo.minAngle());
            assertEquals(90.0, servo.maxAngle());
            assertEquals(180.0, servo.angleRange());
        }

        @Test
        @DisplayName("should create with custom encoding")
        void shouldCreateWithCustomEncoding() {
            ServoMotor servo = ServoMotor.builder()
                .encoding(ServoMotor.Encoding.INCREMENTAL)
                .build();

            assertEquals(ServoMotor.Encoding.INCREMENTAL, servo.encoding());
        }

        @Test
        @DisplayName("should create with custom gain")
        void shouldCreateWithCustomGain() {
            ServoMotor servo = ServoMotor.builder()
                .gain(2.0)
                .build();

            assertEquals(2.0, servo.gain());
        }

        @Test
        @DisplayName("should create with custom incremental step ratio")
        void shouldCreateWithCustomIncrementalStepRatio() {
            ServoMotor servo = ServoMotor.builder()
                .incrementalStepRatio(0.1)
                .build();

            assertEquals(0.1, servo.incrementalStepRatio());
        }

        @Test
        @DisplayName("should throw when minAngle >= maxAngle")
        void shouldThrowWhenMinAngleGreaterOrEqualMaxAngle() {
            assertThrows(IllegalArgumentException.class, () ->
                ServoMotor.builder()
                    .angleRange(180.0, 0.0)
                    .build()
            );

            assertThrows(IllegalArgumentException.class, () ->
                ServoMotor.builder()
                    .angleRange(90.0, 90.0)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when encoding is null")
        void shouldThrowWhenEncodingIsNull() {
            assertThrows(NullPointerException.class, () ->
                ServoMotor.builder()
                    .encoding(null)
                    .build()
            );
        }
    }

    @Nested
    @DisplayName("Angle Properties")
    class AnglePropertiesTests {

        @Test
        @DisplayName("should start at center angle")
        void shouldStartAtCenterAngle() {
            ServoMotor servo = ServoMotor.builder()
                .angleRange(0.0, 180.0)
                .build();

            assertEquals(90.0, servo.getAngle());
        }

        @Test
        @DisplayName("should calculate correct center angle")
        void shouldCalculateCorrectCenterAngle() {
            ServoMotor servo1 = ServoMotor.builder()
                .angleRange(0.0, 180.0)
                .build();

            assertEquals(90.0, servo1.centerAngle());

            ServoMotor servo2 = ServoMotor.builder()
                .angleRange(-90.0, 90.0)
                .build();

            assertEquals(0.0, servo2.centerAngle());
        }

        @Test
        @DisplayName("should have correct raw value initially")
        void shouldHaveCorrectRawValueInitially() {
            ServoMotor servo = ServoMotor.builder().build();

            assertEquals(0.0, servo.rawValue());
        }
    }

    @Nested
    @DisplayName("Motor Command Processing")
    class MotorCommandTests {

        @Test
        @DisplayName("should process absolute mode command")
        void shouldProcessAbsoluteModeCommand() {
            ServoMotor servo = ServoMotor.builder()
                .angleRange(0.0, 180.0)
                .encoding(ServoMotor.Encoding.ABSOLUTE)
                .build();

            // -1.0 should map to min (0 degrees)
            servo.processMotorCommand(-1.0);
            assertEquals(0.0, servo.getAngle());

            // 0.0 should map to center (90 degrees)
            servo.processMotorCommand(0.0);
            assertEquals(90.0, servo.getAngle());

            // 1.0 should map to max (180 degrees)
            servo.processMotorCommand(1.0);
            assertEquals(180.0, servo.getAngle());
        }

        @Test
        @DisplayName("should process incremental mode command")
        void shouldProcessIncrementalModeCommand() {
            ServoMotor servo = ServoMotor.builder()
                .angleRange(0.0, 180.0)
                .encoding(ServoMotor.Encoding.INCREMENTAL)
                .incrementalStepRatio(0.1)
                .build();

            double initialAngle = servo.getAngle();

            // Positive command should increase angle
            servo.processMotorCommand(0.5);
            assertTrue(servo.getAngle() > initialAngle);

            // Stronger negative command should bring angle below initial
            servo.processMotorCommand(-1.0);
            assertTrue(servo.getAngle() < initialAngle);
        }

        @Test
        @DisplayName("should clamp angle to range")
        void shouldClampAngleToRange() {
            ServoMotor servo = ServoMotor.builder()
                .angleRange(0.0, 180.0)
                .gain(2.0)  // High gain to test clamping
                .build();

            // Command with high gain should still be clamped
            servo.processMotorCommand(1.0);
            assertEquals(180.0, servo.getAngle());

            servo.processMotorCommand(-1.0);
            assertEquals(0.0, servo.getAngle());
        }

        @Test
        @DisplayName("should store raw value")
        void shouldStoreRawValue() {
            ServoMotor servo = ServoMotor.builder().build();

            servo.processMotorCommand(0.5);

            assertEquals(0.5, servo.rawValue());
        }

        @Test
        @DisplayName("should apply gain")
        void shouldApplyGain() {
            ServoMotor servo = ServoMotor.builder()
                .angleRange(0.0, 180.0)
                .gain(0.5)
                .build();

            // With gain 0.5, input 1.0 becomes 0.5, which maps to 135 degrees
            servo.processMotorCommand(1.0);
            assertEquals(135.0, servo.getAngle());
        }
    }

    @Nested
    @DisplayName("Registration")
    class RegistrationTests {

        @Test
        @DisplayName("should register with cache")
        void shouldRegisterWithCache() {
            ServoMotor servo = ServoMotor.builder().build();

            assertFalse(servo.isRegistered());
            assertNull(servo.groupId());

            servo._registerWithCache();

            assertTrue(servo.isRegistered());
            assertEquals(0, servo.groupId());
        }
    }

    @Nested
    @DisplayName("equals() and hashCode()")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("should be equal when all fields are equal")
        void shouldBeEqualWhenAllFieldsAreEqual() {
            ServoMotor servo1 = ServoMotor.builder()
                .angleRange(0.0, 180.0)
                .encoding(ServoMotor.Encoding.ABSOLUTE)
                .gain(1.0)
                .build();

            ServoMotor servo2 = ServoMotor.builder()
                .angleRange(0.0, 180.0)
                .encoding(ServoMotor.Encoding.ABSOLUTE)
                .gain(1.0)
                .build();

            assertEquals(servo1, servo2);
            assertEquals(servo1.hashCode(), servo2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when angle range differs")
        void shouldNotBeEqualWhenAngleRangeDiffers() {
            ServoMotor servo1 = ServoMotor.builder()
                .angleRange(0.0, 180.0)
                .build();

            ServoMotor servo2 = ServoMotor.builder()
                .angleRange(-90.0, 90.0)
                .build();

            assertNotEquals(servo1, servo2);
        }

        @Test
        @DisplayName("should not be equal when encoding differs")
        void shouldNotBeEqualWhenEncodingDiffers() {
            ServoMotor servo1 = ServoMotor.builder()
                .encoding(ServoMotor.Encoding.ABSOLUTE)
                .build();

            ServoMotor servo2 = ServoMotor.builder()
                .encoding(ServoMotor.Encoding.INCREMENTAL)
                .build();

            assertNotEquals(servo1, servo2);
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @Test
        @DisplayName("should return formatted string")
        void shouldReturnFormattedString() {
            ServoMotor servo = ServoMotor.builder()
                .angleRange(0.0, 180.0)
                .encoding(ServoMotor.Encoding.ABSOLUTE)
                .gain(1.0)
                .build();

            String result = servo.toString();

            assertTrue(result.contains("ServoMotor"));
            assertTrue(result.contains("minAngle=0.0"));
            assertTrue(result.contains("maxAngle=180.0"));
            assertTrue(result.contains("encoding=ABSOLUTE"));
        }
    }
}
