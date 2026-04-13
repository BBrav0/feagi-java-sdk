/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AgentCapabilities} FEAGI 2.0 vision_unit/motor_unit support.
 */
@DisplayName("AgentCapabilities - FEAGI 2.0 Unit Support")
class AgentCapabilitiesUnitSupportTest {

    @Nested
    @DisplayName("visionUnit()")
    class VisionUnitTests {

        @Test
        @DisplayName("should set and get visionUnit")
        void shouldSetAndGetVisionUnit() {
            VisionUnitConfig visionUnit = VisionUnitConfig.builder()
                .modality("camera")
                .resolution(640, 480)
                .channels(3)
                .unit(0)
                .group(0)
                .build();

            AgentCapabilities caps = AgentCapabilities.builder()
                .visionUnit(visionUnit)
                .build();

            assertNotNull(caps.visionUnit());
            assertEquals("camera", caps.visionUnit().modality());
            assertEquals(640, caps.visionUnit().width());
            assertEquals(480, caps.visionUnit().height());
            assertTrue(caps.hasVisionUnit());
        }

        @Test
        @DisplayName("should throw when visionUnit is null")
        void shouldThrowWhenVisionUnitIsNull() {
            assertThrows(NullPointerException.class, () ->
                AgentCapabilities.builder()
                    .visionUnit(null)
                    .build()
            );
        }

        @Test
        @DisplayName("should return false for hasVisionUnit when not set")
        void shouldReturnFalseForHasVisionUnitWhenNotSet() {
            AgentCapabilities caps = AgentCapabilities.builder()
                .customCapabilityJson("test", "{}")
                .build();

            assertFalse(caps.hasVisionUnit());
            assertNull(caps.visionUnit());
        }
    }

    @Nested
    @DisplayName("motorUnit() - single motor unit")
    class MotorUnitSingleTests {

        @Test
        @DisplayName("should set and get motorUnit")
        void shouldSetAndGetMotorUnit() {
            MotorUnitConfig motorUnit = MotorUnitConfig.builder()
                .type("servo")
                .range(0.0, 180.0)
                .unit(0)
                .group(1)
                .build();

            AgentCapabilities caps = AgentCapabilities.builder()
                .motorUnit(motorUnit)
                .build();

            assertNotNull(caps.motorUnit());
            assertEquals("servo", caps.motorUnit().type());
            assertEquals(0.0, caps.motorUnit().minValue());
            assertEquals(180.0, caps.motorUnit().maxValue());
            assertTrue(caps.hasMotorUnit());
            assertTrue(caps.motorUnits().isEmpty());
        }

        @Test
        @DisplayName("should throw when motorUnit is null")
        void shouldThrowWhenMotorUnitIsNull() {
            assertThrows(NullPointerException.class, () ->
                AgentCapabilities.builder()
                    .motorUnit(null)
                    .build()
            );
        }

        @Test
        @DisplayName("should return false for hasMotorUnit when not set")
        void shouldReturnFalseForHasMotorUnitWhenNotSet() {
            AgentCapabilities caps = AgentCapabilities.builder()
                .customCapabilityJson("test", "{}")
                .build();

            assertFalse(caps.hasMotorUnit());
            assertNull(caps.motorUnit());
        }
    }

    @Nested
    @DisplayName("motorUnits() - multiple motor units")
    class MotorUnitsMultipleTests {

        @Test
        @DisplayName("should add and get multiple motorUnits")
        void shouldAddAndGetMultipleMotorUnits() {
            MotorUnitConfig servoMotor = MotorUnitConfig.builder()
                .type("servo")
                .range(0.0, 180.0)
                .unit(0)
                .group(1)
                .build();

            MotorUnitConfig rotaryMotor = MotorUnitConfig.builder()
                .type("rotary")
                .range(-1.0, 1.0)
                .unit(1)
                .group(1)
                .build();

            AgentCapabilities caps = AgentCapabilities.builder()
                .addMotorUnit(servoMotor)
                .addMotorUnit(rotaryMotor)
                .build();

            List<MotorUnitConfig> motorUnits = caps.motorUnits();
            assertNotNull(motorUnits);
            assertEquals(2, motorUnits.size());

            MotorUnitConfig first = motorUnits.get(0);
            assertEquals("servo", first.type());

            MotorUnitConfig second = motorUnits.get(1);
            assertEquals("rotary", second.type());

            assertTrue(caps.hasMotorUnits());
            assertFalse(caps.hasMotorUnit());
        }

        @Test
        @DisplayName("should throw when adding null motorUnit")
        void shouldThrowWhenAddingNullMotorUnit() {
            assertThrows(NullPointerException.class, () ->
                AgentCapabilities.builder()
                    .addMotorUnit(null)
                    .build()
            );
        }

        @Test
        @DisplayName("should return unmodifiable motorUnits list")
        void shouldReturnUnmodifiableMotorUnitsList() {
            AgentCapabilities caps = AgentCapabilities.builder()
                .addMotorUnit(MotorUnitConfig.builder().type("servo").build())
                .build();

            assertThrows(UnsupportedOperationException.class, () ->
                caps.motorUnits().add(MotorUnitConfig.builder().type("servo").build())
            );
        }
    }

    @Nested
    @DisplayName("Combined vision_unit and motor_units")
    class CombinedTests {

        @Test
        @DisplayName("should support vision_unit with multiple motor_units")
        void shouldSupportVisionUnitWithMultipleMotorUnits() {
            VisionUnitConfig visionUnit = VisionUnitConfig.builder()
                .modality("camera")
                .resolution(640, 480)
                .channels(3)
                .unit(0)
                .group(0)
                .build();

            MotorUnitConfig servoMotor = MotorUnitConfig.builder()
                .type("servo")
                .range(0.0, 180.0)
                .unit(0)
                .group(1)
                .build();

            MotorUnitConfig rotaryMotor = MotorUnitConfig.builder()
                .type("rotary")
                .range(-1.0, 1.0)
                .unit(1)
                .group(1)
                .build();

            AgentCapabilities caps = AgentCapabilities.builder()
                .visionUnit(visionUnit)
                .addMotorUnit(servoMotor)
                .addMotorUnit(rotaryMotor)
                .build();

            assertTrue(caps.hasVisionUnit());
            assertTrue(caps.hasMotorUnits());
            assertNotNull(caps.visionUnit());
            assertEquals(2, caps.motorUnits().size());
        }

        @Test
        @DisplayName("should support vision_unit with single motor_unit")
        void shouldSupportVisionUnitWithSingleMotorUnit() {
            VisionUnitConfig visionUnit = VisionUnitConfig.builder()
                .modality("camera")
                .resolution(1920, 1080)
                .build();

            MotorUnitConfig motorUnit = MotorUnitConfig.builder()
                .type("servo")
                .range(0.0, 180.0)
                .build();

            AgentCapabilities caps = AgentCapabilities.builder()
                .visionUnit(visionUnit)
                .motorUnit(motorUnit)
                .build();

            assertTrue(caps.hasVisionUnit());
            assertTrue(caps.hasMotorUnit());
            assertFalse(caps.hasMotorUnits());
        }

        @Test
        @DisplayName("should throw when no capabilities are set")
        void shouldThrowWhenNoCapabilitiesAreSet() {
            assertThrows(IllegalArgumentException.class, () ->
                AgentCapabilities.builder().build()
            );
        }

        @Test
        @DisplayName("should work with custom capabilities")
        void shouldWorkWithCustomCapabilities() {
            VisionUnitConfig visionUnit = VisionUnitConfig.builder()
                .modality("camera")
                .resolution(640, 480)
                .build();

            AgentCapabilities caps = AgentCapabilities.builder()
                .visionUnit(visionUnit)
                .customCapabilityJson("bluetooth", "{\"enabled\": true}")
                .customCapabilityJson("max_range", "100")
                .build();

            assertTrue(caps.hasVisionUnit());
            assertEquals(2, caps.customCapabilitiesJson().size());
            assertEquals("{\"enabled\": true}", caps.customCapabilitiesJson().get("bluetooth"));
        }
    }

    @Nested
    @DisplayName("Builder fluent API")
    class FluentApiTests {

        @Test
        @DisplayName("should support fluent API")
        void shouldSupportFluentApi() {
            AgentCapabilities caps = AgentCapabilities.builder()
                .visionUnit(VisionUnitConfig.builder()
                    .modality("camera")
                    .resolution(640, 480)
                    .channels(3)
                    .unit(0)
                    .group(0)
                    .build())
                .motorUnit(MotorUnitConfig.builder()
                    .type("servo")
                    .range(0.0, 180.0)
                    .unit(0)
                    .group(1)
                    .build())
                .customCapabilityJson("custom", "{\"key\": \"value\"}")
                .build();

            assertNotNull(caps);
            assertTrue(caps.hasVisionUnit());
            assertTrue(caps.hasMotorUnit());
            assertEquals(1, caps.customCapabilitiesJson().size());
        }
    }
}
