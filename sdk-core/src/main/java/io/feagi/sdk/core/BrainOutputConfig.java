/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for BrainOutput.
 *
 * <p>BrainOutputConfig provides settings for customizing the behavior
 * of a BrainOutput instance.
 */
public final class BrainOutputConfig {

    private final Duration pollTimeout;
    private final int pollIntervalMillis;
    private final boolean autoClearStaleData;
    private final long staleDataTimeoutMillis;

    /**
     * Create a new configuration with defaults.
     */
    public BrainOutputConfig() {
        this.pollTimeout = Duration.ofMillis(100);
        this.pollIntervalMillis = 10;
        this.autoClearStaleData = true;
        this.staleDataTimeoutMillis = 1000;
    }

    private BrainOutputConfig(Builder builder) {
        this.pollTimeout = builder.pollTimeout;
        this.pollIntervalMillis = builder.pollIntervalMillis;
        this.autoClearStaleData = builder.autoClearStaleData;
        this.staleDataTimeoutMillis = builder.staleDataTimeoutMillis;
    }

    /**
     * Return the poll timeout.
     *
     * @return poll timeout duration
     */
    public Duration getPollTimeout() {
        return pollTimeout;
    }

    /**
     * Return the poll interval in milliseconds.
     *
     * @return poll interval millis
     */
    public int getPollIntervalMillis() {
        return pollIntervalMillis;
    }

    /**
     * Return whether to automatically clear stale data.
     *
     * @return true if auto-clear is enabled
     */
    public boolean isAutoClearStaleData() {
        return autoClearStaleData;
    }

    /**
     * Return the stale data timeout in milliseconds.
     *
     * @return stale data timeout millis
     */
    public long getStaleDataTimeoutMillis() {
        return staleDataTimeoutMillis;
    }

    /**
     * Create a builder for BrainOutputConfig.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for BrainOutputConfig.
     */
    public static final class Builder {
        private Duration pollTimeout = Duration.ofMillis(100);
        private int pollIntervalMillis = 10;
        private boolean autoClearStaleData = true;
        private long staleDataTimeoutMillis = 1000;

        private Builder() {}

        /**
         * Set the poll timeout.
         *
         * @param pollTimeout timeout for polling operations
         * @return this builder
         */
        public Builder pollTimeout(Duration pollTimeout) {
            Objects.requireNonNull(pollTimeout, "pollTimeout must not be null");
            if (pollTimeout.isZero() || pollTimeout.isNegative()) {
                throw new IllegalArgumentException("pollTimeout must be positive");
            }
            this.pollTimeout = pollTimeout;
            return this;
        }

        /**
         * Set the poll interval.
         *
         * @param pollIntervalMillis interval between polls
         * @return this builder
         */
        public Builder pollIntervalMillis(int pollIntervalMillis) {
            if (pollIntervalMillis < 0) {
                throw new IllegalArgumentException("pollIntervalMillis must be non-negative");
            }
            this.pollIntervalMillis = pollIntervalMillis;
            return this;
        }

        /**
         * Set whether to automatically clear stale data.
         *
         * @param autoClearStaleData true to auto-clear
         * @return this builder
         */
        public Builder autoClearStaleData(boolean autoClearStaleData) {
            this.autoClearStaleData = autoClearStaleData;
            return this;
        }

        /**
         * Set the stale data timeout.
         *
         * @param staleDataTimeoutMillis timeout in milliseconds
         * @return this builder
         */
        public Builder staleDataTimeoutMillis(long staleDataTimeoutMillis) {
            if (staleDataTimeoutMillis < 0) {
                throw new IllegalArgumentException("staleDataTimeoutMillis must be non-negative");
            }
            this.staleDataTimeoutMillis = staleDataTimeoutMillis;
            return this;
        }

        /**
         * Build the configuration.
         *
         * @return new BrainOutputConfig instance
         */
        public BrainOutputConfig build() {
            return new BrainOutputConfig(this);
        }
    }
}