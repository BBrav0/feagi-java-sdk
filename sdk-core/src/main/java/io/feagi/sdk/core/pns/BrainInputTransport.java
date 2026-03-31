/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core.pns;

import io.feagi.sdk.core.TransportMode;

/**
 * Transport contract for {@link BrainInput}.
 */
public interface BrainInputTransport extends AutoCloseable {

    /**
     * Connect to the FEAGI host/port using the selected transport.
     */
    void connect(String host, int port, TransportMode transportMode);

    /**
     * Send a single encoded payload to FEAGI.
     */
    void send(byte[] payload);

    /**
     * Release transport-specific resources.
     */
    @Override
    void close();
}
