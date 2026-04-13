/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.nativeffi;

import io.feagi.sdk.core.FeagiSdkException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Native library loader for the FEAGI Java SDK.
 *
 * <p>Loads the platform-specific JNI library from either a classpath resource
 * ({@code native/&lt;platform&gt;/...}) or via {@link System#loadLibrary(String)}.
 * After load, it validates ABI compatibility through {@code feagiAbiVersion()}.
 */
public final class FeagiNativeLibrary {
    private static final String BASE_LIBRARY_NAME = "feagi_java_ffi";
    private static final String NATIVES_ROOT = "native";
    private static final String TEMP_PREFIX = "feagi-java-ffi-lib-";
    private static final int EXPECTED_ABI_VERSION = FeagiNativeBindings.EXPECTED_ABI_VERSION;

    private static volatile boolean loaded = false;

    private FeagiNativeLibrary() {}

    @FunctionalInterface
    interface PathLoadHook {
        void load(String absolutePath);
    }

    @FunctionalInterface
    interface NameLoadHook {
        void load(String libraryName);
    }

    @FunctionalInterface
    interface AbiVersionProvider {
        int getAsInt();
    }

    static volatile PathLoadHook pathLoadHook = System::load;
    static volatile NameLoadHook nameLoadHook = System::loadLibrary;
    static volatile AbiVersionProvider abiVersionProvider = FeagiNativeBindings::feagiAbiVersion;

    public static void load(String libraryName) {
        validateLibraryName(libraryName);
        loadLibraryByName(libraryName);
    }

    public static void loadAndVerify(String libraryName) {
        loadAndVerify(
                libraryName,
                System.getProperty("os.name"),
                System.getProperty("os.arch"),
                Thread.currentThread().getContextClassLoader()
        );
    }

    static void loadAndVerify(String libraryName, String osName, String osArch) {
        loadAndVerify(libraryName, osName, osArch, Thread.currentThread().getContextClassLoader());
    }

    static void loadAndVerify(String libraryName, String osName, String osArch, ClassLoader classLoader) {
        validateLibraryName(libraryName);
        if (osName == null || osName.isBlank()) {
            throw new IllegalArgumentException("osName must not be null/blank");
        }
        if (osArch == null || osArch.isBlank()) {
            throw new IllegalArgumentException("osArch must not be null/blank");
        }

        final String normalizedOs = PlatformDetector.detectOs(osName);
        final String normalizedArch = PlatformDetector.detectArch(osArch);
        final String platform = PlatformDetector.platformString(normalizedOs, normalizedArch);
        final String resourcePath = PlatformDetector.resourcePath(BASE_LIBRARY_NAME, normalizedOs, normalizedArch);
        final String nativeFilename = PlatformDetector.nativeLibraryName(BASE_LIBRARY_NAME, normalizedOs);
        final ClassLoader effectiveClassLoader =
                classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();

        if (loaded) {
            throw new IllegalStateException("FEAGI native library already loaded");
        }

        synchronized (FeagiNativeLibrary.class) {
            if (loaded) {
                throw new IllegalStateException("FEAGI native library already loaded");
            }

            boolean loadedFromClasspath = false;
            try (InputStream resourceStream = effectiveClassLoader.getResourceAsStream(resourcePath)) {
                if (resourceStream != null) {
                    loadedFromClasspath = loadFromClasspathResource(resourcePath, nativeFilename, resourceStream);
                }
            } catch (IOException e) {
                throw new FeagiSdkException(
                        "Failed to close classpath resource stream for '" + resourcePath + "'.",
                        e
                );
            } catch (FeagiSdkException e) {
                throw e;
            }

            if (!loadedFromClasspath) {
                try {
                    loadLibraryByName(libraryName);
                } catch (FeagiSdkException e) {
                    throw formatLoadFailure(e, resourcePath, platform, libraryName);
                }
            }

            verifyAbi(resourcePath, platform, libraryName);
            loaded = true;
        }
    }

    static boolean loadFromClasspathResource(
            String resourcePath,
            String nativeFilename,
            InputStream resourceStream
    ) {
        Path tempFile = null;

        try {
            String suffix = determineLibraryExtension(nativeFilename);
            tempFile = Files.createTempFile(TEMP_PREFIX, suffix);
            try (OutputStream out = Files.newOutputStream(tempFile)) {
                resourceStream.transferTo(out);
            }

            FileCleanup.register(tempFile.toFile());

            try {
                loadLibraryByPath(tempFile.toAbsolutePath().toString());
            } catch (UnsatisfiedLinkError e) {
                throw new FeagiSdkException(
                        "Failed to load FEAGI native library from classpath resource '"
                                + resourcePath + "'. "
                                + "This looks like a classifier JAR mismatch or corrupt binary. "
                                + "Original error: " + e.getMessage(),
                        e
                );
            }

            return true;
        } catch (IOException e) {
            cleanupTempFile(tempFile);
            throw new FeagiSdkException(
                    "Failed to extract FEAGI native library from classpath resource '"
                            + resourcePath + "'.",
                    e
            );
        }
    }

    private static String determineLibraryExtension(String nativeFilename) {
        int index = nativeFilename.lastIndexOf('.');
        if (index < 0) {
            throw new IllegalArgumentException("Could not determine extension for native library: " + nativeFilename);
        }
        return nativeFilename.substring(index);
    }

    private static void verifyAbi(String resourcePath, String platform, String libraryName) {
        final int abi;
        try {
            abi = abiVersionProvider.getAsInt();
        } catch (UnsatisfiedLinkError e) {
            throw new FeagiSdkException(
                    "Loaded JNI library, but feagiAbiVersion() is missing. "
                            + "Attempted resource: '" + resourcePath + "'. "
                            + "Detected platform: '" + platform + "'. "
                            + "Loaded library: '" + libraryName + "'. "
                            + "Original error: " + e.getMessage(),
                    e
            );
        }

        if (abi != EXPECTED_ABI_VERSION) {
            throw new FeagiSdkException(
                    "FEAGI native ABI mismatch: expected "
                            + EXPECTED_ABI_VERSION + " but got " + abi + ". "
                            + "Attempted resource: '" + resourcePath + "'. "
                            + "Detected platform: '" + platform + "'."
            );
        }
    }

    private static void loadLibraryByName(String libraryName) {
        try {
            nameLoadHook.load(libraryName);
        } catch (UnsatisfiedLinkError e) {
            throw new FeagiSdkException(
                    "Failed to load FEAGI native library by name '" + libraryName + "'. "
                            + "Make sure the library is on java.library.path and its dependencies are on PATH.",
                    e
            );
        }
    }

    private static void loadLibraryByPath(String absolutePath) {
        pathLoadHook.load(absolutePath);
    }

    private static FeagiSdkException formatLoadFailure(
            FeagiSdkException e,
            String resourcePath,
            String platform,
            String libraryName
    ) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        String causeMessage = cause == null ? e.getMessage() : cause.getMessage();
        return new FeagiSdkException(
                "Failed to load FEAGI native library '" + libraryName + "'. "
                        + "Attempted classpath resource: '" + resourcePath + "'. "
                        + "Detected platform: '" + platform + "'. "
                        + "java.library.path=" + System.getProperty("java.library.path") + ". "
                        + "Tried to fall back to System.loadLibrary(" + libraryName + "), but it failed. "
                        + "Original error: " + causeMessage,
                cause
        );
    }

    static void cleanupTempFile(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
            // Best effort cleanup.
        }
    }

    private static void validateLibraryName(String libraryName) {
        if (libraryName == null || libraryName.isBlank()) {
            throw new IllegalArgumentException("libraryName must not be null/blank");
        }
    }

    static void resetForTests() {
        synchronized (FeagiNativeLibrary.class) {
            loaded = false;
            pathLoadHook = System::load;
            nameLoadHook = System::loadLibrary;
            abiVersionProvider = FeagiNativeBindings::feagiAbiVersion;
        }
    }

    private static class FileCleanup {
        private static void register(java.io.File file) {
            file.deleteOnExit();
            try {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        Files.deleteIfExists(file.toPath());
                    } catch (IOException ignored) {
                        // Best effort cleanup.
                    }
                }));
            } catch (IllegalStateException ignored) {
                // Ignore when JVM is shutting down.
            }
        }
    }

    static class PlatformDetector {
        static String detectOs(String osName) {
            if (osName == null || osName.isBlank()) {
                throw new IllegalArgumentException("osName must not be null/blank");
            }
            String normalized = osName.toLowerCase(Locale.ROOT);
            if (normalized.contains("linux")) {
                return "linux";
            }
            if (normalized.contains("mac") || normalized.contains("darwin")) {
                return "osx";
            }
            if (normalized.contains("win")) {
                return "windows";
            }
            throw new FeagiSdkException("Unsupported operating system: " + osName);
        }

        static String detectArch(String osArch) {
            if (osArch == null || osArch.isBlank()) {
                throw new IllegalArgumentException("osArch must not be null/blank");
            }
            String normalized = osArch.toLowerCase(Locale.ROOT);
            if ("amd64".equals(normalized) || "x86_64".equals(normalized)) {
                return "x86_64";
            }
            if ("aarch64".equals(normalized) || "arm64".equals(normalized)) {
                return "aarch64";
            }
            throw new FeagiSdkException("Unsupported architecture: " + osArch);
        }

        static String platformString(String osName, String osArch) {
            return detectOs(osName) + "-" + detectArch(osArch);
        }

        static String nativeLibraryName(String baseName, String os) {
            validateLibraryName(baseName);
            String normalizedOs = detectOs(os);
            if ("linux".equals(normalizedOs)) {
                return "lib" + baseName + ".so";
            }
            if ("osx".equals(normalizedOs)) {
                return "lib" + baseName + ".dylib";
            }
            if ("windows".equals(normalizedOs)) {
                return baseName + ".dll";
            }
            throw new FeagiSdkException("Unsupported operating system: " + os);
        }

        static String resourcePath(String baseName, String osName, String osArch) {
            return NATIVES_ROOT + "/" + platformString(osName, osArch) + "/" + nativeLibraryName(baseName, osName);
        }
    }
}
