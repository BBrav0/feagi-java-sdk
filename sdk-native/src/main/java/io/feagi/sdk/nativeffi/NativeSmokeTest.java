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
        try {
            String lib = (args.length > 0 && !args[0].isBlank()) ? args[0] : "feagi_java_ffi_jni";
            FeagiNativeLibrary.loadAndVerify(lib);

            System.out.println("OK: ABI handshake passed (version "
                    + FeagiNativeBindings.feagiAbiVersion() + ")");
        } catch (Throwable t) {
            System.err.println("FAIL: ABI handshake failed: " + t.getMessage());
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
