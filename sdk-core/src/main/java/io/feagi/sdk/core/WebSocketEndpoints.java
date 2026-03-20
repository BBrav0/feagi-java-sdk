/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.util.Objects;

/**
 * Explicit FEAGI WebSocket endpoints.
 *
 * <p>Guardrail: no defaults. Callers must provide all endpoints explicitly or via a deterministic
 * registration response.
 */
public final class WebSocketEndpoints {
    private final String registrationEndpoint;
    private final String sensoryEndpoint;
    private final String motorEndpoint;
    private final String visualizationEndpoint;
    private final String controlEndpoint;

    /**
     * Create a WebSocket endpoint set. Endpoints not applicable to the agent type may be {@code null}.
     *
     * @param registrationEndpoint required registration endpoint (ws://...)
     * @param sensoryEndpoint optional sensory endpoint (ws://...)
     * @param motorEndpoint optional motor endpoint (ws://...)
     * @param visualizationEndpoint optional visualization endpoint (ws://...)
     * @param controlEndpoint optional control endpoint (ws://...)
     */
    public WebSocketEndpoints(
            String registrationEndpoint,
            String sensoryEndpoint,
            String motorEndpoint,
            String visualizationEndpoint,
            String controlEndpoint
    ) {
        this.registrationEndpoint = requireWsEndpoint(registrationEndpoint, "registrationEndpoint");
        this.sensoryEndpoint = requireOptionalWsEndpoint(sensoryEndpoint, "sensoryEndpoint");
        this.motorEndpoint = requireOptionalWsEndpoint(motorEndpoint, "motorEndpoint");
        this.visualizationEndpoint = requireOptionalWsEndpoint(
                visualizationEndpoint, "visualizationEndpoint");
        this.controlEndpoint = requireOptionalWsEndpoint(controlEndpoint, "controlEndpoint");
    }

    private static String requireWsEndpoint(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        if (!value.startsWith("ws://") && !value.startsWith("wss://")) {
            throw new IllegalArgumentException(name + " must start with ws:// or wss://");
        }
        return value;
    }

    private static String requireOptionalWsEndpoint(String value, String name) {
        if (value == null) {
            return null;
        }
        return requireWsEndpoint(value, name);
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
