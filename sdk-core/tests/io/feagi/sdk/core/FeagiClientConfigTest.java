/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FeagiClientConfig}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Builder rejects {@code build()} when required fields are missing</li>
 *   <li>All validation rules for each field (type, range, format)</li>
 *   <li>Optional fields default to {@code null} / sentinel when not set</li>
 *   <li>{@link FeagiClientConfig#from(AgentConfig)} round-trip</li>
 *   <li>Auth token validation (standard vs URL-safe Base64, length)</li>
 * </ul>
 */
class FeagiClientConfigTest {

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Minimal valid builder with all required fields set. */
    private static FeagiClientConfig.Builder minimalBuilder() {
        return FeagiClientConfig.builder()
                .registrationEndpoint("tcp://localhost:30001")
                .connectionTimeout(Duration.ofSeconds(5))
                .heartbeatInterval(Duration.ofSeconds(1))
                .registrationRetries(3)
                .retryBackoff(Duration.ofMillis(500))
                .sensorySocketConfig(new SensorySocketConfig(1000, 0, true));
    }

    /** Valid 32-byte standard Base64 auth token. */
    private static String validAuthToken() {
        return Base64.getEncoder().encodeToString(new byte[32]);
    }

    // ── Missing required fields ────────────────────────────────────────────────

    @Test
    void build_missingRegistrationEndpoint_throws() {
        var b = FeagiClientConfig.builder()
                .connectionTimeout(Duration.ofSeconds(5))
                .heartbeatInterval(Duration.ofSeconds(1))
                .registrationRetries(3)
                .retryBackoff(Duration.ofMillis(500))
                .sensorySocketConfig(new SensorySocketConfig(1000, 0, true));
        assertThrows(IllegalStateException.class, b::build);
    }

    @Test
    void build_missingConnectionTimeout_throws() {
        var b = FeagiClientConfig.builder()
                .registrationEndpoint("tcp://localhost:30001")
                .heartbeatInterval(Duration.ofSeconds(1))
                .registrationRetries(3)
                .retryBackoff(Duration.ofMillis(500))
                .sensorySocketConfig(new SensorySocketConfig(1000, 0, true));
        assertThrows(IllegalStateException.class, b::build);
    }

    @Test
    void build_missingHeartbeatInterval_throws() {
        var b = FeagiClientConfig.builder()
                .registrationEndpoint("tcp://localhost:30001")
                .connectionTimeout(Duration.ofSeconds(5))
                .registrationRetries(3)
                .retryBackoff(Duration.ofMillis(500))
                .sensorySocketConfig(new SensorySocketConfig(1000, 0, true));
        assertThrows(IllegalStateException.class, b::build);
    }

    @Test
    void build_missingRegistrationRetries_throws() {
        var b = FeagiClientConfig.builder()
                .registrationEndpoint("tcp://localhost:30001")
                .connectionTimeout(Duration.ofSeconds(5))
                .heartbeatInterval(Duration.ofSeconds(1))
                .retryBackoff(Duration.ofMillis(500))
                .sensorySocketConfig(new SensorySocketConfig(1000, 0, true));
        assertThrows(IllegalStateException.class, b::build);
    }

    @Test
    void build_missingRetryBackoff_throws() {
        // retryBackoff is required only when registrationRetries > 0
        var b = FeagiClientConfig.builder()
                .registrationEndpoint("tcp://localhost:30001")
                .connectionTimeout(Duration.ofSeconds(5))
                .heartbeatInterval(Duration.ofSeconds(1))
                .registrationRetries(3) // > 0, so retryBackoff is required
                .sensorySocketConfig(new SensorySocketConfig(1000, 0, true));
        assertThrows(IllegalStateException.class, b::build);
    }

    @Test
    void retryBackoff_zero_acceptedWhenRetriesAreZero() {
        // Duration.ZERO is a valid explicit "no backoff" when retries are disabled
        assertDoesNotThrow(() -> FeagiClientConfig.builder()
                .registrationEndpoint("tcp://localhost:30001")
                .connectionTimeout(Duration.ofSeconds(5))
                .heartbeatInterval(Duration.ofSeconds(1))
                .registrationRetries(0)
                .retryBackoff(Duration.ZERO)
                .sensorySocketConfig(new SensorySocketConfig(1000, 0, true))
                .build());
    }

    @Test
    void retryBackoff_zero_rejectedWhenRetriesArePositive() {
        var b = FeagiClientConfig.builder()
                .registrationEndpoint("tcp://localhost:30001")
                .connectionTimeout(Duration.ofSeconds(5))
                .heartbeatInterval(Duration.ofSeconds(1))
                .registrationRetries(3)
                .retryBackoff(Duration.ZERO)
                .sensorySocketConfig(new SensorySocketConfig(1000, 0, true));
        assertThrows(IllegalStateException.class, b::build);
    }

    @Test
    void build_retryBackoffNotRequired_whenRetriesAreZero() {
        FeagiClientConfig cfg = FeagiClientConfig.builder()
                .registrationEndpoint("tcp://localhost:30001")
                .connectionTimeout(Duration.ofSeconds(5))
                .heartbeatInterval(Duration.ofSeconds(1))
                .registrationRetries(0)
                .sensorySocketConfig(new SensorySocketConfig(1000, 0, true))
                .build();
        assertEquals(Duration.ZERO, cfg.retryBackoff(),
                "retryBackoff() must return Duration.ZERO (not null) when retries=0");
    }

    @Test
    void equals_twoZeroRetryConfigs_withSameBackoff_areEqual() {
        // Without the special-case, two zero-retry configs are equal only if
        // retryBackoff is also equal (both Duration.ZERO via normalization).
        FeagiClientConfig a = FeagiClientConfig.builder()
                .registrationEndpoint("tcp://localhost:30001")
                .connectionTimeout(Duration.ofSeconds(5))
                .heartbeatInterval(Duration.ofSeconds(1))
                .registrationRetries(0)
                .sensorySocketConfig(new SensorySocketConfig(1000, 0, true))
                .build();
        FeagiClientConfig b = FeagiClientConfig.builder()
                .registrationEndpoint("tcp://localhost:30001")
                .connectionTimeout(Duration.ofSeconds(5))
                .heartbeatInterval(Duration.ofSeconds(1))
                .registrationRetries(0)
                .sensorySocketConfig(new SensorySocketConfig(1000, 0, true))
                .build();
        assertEquals(a, b);
    }

    // ── endpoint scheme validation ─────────────────────────────────────────────

    @Test
    void registrationEndpoint_ipcScheme_isAccepted() {
        // ipc:// is valid for local/test use
        assertDoesNotThrow(() -> minimalBuilder()
                .registrationEndpoint("ipc:///tmp/feagi-reg.sock")
                .build());
    }

    @Test
    void registrationEndpoint_inprocScheme_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> FeagiClientConfig.builder()
                        .registrationEndpoint("inproc://feagi"));
    }

    // ── retryBackoff error message distinction ────────────────────────────────

    @Test
    void build_retryBackoffSetToZeroWithRetriesPositive_givesDistinctError() {
        var b = FeagiClientConfig.builder()
                .registrationEndpoint("tcp://localhost:30001")
                .connectionTimeout(Duration.ofSeconds(5))
                .heartbeatInterval(Duration.ofSeconds(1))
                .registrationRetries(3)
                .retryBackoff(Duration.ZERO)
                .sensorySocketConfig(new SensorySocketConfig(1000, 0, true));
        IllegalStateException ex = assertThrows(IllegalStateException.class, b::build);
        // Message must not say "no SDK default" — the caller DID set it, to zero
        assertFalse(ex.getMessage().contains("no SDK default"),
                "Error must distinguish 'set to zero' from 'not set'");
        assertTrue(ex.getMessage().contains("Duration.ZERO"),
                "Error must mention Duration.ZERO to explain what was wrong");
    }

    @Test
    void build_missingSensorySocketConfig_throws() {
        var b = FeagiClientConfig.builder()
                .registrationEndpoint("tcp://localhost:30001")
                .connectionTimeout(Duration.ofSeconds(5))
                .heartbeatInterval(Duration.ofSeconds(1))
                .registrationRetries(3)
                .retryBackoff(Duration.ofMillis(500));
        assertThrows(IllegalStateException.class, b::build);
    }

    // ── Successful build ───────────────────────────────────────────────────────

    @Test
    void build_withAllRequiredFields_succeeds() {
        FeagiClientConfig cfg = minimalBuilder().build();
        assertEquals("tcp://localhost:30001", cfg.registrationEndpoint());
        assertEquals(Duration.ofSeconds(5), cfg.connectionTimeout());
        assertEquals(Duration.ofSeconds(1), cfg.heartbeatInterval());
        assertEquals(3, cfg.registrationRetries());
        assertEquals(Duration.ofMillis(500), cfg.retryBackoff());
        assertNotNull(cfg.sensorySocketConfig());
    }

    @Test
    void build_optionalFieldsDefaultToNull() {
        FeagiClientConfig cfg = minimalBuilder().build();
        assertNull(cfg.sensoryEndpoint());
        assertNull(cfg.motorEndpoint());
        assertNull(cfg.visualizationEndpoint());
        assertNull(cfg.controlEndpoint());
        assertNull(cfg.authTokenBase64());
        assertNull(cfg.manufacturer());
        assertNull(cfg.agentName());
        assertFalse(cfg.agentVersion().isPresent());
        assertTrue(cfg.agentVersion().isEmpty());
    }

    @Test
    void build_withAllOptionalFields_roundTrips() {
        String token = validAuthToken();
        FeagiClientConfig cfg = minimalBuilder()
                .sensoryEndpoint("tcp://localhost:5558")
                .motorEndpoint("tcp://localhost:5564")
                .visualizationEndpoint("tcp://localhost:5570")
                .controlEndpoint("tcp://localhost:5580")
                .authTokenBase64(token)
                .manufacturer("Neuraville")
                .agentName("TestAgent")
                .agentVersion(2)
                .build();

        assertEquals("tcp://localhost:5558", cfg.sensoryEndpoint());
        assertEquals("tcp://localhost:5564", cfg.motorEndpoint());
        assertEquals("tcp://localhost:5570", cfg.visualizationEndpoint());
        assertEquals("tcp://localhost:5580", cfg.controlEndpoint());
        assertEquals(token, cfg.authTokenBase64());
        assertEquals("Neuraville", cfg.manufacturer());
        assertEquals("TestAgent", cfg.agentName());
        assertEquals(2, cfg.agentVersion().getAsInt());
    }

    // ── registrationEndpoint validation ───────────────────────────────────────

    @Test
    void registrationEndpoint_null_throws() {
        assertThrows(NullPointerException.class,
                () -> FeagiClientConfig.builder().registrationEndpoint(null));
    }

    @Test
    void registrationEndpoint_blank_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> FeagiClientConfig.builder().registrationEndpoint("  "));
    }

    @Test
    void registrationEndpoint_nonTcp_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> FeagiClientConfig.builder().registrationEndpoint("http://localhost:30001"));
    }

    @Test
    void registrationEndpoint_barePrefix_throws() {
        // "tcp://" with nothing after it must be rejected
        assertThrows(IllegalArgumentException.class,
                () -> FeagiClientConfig.builder().registrationEndpoint("tcp://"));
    }

    // ── connectionTimeout validation ──────────────────────────────────────────

    @Test
    void connectionTimeout_zero_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> FeagiClientConfig.builder().connectionTimeout(Duration.ZERO));
    }

    @Test
    void connectionTimeout_negative_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> FeagiClientConfig.builder().connectionTimeout(Duration.ofMillis(-1)));
    }

    // ── heartbeatInterval validation ──────────────────────────────────────────

    @Test
    void heartbeatInterval_zero_isAllowed() {
        // Duration.ZERO disables heartbeating — this is a valid explicit choice
        assertDoesNotThrow(() -> minimalBuilder()
                .heartbeatInterval(Duration.ZERO)
                .build());
    }

    @Test
    void heartbeatInterval_negative_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> FeagiClientConfig.builder().heartbeatInterval(Duration.ofSeconds(-1)));
    }

    // ── registrationRetries validation ────────────────────────────────────────

    @Test
    void registrationRetries_zero_isAllowed() {
        // 0 retries = try once and fail immediately — valid explicit choice
        assertDoesNotThrow(() -> minimalBuilder()
                .registrationRetries(0)
                .build());
    }

    @Test
    void registrationRetries_negative_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> FeagiClientConfig.builder().registrationRetries(-1));
    }

    // ── Optional endpoint validation ──────────────────────────────────────────

    @Test
    void sensoryEndpoint_nonTcp_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> FeagiClientConfig.builder().sensoryEndpoint("udp://localhost:5558"));
    }

    @Test
    void sensoryEndpoint_null_isAllowed() {
        assertDoesNotThrow(() -> minimalBuilder().sensoryEndpoint(null).build());
    }

    // ── authTokenBase64 validation ────────────────────────────────────────────

    @Test
    void authToken_urlSafeBase64_throws() {
        // Byte value 0xFB encodes to '+' in standard Base64 and '-' in URL-safe Base64.
        // Build a 32-byte array containing 0xFB to guarantee a '-' appears in the
        // URL-safe encoding, then encode with getUrlEncoder() to produce a token
        // the validator must reject.
        byte[] bytes = new byte[32];
        bytes[0] = (byte) 0xFB; // guarantees '-' in URL-safe output
        String urlSafe = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        assertTrue(urlSafe.contains("-") || urlSafe.contains("_"),
                "Test setup error: URL-safe token must contain - or _");
        assertThrows(IllegalArgumentException.class,
                () -> FeagiClientConfig.builder().authTokenBase64(urlSafe));
    }

    @Test
    void authToken_invalidBase64_throws() {
        // "not@valid@base64" has no - or _, so the URL-safe check does not fire.
        // The @ chars are illegal Base64 characters, so the decoder path is exercised.
        assertThrows(IllegalArgumentException.class,
                () -> FeagiClientConfig.builder().authTokenBase64("not@valid@base64"));
    }

    @Test
    void authToken_wrongByteLength_throws() {
        // Valid Base64 but only 16 bytes
        String short16 = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalArgumentException.class,
                () -> FeagiClientConfig.builder().authTokenBase64(short16));
    }

    @Test
    void authToken_exactly32Bytes_isAccepted() {
        assertDoesNotThrow(() -> minimalBuilder()
                .authTokenBase64(validAuthToken())
                .build());
    }

    @Test
    void authToken_null_isAllowed() {
        assertDoesNotThrow(() -> minimalBuilder().authTokenBase64(null).build());
    }

    // ── agentVersion validation ───────────────────────────────────────────────

    @Test
    void agentVersion_negative_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> FeagiClientConfig.builder().agentVersion(-1));
    }

    @Test
    void agentVersion_zero_isAllowed() {
        FeagiClientConfig cfg = minimalBuilder().agentVersion(0).build();
        assertEquals(0, cfg.agentVersion().getAsInt());
    }

    // ── manufacturer / agentName validation ──────────────────────────────────

    @Test
    void manufacturer_blank_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> FeagiClientConfig.builder().manufacturer("   "));
    }

    @Test
    void agentName_blank_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> FeagiClientConfig.builder().agentName(""));
    }

    // ── from(AgentConfig) ─────────────────────────────────────────────────────

    @Test
    void from_agentConfig_roundTripsAllConnectionFields() {
        FeagiEndpoints ep = new FeagiEndpoints(
                "tcp://localhost:30001",
                "tcp://localhost:5558",
                "tcp://localhost:5564",
                null, null);
        AgentCapabilities caps = AgentCapabilities.builder()
                .sensory(new SensoryCapability(30.0, null))
                .motor(MotorCapability.fromUnit("drive", 4, MotorUnit.ROTARY_MOTOR, 0))
                .build();
        AgentConfig agentConfig = new AgentConfig(
                "test-agent", AgentType.BOTH, ep, caps,
                Duration.ofSeconds(1),
                Duration.ofSeconds(10),
                5,
                Duration.ofMillis(250),
                new SensorySocketConfig(1000, 0, true));

        FeagiClientConfig clientConfig = FeagiClientConfig.from(agentConfig);

        assertEquals("tcp://localhost:30001", clientConfig.registrationEndpoint());
        assertEquals("tcp://localhost:5558",  clientConfig.sensoryEndpoint());
        assertEquals("tcp://localhost:5564",  clientConfig.motorEndpoint());
        assertNull(clientConfig.visualizationEndpoint());
        assertNull(clientConfig.controlEndpoint());
        assertEquals(Duration.ofSeconds(10),  clientConfig.connectionTimeout());
        assertEquals(Duration.ofSeconds(1),   clientConfig.heartbeatInterval());
        assertEquals(5,                       clientConfig.registrationRetries());
        assertEquals(Duration.ofMillis(250),  clientConfig.retryBackoff());
        assertNotNull(clientConfig.sensorySocketConfig());
    }

    @Test
    void from_nullAgentConfig_throws() {
        assertThrows(NullPointerException.class,
                () -> FeagiClientConfig.from(null));
    }

    // ── equals / hashCode ─────────────────────────────────────────────────────

    @Test
    void equals_identicalConfigs_areEqual() {
        FeagiClientConfig a = minimalBuilder().build();
        FeagiClientConfig b = minimalBuilder().build();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equals_differentEndpoint_areNotEqual() {
        FeagiClientConfig a = minimalBuilder().build();
        FeagiClientConfig b = minimalBuilder()
                .registrationEndpoint("tcp://other-host:30001")
                .build();
        assertNotEquals(a, b);
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Test
    void toString_doesNotExposeAuthToken() {
        FeagiClientConfig cfg = minimalBuilder()
                .authTokenBase64(validAuthToken())
                .build();
        String s = cfg.toString();
        assertFalse(s.contains(validAuthToken()),
                "toString must not expose raw auth token");
        assertTrue(s.contains("<set>"),
                "toString must indicate auth token is set");
    }

    @Test
    void toString_containsRegistrationEndpoint() {
        FeagiClientConfig cfg = minimalBuilder()
                .visualizationEndpoint("tcp://localhost:5570")
                .controlEndpoint("tcp://localhost:5580")
                .manufacturer("Neuraville")
                .agentName("TestAgent")
                .build();
        String s = cfg.toString();
        assertTrue(s.contains("tcp://localhost:30001"), "missing registrationEndpoint");
        assertTrue(s.contains("tcp://localhost:5570"),  "missing visualizationEndpoint");
        assertTrue(s.contains("tcp://localhost:5580"),  "missing controlEndpoint");
        assertTrue(s.contains("Neuraville"),            "missing manufacturer");
        assertTrue(s.contains("TestAgent"),             "missing agentName");
    }

    // ── isAgentVersionSet ─────────────────────────────────────────────────────

    @Test
    void isAgentVersionSet_falseByDefault() {
        assertFalse(minimalBuilder().build().agentVersion().isPresent());
        assertTrue(minimalBuilder().build().agentVersion().isEmpty());
    }

    @Test
    void isAgentVersionSet_trueAfterSet() {
        FeagiClientConfig cfg = minimalBuilder().agentVersion(0).build();
        assertTrue(cfg.agentVersion().isPresent());
        assertEquals(0, cfg.agentVersion().getAsInt());
    }
}
