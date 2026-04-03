/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.pns.inputs;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Text stream input type for FEAGI PNS.
 *
 * <p>This class represents a stream of text data that can be processed
 * by the FEAGI framework. It supports character-level and token-level
 * encoding for natural language processing tasks.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * TextStream textInput = TextStream.builder()
 *     .maxLength(256)
 *     .encoding("UTF-8")
 *     .groupId(0)
 *     .build();
 *
 * textInput._registerWithCache();
 * textInput.writeText("Hello, FEAGI!");
 * }</pre>
 *
 * @see BaseInput
 */
public class TextStream extends BaseInput<String> {

    /**
     * Maximum allowed text length.
     */
    private final int maxLength;

    /**
     * Character encoding (e.g., "UTF-8", "ASCII").
     */
    private final String encoding;

    /**
     * Whether to pad text to maxLength.
     */
    private final boolean padToMaxLength;

    /**
     * Current text buffer.
     */
    private String currentText;

    /**
     * History of written texts.
     */
    private final List<String> textHistory;

    /**
     * Creates a new TextStream with the specified configuration.
     *
     * @param builder the builder containing configuration values
     */
    private TextStream(Builder builder) {
        super(builder.groupId);
        this.maxLength = builder.maxLength;
        this.encoding = builder.encoding;
        this.padToMaxLength = builder.padToMaxLength;
        this.currentText = null;
        this.textHistory = new ArrayList<>();
    }

    /**
     * Create a builder for TextStream configuration.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the maximum allowed text length.
     *
     * @return the maximum length
     */
    public int maxLength() {
        return maxLength;
    }

    /**
     * Get the character encoding.
     *
     * @return the encoding string
     */
    public String encoding() {
        return encoding;
    }

    /**
     * Check if text should be padded to maxLength.
     *
     * @return true if padding is enabled, false otherwise
     */
    public boolean padToMaxLength() {
        return padToMaxLength;
    }

    /**
     * Get the current text.
     *
     * @return the current text, or null if no text has been set
     */
    public String getCurrentText() {
        return currentText;
    }

    /**
     * Get the history of written texts (unmodifiable view).
     *
     * @return unmodifiable list of previously written texts
     */
    public List<String> getTextHistory() {
        return Collections.unmodifiableList(textHistory);
    }

    /**
     * Get the number of texts in history.
     *
     * @return the history size
     */
    public int historySize() {
        return textHistory.size();
    }

    /**
     * Write a new text.
     *
     * @param text the text to write
     * @throws IllegalArgumentException if text length exceeds maxLength
     * @throws NullPointerException if text is null
     */
    public void writeText(String text) {
        Objects.requireNonNull(text, "text must not be null");

        if (text.length() > maxLength) {
            throw new IllegalArgumentException(
                "Text length " + text.length() + " exceeds maxLength " + maxLength);
        }

        String processedText = text;

        // Apply padding if enabled
        if (padToMaxLength && text.length() < maxLength) {
            processedText = padText(text, maxLength);
        }

        this.currentText = processedText;
        this.textHistory.add(processedText);

        // Write to cache if registered
        if (isRegistered()) {
            _writeToCache(processedText);
        }
    }

    /**
     * Write text and encode to byte array.
     *
     * @param text the text to encode
     * @return the encoded byte array
     * @throws NullPointerException if text is null
     */
    public byte[] encodeText(String text) {
        Objects.requireNonNull(text, "text must not be null");
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Decode byte array to text.
     *
     * @param data the byte array to decode
     * @return the decoded text
     * @throws NullPointerException if data is null
     */
    public String decodeText(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        return new String(data, StandardCharsets.UTF_8);
    }

    /**
     * Get character-level token indices.
     *
     * <p>Each character is converted to its Unicode code point value.</p>
     *
     * @param text the text to tokenize
     * @return array of character code points
     * @throws NullPointerException if text is null
     */
    public int[] tokenizeCharacters(String text) {
        Objects.requireNonNull(text, "text must not be null");
        return text.codePoints().toArray();
    }

    /**
     * Get simple word-level tokens.
     *
     * <p>Text is split on whitespace into word tokens.</p>
     *
     * @param text the text to tokenize
     * @return list of word tokens
     * @throws NullPointerException if text is null
     */
    public List<String> tokenizeWords(String text) {
        Objects.requireNonNull(text, "text must not be null");
        String[] words = text.split("\\s+");
        List<String> result = new ArrayList<>();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.add(word);
            }
        }
        return result;
    }

    /**
     * Pad text to specified length with spaces.
     *
     * @param text the text to pad
     * @param targetLength the target length
     * @return the padded text
     */
    private String padText(String text, int targetLength) {
        if (text.length() >= targetLength) {
            return text;
        }
        StringBuilder padded = new StringBuilder(text);
        while (padded.length() < targetLength) {
            padded.append(' ');
        }
        return padded.toString();
    }

    /**
     * Clear the current text and history.
     */
    public void clear() {
        this.currentText = null;
        this.textHistory.clear();
    }

    @Override
    protected void _registerWithCache() {
        // Register text stream with FEAGI cache system
        // In a full implementation, this would:
        // 1. Allocate shared memory region for text buffer
        // 2. Register with NPU for text processing
        // 3. Set up tokenizer integration if applicable

        // For now, just mark as registered
        markRegistered();
    }

    @Override
    protected void _writeToCache(String data) {
        // Write text data to FEAGI cache
        // In a full implementation, this would:
        // 1. Encode text and copy to shared memory region
        // 2. Signal NPU that new text is available
        // 3. Update text counters/timestamps

        // For now, this is a no-op as data is stored in currentText
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TextStream that = (TextStream) o;

        if (maxLength != that.maxLength) return false;
        if (padToMaxLength != that.padToMaxLength) return false;
        if (groupId != that.groupId) return false;
        if (!Objects.equals(encoding, that.encoding)) return false;
        return Objects.equals(currentText, that.currentText);
    }

    @Override
    public int hashCode() {
        int result = maxLength;
        result = 31 * result + (encoding != null ? encoding.hashCode() : 0);
        result = 31 * result + (padToMaxLength ? 1 : 0);
        result = 31 * result + (currentText != null ? currentText.hashCode() : 0);
        result = 31 * result + groupId;
        return result;
    }

    @Override
    public String toString() {
        return "TextStream{" +
            "maxLength=" + maxLength +
            ", encoding='" + encoding + '\'' +
            ", padToMaxLength=" + padToMaxLength +
            ", groupId=" + groupId +
            ", currentText='" + (currentText != null ? truncate(currentText, 20) : "null") + '\'' +
            ", historySize=" + textHistory.size() +
            '}';
    }

    /**
     * Truncate a string for display.
     *
     * @param s the string to truncate
     * @param length the maximum length
     * @return the truncated string with "..." if applicable
     */
    private String truncate(String s, int length) {
        if (s == null || s.length() <= length) {
            return s;
        }
        return s.substring(0, length) + "...";
    }

    /**
     * Builder for TextStream configuration.
     */
    public static final class Builder {
        private int maxLength = 256;
        private String encoding = "UTF-8";
        private boolean padToMaxLength = false;
        private int groupId = 0;

        private Builder() {}

        /**
         * Validate that a numeric value is positive.
         */
        private void validatePositive(int value, String fieldName) {
            if (value <= 0) {
                throw new IllegalArgumentException(fieldName + " must be positive, got: " + value);
            }
        }

        /**
         * Validate that a string is not null or empty.
         */
        private void validateNotEmpty(String value, String fieldName) {
            if (value == null) {
                throw new NullPointerException(fieldName + " must not be null");
            }
            if (value.isEmpty()) {
                throw new IllegalArgumentException(fieldName + " must not be empty");
            }
        }

        /**
         * Validate that a numeric value is within range [0, 255].
         */
        private void validateRange(int value, String fieldName) {
            if (value < 0 || value > 255) {
                throw new IllegalArgumentException(
                    fieldName + " must be in range [0, 255], got: " + value);
            }
        }

        /**
         * Set the maximum text length.
         *
         * @param maxLength the maximum length (must be positive)
         * @return this builder
         * @throws IllegalArgumentException if maxLength is not positive
         */
        public Builder maxLength(int maxLength) {
            validatePositive(maxLength, "maxLength");
            this.maxLength = maxLength;
            return this;
        }

        /**
         * Set the character encoding.
         *
         * @param encoding the encoding string (e.g., "UTF-8", "ASCII")
         * @return this builder
         * @throws NullPointerException if encoding is null
         * @throws IllegalArgumentException if encoding is empty
         */
        public Builder encoding(String encoding) {
            validateNotEmpty(encoding, "encoding");
            this.encoding = encoding;
            return this;
        }

        /**
         * Enable or disable padding to maxLength.
         *
         * @param padToMaxLength true to pad, false otherwise
         * @return this builder
         */
        public Builder padToMaxLength(boolean padToMaxLength) {
            this.padToMaxLength = padToMaxLength;
            return this;
        }

        /**
         * Set the group ID.
         *
         * @param groupId the group ID (0-255)
         * @return this builder
         * @throws IllegalArgumentException if groupId is out of range
         */
        public Builder groupId(int groupId) {
            validateRange(groupId, "groupId");
            this.groupId = groupId;
            return this;
        }

        /**
         * Build the TextStream instance.
         *
         * @return a new TextStream with the configured values
         */
        public TextStream build() {
            return new TextStream(this);
        }
    }
}
