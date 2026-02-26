/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.nativeffi;

/**
 * Minimal smoke test: load the JNI bridge and verify the ABI handshake.
 *
 * <p>Run via Gradle:
 * <pre>
 *   ./gradlew :sdk-native:nativeSmokeTest
 * </pre>
 */
public final class NativeSmokeTest {

    public static void main(String[] args) {
        // loadAndVerify: loads the DLL, calls feagiAbiVersion(), and throws
        // FeagiSdkException with a clear message on any mismatch.
        FeagiNativeLibrary.loadAndVerify("feagi_java_ffi_jni");

        System.out.println("OK: ABI handshake passed (version "
                + FeagiNativeBindings.feagiAbiVersion() + ")");
    }
}