/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

/**
 * WebSocket client transport configuration.
 *
 * <p>Used when the agent connects as a WebSocket client to a platform-managed relay
 * (e.g. Tauri/Electron BLE relay). No builder needed: all fields are scalar and required.
 */
public final class WebSocketClientConfig implements WebSocketTransportConfig {
    private final String host;
    private final int port;
    private final String embodimentId;
    private final long connectionTimeoutMs;
    private final long receiveTimeoutMs;

    /**
     * Create a WebSocket client configuration.
     *
     * @param host relay server host (e.g. "127.0.0.1"); non-null, non-empty
     * @param port relay server port (1-65535)
     * @param embodimentId agent/embodiment identifier; non-null, non-empty
     * @param connectionTimeoutMs connection timeout in milliseconds; must be > 0
     * @param receiveTimeoutMs receive timeout in milliseconds; must be > 0
     */
    public WebSocketClientConfig(
            String host,
            int port,
            String embodimentId,
            long connectionTimeoutMs,
            long receiveTimeoutMs
    ) {
        this.host = requireNonEmptyString(host, "host");
        this.port = requireValidPort(port, "port");
        this.embodimentId = requireNonEmptyString(embodimentId, "embodimentId");
        this.connectionTimeoutMs = requirePositive(connectionTimeoutMs, "connectionTimeoutMs");
        this.receiveTimeoutMs = requirePositive(receiveTimeoutMs, "receiveTimeoutMs");
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

    /**
     * Return the relay server host.
     */
    public String host() {
        return host;
    }

    /**
     * Return the relay server port.
     */
    public int port() {
        return port;
    }

    /**
     * Return the embodiment identifier.
     */
    public String embodimentId() {
        return embodimentId;
    }

    /**
     * Return the connection timeout in milliseconds.
     */
    public long connectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    /**
     * Return the receive timeout in milliseconds.
     */
    public long receiveTimeoutMs() {
        return receiveTimeoutMs;
    }
}
