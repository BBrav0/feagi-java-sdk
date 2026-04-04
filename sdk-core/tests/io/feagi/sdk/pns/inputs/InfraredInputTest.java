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
 * Unit tests for {@link InfraredInput}.
 */
@DisplayName("InfraredInput")
class InfraredInputTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should create with default values")
        void shouldCreateWithDefaultValues() {
            InfraredInput ir = InfraredInput.createBuilder().build();

            assertEquals(25.0, ir.fieldOfView());
            assertEquals(0.0, ir.minValue());
            assertEquals(1.0, ir.maxValue());
            assertNull(ir.position());
            assertNull(ir.sensorModel());
            assertEquals(0, ir.groupId());
        }

        @Test
        @DisplayName("should create with custom range")
        void shouldCreateWithCustomRange() {
            InfraredInput ir = InfraredInput.createBuilder()
                .range(0.03, 0.40)
                .build();

            assertEquals(0.03, ir.minValue());
            assertEquals(0.40, ir.maxValue());
        }

        @Test
        @DisplayName("should create with custom fieldOfView")
        void shouldCreateWithCustomFieldOfView() {
            InfraredInput ir = InfraredInput.createBuilder()
                .fieldOfView(30.0)
                .build();

            assertEquals(30.0, ir.fieldOfView());
        }

        @Test
        @DisplayName("should create with position")
        void shouldCreateWithPosition() {
            InfraredInput ir = InfraredInput.createBuilder()
                .position("front")
                .build();

            assertEquals("front", ir.position());
        }

        @Test
        @DisplayName("should create with sensorModel")
        void shouldCreateWithSensorModel() {
            InfraredInput ir = InfraredInput.createBuilder()
                .sensorModel("GP2Y0A21YK0F")
                .build();

            assertEquals("GP2Y0A21YK0F", ir.sensorModel());
        }

        @Test
        @DisplayName("should throw when fieldOfView is not positive")
        void shouldThrowWhenFieldOfViewIsNotPositive() {
            assertThrows(IllegalArgumentException.class, () ->
                InfraredInput.createBuilder()
                    .fieldOfView(0)
                    .build()
            );

            assertThrows(IllegalArgumentException.class, () ->
                InfraredInput.createBuilder()
                    .fieldOfView(-25.0)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when range is invalid")
        void shouldThrowWhenRangeIsInvalid() {
            assertThrows(IllegalArgumentException.class, () ->
                InfraredInput.createBuilder()
                    .range(0.40, 0.03)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when groupId is out of range")
        void shouldThrowWhenGroupIdIsOutOfRange() {
            assertThrows(IllegalArgumentException.class, () ->
                InfraredInput.createBuilder()
                    .groupId(-1)
                    .build()
            );

            assertThrows(IllegalArgumentException.class, () ->
                InfraredInput.createBuilder()
                    .groupId(256)
                    .build()
            );
        }
    }

    @Nested
    @DisplayName("Distance Measurement")
    class DistanceTests {

        @Test
        @DisplayName("should write distance value")
        void shouldWriteDistanceValue() {
            InfraredInput ir = InfraredInput.createBuilder()
                .range(0.03, 0.40)
                .build();

            ir.writeDistance(0.25);

            assertEquals(0.25, ir.getCurrentValue());
        }

        @Test
        @DisplayName("should get distance in centimeters")
        void shouldGetDistanceInCentimeters() {
            InfraredInput ir = InfraredInput.createBuilder()
                .range(0.03, 0.40)
                .build();

            ir.writeDistance(0.25);

            assertEquals(25.0, ir.getCurrentDistanceCm());
        }

        @Test
        @DisplayName("should return null when no distance set")
        void shouldReturnNullWhenNoDistanceSet() {
            InfraredInput ir = InfraredInput.createBuilder().build();

            assertNull(ir.getCurrentDistanceCm());
        }

        @Test
        @DisplayName("should clamp distance when clampToRange is true")
        void shouldClampDistanceWhenClampToRangeIsTrue() {
            InfraredInput ir = InfraredInput.createBuilder()
                .range(0.03, 0.40)
                .clampToRange(true)
                .build();

            ir.writeDistance(0.50);  // Above max

            assertEquals(0.40, ir.getCurrentValue());

            ir.writeDistance(0.01);  // Below min

            assertEquals(0.03, ir.getCurrentValue());
        }

        @Test
        @DisplayName("should throw when distance out of range and clampToRange is false")
        void shouldThrowWhenDistanceOutOfRangeAndClampToRangeFalse() {
            InfraredInput ir = InfraredInput.createBuilder()
                .range(0.03, 0.40)
                .clampToRange(false)
                .build();

            assertThrows(IllegalArgumentException.class, () ->
                ir.writeDistance(0.50)
            );
        }
    }

    @Nested
    @DisplayName("Object Detection")
    class DetectionTests {

        @Test
        @DisplayName("should detect object within threshold")
        void shouldDetectObjectWithinThreshold() {
            InfraredInput ir = InfraredInput.createBuilder()
                .range(0.03, 0.40)
                .build();

            ir.writeDistance(0.20);

            assertTrue(ir.isObjectDetected(0.30));
        }

        @Test
        @DisplayName("should not detect object beyond threshold")
        void shouldNotDetectObjectBeyondThreshold() {
            InfraredInput ir = InfraredInput.createBuilder()
                .range(0.03, 0.40)
                .build();

            ir.writeDistance(0.35);

            assertFalse(ir.isObjectDetected(0.30));
        }

        @Test
        @DisplayName("should return false when no reading available")
        void shouldReturnFalseWhenNoReadingAvailable() {
            InfraredInput ir = InfraredInput.createBuilder().build();

            assertFalse(ir.isObjectDetected(0.30));
        }
    }

    @Nested
    @DisplayName("Detection Cone")
    class DetectionConeTests {

        @Test
        @DisplayName("should calculate detection cone diameter")
        void shouldCalculateDetectionConeDiameter() {
            InfraredInput ir = InfraredInput.createBuilder()
                .fieldOfView(25.0)
                .build();

            double diameter = ir.getDetectionConeDiameter(0.10);

            assertTrue(diameter > 0);
            // At 10cm distance with 25° FOV, diameter should be approximately 4.4cm
            assertTrue(diameter > 0.01 && diameter < 0.10);
        }

        @Test
        @DisplayName("should increase cone diameter with distance")
        void shouldIncreaseConeDiameterWithDistance() {
            InfraredInput ir = InfraredInput.createBuilder()
                .fieldOfView(25.0)
                .build();

            double diameter1 = ir.getDetectionConeDiameter(0.10);
            double diameter2 = ir.getDetectionConeDiameter(0.20);

            assertTrue(diameter2 > diameter1);
        }

        @Test
        @DisplayName("should return zero diameter at zero distance")
        void shouldReturnZeroDiameterAtZeroDistance() {
            InfraredInput ir = InfraredInput.createBuilder().build();

            double diameter = ir.getDetectionConeDiameter(0);

            assertEquals(0.0, diameter);
        }
    }

    @Nested
    @DisplayName("Inherited NumericStream Features")
    class InheritedTests {

        @Test
        @DisplayName("should inherit precision from NumericStream")
        void shouldInheritPrecisionFromNumericStream() {
            InfraredInput ir = InfraredInput.createBuilder()
                .precision(0.001)
                .build();

            assertEquals(0.001, ir.precision());
        }

        @Test
        @DisplayName("should inherit scaleFactor from NumericStream")
        void shouldInheritScaleFactorFromNumericStream() {
            InfraredInput ir = InfraredInput.createBuilder()
                .scaleFactor(100.0)
                .build();

            ir.writeDistance(0.25);

            assertEquals(25.0, ir.getCurrentValue());
        }
    }

    @Nested
    @DisplayName("Registration")
    class RegistrationTests {

        @Test
        @DisplayName("should register with cache")
        void shouldRegisterWithCache() {
            InfraredInput ir = InfraredInput.createBuilder().build();

            assertFalse(ir.isRegistered());

            ir._registerWithCache();

            assertTrue(ir.isRegistered());
        }
    }

    @Nested
    @DisplayName("equals() and hashCode()")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("should be equal when all fields are equal")
        void shouldBeEqualWhenAllFieldsAreEqual() {
            InfraredInput ir1 = InfraredInput.createBuilder()
                .range(0.03, 0.40)
                .fieldOfView(25.0)
                .position("front")
                .sensorModel("GP2Y0A21YK0F")
                .groupId(1)
                .build();

            InfraredInput ir2 = InfraredInput.createBuilder()
                .range(0.03, 0.40)
                .fieldOfView(25.0)
                .position("front")
                .sensorModel("GP2Y0A21YK0F")
                .groupId(1)
                .build();

            assertEquals(ir1, ir2);
            assertEquals(ir1.hashCode(), ir2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when fields differ")
        void shouldNotBeEqualWhenFieldsDiffer() {
            InfraredInput ir1 = InfraredInput.createBuilder()
                .range(0.03, 0.40)
                .build();

            InfraredInput ir2 = InfraredInput.createBuilder()
                .range(0.10, 0.80)
                .build();

            assertNotEquals(ir1, ir2);
        }

        @Test
        @DisplayName("should not be equal when fieldOfView differs")
        void shouldNotBeEqualWhenFieldOfViewDiffers() {
            InfraredInput ir1 = InfraredInput.createBuilder()
                .fieldOfView(25.0)
                .build();

            InfraredInput ir2 = InfraredInput.createBuilder()
                .fieldOfView(30.0)
                .build();

            assertNotEquals(ir1, ir2);
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @Test
        @DisplayName("should return formatted string")
        void shouldReturnFormattedString() {
            InfraredInput ir = InfraredInput.createBuilder()
                .range(0.03, 0.40)
                .fieldOfView(25.0)
                .position("front")
                .sensorModel("GP2Y0A21YK0F")
                .build();

            String result = ir.toString();

            assertTrue(result.contains("InfraredInput"));
            assertTrue(result.contains("fieldOfView=25.0"));
            assertTrue(result.contains("position='front'"));
            assertTrue(result.contains("sensorModel='GP2Y0A21YK0F'"));
            assertTrue(result.contains("minValue=0.03"));
            // Note: 0.40 is displayed as 0.4 in Java's Double.toString()
            assertTrue(result.contains("maxValue=0.4"));
        }
    }
}
