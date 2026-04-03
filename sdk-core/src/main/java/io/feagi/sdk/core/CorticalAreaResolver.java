/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Detects cortical area dimensions by querying the FEAGI REST API.
 *
 * <h2>API endpoint</h2>
 * Calls {@code GET http://<host>:<port>/v1/genome/cortical_area/<corticalAreaId>}
 * and parses the {@code cortical_dimensions} field from the JSON response:
 * <pre>
 * {
 *   "cortical_dimensions": [width, height, depth],
 *   ...
 * }
 * </pre>
 *
 * <h2>Usage — with explicit host</h2>
 * <pre>{@code
 * Optional<CorticalDimensions> dims =
 *         CorticalAreaResolver.resolve("i__inf", "feagi-host", 8000);
 * dims.ifPresent(d -> System.out.println(d.width() + "x" + d.height() + "x" + d.depth()));
 * }</pre>
 *
 * <h2>Usage — with default localhost</h2>
 * <pre>{@code
 * Optional<CorticalDimensions> dims = CorticalAreaResolver.resolve("i__inf");
 * }</pre>
 *
 * <h2>Return contract</h2>
 * Returns {@link Optional#empty()} in two distinct cases:
 * <ul>
 *   <li><b>Area not found (404):</b> logged at {@code FINE}. The cortical area ID is
 *       valid but not present in the current FEAGI genome.</li>
 *   <li><b>Network error:</b> logged at {@code WARNING} with the full exception stack
 *       trace. The host may be unreachable, the port wrong, or the connection timed out.</li>
 * </ul>
 * Both return {@code empty} so callers can retry or fall back without a try/catch.
 * Callers who need to distinguish the two should enable {@code WARNING}-level logging
 * or check connectivity separately. Throws {@link FeagiSdkException} only for
 * unambiguously malformed responses (unexpected HTTP status, invalid JSON structure).
 *
 * <h2>No runtime dependencies</h2>
 * Uses only {@code java.net.HttpURLConnection} — no third-party HTTP library required.
 *
 * <h2>Thread safety</h2>
 * All methods are stateless and safe to call from any thread.
 *
 * <h2>Placement</h2>
 * {@code sdk-core/src/main/java/io/feagi/sdk/core/CorticalAreaResolver.java}
 */
public final class CorticalAreaResolver {

    private static final Logger LOG = Logger.getLogger(CorticalAreaResolver.class.getName());

    /** Default FEAGI REST API host. */
    public static final String DEFAULT_HOST = "127.0.0.1";

    /** Default FEAGI REST API port. */
    public static final int DEFAULT_PORT = 8000;

    /** Default connection + read timeout applied to each HTTP request. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private CorticalAreaResolver() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Resolve dimensions for the given cortical area using default localhost settings.
     *
     * <p>Equivalent to {@link #resolve(String, String, int)} with
     * {@code host=}{@value #DEFAULT_HOST} and {@code port=}{@value #DEFAULT_PORT}.
     *
     * @param corticalAreaId FEAGI cortical area identifier (e.g. {@code "i__inf"});
     *                       must not be null or blank
     * @return dimensions if the area was found, {@link Optional#empty()} otherwise
     * @throws FeagiSdkException if the response is malformed or an unexpected HTTP
     *                           status is received
     */
    public static Optional<CorticalDimensions> resolve(String corticalAreaId) {
        return resolve(corticalAreaId, DEFAULT_HOST, DEFAULT_PORT, DEFAULT_TIMEOUT);
    }

    /**
     * Resolve dimensions for the given cortical area using the specified host and port.
     *
     * @param corticalAreaId FEAGI cortical area identifier (e.g. {@code "i__inf"});
     *                       must not be null or blank
     * @param host           FEAGI API host (e.g. {@code "127.0.0.1"} or a hostname)
     * @param port           FEAGI API port (typically {@value #DEFAULT_PORT})
     * @return dimensions if the area was found, {@link Optional#empty()} otherwise
     * @throws FeagiSdkException if the response is malformed or an unexpected HTTP
     *                           status is received
     */
    public static Optional<CorticalDimensions> resolve(
            String corticalAreaId, String host, int port) {
        return resolve(corticalAreaId, host, port, DEFAULT_TIMEOUT);
    }

    /**
     * Resolve dimensions with an explicit timeout.
     *
     * @param corticalAreaId FEAGI cortical area identifier; must not be null or blank
     * @param host           FEAGI API host
     * @param port           FEAGI API port
     * @param timeout        connection and read timeout; must be positive
     * @return dimensions if the area was found, {@link Optional#empty()} otherwise
     * @throws FeagiSdkException if the response is malformed or an unexpected HTTP
     *                           status is received
     */
    public static Optional<CorticalDimensions> resolve(
            String corticalAreaId, String host, int port, Duration timeout) {
        Objects.requireNonNull(corticalAreaId, "corticalAreaId must not be null");
        Objects.requireNonNull(host,           "host must not be null");
        Objects.requireNonNull(timeout,        "timeout must not be null");
        if (corticalAreaId.isBlank()) {
            throw new IllegalArgumentException("corticalAreaId must not be blank");
        }
        // Guard against path traversal and injection. The URI multi-arg constructor
        // encodes '?' and '#' but does NOT encode '/', so "../../etc" would produce
        // a traversable path. FEAGI IDs are alphanumeric + underscore + hyphen only.
        if (!corticalAreaId.matches("[A-Za-z0-9_\\-]+")) {
            throw new IllegalArgumentException(
                    "corticalAreaId contains disallowed characters — only ASCII "
                    + "alphanumeric, underscore, and hyphen are permitted: '"
                    + corticalAreaId + "'");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be in [1, 65535], got " + port);
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }

        String url = buildUrl(host, port, corticalAreaId);
        LOG.fine("CorticalAreaResolver: GET " + url);

        try {
            Optional<String> body = httpGet(url, timeout);
            if (body.isEmpty()) {
                // 404 — area not found
                return Optional.empty();
            }
            return Optional.of(parseDimensions(body.get(), corticalAreaId));
        } catch (IOException e) {
            // Network error (connection refused, timeout, etc.) — treat as not found
            // rather than throwing, so callers can retry without a try/catch.
            LOG.log(Level.WARNING,
                    "CorticalAreaResolver: could not reach FEAGI API at " + url, e);
            return Optional.empty();
        }
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    /**
     * Execute a GET request and return the response body as a string.
     *
     * @return {@link Optional} containing the response body, or {@link Optional#empty()}
     *         if the server returned 404
     * @throws IOException       on network error or timeout
     * @throws FeagiSdkException on unexpected HTTP status (not 200 or 404)
     */
    static Optional<String> httpGet(String url, Duration timeout) throws IOException {
        URL urlObj = URI.create(url).toURL();
        HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            // Clamp to Integer.MAX_VALUE (~24.8 days) — HttpURLConnection takes int millis.
            // An unclamped cast of a large Duration.toMillis() (long) would silently overflow
            // to a wrong or negative value, disabling the timeout entirely.
            int timeoutMs = (int) Math.min(timeout.toMillis(), Integer.MAX_VALUE);
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setDoOutput(false);

            int status = conn.getResponseCode();

            if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                LOG.fine("CorticalAreaResolver: 404 for " + url + " — area not found");
                return Optional.empty();
            }
            if (status != HttpURLConnection.HTTP_OK) {
                throw new FeagiSdkException(
                        "CorticalAreaResolver: unexpected HTTP " + status
                        + " from " + url);
            }

            try (InputStream is = conn.getInputStream()) {
                // 64 KB is far more than any cortical-area JSON response needs.
                // readAllBytes() has no size cap — a misbehaving server could cause OOM.
                byte[] bytes = is.readNBytes(64 * 1024);
                return Optional.of(new String(bytes, StandardCharsets.UTF_8));
            }
        } finally {
            conn.disconnect();
        }
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    /**
     * Parse {@code cortical_dimensions} from a FEAGI cortical area JSON response.
     *
     * <p>Expected JSON shape:
     * <pre>
     * {
     *   "cortical_dimensions": [width, height, depth],
     *   ...
     * }
     * </pre>
     *
     * <p>Uses hand-rolled parsing to avoid a JSON library dependency. The field is
     * a fixed-format integer array — no recursive parsing required.
     *
     * @throws FeagiSdkException if {@code cortical_dimensions} is absent or malformed
     */
    static CorticalDimensions parseDimensions(String json, String corticalAreaId) {
        // Locate the key using indexOf. Known limitation: this matches the first
        // occurrence of the byte sequence "cortical_dimensions" anywhere in the JSON,
        // including inside string values (e.g. a "description" field that mentions
        // "cortical_dimensions"). FEAGI API responses are not expected to contain such
        // values in practice. If future API responses become more complex, replace this
        // with a proper JSON parser (e.g. org.json or Jackson).
        String key = "\"cortical_dimensions\"";
        int keyIdx = json.indexOf(key);
        if (keyIdx < 0) {
            throw new FeagiSdkException(
                    "CorticalAreaResolver: 'cortical_dimensions' field missing in response "
                    + "for cortical area '" + corticalAreaId + "'. Response: "
                    + truncate(json, 200));
        }

        // Find the opening bracket of the array
        int bracketOpen = json.indexOf('[', keyIdx + key.length());
        if (bracketOpen < 0) {
            throw new FeagiSdkException(
                    "CorticalAreaResolver: 'cortical_dimensions' value is not an array "
                    + "for cortical area '" + corticalAreaId + "'");
        }
        int bracketClose = json.indexOf(']', bracketOpen);
        if (bracketClose < 0) {
            throw new FeagiSdkException(
                    "CorticalAreaResolver: unterminated array in 'cortical_dimensions' "
                    + "for cortical area '" + corticalAreaId + "'");
        }

        // Parse the three integers
        String arrayContent = json.substring(bracketOpen + 1, bracketClose).trim();
        String[] parts = arrayContent.split(",");
        if (parts.length != 3) {
            throw new FeagiSdkException(
                    "CorticalAreaResolver: 'cortical_dimensions' must have exactly 3 elements "
                    + "[width, height, depth], got " + parts.length
                    + " for cortical area '" + corticalAreaId + "'");
        }

        try {
            int width  = Integer.parseInt(parts[0].trim());
            int height = Integer.parseInt(parts[1].trim());
            int depth  = Integer.parseInt(parts[2].trim());
            CorticalDimensions dims = new CorticalDimensions(width, height, depth);
            LOG.fine("CorticalAreaResolver: resolved '" + corticalAreaId + "' → " + dims);
            return dims;
        } catch (NumberFormatException e) {
            // Must be caught before IllegalArgumentException — NFE is a subtype of IAE.
            throw new FeagiSdkException(
                    "CorticalAreaResolver: non-integer value in 'cortical_dimensions' "
                    + "for cortical area '" + corticalAreaId + "': " + arrayContent, e);
        } catch (IllegalArgumentException e) {
            // CorticalDimensions compact constructor rejects zero/negative values.
            // Wrapped so callers only need to catch FeagiSdkException from this method.
            throw new FeagiSdkException(
                    "CorticalAreaResolver: invalid dimension value in response "
                    + "for cortical area '" + corticalAreaId + "': " + e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static String buildUrl(String host, int port, String corticalAreaId) {
        // Internal precondition: corticalAreaId must contain only [A-Za-z0-9_\-].
        // The URI multi-arg constructor encodes '?' and '#' but NOT '/', so an unvalidated
        // ID like "../../etc" would produce a traversable URL. resolve() enforces this
        // constraint before calling buildUrl. If buildUrl is ever called from a new code path,
        // that path must apply the same regex guard — do not call buildUrl with unvalidated input.
        if (!corticalAreaId.matches("[A-Za-z0-9_\\-]+")) {
            throw new IllegalStateException(
                    "buildUrl called with unvalidated corticalAreaId — "
                    + "this is a programming error. Apply the [A-Za-z0-9_\\-]+ "
                    + "guard before calling buildUrl. Got: '" + corticalAreaId + "'");
        }
        try {
            URI uri = new URI(
                    "http",
                    null,                                         // userInfo
                    host,
                    port,
                    "/v1/genome/cortical_area/" + corticalAreaId,
                    null,                                         // query
                    null);                                        // fragment
            return uri.toASCIIString();
        } catch (URISyntaxException e) {
            throw new FeagiSdkException(
                    "CorticalAreaResolver: invalid host or cortical area ID — "
                    + "host='" + host + "', id='" + corticalAreaId + "': "
                    + e.getMessage(), e);
        }
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }
}
