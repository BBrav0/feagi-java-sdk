/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

/**
 * Sealed interface for WebSocket transport configuration modes.
 *
 * <p>Permitted implementations:
 * <ul>
 *   <li>{@link WebSocketClientConfig} &mdash; client mode (connects to relay)</li>
 *   <li>{@link WebSocketRelayConfig} &mdash; server/relay mode (listens for connections)</li>
 * </ul>
 */
public sealed interface WebSocketTransportConfig
    permits WebSocketClientConfig, WebSocketRelayConfig {}
