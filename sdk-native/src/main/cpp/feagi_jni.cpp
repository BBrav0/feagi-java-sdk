#include <jni.h>
#include <cstdint>
#include "feagi_java_ffi.h"

#define PTR_TO_JLONG(ptr)      (static_cast<jlong>(reinterpret_cast<intptr_t>(ptr)))
#define JLONG_TO_PTR(type, jl) (reinterpret_cast<type*>(static_cast<intptr_t>(jl)))

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiAbiVersion(JNIEnv*, jclass) {
    return (jint)feagi_abi_version();
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiClientReceiveMotorBuffer(
        JNIEnv* env, jclass, jlong clientHandle,
        jlongArray outBufferHandle, jbooleanArray outHasData) {
    if (outBufferHandle == nullptr || outHasData == nullptr) {
        return static_cast<jint>(FEAGI_STATUS_NULL_POINTER);
    }

    FeagiByteBufferHandle* buf = nullptr;
    bool hasData = false;
    FeagiStatus status = feagi_client_receive_motor_buffer(
            JLONG_TO_PTR(FeagiAgentClientHandle, clientHandle), &buf, &hasData);

    if (status == FEAGI_STATUS_OK) {
        jlong handle = PTR_TO_JLONG(buf);
        jboolean jHasData = static_cast<jboolean>(hasData);
        env->SetLongArrayRegion(outBufferHandle, 0, 1, &handle);
        env->SetBooleanArrayRegion(outHasData, 0, 1, &jHasData);
        return static_cast<jint>(status);
    }

    // On non-OK, the ABI does not guarantee ownership/validity of out_buf.
    // Never free buf on this path to avoid invalid-free/double-free UB.
    jlong zero = 0L;
    jboolean jFalse = JNI_FALSE;
    env->SetLongArrayRegion(outBufferHandle, 0, 1, &zero);
    env->SetBooleanArrayRegion(outHasData, 0, 1, &jFalse);
    return static_cast<jint>(status);
}