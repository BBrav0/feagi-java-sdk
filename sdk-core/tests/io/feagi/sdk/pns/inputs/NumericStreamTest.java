/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.pns.inputs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link NumericStream}.
 */
@DisplayName("NumericStream")
class NumericStreamTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should create with default values")
        void shouldCreateWithDefaultValues() {
            NumericStream stream = NumericStream.builder().build();

            assertEquals(0.001, stream.precision());
            assertEquals(0.0, stream.minValue());
            assertEquals(1.0, stream.maxValue());
            assertEquals(1.0, stream.scaleFactor());
            assertTrue(stream.clampToRange());
            assertEquals(0, stream.groupId());
        }

        @Test
        @DisplayName("should create with custom precision")
        void shouldCreateWithCustomPrecision() {
            NumericStream stream = NumericStream.builder()
                .precision(0.01)
                .build();

            assertEquals(0.01, stream.precision());
        }

        @Test
        @DisplayName("should create with custom range")
        void shouldCreateWithCustomRange() {
            NumericStream stream = NumericStream.builder()
                .range(-10.0, 10.0)
                .build();

            assertEquals(-10.0, stream.minValue());
            assertEquals(10.0, stream.maxValue());
        }

        @Test
        @DisplayName("should create with custom scaleFactor")
        void shouldCreateWithCustomScaleFactor() {
            NumericStream stream = NumericStream.builder()
                .scaleFactor(100.0)
                .build();

            assertEquals(100.0, stream.scaleFactor());
        }

        @Test
        @DisplayName("should throw when precision is not positive")
        void shouldThrowWhenPrecisionIsNotPositive() {
            assertThrows(IllegalArgumentException.class, () ->
                NumericStream.builder()
                    .precision(0)
                    .build()
            );

            assertThrows(IllegalArgumentException.class, () ->
                NumericStream.builder()
                    .precision(-0.001)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when minValue >= maxValue")
        void shouldThrowWhenMinValueGreaterOrEqualMaxValue() {
            assertThrows(IllegalArgumentException.class, () ->
                NumericStream.builder()
                    .range(10.0, 5.0)
                    .build()
            );

            assertThrows(IllegalArgumentException.class, () ->
                NumericStream.builder()
                    .range(5.0, 5.0)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when scaleFactor is not positive")
        void shouldThrowWhenScaleFactorIsNotPositive() {
            assertThrows(IllegalArgumentException.class, () ->
                NumericStream.builder()
                    .scaleFactor(0)
                    .build()
            );

            assertThrows(IllegalArgumentException.class, () ->
                NumericStream.builder()
                    .scaleFactor(-1.0)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when groupId is out of range")
        void shouldThrowWhenGroupIdIsOutOfRange() {
            assertThrows(IllegalArgumentException.class, () ->
                NumericStream.builder()
                    .groupId(-1)
                    .build()
            );

            assertThrows(IllegalArgumentException.class, () ->
                NumericStream.builder()
                    .groupId(256)
                    .build()
            );
        }
    }

    @Nested
    @DisplayName("Write Value")
    class WriteValueTests {

        @Test
        @DisplayName("should write value within range")
        void shouldWriteValueWithinRange() {
            NumericStream stream = NumericStream.builder()
                .range(0.0, 10.0)
                .build();

            stream.writeValue(5.0);

            assertEquals(5.0, stream.getCurrentValue());
        }

        @Test
        @DisplayName("should clamp value when clampToRange is true")
        void shouldClampValueWhenClampToRangeIsTrue() {
            NumericStream stream = NumericStream.builder()
                .range(0.0, 10.0)
                .clampToRange(true)
                .build();

            stream.writeValue(15.0);  // Above max

            assertEquals(10.0, stream.getCurrentValue());

            stream.writeValue(-5.0);  // Below min

            assertEquals(0.0, stream.getCurrentValue());
        }

        @Test
        @DisplayName("should throw when value out of range and clampToRange is false")
        void shouldThrowWhenValueOutOfRangeAndClampToRangeFalse() {
            NumericStream stream = NumericStream.builder()
                .range(0.0, 10.0)
                .clampToRange(false)
                .build();

            assertThrows(IllegalArgumentException.class, () ->
                stream.writeValue(15.0)
            );

            assertThrows(IllegalArgumentException.class, () ->
                stream.writeValue(-5.0)
            );
        }

        @Test
        @DisplayName("should apply precision rounding")
        void shouldApplyPrecisionRounding() {
            NumericStream stream = NumericStream.builder()
                .precision(0.1)
                .build();

            stream.writeValue(0.056);

            assertEquals(0.1, stream.getCurrentValue());

            stream.writeValue(0.14);

            assertEquals(0.1, stream.getCurrentValue());
        }

        @Test
        @DisplayName("should apply scaling")
        void shouldApplyScaling() {
            NumericStream stream = NumericStream.builder()
                .scaleFactor(100.0)
                .build();

            stream.writeValue(0.5);

            assertEquals(50.0, stream.getCurrentValue());
        }
    }

    @Nested
    @DisplayName("Normalize/Denormalize")
    class NormalizeTests {

        @Test
        @DisplayName("should normalize value to [0, 1]")
        void shouldNormalizeValueToZeroToOne() {
            NumericStream stream = NumericStream.builder()
                .range(0.0, 100.0)
                .build();

            double normalized = stream.normalize(50.0);

            assertEquals(0.5, normalized);
        }

        @Test
        @DisplayName("should normalize min value to 0")
        void shouldNormalizeMinValueToZero() {
            NumericStream stream = NumericStream.builder()
                .range(0.0, 100.0)
                .build();

            double normalized = stream.normalize(0.0);

            assertEquals(0.0, normalized);
        }

        @Test
        @DisplayName("should normalize max value to 1")
        void shouldNormalizeMaxValueToOne() {
            NumericStream stream = NumericStream.builder()
                .range(0.0, 100.0)
                .build();

            double normalized = stream.normalize(100.0);

            assertEquals(1.0, normalized);
        }

        @Test
        @DisplayName("should denormalize value from [0, 1]")
        void shouldDenormalizeValueFromZeroToOne() {
            NumericStream stream = NumericStream.builder()
                .range(0.0, 100.0)
                .build();

            double denormalized = stream.denormalize(0.5);

            assertEquals(50.0, denormalized);
        }

        @Test
        @DisplayName("should throw when normalized value out of range")
        void shouldThrowWhenNormalizedValueOutOfRange() {
            NumericStream stream = NumericStream.builder()
                .range(0.0, 100.0)
                .build();

            assertThrows(IllegalArgumentException.class, () ->
                stream.denormalize(-0.1)
            );

            assertThrows(IllegalArgumentException.class, () ->
                stream.denormalize(1.1)
            );
        }
    }

    @Nested
    @DisplayName("Registration")
    class RegistrationTests {

        @Test
        @DisplayName("should register with cache")
        void shouldRegisterWithCache() {
            NumericStream stream = NumericStream.builder().build();

            assertFalse(stream.isRegistered());

            stream._registerWithCache();

            assertTrue(stream.isRegistered());
        }
    }

    @Nested
    @DisplayName("equals() and hashCode()")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("should be equal when all fields are equal")
        void shouldBeEqualWhenAllFieldsAreEqual() {
            NumericStream stream1 = NumericStream.builder()
                .precision(0.01)
                .range(-5.0, 5.0)
                .scaleFactor(10.0)
                .clampToRange(false)
                .groupId(1)
                .build();

            NumericStream stream2 = NumericStream.builder()
                .precision(0.01)
                .range(-5.0, 5.0)
                .scaleFactor(10.0)
                .clampToRange(false)
                .groupId(1)
                .build();

            assertEquals(stream1, stream2);
            assertEquals(stream1.hashCode(), stream2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when fields differ")
        void shouldNotBeEqualWhenFieldsDiffer() {
            NumericStream stream1 = NumericStream.builder()
                .range(0.0, 10.0)
                .build();

            NumericStream stream2 = NumericStream.builder()
                .range(-10.0, 10.0)
                .build();

            assertNotEquals(stream1, stream2);
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @Test
        @DisplayName("should return formatted string")
        void shouldReturnFormattedString() {
            NumericStream stream = NumericStream.builder()
                .precision(0.01)
                .range(-10.0, 10.0)
                .build();

            String result = stream.toString();

            assertTrue(result.contains("NumericStream"));
            assertTrue(result.contains("precision=0.01"));
            assertTrue(result.contains("minValue=-10.0"));
            assertTrue(result.contains("maxValue=10.0"));
        }
    }
}
