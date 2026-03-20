/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSocketClientConfigTest {
    @Test
    void testValidConstruction() {
        var config = new WebSocketClientConfig("127.0.0.1", 8080, "agent-1", 5000, 100);
        assertEquals("127.0.0.1", config.host());
        assertEquals(8080, config.port());
        assertEquals("agent-1", config.embodimentId());
        assertEquals(5000, config.connectionTimeoutMs());
        assertEquals(100, config.receiveTimeoutMs());
    }

    @Test
    void testNullHost() {
        assertThrows(NullPointerException.class,
                () -> new WebSocketClientConfig(null, 8080, "agent-1", 5000, 100));
    }

    @Test
    void testEmptyHost() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketClientConfig("", 8080, "agent-1", 5000, 100));
    }

    @Test
    void testPortZero() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketClientConfig("127.0.0.1", 0, "agent-1", 5000, 100));
    }

    @Test
    void testPortMax() {
        var config = new WebSocketClientConfig("127.0.0.1", 65535, "agent-1", 5000, 100);
        assertEquals(65535, config.port());
    }

    @Test
    void testPortTooHigh() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketClientConfig("127.0.0.1", 65536, "agent-1", 5000, 100));
    }

    @Test
    void testPortMin() {
        var config = new WebSocketClientConfig("127.0.0.1", 1, "agent-1", 5000, 100);
        assertEquals(1, config.port());
    }

    @Test
    void testNullEmbodimentId() {
        assertThrows(NullPointerException.class,
                () -> new WebSocketClientConfig("127.0.0.1", 8080, null, 5000, 100));
    }

    @Test
    void testEmptyEmbodimentId() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketClientConfig("127.0.0.1", 8080, "", 5000, 100));
    }

    @Test
    void testConnectionTimeoutZero() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketClientConfig("127.0.0.1", 8080, "agent-1", 0, 100));
    }

    @Test
    void testConnectionTimeoutNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketClientConfig("127.0.0.1", 8080, "agent-1", -1, 100));
    }

    @Test
    void testReceiveTimeoutZero() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketClientConfig("127.0.0.1", 8080, "agent-1", 5000, 0));
    }

    @Test
    void testReceiveTimeoutNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketClientConfig("127.0.0.1", 8080, "agent-1", 5000, -1));
    }

    @Test
    void testImplementsWebSocketTransportConfig() {
        var config = new WebSocketClientConfig("127.0.0.1", 8080, "agent-1", 5000, 100);
        org.junit.jupiter.api.Assertions.assertInstanceOf(WebSocketTransportConfig.class, config);
    }
}
