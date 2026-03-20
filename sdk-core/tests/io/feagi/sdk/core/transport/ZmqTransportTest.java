/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core.transport;

import io.feagi.sdk.core.AgentCapabilities;
import io.feagi.sdk.core.AgentConfig;
import io.feagi.sdk.core.AgentType;
import io.feagi.sdk.core.FeagiEndpoints;
import io.feagi.sdk.core.SensorySocketConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.feagi.sdk.core.MotorCapability;
import io.feagi.sdk.core.VisionCapability;
import io.feagi.sdk.core.MotorUnitSpec;
import io.feagi.sdk.core.MotorUnit;
import java.util.List;

/**
 * Unit tests for ZmqTransport.
 */
public class ZmqTransportTest {

    private ZContext feagiContext;
    private ZMQ.Socket mockFeagiSensorySocket;
    private ZMQ.Socket mockFeagiMotorSocket;
    private String sensoryEndpoint;
    private String motorEndpoint;

    @BeforeEach
    public void setup() {
        feagiContext = new ZContext();
        
        // Mock FEAGI binds to random ports
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

    @Test
    public void testSendSensoryBytes() throws Exception {
        FeagiEndpoints endpoints = new FeagiEndpoints(
                "tcp://127.0.0.1:30001",
                sensoryEndpoint,
                motorEndpoint,
                null,
                null
        );

        AgentCapabilities capabilities = AgentCapabilities.builder()
                .vision(VisionCapability.fromTargetArea("camera", 640, 480, 3, "i_vision"))
                .motor(MotorCapability.fromUnits("servo", 1, List.of(new MotorUnitSpec(MotorUnit.ROTARY_MOTOR, 0))))
                .build();

        AgentConfig config = new AgentConfig(
                "agent1",
                AgentType.BOTH,
                endpoints,
                capabilities,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                3,
                Duration.ofSeconds(1),
                new SensorySocketConfig(1, 0, false)
        );

        try (ZmqTransport transport = new ZmqTransport(config)) {
            byte[] payload = "sensory_data".getBytes();
            transport.sendSensoryBytes(payload);

            // Mock FEAGI should receive it
            // Need a bit of time for ZMQ to deliver
            byte[] receivedBytes = mockFeagiSensorySocket.recv(ZMQ.DONTWAIT);
            
            // Retry a few times in case of slight delay
            for (int i = 0; i < 10 && receivedBytes == null; i++) {
                Thread.sleep(100);
                receivedBytes = mockFeagiSensorySocket.recv(ZMQ.DONTWAIT);
            }
            
            assertNotNull(receivedBytes, "FEAGI sensory socket should receive payload");
            assertArrayEquals(payload, receivedBytes);
        }
    }

    @Test
    public void testPollMotorBytes() throws Exception {
        FeagiEndpoints endpoints = new FeagiEndpoints(
                "tcp://127.0.0.1:30001",
                sensoryEndpoint,
                motorEndpoint,
                null,
                null
        );

        AgentCapabilities capabilities = AgentCapabilities.builder()
                .vision(VisionCapability.fromTargetArea("camera", 640, 480, 3, "i_vision"))
                .motor(MotorCapability.fromUnits("servo", 1, List.of(new MotorUnitSpec(MotorUnit.ROTARY_MOTOR, 0))))
                .build();

        AgentConfig config = new AgentConfig(
                "agent1",
                AgentType.BOTH,
                endpoints,
                capabilities,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                3,
                Duration.ofSeconds(1),
                new SensorySocketConfig(1, 0, false)
        );

        try (ZmqTransport transport = new ZmqTransport(config)) {
            // Mock FEAGI sends motor data
            byte[] payload = "motor_data".getBytes();
            boolean sent = mockFeagiMotorSocket.send(payload);
            assertTrue(sent, "Mock FEAGI motor socket should send successfully");

            // Agent should poll it
            byte[] receivedBytes = transport.pollMotorBytes();
            
            // Retry a few times in case of slight delay
            for (int i = 0; i < 10 && receivedBytes == null; i++) {
                Thread.sleep(100);
                receivedBytes = transport.pollMotorBytes();
            }

            assertNotNull(receivedBytes, "Agent motor socket should receive payload");
            assertArrayEquals(payload, receivedBytes);
        }
    }
}
