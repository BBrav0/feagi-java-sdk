#include <jni.h>
#include "feagi_java_ffi.h"

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiAbiVersion(JNIEnv*, jclass) {
    return (jint)feagi_abi_version();
}

// Copies bytes out of a FeagiByteBufferHandle into a new Java byte array.
// Called by NativeFeagiAgentClient.pollMotorBytes() to materialise the motor frame.
JNIEXPORT jbyteArray JNICALL
Java_io_feagi_sdk_nativeffi_NativeFeagiAgentClient_copyNativeBuffer(
        JNIEnv* env, jclass /*cls*/,
        jlong   bufHandle,
        jint    length) {

    const FeagiByteBufferHandle* buf = JLONG_TO_PTR(const FeagiByteBufferHandle, bufHandle);
    if (buf == nullptr || length <= 0) {
        return nullptr;
    }

    const uint8_t* ptr = feagi_buffer_ptr(buf);
    if (ptr == nullptr) {
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(length);
    if (result == nullptr) {
        return nullptr;   // OutOfMemoryError already thrown by JVM
    }

    env->SetByteArrayRegion(
        result, 0, length,
        reinterpret_cast<const jbyte*>(ptr));

    return result;
}