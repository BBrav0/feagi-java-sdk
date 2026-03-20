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
 *
 * <p>For complex configurations with many parameters, prefer {@link #builder}.
 *
 * <p><strong>Timeouts vs transport:</strong> {@link #connectionTimeout} limits SDK-level registration
 * and agent requests. When {@link #transportConfig} is a {@link WebSocketClientConfig},
 * {@link WebSocketClientConfig#connectionTimeoutMs} applies separately to the relay socket connect.
 * Neither value overrides the other.
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
     * @param connectionTimeout SDK-level connection timeout for registration and agent requests; non-null
     *                          and positive. Independent of {@link WebSocketClientConfig#connectionTimeoutMs}
     *                          when using client transport.
     * @param registrationRetries registration retry attempts
     * @param retryBackoff backoff between registration retries; may be {@code null} only when
     *                     {@code registrationRetries == 0} (treated as {@link Duration#ZERO}).
     *                     When {@code registrationRetries > 0}, must be non-null and positive.
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
        this.agentId = agentId;
        this.agentType = agentType;
        this.endpoints = endpoints;
        this.capabilities = capabilities;
        this.heartbeatInterval = heartbeatInterval;
        this.connectionTimeout = connectionTimeout;
        this.registrationRetries = registrationRetries;
        if (registrationRetries < 0) {
            throw new IllegalArgumentException("registrationRetries must be >= 0");
        }
        this.retryBackoff = resolveRetryBackoff(retryBackoff, registrationRetries);
        this.transportConfig = transportConfig;

        validate();
    }

    private WebSocketAgentConfig(Builder builder) {
        this.agentId = builder.agentId;
        this.agentType = builder.agentType;
        this.endpoints = builder.endpoints;
        this.capabilities = builder.capabilities;
        this.heartbeatInterval = builder.heartbeatInterval;
        this.connectionTimeout = builder.connectionTimeout;
        this.registrationRetries = builder.registrationRetries;
        if (builder.registrationRetries < 0) {
            throw new IllegalArgumentException("registrationRetries must be >= 0");
        }
        this.retryBackoff = resolveRetryBackoff(builder.retryBackoff, builder.registrationRetries);
        this.transportConfig = builder.transportConfig;

        validate();
    }

    /**
     * When there are no retries, missing {@code retryBackoff} is treated as {@link Duration#ZERO}.
     */
    private static Duration resolveRetryBackoff(Duration retryBackoff, int registrationRetries) {
        if (retryBackoff == null) {
            if (registrationRetries == 0) {
                return Duration.ZERO;
            }
            throw new NullPointerException("retryBackoff must not be null when registrationRetries > 0");
        }
        return retryBackoff;
    }

    /**
     * Shared validation logic for all construction paths.
     *
     * @throws NullPointerException if any required field is null
     * @throws IllegalArgumentException if any validation constraint is violated
     */
    private void validate() {
        Objects.requireNonNull(agentId, "agentId must not be null");
        if (agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        Objects.requireNonNull(agentType, "agentType must not be null");
        Objects.requireNonNull(endpoints, "endpoints must not be null");
        Objects.requireNonNull(capabilities, "capabilities must not be null");
        Objects.requireNonNull(transportConfig, "transportConfig must not be null");

        requireNonNegative(heartbeatInterval, "heartbeatInterval");
        requirePositive(connectionTimeout, "connectionTimeout");

        if (registrationRetries > 0) {
            requirePositive(retryBackoff, "retryBackoff");
        } else {
            requireNonNegative(retryBackoff, "retryBackoff");
        }

        endpoints.validateForAgentType(agentType);
        capabilities.validateForAgentType(agentType);
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
     * Create a new builder for constructing {@code WebSocketAgentConfig} instances.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
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
     * Return the SDK-level connection timeout (registration and agent requests).
     * Differs from {@link WebSocketClientConfig#connectionTimeoutMs} when transport is client mode.
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
     * Return the retry backoff duration (never null; {@link Duration#ZERO} when no retries are configured).
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
    // WebSocketTransportConfig is sealed to WebSocketClientConfig and WebSocketRelayConfig; with Java 21+,
    // this could use an exhaustive switch on the sealed hierarchy for compile-time coverage.
    public boolean isClientMode() {
        if (transportConfig instanceof WebSocketClientConfig) {
            return true;
        }
        if (transportConfig instanceof WebSocketRelayConfig) {
            return false;
        }
        throw new IllegalStateException("Unexpected WebSocketTransportConfig: " + transportConfig.getClass());
    }

    /**
     * Return true if transport is configured for relay (server) mode.
     */
    // See isClientMode() for notes on sealed transport types and Java 21+ pattern matching.
    public boolean isRelayMode() {
        if (transportConfig instanceof WebSocketRelayConfig) {
            return true;
        }
        if (transportConfig instanceof WebSocketClientConfig) {
            return false;
        }
        throw new IllegalStateException("Unexpected WebSocketTransportConfig: " + transportConfig.getClass());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WebSocketAgentConfig that = (WebSocketAgentConfig) o;
        return registrationRetries == that.registrationRetries &&
               Objects.equals(agentId, that.agentId) &&
               agentType == that.agentType &&
               Objects.equals(endpoints, that.endpoints) &&
               Objects.equals(capabilities, that.capabilities) &&
               Objects.equals(heartbeatInterval, that.heartbeatInterval) &&
               Objects.equals(connectionTimeout, that.connectionTimeout) &&
               Objects.equals(retryBackoff, that.retryBackoff) &&
               Objects.equals(transportConfig, that.transportConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agentId, agentType, endpoints, capabilities, heartbeatInterval,
                           connectionTimeout, registrationRetries, retryBackoff, transportConfig);
    }

    @Override
    public String toString() {
        return "WebSocketAgentConfig{" +
               "agentId='" + agentId + '\'' +
               ", agentType=" + agentType +
               ", endpoints=" + endpoints +
               ", capabilities=" + capabilities +
               ", heartbeatInterval=" + heartbeatInterval +
               ", connectionTimeout=" + connectionTimeout +
               ", registrationRetries=" + registrationRetries +
               ", retryBackoff=" + retryBackoff +
               ", transportConfig=" + transportConfig +
               '}';
    }

    /**
     * Builder for constructing {@link WebSocketAgentConfig} instances with a fluent API.
     *
     * <p>This builder helps prevent parameter ordering mistakes when constructing
     * configurations with many parameters, especially when multiple parameters share
     * the same type (for example connectionTimeout and retryBackoff are both Duration values).
     *
     * <p>Required parameters must be set before calling {@link #build}:
     * agentId, agentType, endpoints, capabilities, connectionTimeout, transportConfig.
     * Retry backoff is required when registrationRetries is greater than zero; when retries are zero it may
     * be omitted (defaults to a zero duration).
     */
    public static final class Builder {
        private String agentId;
        private AgentType agentType;
        private WebSocketEndpoints endpoints;
        private AgentCapabilities capabilities;
        private Duration heartbeatInterval = Duration.ZERO;
        private Duration connectionTimeout;
        private int registrationRetries = 0;
        private Duration retryBackoff;
        private WebSocketTransportConfig transportConfig;

        private Builder() {
        }

        /**
         * Set the unique agent identifier.
         *
         * @param agentId unique agent identifier; non-null, non-blank
         * @return this builder
         */
        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        /**
         * Set the agent role.
         *
         * @param agentType agent role (sensory, motor, both, visualization, infrastructure)
         * @return this builder
         */
        public Builder agentType(AgentType agentType) {
            this.agentType = agentType;
            return this;
        }

        /**
         * Set the explicit FEAGI WebSocket endpoints.
         *
         * @param endpoints explicit FEAGI WebSocket endpoints
         * @return this builder
         */
        public Builder endpoints(WebSocketEndpoints endpoints) {
            this.endpoints = endpoints;
            return this;
        }

        /**
         * Set the declared agent capabilities.
         *
         * @param capabilities declared agent capabilities
         * @return this builder
         */
        public Builder capabilities(AgentCapabilities capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        /**
         * Set the heartbeat interval.
         *
         * @param heartbeatInterval heartbeat interval, or zero to disable
         * @return this builder
         */
        public Builder heartbeatInterval(Duration heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
            return this;
        }

        /**
         * Set the SDK-level connection timeout for registration and agent requests.
         *
         * @param connectionTimeout non-null positive duration, independent of relay transport connect timeout
         * @return this builder
         */
        public Builder connectionTimeout(Duration connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        /**
         * Set the registration retry attempts.
         *
         * @param registrationRetries registration retry attempts; defaults to 0
         * @return this builder
         */
        public Builder registrationRetries(int registrationRetries) {
            this.registrationRetries = registrationRetries;
            return this;
        }

        /**
         * Set the retry backoff duration.
         *
         * @param retryBackoff backoff between retries; required and positive when registrationRetries is
         *                     greater than zero. When retries are zero, may be omitted (defaults to zero).
         * @return this builder
         */
        public Builder retryBackoff(Duration retryBackoff) {
            this.retryBackoff = retryBackoff;
            return this;
        }

        /**
         * Set the WebSocket transport mode configuration.
         *
         * @param transportConfig WebSocket transport mode configuration (client or relay)
         * @return this builder
         */
        public Builder transportConfig(WebSocketTransportConfig transportConfig) {
            this.transportConfig = transportConfig;
            return this;
        }

        /**
         * Build the {@link WebSocketAgentConfig} instance.
         *
         * @return a new WebSocketAgentConfig instance
         * @throws NullPointerException if required parameters are null
         * @throws IllegalArgumentException if validation fails
         */
        public WebSocketAgentConfig build() {
            return new WebSocketAgentConfig(this);
        }
    }
}