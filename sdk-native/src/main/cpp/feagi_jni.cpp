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
extern "C" {
    #include <feagi_java_ffi.h>
}

#ifndef FEAGI_JAVA_FFI_H
#  pragma message("NOTE: FEAGI_JAVA_FFI_H not defined by header (this is ok if it uses different guard name)")
#endif

// Hard fail if the expected API isn't visible
#ifndef feagi_library_version_alloc
// This check doesn't work for functions (they aren't macros), so use a symbol reference instead.
#endif

// Force the compiler to see the types/functions (will error with a clearer message)
static void __feagi_header_sanity_check() {
    (void)&feagi_library_version_alloc;
    (void)&feagi_last_error_message_alloc;
    FeagiAgentConfigHandle* a = nullptr;
    FeagiAgentClientHandle* b = nullptr;
    (void)a; (void)b;
}

// ── Pointer encoding helpers ───────────────────────────────────────────────────
// Opaque C pointers are passed to Java as jlong (64-bit signed integer).
#define PTR_TO_JLONG(ptr)          (static_cast<jlong>(reinterpret_cast<intptr_t>(ptr)))
#define JLONG_TO_PTR(type, jl)     (reinterpret_cast<type*>(static_cast<intptr_t>(jl)))

// ── Null-safe jstring → UTF-8 helper ──────────────────────────────────────────
// Returns nullptr if jstr is nullptr (maps to C NULL).
static const char* jstring_to_utf8(JNIEnv* env, jstring jstr) {
    if (jstr == nullptr) return nullptr;
    return env->GetStringUTFChars(jstr, nullptr);
}

static void release_utf8(JNIEnv* env, jstring jstr, const char* chars) {
    if (jstr != nullptr && chars != nullptr) {
        env->ReleaseStringUTFChars(jstr, chars);
    }
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
    jstring out = env->NewStringUTF(ver);
    feagi_string_free(ver);
    return out;
}

// ── Error reporting ───────────────────────────────────────────────────────────

extern "C" JNIEXPORT jstring JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiLastErrorMessage(JNIEnv* env, jclass) {
    char* msg = feagi_last_error_message_alloc();
    if (!msg) return nullptr;
    jstring out = env->NewStringUTF(msg);
    feagi_string_free(msg);
    return out;
}

// ── Config lifecycle ──────────────────────────────────────────────────────────

extern "C" JNIEXPORT jlong JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigNew(
        JNIEnv* env, jclass,
        jstring agentId, jint agentType) {

    const char* id = jstring_to_utf8(env, agentId);
    FeagiAgentConfigHandle* handle = feagi_config_new(id, static_cast<FeagiAgentType>(agentType));
    release_utf8(env, agentId, id);
    return PTR_TO_JLONG(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigFree(
        JNIEnv*, jclass, jlong cfgHandle) {

    feagi_config_free(JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle));
}

// ── Endpoint setters ──────────────────────────────────────────────────────────

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetRegistrationEndpoint(
        JNIEnv* env, jclass, jlong cfgHandle, jstring endpoint) {

    const char* ep = jstring_to_utf8(env, endpoint);
    FeagiStatus status = feagi_config_set_registration_endpoint(
            JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle), ep);
    release_utf8(env, endpoint, ep);
    return static_cast<jint>(status);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetSensoryEndpoint(
        JNIEnv* env, jclass, jlong cfgHandle, jstring endpoint) {

    const char* ep = jstring_to_utf8(env, endpoint);
    FeagiStatus status = feagi_config_set_sensory_endpoint(
            JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle), ep);
    release_utf8(env, endpoint, ep);
    return static_cast<jint>(status);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetMotorEndpoint(
        JNIEnv* env, jclass, jlong cfgHandle, jstring endpoint) {

    const char* ep = jstring_to_utf8(env, endpoint);
    FeagiStatus status = feagi_config_set_motor_endpoint(
            JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle), ep);
    release_utf8(env, endpoint, ep);
    return static_cast<jint>(status);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetVisualizationEndpoint(
        JNIEnv* env, jclass, jlong cfgHandle, jstring endpoint) {

    const char* ep = jstring_to_utf8(env, endpoint);
    FeagiStatus status = feagi_config_set_visualization_endpoint(
            JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle), ep);
    release_utf8(env, endpoint, ep);
    return static_cast<jint>(status);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetControlEndpoint(
        JNIEnv* env, jclass, jlong cfgHandle, jstring endpoint) {

    const char* ep = jstring_to_utf8(env, endpoint);
    FeagiStatus status = feagi_config_set_control_endpoint(
            JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle), ep);
    release_utf8(env, endpoint, ep);
    return static_cast<jint>(status);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetFeagiEndpoints(
        JNIEnv* env, jclass, jlong cfgHandle,
        jstring host,
        jint registrationPort, jint sensoryPort, jint motorPort,
        jint visualizationPort, jint controlPort) {

    const char* h = jstring_to_utf8(env, host);
    FeagiStatus status = feagi_config_set_feagi_endpoints(
            JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle),
            h,
            static_cast<uint16_t>(registrationPort),
            static_cast<uint16_t>(sensoryPort),
            static_cast<uint16_t>(motorPort),
            static_cast<uint16_t>(visualizationPort),
            static_cast<uint16_t>(controlPort));
    release_utf8(env, host, h);
    return static_cast<jint>(status);
}

// ── Timing / retry ────────────────────────────────────────────────────────────

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetHeartbeatIntervalSeconds(
        JNIEnv*, jclass, jlong cfgHandle, jdouble heartbeatIntervalSeconds) {

    return static_cast<jint>(feagi_config_set_heartbeat_interval_s(
            JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle),
            static_cast<double>(heartbeatIntervalSeconds)));
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetConnectionTimeoutMs(
        JNIEnv*, jclass, jlong cfgHandle, jlong connectionTimeoutMs) {

    return static_cast<jint>(feagi_config_set_connection_timeout_ms(
            JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle),
            static_cast<uint64_t>(connectionTimeoutMs)));
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetRegistrationRetries(
        JNIEnv*, jclass, jlong cfgHandle, jint registrationRetries) {

    return static_cast<jint>(feagi_config_set_registration_retries(
            JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle),
            static_cast<uint32_t>(registrationRetries)));
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetRetryBackoffMs(
        JNIEnv*, jclass, jlong cfgHandle, jlong retryBackoffMs) {

    return static_cast<jint>(feagi_config_set_retry_backoff_ms(
            JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle),
            static_cast<uint64_t>(retryBackoffMs)));
}

// ── Agent descriptor / auth ───────────────────────────────────────────────────

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetAgentDescriptor(
        JNIEnv* env, jclass, jlong cfgHandle,
        jstring manufacturer, jstring agentName, jint agentVersion) {

    const char* mfr  = jstring_to_utf8(env, manufacturer);
    const char* name = jstring_to_utf8(env, agentName);
    FeagiStatus status = feagi_config_set_agent_descriptor(
            JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle),
            mfr, name, static_cast<uint32_t>(agentVersion));
    release_utf8(env, manufacturer, mfr);
    release_utf8(env, agentName,    name);
    return static_cast<jint>(status);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_nativeConfigSetAuthTokenBase64(
        JNIEnv* env, jclass, jlong cfgHandle, jstring authTokenBase64) {

    const char* token = jstring_to_utf8(env, authTokenBase64);
    FeagiStatus status = feagi_config_set_auth_token_base64(
            JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle), token);
    release_utf8(env, authTokenBase64, token);
    return static_cast<jint>(status);
}

// ── Sensory socket config ─────────────────────────────────────────────────────

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetSensorySocketConfig(
        JNIEnv*, jclass, jlong cfgHandle,
        jint sendHwm, jint lingerMs, jboolean immediate) {

    return static_cast<jint>(feagi_config_set_sensory_socket_config(
            JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle),
            static_cast<int32_t>(sendHwm),
            static_cast<int32_t>(lingerMs),
            static_cast<bool>(immediate)));
}

// ── Capabilities ──────────────────────────────────────────────────────────────

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetSensoryCapability(
        JNIEnv* env, jclass, jlong cfgHandle,
        jdouble rateHz, jstring shmPathOrNull) {

    const char* shm = jstring_to_utf8(env, shmPathOrNull);  // null-safe
    FeagiStatus status = feagi_config_set_sensory_capability(
            JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle),
            static_cast<double>(rateHz), shm);
    release_utf8(env, shmPathOrNull, shm);
    return static_cast<jint>(status);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetVisionCapability(
        JNIEnv* env, jclass, jlong cfgHandle,
        jstring modality, jlong width, jlong height, jlong channels,
        jstring targetCorticalArea) {

    const char* mod  = jstring_to_utf8(env, modality);
    const char* area = jstring_to_utf8(env, targetCorticalArea);
    FeagiStatus status = feagi_config_set_vision_capability(
            JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle),
            mod,
            static_cast<uint32_t>(width),
            static_cast<uint32_t>(height),
            static_cast<uint32_t>(channels),
            area);
    release_utf8(env, modality,          mod);
    release_utf8(env, targetCorticalArea, area);
    return static_cast<jint>(status);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetVisionUnit(
        JNIEnv* env, jclass, jlong cfgHandle,
        jstring modality, jlong width, jlong height, jlong channels,
        jint unit, jint group) {

    const char* mod = jstring_to_utf8(env, modality);
    FeagiStatus status = feagi_config_set_vision_unit(
            JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle),
            mod,
            static_cast<uint32_t>(width),
            static_cast<uint32_t>(height),
            static_cast<uint32_t>(channels),
            static_cast<FeagiSensoryUnit>(unit),
            static_cast<uint8_t>(group));
    release_utf8(env, modality, mod);
    return static_cast<jint>(status);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetMotorCapability(
        JNIEnv* env, jclass, jlong cfgHandle,
        jstring modality, jlong outputCount, jstring sourceCorticalAreasJson) {

    const char* mod  = jstring_to_utf8(env, modality);
    const char* json = jstring_to_utf8(env, sourceCorticalAreasJson);
    FeagiStatus status = feagi_config_set_motor_capability(
            JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle),
            mod, static_cast<uint32_t>(outputCount), json);
    release_utf8(env, modality,               mod);
    release_utf8(env, sourceCorticalAreasJson, json);
    return static_cast<jint>(status);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetMotorUnit(
        JNIEnv* env, jclass, jlong cfgHandle,
        jstring modality, jlong outputCount, jint unit, jint group) {

    const char* mod = jstring_to_utf8(env, modality);
    FeagiStatus status = feagi_config_set_motor_unit(
            JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle),
            mod,
            static_cast<uint32_t>(outputCount),
            static_cast<FeagiMotorUnit>(unit),
            static_cast<uint8_t>(group));
    release_utf8(env, modality, mod);
    return static_cast<jint>(status);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetMotorUnitsJson(
        JNIEnv* env, jclass, jlong cfgHandle,
        jstring modality, jlong outputCount, jstring motorUnitsJson) {

    const char* mod  = jstring_to_utf8(env, modality);
    const char* json = jstring_to_utf8(env, motorUnitsJson);
    FeagiStatus status = feagi_config_set_motor_units_json(
            JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle),
            mod, static_cast<uint32_t>(outputCount), json);
    release_utf8(env, modality,     mod);
    release_utf8(env, motorUnitsJson, json);
    return static_cast<jint>(status);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetVisualizationCapability(
        JNIEnv* env, jclass, jlong cfgHandle,
        jstring visualizationType,
        jboolean hasResolution, jlong resolutionWidth, jlong resolutionHeight,
        jboolean hasRefreshRate, jdouble refreshRateHz,
        jboolean bridgeProxy) {

    const char* vizType = jstring_to_utf8(env, visualizationType);
    FeagiStatus status = feagi_config_set_visualization_capability(
            JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle),
            vizType,
            static_cast<bool>(hasResolution),
            static_cast<uint32_t>(resolutionWidth),
            static_cast<uint32_t>(resolutionHeight),
            static_cast<bool>(hasRefreshRate),
            static_cast<double>(refreshRateHz),
            static_cast<bool>(bridgeProxy));
    release_utf8(env, visualizationType, vizType);
    return static_cast<jint>(status);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigSetCustomCapabilityJson(
        JNIEnv* env, jclass, jlong cfgHandle,
        jstring key, jstring jsonValue) {

    const char* k = jstring_to_utf8(env, key);
    const char* v = jstring_to_utf8(env, jsonValue);
    FeagiStatus status = feagi_config_set_custom_capability_json(
            JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle), k, v);
    release_utf8(env, key,       k);
    release_utf8(env, jsonValue, v);
    return static_cast<jint>(status);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiConfigValidate(
        JNIEnv*, jclass, jlong cfgHandle) {

    FeagiStatus status = feagi_config_validate(
        const_cast<const FeagiAgentConfigHandle*>(
            JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle)
        )
    );
    return static_cast<jint>(status);
}

// ── Client lifecycle ──────────────────────────────────────────────────────────

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiClientNew(
        JNIEnv* env, jclass,
        jlong cfgHandle, jlongArray outClientHandle) {

    FeagiAgentClientHandle* client = nullptr;
    FeagiStatus status = feagi_client_new(
            JLONG_TO_PTR(FeagiAgentConfigHandle, cfgHandle), &client);

    jlong clientJlong = PTR_TO_JLONG(client);
    env->SetLongArrayRegion(outClientHandle, 0, 1, &clientJlong);
    return static_cast<jint>(status);
}

extern "C" JNIEXPORT void JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiClientFree(
        JNIEnv*, jclass, jlong clientHandle) {

    feagi_client_free(JLONG_TO_PTR(FeagiAgentClientHandle, clientHandle));
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiClientConnect(
        JNIEnv*, jclass, jlong clientHandle) {

    return static_cast<jint>(feagi_client_connect(
            JLONG_TO_PTR(FeagiAgentClientHandle, clientHandle)));
}

// ── Registration response accessors ──────────────────────────────────────────

extern "C" JNIEXPORT jstring JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiClientRegistrationResponseJson(
        JNIEnv* env, jclass, jlong clientHandle) {

    const char* json = feagi_client_registration_response_json_alloc(
            JLONG_TO_PTR(FeagiAgentClientHandle, clientHandle));
    if (!json) return nullptr;
    jstring result = env->NewStringUTF(json);
    feagi_string_free(const_cast<char*>(json));
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiClientRegistrationZmqPortsJson(
        JNIEnv* env, jclass, jlong clientHandle) {

    const char* json = feagi_client_registration_zmq_ports_json_alloc(
            JLONG_TO_PTR(FeagiAgentClientHandle, clientHandle));
    if (!json) return nullptr;
    jstring result = env->NewStringUTF(json);
    feagi_string_free(const_cast<char*>(json));
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiClientRegistrationChosenTransportJson(
        JNIEnv* env, jclass, jlong clientHandle, jstring preferenceOrNull) {

    const char* pref = jstring_to_utf8(env, preferenceOrNull);
    char* json = feagi_client_registration_chosen_transport_json_alloc(
            JLONG_TO_PTR(FeagiAgentClientHandle, clientHandle), pref);
    release_utf8(env, preferenceOrNull, pref);
    if (!json) return nullptr;
    jstring result = env->NewStringUTF(json);
    feagi_string_free(const_cast<char*>(json));
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiClientRegistrationRecommendedTransport(
        JNIEnv* env, jclass, jlong clientHandle) {

    const char* transport = feagi_client_registration_recommended_transport_alloc(
            JLONG_TO_PTR(FeagiAgentClientHandle, clientHandle));
    if (!transport) return nullptr;
    jstring result = env->NewStringUTF(transport);
    feagi_string_free(const_cast<char*>(transport));
    return result;
}

// ── Sensory send ──────────────────────────────────────────────────────────────

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiClientSendSensoryBytes(
        JNIEnv* env, jclass, jlong clientHandle, jbyteArray bytes) {

    jsize len = env->GetArrayLength(bytes);
    jbyte* buf = env->GetByteArrayElements(bytes, nullptr);
    FeagiStatus status = feagi_client_send_sensory_bytes(
            JLONG_TO_PTR(FeagiAgentClientHandle, clientHandle),
            reinterpret_cast<const uint8_t*>(buf),
            static_cast<uintptr_t>(len));
    env->ReleaseByteArrayElements(bytes, buf, JNI_ABORT);
    return static_cast<jint>(status);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiClientTrySendSensoryBytes(
        JNIEnv* env, jclass,
        jlong clientHandle, jbyteArray bytes, jbooleanArray outSent) {

    jsize len = env->GetArrayLength(bytes);
    jbyte* buf = env->GetByteArrayElements(bytes, nullptr);
    bool sent = false;
    FeagiStatus status = feagi_client_try_send_sensory_bytes(
            JLONG_TO_PTR(FeagiAgentClientHandle, clientHandle),
            reinterpret_cast<const uint8_t*>(buf),
            static_cast<uintptr_t>(len),
            &sent);
    env->ReleaseByteArrayElements(bytes, buf, JNI_ABORT);
    jboolean jSent = static_cast<jboolean>(sent);
    env->SetBooleanArrayRegion(outSent, 0, 1, &jSent);
    return static_cast<jint>(status);
}

// ── Motor receive ─────────────────────────────────────────────────────────────

extern "C" JNIEXPORT jint JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiClientReceiveMotorBuffer(
        JNIEnv* env, jclass,
        jlong clientHandle, jlongArray outBufferHandle, jbooleanArray outHasData) {

    FeagiByteBufferHandle* buf = nullptr;
    bool hasData = false;
    FeagiStatus status = feagi_client_receive_motor_buffer(
            JLONG_TO_PTR(FeagiAgentClientHandle, clientHandle), &buf, &hasData);

    jlong bufJlong = PTR_TO_JLONG(buf);
    env->SetLongArrayRegion(outBufferHandle, 0, 1, &bufJlong);
    jboolean jHasData = static_cast<jboolean>(hasData);
    env->SetBooleanArrayRegion(outHasData, 0, 1, &jHasData);
    return static_cast<jint>(status);
}

// ── Buffer helpers ────────────────────────────────────────────────────────────

extern "C" JNIEXPORT jlong JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiBufferPtr(
        JNIEnv*, jclass, jlong bufferHandle) {

    const uint8_t* ptr = feagi_buffer_ptr(
            const_cast<const FeagiByteBufferHandle*>(JLONG_TO_PTR(FeagiByteBufferHandle, bufferHandle)));
    return PTR_TO_JLONG(ptr);
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiBufferLen(
        JNIEnv*, jclass, jlong bufferHandle) {

    return static_cast<jlong>(feagi_buffer_len(
            const_cast<const FeagiByteBufferHandle*>(JLONG_TO_PTR(FeagiByteBufferHandle, bufferHandle))));
}

extern "C" JNIEXPORT void JNICALL
Java_io_feagi_sdk_nativeffi_FeagiNativeBindings_feagiBufferFree(
        JNIEnv*, jclass, jlong bufferHandle) {

    feagi_buffer_free(JLONG_TO_PTR(FeagiByteBufferHandle, bufferHandle));
}

// ── NativeFeagiAgentClient: buffer copy ──────────────────────────────────────
// Copies native buffer bytes directly into a new Java byte array.
// Called by NativeFeagiAgentClient.pollMotorBytes().

extern "C" JNIEXPORT jbyteArray JNICALL
Java_io_feagi_sdk_nativeffi_NativeFeagiAgentClient_copyNativeBuffer(
        JNIEnv* env, jclass,
        jlong bufHandle, jint length) {

    const FeagiByteBufferHandle* buf = const_cast<const FeagiByteBufferHandle*>(JLONG_TO_PTR(FeagiByteBufferHandle, bufHandle));
    if (buf == nullptr || length <= 0) return nullptr;

    const uint8_t* ptr = feagi_buffer_ptr(buf);
    if (ptr == nullptr) return nullptr;

    jbyteArray result = env->NewByteArray(length);
    if (result == nullptr) return nullptr;  // OOM already thrown

    env->SetByteArrayRegion(result, 0, length,
            reinterpret_cast<const jbyte*>(ptr));
    return result;
}
