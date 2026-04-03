/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.time.Duration;
import java.util.Optional;

/**
 * Resolves cortical area dimensions by querying the FEAGI REST API.
 *
 * <h2>Usage — static convenience (no instance needed)</h2>
 * <pre>{@code
 * // Default localhost:8000
 * Optional<CorticalDimensions> dims = CorticalAreaResolver.resolveOnce("i__inf");
 *
 * // Explicit host and port
 * Optional<CorticalDimensions> dims = CorticalAreaResolver.resolveOnce("i__inf", "feagi-host", 8000);
 * }</pre>
 *
 * <h2>Usage — injected instance (testable)</h2>
 * <pre>{@code
 * // In production
 * CorticalAreaResolver resolver = CorticalAreaResolver.create();
 *
 * // In tests — pass a lambda or mock
 * CorticalAreaResolver resolver = id -> Optional.of(new CorticalDimensions(10, 10, 1));
 * }</pre>
 *
 * <h2>Return contract</h2>
 * Returns {@link Optional#empty()} in two distinct cases:
 * <ul>
 *   <li><b>Area not found (404):</b> logged at {@code FINE}.</li>
 *   <li><b>Network error:</b> logged at {@code WARNING} with the full stack trace.
 *       The host may be unreachable, the port wrong, or the connection timed out.</li>
 * </ul>
 * Both cases return {@code empty} so callers can retry without a try/catch.
 * Callers who need to distinguish the two should enable {@code WARNING}-level logging.
 * Throws {@link FeagiSdkException} only for malformed responses (unexpected HTTP status,
 * invalid JSON structure).
 *
 * <h2>Placement</h2>
 * {@code sdk-core/src/main/java/io/feagi/sdk/core/CorticalAreaResolver.java}
 */
@FunctionalInterface
public interface CorticalAreaResolver {

    /** Default FEAGI REST API host. */
    String DEFAULT_HOST = "127.0.0.1";

    /** Default FEAGI REST API port. */
    int DEFAULT_PORT = 8000;

    /** Default connection + read timeout applied to each HTTP request. */
    Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    // ── Instance API ──────────────────────────────────────────────────────────

    /**
     * Resolve dimensions for the given cortical area ID using this resolver's
     * configured host and port.
     *
     * @param corticalAreaId FEAGI cortical area identifier (e.g. {@code "i__inf"});
     *                       must not be null, blank, or contain characters outside
     *                       {@code [A-Za-z0-9_-]}
     * @return dimensions if the area was found, {@link Optional#empty()} for 404 or
     *         network error (see class Javadoc for the distinction)
     * @throws FeagiSdkException     if the response is malformed
     * @throws IllegalArgumentException if {@code corticalAreaId} is invalid
     */
    Optional<CorticalDimensions> resolve(String corticalAreaId);

    // ── Static factory ────────────────────────────────────────────────────────

    /**
     * Create a resolver that targets {@value #DEFAULT_HOST}:{@value #DEFAULT_PORT}
     * with {@link #DEFAULT_TIMEOUT}.
     */
    static CorticalAreaResolver create() {
        return create(DEFAULT_HOST, DEFAULT_PORT, DEFAULT_TIMEOUT);
    }

    /**
     * Create a resolver targeting the specified host and port with {@link #DEFAULT_TIMEOUT}.
     *
     * @param host FEAGI API host
     * @param port FEAGI API port (typically {@value #DEFAULT_PORT})
     */
    static CorticalAreaResolver create(String host, int port) {
        return create(host, port, DEFAULT_TIMEOUT);
    }

    /**
     * Create a resolver with explicit host, port, and timeout.
     *
     * @param host    FEAGI API host
     * @param port    FEAGI API port
     * @param timeout connection and read timeout; must be positive
     */
    static CorticalAreaResolver create(String host, int port, Duration timeout) {
        return new DefaultCorticalAreaResolver(host, port, timeout);
    }

    // ── Static convenience ────────────────────────────────────────────────────

    /**
     * One-shot resolve using default localhost settings.
     * Equivalent to {@code CorticalAreaResolver.create().resolve(corticalAreaId)}.
     */
    static Optional<CorticalDimensions> resolveOnce(String corticalAreaId) {
        return create().resolve(corticalAreaId);
    }

    /**
     * One-shot resolve with explicit host and port.
     */
    static Optional<CorticalDimensions> resolveOnce(String corticalAreaId, String host, int port) {
        return create(host, port).resolve(corticalAreaId);
    }

    /**
     * One-shot resolve with explicit host, port, and timeout.
     */
    static Optional<CorticalDimensions> resolveOnce(
            String corticalAreaId, String host, int port, Duration timeout) {
        return create(host, port, timeout).resolve(corticalAreaId);
    }
}
