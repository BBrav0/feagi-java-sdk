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
 * Returns {@link Optional#empty()} when the cortical area is not found (404) or when
 * the API is unreachable. Throws {@link FeagiSdkException} only for unambiguously
 * malformed responses (unexpected HTTP status, invalid JSON structure). This means
 * callers can use the empty-optional result to retry later or fall back gracefully.
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
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be in [1, 65535], got " + port);
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }

        String url = buildUrl(host, port, corticalAreaId);
        LOG.fine("CorticalAreaResolver: GET " + url);

        try {
            String body = httpGet(url, timeout);
            if (body == null) {
                // 404 or empty — area not found
                return Optional.empty();
            }
            return Optional.of(parseDimensions(body, corticalAreaId));
        } catch (IOException e) {
            // Network error (connection refused, timeout, etc.) — treat as not found
            // rather than throwing, so callers can retry without a try/catch.
            LOG.log(Level.WARNING,
                    "CorticalAreaResolver: could not reach FEAGI API at " + url
                    + " — " + e.getMessage());
            return Optional.empty();
        }
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    /**
     * Execute a GET request and return the response body as a string.
     *
     * @return response body string, or {@code null} if the server returned 404
     * @throws IOException       on network error or timeout
     * @throws FeagiSdkException on unexpected HTTP status (not 200 or 404)
     */
    static String httpGet(String url, Duration timeout) throws IOException {
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
                return null;
            }
            if (status != HttpURLConnection.HTTP_OK) {
                throw new FeagiSdkException(
                        "CorticalAreaResolver: unexpected HTTP " + status
                        + " from " + url);
            }

            try (InputStream is = conn.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
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
        // Locate the key
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
            LOG.fine("CorticalAreaResolver: resolved '" + corticalAreaId
                    + "' → " + dims);
            return dims;
        } catch (NumberFormatException e) {
            throw new FeagiSdkException(
                    "CorticalAreaResolver: non-integer value in 'cortical_dimensions' "
                    + "for cortical area '" + corticalAreaId + "': " + arrayContent, e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static String buildUrl(String host, int port, String corticalAreaId) {
        // Use URI's multi-argument constructor to enforce proper component boundaries.
        // String concatenation allows host values like "evil@real-host" or
        // "real-host/injected/path?q=1" to manipulate the resulting URL. The multi-arg
        // constructor treats each parameter as a distinct URI component and percent-encodes
        // any reserved characters, preventing injection and future-proofing against
        // cortical area IDs that may expand beyond the current safe-character set.
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
