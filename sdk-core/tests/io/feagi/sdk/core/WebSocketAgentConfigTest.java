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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSocketAgentConfigTest {

    private static WebSocketEndpoints sensoryEndpoints() {
        return new WebSocketEndpoints("ws://127.0.0.1:9053", "ws://127.0.0.1:9051", null, null, null);
    }

    private static WebSocketEndpoints bothEndpoints() {
        return new WebSocketEndpoints(
                "ws://127.0.0.1:9053",
                "ws://127.0.0.1:9051",
                "ws://127.0.0.1:9052",
                null,
                null);
    }

    private static AgentCapabilities sensoryCapabilities() {
        return AgentCapabilities.builder().sensory(new SensoryCapability(10.0, null)).build();
    }

    private static AgentCapabilities bothCapabilities() {
        return AgentCapabilities.builder()
                .sensory(new SensoryCapability(10.0, null))
                .motor(MotorCapability.fromUnits(
                        "test",
                        1,
                        List.of(new MotorUnitSpec(MotorUnit.ROTARY_MOTOR, 0))
                ))
                .build();
    }

    private static WebSocketClientConfig clientTransport(String embodimentId) {
        return new WebSocketClientConfig("127.0.0.1", 8080, embodimentId, 5000, 100);
    }

    @Test
    void testValidConstructionWithClientConfig() {
        var endpoints = bothEndpoints();
        var capabilities = bothCapabilities();
        var clientConfig = clientTransport("agent-1");

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
    void testBuilderWithClientConfig() {
        var endpoints = bothEndpoints();
        var capabilities = bothCapabilities();
        var clientConfig = clientTransport("agent-1");

        var config = WebSocketAgentConfig.builder()
                .agentId("agent-123")
                .agentType(AgentType.BOTH)
                .endpoints(endpoints)
                .capabilities(capabilities)
                .heartbeatInterval(Duration.ofSeconds(1))
                .connectionTimeout(Duration.ofMillis(5000))
                .registrationRetries(3)
                .retryBackoff(Duration.ofMillis(100))
                .transportConfig(clientConfig)
                .build();

        assertEquals("agent-123", config.agentId());
        assertEquals(AgentType.BOTH, config.agentType());
        assertTrue(config.isClientMode());
        assertFalse(config.isRelayMode());
    }

    @Test
    void testBuilderWithRelayConfig() {
        var endpoints = sensoryEndpoints();
        var capabilities = sensoryCapabilities();
        var relayConfig = new WebSocketRelayConfig("127.0.0.1", 9052, "relay-1", 10485760, 60000, 5000);

        var config = WebSocketAgentConfig.builder()
                .agentId("relay-agent")
                .agentType(AgentType.SENSORY)
                .endpoints(endpoints)
                .capabilities(capabilities)
                .connectionTimeout(Duration.ofMillis(5000))
                .retryBackoff(Duration.ofMillis(100))
                .transportConfig(relayConfig)
                .build();

        assertEquals("relay-agent", config.agentId());
        assertEquals(AgentType.SENSORY, config.agentType());
        assertFalse(config.isClientMode());
        assertTrue(config.isRelayMode());
    }

    @Test
    void testBuilderDefaults() {
        var config = WebSocketAgentConfig.builder()
                .agentId("agent-1")
                .agentType(AgentType.SENSORY)
                .endpoints(sensoryEndpoints())
                .capabilities(sensoryCapabilities())
                .connectionTimeout(Duration.ofMillis(5000))
                .transportConfig(clientTransport("agent-1"))
                .build();

        assertEquals(Duration.ZERO, config.heartbeatInterval());
        assertEquals(0, config.registrationRetries());
        assertEquals(Duration.ZERO, config.retryBackoff());
    }

    @Test
    void testBuilderMissingAgentId() {
        assertThrows(NullPointerException.class,
                () -> WebSocketAgentConfig.builder()
                        .agentType(AgentType.SENSORY)
                        .endpoints(sensoryEndpoints())
                        .capabilities(sensoryCapabilities())
                        .connectionTimeout(Duration.ofMillis(5000))
                        .retryBackoff(Duration.ofMillis(100))
                        .transportConfig(clientTransport("a"))
                        .build());
    }

    @Test
    void testBuilderEmptyAgentId() {
        assertThrows(IllegalArgumentException.class,
                () -> WebSocketAgentConfig.builder()
                        .agentId("")
                        .agentType(AgentType.SENSORY)
                        .endpoints(sensoryEndpoints())
                        .capabilities(sensoryCapabilities())
                        .connectionTimeout(Duration.ofMillis(5000))
                        .retryBackoff(Duration.ofMillis(100))
                        .transportConfig(clientTransport("a"))
                        .build());
    }

    @Test
    void testBlankAgentId() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketAgentConfig(
                        "   ", AgentType.SENSORY,
                        sensoryEndpoints(),
                        sensoryCapabilities(),
                        Duration.ofSeconds(1), Duration.ofMillis(5000), 0,
                        null,
                        clientTransport("a")));
    }

    @Test
    void testBuilderBlankAgentId() {
        assertThrows(IllegalArgumentException.class,
                () -> WebSocketAgentConfig.builder()
                        .agentId(" \t")
                        .agentType(AgentType.SENSORY)
                        .endpoints(sensoryEndpoints())
                        .capabilities(sensoryCapabilities())
                        .connectionTimeout(Duration.ofMillis(5000))
                        .transportConfig(clientTransport("a"))
                        .build());
    }

    @Test
    void testConstructorNullRetryBackoffWhenRetriesPositive() {
        assertThrows(NullPointerException.class,
                () -> new WebSocketAgentConfig(
                        "agent-1", AgentType.SENSORY,
                        sensoryEndpoints(),
                        sensoryCapabilities(),
                        Duration.ofSeconds(1), Duration.ofMillis(5000), 3,
                        null,
                        clientTransport("a")));
    }

    @Test
    void testBuilderNullRetryBackoffWhenRetriesPositive() {
        assertThrows(NullPointerException.class,
                () -> WebSocketAgentConfig.builder()
                        .agentId("agent-1")
                        .agentType(AgentType.SENSORY)
                        .endpoints(sensoryEndpoints())
                        .capabilities(sensoryCapabilities())
                        .connectionTimeout(Duration.ofMillis(5000))
                        .registrationRetries(3)
                        .transportConfig(clientTransport("a"))
                        .build());
    }

    @Test
    void testRetryBackoffNegativeWhenRetriesZero() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketAgentConfig(
                        "agent-1", AgentType.SENSORY,
                        sensoryEndpoints(),
                        sensoryCapabilities(),
                        Duration.ofSeconds(1), Duration.ofMillis(5000), 0,
                        Duration.ofMillis(-1),
                        clientTransport("a")));
    }

    @Test
    void testNullAgentId() {
        assertThrows(NullPointerException.class,
                () -> new WebSocketAgentConfig(
                        null, AgentType.SENSORY,
                        sensoryEndpoints(),
                        sensoryCapabilities(),
                        Duration.ofSeconds(1), Duration.ofMillis(5000), 0,
                        null,
                        clientTransport("a")));
    }

    @Test
    void testEmptyAgentId() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketAgentConfig(
                        "", AgentType.SENSORY,
                        sensoryEndpoints(),
                        sensoryCapabilities(),
                        Duration.ofSeconds(1), Duration.ofMillis(5000), 0,
                        null,
                        clientTransport("a")));
    }

    @Test
    void testNullEndpoints() {
        assertThrows(NullPointerException.class,
                () -> new WebSocketAgentConfig(
                        "agent-1", AgentType.SENSORY, null,
                        sensoryCapabilities(),
                        Duration.ofSeconds(1), Duration.ofMillis(5000), 0,
                        null,
                        clientTransport("a")));
    }

    @Test
    void testNullCapabilities() {
        assertThrows(NullPointerException.class,
                () -> new WebSocketAgentConfig(
                        "agent-1", AgentType.SENSORY,
                        sensoryEndpoints(),
                        null,
                        Duration.ofSeconds(1), Duration.ofMillis(5000), 0,
                        null,
                        clientTransport("a")));
    }

    @Test
    void testHeartbeatIntervalZero() {
        var config = new WebSocketAgentConfig(
                "agent-1", AgentType.SENSORY, sensoryEndpoints(), sensoryCapabilities(),
                Duration.ZERO, Duration.ofMillis(5000), 0, null,
                clientTransport("a"));
        assertEquals(Duration.ZERO, config.heartbeatInterval());
    }

    @Test
    void testHeartbeatIntervalNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketAgentConfig(
                        "agent-1", AgentType.SENSORY,
                        sensoryEndpoints(),
                        sensoryCapabilities(),
                        Duration.ofMillis(-1), Duration.ofMillis(5000), 0,
                        null,
                        clientTransport("a")));
    }

    @Test
    void testConnectionTimeoutZero() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketAgentConfig(
                        "agent-1", AgentType.SENSORY,
                        sensoryEndpoints(),
                        sensoryCapabilities(),
                        Duration.ofSeconds(1), Duration.ZERO, 0,
                        null,
                        clientTransport("a")));
    }

    @Test
    void testNegativeRegistrationRetries() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketAgentConfig(
                        "agent-1", AgentType.SENSORY,
                        sensoryEndpoints(),
                        sensoryCapabilities(),
                        Duration.ofSeconds(1), Duration.ofMillis(5000), -1,
                        null,
                        clientTransport("a")));
    }

    @Test
    void testEndpointValidationForAgentType() {
        var endpoints = new WebSocketEndpoints("ws://127.0.0.1:9053", null, "ws://127.0.0.1:9052", null, null);
        var capabilities = bothCapabilities();

        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketAgentConfig(
                        "agent-1", AgentType.BOTH, endpoints, capabilities,
                        Duration.ofSeconds(1), Duration.ofMillis(5000), 0,
                        null,
                        clientTransport("a")));
    }

    @Test
    void testCapabilityValidationForAgentType() {
        var endpoints = sensoryEndpoints();
        var capabilities = sensoryCapabilities();

        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketAgentConfig(
                        "agent-1", AgentType.MOTOR, endpoints, capabilities,
                        Duration.ofSeconds(1), Duration.ofMillis(5000), 0,
                        null,
                        clientTransport("a")));
    }

    @Test
    void testAccessors() {
        var endpoints = bothEndpoints();
        var capabilities = bothCapabilities();
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

    @Test
    void testEquals() {
        var endpoints = bothEndpoints();
        var capabilities = bothCapabilities();
        var clientConfig = clientTransport("agent-1");

        var config1 = new WebSocketAgentConfig(
                "agent-1", AgentType.BOTH, endpoints, capabilities,
                Duration.ofSeconds(1), Duration.ofMillis(5000), 3,
                Duration.ofMillis(100), clientConfig
        );
        var config2 = new WebSocketAgentConfig(
                "agent-1", AgentType.BOTH, endpoints, capabilities,
                Duration.ofSeconds(1), Duration.ofMillis(5000), 3,
                Duration.ofMillis(100), clientConfig
        );
        var config3 = new WebSocketAgentConfig(
                "agent-2", AgentType.BOTH, endpoints, capabilities,
                Duration.ofSeconds(1), Duration.ofMillis(5000), 3,
                Duration.ofMillis(100), clientConfig
        );

        assertEquals(config1, config2);
        assertNotEquals(config1, config3);
    }

    @Test
    void testHashCode() {
        var endpoints = bothEndpoints();
        var capabilities = bothCapabilities();
        var clientConfig = clientTransport("agent-1");

        var config1 = new WebSocketAgentConfig(
                "agent-1", AgentType.BOTH, endpoints, capabilities,
                Duration.ofSeconds(1), Duration.ofMillis(5000), 3,
                Duration.ofMillis(100), clientConfig
        );
        var config2 = new WebSocketAgentConfig(
                "agent-1", AgentType.BOTH, endpoints, capabilities,
                Duration.ofSeconds(1), Duration.ofMillis(5000), 3,
                Duration.ofMillis(100), clientConfig
        );

        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    void testToString() {
        var endpoints = bothEndpoints();
        var capabilities = bothCapabilities();
        var clientConfig = clientTransport("agent-1");

        var config = new WebSocketAgentConfig(
                "test-agent", AgentType.BOTH, endpoints, capabilities,
                Duration.ofSeconds(1), Duration.ofMillis(5000), 3,
                Duration.ofMillis(100), clientConfig
        );

        String str = config.toString();
        assertTrue(str.startsWith("WebSocketAgentConfig{"));
        assertTrue(str.contains("agentId='test-agent'"));
        assertTrue(str.contains("agentType=BOTH"));
        assertTrue(str.contains("heartbeatInterval=PT1S"));
        assertTrue(str.contains("connectionTimeout=PT5S"));
        assertTrue(str.contains("registrationRetries=3"));
        assertTrue(str.contains("retryBackoff=PT0.1S"));
        assertTrue(str.contains("endpoints="));
        assertTrue(str.contains("capabilities="));
        assertTrue(str.contains(
                "transportConfig=WebSocketClientConfig{host='127.0.0.1', port=8080, embodimentId='agent-1', "
                        + "connectionTimeoutMs=5000, receiveTimeoutMs=100}"));
    }
}