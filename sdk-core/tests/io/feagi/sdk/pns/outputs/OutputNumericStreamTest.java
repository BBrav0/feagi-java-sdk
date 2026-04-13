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
 * Unit tests for {@link OutputNumericStream}.
 */
@DisplayName("OutputNumericStream")
class OutputNumericStreamTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should create with default values")
        void shouldCreateWithDefaultValues() {
            OutputNumericStream stream = OutputNumericStream.builder().build();

            assertEquals(1, stream.dimensions());
        }

        @Test
        @DisplayName("should create with custom dimensions")
        void shouldCreateWithCustomDimensions() {
            OutputNumericStream stream = OutputNumericStream.builder()
                .dimensions(5)
                .build();

            assertEquals(5, stream.dimensions());
        }

        @Test
        @DisplayName("should throw when dimensions is not positive")
        void shouldThrowWhenDimensionsIsNotPositive() {
            assertThrows(IllegalArgumentException.class, () ->
                OutputNumericStream.builder()
                    .dimensions(0)
                    .build()
            );

            assertThrows(IllegalArgumentException.class, () ->
                OutputNumericStream.builder()
                    .dimensions(-1)
                    .build()
            );
        }
    }

    @Nested
    @DisplayName("Values")
    class ValuesTests {

        @Test
        @DisplayName("should initialize with zeros")
        void shouldInitializeWithZeros() {
            OutputNumericStream stream = OutputNumericStream.builder()
                .dimensions(3)
                .build();

            double[] values = stream.getValues();

            assertEquals(3, values.length);
            assertEquals(0.0, values[0]);
            assertEquals(0.0, values[1]);
            assertEquals(0.0, values[2]);
        }

        @Test
        @DisplayName("should process values")
        void shouldProcessValues() {
            OutputNumericStream stream = OutputNumericStream.builder()
                .dimensions(3)
                .build();

            stream.processValues(new double[] {0.1, 0.5, 0.9});

            assertEquals(0.1, stream.getValue(0));
            assertEquals(0.5, stream.getValue(1));
            assertEquals(0.9, stream.getValue(2));
        }

        @Test
        @DisplayName("should return copy of values")
        void shouldReturnCopyOfValues() {
            OutputNumericStream stream = OutputNumericStream.builder()
                .dimensions(2)
                .build();

            stream.processValues(new double[] {0.5, 0.8});

            double[] values1 = stream.getValues();
            double[] values2 = stream.getValues();

            assertNotSame(values1, values2);
            assertArrayEquals(values1, values2);
        }

        @Test
        @DisplayName("should get single value by index")
        void shouldGetSingleValueByIndex() {
            OutputNumericStream stream = OutputNumericStream.builder()
                .dimensions(3)
                .build();

            stream.processValues(new double[] {0.1, 0.5, 0.9});

            assertEquals(0.5, stream.getValue(1));
        }

        @Test
        @DisplayName("should throw when index is out of bounds")
        void shouldThrowWhenIndexIsOutOfBounds() {
            OutputNumericStream stream = OutputNumericStream.builder()
                .dimensions(3)
                .build();

            assertThrows(IndexOutOfBoundsException.class, () ->
                stream.getValue(-1)
            );

            assertThrows(IndexOutOfBoundsException.class, () ->
                stream.getValue(3)
            );
        }

        @Test
        @DisplayName("should throw when values is null")
        void shouldThrowWhenValuesIsNull() {
            OutputNumericStream stream = OutputNumericStream.builder()
                .dimensions(3)
                .build();

            assertThrows(NullPointerException.class, () ->
                stream.processValues(null)
            );
        }

        @Test
        @DisplayName("should throw when values length mismatches")
        void shouldThrowWhenValuesLengthMismatches() {
            OutputNumericStream stream = OutputNumericStream.builder()
                .dimensions(3)
                .build();

            assertThrows(IllegalArgumentException.class, () ->
                stream.processValues(new double[] {0.1, 0.5})
            );
        }

        @Test
        @DisplayName("should get max value")
        void shouldGetMaxValue() {
            OutputNumericStream stream = OutputNumericStream.builder()
                .dimensions(4)
                .build();

            stream.processValues(new double[] {0.1, 0.9, 0.3, 0.7});

            assertEquals(0.9, stream.getMaxValue());
        }

        @Test
        @DisplayName("should get max index")
        void shouldGetMaxIndex() {
            OutputNumericStream stream = OutputNumericStream.builder()
                .dimensions(4)
                .build();

            stream.processValues(new double[] {0.1, 0.9, 0.3, 0.7});

            assertEquals(1, stream.getMaxIndex());
        }
    }

    @Nested
    @DisplayName("Registration")
    class RegistrationTests {

        @Test
        @DisplayName("should register with cache")
        void shouldRegisterWithCache() {
            OutputNumericStream stream = OutputNumericStream.builder().build();

            assertFalse(stream.isRegistered());
            assertNull(stream.groupId());

            stream._registerWithCache();

            assertTrue(stream.isRegistered());
            assertEquals(0, stream.groupId());
        }
    }

    @Nested
    @DisplayName("equals() and hashCode()")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("should be equal when all fields are equal")
        void shouldBeEqualWhenAllFieldsAreEqual() {
            OutputNumericStream stream1 = OutputNumericStream.builder()
                .dimensions(3)
                .build();

            OutputNumericStream stream2 = OutputNumericStream.builder()
                .dimensions(3)
                .build();

            assertEquals(stream1, stream2);
            assertEquals(stream1.hashCode(), stream2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when dimensions differs")
        void shouldNotBeEqualWhenDimensionsDiffers() {
            OutputNumericStream stream1 = OutputNumericStream.builder()
                .dimensions(3)
                .build();

            OutputNumericStream stream2 = OutputNumericStream.builder()
                .dimensions(5)
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
            OutputNumericStream stream = OutputNumericStream.builder()
                .dimensions(3)
                .build();

            stream.processValues(new double[] {0.1, 0.5, 0.9});

            String result = stream.toString();

            assertTrue(result.contains("OutputNumericStream"));
            assertTrue(result.contains("dimensions=3"));
            assertTrue(result.contains("[0.1, 0.5, 0.9]"));
        }
    }
}
