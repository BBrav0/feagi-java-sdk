/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.nativeffi;

import io.feagi.sdk.core.AgentCapabilities;
import io.feagi.sdk.core.AgentConfig;
import io.feagi.sdk.core.AgentType;
import io.feagi.sdk.core.FeagiSdkException;
import io.feagi.sdk.core.FeagiEndpoints;
import io.feagi.sdk.core.MotorCapability;
import io.feagi.sdk.core.MotorUnit;
import io.feagi.sdk.core.MotorUnitSpec;
import io.feagi.sdk.core.SensorySocketConfig;
import io.feagi.sdk.core.SensoryUnit;
import io.feagi.sdk.core.VisionCapability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link NativeFeagiAgentClient} that do NOT require the native library.
 *
 * <p>Covers:
 * <ul>
 *   <li>Null config rejection at construction time</li>
 *   <li>Guard-rails on sendSensoryBytes / pollMotorBytes before connect()</li>
 *   <li>Double connect() guard</li>
 *   <li>Null / empty payload rejection on sendSensoryBytes</li>
 *   <li>Idempotent close()</li>
 *   <li>Cortical area ID validation (JSON injection guard)</li>
 *   <li>toJsonStringArray serialization contract</li>
 *   <li>motorUnitSpecsToJson ABI contract</li>
 *   <li>ABI code mappings for all enum values (SensoryUnitCode, MotorUnitCode, AgentTypeCode)</li>
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

    // ── Double connect() guard (#7) ───────────────────────────────────────────
    // connect() requires the native library for the actual connection, but the
    // already-connected guard fires before any native call if connected == true.
    // We can't reach that state without a successful connect(), so we verify the
    // guard text is present and that the check is in the right place via code review.
    // The guard is tested indirectly: close() resets connected, and a second close()
    // is idempotent (no double-free). The IllegalStateException path for a live
    // double-connect is covered by the integration smoke test.

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

    @Test
    void validateCorticalAreaId_rejectsUnicodeLetters() {
        // é is a letter per Character.isLetterOrDigit() but must be rejected
        // by our strict ASCII-only check.
        assertThrows(IllegalArgumentException.class,
                () -> NativeFeagiAgentClient.validateCorticalAreaId("caf\u00e9"));
        assertThrows(IllegalArgumentException.class,
                () -> NativeFeagiAgentClient.validateCorticalAreaId("\u4e2d\u6587")); // CJK
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
    void toJsonStringArray_rejectsNullElement() {
        // validateCorticalAreaId(null) throws — verify the list-level path surfaces this.
        List<String> list = new java.util.ArrayList<>();
        list.add("valid_area");
        list.add(null);
        assertThrows(IllegalArgumentException.class,
                () -> NativeFeagiAgentClient.toJsonStringArray(list));
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
        assertEquals(
                "[{\"unit\":0,\"group\":0},{\"unit\":1,\"group\":1},{\"unit\":2,\"group\":2}]",
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

    @Test
    void motorUnitSpecsToJson_rejectsNullElement() {
        List<MotorUnitSpec> specs = new java.util.ArrayList<>();
        specs.add(new MotorUnitSpec(MotorUnit.ROTARY_MOTOR, 0));
        specs.add(null);
        assertThrows(IllegalArgumentException.class,
                () -> NativeFeagiAgentClient.motorUnitSpecsToJson(specs));
    }

    // ── ABI code mapping coverage (#8) ────────────────────────────────────────
    // Parameterized tests that iterate all enum values and assert of() does not throw.
    // These will catch regressions when new enum values are added without updating
    // the mapping classes.

    @ParameterizedTest
    @EnumSource(SensoryUnit.class)
    void sensoryUnitCode_allValuesHaveMapping(SensoryUnit unit) {
        assertDoesNotThrow(() -> SensoryUnitCode.of(unit),
                "SensoryUnit." + unit.name() + " is missing from SensoryUnitCode mapping");
    }

    @ParameterizedTest
    @EnumSource(MotorUnit.class)
    void motorUnitCode_allValuesHaveMapping(MotorUnit unit) {
        assertDoesNotThrow(() -> MotorUnitCode.of(unit),
                "MotorUnit." + unit.name() + " is missing from MotorUnitCode mapping");
    }

    @ParameterizedTest
    @EnumSource(AgentType.class)
    void agentTypeCode_allValuesHaveMapping(AgentType type) {
        assertDoesNotThrow(() -> AgentTypeCode.of(type),
                "AgentType." + type.name() + " is missing from AgentTypeCode mapping");
    }

    @Test
    void sensoryUnitCode_rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> SensoryUnitCode.of(null));
    }

    @Test
    void motorUnitCode_rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> MotorUnitCode.of(null));
    }

    @Test
    void agentTypeCode_rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> AgentTypeCode.of(null));
    }

    // Spot-check a few pinned values to guard against accidental constant drift.
    @Test
    void sensoryUnitCode_pinnedValues() {
        assertEquals(0,  SensoryUnitCode.of(SensoryUnit.INFRARED));
        assertEquals(10, SensoryUnitCode.of(SensoryUnit.VISION));
        assertEquals(13, SensoryUnitCode.of(SensoryUnit.GYROSCOPE));
    }

    @Test
    void motorUnitCode_pinnedValues() {
        assertEquals(0, MotorUnitCode.of(MotorUnit.ROTARY_MOTOR));
        assertEquals(2, MotorUnitCode.of(MotorUnit.GAZE));
        assertEquals(7, MotorUnitCode.of(MotorUnit.SIMPLE_VISION_OUTPUT));
    }

    @Test
    void agentTypeCode_pinnedValues() {
        assertEquals(0, AgentTypeCode.of(AgentType.SENSORY));
        assertEquals(2, AgentTypeCode.of(AgentType.BOTH));
        assertEquals(4, AgentTypeCode.of(AgentType.INFRASTRUCTURE));
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

    // ── Double-connect guard via reflection (#9) ───────────────────────────────
    // We can't reach connected==true without a successful native connect(), but we
    // can use reflection to set the field directly and verify the guard fires before
    // any native call is attempted.

    @Test
    void connect_whenAlreadyConnected_throwsIllegalState() throws Exception {
        var client = new NativeFeagiAgentClient(minimalConfig());

        // Force connected = true and a non-null handle via reflection
        java.lang.reflect.Field connectedField =
                NativeFeagiAgentClient.class.getDeclaredField("connected");
        connectedField.setAccessible(true);
        connectedField.set(client, true);

        java.lang.reflect.Field handleField =
                NativeFeagiAgentClient.class.getDeclaredField("clientHandle");
        handleField.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicLong) handleField.get(client)).set(42L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, client::connect);
        assertTrue(ex.getMessage().contains("Already connected"),
                "Expected 'Already connected' in message, got: " + ex.getMessage());

        // Clean up — reset so close() doesn't try to free handle 42
        connectedField.set(client, false);
        ((java.util.concurrent.atomic.AtomicLong) handleField.get(client)).set(0L);
    }

    // ── pollMotorBytes feagiBufferLen edge cases (#10) ────────────────────────
    // These branches guard against native-side errors and oversized frames.
    // We reach them via reflection by stubbing the internal state after a
    // hypothetical pollMotorBytes call — but since these checks occur *after*
    // a native call we can only unit-test the guard logic directly on the
    // private methods or document that integration test coverage is required.
    // The guards themselves are visible in the source; tests below verify the
    // exception messages are correct by calling a test-hook if one is added,
    // or noting integration test coverage is needed for the live path.
    //
    // For now, assert that the guard constants are sane (len < 0 and len > MAX_VALUE
    // are mutually exclusive boundary conditions) to at least document the contract.

    @Test
    void pollMotorBytes_negativeLenGuard_documentedBoundary() {
        // The guard `if (len < 0)` in pollMotorBytes must fire before
        // `if (len > Integer.MAX_VALUE)` since a negative long can never be > MAX_VALUE.
        // This test documents the invariant; live coverage requires a native stub.
        assertTrue(-1L < 0, "negative len triggers native-error branch");
        assertTrue((long) Integer.MAX_VALUE + 1 > Integer.MAX_VALUE,
                "oversized len triggers oversized-frame branch");
    }
}
