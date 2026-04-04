/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CorticalAreaResolver} and {@link CorticalDimensions}.
 *
 * <p>All tests are pure unit tests — no real network calls. The package-private
 * {@code parseDimensions}, {@code buildUrl}, and {@code httpGet} methods are tested
 * directly to provide coverage without requiring a live FEAGI instance.
 */
class CorticalAreaResolverTest {

    // ── CorticalDimensions (record) ───────────────────────────────────────────

    @Test
    void dimensions_rejectsZeroWidth() {
        assertThrows(IllegalArgumentException.class,
                () -> new CorticalDimensions(0, 5, 1));
    }

    @Test
    void dimensions_rejectsNegativeWidth() {
        assertThrows(IllegalArgumentException.class,
                () -> new CorticalDimensions(-1, 5, 1));
    }

    @Test
    void dimensions_rejectsZeroHeight() {
        assertThrows(IllegalArgumentException.class,
                () -> new CorticalDimensions(5, 0, 1));
    }

    @Test
    void dimensions_rejectsNegativeHeight() {
        assertThrows(IllegalArgumentException.class,
                () -> new CorticalDimensions(5, -1, 1));
    }

    @Test
    void dimensions_rejectsZeroDepth() {
        assertThrows(IllegalArgumentException.class,
                () -> new CorticalDimensions(5, 5, 0));
    }

    @Test
    void dimensions_rejectsNegativeDepth() {
        assertThrows(IllegalArgumentException.class,
                () -> new CorticalDimensions(5, 5, -1));
    }

    @Test
    void dimensions_totalNeurons() {
        CorticalDimensions d = new CorticalDimensions(4, 5, 3);
        assertEquals(60L, d.totalNeurons());
    }

    @Test
    void dimensions_totalNeurons_noOverflowForLargeArea() {
        // 1300×1300×1300 = 2,197,000,000 which exceeds Integer.MAX_VALUE (2,147,483,647)
        // and would wrap to a negative int without the (long) cast in totalNeurons().
        CorticalDimensions d = new CorticalDimensions(1300, 1300, 1300);
        assertEquals(2_197_000_000L, d.totalNeurons());
        assertTrue(d.totalNeurons() > Integer.MAX_VALUE,
                "totalNeurons() must exceed Integer.MAX_VALUE to verify no int overflow");
    }

    @Test
    void dimensions_equalsAndHashCode() {
        // Records provide structural equals/hashCode automatically
        CorticalDimensions a = new CorticalDimensions(10, 20, 3);
        CorticalDimensions b = new CorticalDimensions(10, 20, 3);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void dimensions_notEqualWhenDifferent() {
        assertNotEquals(new CorticalDimensions(1, 2, 3), new CorticalDimensions(1, 2, 4));
    }

    @Test
    void dimensions_toString_containsAllFields() {
        // Record toString() includes all components by default
        String s = new CorticalDimensions(8, 16, 2).toString();
        assertTrue(s.contains("8") && s.contains("16") && s.contains("2"));
    }

    // ── buildUrl ──────────────────────────────────────────────────────────────

    @Test
    void buildUrl_correctFormat() {
        String url = DefaultCorticalAreaResolver.buildUrl("http", "127.0.0.1", 8000, "i__inf");
        assertEquals("http://127.0.0.1:8000/v1/genome/cortical_area/i__inf", url);
    }

    @Test
    void buildUrl_customHostAndPort() {
        String url = DefaultCorticalAreaResolver.buildUrl("http", "feagi-host", 9000, "o__mot");
        assertEquals("http://feagi-host:9000/v1/genome/cortical_area/o__mot", url);
    }

    @Test
    void buildUrl_httpsScheme() {
        String url = DefaultCorticalAreaResolver.buildUrl("https", "feagi-host", 443, "i__inf");
        assertEquals("https://feagi-host:443/v1/genome/cortical_area/i__inf", url);
    }

    @Test
    void resolve_hostWithInjectedPath_isRejectedByValidation() {
        // resolve() validates corticalAreaId before calling buildUrl.
        // A path-traversal cortical area ID must be caught at the validation step.
        assertThrows(IllegalArgumentException.class,
                () -> CorticalAreaResolver.resolveOnce("../../etc", "real-host", 8000));
    }

    @Test
    void resolve_corticalIdWithQueryChar_isRejectedByValidation() {
        // '?' in the cortical area ID must be caught by the regex guard.
        assertThrows(IllegalArgumentException.class,
                () -> CorticalAreaResolver.resolveOnce("id?inject=1", "real-host", 8000));
    }

    @Test
    void buildUrl_cleanInput_correctFormat() {
        // buildUrl is only called with pre-validated (safe) input from resolve().
        // Verify the happy path produces the expected URL.
        String url = DefaultCorticalAreaResolver.buildUrl("http", "real-host", 8000, "i__inf");
        assertEquals("http://real-host:8000/v1/genome/cortical_area/i__inf", url);
    }

    @Test
    void buildUrl_unvalidatedInput_throwsIllegalStateException() {
        // buildUrl's own guard rejects invalid IDs so the programming error
        // is caught even if called outside of resolve().
        assertThrows(IllegalStateException.class,
                () -> DefaultCorticalAreaResolver.buildUrl("http", "host", 8000, "../../etc"));
    }

    // ── parseDimensions ───────────────────────────────────────────────────────

    @Test
    void parseDimensions_typicalResponse() {
        String json = """
                {
                  "cortical_id": "i__inf",
                  "cortical_dimensions": [10, 20, 3],
                  "cortical_name": "infrared"
                }
                """;
        CorticalDimensions d = DefaultCorticalAreaResolver.parseDimensions(json, "i__inf");
        assertEquals(10, d.width());
        assertEquals(20, d.height());
        assertEquals(3,  d.depth());
    }

    @Test
    void parseDimensions_extraWhitespace() {
        String json = "{ \"cortical_dimensions\" : [ 5 , 8 , 2 ] }";
        CorticalDimensions d = DefaultCorticalAreaResolver.parseDimensions(json, "x");
        assertEquals(new CorticalDimensions(5, 8, 2), d);
    }

    @Test
    void parseDimensions_missingField_throwsFeagiSdkException() {
        String json = "{ \"cortical_id\": \"i__inf\" }";
        FeagiSdkException ex = assertThrows(FeagiSdkException.class,
                () -> DefaultCorticalAreaResolver.parseDimensions(json, "i__inf"));
        assertTrue(ex.getMessage().contains("cortical_dimensions"));
    }

    @Test
    void parseDimensions_wrongElementCount_throws() {
        // The regex requires exactly 3 integer groups — a 2-element array won't match,
        // so the exception message says "missing or malformed" rather than "3 elements".
        String json = "{ \"cortical_dimensions\": [10, 20] }"; // only 2 elements
        FeagiSdkException ex = assertThrows(FeagiSdkException.class,
                () -> DefaultCorticalAreaResolver.parseDimensions(json, "x"));
        assertTrue(ex.getMessage().contains("missing or malformed"),
                "Expected 'missing or malformed' in: " + ex.getMessage());
    }

    @Test
    void parseDimensions_nonIntegerValue_throws() {
        String json = "{ \"cortical_dimensions\": [10, 20, \"three\"] }";
        assertThrows(FeagiSdkException.class,
                () -> DefaultCorticalAreaResolver.parseDimensions(json, "x"));
    }

    @Test
    void parseDimensions_zeroDimension_throwsFeagiSdkException() {
        // CorticalDimensions rejects zero values with IAE; parseDimensions wraps it as FSE
        // so callers only need to catch FeagiSdkException from this method.
        String json = "{ \"cortical_dimensions\": [0, 20, 3] }";
        assertThrows(FeagiSdkException.class,
                () -> DefaultCorticalAreaResolver.parseDimensions(json, "x"));
    }

    @Test
    void parseDimensions_fieldFirstInJson() {
        // Ensure key is found regardless of field order
        String json = "{ \"cortical_dimensions\": [3, 4, 1], \"name\": \"x\" }";
        CorticalDimensions d = DefaultCorticalAreaResolver.parseDimensions(json, "x");
        assertEquals(new CorticalDimensions(3, 4, 1), d);
    }

    @Test
    void parseDimensions_keyInStringValue_falseMatchIsKnownLimitation() {
        // The true false-match scenario requires the verbatim text
        //   "cortical_dimensions": [w, h, d]
        // to appear inside a JSON string value WITHOUT escape sequences — i.e. as raw
        // JSON bytes. This cannot be constructed as a valid JSON string value using
        // Java string literals because the inner quotes would need to be escaped as \",
        // and the regex pattern matches " (unescaped quote), not \" (backslash-quote).
        //
        // In practice, the risk is a FEAGI response where a string field happens to
        // contain the exact unescaped key-colon-array sequence — unlikely in FEAGI's
        // API but documented here for future maintainers.
        //
        // What we CAN test: escaped inner quotes do NOT trigger the false match.
        // The regex correctly skips the escaped-quote sequence and finds the real key.
        String json = "{ \"note\": \"\\\"cortical_dimensions\\\": [99, 88, 77]\","
                    + " \"cortical_dimensions\": [5, 6, 2] }";
        CorticalDimensions d = DefaultCorticalAreaResolver.parseDimensions(json, "x");
        // Escaped inner quotes don't match — the real key is found correctly.
        assertEquals(new CorticalDimensions(5, 6, 2), d,
                "Escaped-quote variant must NOT false-match; real key must be found");
    }

    @Test
    void parseDimensions_keyMentionedInStringOnly_doesNotFalseMatch() {
        // A string value mentioning "cortical_dimensions" without the `: [w,h,d]` form
        // does NOT trigger the false-match — the regex requires the full key:array syntax.
        String json = "{ \"description\": \"cortical_dimensions is metadata\","
                    + " \"cortical_dimensions\": [5, 6, 2] }";
        CorticalDimensions d = DefaultCorticalAreaResolver.parseDimensions(json, "x");
        assertEquals(new CorticalDimensions(5, 6, 2), d);
    }

    // ── CorticalAreaResolver as injectable interface (#3) ─────────────────────

    @Test
    void resolver_canBeImplementedAsLambdaForTesting() {
        // The interface design means test code can inject a stub without a real network.
        CorticalAreaResolver stub = id -> Optional.of(new CorticalDimensions(10, 20, 3));
        Optional<CorticalDimensions> result = stub.resolve("i__inf");
        assertTrue(result.isPresent());
        assertEquals(new CorticalDimensions(10, 20, 3), result.get());
    }

    @Test
    void resolver_createReturnsDefaultImplementation() {
        // Verify the factory method returns a non-null working instance
        CorticalAreaResolver resolver = CorticalAreaResolver.create("127.0.0.1", 8000);
        assertNotNull(resolver);
        assertInstanceOf(DefaultCorticalAreaResolver.class, resolver);
    }

    // ── resolve() argument validation ─────────────────────────────────────────

    @Test
    void resolve_nullAreaId_throws() {
        assertThrows(NullPointerException.class,
                () -> CorticalAreaResolver.resolveOnce(null, "localhost", 8000));
    }

    @Test
    void resolve_blankAreaId_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> CorticalAreaResolver.resolveOnce("  ", "localhost", 8000));
    }

    @Test
    void resolve_areaIdWithSlash_throws() {
        // Path traversal attempt must be rejected before the HTTP call
        assertThrows(IllegalArgumentException.class,
                () -> CorticalAreaResolver.resolveOnce("../../etc", "localhost", 8000));
    }

    @Test
    void resolve_areaIdWithQueryString_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> CorticalAreaResolver.resolveOnce("id?inject=1", "localhost", 8000));
    }

    @Test
    void resolve_areaIdWithLeadingHyphen_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> CorticalAreaResolver.resolveOnce("-bad", "localhost", 8000));
    }

    @Test
    void resolve_areaIdWithTrailingHyphen_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> CorticalAreaResolver.resolveOnce("bad-", "localhost", 8000));
    }

    @Test
    void resolve_areaIdWithInternalHyphen_matchesValidPattern() {
        // Verifies the regex accepts internal hyphens without a network call.
        // Testing the pattern directly is sufficient — the network path is covered
        // by the HttpServer-backed tests.
        assertTrue(DefaultCorticalAreaResolver.VALID_CORTICAL_ID
                .matcher("v1-motor").matches(),
                "Internal hyphens must be accepted by VALID_CORTICAL_ID");
        assertTrue(DefaultCorticalAreaResolver.VALID_CORTICAL_ID
                .matcher("i__inf").matches(),
                "Double-underscore IDs must be accepted");
    }

    @Test
    void resolve_portZero_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> CorticalAreaResolver.resolveOnce("i__inf", "localhost", 0));
    }

    @Test
    void resolve_portClearlyOutOfRange_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> CorticalAreaResolver.resolveOnce("i__inf", "localhost", 70000));
    }

    @Test
    void resolve_port65536_throws() {
        // 65536 is the first value above the valid range [1, 65535]
        assertThrows(IllegalArgumentException.class,
                () -> CorticalAreaResolver.resolveOnce("i__inf", "localhost", 65536));
    }

    @Test
    void resolve_port65535_isValid() {
        // 65535 is the upper boundary of the valid range — must not throw on construction
        CorticalAreaResolver resolver = CorticalAreaResolver.create("localhost", 65535);
        assertNotNull(resolver);
    }

    @Test
    void resolve_port1_isValid() {
        // 1 is the lower boundary of the valid range — must not throw on construction
        CorticalAreaResolver resolver = CorticalAreaResolver.create("localhost", 1);
        assertNotNull(resolver);
    }

    @Test
    void resolve_nullHost_throws() {
        assertThrows(NullPointerException.class,
                () -> CorticalAreaResolver.resolveOnce("i__inf", null, 8000));
    }

    @Test
    void resolve_blankHost_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> CorticalAreaResolver.create("  ", 8000));
        assertThrows(IllegalArgumentException.class,
                () -> CorticalAreaResolver.create("", 8000));
    }

    @Test
    void resolve_zeroTimeout_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> CorticalAreaResolver.resolveOnce("i__inf", "localhost", 8000, Duration.ZERO));
    }

    @Test
    void resolve_negativeTimeout_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> CorticalAreaResolver.resolveOnce("i__inf", "localhost", 8000,
                        Duration.ofSeconds(-1)));
    }

    @Test
    void resolve_unreachableHost_returnsEmpty() throws Exception {
        // Open a ServerSocket on an ephemeral port, close it immediately, then use that
        // port. This guarantees ECONNREFUSED without relying on port 1 being refused,
        // which can flake in some CI environments or with certain firewall configurations.
        int refusedPort;
        try (java.net.ServerSocket ss = new java.net.ServerSocket(0)) {
            refusedPort = ss.getLocalPort();
        } // port is now closed — any connect attempt will be refused immediately

        Optional<CorticalDimensions> result =
                CorticalAreaResolver.resolveOnce("i__inf", "127.0.0.1", refusedPort,
                        Duration.ofMillis(500));
        assertTrue(result.isEmpty(),
                "Connection-refused must return Optional.empty(), not throw");
    }

    // ── httpGet — non-200/404 status codes (#3) ───────────────────────────────

    @Test
    void httpGet_500response_throwsFeagiSdkException() throws Exception {
        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(
                        new java.net.InetSocketAddress(0), 0);
        server.createContext("/v1/genome/cortical_area/i__inf", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            String url = DefaultCorticalAreaResolver.buildUrl("http", "127.0.0.1", port, "i__inf");
            assertThrows(FeagiSdkException.class,
                    () -> DefaultCorticalAreaResolver.httpGet(url, Duration.ofSeconds(2)));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void httpGet_503response_throwsFeagiSdkException() throws Exception {
        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(
                        new java.net.InetSocketAddress(0), 0);
        server.createContext("/v1/genome/cortical_area/i__inf", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            String url = DefaultCorticalAreaResolver.buildUrl("http", "127.0.0.1", port, "i__inf");
            assertThrows(FeagiSdkException.class,
                    () -> DefaultCorticalAreaResolver.httpGet(url, Duration.ofSeconds(2)));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void httpGet_404response_returnsEmpty() throws Exception {
        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(
                        new java.net.InetSocketAddress(0), 0);
        server.createContext("/v1/genome/cortical_area/i__inf", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            String url = DefaultCorticalAreaResolver.buildUrl("http", "127.0.0.1", port, "i__inf");
            Optional<String> result =
                    DefaultCorticalAreaResolver.httpGet(url, Duration.ofSeconds(2));
            assertTrue(result.isEmpty());
        } finally {
            server.stop(0);
        }
    }

    // ── Integration shape test (skipped without live FEAGI) ──────────────────
    // To run against a live FEAGI instance:
    //   CorticalAreaResolver.resolveOnce("i__inf", "your-feagi-host", 8000)
    //          .ifPresent(d -> System.out.println("dims: " + d));
    //
    // Not automated here because CI has no FEAGI dependency.
}
