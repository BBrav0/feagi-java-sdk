/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BaseAgent}.
 *
 * <p>All tests use {@link StubFeagiAgentClient} (no native library required) and
 * {@link RecordingAgent}, a minimal subclass that records every lifecycle call.
 *
 * <p>The lifecycle under test mirrors Python {@code BaseAgent}:
 * <pre>
 *   connect() → run() → [initializeHardware, readSensors, mapSensors,
 *                         send, poll, mapMotors, executeCommands] → stop()/close()
 * </pre>
 */
class BaseAgentTest {

    // ── Stubs ──────────────────────────────────────────────────────────────────

    static class StubFeagiAgentClient implements FeagiAgentClient {
        int connectCalls;
        int closeCalls;
        final List<byte[]> sentPayloads = new ArrayList<>();
        byte[] nextMotorBytes = null;
        RuntimeException connectException = null;

        @Override public void connect() {
            connectCalls++;
            if (connectException != null) throw connectException;
        }
        @Override public void sendSensoryBytes(byte[] p) { sentPayloads.add(p); }
        @Override public byte[] pollMotorBytes()         { return nextMotorBytes; }
        @Override public void close()                    { closeCalls++; }
    }

    /**
     * Concrete BaseAgent that records every hook call and the arguments it received.
     * readSensors returns a fixed {@code HwSnapshot} object; mapSensors returns one
     * channel; mapMotors returns a String command or null.
     */
    static class RecordingAgent extends BaseAgent {

        record HwSnapshot(byte[] data) {}

        // Call counts
        int initHwCalls;
        int readSensorsCalls;
        int mapSensorsCalls;
        int mapMotorsCalls;
        int executeCommandsCalls;
        int closeHwCalls;

        // What hooks received / returned
        final List<Object>      hwDataReceived   = new ArrayList<>();
        final List<AgentFrame>  framesReceived   = new ArrayList<>();
        final List<Object>      commandsReceived = new ArrayList<>();

        // Configuration
        boolean throwOnReadSensors   = false;
        boolean throwOnMapSensors    = false;
        boolean throwOnMapMotors     = false;
        boolean throwOnExecute       = false;
        Exception hardwareInitEx     = null;
        Map<String, byte[]> sensorsToReturn = Map.of("test", new byte[]{1, 2, 3});

        RecordingAgent(StubFeagiAgentClient client) {
            super("test-agent", makeConfig(), client);
        }

        @Override
        protected void initializeHardware() throws Exception {
            initHwCalls++;
            if (hardwareInitEx != null) throw hardwareInitEx;
        }

        @Override
        protected Object readSensors() throws Exception {
            readSensorsCalls++;
            if (throwOnReadSensors) throw new RuntimeException("read error");
            return new HwSnapshot(new byte[]{9});
        }

        @Override
        protected Map<String, byte[]> mapSensors(Object hwData) throws Exception {
            mapSensorsCalls++;
            hwDataReceived.add(hwData);
            if (throwOnMapSensors) throw new RuntimeException("sensor map error");
            return sensorsToReturn;
        }

        @Override
        protected Object mapMotors(AgentFrame frame) throws Exception {
            mapMotorsCalls++;
            framesReceived.add(frame);
            if (throwOnMapMotors) throw new RuntimeException("motor map error");
            return frame.hasData() ? "motor_cmd" : null;
        }

        @Override
        protected void executeCommands(Object commands) throws Exception {
            executeCommandsCalls++;
            commandsReceived.add(commands);
            if (throwOnExecute) throw new RuntimeException("execute error");
        }

        @Override
        protected void closeHardware() { closeHwCalls++; }

        static AgentConfig makeConfig() {
            FeagiEndpoints ep = new FeagiEndpoints(
                    "tcp://localhost:30001",
                    "tcp://localhost:5558",
                    "tcp://localhost:5564",
                    null, null);
            AgentCapabilities caps = AgentCapabilities.builder()
                    .sensory(new SensoryCapability(30.0, null))
                    .motor(MotorCapability.fromUnit("drive", 4, MotorUnit.ROTARY_MOTOR, 0))
                    .build();
            return new AgentConfig(
                    "test-agent", AgentType.BOTH, ep, caps,
                    Duration.ZERO, Duration.ofSeconds(5), 3,
                    Duration.ofMillis(500), new SensorySocketConfig(1000, 0, true));
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    StubFeagiAgentClient stub;
    RecordingAgent agent;

    @BeforeEach
    void setUp() {
        stub  = new StubFeagiAgentClient();
        agent = new RecordingAgent(stub);
    }

    /** Connect and run for a short window then stop; returns when loop exits. */
    private void runBriefly(RecordingAgent a, long sleepMs) throws Exception {
        a.connect();
        var exec = Executors.newSingleThreadExecutor();
        Future<?> f = exec.submit(() -> {
            try { a.run(AgentRunConfig.builder().tickInterval(Duration.ZERO).build()); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        Thread.sleep(sleepMs);
        a.stop();
        f.get(3, TimeUnit.SECONDS);
        exec.shutdown();
    }

    // ── Construction ───────────────────────────────────────────────────────────

    @Test
    void constructor_rejectsNullAgentId() {
        assertThrows(NullPointerException.class,
                () -> new BaseAgent(null, RecordingAgent.makeConfig(), stub) {
                    @Override protected void initializeHardware() {}
                    @Override protected Map<String,byte[]> mapSensors(Object d) { return null; }
                    @Override protected Object mapMotors(AgentFrame f)           { return null; }
                    @Override protected void closeHardware()                     {}
                });
    }

    @Test
    void constructor_rejectsEmptyAgentId() {
        assertThrows(IllegalArgumentException.class,
                () -> new BaseAgent("", RecordingAgent.makeConfig(), stub) {
                    @Override protected void initializeHardware() {}
                    @Override protected Map<String,byte[]> mapSensors(Object d) { return null; }
                    @Override protected Object mapMotors(AgentFrame f)           { return null; }
                    @Override protected void closeHardware()                     {}
                });
    }

    // ── connect() ─────────────────────────────────────────────────────────────

    @Test
    void connect_delegatesToClient() {
        agent.connect();
        assertEquals(1, stub.connectCalls);
        assertTrue(agent.isConnected());
    }

    @Test
    void connect_doubleConnect_throws() {
        agent.connect();
        assertThrows(IllegalStateException.class, agent::connect);
        assertEquals(1, stub.connectCalls);
    }

    @Test
    void connect_propagatesClientException() {
        stub.connectException = new FeagiSdkException("transport error");
        assertThrows(FeagiSdkException.class, agent::connect);
        assertFalse(agent.isConnected());
    }

    // ── run() — requires connect first ────────────────────────────────────────

    @Test
    void run_requiresConnected() {
        assertThrows(IllegalStateException.class,
                () -> agent.run(AgentRunConfig.defaults()));
    }

    @Test
    void run_rejectsNullRunConfig() {
        agent.connect();
        assertThrows(NullPointerException.class, () -> agent.run(null));
    }

    // ── run() — initializeHardware called once inside run() ───────────────────

    @Test
    void run_callsInitializeHardwareOnce() throws Exception {
        runBriefly(agent, 30);
        assertEquals(1, agent.initHwCalls,
                "initializeHardware must be called exactly once per run()");
        // hardwareInitialized is cleared by run()'s finally block after the loop exits,
        // so isHardwareInitialized() is false once run() returns — this is correct.
        assertFalse(agent.isHardwareInitialized(),
                "hardwareInitialized must be cleared by run() finally block");
        // closeHardware must have been called exactly once by run()'s finally block
        assertEquals(1, agent.closeHwCalls,
                "closeHardware must be called once by run() finally block");
    }

    @Test
    void run_abortsIfInitializeHardwareThrows() throws Exception {
        agent.hardwareInitEx = new Exception("hw failed");
        agent.connect();
        assertThrows(FeagiSdkException.class,
                () -> agent.run(AgentRunConfig.defaults()));
        assertFalse(agent.isHardwareInitialized());
    }

    // ── run() — sense-act loop order ──────────────────────────────────────────

    @Test
    void run_callsFullPipelineEachTick() throws Exception {
        runBriefly(agent, 30);

        // All hooks must have been called at least once
        assertTrue(agent.readSensorsCalls   >= 1, "readSensors not called");
        assertTrue(agent.mapSensorsCalls    >= 1, "mapSensors not called");
        assertTrue(agent.mapMotorsCalls     >= 1, "mapMotors not called");
        assertTrue(agent.executeCommandsCalls >= 1, "executeCommands not called");
    }

    @Test
    void run_passesSensorDataThroughPipeline() throws Exception {
        runBriefly(agent, 30);

        // hwData passed to mapSensors must be the HwSnapshot returned by readSensors
        assertFalse(agent.hwDataReceived.isEmpty());
        assertTrue(agent.hwDataReceived.get(0) instanceof RecordingAgent.HwSnapshot,
                "mapSensors must receive the object returned by readSensors");
    }

    @Test
    void run_sendsSerializedSensoryPayload() throws Exception {
        runBriefly(agent, 30);

        assertFalse(stub.sentPayloads.isEmpty(), "sensory payload must be sent");
        assertTrue(stub.sentPayloads.get(0).length > 0);
    }

    @Test
    void run_suppressesSendWhenMapSensorsReturnsNull() throws Exception {
        agent.sensorsToReturn = null;
        runBriefly(agent, 30);
        assertTrue(stub.sentPayloads.isEmpty(),
                "nothing sent when mapSensors returns null");
    }

    // ── run() — motor frame delivery ─────────────────────────────────────────

    @Test
    void run_deliversFrameWithData_whenPollReturnsBytes() throws Exception {
        stub.nextMotorBytes = new byte[]{10, 20, 30};
        runBriefly(agent, 30);

        assertTrue(agent.framesReceived.stream().anyMatch(AgentFrame::hasData),
                "at least one frame with data must be delivered");
        assertArrayEquals(new byte[]{10, 20, 30},
                agent.framesReceived.stream().filter(AgentFrame::hasData)
                        .findFirst().get().motorBytes());
    }

    @Test
    void run_deliversEmptyFrame_whenPollReturnsNull() throws Exception {
        stub.nextMotorBytes = null;
        runBriefly(agent, 30);

        assertTrue(agent.framesReceived.stream().anyMatch(f -> !f.hasData()),
                "empty frame must be delivered when poll returns null");
    }

    @Test
    void run_passesMapMotorsResultToExecuteCommands() throws Exception {
        stub.nextMotorBytes = new byte[]{1};
        runBriefly(agent, 30);

        // When frame has data, mapMotors returns "motor_cmd"
        assertTrue(agent.commandsReceived.stream().anyMatch("motor_cmd"::equals),
                "executeCommands must receive the object returned by mapMotors");
    }

    @Test
    void run_passesNullCommandWhenNoMotorData() throws Exception {
        stub.nextMotorBytes = null;
        runBriefly(agent, 30);

        // When frame is empty, mapMotors returns null
        assertTrue(agent.commandsReceived.stream().anyMatch(c -> c == null),
                "executeCommands must receive null when mapMotors returns null");
    }

    // ── run() — error handling ────────────────────────────────────────────────

    @Test
    void run_abortsAfterMaxConsecutiveErrors() throws Exception {
        agent.throwOnMapSensors = true;
        agent.connect();

        AgentRunConfig cfg = AgentRunConfig.builder()
                .tickInterval(Duration.ZERO)
                .maxConsecutiveErrors(3)
                .build();

        assertThrows(FeagiSdkException.class, () -> agent.run(cfg));
        assertEquals(3, agent.mapSensorsCalls,
                "must attempt exactly maxConsecutiveErrors ticks");
    }

    @Test
    void run_countsErrorsAcrossAllHooks() throws Exception {
        agent.throwOnExecute = true;
        agent.connect();

        AgentRunConfig cfg = AgentRunConfig.builder()
                .tickInterval(Duration.ZERO)
                .maxConsecutiveErrors(2)
                .build();

        assertThrows(FeagiSdkException.class, () -> agent.run(cfg));
        assertEquals(2, agent.executeCommandsCalls);
    }

    @Test
    void run_resetsConsecutiveErrorCountAfterSuccess() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        BaseAgent resilient = new BaseAgent("r", RecordingAgent.makeConfig(), stub) {
            @Override protected void initializeHardware() {}
            @Override protected Object readSensors()           { return null; }
            @Override protected Map<String,byte[]> mapSensors(Object d) throws Exception {
                if (calls.incrementAndGet() <= 2) throw new RuntimeException("transient");
                return null;
            }
            @Override protected Object mapMotors(AgentFrame f) { return null; }
            @Override protected void closeHardware()           {}
        };
        resilient.connect();

        AgentRunConfig cfg = AgentRunConfig.builder()
                .tickInterval(Duration.ZERO).maxConsecutiveErrors(3).build();

        var exec = Executors.newSingleThreadExecutor();
        Future<?> f = exec.submit(() -> {
            try { resilient.run(cfg); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        Thread.sleep(40);
        resilient.stop();
        // Should NOT throw — errors reset after success
        assertDoesNotThrow(() -> f.get(2, TimeUnit.SECONDS));
        exec.shutdown();
    }

    // ── stop() ────────────────────────────────────────────────────────────────

    @Test
    void stop_fromAnotherThread_terminatesLoop() throws Exception {
        agent.connect();
        CountDownLatch started = new CountDownLatch(1);

        Thread loopThread = new Thread(() -> {
            try {
                started.countDown();
                agent.run(AgentRunConfig.builder().tickInterval(Duration.ZERO).build());
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        loopThread.start();
        assertTrue(started.await(1, TimeUnit.SECONDS));
        Thread.sleep(10);
        agent.stop();
        loopThread.join(2000);

        assertFalse(loopThread.isAlive(), "loop must terminate after stop()");
    }

    @Test
    void stop_isIdempotent() {
        agent.stop();
        agent.stop(); // must not throw
    }

    // ── close() ───────────────────────────────────────────────────────────────

    @Test
    void close_closesTransportAndHardware() throws Exception {
        runBriefly(agent, 20);
        // closeHardware() is now called by run()'s finally block, not by close().
        assertEquals(1, agent.closeHwCalls,
                "closeHardware must be called by run() finally block");
        agent.close();
        assertEquals(1, stub.closeCalls);
        // closeHardware must not be called a second time by close()
        assertEquals(1, agent.closeHwCalls,
                "closeHardware must not be called again by close()");
        assertFalse(agent.isConnected());
        assertFalse(agent.isHardwareInitialized());
    }

    @Test
    void close_withoutRunning_doesNotCallCloseHardware() {
        agent.connect();
        agent.close();

        // Hardware was never initialized (run() not called), so closeHardware must not fire
        assertEquals(0, agent.closeHwCalls,
                "closeHardware must not be called if hardware was never initialized");
        assertEquals(1, stub.closeCalls);
    }

    @Test
    void close_isIdempotent() throws Exception {
        runBriefly(agent, 20);
        agent.close();
        agent.close(); // must not throw or double-free
    }

    @Test
    void close_callsCloseHardwareEvenWhenClientThrows() throws Exception {
        StubFeagiAgentClient badClient = new StubFeagiAgentClient() {
            @Override public void close() { throw new RuntimeException("transport close failed"); }
        };
        AtomicInteger hwCloses = new AtomicInteger();
        BaseAgent a = new BaseAgent("x", RecordingAgent.makeConfig(), badClient) {
            @Override protected void initializeHardware() {}
            @Override protected Object readSensors() { return null; }
            @Override protected Map<String,byte[]> mapSensors(Object d) { return null; }
            @Override protected Object mapMotors(AgentFrame f) { return null; }
            @Override protected void closeHardware() { hwCloses.incrementAndGet(); }
        };
        a.connect();
        var exec = Executors.newSingleThreadExecutor();
        Future<?> f = exec.submit(() -> {
            try { a.run(AgentRunConfig.builder().tickInterval(Duration.ZERO).build()); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        Thread.sleep(30); // let at least one tick run
        a.stop();
        f.get(2, TimeUnit.SECONDS);
        exec.shutdown();

        // Hardware is now released by run()'s finally block, not by close().
        assertEquals(1, hwCloses.get(), "closeHardware must be called by run() finally block");

        // close() must not propagate the transport exception, and must not double-call closeHardware
        assertDoesNotThrow(a::close);
        assertEquals(1, hwCloses.get(), "closeHardware must not be called a second time by close()");
    }

    // ── AgentFrame ────────────────────────────────────────────────────────────

    @Test
    void agentFrame_empty_hasNoData() {
        AgentFrame f = AgentFrame.empty();
        assertFalse(f.hasData());
        assertNull(f.motorBytes());
    }

    @Test
    void agentFrame_of_hasData() {
        byte[] data = {1, 2, 3};
        AgentFrame f = AgentFrame.of(data);
        assertTrue(f.hasData());
        assertArrayEquals(data, f.motorBytes());
    }

    @Test
    void agentFrame_of_rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> AgentFrame.of(null));
    }

    @Test
    void agentFrame_empty_returnsSameInstance() {
        assertSame(AgentFrame.empty(), AgentFrame.empty());
    }

    // ── AgentRunConfig ────────────────────────────────────────────────────────

    @Test
    void agentRunConfig_defaults_areReasonable() {
        AgentRunConfig cfg = AgentRunConfig.defaults();
        assertFalse(cfg.tickInterval().isNegative());
        assertTrue(cfg.maxConsecutiveErrors() >= 1);
    }

    @Test
    void agentRunConfig_rejectsNegativeTickInterval() {
        assertThrows(IllegalArgumentException.class,
                () -> AgentRunConfig.builder().tickInterval(Duration.ofMillis(-1)).build());
    }

    @Test
    void agentRunConfig_rejectsZeroMaxErrors() {
        assertThrows(IllegalArgumentException.class,
                () -> AgentRunConfig.builder().maxConsecutiveErrors(0).build());
    }

    // ── serializeSensoryData ──────────────────────────────────────────────────

    @Test
    void serializeSensoryData_nullReturnsNull() {
        assertNull(agent.serializeSensoryData(null));
    }

    @Test
    void serializeSensoryData_emptyMapReturnsNull() {
        assertNull(agent.serializeSensoryData(Map.of()));
    }

    @Test
    void serializeSensoryData_singleChannel_decodesCorrectly() {
        byte[] value = {10, 20, 30};
        byte[] buf = agent.serializeSensoryData(Map.of("cam", value));
        assertNotNull(buf);

        // Parse: 4-byte big-endian key len, key bytes, 4-byte value len, value bytes
        int kLen = ((buf[0] & 0xFF) << 24) | ((buf[1] & 0xFF) << 16)
                 | ((buf[2] & 0xFF) <<  8) |  (buf[3] & 0xFF);
        String key = new String(buf, 4, kLen, java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("cam", key);

        int off  = 4 + kLen;
        int vLen = ((buf[off] & 0xFF) << 24) | ((buf[off+1] & 0xFF) << 16)
                 | ((buf[off+2] & 0xFF) << 8) |  (buf[off+3] & 0xFF);
        byte[] parsed = new byte[vLen];
        System.arraycopy(buf, off + 4, parsed, 0, vLen);
        assertArrayEquals(value, parsed);
    }
}
