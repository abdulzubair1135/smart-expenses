package com.smarttracker.ui.voice.engine;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Native JNI bridge for local GGUF model execution using llama.cpp bindings.
 * All native calls run strictly off the Android UI thread.
 */
public class LlamaCppEngine {

    private static final String TAG = "LlamaCppEngine";
    private static boolean isNativeLibraryLoaded = false;

    static {
        try {
            System.loadLibrary("smarttracker_native");
            isNativeLibraryLoaded = true;
            Log.i(TAG, "libsmarttracker_native.so loaded successfully");
        } catch (Throwable t) {
            isNativeLibraryLoaded = false;
            Log.w(TAG, "Native library load failed: " + t.getMessage());
        }
    }

    private final ExecutorService inferenceExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isModelLoaded = new AtomicBoolean(false);
    private final AtomicBoolean isGenerating = new AtomicBoolean(false);
    private Future<?> currentInferenceFuture;

    public interface GenerationCallback {
        void onResponse(@NonNull String generatedText);
        void onError(@NonNull String errorMessage);
    }

    public boolean isLibraryLoaded() {
        return isNativeLibraryLoaded;
    }

    public boolean isReady() {
        return isNativeLibraryLoaded && isModelLoaded.get() && nativeIsReady();
    }

    /**
     * Initializes the GGUF model from the specified file path.
     */
    public synchronized boolean initModel(@NonNull File modelFile, int nThreads, int contextSize) {
        if (!isNativeLibraryLoaded) {
            Log.w(TAG, "Cannot init model: native library not loaded");
            return false;
        }

        if (!modelFile.exists()) {
            Log.w(TAG, "Cannot init model: file does not exist at " + modelFile.getAbsolutePath());
            return false;
        }

        try {
            boolean success = nativeInitModel(modelFile.getAbsolutePath(), nThreads, contextSize);
            isModelLoaded.set(success);
            Log.i(TAG, "nativeInitModel completed with result: " + success);
            return success;
        } catch (Throwable t) {
            Log.e(TAG, "Exception during nativeInitModel: " + t.getMessage(), t);
            isModelLoaded.set(false);
            return false;
        }
    }

    /**
     * Executes local prompt generation with strict background threading and timeout protection.
     */
    public void generateAsync(@NonNull String prompt, int maxTokens, float temperature, long timeoutSeconds, @NonNull GenerationCallback callback) {
        if (!isReady()) {
            callback.onError("Local AI model is not ready for inference.");
            return;
        }

        if (isGenerating.get()) {
            callback.onError("Inference is already in progress.");
            return;
        }

        isGenerating.set(true);

        currentInferenceFuture = inferenceExecutor.submit(() -> {
            try {
                String result = nativeGenerate(prompt, maxTokens, temperature);
                isGenerating.set(false);
                mainHandler.post(() -> callback.onResponse(result != null ? result : ""));
            } catch (Throwable t) {
                isGenerating.set(false);
                Log.e(TAG, "Native generation error: " + t.getMessage(), t);
                mainHandler.post(() -> callback.onError("Generation failed: " + t.getMessage()));
            }
        });

        // Timeout watcher
        new Thread(() -> {
            try {
                if (currentInferenceFuture != null) {
                    currentInferenceFuture.get(timeoutSeconds, TimeUnit.SECONDS);
                }
            } catch (TimeoutException e) {
                cancel();
                isGenerating.set(false);
                mainHandler.post(() -> callback.onError("Inference timed out after " + timeoutSeconds + " seconds."));
            } catch (Exception ignored) {
            }
        }).start();
    }

    public void cancel() {
        if (isGenerating.get()) {
            try {
                nativeCancel();
            } catch (Throwable ignored) {}
            if (currentInferenceFuture != null) {
                currentInferenceFuture.cancel(true);
            }
            isGenerating.set(false);
            Log.i(TAG, "Inference cancelled.");
        }
    }

    public synchronized void release() {
        cancel();
        if (isNativeLibraryLoaded && isModelLoaded.get()) {
            try {
                nativeRelease();
            } catch (Throwable ignored) {}
            isModelLoaded.set(false);
            Log.i(TAG, "Native model released.");
        }
    }

    @Nullable
    public String getNativeVersion() {
        if (isNativeLibraryLoaded) {
            try {
                return nativeGetVersion();
            } catch (Throwable ignored) {}
        }
        return null;
    }

    // Native JNI method declarations implemented in native-lib.cpp
    private native boolean nativeInitModel(String modelPath, int nThreads, int contextSize);
    private native boolean nativeIsReady();
    private native String nativeGenerate(String prompt, int maxTokens, float temperature);
    private native void nativeCancel();
    private native void nativeRelease();
    private native String nativeGetVersion();
}
