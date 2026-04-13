/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.time.Duration;
import java.util.Objects;

/**
 * Tuning parameters for the {@link BaseAgent#run(AgentRunConfig)} loop.
 *
 * <p>Separating run-loop policy from connection configuration ({@link AgentConfig}) allows
 * the same agent instance to be reconnected with different polling rates without rebuilding
 * the full config object.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AgentRunConfig runConfig = AgentRunConfig.builder()
 *         .tickInterval(Duration.ofMillis(16))   // ~60 Hz
 *         .build();
 * }</pre>
 */
public final class AgentRunConfig {

    /** Default tick interval: 33 ms (~30 Hz), matching typical FEAGI cycle rate. */
    public static final Duration DEFAULT_TICK_INTERVAL = Duration.ofMillis(33);

    private final Duration tickInterval;
    private final int maxConsecutiveErrors;

    private AgentRunConfig(Builder builder) {
        this.tickInterval = builder.tickInterval;
        this.maxConsecutiveErrors = builder.maxConsecutiveErrors;
    }

    /**
     * Return a builder with all defaults applied.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Return a config with all defaults — useful for quick-start scenarios.
     */
    public static AgentRunConfig defaults() {
        return builder().build();
    }

    /**
     * Target duration between loop ticks. The loop sleeps for any remaining time after
     * {@link BaseAgent#mapSensors(Object)} and {@link BaseAgent#mapMotors(AgentFrame)} complete.
     * A zero value means the loop runs as fast as possible with no sleep.
     */
    public Duration tickInterval() {
        return tickInterval;
    }

    /**
     * Maximum number of consecutive exceptions from {@link BaseAgent#mapSensors(Object)} or
     * {@link BaseAgent#mapMotors(AgentFrame)} before the run loop aborts.
     * Set to {@link Integer#MAX_VALUE} to never abort on errors.
     */
    public int maxConsecutiveErrors() {
        return maxConsecutiveErrors;
    }

    /**
     * Builder for {@link AgentRunConfig}.
     */
    public static final class Builder {
        private Duration tickInterval = DEFAULT_TICK_INTERVAL;
        private int maxConsecutiveErrors = 10;

        private Builder() {}

        /**
         * Set the target tick interval. Use {@link Duration#ZERO} for unbounded rate.
         *
         * @param tickInterval must not be null or negative
         */
        public Builder tickInterval(Duration tickInterval) {
            Objects.requireNonNull(tickInterval, "tickInterval must not be null");
            if (tickInterval.isNegative()) {
                throw new IllegalArgumentException("tickInterval must not be negative");
            }
            this.tickInterval = tickInterval;
            return this;
        }

        /**
         * Set the maximum number of consecutive errors before the run loop aborts.
         *
         * @param maxConsecutiveErrors must be >= 1
         */
        public Builder maxConsecutiveErrors(int maxConsecutiveErrors) {
            if (maxConsecutiveErrors < 1) {
                throw new IllegalArgumentException("maxConsecutiveErrors must be >= 1");
            }
            this.maxConsecutiveErrors = maxConsecutiveErrors;
            return this;
        }

        /**
         * Build the run configuration.
         */
        public AgentRunConfig build() {
            return new AgentRunConfig(this);
        }
    }
}
