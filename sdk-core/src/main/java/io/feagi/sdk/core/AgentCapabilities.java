/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Declared agent capabilities (vision, motor, visualization, sensory, and custom JSON).
 */
public final class AgentCapabilities {
    private final VisionCapability vision;
    private final MotorCapability motor;
    private final VisualizationCapability visualization;
    private final SensoryCapability sensory;
    private final Map<String, String> customCapabilitiesJson;

    private AgentCapabilities(Builder builder) {
        this.vision = builder.vision;
        this.motor = builder.motor;
        this.visualization = builder.visualization;
        this.sensory = builder.sensory;
        this.customCapabilitiesJson = Collections.unmodifiableMap(new LinkedHashMap<>(
                builder.customCapabilitiesJson));
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
        if (vision == null
                && motor == null
                && visualization == null
                && sensory == null
                && customCapabilitiesJson.isEmpty()) {
            throw new IllegalArgumentException(
                    "Agent must declare at least one capability");
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
         * Build immutable capabilities.
         */
        public AgentCapabilities build() {
            return new AgentCapabilities(this);
        }
    }
}
