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
 * Unit tests for {@link Camera}.
 */
@DisplayName("Camera")
class CameraTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should create with default values")
        void shouldCreateWithDefaultValues() {
            Camera camera = Camera.builder().build();

            assertEquals(640, camera.width());
            assertEquals(480, camera.height());
            assertEquals(3, camera.channels());
            assertEquals("RGB", camera.encoding());
            assertEquals(0, camera.groupId());
            assertNull(camera.position());
        }

        @Test
        @DisplayName("should create with custom resolution")
        void shouldCreateWithCustomResolution() {
            Camera camera = Camera.builder()
                .resolution(1920, 1080)
                .build();

            assertEquals(1920, camera.width());
            assertEquals(1080, camera.height());
        }

        @Test
        @DisplayName("should create with custom channels")
        void shouldCreateWithCustomChannels() {
            Camera camera = Camera.builder()
                .channels(1)
                .build();

            assertEquals(1, camera.channels());
        }

        @Test
        @DisplayName("should create with custom encoding")
        void shouldCreateWithCustomEncoding() {
            Camera camera = Camera.builder()
                .encoding("RGBA")
                .build();

            assertEquals("RGBA", camera.encoding());
        }

        @Test
        @DisplayName("should create with position")
        void shouldCreateWithPosition() {
            Camera camera = Camera.builder()
                .position("front")
                .build();

            assertEquals("front", camera.position());
        }

        @Test
        @DisplayName("should create with groupId")
        void shouldCreateWithGroupId() {
            Camera camera = Camera.builder()
                .groupId(5)
                .build();

            assertEquals(5, camera.groupId());
        }

        @Test
        @DisplayName("should throw when width is not positive")
        void shouldThrowWhenWidthIsNotPositive() {
            assertThrows(IllegalArgumentException.class, () ->
                Camera.builder()
                    .width(0)
                    .build()
            );

            assertThrows(IllegalArgumentException.class, () ->
                Camera.builder()
                    .width(-100)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when height is not positive")
        void shouldThrowWhenHeightIsNotPositive() {
            assertThrows(IllegalArgumentException.class, () ->
                Camera.builder()
                    .height(0)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when channels is not positive")
        void shouldThrowWhenChannelsIsNotPositive() {
            assertThrows(IllegalArgumentException.class, () ->
                Camera.builder()
                    .channels(0)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when encoding is null")
        void shouldThrowWhenEncodingIsNull() {
            assertThrows(NullPointerException.class, () ->
                Camera.builder()
                    .encoding(null)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when encoding is empty")
        void shouldThrowWhenEncodingIsEmpty() {
            assertThrows(IllegalArgumentException.class, () ->
                Camera.builder()
                    .encoding("")
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when groupId is out of range")
        void shouldThrowWhenGroupIdIsOutOfRange() {
            assertThrows(IllegalArgumentException.class, () ->
                Camera.builder()
                    .groupId(-1)
                    .build()
            );

            assertThrows(IllegalArgumentException.class, () ->
                Camera.builder()
                    .groupId(256)
                    .build()
            );
        }
    }

    @Nested
    @DisplayName("Frame Operations")
    class FrameTests {

        @Test
        @DisplayName("should set and get frame")
        void shouldSetAndGetFrame() {
            Camera camera = Camera.builder()
                .resolution(2, 2)
                .channels(1)
                .build();

            byte[] frame = new byte[] {1, 2, 3, 4};
            camera.setFrame(frame);

            assertNotNull(camera.getCurrentFrame());
            assertArrayEquals(frame, camera.getCurrentFrame());
        }

        @Test
        @DisplayName("should throw when frame is null")
        void shouldThrowWhenFrameIsNull() {
            Camera camera = Camera.builder().build();

            assertThrows(NullPointerException.class, () ->
                camera.setFrame(null)
            );
        }

        @Test
        @DisplayName("should throw when frame size mismatches")
        void shouldThrowWhenFrameSizeMismatches() {
            Camera camera = Camera.builder()
                .resolution(2, 2)
                .channels(1)
                .build();

            byte[] wrongSizeFrame = new byte[] {1, 2, 3};  // Expected 4 bytes

            assertThrows(IllegalArgumentException.class, () ->
                camera.setFrame(wrongSizeFrame)
            );
        }

        @Test
        @DisplayName("should calculate correct frameSize")
        void shouldCalculateCorrectFrameSize() {
            Camera camera1 = Camera.builder()
                .resolution(640, 480)
                .channels(3)
                .build();

            assertEquals(640 * 480 * 3, camera1.frameSize());

            Camera camera2 = Camera.builder()
                .resolution(320, 240)
                .channels(1)
                .build();

            assertEquals(320 * 240 * 1, camera2.frameSize());
        }

        @Test
        @DisplayName("should set frame from ints")
        void shouldSetFrameFromInts() {
            Camera camera = Camera.builder()
                .resolution(2, 2)
                .channels(1)
                .build();

            int[] pixelData = new int[] {10, 20, 30, 40};
            camera.setFrameFromInts(pixelData);

            assertNotNull(camera.getCurrentFrame());
            assertEquals(4, camera.getCurrentFrame().length);
            assertEquals(10, camera.getCurrentFrame()[0] & 0xFF);
            assertEquals(40, camera.getCurrentFrame()[3] & 0xFF);
        }

        @Test
        @DisplayName("should throw when int pixel value is out of range")
        void shouldThrowWhenIntPixelValueIsOutOfRange() {
            Camera camera = Camera.builder()
                .resolution(1, 1)
                .channels(1)
                .build();

            assertThrows(IllegalArgumentException.class, () ->
                camera.setFrameFromInts(new int[] {256})
            );

            assertThrows(IllegalArgumentException.class, () ->
                camera.setFrameFromInts(new int[] {-1})
            );
        }

        @Test
        @DisplayName("should clone frame data")
        void shouldCloneFrameData() {
            Camera camera = Camera.builder()
                .resolution(2, 2)
                .channels(1)
                .build();

            byte[] frame = new byte[] {1, 2, 3, 4};
            camera.setFrame(frame);

            // Modify original array
            frame[0] = 99;

            // Camera's frame should be unchanged
            assertEquals(1, camera.getCurrentFrame()[0]);
        }
    }

    @Nested
    @DisplayName("Registration")
    class RegistrationTests {

        @Test
        @DisplayName("should register with cache")
        void shouldRegisterWithCache() {
            Camera camera = Camera.builder().build();

            assertFalse(camera.isRegistered());

            camera._registerWithCache();

            assertTrue(camera.isRegistered());
        }
    }

    @Nested
    @DisplayName("equals() and hashCode()")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("should be equal when all fields are equal")
        void shouldBeEqualWhenAllFieldsAreEqual() {
            Camera camera1 = Camera.builder()
                .resolution(640, 480)
                .channels(3)
                .encoding("RGB")
                .position("front")
                .groupId(0)
                .build();

            Camera camera2 = Camera.builder()
                .resolution(640, 480)
                .channels(3)
                .encoding("RGB")
                .position("front")
                .groupId(0)
                .build();

            assertEquals(camera1, camera2);
            assertEquals(camera1.hashCode(), camera2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when fields differ")
        void shouldNotBeEqualWhenFieldsDiffer() {
            Camera camera1 = Camera.builder()
                .resolution(640, 480)
                .build();

            Camera camera2 = Camera.builder()
                .resolution(1920, 1080)
                .build();

            assertNotEquals(camera1, camera2);
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @Test
        @DisplayName("should return formatted string")
        void shouldReturnFormattedString() {
            Camera camera = Camera.builder()
                .resolution(640, 480)
                .channels(3)
                .encoding("RGB")
                .position("front")
                .build();

            String result = camera.toString();

            assertTrue(result.contains("Camera"));
            assertTrue(result.contains("width=640"));
            assertTrue(result.contains("height=480"));
            assertTrue(result.contains("channels=3"));
            assertTrue(result.contains("encoding='RGB'"));
            assertTrue(result.contains("position='front'"));
        }
    }
}
