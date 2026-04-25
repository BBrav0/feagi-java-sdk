/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * High-level send/receive API that encodes sensory data as XYZP and decodes
 * motor data from XYZP, sitting on top of the low-level {@link FeagiAgentClient}.
 *
 * <p>{@code FeagiDataClient} is a thin adapter — it owns no state beyond a reference
 * to the underlying transport client. Lifecycle (connect, close) is managed on the
 * underlying client; this class only handles data encoding/decoding.
 *
 * <h2>Sending sensory data</h2>
 * <pre>{@code
 * FeagiDataClient data = new FeagiDataClient(client);
 *
 * // Single channel
 * data.sendSensoryData("i__inf", List.of(
 *         NeuronPotential.of(0, 1.0f),
 *         NeuronPotential.of(1, 0.5f)));
 *
 * // Multiple channels in one transport call
 * data.sendSensoryData(Map.of(
 *         "i__inf", infraredNeurons,
 *         "i__bat", batteryNeurons));
 * }</pre>
 *
 * <h2>Receiving motor data</h2>
 * <pre>{@code
 * Map<String, List<NeuronPotential>> motorData = data.pollMotorData();
 * if (motorData != null) {
 *     List<NeuronPotential> drive = motorData.getOrDefault("o__mot", List.of());
 *     for (NeuronPotential m : drive) {
 *         applyMotorCommand(m.neuronId(), m.potential());
 *     }
 * }
 * }</pre>
 *
 * <h2>Coordinate helpers</h2>
 * Use {@link XyzpCodec#toFlatId(int, int, int, int, int)} when your sensor data is
 * natively in {@code (x, y, z)} cortical coordinates:
 * <pre>{@code
 * int id = XyzpCodec.toFlatId(x, y, 0, areaWidth, areaHeight);
 * NeuronPotential.of(id, potential);
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * Thread safety follows the underlying {@link FeagiAgentClient}: concurrent calls to
 * {@link #sendSensoryData} and {@link #pollMotorData} are safe if the underlying client
 * permits concurrent send and poll (which {@code NativeFeagiAgentClient} does via its
 * read lock).
 *
 * <h2>Placement</h2>
 * {@code sdk-core/src/main/java/io/feagi/sdk/core/FeagiDataClient.java}
 */
public final class FeagiDataClient {

    private final FeagiAgentClient client;

    /**
     * Create a data client wrapping the given transport client.
     *
     * @param client connected or pre-connect transport client; must not be null
     */
    public FeagiDataClient(FeagiAgentClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    // ── Sensory ───────────────────────────────────────────────────────────────

    /**
     * Encode and send sensory neuron potentials on a single channel.
     *
     * <p>Uses real-time semantics — the underlying transport may drop the frame under
     * backpressure. An empty {@code neurons} list sends a zero-length XYZP payload,
     * which signals "no active neurons on this channel this tick".
     *
     * @param channelName FEAGI sensory channel name (e.g. {@code "i__inf"})
     * @param neurons     neuron potentials to send; must not be null
     * @throws FeagiSdkException     if the underlying send fails
     * @throws IllegalStateException if the client is not connected
     */
    public void sendSensoryData(String channelName, List<NeuronPotential> neurons) {
        Objects.requireNonNull(channelName, "channelName must not be null");
        Objects.requireNonNull(neurons, "neurons must not be null");
        byte[] payload = XyzpCodec.encodeContainer(channelName, neurons);
        client.sendSensoryBytes(payload);
    }

    /**
     * Encode and send sensory neuron potentials across multiple channels in a single
     * transport call.
     *
     * <p>Prefer this over calling {@link #sendSensoryData(String, List)} in a loop
     * when sending multiple modalities at the same logical tick — it produces a single
     * byte-container and a single native send call.
     *
     * @param channels ordered map of channel name → neuron list; must not be null or empty
     * @throws FeagiSdkException     if the underlying send fails
     * @throws IllegalStateException if the client is not connected
     */
    public void sendSensoryData(Map<String, List<NeuronPotential>> channels) {
        Objects.requireNonNull(channels, "channels must not be null");
        if (channels.isEmpty()) {
            throw new IllegalArgumentException(
                    "channels must not be empty — omit the call rather than sending nothing");
        }
        byte[] payload = XyzpCodec.encodeContainer(channels);
        client.sendSensoryBytes(payload);
    }

    // ── Motor ─────────────────────────────────────────────────────────────────

    /**
     * Non-blocking poll for motor data from FEAGI.
     *
     * <p>Decodes the raw XYZP byte-container payload into a map of channel name →
     * neuron potentials. Each entry represents a motor command: the neuron ID encodes
     * the motor index and the potential encodes the commanded power.
     *
     * @return map of channel name → motor neuron potentials if data is available,
     *         or {@code null} if no frame is pending (non-blocking)
     * @throws FeagiSdkException     if the underlying receive or decode fails
     * @throws IllegalStateException if the client is not connected
     */
    public Map<String, List<NeuronPotential>> pollMotorData() {
        byte[] raw = client.pollMotorBytes();
        if (raw == null) return null;
        if (raw.length == 0) return Collections.emptyMap();
        try {
            return XyzpCodec.decodeContainer(raw);
        } catch (IllegalArgumentException e) {
            throw new FeagiSdkException(
                    "Failed to decode motor byte-container payload: " + e.getMessage(), e);
        }
    }

    /**
     * Non-blocking poll for motor data on a single channel.
     *
     * <p>Convenience wrapper that polls all motor data and returns only the named channel.
     * Use {@link #pollMotorData()} when you need all channels to avoid decoding twice.
     *
     * @param channelName the motor channel to extract (e.g. {@code "o__mot"})
     * @return neuron potentials for the channel if a frame was pending and the channel
     *         is present, or {@code null} if no frame was pending
     */
    public List<NeuronPotential> pollMotorData(String channelName) {
        Objects.requireNonNull(channelName, "channelName must not be null");
        Map<String, List<NeuronPotential>> all = pollMotorData();
        if (all == null) return null;
        return all.getOrDefault(channelName, Collections.emptyList());
    }

    // ── Pass-through ──────────────────────────────────────────────────────────

    /**
     * Return the underlying transport client.
     * Use for lifecycle operations ({@code connect()}, {@code close()}) and for
     * raw byte access when the XYZP codec is not appropriate.
     */
    public FeagiAgentClient client() {
        return client;
    }
}
