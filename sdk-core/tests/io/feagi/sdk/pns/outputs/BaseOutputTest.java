/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.pns.outputs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BaseOutput}.
 */
@DisplayName("BaseOutput")
class BaseOutputTest {

    /**
     * Concrete test implementation of BaseOutput for testing.
     */
    private static class TestOutput extends BaseOutput {
        @Override
        protected void _registerWithCache() {
            markRegistered(0);
        }

        @Override
        protected void _readFromCache() {
            // No-op for testing
        }
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("should create with null groupId")
        void shouldCreateWithNullGroupId() {
            TestOutput output = new TestOutput();

            assertNull(output.groupId());
            assertFalse(output.isRegistered());
        }
    }

    @Nested
    @DisplayName("Registration")
    class RegistrationTests {

        @Test
        @DisplayName("should mark as registered after _registerWithCache")
        void shouldMarkAsRegisteredAfterRegisterWithCache() {
            TestOutput output = new TestOutput();

            assertFalse(output.isRegistered());
            assertNull(output.groupId());

            output._registerWithCache();

            assertTrue(output.isRegistered());
            assertEquals(0, output.groupId());
        }

        @Test
        @DisplayName("should not be registered initially")
        void shouldNotBeRegisteredInitially() {
            TestOutput output = new TestOutput();

            assertFalse(output.isRegistered());
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("should throw when value is out of range")
        void shouldThrowWhenValueIsOutOfRange() {
            TestOutput output = new TestOutput();

            assertThrows(IllegalArgumentException.class, () ->
                output.validateRange(-1, "testField")
            );

            assertThrows(IllegalArgumentException.class, () ->
                output.validateRange(256, "testField")
            );
        }

        @Test
        @DisplayName("should accept values in range")
        void shouldAcceptValuesInRange() {
            TestOutput output = new TestOutput();

            // Should not throw
            output.validateRange(0, "testField");
            output.validateRange(128, "testField");
            output.validateRange(255, "testField");
        }

        @Test
        @DisplayName("should throw when value is not positive")
        void shouldThrowWhenValueIsNotPositive() {
            TestOutput output = new TestOutput();

            assertThrows(IllegalArgumentException.class, () ->
                output.validatePositive(0, "testField")
            );

            assertThrows(IllegalArgumentException.class, () ->
                output.validatePositive(-1, "testField")
            );

            assertThrows(IllegalArgumentException.class, () ->
                output.validatePositive(0.0, "testField")
            );

            assertThrows(IllegalArgumentException.class, () ->
                output.validatePositive(-1.0, "testField")
            );
        }

        @Test
        @DisplayName("should accept positive values")
        void shouldAcceptPositiveValues() {
            TestOutput output = new TestOutput();

            // Should not throw
            output.validatePositive(1, "testField");
            output.validatePositive(100, "testField");
            output.validatePositive(1.0, "testField");
            output.validatePositive(100.5, "testField");
        }

        @Test
        @DisplayName("should throw when string is null")
        void shouldThrowWhenStringIsNull() {
            TestOutput output = new TestOutput();

            assertThrows(NullPointerException.class, () ->
                output.validateNotEmpty(null, "testField")
            );
        }

        @Test
        @DisplayName("should throw when string is empty")
        void shouldThrowWhenStringIsEmpty() {
            TestOutput output = new TestOutput();

            assertThrows(IllegalArgumentException.class, () ->
                output.validateNotEmpty("", "testField")
            );
        }

        @Test
        @DisplayName("should accept non-empty string")
        void shouldAcceptNonEmptyString() {
            TestOutput output = new TestOutput();

            // Should not throw
            output.validateNotEmpty("test", "testField");
            output.validateNotEmpty(" ", "testField");
        }

        @Test
        @DisplayName("should throw when value is out of double range")
        void shouldThrowWhenValueIsOutOfRangeDouble() {
            TestOutput output = new TestOutput();

            assertThrows(IllegalArgumentException.class, () ->
                output.validateRange(5.0, 0.0, 1.0, "testField")
            );

            assertThrows(IllegalArgumentException.class, () ->
                output.validateRange(-1.0, 0.0, 1.0, "testField")
            );
        }

        @Test
        @DisplayName("should accept values in double range")
        void shouldAcceptValuesInDoubleRange() {
            TestOutput output = new TestOutput();

            // Should not throw
            output.validateRange(0.5, 0.0, 1.0, "testField");
            output.validateRange(0.0, 0.0, 1.0, "testField");
            output.validateRange(1.0, 0.0, 1.0, "testField");
        }

        @Test
        @DisplayName("should clamp values correctly")
        void shouldClampValuesCorrectly() {
            TestOutput output = new TestOutput();

            assertEquals(0.5, output.clamp(0.5, 0.0, 1.0));
            assertEquals(0.0, output.clamp(-1.0, 0.0, 1.0));
            assertEquals(1.0, output.clamp(2.0, 0.0, 1.0));
        }
    }
}
