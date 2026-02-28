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
<<<<<<< HEAD
import io.feagi.sdk.core.MotorUnit;
=======
>>>>>>> 4d6cc51dc977984ccdef8671e77f8e249d1f91d0
import io.feagi.sdk.core.MotorUnitSpec;
import io.feagi.sdk.core.SensoryCapability;
import io.feagi.sdk.core.SensorySocketConfig;
import io.feagi.sdk.core.VisionCapability;
import io.feagi.sdk.core.VisualizationCapability;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
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
 * {@link #sendSensoryBytes} and {@link #pollMotorBytes} are safe to call from any thread
 * after {@link #connect()} returns. {@link #connect()} and {@link #close()} must not be
 * called concurrently.
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
     * <p>Translates every field of {@link AgentConfig} into native config calls — no
     * hardcoded host/ports/timeouts anywhere in this method.
     *
     * @throws FeagiSdkException     if native config, client creation, or connection fails
     * @throws IllegalStateException if already connected
     */
    @Override
    public void connect() {
<<<<<<< HEAD
        synchronized (this) {
            if (connected) {
                throw new IllegalStateException("Already connected...");
            }
            connected = true;
=======
        if (connected) {
            throw new IllegalStateException("Already connected. Call close() first.");
>>>>>>> 4d6cc51dc977984ccdef8671e77f8e249d1f91d0
        }

        // ── 1. Allocate native config handle ──────────────────────────────────
        long cfgHandle = FeagiNativeBindings.feagiConfigNew(
                config.agentId(),
                config.agentType().ordinal());
        if (cfgHandle == NULL_HANDLE) {
            throw new FeagiSdkException(
                    "feagiConfigNew failed for agent '" + config.agentId() + "': "
                    + nativeError());
        }

        try {
            // ── 2. Endpoints ──────────────────────────────────────────────────
            applyEndpoints(cfgHandle);

            // ── 3. Timing / retry ─────────────────────────────────────────────
            applyTimingConfig(cfgHandle);

            // ── 4. Sensory socket ─────────────────────────────────────────────
            applySensorySocketConfig(cfgHandle);

            // ── 5. Capabilities ───────────────────────────────────────────────
            applyCapabilities(cfgHandle);

            // ── 6. Rust-side validation ───────────────────────────────────────
            int validateStatus = FeagiNativeBindings.feagiConfigValidate(cfgHandle);
            checkStatus(validateStatus, "feagiConfigValidate");

            // ── 7. Create native client ───────────────────────────────────────
            long[] outClient = new long[1];
            int newStatus = FeagiNativeBindings.feagiClientNew(cfgHandle, outClient);
            checkStatus(newStatus, "feagiClientNew");

            long handle = outClient[0];
            if (handle == NULL_HANDLE) {
                throw new FeagiSdkException(
                        "feagiClientNew returned null handle: " + nativeError());
            }
            clientHandle.set(handle);

        } finally {
            // Config handle is only needed during client creation — free it now.
            FeagiNativeBindings.feagiConfigFree(cfgHandle);
        }

        // ── 8. Connect and register ───────────────────────────────────────────
        int connectStatus = FeagiNativeBindings.feagiClientConnect(clientHandle.get());
        if (connectStatus != FeagiNativeBindings.FeagiStatus.OK.code()) {
            long h = clientHandle.getAndSet(NULL_HANDLE);
            FeagiNativeBindings.feagiClientFree(h);
            throw new FeagiSdkException(
                    "feagiClientConnect failed (status=" + connectStatus + "): " + nativeError());
        }

<<<<<<< HEAD
        synchronized (this) {
            if (!connected) return;
            connected = false;
        }
=======
        connected = true;
>>>>>>> 4d6cc51dc977984ccdef8671e77f8e249d1f91d0
        LOG.info("NativeFeagiAgentClient connected: agentId=" + config.agentId()
                + " type=" + config.agentType()
                + " registration=" + config.endpoints().registrationEndpoint());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses try-send semantics — frames may be silently dropped under ZMQ backpressure
     * (real-time contract, no implicit buffering).
     */
    @Override
    public void sendSensoryBytes(byte[] payload) {
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("payload must not be null or empty");
        }
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
    }

    /**
     * {@inheritDoc}
     *
     * <p>Non-blocking. Returns {@code null} if no motor frame is available yet.
     */
    @Override
    public byte[] pollMotorBytes() {
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
            return null;
        }

        long bufHandle = outBufHandle[0];
        try {
            long len = FeagiNativeBindings.feagiBufferLen(bufHandle);
            if (len <= 0 || len > Integer.MAX_VALUE) {
                return null;
            }
            return copyNativeBuffer(bufHandle, (int) len);
        } finally {
            FeagiNativeBindings.feagiBufferFree(bufHandle);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Idempotent — safe to call multiple times.
     */
    @Override
    public void close() {
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
            // Option A: explicit cortical area string
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
            // Option B: semantic SensoryUnit enum + group index
            // ordinal() matches the FeagiSensoryUnit C enum values by contract
            checkStatus(
                    FeagiNativeBindings.feagiConfigSetVisionUnit(
                            cfgHandle,
                            vision.modality(),
                            vision.width(),
                            vision.height(),
                            vision.channels(),
                            vision.unit().ordinal(),   // SensoryUnit → int
                            vision.group()),            // Integer guaranteed non-null here
                    "feagiConfigSetVisionUnit");
        }
    }

    private void applyMotorCapability(long cfgHandle, MotorCapability motor) {
        if (motor == null) return;

        if (motor.sourceCorticalAreas() != null) {
            // Option A: list of cortical area IDs → JSON string array
            checkStatus(
                    FeagiNativeBindings.feagiConfigSetMotorCapability(
                            cfgHandle,
                            motor.modality(),
                            motor.outputCount(),
                            toJsonStringArray(motor.sourceCorticalAreas())),
                    "feagiConfigSetMotorCapability");

        } else if (motor.sourceUnits() != null) {
            // Option C: multiple MotorUnitSpec → JSON array of {unit, group} objects
            checkStatus(
                    FeagiNativeBindings.feagiConfigSetMotorUnitsJson(
                            cfgHandle,
                            motor.modality(),
                            motor.outputCount(),
                            motorUnitSpecsToJson(motor.sourceUnits())),
                    "feagiConfigSetMotorUnitsJson");

        } else {
            // Option B: single MotorUnit enum + group index
            // ordinal() matches the FeagiMotorUnit C enum values by contract
            checkStatus(
                    FeagiNativeBindings.feagiConfigSetMotorUnit(
                            cfgHandle,
                            motor.modality(),
                            motor.outputCount(),
                            motor.unit().ordinal(),    // MotorUnit → int
                            motor.group()),             // Integer guaranteed non-null here
                    "feagiConfigSetMotorUnit");
        }
    }

    private void applyVisualizationCapability(long cfgHandle, VisualizationCapability viz) {
        if (viz == null) return;

        // FeagiResolution is nullable — unpack safely
        FeagiResolution res = viz.resolution();
        boolean hasResolution = res != null;
        int resWidth  = hasResolution ? res.width()  : 0;
        int resHeight = hasResolution ? res.height() : 0;

        // refreshRateHz() returns Double (nullable)
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
                        sensory.shmPath()),   // nullable — JNI bridge handles null as NULL ptr
                "feagiConfigSetSensoryCapability");
    }

    // ── JSON serialization ─────────────────────────────────────────────────────

    /**
     * Serialize to minimal JSON string array, e.g. {@code ["v1_motor","v2_drive"]}.
     * No external JSON library required — cortical area IDs are safe ASCII identifiers.
     */
    private static String toJsonStringArray(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            sb.append('"')
              .append(items.get(i).replace("\\", "\\\\").replace("\"", "\\\""))
              .append('"');
            if (i < items.size() - 1) sb.append(',');
        }
        return sb.append(']').toString();
    }

    /**
     * Serialize to JSON array of unit/group objects, e.g.
     * {@code [{"unit":0,"group":1},{"unit":2,"group":0}]}.
     * {@link MotorUnit#ordinal()} matches FeagiMotorUnit C enum values by contract.
     */
    private static String motorUnitSpecsToJson(List<MotorUnitSpec> specs) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < specs.size(); i++) {
            MotorUnitSpec s = specs.get(i);
            sb.append("{\"unit\":").append(s.unit().ordinal())
              .append(",\"group\":").append(s.group())
              .append('}');
            if (i < specs.size() - 1) sb.append(',');
        }
        return sb.append(']').toString();
    }

    // ── Native helpers ─────────────────────────────────────────────────────────

    /**
     * Copy bytes out of a native {@code FeagiByteBufferHandle} into a Java byte array.
     * The buffer handle must remain valid during this call; caller frees it afterward.
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
<<<<<<< HEAD
}
=======
}
>>>>>>> 4d6cc51dc977984ccdef8671e77f8e249d1f91d0
