/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.pns.inputs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BaseInput}.
 */
@DisplayName("BaseInput")
class BaseInputTest {

    /**
     * Concrete test implementation of BaseInput for testing.
     */
    private static class TestInput extends BaseInput<String> {
        private TestInput(int groupId) {
            super(groupId);
        }

        private TestInput() {
            super();
        }

        @Override
        protected void _registerWithCache() {
            markRegistered();
        }

        @Override
        protected void _writeToCache(String data) {
            // No-op for testing
        }
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("should create with default groupId")
        void shouldCreateWithDefaultGroupId() {
            TestInput input = new TestInput();

            assertEquals(0, input.groupId());
            assertFalse(input.isRegistered());
        }

        @Test
        @DisplayName("should create with specified groupId")
        void shouldCreateWithSpecifiedGroupId() {
            TestInput input = new TestInput(5);

            assertEquals(5, input.groupId());
            assertFalse(input.isRegistered());
        }

        @Test
        @DisplayName("should throw when groupId is negative")
        void shouldThrowWhenGroupIdIsNegative() {
            assertThrows(IllegalArgumentException.class, () ->
                new TestInput(-1)
            );
        }

        @Test
        @DisplayName("should throw when groupId exceeds 255")
        void shouldThrowWhenGroupIdExceeds255() {
            assertThrows(IllegalArgumentException.class, () ->
                new TestInput(256)
            );
        }

        @Test
        @DisplayName("should accept groupId at boundaries")
        void shouldAcceptGroupIdAtBoundaries() {
            TestInput input0 = new TestInput(0);
            TestInput input255 = new TestInput(255);

            assertEquals(0, input0.groupId());
            assertEquals(255, input255.groupId());
        }
    }

    @Nested
    @DisplayName("Registration")
    class RegistrationTests {

        @Test
        @DisplayName("should mark as registered after _registerWithCache")
        void shouldMarkAsRegisteredAfterRegisterWithCache() {
            TestInput input = new TestInput();

            assertFalse(input.isRegistered());

            input._registerWithCache();

            assertTrue(input.isRegistered());
        }

        @Test
        @DisplayName("should not be registered initially")
        void shouldNotBeRegisteredInitially() {
            TestInput input = new TestInput();

            assertFalse(input.isRegistered());
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("should throw when value is out of range")
        void shouldThrowWhenValueIsOutOfRange() {
            TestInput input = new TestInput();

            assertThrows(IllegalArgumentException.class, () ->
                input.validateRange(-1, "testField")
            );

            assertThrows(IllegalArgumentException.class, () ->
                input.validateRange(256, "testField")
            );
        }

        @Test
        @DisplayName("should accept values in range")
        void shouldAcceptValuesInRange() {
            TestInput input = new TestInput();

            // Should not throw
            input.validateRange(0, "testField");
            input.validateRange(128, "testField");
            input.validateRange(255, "testField");
        }

        @Test
        @DisplayName("should throw when value is not positive")
        void shouldThrowWhenValueIsNotPositive() {
            TestInput input = new TestInput();

            assertThrows(IllegalArgumentException.class, () ->
                input.validatePositive(0, "testField")
            );

            assertThrows(IllegalArgumentException.class, () ->
                input.validatePositive(-1, "testField")
            );

            assertThrows(IllegalArgumentException.class, () ->
                input.validatePositive(0.0, "testField")
            );

            assertThrows(IllegalArgumentException.class, () ->
                input.validatePositive(-1.0, "testField")
            );
        }

        @Test
        @DisplayName("should accept positive values")
        void shouldAcceptPositiveValues() {
            TestInput input = new TestInput();

            // Should not throw
            input.validatePositive(1, "testField");
            input.validatePositive(100, "testField");
            input.validatePositive(1.0, "testField");
            input.validatePositive(100.5, "testField");
        }

        @Test
        @DisplayName("should throw when string is null")
        void shouldThrowWhenStringIsNull() {
            TestInput input = new TestInput();

            assertThrows(NullPointerException.class, () ->
                input.validateNotEmpty(null, "testField")
            );
        }

        @Test
        @DisplayName("should throw when string is empty")
        void shouldThrowWhenStringIsEmpty() {
            TestInput input = new TestInput();

            assertThrows(IllegalArgumentException.class, () ->
                input.validateNotEmpty("", "testField")
            );
        }

        @Test
        @DisplayName("should accept non-empty string")
        void shouldAcceptNonEmptyString() {
            TestInput input = new TestInput();

            // Should not throw
            input.validateNotEmpty("test", "testField");
            input.validateNotEmpty(" ", "testField");
        }
    }
}
