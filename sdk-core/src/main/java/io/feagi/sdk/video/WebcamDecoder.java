/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.video;

import io.feagi.sdk.core.VideoStreamAgent;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * {@link VideoStreamAgent.VideoDecoder} implementation backed by JavaCV's
 * {@code OpenCVFrameGrabber} for live webcam input.
 *
 * <p>Usage — pass device index (0 = default webcam):
 * <pre>{@code
 * VideoDecoder decoder = new WebcamDecoder(0);
 * VideoStreamAgent agent = new VideoStreamAgent(
 *         Path.of("webcam"), config, client, decoder, "img");
 * }</pre>
 *
 * <h2>Placement</h2>
 * {@code sdk-core/src/main/java/io/feagi/sdk/video/WebcamDecoder.java}
 */
public class WebcamDecoder implements VideoStreamAgent.VideoDecoder {

    private final int deviceIndex;
    private OpenCVFrameGrabber grabber;
    private Java2DFrameConverter converter;

    /**
     * Create a webcam decoder for the given device index.
     *
     * @param deviceIndex device index (0 = default system webcam)
     */
    public WebcamDecoder(int deviceIndex) {
        this.deviceIndex = deviceIndex;
    }

    /**
     * Convenience constructor using device 0 (default webcam).
     */
    public WebcamDecoder() {
        this(0);
    }

    @Override
    public VideoStreamAgent.VideoProperties open(Path path) throws IOException {
        // path is ignored for webcam — device index is used instead
        grabber = new OpenCVFrameGrabber(deviceIndex);
        converter = new Java2DFrameConverter();
        try {
            grabber.start();
        } catch (Exception e) {
            throw new IOException("Failed to open webcam device " + deviceIndex + ": " + e.getMessage(), e);
        }
        int width  = grabber.getImageWidth();
        int height = grabber.getImageHeight();
        double fps = grabber.getFrameRate();
        if (fps <= 0) fps = 30.0; // default if not reported
        if (width <= 0 || height <= 0) {
            // Read one frame to get actual dimensions
            try {
                Frame f = grabber.grab();
                if (f != null && f.image != null) {
                    BufferedImage img = converter.convert(f);
                    if (img != null) {
                        width  = img.getWidth();
                        height = img.getHeight();
                    }
                }
            } catch (Exception ignored) {}
        }
        return new VideoStreamAgent.VideoProperties(width, height, fps, -1); // -1 = live, no total
    }

    @Override
    public VideoStreamAgent.RawFrame readFrame() throws IOException {
        if (grabber == null) throw new IOException("Webcam decoder not open");
        try {
            Frame frame = grabber.grab();
            if (frame == null || frame.image == null) return null;
            BufferedImage img = converter.convert(frame);
            if (img == null) return null;
            int w = img.getWidth();
            int h = img.getHeight();
            byte[] rgb = toRgbBytes(img, w, h);
            return new VideoStreamAgent.RawFrame(rgb, w, h);
        } catch (Exception e) {
            throw new IOException("Webcam read error: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() throws Exception {
        if (converter != null) {
            converter.close();
            converter = null;
        }
        if (grabber != null) {
            grabber.stop();
            grabber.release();
            grabber = null;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static byte[] toRgbBytes(BufferedImage img, int w, int h) {
        byte[] rgb = new byte[w * h * 3];
        int idx = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = img.getRGB(x, y);
                rgb[idx++] = (byte) ((pixel >> 16) & 0xFF); // R
                rgb[idx++] = (byte) ((pixel >>  8) & 0xFF); // G
                rgb[idx++] = (byte) ( pixel        & 0xFF); // B
            }
        }
        return rgb;
    }
}
