/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core.transport;

import io.feagi.sdk.core.AgentConfig;
import io.feagi.sdk.core.AgentType;
import io.feagi.sdk.core.FeagiEndpoints;
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
 * <p>Sensory and motor paths use separate locks ({@code sensoryLock}, {@code motorLock}) so send and
 * receive do not serialize each other; each lock guards the corresponding Jeromq socket only.
 * {@link #close()} acquires {@code sensoryLock} and then {@code motorLock} (always in that order)
 * to tear down without races.
 *
 * <p>Only {@link AgentType#SENSORY}, {@link AgentType#MOTOR}, and {@link AgentType#BOTH} are
 * supported; other types throw {@link IllegalArgumentException} at construction time.
 *
 * <p>Sockets are opened without ZMQ curve/security or application-level authentication. Suitable
 * for trusted local networks; not appropriate as-is for hostile, multi-tenant, or wide-area links
 * without additional protection.
 *
 * <p>{@link #close()} is idempotent.
 */
public class ZmqTransport implements Transport {
    private static final Logger LOG = Logger.getLogger(ZmqTransport.class.getName());

    private final Object sensoryLock = new Object();
    private final Object motorLock = new Object();
    private final ZContext context;
    private final AgentType agentType;
    /** Set under {@code sensoryLock} + {@code motorLock} in {@link #close()}; volatile for readability. */
    private volatile boolean closed;

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
        try {
            assertSupportedAgentType(agentType);
            openSocketsFromEndpoints(
                    config.endpoints(),
                    config.sensorySocketConfig(),
                    config.motorSocketConfig());
        } catch (Exception e) {
            close();
            throw new FeagiSdkException("Failed to initialize ZMQ transport", e);
        }
    }

    /**
     * Package-private for tests: same socket setup as {@link #ZmqTransport(AgentConfig)} without
     * {@link AgentConfig} construction, so missing sensory or motor URIs can be exercised (public
     * {@link AgentConfig} / {@link FeagiEndpoints} validation normally forbids those combinations).
     */
    ZmqTransport(
            AgentType agentType,
            FeagiEndpoints endpoints,
            SensorySocketConfig sensorySocketConfig,
            MotorSocketConfig motorSocketConfig) {
        Objects.requireNonNull(agentType, "agentType must not be null");
        Objects.requireNonNull(endpoints, "endpoints must not be null");
        this.agentType = agentType;
        this.context = new ZContext();
        try {
            assertSupportedAgentType(agentType);
            openSocketsFromEndpoints(endpoints, sensorySocketConfig, motorSocketConfig);
        } catch (Exception e) {
            close();
            throw new FeagiSdkException("Failed to initialize ZMQ transport", e);
        }
    }

    private static void assertSupportedAgentType(AgentType agentType) {
        switch (agentType) {
            case SENSORY:
            case MOTOR:
            case BOTH:
                return;
            default:
                throw new IllegalArgumentException(
                        "ZmqTransport supports only SENSORY, MOTOR, and BOTH agent types; got: "
                                + agentType);
        }
    }

    private void openSocketsFromEndpoints(
            FeagiEndpoints endpoints,
            SensorySocketConfig sensoryCfgNullable,
            MotorSocketConfig motorCfgNullable) {
        boolean hasSensory = agentType.needsSensory();
        boolean hasMotor = agentType.needsMotor();

        if (hasSensory) {
            String sensoryEndpoint = endpoints.sensoryEndpoint();
            if (sensoryEndpoint != null && !sensoryEndpoint.isEmpty()) {
                sensorySocket = context.createSocket(SocketType.PUSH);
                SensorySocketConfig sCfg = Objects.requireNonNull(
                        sensoryCfgNullable, "sensorySocketConfig");
                sensorySocket.setSndHWM(sCfg.sendHwm());
                sensorySocket.setLinger(sCfg.lingerMs());
                sensorySocket.setImmediate(sCfg.immediate());
                LOG.info("Connecting ZMQ PUSH to sensory endpoint: " + sensoryEndpoint);
                sensorySocket.connect(sensoryEndpoint);
            } else {
                // Intentionally no exception here: valid AgentConfig + FeagiEndpoints always supply
                // required URIs; the package-private constructor is for tests with inconsistent
                // endpoints. Failing at first send keeps ctor side-effect free and allows partial
                // transport (e.g. motor-only) in those scenarios.
                LOG.warning("AgentType indicates sensory, but no sensory endpoint was provided.");
            }
        }

        if (hasMotor) {
            String motorEndpoint = endpoints.motorEndpoint();
            if (motorEndpoint != null && !motorEndpoint.isEmpty()) {
                motorSocket = context.createSocket(SocketType.PULL);
                MotorSocketConfig mCfg = Objects.requireNonNull(motorCfgNullable, "motorSocketConfig");
                motorSocket.setRcvHWM(mCfg.subscribeHwm());
                motorSocket.setLinger(mCfg.lingerMs());
                LOG.info("Connecting ZMQ PULL to motor endpoint: " + motorEndpoint);
                motorSocket.connect(motorEndpoint);
            } else {
                // Same deferred-failure rationale as the sensory branch above.
                LOG.warning("AgentType indicates motor, but no motor endpoint was provided.");
            }
        }
    }

    @Override
    public void sendSensoryBytes(byte[] payload) {
        synchronized (sensoryLock) {
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
                LOG.warning("Sensory payload dropped (ZMQ PUSH high-water mark / backpressure)");
            }
        }
    }

    @Override
    public byte[] pollMotorBytes() {
        synchronized (motorLock) {
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

    /**
     * Blocks up to the socket receive timeout for one motor frame (uses {@code recv(0)}). For tests
     * only; production code should use {@link #pollMotorBytes()}.
     */
    byte[] recvMotorBytesBlockingForTest() {
        synchronized (motorLock) {
            if (closed) {
                throw new IllegalStateException("ZMQ transport is closed");
            }
            if (motorSocket == null) {
                throw new IllegalStateException(
                        "Motor socket is not available (agentType=" + agentType + ")");
            }
            return motorSocket.recv(0);
        }
    }

    /**
     * Sets {@link ZMQ.Socket#setReceiveTimeOut(int)} on the motor PULL socket (test hook).
     */
    void setMotorReceiveTimeoutMsForTest(int timeoutMs) {
        synchronized (motorLock) {
            if (motorSocket != null) {
                motorSocket.setReceiveTimeOut(timeoutMs);
            }
        }
    }

    @Override
    public void close() {
        synchronized (sensoryLock) {
            synchronized (motorLock) {
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
}
