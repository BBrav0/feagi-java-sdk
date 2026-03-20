/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core.transport;

import io.feagi.sdk.core.AgentConfig;
import io.feagi.sdk.core.AgentType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.SocketType;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * ZMQ Push/Pull implementation of FEAGI sensory/motor transport.
 */
public class ZmqTransport implements Transport {
    private static final Logger LOG = Logger.getLogger(ZmqTransport.class.getName());

    private final ZContext context;
    private ZMQ.Socket sensorySocket;
    private ZMQ.Socket motorSocket;

    /**
     * Create a new ZMQ transport initialized from the provided agent configuration.
     *
     * @param config the agent configuration defining endpoints and capabilities.
     */
    public ZmqTransport(AgentConfig config) {
        Objects.requireNonNull(config, "config must not be null");

        this.context = new ZContext();
        boolean hasSensory = false;
        boolean hasMotor = false;

        AgentType agentType = config.agentType();

        switch (agentType) {
            case SENSORY:
                hasSensory = true;
                break;
            case MOTOR:
                hasMotor = true;
                break;
            case BOTH:
                hasSensory = true;
                hasMotor = true;
                break;
            default:
                // Visualization or infrastructure may not use sensory/motor transport in the same way,
                // but if endpoints are present, they could be supported if needed.
                break;
        }

        try {
            if (hasSensory) {
                String sensoryEndpoint = config.endpoints().sensoryEndpoint();
                if (sensoryEndpoint != null && !sensoryEndpoint.isEmpty()) {
                    sensorySocket = context.createSocket(SocketType.PUSH);
                    if (config.sensorySocketConfig() != null) {
                        sensorySocket.setSndHWM(config.sensorySocketConfig().sendHwm());
                        sensorySocket.setLinger(config.sensorySocketConfig().lingerMs());
                        if (config.sensorySocketConfig().immediate()) {
                            sensorySocket.setImmediate(true);
                        }
                    }
                    LOG.info("Connecting ZMQ PUSH to sensory endpoint: " + sensoryEndpoint);
                    sensorySocket.connect(sensoryEndpoint);
                } else {
                    LOG.warning("AgentType indicates sensory, but no sensory endpoint was provided.");
                }
            }

            if (hasMotor) {
                String motorEndpoint = config.endpoints().motorEndpoint();
                if (motorEndpoint != null && !motorEndpoint.isEmpty()) {
                    motorSocket = context.createSocket(SocketType.PULL);
                    // Standard config for motor socket polling
                    motorSocket.setRcvHWM(1);
                    motorSocket.setLinger(0);
                    motorSocket.setConflate(true); // Always drop old messages, only get the latest
                    LOG.info("Connecting ZMQ PULL to motor endpoint: " + motorEndpoint);
                    motorSocket.connect(motorEndpoint);
                } else {
                    LOG.warning("AgentType indicates motor, but no motor endpoint was provided.");
                }
            }
        } catch (Exception e) {
            close();
            throw new RuntimeException("Failed to initialize ZMQ Transport", e);
        }
    }

    @Override
    public void sendSensoryBytes(byte[] payload) {
        if (sensorySocket == null) {
            throw new IllegalStateException("Sensory socket is not initialized");
        }
        if (payload != null && payload.length > 0) {
            // Non-blocking send by default so we don't stall the agent
            boolean sent = sensorySocket.send(payload, ZMQ.DONTWAIT);
            if (!sent) {
                LOG.fine("Sensory payload dropped (ZMQ PUSH queue full)");
            }
        }
    }

    @Override
    public byte[] pollMotorBytes() {
        if (motorSocket == null) {
            throw new IllegalStateException("Motor socket is not initialized");
        }
        // Non-blocking receive
        return motorSocket.recv(ZMQ.DONTWAIT);
    }

    @Override
    public void close() {
        if (sensorySocket != null) {
            sensorySocket.close();
            sensorySocket = null;
        }
        if (motorSocket != null) {
            motorSocket.close();
            motorSocket = null;
        }
        if (context != null) {
            context.close();
        }
    }
}
