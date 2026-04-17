/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.pns.inputs;

import java.util.Objects;

/**
 * Camera input type for FEAGI PNS vision processing.
 *
 * <p>This class represents a camera input stream that can process visual data
 * in various formats (RGB, grayscale, etc.) and resolutions. It supports
 * both standard and segmented vision configurations.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * Camera camera = Camera.builder()
 *     .resolution(640, 480)
 *     .channels(3)
 *     .encoding("RGB")
 *     .groupId(0)
 *     .build();
 *
 * camera._registerWithCache();
 * camera.setFrame(imageData);
 * }</pre>
 *
 * @see BaseInput
 * @see VisionUnitConfig
 */
public class Camera extends BaseInput<byte[]> {

    /**
     * Image width in pixels.
     */
    private final int width;

    /**
     * Image height in pixels.
     */
    private final int height;

    /**
     * Number of color channels (1 for grayscale, 3 for RGB, 4 for RGBA).
     */
    private final int channels;

    /**
     * Pixel encoding format (e.g., "RGB", "RGBA", "GRAY", "YUV").
     */
    private final String encoding;

    /**
     * Camera position identifier for multi-camera setups.
     */
    private final String position;

    /**
     * Current frame buffer.
     */
    private volatile byte[] currentFrame;

    /**
     * Creates a new Camera input with the specified configuration.
     *
     * @param builder the builder containing configuration values
     */
    private Camera(Builder builder) {
        super(builder.groupId);
        this.width = builder.width;
        this.height = builder.height;
        this.channels = builder.channels;
        this.encoding = builder.encoding;
        this.position = builder.position;
        this.currentFrame = null;
    }

    /**
     * Create a builder for Camera configuration.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the image width in pixels.
     *
     * @return the width
     */
    public int width() {
        return width;
    }

    /**
     * Get the image height in pixels.
     *
     * @return the height
     */
    public int height() {
        return height;
    }

    /**
     * Get the number of color channels.
     *
     * @return the channel count (1=grayscale, 3=RGB, 4=RGBA)
     */
    public int channels() {
        return channels;
    }

    /**
     * Get the pixel encoding format.
     *
     * @return the encoding string
     */
    public String encoding() {
        return encoding;
    }

    /**
     * Get the camera position identifier.
     *
     * @return the position identifier, or null if not set
     */
    public String position() {
        return position;
    }

    /**
     * Get the total number of bytes per frame.
     *
     * @return width * height * channels
     */
    public int frameSize() {
        return width * height * channels;
    }

    /**
     * Get the current frame buffer.
     *
     * @return the current frame data, or null if no frame has been set
     */
    public byte[] getCurrentFrame() {
        return currentFrame;
    }

    /**
     * Set a new frame of image data.
     *
     * <p>The frame data must match the expected size based on
     * width * height * channels.</p>
     *
     * @param frameData the raw image byte array
     * @throws IllegalArgumentException if frame size does not match expected dimensions
     * @throws NullPointerException if frameData is null
     */
    public void setFrame(byte[] frameData) {
        Objects.requireNonNull(frameData, "frameData must not be null");

        int expectedSize = frameSize();
        if (frameData.length != expectedSize) {
            throw new IllegalArgumentException(
                "Frame size mismatch: expected " + expectedSize +
                " bytes, got " + frameData.length + " bytes");
        }

        this.currentFrame = frameData.clone();

        // Write to cache if registered
        if (isRegistered()) {
            _writeToCache(this.currentFrame);
        }
    }

    /**
     * Set a new frame from an integer array (convenience method).
     *
     * <p>Each integer represents a pixel value (0-255 for grayscale,
     * or packed RGB values).</p>
     *
     * @param pixelData the pixel data as integers
     * @throws IllegalArgumentException if pixel data size does not match
     */
    public void setFrameFromInts(int[] pixelData) {
        Objects.requireNonNull(pixelData, "pixelData must not be null");

        int expectedPixels = width * height;
        if (pixelData.length != expectedPixels * channels) {
            throw new IllegalArgumentException(
                "Pixel count mismatch: expected " + (expectedPixels * channels) +
                " values, got " + pixelData.length);
        }

        byte[] frameData = new byte[pixelData.length];
        for (int i = 0; i < pixelData.length; i++) {
            int value = pixelData[i];
            if (value < 0 || value > 255) {
                throw new IllegalArgumentException(
                    "Pixel value must be in range [0, 255], got: " + value + " at index " + i);
            }
            frameData[i] = (byte) value;
        }

        setFrame(frameData);
    }

    @Override
    protected void _registerWithCache() {
        // Register camera input with FEAGI cache system
        // In a full implementation, this would:
        // 1. Allocate shared memory region for frame buffer
        // 2. Register with NPU for vision processing
        // 3. Set up DMA transfers if applicable

        // For now, just mark as registered
        markRegistered();
    }

    @Override
    protected void _writeToCache(byte[] data) {
        // Write frame data to FEAGI cache
        // In a full implementation, this would:
        // 1. Copy data to shared memory region
        // 2. Signal NPU that new frame is available
        // 3. Update frame counters/timestamps

        // For now, this is a no-op as data is stored in currentFrame
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Camera camera = (Camera) o;

        if (width != camera.width) return false;
        if (height != camera.height) return false;
        if (channels != camera.channels) return false;
        if (groupId != camera.groupId) return false;
        if (!Objects.equals(encoding, camera.encoding)) return false;
        return Objects.equals(position, camera.position);
    }

    @Override
    public int hashCode() {
        int result = width;
        result = 31 * result + height;
        result = 31 * result + channels;
        result = 31 * result + (encoding != null ? encoding.hashCode() : 0);
        result = 31 * result + (position != null ? position.hashCode() : 0);
        result = 31 * result + groupId;
        return result;
    }

    @Override
    public String toString() {
        return "Camera{" +
            "width=" + width +
            ", height=" + height +
            ", channels=" + channels +
            ", encoding='" + encoding + '\'' +
            ", position='" + position + '\'' +
            ", groupId=" + groupId +
            ", frameSize=" + frameSize() +
            '}';
    }

    /**
     * Builder for Camera configuration.
     */
    public static final class Builder {
        private int width = 640;
        private int height = 480;
        private int channels = 3;
        private String encoding = "RGB";
        private String position;
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
         * Set the image resolution.
         *
         * @param width the width in pixels (must be positive)
         * @param height the height in pixels (must be positive)
         * @return this builder
         * @throws IllegalArgumentException if width or height is not positive
         */
        public Builder resolution(int width, int height) {
            validatePositive(width, "width");
            validatePositive(height, "height");
            this.width = width;
            this.height = height;
            return this;
        }

        /**
         * Set the width.
         *
         * @param width the width in pixels (must be positive)
         * @return this builder
         */
        public Builder width(int width) {
            validatePositive(width, "width");
            this.width = width;
            return this;
        }

        /**
         * Set the height.
         *
         * @param height the height in pixels (must be positive)
         * @return this builder
         */
        public Builder height(int height) {
            validatePositive(height, "height");
            this.height = height;
            return this;
        }

        /**
         * Set the number of color channels.
         *
         * @param channels the channel count (1, 3, or 4 recommended)
         * @return this builder
         * @throws IllegalArgumentException if channels is not positive
         */
        public Builder channels(int channels) {
            validatePositive(channels, "channels");
            this.channels = channels;
            return this;
        }

        /**
         * Set the pixel encoding format.
         *
         * @param encoding the encoding string (e.g., "RGB", "RGBA", "GRAY")
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
         * Set the camera position identifier.
         *
         * @param position the position identifier (e.g., "front", "rear", "left")
         * @return this builder
         */
        public Builder position(String position) {
            this.position = position;
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
         * Build the Camera instance.
         *
         * @return a new Camera with the configured values
         */
        public Camera build() {
            return new Camera(this);
        }
    }
}
