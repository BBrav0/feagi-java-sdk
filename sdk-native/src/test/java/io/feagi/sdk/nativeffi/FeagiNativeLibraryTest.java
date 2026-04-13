/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.feagi.sdk.nativeffi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.feagi.sdk.core.FeagiSdkException;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for native library loading behavior.
 */
public class FeagiNativeLibraryTest {
    private static final String BASE_NAME = "feagi_java_ffi";
    private static final byte[] DUMMY_LIBRARY_BYTES = new byte[] {0x7f, 0x45, 0x4c, 0x46};

    private ClassLoader originalClassLoader;
    private final List<String> libraryLoadByPathCalls = new ArrayList<>();
    private final List<String> libraryLoadByNameCalls = new ArrayList<>();
    private final AtomicReference<String> nativePathCapture = new AtomicReference<>();
    private final AtomicReference<Throwable> abiExceptionCapture = new AtomicReference<>();

    @BeforeEach
    public void setUp() {
        originalClassLoader = Thread.currentThread().getContextClassLoader();
        FeagiNativeLibrary.resetForTests();
        FeagiNativeLibrary.pathLoadHook = (path) -> {
            libraryLoadByPathCalls.add(path);
            nativePathCapture.set(path);
        };
        FeagiNativeLibrary.nameLoadHook = (name) -> libraryLoadByNameCalls.add(name);
        FeagiNativeLibrary.abiVersionProvider = () -> {
            Throwable failure = abiExceptionCapture.get();
            if (failure != null) {
                if (failure instanceof UnsatisfiedLinkError) {
                    throw (UnsatisfiedLinkError) failure;
                }
                throw new RuntimeException(failure);
            }
            return FeagiNativeBindings.EXPECTED_ABI_VERSION;
        };
    }

    @AfterEach
    public void tearDown() {
        Thread.currentThread().setContextClassLoader(originalClassLoader);
        FeagiNativeLibrary.resetForTests();
    }

    @Test
    public void inputValidation_rejectsNullOrBlankLibraryName() {
        assertThrows(IllegalArgumentException.class, () -> FeagiNativeLibrary.loadAndVerify(null));
        assertThrows(IllegalArgumentException.class, () -> FeagiNativeLibrary.loadAndVerify(" "));
        assertThrows(IllegalArgumentException.class, () -> FeagiNativeLibrary.loadAndVerify(""));

        assertThrows(IllegalArgumentException.class, () -> FeagiNativeLibrary.load(""));
        assertThrows(IllegalArgumentException.class, () -> FeagiNativeLibrary.load("   "));
    }

    @Test
    public void classloaderExtractionLoadsAndRegistersTemporaryPath() {
        Thread.currentThread().setContextClassLoader(
                resourceClassLoader(
                        FeagiNativeLibrary.PlatformDetector.resourcePath(BASE_NAME, "Linux", "amd64"),
                        DUMMY_LIBRARY_BYTES,
                        null
                )
        );

        FeagiNativeLibrary.loadAndVerify(BASE_NAME, "Linux", "amd64");

        String loadedPath = nativePathCapture.get();
        assertTrue(loadedPath != null && !loadedPath.isBlank());
        assertTrue(loadedPath.startsWith(System.getProperty("java.io.tmpdir")));
        assertTrue(loadedPath.endsWith(".so"));
        assertTrue(Files.exists(Path.of(loadedPath)));
        assertEquals(1, libraryLoadByPathCalls.size());
    }

    @Test
    public void classloaderResourceMissingFallsBackToLoadLibrary() {
        Thread.currentThread().setContextClassLoader(resourceClassLoaderWithoutResource());

        FeagiNativeLibrary.loadAndVerify(BASE_NAME, "Linux", "amd64");

        assertEquals(0, libraryLoadByPathCalls.size());
        assertEquals(List.of("feagi_java_ffi"), libraryLoadByNameCalls);
    }

    @Test
    public void classloaderResourceMissingAndLoadLibraryFailsProducesDiagnostics() {
        Thread.currentThread().setContextClassLoader(resourceClassLoaderWithoutResource());
        FeagiNativeLibrary.nameLoadHook = (name) -> {
            throw new UnsatisfiedLinkError("missing library");
        };

        FeagiSdkException ex = assertThrows(
                FeagiSdkException.class,
                () -> FeagiNativeLibrary.loadAndVerify(BASE_NAME, "Linux", "amd64")
        );

        String expectedResource = FeagiNativeLibrary.PlatformDetector.resourcePath(BASE_NAME, "Linux", "amd64");
        assertInstanceOf(FeagiSdkException.class, ex);
        assertTrue(ex.getMessage().contains(expectedResource));
        assertTrue(ex.getMessage().contains("java.library.path"));
        assertTrue(ex.getCause() instanceof UnsatisfiedLinkError);
        assertEquals(0, libraryLoadByPathCalls.size());
    }

    @Test
    public void classloaderResourcePresentButBadBinaryMentionsClassifierMismatch() {
        Thread.currentThread().setContextClassLoader(
                resourceClassLoader(
                        FeagiNativeLibrary.PlatformDetector.resourcePath(BASE_NAME, "Linux", "amd64"),
                        DUMMY_LIBRARY_BYTES,
                        null
                )
        );
        FeagiNativeLibrary.pathLoadHook = (path) -> {
            libraryLoadByPathCalls.add(path);
            throw new UnsatisfiedLinkError("bad binary");
        };

        FeagiSdkException ex = assertThrows(
                FeagiSdkException.class,
                () -> FeagiNativeLibrary.loadAndVerify(BASE_NAME, "Linux", "amd64")
        );
        String expectedResource = FeagiNativeLibrary.PlatformDetector.resourcePath(BASE_NAME, "Linux", "amd64");
        assertTrue(ex.getMessage().contains(expectedResource));
        assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("mismatch"));
        assertEquals(1, libraryLoadByPathCalls.size());
        assertEquals(0, libraryLoadByNameCalls.size());
    }

    @Test
    public void abiMismatchIsReportedWhenVersionDoesNotMatch() {
        Thread.currentThread().setContextClassLoader(
                resourceClassLoader(
                        FeagiNativeLibrary.PlatformDetector.resourcePath(BASE_NAME, "Linux", "amd64"),
                        DUMMY_LIBRARY_BYTES,
                        null
                )
        );
        FeagiNativeLibrary.abiVersionProvider = () -> FeagiNativeBindings.EXPECTED_ABI_VERSION + 1;

        FeagiNativeLibraryException ex = assertThrowsAndCapture(() -> FeagiNativeLibrary.loadAndVerify(BASE_NAME, "Linux", "amd64"));
        assertTrue(ex.message.contains(
                "FEAGI native ABI mismatch: expected "
                        + FeagiNativeBindings.EXPECTED_ABI_VERSION
                        + " but got "
                        + (FeagiNativeBindings.EXPECTED_ABI_VERSION + 1)
        ));
    }

    @Test
    public void missingAbiSymbolIsWrappedAndGuided() {
        Thread.currentThread().setContextClassLoader(
                resourceClassLoader(
                        FeagiNativeLibrary.PlatformDetector.resourcePath(BASE_NAME, "Linux", "amd64"),
                        DUMMY_LIBRARY_BYTES,
                        null
                )
        );
        abiExceptionCapture.set(new UnsatisfiedLinkError("missing symbol"));

        FeagiSdkException ex = assertThrows(
                FeagiSdkException.class,
                () -> FeagiNativeLibrary.loadAndVerify(BASE_NAME, "Linux", "amd64")
        );

        assertTrue(ex.getMessage().contains("feagiAbiVersion"));
        assertTrue(ex.getCause() instanceof UnsatisfiedLinkError);
    }

    @Test
    public void classloaderInputStreamIsClosedAfterUse() {
        AtomicBoolean closed = new AtomicBoolean(false);
        Thread.currentThread().setContextClassLoader(
                resourceClassLoader(
                        FeagiNativeLibrary.PlatformDetector.resourcePath(BASE_NAME, "Linux", "amd64"),
                        DUMMY_LIBRARY_BYTES,
                        closed
                )
        );
        FeagiNativeLibrary.pathLoadHook = (path) -> {
            throw new UnsatisfiedLinkError("simulate corrupt");
        };

        assertThrows(
                FeagiSdkException.class,
                () -> FeagiNativeLibrary.loadAndVerify(BASE_NAME, "Linux", "amd64")
        );
        assertTrue(closed.get());
    }

    @Test
    public void ioErrorDuringExtractionIsWrappedWithChain() {
        AtomicBoolean closed = new AtomicBoolean(false);
        Thread.currentThread().setContextClassLoader(
                resourceClassLoaderThrowsOnRead(
                        FeagiNativeLibrary.PlatformDetector.resourcePath(BASE_NAME, "Linux", "amd64"),
                        closed
                )
        );

        FeagiSdkException ex = assertThrows(
                FeagiSdkException.class,
                () -> FeagiNativeLibrary.loadAndVerify(BASE_NAME, "Linux", "amd64")
        );

        assertTrue(closed.get());
        assertTrue(ex.getMessage().contains("extract"));
        assertInstanceOf(FeagiSdkException.class, ex);
    }

    @Test
    public void loadFromClasspathCreatesUniqueTemporaryFiles() {
        Thread.currentThread().setContextClassLoader(
                resourceClassLoader(
                        FeagiNativeLibrary.PlatformDetector.resourcePath(BASE_NAME, "Linux", "amd64"),
                        DUMMY_LIBRARY_BYTES,
                        null
                )
        );
        FeagiNativeLibrary.pathLoadHook = (path) -> {
            libraryLoadByPathCalls.add(path);
            throw new UnsatisfiedLinkError("simulate load failure");
        };

        String firstPath = "";
        String secondPath = "";

        assertThrows(
                FeagiSdkException.class,
                () -> {
                    FeagiNativeLibrary.loadAndVerify(BASE_NAME, "Linux", "amd64");
                }
        );
        firstPath = libraryLoadByPathCalls.get(0);
        FeagiNativeLibrary.resetForTests();
        FeagiNativeLibrary.pathLoadHook = (path) -> {
            libraryLoadByPathCalls.add(path);
            throw new UnsatisfiedLinkError("simulate load failure");
        };
        assertThrows(
                FeagiSdkException.class,
                () -> {
                    FeagiNativeLibrary.loadAndVerify(BASE_NAME, "Linux", "amd64");
                }
        );
        secondPath = libraryLoadByPathCalls.get(1);

        assertFalse(firstPath.isBlank());
        assertFalse(secondPath.isBlank());
        assertTrue(firstPath.endsWith(".so"));
        assertTrue(secondPath.endsWith(".so"));
        assertTrue(Files.exists(Path.of(firstPath)));
        assertTrue(Files.exists(Path.of(secondPath)));
        assertTrue(firstPath.startsWith(System.getProperty("java.io.tmpdir")));
        assertTrue(secondPath.startsWith(System.getProperty("java.io.tmpdir")));
        assertTrue(!Objects.equals(firstPath, secondPath), "Expected unique temporary names");
    }

    @Test
    public void classLoadingIsThreadSafe() throws InterruptedException, ExecutionException {
        Thread.currentThread().setContextClassLoader(
                resourceClassLoader(
                        FeagiNativeLibrary.PlatformDetector.resourcePath(BASE_NAME, "Linux", "amd64"),
                        DUMMY_LIBRARY_BYTES,
                        null
                )
        );
        FeagiNativeLibrary.abiVersionProvider = () -> FeagiNativeBindings.EXPECTED_ABI_VERSION;
        AtomicInteger threadCount = new AtomicInteger(8);
        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit((Callable<Boolean>) () -> {
                    ready.await();
                    FeagiNativeLibrary.loadAndVerify(BASE_NAME, "Linux", "amd64");
                    return true;
                }));
            }
            ready.countDown();
            int successCount = 0;
            int stateCount = 0;
            for (Future<Boolean> future : futures) {
                try {
                    if (future.get()) {
                        successCount++;
                    }
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof IllegalStateException) {
                        stateCount++;
                    } else {
                        throw e;
                    }
                    successCount += 0;
                }
            }
            assertEquals(1, successCount);
            assertEquals(7, stateCount);
            assertEquals(1, libraryLoadByPathCalls.size());
        } finally {
            pool.shutdownNow();
            FeagiNativeLibrary.resetForTests();
        }
    }

    @Test
    public void utilityClassIsFinalAndHasPrivateConstructor() {
        assertTrue(Modifier.isFinal(FeagiNativeLibrary.class.getModifiers()));
        var constructors = FeagiNativeLibrary.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
    }

    private ClassLoader resourceClassLoader(String resourcePath, byte[] bytes, AtomicBoolean closeTracker) {
        return new ClassLoader() {
            @Override
            public InputStream getResourceAsStream(String name) {
                if (!resourcePath.equals(name)) {
                    return null;
                }
                return new TrackingInputStream(bytes, closeTracker);
            }
        };
    }

    private ClassLoader resourceClassLoaderWithoutResource() {
        return new ClassLoader() {
            @Override
            public InputStream getResourceAsStream(String name) {
                return null;
            }
        };
    }

    private ClassLoader resourceClassLoaderThrowsOnRead(String resourcePath, AtomicBoolean closeTracker) {
        return new ClassLoader() {
            @Override
            public InputStream getResourceAsStream(String name) {
                if (!resourcePath.equals(name)) {
                    return null;
                }
                return new InputStream() {
                    private boolean closed;

                    @Override
                    public int read() throws IOException {
                        throw new IOException("read failure");
                    }

                    @Override
                    public void close() {
                        closed = true;
                        if (closeTracker != null) {
                            closeTracker.set(true);
                        }
                    }
                };
            }
        };
    }

    private static final class TrackingInputStream extends FilterInputStream {
        private final AtomicBoolean closed;

        TrackingInputStream(byte[] bytes, AtomicBoolean closed) {
            super(new ByteArrayInputStream(bytes));
            this.closed = closed == null ? new AtomicBoolean(false) : closed;
        }

        @Override
        public void close() throws IOException {
            closed.set(true);
            super.close();
        }
    }

    private static final class FeagiNativeLibraryException extends RuntimeException {
        private final String message;
    private final Throwable cause;

        FeagiNativeLibraryException() {
            message = null;
            cause = null;
        }

        FeagiNativeLibraryException(String message, Throwable cause) {
            super(message, cause);
            this.message = message;
            this.cause = cause;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }

    private FeagiNativeLibraryException assertThrowsAndCapture(Executable action) {
        try {
            action.execute();
            return new FeagiNativeLibraryException("Expected " + FeagiSdkException.class.getSimpleName(), null);
        } catch (FeagiSdkException ex) {
            return new FeagiNativeLibraryException(ex.getMessage(), ex.getCause());
        }
    }

    @FunctionalInterface
    private interface Executable {
        void execute();
    }
}
