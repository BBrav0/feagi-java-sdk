/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core.pns;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrainInputTest {

    @Test
    void configureRequiresExplicitValues() {
        assertThrows(IllegalArgumentException.class, () -> new BrainInputConfig(" ", 5558, BrainInputTransportType.ZMQ));
        assertThrows(IllegalArgumentException.class, () -> new BrainInputConfig("localhost", 0, BrainInputTransportType.ZMQ));
        assertThrows(NullPointerException.class, () -> new BrainInputConfig("localhost", 5558, null));
    }

    @Test
    void connectRequiresConfigurationAndTransport() {
        BrainInput brainInput = BrainInput.create();
        assertThrows(IllegalStateException.class, brainInput::connect);

        brainInput.configure("localhost", 5558, BrainInputTransportType.ZMQ);
        assertThrows(IllegalStateException.class, brainInput::connect);
    }

    @Test
    void sendRequiresConnectionAndRegisteredInputs() {
        RecordingTransport transport = new RecordingTransport();
        BrainInput brainInput = BrainInput.create(transport)
                .configure("localhost", 5558, BrainInputTransportType.ZMQ);

        assertThrows(IllegalStateException.class, brainInput::send);

        brainInput.connect();
        assertThrows(IllegalStateException.class, brainInput::send);
    }

    @Test
    void registerConnectAndSendEncodesAllInputs() {
        RecordingTransport transport = new RecordingTransport();
        BrainInput brainInput = BrainInput.create(transport)
                .configure("127.0.0.1", 7777, BrainInputTransportType.WEBSOCKET)
                .registerInput("camera", () -> new byte[]{1, 2, 3})
                .registerInput("gyro", () -> new byte[]{9});

        brainInput.connect();
        brainInput.send();

        assertTrue(brainInput.connected());
        assertEquals("127.0.0.1", transport.host);
        assertEquals(7777, transport.port);
        assertEquals(BrainInputTransportType.WEBSOCKET, transport.transportType);

        byte[] payload = transport.lastPayload;
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        assertEquals(2, buffer.getInt());
        assertEquals("camera", readString(buffer));
        assertArrayEquals(new byte[]{1, 2, 3}, readBytes(buffer));
        assertEquals("gyro", readString(buffer));
        assertArrayEquals(new byte[]{9}, readBytes(buffer));
        assertFalse(buffer.hasRemaining());
    }

    @Test
    void closeReleasesTransport() {
        RecordingTransport transport = new RecordingTransport();
        BrainInput brainInput = BrainInput.create(transport)
                .configure("localhost", 5558, BrainInputTransportType.ZMQ)
                .registerInput("text", () -> "abc".getBytes(StandardCharsets.UTF_8));

        brainInput.connect();
        brainInput.close();

        assertTrue(transport.closed);
        assertFalse(brainInput.connected());
    }

    private static String readString(ByteBuffer buffer) {
        return new String(readBytes(buffer), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(ByteBuffer buffer) {
        int size = buffer.getInt();
        byte[] bytes = new byte[size];
        buffer.get(bytes);
        return bytes;
    }

    private static final class RecordingTransport implements BrainInputTransport {
        private String host;
        private int port;
        private BrainInputTransportType transportType;
        private byte[] lastPayload;
        private boolean closed;

        @Override
        public void connect(String host, int port, BrainInputTransportType transportType) {
            this.host = host;
            this.port = port;
            this.transportType = transportType;
        }

        @Override
        public void send(byte[] payload) {
            this.lastPayload = payload;
        }

        @Override
        public void close() {
            this.closed = true;
        }
    }
}