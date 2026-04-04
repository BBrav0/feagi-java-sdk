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
 *
 * <p>Use {@link #createSnapshot(double, long)} to create immutable snapshots
 * of motor state that are safe to store and use across threads.
 */
public interface Motor {

    /**
     * Motor type enumeration.
     */
    enum MotorType {
        /**
         * Positional servo motor.
         */
        SERVO,
        /**
         * Continuous rotary motor.
         */
        ROTARY
    }

    /**
     * Immutable snapshot of motor state at a point in time.
     *
     * <p>Snapshots are safe to store and use across threads and will not
     * be affected by subsequent motor updates.
     */
    interface Snapshot {
        /**
         * Return the motor name.
         *
         * @return motor name
         */
        String getName();

        /**
         * Return the motor type.
         *
         * @return motor type
         */
        MotorType getMotorType();

        /**
         * Return the raw value from FEAGI.
         *
         * @return raw value (typically 0.0-1.0)
         */
        double getRawValue();

        /**
         * Return the snapshot timestamp.
         *
         * @return timestamp in milliseconds
         */
        long getTimestamp();

        /**
         * Return this snapshot as a ServoSnapshot.
         *
         * @return this cast to ServoSnapshot
         * @throws ClassCastException if not a servo snapshot
         */
        default ServoSnapshot asServo() {
            return (ServoSnapshot) this;
        }

        /**
         * Return this snapshot as a RotarySnapshot.
         *
         * @return this cast to RotarySnapshot
         * @throws ClassCastException if not a rotary snapshot
         */
        default RotarySnapshot asRotary() {
            return (RotarySnapshot) this;
        }

        /**
         * Check if this is a servo snapshot.
         *
         * @return true if servo snapshot
         */
        default boolean isServo() {
            return this instanceof ServoSnapshot;
        }

        /**
         * Check if this is a rotary snapshot.
         *
         * @return true if rotary snapshot
         */
        default boolean isRotary() {
            return this instanceof RotarySnapshot;
        }
    }

    /**
     * Snapshot for servo motors with angle information.
     */
    interface ServoSnapshot extends Snapshot {
        /**
         * Return the angle in degrees.
         *
         * @return angle in degrees
         */
        double getAngle();

        /**
         * Return the angle in radians.
         *
         * @return angle in radians
         */
        double getAngleRadians();

        /**
         * Return the normalized position (0.0 to 1.0).
         *
         * @return normalized position
         */
        double getNormalizedPosition();
    }

    /**
     * Snapshot for rotary motors with speed and direction information.
     */
    interface RotarySnapshot extends Snapshot {
        /**
         * Return the speed in the configured unit.
         *
         * @return speed value
         */
        double getSpeed();

        /**
         * Return the normalized speed.
         *
         * <p>For bidirectional motors, range is -1.0 to 1.0.
         * For unidirectional, range is 0.0 to 1.0.
         *
         * @return normalized speed
         */
        double getNormalizedSpeed();

        /**
         * Get the speed as a percentage of maximum speed.
         *
         * <p>For bidirectional motors, negative percentage indicates reverse direction.
         * To get a 0-100% range regardless of direction, use {@link #getSpeedPercentageAbsolute()}.
         *
         * @return speed as percentage (-100 to 100 for bidirectional, 0 to 100 otherwise)
         */
        double getSpeedPercentage();

        /**
         * Get the speed as an absolute percentage (always 0-100%).
         *
         * <p>Use {@link #getDirection()} to determine rotation direction.
         *
         * @return speed as percentage (0 to 100)
         */
        double getSpeedPercentageAbsolute();

        /**
         * Get the current direction.
         *
         * <p>For unidirectional motors, this always returns 1 (forward).
         * For bidirectional motors, returns 1 for forward, -1 for backward, 0 for stopped.
         *
         * @return direction indicator (-1, 0, or 1)
         */
        int getDirection();

        /**
         * Check if motor is currently stopped.
         *
         * @return true if speed is effectively zero
         */
        boolean isStopped();

        /**
         * Check if motor is moving forward.
         *
         * @return true if moving forward
         */
        boolean isMovingForward();

        /**
         * Check if motor is moving backward.
         *
         * @return true if moving backward (bidirectional only)
         */
        boolean isMovingBackward();
    }

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

    /**
     * Return the motor type.
     *
     * @return motor type
     */
    MotorType getMotorType();

    /**
     * Create an immutable snapshot of this motor's current state.
     *
     * @param rawValue  the raw FEAGI value
     * @param timestamp the snapshot timestamp
     * @return immutable snapshot
     */
    Snapshot createSnapshot(double rawValue, long timestamp);
}