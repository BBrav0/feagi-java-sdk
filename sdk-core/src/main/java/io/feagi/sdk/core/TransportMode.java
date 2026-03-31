/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

/**
 * Supported transport modes for FEAGI agent communication.
 */
public enum TransportMode {
    /** ZMQ transport (TCP-based) */
    ZMQ("zmq"),
    /** WebSocket transport (WS-based) */
    WEBSOCKET("websocket");

    private final String preferenceString;

    TransportMode(String preferenceString) {
        this.preferenceString = preferenceString;
    }

    /**
     * Return the transport preference string for native binding use.
     */
    public String toPreferenceString() {
        return preferenceString;
    }
}
