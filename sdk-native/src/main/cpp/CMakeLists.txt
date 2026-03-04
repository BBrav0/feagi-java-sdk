cmake_minimum_required(VERSION 3.20)
project(feagi_java_ffi_jni LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(feagi_java_ffi_jni SHARED feagi_jni.cpp)

find_package(JNI REQUIRED)
target_include_directories(feagi_java_ffi_jni PRIVATE ${JNI_INCLUDE_DIRS})

# ---------------------------------------------------------------------------
# FEAGI_FFI_INCLUDE_DIR — path to the directory containing feagi_java_ffi.h
# Passed from Gradle via -DFEAGI_FFI_INCLUDE_DIR=...
# ---------------------------------------------------------------------------

message(STATUS "FEAGI_FFI_INCLUDE_DIR='${FEAGI_FFI_INCLUDE_DIR}'")
message(STATUS "FEAGI_FFI_LIB_DIR='${FEAGI_FFI_LIB_DIR}'")

if(NOT DEFINED FEAGI_FFI_INCLUDE_DIR OR "${FEAGI_FFI_INCLUDE_DIR}" STREQUAL "")
    message(FATAL_ERROR "FEAGI_FFI_INCLUDE_DIR is not set. Gradle must pass -DFEAGI_FFI_INCLUDE_DIR=...")
endif()

# Normalize path for Windows and verify the header actually exists.
file(TO_CMAKE_PATH "${FEAGI_FFI_INCLUDE_DIR}" _feagi_inc_dir)
if(NOT EXISTS "${_feagi_inc_dir}/feagi_java_ffi.h")
    message(FATAL_ERROR
        "feagi_java_ffi.h not found in FEAGI_FFI_INCLUDE_DIR='${_feagi_inc_dir}'. "
        "Point FEAGI_FFI_INCLUDE_DIR to the folder containing feagi_java_ffi.h")
endif()

target_include_directories(feagi_java_ffi_jni PRIVATE "${_feagi_inc_dir}")

# ---------------------------------------------------------------------------
# FEAGI_FFI_LIB_DIR — path to the directory containing the compiled Rust library.
# Passed from Gradle via -DFEAGI_FFI_LIB_DIR=...
#
# BREAKING CHANGE (intentional): This variable is now REQUIRED.
# Previously, omitting FEAGI_FFI_LIB_DIR silently skipped linking, which
# allowed building a JNI stub that would crash at runtime with
# UnsatisfiedLinkError. That silent failure mode was worse than a loud one.
#
# Migration: If you previously built without the FFI library (e.g. for IDE
# indexing only), run 'cargo build --release' in feagi-java-ffi first, then
# pass -DFEAGI_FFI_LIB_DIR=<path/to/target/release> to CMake (or let Gradle
# set it via the feagiNativeLibDir property in local.properties).
# ---------------------------------------------------------------------------

if(NOT DEFINED FEAGI_FFI_LIB_DIR OR "${FEAGI_FFI_LIB_DIR}" STREQUAL "")
    message(FATAL_ERROR
        "FEAGI_FFI_LIB_DIR is not set. "
        "Run 'cargo build --release' in feagi-java-ffi, then pass "
        "-DFEAGI_FFI_LIB_DIR=<path/to/feagi-java-ffi/target/release> to CMake. "
        "Gradle sets this automatically via the feagiNativeLibDir property.")
endif()

# Normalize backslashes — required for EXISTS/IS_DIRECTORY on Windows.
file(TO_CMAKE_PATH "${FEAGI_FFI_LIB_DIR}" _feagi_lib_dir)

if(NOT IS_DIRECTORY "${_feagi_lib_dir}")
    message(FATAL_ERROR
        "FEAGI_FFI_LIB_DIR='${_feagi_lib_dir}' is not a directory. "
        "Run 'cargo build --release' in feagi-java-ffi first.")
endif()

if(WIN32)
    # Pass the FULL ABSOLUTE PATH — never a bare name, never target_link_directories
    # on Windows. A bare name makes MSVC search its default lib path and pick the
    # wrong file (LNK1181 "cannot open input file 'feagi_java_ffi.lib'").
    set(_lib_dot_dll "${_feagi_lib_dir}/feagi_java_ffi.dll.lib")
    set(_lib_cdylib  "${_feagi_lib_dir}/feagi_java_ffi_dll.lib")
    set(_lib_plain   "${_feagi_lib_dir}/feagi_java_ffi.lib")

    message(STATUS "feagi_java_ffi: searching in ${_feagi_lib_dir}")

    if(EXISTS "${_lib_dot_dll}")
        message(STATUS "feagi_java_ffi: linking ${_lib_dot_dll}")
        target_link_libraries(feagi_java_ffi_jni PRIVATE "${_lib_dot_dll}")
    elseif(EXISTS "${_lib_cdylib}")
        message(STATUS "feagi_java_ffi: linking ${_lib_cdylib}")
        target_link_libraries(feagi_java_ffi_jni PRIVATE "${_lib_cdylib}")
    elseif(EXISTS "${_lib_plain}")
        message(STATUS "feagi_java_ffi: linking ${_lib_plain}")
        target_link_libraries(feagi_java_ffi_jni PRIVATE "${_lib_plain}")
    else()
        message(FATAL_ERROR
            "feagi_java_ffi import lib not found in '${_feagi_lib_dir}'. "
            "Expected one of: feagi_java_ffi.dll.lib, feagi_java_ffi_dll.lib, "
            "feagi_java_ffi.lib. Run 'cargo build --release' in feagi-java-ffi and retry.")
    endif()

else()
    # Linux / macOS
    target_link_directories(feagi_java_ffi_jni PRIVATE "${_feagi_lib_dir}")
    target_link_libraries(feagi_java_ffi_jni PRIVATE feagi_java_ffi)
endif()

if(MSVC)
    target_link_options(feagi_java_ffi_jni PRIVATE /IGNORE:4098)
endif()
