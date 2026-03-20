/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.time.Duration;
import java.util.Objects;

/**
 * WebSocket client transport configuration.
 *
 * <p>Used when the agent connects as a WebSocket client to a platform-managed relay
 * (e.g. Tauri/Electron BLE relay). No builder needed: all fields are scalar and required.
 *
 * <p><strong>Timeouts vs {@link WebSocketAgentConfig}:</strong> {@link #connectionTimeoutMs} is the
 * transport-layer socket connect timeout for the relay WebSocket client. {@link WebSocketAgentConfig#connectionTimeout}
 * is the SDK-level timeout for registration and other agent requests. They operate at different layers;
 * neither overrides the other.
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
     * @param host relay server host (e.g. "127.0.0.1"); non-null, non-blank
     * @param port relay server port (1-65535)
     * @param embodimentId agent/embodiment identifier; non-null, non-blank
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
        this.host = WebSocketConfigValidation.requireNonEmptyString(host, "host");
        this.port = WebSocketConfigValidation.requireValidPort(port, "port");
        this.embodimentId = WebSocketConfigValidation.requireNonEmptyString(embodimentId, "embodimentId");
        this.connectionTimeoutMs = WebSocketConfigValidation.requirePositive(connectionTimeoutMs, "connectionTimeoutMs");
        this.receiveTimeoutMs = WebSocketConfigValidation.requirePositive(receiveTimeoutMs, "receiveTimeoutMs");
    }

    /**
     * Create a WebSocket client configuration using {@link Duration} for timeouts.
     *
     * <p>This factory method aligns with {@link WebSocketAgentConfig}'s use of {@code Duration},
     * making it easier to compose configurations without manual unit conversion.
     *
     * @param host relay server host (e.g. "127.0.0.1"); non-null, non-blank
     * @param port relay server port (1-65535)
     * @param embodimentId agent/embodiment identifier; non-null, non-blank
     * @param connectionTimeout connection timeout; non-null, must be > 0
     * @param receiveTimeout receive timeout; non-null, must be > 0
     * @return a new WebSocketClientConfig instance
     */
    public static WebSocketClientConfig of(
            String host,
            int port,
            String embodimentId,
            Duration connectionTimeout,
            Duration receiveTimeout
    ) {
        WebSocketConfigValidation.requirePositiveDurationRepresentableAsMillis(
                connectionTimeout, "connectionTimeout");
        WebSocketConfigValidation.requirePositiveDurationRepresentableAsMillis(
                receiveTimeout, "receiveTimeout");
        return new WebSocketClientConfig(
                host,
                port,
                embodimentId,
                connectionTimeout.toMillis(),
                receiveTimeout.toMillis()
        );
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
     * Return the connection timeout as a {@link Duration}.
     */
    public Duration connectionTimeout() {
        return Duration.ofMillis(connectionTimeoutMs);
    }

    /**
     * Return the receive timeout in milliseconds.
     */
    public long receiveTimeoutMs() {
        return receiveTimeoutMs;
    }

    /**
     * Return the receive timeout as a {@link Duration}.
     */
    public Duration receiveTimeout() {
        return Duration.ofMillis(receiveTimeoutMs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WebSocketClientConfig that = (WebSocketClientConfig) o;
        return port == that.port &&
               connectionTimeoutMs == that.connectionTimeoutMs &&
               receiveTimeoutMs == that.receiveTimeoutMs &&
               Objects.equals(host, that.host) &&
               Objects.equals(embodimentId, that.embodimentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, embodimentId, connectionTimeoutMs, receiveTimeoutMs);
    }

    @Override
    public String toString() {
        return "WebSocketClientConfig{" +
               "host='" + host + '\'' +
               ", port=" + port +
               ", embodimentId='" + embodimentId + '\'' +
               ", connectionTimeoutMs=" + connectionTimeoutMs +
               ", receiveTimeoutMs=" + receiveTimeoutMs +
               '}';
    }
}
