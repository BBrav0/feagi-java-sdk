/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.time.Duration;
import java.util.Objects;

/**
 * High-level configuration for a FEAGI agent using WebSocket transport.
 *
 * <p>Guardrails:
 * - No hidden defaults for endpoints or timeouts.
 * - Validate all values at construction time.
 */
public final class WebSocketAgentConfig {
    private final String agentId;
    private final AgentType agentType;
    private final WebSocketEndpoints endpoints;
    private final AgentCapabilities capabilities;
    private final Duration heartbeatInterval;
    private final Duration connectionTimeout;
    private final int registrationRetries;
    private final Duration retryBackoff;
    private final WebSocketTransportConfig transportConfig;

    /**
     * Create an immutable WebSocket agent configuration.
     *
     * <p>Validation is performed eagerly to keep behavior deterministic and fail-fast.
     *
     * @param agentId unique agent identifier
     * @param agentType agent role (sensory, motor, both, visualization, infrastructure)
     * @param endpoints explicit FEAGI WebSocket endpoints
     * @param capabilities declared agent capabilities
     * @param heartbeatInterval heartbeat interval (0 disables)
     * @param connectionTimeout connection timeout for requests
     * @param registrationRetries registration retry attempts
     * @param retryBackoff retry backoff duration
     * @param transportConfig WebSocket transport mode configuration (client or relay)
     */
    public WebSocketAgentConfig(
            String agentId,
            AgentType agentType,
            WebSocketEndpoints endpoints,
            AgentCapabilities capabilities,
            Duration heartbeatInterval,
            Duration connectionTimeout,
            int registrationRetries,
            Duration retryBackoff,
            WebSocketTransportConfig transportConfig
    ) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        if (agentId.isEmpty()) {
            throw new IllegalArgumentException("agentId must not be empty");
        }
        this.agentId = agentId;
        this.agentType = Objects.requireNonNull(agentType, "agentType must not be null");
        this.endpoints = Objects.requireNonNull(endpoints, "endpoints must not be null");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities must not be null");

        this.heartbeatInterval = requireNonNegative(heartbeatInterval, "heartbeatInterval");
        this.connectionTimeout = requirePositive(connectionTimeout, "connectionTimeout");

        if (registrationRetries < 0) {
            throw new IllegalArgumentException("registrationRetries must be >= 0");
        }
        this.registrationRetries = registrationRetries;
        this.retryBackoff = requirePositive(retryBackoff, "retryBackoff");
        this.transportConfig = Objects.requireNonNull(transportConfig, "transportConfig must not be null");

        this.endpoints.validateForAgentType(agentType);
        this.capabilities.validateForAgentType(agentType);
    }

    private static Duration requirePositive(Duration v, String name) {
        Objects.requireNonNull(v, name + " must not be null");
        if (v.isZero() || v.isNegative()) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return v;
    }

    private static Duration requireNonNegative(Duration v, String name) {
        Objects.requireNonNull(v, name + " must not be null");
        if (v.isNegative()) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
        return v;
    }

    /**
     * Return the agent identifier.
     */
    public String agentId() {
        return agentId;
    }

    /**
     * Return the configured agent type.
     */
    public AgentType agentType() {
        return agentType;
    }

    /**
     * Return the explicit FEAGI WebSocket endpoints.
     */
    public WebSocketEndpoints endpoints() {
        return endpoints;
    }

    /**
     * Return declared capabilities for this agent.
     */
    public AgentCapabilities capabilities() {
        return capabilities;
    }

    /**
     * Return the heartbeat interval.
     */
    public Duration heartbeatInterval() {
        return heartbeatInterval;
    }

    /**
     * Return the connection timeout.
     */
    public Duration connectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Return the registration retry count.
     */
    public int registrationRetries() {
        return registrationRetries;
    }

    /**
     * Return the retry backoff duration.
     */
    public Duration retryBackoff() {
        return retryBackoff;
    }

    /**
     * Return the WebSocket transport configuration.
     */
    public WebSocketTransportConfig transportConfig() {
        return transportConfig;
    }

    /**
     * Return true if transport is configured for client (relay client) mode.
     */
    public boolean isClientMode() {
        return transportConfig instanceof WebSocketClientConfig;
    }

    /**
     * Return true if transport is configured for relay (server) mode.
     */
    public boolean isRelayMode() {
        return transportConfig instanceof WebSocketRelayConfig;
    }
}
