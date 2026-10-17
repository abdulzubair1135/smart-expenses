package com.smarttracker.ui.voice;

import android.content.Context;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * Text-to-Speech manager prioritizing local, offline voice packs.
 * Checks whether voice data requires network connection, and provides
 * mute/unmute and voice info controls.
 */
public class TextToSpeechManager {

    private static final String TAG = "TextToSpeechManager";

    public interface TTSCompletionCallback {
        void onUtteranceDone(String utteranceId);
    }

    private TextToSpeech tts;
    private boolean isInitialized = false;
    private boolean isMuted = false;
    private String selectedVoiceName = "Default";
    private boolean hasOfflineVoice = false;
    private TTSCompletionCallback activeCallback;

    public TextToSpeechManager(Context context) {
        tts = new TextToSpeech(context.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                // Try Indian Hindi first, then Indian English, then device default
                Locale hiIn = new Locale("hi", "IN");
                int result = tts.setLanguage(hiIn);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Locale enIn = new Locale("en", "IN");
                    result = tts.setLanguage(enIn);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts.setLanguage(Locale.getDefault());
                    }
                }

                // Check for local offline voice
                detectOfflineVoice();

                tts.setPitch(1.0f);
                tts.setSpeechRate(0.95f);

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        Log.d(TAG, "TTS onStart: " + utteranceId);
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        Log.d(TAG, "TTS onDone: " + utteranceId);
                        if (activeCallback != null) {
                            TTSCompletionCallback cb = activeCallback;
                            activeCallback = null;
                            cb.onUtteranceDone(utteranceId);
                        }
                    }

                    @Override
                    public void onError(String utteranceId) {
                        Log.w(TAG, "TTS onError: " + utteranceId);
                        if (activeCallback != null) {
                            TTSCompletionCallback cb = activeCallback;
                            activeCallback = null;
                            cb.onUtteranceDone(utteranceId);
                        }
                    }
                });

                isInitialized = true;
                Log.d(TAG, "TTS initialized. Offline voice available: " + hasOfflineVoice);
            } else {
                Log.w(TAG, "TTS initialization failed: " + status);
            }
        });
    }

    private void detectOfflineVoice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && tts != null) {
            try {
                Voice currentVoice = tts.getVoice();
                if (currentVoice != null) {
                    selectedVoiceName = currentVoice.getName();
                    hasOfflineVoice = !currentVoice.isNetworkConnectionRequired();
                } else {
                    Set<Voice> voices = tts.getVoices();
                    if (voices != null) {
                        for (Voice v : voices) {
                            if (!v.isNetworkConnectionRequired()) {
                                tts.setVoice(v);
                                selectedVoiceName = v.getName();
                                hasOfflineVoice = true;
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error checking offline voice: " + e.getMessage());
            }
        }
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public boolean isOfflineVoiceAvailable() {
        return hasOfflineVoice;
    }

    public String getSelectedVoiceName() {
        return selectedVoiceName;
    }

    public boolean isMuted() {
        return isMuted;
    }

    public void setMuted(boolean muted) {
        this.isMuted = muted;
        if (muted) {
            stop();
        }
    }

    public void speak(String text) {
        speak(text, null);
    }

    public void speak(String text, @Nullable TTSCompletionCallback callback) {
        if (!isInitialized || isMuted || tts == null || text == null || text.trim().isEmpty()) {
            if (callback != null) {
                callback.onUtteranceDone("empty_or_muted");
            }
            return;
        }
        try {
            this.activeCallback = callback;
            String utteranceId = "voice_utterance_" + System.currentTimeMillis();
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        } catch (Exception e) {
            Log.e(TAG, "Error in TTS speak", e);
            if (callback != null) {
                callback.onUtteranceDone("error");
            }
        }
    }

    public void stop() {
        if (tts != null) {
            tts.stop();
        }
    }

    public void shutdown() {
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception e) {
                Log.e(TAG, "Error shutting down TTS", e);
            }
            tts = null;
            isInitialized = false;
        }
    }
}
