/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.nativeffi;

import io.feagi.sdk.core.FeagiSdkException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OS/arch detection, platform composition, library filename resolution,
 * and classpath extraction logic in {@link FeagiNativeLibrary}.
 */
class FeagiNativeLibraryLoaderTest {

    // =========================================================================
    // OS Detection tests
    // =========================================================================

    @Test
    void detectOs_linux_returnsLinux() {
        assertEquals("linux", FeagiNativeLibrary.detectOs("Linux"));
    }

    @Test
    void detectOs_linuxLowercase_returnsLinux() {
        assertEquals("linux", FeagiNativeLibrary.detectOs("linux"));
    }

    @Test
    void detectOs_macOsX_returnsOsx() {
        assertEquals("osx", FeagiNativeLibrary.detectOs("Mac OS X"));
    }

    @Test
    void detectOs_darwin_returnsOsx() {
        assertEquals("osx", FeagiNativeLibrary.detectOs("Darwin"));
    }

    @Test
    void detectOs_macWithSpaces_returnsOsx() {
        assertEquals("osx", FeagiNativeLibrary.detectOs("mac os x 10.15"));
    }

    @Test
    void detectOs_windows_returnsWindows() {
        assertEquals("windows", FeagiNativeLibrary.detectOs("Windows 10"));
    }

    @Test
    void detectOs_windows11_returnsWindows() {
        assertEquals("windows", FeagiNativeLibrary.detectOs("Windows 11"));
    }

    @Test
    void detectOs_windowsServer_returnsWindows() {
        assertEquals("windows", FeagiNativeLibrary.detectOs("Windows Server 2019"));
    }

    @Test
    void detectOs_unknown_throwsFeagiSdkException() {
        FeagiSdkException ex = assertThrows(FeagiSdkException.class,
                () -> FeagiNativeLibrary.detectOs("SunOS"));
        assertTrue(ex.getMessage().contains("SunOS"),
                "Exception message should contain the unrecognized OS name");
    }

    @Test
    void detectOs_null_throwsFeagiSdkException() {
        assertThrows(FeagiSdkException.class,
                () -> FeagiNativeLibrary.detectOs(null));
    }

    @Test
    void detectOs_emptyString_throwsFeagiSdkException() {
        assertThrows(FeagiSdkException.class,
                () -> FeagiNativeLibrary.detectOs(""));
    }

    @Test
    void detectOs_freebsd_throwsFeagiSdkException() {
        FeagiSdkException ex = assertThrows(FeagiSdkException.class,
                () -> FeagiNativeLibrary.detectOs("FreeBSD"));
        assertTrue(ex.getMessage().contains("FreeBSD"),
                "Exception message should contain the unrecognized OS name");
    }

    // =========================================================================
    // Architecture Detection tests
    // =========================================================================

    @Test
    void detectArch_amd64_returnsX86_64() {
        assertEquals("x86_64", FeagiNativeLibrary.detectArch("amd64"));
    }

    @Test
    void detectArch_x86_64_returnsX86_64() {
        assertEquals("x86_64", FeagiNativeLibrary.detectArch("x86_64"));
    }

    @Test
    void detectArch_aarch64_returnsAarch64() {
        assertEquals("aarch64", FeagiNativeLibrary.detectArch("aarch64"));
    }

    @Test
    void detectArch_arm64_returnsAarch64() {
        assertEquals("aarch64", FeagiNativeLibrary.detectArch("arm64"));
    }

    @Test
    void detectArch_AMD64Uppercase_returnsX86_64() {
        assertEquals("x86_64", FeagiNativeLibrary.detectArch("AMD64"));
    }

    @Test
    void detectArch_AARCH64Uppercase_returnsAarch64() {
        assertEquals("aarch64", FeagiNativeLibrary.detectArch("AARCH64"));
    }

    @Test
    void detectArch_unknown_throwsFeagiSdkException() {
        FeagiSdkException ex = assertThrows(FeagiSdkException.class,
                () -> FeagiNativeLibrary.detectArch("i386"));
        assertTrue(ex.getMessage().contains("i386"),
                "Exception message should contain the unrecognized arch name");
    }

    @Test
    void detectArch_null_throwsFeagiSdkException() {
        assertThrows(FeagiSdkException.class,
                () -> FeagiNativeLibrary.detectArch(null));
    }

    @Test
    void detectArch_ppc64_throwsFeagiSdkException() {
        FeagiSdkException ex = assertThrows(FeagiSdkException.class,
                () -> FeagiNativeLibrary.detectArch("ppc64"));
        assertTrue(ex.getMessage().contains("ppc64"),
                "Exception message should contain the unrecognized arch name");
    }

    // =========================================================================
    // Platform String Composition tests
    // =========================================================================

    @Test
    void composePlatform_linuxX86_64_returnsCorrectString() {
        assertEquals("linux-x86_64", FeagiNativeLibrary.composePlatform("linux", "x86_64"));
    }

    @Test
    void composePlatform_linuxAarch64_returnsCorrectString() {
        assertEquals("linux-aarch64", FeagiNativeLibrary.composePlatform("linux", "aarch64"));
    }

    @Test
    void composePlatform_osxX86_64_returnsCorrectString() {
        assertEquals("osx-x86_64", FeagiNativeLibrary.composePlatform("osx", "x86_64"));
    }

    @Test
    void composePlatform_osxAarch64_returnsCorrectString() {
        assertEquals("osx-aarch64", FeagiNativeLibrary.composePlatform("osx", "aarch64"));
    }

    @Test
    void composePlatform_windowsX86_64_returnsCorrectString() {
        assertEquals("windows-x86_64", FeagiNativeLibrary.composePlatform("windows", "x86_64"));
    }

    // =========================================================================
    // Library Filename Resolution tests
    // =========================================================================

    @Test
    void resolveLibraryFilename_linux_returnsSo() {
        assertEquals("libfeagi_java_ffi.so", FeagiNativeLibrary.resolveLibraryFilename("linux"));
    }

    @Test
    void resolveLibraryFilename_osx_returnsDylib() {
        assertEquals("libfeagi_java_ffi.dylib", FeagiNativeLibrary.resolveLibraryFilename("osx"));
    }

    @Test
    void resolveLibraryFilename_windows_returnsDll() {
        assertEquals("feagi_java_ffi.dll", FeagiNativeLibrary.resolveLibraryFilename("windows"));
    }

    @Test
    void resolveLibraryFilename_unknown_throwsFeagiSdkException() {
        assertThrows(FeagiSdkException.class,
                () -> FeagiNativeLibrary.resolveLibraryFilename("unknown_os"));
    }

    // =========================================================================
    // Classpath Resource Path Format tests
    // =========================================================================

    @Test
    void classpathResourcePath_linuxX86_64_correctFormat() {
        String platform = FeagiNativeLibrary.composePlatform("linux", "x86_64");
        String libFilename = FeagiNativeLibrary.resolveLibraryFilename("linux");
        String resourcePath = "native/" + platform + "/" + libFilename;
        assertEquals("native/linux-x86_64/libfeagi_java_ffi.so", resourcePath);
    }

    @Test
    void classpathResourcePath_osxAarch64_correctFormat() {
        String platform = FeagiNativeLibrary.composePlatform("osx", "aarch64");
        String libFilename = FeagiNativeLibrary.resolveLibraryFilename("osx");
        String resourcePath = "native/" + platform + "/" + libFilename;
        assertEquals("native/osx-aarch64/libfeagi_java_ffi.dylib", resourcePath);
    }

    @Test
    void classpathResourcePath_windowsX86_64_correctFormat() {
        String platform = FeagiNativeLibrary.composePlatform("windows", "x86_64");
        String libFilename = FeagiNativeLibrary.resolveLibraryFilename("windows");
        String resourcePath = "native/" + platform + "/" + libFilename;
        assertEquals("native/windows-x86_64/feagi_java_ffi.dll", resourcePath);
    }

    // =========================================================================
    // Classpath Extraction tests
    // =========================================================================

    @Test
    void extractAndLoad_resourceNotFound_throwsFeagiSdkExceptionWithExpectedPath() {
        FeagiSdkException ex = assertThrows(FeagiSdkException.class,
                () -> FeagiNativeLibrary.extractAndLoad("nonexistent-platform", "libfeagi_java_ffi.so"));
        String msg = ex.getMessage();
        assertTrue(msg.contains("native/nonexistent-platform/libfeagi_java_ffi.so"),
                "Exception should mention the expected classpath resource path. Got: " + msg);
        assertTrue(msg.contains("nonexistent-platform"),
                "Exception should mention the platform name. Got: " + msg);
    }

    @Test
    void extractAndLoad_osxResourceNotFound_throwsFeagiSdkExceptionWithPlatform() {
        // Use a platform+filename that is definitively absent from test classpath
        FeagiSdkException ex = assertThrows(FeagiSdkException.class,
                () -> FeagiNativeLibrary.extractAndLoad("osx-missing-platform", "libfeagi_java_ffi.dylib"));
        String msg = ex.getMessage();
        assertTrue(msg.contains("native/osx-missing-platform/libfeagi_java_ffi.dylib"),
                "Exception should mention the expected classpath resource path. Got: " + msg);
    }

    // =========================================================================
    // Existing load() validation tests — preserved behavior
    // =========================================================================

    @Test
    void load_nullLibraryName_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> FeagiNativeLibrary.load(null));
    }

    @Test
    void load_emptyLibraryName_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> FeagiNativeLibrary.load(""));
    }
}
