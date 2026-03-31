/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core.pns;

/**
 * Payload source for a BrainInput input slot.
 *
 * <p>Implementations must provide deterministic binary payloads for each send cycle.
 */
@FunctionalInterface
public interface BrainInputInput {
    /**
     * Return the encoded bytes for this input on the current tick.
     */
    byte[] encode();
}