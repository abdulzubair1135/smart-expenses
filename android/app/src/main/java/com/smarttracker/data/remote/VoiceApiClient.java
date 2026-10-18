package com.smarttracker.data.remote;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smarttracker.data.session.SessionManager;
import com.smarttracker.ui.voice.LocalPatternLanguageParser;
import com.smarttracker.ui.voice.VoiceCommandModel;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class VoiceApiClient {

    private static final String TAG = "VoiceApiClient";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Cloud backend on Render (primary); USB reverse (dev); Emulator bridge (emulator dev)
    private static final String BASE_URL_CLOUD = "https://smart-expenses-kuob.onrender.com";
    private static final String BASE_URL_USB = "http://127.0.0.1:4000";
    private static final String BASE_URL_EMULATOR = "http://10.0.2.2:4000";

    private final OkHttpClient httpClient;
    private final SessionManager sessionManager;
    private final Handler mainHandler;

    public interface VoiceCallback {
        void onSuccess(@NonNull VoiceCommandModel result);
        void onFallback(@NonNull VoiceCommandModel fallbackResult, @NonNull String fallbackReason);
        void onError(@NonNull String errorMessage);
    }

    public VoiceApiClient(Context context) {
        this.sessionManager = SessionManager.getInstance(context);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(3500, TimeUnit.MILLISECONDS)
                .readTimeout(6000, TimeUnit.MILLISECONDS)
                .writeTimeout(3500, TimeUnit.MILLISECONDS)
                .build();
    }

    public void parseTranscript(@NonNull String transcript, @NonNull VoiceCallback callback) {
        final String correlationId = "corr_" + UUID.randomUUID().toString().substring(0, 8);
        final long startTime = System.currentTimeMillis();

        String rawToken = sessionManager.getAuthToken();
        final String authToken = (rawToken != null && !rawToken.trim().isEmpty())
                ? rawToken
                : "demo-user-" + sessionManager.getUserId();

        JsonObject payload = new JsonObject();
        payload.addProperty("transcript", transcript);
        payload.addProperty("correlationId", correlationId);

        String jsonBody = payload.toString();
        RequestBody requestBody = RequestBody.create(jsonBody, JSON);

        // Try cloud backend first (primary)
        executeRequest(BASE_URL_CLOUD + "/api/v1/voice/parse", authToken, correlationId, requestBody, transcript, startTime, new InternalCallback() {
            @Override
            public void onComplete(VoiceCommandModel result) {
                mainHandler.post(() -> callback.onSuccess(result));
            }

            @Override
            public void onFailed(String cloudReason) {
                // Fallback 1: USB port reverse (local dev http://127.0.0.1:4000)
                executeRequest(BASE_URL_USB + "/api/v1/voice/parse", authToken, correlationId, requestBody, transcript, startTime, new InternalCallback() {
                    @Override
                    public void onComplete(VoiceCommandModel result) {
                        mainHandler.post(() -> callback.onSuccess(result));
                    }

                    @Override
                    public void onFailed(String usbReason) {
                        // Fallback 2: Emulator bridge (http://10.0.2.2:4000)
                        executeRequest(BASE_URL_EMULATOR + "/api/v1/voice/parse", authToken, correlationId, requestBody, transcript, startTime, new InternalCallback() {
                            @Override
                            public void onComplete(VoiceCommandModel result) {
                                mainHandler.post(() -> callback.onSuccess(result));
                            }

                            @Override
                            public void onFailed(String secondReason) {
                                // Final fallback: deterministic offline parser
                                VoiceCommandModel fallbackModel = LocalPatternLanguageParser.parse(transcript);
                                fallbackModel.correlationId = correlationId;
                                fallbackModel.aiProvider = "deterministic-fallback";
                                Log.w(TAG, "[" + correlationId + "] All backends failed. Offline fallback. Reason: " + secondReason);
                                mainHandler.post(() -> callback.onFallback(fallbackModel, secondReason));
                            }
                        });
                    }
                });
            }
        });
    }

    private interface InternalCallback {
        void onComplete(VoiceCommandModel result);
        void onFailed(String reason);
    }

    private void executeRequest(String url, String token, String correlationId, RequestBody body, String transcript, long startTime, InternalCallback internalCallback) {
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("X-Correlation-ID", correlationId)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                long duration = System.currentTimeMillis() - startTime;
                Log.d(TAG, "[" + correlationId + "] Network call failed in " + duration + "ms: " + e.getMessage());
                internalCallback.onFailed("Connection failed: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                long duration = System.currentTimeMillis() - startTime;
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        internalCallback.onFailed("Server responded with HTTP " + response.code());
                        return;
                    }

                    String responseString = response.body().string();
                    JsonObject root = JsonParser.parseString(responseString).getAsJsonObject();
                    if (!root.has("success") || !root.get("success").getAsBoolean() || !root.has("data")) {
                        internalCallback.onFailed("Server returned success=false");
                        return;
                    }

                    JsonObject data = root.getAsJsonObject("data");
                    VoiceCommandModel model = new VoiceCommandModel();
                    model.correlationId = correlationId;
                    model.intent = data.has("intent") && !data.get("intent").isJsonNull() ? data.get("intent").getAsString() : "UNKNOWN";

                    if (data.has("transaction_type") && !data.get("transaction_type").isJsonNull()) {
                        model.transactionType = data.get("transaction_type").getAsString().toLowerCase();
                    } else {
                        model.transactionType = null;
                    }

                    if (data.has("amount_minor") && !data.get("amount_minor").isJsonNull()) {
                        model.amountMinor = data.get("amount_minor").getAsLong();
                    } else {
                        model.amountMinor = 0;
                    }

                    model.currencyCode = data.has("currency_code") && !data.get("currency_code").isJsonNull() ? data.get("currency_code").getAsString() : "INR";
                    model.description = data.has("description") && !data.get("description").isJsonNull() ? data.get("description").getAsString() : "Transaction";
                    model.categoryName = data.has("category_name") && !data.get("category_name").isJsonNull() ? data.get("category_name").getAsString() : "Others";
                    model.accountName = data.has("account_name") && !data.get("account_name").isJsonNull() ? data.get("account_name").getAsString() : "UPI";
                    model.transactionDate = data.has("transaction_date") && !data.get("transaction_date").isJsonNull() ? data.get("transaction_date").getAsString() : null;
                    model.recipient = data.has("recipient") && !data.get("recipient").isJsonNull() ? data.get("recipient").getAsString() : null;
                    model.sourceAccount = data.has("source_account") && !data.get("source_account").isJsonNull() ? data.get("source_account").getAsString() : null;
                    model.destinationAccount = data.has("destination_account") && !data.get("destination_account").isJsonNull() ? data.get("destination_account").getAsString() : null;

                    model.clarificationRequired = data.has("clarification_required") && data.get("clarification_required").getAsBoolean();
                    model.clarificationQuestion = data.has("clarification_question") && !data.get("clarification_question").isJsonNull() ? data.get("clarification_question").getAsString() : null;
                    model.spokenResponse = data.has("spoken_response") && !data.get("spoken_response").isJsonNull() ? data.get("spoken_response").getAsString() : "";

                    model.aiProvider = data.has("ai_provider") && !data.get("ai_provider").isJsonNull() ? data.get("ai_provider").getAsString() : "qwen3.5:4b";
                    model.confidence = data.has("confidence") && !data.get("confidence").isJsonNull() ? data.get("confidence").getAsDouble() : 0.95;

                    Log.d(TAG, "[" + correlationId + "] Successfully parsed via " + model.aiProvider + " in " + duration + "ms. Type: " + model.transactionType);
                    internalCallback.onComplete(model);
                } catch (Exception parseEx) {
                    Log.e(TAG, "[" + correlationId + "] Error parsing response: " + parseEx.getMessage());
                    internalCallback.onFailed("Parsing response failed: " + parseEx.getMessage());
                }
            }
        });
    }
}
