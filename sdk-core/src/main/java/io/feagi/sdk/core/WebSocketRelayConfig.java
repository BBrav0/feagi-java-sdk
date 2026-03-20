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
     * @param bindHost host to bind on (e.g. "127.0.0.1", "0.0.0.0"); non-null, non-blank.
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
     * Create a WebSocket relay configuration using {@link Duration} for ping timing.
     *
     * @param bindHost host to bind on; non-null, non-blank
     * @param bindPort port to bind on (1-65535)
     * @param embodimentId agent/embodiment identifier; non-null, non-empty
     * @param maxMessageSizeBytes maximum WebSocket message size in bytes; must be > 0
     * @param pingInterval ping interval ({@code Duration.ZERO} disables); non-null, must be >= 0
     * @param pingTimeout ping timeout; non-null, must be > 0
     * @return a new WebSocketRelayConfig instance
     */
    public static WebSocketRelayConfig of(
            String bindHost,
            int bindPort,
            String embodimentId,
            long maxMessageSizeBytes,
            Duration pingInterval,
            Duration pingTimeout
    ) {
        WebSocketConfigValidation.requireNonNegativeDuration(pingInterval, "pingInterval");
        WebSocketConfigValidation.rejectSubMillisecondNonZeroDuration(pingInterval, "pingInterval");
        WebSocketConfigValidation.requirePositiveDurationRepresentableAsMillis(pingTimeout, "pingTimeout");
        return new WebSocketRelayConfig(
                bindHost,
                bindPort,
                embodimentId,
                maxMessageSizeBytes,
                pingInterval.toMillis(),
                pingTimeout.toMillis()
        );
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
     * Return the ping interval as a {@link Duration} ({@code Duration.ZERO} = disabled).
     */
    public Duration pingInterval() {
        return Duration.ofMillis(pingIntervalMs);
    }

    /**
     * Return the ping timeout in milliseconds.
     */
    public long pingTimeoutMs() {
        return pingTimeoutMs;
    }

    /**
     * Return the ping timeout as a {@link Duration}.
     */
    public Duration pingTimeout() {
        return Duration.ofMillis(pingTimeoutMs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WebSocketRelayConfig that = (WebSocketRelayConfig) o;
        return bindPort == that.bindPort &&
               maxMessageSizeBytes == that.maxMessageSizeBytes &&
               pingIntervalMs == that.pingIntervalMs &&
               pingTimeoutMs == that.pingTimeoutMs &&
               Objects.equals(bindHost, that.bindHost) &&
               Objects.equals(embodimentId, that.embodimentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bindHost, bindPort, embodimentId, maxMessageSizeBytes,
                           pingIntervalMs, pingTimeoutMs);
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
