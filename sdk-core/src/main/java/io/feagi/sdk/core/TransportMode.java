/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

/**
 * Supported transport backends.
 */
public enum TransportMode {
    ZMQ,
    WEBSOCKET;

    /**
     * Parse a transport mode from caller input.
     *
     * @param value transport name (e.g. "zmq" or "websocket")
     * @return resolved mode
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

    /**
     * Return FEAGI registration preference string.
     */
    public String toPreferenceString() {
        return switch (this) {
            case ZMQ -> "zmq";
            case WEBSOCKET -> "websocket";
        };
    }
}
