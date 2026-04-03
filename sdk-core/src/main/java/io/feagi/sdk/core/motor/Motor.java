/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core.motor;

/**
 * Base interface for all motor types in the FEAGI SDK.
 *
 * <p>Motor implementations represent physical actuators that receive control
 * signals from FEAGI's brain output. Each motor type provides specialized
 * accessors for its particular control parameters.
 */
public interface Motor {

    /**
     * Return the unique name/identifier for this motor.
     *
     * @return motor name
     */
    String getName();

    /**
     * Return the group ID this motor belongs to.
     *
     * @return group ID (0-255)
     */
    int getGroupId();

    /**
     * Return the output index within the group.
     *
     * @return output index
     */
    int getOutputIndex();

    /**
     * Return the raw value received from FEAGI.
     *
     * @return raw motor value
     */
    double getRawValue();

    /**
     * Return true if this motor has received data in the last frame.
     *
     * @return true if data was received
     */
    boolean hasData();

    /**
     * Return the timestamp of the last data update.
     *
     * @return timestamp in milliseconds since epoch, or 0 if no data
     */
    long getLastUpdateTimestamp();
}