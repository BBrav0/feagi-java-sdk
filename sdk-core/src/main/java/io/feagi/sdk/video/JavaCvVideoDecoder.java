/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.video;

import io.feagi.sdk.core.VideoStreamAgent;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * {@link VideoStreamAgent.VideoDecoder} implementation backed by JavaCV's
 * {@code FFmpegFrameGrabber}. Loaded reflectively by
 * {@code VideoStreamAgent.withFile()} when {@code javacv-platform} is on
 * the classpath.
 *
 * <h2>Placement</h2>
 * {@code sdk-core/src/main/java/io/feagi/sdk/video/JavaCvVideoDecoder.java}
 */
public class JavaCvVideoDecoder implements VideoStreamAgent.VideoDecoder {

    private FFmpegFrameGrabber grabber;
    private Java2DFrameConverter converter;

    @Override
    public VideoStreamAgent.VideoProperties open(Path path) throws IOException {
        grabber   = new FFmpegFrameGrabber(path.toFile());
        converter = new Java2DFrameConverter();
        grabber.start();
        int width       = grabber.getImageWidth();
        int height      = grabber.getImageHeight();
        double fps      = grabber.getFrameRate();
        int totalFrames = (int) grabber.getLengthInFrames();
        return new VideoStreamAgent.VideoProperties(width, height, fps, totalFrames);
    }

    @Override
    public VideoStreamAgent.RawFrame readFrame() throws IOException {
        if (grabber == null) throw new IOException("Decoder not open");

        Frame frame;
        // Skip audio-only frames
        while ((frame = grabber.grab()) != null) {
            if (frame.image == null) continue;
            BufferedImage img = converter.convert(frame);
            if (img == null) continue;

            int w = img.getWidth();
            int h = img.getHeight();
            byte[] rgb = toRgbBytes(img, w, h);
            return new VideoStreamAgent.RawFrame(rgb, w, h);
        }
        return null; // EOF
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
