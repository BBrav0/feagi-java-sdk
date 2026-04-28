/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Demo: start FEAGI → stream video/webcam → print motor output
 *
 * Usage:
 *   java -Dfeagi.native.lib=feagi_jni.dll -jar sdk-demo.jar <video-file>
 *   java -Dfeagi.native.lib=feagi_jni.dll -jar sdk-demo.jar --webcam [device-index]
 */

package io.feagi.sdk.demo;

import io.feagi.sdk.core.AgentCapabilities;
import io.feagi.sdk.core.AgentConfig;
import io.feagi.sdk.core.AgentFrame;
import io.feagi.sdk.core.AgentType;
import io.feagi.sdk.core.FeagiAgentClient;
import io.feagi.sdk.core.FeagiEndpoints;
import io.feagi.sdk.core.MotorCapability;
import io.feagi.sdk.core.MotorUnit;
import io.feagi.sdk.core.NeuronPotential;
import io.feagi.sdk.core.SensorySocketConfig;
import io.feagi.sdk.core.SensoryUnit;
import io.feagi.sdk.core.VideoStreamAgent;
import io.feagi.sdk.core.VisionCapability;
import io.feagi.sdk.engine.FeagiEngine;
import io.feagi.sdk.nativeffi.FeagiNativeLibrary;
import io.feagi.sdk.nativeffi.NativeFeagiAgentClient;
import io.feagi.sdk.core.MotorSocketConfig;
import io.feagi.sdk.video.JavaCvVideoDecoder;
import io.feagi.sdk.video.WebcamDecoder;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class ExampleWebcamMotor extends VideoStreamAgent {

    public ExampleWebcamMotor(Path videoPath, AgentConfig config,
                               FeagiAgentClient client, String channelName) {
        super(videoPath, config, client, new JavaCvVideoDecoder(), channelName);
    }

    public ExampleWebcamMotor(Path videoPath, AgentConfig config,
                               FeagiAgentClient client,
                               VideoDecoder decoder, String channelName) {
        super(videoPath, config, client, decoder, channelName);
    }

    @Override
    protected Object mapMotors(AgentFrame frame) {
        if (!frame.hasData()) return null;
        byte[] raw = frame.motorBytes();
        if (raw == null || raw.length == 0) return null;

        try {
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(raw)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN);

            // FBC format: 4 (global header) + 48 (agent id) + 4 (struct lookup)
            buf.position(56);

            // struct header: type(1) + version(1) + cortical_count(2)
            buf.get();
            buf.get();
            int corticalCount = buf.getShort() & 0xFFFF;

            // Each sub-header: 8 (cortical id) + 4 (dataStart) + 4 (dataLen) + 4 (neuronCount)
            int[] dataStarts   = new int[corticalCount];
            int[] dataLens     = new int[corticalCount];
            int[] neuronCounts = new int[corticalCount];
            for (int c = 0; c < corticalCount; c++) {
                buf.position(buf.position() + 8);
                dataStarts[c]   = buf.getInt();
                dataLens[c]     = buf.getInt();
                neuronCounts[c] = buf.getInt();
            }

            List<NeuronPotential> result = new ArrayList<>();
            for (int c = 0; c < corticalCount; c++) {
                if (dataLens[c] <= 0) continue;
                int absStart = 56 + dataStarts[c];
                if (absStart + dataLens[c] > raw.length) continue;

                java.nio.ByteBuffer dataBuf = java.nio.ByteBuffer.wrap(
                        raw, absStart, dataLens[c])
                        .order(java.nio.ByteOrder.LITTLE_ENDIAN);

                int neuronCount = neuronCounts[c];
                for (int i = 0; i < neuronCount; i++) {
                    int x = dataBuf.getInt();
                    result.add(NeuronPotential.of(x, 1.0f));
                }
            }

            return result.isEmpty() ? null : result;

        } catch (Exception e) {
            System.err.println("[motor] FBC decode error: " + e.getMessage());
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void executeCommands(Object commands) {
        if (commands == null) return;
        List<NeuronPotential> neurons = (List<NeuronPotential>) commands;
        for (NeuronPotential m : neurons) {
            if (m.neuronId() == 0) {
                System.out.println("[motor] TOP-RIGHT panel active  potential=" 
                    + String.format("%.3f", m.potential()));
            }
        }
    }

    private static String regionLabel(int id) {
        switch (id) {
            case 0: return "TOP-RIGHT";
            case 1: return "TOP-MID";
            case 2: return "TOP-LEFT";
            case 3: return "MID-RIGHT";
            case 4: return "CENTER";
            case 5: return "MID-LEFT";
            case 6: return "BOTTOM-RIGHT";
            case 7: return "BOTTOM-MID";
            case 8: return "BOTTOM-LEFT";
            default: return "region-" + id;
        }
    }

    public static void main(String[] args) throws Exception {

        boolean useWebcam    = false;
        int     webcamDevice = 0;
        Path    videoFile    = null;

        if (args.length == 0) {
            printUsage();
            return;
        }

        if (args[0].equals("--webcam")) {
            useWebcam = true;
            if (args.length > 1) {
                try { webcamDevice = Integer.parseInt(args[1]); }
                catch (NumberFormatException e) {
                    System.out.println("[WARN] Invalid device index, using 0");
                }
            }
        } else {
            videoFile = Path.of(args[0]);
        }

        System.out.println("=".repeat(60));
        System.out.println("FEAGI Java SDK - Webcam + Motor Demo");
        System.out.println("=".repeat(60));
        if (useWebcam) {
            System.out.println("Mode   : Live Webcam (device " + webcamDevice + ")");
        } else {
            System.out.println("Video  : " + videoFile);
        }
        System.out.println("Press Ctrl+C to stop.");
        System.out.println("-".repeat(60));

        String lib = System.getProperty("feagi.native.lib",
                System.mapLibraryName("feagi_java_ffi"));
        FeagiNativeLibrary.loadAndVerify(lib);

        // Start FEAGI
        FeagiEngine engine = FeagiEngine.builder().build();
        boolean started = engine.start();
        if (!started) {
            System.out.println("[FAIL] FEAGI failed to start.");
            engine.close();
            return;
        }
        System.out.println("[OK] FEAGI started (PID: " + engine.pid().orElse(-1) + ")");
        System.out.println();
        System.out.println("Next steps:");
        System.out.println("  1. Open Brain Visualizer: feagi bv start");
        System.out.println("  2. Load the Essential Genome from the BV toolbar");
        System.out.println("  3. Draw a connection from vision_TR to Motor Output");
        System.out.println("  4. Wave your hand in the top-right of the webcam frame");
        System.out.println();
        System.out.println("Waiting 10s for genome setup...");
        Thread.sleep(10000);

        AgentConfig config = buildConfig(engine.host(), engine.restPort());

        if (useWebcam) {
            WebcamDecoder webcamDecoder = new WebcamDecoder(webcamDevice);
            Path tempPath = java.nio.file.Files.createTempFile("webcam", ".tmp");
            tempPath.toFile().deleteOnExit();

            try (engine;
                 NativeFeagiAgentClient transport = new NativeFeagiAgentClient(config);
                 ExampleWebcamMotor agent = new ExampleWebcamMotor(
                         tempPath, config, transport, webcamDecoder, "img")) {

                agent.connect();
                System.out.println("[OK] Connected to FEAGI.");
                System.out.println("[WEBCAM] Streaming live from device " + webcamDevice);
                System.out.println("[INFO] Motor output will print when motion is detected.");
                System.out.println();

                int loopCount = 0;
                while (!Thread.currentThread().isInterrupted()) {
                    loopCount++;
                    System.out.printf("%n[%d] Live webcam stream%n", loopCount);
                    int framesSent = agent.run();
                    System.out.printf("  Sent %d frames%n", framesSent);
                }
            }

        } else {
            Path assetsDir = videoFile.getParent() != null
                    ? videoFile.getParent() : Path.of(".");
            List<Path> mediaFiles = new ArrayList<>();
            try (var dirStream = java.nio.file.Files.list(assetsDir)) {
                dirStream.filter(p -> {
                    String n = p.getFileName().toString().toLowerCase();
                    return n.endsWith(".mp4") || n.endsWith(".m4v") || n.endsWith(".avi")
                        || n.endsWith(".mov") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                        || n.endsWith(".png");
                }).sorted().forEach(mediaFiles::add);
            } catch (Exception e) {
                mediaFiles.add(videoFile);
            }
            if (mediaFiles.isEmpty()) mediaFiles.add(videoFile);

            try (engine;
                 NativeFeagiAgentClient transport = new NativeFeagiAgentClient(config);
                 ExampleWebcamMotor agent = new ExampleWebcamMotor(
                         videoFile, config, transport, "img")) {

                agent.connect();
                System.out.println("[OK] Connected to FEAGI.");

                int loopCount = 0;
                while (!Thread.currentThread().isInterrupted()) {
                    loopCount++;
                    for (Path media : mediaFiles) {
                        System.out.printf("%n[%d] Playing %s%n",
                                loopCount, media.getFileName());
                        agent.resetFile(media);

                        String name = media.getFileName().toString().toLowerCase();
                        boolean isImage = name.endsWith(".jpg") || name.endsWith(".jpeg")
                                || name.endsWith(".png") || name.endsWith(".bmp");

                        if (isImage) {
                            int framesSent = agent.runImage(5, 30);
                            System.out.printf("  Sent %d frames (static image)%n", framesSent);
                        } else {
                            int framesSent = agent.run();
                            System.out.printf("  Sent %d frames%n", framesSent);
                        }
                    }
                }
            }
        }

        System.out.println("FEAGI stopped.");
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  ExampleWebcamMotor <video-file>");
        System.out.println("  ExampleWebcamMotor --webcam [device-index]");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  ExampleWebcamMotor assets/rotating_bar.m4v");
        System.out.println("  ExampleWebcamMotor --webcam");
        System.out.println("  ExampleWebcamMotor --webcam 1");
    }

    private static AgentConfig buildConfig(String host, int restPort) {
        FeagiEndpoints endpoints = new FeagiEndpoints(
                "tcp://" + host + ":30001",
                "tcp://" + host + ":5558",
                "tcp://" + host + ":5564",
                null, null
        );

        VisionCapability vision = VisionCapability.fromUnit(
                "img", 64, 64, 3, SensoryUnit.SEGMENTED_VISION, 0);

        MotorCapability motor = MotorCapability.fromUnit(
                "misc-out", 2, MotorUnit.MISC_DATA, 0);

        AgentCapabilities caps = AgentCapabilities.builder()
                .vision(vision)
                .motor(motor)
                .build();

        return new AgentConfig(
            "webcam-motor-demo",
            AgentType.BOTH,
            endpoints,
            caps,
            Duration.ZERO,
            Duration.ofSeconds(10),
            3,
            Duration.ofMillis(500),
            new SensorySocketConfig(1000, 0, true),
            new MotorSocketConfig(1000, 0, true)
        );
    }
}
