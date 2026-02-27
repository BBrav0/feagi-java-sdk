package io.feagi.sdk.nativeffi;

import io.feagi.sdk.core.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NativeFeagiAgentClient that do NOT require the native library.
 *
 * <p>Covers:
 * <ul>
 *   <li>Null config rejection at construction time</li>
 *   <li>Guard-rails on sendSensoryBytes / pollMotorBytes before connect()</li>
 *   <li>Null / empty payload rejection on sendSensoryBytes</li>
 *   <li>Idempotent close()</li>
 * </ul>
 *
 * <p>Integration tests (requiring the native library) live in the smoke test suite.
 */
class NativeFeagiAgentClientTest {

    // ── Test fixture ───────────────────────────────────────────────────────────

    private static AgentConfig minimalConfig() {
        FeagiEndpoints endpoints = new FeagiEndpoints(
                "tcp://localhost:30001",   // registration — required
                "tcp://localhost:5558",    // sensory
                "tcp://localhost:5564",    // motor
                null,                      // visualization — not used
                null                       // control — not used
        );

        // VisionCapability.fromUnit() — Option B: SensoryUnit enum + group
        VisionCapability vision = VisionCapability.fromUnit(
                "camera",
                320, 240, 3,
                SensoryUnit.VISION,
                0
        );

        // MotorCapability.fromUnit() — Option B: MotorUnit enum + group
        MotorCapability motor = MotorCapability.fromUnit(
                "drive",
                4,
                MotorUnit.ROTARY_MOTOR,
                0
        );

        AgentCapabilities caps = AgentCapabilities.builder()
                .vision(vision)
                .motor(motor)
                .build();

        return new AgentConfig(
                "test-agent",
                AgentType.BOTH,
                endpoints,
                caps,
                Duration.ofSeconds(5),      // heartbeatInterval
                Duration.ofSeconds(10),     // connectionTimeout
                3,                          // registrationRetries
                Duration.ofMillis(500),     // retryBackoff
                new SensorySocketConfig(1000, 0, true)
        );
    }

    // ── Construction ───────────────────────────────────────────────────────────

    @Test
    void constructor_rejectsNullConfig() {
        assertThrows(IllegalArgumentException.class,
                () -> new NativeFeagiAgentClient(null));
    }

    @Test
    void constructor_acceptsValidConfig() {
        assertDoesNotThrow(() -> new NativeFeagiAgentClient(minimalConfig()));
    }

    // ── Guard-rails before connect() ───────────────────────────────────────────

    @Test
    void sendSensoryBytes_beforeConnect_throwsIllegalState() {
        var client = new NativeFeagiAgentClient(minimalConfig());
        assertThrows(IllegalStateException.class,
                () -> client.sendSensoryBytes(new byte[]{1, 2, 3}));
    }

    @Test
    void pollMotorBytes_beforeConnect_throwsIllegalState() {
        var client = new NativeFeagiAgentClient(minimalConfig());
        assertThrows(IllegalStateException.class, client::pollMotorBytes);
    }

    // ── Input validation ───────────────────────────────────────────────────────

    @Test
    void sendSensoryBytes_rejectsNullPayload() {
        var client = new NativeFeagiAgentClient(minimalConfig());
        // Null payload is rejected before connection state is checked
        assertThrows(IllegalArgumentException.class,
                () -> client.sendSensoryBytes(null));
    }

    @Test
    void sendSensoryBytes_rejectsEmptyPayload() {
        var client = new NativeFeagiAgentClient(minimalConfig());
        assertThrows(IllegalArgumentException.class,
                () -> client.sendSensoryBytes(new byte[0]));
    }

    // ── Idempotent close ───────────────────────────────────────────────────────

    @Test
    void close_beforeConnect_doesNotThrow() {
        var client = new NativeFeagiAgentClient(minimalConfig());
        assertDoesNotThrow(client::close);
    }

    @Test
    void close_isIdempotent() {
        var client = new NativeFeagiAgentClient(minimalConfig());
        assertDoesNotThrow(client::close);
        assertDoesNotThrow(client::close);
    }

    // ── Config variations ──────────────────────────────────────────────────────

    @Test
    void constructor_acceptsVisionFromTargetArea() {
        FeagiEndpoints endpoints = new FeagiEndpoints(
                "tcp://localhost:30001",
                "tcp://localhost:5558",
                null, null, null
        );

        VisionCapability vision = VisionCapability.fromTargetArea(
                "camera", 640, 480, 3, "vision_cortex"
        );

        AgentCapabilities caps = AgentCapabilities.builder().vision(vision).build();

        AgentConfig config = new AgentConfig(
                "vision-agent",
                AgentType.SENSORY,
                endpoints,
                caps,
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                3,
                Duration.ofMillis(500),
                new SensorySocketConfig(1000, 0, true)
        );

        assertDoesNotThrow(() -> new NativeFeagiAgentClient(config));
    }

    @Test
    void constructor_acceptsMotorFromCorticalAreas() {
        FeagiEndpoints endpoints = new FeagiEndpoints(
                "tcp://localhost:30001",
                null,
                "tcp://localhost:5564",
                null, null
        );

        MotorCapability motor = MotorCapability.fromCorticalAreas(
                "servo", 2, java.util.List.of("m_servo_0", "m_servo_1")
        );

        AgentCapabilities caps = AgentCapabilities.builder().motor(motor).build();

        AgentConfig config = new AgentConfig(
                "motor-agent",
                AgentType.MOTOR,
                endpoints,
                caps,
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                3,
                Duration.ofMillis(500),
                new SensorySocketConfig(1000, 0, true)
        );

        assertDoesNotThrow(() -> new NativeFeagiAgentClient(config));
    }

    @Test
    void constructor_acceptsMotorFromUnits() {
        FeagiEndpoints endpoints = new FeagiEndpoints(
                "tcp://localhost:30001",
                null,
                "tcp://localhost:5564",
                null, null
        );

        MotorCapability motor = MotorCapability.fromUnits(
                "mixed", 4,
                java.util.List.of(
                        new MotorUnitSpec(MotorUnit.ROTARY_MOTOR, 0),
                        new MotorUnitSpec(MotorUnit.POSITIONAL_SERVO, 1)
                )
        );

        AgentCapabilities caps = AgentCapabilities.builder().motor(motor).build();

        AgentConfig config = new AgentConfig(
                "multi-motor-agent",
                AgentType.MOTOR,
                endpoints,
                caps,
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                3,
                Duration.ofMillis(500),
                new SensorySocketConfig(1000, 0, true)
        );

        assertDoesNotThrow(() -> new NativeFeagiAgentClient(config));
    }
}