/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.nativeffi;

import io.feagi.sdk.core.AgentCapabilities;
import io.feagi.sdk.core.AgentConfig;
import io.feagi.sdk.core.AgentType;
import io.feagi.sdk.core.FeagiEndpoints;
import io.feagi.sdk.core.MotorCapability;
import io.feagi.sdk.core.MotorUnit;
import io.feagi.sdk.core.MotorUnitSpec;
import io.feagi.sdk.core.SensorySocketConfig;
import io.feagi.sdk.core.SensoryUnit;
import io.feagi.sdk.core.VisionCapability;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for NativeFeagiAgentClient that do NOT require the native library.
 *
 * <p>Covers:
 * <ul>
 *   <li>Null config rejection at construction time</li>
 *   <li>Guard-rails on sendSensoryBytes / pollMotorBytes before connect()</li>
 *   <li>Null / empty payload rejection on sendSensoryBytes</li>
 *   <li>Idempotent close()</li>
 *   <li>cortical area ID validation (JSON injection guard)</li>
 *   <li>motorUnitSpecsToJson serialization contract</li>
 * </ul>
 */
class NativeFeagiAgentClientTest {

    // ── Test fixture ───────────────────────────────────────────────────────────

    private static AgentConfig minimalConfig() {
        FeagiEndpoints endpoints = new FeagiEndpoints(
                "tcp://localhost:30001",
                "tcp://localhost:5558",
                "tcp://localhost:5564",
                null,
                null
        );

        VisionCapability vision = VisionCapability.fromUnit(
                "camera", 320, 240, 3, SensoryUnit.VISION, 0);

        MotorCapability motor = MotorCapability.fromUnit(
                "drive", 4, MotorUnit.ROTARY_MOTOR, 0);

        AgentCapabilities caps = AgentCapabilities.builder()
                .vision(vision)
                .motor(motor)
                .build();

        return new AgentConfig(
                "test-agent",
                AgentType.BOTH,
                endpoints,
                caps,
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                3,
                Duration.ofMillis(500),
                new SensorySocketConfig(1000, 0, true)
        );
    }

    // ── Construction ───────────────────────────────────────────────────────────

    @Test
    void constructor_rejectsNullConfig() {
        assertThrows(IllegalArgumentException.class,
                () -> new NativeFeagiAgentClient(null));
    }

    @Test
    void constructor_acceptsValidConfig() {
        assertDoesNotThrow(() -> new NativeFeagiAgentClient(minimalConfig()));
    }

    // ── Guard-rails before connect() ───────────────────────────────────────────

    @Test
    void sendSensoryBytes_beforeConnect_throwsIllegalState() {
        var client = new NativeFeagiAgentClient(minimalConfig());
        assertThrows(IllegalStateException.class,
                () -> client.sendSensoryBytes(new byte[]{1, 2, 3}));
    }

    @Test
    void pollMotorBytes_beforeConnect_throwsIllegalState() {
        var client = new NativeFeagiAgentClient(minimalConfig());
        assertThrows(IllegalStateException.class, client::pollMotorBytes);
    }

    // ── Input validation ───────────────────────────────────────────────────────

    @Test
    void sendSensoryBytes_rejectsNullPayload() {
        var client = new NativeFeagiAgentClient(minimalConfig());
        assertThrows(IllegalArgumentException.class,
                () -> client.sendSensoryBytes(null));
    }

    @Test
    void sendSensoryBytes_rejectsEmptyPayload() {
        var client = new NativeFeagiAgentClient(minimalConfig());
        assertThrows(IllegalArgumentException.class,
                () -> client.sendSensoryBytes(new byte[0]));
    }

    // ── Idempotent close ───────────────────────────────────────────────────────

    @Test
    void close_beforeConnect_doesNotThrow() {
        var client = new NativeFeagiAgentClient(minimalConfig());
        assertDoesNotThrow(client::close);
    }

    @Test
    void close_isIdempotent() {
        var client = new NativeFeagiAgentClient(minimalConfig());
        assertDoesNotThrow(client::close);
        assertDoesNotThrow(client::close);
    }

    // ── validateCorticalAreaId ─────────────────────────────────────────────────

    @Test
    void validateCorticalAreaId_acceptsValidIds() {
        assertDoesNotThrow(() -> NativeFeagiAgentClient.validateCorticalAreaId("v1_motor"));
        assertDoesNotThrow(() -> NativeFeagiAgentClient.validateCorticalAreaId("area-01"));
        assertDoesNotThrow(() -> NativeFeagiAgentClient.validateCorticalAreaId("ABC"));
    }

    @Test
    void validateCorticalAreaId_rejectsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> NativeFeagiAgentClient.validateCorticalAreaId(null));
    }

    @Test
    void validateCorticalAreaId_rejectsEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> NativeFeagiAgentClient.validateCorticalAreaId(""));
    }

    @Test
    void validateCorticalAreaId_rejectsSpace() {
        assertThrows(IllegalArgumentException.class,
                () -> NativeFeagiAgentClient.validateCorticalAreaId("bad id"));
    }

    @Test
    void validateCorticalAreaId_rejectsQuote() {
        assertThrows(IllegalArgumentException.class,
                () -> NativeFeagiAgentClient.validateCorticalAreaId("bad\"id"));
    }

    @Test
    void validateCorticalAreaId_rejectsBrace() {
        assertThrows(IllegalArgumentException.class,
                () -> NativeFeagiAgentClient.validateCorticalAreaId("bad{id}"));
    }

    // ── toJsonStringArray ──────────────────────────────────────────────────────

    @Test
    void toJsonStringArray_emptyList_producesEmptyArray() {
        assertEquals("[]", NativeFeagiAgentClient.toJsonStringArray(Collections.emptyList()));
    }

    @Test
    void toJsonStringArray_singleItem() {
        assertEquals("[\"v1_motor\"]",
                NativeFeagiAgentClient.toJsonStringArray(List.of("v1_motor")));
    }

    @Test
    void toJsonStringArray_multipleItems() {
        assertEquals("[\"a\",\"b\",\"c\"]",
                NativeFeagiAgentClient.toJsonStringArray(List.of("a", "b", "c")));
    }

    @Test
    void toJsonStringArray_rejectsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> NativeFeagiAgentClient.toJsonStringArray(null));
    }

    @Test
    void toJsonStringArray_rejectsUnsafeId() {
        assertThrows(IllegalArgumentException.class,
                () -> NativeFeagiAgentClient.toJsonStringArray(List.of("ok", "bad id")));
    }

    // ── motorUnitSpecsToJson — ABI contract ────────────────────────────────────

    @Test
    void motorUnitSpecsToJson_singleSpec_exactFormat() {
        List<MotorUnitSpec> specs = List.of(new MotorUnitSpec(MotorUnit.ROTARY_MOTOR, 0));
        // MotorUnitCode.of(ROTARY_MOTOR) == 0
        assertEquals("[{\"unit\":0,\"group\":0}]",
                NativeFeagiAgentClient.motorUnitSpecsToJson(specs));
    }

    @Test
    void motorUnitSpecsToJson_multipleSpecs_exactFormat() {
        List<MotorUnitSpec> specs = Arrays.asList(
                new MotorUnitSpec(MotorUnit.ROTARY_MOTOR, 0),       // code 0
                new MotorUnitSpec(MotorUnit.POSITIONAL_SERVO, 1),    // code 1
                new MotorUnitSpec(MotorUnit.GAZE, 2)                 // code 2
        );
        assertEquals("[{\"unit\":0,\"group\":0},{\"unit\":1,\"group\":1},{\"unit\":2,\"group\":2}]",
                NativeFeagiAgentClient.motorUnitSpecsToJson(specs));
    }

    @Test
    void motorUnitSpecsToJson_emptyList_producesEmptyArray() {
        assertEquals("[]",
                NativeFeagiAgentClient.motorUnitSpecsToJson(Collections.emptyList()));
    }

    @Test
    void motorUnitSpecsToJson_rejectsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> NativeFeagiAgentClient.motorUnitSpecsToJson(null));
    }

    // ── Config variations ──────────────────────────────────────────────────────

    @Test
    void constructor_acceptsVisionFromTargetArea() {
        FeagiEndpoints endpoints = new FeagiEndpoints(
                "tcp://localhost:30001", "tcp://localhost:5558", null, null, null);

        VisionCapability vision = VisionCapability.fromTargetArea(
                "camera", 640, 480, 3, "vision_cortex");

        AgentConfig config = new AgentConfig(
                "vision-agent", AgentType.SENSORY, endpoints,
                AgentCapabilities.builder().vision(vision).build(),
                Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
                Duration.ofMillis(500), new SensorySocketConfig(1000, 0, true));

        assertDoesNotThrow(() -> new NativeFeagiAgentClient(config));
    }

    @Test
    void constructor_acceptsMotorFromCorticalAreas() {
        FeagiEndpoints endpoints = new FeagiEndpoints(
                "tcp://localhost:30001", null, "tcp://localhost:5564", null, null);

        MotorCapability motor = MotorCapability.fromCorticalAreas(
                "servo", 2, List.of("m_servo_0", "m_servo_1"));

        AgentConfig config = new AgentConfig(
                "motor-agent", AgentType.MOTOR, endpoints,
                AgentCapabilities.builder().motor(motor).build(),
                Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
                Duration.ofMillis(500), new SensorySocketConfig(1000, 0, true));

        assertDoesNotThrow(() -> new NativeFeagiAgentClient(config));
    }

    @Test
    void constructor_acceptsMotorFromUnits() {
        FeagiEndpoints endpoints = new FeagiEndpoints(
                "tcp://localhost:30001", null, "tcp://localhost:5564", null, null);

        MotorCapability motor = MotorCapability.fromUnits(
                "mixed", 4, List.of(
                        new MotorUnitSpec(MotorUnit.ROTARY_MOTOR, 0),
                        new MotorUnitSpec(MotorUnit.POSITIONAL_SERVO, 1)));

        AgentConfig config = new AgentConfig(
                "multi-motor-agent", AgentType.MOTOR, endpoints,
                AgentCapabilities.builder().motor(motor).build(),
                Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
                Duration.ofMillis(500), new SensorySocketConfig(1000, 0, true));

        assertDoesNotThrow(() -> new NativeFeagiAgentClient(config));
    }
}
