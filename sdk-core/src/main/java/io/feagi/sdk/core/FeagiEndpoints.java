/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.util.Objects;

/**
 * Explicit FEAGI ZMQ endpoints.
 *
 * <p>Guardrail: no defaults. Callers must provide all endpoints explicitly or via a deterministic
 * registration response.
 */
public final class FeagiEndpoints {
    private final String registrationEndpoint;
    private final String sensoryEndpoint;
    private final String motorEndpoint;
    private final String visualizationEndpoint;
    private final String controlEndpoint;

    /**
     * Create an endpoint set. Endpoints not applicable to the agent type may be {@code null}.
     *
     * @param registrationEndpoint required registration endpoint (tcp://...)
     * @param sensoryEndpoint optional sensory endpoint (tcp://...)
     * @param motorEndpoint optional motor endpoint (tcp://...)
     * @param visualizationEndpoint optional visualization endpoint (tcp://...)
     * @param controlEndpoint optional control endpoint (tcp://...)
     */
    public FeagiEndpoints(
            String registrationEndpoint,
            String sensoryEndpoint,
            String motorEndpoint,
            String visualizationEndpoint,
            String controlEndpoint
    ) {
        this.registrationEndpoint = requireTcpEndpoint(registrationEndpoint, "registrationEndpoint");
        this.sensoryEndpoint = requireOptionalTcpEndpoint(sensoryEndpoint, "sensoryEndpoint");
        this.motorEndpoint = requireOptionalTcpEndpoint(motorEndpoint, "motorEndpoint");
        this.visualizationEndpoint = requireOptionalTcpEndpoint(
                visualizationEndpoint, "visualizationEndpoint");
        this.controlEndpoint = requireOptionalTcpEndpoint(controlEndpoint, "controlEndpoint");
    }

    private static String requireTcpEndpoint(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        if (!value.startsWith("tcp://")) {
            throw new IllegalArgumentException(name + " must start with tcp://");
        }
        return value;
    }

    private static String requireOptionalTcpEndpoint(String value, String name) {
        if (value == null) {
            return null;
        }
        return requireTcpEndpoint(value, name);
    }

    /**
     * Validate endpoints required by the agent type.
     *
     * @param agentType agent role driving required endpoints
     */
    public void validateForAgentType(AgentType agentType) {
        Objects.requireNonNull(agentType, "agentType must not be null");
        switch (agentType) {
            case SENSORY:
                requirePresent(sensoryEndpoint, "sensoryEndpoint");
                break;
            case MOTOR:
                requirePresent(motorEndpoint, "motorEndpoint");
                break;
            case BOTH:
                requirePresent(sensoryEndpoint, "sensoryEndpoint");
                requirePresent(motorEndpoint, "motorEndpoint");
                break;
            case VISUALIZATION:
                requirePresent(visualizationEndpoint, "visualizationEndpoint");
                break;
            case INFRASTRUCTURE:
                break;
            default:
                throw new IllegalArgumentException("Unsupported agentType: " + agentType);
        }
    }

    private static void requirePresent(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " must be set for this agent type");
        }
    }

    /**
     * Return the registration endpoint.
     */
    public String registrationEndpoint() {
        return registrationEndpoint;
    }

    /**
     * Return the sensory endpoint (may be null).
     */
    public String sensoryEndpoint() {
        return sensoryEndpoint;
    }

    /**
     * Return the motor endpoint (may be null).
     */
    public String motorEndpoint() {
        return motorEndpoint;
    }

    /**
     * Return the visualization endpoint (may be null).
     */
    public String visualizationEndpoint() {
        return visualizationEndpoint;
    }

    /**
     * Return the control endpoint (may be null).
     */
    public String controlEndpoint() {
        return controlEndpoint;
    }
}

