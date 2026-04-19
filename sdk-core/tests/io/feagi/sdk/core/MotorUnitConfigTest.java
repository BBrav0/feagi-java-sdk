/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MotorUnitConfig}.
 */
@DisplayName("MotorUnitConfig")
class MotorUnitConfigTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should create default MotorUnitConfig with explicit type")
        void shouldCreateDefaultMotorUnitConfig() {
            MotorUnitConfig config = MotorUnitConfig.builder()
                .type("servo")
                .build();

            assertEquals("servo", config.type());
            assertEquals(0.0, config.minValue());
            assertEquals(1.0, config.maxValue());
            assertEquals(0, config.unit());
            assertEquals(0, config.group());
        }

        @Test
        @DisplayName("should create MotorUnitConfig for servo motor")
        void shouldCreateMotorUnitConfigForServoMotor() {
            MotorUnitConfig config = MotorUnitConfig.builder()
                .type("servo")
                .range(0.0, 180.0)
                .unit(0)
                .group(1)
                .build();

            assertEquals("servo", config.type());
            assertEquals(0.0, config.minValue());
            assertEquals(180.0, config.maxValue());
            assertEquals(0, config.unit());
            assertEquals(1, config.group());
        }

        @Test
        @DisplayName("should create MotorUnitConfig for rotary motor")
        void shouldCreateMotorUnitConfigForRotaryMotor() {
            MotorUnitConfig config = MotorUnitConfig.builder()
                .type("rotary")
                .range(-1.0, 1.0)
                .unit(1)
                .group(1)
                .build();

            assertEquals("rotary", config.type());
            assertEquals(-1.0, config.minValue());
            assertEquals(1.0, config.maxValue());
            assertEquals(1, config.unit());
            assertEquals(1, config.group());
        }

        @Test
        @DisplayName("should create MotorUnitConfig using individual setters")
        void shouldCreateMotorUnitConfigUsingIndividualSetters() {
            MotorUnitConfig config = MotorUnitConfig.builder()
                .type("stepper")
                .minValue(0.0)
                .maxValue(360.0)
                .unit(2)
                .group(0)
                .build();

            assertEquals("stepper", config.type());
            assertEquals(0.0, config.minValue());
            assertEquals(360.0, config.maxValue());
            assertEquals(2, config.unit());
            assertEquals(0, config.group());
        }

        @Test
        @DisplayName("should throw when type is null")
        void shouldThrowWhenTypeIsNull() {
            assertThrows(NullPointerException.class, () ->
                MotorUnitConfig.builder()
                    .type(null)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when type is empty")
        void shouldThrowWhenTypeIsEmpty() {
            assertThrows(IllegalArgumentException.class, () ->
                MotorUnitConfig.builder()
                    .type("")
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when minValue > maxValue")
        void shouldThrowWhenMinRangeIsGreaterThanMaxRange() {
            assertThrows(IllegalArgumentException.class, () ->
                MotorUnitConfig.builder()
                    .type("servo")
                    .minValue(180.0)
                    .maxValue(0.0)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when unit is out of range")
        void shouldThrowWhenUnitIsOutOfRange() {
            assertThrows(IllegalArgumentException.class, () ->
                MotorUnitConfig.builder()
                    .type("servo")
                    .unit(-1)
                    .build()
            );

            assertThrows(IllegalArgumentException.class, () ->
                MotorUnitConfig.builder()
                    .type("servo")
                    .unit(256)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when group is out of range")
        void shouldThrowWhenGroupIsOutOfRange() {
            assertThrows(IllegalArgumentException.class, () ->
                MotorUnitConfig.builder()
                    .type("servo")
                    .group(-1)
                    .build()
            );

            assertThrows(IllegalArgumentException.class, () ->
                MotorUnitConfig.builder()
                    .type("servo")
                    .group(256)
                    .build()
            );
        }
    }

    @Nested
    @DisplayName("equals() and hashCode()")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("should be equal when all fields are equal")
        void shouldBeEqualWhenAllFieldsAreEqual() {
            MotorUnitConfig config1 = MotorUnitConfig.builder()
                .type("servo")
                .range(0.0, 180.0)
                .unit(0)
                .group(1)
                .build();

            MotorUnitConfig config2 = MotorUnitConfig.builder()
                .type("servo")
                .range(0.0, 180.0)
                .unit(0)
                .group(1)
                .build();

            assertEquals(config1, config2);
            assertEquals(config1.hashCode(), config2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when fields differ")
        void shouldNotBeEqualWhenFieldsDiffer() {
            MotorUnitConfig config1 = MotorUnitConfig.builder()
                .type("servo")
                .range(0.0, 180.0)
                .build();

            MotorUnitConfig config2 = MotorUnitConfig.builder()
                .type("rotary")
                .range(0.0, 180.0)
                .build();

            assertNotEquals(config1, config2);
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @Test
        @DisplayName("should return formatted string")
        void shouldReturnFormattedString() {
            MotorUnitConfig config = MotorUnitConfig.builder()
                .type("servo")
                .range(0.0, 180.0)
                .unit(0)
                .group(1)
                .build();

            String result = config.toString();

            assertTrue(result.contains("MotorUnitConfig"));
            assertTrue(result.contains("type='servo'"));
            assertTrue(result.contains("minValue=0.0"));
            assertTrue(result.contains("maxValue=180.0"));
        }
    }
}
