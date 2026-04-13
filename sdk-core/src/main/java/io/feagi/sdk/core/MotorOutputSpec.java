/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import io.feagi.sdk.core.motor.Motor;
import io.feagi.sdk.core.motor.RotaryMotor;
import io.feagi.sdk.core.motor.ServoMotor;

import java.util.Objects;

/**
 * Specification for registering a motor output with BrainOutput.
 *
 * <p>MotorOutputSpec defines how a motor output from FEAGI should be mapped
 * to a named motor instance. This allows the BrainOutput system to decode
 * incoming motor data and make it available through type-specific accessors.
 *
 * <p>Example usage:
 * <pre>{@code
 * MotorOutputSpec servo = MotorOutputSpec.forServo("arm_joint", 0, 0)
 *     .angleRange(0, 360)
 *     .build();
 *
 * MotorOutputSpec wheel = MotorOutputSpec.forRotaryMotor("left_wheel", 1, 0)
 *     .maxSpeed(500)
 *     .speedUnit(RotaryMotor.SpeedUnit.RPM)
 *     .bidirectional(true)
 *     .build();
 * }</pre>
 */
public final class MotorOutputSpec {

    private final String name;
    private final MotorUnit motorUnit;
    private final int groupId;
    private final int outputIndex;

    // Servo-specific settings
    private final Double servoMinAngle;
    private final Double servoMaxAngle;
    private final Boolean servoInvertDirection;

    // Rotary motor-specific settings
    private final Double rotaryMaxSpeed;
    private final RotaryMotor.SpeedUnit rotarySpeedUnit;
    private final Boolean rotaryInvertDirection;
    private final Boolean rotaryBidirectional;

    private MotorOutputSpec(Builder builder) {
        this.name = builder.name;
        this.motorUnit = builder.motorUnit;
        this.groupId = builder.groupId;
        this.outputIndex = builder.outputIndex;
        this.servoMinAngle = builder.servoMinAngle;
        this.servoMaxAngle = builder.servoMaxAngle;
        this.servoInvertDirection = builder.servoInvertDirection;
        this.rotaryMaxSpeed = builder.rotaryMaxSpeed;
        this.rotarySpeedUnit = builder.rotarySpeedUnit;
        this.rotaryInvertDirection = builder.rotaryInvertDirection;
        this.rotaryBidirectional = builder.rotaryBidirectional;
    }

    /**
     * Create a builder for a positional servo motor specification.
     *
     * @param name        unique motor name
     * @param groupId     group ID (0-255)
     * @param outputIndex output index within the group
     * @return builder for servo motor spec
     */
    public static Builder forServo(String name, int groupId, int outputIndex) {
        return new Builder(name, MotorUnit.POSITIONAL_SERVO, groupId, outputIndex);
    }

    /**
     * Create a builder for a rotary motor specification.
     *
     * @param name        unique motor name
     * @param groupId     group ID (0-255)
     * @param outputIndex output index within the group
     * @return builder for rotary motor spec
     */
    public static Builder forRotaryMotor(String name, int groupId, int outputIndex) {
        return new Builder(name, MotorUnit.ROTARY_MOTOR, groupId, outputIndex);
    }

    /**
     * Create a builder for a generic motor specification.
     *
     * @param name        unique motor name
     * @param motorUnit   motor unit type
     * @param groupId     group ID (0-255)
     * @param outputIndex output index within the group
     * @return builder for motor spec
     */
    public static Builder forMotorUnit(String name, MotorUnit motorUnit, int groupId, int outputIndex) {
        return new Builder(name, motorUnit, groupId, outputIndex);
    }

    /**
     * Return the motor name.
     */
    public String getName() {
        return name;
    }

    /**
     * Return the motor unit type.
     */
    public MotorUnit getMotorUnit() {
        return motorUnit;
    }

    /**
     * Return the group ID.
     */
    public int getGroupId() {
        return groupId;
    }

    /**
     * Return the output index.
     */
    public int getOutputIndex() {
        return outputIndex;
    }

    /**
     * Create a Motor instance from this specification.
     *
     * @return new Motor instance appropriate for this spec
     */
    public Motor createMotor() {
        switch (motorUnit) {
            case POSITIONAL_SERVO:
                return createServoMotor();
            case ROTARY_MOTOR:
                return createRotaryMotor();
            default:
                // For other motor types, default to rotary motor behavior
                return createRotaryMotor();
        }
    }

    private ServoMotor createServoMotor() {
        ServoMotor.Builder builder = ServoMotor.builder()
                .name(name)
                .groupId(groupId)
                .outputIndex(outputIndex);

        if (servoMinAngle != null && servoMaxAngle != null) {
            builder.angleRange(servoMinAngle, servoMaxAngle);
        }
        if (servoInvertDirection != null) {
            builder.invertDirection(servoInvertDirection);
        }

        return builder.build();
    }

    private RotaryMotor createRotaryMotor() {
        RotaryMotor.Builder builder = RotaryMotor.builder()
                .name(name)
                .groupId(groupId)
                .outputIndex(outputIndex);

        if (rotaryMaxSpeed != null) {
            builder.maxSpeed(rotaryMaxSpeed);
        }
        if (rotarySpeedUnit != null) {
            builder.speedUnit(rotarySpeedUnit);
        }
        if (rotaryInvertDirection != null) {
            builder.invertDirection(rotaryInvertDirection);
        }
        if (rotaryBidirectional != null) {
            builder.bidirectional(rotaryBidirectional);
        }

        return builder.build();
    }

    /**
     * Return a unique key for this spec (groupId:outputIndex).
     */
    public String getKey() {
        return groupId + ":" + outputIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MotorOutputSpec that = (MotorOutputSpec) o;
        return groupId == that.groupId &&
                outputIndex == that.outputIndex &&
                Objects.equals(name, that.name) &&
                motorUnit == that.motorUnit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, motorUnit, groupId, outputIndex);
    }

    @Override
    public String toString() {
        return String.format("MotorOutputSpec{name='%s', unit=%s, group=%d, index=%d}",
                name, motorUnit, groupId, outputIndex);
    }

    /**
     * Builder for MotorOutputSpec instances.
     */
    public static final class Builder {
        private final String name;
        private final MotorUnit motorUnit;
        private final int groupId;
        private final int outputIndex;

        private Double servoMinAngle;
        private Double servoMaxAngle;
        private Boolean servoInvertDirection;

        private Double rotaryMaxSpeed;
        private RotaryMotor.SpeedUnit rotarySpeedUnit;
        private Boolean rotaryInvertDirection;
        private Boolean rotaryBidirectional;

        private Builder(String name, MotorUnit motorUnit, int groupId, int outputIndex) {
            Objects.requireNonNull(name, "name must not be null");
            if (name.isEmpty()) {
                throw new IllegalArgumentException("name must not be empty");
            }
            Objects.requireNonNull(motorUnit, "motorUnit must not be null");
            if (groupId < 0 || groupId > 255) {
                throw new IllegalArgumentException("groupId must be in [0, 255]");
            }
            if (outputIndex < 0) {
                throw new IllegalArgumentException("outputIndex must be >= 0");
            }

            this.name = name;
            this.motorUnit = motorUnit;
            this.groupId = groupId;
            this.outputIndex = outputIndex;
        }

        /**
         * Set the servo angle range (only applicable for POSITIONAL_SERVO).
         *
         * @param minAngle minimum angle in degrees
         * @param maxAngle maximum angle in degrees
         */
        public Builder angleRange(double minAngle, double maxAngle) {
            this.servoMinAngle = minAngle;
            this.servoMaxAngle = maxAngle;
            return this;
        }

        /**
         * Set whether to invert servo direction (only applicable for POSITIONAL_SERVO).
         */
        public Builder invertServoDirection(boolean invert) {
            this.servoInvertDirection = invert;
            return this;
        }

        /**
         * Set the maximum speed (only applicable for ROTARY_MOTOR).
         */
        public Builder maxSpeed(double maxSpeed) {
            this.rotaryMaxSpeed = maxSpeed;
            return this;
        }

        /**
         * Set the speed unit (only applicable for ROTARY_MOTOR).
         */
        public Builder speedUnit(RotaryMotor.SpeedUnit speedUnit) {
            this.rotarySpeedUnit = speedUnit;
            return this;
        }

        /**
         * Set whether to invert rotary motor direction (only applicable for ROTARY_MOTOR).
         */
        public Builder invertRotaryDirection(boolean invert) {
            this.rotaryInvertDirection = invert;
            return this;
        }

        /**
         * Set whether the rotary motor is bidirectional (only applicable for ROTARY_MOTOR).
         */
        public Builder bidirectional(boolean bidirectional) {
            this.rotaryBidirectional = bidirectional;
            return this;
        }

        /**
         * Build the MotorOutputSpec instance.
         */
        public MotorOutputSpec build() {
            return new MotorOutputSpec(this);
        }
    }
}