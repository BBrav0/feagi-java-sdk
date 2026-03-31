/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core.pns;

import java.util.Objects;

/**
 * Explicit configuration for {@link BrainInput}.
 *
 * <p>There are no implicit defaults: callers must provide all values required by the transport.
 */
public final class BrainInputConfig {
    private final String host;
    private final int port;
    private final BrainInputTransportType transport;

    /**
     * Create a transport configuration.
     *
     * @param host FEAGI host
     * @param port FEAGI input port
     * @param transport transport name
     */
    public BrainInputConfig(String host, int port, BrainInputTransportType transport) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be null or blank");
        }
        if (port <= 0) {
            throw new IllegalArgumentException("port must be > 0");
        }
        this.host = host;
        this.port = port;
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public BrainInputTransportType transport() {
        return transport;
    }
}