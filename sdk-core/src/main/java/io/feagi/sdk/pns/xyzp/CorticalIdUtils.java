/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package io.feagi.sdk.pns.xyzp;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utilities for parsing FEAGI "CorticalID" wire-format bytes.
 *
 * <p>Python reference semantics (`_parse_cortical_unit_index_from_b64` and
 * `_parse_cortical_id_bytes`) define:
 * <ul>
 *   <li>Preferred: base64-encoded raw 8 bytes, where byte #7 is the unit/group index</li>
 *   <li>Legacy fallback: a raw 8-character string where each character maps to a byte
 *       via latin-1/ISO-8859-1</li>
 * </ul>
 */
public final class CorticalIdUtils {
    private static final Charset LEGACY_CHARSET = StandardCharsets.ISO_8859_1;
    private static final int RAW_LEN_BYTES = 8;

    private CorticalIdUtils() {}

    /**
     * Parse the cortical unit index (group) from a CorticalID.
     *
     * @return unit index [0..255] if parse succeeds; otherwise {@code null}.
     */
    public static Integer parseCorticalUnitIndex(String corticalId) {
        byte[] raw = parseRawCorticalIdBytes(corticalId);
        if (raw == null) {
            return null;
        }
        // Python bytes indexing returns int 0..255 for raw[7]; replicate with unsigned conversion.
        return raw[7] & 0xFF;
    }

    /**
     * Parse raw 8-byte CorticalID payload.
     *
     * <p>Returns {@code null} when parsing fails.
     */
    public static byte[] parseRawCorticalIdBytes(String corticalId) {
        if (corticalId == null) {
            return null;
        }

        // Preferred wire format: base64 (strict validation).
        try {
            byte[] decoded = Base64.getDecoder().decode(corticalId);
            if (decoded.length == RAW_LEN_BYTES) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid base64: fall through to legacy parsing.
        }

        // Legacy fallback: 8-character latin-1 string -> 8 bytes.
        if (corticalId.length() == RAW_LEN_BYTES) {
            byte[] legacy = corticalId.getBytes(LEGACY_CHARSET);
            if (legacy.length == RAW_LEN_BYTES) {
                return legacy;
            }
        }

        return null;
    }
}

