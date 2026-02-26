cmake_minimum_required(VERSION 3.20)
project(feagi_java_ffi_jni LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(feagi_java_ffi_jni SHARED feagi_jni.cpp)

find_package(JNI REQUIRED)
target_include_directories(feagi_java_ffi_jni PRIVATE ${JNI_INCLUDE_DIRS})

# Passed from Gradle via -DFEAGI_FFI_INCLUDE_DIR and -DFEAGI_FFI_LIB_DIR
target_include_directories(feagi_java_ffi_jni PRIVATE ${FEAGI_FFI_INCLUDE_DIR})

# ---------------------------------------------------------------------------
# Conditional linking against feagi_java_ffi
#
# Rust on Windows can produce any of these import lib names depending on
# the toolchain version and crate configuration:
#   feagi_java_ffi.dll.lib   <- actual output observed (dot-separated)
#   feagi_java_ffi_dll.lib   <- underscore variant
#   feagi_java_ffi.lib       <- plain name
# We check all three in order.
# ---------------------------------------------------------------------------

if(DEFINED FEAGI_FFI_LIB_DIR AND NOT "${FEAGI_FFI_LIB_DIR}" STREQUAL "")

    if(WIN32)
        # Normalise backslashes so cmake EXISTS works correctly on Windows
        file(TO_CMAKE_PATH "${FEAGI_FFI_LIB_DIR}" _feagi_lib_dir)

        set(_lib_dot_dll  "${_feagi_lib_dir}/feagi_java_ffi.dll.lib")
        set(_lib_cdylib   "${_feagi_lib_dir}/feagi_java_ffi_dll.lib")
        set(_lib_plain    "${_feagi_lib_dir}/feagi_java_ffi.lib")

        message(STATUS "feagi_java_ffi: searching in ${_feagi_lib_dir}")

        if(EXISTS "${_lib_dot_dll}")
            message(STATUS "feagi_java_ffi: found ${_lib_dot_dll} -- linking")
            target_link_libraries(feagi_java_ffi_jni PRIVATE "${_lib_dot_dll}")
        elseif(EXISTS "${_lib_cdylib}")
            message(STATUS "feagi_java_ffi: found ${_lib_cdylib} -- linking")
            target_link_libraries(feagi_java_ffi_jni PRIVATE "${_lib_cdylib}")
        elseif(EXISTS "${_lib_plain}")
            message(STATUS "feagi_java_ffi: found ${_lib_plain} -- linking")
            target_link_libraries(feagi_java_ffi_jni PRIVATE "${_lib_plain}")
        else()
            message(WARNING
                "feagi_java_ffi import lib not found in ${_feagi_lib_dir}. "
                "Run 'cargo build --release' in feagi-java-ffi and retry.")
        endif()

    else()
        # Linux / macOS
        target_link_directories(feagi_java_ffi_jni PRIVATE ${FEAGI_FFI_LIB_DIR})
        target_link_libraries(feagi_java_ffi_jni PRIVATE feagi_java_ffi)
    endif()

else()
    message(STATUS
        "FEAGI_FFI_LIB_DIR not set -- skipping link-time binding.")
endif()

if(MSVC)
    target_link_options(feagi_java_ffi_jni PRIVATE /IGNORE:4098)
endif()