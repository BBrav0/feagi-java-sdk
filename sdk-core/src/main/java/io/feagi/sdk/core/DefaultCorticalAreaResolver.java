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
 * <p>Package-private — obtain instances via {@link CorticalAreaResolver#create()} rather
 * than constructing this class directly. The {@code create()} factory methods are the
 * public API; this class is an implementation detail.
 *
 * <h2>No runtime dependencies</h2>
 * Uses only {@code java.net.HttpURLConnection} — no third-party HTTP library required.
 *
 * <h2>Thread safety</h2>
 * Instances are immutable after construction and safe to call from any thread.
 *
 * <h2>Connection behaviour</h2>
 * Each call to {@link #resolve(String)} opens a new HTTP connection and closes it
 * when the response is received ({@code HttpURLConnection.disconnect()} is called in
 * the {@code finally} block, which prevents keep-alive reuse). For a single lookup
 * this is fine; for repeated lookups in a loop, consider caching the result rather
 * than calling {@code resolve()} on every iteration.
 *
 * <h2>Placement</h2>
 * {@code sdk-core/src/main/java/io/feagi/sdk/core/DefaultCorticalAreaResolver.java}
 */
final class DefaultCorticalAreaResolver implements CorticalAreaResolver {

    private static final Logger LOG =
            Logger.getLogger(DefaultCorticalAreaResolver.class.getName());

    /**
     * Pre-compiled pattern for valid cortical area IDs.
     * Rules:
     * <ul>
     *   <li>First character: ASCII letter or digit ({@code [A-Za-z0-9]}) — not underscore
     *       or hyphen.</li>
     *   <li>Middle characters (optional): ASCII letter, digit, underscore, or hyphen.</li>
     *   <li>Last character (if more than one character): ASCII letter or digit — not
     *       underscore or hyphen.</li>
     * </ul>
     * Examples of valid IDs: {@code "i__inf"}, {@code "v1-motor"}, {@code "x"}.
     * Examples of invalid IDs: {@code "_bad"}, {@code "bad-"}, {@code "-bad"}.
     * Defined once here — both {@link #resolve} and {@link #buildUrl} use it.
     */
    static final Pattern VALID_CORTICAL_ID =
            Pattern.compile("[A-Za-z0-9]([A-Za-z0-9_\\-]*[A-Za-z0-9])?");

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
     *
     * <p>Captures only non-negative integers ({@code \\d+}, no leading minus). Negative
     * values would be rejected by {@link CorticalDimensions} anyway, but excluding them
     * at the regex level surfaces a cleaner "missing or malformed" message rather than a
     * wrapped {@code IllegalArgumentException}. Zero is accepted by the regex and rejected
     * by the constructor — the constructor's "must be >= 1" message is more informative
     * than a generic parse failure for that specific case.
     *
     * <p>Known limitation: if the literal text {@code "cortical_dimensions": [w, h, d]}
     * appears inside a JSON string value, the regex will still match it. Acceptable given
     * FEAGI API response shapes; replace with a proper JSON parser if responses become
     * more complex.
     */
    static final Pattern DIMENSIONS_PATTERN = Pattern.compile(
            "\"cortical_dimensions\"\\s*:\\s*\\[\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*]");

    private final String host;
    private final int    port;
    private final Duration timeout;
    private final String scheme;

    DefaultCorticalAreaResolver(String host, int port, Duration timeout) {
        this(host, port, timeout, "http");
    }

    DefaultCorticalAreaResolver(String host, int port, Duration timeout, String scheme) {
        this.host    = Objects.requireNonNull(host, "host must not be null");
        if (host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        this.port    = port;
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.scheme  = Objects.requireNonNull(scheme, "scheme must not be null");
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException(
                    "scheme must be 'http' or 'https', got: '" + scheme + "'");
        }
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

        String url = buildUrl(scheme, host, port, corticalAreaId);
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
        // URI.create() throws unchecked IllegalArgumentException for syntactically invalid
        // URLs. This is unreachable in practice: buildUrl() uses the multi-arg URI constructor
        // which percent-encodes components, guaranteeing a well-formed URL string here.
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
                // Cap at 64 KB — far more than any cortical-area JSON response needs.
                // readAllBytes() has no size cap and could OOM on a misbehaving server.
                final int maxBytes = 64 * 1024;
                byte[] bytes = is.readNBytes(maxBytes);
                if (bytes.length == maxBytes) {
                    // readNBytes returns exactly maxBytes when the response is >= maxBytes,
                    // which means the body may have been truncated mid-JSON. Throw early
                    // with a clear message rather than letting parseDimensions produce a
                    // confusing "missing or malformed" error.
                    throw new FeagiSdkException(
                            "CorticalAreaResolver: response from " + url
                            + " reached the " + maxBytes + "-byte cap and may be truncated."
                            + " This is unexpected for a cortical area response.");
                }
                LOG.fine("CorticalAreaResolver: read " + bytes.length + " bytes from " + url);
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
            // Log the response body at FINE for debugging, but keep it out of the
            // exception message. The body could contain auth tokens, session IDs, or
            // other PII in an error payload that would otherwise appear in logs and
            // stack traces at WARNING level or above.
            LOG.fine("CorticalAreaResolver: response body for '" + corticalAreaId
                    + "' (first 200 chars): " + truncate(json, 200));
            throw new FeagiSdkException(
                    "CorticalAreaResolver: 'cortical_dimensions' field missing or malformed "
                    + "in response for cortical area '" + corticalAreaId + "'. "
                    + "Expected format: \"cortical_dimensions\": [w, h, d]. "
                    + "Enable FINE logging to see the response body.");
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

    static String buildUrl(String scheme, String host, int port, String corticalAreaId) {
        // Belt-and-suspenders guard: resolve() validates corticalAreaId before calling
        // here, so this check should never fire in normal use. It exists solely to catch
        // future callers who bypass resolve() and call buildUrl directly.
        if (!VALID_CORTICAL_ID.matcher(corticalAreaId).matches()) {
            throw new IllegalStateException(
                    "buildUrl called with unvalidated corticalAreaId '" + corticalAreaId
                    + "' — apply VALID_CORTICAL_ID before calling buildUrl directly.");
        }
        try {
            URI uri = new URI(
                    scheme,
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
