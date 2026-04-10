/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

/**
 * Minimal client contract for FEAGI agents.
 *
 * <p>This is a skeleton interface. Implementations are expected to be Rust-backed via JNI.
 */
public interface FeagiAgentClient extends AutoCloseable {
    /**
     * Connect and register the agent with FEAGI.
     *
     * <p>Implementations must fail fast on configuration issues and must not assume defaults.
     */
    void connect();

    /**
     * Return {@code true} if currently connected to FEAGI.
     *
     * <p>Reflects real connection state: {@code true} after a successful {@link #connect()},
     * {@code false} before connect or after {@link #close()} / {@link #disconnect()}.
     * Must be safe to call from any thread.
     *
     * <p>The default implementation returns {@code false} (conservative / safe). Implementations
     * that track real connection state should override this.
     */
    default boolean isConnected() {
        return false;
    }

    /**
     * Gracefully disconnect from FEAGI and release resources.
     *
     * <p>Equivalent to {@link #close()} but named symmetrically with {@link #connect()}
     * for readability in non-try-with-resources usage. Idempotent — safe to call multiple times.
     *
     * <p>The default implementation delegates to {@link #close()}, matching
     * {@code NativeFeagiAgentClient}'s behaviour. Implementations that require distinct
     * disconnect semantics should override this.
     */
    default void disconnect() {
        close();
    }

    /**
     * Send already-serialized FEAGI byte-container sensory payload (real-time semantics).
     *
     * <p>No implicit buffering: underlying implementation may drop on backpressure.
     */
    void sendSensoryBytes(byte[] payload);

    /**
     * Non-blocking receive of FEAGI motor payload as byte-container bytes.
     *
     * @return payload bytes if available, otherwise {@code null}
     */
    byte[] pollMotorBytes();

    /**
     * Close and release native resources.
     */
    @Override
    void close();
}
