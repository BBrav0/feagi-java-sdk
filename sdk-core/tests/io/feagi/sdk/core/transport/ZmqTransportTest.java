/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core.transport;

import io.feagi.sdk.core.AgentCapabilities;
import io.feagi.sdk.core.AgentConfig;
import io.feagi.sdk.core.AgentType;
import io.feagi.sdk.core.FeagiEndpoints;
import io.feagi.sdk.core.MotorCapability;
import io.feagi.sdk.core.MotorSocketConfig;
import io.feagi.sdk.core.MotorUnit;
import io.feagi.sdk.core.MotorUnitSpec;
import io.feagi.sdk.core.SensorySocketConfig;
import io.feagi.sdk.core.VisionCapability;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ZmqTransport.
 */
public class ZmqTransportTest {

    /**
     * Registration endpoint is required by {@link FeagiEndpoints} / {@link AgentConfig} validation
     * only; {@link ZmqTransport} does not open this socket in these tests.
     */
    private static final String REGISTRATION_PLACEHOLDER = "tcp://127.0.0.1:30001";

    private static final Duration ONE_SECOND = Duration.ofSeconds(1);
    private static final SensorySocketConfig DEFAULT_SENSORY_SOCKET = new SensorySocketConfig(1, 0, false);
    private static final MotorSocketConfig DEFAULT_MOTOR_SOCKET = new MotorSocketConfig(1, 0);

    private ZContext feagiContext;
    private ZMQ.Socket mockFeagiSensorySocket;
    private ZMQ.Socket mockFeagiMotorSocket;
    private String sensoryEndpoint;
    private String motorEndpoint;

    @BeforeEach
    public void setup() {
        feagiContext = new ZContext();

        mockFeagiSensorySocket = feagiContext.createSocket(SocketType.PULL);
        int sensoryPort = mockFeagiSensorySocket.bindToRandomPort("tcp://127.0.0.1");
        sensoryEndpoint = "tcp://127.0.0.1:" + sensoryPort;

        mockFeagiMotorSocket = feagiContext.createSocket(SocketType.PUSH);
        int motorPort = mockFeagiMotorSocket.bindToRandomPort("tcp://127.0.0.1");
        motorEndpoint = "tcp://127.0.0.1:" + motorPort;
    }

    @AfterEach
    public void teardown() {
        if (mockFeagiSensorySocket != null) {
            mockFeagiSensorySocket.close();
        }
        if (mockFeagiMotorSocket != null) {
            mockFeagiMotorSocket.close();
        }
        if (feagiContext != null) {
            feagiContext.close();
        }
    }

    private AgentCapabilities bothCapabilities() {
        return AgentCapabilities.builder()
                .vision(VisionCapability.fromTargetArea("camera", 640, 480, 3, "i_vision"))
                .motor(MotorCapability.fromUnits(
                        "servo", 1, List.of(new MotorUnitSpec(MotorUnit.ROTARY_MOTOR, 0))))
                .build();
    }

    private AgentConfig bothAgentConfig() {
        FeagiEndpoints endpoints = new FeagiEndpoints(
                REGISTRATION_PLACEHOLDER,
                sensoryEndpoint,
                motorEndpoint,
                null,
                null);
        return new AgentConfig(
                "agent1",
                AgentType.BOTH,
                endpoints,
                bothCapabilities(),
                ONE_SECOND,
                ONE_SECOND,
                3,
                ONE_SECOND,
                DEFAULT_SENSORY_SOCKET,
                DEFAULT_MOTOR_SOCKET);
    }

    private AgentConfig motorOnlyAgentConfig() {
        FeagiEndpoints endpoints = new FeagiEndpoints(
                REGISTRATION_PLACEHOLDER,
                null,
                motorEndpoint,
                null,
                null);
        AgentCapabilities capabilities = AgentCapabilities.builder()
                .motor(MotorCapability.fromUnits(
                        "servo", 1, List.of(new MotorUnitSpec(MotorUnit.ROTARY_MOTOR, 0))))
                .build();
        return new AgentConfig(
                "motor-agent",
                AgentType.MOTOR,
                endpoints,
                capabilities,
                ONE_SECOND,
                ONE_SECOND,
                3,
                ONE_SECOND,
                null,
                DEFAULT_MOTOR_SOCKET);
    }

    private AgentConfig sensoryOnlyAgentConfig() {
        FeagiEndpoints endpoints = new FeagiEndpoints(
                REGISTRATION_PLACEHOLDER,
                sensoryEndpoint,
                null,
                null,
                null);
        AgentCapabilities capabilities = AgentCapabilities.builder()
                .vision(VisionCapability.fromTargetArea("camera", 640, 480, 3, "i_vision"))
                .build();
        return new AgentConfig(
                "sensory-agent",
                AgentType.SENSORY,
                endpoints,
                capabilities,
                ONE_SECOND,
                ONE_SECOND,
                3,
                ONE_SECOND,
                DEFAULT_SENSORY_SOCKET,
                null);
    }

    @Test
    public void sendSensoryBytes_deliversPayloadToFeagi() throws Exception {
        mockFeagiSensorySocket.setReceiveTimeOut(1000);
        try (ZmqTransport transport = new ZmqTransport(bothAgentConfig())) {
            byte[] payload = "sensory_data".getBytes();
            // Jeromq connect is asynchronous; in a busy test run the first send can be dropped
            // with DONTWAIT before the PUSH/PULL handshake is ready. Retry briefly for stability.
            byte[] receivedBytes = null;
            for (int attempt = 0; attempt < 10 && receivedBytes == null; attempt++) {
                transport.sendSensoryBytes(payload);
                receivedBytes = mockFeagiSensorySocket.recv(ZMQ.DONTWAIT);
                if (receivedBytes == null) {
                    Thread.sleep(10);
                }
            }

            assertNotNull(receivedBytes, "FEAGI sensory socket should receive payload");
            assertArrayEquals(payload, receivedBytes);
        } finally {
            // Restore default blocking so other tests (or refactored teardown order) never inherit a
            // 1s timeout on the shared mock socket.
            mockFeagiSensorySocket.setReceiveTimeOut(-1);
        }
    }

    @Test
    public void pollMotorBytes_returnsPayloadFromFeagi() throws Exception {
        try (ZmqTransport transport = new ZmqTransport(bothAgentConfig())) {
            transport.setMotorReceiveTimeoutMsForTest(1000);
            try {
                byte[] payload = "motor_data".getBytes();
                boolean sent = mockFeagiMotorSocket.send(payload);
                assertTrue(sent, "Mock FEAGI motor socket should send successfully");

                byte[] receivedBytes = transport.recvMotorBytesBlockingForTest();

                assertNotNull(receivedBytes, "Agent motor socket should receive payload");
                assertArrayEquals(payload, receivedBytes);
            } finally {
                transport.setMotorReceiveTimeoutMsForTest(-1);
            }
        }
    }

    @Test
    public void sendSensoryBytes_onMotorOnlyAgent_throwsIllegalStateException() {
        try (ZmqTransport transport = new ZmqTransport(motorOnlyAgentConfig())) {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> transport.sendSensoryBytes(new byte[] {1}));
            assertTrue(ex.getMessage().contains("MOTOR"));
        }
    }

    @Test
    public void pollMotorBytes_onSensoryOnlyAgent_throwsIllegalStateException() {
        try (ZmqTransport transport = new ZmqTransport(sensoryOnlyAgentConfig())) {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    transport::pollMotorBytes);
            assertTrue(ex.getMessage().contains("SENSORY"));
        }
    }

    @Test
    public void sendSensoryBytes_and_pollMotorBytes_afterClose_throwIllegalStateException() {
        ZmqTransport transport = new ZmqTransport(bothAgentConfig());
        transport.close();

        IllegalStateException sendEx = assertThrows(
                IllegalStateException.class,
                () -> transport.sendSensoryBytes(new byte[] {1}));
        assertTrue(sendEx.getMessage().contains("closed"));

        IllegalStateException pollEx = assertThrows(
                IllegalStateException.class,
                transport::pollMotorBytes);
        assertTrue(pollEx.getMessage().contains("closed"));
    }

    @Test
    public void close_isIdempotent() {
        ZmqTransport transport = new ZmqTransport(bothAgentConfig());
        assertDoesNotThrow(() -> {
            transport.close();
            transport.close();
        });
    }

    @Test
    public void sendSensoryBytes_nullPayload_isNoOp() {
        try (ZmqTransport transport = new ZmqTransport(bothAgentConfig())) {
            assertDoesNotThrow(() -> transport.sendSensoryBytes(null));
            // No ZMQ frame is sent for null, so a single DONTWAIT recv cannot race undelivered data
            // (unlike the happy-path test, which waits for delivery).
            byte[] received = mockFeagiSensorySocket.recv(ZMQ.DONTWAIT);
            assertNull(received, "Null payload must not enqueue a ZMQ frame");
        }
    }

    @Test
    public void sendSensoryBytes_emptyPayload_isNoOp() {
        try (ZmqTransport transport = new ZmqTransport(bothAgentConfig())) {
            assertDoesNotThrow(() -> transport.sendSensoryBytes(new byte[0]));
            byte[] received = mockFeagiSensorySocket.recv(ZMQ.DONTWAIT);
            assertNull(received, "Empty payload must not enqueue a ZMQ frame");
        }
    }

    /**
     * {@link AgentConfig} normally rejects BOTH without a sensory URI; the package-private
     * constructor mirrors the public one so the defensive "missing endpoint" path stays covered.
     */
    @Test
    public void constructor_missingSensoryUri_opensMotorOnly_sendSensoryThrows() {
        FeagiEndpoints endpoints = new FeagiEndpoints(
                REGISTRATION_PLACEHOLDER,
                null,
                motorEndpoint,
                null,
                null);
        try (ZmqTransport transport = new ZmqTransport(
                AgentType.BOTH,
                endpoints,
                DEFAULT_SENSORY_SOCKET,
                DEFAULT_MOTOR_SOCKET)) {
            assertThrows(IllegalStateException.class, () -> transport.sendSensoryBytes(new byte[] {1}));

            transport.setMotorReceiveTimeoutMsForTest(1000);
            try {
                byte[] payload = "motor_still_works".getBytes();
                assertTrue(mockFeagiMotorSocket.send(payload));
                assertArrayEquals(payload, transport.recvMotorBytesBlockingForTest());
            } finally {
                transport.setMotorReceiveTimeoutMsForTest(-1);
            }
        }
    }
}
