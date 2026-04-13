/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransportModeTest {
    @Test
    void testZmqPreferenceString() {
        assertEquals("zmq", TransportMode.ZMQ.toPreferenceString());
    }

    @Test
    void testWebSocketPreferenceString() {
        assertEquals("websocket", TransportMode.WEBSOCKET.toPreferenceString());
    }
}
