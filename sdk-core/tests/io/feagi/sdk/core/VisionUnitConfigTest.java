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
 * Unit tests for {@link VisionUnitConfig}.
 */
@DisplayName("VisionUnitConfig")
class VisionUnitConfigTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should create default VisionUnitConfig")
        void shouldCreateDefaultVisionUnitConfig() {
            VisionUnitConfig config = VisionUnitConfig.builder().build();

            assertEquals("camera", config.modality());
            assertEquals(640, config.width());
            assertEquals(480, config.height());
            assertEquals(3, config.channels());
            assertEquals(0, config.unit());
            assertEquals(0, config.group());
        }

        @Test
        @DisplayName("should create VisionUnitConfig with custom values")
        void shouldCreateVisionUnitConfigWithCustomValues() {
            VisionUnitConfig config = VisionUnitConfig.builder()
                .modality("infrared")
                .resolution(320, 240)
                .channels(1)
                .unit(1)
                .group(2)
                .build();

            assertEquals("infrared", config.modality());
            assertEquals(320, config.width());
            assertEquals(240, config.height());
            assertEquals(1, config.channels());
            assertEquals(1, config.unit());
            assertEquals(2, config.group());
        }

        @Test
        @DisplayName("should create VisionUnitConfig using individual setters")
        void shouldCreateVisionUnitConfigUsingIndividualSetters() {
            VisionUnitConfig config = VisionUnitConfig.builder()
                .width(1920)
                .height(1080)
                .channels(3)
                .modality("camera")
                .unit(0)
                .group(0)
                .build();

            assertEquals(1920, config.width());
            assertEquals(1080, config.height());
            assertEquals(3, config.channels());
        }

        @Test
        @DisplayName("should throw when modality is null")
        void shouldThrowWhenModalityIsNull() {
            assertThrows(NullPointerException.class, () ->
                VisionUnitConfig.builder()
                    .modality(null)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when modality is empty")
        void shouldThrowWhenModalityIsEmpty() {
            assertThrows(IllegalArgumentException.class, () ->
                VisionUnitConfig.builder()
                    .modality("")
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when width is not positive")
        void shouldThrowWhenWidthIsNotPositive() {
            assertThrows(IllegalArgumentException.class, () ->
                VisionUnitConfig.builder()
                    .width(0)
                    .build()
            );

            assertThrows(IllegalArgumentException.class, () ->
                VisionUnitConfig.builder()
                    .width(-100)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when height is not positive")
        void shouldThrowWhenHeightIsNotPositive() {
            assertThrows(IllegalArgumentException.class, () ->
                VisionUnitConfig.builder()
                    .height(0)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when channels is not positive")
        void shouldThrowWhenChannelsIsNotPositive() {
            assertThrows(IllegalArgumentException.class, () ->
                VisionUnitConfig.builder()
                    .channels(0)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when unit is out of range")
        void shouldThrowWhenUnitIsOutOfRange() {
            assertThrows(IllegalArgumentException.class, () ->
                VisionUnitConfig.builder()
                    .unit(-1)
                    .build()
            );

            assertThrows(IllegalArgumentException.class, () ->
                VisionUnitConfig.builder()
                    .unit(256)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when group is out of range")
        void shouldThrowWhenGroupIsOutOfRange() {
            assertThrows(IllegalArgumentException.class, () ->
                VisionUnitConfig.builder()
                    .group(-1)
                    .build()
            );

            assertThrows(IllegalArgumentException.class, () ->
                VisionUnitConfig.builder()
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
            VisionUnitConfig config1 = VisionUnitConfig.builder()
                .modality("camera")
                .resolution(640, 480)
                .channels(3)
                .unit(0)
                .group(0)
                .build();

            VisionUnitConfig config2 = VisionUnitConfig.builder()
                .modality("camera")
                .resolution(640, 480)
                .channels(3)
                .unit(0)
                .group(0)
                .build();

            assertEquals(config1, config2);
            assertEquals(config1.hashCode(), config2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when fields differ")
        void shouldNotBeEqualWhenFieldsDiffer() {
            VisionUnitConfig config1 = VisionUnitConfig.builder()
                .modality("camera")
                .resolution(640, 480)
                .build();

            VisionUnitConfig config2 = VisionUnitConfig.builder()
                .modality("infrared")
                .resolution(640, 480)
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
            VisionUnitConfig config = VisionUnitConfig.builder()
                .modality("camera")
                .resolution(640, 480)
                .channels(3)
                .build();

            String result = config.toString();

            assertTrue(result.contains("VisionUnitConfig"));
            assertTrue(result.contains("modality='camera'"));
            assertTrue(result.contains("width=640"));
            assertTrue(result.contains("height=480"));
            assertTrue(result.contains("channels=3"));
        }
    }
}
