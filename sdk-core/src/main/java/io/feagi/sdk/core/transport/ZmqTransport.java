/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core.transport;

import io.feagi.sdk.core.AgentConfig;
import io.feagi.sdk.core.AgentType;
import io.feagi.sdk.core.FeagiSdkException;
import io.feagi.sdk.core.MotorSocketConfig;
import io.feagi.sdk.core.SensorySocketConfig;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.SocketType;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * ZMQ Push/Pull implementation of FEAGI sensory/motor transport.
 *
 * <p>Public methods ({@link #sendSensoryBytes}, {@link #pollMotorBytes}, {@link #close}) use a
 * common lock so they may be invoked concurrently from multiple threads without data races on the
 * underlying ZMQ sockets (Jeromq sockets are not thread-safe). Operations are serialized on this
 * lock.
 *
 * <p>Because send and receive share one lock, a slow or blocked {@link #pollMotorBytes} can delay
 * {@link #sendSensoryBytes} and vice versa. For lowest cross-thread latency, avoid multiplexing
 * high-frequency sensory sends and motor polls on the same {@code ZmqTransport} instance from
 * different threads.
 *
 * <p>Only {@link AgentType#SENSORY}, {@link AgentType#MOTOR}, and {@link AgentType#BOTH} are
 * supported; other types throw {@link IllegalArgumentException} at construction time.
 *
 * <p>{@link #close()} is idempotent.
 */
public class ZmqTransport implements Transport {
    private static final Logger LOG = Logger.getLogger(ZmqTransport.class.getName());

    private final Object lock = new Object();
    private final ZContext context;
    private final AgentType agentType;
    private boolean closed;

    private ZMQ.Socket sensorySocket;
    private ZMQ.Socket motorSocket;

    /**
     * Create a new ZMQ transport initialized from the provided agent configuration.
     *
     * @param config the agent configuration defining endpoints and capabilities.
     */
    public ZmqTransport(AgentConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        this.agentType = config.agentType();

        this.context = new ZContext();
        boolean hasSensory = false;
        boolean hasMotor = false;

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
                throw new IllegalArgumentException(
                        "ZmqTransport supports only SENSORY, MOTOR, and BOTH agent types; got: "
                                + agentType);
        }

        try {
            if (hasSensory) {
                String sensoryEndpoint = config.endpoints().sensoryEndpoint();
                if (sensoryEndpoint != null && !sensoryEndpoint.isEmpty()) {
                    sensorySocket = context.createSocket(SocketType.PUSH);
                    SensorySocketConfig sCfg = Objects.requireNonNull(
                            config.sensorySocketConfig(), "sensorySocketConfig");
                    sensorySocket.setSndHWM(sCfg.sendHwm());
                    sensorySocket.setLinger(sCfg.lingerMs());
                    sensorySocket.setImmediate(sCfg.immediate());
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
                    MotorSocketConfig mCfg = Objects.requireNonNull(
                            config.motorSocketConfig(), "motorSocketConfig");
                    motorSocket.setRcvHWM(mCfg.rcvHwm());
                    motorSocket.setLinger(mCfg.lingerMs());
                    LOG.info("Connecting ZMQ PULL to motor endpoint: " + motorEndpoint);
                    motorSocket.connect(motorEndpoint);
                } else {
                    LOG.warning("AgentType indicates motor, but no motor endpoint was provided.");
                }
            }
        } catch (Exception e) {
            close();
            throw new FeagiSdkException("Failed to initialize ZMQ transport", e);
        }
    }

    @Override
    public void sendSensoryBytes(byte[] payload) {
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("ZMQ transport is closed");
            }
            if (sensorySocket == null) {
                throw new IllegalStateException(
                        "Sensory socket is not available (agentType=" + agentType + ")");
            }
            if (payload == null) {
                LOG.fine("sendSensoryBytes skipped: null payload");
                return;
            }
            if (payload.length == 0) {
                LOG.fine("sendSensoryBytes skipped: empty payload");
                return;
            }
            // Non-blocking send by default so we don't stall the agent
            boolean sent = sensorySocket.send(payload, ZMQ.DONTWAIT);
            if (!sent) {
                LOG.fine("Sensory payload dropped (ZMQ PUSH queue full)");
            }
        }
    }

    @Override
    public byte[] pollMotorBytes() {
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("ZMQ transport is closed");
            }
            if (motorSocket == null) {
                throw new IllegalStateException(
                        "Motor socket is not available (agentType=" + agentType + ")");
            }
            // Non-blocking receive
            return motorSocket.recv(ZMQ.DONTWAIT);
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            if (sensorySocket != null) {
                sensorySocket.close();
                sensorySocket = null;
            }
            if (motorSocket != null) {
                motorSocket.close();
                motorSocket = null;
            }
            context.close();
            closed = true;
        }
    }
}
