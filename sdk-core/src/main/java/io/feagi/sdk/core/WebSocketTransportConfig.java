/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

/**
 * Sealed interface for WebSocket transport configuration modes.
 *
 * <p>Permitted implementations:
 * - {@link WebSocketClientConfig} - client mode (connects to relay)
 * - {@link WebSocketRelayConfig} - server/relay mode (listens for connections)
 */
public sealed interface WebSocketTransportConfig
    permits WebSocketClientConfig, WebSocketRelayConfig {}
