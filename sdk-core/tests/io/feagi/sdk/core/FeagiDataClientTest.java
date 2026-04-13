/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FeagiDataClient}.
 *
 * <p>Uses a {@link StubFeagiAgentClient} — no native library required.
 */
class FeagiDataClientTest {

    // ── Stub ──────────────────────────────────────────────────────────────────

    static class StubFeagiAgentClient implements FeagiAgentClient {
        final List<byte[]> sentPayloads = new ArrayList<>();
        byte[] nextMotorBytes = null; // null = no pending data

        @Override public void connect()    {}
        @Override public void disconnect() {}
        @Override public void close()      {}
        @Override public boolean isConnected() { return true; }

        @Override
        public void sendSensoryBytes(byte[] payload) {
            sentPayloads.add(payload.clone());
        }

        @Override
        public byte[] pollMotorBytes() {
            byte[] result = nextMotorBytes;
            nextMotorBytes = null; // consume once
            return result;
        }
    }

    StubFeagiAgentClient stub;
    FeagiDataClient data;

    @BeforeEach
    void setUp() {
        stub = new StubFeagiAgentClient();
        data = new FeagiDataClient(stub);
    }

    // ── sendSensoryData (single channel) ─────────────────────────────────────

    @Test
    void sendSensoryData_singleChannel_callsTransportOnce() {
        data.sendSensoryData("i__inf", List.of(NeuronPotential.of(0, 1.0f)));
        assertEquals(1, stub.sentPayloads.size());
    }

    @Test
    void sendSensoryData_singleChannel_payloadDecodesCorrectly() {
        List<NeuronPotential> neurons = List.of(
                NeuronPotential.of(3, 0.75f),
                NeuronPotential.of(7, 0.25f));
        data.sendSensoryData("i__inf", neurons);

        Map<String, List<NeuronPotential>> decoded =
                XyzpCodec.decodeContainer(stub.sentPayloads.get(0));
        assertEquals(1, decoded.size());
        assertEquals(neurons, decoded.get("i__inf"));
    }

    @Test
    void sendSensoryData_emptyNeuronList_sendsZeroLengthValue() {
        data.sendSensoryData("i__inf", List.of());

        Map<String, List<NeuronPotential>> decoded =
                XyzpCodec.decodeContainer(stub.sentPayloads.get(0));
        assertTrue(decoded.get("i__inf").isEmpty());
    }

    @Test
    void sendSensoryData_nullChannelName_throws() {
        assertThrows(NullPointerException.class,
                () -> data.sendSensoryData(null, List.of()));
    }

    @Test
    void sendSensoryData_nullNeurons_throws() {
        assertThrows(NullPointerException.class,
                () -> data.sendSensoryData("i__inf", null));
    }

    // ── sendSensoryData (multi-channel) ───────────────────────────────────────

    @Test
    void sendSensoryData_multiChannel_singleTransportCall() {
        Map<String, List<NeuronPotential>> channels = new LinkedHashMap<>();
        channels.put("i__inf", List.of(NeuronPotential.of(0, 1.0f)));
        channels.put("i__bat", List.of(NeuronPotential.of(1, 0.8f)));
        data.sendSensoryData(channels);

        assertEquals(1, stub.sentPayloads.size(), "Multi-channel must be one transport call");
    }

    @Test
    void sendSensoryData_multiChannel_payloadDecodesAllChannels() {
        Map<String, List<NeuronPotential>> channels = new LinkedHashMap<>();
        channels.put("i__inf", List.of(NeuronPotential.of(0, 1.0f)));
        channels.put("i__bat", List.of(NeuronPotential.of(1, 0.5f)));
        data.sendSensoryData(channels);

        Map<String, List<NeuronPotential>> decoded =
                XyzpCodec.decodeContainer(stub.sentPayloads.get(0));
        assertEquals(channels, decoded);
    }

    @Test
    void sendSensoryData_emptyMap_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> data.sendSensoryData(Map.of()));
    }

    // ── pollMotorData ─────────────────────────────────────────────────────────

    @Test
    void pollMotorData_noDataPending_returnsNull() {
        stub.nextMotorBytes = null;
        assertNull(data.pollMotorData());
    }

    @Test
    void pollMotorData_emptyFrame_returnsEmptyMap() {
        stub.nextMotorBytes = new byte[0];
        Map<String, List<NeuronPotential>> result = data.pollMotorData();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void pollMotorData_decodesMotorFrame() {
        List<NeuronPotential> motorNeurons = List.of(
                NeuronPotential.of(0, 0.9f),
                NeuronPotential.of(1, 0.4f));
        stub.nextMotorBytes = XyzpCodec.encodeContainer("o__mot", motorNeurons);

        Map<String, List<NeuronPotential>> result = data.pollMotorData();
        assertNotNull(result);
        assertEquals(motorNeurons, result.get("o__mot"));
    }

    @Test
    void pollMotorData_multiChannel_decodesAll() {
        Map<String, List<NeuronPotential>> motor = new LinkedHashMap<>();
        motor.put("o__mot", List.of(NeuronPotential.of(0, 1.0f)));
        motor.put("o__srv", List.of(NeuronPotential.of(2, 0.6f)));
        stub.nextMotorBytes = XyzpCodec.encodeContainer(motor);

        Map<String, List<NeuronPotential>> result = data.pollMotorData();
        assertEquals(motor, result);
    }

    @Test
    void pollMotorData_malformedBytes_throwsFeagiSdkException() {
        stub.nextMotorBytes = new byte[]{0x00, 0x00, 0x00}; // too short for key length prefix
        assertThrows(FeagiSdkException.class, () -> data.pollMotorData());
    }

    @Test
    void pollMotorData_isNonBlocking_consumesOnce() {
        stub.nextMotorBytes = XyzpCodec.encodeContainer("o__mot",
                List.of(NeuronPotential.of(0, 1.0f)));

        assertNotNull(data.pollMotorData()); // first poll returns data
        assertNull(data.pollMotorData());    // second poll returns null — non-blocking
    }

    // ── pollMotorData(channelName) ────────────────────────────────────────────

    @Test
    void pollMotorData_byChannel_returnsOnlyNamedChannel() {
        Map<String, List<NeuronPotential>> motor = new LinkedHashMap<>();
        motor.put("o__mot", List.of(NeuronPotential.of(0, 1.0f)));
        motor.put("o__srv", List.of(NeuronPotential.of(1, 0.5f)));
        stub.nextMotorBytes = XyzpCodec.encodeContainer(motor);

        List<NeuronPotential> result = data.pollMotorData("o__mot");
        assertEquals(List.of(NeuronPotential.of(0, 1.0f)), result);
    }

    @Test
    void pollMotorData_byChannel_missingChannel_returnsEmptyList() {
        stub.nextMotorBytes = XyzpCodec.encodeContainer("o__mot",
                List.of(NeuronPotential.of(0, 1.0f)));

        List<NeuronPotential> result = data.pollMotorData("o__srv");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void pollMotorData_byChannel_noDataPending_returnsNull() {
        stub.nextMotorBytes = null;
        assertNull(data.pollMotorData("o__mot"));
    }

    // ── client() pass-through ─────────────────────────────────────────────────

    @Test
    void client_returnsUnderlyingClient() {
        assertSame(stub, data.client());
    }

    // ── XYZ coordinate integration ────────────────────────────────────────────

    @Test
    void xyzCoordinateRoundTrip_throughSendAndPoll() {
        // Build sensory payload using cortical coordinates
        int width = 5, height = 5;
        List<NeuronPotential> neurons = List.of(
                NeuronPotential.of(XyzpCodec.toFlatId(0, 0, 0, width, height), 1.0f),
                NeuronPotential.of(XyzpCodec.toFlatId(2, 3, 1, width, height), 0.5f));
        data.sendSensoryData("camera", neurons);

        // Simulate FEAGI echoing it back as motor data
        stub.nextMotorBytes = stub.sentPayloads.get(0);
        Map<String, List<NeuronPotential>> motor = data.pollMotorData();

        assertEquals(neurons, motor.get("camera"));

        // Verify coordinate reconstruction
        int[] xyz = XyzpCodec.fromFlatId(motor.get("camera").get(1).neuronId(), width, height);
        assertArrayEquals(new int[]{2, 3, 1}, xyz);
    }
}
