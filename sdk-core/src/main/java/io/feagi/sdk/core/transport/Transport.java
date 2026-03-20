/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core.transport;

/**
 * Transport interface for FEAGI agents to exchange data with FEAGI.
 */
public interface Transport extends AutoCloseable {

    /**
     * Send already-serialized FEAGI byte-container sensory payload (real-time semantics).
     *
     * <p>No implicit buffering: underlying implementation may drop on backpressure.
     *
     * @param payload payload bytes to send
     */
    void sendSensoryBytes(byte[] payload);

    /**
     * Non-blocking receive of FEAGI motor payload as byte-container bytes.
     *
     * @return payload bytes if available, otherwise {@code null}
     */
    byte[] pollMotorBytes();

    /**
     * Close the transport and release related resources.
     */
    @Override
    void close();
}
