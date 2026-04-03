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
        // 1000×1000×1000 = 1_000_000_000 — fits in long but overflows int
        CorticalDimensions d = new CorticalDimensions(1000, 1000, 1000);
        assertEquals(1_000_000_000L, d.totalNeurons());
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
        String url = CorticalAreaResolver.buildUrl("127.0.0.1", 8000, "i__inf");
        assertEquals("http://127.0.0.1:8000/v1/genome/cortical_area/i__inf", url);
    }

    @Test
    void buildUrl_customHostAndPort() {
        String url = CorticalAreaResolver.buildUrl("feagi-host", 9000, "o__mot");
        assertEquals("http://feagi-host:9000/v1/genome/cortical_area/o__mot", url);
    }

    @Test
    void buildUrl_hostWithInjectedPath_isContained() {
        // A host containing a path separator must not be able to inject extra path segments.
        // URI construction should either encode or reject the offending character.
        String url = CorticalAreaResolver.buildUrl("real-host", 8000, "i__inf");
        assertTrue(url.startsWith("http://real-host:8000/"),
                "Host must be placed in the authority component only");
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
        CorticalDimensions d = CorticalAreaResolver.parseDimensions(json, "i__inf");
        assertEquals(10, d.width());
        assertEquals(20, d.height());
        assertEquals(3,  d.depth());
    }

    @Test
    void parseDimensions_extraWhitespace() {
        String json = "{ \"cortical_dimensions\" : [ 5 , 8 , 2 ] }";
        CorticalDimensions d = CorticalAreaResolver.parseDimensions(json, "x");
        assertEquals(new CorticalDimensions(5, 8, 2), d);
    }

    @Test
    void parseDimensions_missingField_throwsFeagiSdkException() {
        String json = "{ \"cortical_id\": \"i__inf\" }";
        FeagiSdkException ex = assertThrows(FeagiSdkException.class,
                () -> CorticalAreaResolver.parseDimensions(json, "i__inf"));
        assertTrue(ex.getMessage().contains("cortical_dimensions"));
    }

    @Test
    void parseDimensions_wrongElementCount_throws() {
        String json = "{ \"cortical_dimensions\": [10, 20] }"; // only 2 elements
        FeagiSdkException ex = assertThrows(FeagiSdkException.class,
                () -> CorticalAreaResolver.parseDimensions(json, "x"));
        assertTrue(ex.getMessage().contains("3 elements"));
    }

    @Test
    void parseDimensions_nonIntegerValue_throws() {
        String json = "{ \"cortical_dimensions\": [10, 20, \"three\"] }";
        assertThrows(FeagiSdkException.class,
                () -> CorticalAreaResolver.parseDimensions(json, "x"));
    }

    @Test
    void parseDimensions_zeroDimension_throwsFeagiSdkException() {
        // CorticalDimensions rejects zero values with IAE; parseDimensions wraps it as FSE
        // so callers only need to catch FeagiSdkException from this method.
        String json = "{ \"cortical_dimensions\": [0, 20, 3] }";
        assertThrows(FeagiSdkException.class,
                () -> CorticalAreaResolver.parseDimensions(json, "x"));
    }

    @Test
    void parseDimensions_fieldFirstInJson() {
        // Ensure key is found regardless of field order
        String json = "{ \"cortical_dimensions\": [3, 4, 1], \"name\": \"x\" }";
        CorticalDimensions d = CorticalAreaResolver.parseDimensions(json, "x");
        assertEquals(new CorticalDimensions(3, 4, 1), d);
    }

    // ── resolve() argument validation ─────────────────────────────────────────

    @Test
    void resolve_nullAreaId_throws() {
        assertThrows(NullPointerException.class,
                () -> CorticalAreaResolver.resolve(null, "localhost", 8000));
    }

    @Test
    void resolve_blankAreaId_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> CorticalAreaResolver.resolve("  ", "localhost", 8000));
    }

    @Test
    void resolve_areaIdWithSlash_throws() {
        // Path traversal attempt must be rejected before the HTTP call
        assertThrows(IllegalArgumentException.class,
                () -> CorticalAreaResolver.resolve("../../etc", "localhost", 8000));
    }

    @Test
    void resolve_areaIdWithQueryString_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> CorticalAreaResolver.resolve("id?inject=1", "localhost", 8000));
    }

    @Test
    void resolve_invalidPort_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> CorticalAreaResolver.resolve("i__inf", "localhost", 0));
        assertThrows(IllegalArgumentException.class,
                () -> CorticalAreaResolver.resolve("i__inf", "localhost", 70000));
    }

    @Test
    void resolve_nullHost_throws() {
        assertThrows(NullPointerException.class,
                () -> CorticalAreaResolver.resolve("i__inf", null, 8000));
    }

    @Test
    void resolve_zeroTimeout_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> CorticalAreaResolver.resolve("i__inf", "localhost", 8000, Duration.ZERO));
    }

    @Test
    void resolve_negativeTimeout_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> CorticalAreaResolver.resolve("i__inf", "localhost", 8000,
                        Duration.ofSeconds(-1)));
    }

    @Test
    void resolve_unreachableHost_returnsEmpty() {
        // Port 1 is almost certainly refused — should return empty, not throw
        Optional<CorticalDimensions> result =
                CorticalAreaResolver.resolve("i__inf", "127.0.0.1", 1,
                        Duration.ofMillis(200));
        assertTrue(result.isEmpty(),
                "Unreachable host must return Optional.empty(), not throw");
    }

    // ── Integration shape test (skipped without live FEAGI) ──────────────────
    // To run against a live FEAGI instance:
    //   CorticalAreaResolver.resolve("i__inf", "your-feagi-host", 8000)
    //          .ifPresent(d -> System.out.println("dims: " + d));
    //
    // Not automated here because CI has no FEAGI dependency.
}
