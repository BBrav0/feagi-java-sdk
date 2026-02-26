/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.nativeffi;

import io.feagi.sdk.core.FeagiSdkException;

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

    private static final int EXPECTED_ABI_VERSION = 1;

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
                System.loadLibrary(libraryName);
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
}

