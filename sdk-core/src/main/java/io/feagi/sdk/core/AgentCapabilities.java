/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Declared agent capabilities (vision, motor, visualization, sensory, and custom JSON).
 *
 * <p>This class supports both the legacy capability format and the FEAGI 2.0
 * {@code vision_unit} / {@code motor_unit} format for compatibility with the
 * Python SDK.</p>
 *
 * <p>FEAGI 2.0 format example:
 * <pre>{@code
 * // Vision unit format
 * AgentCapabilities caps = AgentCapabilities.builder()
 *     .visionUnit(VisionUnitConfig.builder()
 *         .modality("camera")
 *         .resolution(640, 480)
 *         .channels(3)
 *         .unit(0)
 *         .group(0)
 *         .build())
 *     .motorUnit(MotorUnitConfig.builder()
 *         .type("servo")
 *         .range(0.0, 180.0)
 *         .unit(0)
 *         .group(1)
 *         .build())
 *     .build();
 *
 * // Multiple motor units format
 * AgentCapabilities caps = AgentCapabilities.builder()
 *     .visionUnit(vision)
 *     .addMotorUnit(servoMotor)
 *     .addMotorUnit(rotaryMotor)
 *     .build();
 * }</pre>
 *
 * @see VisionUnitConfig
 * @see MotorUnitConfig
 */
public final class AgentCapabilities {
    private final VisionCapability vision;
    private final MotorCapability motor;
    private final VisualizationCapability visualization;
    private final SensoryCapability sensory;
    private final Map<String, String> customCapabilitiesJson;

    // FEAGI 2.0 vision_unit support
    private final VisionUnitConfig visionUnit;

    // FEAGI 2.0 motor_unit / motor_units support
    private final MotorUnitConfig motorUnit;
    private final List<MotorUnitConfig> motorUnits;

    private AgentCapabilities(Builder builder) {
        this.vision = builder.vision;
        this.motor = builder.motor;
        this.visualization = builder.visualization;
        this.sensory = builder.sensory;
        this.customCapabilitiesJson = Collections.unmodifiableMap(new LinkedHashMap<>(
                builder.customCapabilitiesJson));
        this.visionUnit = builder.visionUnit;
        this.motorUnit = builder.motorUnit;
        this.motorUnits = Collections.unmodifiableList(new ArrayList<>(builder.motorUnits));
        validateAtLeastOne();
    }

    /**
     * Create a builder for agent capabilities.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Return vision capability (nullable).
     */
    public VisionCapability vision() {
        return vision;
    }

    /**
     * Return motor capability (nullable).
     */
    public MotorCapability motor() {
        return motor;
    }

    /**
     * Return visualization capability (nullable).
     */
    public VisualizationCapability visualization() {
        return visualization;
    }

    /**
     * Return sensory capability (nullable).
     */
    public SensoryCapability sensory() {
        return sensory;
    }

    /**
     * Return custom capability JSON map.
     */
    public Map<String, String> customCapabilitiesJson() {
        return customCapabilitiesJson;
    }

    /**
     * Return the FEAGI 2.0 vision unit configuration (nullable).
     *
     * @return vision unit config, or null if using legacy format
     */
    public VisionUnitConfig visionUnit() {
        return visionUnit;
    }

    /**
     * Return the FEAGI 2.0 primary motor unit configuration (nullable).
     *
     * @return motor unit config, or null if using legacy format or motor_units
     */
    public MotorUnitConfig motorUnit() {
        return motorUnit;
    }

    /**
     * Return the FEAGI 2.0 multiple motor units list.
     *
     * @return unmodifiable list of motor unit configs
     */
    public List<MotorUnitConfig> motorUnits() {
        return motorUnits;
    }

    /**
     * Check if using FEAGI 2.0 vision_unit format.
     *
     * @return true if visionUnit is set
     */
    public boolean hasVisionUnit() {
        return visionUnit != null;
    }

    /**
     * Check if using FEAGI 2.0 motor_unit format (single).
     *
     * @return true if motorUnit is set and motorUnits is empty
     */
    public boolean hasMotorUnit() {
        return motorUnit != null && motorUnits.isEmpty();
    }

    /**
     * Check if using FEAGI 2.0 motor_units format (multiple).
     *
     * @return true if motorUnits is not empty
     */
    public boolean hasMotorUnits() {
        return !motorUnits.isEmpty();
    }

    /**
     * Validate capabilities against an agent type.
     *
     * @param agentType agent role driving required capabilities
     */
    public void validateForAgentType(AgentType agentType) {
        Objects.requireNonNull(agentType, "agentType must not be null");
        validateAtLeastOne();
        switch (agentType) {
            case SENSORY:
                if (vision == null && sensory == null && customCapabilitiesJson.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Sensory agent must declare at least one input capability");
                }
                break;
            case MOTOR:
                if (motor == null) {
                    throw new IllegalArgumentException(
                            "Motor agent must declare motor capability");
                }
                break;
            case BOTH:
                boolean hasInput = vision != null || sensory != null || !customCapabilitiesJson.isEmpty();
                if (!hasInput || motor == null) {
                    throw new IllegalArgumentException(
                            "Bidirectional agent must declare both input and motor capabilities");
                }
                break;
            case VISUALIZATION:
                if (visualization == null) {
                    throw new IllegalArgumentException(
                            "Visualization agent must declare visualization capability");
                }
                break;
            case INFRASTRUCTURE:
                if (vision == null
                        && sensory == null
                        && motor == null
                        && visualization == null
                        && customCapabilitiesJson.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Infrastructure agent must declare at least one capability");
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported agentType: " + agentType);
        }
    }

    private void validateAtLeastOne() {
        boolean hasLegacyCapability = (vision != null || motor != null || visualization != null || sensory != null || !customCapabilitiesJson.isEmpty());
        boolean hasFeagi2Capability = (visionUnit != null || motorUnit != null || !motorUnits.isEmpty());

        if (!hasLegacyCapability && !hasFeagi2Capability) {
            throw new IllegalArgumentException(
                    "Agent must declare at least one capability (legacy or FEAGI 2.0 format)");
        }
    }

    /**
     * Builder for AgentCapabilities.
     */
    public static final class Builder {
        private VisionCapability vision;
        private MotorCapability motor;
        private VisualizationCapability visualization;
        private SensoryCapability sensory;
        private final Map<String, String> customCapabilitiesJson = new LinkedHashMap<>();

        // FEAGI 2.0 vision_unit / motor_unit support
        private VisionUnitConfig visionUnit;
        private MotorUnitConfig motorUnit;
        private final List<MotorUnitConfig> motorUnits = new ArrayList<>();

        private Builder() {}

        /**
         * Set vision capability.
         */
        public Builder vision(VisionCapability vision) {
            this.vision = Objects.requireNonNull(vision, "vision must not be null");
            return this;
        }

        /**
         * Set motor capability.
         */
        public Builder motor(MotorCapability motor) {
            this.motor = Objects.requireNonNull(motor, "motor must not be null");
            return this;
        }

        /**
         * Set visualization capability.
         */
        public Builder visualization(VisualizationCapability visualization) {
            this.visualization = Objects.requireNonNull(
                    visualization, "visualization must not be null");
            return this;
        }

        /**
         * Set sensory capability.
         */
        public Builder sensory(SensoryCapability sensory) {
            this.sensory = Objects.requireNonNull(sensory, "sensory must not be null");
            return this;
        }

        /**
         * Add custom capability JSON by key.
         */
        public Builder customCapabilityJson(String key, String jsonValue) {
            Objects.requireNonNull(key, "key must not be null");
            if (key.isEmpty()) {
                throw new IllegalArgumentException("key must not be empty");
            }
            Objects.requireNonNull(jsonValue, "jsonValue must not be null");
            if (jsonValue.isEmpty()) {
                throw new IllegalArgumentException("jsonValue must not be empty");
            }
            customCapabilitiesJson.put(key, jsonValue);
            return this;
        }

        /**
         * Set the FEAGI 2.0 vision_unit configuration.
         *
         * @param visionUnit vision unit config
         * @return this builder
         */
        public Builder visionUnit(VisionUnitConfig visionUnit) {
            this.visionUnit = Objects.requireNonNull(visionUnit, "visionUnit must not be null");
            return this;
        }

        /**
         * Set the FEAGI 2.0 motor_unit configuration (single motor unit).
         *
         * @param motorUnit motor unit config
         * @return this builder
         */
        public Builder motorUnit(MotorUnitConfig motorUnit) {
            this.motorUnit = Objects.requireNonNull(motorUnit, "motorUnit must not be null");
            return this;
        }

        /**
         * Add a FEAGI 2.0 motor_unit to the motor_units list.
         *
         * @param motorUnit motor unit config to add
         * @return this builder
         */
        public Builder addMotorUnit(MotorUnitConfig motorUnit) {
            Objects.requireNonNull(motorUnit, "motorUnit must not be null");
            this.motorUnits.add(motorUnit);
            return this;
        }

        /**
         * Build immutable capabilities.
         */
        public AgentCapabilities build() {
            return new AgentCapabilities(this);
        }
    }
}
