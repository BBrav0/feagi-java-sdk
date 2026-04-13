/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.pns.outputs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link OutputTextStream}.
 */
@DisplayName("OutputTextStream")
class OutputTextStreamTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should create with default values")
        void shouldCreateWithDefaultValues() {
            OutputTextStream stream = OutputTextStream.builder().build();

            assertEquals(256, stream.maxTextLength());
            assertEquals("UTF-8", stream.encoding());
            assertFalse(stream.isKeepHistory());
        }

        @Test
        @DisplayName("should create with custom maxTextLength")
        void shouldCreateWithCustomMaxTextLength() {
            OutputTextStream stream = OutputTextStream.builder()
                .maxTextLength(512)
                .build();

            assertEquals(512, stream.maxTextLength());
        }

        @Test
        @DisplayName("should create with custom encoding")
        void shouldCreateWithCustomEncoding() {
            OutputTextStream stream = OutputTextStream.builder()
                .encoding("ASCII")
                .build();

            assertEquals("ASCII", stream.encoding());
        }

        @Test
        @DisplayName("should create with keepHistory enabled")
        void shouldCreateWithKeepHistoryEnabled() {
            OutputTextStream stream = OutputTextStream.builder()
                .keepHistory(true)
                .build();

            assertTrue(stream.isKeepHistory());
        }

        @Test
        @DisplayName("should throw when maxTextLength is not positive")
        void shouldThrowWhenMaxTextLengthIsNotPositive() {
            assertThrows(IllegalArgumentException.class, () ->
                OutputTextStream.builder()
                    .maxTextLength(0)
                    .build()
            );

            assertThrows(IllegalArgumentException.class, () ->
                OutputTextStream.builder()
                    .maxTextLength(-100)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when encoding is null")
        void shouldThrowWhenEncodingIsNull() {
            assertThrows(NullPointerException.class, () ->
                OutputTextStream.builder()
                    .encoding(null)
                    .build()
            );
        }

        @Test
        @DisplayName("should throw when encoding is empty")
        void shouldThrowWhenEncodingIsEmpty() {
            assertThrows(IllegalArgumentException.class, () ->
                OutputTextStream.builder()
                    .encoding("")
                    .build()
            );
        }
    }

    @Nested
    @DisplayName("Text Processing")
    class TextProcessingTests {

        @Test
        @DisplayName("should initialize with empty text")
        void shouldInitializeWithEmptyText() {
            OutputTextStream stream = OutputTextStream.builder().build();

            assertEquals("", stream.getText());
        }

        @Test
        @DisplayName("should process text")
        void shouldProcessText() {
            OutputTextStream stream = OutputTextStream.builder().build();

            stream.processText("Hello, World!");

            assertEquals("Hello, World!", stream.getText());
        }

        @Test
        @DisplayName("should truncate text exceeding max length")
        void shouldTruncateTextExceedingMaxLength() {
            OutputTextStream stream = OutputTextStream.builder()
                .maxTextLength(5)
                .build();

            stream.processText("Hello, World!");

            assertEquals("Hello", stream.getText());
        }

        @Test
        @DisplayName("should append text")
        void shouldAppendText() {
            OutputTextStream stream = OutputTextStream.builder().build();

            stream.appendText("Hello");
            stream.appendText(" ");
            stream.appendText("World!");

            assertEquals("Hello World!", stream.getText());
        }

        @Test
        @DisplayName("should throw when text is null")
        void shouldThrowWhenTextIsNull() {
            OutputTextStream stream = OutputTextStream.builder().build();

            assertThrows(NullPointerException.class, () ->
                stream.processText(null)
            );

            assertThrows(NullPointerException.class, () ->
                stream.appendText(null)
            );
        }

        @Test
        @DisplayName("should clear text")
        void shouldClearText() {
            OutputTextStream stream = OutputTextStream.builder().build();

            stream.processText("Hello");
            stream.clear();

            assertEquals("", stream.getText());
        }
    }

    @Nested
    @DisplayName("Text History")
    class HistoryTests {

        @Test
        @DisplayName("should track text history when enabled")
        void shouldTrackTextHistoryWhenEnabled() {
            OutputTextStream stream = OutputTextStream.builder()
                .keepHistory(true)
                .build();

            stream.processText("First");
            stream.processText("Second");
            stream.processText("Third");

            assertEquals(3, stream.historySize());
            assertEquals("First", stream.getTextHistory().get(0));
            assertEquals("Second", stream.getTextHistory().get(1));
            assertEquals("Third", stream.getTextHistory().get(2));
        }

        @Test
        @DisplayName("should not track history when disabled")
        void shouldNotTrackHistoryWhenDisabled() {
            OutputTextStream stream = OutputTextStream.builder()
                .keepHistory(false)
                .build();

            stream.processText("Test");

            assertEquals(0, stream.historySize());
            assertTrue(stream.getTextHistory().isEmpty());
        }

        @Test
        @DisplayName("should return unmodifiable history list")
        void shouldReturnUnmodifiableHistoryList() {
            OutputTextStream stream = OutputTextStream.builder()
                .keepHistory(true)
                .build();

            stream.processText("Test");

            assertThrows(UnsupportedOperationException.class, () ->
                stream.getTextHistory().add("Modified")
            );
        }

        @Test
        @DisplayName("should clear history")
        void shouldClearHistory() {
            OutputTextStream stream = OutputTextStream.builder()
                .keepHistory(true)
                .build();

            stream.processText("First");
            stream.processText("Second");
            stream.clear();

            assertEquals(0, stream.historySize());
        }
    }

    @Nested
    @DisplayName("Encoding/Decoding")
    class EncodingDecodingTests {

        @Test
        @DisplayName("should encode text to bytes")
        void shouldEncodeTextToBytes() {
            OutputTextStream stream = OutputTextStream.builder().build();

            byte[] encoded = stream.encodeText("Hello");

            assertNotNull(encoded);
            assertEquals(5, encoded.length);
        }

        @Test
        @DisplayName("should decode bytes to text")
        void shouldDecodeBytesToText() {
            OutputTextStream stream = OutputTextStream.builder().build();

            byte[] encoded = stream.encodeText("Hello");
            String decoded = stream.decodeText(encoded);

            assertEquals("Hello", decoded);
        }

        @Test
        @DisplayName("should throw when encoding null text")
        void shouldThrowWhenEncodingNullText() {
            OutputTextStream stream = OutputTextStream.builder().build();

            assertThrows(NullPointerException.class, () ->
                stream.encodeText(null)
            );
        }

        @Test
        @DisplayName("should throw when decoding null bytes")
        void shouldThrowWhenDecodingNullBytes() {
            OutputTextStream stream = OutputTextStream.builder().build();

            assertThrows(NullPointerException.class, () ->
                stream.decodeText(null)
            );
        }
    }

    @Nested
    @DisplayName("Tokenization")
    class TokenizationTests {

        @Test
        @DisplayName("should tokenize words")
        void shouldTokenizeWords() {
            OutputTextStream stream = OutputTextStream.builder().build();

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
            OutputTextStream stream = OutputTextStream.builder().build();

            List<String> tokens = stream.tokenizeWords("Hello   World    Test");

            assertEquals(3, tokens.size());
        }

        @Test
        @DisplayName("should throw when tokenizing null text")
        void shouldThrowWhenTokenizingNullText() {
            OutputTextStream stream = OutputTextStream.builder().build();

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
            OutputTextStream stream = OutputTextStream.builder().build();

            assertFalse(stream.isRegistered());
            assertNull(stream.groupId());

            stream._registerWithCache();

            assertTrue(stream.isRegistered());
            assertEquals(0, stream.groupId());
        }
    }

    @Nested
    @DisplayName("equals() and hashCode()")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("should be equal when all fields are equal")
        void shouldBeEqualWhenAllFieldsAreEqual() {
            OutputTextStream stream1 = OutputTextStream.builder()
                .maxTextLength(256)
                .encoding("UTF-8")
                .keepHistory(false)
                .build();

            OutputTextStream stream2 = OutputTextStream.builder()
                .maxTextLength(256)
                .encoding("UTF-8")
                .keepHistory(false)
                .build();

            assertEquals(stream1, stream2);
            assertEquals(stream1.hashCode(), stream2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when maxTextLength differs")
        void shouldNotBeEqualWhenMaxTextLengthDiffers() {
            OutputTextStream stream1 = OutputTextStream.builder()
                .maxTextLength(256)
                .build();

            OutputTextStream stream2 = OutputTextStream.builder()
                .maxTextLength(512)
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
            OutputTextStream stream = OutputTextStream.builder()
                .maxTextLength(512)
                .encoding("ASCII")
                .build();

            stream.processText("Hello");

            String result = stream.toString();

            assertTrue(result.contains("OutputTextStream"));
            assertTrue(result.contains("maxTextLength=512"));
            assertTrue(result.contains("encoding='ASCII'"));
        }

        @Test
        @DisplayName("should truncate long text in toString")
        void shouldTruncateLongTextInToString() {
            OutputTextStream stream = OutputTextStream.builder()
                .maxTextLength(100)
                .build();

            stream.processText("This is a very long text for testing purposes");

            String result = stream.toString();

            assertTrue(result.contains("..."));
        }
    }
}
