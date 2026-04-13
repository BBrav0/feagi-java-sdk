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
        @DisplayName("should default channels to 3")
        void shouldDefaultChannelsToThree() {
            VisionUnitConfig config = VisionUnitConfig.builder()
                .modality("camera")
                .resolution(640, 480)
                .build();

            assertEquals(3, config.channels());
        }

        @Test
        @DisplayName("should throw when modality is null")
        void shouldThrowWhenModalityIsNull() {
            assertThrows(NullPointerException.class, () ->
                VisionUnitConfig.builder()
                    .modality(null)
                    .resolution(640, 480)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when modality is empty")
        void shouldThrowWhenModalityIsEmpty() {
            assertThrows(IllegalArgumentException.class, () ->
                VisionUnitConfig.builder()
                    .modality("")
                    .resolution(640, 480)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when width is not positive")
        void shouldThrowWhenWidthIsNotPositive() {
            assertThrows(IllegalArgumentException.class, () ->
                VisionUnitConfig.builder()
                    .modality("camera")
                    .width(0)
                    .height(480)
                    .build()
            );

            assertThrows(IllegalArgumentException.class, () ->
                VisionUnitConfig.builder()
                    .modality("camera")
                    .width(-100)
                    .height(480)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when height is not positive")
        void shouldThrowWhenHeightIsNotPositive() {
            assertThrows(IllegalArgumentException.class, () ->
                VisionUnitConfig.builder()
                    .modality("camera")
                    .width(640)
                    .height(0)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when channels is not positive")
        void shouldThrowWhenChannelsIsNotPositive() {
            assertThrows(IllegalArgumentException.class, () ->
                VisionUnitConfig.builder()
                    .modality("camera")
                    .resolution(640, 480)
                    .channels(0)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when unit is out of range")
        void shouldThrowWhenUnitIsOutOfRange() {
            assertThrows(IllegalArgumentException.class, () ->
                VisionUnitConfig.builder()
                    .modality("camera")
                    .resolution(640, 480)
                    .unit(-1)
                    .build()
            );

            assertThrows(IllegalArgumentException.class, () ->
                VisionUnitConfig.builder()
                    .modality("camera")
                    .resolution(640, 480)
                    .unit(256)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when group is out of range")
        void shouldThrowWhenGroupIsOutOfRange() {
            assertThrows(IllegalArgumentException.class, () ->
                VisionUnitConfig.builder()
                    .modality("camera")
                    .resolution(640, 480)
                    .group(-1)
                    .build()
            );

            assertThrows(IllegalArgumentException.class, () ->
                VisionUnitConfig.builder()
                    .modality("camera")
                    .resolution(640, 480)
                    .group(256)
                    .build()
            );
        }
    }
}
