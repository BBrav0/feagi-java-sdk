/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSocketRelayConfigTest {
    @Test
    void testValidConstruction() {
        var config = new WebSocketRelayConfig("127.0.0.1", 9052, "relay-1", 10485760, 60000, 5000);
        assertEquals("127.0.0.1", config.bindHost());
        assertEquals(9052, config.bindPort());
        assertEquals("relay-1", config.embodimentId());
        assertEquals(10485760, config.maxMessageSizeBytes());
        assertEquals(60000, config.pingIntervalMs());
        assertEquals(5000, config.pingTimeoutMs());
    }

    @Test
    void testPingIntervalDisabled() {
        var config = new WebSocketRelayConfig("127.0.0.1", 9052, "relay-1", 10485760, 0, 5000);
        assertEquals(0, config.pingIntervalMs());
    }

    @Test
    void testNullBindHost() {
        assertThrows(NullPointerException.class,
                () -> new WebSocketRelayConfig(null, 9052, "relay-1", 10485760, 60000, 5000));
    }

    @Test
    void testEmptyBindHost() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketRelayConfig("", 9052, "relay-1", 10485760, 60000, 5000));
    }

    @Test
    void testBindPortZero() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketRelayConfig("127.0.0.1", 0, "relay-1", 10485760, 60000, 5000));
    }

    @Test
    void testBindPortMax() {
        var config = new WebSocketRelayConfig("127.0.0.1", 65535, "relay-1", 10485760, 60000, 5000);
        assertEquals(65535, config.bindPort());
    }

    @Test
    void testBindPortTooHigh() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketRelayConfig("127.0.0.1", 65536, "relay-1", 10485760, 60000, 5000));
    }

    @Test
    void testBindPortMin() {
        var config = new WebSocketRelayConfig("127.0.0.1", 1, "relay-1", 10485760, 60000, 5000);
        assertEquals(1, config.bindPort());
    }

    @Test
    void testNullEmbodimentId() {
        assertThrows(NullPointerException.class,
                () -> new WebSocketRelayConfig("127.0.0.1", 9052, null, 10485760, 60000, 5000));
    }

    @Test
    void testEmptyEmbodimentId() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketRelayConfig("127.0.0.1", 9052, "", 10485760, 60000, 5000));
    }

    @Test
    void testMaxMessageSizeZero() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketRelayConfig("127.0.0.1", 9052, "relay-1", 0, 60000, 5000));
    }

    @Test
    void testMaxMessageSizeNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketRelayConfig("127.0.0.1", 9052, "relay-1", -1, 60000, 5000));
    }

    @Test
    void testPingIntervalNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketRelayConfig("127.0.0.1", 9052, "relay-1", 10485760, -1, 5000));
    }

    @Test
    void testPingTimeoutZero() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketRelayConfig("127.0.0.1", 9052, "relay-1", 10485760, 60000, 0));
    }

    @Test
    void testPingTimeoutNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketRelayConfig("127.0.0.1", 9052, "relay-1", 10485760, 60000, -1));
    }

    @Test
    void testImplementsWebSocketTransportConfig() {
        var config = new WebSocketRelayConfig("127.0.0.1", 9052, "relay-1", 10485760, 60000, 5000);
        org.junit.jupiter.api.Assertions.assertInstanceOf(WebSocketTransportConfig.class, config);
    }
}
