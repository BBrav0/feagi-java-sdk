/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core.pns;

/**
 * Transport contract for {@link BrainInput}.
 */
public interface BrainInputTransport {

    /**
     * Connect to the FEAGI host/port using the selected transport.
     */
    void connect(String host, int port, BrainInputTransportType transportType);

    /**
     * Send a single encoded payload to FEAGI.
     */
    void send(byte[] payload);

    /**
     * Release transport-specific resources.
     */
    default void close() {
    }
}