/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.pns.inputs;

/**
 * Abstract base class for all FEAGI PNS (Peripheral Nervous System) input types.
 *
 * <p>This class provides the foundation for all input types in the FEAGI framework,
 * including vision, numeric, text, and infrared inputs. Subclasses must implement
 * the cache registration and write methods for data persistence.</p>
 *
 * <p>The input types correspond to the Python SDK's {@code feagi.pns.inputs} module
 * and support the FEAGI 2.0 neuromorphic data format.</p>
 *
 * <h2>Subclass Responsibilities</h2>
 * <ul>
 *   <li>{@link #_registerWithCache()} - Register this input with the FEAGI cache system</li>
 *   <li>{@link #_writeToCache(Object)} - Write input data to the cache</li>
 * </ul>
 *
 * @see Camera
 * @see NumericStream
 * @see TextStream
 * @see InfraredInput
 */
public abstract class BaseInput<T> {

    /**
     * Group ID for organizing related inputs.
     * Used for multi-unit agent configurations.
     */
    protected final int groupId;

    /**
     * Registration state flag.
     * Tracks whether this input has been registered with the cache.
     */
    private volatile boolean registered = false;

    /**
     * Creates a new BaseInput with the specified group ID.
     *
     * @param groupId the group ID for this input (0-255)
     * @throws IllegalArgumentException if groupId is out of range [0, 255]
     */
    protected BaseInput(int groupId) {
        validateRange(groupId, "groupId");
        this.groupId = groupId;
    }

    /**
     * Creates a new BaseInput with default group ID (0).
     */
    protected BaseInput() {
        this(0);
    }

    /**
     * Get the group ID for this input.
     *
     * @return the group ID
     */
    public int groupId() {
        return groupId;
    }

    /**
     * Check if this input has been registered with the cache.
     *
     * @return true if registered, false otherwise
     */
    public boolean isRegistered() {
        return registered;
    }

    /**
     * Mark this input as registered.
     * Should be called by {@link #_registerWithCache()} after successful registration.
     */
    protected void markRegistered() {
        this.registered = true;
    }

    /**
     * Register this input with the FEAGI cache system.
     *
     * <p>Subclasses must implement this method to perform type-specific
     * registration logic, such as:</p>
     * <ul>
     *   <li>Allocating shared memory regions</li>
     *   <li>Registering with the NPU (Neural Processing Unit)</li>
     *   <li>Setting up data buffers</li>
     * </ul>
     *
     * @throws RuntimeException if registration fails
     */
    protected abstract void _registerWithCache();

    /**
     * Write input data to the FEAGI cache.
     *
     * <p>Subclasses must implement this method to write their specific
     * data format to the cache. The data type parameter should match
     * the expected type for the subclass.</p>
     *
     * @param data the data to write
     * @throws RuntimeException if write operation fails
     */
    protected abstract void _writeToCache(T data);

    /**
     * Validate that a numeric value is within the valid range [0, 255].
     *
     * @param value the value to validate
     * @param fieldName the name of the field (for error messages)
     * @throws IllegalArgumentException if value is out of range
     */
    protected void validateRange(int value, String fieldName) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(
                fieldName + " must be in range [0, 255], got: " + value);
        }
    }

    /**
     * Validate that a numeric value is positive.
     *
     * @param value the value to validate
     * @param fieldName the name of the field (for error messages)
     * @throws IllegalArgumentException if value is not positive
     */
    protected void validatePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                fieldName + " must be positive, got: " + value);
        }
    }

    /**
     * Validate that a numeric value is positive (double).
     *
     * @param value the value to validate
     * @param fieldName the name of the field (for error messages)
     * @throws IllegalArgumentException if value is not positive
     */
    protected void validatePositive(double value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                fieldName + " must be positive, got: " + value);
        }
    }

    /**
     * Validate that a string is not null or empty.
     *
     * @param value the value to validate
     * @param fieldName the name of the field (for error messages)
     * @throws NullPointerException if value is null
     * @throws IllegalArgumentException if value is empty
     */
    protected void validateNotEmpty(String value, String fieldName) {
        if (value == null) {
            throw new NullPointerException(fieldName + " must not be null");
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
    }
}
