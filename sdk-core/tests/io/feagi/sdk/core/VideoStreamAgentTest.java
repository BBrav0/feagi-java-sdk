/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link VideoStreamAgent}.
 *
 * <p>Uses {@link StubDecoder} — no video library required.
 * The stub emits N synthetic frames; each frame's RGB bytes are filled with
 * {@code (frameIndex % 256)} so tests can assert on exact byte values.
 *
 * <p>Key behaviors under test:
 * <ul>
 *   <li>{@code stream()} yields exactly N frames then stops</li>
 *   <li>Yielded frame bytes match what was sent to FEAGI</li>
 *   <li>Iterator stops at EOF without throwing</li>
 *   <li>{@code run()} is a simple consumer of {@code stream()}</li>
 *   <li>Overloads {@code run(maxFrames, paceByFps, progressInterval)} work correctly</li>
 *   <li>Frame numbers are 1-based and sequential</li>
 *   <li>{@code mapMotors()} is always a no-op</li>
 * </ul>
 */
class VideoStreamAgentTest {

    // ── Stubs ──────────────────────────────────────────────────────────────────

    static class StubClient implements FeagiAgentClient {
        int connectCalls;
        final List<byte[]> sentPayloads = new ArrayList<>();
        @Override public void connect()                  { connectCalls++; }
        @Override public void sendSensoryBytes(byte[] p) { sentPayloads.add(p.clone()); }
        @Override public byte[] pollMotorBytes()         { return null; }
        @Override public void close()                    {}
    }

    /**
     * Stub decoder: emits {@code totalFrames} frames then returns null.
     * Frame N (0-indexed) is filled with byte value {@code N % 256}.
     */
    static class StubDecoder implements VideoStreamAgent.VideoDecoder {
        final int totalFrames;
        final int width;
        final int height;
        final double fps;
        int readCount = 0;
        boolean opened = false;
        boolean closed = false;
        IOException openEx  = null;
        IOException readEx  = null;

        StubDecoder(int totalFrames, int width, int height, double fps) {
            this.totalFrames = totalFrames;
            this.width = width;
            this.height = height;
            this.fps = fps;
        }

        @Override
        public VideoStreamAgent.VideoProperties open(Path path) throws IOException {
            if (openEx != null) throw openEx;
            opened = true;
            return new VideoStreamAgent.VideoProperties(width, height, fps, totalFrames);
        }

        @Override
        public VideoStreamAgent.RawFrame readFrame() throws IOException {
            if (readEx != null) throw readEx;
            if (readCount >= totalFrames) return null;
            byte fill = (byte) (readCount % 256);
            byte[] rgb = new byte[width * height * 3];
            java.util.Arrays.fill(rgb, fill);
            readCount++;
            return new VideoStreamAgent.RawFrame(rgb, width, height);
        }

        @Override public void close() { closed = true; }
    }

    // ── Setup ──────────────────────────────────────────────────────────────────

    @TempDir Path tempDir;
    StubClient  stub;
    StubDecoder decoder;
    Path        fakeVideo;

    @BeforeEach
    void setUp() throws Exception {
        stub      = new StubClient();
        decoder   = new StubDecoder(5, 4, 3, 30.0); // 5 frames, 4x3, 30 fps
        fakeVideo = tempDir.resolve("clip.mp4");
        Files.writeString(fakeVideo, "not real video");
    }

    private VideoStreamAgent agent() {
        return new VideoStreamAgent(fakeVideo, makeConfig(), stub, decoder);
    }

    static AgentConfig makeConfig() {
        FeagiEndpoints ep = new FeagiEndpoints(
                "tcp://localhost:30001",
                "tcp://localhost:5558",
                null, null, null);
        AgentCapabilities caps = AgentCapabilities.builder()
                .sensory(new SensoryCapability(30.0, null))
                .build();
        return new AgentConfig(
                "video-agent", AgentType.SENSORY, ep, caps,
                Duration.ZERO, Duration.ofSeconds(5), 3,
                Duration.ofMillis(500),
                new SensorySocketConfig(1000, 0, true),
                null);
    }

    // ── Construction ───────────────────────────────────────────────────────────

    @Test
    void constructor_rejectsNullVideoPath() {
        assertThrows(NullPointerException.class,
                () -> new VideoStreamAgent(null, makeConfig(), stub, decoder));
    }

    @Test
    void constructor_rejectsNonExistentFile() {
        assertThrows(IllegalArgumentException.class,
                () -> new VideoStreamAgent(
                        tempDir.resolve("missing.mp4"), makeConfig(), stub, decoder));
    }

    @Test
    void constructor_rejectsDirectory() {
        assertThrows(IllegalArgumentException.class,
                () -> new VideoStreamAgent(tempDir, makeConfig(), stub, decoder));
    }

    @Test
    void constructor_derivesAgentIdFromFilename() {
        assertEquals("video-clip", agent().agentId());
    }

    @Test
    void videoProperties_isNullBeforeAnyOpen() {
        assertNull(agent().videoProperties(),
                "videoProperties must be null before open");
    }

    // ── stream() — iterator contract ──────────────────────────────────────────

    @Test
    void stream_yieldsExactlyNFrames() {
        VideoStreamAgent a = agent();
        a.connect();

        List<VideoStreamAgent.Frame> frames = new ArrayList<>();
        for (VideoStreamAgent.Frame f : a.stream(0, false, 0)) {
            frames.add(f);
        }
        assertEquals(5, frames.size(), "must yield exactly totalFrames frames");
    }

    @Test
    void stream_frameNumbersAreOneBasedAndSequential() {
        VideoStreamAgent a = agent();
        a.connect();

        int expected = 1;
        for (VideoStreamAgent.Frame f : a.stream(0, false, 0)) {
            assertEquals(expected, f.frameNumber());
            expected++;
        }
        assertEquals(6, expected, "must have iterated 5 frames");
    }

    @Test
    void stream_stopsAtEof_withoutThrowing() {
        VideoStreamAgent a = agent();
        a.connect();

        // Drain all frames — must not throw NoSuchElementException or anything else
        assertDoesNotThrow(() -> {
            int drained = 0;
            for (VideoStreamAgent.Frame f : a.stream(0, false, 0)) {
                drained++;
                f.frameNumber(); // use f to avoid unused warnings
            }
            assertEquals(5, drained, "must drain all frames until EOF");
        });
    }

    @Test
    void stream_yieldedFrameBytesMatchDecoderOutput() {
        VideoStreamAgent a = agent();
        a.connect();

        // Frame 1 (0-indexed frame 0): fill = 0
        // Frame 2 (0-indexed frame 1): fill = 1
        int frameIdx = 0;
        for (VideoStreamAgent.Frame f : a.stream(0, false, 0)) {
            byte expectedFill = (byte) frameIdx;
            for (byte b : f.rgbBytes()) {
                assertEquals(expectedFill, b,
                        "frame " + f.frameNumber() + " byte mismatch");
            }
            assertEquals(4, f.width());
            assertEquals(3, f.height());
            frameIdx++;
        }
    }

    @Test
    void stream_sendsOnePayloadPerFrame() {
        VideoStreamAgent a = agent();
        a.connect();

        int drained = 0;
        for (VideoStreamAgent.Frame f : a.stream(0, false, 0)) {
            drained++;
            f.frameNumber(); // use f to avoid unused warnings
        }

        assertEquals(5, drained,
                "must iterate exactly totalFrames frames");
        assertEquals(5, stub.sentPayloads.size(),
                "one sendSensoryBytes call per frame");
    }

    @Test
    void stream_sentPayloadBytesMatchYieldedFrameBytes() {
        VideoStreamAgent a = agent();
        a.connect();

        List<VideoStreamAgent.Frame> yielded = new ArrayList<>();
        for (VideoStreamAgent.Frame f : a.stream(0, false, 0)) {
            yielded.add(f);
        }
        assertEquals(5, stub.sentPayloads.size());

        // For each frame, decode the length-prefixed payload and verify the
        // value bytes match the yielded RGB bytes exactly.
        for (int i = 0; i < yielded.size(); i++) {
            byte[] payload = stub.sentPayloads.get(i);
            byte[] frameRgb = yielded.get(i).rgbBytes();

            // Parse: 4-byte key len, key bytes, 4-byte value len, value bytes
            int kLen = toInt(payload, 0);
            int off  = 4 + kLen;
            int vLen = toInt(payload, off);
            byte[] sentValue = new byte[vLen];
            System.arraycopy(payload, off + 4, sentValue, 0, vLen);

            assertArrayEquals(frameRgb, sentValue,
                    "sent bytes must match yielded rgbBytes for frame " + (i + 1));
        }
    }

    @Test
    void stream_payloadChannelKeyIsCameraByDefault() {
        VideoStreamAgent a = agent();
        a.connect();

        VideoStreamAgent.Frame first = null;
        for (VideoStreamAgent.Frame f : a.stream(0, false, 0)) {
            first = f;
            break; // just first frame
        }
        assertNotNull(first, "must yield at least one frame");

        byte[] payload = stub.sentPayloads.get(0);
        int kLen = toInt(payload, 0);
        String key = new String(payload, 4, kLen, StandardCharsets.UTF_8);
        assertEquals(VideoStreamAgent.DEFAULT_CHANNEL, key);
    }

    // ── stream(maxFrames) — limit ─────────────────────────────────────────────

    @Test
    void stream_maxFrames_stopsEarly() {
        VideoStreamAgent a = agent();
        a.connect();

        int count = 0;
        for (VideoStreamAgent.Frame f : a.stream(3, false, 0)) {
            count++;
            f.frameNumber(); // use f to avoid unused warnings
        }
        assertEquals(3, count, "must stop after maxFrames");
        assertEquals(3, stub.sentPayloads.size());
    }

    @Test
    void stream_maxFramesLargerThanFile_yieldsEntireFile() {
        VideoStreamAgent a = agent();
        a.connect();

        int count = 0;
        for (VideoStreamAgent.Frame f : a.stream(999, false, 0)) {
            count++;
            f.frameNumber(); // use f to avoid unused warnings
        }
        assertEquals(5, count, "must yield all frames when maxFrames > total");
    }

    // ── run() — consumer of stream() ─────────────────────────────────────────

    @Test
    void run_returnsFrameCount() throws InterruptedException {
        VideoStreamAgent a = agent();
        a.connect();
        assertEquals(5, a.run());
    }

    @Test
    void run_withMaxFrames_returnsLimitedCount() throws InterruptedException {
        VideoStreamAgent a = agent();
        a.connect();
        assertEquals(3, a.run(3, false, 0));
    }

    @Test
    void run_requiresConnect() {
        VideoStreamAgent a = agent();
        assertThrows(IllegalStateException.class, a::run);
    }

    @Test
    void run_opensAndClosesDecoder() throws InterruptedException {
        VideoStreamAgent a = agent();
        a.connect();
        a.run();
        assertTrue(decoder.opened, "decoder must be opened during run");
        assertTrue(decoder.closed, "decoder must be closed when stream exhausts");
    }

    @Test
    void close_releasesDecoder() throws InterruptedException {
        VideoStreamAgent a = agent();
        a.connect();
        a.run();
        // Decoder already closed by iterator at EOF.
        // close() must be idempotent and not throw.
        assertDoesNotThrow(a::close);
        assertTrue(decoder.closed);
    }

    @Test
    void stream_earlyBreak_decoderReleasedByClose() {
        // If caller breaks out of stream() before EOF, the decoder must be released
        // when close() is called — it must not leak.
        VideoStreamAgent a = agent();
        a.connect();

        // Break after first frame
        for (VideoStreamAgent.Frame f : a.stream(0, false, 0)) {
            f.frameNumber(); // use f to avoid unused warnings
            break;
        }

        // Decoder is still open because early break skips the EOF close in next().
        // close() must detect decoderOpen=true and release it.
        a.close();
        assertTrue(decoder.closed,
                "decoder must be closed by close() after early stream() exit");
    }

    // ── videoProperties after open ────────────────────────────────────────────

    @Test
    void videoProperties_populatedAfterFirstStream() {
        VideoStreamAgent a = agent();
        a.connect();

        // Consume one frame to trigger open
        for (VideoStreamAgent.Frame f : a.stream(1, false, 0)) {
            f.frameNumber(); // use f to avoid unused warnings
            break;
        }

        VideoStreamAgent.VideoProperties p = a.videoProperties();
        assertNotNull(p);
        assertEquals(4,    p.width());
        assertEquals(3,    p.height());
        assertEquals(30.0, p.fps());
        assertEquals(5,    p.totalFrames());
    }

    // ── mapMotors — input-only contract ───────────────────────────────────────

    @Test
    void frame_rgbBytes_isDefensivelyCopied() {
        byte[] original = {1, 2, 3};
        VideoStreamAgent.Frame frame =
                new VideoStreamAgent.Frame(1, original, 1, 1);

        // Mutating the array passed to the constructor must not affect the frame.
        // The compact constructor copies on construction — this is the guarantee.
        original[0] = 99;
        assertEquals(1, frame.rgbBytes()[0],
                "Frame must defensively copy rgbBytes — mutation of original must not affect frame");

        // Note: rgbBytes() returns the same internal array reference on each call
        // (standard record accessor behaviour). Callers who need an independent copy
        // must clone the result themselves: frame.rgbBytes().clone()
    }

    // ── mapMotors — input-only contract ───────────────────────────────────────

    @Test
    void mapMotors_isAlwaysNoop() {
        VideoStreamAgent a = agent();
        assertNull(a.mapMotors(AgentFrame.empty()));
        assertNull(a.mapMotors(AgentFrame.of(new byte[]{1})));
    }

    // ── error handling ────────────────────────────────────────────────────────

    @Test
    void stream_readerError_stopsIteratorGracefully() {
        decoder.readEx = new IOException("disk error");
        VideoStreamAgent a = agent();
        a.connect();

        // Iterator should stop at first read failure, not throw
        int count = 0;
        for (VideoStreamAgent.Frame f : a.stream(0, false, 0)) {
            count++;
            f.frameNumber(); // use f to avoid unused warnings
        }
        assertEquals(0, count, "no frames should be yielded on immediate read error");
    }

    // ── close() ───────────────────────────────────────────────────────────────

    @Test
    void close_isIdempotent() throws InterruptedException {
        VideoStreamAgent a = agent();
        a.connect();
        a.run();
        assertDoesNotThrow(() -> { a.close(); a.close(); });
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static int toInt(byte[] buf, int off) {
        return ((buf[off] & 0xFF) << 24) | ((buf[off+1] & 0xFF) << 16)
             | ((buf[off+2] & 0xFF) << 8) |  (buf[off+3] & 0xFF);
    }
}
