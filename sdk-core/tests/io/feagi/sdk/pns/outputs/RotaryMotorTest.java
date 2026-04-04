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
 * Unit tests for {@link RotaryMotor}.
 */
@DisplayName("RotaryMotor")
class RotaryMotorTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should create with default values")
        void shouldCreateWithDefaultValues() {
            RotaryMotor motor = RotaryMotor.builder().build();

            assertEquals(RotaryMotor.Encoding.ABSOLUTE, motor.encoding());
            assertTrue(motor.isBidirectional());
        }

        @Test
        @DisplayName("should create with custom encoding")
        void shouldCreateWithCustomEncoding() {
            RotaryMotor motor = RotaryMotor.builder()
                .encoding(RotaryMotor.Encoding.INCREMENTAL)
                .build();

            assertEquals(RotaryMotor.Encoding.INCREMENTAL, motor.encoding());
        }

        @Test
        @DisplayName("should create with unidirectional")
        void shouldCreateWithUnidirectional() {
            RotaryMotor motor = RotaryMotor.builder()
                .bidirectional(false)
                .build();

            assertFalse(motor.isBidirectional());
        }

        @Test
        @DisplayName("should throw when encoding is null")
        void shouldThrowWhenEncodingIsNull() {
            assertThrows(NullPointerException.class, () ->
                RotaryMotor.builder()
                    .encoding(null)
                    .build()
            );
        }
    }

    @Nested
    @DisplayName("Speed Properties")
    class SpeedPropertiesTests {

        @Test
        @DisplayName("should start at zero speed")
        void shouldStartAtZeroSpeed() {
            RotaryMotor motor = RotaryMotor.builder().build();

            assertEquals(0.0, motor.getSpeed());
            assertEquals(0.0, motor.rawValue());
        }

        @Test
        @DisplayName("should have correct speed range for bidirectional")
        void shouldHaveCorrectSpeedRangeForBidirectional() {
            RotaryMotor motor = RotaryMotor.builder()
                .bidirectional(true)
                .build();

            assertEquals(2.0, motor.speedRange());
        }

        @Test
        @DisplayName("should have correct speed range for unidirectional")
        void shouldHaveCorrectSpeedRangeForUnidirectional() {
            RotaryMotor motor = RotaryMotor.builder()
                .bidirectional(false)
                .build();

            assertEquals(1.0, motor.speedRange());
        }
    }

    @Nested
    @DisplayName("Motor Command Processing")
    class MotorCommandTests {

        @Test
        @DisplayName("should process bidirectional command")
        void shouldProcessBidirectionalCommand() {
            RotaryMotor motor = RotaryMotor.builder()
                .bidirectional(true)
                .build();

            // -1.0 should map to full reverse
            motor.processMotorCommand(-1.0);
            assertEquals(-1.0, motor.getSpeed());

            // 0.0 should be stopped
            motor.processMotorCommand(0.0);
            assertEquals(0.0, motor.getSpeed());

            // 1.0 should map to full forward
            motor.processMotorCommand(1.0);
            assertEquals(1.0, motor.getSpeed());
        }

        @Test
        @DisplayName("should process unidirectional command")
        void shouldProcessUnidirectionalCommand() {
            RotaryMotor motor = RotaryMotor.builder()
                .bidirectional(false)
                .build();

            // -1.0 should map to stopped (0.0)
            motor.processMotorCommand(-1.0);
            assertEquals(0.0, motor.getSpeed());

            // 0.0 should map to half speed (0.5)
            motor.processMotorCommand(0.0);
            assertEquals(0.5, motor.getSpeed());

            // 1.0 should map to full speed (1.0)
            motor.processMotorCommand(1.0);
            assertEquals(1.0, motor.getSpeed());
        }

        @Test
        @DisplayName("should store raw value")
        void shouldStoreRawValue() {
            RotaryMotor motor = RotaryMotor.builder().build();

            motor.processMotorCommand(0.5);

            assertEquals(0.5, motor.rawValue());
        }

        @Test
        @DisplayName("should clamp bidirectional speed")
        void shouldClampBidirectionalSpeed() {
            RotaryMotor motor = RotaryMotor.builder()
                .bidirectional(true)
                .build();

            motor.processMotorCommand(1.5);
            assertEquals(1.0, motor.getSpeed());

            motor.processMotorCommand(-1.5);
            assertEquals(-1.0, motor.getSpeed());
        }
    }

    @Nested
    @DisplayName("Registration")
    class RegistrationTests {

        @Test
        @DisplayName("should register with cache")
        void shouldRegisterWithCache() {
            RotaryMotor motor = RotaryMotor.builder().build();

            assertFalse(motor.isRegistered());
            assertNull(motor.groupId());

            motor._registerWithCache();

            assertTrue(motor.isRegistered());
            assertEquals(0, motor.groupId());
        }
    }

    @Nested
    @DisplayName("equals() and hashCode()")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("should be equal when all fields are equal")
        void shouldBeEqualWhenAllFieldsAreEqual() {
            RotaryMotor motor1 = RotaryMotor.builder()
                .encoding(RotaryMotor.Encoding.ABSOLUTE)
                .bidirectional(true)
                .build();

            RotaryMotor motor2 = RotaryMotor.builder()
                .encoding(RotaryMotor.Encoding.ABSOLUTE)
                .bidirectional(true)
                .build();

            assertEquals(motor1, motor2);
            assertEquals(motor1.hashCode(), motor2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when encoding differs")
        void shouldNotBeEqualWhenEncodingDiffers() {
            RotaryMotor motor1 = RotaryMotor.builder()
                .encoding(RotaryMotor.Encoding.ABSOLUTE)
                .build();

            RotaryMotor motor2 = RotaryMotor.builder()
                .encoding(RotaryMotor.Encoding.INCREMENTAL)
                .build();

            assertNotEquals(motor1, motor2);
        }

        @Test
        @DisplayName("should not be equal when bidirectional differs")
        void shouldNotBeEqualWhenBidirectionalDiffers() {
            RotaryMotor motor1 = RotaryMotor.builder()
                .bidirectional(true)
                .build();

            RotaryMotor motor2 = RotaryMotor.builder()
                .bidirectional(false)
                .build();

            assertNotEquals(motor1, motor2);
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @Test
        @DisplayName("should return formatted string")
        void shouldReturnFormattedString() {
            RotaryMotor motor = RotaryMotor.builder()
                .encoding(RotaryMotor.Encoding.ABSOLUTE)
                .bidirectional(true)
                .build();

            String result = motor.toString();

            assertTrue(result.contains("RotaryMotor"));
            assertTrue(result.contains("encoding=ABSOLUTE"));
            assertTrue(result.contains("bidirectional=true"));
        }
    }
}
