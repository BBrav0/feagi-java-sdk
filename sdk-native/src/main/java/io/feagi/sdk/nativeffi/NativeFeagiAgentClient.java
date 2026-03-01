/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.nativeffi;

import io.feagi.sdk.core.AgentCapabilities;
import io.feagi.sdk.core.AgentConfig;
import io.feagi.sdk.core.FeagiAgentClient;
import io.feagi.sdk.core.FeagiResolution;
import io.feagi.sdk.core.FeagiSdkException;
import io.feagi.sdk.core.MotorCapability;
import io.feagi.sdk.core.MotorUnitSpec;
import io.feagi.sdk.core.SensoryCapability;
import io.feagi.sdk.core.SensorySocketConfig;
import io.feagi.sdk.core.VisionCapability;
import io.feagi.sdk.core.VisualizationCapability;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link FeagiAgentClient} implementation backed by the Rust feagi-java-ffi library via JNI.
 *
 * <h2>Lifecycle</h2>
 * <pre>{@code
 * AgentConfig config = new AgentConfig(...);
 *
 * try (NativeFeagiAgentClient client = new NativeFeagiAgentClient(config)) {
 *     client.connect();
 *     client.sendSensoryBytes(payload);
 *     byte[] motor = client.pollMotorBytes();
 * }
 * }</pre>
 *
 * <h2>Handle ownership</h2>
 * <ul>
 *   <li>{@code cfgHandle} — allocated in {@link #connect()}, freed in the same call after
 *       the client handle is created.</li>
 *   <li>{@code clientHandle} — allocated by {@link #connect()}, freed by {@link #close()}.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * {@link #sendSensoryBytes} and {@link #pollMotorBytes} are safe to call concurrently from
 * any thread after {@link #connect()} returns. A read-write lock guards the handle:
 * the read lock is held during send/poll native calls; the write lock is held during
 * {@link #connect()} and {@link #close()} to prevent concurrent double-connect or
 * use-after-free races.
 */
public final class NativeFeagiAgentClient implements FeagiAgentClient {

    private static final Logger LOG = Logger.getLogger(NativeFeagiAgentClient.class.getName());
    private static final long NULL_HANDLE = 0L;

    private final AgentConfig config;

    /**
     * Opaque pointer to the native {@code FeagiAgentClientHandle}.
     * Set by {@link #connect()}, cleared by {@link #close()}.
     */
    private final AtomicLong clientHandle = new AtomicLong(NULL_HANDLE);

    /**
     * Guards the handle against races between connect/close and concurrent send/poll.
     *
     * <ul>
     *   <li>Read lock: held by {@link #sendSensoryBytes} and {@link #pollMotorBytes}
     *       for the duration of the native call.</li>
     *   <li>Write lock: held for the entire body of {@link #connect()} and
     *       {@link #close()} so that two threads cannot both see
     *       {@code connected == false} and race into connect(), and so that
     *       close() cannot free the handle while a send/poll is in flight.</li>
     * </ul>
     */
    private final ReentrantReadWriteLock handleLock = new ReentrantReadWriteLock();

    private volatile boolean connected = false;

    // ── Construction ───────────────────────────────────────────────────────────

    /**
     * Create a new client. The native library must already be loaded via
     * {@link FeagiNativeLibrary#loadAndVerify(String)} before constructing this object.
     *
     * @param config fully-populated agent configuration; must not be null
     */
    public NativeFeagiAgentClient(AgentConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
    }

    // ── FeagiAgentClient ───────────────────────────────────────────────────────

    /**
     * Connect and register the agent with FEAGI.
     *
     * <p>Holds the write lock for the entire body so that two concurrent callers
     * cannot both pass the {@code connected == false} guard and race into native
     * allocation.
     *
     * <p>All steps — config allocation, capability registration, client creation, and
     * connection — are inside a single try/finally block. If any step fails, all
     * native resources allocated so far are freed before the exception propagates.
     *
     * @throws FeagiSdkException     if any native step fails
     * @throws IllegalStateException if already connected
     */
    @Override
    public void connect() {
        handleLock.writeLock().lock();
        try {
            if (connected) {
                throw new IllegalStateException("Already connected. Call close() first.");
            }

            // ── 1. Allocate native config handle ──────────────────────────────
            // AgentTypeCode provides a stable ABI mapping — never use .ordinal() here.
            long cfgHandle = FeagiNativeBindings.feagiConfigNew(
                    config.agentId(),
                    AgentTypeCode.of(config.agentType()));
            if (cfgHandle == NULL_HANDLE) {
                throw new FeagiSdkException(
                        "feagiConfigNew failed for agent '" + config.agentId() + "': "
                        + nativeError());
            }

            long newClientHandle = NULL_HANDLE;
            try {
                // ── 2. Endpoints ──────────────────────────────────────────────
                applyEndpoints(cfgHandle);

                // ── 3. Timing / retry ─────────────────────────────────────────
                applyTimingConfig(cfgHandle);

                // ── 4. Sensory socket ─────────────────────────────────────────
                applySensorySocketConfig(cfgHandle);

                // ── 5. Capabilities ───────────────────────────────────────────
                applyCapabilities(cfgHandle);

                // ── 6. Rust-side validation ───────────────────────────────────
                checkStatus(FeagiNativeBindings.feagiConfigValidate(cfgHandle),
                        "feagiConfigValidate");

                // ── 7. Create native client ───────────────────────────────────
                long[] outClient = new long[1];
                checkStatus(FeagiNativeBindings.feagiClientNew(cfgHandle, outClient),
                        "feagiClientNew");

                newClientHandle = outClient[0];
                if (newClientHandle == NULL_HANDLE) {
                    throw new FeagiSdkException(
                            "feagiClientNew returned null handle: " + nativeError());
                }

                // ── 8. Connect and register ───────────────────────────────────
                checkStatus(FeagiNativeBindings.feagiClientConnect(newClientHandle),
                        "feagiClientConnect");

                // All steps succeeded — publish handle and mark connected.
                clientHandle.set(newClientHandle);
                connected = true;
                newClientHandle = NULL_HANDLE; // ownership transferred; don't free in finally

            } finally {
                // Config handle is always freed — not needed after client creation.
                FeagiNativeBindings.feagiConfigFree(cfgHandle);
                // If an exception prevented ownership transfer, free the client too.
                if (newClientHandle != NULL_HANDLE) {
                    FeagiNativeBindings.feagiClientFree(newClientHandle);
                }
            }

            LOG.info("NativeFeagiAgentClient connected: agentId=" + config.agentId()
                    + " type=" + config.agentType()
                    + " registration=" + config.endpoints().registrationEndpoint());

        } finally {
            handleLock.writeLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses try-send semantics — frames may be silently dropped under ZMQ backpressure
     * (real-time contract, no implicit buffering).
     *
     * <p>The read lock is held during the native call to prevent {@link #close()} from
     * freeing the handle while the send is in progress.
     */
    @Override
    public void sendSensoryBytes(byte[] payload) {
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("payload must not be null or empty");
        }

        handleLock.readLock().lock();
        try {
            requireConnected("sendSensoryBytes");

            boolean[] outSent = new boolean[1];
            int status = FeagiNativeBindings.feagiClientTrySendSensoryBytes(
                    clientHandle.get(), payload, outSent);

            if (status != FeagiNativeBindings.FeagiStatus.OK.code()) {
                throw new FeagiSdkException(
                        "sendSensoryBytes failed (status=" + status + "): " + nativeError());
            }
            if (!outSent[0]) {
                LOG.fine("sendSensoryBytes: frame dropped (backpressure)");
            }
        } finally {
            handleLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Non-blocking. Returns {@code null} if no motor frame is currently available.
     * Returns an empty {@code byte[]} for a zero-length frame (distinguishable from
     * "no data available").
     *
     * <p>The read lock is held during the native call to prevent {@link #close()} from
     * freeing the handle while the poll is in progress.
     */
    @Override
    public byte[] pollMotorBytes() {
        handleLock.readLock().lock();
        try {
            requireConnected("pollMotorBytes");

            long[] outBufHandle = new long[1];
            boolean[] outHasData = new boolean[1];

            int status = FeagiNativeBindings.feagiClientReceiveMotorBuffer(
                    clientHandle.get(), outBufHandle, outHasData);

            if (status != FeagiNativeBindings.FeagiStatus.OK.code()) {
                throw new FeagiSdkException(
                        "pollMotorBytes failed (status=" + status + "): " + nativeError());
            }

            if (!outHasData[0] || outBufHandle[0] == NULL_HANDLE) {
                return null;  // no frame available
            }

            long bufHandle = outBufHandle[0];
            try {
                long len = FeagiNativeBindings.feagiBufferLen(bufHandle);
                if (len < 0 || len > Integer.MAX_VALUE) {
                    return null;
                }
                // len == 0: copyNativeBuffer returns new byte[0] (not null),
                // so callers can distinguish "zero-length frame" from "no frame".
                return copyNativeBuffer(bufHandle, (int) len);
            } finally {
                FeagiNativeBindings.feagiBufferFree(bufHandle);
            }
        } finally {
            handleLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Idempotent — safe to call multiple times. Holds the write lock so it cannot
     * run concurrently with an in-progress send or poll.
     */
    @Override
    public void close() {
        handleLock.writeLock().lock();
        try {
            connected = false;
            long handle = clientHandle.getAndSet(NULL_HANDLE);
            if (handle != NULL_HANDLE) {
                try {
                    FeagiNativeBindings.feagiClientFree(handle);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Error freeing native client handle", e);
                }
                LOG.info("NativeFeagiAgentClient closed: agentId=" + config.agentId());
            }
        } finally {
            handleLock.writeLock().unlock();
        }
    }

    // ── Config helpers ─────────────────────────────────────────────────────────

    private void applyEndpoints(long cfgHandle) {
        var ep = config.endpoints();

        checkStatus(
                FeagiNativeBindings.feagiConfigSetRegistrationEndpoint(
                        cfgHandle, ep.registrationEndpoint()),
                "feagiConfigSetRegistrationEndpoint");

        if (ep.sensoryEndpoint() != null) {
            checkStatus(
                    FeagiNativeBindings.feagiConfigSetSensoryEndpoint(
                            cfgHandle, ep.sensoryEndpoint()),
                    "feagiConfigSetSensoryEndpoint");
        }
        if (ep.motorEndpoint() != null) {
            checkStatus(
                    FeagiNativeBindings.feagiConfigSetMotorEndpoint(
                            cfgHandle, ep.motorEndpoint()),
                    "feagiConfigSetMotorEndpoint");
        }
        if (ep.visualizationEndpoint() != null) {
            checkStatus(
                    FeagiNativeBindings.feagiConfigSetVisualizationEndpoint(
                            cfgHandle, ep.visualizationEndpoint()),
                    "feagiConfigSetVisualizationEndpoint");
        }
        if (ep.controlEndpoint() != null) {
            checkStatus(
                    FeagiNativeBindings.feagiConfigSetControlEndpoint(
                            cfgHandle, ep.controlEndpoint()),
                    "feagiConfigSetControlEndpoint");
        }
    }

    private void applyTimingConfig(long cfgHandle) {
        double heartbeatSecs = config.heartbeatInterval().toMillis() / 1000.0;
        checkStatus(
                FeagiNativeBindings.feagiConfigSetHeartbeatIntervalSeconds(
                        cfgHandle, heartbeatSecs),
                "feagiConfigSetHeartbeatIntervalSeconds");

        checkStatus(
                FeagiNativeBindings.feagiConfigSetConnectionTimeoutMs(
                        cfgHandle, config.connectionTimeout().toMillis()),
                "feagiConfigSetConnectionTimeoutMs");

        checkStatus(
                FeagiNativeBindings.feagiConfigSetRegistrationRetries(
                        cfgHandle, config.registrationRetries()),
                "feagiConfigSetRegistrationRetries");

        checkStatus(
                FeagiNativeBindings.feagiConfigSetRetryBackoffMs(
                        cfgHandle, config.retryBackoff().toMillis()),
                "feagiConfigSetRetryBackoffMs");
    }

    private void applySensorySocketConfig(long cfgHandle) {
        SensorySocketConfig sc = config.sensorySocketConfig();
        checkStatus(
                FeagiNativeBindings.feagiConfigSetSensorySocketConfig(
                        cfgHandle, sc.sendHwm(), sc.lingerMs(), sc.immediate()),
                "feagiConfigSetSensorySocketConfig");
    }

    private void applyCapabilities(long cfgHandle) {
        AgentCapabilities caps = config.capabilities();
        applyVisionCapability(cfgHandle, caps.vision());
        applyMotorCapability(cfgHandle, caps.motor());
        applyVisualizationCapability(cfgHandle, caps.visualization());
        applySensoryCapability(cfgHandle, caps.sensory());

        for (Map.Entry<String, String> entry : caps.customCapabilitiesJson().entrySet()) {
            checkStatus(
                    FeagiNativeBindings.feagiConfigSetCustomCapabilityJson(
                            cfgHandle, entry.getKey(), entry.getValue()),
                    "feagiConfigSetCustomCapabilityJson[" + entry.getKey() + "]");
        }
    }

    private void applyVisionCapability(long cfgHandle, VisionCapability vision) {
        if (vision == null) return;

        if (vision.targetCorticalArea() != null) {
            checkStatus(
                    FeagiNativeBindings.feagiConfigSetVisionCapability(
                            cfgHandle,
                            vision.modality(),
                            vision.width(),
                            vision.height(),
                            vision.channels(),
                            vision.targetCorticalArea()),
                    "feagiConfigSetVisionCapability");
        } else {
            // SensoryUnitCode: stable ABI mapping — never use .ordinal()
            checkStatus(
                    FeagiNativeBindings.feagiConfigSetVisionUnit(
                            cfgHandle,
                            vision.modality(),
                            vision.width(),
                            vision.height(),
                            vision.channels(),
                            SensoryUnitCode.of(vision.unit()),
                            vision.group()),
                    "feagiConfigSetVisionUnit");
        }
    }

    private void applyMotorCapability(long cfgHandle, MotorCapability motor) {
        if (motor == null) return;

        if (motor.sourceCorticalAreas() != null) {
            checkStatus(
                    FeagiNativeBindings.feagiConfigSetMotorCapability(
                            cfgHandle,
                            motor.modality(),
                            motor.outputCount(),
                            toJsonStringArray(motor.sourceCorticalAreas())),
                    "feagiConfigSetMotorCapability");

        } else if (motor.sourceUnits() != null) {
            checkStatus(
                    FeagiNativeBindings.feagiConfigSetMotorUnitsJson(
                            cfgHandle,
                            motor.modality(),
                            motor.outputCount(),
                            motorUnitSpecsToJson(motor.sourceUnits())),
                    "feagiConfigSetMotorUnitsJson");

        } else {
            // MotorUnitCode: stable ABI mapping — never use .ordinal()
            checkStatus(
                    FeagiNativeBindings.feagiConfigSetMotorUnit(
                            cfgHandle,
                            motor.modality(),
                            motor.outputCount(),
                            MotorUnitCode.of(motor.unit()),
                            motor.group()),
                    "feagiConfigSetMotorUnit");
        }
    }

    private void applyVisualizationCapability(long cfgHandle, VisualizationCapability viz) {
        if (viz == null) return;

        FeagiResolution res = viz.resolution();
        boolean hasResolution = res != null;
        int resWidth  = hasResolution ? res.width()  : 0;
        int resHeight = hasResolution ? res.height() : 0;

        Double refreshRate = viz.refreshRateHz();
        boolean hasRefreshRate = refreshRate != null;
        double refreshRateHz = hasRefreshRate ? refreshRate : 0.0;

        checkStatus(
                FeagiNativeBindings.feagiConfigSetVisualizationCapability(
                        cfgHandle,
                        viz.visualizationType(),
                        hasResolution,
                        resWidth,
                        resHeight,
                        hasRefreshRate,
                        refreshRateHz,
                        viz.bridgeProxy()),
                "feagiConfigSetVisualizationCapability");
    }

    private void applySensoryCapability(long cfgHandle, SensoryCapability sensory) {
        if (sensory == null) return;

        checkStatus(
                FeagiNativeBindings.feagiConfigSetSensoryCapability(
                        cfgHandle,
                        sensory.rateHz(),
                        sensory.shmPath()),
                "feagiConfigSetSensoryCapability");
    }

    // ── JSON serialization ─────────────────────────────────────────────────────

    /**
     * Serialize to minimal JSON string array, e.g. {@code ["v1_motor","v2_drive"]}.
     *
     * <p>Validates that each ID contains only safe ASCII identifier characters
     * (alphanumeric, underscore, hyphen) to prevent JSON injection. This is
     * stricter than generic escaping but matches what FEAGI accepts for cortical area IDs.
     *
     * @throws IllegalArgumentException if items is null, or any ID is null/empty/unsafe
     */
    static String toJsonStringArray(List<String> items) {
        if (items == null) {
            throw new IllegalArgumentException("items must not be null");
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            validateCorticalAreaId(items.get(i));
            sb.append('"').append(items.get(i)).append('"');
            if (i < items.size() - 1) {
                sb.append(',');
            }
        }
        return sb.append(']').toString();
    }

    /**
     * Validate that a cortical area ID contains only safe ASCII characters.
     *
     * @throws IllegalArgumentException if the ID is null, empty, or contains unsafe characters
     */
    static void validateCorticalAreaId(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cortical area ID must not be null or empty");
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-') {
                throw new IllegalArgumentException(
                        "Cortical area ID '" + id + "' contains invalid character '"
                        + c + "' at index " + i
                        + ". Only alphanumeric, underscore, and hyphen are allowed.");
            }
        }
    }

    /**
     * Serialize a list of {@link MotorUnitSpec} to a JSON array of unit/group objects.
     *
     * <p>Example output: {@code [{"unit":0,"group":1},{"unit":2,"group":0}]}
     *
     * <p>Uses {@link MotorUnitCode} for stable ABI integer mapping — never {@code .ordinal()}.
     *
     * @throws IllegalArgumentException if specs is null
     */
    static String motorUnitSpecsToJson(List<MotorUnitSpec> specs) {
        if (specs == null) {
            throw new IllegalArgumentException("specs must not be null");
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < specs.size(); i++) {
            MotorUnitSpec s = specs.get(i);
            sb.append("{\"unit\":").append(MotorUnitCode.of(s.unit()))
              .append(",\"group\":").append(s.group())
              .append('}');
            if (i < specs.size() - 1) {
                sb.append(',');
            }
        }
        return sb.append(']').toString();
    }

    // ── Native helpers ─────────────────────────────────────────────────────────

    /**
     * Copy bytes from a native {@code FeagiByteBufferHandle} into a Java byte array.
     * Returns {@code new byte[0]} for zero-length frames (never null for valid handles).
     * The caller is responsible for freeing the buffer handle after this call.
     */
    private static native byte[] copyNativeBuffer(long bufHandle, int length);

    private void requireConnected(String method) {
        if (!connected || clientHandle.get() == NULL_HANDLE) {
            throw new IllegalStateException(
                    method + "() called but client is not connected. Call connect() first.");
        }
    }

    private static void checkStatus(int status, String operation) {
        if (status != FeagiNativeBindings.FeagiStatus.OK.code()) {
            throw new FeagiSdkException(
                    operation + " failed (status=" + status + "): " + nativeError());
        }
    }

    private static String nativeError() {
        String msg = FeagiNativeBindings.feagiLastErrorMessage();
        return msg != null ? msg : "(no native error message)";
    }
}