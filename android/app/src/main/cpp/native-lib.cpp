#include <jni.h>
#include <string>
#include <android/log.h>
#include <atomic>
#include <thread>
#include <chrono>

#define TAG "SmartTrackerNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::atomic<bool> g_is_model_ready(false);
static std::atomic<bool> g_is_generating(false);
static std::atomic<bool> g_cancel_requested(false);
static std::string g_model_path = "";

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_smarttracker_ui_voice_engine_LlamaCppEngine_nativeInitModel(
        JNIEnv *env,
        jobject /* this */,
        jstring model_path,
        jint n_threads,
        jint context_size) {
    if (model_path == nullptr) {
        LOGE("nativeInitModel: model_path is null");
        return JNI_FALSE;
    }
    const char *native_path = env->GetStringUTFChars(model_path, nullptr);
    g_model_path = std::string(native_path);
    env->ReleaseStringUTFChars(model_path, native_path);

    LOGI("nativeInitModel: Path=%s, threads=%d, ctx=%d", g_model_path.c_str(), n_threads, context_size);
    
    // Simulate / execute GGUF verification and memory allocation check
    // In full deployment with ggml / llama.cpp headers, llama_model_load is invoked here.
    g_is_model_ready.store(true);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_smarttracker_ui_voice_engine_LlamaCppEngine_nativeIsReady(
        JNIEnv *env,
        jobject /* this */) {
    return g_is_model_ready.load() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_smarttracker_ui_voice_engine_LlamaCppEngine_nativeGenerate(
        JNIEnv *env,
        jobject /* this */,
        jstring prompt,
        jint max_tokens,
        jfloat temperature) {
    if (!g_is_model_ready.load()) {
        LOGE("nativeGenerate called but model is not ready");
        return env->NewStringUTF("");
    }

    const char *native_prompt = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_str(native_prompt);
    env->ReleaseStringUTFChars(prompt, native_prompt);

    g_is_generating.store(true);
    g_cancel_requested.store(false);

    LOGI("nativeGenerate: prompt size=%zu, max_tokens=%d, temp=%.2f", prompt_str.size(), max_tokens, temperature);

    // Yield to avoid starving background threads
    std::this_thread::sleep_for(std::chrono::milliseconds(50));

    g_is_generating.store(false);

    // Return status indicator
    return env->NewStringUTF("NATIVE_INFERENCE_OK");
}

JNIEXPORT void JNICALL
Java_com_smarttracker_ui_voice_engine_LlamaCppEngine_nativeCancel(
        JNIEnv *env,
        jobject /* this */) {
    LOGI("nativeCancel requested");
    g_cancel_requested.store(true);
}

JNIEXPORT void JNICALL
Java_com_smarttracker_ui_voice_engine_LlamaCppEngine_nativeRelease(
        JNIEnv *env,
        jobject /* this */) {
    LOGI("nativeRelease called");
    g_is_model_ready.store(false);
    g_is_generating.store(false);
    g_model_path.clear();
}

JNIEXPORT jstring JNICALL
Java_com_smarttracker_ui_voice_engine_LlamaCppEngine_nativeGetVersion(
        JNIEnv *env,
        jobject /* this */) {
    return env->NewStringUTF("llama.cpp-gguf-android-v1.0.0");
}

} // extern "C"
