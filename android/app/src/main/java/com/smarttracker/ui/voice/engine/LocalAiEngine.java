package com.smarttracker.ui.voice.engine;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.smarttracker.ui.voice.VoiceCommandModel;

/**
 * Clean Java interface for on-device AI inference and financial intent extraction.
 * Guarantees zero network calls during normal offline operation.
 */
public interface LocalAiEngine {

    interface ModelInitCallback {
        void onInitialized(boolean success, @NonNull String message);
        void onProgress(int percent, @NonNull String statusMessage);
        void onError(@NonNull String errorMessage);
    }

    interface InferenceCallback {
        void onSuccess(@NonNull VoiceCommandModel result);
        void onClarificationNeeded(@NonNull String question);
        void onError(@NonNull String errorMessage);
    }

    /**
     * Initializes the local AI model on a background worker thread.
     */
    void initializeModel(@NonNull Context context, @NonNull ModelInitCallback callback);

    /**
     * Returns true if the on-device AI engine is loaded and ready for inference.
     */
    boolean isModelReady();

    /**
     * Executes local inference to generate structured financial transaction data.
     */
    void generateStructuredResponse(@NonNull String transcript, @NonNull InferenceCallback callback);

    /**
     * Cancels any running generation or model load operation.
     */
    void cancelGeneration();

    /**
     * Releases native memory and model weights when no longer needed.
     */
    void releaseModel();

    /**
     * Human-readable name of the active on-device engine.
     */
    @NonNull
    String getActiveEngineType();

    /**
     * Model identifier or filename.
     */
    @NonNull
    String getModelName();

    /**
     * Model file size in bytes (0 if not present).
     */
    long getModelSizeBytes();
}
