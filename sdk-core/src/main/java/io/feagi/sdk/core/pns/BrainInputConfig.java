/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core.pns;

/**
 * Explicit configuration for {@link BrainInput}.
 *
 * <p>There are no implicit defaults: callers must provide all values required by the transport.
 */
public record BrainInputConfig(String host, int port, TransportMode transport) {

    /**
     * Create a transport configuration.
     *
     * @param host FEAGI host
     * @param port FEAGI input port
     * @param transport transport name
     */
    public BrainInputConfig {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be null or blank");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        if (transport == null) {
            throw new IllegalArgumentException("transport must not be null");
        }
    }
}
