/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.time.Duration;
import java.util.Objects;

/**
 * WebSocket relay (server) transport configuration.
 *
 * <p>Used when the agent acts as a WebSocket server; browsers or platform processes connect to it.
 * No builder needed: all fields are scalar and required.
 */
public final class WebSocketRelayConfig implements WebSocketTransportConfig {
    private final String bindHost;
    private final int bindPort;
    private final String embodimentId;
    private final long maxMessageSizeBytes;
    private final long pingIntervalMs;
    private final long pingTimeoutMs;

    /**
     * Create a WebSocket relay configuration.
     *
     * @param bindHost host to bind on (e.g. "127.0.0.1", "0.0.0.0"); non-null, non-empty.
     *                 <p><strong>Security note:</strong> Using "0.0.0.0" binds on all network
     *                 interfaces, which may expose the relay on public networks in production
     *                 deployments. Use "127.0.0.1" for localhost-only binding in development.
     * @param bindPort port to bind on (1-65535)
     * @param embodimentId agent/embodiment identifier; non-null, non-empty
     * @param maxMessageSizeBytes maximum WebSocket message size in bytes; must be > 0
     * @param pingIntervalMs ping interval in milliseconds (0 disables); must be >= 0
     * @param pingTimeoutMs ping timeout in milliseconds; must be > 0
     */
    public WebSocketRelayConfig(
            String bindHost,
            int bindPort,
            String embodimentId,
            long maxMessageSizeBytes,
            long pingIntervalMs,
            long pingTimeoutMs
    ) {
        this.bindHost = WebSocketConfigValidation.requireNonEmptyString(bindHost, "bindHost");
        this.bindPort = WebSocketConfigValidation.requireValidPort(bindPort, "bindPort");
        this.embodimentId = WebSocketConfigValidation.requireNonEmptyString(embodimentId, "embodimentId");
        this.maxMessageSizeBytes = WebSocketConfigValidation.requirePositive(maxMessageSizeBytes, "maxMessageSizeBytes");
        this.pingIntervalMs = WebSocketConfigValidation.requireNonNegative(pingIntervalMs, "pingIntervalMs");
        this.pingTimeoutMs = WebSocketConfigValidation.requirePositive(pingTimeoutMs, "pingTimeoutMs");
    }

    /**
     * Return the bind host.
     */
    public String bindHost() {
        return bindHost;
    }

    /**
     * Return the bind port.
     */
    public int bindPort() {
        return bindPort;
    }

    /**
     * Return the embodiment identifier.
     */
    public String embodimentId() {
        return embodimentId;
    }

    /**
     * Return the maximum message size in bytes.
     */
    public long maxMessageSizeBytes() {
        return maxMessageSizeBytes;
    }


    @Override
    public String toString() {
        return "WebSocketRelayConfig{" +
               "bindHost='" + bindHost + '\'' +
               ", bindPort=" + bindPort +
               ", embodimentId='" + embodimentId + '\'' +
               ", maxMessageSizeBytes=" + maxMessageSizeBytes +
               ", pingIntervalMs=" + pingIntervalMs +
               ", pingTimeoutMs=" + pingTimeoutMs +
               '}';
    }
}