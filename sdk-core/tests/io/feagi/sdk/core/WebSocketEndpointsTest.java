/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSocketEndpointsTest {
    @Test
    void testValidConstruction() {
        var endpoints = new WebSocketEndpoints(
                "ws://127.0.0.1:9053",
                "ws://127.0.0.1:9051",
                "ws://127.0.0.1:9052",
                "ws://127.0.0.1:9050",
                "ws://127.0.0.1:9054"
        );
        assertEquals("ws://127.0.0.1:9053", endpoints.registrationEndpoint());
        assertEquals("ws://127.0.0.1:9051", endpoints.sensoryEndpoint());
        assertEquals("ws://127.0.0.1:9052", endpoints.motorEndpoint());
        assertEquals("ws://127.0.0.1:9050", endpoints.visualizationEndpoint());
        assertEquals("ws://127.0.0.1:9054", endpoints.controlEndpoint());
    }

    @Test
    void testWssEndpoints() {
        var endpoints = new WebSocketEndpoints(
                "wss://example.com:9053",
                "wss://example.com:9051",
                "wss://example.com:9052",
                null,
                null
        );
        assertEquals("wss://example.com:9053", endpoints.registrationEndpoint());
        assertEquals("wss://example.com:9051", endpoints.sensoryEndpoint());
        assertNull(endpoints.visualizationEndpoint());
    }

    @Test
    void testNullRegistrationEndpoint() {
        assertThrows(NullPointerException.class,
                () -> new WebSocketEndpoints(null, "ws://127.0.0.1:9051", null, null, null));
    }

    @Test
    void testEmptyRegistrationEndpoint() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketEndpoints("", "ws://127.0.0.1:9051", null, null, null));
    }

    @Test
    void testRegistrationEndpointNonWs() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketEndpoints("tcp://127.0.0.1:5555", "ws://127.0.0.1:9051", null, null, null));
    }

    @Test
    void testSensoryEndpointNonWs() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketEndpoints("ws://127.0.0.1:9053", "tcp://127.0.0.1:5558", null, null, null));
    }

    @Test
    void testMotorEndpointNonWs() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketEndpoints("ws://127.0.0.1:9053", null, "http://127.0.0.1:9052", null, null));
    }

    @Test
    void testVisualizationEndpointNonWs() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketEndpoints("ws://127.0.0.1:9053", null, null, "udp://127.0.0.1:8080", null));
    }

    @Test
    void testBareWsSchemeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketEndpoints("ws://", null, null, null, null));
    }

    @Test
    void testBareWssSchemeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketEndpoints("wss://", null, null, null, null));
    }

    @Test
    void testOptionalEndpointsNull() {
        var endpoints = new WebSocketEndpoints("ws://127.0.0.1:9053", null, null, null, null);
        assertEquals("ws://127.0.0.1:9053", endpoints.registrationEndpoint());
        assertNull(endpoints.sensoryEndpoint());
        assertNull(endpoints.motorEndpoint());
    }

    @Test
    void testValidateForAgentTypeSensoryPass() {
        var endpoints = new WebSocketEndpoints(
                "ws://127.0.0.1:9053",
                "ws://127.0.0.1:9051",
                null,
                null,
                null
        );
        endpoints.validateForAgentType(AgentType.SENSORY);
    }

    @Test
    void testValidateForAgentTypeSensoryFail() {
        var endpoints = new WebSocketEndpoints("ws://127.0.0.1:9053", null, null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> endpoints.validateForAgentType(AgentType.SENSORY));
    }

    @Test
    void testValidateForAgentTypeMotorPass() {
        var endpoints = new WebSocketEndpoints(
                "ws://127.0.0.1:9053",
                null,
                "ws://127.0.0.1:9052",
                null,
                null
        );
        endpoints.validateForAgentType(AgentType.MOTOR);
    }

    @Test
    void testValidateForAgentTypeMotorFail() {
        var endpoints = new WebSocketEndpoints("ws://127.0.0.1:9053", null, null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> endpoints.validateForAgentType(AgentType.MOTOR));
    }

    @Test
    void testValidateForAgentTypeBothPass() {
        var endpoints = new WebSocketEndpoints(
                "ws://127.0.0.1:9053",
                "ws://127.0.0.1:9051",
                "ws://127.0.0.1:9052",
                null,
                null
        );
        endpoints.validateForAgentType(AgentType.BOTH);
    }

    @Test
    void testValidateForAgentTypeBothFailMissingSensory() {
        var endpoints = new WebSocketEndpoints(
                "ws://127.0.0.1:9053",
                null,
                "ws://127.0.0.1:9052",
                null,
                null
        );
        assertThrows(IllegalArgumentException.class,
                () -> endpoints.validateForAgentType(AgentType.BOTH));
    }

    @Test
    void testValidateForAgentTypeBothFailMissingMotor() {
        var endpoints = new WebSocketEndpoints(
                "ws://127.0.0.1:9053",
                "ws://127.0.0.1:9051",
                null,
                null,
                null
        );
        assertThrows(IllegalArgumentException.class,
                () -> endpoints.validateForAgentType(AgentType.BOTH));
    }

    @Test
    void testValidateForAgentTypeVisualizationPass() {
        var endpoints = new WebSocketEndpoints(
                "ws://127.0.0.1:9053",
                null,
                null,
                "ws://127.0.0.1:9050",
                null
        );
        endpoints.validateForAgentType(AgentType.VISUALIZATION);
    }

    @Test
    void testValidateForAgentTypeVisualizationFail() {
        var endpoints = new WebSocketEndpoints("ws://127.0.0.1:9053", null, null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> endpoints.validateForAgentType(AgentType.VISUALIZATION));
    }

    @Test
    void testValidateForAgentTypeInfrastructure() {
        var endpoints = new WebSocketEndpoints("ws://127.0.0.1:9053", null, null, null, null);
        endpoints.validateForAgentType(AgentType.INFRASTRUCTURE);
    }

    @Test
    void testEquals() {
        var endpoints1 = new WebSocketEndpoints(
                "ws://127.0.0.1:9053",
                "ws://127.0.0.1:9051",
                "ws://127.0.0.1:9052",
                null,
                null
        );
        var endpoints2 = new WebSocketEndpoints(
                "ws://127.0.0.1:9053",
                "ws://127.0.0.1:9051",
                "ws://127.0.0.1:9052",
                null,
                null
        );
        var endpoints3 = new WebSocketEndpoints(
                "ws://127.0.0.1:9053",
                "ws://127.0.0.1:9051",
                null,
                null,
                null
        );
        assertEquals(endpoints1, endpoints2);
        assertNotEquals(endpoints1, endpoints3);
    }

    @Test
    void testHashCode() {
        var endpoints1 = new WebSocketEndpoints(
                "ws://127.0.0.1:9053",
                "ws://127.0.0.1:9051",
                "ws://127.0.0.1:9052",
                null,
                null
        );
        var endpoints2 = new WebSocketEndpoints(
                "ws://127.0.0.1:9053",
                "ws://127.0.0.1:9051",
                "ws://127.0.0.1:9052",
                null,
                null
        );
        assertEquals(endpoints1.hashCode(), endpoints2.hashCode());
    }

    @Test
    void testToString() {
        var endpoints = new WebSocketEndpoints(
                "ws://127.0.0.1:9053",
                "ws://127.0.0.1:9051",
                null,
                null,
                null
        );
        assertEquals(
                "WebSocketEndpoints{registrationEndpoint='ws://127.0.0.1:9053', "
                        + "sensoryEndpoint='ws://127.0.0.1:9051'}",
                endpoints.toString());
        assertFalse(endpoints.toString().contains("motorEndpoint"));
        assertFalse(endpoints.toString().contains("null"));
    }
}