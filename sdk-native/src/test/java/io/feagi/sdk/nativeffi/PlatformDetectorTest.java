package io.feagi.sdk.nativeffi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.feagi.sdk.core.FeagiSdkException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for platform detection and native naming logic.
 */
public class PlatformDetectorTest {

    @Test
    public void detectOsLinux() {
        assertEquals("linux", FeagiNativeLibrary.PlatformDetector.detectOs("Linux"));
        assertEquals("linux", FeagiNativeLibrary.PlatformDetector.detectOs("gnu/linux"));
        assertEquals("linux", FeagiNativeLibrary.PlatformDetector.detectOs("LINUX"));
    }

    @Test
    public void detectOsMac() {
        assertEquals("osx", FeagiNativeLibrary.PlatformDetector.detectOs("Mac OS X"));
        assertEquals("osx", FeagiNativeLibrary.PlatformDetector.detectOs("darwin"));
    }

    @Test
    public void detectOsWindows() {
        assertEquals("windows", FeagiNativeLibrary.PlatformDetector.detectOs("Windows 11"));
    }

    @Test
    public void detectOsUnsupported() {
        FeagiSdkException ex = assertThrows(FeagiSdkException.class, () -> FeagiNativeLibrary.PlatformDetector.detectOs("Solaris"));
        assertTrue(ex.getMessage().contains("Solaris"));
    }

    @Test
    public void detectArchX86_64() {
        assertEquals("x86_64", FeagiNativeLibrary.PlatformDetector.detectArch("amd64"));
        assertEquals("x86_64", FeagiNativeLibrary.PlatformDetector.detectArch("x86_64"));
    }

    @Test
    public void detectArchAarch64() {
        assertEquals("aarch64", FeagiNativeLibrary.PlatformDetector.detectArch("aarch64"));
        assertEquals("aarch64", FeagiNativeLibrary.PlatformDetector.detectArch("arm64"));
    }

    @Test
    public void detectArchUnsupported() {
        FeagiSdkException ex = assertThrows(FeagiSdkException.class, () -> FeagiNativeLibrary.PlatformDetector.detectArch("armv7"));
        assertTrue(ex.getMessage().contains("armv7"));
    }

    @Test
    public void platformStringAllPlatforms() {
        assertEquals("linux-x86_64", FeagiNativeLibrary.PlatformDetector.platformString("Linux", "amd64"));
        assertEquals("linux-aarch64", FeagiNativeLibrary.PlatformDetector.platformString("Linux", "aarch64"));
        assertEquals("osx-x86_64", FeagiNativeLibrary.PlatformDetector.platformString("Mac OS X", "amd64"));
        assertEquals("osx-aarch64", FeagiNativeLibrary.PlatformDetector.platformString("darwin", "arm64"));
        assertEquals("windows-x86_64", FeagiNativeLibrary.PlatformDetector.platformString("Windows 10", "x86_64"));
    }

    @Test
    public void nativeLibraryNameByPlatform() {
        assertEquals("libfeagi_java_ffi.so", FeagiNativeLibrary.PlatformDetector.nativeLibraryName("feagi_java_ffi", "Linux"));
        assertEquals("libfeagi_java_ffi.dylib", FeagiNativeLibrary.PlatformDetector.nativeLibraryName("feagi_java_ffi", "Darwin"));
        assertEquals("feagi_java_ffi.dll", FeagiNativeLibrary.PlatformDetector.nativeLibraryName("feagi_java_ffi", "Windows"));
    }

    @Test
    public void resourcePathForAllPlatforms() {
        assertEquals(
                "native/linux-x86_64/libfeagi_java_ffi.so",
                FeagiNativeLibrary.PlatformDetector.resourcePath("feagi_java_ffi", "Linux", "amd64")
        );
        assertEquals(
                "native/linux-aarch64/libfeagi_java_ffi.so",
                FeagiNativeLibrary.PlatformDetector.resourcePath("feagi_java_ffi", "Linux", "aarch64")
        );
        assertEquals(
                "native/osx-x86_64/libfeagi_java_ffi.dylib",
                FeagiNativeLibrary.PlatformDetector.resourcePath("feagi_java_ffi", "Mac OS X", "amd64")
        );
        assertEquals(
                "native/osx-aarch64/libfeagi_java_ffi.dylib",
                FeagiNativeLibrary.PlatformDetector.resourcePath("feagi_java_ffi", "darwin", "arm64")
        );
        assertEquals(
                "native/windows-x86_64/feagi_java_ffi.dll",
                FeagiNativeLibrary.PlatformDetector.resourcePath("feagi_java_ffi", "Windows", "x86_64")
        );
    }
}
