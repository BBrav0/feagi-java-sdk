/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.pns.outputs;

/**
 * Abstract base class for all FEAGI PNS (Peripheral Nervous System) output types.
 *
 * <p>This class provides the foundation for all output types in the FEAGI framework,
 * including motor outputs (servo, rotary), numeric streams, and text streams.
 * Subclasses must implement the cache registration and read methods for data
 * persistence.</p>
 *
 * <p>The output types correspond to the Python SDK's {@code feagi.pns.outputs} module
 * and support the FEAGI 2.0 neuromorphic data format.</p>
 *
 * <h2>Subclass Responsibilities</h2>
 * <ul>
 *   <li>{@link #_registerWithCache()} - Register this output with the FEAGI cache system</li>
 *   <li>{@link #_readFromCache()} - Read output data from the cache</li>
 * </ul>
 *
 * @see ServoMotor
 * @see RotaryMotor
 * @see OutputNumericStream
 * @see OutputTextStream
 */
public abstract class BaseOutput {

    /**
     * Group ID for organizing related outputs.
     * Used for multi-unit agent configurations.
     */
    protected Integer groupId;

    /**
     * Registration state flag.
     * Tracks whether this output has been registered with the cache.
     */
    private boolean registered = false;

    /**
     * Creates a new BaseOutput with no group ID.
     */
    protected BaseOutput() {
        this.groupId = null;
    }

    /**
     * Get the group ID for this output.
     *
     * @return the group ID, or null if not yet registered
     */
    public Integer groupId() {
        return groupId;
    }

    /**
     * Check if this output has been registered with the cache.
     *
     * @return true if registered, false otherwise
     */
    public boolean isRegistered() {
        return registered;
    }

    /**
     * Mark this output as registered.
     * Should be called by {@link #_registerWithCache()} after successful registration.
     *
     * @param groupId the assigned group ID
     */
    protected void markRegistered(int groupId) {
        this.groupId = groupId;
        this.registered = true;
    }

    /**
     * Register this output with the FEAGI cache system.
     *
     * <p>Subclasses must implement this method to perform type-specific
     * registration logic, such as:</p>
     * <ul>
     *   <li>Registering with the NPU (Neural Processing Unit)</li>
     *   <li>Setting up data buffers</li>
     *   <li>Configuring callback handlers</li>
     * </ul>
     *
     * @throws RuntimeException if registration fails
     */
    protected abstract void _registerWithCache();

    /**
     * Read output data from the FEAGI cache.
     *
     * <p>Subclasses must implement this method to read their specific
     * data format from the cache.</p>
     *
     * @throws RuntimeException if read operation fails
     */
    protected abstract void _readFromCache();

    /**
     * Validate that a numeric value is within the range [0, 255].
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

    /**
     * Validate that a value is in range [min, max].
     *
     * @param value the value to validate
     * @param min the minimum allowed value
     * @param max the maximum allowed value
     * @param fieldName the name of the field (for error messages)
     * @throws IllegalArgumentException if value is out of range
     */
    protected void validateRange(double value, double min, double max, String fieldName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                fieldName + " must be in range [" + min + ", " + max + "], got: " + value);
        }
    }

    /**
     * Clamp a value to the range [min, max].
     *
     * @param value the value to clamp
     * @param min the minimum allowed value
     * @param max the maximum allowed value
     * @return the clamped value
     */
    protected double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
