/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void testStaticFactoryWithDuration() {
        var config = WebSocketClientConfig.of(
                "127.0.0.1", 8080, "agent-1",
                Duration.ofMillis(5000), Duration.ofMillis(100));
        assertEquals("127.0.0.1", config.host());
        assertEquals(8080, config.port());
        assertEquals("agent-1", config.embodimentId());
        assertEquals(5000, config.connectionTimeoutMs());
        assertEquals(100, config.receiveTimeoutMs());
    }

    @Test
    void testDurationAccessors() {
        var config = new WebSocketClientConfig("127.0.0.1", 8080, "agent-1", 5000, 100);
        assertEquals(Duration.ofMillis(5000), config.connectionTimeout());
        assertEquals(Duration.ofMillis(100), config.receiveTimeout());
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
        assertInstanceOf(WebSocketTransportConfig.class, config);
    }

    @Test
    void testEquals() {
        var config1 = new WebSocketClientConfig("127.0.0.1", 8080, "agent-1", 5000, 100);
        var config2 = new WebSocketClientConfig("127.0.0.1", 8080, "agent-1", 5000, 100);
        var config3 = new WebSocketClientConfig("127.0.0.1", 8080, "agent-2", 5000, 100);
        assertEquals(config1, config2);
        assertNotEquals(config1, config3);
    }

    @Test
    void testHashCode() {
        var config1 = new WebSocketClientConfig("127.0.0.1", 8080, "agent-1", 5000, 100);
        var config2 = new WebSocketClientConfig("127.0.0.1", 8080, "agent-1", 5000, 100);
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    void testToString() {
        var config = new WebSocketClientConfig("127.0.0.1", 8080, "agent-1", 5000, 100);
        String str = config.toString();
        assertTrue(str.contains("WebSocketClientConfig"));
        assertTrue(str.contains("127.0.0.1"));
        assertTrue(str.contains("8080"));
        assertTrue(str.contains("agent-1"));
    }
}