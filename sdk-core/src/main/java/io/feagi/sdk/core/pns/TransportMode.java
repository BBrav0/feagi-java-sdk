/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core.pns;

/**
 * Supported BrainInput transport backends.
 *
 * <p>Transport names are intentionally explicit so that callers cannot rely on hidden defaults.
 */
public enum TransportMode {
    ZMQ,
    WEBSOCKET;

    /**
     * Parse transport type from caller input.
     *
     * @param value transport name (e.g. "zmq" or "websocket")
     * @return resolved transport type
     * @throws IllegalArgumentException if transport is not supported
     */
    public static TransportMode from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("transport must not be null or blank");
        }
        return switch (value.trim().toLowerCase()) {
            case "zmq" -> ZMQ;
            case "websocket", "ws", "wss" -> WEBSOCKET;
            default -> throw new IllegalArgumentException("Unsupported transport: " + value);
        };
    }
}
