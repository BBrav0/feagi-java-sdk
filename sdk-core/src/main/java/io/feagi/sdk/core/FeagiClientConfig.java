/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Explicit connection parameters for a FEAGI agent client.
 *
 * <p>Separates <em>connection policy</em> (endpoints, timeouts, retries, auth) from
 * <em>agent identity and capability</em> ({@link AgentConfig}). This mirrors the Python
 * SDK's {@code connect()} parameters and satisfies the requirement that all connection
 * params be required with no SDK defaults.
 *
 * <h2>Endpoint schemes</h2>
 * Endpoints accept {@code tcp://} (networked) and {@code ipc://} (local inter-process,
 * useful for integration tests). {@code inproc://} is not supported because it requires
 * sharing a ZMQ context with the FEAGI process, which this SDK does not do.
 *
 * <h2>No defaults</h2>
 * Every required field must be supplied explicitly. The builder rejects {@link Builder#build()}
 * if any required field is missing. Validation runs eagerly so misconfiguration is caught
 * at config construction time, not at {@code connect()} time.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * FeagiClientConfig config = FeagiClientConfig.builder()
 *         .registrationEndpoint("tcp://feagi-host:30001")
 *         .sensoryEndpoint("tcp://feagi-host:5558")
 *         .motorEndpoint("tcp://feagi-host:5564")
 *         .connectionTimeout(Duration.ofSeconds(10))
 *         .heartbeatInterval(Duration.ofSeconds(1))
 *         .registrationRetries(3)
 *         .retryBackoff(Duration.ofMillis(500))
 *         .sensorySocketConfig(new SensorySocketConfig(1000, 0, true))
 *         .build();
 * }</pre>
 *
 * <h2>Relationship to AgentConfig</h2>
 * {@link AgentConfig} composes both agent identity/capability and connection parameters.
 * {@code FeagiClientConfig} can be derived from an existing {@link AgentConfig} via
 * {@link #from(AgentConfig)}, or built independently when constructing
 * {@code NativeFeagiAgentClient} directly.
 */
public final class FeagiClientConfig {

    // ── Endpoints ─────────────────────────────────────────────────────────────

    private final String registrationEndpoint;
    private final String sensoryEndpoint;
    private final String motorEndpoint;
    private final String visualizationEndpoint;
    private final String controlEndpoint;

    // ── Timing / retry ────────────────────────────────────────────────────────

    private final Duration connectionTimeout;
    private final Duration heartbeatInterval;
    private final int registrationRetries;
    private final Duration retryBackoff;

    // ── Socket ────────────────────────────────────────────────────────────────

    private final SensorySocketConfig sensorySocketConfig;

    // ── Optional auth / descriptor ────────────────────────────────────────────

    private final String authTokenBase64;
    private final String manufacturer;
    private final String agentName;
    private final OptionalLong agentVersion;

    // ── Construction ──────────────────────────────────────────────────────────

    private FeagiClientConfig(Builder b) {
        this.registrationEndpoint  = b.registrationEndpoint;
        this.sensoryEndpoint       = b.sensoryEndpoint;
        this.motorEndpoint         = b.motorEndpoint;
        this.visualizationEndpoint = b.visualizationEndpoint;
        this.controlEndpoint       = b.controlEndpoint;
        this.connectionTimeout     = b.connectionTimeout;
        this.heartbeatInterval     = b.heartbeatInterval;
        this.registrationRetries   = b.registrationRetries;
        this.retryBackoff          = b.retryBackoff;
        this.sensorySocketConfig   = b.sensorySocketConfig;
        this.authTokenBase64       = b.authTokenBase64;
        this.manufacturer          = b.manufacturer;
        this.agentName             = b.agentName;
        this.agentVersion          = b.agentVersionSet
                ? OptionalLong.of(b.agentVersion) : OptionalLong.empty();
    }

    // ── Factories ─────────────────────────────────────────────────────────────

    /** Return a new builder with no fields pre-set. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Derive a {@code FeagiClientConfig} from an existing {@link AgentConfig}.
     *
     * <p>Copies all connection parameters (endpoints, timeouts, retries, socket config)
     * so callers do not have to re-specify them when constructing
     * {@code NativeFeagiAgentClient} directly.
     *
     * <p><b>Note:</b> auth and descriptor fields ({@code authTokenBase64},
     * {@code manufacturer}, {@code agentName}, {@code agentVersion}) are <em>not</em>
     * copied because {@link AgentConfig} does not currently carry them. If those fields
     * are needed, use {@link #builder()} directly instead of this factory.
     *
     * @param config source agent configuration; must not be null
     */
    public static FeagiClientConfig from(AgentConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        FeagiEndpoints ep = config.endpoints();
        return builder()
                .registrationEndpoint(ep.registrationEndpoint())
                .sensoryEndpoint(ep.sensoryEndpoint())
                .motorEndpoint(ep.motorEndpoint())
                .visualizationEndpoint(ep.visualizationEndpoint())
                .controlEndpoint(ep.controlEndpoint())
                .connectionTimeout(config.connectionTimeout())
                .heartbeatInterval(config.heartbeatInterval())
                .registrationRetries(config.registrationRetries())
                .retryBackoff(config.retryBackoff())
                .sensorySocketConfig(config.sensorySocketConfig())
                .build();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Required FEAGI registration endpoint (e.g. {@code tcp://host:30001}). */
    public String registrationEndpoint()  { return registrationEndpoint; }

    /** Optional sensory ZMQ endpoint; {@code null} for motor-only agents. */
    public String sensoryEndpoint()       { return sensoryEndpoint; }

    /** Optional motor ZMQ endpoint; {@code null} for sensory-only agents. */
    public String motorEndpoint()         { return motorEndpoint; }

    /** Optional visualization ZMQ endpoint; {@code null} if unused. */
    public String visualizationEndpoint() { return visualizationEndpoint; }

    /** Optional control ZMQ endpoint; {@code null} if unused. */
    public String controlEndpoint()       { return controlEndpoint; }

    /** Connection timeout for the registration handshake. Always positive. */
    public Duration connectionTimeout()   { return connectionTimeout; }

    /**
     * Heartbeat interval. {@link Duration#ZERO} means heartbeating is disabled.
     * Always non-negative.
     */
    public Duration heartbeatInterval()   { return heartbeatInterval; }

    /** Number of registration retries before failing. Always {@code >= 0}. */
    public int registrationRetries()      { return registrationRetries; }

    /**
     * Backoff duration between registration retry attempts.
     *
     * <p>Returns {@link Duration#ZERO} when {@link #registrationRetries()} is zero,
     * because no retries means no backoff is ever needed. When retries are enabled
     * ({@code > 0}), this is always positive — the builder requires it.
     */
    public Duration retryBackoff()        { return retryBackoff; }

    /** ZMQ sensory socket configuration. Never {@code null}. */
    public SensorySocketConfig sensorySocketConfig() { return sensorySocketConfig; }

    /**
     * Base64-encoded auth token (standard Base64, decodes to 32 bytes),
     * or {@code null} if not set.
     *
     * <p><b>Security note:</b> The token is stored as a {@code String}, which cannot be
     * explicitly zeroed after use. It will remain in memory until garbage collected.
     * If zeroing credentials is required, prefer passing the decoded {@code byte[]} directly
     * to the native layer and clearing it there — tracked as a future improvement.
     */
    public String authTokenBase64()       { return authTokenBase64; }

    /** Agent manufacturer string (e.g. {@code "Neuraville"}), or {@code null}. */
    public String manufacturer()          { return manufacturer; }

    /** Agent name string (e.g. {@code "CozmoAgent"}), or {@code null}. */
    public String agentName()             { return agentName; }

    /**
     * Agent software version, or {@link OptionalLong#empty()} if not set.
     * Use {@code agentVersion().isPresent()} to check whether it was set.
     *
     * <p>Returns the full unsigned 32-bit range ({@code 0}–{@code 4,294,967,295})
     * losslessly as a {@code long}. Using {@code OptionalLong} avoids the sign-extension
     * that would occur with {@code OptionalInt} for values above {@link Integer#MAX_VALUE}.
     * The native layer receives this as {@code uint32_t}.
     */
    public OptionalLong agentVersion()    { return agentVersion; }

    // ── Builder ───────────────────────────────────────────────────────────────

    /**
     * Fluent builder for {@link FeagiClientConfig}.
     *
     * <h2>Required fields</h2>
     * {@link #registrationEndpoint}, {@link #connectionTimeout},
     * {@link #heartbeatInterval}, {@link #registrationRetries},
     * {@link #retryBackoff}, {@link #sensorySocketConfig}.
     *
     * <h2>Optional fields</h2>
     * {@link #sensoryEndpoint}, {@link #motorEndpoint},
     * {@link #visualizationEndpoint}, {@link #controlEndpoint},
     * {@link #authTokenBase64}, {@link #manufacturer},
     * {@link #agentName}, {@link #agentVersion}.
     */
    public static final class Builder {

        // Required — boolean flags track whether each int field has been set
        private String registrationEndpoint;
        private Duration connectionTimeout;
        private Duration heartbeatInterval;
        private int registrationRetries;
        private boolean registrationRetriesSet = false;
        private Duration retryBackoff;
        private SensorySocketConfig sensorySocketConfig;

        // Optional endpoints
        private String sensoryEndpoint;
        private String motorEndpoint;
        private String visualizationEndpoint;
        private String controlEndpoint;

        // Optional auth / descriptor
        private String authTokenBase64;
        private String manufacturer;
        private String agentName;
        private long agentVersion; // stored as unsigned bits via Integer.toUnsignedLong()
        private boolean agentVersionSet = false;

        private Builder() {}

        // ── Required ──────────────────────────────────────────────────────────

        /**
         * Set the FEAGI registration endpoint.
         *
         * @param endpoint must start with {@code tcp://}; must not be null or blank
         */
        public Builder registrationEndpoint(String endpoint) {
            this.registrationEndpoint = requireZmqEndpoint(endpoint, "registrationEndpoint");
            return this;
        }

        /**
         * Set the connection timeout for the registration handshake.
         *
         * @param timeout must be positive; must not be null
         */
        public Builder connectionTimeout(Duration timeout) {
            this.connectionTimeout = requirePositive(timeout, "connectionTimeout");
            return this;
        }

        /**
         * Set the heartbeat interval. Use {@link Duration#ZERO} to disable heartbeating.
         *
         * @param interval must be non-negative; must not be null
         */
        public Builder heartbeatInterval(Duration interval) {
            Objects.requireNonNull(interval, "heartbeatInterval must not be null");
            if (interval.isNegative()) {
                throw new IllegalArgumentException("heartbeatInterval must be >= 0");
            }
            this.heartbeatInterval = interval;
            return this;
        }

        /**
         * Set the number of registration retry attempts.
         *
         * @param retries must be {@code >= 0}
         */
        public Builder registrationRetries(int retries) {
            if (retries < 0) {
                throw new IllegalArgumentException("registrationRetries must be >= 0");
            }
            this.registrationRetries = retries;
            this.registrationRetriesSet = true;
            return this;
        }

        /**
         * Set the backoff duration between registration retry attempts.
         *
         * <p>{@link Duration#ZERO} is accepted here regardless of whether retries
         * have been set. The cross-field constraint — that backoff must be positive
         * when {@code registrationRetries > 0} — is enforced at {@link #build()} so
         * that the result is identical regardless of call order.
         *
         * @param backoff must be non-negative; must not be null
         */
        public Builder retryBackoff(Duration backoff) {
            Objects.requireNonNull(backoff, "retryBackoff must not be null");
            if (backoff.isNegative()) {
                throw new IllegalArgumentException("retryBackoff must not be negative");
            }
            this.retryBackoff = backoff;
            return this;
        }

        /**
         * Set the ZMQ sensory socket configuration.
         *
         * @param config must not be null
         */
        public Builder sensorySocketConfig(SensorySocketConfig config) {
            this.sensorySocketConfig =
                    Objects.requireNonNull(config, "sensorySocketConfig must not be null");
            return this;
        }

        // ── Optional endpoints ────────────────────────────────────────────────

        /**
         * Set the sensory ZMQ endpoint. Required for sensory and bidirectional agents.
         * Pass {@code null} to leave unset (motor-only agents).
         *
         * @param endpoint must start with {@code tcp://} or {@code ipc://} if non-null
         */
        public Builder sensoryEndpoint(String endpoint) {
            this.sensoryEndpoint = requireOptionalZmqEndpoint(endpoint, "sensoryEndpoint");
            return this;
        }

        /**
         * Set the motor ZMQ endpoint. Required for motor and bidirectional agents.
         * Pass {@code null} to leave unset (sensory-only agents).
         *
         * @param endpoint must start with {@code tcp://} or {@code ipc://} if non-null
         */
        public Builder motorEndpoint(String endpoint) {
            this.motorEndpoint = requireOptionalZmqEndpoint(endpoint, "motorEndpoint");
            return this;
        }

        /**
         * Set the visualization ZMQ endpoint.
         *
         * @param endpoint must start with {@code tcp://} or {@code ipc://} if non-null
         */
        public Builder visualizationEndpoint(String endpoint) {
            this.visualizationEndpoint =
                    requireOptionalZmqEndpoint(endpoint, "visualizationEndpoint");
            return this;
        }

        /**
         * Set the control ZMQ endpoint.
         *
         * @param endpoint must start with {@code tcp://} or {@code ipc://} if non-null
         */
        public Builder controlEndpoint(String endpoint) {
            this.controlEndpoint = requireOptionalZmqEndpoint(endpoint, "controlEndpoint");
            return this;
        }

        // ── Optional auth / descriptor ────────────────────────────────────────

        /**
         * Set the auth token as standard Base64 (must decode to exactly 32 bytes).
         * URL-safe Base64 ({@code -} and {@code _}) is rejected.
         * Pass {@code null} to leave unset.
         */
        public Builder authTokenBase64(String tokenBase64) {
            if (tokenBase64 != null) {
                validateAuthToken(tokenBase64);
            }
            this.authTokenBase64 = tokenBase64;
            return this;
        }

        /**
         * Set the agent manufacturer string (e.g. {@code "Neuraville"}).
         * Pass {@code null} to leave unset.
         *
         * @param manufacturer must not be blank if non-null
         */
        public Builder manufacturer(String manufacturer) {
            if (manufacturer != null && manufacturer.isBlank()) {
                throw new IllegalArgumentException("manufacturer must not be blank");
            }
            this.manufacturer = manufacturer;
            return this;
        }

        /**
         * Set the agent name string (e.g. {@code "CozmoAgent"}).
         * Pass {@code null} to leave unset.
         *
         * @param agentName must not be blank if non-null
         */
        public Builder agentName(String agentName) {
            if (agentName != null && agentName.isBlank()) {
                throw new IllegalArgumentException("agentName must not be blank");
            }
            this.agentName = agentName;
            return this;
        }

        /**
         * Set the agent software version. Passed as {@code uint32_t} to FEAGI.
         *
         * <p>Accepts the full unsigned 32-bit range: {@code 0} to {@code 4,294,967,295}
         * ({@code 0xFFFFFFFFL}). Values are narrowed to {@code int} for native ABI
         * compatibility — the widening to {@code long} here ensures values above
         * {@link Integer#MAX_VALUE} are not silently rejected or wrapped.
         *
         * @param version must be in {@code [0, 4294967295]}
         */
        public Builder agentVersion(long version) {
            if (version < 0 || version > 0xFFFFFFFFL) {
                throw new IllegalArgumentException(
                        "agentVersion must be in [0, 4294967295] (full uint32_t range), got " + version);
            }
            this.agentVersion = version; // stored as long, full uint32_t range preserved
            this.agentVersionSet = true;
            return this;
        }

        // ── Build ─────────────────────────────────────────────────────────────

        /**
         * Build and return an immutable {@link FeagiClientConfig}.
         *
         * <p>{@code retryBackoff} is only required when {@code registrationRetries > 0}
         * and must be positive in that case. When retries are disabled
         * ({@code registrationRetries = 0}), the backoff value is never consulted:
         * if it was set to {@link Duration#ZERO} or left unset, it is normalized to
         * {@link Duration#ZERO} here. The cross-field constraint is enforced at
         * {@code build()} regardless of the order in which setters were called.
         *
         * @throws IllegalStateException    if any required field was not set
         * @throws IllegalArgumentException if {@code retryBackoff} is {@link Duration#ZERO}
         *                                  or not set while {@code registrationRetries > 0}
         */
        public FeagiClientConfig build() {
            requireSet(registrationEndpoint, "registrationEndpoint");
            requireSet(connectionTimeout,    "connectionTimeout");
            requireSet(heartbeatInterval,    "heartbeatInterval");
            requireSet(sensorySocketConfig,  "sensorySocketConfig");
            if (!registrationRetriesSet) {
                throw new IllegalStateException(
                        "registrationRetries must be set — no SDK default. "
                        + "Call .registrationRetries(n) where n >= 0.");
            }
            if (registrationRetries > 0) {
                if (retryBackoff == null) {
                    throw new IllegalStateException(
                            "retryBackoff must be set when registrationRetries > 0 — no SDK default. "
                            + "Call .retryBackoff(Duration).");
                }
                if (retryBackoff.isZero()) {
                    throw new IllegalArgumentException(
                            "retryBackoff must be positive when registrationRetries > 0, "
                            + "but was set to Duration.ZERO. "
                            + "Use a positive duration (e.g. Duration.ofMillis(500)).");
                }
            } else {
                // Normalize: retryBackoff is never consulted when retries == 0.
                // Explicit policy in build() rather than a silent default in the constructor.
                if (retryBackoff == null) {
                    retryBackoff = Duration.ZERO;
                }
            }
            return new FeagiClientConfig(this);
        }

        // ── Validation helpers ────────────────────────────────────────────────

        private static String requireZmqEndpoint(String value, String name) {
            Objects.requireNonNull(value, name + " must not be null");
            if (value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            // Accept tcp:// (networked) and ipc:// (local/test). inproc:// is not supported
            // because it requires sharing a ZMQ context with FEAGI, which the SDK does not do.
            if (!value.startsWith("tcp://") && !value.startsWith("ipc://")) {
                throw new IllegalArgumentException(
                        name + " must start with tcp:// or ipc:// (got: '" + value + "')");
            }
            // isBlank() on the part after "//" covers both "tcp://" and "ipc://" with
            // no assumption about prefix length — handles any future scheme addition cleanly.
            if (value.substring(value.indexOf("//") + 2).isBlank()) {
                throw new IllegalArgumentException(
                        name + " must include an address after the scheme (got: '" + value + "')");
            }
            return value;
        }

        private static String requireOptionalZmqEndpoint(String value, String name) {
            if (value == null) return null;
            return requireZmqEndpoint(value, name);
        }

        private static Duration requirePositive(Duration v, String name) {
            Objects.requireNonNull(v, name + " must not be null");
            if (v.isZero() || v.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive (> 0)");
            }
            return v;
        }

        private static void requireSet(Object value, String name) {
            if (value == null) {
                throw new IllegalStateException(
                        name + " must be set — no SDK default. "
                        + "Call ." + name + "(...) on the builder.");
            }
        }

        private static void validateAuthToken(String token) {
            if (token.contains("-") || token.contains("_")) {
                throw new IllegalArgumentException(
                        "authTokenBase64 appears to use URL-safe Base64 (- or _ chars). "
                        + "Use standard Base64 encoding (+ and /) instead.");
            }
            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(token);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "authTokenBase64 is not valid Base64: " + e.getMessage(), e);
            }
            if (decoded.length != 32) {
                throw new IllegalArgumentException(
                        "authTokenBase64 must decode to exactly 32 bytes, got "
                        + decoded.length);
            }
        }
    }

    // ── Object ────────────────────────────────────────────────────────────────

    /**
     * Two configs are equal if all fields are equal, including {@code authTokenBase64}.
     *
     * <p><b>Security note:</b> {@code authTokenBase64} is compared via
     * {@link String#equals}, which short-circuits on the first differing byte. This
     * is not constant-time. Config objects are not used in timing-sensitive auth
     * comparisons in the current SDK, so the risk is negligible — but do not use
     * {@code equals()} to compare tokens in a security-critical path.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FeagiClientConfig that)) return false;
        return registrationRetries == that.registrationRetries
            && Objects.equals(agentVersion,          that.agentVersion)
            && Objects.equals(registrationEndpoint,  that.registrationEndpoint)
            && Objects.equals(sensoryEndpoint,        that.sensoryEndpoint)
            && Objects.equals(motorEndpoint,          that.motorEndpoint)
            && Objects.equals(visualizationEndpoint,  that.visualizationEndpoint)
            && Objects.equals(controlEndpoint,        that.controlEndpoint)
            && Objects.equals(connectionTimeout,      that.connectionTimeout)
            && Objects.equals(heartbeatInterval,      that.heartbeatInterval)
            && Objects.equals(retryBackoff,           that.retryBackoff)
            && Objects.equals(sensorySocketConfig,    that.sensorySocketConfig)
            && Objects.equals(authTokenBase64,        that.authTokenBase64)
            && Objects.equals(manufacturer,           that.manufacturer)
            && Objects.equals(agentName,              that.agentName);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>Security note:</b> {@code authTokenBase64} is included in the hash.
     * In adversarial contexts where an attacker can control which bucket a config
     * lands in and observe lookup timing, this could leak partial token information.
     * Config objects are not used as map keys in timing-sensitive paths in the current
     * SDK — this note exists to prevent such use being added silently in the future.
     */
    @Override
    public int hashCode() {
        return Objects.hash(
                registrationEndpoint, sensoryEndpoint, motorEndpoint,
                visualizationEndpoint, controlEndpoint,
                connectionTimeout, heartbeatInterval,
                registrationRetries, retryBackoff,
                sensorySocketConfig, authTokenBase64,
                manufacturer, agentName, agentVersion);
    }

    /**
     * Returns a human-readable summary of all connection parameters.
     * The auth token is masked to avoid accidentally logging a credential.
     */
    @Override
    public String toString() {
        return "FeagiClientConfig{"
                + "registrationEndpoint='" + registrationEndpoint + '\''
                + ", sensoryEndpoint='" + sensoryEndpoint + '\''
                + ", motorEndpoint='" + motorEndpoint + '\''
                + ", visualizationEndpoint='" + visualizationEndpoint + '\''
                + ", controlEndpoint='" + controlEndpoint + '\''
                + ", connectionTimeout=" + connectionTimeout
                + ", heartbeatInterval=" + heartbeatInterval
                + ", registrationRetries=" + registrationRetries
                + ", retryBackoff=" + (registrationRetries == 0 ? "N/A" : retryBackoff())
                + ", sensorySocketConfig=" + sensorySocketConfig
                + ", manufacturer=" + manufacturer
                + ", agentName=" + agentName
                + ", authTokenBase64=" + (authTokenBase64 != null ? "<set>" : "null")
                + ", agentVersion=" + (agentVersion.isPresent() ? agentVersion.getAsLong() : "not set")
                + '}';
    }
}
