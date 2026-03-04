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
 * use-after-free races. Logging in {@link #connect()} is deferred until after the write
 * lock is released to minimize lock hold time.
 */
public final class NativeFeagiAgentClient implements FeagiAgentClient {

    private static final Logger LOG = Logger.getLogger(NativeFeagiAgentClient.class.getName());
    private static final long NULL_HANDLE = 0L;

    private final AgentConfig config;

    /**
     * Opaque pointer to the native {@code FeagiAgentClientHandle}.
     * Set by {@link #connect()}, cleared by {@link #close()}.
     *
     * <p>Note: {@code AtomicLong} is used here for its memory-model guarantees on
     * individual reads/writes, but all accesses already occur under {@link #handleLock}.
     * The combination is belt-and-suspenders: the lock provides the critical-section
     * guarantee; the AtomicLong makes the intent explicit and avoids requiring
     * readers to reason about lock scopes when they see {@code clientHandle.get()}.
     * Similarly, {@code connected} is {@code volatile} even though reads/writes happen
     * under the lock — this keeps the visibility guarantee self-documenting.
     */
    private final AtomicLong clientHandle = new AtomicLong(NULL_HANDLE);

    /**
     * Guards the handle against races between connect/close and concurrent send/poll.
     *
     * <ul>
     *   <li>Read lock: held by {@link #sendSensoryBytes} and {@link #pollMotorBytes}
     *       for the duration of the native call.</li>
     *   <li>Write lock: held for the entire body of {@link #connect()} and
     *       {@link #close()} so that two threads cannot both pass the
     *       {@code connected == false} guard, and so that close() cannot free the
     *       handle while a send/poll is in flight.</li>
     * </ul>
     */
    private final ReentrantReadWriteLock handleLock = new ReentrantReadWriteLock();

    private volatile boolean connected = false;

    /**
     * Reusable out-parameter carrier for {@link #sendSensoryBytes}.
     * Guarded by the read lock held during the native call — the lock already
     * serialises concurrent callers, so this single instance is safe to reuse
     * without further synchronisation. Eliminates the per-call boolean[] allocation
     * in the high-frequency sensory hot path.
     */
    private final boolean[] outSent = new boolean[1];

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
     * <p>Holds the write lock for the entire native portion so that two concurrent callers
     * cannot both pass the {@code connected == false} guard and race into native allocation.
     * Logging is deferred until after the lock is released to minimize hold time.
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
                applyEndpoints(cfgHandle);
                applyTimingConfig(cfgHandle);
                applySensorySocketConfig(cfgHandle);
                applyCapabilities(cfgHandle);
                // TODO: wire feagiConfigSetAgentDescriptor and feagiConfigSetAuthTokenBase64
                // once AgentConfig exposes manufacturer/agentName/agentVersion/authToken fields.
                // If feagiConfigValidate requires the agent descriptor, connect() will fail
                // at the validate step with an opaque status until this is wired.

                checkStatus(FeagiNativeBindings.feagiConfigValidate(cfgHandle),
                        "feagiConfigValidate");

                long[] outClient = new long[1];
                checkStatus(FeagiNativeBindings.feagiClientNew(cfgHandle, outClient),
                        "feagiClientNew");

                newClientHandle = outClient[0];
                if (newClientHandle == NULL_HANDLE) {
                    throw new FeagiSdkException(
                            "feagiClientNew returned null handle: " + nativeError());
                }

                checkStatus(FeagiNativeBindings.feagiClientConnect(newClientHandle),
                        "feagiClientConnect");

                clientHandle.set(newClientHandle);
                connected = true;
                newClientHandle = NULL_HANDLE; // ownership transferred; don't free in finally

            } finally {
                FeagiNativeBindings.feagiConfigFree(cfgHandle);
                if (newClientHandle != NULL_HANDLE) {
                    FeagiNativeBindings.feagiClientFree(newClientHandle);
                }
            }
        } finally {
            handleLock.writeLock().unlock();
        }

        // Log after releasing the write lock — no need to hold it while logging.
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

        handleLock.readLock().lock();
        try {
            requireConnected("sendSensoryBytes");

            // outSent must be method-local: the read lock allows multiple threads to
            // hold it simultaneously, so a shared field would cause a data race between
            // concurrent sendSensoryBytes callers (thread A reads thread B's result).
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
     * @throws FeagiSdkException if the native call fails, or if {@code feagiBufferLen}
     *                           returns a negative value (indicating a native-side error)
     *                           or a value exceeding {@link Integer#MAX_VALUE}
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

            long bufHandle = outBufHandle[0];

            // Defensive free: if the native layer ever returns hasData=false with a
            // non-null buffer handle (inconsistent state), free it rather than leak.
            if (!outHasData[0]) {
                if (bufHandle != NULL_HANDLE) {
                    LOG.warning("pollMotorBytes: native returned hasData=false with non-null "
                            + "bufHandle — possible native-side inconsistency; freeing buffer.");
                    FeagiNativeBindings.feagiBufferFree(bufHandle);
                }
                return null;  // no frame available
            }
            if (bufHandle == NULL_HANDLE) {
                return null;  // no frame available
            }
            try {
                long len = FeagiNativeBindings.feagiBufferLen(bufHandle);

                // Negative length is a native-side error — throw rather than swallow.
                if (len < 0) {
                    throw new FeagiSdkException(
                            "feagiBufferLen returned negative length (" + len
                            + ") — native error: " + nativeError());
                }
                if (len > Integer.MAX_VALUE) {
                    throw new FeagiSdkException(
                            "feagiBufferLen returned oversized frame (" + len
                            + " bytes) which exceeds Java array limit");
                }
                // len == 0: copyNativeBuffer returns new byte[0], not null,
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
        String closedAgentId = null;
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
                closedAgentId = config.agentId();
            }
        } finally {
            handleLock.writeLock().unlock();
        }
        // Log after releasing the write lock — consistent with connect() deferral.
        if (closedAgentId != null) {
            LOG.info("NativeFeagiAgentClient closed: agentId=" + closedAgentId);
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
        // Use toNanos() / 1e9 rather than toMillis() / 1000.0 to preserve
        // sub-millisecond precision (e.g. Duration.ofNanos(500_000) = 0.0005 s).
        double heartbeatSecs = config.heartbeatInterval().toNanos() / 1_000_000_000.0;
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
            // Explicit null guard: unit() may be null if VisionCapability was constructed
            // in an invalid state (neither targetCorticalArea nor unit provided).
            if (vision.unit() == null) {
                throw new FeagiSdkException(
                        "VisionCapability has neither targetCorticalArea nor unit set. "
                        + "Use VisionCapability.fromTargetArea() or VisionCapability.fromUnit().");
            }
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
            if (motor.unit() == null) {
                throw new FeagiSdkException(
                        "MotorCapability has neither sourceCorticalAreas, sourceUnits, nor unit set. "
                        + "Use MotorCapability.fromCorticalAreas(), fromUnits(), or fromUnit().");
            }
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
     * (alphanumeric, underscore, hyphen) to prevent JSON injection.
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
            // Strict ASCII check — intentionally not using Character.isLetterOrDigit()
            // which accepts Unicode letters (e-acute, CJK, etc.) and would allow
            // non-ASCII characters that could cause issues in JSON or cross-language contexts.
            boolean isAsciiAlphanumeric = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9');
            if (!isAsciiAlphanumeric && c != '_' && c != '-') {
                throw new IllegalArgumentException(
                        "Cortical area ID '" + id + "' contains invalid character '"
                        + c + "' (0x" + Integer.toHexString(c) + ") at index " + i
                        + ". Only ASCII alphanumeric, underscore, and hyphen are allowed.");
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
            if (s == null) {
                throw new IllegalArgumentException(
                        "specs must not contain null elements (null at index " + i + ")");
            }
            int group = s.group();
            if (group < 0 || group > 255) {
                throw new IllegalArgumentException(
                        "specs[" + i + "].group() = " + group
                        + " is out of range [0, 255]. Must fit in uint8_t for the native ABI.");
            }
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

    /**
     * Throws {@link FeagiSdkException} if {@code status} is not OK, including the
     * native status code in the exception so callers can inspect or log it.
     *
     * <p>TODO: expose {@code status} as a field on {@link FeagiSdkException} once
     * that class is extended with a {@code int nativeStatus()} accessor. This avoids
     * callers having to parse the status out of the message string for error recovery.
     */
    private static void checkStatus(int status, String operation) {
        if (status != FeagiNativeBindings.FeagiStatus.OK.code()) {
            throw new FeagiSdkException(
                    operation + " failed (status=" + status + "): " + nativeError());
        }
    }

    /**
     * Returns the most recent native error message string, or a placeholder if none.
     *
     * <p><b>Thread-safety note:</b> {@code feagi_last_error_message_alloc()} is called
     * without a lock. Whether this is safe depends on the Rust library's error-storage
     * strategy:
     * <ul>
     *   <li>If the Rust library stores the last error in a <b>thread-local</b> (e.g. via
     *       {@code thread_local!}), concurrent calls from different threads cannot
     *       interfere — each thread reads its own slot.</li>
     *   <li>If the error is stored in a <b>global</b> (e.g. a static {@code Mutex<String>}),
     *       a concurrent send or poll on another thread could overwrite the error between
     *       the failing call and this read, producing a misleading message.</li>
     * </ul>
     * Verify the Rust library uses thread-local error storage before relying on the
     * message being accurate under concurrent use. If it does not, this call should be
     * moved inside the lock scope of the caller.
     */
    private static String nativeError() {
        String msg = FeagiNativeBindings.feagiLastErrorMessage();
        return msg != null ? msg : "(no native error message)";
    }
}
