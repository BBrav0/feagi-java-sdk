/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.logging.Logger;

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

    private final Path videoPath;
    private final VideoDecoder decoder;
    private final String channelName;

    // ── State set at open time ────────────────────────────────────────────────

    private volatile VideoProperties videoProperties;

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
     * Open the video file, read metadata, then close — without starting the
     * full agent lifecycle. Safe to call immediately after construction.
     *
     * <p>Mirrors Python's {@code auto_detect=True} constructor parameter.
     * Properties are also populated automatically when {@link #run()} or
     * {@link #stream()} first opens the file.
     *
     * @return this agent, for chaining
     * @throws IOException if the file cannot be opened or is not a valid video
     */
    public VideoStreamAgent detectProperties() throws IOException {
        try (VideoDecoder probe = decoder.getClass()
                .getDeclaredConstructor().newInstance()) {
            videoProperties = probe.open(videoPath);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to probe video properties", e);
        }
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
        try {
            decoder.close();
        } catch (Exception e) {
            LOG.warning("VideoStreamAgent: error closing decoder: " + e.getMessage());
        }
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
    public record Frame(int frameNumber, byte[] rgbBytes, int width, int height) {}

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
     * The primary engine. Each call to {@link #next()} does exactly:
     * <ol>
     *   <li>Read one frame from the decoder</li>
     *   <li>Send it to FEAGI via the sensory path</li>
     *   <li>Log progress if due</li>
     *   <li>Sleep for FPS pacing if enabled</li>
     *   <li>Return the frame to the caller</li>
     * </ol>
     * Frame accounting lives here and nowhere else.
     */
    private class StreamIterator implements Iterator<Frame> {

        private final int maxFrames;
        private final boolean paceByFps;
        private final int progressInterval;

        private int frameNumber = 0;
        private long startNanos = System.nanoTime();
        private Frame nextFrame;
        private boolean done = false;
        private boolean ownsHardware = false; // true if we opened the decoder

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

            // Open decoder if not already done by BaseAgent.run(AgentRunConfig)
            if (videoProperties == null) {
                try {
                    initializeHardware();
                    ownsHardware = true; // we opened it, we must close it
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
            // Close decoder when iteration is exhausted and we own the hardware
            if (!hasNext() && ownsHardware) {
                try {
                    closeHardware();
                } catch (Exception e) {
                    LOG.warning("VideoStreamAgent: error closing after stream: "
                            + e.getMessage());
                }
                ownsHardware = false;
            }
            return current;
        }

        private void advance() {
            if (done) return;

            // Check frame limit
            if (maxFrames > 0 && frameNumber >= maxFrames) {
                done = true;
                nextFrame = null;
                return;
            }

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

            // 4. FPS pacing
            if (paceByFps && videoProperties != null && videoProperties.fps() > 0) {
                long sleepMs = Math.round(1000.0 / videoProperties.fps());
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    done = true;
                    nextFrame = null;
                    return;
                }
            }

            nextFrame = new Frame(frameNumber, raw.rgbBytes(), raw.width(), raw.height());
        }
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
