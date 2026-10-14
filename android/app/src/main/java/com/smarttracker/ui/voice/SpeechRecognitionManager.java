package com.smarttracker.ui.voice;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;

/**
 * Speech Recognition Manager supporting Hindi, Hinglish, and English.
 * Uses standard Android SpeechRecognizer for maximum hardware compatibility,
 * with real-time partial results streaming and main-thread safety.
 */
public class SpeechRecognitionManager {

    private static final String TAG = "SpeechRecognitionManager";

    public interface SpeechCallback {
        void onReadyForSpeech();
        void onBeginningOfSpeech();
        void onRmsChanged(float rmsdB);
        void onPartialResults(String partialTranscript);
        void onResults(String transcript);
        void onError(String errorMessage);
    }

    private final Context context;
    private final SpeechCallback callback;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;

    public SpeechRecognitionManager(Context context, SpeechCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    public boolean hasPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean isRecognitionAvailable() {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    public boolean isOnDeviceRecognitionAvailable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return SpeechRecognizer.isOnDeviceRecognitionAvailable(context);
        }
        return false;
    }

    public void startListening() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(this::startListening);
            return;
        }

        if (!hasPermission()) {
            if (callback != null) callback.onError("Microphone permission not granted");
            return;
        }

        if (!isRecognitionAvailable()) {
            if (callback != null) callback.onError("Speech recognition service not available on device");
            return;
        }

        stopListening();

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            Log.i(TAG, "Initialized Standard SpeechRecognizer with Hindi/Hinglish/English support");

            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    isListening = true;
                    if (callback != null) callback.onReadyForSpeech();
                }

                @Override
                public void onBeginningOfSpeech() {
                    if (callback != null) callback.onBeginningOfSpeech();
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    if (callback != null) callback.onRmsChanged(rmsdB);
                }

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    isListening = false;
                }

                @Override
                public void onError(int error) {
                    isListening = false;
                    String message = getErrorMessage(error);
                    Log.w(TAG, "SpeechRecognizer error: " + error + " (" + message + ")");
                    if (callback != null) callback.onError(message);
                }

                @Override
                public void onResults(Bundle results) {
                    isListening = false;
                    if (results != null) {
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String topMatch = matches.get(0);
                            Log.d(TAG, "Recognized speech: " + topMatch);
                            if (callback != null) callback.onResults(topMatch);
                            return;
                        }
                    }
                    if (callback != null) callback.onError("No speech recognized. Please try again.");
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    if (partialResults != null) {
                        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty() && matches.get(0) != null) {
                            String partial = matches.get(0).trim();
                            if (!partial.isEmpty() && callback != null) {
                                callback.onPartialResults(partial);
                            }
                        }
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN");
            intent.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{"en-IN", "hi-IN", "en-US"});
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error starting speech recognition", e);
            isListening = false;
            if (callback != null) callback.onError("Failed to start speech recognizer: " + e.getMessage());
        }
    }

    public void stopListening() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(this::stopListening);
            return;
        }
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
                speechRecognizer.cancel();
                speechRecognizer.destroy();
            } catch (Exception e) {
                Log.w(TAG, "Error stopping recognizer", e);
            }
            speechRecognizer = null;
        }
        isListening = false;
    }

    public boolean isListening() {
        return isListening;
    }

    public void destroy() {
        stopListening();
    }

    private String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Audio error. Check microphone.";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Tap microphone to speak (Boliye)...";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Microphone permission required.";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Speech connection timeout. Tap mic or type below.";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "Kuch suna nahi diya. Dobara mic dabakar boliye.";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Mic busy hai. Ek second baad dabayein.";
            case SpeechRecognizer.ERROR_SERVER:
                return "Speech service error. Tap mic or type below.";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "Kuch bola nahi gaya. Mic dabakar boliye.";
            case 12: // ERROR_LANGUAGE_NOT_SUPPORTED
            case 13: // ERROR_LANGUAGE_UNAVAILABLE
                return "Language pack missing. Switching to default recognizer...";
            default:
                return "Voice error (" + errorCode + "). Tap mic to speak.";
        }
    }
}

