/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.logging.Logger;
import io.feagi.sdk.video.FbcEncoder;

/**
 * Concrete {@link BaseAgent} for streaming video frames to FEAGI.
 *
 * <p>Mirrors Python's {@code VideoStreamAgent} in {@code feagi/agent/video.py}.
 * This agent is <b>input-only</b>: it reads frames, encodes them as RGB bytes,
 * and sends them to FEAGI each tick. Motor output from FEAGI is ignored.
 *
 * <h2>Quickstart</h2>
 * <pre>{@code
 * try (VideoStreamAgent agent = new VideoStreamAgent(
 *         Path.of("clip.mp4"), config, client, decoder)) {
 *
 *     agent.connect();
 *
 *     // Option A — one-liner, stream entire file
 *     int sent = agent.run();
 *
 *     // Option B — frame-by-frame with custom logic (mirrors Python stream())
 *     for (VideoStreamAgent.Frame f : agent.stream()) {
 *         System.out.println("frame " + f.frameNumber());
 *         // send motor commands, update UI, log, etc.
 *     }
 *
 *     // Option C — parameterized (mirrors Python arguments exactly)
 *     for (VideoStreamAgent.Frame f : agent.stream(100, true, 10)) {
 *         // max 100 frames, paced by FPS, log every 10
 *     }
 * }
 * }</pre>
 *
 * <h2>Video decoding</h2>
 * Supply a {@link VideoDecoder} at construction. Use the factory
 * {@link #withFile(Path, AgentConfig, FeagiAgentClient)} to auto-detect
 * a decoder from the classpath (requires {@code javacv-platform}).
 *
 * <h2>Video metadata</h2>
 * Call {@link #detectProperties()} after construction to read width/height/FPS
 * without starting the full lifecycle — mirrors Python's {@code auto_detect=True}.
 * Properties are also available via {@link #videoProperties()} after
 * {@link #run()} or {@link #stream()} opens the file.
 */
public class VideoStreamAgent extends BaseAgent {

    private static final Logger LOG = Logger.getLogger(VideoStreamAgent.class.getName());

    /** Default FEAGI sensory channel name for the camera feed. */
    public static final String DEFAULT_CHANNEL = "camera";

    /** Default progress log interval passed to {@link #run()} and {@link #stream()} (0 = disabled). */
    public static final int DEFAULT_PROGRESS_INTERVAL = 30;

    // ── Configuration ──────────────────────────────────────────────────────────

    private Path videoPath;
    private final VideoDecoder decoder;
    private final String channelName;
    private final FbcEncoder fbcEncoder = new FbcEncoder();
    private byte[] prevFrame = null;
    private static final int DIFF_THRESHOLD = 15; // pixel change threshold

    // ── State set at open time ────────────────────────────────────────────────

    private volatile VideoProperties videoProperties;

    /**
     * Tracks whether the decoder is currently open, regardless of which code path
     * opened it (BaseAgent run loop or StreamIterator directly).
     * Used by {@link #close()} to ensure the decoder is always released.
     */
    private volatile boolean decoderOpen = false;

    // ── Construction ──────────────────────────────────────────────────────────

    /**
     * Create a VideoStreamAgent with explicit decoder and channel settings.
     *
     * @param videoPath   path to the video file; must exist
     * @param config      agent config declaring a sensory/vision capability
     * @param client      transport client
     * @param decoder     video decoder implementation
     * @param channelName FEAGI sensory channel name (e.g. {@code "camera"})
     */
    public VideoStreamAgent(
            Path videoPath,
            AgentConfig config,
            FeagiAgentClient client,
            VideoDecoder decoder,
            String channelName) {
        super(deriveAgentId(videoPath), config, client);
        this.videoPath   = validated(videoPath);
        this.decoder     = Objects.requireNonNull(decoder, "decoder must not be null");
        this.channelName = (channelName != null && !channelName.isBlank())
                ? channelName : DEFAULT_CHANNEL;
    }

    /**
     * Convenience constructor: uses {@value #DEFAULT_CHANNEL} as the channel name.
     */
    public VideoStreamAgent(
            Path videoPath,
            AgentConfig config,
            FeagiAgentClient client,
            VideoDecoder decoder) {
        this(videoPath, config, client, decoder, DEFAULT_CHANNEL);
    }

    /**
     * Factory: auto-selects a decoder from the classpath.
     * Requires {@code io.feagi.sdk.video.JavaCvVideoDecoder} (from {@code javacv-platform}).
     *
     * @throws FeagiSdkException if no decoder is found
     */
    public static VideoStreamAgent withFile(
            Path videoPath, AgentConfig config, FeagiAgentClient client) {
        return new VideoStreamAgent(videoPath, config, client, findDecoder());
    }

    // ── Early metadata detection (mirrors Python auto_detect=True) ────────────

    /**
     * Open the video file using the agent's decoder, read metadata, then close it —
     * without starting the full agent lifecycle. Safe to call immediately after
     * construction.
     *
     * <p>Mirrors Python's {@code auto_detect=True} constructor parameter. Properties
     * are also populated automatically when {@link #run()} or {@link #stream()} first
     * opens the file.
     *
     * <p>Note: this opens and closes the agent's own decoder instance. If the decoder
     * is stateful and cannot be reopened after closing, call this method only once and
     * before {@link #run()} or {@link #stream()}.
     *
     * @return this agent, for chaining
     * @throws IOException if the file cannot be opened or is not a valid video
     */
    public VideoStreamAgent detectProperties() throws IOException {
        VideoProperties props = decoder.open(videoPath);
        try {
            decoder.close(); // release immediately — reopen happens in initializeHardware()
        } catch (Exception e) {
            LOG.warning("VideoStreamAgent: error closing probe decoder: " + e.getMessage());
        }
        decoderOpen = false;
        videoProperties = props;
        LOG.info("VideoStreamAgent: detected " + videoProperties.width()
                + "x" + videoProperties.height()
                + " @ " + videoProperties.fps() + " fps, "
                + videoProperties.totalFrames() + " frames");
        return this;
    }

    // ── BaseAgent hooks ───────────────────────────────────────────────────────

    /**
     * Open the decoder and read video metadata.
     * Called once at the start of {@link #run(AgentRunConfig)}.
     */
    @Override
    protected void initializeHardware() throws Exception {
        LOG.info("VideoStreamAgent: opening " + videoPath);
        videoProperties = decoder.open(videoPath);
        decoderOpen = true;
        LOG.info("VideoStreamAgent: " + videoProperties.width()
                + "x" + videoProperties.height()
                + " @ " + videoProperties.fps() + " fps, "
                + videoProperties.totalFrames() + " total frames");
    }

    /**
     * Read one raw frame. Returns {@code null} at end of file.
     */
    @Override
    protected Object readSensors() throws Exception {
        return decoder.readFrame();
    }

    /**
     * Convert a raw frame to the FEAGI channel map and count it.
     * Calls {@link #stop()} when the file ends.
     */
    @Override
    protected Map<String, byte[]> mapSensors(Object hwData) {
        if (hwData == null) {
            LOG.info("VideoStreamAgent: end of file");
            stop();
            return null;
        }
        RawFrame rf = (RawFrame) hwData;
        return Map.of(channelName, rf.rgbBytes());
    }

    /** No-op — video streaming is input-only. */
    @Override
    protected Object mapMotors(AgentFrame frame) {
        return null;
    }

    /** No-op — no motor hardware. */
    @Override
    protected void executeCommands(Object commands) {}

    /** Release the decoder. */
    @Override
    protected void closeHardware() {
        if (!decoderOpen) return; // idempotent
        decoderOpen = false;
        try {
            decoder.close();
        } catch (Exception e) {
            LOG.warning("VideoStreamAgent: error closing decoder: " + e.getMessage());
        }
    }

    /**
     * Called unconditionally by {@link BaseAgent#close()}.
     * Ensures the decoder is released even when it was opened by {@link StreamIterator}
     * directly (the {@link #stream()} path), in which case {@link #closeHardware()} is
     * not called because {@code hardwareInitialized} is never set on that path.
     */
    @Override
    protected void onClose() {
        closeHardware(); // idempotent via decoderOpen guard
    }

    // ── High-level API ────────────────────────────────────────────────────────

    /**
     * Stream the entire video to FEAGI and return the number of frames sent.
     * Tick interval is derived from the video's FPS.
     *
     * <p>Mirrors Python {@code VideoStreamAgent.run()}.
     *
     * @return number of frames streamed
     * @throws InterruptedException if interrupted
     */
    public int run() throws InterruptedException {
        return run(0, true, DEFAULT_PROGRESS_INTERVAL);
    }

    /**
     * Stream video to FEAGI with explicit parameters.
     *
     * <p>Mirrors Python {@code run(max_frames, pace_by_fps, progress_interval)}.
     *
     * @param maxFrames        maximum frames to stream; 0 = entire file
     * @param paceByFps        sleep between frames to match source FPS
     * @param progressInterval log progress every N frames; 0 disables
     * @return number of frames streamed
     * @throws InterruptedException if interrupted
     */
    public int run(int maxFrames, boolean paceByFps, int progressInterval)
            throws InterruptedException {
        int count = 0;
        for (Frame f : stream(maxFrames, paceByFps, progressInterval)) {
            count = f.frameNumber();
        }
        return count;
    }

    /**
     * Stream a static image to FEAGI for a fixed duration.
     * Reads the first frame once then repeats it at the given FPS.
     * Use this for image files (JPG, PNG, etc.) instead of {@link #run()}.
     *
     * <p>Mirrors Python's image streaming logic in {@code example_video_simple.py}.
     *
     * @param durationSeconds how long to stream the image
     * @param fps             frames per second to send
     * @return number of frames sent
     * @throws InterruptedException if interrupted
     */
    public int runImage(int durationSeconds, int fps) throws InterruptedException {
        if (!isConnected()) {
            throw new IllegalStateException(
                    "Agent '" + agentId() + "' is not connected. Call connect() first.");
        }

        // Open decoder and read the single frame
        if (!decoderOpen) {
            try {
                initializeHardware();
                markHardwareInitialized();
            } catch (Exception e) {
                LOG.warning("VideoStreamAgent: failed to initialize for image: " + e.getMessage());
                return 0;
            }
        }

        // Read just the first frame
        RawFrame frame;
        try {
            frame = decoder.readFrame();
        } catch (Exception e) {
            LOG.warning("VideoStreamAgent: failed to read image frame: " + e.getMessage());
            closeHardware();
            return 0;
        }

        if (frame == null) {
            LOG.warning("VideoStreamAgent: image file produced no frames");
            closeHardware();
            return 0;
        }

        // Pre-encode once — no need to re-encode the same frame repeatedly
        Map<String, byte[]> sensorMap = Map.of(channelName, frame.rgbBytes());
        byte[] payload = serializeSensoryData(sensorMap);

        // Stream the same frame repeatedly for the given duration
        int totalFrames = durationSeconds * fps;
        long sleepMs = 1000L / fps;
        int sent = 0;

        for (int i = 0; i < totalFrames; i++) {
            if (Thread.currentThread().isInterrupted()) break;
            if (payload != null) {
                try {
                    sendSensoryPayload(payload);
                    sent++;
                } catch (Exception e) {
                    LOG.warning("VideoStreamAgent: image send error at frame " + i + ": " + e.getMessage());
                }
            }
            Thread.sleep(sleepMs);
        }

        closeHardware();
        return sent;
    }

    /**
     * Stream frames with default parameters (entire file, FPS-paced,
     * default progress interval).
     *
     * <p>Mirrors Python {@code stream()}.
     *
     * @return iterable of {@link Frame} records; each frame has already been
     *         sent to FEAGI before being yielded
     */
    public Iterable<Frame> stream() {
        return stream(0, true, DEFAULT_PROGRESS_INTERVAL);
    }

    /**
     * Stream frames with explicit parameters.
     *
     * <p>Mirrors Python {@code stream(max_frames, pace_by_fps, progress_interval)}.
     *
     * <p>{@code stream()} is the primary engine — each iteration reads one frame,
     * sends it to FEAGI via the sensory path, then yields it to the caller.
     * {@link #run()} is simply a consumer of this iterator.
     *
     * @param maxFrames        maximum frames; 0 = entire file
     * @param paceByFps        sleep to match source FPS
     * @param progressInterval log every N frames; 0 disables
     * @return iterable of {@link Frame} records
     */
    public Iterable<Frame> stream(int maxFrames, boolean paceByFps, int progressInterval) {
        return () -> new StreamIterator(maxFrames, paceByFps, progressInterval);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /**
     * Video metadata. Available after {@link #detectProperties()}, or after the
     * first frame is read inside {@link #run()} / {@link #stream()}.
     * Returns {@code null} if neither has been called yet.
     */
    public VideoProperties videoProperties() {
        return videoProperties;
    }

    /** Path to the video file supplied at construction. */
    public Path videoPath() {
        return videoPath;
    }

        /**
     * Reset the video file for the next playback without disconnecting.
     * Call this between loops to swap the source file.
     * The agent must be connected but not currently streaming.
     *
     * @param newPath path to the new video file; must exist
     */
    public void resetFile(Path newPath) {
        closeHardware();
        this.videoPath = validated(newPath);
    }

    // ── Supporting types ──────────────────────────────────────────────────────

    /**
     * A single decoded video frame as yielded by {@link #stream()}.
     * The frame has already been sent to FEAGI before being returned.
     *
     * @param frameNumber 1-based index within the current stream
     * @param rgbBytes    raw RGB pixel data, row-major, 3 bytes per pixel
     * @param width       frame width in pixels
     * @param height      frame height in pixels
     */
    public record Frame(int frameNumber, byte[] rgbBytes, int width, int height) {
        /**
         * Compact constructor: defensively copies {@code rgbBytes} so that callers
         * who mutate the returned array cannot affect other consumers or internal state.
         * Records do not copy array fields automatically.
         */
        public Frame {
            rgbBytes = (rgbBytes != null) ? rgbBytes.clone() : null;
        }
    }

    /**
     * Video file metadata returned by {@link VideoDecoder#open(Path)}.
     *
     * @param width       frame width in pixels
     * @param height      frame height in pixels
     * @param fps         frames per second
     * @param totalFrames total frame count; -1 if unknown (live stream)
     */
    public record VideoProperties(int width, int height, double fps, int totalFrames) {}

    /**
     * A single raw frame from the decoder, already converted to RGB.
     *
     * @param rgbBytes raw RGB pixel bytes (decoder handles BGR-&gt;RGB)
     * @param width    frame width
     * @param height   frame height
     */
    public record RawFrame(byte[] rgbBytes, int width, int height) {}

    /**
     * SPI for pluggable video decoding backends (JavaCV, FFmpeg, etc.).
     *
     * <p>The decoder handles all format-specific work:
     * opening the file, reading frames, BGR-&gt;RGB conversion, and cleanup.
     */
    public interface VideoDecoder extends AutoCloseable {
        /**
         * Open the file and return its properties.
         *
         * @throws IOException if the file cannot be opened or is invalid
         */
        VideoProperties open(Path path) throws IOException;

        /**
         * Read the next frame. Returns {@code null} at end of file.
         *
         * @throws IOException if reading fails
         */
        RawFrame readFrame() throws IOException;

        @Override
        void close() throws Exception;
    }

    // ── Stream iterator — single source of truth ──────────────────────────────

    /**
     * The primary engine. Implements {@link AutoCloseable} so that early loop exits
     * (break, exception, return) release the decoder via try-with-resources:
     *
     * <pre>{@code
     * try (var it = agent.stream().iterator()) {
     *     while (it.hasNext()) {
     *         Frame f = it.next();
     *         if (done) break; // decoder released here
     *     }
     * }
     * }</pre>
     *
     * When using the for-each form ({@code for (Frame f : agent.stream())}), the
     * decoder is released when the iterator is naturally exhausted (EOF or maxFrames).
     * For early-exit loops, prefer try-with-resources on the iterator.
     *
     * <p>Frame accounting lives here and nowhere else.
     */
    private class StreamIterator implements Iterator<Frame>, AutoCloseable {

        private final int maxFrames;
        private final boolean paceByFps;
        private final int progressInterval;

        private int frameNumber = 0;
        private long startNanos = System.nanoTime();
        private Frame nextFrame;
        private boolean done = false;

        StreamIterator(int maxFrames, boolean paceByFps, int progressInterval) {
            this.maxFrames        = maxFrames;
            this.paceByFps        = paceByFps;
            this.progressInterval = progressInterval;

            // Guard: must be connected before streaming
            if (!isConnected()) {
                throw new IllegalStateException(
                        "Agent '" + agentId() + "' is not connected. "
                        + "Call connect() first.");
            }

            // Open decoder if not already done by BaseAgent.run(AgentRunConfig).
            // Also marks hardware initialized so isHardwareInitialized() reflects reality
            // and close() can call closeHardware() via the standard path if needed.
            if (!decoderOpen) {
                try {
                    initializeHardware();    // sets decoderOpen = true
                    markHardwareInitialized(); // keeps BaseAgent state consistent
                } catch (Exception e) {
                    done = true;
                    LOG.warning("VideoStreamAgent: failed to initialize: " + e.getMessage());
                    return;
                }
            }
            startNanos = System.nanoTime();
            advance();
        }

        @Override
        public boolean hasNext() {
            return !done && nextFrame != null;
        }

        @Override
        public Frame next() {
            if (!hasNext()) throw new NoSuchElementException("No more frames");
            Frame current = nextFrame;
            advance();
            // Close decoder when iteration is naturally exhausted
            if (!hasNext()) {
                closeHardware(); // idempotent via decoderOpen guard
            }
            return current;
        }

        /**
         * Release the decoder if the iterator is abandoned before EOF.
         * Called automatically when used in try-with-resources.
         */
        @Override
        public void close() {
            done = true;
            closeHardware(); // idempotent via decoderOpen guard
        }

        private void advance() {
            if (done) return;

            // Check frame limit
            if (maxFrames > 0 && frameNumber >= maxFrames) {
                done = true;
                nextFrame = null;
                return;
            }

            long tickStart = System.nanoTime(); // must be before read + send

            // 1. Read frame
            RawFrame raw;
            try {
                raw = decoder.readFrame();
            } catch (IOException e) {
                LOG.warning("VideoStreamAgent: read error at frame "
                        + frameNumber + ": " + e.getMessage());
                done = true;
                nextFrame = null;
                return;
            }

            // EOF
            if (raw == null) {
                done = true;
                nextFrame = null;
                return;
            }

            frameNumber++;

            // 2. Send to FEAGI via sensory path
            Map<String, byte[]> sensorMap = Map.of(channelName, raw.rgbBytes());
            byte[] payload = serializeSensoryData(sensorMap);
            if (payload != null) {
                try {
                    sendSensoryPayload(payload);
                } catch (Exception e) {
                    LOG.warning("VideoStreamAgent: send error at frame "
                            + frameNumber + ": " + e.getMessage());
                }
            }
            // 2b. Poll motor data and process
            byte[] motorBytes = pollMotorBytesIfConnected();
            if (motorBytes != null) {
                AgentFrame motorFrame = AgentFrame.of(motorBytes);
                try {
                    Object commands = mapMotors(motorFrame);
                    if (commands != null) executeCommands(commands);
                } catch (Exception e) {
                    LOG.warning("VideoStreamAgent: motor error at frame "
                            + frameNumber + ": " + e.getMessage());
                }
            }

            // 3. Progress reporting (single location — not duplicated anywhere else)
            if (progressInterval > 0 && frameNumber % progressInterval == 0) {
                double elapsedSecs = (System.nanoTime() - startNanos) / 1_000_000_000.0;
                double actualFps   = elapsedSecs > 0 ? frameNumber / elapsedSecs : 0;
                int total = videoProperties != null ? videoProperties.totalFrames() : -1;
                LOG.info(String.format("VideoStreamAgent: frame %d%s (%.1f fps)",
                        frameNumber,
                        total > 0 ? "/" + total : "",
                        actualFps));
            }

            // 4. FPS pacing — subtract time already spent on read + send,
            //    identical to BaseAgent.run() tick pacing.
            if (paceByFps && videoProperties != null && videoProperties.fps() > 0) {
                long frameNanos = Math.round(1_000_000_000.0 / videoProperties.fps());
                long remaining  = frameNanos - (System.nanoTime() - tickStart);
                if (remaining > 0) {
                    try {
                        Thread.sleep(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        done = true;
                        nextFrame = null;
                        return;
                    }
                }
            }

            nextFrame = new Frame(frameNumber, raw.rgbBytes(), raw.width(), raw.height());
        }
    }

    private byte[] pollMotorBytesIfConnected() {
        try {
            return isConnected() ? pollMotorBytes() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] diffFrames(byte[] curr, byte[] prev, int threshold) {
        if (prev == null) return curr;
        byte[] result = new byte[curr.length];
        for (int i = 0; i < curr.length; i += 3) {
            int dr = Math.abs((curr[i]   & 0xFF) - (prev[i]   & 0xFF));
            int dg = Math.abs((curr[i+1] & 0xFF) - (prev[i+1] & 0xFF));
            int db = Math.abs((curr[i+2] & 0xFF) - (prev[i+2] & 0xFF));
            int delta = (dr + dg + db) / 3;
            if (delta >= threshold) {
                result[i]   = curr[i];
                result[i+1] = curr[i+1];
                result[i+2] = curr[i+2];
            }
            // else leave as 0 (black = no signal)
        }
        return result;
    }

    @Override
    protected byte[] serializeSensoryData(Map<String, byte[]> sensorData) {
        if (sensorData == null || sensorData.isEmpty()) return null;
        byte[] rgb = sensorData.get(channelName);
        if (rgb == null || videoProperties == null) return null;

        // Apply frame differencing for webcam/video
        byte[] processed = diffFrames(rgb, prevFrame, DIFF_THRESHOLD);
        prevFrame = rgb.clone();
        rgb = processed;

        int srcW = videoProperties.width();
        int srcH = videoProperties.height();

        // Images use simple vision (vision_C only)
        // Videos use segmented vision (all 9 panels)
        String fileName = videoPath.getFileName().toString().toLowerCase();
        boolean isImage = fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")
                || fileName.endsWith(".png") || fileName.endsWith(".bmp");

        if (isImage) {
            // Images use segmented vision — all 9 panels
            List<FbcEncoder.CorticalFrame> frames = new ArrayList<>();
            byte[] center = scaleRgb(rgb, srcW, srcH, 128, 128);
            frames.add(new FbcEncoder.CorticalFrame(
                    FbcEncoder.segmentedVisionCorticalId(4), center, 128, 128));
            int[][] grid = {
                {0,0},{1,0},{2,0},
                {0,1},{2,1},
                {0,2},{1,2},{2,2}
            };
            int[] groupIds = {6, 7, 8, 3, 5, 0, 1, 2};
            for (int i = 0; i < grid.length; i++) {
                byte[] crop = cropAndScale(rgb, srcW, srcH,
                        grid[i][0], grid[i][1], 3, 3, 16, 16);
                frames.add(new FbcEncoder.CorticalFrame(
                        FbcEncoder.segmentedVisionCorticalId(groupIds[i]), crop, 16, 16));
            }
            return fbcEncoder.encodeMultipleFrames(frames);
        } else {
             // Webcam/video — send all 9 segmented vision panels
            List<FbcEncoder.CorticalFrame> frames = new ArrayList<>();

            // Center panel (vision_C, group 4) — full frame scaled down
            byte[] center = scaleRgb(rgb, srcW, srcH, 128, 128);
            frames.add(new FbcEncoder.CorticalFrame(
                    FbcEncoder.segmentedVisionCorticalId(4), center, 128, 128));

            // 8 peripheral panels
            int[][] grid = {
                {0,2},{1,2},{2,2},  // BL, BM, BR  → groups 0,1,2
                {0,1},{2,1},        // ML, MR       → groups 3,5
                {0,0},{1,0},{2,0}   // TL, TM, TR   → groups 6,7,8
            };
            int[] groupIds = {0, 1, 2, 3, 5, 6, 7, 8};
            for (int i = 0; i < grid.length; i++) {
                byte[] crop = cropAndScale(rgb, srcW, srcH,
                        grid[i][0], grid[i][1], 3, 3, 16, 16);
                frames.add(new FbcEncoder.CorticalFrame(
                        FbcEncoder.segmentedVisionCorticalId(groupIds[i]), crop, 16, 16));
            }
            return fbcEncoder.encodeMultipleFrames(frames);
        }
    }

    private static byte[] cropAndScale(byte[] src, int srcW, int srcH,
            int gridX, int gridY, int gridCols, int gridRows,
            int dstW, int dstH) {
        // Source region for this grid cell
        int regionW = srcW / gridCols;
        int regionH = srcH / gridRows;
        int startX  = gridX * regionW;
        int startY  = gridY * regionH;

        byte[] dst = new byte[dstW * dstH * 3];
        for (int y = 0; y < dstH; y++) {
            for (int x = 0; x < dstW; x++) {
                int srcX = startX + x * regionW / dstW;
                int srcY = startY + y * regionH / dstH;
                int srcIdx = (srcY * srcW + srcX) * 3;
                int dstIdx = (y * dstW + x) * 3;
                dst[dstIdx]     = src[srcIdx];
                dst[dstIdx + 1] = src[srcIdx + 1];
                dst[dstIdx + 2] = src[srcIdx + 2];
            }
        }
        return dst;
    }

    private static byte[] scaleRgb(byte[] src, int srcW, int srcH, int dstW, int dstH) {
        byte[] dst = new byte[dstW * dstH * 3];
        for (int y = 0; y < dstH; y++) {
            for (int x = 0; x < dstW; x++) {
                int srcX = x * srcW / dstW;
                int srcY = y * srcH / dstH;
                int srcIdx = (srcY * srcW + srcX) * 3;
                int dstIdx = (y * dstW + x) * 3;
                dst[dstIdx]     = src[srcIdx];
                dst[dstIdx + 1] = src[srcIdx + 1];
                dst[dstIdx + 2] = src[srcIdx + 2];
            }
        }
        return dst;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static String deriveAgentId(Path path) {
        if (path == null) return "video-stream-agent";
        String name = path.getFileName() != null ? path.getFileName().toString() : "video";
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return "video-" + name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private static Path validated(Path path) {
        Objects.requireNonNull(path, "videoPath must not be null");
        if (!Files.exists(path))       throw new IllegalArgumentException("Video file not found: " + path);
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("videoPath is not a file: " + path);
        return path;
    }

    private static VideoDecoder findDecoder() {
        try {
            Class<?> cls = Class.forName("io.feagi.sdk.video.JavaCvVideoDecoder");
            return (VideoDecoder) cls.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException ignored) {
            // JavaCV not on classpath
        } catch (Exception e) {
            LOG.warning("Failed to instantiate JavaCvVideoDecoder: " + e.getMessage());
        }
        throw new FeagiSdkException(
                "No VideoDecoder found. Add 'org.bytedeco:javacv-platform' to your "
                + "dependencies, or supply a VideoDecoder via the constructor.");
    }
}
