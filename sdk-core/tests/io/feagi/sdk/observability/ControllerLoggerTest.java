/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ControllerLogger}.
 */
@DisplayName("ControllerLogger Tests")
class ControllerLoggerTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("should create logger with default settings")
        void shouldCreateLoggerWithDefaultSettings() {
            ControllerLogger logger = new ControllerLogger.Builder()
                .controllerName("TestController")
                .build();

            assertNotNull(logger);
            assertTrue(logger.isEnabled());
        }

        @Test
        @DisplayName("should create logger with custom settings")
        void shouldCreateLoggerWithCustomSettings() {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            ControllerLogger logger = new ControllerLogger.Builder()
                .controllerName("CustomController")
                .showTimestamps(false)
                .logLevel(ControllerLogger.LogLevel.DEBUG)
                .stream(new PrintStream(outputStream))
                .enabled(true)
                .build();

            assertNotNull(logger);
            assertTrue(logger.isEnabled());
        }

        @Test
        @DisplayName("should create disabled logger")
        void shouldCreateDisabledLogger() {
            ControllerLogger logger = new ControllerLogger.Builder()
                .controllerName("DisabledController")
                .enabled(false)
                .build();

            assertNotNull(logger);
            assertFalse(logger.isEnabled());
        }
    }

    @Nested
    @DisplayName("Log Level Tests")
    class LogLevelTests {

        @Test
        @DisplayName("should log at INFO level")
        void shouldLogAtInfoLevel() {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            ControllerLogger logger = new ControllerLogger.Builder()
                .controllerName("InfoTest")
                .showTimestamps(false)
                .logLevel(ControllerLogger.LogLevel.INFO)
                .stream(new PrintStream(outputStream))
                .build();

            logger.info("Test info message");

            String output = outputStream.toString();
            assertTrue(output.contains("Test info message"));

            logger.disable();
        }

        @Test
        @DisplayName("should log at DEBUG level")
        void shouldLogAtDebugLevel() {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            ControllerLogger logger = new ControllerLogger.Builder()
                .controllerName("DebugTest")
                .showTimestamps(false)
                .logLevel(ControllerLogger.LogLevel.DEBUG)
                .stream(new PrintStream(outputStream))
                .build();

            logger.debug("Test debug message");

            String output = outputStream.toString();
            assertTrue(output.contains("Test debug message"));

            logger.disable();
        }

        @Test
        @DisplayName("should log at WARNING level")
        void shouldLogAtWarningLevel() {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            ControllerLogger logger = new ControllerLogger.Builder()
                .controllerName("WarningTest")
                .showTimestamps(false)
                .logLevel(ControllerLogger.LogLevel.WARNING)
                .stream(new PrintStream(outputStream))
                .build();

            logger.warning("Test warning message");

            String output = outputStream.toString();
            assertTrue(output.contains("Test warning message"));

            logger.disable();
        }

        @Test
        @DisplayName("should log at ERROR level")
        void shouldLogAtErrorLevel() {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            ControllerLogger logger = new ControllerLogger.Builder()
                .controllerName("ErrorTest")
                .showTimestamps(false)
                .logLevel(ControllerLogger.LogLevel.ERROR)
                .stream(new PrintStream(outputStream))
                .build();

            logger.error("Test error message");

            String output = outputStream.toString();
            assertTrue(output.contains("Test error message"));

            logger.disable();
        }

        @Test
        @DisplayName("should log at CRITICAL level")
        void shouldLogAtCriticalLevel() {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            ControllerLogger logger = new ControllerLogger.Builder()
                .controllerName("CriticalTest")
                .showTimestamps(false)
                .logLevel(ControllerLogger.LogLevel.CRITICAL)
                .stream(new PrintStream(outputStream))
                .build();

            logger.critical("Test critical message");

            String output = outputStream.toString();
            assertTrue(output.contains("Test critical message"));

            logger.disable();
        }

        @Test
        @DisplayName("should not log below configured level")
        void shouldNotLogBelowConfiguredLevel() {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            ControllerLogger logger = new ControllerLogger.Builder()
                .controllerName("LevelTest")
                .showTimestamps(false)
                .logLevel(ControllerLogger.LogLevel.WARNING)
                .stream(new PrintStream(outputStream))
                .build();

            logger.debug("Debug message");
            logger.info("Info message");
            logger.warning("Warning message");

            String output = outputStream.toString();
            assertFalse(output.contains("Debug message"));
            assertFalse(output.contains("Info message"));
            assertTrue(output.contains("Warning message"));

            logger.disable();
        }
    }

    @Nested
    @DisplayName("Timestamp Tests")
    class TimestampTests {

        @Test
        @DisplayName("should include timestamps when enabled")
        void shouldIncludeTimestampsWhenEnabled() {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            ControllerLogger logger = new ControllerLogger.Builder()
                .controllerName("TimestampTest")
                .showTimestamps(true)
                .logLevel(ControllerLogger.LogLevel.INFO)
                .stream(new PrintStream(outputStream))
                .build();

            logger.info("Test message");

            String output = outputStream.toString();
            // Should contain timestamp pattern with date/time
            // Format is: [yyyy-MM-dd HH:mm:ss] [ControllerName] message
            assertTrue(output.length() > 0, "Output should not be empty: " + output);
            assertTrue(output.contains("20"), "Output should contain year (20xx): " + output);
            assertTrue(output.contains("TimestampTest"), "Output should contain controller name: " + output);

            logger.disable();
        }

        @Test
        @DisplayName("should not include timestamps when disabled")
        void shouldNotIncludeTimestampsWhenDisabled() {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            ControllerLogger logger = new ControllerLogger.Builder()
                .controllerName("NoTimestampTest")
                .showTimestamps(false)
                .stream(new PrintStream(outputStream))
                .build();

            logger.info("Test message");

            String output = outputStream.toString();
            assertFalse(output.matches(".*\\[\\d{4}-\\d{2}-\\d{2}.*\\].*"));
            assertTrue(output.contains("[NoTimestampTest]"));

            logger.disable();
        }
    }

    @Nested
    @DisplayName("Controller Name Tests")
    class ControllerNameTests {

        @Test
        @DisplayName("should include controller name in log")
        void shouldIncludeControllerNameInLog() {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            ControllerLogger logger = new ControllerLogger.Builder()
                .controllerName("MuJoCo")
                .showTimestamps(false)
                .stream(new PrintStream(outputStream))
                .build();

            logger.info("Test message");

            String output = outputStream.toString();
            assertTrue(output.contains("[MuJoCo]"));

            logger.disable();
        }

        @Test
        @DisplayName("should use default controller name")
        void shouldUseDefaultControllerName() {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            ControllerLogger logger = new ControllerLogger.Builder()
                .showTimestamps(false)
                .stream(new PrintStream(outputStream))
                .build();

            logger.info("Test message");

            String output = outputStream.toString();
            assertTrue(output.contains("[Controller]"));

            logger.disable();
        }
    }

    @Nested
    @DisplayName("Monitor Interface Tests")
    class MonitorInterfaceTests {

        @Test
        @DisplayName("should handle onSendStart event")
        void shouldHandleOnSendStartEvent() {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            ControllerLogger logger = new ControllerLogger.Builder()
                .controllerName("MonitorTest")
                .showTimestamps(false)
                .logLevel(ControllerLogger.LogLevel.DEBUG)
                .stream(new PrintStream(outputStream))
                .build();

            Map<String, Object> eventData = new HashMap<>();
            eventData.put("input_count", 3);
            eventData.put("cortical_areas", Arrays.asList("iic100", "iic200"));

            logger.onSendStart(eventData);

            // DEBUG level should log send start
            String output = outputStream.toString();
            assertTrue(output.contains("Sending sensory data"));

            logger.disable();
        }

        @Test
        @DisplayName("should handle onSendComplete event")
        void shouldHandleOnSendCompleteEvent() {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            ControllerLogger logger = new ControllerLogger.Builder()
                .controllerName("MonitorTest")
                .showTimestamps(false)
                .logLevel(ControllerLogger.LogLevel.DEBUG)
                .stream(new PrintStream(outputStream))
                .build();

            Map<String, Object> eventData = new HashMap<>();
            eventData.put("neuron_count", 100);
            eventData.put("packet_size_bytes", 512);
            eventData.put("duration_ms", 10.5);

            logger.onSendComplete(eventData);

            String output = outputStream.toString();
            assertTrue(output.contains("Sent"));
            assertTrue(output.contains("100 neurons"));

            logger.disable();
        }

        @Test
        @DisplayName("should handle onReceiveStart event")
        void shouldHandleOnReceiveStartEvent() {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            ControllerLogger logger = new ControllerLogger.Builder()
                .controllerName("MonitorTest")
                .showTimestamps(false)
                .logLevel(ControllerLogger.LogLevel.DEBUG)
                .stream(new PrintStream(outputStream))
                .build();

            Map<String, Object> eventData = new HashMap<>();
            eventData.put("output_count", 2);

            logger.onReceiveStart(eventData);

            String output = outputStream.toString();
            assertTrue(output.contains("Receiving motor commands"));

            logger.disable();
        }

        @Test
        @DisplayName("should handle onReceiveComplete event")
        void shouldHandleOnReceiveCompleteEvent() {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            ControllerLogger logger = new ControllerLogger.Builder()
                .controllerName("MonitorTest")
                .showTimestamps(false)
                .logLevel(ControllerLogger.LogLevel.DEBUG)
                .stream(new PrintStream(outputStream))
                .build();

            Map<String, Object> eventData = new HashMap<>();
            eventData.put("command_count", 5);
            eventData.put("duration_ms", 2.5);

            logger.onReceiveComplete(eventData);

            String output = outputStream.toString();
            assertTrue(output.contains("Received"));
            assertTrue(output.contains("5 commands"));

            logger.disable();
        }

        @Test
        @DisplayName("should not log monitor events at INFO level")
        void shouldNotLogMonitorEventsAtInfoLevel() {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            ControllerLogger logger = new ControllerLogger.Builder()
                .controllerName("MonitorTest")
                .showTimestamps(false)
                .logLevel(ControllerLogger.LogLevel.INFO)
                .stream(new PrintStream(outputStream))
                .build();

            Map<String, Object> eventData = new HashMap<>();
            eventData.put("neuron_count", 100);
            eventData.put("packet_size_bytes", 512);
            eventData.put("duration_ms", 10.5);

            logger.onSendComplete(eventData);

            String output = outputStream.toString();
            assertFalse(output.contains("Sent"));

            logger.disable();
        }
    }

    @Nested
    @DisplayName("Enable/Disable Tests")
    class EnableDisableTests {

        @Test
        @DisplayName("should not log when disabled")
        void shouldNotLogWhenDisabled() {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            ControllerLogger logger = new ControllerLogger.Builder()
                .controllerName("DisabledTest")
                .showTimestamps(false)
                .enabled(false)
                .stream(new PrintStream(outputStream))
                .build();

            assertFalse(logger.isEnabled());

            logger.info("Test message");
            logger.error("Error message");

            String output = outputStream.toString();
            assertTrue(output.isEmpty());
        }

        @Test
        @DisplayName("should log when enabled")
        void shouldLogWhenEnabled() {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            ControllerLogger logger = new ControllerLogger.Builder()
                .controllerName("EnabledTest")
                .showTimestamps(false)
                .enabled(true)
                .stream(new PrintStream(outputStream))
                .build();

            assertTrue(logger.isEnabled());

            logger.info("Test message");

            String output = outputStream.toString();
            assertTrue(output.contains("Test message"));

            logger.disable();
        }
    }
}
