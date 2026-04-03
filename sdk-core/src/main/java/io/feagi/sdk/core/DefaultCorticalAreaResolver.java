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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default {@link CorticalAreaResolver} implementation backed by
 * {@code java.net.HttpURLConnection}.
 *
 * <p>Obtain instances via {@link CorticalAreaResolver#create()} rather than constructing
 * this class directly.
 *
 * <h2>No runtime dependencies</h2>
 * Uses only {@code java.net.HttpURLConnection} — no third-party HTTP library required.
 *
 * <h2>Thread safety</h2>
 * Instances are immutable after construction and safe to call from any thread.
 *
 * <h2>Placement</h2>
 * {@code sdk-core/src/main/java/io/feagi/sdk/core/DefaultCorticalAreaResolver.java}
 */
public final class DefaultCorticalAreaResolver implements CorticalAreaResolver {

    private static final Logger LOG =
            Logger.getLogger(DefaultCorticalAreaResolver.class.getName());

    /**
     * Pre-compiled pattern for valid cortical area IDs.
     * Defined once here — both {@link #resolve} and {@link #buildUrl} use it.
     * Update the pattern here only; it is the single source of truth.
     */
    static final Pattern VALID_CORTICAL_ID = Pattern.compile("[A-Za-z0-9_\\-]+");

    /**
     * Matches {@code "cortical_dimensions": [w, h, d]} in a JSON response.
     *
     * <p>Using a regex over {@code indexOf} avoids three fragility issues present in
     * the naive approach:
     * <ul>
     *   <li>Requires the key to appear in key-colon-array form, reducing false matches
     *       inside string values compared to a bare {@code indexOf} on the key name.</li>
     *   <li>Captures array elements directly — no separate bracket-search that would
     *       break on nested objects.</li>
     *   <li>Handles optional whitespace around {@code :} and inside the array.</li>
     * </ul>
     * Known limitation: if the literal text {@code "cortical_dimensions": [w, h, d]}
     * appears inside a JSON string value, the regex will still match it. This is
     * acceptable given FEAGI API response shapes; replace with a full JSON parser if
     * the response format becomes more complex.
     */
    static final Pattern DIMENSIONS_PATTERN = Pattern.compile(
            "\"cortical_dimensions\"\\s*:\\s*\\[\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*]");

    private final String host;
    private final int    port;
    private final Duration timeout;

    DefaultCorticalAreaResolver(String host, int port, Duration timeout) {
        this.host    = Objects.requireNonNull(host,    "host must not be null");
        this.port    = port;
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be in [1, 65535], got " + port);
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    // ── CorticalAreaResolver ──────────────────────────────────────────────────

    @Override
    public Optional<CorticalDimensions> resolve(String corticalAreaId) {
        Objects.requireNonNull(corticalAreaId, "corticalAreaId must not be null");
        if (corticalAreaId.isBlank()) {
            throw new IllegalArgumentException("corticalAreaId must not be blank");
        }
        // Guard against path traversal and injection. The URI multi-arg constructor
        // encodes '?' and '#' but does NOT encode '/', so "../../etc" would produce
        // a traversable path. FEAGI IDs are alphanumeric + underscore + hyphen only.
        if (!VALID_CORTICAL_ID.matcher(corticalAreaId).matches()) {
            throw new IllegalArgumentException(
                    "corticalAreaId contains disallowed characters — only ASCII "
                    + "alphanumeric, underscore, and hyphen are permitted: '"
                    + corticalAreaId + "'");
        }

        String url = buildUrl(host, port, corticalAreaId);
        LOG.fine("CorticalAreaResolver: GET " + url);

        try {
            Optional<String> body = httpGet(url, timeout);
            if (body.isEmpty()) {
                return Optional.empty(); // 404 — area not found
            }
            return Optional.of(parseDimensions(body.get(), corticalAreaId));
        } catch (IOException e) {
            // Network error (connection refused, timeout, DNS failure, etc.).
            // Logged at WARNING with full stack trace so the caller can distinguish
            // this from a genuine 404 via log inspection.
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
                        "CorticalAreaResolver: unexpected HTTP " + status + " from " + url);
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
     * @throws FeagiSdkException if {@code cortical_dimensions} is absent or malformed
     */
    static CorticalDimensions parseDimensions(String json, String corticalAreaId) {
        Matcher m = DIMENSIONS_PATTERN.matcher(json);
        if (!m.find()) {
            throw new FeagiSdkException(
                    "CorticalAreaResolver: 'cortical_dimensions' field missing or malformed "
                    + "in response for cortical area '" + corticalAreaId + "'. "
                    + "Expected format: \"cortical_dimensions\": [w, h, d]. Response: "
                    + truncate(json, 200));
        }
        // Groups 1/2/3 are digit strings (with optional leading minus) — NFE is not
        // expected but caught as a safety net for values that overflow int.
        try {
            int width  = Integer.parseInt(m.group(1));
            int height = Integer.parseInt(m.group(2));
            int depth  = Integer.parseInt(m.group(3));
            CorticalDimensions dims = new CorticalDimensions(width, height, depth);
            LOG.fine("CorticalAreaResolver: resolved '" + corticalAreaId + "' → " + dims);
            return dims;
        } catch (NumberFormatException e) {
            throw new FeagiSdkException(
                    "CorticalAreaResolver: dimension value overflows int in response "
                    + "for cortical area '" + corticalAreaId + "': "
                    + m.group(1) + ", " + m.group(2) + ", " + m.group(3), e);
        } catch (IllegalArgumentException e) {
            throw new FeagiSdkException(
                    "CorticalAreaResolver: invalid dimension value in response "
                    + "for cortical area '" + corticalAreaId + "': " + e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static String buildUrl(String host, int port, String corticalAreaId) {
        // Precondition: corticalAreaId must be pre-validated by resolve().
        // The URI multi-arg constructor encodes '?' and '#' but NOT '/', so an unvalidated
        // ID like "../../etc" would produce a traversable URL.
        if (!VALID_CORTICAL_ID.matcher(corticalAreaId).matches()) {
            throw new IllegalStateException(
                    "buildUrl called with unvalidated corticalAreaId — "
                    + "this is a programming error. Apply the VALID_CORTICAL_ID "
                    + "guard before calling buildUrl. Got: '" + corticalAreaId + "'");
        }
        try {
            URI uri = new URI(
                    "http",
                    null,
                    host,
                    port,
                    "/v1/genome/cortical_area/" + corticalAreaId,
                    null,
                    null);
            return uri.toASCIIString();
        } catch (URISyntaxException e) {
            throw new FeagiSdkException(
                    "CorticalAreaResolver: invalid host — "
                    + "host='" + host + "': " + e.getMessage(), e);
        }
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }
}
