/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSocketAgentConfigTest {
    @Test
    void testValidConstructionWithClientConfig() {
        var endpoints = new WebSocketEndpoints(
                "ws://127.0.0.1:9053",
                "ws://127.0.0.1:9051",
                "ws://127.0.0.1:9052",
                null,
                null
        );
        var capabilities = AgentCapabilities.builder()
                .sensory(new SensoryCapability(10.0, null))
                .motor(MotorCapability.fromUnits(
                        "test",
                        1,
                        List.of(new MotorUnitSpec(MotorUnit.ROTARY_MOTOR, 0))
                ))
                .build();
        var clientConfig = new WebSocketClientConfig("127.0.0.1", 8080, "agent-1", 5000, 100);

        var config = new WebSocketAgentConfig(
                "agent-123",
                AgentType.BOTH,
                endpoints,
                capabilities,
                Duration.ofSeconds(1),
                Duration.ofMillis(5000),
                3,
                Duration.ofMillis(100),
                clientConfig
        );

        assertEquals("agent-123", config.agentId());
        assertEquals(AgentType.BOTH, config.agentType());
        assertTrue(config.isClientMode());
        assertFalse(config.isRelayMode());
    }

    @Test
    void testValidConstructionWithRelayConfig() {
        var endpoints = new WebSocketEndpoints("ws://127.0.0.1:9053", "ws://127.0.0.1:9051", null, null, null);
        var capabilities = AgentCapabilities.builder()
                .sensory(new SensoryCapability(10.0, null))
                .build();
        var relayConfig = new WebSocketRelayConfig("0.0.0.0", 9052, "relay-1", 10485760, 60000, 5000);

        var config = new WebSocketAgentConfig(
                "relay-agent",
                AgentType.SENSORY,
                endpoints,
                capabilities,
                Duration.ofSeconds(1),
                Duration.ofMillis(5000),
                0,
                Duration.ofMillis(100),
                relayConfig
        );

        assertEquals("relay-agent", config.agentId());
        assertEquals(AgentType.SENSORY, config.agentType());
        assertFalse(config.isClientMode());
        assertTrue(config.isRelayMode());
    }

    @Test
    void testNullAgentId() {
        assertThrows(NullPointerException.class,
                () -> new WebSocketAgentConfig(
                        null, AgentType.SENSORY,
                        new WebSocketEndpoints("ws://127.0.0.1:9053", "ws://127.0.0.1:9051", null, null, null),
                        AgentCapabilities.builder().sensory(new SensoryCapability(10.0, null)).build(),
                        Duration.ofSeconds(1), Duration.ofMillis(5000), 0,
                        Duration.ofMillis(100),
                        new WebSocketClientConfig("127.0.0.1", 8080, "a", 5000, 100)
                ));
    }

    @Test
    void testEmptyAgentId() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketAgentConfig(
                        "", AgentType.SENSORY,
                        new WebSocketEndpoints("ws://127.0.0.1:9053", "ws://127.0.0.1:9051", null, null, null),
                        AgentCapabilities.builder().sensory(new SensoryCapability(10.0, null)).build(),
                        Duration.ofSeconds(1), Duration.ofMillis(5000), 0,
                        Duration.ofMillis(100),
                        new WebSocketClientConfig("127.0.0.1", 8080, "a", 5000, 100)
                ));
    }

    @Test
    void testNullAgentType() {
        assertThrows(NullPointerException.class,
                () -> new WebSocketAgentConfig(
                        "agent-1", null,
                        new WebSocketEndpoints("ws://127.0.0.1:9053", "ws://127.0.0.1:9051", null, null, null),
                        AgentCapabilities.builder().sensory(new SensoryCapability(10.0, null)).build(),
                        Duration.ofSeconds(1), Duration.ofMillis(5000), 0,
                        Duration.ofMillis(100),
                        new WebSocketClientConfig("127.0.0.1", 8080, "a", 5000, 100)
                ));
    }

    @Test
    void testNullEndpoints() {
        assertThrows(NullPointerException.class,
                () -> new WebSocketAgentConfig(
                        "agent-1", AgentType.SENSORY, null,
                        AgentCapabilities.builder().sensory(new SensoryCapability(10.0, null)).build(),
                        Duration.ofSeconds(1), Duration.ofMillis(5000), 0,
                        Duration.ofMillis(100),
                        new WebSocketClientConfig("127.0.0.1", 8080, "a", 5000, 100)
                ));
    }

    @Test
    void testNullCapabilities() {
        assertThrows(NullPointerException.class,
                () -> new WebSocketAgentConfig(
                        "agent-1", AgentType.SENSORY,
                        new WebSocketEndpoints("ws://127.0.0.1:9053", "ws://127.0.0.1:9051", null, null, null),
                        null,
                        Duration.ofSeconds(1), Duration.ofMillis(5000), 0,
                        Duration.ofMillis(100),
                        new WebSocketClientConfig("127.0.0.1", 8080, "a", 5000, 100)
                ));
    }

    @Test
    void testHeartbeatIntervalZero() {
        var endpoints = new WebSocketEndpoints("ws://127.0.0.1:9053", "ws://127.0.0.1:9051", null, null, null);
        var capabilities = AgentCapabilities.builder().sensory(new SensoryCapability(10.0, null)).build();

        var config = new WebSocketAgentConfig(
                "agent-1", AgentType.SENSORY, endpoints, capabilities,
                Duration.ZERO, Duration.ofMillis(5000), 0, Duration.ofMillis(100),
                new WebSocketClientConfig("127.0.0.1", 8080, "a", 5000, 100)
        );
        assertEquals(Duration.ZERO, config.heartbeatInterval());
    }

    @Test
    void testHeartbeatIntervalNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketAgentConfig(
                        "agent-1", AgentType.SENSORY,
                        new WebSocketEndpoints("ws://127.0.0.1:9053", "ws://127.0.0.1:9051", null, null, null),
                        AgentCapabilities.builder().sensory(new SensoryCapability(10.0, null)).build(),
                        Duration.ofMillis(-1), Duration.ofMillis(5000), 0,
                        Duration.ofMillis(100),
                        new WebSocketClientConfig("127.0.0.1", 8080, "a", 5000, 100)
                ));
    }

    @Test
    void testConnectionTimeoutZero() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketAgentConfig(
                        "agent-1", AgentType.SENSORY,
                        new WebSocketEndpoints("ws://127.0.0.1:9053", "ws://127.0.0.1:9051", null, null, null),
                        AgentCapabilities.builder().sensory(new SensoryCapability(10.0, null)).build(),
                        Duration.ofSeconds(1), Duration.ZERO, 0,
                        Duration.ofMillis(100),
                        new WebSocketClientConfig("127.0.0.1", 8080, "a", 5000, 100)
                ));
    }

    @Test
    void testNegativeRegistrationRetries() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketAgentConfig(
                        "agent-1", AgentType.SENSORY,
                        new WebSocketEndpoints("ws://127.0.0.1:9053", "ws://127.0.0.1:9051", null, null, null),
                        AgentCapabilities.builder().sensory(new SensoryCapability(10.0, null)).build(),
                        Duration.ofSeconds(1), Duration.ofMillis(5000), -1,
                        Duration.ofMillis(100),
                        new WebSocketClientConfig("127.0.0.1", 8080, "a", 5000, 100)
                ));
    }

    @Test
    void testNullTransportConfig() {
        assertThrows(NullPointerException.class,
                () -> new WebSocketAgentConfig(
                        "agent-1", AgentType.SENSORY,
                        new WebSocketEndpoints("ws://127.0.0.1:9053", "ws://127.0.0.1:9051", null, null, null),
                        AgentCapabilities.builder().sensory(new SensoryCapability(10.0, null)).build(),
                        Duration.ofSeconds(1), Duration.ofMillis(5000), 0,
                        Duration.ofMillis(100), null
                ));
    }

    @Test
    void testEndpointValidationForAgentType() {
        var endpoints = new WebSocketEndpoints("ws://127.0.0.1:9053", null, "ws://127.0.0.1:9052", null, null);
        var capabilities = AgentCapabilities.builder()
                .sensory(new SensoryCapability(10.0, null))
                .motor(MotorCapability.fromUnits(
                        "test",
                        1,
                        List.of(new MotorUnitSpec(MotorUnit.ROTARY_MOTOR, 0))
                ))
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketAgentConfig(
                        "agent-1", AgentType.BOTH, endpoints, capabilities,
                        Duration.ofSeconds(1), Duration.ofMillis(5000), 0,
                        Duration.ofMillis(100),
                        new WebSocketClientConfig("127.0.0.1", 8080, "a", 5000, 100)
                ));
    }

    @Test
    void testCapabilityValidationForAgentType() {
        var endpoints = new WebSocketEndpoints("ws://127.0.0.1:9053", "ws://127.0.0.1:9051", null, null, null);
        var capabilities = AgentCapabilities.builder()
                .sensory(new SensoryCapability(10.0, null))
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketAgentConfig(
                        "agent-1", AgentType.MOTOR, endpoints, capabilities,
                        Duration.ofSeconds(1), Duration.ofMillis(5000), 0,
                        Duration.ofMillis(100),
                        new WebSocketClientConfig("127.0.0.1", 8080, "a", 5000, 100)
                ));
    }

    @Test
    void testAccessors() {
        var endpoints = new WebSocketEndpoints(
                "ws://127.0.0.1:9053",
                "ws://127.0.0.1:9051",
                "ws://127.0.0.1:9052",
                null,
                null
        );
        var capabilities = AgentCapabilities.builder()
                .sensory(new SensoryCapability(10.0, null))
                .motor(MotorCapability.fromUnits(
                        "test",
                        1,
                        List.of(new MotorUnitSpec(MotorUnit.ROTARY_MOTOR, 0))
                ))
                .build();
        var clientConfig = new WebSocketClientConfig("host.local", 8080, "agent-1", 5000, 100);
        var heartbeat = Duration.ofSeconds(2);
        var timeout = Duration.ofMillis(3000);
        int retries = 5;
        var backoff = Duration.ofMillis(250);

        var config = new WebSocketAgentConfig(
                "test-agent",
                AgentType.BOTH,
                endpoints,
                capabilities,
                heartbeat,
                timeout,
                retries,
                backoff,
                clientConfig
        );

        assertEquals("test-agent", config.agentId());
        assertEquals(AgentType.BOTH, config.agentType());
        assertEquals(endpoints, config.endpoints());
        assertEquals(capabilities, config.capabilities());
        assertEquals(heartbeat, config.heartbeatInterval());
        assertEquals(timeout, config.connectionTimeout());
        assertEquals(retries, config.registrationRetries());
        assertEquals(backoff, config.retryBackoff());
        assertEquals(clientConfig, config.transportConfig());
    }
}
