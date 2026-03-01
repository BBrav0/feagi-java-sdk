/*
 * Copyright 2026 Neuraville Inc.
 * SPDX-License-Identifier: Apache-2.0
 *
 * JNI bridge: Java → feagi_java_ffi C ABI
 *
 * Naming convention:
 *   Java class io.feagi.sdk.nativeffi.FeagiNativeBindings
 *   → Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_<methodName>
 *
 *   Java class io.feagi.sdk.nativeffi.NativeFeagiAgentClient
 *   → Java_io_feagi_sdk_nativeffi_NativeFeagiAgentClient_<methodName>
 */

#include <jni.h>
#include <cstdint>
#include <cstring>
#include "feagi_java_ffi.h"

// ── Pointer helpers ────────────────────────────────────────────────────────────
#define PTR_TO_JLONG(ptr)      (static_cast<jlong>(reinterpret_cast<intptr_t>(ptr)))
#define JLONG_TO_PTR(type, jl) (reinterpret_cast<type*>(static_cast<intptr_t>(jl)))

// ── Null-safe jstring → UTF-8 ─────────────────────────────────────────────────
static const char* jstr_get(JNIEnv* env, jstring s) {
    if (!s) return nullptr;
    const char* c = env->GetStringUTFChars(s, nullptr);
    return (env->ExceptionCheck()) ? nullptr : c;
}
static void jstr_release(JNIEnv* env, jstring s, const char* c) {
    if (s && c) env->ReleaseStringUTFChars(s, c);
}

// ── ABI / version ─────────────────────────────────────────────────────────────

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiAbiVersion(JNIEnv*, jclass) {
    return static_cast<jint>(feagi_abi_version());
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiLibraryVersion(JNIEnv* env, jclass) {
    char* ver = feagi_library_version_alloc();
    if (!ver) return nullptr;
    jstring result = env->NewStringUTF(ver);
    feagi_string_free(ver);
    return result;
}

// ── Error reporting ───────────────────────────────────────────────────────────

extern "C" JNIEXPORT jstring JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiLastErrorMessage(JNIEnv* env, jclass) {
    char* msg = feagi_last_error_message_alloc();
    if (!msg) return nullptr;
    jstring result = env->NewStringUTF(msg);
    feagi_string_free(msg);
    return result;
}

// ── Config lifecycle ──────────────────────────────────────────────────────────

extern "C" JNIEXPORT jlong JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigNew(
        JNIEnv* env, jclass, jstring agentId, jint agentType) {
    const char* id = jstr_get(env, agentId);
    FeagiAgentConfigHandle* h = feagi_config_new(id, static_cast<FeagiAgentType>(agentType));
    jstr_release(env, agentId, id);
    return PTR_TO_JLONG(h);
}

extern "C" JNIEXPORT void JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigFree(
        JNIEnv*, jclass, jlong h) {
    feagi_config_free(JLONG_TO_PTR(FeagiAgentConfigHandle, h));
}

// ── Endpoint setters ──────────────────────────────────────────────────────────

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetRegistrationEndpoint(
        JNIEnv* env, jclass, jlong h, jstring ep) {
    const char* s = jstr_get(env, ep);
    FeagiStatus r = feagi_config_set_registration_endpoint(JLONG_TO_PTR(FeagiAgentConfigHandle, h), s);
    jstr_release(env, ep, s);
    return static_cast<jint>(r);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetSensoryEndpoint(
        JNIEnv* env, jclass, jlong h, jstring ep) {
    const char* s = jstr_get(env, ep);
    FeagiStatus r = feagi_config_set_sensory_endpoint(JLONG_TO_PTR(FeagiAgentConfigHandle, h), s);
    jstr_release(env, ep, s);
    return static_cast<jint>(r);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetMotorEndpoint(
        JNIEnv* env, jclass, jlong h, jstring ep) {
    const char* s = jstr_get(env, ep);
    FeagiStatus r = feagi_config_set_motor_endpoint(JLONG_TO_PTR(FeagiAgentConfigHandle, h), s);
    jstr_release(env, ep, s);
    return static_cast<jint>(r);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetVisualizationEndpoint(
        JNIEnv* env, jclass, jlong h, jstring ep) {
    const char* s = jstr_get(env, ep);
    FeagiStatus r = feagi_config_set_visualization_endpoint(JLONG_TO_PTR(FeagiAgentConfigHandle, h), s);
    jstr_release(env, ep, s);
    return static_cast<jint>(r);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetControlEndpoint(
        JNIEnv* env, jclass, jlong h, jstring ep) {
    const char* s = jstr_get(env, ep);
    FeagiStatus r = feagi_config_set_control_endpoint(JLONG_TO_PTR(FeagiAgentConfigHandle, h), s);
    jstr_release(env, ep, s);
    return static_cast<jint>(r);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetFeagiEndpoints(
        JNIEnv* env, jclass, jlong h, jstring host,
        jint regPort, jint sensPort, jint motorPort, jint vizPort, jint ctrlPort) {
    // Validate port range before uint16_t cast
    auto valid = [](jint p) { return p >= 1 && p <= 65535; };
    if (!valid(regPort) || !valid(sensPort) || !valid(motorPort) ||
        !valid(vizPort) || !valid(ctrlPort)) {
        return static_cast<jint>(FEAGI_STATUS_INVALID_ARGUMENT);
    }
    const char* hs = jstr_get(env, host);
    FeagiStatus r = feagi_config_set_feagi_endpoints(
            JLONG_TO_PTR(FeagiAgentConfigHandle, h), hs,
            static_cast<uint16_t>(regPort),
            static_cast<uint16_t>(sensPort),
            static_cast<uint16_t>(motorPort),
            static_cast<uint16_t>(vizPort),
            static_cast<uint16_t>(ctrlPort));
    jstr_release(env, host, hs);
    return static_cast<jint>(r);
}

// ── Timing / retry ────────────────────────────────────────────────────────────

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetHeartbeatIntervalSeconds(
        JNIEnv*, jclass, jlong h, jdouble secs) {
    // C ABI function is feagi_config_set_heartbeat_interval_s (not _seconds)
    return static_cast<jint>(feagi_config_set_heartbeat_interval_s(
            JLONG_TO_PTR(FeagiAgentConfigHandle, h), static_cast<double>(secs)));
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetConnectionTimeoutMs(
        JNIEnv*, jclass, jlong h, jlong ms) {
    return static_cast<jint>(feagi_config_set_connection_timeout_ms(
            JLONG_TO_PTR(FeagiAgentConfigHandle, h), static_cast<uint64_t>(ms)));
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetRegistrationRetries(
        JNIEnv*, jclass, jlong h, jint retries) {
    return static_cast<jint>(feagi_config_set_registration_retries(
            JLONG_TO_PTR(FeagiAgentConfigHandle, h), static_cast<uint32_t>(retries)));
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetRetryBackoffMs(
        JNIEnv*, jclass, jlong h, jlong ms) {
    return static_cast<jint>(feagi_config_set_retry_backoff_ms(
            JLONG_TO_PTR(FeagiAgentConfigHandle, h), static_cast<uint64_t>(ms)));
}

// ── Agent descriptor / auth ───────────────────────────────────────────────────

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetAgentDescriptor(
        JNIEnv* env, jclass, jlong h,
        jstring manufacturer, jstring agentName, jint agentVersion) {
    const char* mfr  = jstr_get(env, manufacturer);
    const char* name = jstr_get(env, agentName);
    FeagiStatus r = feagi_config_set_agent_descriptor(
            JLONG_TO_PTR(FeagiAgentConfigHandle, h),
            mfr, name, static_cast<uint32_t>(agentVersion));
    jstr_release(env, manufacturer, mfr);
    jstr_release(env, agentName, name);
    return static_cast<jint>(r);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_nativeConfigSetAuthTokenBase64(
        JNIEnv* env, jclass, jlong h, jstring token) {
    const char* t = jstr_get(env, token);
    FeagiStatus r = feagi_config_set_auth_token_base64(
            JLONG_TO_PTR(FeagiAgentConfigHandle, h), t);
    jstr_release(env, token, t);
    return static_cast<jint>(r);
}

// ── Sensory socket ────────────────────────────────────────────────────────────

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetSensorySocketConfig(
        JNIEnv*, jclass, jlong h, jint sendHwm, jint lingerMs, jboolean immediate) {
    return static_cast<jint>(feagi_config_set_sensory_socket_config(
            JLONG_TO_PTR(FeagiAgentConfigHandle, h),
            static_cast<int32_t>(sendHwm),
            static_cast<int32_t>(lingerMs),
            static_cast<bool>(immediate)));
}

// ── Capabilities ──────────────────────────────────────────────────────────────

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetSensoryCapability(
        JNIEnv* env, jclass, jlong h, jdouble rateHz, jstring shmPath) {
    const char* shm = jstr_get(env, shmPath);
    FeagiStatus r = feagi_config_set_sensory_capability(
            JLONG_TO_PTR(FeagiAgentConfigHandle, h), static_cast<double>(rateHz), shm);
    jstr_release(env, shmPath, shm);
    return static_cast<jint>(r);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetVisionCapability(
        JNIEnv* env, jclass, jlong h,
        jstring modality, jlong width, jlong height, jlong channels,
        jstring targetCorticalArea) {
    const char* mod  = jstr_get(env, modality);
    const char* area = jstr_get(env, targetCorticalArea);
    FeagiStatus r = feagi_config_set_vision_capability(
            JLONG_TO_PTR(FeagiAgentConfigHandle, h),
            mod,
            static_cast<size_t>(width),
            static_cast<size_t>(height),
            static_cast<size_t>(channels),
            area);
    jstr_release(env, modality, mod);
    jstr_release(env, targetCorticalArea, area);
    return static_cast<jint>(r);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetVisionUnit(
        JNIEnv* env, jclass, jlong h,
        jstring modality, jlong width, jlong height, jlong channels,
        jint unit, jint group) {
    const char* mod = jstr_get(env, modality);
    FeagiStatus r = feagi_config_set_vision_unit(
            JLONG_TO_PTR(FeagiAgentConfigHandle, h),
            mod,
            static_cast<size_t>(width),
            static_cast<size_t>(height),
            static_cast<size_t>(channels),
            static_cast<FeagiSensoryUnit>(unit),
            static_cast<uint8_t>(group));
    jstr_release(env, modality, mod);
    return static_cast<jint>(r);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetMotorCapability(
        JNIEnv* env, jclass, jlong h,
        jstring modality, jlong outputCount, jstring areasJson) {
    const char* mod  = jstr_get(env, modality);
    const char* json = jstr_get(env, areasJson);
    FeagiStatus r = feagi_config_set_motor_capability(
            JLONG_TO_PTR(FeagiAgentConfigHandle, h),
            mod, static_cast<size_t>(outputCount), json);
    jstr_release(env, modality, mod);
    jstr_release(env, areasJson, json);
    return static_cast<jint>(r);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetMotorUnit(
        JNIEnv* env, jclass, jlong h,
        jstring modality, jlong outputCount, jint unit, jint group) {
    const char* mod = jstr_get(env, modality);
    FeagiStatus r = feagi_config_set_motor_unit(
            JLONG_TO_PTR(FeagiAgentConfigHandle, h),
            mod,
            static_cast<size_t>(outputCount),
            static_cast<FeagiMotorUnit>(unit),
            static_cast<uint8_t>(group));
    jstr_release(env, modality, mod);
    return static_cast<jint>(r);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetMotorUnitsJson(
        JNIEnv* env, jclass, jlong h,
        jstring modality, jlong outputCount, jstring unitsJson) {
    const char* mod  = jstr_get(env, modality);
    const char* json = jstr_get(env, unitsJson);
    FeagiStatus r = feagi_config_set_motor_units_json(
            JLONG_TO_PTR(FeagiAgentConfigHandle, h),
            mod, static_cast<size_t>(outputCount), json);
    jstr_release(env, modality, mod);
    jstr_release(env, unitsJson, json);
    return static_cast<jint>(r);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetVisualizationCapability(
        JNIEnv* env, jclass, jlong h,
        jstring vizType,
        jboolean hasRes, jlong resW, jlong resH,
        jboolean hasHz, jdouble hz,
        jboolean bridgeProxy) {
    const char* t = jstr_get(env, vizType);
    FeagiStatus r = feagi_config_set_visualization_capability(
            JLONG_TO_PTR(FeagiAgentConfigHandle, h),
            t,
            static_cast<bool>(hasRes),
            static_cast<size_t>(resW),
            static_cast<size_t>(resH),
            static_cast<bool>(hasHz),
            static_cast<double>(hz),
            static_cast<bool>(bridgeProxy));
    jstr_release(env, vizType, t);
    return static_cast<jint>(r);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetCustomCapabilityJson(
        JNIEnv* env, jclass, jlong h, jstring key, jstring jsonVal) {
    const char* k = jstr_get(env, key);
    const char* v = jstr_get(env, jsonVal);
    FeagiStatus r = feagi_config_set_custom_capability_json(
            JLONG_TO_PTR(FeagiAgentConfigHandle, h), k, v);
    jstr_release(env, key, k);
    jstr_release(env, jsonVal, v);
    return static_cast<jint>(r);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigValidate(
        JNIEnv*, jclass, jlong h) {
    return static_cast<jint>(feagi_config_validate(
            JLONG_TO_PTR(FeagiAgentConfigHandle, h)));
}

// ── Client lifecycle ──────────────────────────────────────────────────────────

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiClientNew(
        JNIEnv* env, jclass, jlong cfgHandle, jlongArray outClientHandle) {
    FeagiAgentClientHandle* client = nullptr;
    FeagiStatus r = feagi_client_new(
            JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle), &client);
    jlong jl = PTR_TO_JLONG(client);
    env->SetLongArrayRegion(outClientHandle, 0, 1, &jl);
    return static_cast<jint>(r);
}

extern "C" JNIEXPORT void JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiClientFree(
        JNIEnv*, jclass, jlong h) {
    feagi_client_free(JLONG_TO_PTR(FeagiAgentClientHandle, h));
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiClientConnect(
        JNIEnv*, jclass, jlong h) {
    return static_cast<jint>(feagi_client_connect(
            JLONG_TO_PTR(FeagiAgentClientHandle, h)));
}

// ── Registration response accessors ──────────────────────────────────────────

extern "C" JNIEXPORT jstring JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiClientRegistrationResponseJson(
        JNIEnv* env, jclass, jlong h) {
    char* json = feagi_client_registration_response_json_alloc(
            JLONG_TO_PTR(FeagiAgentClientHandle, h));
    if (!json) return nullptr;
    jstring r = env->NewStringUTF(json);
    feagi_string_free(json);
    return r;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiClientRegistrationZmqPortsJson(
        JNIEnv* env, jclass, jlong h) {
    char* json = feagi_client_registration_zmq_ports_json_alloc(
            JLONG_TO_PTR(FeagiAgentClientHandle, h));
    if (!json) return nullptr;
    jstring r = env->NewStringUTF(json);
    feagi_string_free(json);
    return r;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiClientRegistrationChosenTransportJson(
        JNIEnv* env, jclass, jlong h, jstring pref) {
    const char* p = jstr_get(env, pref);
    char* json = feagi_client_registration_chosen_transport_json_alloc(
            JLONG_TO_PTR(FeagiAgentClientHandle, h), p);
    jstr_release(env, pref, p);
    if (!json) return nullptr;
    jstring r = env->NewStringUTF(json);
    feagi_string_free(json);
    return r;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiClientRegistrationRecommendedTransport(
        JNIEnv* env, jclass, jlong h) {
    char* t = feagi_client_registration_recommended_transport_alloc(
            JLONG_TO_PTR(FeagiAgentClientHandle, h));
    if (!t) return nullptr;
    jstring r = env->NewStringUTF(t);
    feagi_string_free(t);
    return r;
}

// ── Sensory send ──────────────────────────────────────────────────────────────

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiClientSendSensoryBytes(
        JNIEnv* env, jclass, jlong h, jbyteArray bytes) {
    jsize len = env->GetArrayLength(bytes);
    jbyte* buf = env->GetByteArrayElements(bytes, nullptr);
    FeagiStatus r = feagi_client_send_sensory_bytes(
            JLONG_TO_PTR(FeagiAgentClientHandle, h),
            reinterpret_cast<const uint8_t*>(buf),
            static_cast<size_t>(len));
    env->ReleaseByteArrayElements(bytes, buf, JNI_ABORT);
    return static_cast<jint>(r);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiClientTrySendSensoryBytes(
        JNIEnv* env, jclass, jlong h, jbyteArray bytes, jbooleanArray outSent) {
    jsize len = env->GetArrayLength(bytes);
    jbyte* buf = env->GetByteArrayElements(bytes, nullptr);
    bool sent = false;
    FeagiStatus r = feagi_client_try_send_sensory_bytes(
            JLONG_TO_PTR(FeagiAgentClientHandle, h),
            reinterpret_cast<const uint8_t*>(buf),
            static_cast<size_t>(len),
            &sent);
    env->ReleaseByteArrayElements(bytes, buf, JNI_ABORT);
    jboolean js = static_cast<jboolean>(sent);
    env->SetBooleanArrayRegion(outSent, 0, 1, &js);
    return static_cast<jint>(r);
}

// ── Motor receive ─────────────────────────────────────────────────────────────

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiClientReceiveMotorBuffer(
        JNIEnv* env, jclass, jlong h,
        jlongArray outBufHandle, jbooleanArray outHasData) {
    FeagiByteBufferHandle* buf = nullptr;
    bool hasData = false;
    FeagiStatus r = feagi_client_receive_motor_buffer(
            JLONG_TO_PTR(FeagiAgentClientHandle, h), &buf, &hasData);
    jlong jl = PTR_TO_JLONG(buf);
    env->SetLongArrayRegion(outBufHandle, 0, 1, &jl);
    jboolean jd = static_cast<jboolean>(hasData);
    env->SetBooleanArrayRegion(outHasData, 0, 1, &jd);
    return static_cast<jint>(r);
}

// ── Buffer helpers ────────────────────────────────────────────────────────────

extern "C" JNIEXPORT jlong JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiBufferPtr(
        JNIEnv*, jclass, jlong h) {
    const uint8_t* ptr = feagi_buffer_ptr(
            const_cast<const FeagiByteBufferHandle*>(JLONG_TO_PTR(FeagiByteBufferHandle, h)));
    return PTR_TO_JLONG(ptr);
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiBufferLen(
        JNIEnv*, jclass, jlong h) {
    return static_cast<jlong>(feagi_buffer_len(
            const_cast<const FeagiByteBufferHandle*>(JLONG_TO_PTR(FeagiByteBufferHandle, h))));
}

extern "C" JNIEXPORT void JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiBufferFree(
        JNIEnv*, jclass, jlong h) {
    feagi_buffer_free(JLONG_TO_PTR(FeagiByteBufferHandle, h));
}

// ── NativeFeagiAgentClient.copyNativeBuffer ───────────────────────────────────
// Copies bytes from a native FeagiByteBufferHandle into a new Java byte array.
// Returns new byte[0] for zero-length frames (not null) so Java callers can
// distinguish "zero-length frame received" from "no frame available" (null).

extern "C" JNIEXPORT jbyteArray JNICALL
Java_io_feagi_sdk_nativeffi_NativeFeagiAgentClient_copyNativeBuffer(
        JNIEnv* env, jclass, jlong bufHandle, jint length) {
    const FeagiByteBufferHandle* buf =
            const_cast<const FeagiByteBufferHandle*>(JLONG_TO_PTR(FeagiByteBufferHandle, bufHandle));
    if (!buf) return nullptr;

    if (length == 0) {
        return env->NewByteArray(0);
    }

    const uint8_t* ptr = feagi_buffer_ptr(buf);
    if (!ptr) return nullptr;

    jbyteArray result = env->NewByteArray(length);
    if (!result) return nullptr;  // OOM already thrown by JVM

    env->SetByteArrayRegion(result, 0, length, reinterpret_cast<const jbyte*>(ptr));
    return result;
}
