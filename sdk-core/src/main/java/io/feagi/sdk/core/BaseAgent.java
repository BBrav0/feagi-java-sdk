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
 *       │
 *       ▼
 *   connect()          <- builds client config, connects to FEAGI
 *       │
 *       ▼
 *   run(runConfig)     <- calls initializeHardware() then enters the loop:
 *       │                  readSensors()
 *       │                  mapSensors(hwData)   -> Map<String,byte[]>
 *       │                  send to FEAGI
 *       │                  poll FEAGI           -> AgentFrame
 *       │                  mapMotors(frame)     -> hardware commands
 *       │                  executeCommands(commands)
 *       │                  [sleep for remainder of tick]
 *       ▼
 *   stop() / close()   <- idempotent; releases hardware and transport
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
    private final AtomicBoolean clientClosed = new AtomicBoolean(false);
    private final AtomicBoolean runActive = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);

    private final Object runMonitor = new Object();

    private volatile boolean hardwareInitialized = false;

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
        if (agentId.isBlank()) throw new IllegalArgumentException("agentId must not be empty");
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
     * Release hardware resources.
     *
     * <p>Called by {@link #close()} regardless of connection state. Must be idempotent.
     * Exceptions are logged and not re-thrown.
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
    public final void connect() {
        if (!connected.compareAndSet(false, true)) {
            throw new IllegalStateException("Agent '" + agentId + "' is already connected.");
        }
        LOG.info("BaseAgent[" + agentId + "]: connecting to "
                + config.endpoints().registrationEndpoint());
        try {
            client.connect();
            LOG.info("BaseAgent[" + agentId + "]: connected");
        } catch (RuntimeException | Error e) {
            connected.set(false);
            throw e;
        }
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
     * <p>IF this method throws (e.g., due to hardware initialization failure or exceeding max consecutive errors), the agent remains connected.
     * Callers may invoke {@link #run(AgentRunConfig)} again to restart the loop, or call {@link #close()} to fully shut down the agent.
     * 
     * @param runConfig loop tuning (tick rate, error threshold); must not be null
     * @throws IllegalStateException if not connected
     * @throws FeagiSdkException     if consecutive errors exceed the threshold
     * @throws InterruptedException  if the thread is interrupted during sleep
     */
    public final void run(AgentRunConfig runConfig) throws InterruptedException {
        Objects.requireNonNull(runConfig, "runConfig must not be null");
        if (!connected.get()) {
            throw new IllegalStateException(
                    "Agent '" + agentId + "' is not connected. Call connect() first.");
        }

        stopRequested.set(false);
        if (!runActive.compareAndSet(false, true)) {
            throw new IllegalStateException("Agent '" + agentId + "' is already running.");
        }
        try {
            LOG.info("BaseAgent[" + agentId + "]: initializing hardware");
            try {
                initializeHardware();
                hardwareInitialized = true;
            } catch (Exception e) {
                throw new FeagiSdkException(
                        "BaseAgent[" + agentId + "]: hardware initialization failed", e);
            }

            LOG.info("BaseAgent[" + agentId + "]: hardware initialized, starting run loop"
                    + " tickInterval=" + runConfig.tickInterval().toMillis() + "ms"
                    + " maxConsecutiveErrors=" + runConfig.maxConsecutiveErrors());

            int consecutiveErrors = 0;

            while (!stopRequested.get()) {
                long tickStart = System.nanoTime();
                try {
                    Object hwData = readSensors();
                    Map<String, byte[]> sensorData = mapSensors(hwData);

                    if (sensorData != null && !sensorData.isEmpty()) {
                        byte[] payload = serializeSensoryData(sensorData);
                        if (payload != null && payload.length > 0) {
                            client.sendSensoryBytes(payload);
                        }
                    }

                    byte[] motorBytes = client.pollMotorBytes();
                    AgentFrame frame = (motorBytes != null)
                            ? AgentFrame.of(motorBytes) : AgentFrame.empty();

                    Object hwCommands = mapMotors(frame);
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
            runActive.set(false);
            synchronized (runMonitor) {
                runMonitor.notifyAll();
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
     * if hardware was initialized, even if the transport close throws. Exceptions from
     * either {@code client.close()} or {@code closeHardware()} are logged and swallowed
     * so that close() itself never throws.
     */
    @Override
    public final void close() {
        stop();

        synchronized (runMonitor) {
            while (runActive.get()) {
                try {
                    runMonitor.wait(5_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.log(Level.WARNING,
                            "BaseAgent[" + agentId + "]: interrupted while waiting for run loop to stop", e);
                    break;
                }
            }
        }

        if (clientClosed.compareAndSet(false, true)) {
            try {
                client.close();
            } catch (Exception e) {
                LOG.log(Level.WARNING,
                        "BaseAgent[" + agentId + "]: exception during client.close()", e);
            }
        }

        connected.set(false);

        if (hardwareInitialized) {
            try {
                closeHardware();
            } catch (Exception e) {
                LOG.log(Level.WARNING,
                        "BaseAgent[" + agentId + "]: exception during closeHardware()", e);
            }
            hardwareInitialized = false;
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
        if (sensorData == null || sensorData.isEmpty()) { return null; } 

        record EncodedEntry(byte[] key, byte[] value) {}
        java.util.List<EncodedEntry> entries = new java.util.ArrayList<>(sensorData.size());
        int totalSize = 0;
        for (Map.Entry<String, byte[]> e : sensorData.entrySet()) {
            byte[] keyBytes = e.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] valueBytes = (e.getValue() != null) ? e.getValue() : new byte[0];
            entries.add(new EncodedEntry(keyBytes, valueBytes));
            totalSize += 4 + keyBytes.length + 4 + valueBytes.length;
        }

        byte[] buf = new byte[totalSize];
        int pos = 0;
        for (EncodedEntry entry : entries) {
            byte[] k = entry.key();
            byte[] v = entry.value();

            buf[pos++] = (byte) (k.length >>> 24);
            buf[pos++] = (byte) (k.length >>> 16);
            buf[pos++] = (byte) (k.length >>> 8);
            buf[pos++] = (byte) k.length;
            System.arraycopy(k, 0, buf, pos, k.length);
            pos += k.length;

            buf[pos++] = (byte) (v.length >>> 24);
            buf[pos++] = (byte) (v.length >>> 16);
            buf[pos++] = (byte) (v.length >>> 8);
            buf[pos++] = (byte) v.length;
            System.arraycopy(v, 0, buf, pos, v.length);
            pos += v.length;
        }

        return buf;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Return the agent ID supplied at construction. */
    public final String agentId()                  { return agentId; }

    /** Return the agent configuration. */
    public final AgentConfig config()              { return config; }

    /** Return {@code true} if the agent is currently connected to FEAGI. */
    public final boolean isConnected()             { return connected.get(); }

    /** Return {@code true} if hardware has been initialized inside the run loop. */
    public final boolean isHardwareInitialized()   { return hardwareInitialized; }
}
