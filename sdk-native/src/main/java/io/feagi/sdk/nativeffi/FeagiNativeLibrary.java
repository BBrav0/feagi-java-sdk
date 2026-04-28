/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.nativeffi;

import io.feagi.sdk.core.FeagiSdkException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * Native library loader for the FEAGI Java SDK.
 *
 * <p>This is intentionally minimal. The production SDK will likely load a platform-specific
 * classifier artifact (native .so/.dylib/.dll) and then call {@link FeagiNativeBindings#feagiAbiVersion()}
 * to verify compatibility.
 *
 * <p>Guardrail: no runtime defaults (host/ports/timeouts) belong here.
 */
public final class FeagiNativeLibrary {
    private FeagiNativeLibrary() {}

    private static final int EXPECTED_ABI_VERSION = FeagiNativeBindings.EXPECTED_ABI_VERSION;

    private static volatile boolean loaded = false;

    /**
     * Load the FEAGI native library into the JVM.
     *
     * <p>Future work: implement a robust loader (classpath extraction + OS/arch detection).
     *
     * @param libraryName name passed to {@link System#loadLibrary(String)} (no "lib" prefix)
     */
    public static void load(String libraryName) {
        if (libraryName == null || libraryName.isEmpty()) {
            throw new IllegalArgumentException("libraryName must not be null/empty");
        }
        System.loadLibrary(libraryName);
    }

    /**
     * Required by acceptance: load + ABI handshake + clear error handling in one step.
     */
    public static void loadAndVerify(String libraryName) {
        if (loaded) {
            throw new IllegalStateException("FEAGI native library already loaded");
        }

        synchronized (FeagiNativeLibrary.class) {
            if (loaded) {
                throw new IllegalStateException("FEAGI native library already loaded");
            }

            if (libraryName == null || libraryName.isEmpty()) {
                throw new IllegalArgumentException("libraryName must not be null/empty");
            }

            try {
                if (libraryName.contains("/") || libraryName.contains("\\")) {
                    System.load(libraryName);  // full path
                } else {
                    System.loadLibrary(libraryName);  // just the name
                }
            } catch (UnsatisfiedLinkError e) {
                throw new FeagiSdkException(
                        "Failed to load FEAGI JNI library '" + libraryName + "'. " +
                        "Make sure the DLL is on java.library.path and its dependencies are on PATH. " +
                        "Original error: " + e.getMessage(),
                        e
                );
            }

            final int abi;
            try {
                abi = FeagiNativeBindings.feagiAbiVersion();
            } catch (UnsatisfiedLinkError e) {
                throw new FeagiSdkException(
                        "Loaded JNI library '" + libraryName + "', but feagiAbiVersion() is missing. " +
                        "This usually means you loaded the wrong DLL. " +
                        "Original error: " + e.getMessage(),
                        e
                );
            }

            if (abi != EXPECTED_ABI_VERSION) {
                throw new FeagiSdkException(
                        "FEAGI native ABI mismatch: expected " + EXPECTED_ABI_VERSION + " but got " + abi + ". " +
                        "Update feagi-java-ffi and the JNI bridge to matching versions."
                );
            }

            loaded = true;
        }
    }

    // =========================================================================
    // OS / Architecture detection (package-private for testability)
    // =========================================================================

    /**
     * Detect the canonical OS name from the given {@code os.name} system property value.
     *
     * @param osName value of {@code System.getProperty("os.name")}
     * @return one of {@code "linux"}, {@code "osx"}, or {@code "windows"}
     * @throws FeagiSdkException if the OS cannot be recognized
     */
    static String detectOs(String osName) {
        if (osName == null) {
            throw new FeagiSdkException("Unrecognized OS: null");
        }
        String lower = osName.toLowerCase();
        if (lower.contains("linux")) {
            return "linux";
        } else if (lower.contains("mac os x") || lower.contains("darwin") || lower.contains("mac")) {
            return "osx";
        } else if (lower.contains("windows")) {
            return "windows";
        } else {
            throw new FeagiSdkException("Unrecognized OS: " + osName);
        }
    }

    /**
     * Detect the canonical architecture name from the given {@code os.arch} system property value.
     *
     * @param osArch value of {@code System.getProperty("os.arch")}
     * @return one of {@code "x86_64"} or {@code "aarch64"}
     * @throws FeagiSdkException if the architecture cannot be recognized
     */
    static String detectArch(String osArch) {
        if (osArch == null) {
            throw new FeagiSdkException("Unrecognized architecture: null");
        }
        String lower = osArch.toLowerCase();
        if (lower.equals("amd64") || lower.equals("x86_64")) {
            return "x86_64";
        } else if (lower.equals("aarch64") || lower.equals("arm64")) {
            return "aarch64";
        } else {
            throw new FeagiSdkException("Unrecognized architecture: " + osArch);
        }
    }

    /**
     * Compose the platform string from OS and architecture components.
     *
     * @param os   canonical OS (e.g. "linux", "osx", "windows")
     * @param arch canonical arch (e.g. "x86_64", "aarch64")
     * @return platform string in the form {@code "{os}-{arch}"}
     */
    static String composePlatform(String os, String arch) {
        return os + "-" + arch;
    }

    /**
     * Detect the current platform string using system properties.
     *
     * @return platform string (e.g. "linux-x86_64")
     * @throws FeagiSdkException if OS or arch cannot be recognized
     */
    static String detectPlatform() {
        String os = detectOs(System.getProperty("os.name"));
        String arch = detectArch(System.getProperty("os.arch"));
        return composePlatform(os, arch);
    }

    /**
     * Resolve the native library filename for the given canonical OS name.
     *
     * @param os canonical OS (e.g. "linux", "osx", "windows")
     * @return the native library filename
     * @throws FeagiSdkException if the OS is not recognized
     */
    static String resolveLibraryFilename(String os) {
        switch (os) {
            case "linux":   return "libfeagi_java_ffi.so";
            case "osx":     return "libfeagi_java_ffi.dylib";
            case "windows": return "feagi_java_ffi.dll";
            default: throw new FeagiSdkException("Cannot resolve library filename for OS: " + os);
        }
    }

    /**
     * Get the file extension (including the dot) from a filename.
     * Returns an empty string if no extension is found.
     */
    private static String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot >= 0) {
            return filename.substring(lastDot);
        }
        return "";
    }

    /**
     * Extract a native library from the classpath and load it via {@link System#load(String)}.
     *
     * <p>Looks for the resource at {@code "native/{platform}/{libfilename}"} on the classpath,
     * extracts it to a temporary file (preserving the file extension), registers the temp file
     * for deletion on JVM exit, and calls {@link System#load(String)} with the absolute path.
     *
     * @param platform   platform string (e.g. "linux-x86_64")
     * @param libFilename native library filename (e.g. "libfeagi_java_ffi.so")
     * @throws FeagiSdkException if the resource is not found on the classpath
     * @throws FeagiSdkException if extraction fails due to an I/O error
     */
    static void extractAndLoad(String platform, String libFilename) {
        String resourcePath = "native/" + platform + "/" + libFilename;
        InputStream in = FeagiNativeLibrary.class.getClassLoader().getResourceAsStream(resourcePath);
        if (in == null) {
            throw new FeagiSdkException(
                    "Native library not found on classpath at '" + resourcePath + "' " +
                    "for platform '" + platform + "'. " +
                    "Make sure the platform-classified sdk-native JAR is on the classpath."
            );
        }
        String extension = getFileExtension(libFilename);
        File tempFile;
        try {
            tempFile = File.createTempFile("feagi_native_", extension);
        } catch (IOException e) {
            try { in.close(); } catch (IOException ignored) {}
            throw new FeagiSdkException("Failed to create temp file for native library extraction: " + e.getMessage(), e);
        }
        tempFile.deleteOnExit();
        try (InputStream inputStream = in;
             OutputStream out = Files.newOutputStream(tempFile.toPath())) {
            byte[] buf = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buf)) != -1) {
                out.write(buf, 0, bytesRead);
            }
        } catch (IOException e) {
            throw new FeagiSdkException("Failed to extract native library to temp file: " + e.getMessage(), e);
        }
        System.load(tempFile.getAbsolutePath());
    }

    // =========================================================================
    // Public auto-detection loader
    // =========================================================================

    /**
     * Load the FEAGI native library from the classpath using automatic OS/architecture detection.
     *
     * <p>This method:
     * <ol>
     *   <li>Auto-detects OS and architecture from system properties</li>
     *   <li>Computes the classpath resource path ({@code native/{platform}/{libfilename}})</li>
     *   <li>Extracts the native library to a temp file and loads it</li>
     *   <li>Calls {@link FeagiNativeBindings#feagiAbiVersion()} for ABI verification</li>
     *   <li>Falls back to {@link System#loadLibrary(String)} with {@code "feagi_java_ffi"} if
     *       classpath extraction fails</li>
     * </ol>
     *
     * <p>Thread-safe: uses the same {@code volatile + synchronized} pattern as
     * {@link #loadAndVerify(String)}.
     *
     * @throws FeagiSdkException if both classpath extraction and {@code System.loadLibrary}
     *                           fallback fail, or if the ABI version does not match
     */
    public static void loadFromClasspath() {
        if (loaded) {
            return;
        }

        synchronized (FeagiNativeLibrary.class) {
            if (loaded) {
                return;
            }

            String platform = detectPlatform();
            String os = detectOs(System.getProperty("os.name"));
            String libFilename = resolveLibraryFilename(os);

            FeagiSdkException classpathException = null;
            try {
                extractAndLoad(platform, libFilename);
            } catch (FeagiSdkException e) {
                classpathException = e;
                // Fall back to System.loadLibrary
                try {
                    System.loadLibrary("feagi_java_ffi");
                } catch (UnsatisfiedLinkError ule) {
                    throw new FeagiSdkException(
                            "Failed to load FEAGI native library via classpath extraction (" +
                            classpathException.getMessage() + ") and System.loadLibrary fallback failed: " +
                            ule.getMessage(),
                            ule
                    );
                }
            }

            final int abi;
            try {
                abi = FeagiNativeBindings.feagiAbiVersion();
            } catch (UnsatisfiedLinkError e) {
                throw new FeagiSdkException(
                        "Loaded FEAGI native library for platform '" + platform +
                        "', but feagiAbiVersion() is missing. " +
                        "This usually means you loaded the wrong library. " +
                        "Original error: " + e.getMessage(),
                        e
                );
            }

            if (abi != EXPECTED_ABI_VERSION) {
                throw new FeagiSdkException(
                        "FEAGI native ABI mismatch: expected " + EXPECTED_ABI_VERSION + " but got " + abi + ". " +
                        "Update feagi-java-ffi and the JNI bridge to matching versions."
                );
            }

            loaded = true;
        }
    }
}
