/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

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
     * @param bindHost host to bind on (e.g. "127.0.0.1", "0.0.0.0"); non-null, non-empty
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
        this.bindHost = requireNonEmptyString(bindHost, "bindHost");
        this.bindPort = requireValidPort(bindPort, "bindPort");
        this.embodimentId = requireNonEmptyString(embodimentId, "embodimentId");
        this.maxMessageSizeBytes = requirePositive(maxMessageSizeBytes, "maxMessageSizeBytes");
        this.pingIntervalMs = requireNonNegative(pingIntervalMs, "pingIntervalMs");
        this.pingTimeoutMs = requirePositive(pingTimeoutMs, "pingTimeoutMs");
    }

    private static String requireNonEmptyString(String value, String name) {
        if (value == null) {
            throw new NullPointerException(name + " must not be null");
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return value;
    }

    private static int requireValidPort(int value, String name) {
        if (value < 1 || value > 65535) {
            throw new IllegalArgumentException(name + " must be between 1 and 65535, got " + value);
        }
        return value;
    }

    private static long requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return value;
    }

    private static long requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
        return value;
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

    /**
     * Return the ping interval in milliseconds (0 = disabled).
     */
    public long pingIntervalMs() {
        return pingIntervalMs;
    }

    /**
     * Return the ping timeout in milliseconds.
     */
    public long pingTimeoutMs() {
        return pingTimeoutMs;
    }
}
