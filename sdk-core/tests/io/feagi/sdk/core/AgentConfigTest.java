/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for AgentConfig and capability validation.
 */
public class AgentConfigTest {

    /**
     * Validate that sensory agents require a sensory endpoint.
     */
    @Test
    public void testSensoryEndpointsRequired() {
        FeagiEndpoints endpoints = new FeagiEndpoints(
                "tcp://host:30001",
                null,
                null,
                null,
                null
        );

        AgentCapabilities capabilities = AgentCapabilities.builder()
                .vision(VisionCapability.fromTargetArea(
                        "camera", 640, 480, 3, "i_vision"))
                .build();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new AgentConfig(
                        "agent",
                        AgentType.SENSORY,
                        endpoints,
                        capabilities,
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(2),
                        3,
                        Duration.ofMillis(500),
                        new SensorySocketConfig(1, 0, true)
                )
        );
        assertEquals("sensoryEndpoint must be set for this agent type", ex.getMessage());
    }

    /**
     * Validate that motor agents require motor capability.
     */
    @Test
    public void testMotorCapabilityRequired() {
        FeagiEndpoints endpoints = new FeagiEndpoints(
                "tcp://host:30001",
                null,
                "tcp://host:5564",
                null,
                null
        );

        AgentCapabilities capabilities = AgentCapabilities.builder()
                .vision(VisionCapability.fromTargetArea(
                        "camera", 640, 480, 3, "i_vision"))
                .build();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new AgentConfig(
                        "agent",
                        AgentType.MOTOR,
                        endpoints,
                        capabilities,
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(2),
                        3,
                        Duration.ofMillis(500),
                        new SensorySocketConfig(1, 0, true)
                )
        );
        assertEquals("Motor agent must declare motor capability", ex.getMessage());
    }

    /**
     * Validate that motor capability can be constructed from semantic units.
     */
    @Test
    public void testMotorCapabilityUnits() {
        MotorCapability capability = MotorCapability.fromUnits(
                "servo",
                2,
                List.of(
                        new MotorUnitSpec(MotorUnit.ROTARY_MOTOR, 0),
                        new MotorUnitSpec(MotorUnit.POSITIONAL_SERVO, 1)
                )
        );
        assertEquals(2, capability.outputCount());
        assertEquals(2, capability.sourceUnits().size());
    }
}
