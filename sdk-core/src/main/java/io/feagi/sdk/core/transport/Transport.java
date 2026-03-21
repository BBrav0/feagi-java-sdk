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
     * @param payload payload bytes to send; {@code null} or a zero-length array is a no-op
     *     (implementations may log skipped sends at {@link java.util.logging.Level#FINE})
     */
    void sendSensoryBytes(byte[] payload);

    /**
     * Non-blocking receive of FEAGI motor payload as byte-container bytes.
     *
     * @return payload bytes if available, otherwise {@code null}
     */
    byte[] pollMotorBytes();

    /** {@inheritDoc} */
    @Override
    void close();
}
