/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.util.Objects;

/**
 * Shared validation helpers for WebSocket configuration classes.
 *
 * <p>Package-private utility class to avoid duplication of validation logic
 * across {@link WebSocketClientConfig} and {@link WebSocketRelayConfig}.
 */
final class WebSocketConfigValidation {

    private WebSocketConfigValidation() {
        // Utility class, prevent instantiation
    }

    /**
     * Validate that a string is non-null and non-empty.
     *
     * @param value the string to validate
     * @param name the parameter name for error messages
     * @return the validated string
     * @throws NullPointerException if value is null
     * @throws IllegalArgumentException if value is empty
     */
    static String requireNonEmptyString(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return value;
    }

    /**
     * Validate that a port number is in the valid range (1-65535).
     *
     * @param value the port number to validate
     * @param name the parameter name for error messages
     * @return the validated port number
     * @throws IllegalArgumentException if port is out of range
     */
    static int requireValidPort(int value, String name) {
        if (value < 1 || value > 65535) {
            throw new IllegalArgumentException(name + " must be between 1 and 65535, got " + value);
        }
        return value;
    }

    /**
     * Validate that a long value is positive (> 0).
     *
     * @param value the value to validate
     * @param name the parameter name for error messages
     * @return the validated value
     * @throws IllegalArgumentException if value is not positive
     */
    static long requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return value;
    }

    /**
     * Validate that a long value is non-negative (>= 0).
     *
     * @param value the value to validate
     * @param name the parameter name for error messages
     * @return the validated value
     * @throws IllegalArgumentException if value is negative
     */
    static long requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
        return value;
    }
}