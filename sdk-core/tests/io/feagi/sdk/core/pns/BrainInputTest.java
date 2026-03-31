/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core.pns;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrainInputTest {

    @Test
    void configureRequiresExplicitValues() {
        assertThrows(IllegalArgumentException.class, () -> new BrainInputConfig(" ", 5558, TransportMode.ZMQ));
        assertThrows(IllegalArgumentException.class, () -> new BrainInputConfig("localhost", 0, TransportMode.ZMQ));
        assertThrows(IllegalArgumentException.class, () -> new BrainInputConfig("localhost", 65536, TransportMode.ZMQ));
        assertThrows(IllegalArgumentException.class, () -> new BrainInputConfig("localhost", 5558, null));
    }

    @Test
    void transportModeParsesSupportedValues() {
        assertEquals(TransportMode.ZMQ, TransportMode.from("zmq"));
        assertEquals(TransportMode.WEBSOCKET, TransportMode.from("ws"));
        assertThrows(IllegalArgumentException.class, () -> TransportMode.from(" "));
    }

    @Test
    void connectRequiresConfigurationAndTransport() {
        BrainInput brainInput = BrainInput.create();
        assertThrows(IllegalStateException.class, brainInput::connect);

        brainInput.configure("localhost", 5558, TransportMode.ZMQ);
        assertThrows(IllegalStateException.class, brainInput::connect);
    }

    @Test
    void sendRequiresConnectionAndRegisteredInputs() {
        RecordingTransport transport = new RecordingTransport();
        BrainInput brainInput = BrainInput.create(transport)
                .configure("localhost", 5558, TransportMode.ZMQ);

        assertThrows(IllegalStateException.class, brainInput::send);

        brainInput.connect();
        assertThrows(IllegalStateException.class, brainInput::send);
    }

    @Test
    void registerRejectsDuplicateNames() {
        BrainInput brainInput = BrainInput.create(new RecordingTransport())
                .configure("localhost", 5558, TransportMode.ZMQ)
                .registerInput("camera", () -> new byte[]{1});

        assertThrows(IllegalArgumentException.class, () -> brainInput.registerInput("camera", () -> new byte[]{2}));
    }

    @Test
    void registerConnectAndSendEncodesHeaderAndAllInputs() {
        RecordingTransport transport = new RecordingTransport();
        BrainInput brainInput = BrainInput.create(transport)
                .configure("127.0.0.1", 7777, TransportMode.WEBSOCKET)
                .registerInput("camera", () -> new byte[]{1, 2, 3})
                .registerInput("gyro", () -> new byte[]{9});

        brainInput.connect();
        brainInput.send();

        assertTrue(brainInput.connected());
        assertEquals("127.0.0.1", transport.host);
        assertEquals(7777, transport.port);
        assertEquals(TransportMode.WEBSOCKET, transport.transportMode);

        byte[] payload = transport.lastPayload;
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        byte[] magic = new byte[4];
        buffer.get(magic);
        assertArrayEquals(new byte[]{'F', 'B', 'I', 'N'}, magic);
        assertEquals(1, Byte.toUnsignedInt(buffer.get()));
        assertEquals(2, buffer.getInt());
        assertEquals("camera", readString(buffer));
        assertArrayEquals(new byte[]{1, 2, 3}, readBytes(buffer));
        assertEquals("gyro", readString(buffer));
        assertArrayEquals(new byte[]{9}, readBytes(buffer));
        assertFalse(buffer.hasRemaining());
    }

    @Test
    void closeClearsSingletonState() {
        RecordingTransport transport = new RecordingTransport();
        BrainInput brainInput = BrainInput.create(transport)
                .configure("localhost", 5558, TransportMode.ZMQ)
                .registerInput("text", () -> "abc".getBytes(StandardCharsets.UTF_8));

        brainInput.connect();
        brainInput.close();

        assertTrue(transport.closed);
        assertFalse(brainInput.connected());
        assertNull(brainInput.config());
        assertEquals(List.of(), brainInput.registeredInputs());
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
        private TransportMode transportMode;
        private byte[] lastPayload;
        private boolean closed;

        @Override
        public void connect(String host, int port, TransportMode transportMode) {
            this.host = host;
            this.port = port;
            this.transportMode = transportMode;
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
