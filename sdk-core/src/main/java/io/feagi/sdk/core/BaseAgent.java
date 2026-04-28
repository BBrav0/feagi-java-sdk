/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract base class mirroring the Python {@code BaseAgent} lifecycle for Java agents.
 *
 * <h2>Lifecycle (matches Python BaseAgent)</h2>
 * <pre>
 *   new MyAgent(agentId, config, client)
 *       |
 *       v
 *   connect()          &lt;- builds client config, connects to FEAGI
 *       |
 *       v
 *   run(runConfig)     &lt;- calls initializeHardware() then enters the loop:
 *       |                  readSensors()
 *       |                  mapSensors(hwData)   -&gt; Map&lt;String,byte[]&gt;
 *       |                  send to FEAGI
 *       |                  poll FEAGI           -&gt; AgentFrame
 *       |                  mapMotors(frame)     -&gt; hardware commands
 *       |                  executeCommands(commands)
 *       |                  [sleep for remainder of tick]
 *       v
 *   stop() / close()   &lt;- idempotent; releases hardware and transport
 * </pre>
 *
 * <h2>Implementing an agent</h2>
 * <pre>{@code
 * public class MyRobotAgent extends BaseAgent {
 *
 *     private MyHardware hw;
 *
 *     public MyRobotAgent(String agentId, AgentConfig config, FeagiAgentClient client) {
 *         super(agentId, config, client);
 *     }
 *
 *     // Called once at the start of run()
 *     @Override
 *     protected void initializeHardware() throws Exception {
 *         hw = new MyHardware();
 *         hw.open();
 *     }
 *
 *     // Read raw sensor data from hardware each tick
 *     @Override
 *     protected Object readSensors() throws Exception {
 *         return hw.readAll();   // hardware-specific type
 *     }
 *
 *     // Convert raw hardware data → FEAGI channel map
 *     @Override
 *     protected Map<String, byte[]> mapSensors(Object hwData) throws Exception {
 *         MyHardwareSnapshot snap = (MyHardwareSnapshot) hwData;
 *         return Map.of("infrared", snap.infraredBytes(),
 *                       "camera",   snap.cameraBytes());
 *     }
 *
 *     // Convert FEAGI motor frame → hardware commands
 *     @Override
 *     protected Object mapMotors(AgentFrame frame) throws Exception {
 *         if (!frame.hasData()) return null;        // no commands this cycle
 *         return MyMotorCommands.parse(frame.motorBytes());
 *     }
 *
 *     // Apply hardware commands (null = no-op)
 *     @Override
 *     protected void executeCommands(Object commands) throws Exception {
 *         if (commands != null) hw.apply((MyMotorCommands) commands);
 *     }
 *
 *     @Override
 *     protected void closeHardware() {
 *         if (hw != null) hw.close();
 *     }
 * }
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * {@link #run(AgentRunConfig)} is single-threaded. {@link #stop()} is safe to call from
 * any thread. {@link #connect()} and {@link #close()} must not overlap.
 */
public abstract class BaseAgent implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(BaseAgent.class.getName());

    private final String agentId;
    private final AgentConfig config;
    private final FeagiAgentClient client;

    /** Set by {@link #stop()} to request graceful run-loop exit. */
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    private volatile boolean connected = false;

    /**
     * Guards hardware initialization state. AtomicBoolean with compareAndSet(true, false)
     * ensures closeHardware() is called exactly once even when close() and run()'s finally
     * block execute concurrently — a volatile boolean check-then-act is not atomic and
     * allows both to read true and both to call closeHardware().
     */
    private final AtomicBoolean hardwareInitialized = new AtomicBoolean(false);

    // ── Construction ───────────────────────────────────────────────────────────

    /**
     * Create a new agent.
     *
     * <p>Does not connect or initialize hardware — call {@link #connect()} then
     * {@link #run(AgentRunConfig)} to start the lifecycle.
     *
     * @param agentId unique identifier for this agent instance; must not be null or empty
     * @param config  fully-populated agent configuration; must not be null
     * @param client  pre-constructed transport client; must not be null
     */
    protected BaseAgent(String agentId, AgentConfig config, FeagiAgentClient client) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        if (agentId.isEmpty()) throw new IllegalArgumentException("agentId must not be empty");
        this.agentId = agentId;
        this.config  = Objects.requireNonNull(config, "config must not be null");
        this.client  = Objects.requireNonNull(client, "client must not be null");
    }

    // ── Abstract hooks — subclass must implement ───────────────────────────────

    /**
     * Initialize hardware resources.
     *
     * <p>Called once at the start of {@link #run(AgentRunConfig)}, before the loop begins.
     * Mirrors Python {@code initialize_hardware()}. Open devices, allocate buffers, and
     * perform any self-test here. Throwing aborts the run before the loop starts.
     *
     * @throws Exception if hardware initialization fails
     */
    protected abstract void initializeHardware() throws Exception;

    /**
     * Read raw sensor data from hardware for this tick.
     *
     * <p>Mirrors Python {@code read_sensors()}. The returned object is passed directly to
     * {@link #mapSensors(Object)}. Return {@code null} to indicate no data available;
     * {@link #mapSensors(Object)} will be called with {@code null} in that case.
     *
     * <p>The default implementation returns {@code null} — override when your agent
     * has hardware to read.
     *
     * @return hardware-specific sensor snapshot, or null
     * @throws Exception if sensor reading fails
     */
    protected Object readSensors() throws Exception {
        return null;
    }

    /**
     * Convert raw hardware sensor data to the FEAGI channel map format.
     *
     * <p>Mirrors Python {@code map_sensors(hw_data) -> Dict[str, bytes]}.
     * The map keys are sensory channel names (e.g. {@code "camera"}, {@code "infrared"});
     * values are raw byte arrays.
     *
     * <p>Return {@code null} or an empty map to suppress sending for this tick.
     *
     * @param hwData the object returned by {@link #readSensors()} for this tick; may be null
     * @return sensory data keyed by channel name, or null to skip sending
     * @throws Exception if sensor mapping fails
     */
    protected abstract Map<String, byte[]> mapSensors(Object hwData) throws Exception;

    /**
     * Convert a FEAGI motor frame to hardware commands.
     *
     * <p>Mirrors Python {@code map_motors(feagi_output) -> Any}.
     * The returned object is passed to {@link #executeCommands(Object)}.
     * Return {@code null} to indicate no commands to execute this cycle.
     *
     * <p>Implementations must handle {@link AgentFrame#hasData()} returning {@code false}
     * (no FEAGI output this cycle) — typically by returning {@code null}.
     *
     * @param frame current motor frame from FEAGI; never null, may be empty
     * @return hardware-specific motor commands, or null for no-op
     * @throws Exception if motor mapping fails
     */
    protected abstract Object mapMotors(AgentFrame frame) throws Exception;

    /**
     * Apply motor commands to hardware.
     *
     * <p>Mirrors Python {@code execute_commands(commands)}. Called every tick with the
     * result of {@link #mapMotors(AgentFrame)}. A {@code null} commands argument means
     * no FEAGI output this cycle — hold position or no-op.
     *
     * <p>The default implementation does nothing — override when your agent drives hardware.
     *
     * @param commands hardware-specific motor commands from {@link #mapMotors}; may be null
     * @throws Exception if command execution fails
     */
    protected void executeCommands(Object commands) throws Exception {
        // default: no-op
    }

    /**
     * Optional hook called by {@link #close()} unconditionally, after the transport
     * is closed and before returning. Unlike {@link #closeHardware()}, this is called
     * even if {@link #initializeHardware()} was never invoked.
     *
     * <p>Use this to release resources that may have been opened outside the standard
     * {@link #run(AgentRunConfig)} lifecycle — for example, a decoder opened directly
     * by a subclass streaming loop. The default implementation does nothing.
     *
     * <p>Exceptions are logged and not re-thrown.
     */
    protected void onClose() {
        // default: no-op
    }

    /**
     * Release hardware resources opened by {@link #initializeHardware()}.
     *
     * <p>Called by {@link #close()} only when {@link #isHardwareInitialized()} is true.
     * Must be idempotent. Exceptions are logged and not re-thrown.
     *
     * <p>For resources opened outside {@link #run(AgentRunConfig)}, override
     * {@link #onClose()} instead — it is called unconditionally by {@link #close()}.
     */
    protected abstract void closeHardware();

    // ── Public lifecycle API ──────────────────────────────────────────────────

    /**
     * Connect to FEAGI.
     *
     * <p>Mirrors Python {@code connect()}. Must be called before {@link #run}.
     *
     * @throws IllegalStateException if already connected
     * @throws FeagiSdkException     if the transport fails to connect
     */
    public final synchronized void connect() {
        if (connected) {
            throw new IllegalStateException("Agent '" + agentId + "' is already connected.");
        }
        LOG.info("BaseAgent[" + agentId + "]: connecting to "
                + config.endpoints().registrationEndpoint());
        client.connect();
        connected = true;
        LOG.info("BaseAgent[" + agentId + "]: connected");
    }

    /**
     * Initialize hardware and run the sense-act loop until {@link #stop()} is called
     * or a fatal error threshold is reached.
     *
     * <p>Mirrors Python {@code run()}: calls {@link #initializeHardware()} once, then
     * loops:
     * <ol>
     *   <li>{@link #readSensors()} — read hardware state</li>
     *   <li>{@link #mapSensors(Object)} — convert to FEAGI channel map</li>
     *   <li>Send to FEAGI via {@link FeagiAgentClient#sendSensoryBytes(byte[])}</li>
     *   <li>Poll FEAGI via {@link FeagiAgentClient#pollMotorBytes()}</li>
     *   <li>{@link #mapMotors(AgentFrame)} — convert to hardware commands</li>
     *   <li>{@link #executeCommands(Object)} — apply to hardware</li>
     *   <li>Sleep for remaining tick time</li>
     * </ol>
     *
     * @param runConfig loop tuning (tick rate, error threshold); must not be null
     * @throws IllegalStateException if not connected
     * @throws FeagiSdkException     if consecutive errors exceed the threshold;
     *                               hardware is released before this is thrown
     * @throws InterruptedException  if the thread is interrupted during sleep;
     *                               hardware is released before this propagates
     */
    public final void run(AgentRunConfig runConfig) throws InterruptedException {
        Objects.requireNonNull(runConfig, "runConfig must not be null");
        // Reset the stop flag before entering. This allows a connected agent to be
        // re-run after a previous run() returned. The narrow race window between this
        // reset and the while-loop start cannot be fully closed without a lock; run()
        // is documented as single-threaded and must not overlap with stop().
        stopRequested.set(false);
        if (!connected) {
            throw new IllegalStateException(
                    "Agent '" + agentId + "' is not connected. Call connect() first.");
        }

        LOG.info("BaseAgent[" + agentId + "]: initializing hardware");
        try {
            initializeHardware();
            hardwareInitialized.set(true);
        } catch (Exception e) {
            throw new FeagiSdkException(
                    "BaseAgent[" + agentId + "]: hardware initialization failed", e);
        }
        LOG.info("BaseAgent[" + agentId + "]: hardware initialized, starting run loop"
                + " tickInterval=" + runConfig.tickInterval().toMillis() + "ms"
                + " maxConsecutiveErrors=" + runConfig.maxConsecutiveErrors());

        int consecutiveErrors = 0;

        try {
            while (!stopRequested.get()) {
                long tickStart = System.nanoTime();
                try {
                    // ── 1. Read sensors ──────────────────────────────────────────
                    Object hwData = readSensors();

                    // ── 2. Map sensors ───────────────────────────────────────────
                    Map<String, byte[]> sensorData = mapSensors(hwData);

                    // ── 3. Send to FEAGI ─────────────────────────────────────────
                    if (sensorData != null && !sensorData.isEmpty()) {
                        byte[] payload = serializeSensoryData(sensorData);
                        if (payload != null && payload.length > 0) {
                            client.sendSensoryBytes(payload);
                        }
                    }

                    // ── 4. Poll motor data ───────────────────────────────────────
                    byte[] motorBytes = client.pollMotorBytes();
                    AgentFrame frame = (motorBytes != null)
                            ? AgentFrame.of(motorBytes) : AgentFrame.empty();

                    // ── 5. Map motors ────────────────────────────────────────────
                    Object hwCommands = mapMotors(frame);

                    // ── 6. Execute commands ──────────────────────────────────────
                    executeCommands(hwCommands);

                    consecutiveErrors = 0;

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                } catch (Exception e) {
                    consecutiveErrors++;
                    LOG.log(Level.WARNING,
                            "BaseAgent[" + agentId + "]: tick error ("
                            + consecutiveErrors + "/" + runConfig.maxConsecutiveErrors() + ")", e);
                    if (consecutiveErrors >= runConfig.maxConsecutiveErrors()) {
                        throw new FeagiSdkException(
                                "BaseAgent[" + agentId + "]: run loop aborted after "
                                + consecutiveErrors + " consecutive errors", e);
                    }
                }

                // ── 7. Tick pacing ───────────────────────────────────────────────
                long tickNanos = runConfig.tickInterval().toNanos();
                if (tickNanos > 0) {
                    long remaining = tickNanos - (System.nanoTime() - tickStart);
                    if (remaining > 0) {
                        Thread.sleep(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
                    }
                }
            }
            LOG.info("BaseAgent[" + agentId + "]: run loop stopped");

        } finally {
            // compareAndSet(true, false) is atomic — exactly one caller wins even if
            // close() races with this finally block. The loser sees false and skips.
            if (hardwareInitialized.compareAndSet(true, false)) {
                try {
                    closeHardware();
                } catch (Exception e) {
                    LOG.log(Level.WARNING,
                            "BaseAgent[" + agentId + "]: exception in closeHardware() during run() cleanup", e);
                }
            }
        }
    }

    /**
     * Request the run loop to stop after the current tick completes.
     *
     * <p>Mirrors Python {@code stop()}. Safe to call from any thread. Returns immediately.
     */
    public final void stop() {
        stopRequested.set(true);
        LOG.info("BaseAgent[" + agentId + "]: stop requested");
    }

    /**
     * Stop the run loop, disconnect transport, and release hardware.
     *
     * <p>Idempotent — safe to call multiple times. Always calls {@link #closeHardware()}
     * if hardware was initialized, and always calls {@link #onClose()} unconditionally,
     * even if the transport close throws. All exceptions are logged and swallowed so
     * that close() itself never throws.
     *
     * <p>Synchronized on the same monitor as {@link #connect()} to prevent a race where
     * {@code connected} is written {@code true} by {@code connect()} after {@code close()}
     * has already set it {@code false}, leaving the agent showing connected with a closed
     * transport.
     */
    @Override
    public final synchronized void close() {
        stop();
        try {
            client.close();
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "BaseAgent[" + agentId + "]: exception during client.close()", e);
        }
        connected = false;
        // compareAndSet(true, false) is atomic — exactly one of close() and run()'s
        // finally block will win and call closeHardware(); the other sees false and skips.
        if (hardwareInitialized.compareAndSet(true, false)) {
            try {
                closeHardware();
            } catch (Exception e) {
                LOG.log(Level.WARNING,
                        "BaseAgent[" + agentId + "]: exception during closeHardware()", e);
            }
        }
        try {
            onClose();
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "BaseAgent[" + agentId + "]: exception during onClose()", e);
        }
        LOG.info("BaseAgent[" + agentId + "]: closed");
    }

    // ── Serialization hook ────────────────────────────────────────────────────

    /**
     * Serialize the sensory channel map into a FEAGI byte-container payload.
     *
     * <p>Default encoding: for each entry, 4-byte big-endian key length, UTF-8 key bytes,
     * 4-byte big-endian value length, value bytes. Override to use a different wire format.
     *
     * @param sensorData channel map from {@link #mapSensors}; non-null, non-empty
     * @return serialized payload bytes, or null to suppress sending
     */
    protected byte[] serializeSensoryData(Map<String, byte[]> sensorData) {
        if (sensorData == null || sensorData.isEmpty()) return null;

        int totalSize = 0;
        for (Map.Entry<String, byte[]> e : sensorData.entrySet()) {
            byte[] k = e.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] v = e.getValue();
            totalSize += 4 + k.length + 4 + (v != null ? v.length : 0);
        }

        byte[] buf = new byte[totalSize];
        int pos = 0;
        for (Map.Entry<String, byte[]> e : sensorData.entrySet()) {
            byte[] k = e.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] v = e.getValue() != null ? e.getValue() : new byte[0];
            buf[pos++] = (byte) (k.length >>> 24); buf[pos++] = (byte) (k.length >>> 16);
            buf[pos++] = (byte) (k.length >>> 8);  buf[pos++] = (byte)  k.length;
            System.arraycopy(k, 0, buf, pos, k.length); pos += k.length;
            buf[pos++] = (byte) (v.length >>> 24); buf[pos++] = (byte) (v.length >>> 16);
            buf[pos++] = (byte) (v.length >>> 8);  buf[pos++] = (byte)  v.length;
            System.arraycopy(v, 0, buf, pos, v.length); pos += v.length;
        }
        return buf;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Return the agent ID supplied at construction. */
    public final String agentId()                  { return agentId; }

    /** Return the agent configuration. */
    public final AgentConfig config()              { return config; }

    /** Return {@code true} if the agent is currently connected to FEAGI. */
    public final boolean isConnected()             { return connected; }

    /** Return {@code true} if hardware has been initialized and not yet released. */
    public final boolean isHardwareInitialized()   { return hardwareInitialized.get(); }

    /**
     * Mark hardware as initialized from a subclass-driven loop (e.g. a custom
     * streaming iterator) that calls {@link #initializeHardware()} directly rather
     * than going through {@link #run(AgentRunConfig)}.
     *
     * <p>Calling this ensures {@link #isHardwareInitialized()} returns the correct
     * observable state, and that {@link #close()} will call {@link #closeHardware()}
     * via the standard path if the subclass loop exits without cleaning up.
     */
    protected final void markHardwareInitialized() {
        hardwareInitialized.set(true);
    }

    /**
     * Send a pre-serialized sensory payload to FEAGI directly.
     *
     * <p>Subclasses that drive their own loop (e.g. {@code VideoStreamAgent.stream()})
     * can call this instead of going through the {@link #run(AgentRunConfig)} loop.
     * The agent must be connected before calling this.
     *
     * @param payload serialized byte-container payload; must not be null or empty
     * @throws IllegalStateException if not connected
     */
    protected final void sendSensoryPayload(byte[] payload) {
        if (!connected) {
            throw new IllegalStateException(
                    "Agent '" + agentId + "' is not connected. Call connect() first.");
        }
        client.sendSensoryBytes(payload);
    }

    protected final byte[] pollMotorBytes() {
        return client.pollMotorBytes();
    }
}
