/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.pns.inputs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TextStream}.
 */
@DisplayName("TextStream")
class TextStreamTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should create with default values")
        void shouldCreateWithDefaultValues() {
            TextStream stream = TextStream.builder().build();

            assertEquals(256, stream.maxLength());
            assertEquals("UTF-8", stream.encoding());
            assertFalse(stream.padToMaxLength());
            assertEquals(0, stream.groupId());
        }

        @Test
        @DisplayName("should create with custom maxLength")
        void shouldCreateWithCustomMaxLength() {
            TextStream stream = TextStream.builder()
                .maxLength(512)
                .build();

            assertEquals(512, stream.maxLength());
        }

        @Test
        @DisplayName("should create with custom encoding")
        void shouldCreateWithCustomEncoding() {
            TextStream stream = TextStream.builder()
                .encoding("ASCII")
                .build();

            assertEquals("ASCII", stream.encoding());
        }

        @Test
        @DisplayName("should create with padToMaxLength enabled")
        void shouldCreateWithPadToMaxLengthEnabled() {
            TextStream stream = TextStream.builder()
                .padToMaxLength(true)
                .build();

            assertTrue(stream.padToMaxLength());
        }

        @Test
        @DisplayName("should throw when maxLength is not positive")
        void shouldThrowWhenMaxLengthIsNotPositive() {
            assertThrows(IllegalArgumentException.class, () ->
                TextStream.builder()
                    .maxLength(0)
                    .build()
            );

            assertThrows(IllegalArgumentException.class, () ->
                TextStream.builder()
                    .maxLength(-100)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when encoding is null")
        void shouldThrowWhenEncodingIsNull() {
            assertThrows(NullPointerException.class, () ->
                TextStream.builder()
                    .encoding(null)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when encoding is empty")
        void shouldThrowWhenEncodingIsEmpty() {
            assertThrows(IllegalArgumentException.class, () ->
                TextStream.builder()
                    .encoding("")
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when groupId is out of range")
        void shouldThrowWhenGroupIdIsOutOfRange() {
            assertThrows(IllegalArgumentException.class, () ->
                TextStream.builder()
                    .groupId(-1)
                    .build()
            );

            assertThrows(IllegalArgumentException.class, () ->
                TextStream.builder()
                    .groupId(256)
                    .build()
            );
        }
    }

    @Nested
    @DisplayName("Write Text")
    class WriteTextTests {

        @Test
        @DisplayName("should write text within maxLength")
        void shouldWriteTextWithinMaxLength() {
            TextStream stream = TextStream.builder()
                .maxLength(100)
                .build();

            stream.writeText("Hello, World!");

            assertEquals("Hello, World!", stream.getCurrentText());
        }

        @Test
        @DisplayName("should throw when text exceeds maxLength")
        void shouldThrowWhenTextExceedsMaxLength() {
            TextStream stream = TextStream.builder()
                .maxLength(5)
                .build();

            assertThrows(IllegalArgumentException.class, () ->
                stream.writeText("Hello, World!")
            );
        }

        @Test
        @DisplayName("should throw when text is null")
        void shouldThrowWhenTextIsNull() {
            TextStream stream = TextStream.builder().build();

            assertThrows(NullPointerException.class, () ->
                stream.writeText(null)
            );
        }

        @Test
        @DisplayName("should pad text when padToMaxLength is enabled")
        void shouldPadTextWhenPadToMaxLengthIsEnabled() {
            TextStream stream = TextStream.builder()
                .maxLength(10)
                .padToMaxLength(true)
                .build();

            stream.writeText("Hello");

            assertEquals("Hello     ", stream.getCurrentText());
            assertEquals(10, stream.getCurrentText().length());
        }

        @Test
        @DisplayName("should not pad text when padToMaxLength is disabled")
        void shouldNotPadTextWhenPadToMaxLengthIsDisabled() {
            TextStream stream = TextStream.builder()
                .maxLength(10)
                .padToMaxLength(false)
                .build();

            stream.writeText("Hello");

            assertEquals("Hello", stream.getCurrentText());
            assertEquals(5, stream.getCurrentText().length());
        }
    }

    @Nested
    @DisplayName("Text History")
    class HistoryTests {

        @Test
        @DisplayName("should track text history")
        void shouldTrackTextHistory() {
            TextStream stream = TextStream.builder()
                .maxLength(100)
                .build();

            stream.writeText("First");
            stream.writeText("Second");
            stream.writeText("Third");

            assertEquals(3, stream.historySize());
            assertEquals("First", stream.getTextHistory().get(0));
            assertEquals("Second", stream.getTextHistory().get(1));
            assertEquals("Third", stream.getTextHistory().get(2));
        }

        @Test
        @DisplayName("should return unmodifiable history list")
        void shouldReturnUnmodifiableHistoryList() {
            TextStream stream = TextStream.builder().build();

            stream.writeText("Test");

            assertThrows(UnsupportedOperationException.class, () ->
                stream.getTextHistory().add("Modified")
            );
        }

        @Test
        @DisplayName("should clear history")
        void shouldClearHistory() {
            TextStream stream = TextStream.builder()
                .maxLength(100)
                .build();

            stream.writeText("First");
            stream.writeText("Second");

            stream.clear();

            assertEquals(0, stream.historySize());
            assertNull(stream.getCurrentText());
        }
    }

    @Nested
    @DisplayName("Encoding/Decoding")
    class EncodingTests {

        @Test
        @DisplayName("should encode text to bytes")
        void shouldEncodeTextToBytes() {
            TextStream stream = TextStream.builder().build();

            byte[] encoded = stream.encodeText("Hello");

            assertNotNull(encoded);
            assertEquals(5, encoded.length);
        }

        @Test
        @DisplayName("should decode bytes to text")
        void shouldDecodeBytesToText() {
            TextStream stream = TextStream.builder().build();

            byte[] encoded = stream.encodeText("Hello");
            String decoded = stream.decodeText(encoded);

            assertEquals("Hello", decoded);
        }

        @Test
        @DisplayName("should throw when encoding null text")
        void shouldThrowWhenEncodingNullText() {
            TextStream stream = TextStream.builder().build();

            assertThrows(NullPointerException.class, () ->
                stream.encodeText(null)
            );
        }

        @Test
        @DisplayName("should throw when decoding null bytes")
        void shouldThrowWhenDecodingNullBytes() {
            TextStream stream = TextStream.builder().build();

            assertThrows(NullPointerException.class, () ->
                stream.decodeText(null)
            );
        }
    }

    @Nested
    @DisplayName("Tokenization")
    class TokenizationTests {

        @Test
        @DisplayName("should tokenize characters")
        void shouldTokenizeCharacters() {
            TextStream stream = TextStream.builder().build();

            int[] tokens = stream.tokenizeCharacters("ABC");

            assertNotNull(tokens);
            assertEquals(3, tokens.length);
            assertEquals('A', tokens[0]);
            assertEquals('B', tokens[1]);
            assertEquals('C', tokens[2]);
        }

        @Test
        @DisplayName("should tokenize words")
        void shouldTokenizeWords() {
            TextStream stream = TextStream.builder().build();

            List<String> tokens = stream.tokenizeWords("Hello World Test");

            assertNotNull(tokens);
            assertEquals(3, tokens.size());
            assertEquals("Hello", tokens.get(0));
            assertEquals("World", tokens.get(1));
            assertEquals("Test", tokens.get(2));
        }

        @Test
        @DisplayName("should handle multiple spaces between words")
        void shouldHandleMultipleSpacesBetweenWords() {
            TextStream stream = TextStream.builder().build();

            List<String> tokens = stream.tokenizeWords("Hello   World    Test");

            assertEquals(3, tokens.size());
        }

        @Test
        @DisplayName("should throw when tokenizing null text")
        void shouldThrowWhenTokenizingNullText() {
            TextStream stream = TextStream.builder().build();

            assertThrows(NullPointerException.class, () ->
                stream.tokenizeCharacters(null)
            );

            assertThrows(NullPointerException.class, () ->
                stream.tokenizeWords(null)
            );
        }
    }

    @Nested
    @DisplayName("Registration")
    class RegistrationTests {

        @Test
        @DisplayName("should register with cache")
        void shouldRegisterWithCache() {
            TextStream stream = TextStream.builder().build();

            assertFalse(stream.isRegistered());

            stream._registerWithCache();

            assertTrue(stream.isRegistered());
        }
    }

    @Nested
    @DisplayName("equals() and hashCode()")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("should be equal when all fields are equal")
        void shouldBeEqualWhenAllFieldsAreEqual() {
            TextStream stream1 = TextStream.builder()
                .maxLength(100)
                .encoding("UTF-8")
                .padToMaxLength(true)
                .groupId(1)
                .build();

            TextStream stream2 = TextStream.builder()
                .maxLength(100)
                .encoding("UTF-8")
                .padToMaxLength(true)
                .groupId(1)
                .build();

            assertEquals(stream1, stream2);
            assertEquals(stream1.hashCode(), stream2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when fields differ")
        void shouldNotBeEqualWhenFieldsDiffer() {
            TextStream stream1 = TextStream.builder()
                .maxLength(100)
                .build();

            TextStream stream2 = TextStream.builder()
                .maxLength(256)
                .build();

            assertNotEquals(stream1, stream2);
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @Test
        @DisplayName("should return formatted string")
        void shouldReturnFormattedString() {
            TextStream stream = TextStream.builder()
                .maxLength(512)
                .encoding("ASCII")
                .build();

            String result = stream.toString();

            assertTrue(result.contains("TextStream"));
            assertTrue(result.contains("maxLength=512"));
            assertTrue(result.contains("encoding='ASCII'"));
        }

        @Test
        @DisplayName("should truncate long currentText in toString")
        void shouldTruncateLongCurrentTextInToString() {
            TextStream stream = TextStream.builder()
                .maxLength(100)
                .padToMaxLength(false)
                .build();

            stream.writeText("This is a very long text for testing purposes");

            String result = stream.toString();

            assertTrue(result.contains("..."));
        }
    }
}
