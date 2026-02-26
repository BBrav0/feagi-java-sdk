#include <jni.h>
#include "feagi_java_ffi.h"

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiAbiVersion(JNIEnv*, jclass) {
    return (jint)feagi_abi_version();
}