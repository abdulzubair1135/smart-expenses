package com.smarttracker.ui.voice.engine;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.smarttracker.ui.voice.LocalPatternLanguageParser;
import com.smarttracker.ui.voice.VoiceCommandModel;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Singleton orchestrator implementing LocalAiEngine.
 * Handles the complete on-device financial extraction flow with zero network requests:
 * 1. Checks if local Qwen GGUF model is loaded and available.
 * 2. If available, prompts Qwen using ChatML to produce structured JSON and validates via TransactionDraftValidator.
 * 3. If model is downloading or not yet loaded, delegates instantly to the deterministic 10-stage NLP parser.
 * 4. Ensures thread safety, memory isolation, and cancellation support.
 */
public class OnDeviceAiManager implements LocalAiEngine {

    private static final String TAG = "OnDeviceAiManager";
    private static volatile OnDeviceAiManager instance;

    private final ModelManager modelManager;
    private final LlamaCppEngine llamaEngine;
    private final ExecutorService workerExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final AtomicBoolean isInitializing = new AtomicBoolean(false);
    private final AtomicBoolean isModelLoaded = new AtomicBoolean(false);

    private OnDeviceAiManager() {
        this.modelManager = ModelManager.getInstance();
        this.llamaEngine = new LlamaCppEngine();
    }

    public static OnDeviceAiManager getInstance() {
        if (instance == null) {
            synchronized (OnDeviceAiManager.class) {
                if (instance == null) {
                    instance = new OnDeviceAiManager();
                }
            }
        }
        return instance;
    }

    @Override
    public synchronized void initializeModel(@NonNull Context context, @NonNull ModelInitCallback callback) {
        if (isModelLoaded.get() && llamaEngine.isReady()) {
            callback.onInitialized(true, "Local AI model is already loaded and ready.");
            return;
        }

        if (isInitializing.get()) {
            callback.onProgress(50, "Model initialization in progress...");
            return;
        }

        isInitializing.set(true);
        callback.onProgress(10, "Checking on-device model file...");

        workerExecutor.submit(() -> {
            try {
                File modelFile = modelManager.getModelFile(context);
                if (!modelFile.exists() || modelFile.length() < 1024 * 1024) {
                    isInitializing.set(false);
                    mainHandler.post(() -> {
                        callback.onProgress(100, "Offline NLP engine ready (Qwen GGUF not installed).");
                        callback.onInitialized(false, "GGUF model file not installed. Deterministic offline NLP engine active.");
                    });
                    return;
                }

                mainHandler.post(() -> callback.onProgress(30, "Verifying model integrity..."));
                boolean valid = modelManager.verifyModelIntegrity(context);
                if (!valid) {
                    isInitializing.set(false);
                    mainHandler.post(() -> callback.onError("Model file corrupted or invalid GGUF header."));
                    return;
                }

                mainHandler.post(() -> callback.onProgress(60, "Allocating model weights into memory..."));
                // 4 CPU threads, 2048 context window for mobile
                boolean loaded = llamaEngine.initModel(modelFile, 4, 2048);
                isModelLoaded.set(loaded);
                isInitializing.set(false);

                mainHandler.post(() -> {
                    if (loaded) {
                        callback.onProgress(100, "On-device Qwen model ready.");
                        callback.onInitialized(true, "Qwen 0.6B loaded successfully into local memory.");
                    } else {
                        callback.onInitialized(false, "Failed to load model weights. High-precision offline NLP engine active.");
                    }
                });

            } catch (Throwable t) {
                isInitializing.set(false);
                Log.e(TAG, "Initialization failed: " + t.getMessage(), t);
                mainHandler.post(() -> callback.onError("Model initialization error: " + t.getMessage()));
            }
        });
    }

    @Override
    public boolean isModelReady() {
        return isModelLoaded.get() && llamaEngine.isReady();
    }

    @Override
    public void generateStructuredResponse(@NonNull String transcript, @NonNull InferenceCallback callback) {
        final String correlationId = "local_" + UUID.randomUUID().toString().substring(0, 8);

        // Instant fast-path check for confirmations & cancellations
        String lower = transcript.toLowerCase().trim();
        if (lower.matches(".*\\b(haan|ha|yes|yep|confirm|save|theek hai|karo|done)\\b.*") &&
                !lower.matches(".*\\b(kharch|rupaye|rs|diye|aaye|transfer|kamaye|udhaar|udhar)\\b.*")) {
            VoiceCommandModel confirmModel = LocalPatternLanguageParser.parse(transcript);
            confirmModel.correlationId = correlationId;
            callback.onSuccess(confirmModel);
            return;
        }

        if (lower.matches(".*\\b(cancel|mat karo|cancel kar do|hatao|hata do|discard|stop)\\b.*") &&
                !lower.matches(".*\\b(kharch|rupaye|rs|diye|aaye|transfer|kamaye)\\b.*")) {
            VoiceCommandModel cancelModel = LocalPatternLanguageParser.parse(transcript);
            cancelModel.correlationId = correlationId;
            callback.onSuccess(cancelModel);
            return;
        }

        // If local Qwen GGUF model is loaded and ready, perform on-device LLM inference
        if (isModelReady()) {
            executeQwenInference(transcript, correlationId, callback);
        } else {
            // Instant deterministic 10-stage offline parser
            executeDeterministicInference(transcript, correlationId, callback);
        }
    }

    private void executeQwenInference(@NonNull String transcript, @NonNull String correlationId, @NonNull InferenceCallback callback) {
        String prompt = buildQwenPrompt(transcript);
        llamaEngine.generateAsync(prompt, 256, 0.1f, 15, new LlamaCppEngine.GenerationCallback() {
            @Override
            public void onResponse(@NonNull String generatedText) {
                TransactionDraftValidator.ValidationResult result =
                        TransactionDraftValidator.validateJson(generatedText, transcript);

                if (result.isValid && result.model != null) {
                    result.model.correlationId = correlationId;
                    result.model.aiProvider = "local-qwen";
                    callback.onSuccess(result.model);
                } else if (result.requiresClarification && result.clarificationQuestion != null) {
                    callback.onClarificationNeeded(result.clarificationQuestion);
                } else {
                    // Failover safely to deterministic parser
                    executeDeterministicInference(transcript, correlationId, callback);
                }
            }

            @Override
            public void onError(@NonNull String errorMessage) {
                Log.w(TAG, "[" + correlationId + "] Qwen inference error: " + errorMessage + ". Falling back to deterministic NLP.");
                executeDeterministicInference(transcript, correlationId, callback);
            }
        });
    }

    private void executeDeterministicInference(@NonNull String transcript, @NonNull String correlationId, @NonNull InferenceCallback callback) {
        workerExecutor.submit(() -> {
            VoiceCommandModel parsed = LocalPatternLanguageParser.parse(transcript);
            parsed.correlationId = correlationId;
            parsed.aiProvider = "local-nlp-engine";

            TransactionDraftValidator.ValidationResult result = TransactionDraftValidator.validateModel(parsed);

            mainHandler.post(() -> {
                if (result.isValid && result.model != null) {
                    callback.onSuccess(result.model);
                } else if (result.requiresClarification && result.clarificationQuestion != null) {
                    callback.onClarificationNeeded(result.clarificationQuestion);
                } else {
                    callback.onError("Ye transaction spasht nahi hai. Kripya dobara boliye.");
                }
            });
        });
    }

    private String buildQwenPrompt(String transcript) {
        return "<|im_start|>system\n" +
                "You are Smart Expenses Tracker's local AI financial assistant. Output ONLY JSON.\n" +
                "Classify Hindi/Hinglish speech into financial transactions.\n" +
                "Types: RECEIVED (income/salary/bonus/earnings), EXPENSE (spent/paid), LEND (money given as loan), BORROW (money taken as loan), TRANSFER (bank to wallet).\n" +
                "Format: {\"intent\":\"CREATE_TRANSACTION\",\"transactionType\":\"RECEIVED|EXPENSE|LEND|BORROW|TRANSFER\",\"amountMinor\":<paise>,\"category\":\"<cat>\",\"account\":\"UPI|Cash|Bank Account\",\"requiresClarification\":false}\n" +
                "If ambiguous or purpose/amount is unclear, return: {\"requiresClarification\":true,\"clarificationQuestion\":\"<question>\"}\n" +
                "<|im_end|>\n" +
                "<|im_start|>user\n" + transcript + "\n<|im_end|>\n" +
                "<|im_start|>assistant\n";
    }

    @Override
    public void cancelGeneration() {
        llamaEngine.cancel();
    }

    @Override
    public synchronized void releaseModel() {
        llamaEngine.release();
        isModelLoaded.set(false);
    }

    @NonNull
    @Override
    public String getActiveEngineType() {
        return isModelReady() ? "On-Device Qwen GGUF (Local AI)" : "Deterministic Parity NLP (Local Offline)";
    }

    @NonNull
    @Override
    public String getModelName() {
        return ModelManager.MODEL_DISPLAY_NAME;
    }

    @Override
    public long getModelSizeBytes() {
        return ModelManager.EXPECTED_MODEL_SIZE_BYTES;
    }
}
