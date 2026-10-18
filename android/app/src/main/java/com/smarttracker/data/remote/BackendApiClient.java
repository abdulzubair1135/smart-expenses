package com.smarttracker.data.remote;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smarttracker.data.session.SessionManager;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Production-ready networking client connecting the Android application
 * to the deployed Render backend API with graceful local fallback.
 */
public class BackendApiClient {

    private static final String TAG = "BackendApiClient";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Primary cloud backend deployed on Render; fallback URLs for offline or local dev
    public static final String CLOUD_BASE_URL = "https://smart-expenses-kuob.onrender.com";
    public static final String USB_LOCAL_URL = "http://127.0.0.1:4000";
    public static final String EMULATOR_URL = "http://10.0.2.2:4000";

    private static BackendApiClient instance;
    private final OkHttpClient httpClient;
    private final SessionManager sessionManager;
    private final Handler mainHandler;
    private final Gson gson;

    private BackendApiClient(Context context) {
        this.sessionManager = SessionManager.getInstance(context);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.gson = new Gson();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized BackendApiClient getInstance(Context context) {
        if (instance == null) {
            instance = new BackendApiClient(context);
        }
        return instance;
    }

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    // 1. User Registration
    public void register(String name, String email, String password, String confirmPassword, boolean termsAccepted, ApiCallback<JsonObject> callback) {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.addProperty("email", email);
        body.addProperty("password", password);
        body.addProperty("confirmPassword", confirmPassword);
        body.addProperty("termsAccepted", termsAccepted);

        postJson("/api/v1/auth/register", body.toString(), null, new NetworkCallback() {
            @Override
            public void onResponse(JsonObject json) {
                mainHandler.post(() -> callback.onSuccess(json));
            }

            @Override
            public void onFailure(String error) {
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    // 2. User Login
    public void login(String email, String password, ApiCallback<JsonObject> callback) {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);

        postJson("/api/v1/auth/login", body.toString(), null, new NetworkCallback() {
            @Override
            public void onResponse(JsonObject json) {
                mainHandler.post(() -> callback.onSuccess(json));
            }

            @Override
            public void onFailure(String error) {
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    // 3. Request Password Reset OTP
    public void requestPasswordReset(String email, ApiCallback<JsonObject> callback) {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);

        postJson("/api/v1/auth/forgot-password", body.toString(), null, new NetworkCallback() {
            @Override
            public void onResponse(JsonObject json) {
                mainHandler.post(() -> callback.onSuccess(json));
            }

            @Override
            public void onFailure(String error) {
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    // 4. Verify Password Reset OTP
    public void verifyOtp(String email, String otp, ApiCallback<String> callback) {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("otp", otp);

        postJson("/api/v1/auth/verify-otp", body.toString(), null, new NetworkCallback() {
            @Override
            public void onResponse(JsonObject json) {
                try {
                    JsonObject data = json.getAsJsonObject("data");
                    String resetToken = data.get("resetToken").getAsString();
                    mainHandler.post(() -> callback.onSuccess(resetToken));
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Failed to parse reset token from response"));
                }
            }

            @Override
            public void onFailure(String error) {
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    // 5. Complete Password Reset
    public void resetPassword(String resetToken, String newPassword, String confirmPassword, ApiCallback<String> callback) {
        JsonObject body = new JsonObject();
        body.addProperty("resetToken", resetToken);
        body.addProperty("newPassword", newPassword);
        body.addProperty("confirmPassword", confirmPassword);

        postJson("/api/v1/auth/reset-password", body.toString(), null, new NetworkCallback() {
            @Override
            public void onResponse(JsonObject json) {
                String message = json.has("message") ? json.get("message").getAsString() : "Password reset successfully";
                mainHandler.post(() -> callback.onSuccess(message));
            }

            @Override
            public void onFailure(String error) {
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    // 6. Presence Heartbeat
    public void sendHeartbeat(ApiCallback<Void> callback) {
        String token = sessionManager.getAuthToken();
        if (token == null || token.trim().isEmpty()) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("Not authenticated"));
            }
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("clientPlatform", "android");

        postJson("/api/v1/presence/heartbeat", body.toString(), token, new NetworkCallback() {
            @Override
            public void onResponse(JsonObject json) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(null));
                }
            }

            @Override
            public void onFailure(String error) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(error));
                }
            }
        });
    }

    // 7. Data Export Backup
    public void exportBackup(ApiCallback<JsonObject> callback) {
        String token = sessionManager.getAuthToken();
        if (token == null || token.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("Authentication token required for backup export"));
            return;
        }

        postJson("/api/v1/backups/export", "{}", token, new NetworkCallback() {
            @Override
            public void onResponse(JsonObject json) {
                mainHandler.post(() -> callback.onSuccess(json));
            }

            @Override
            public void onFailure(String error) {
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    // 8. Upload Monitoring Events Batch
    public void uploadMonitoringEvents(java.util.List<com.smarttracker.data.local.entity.MonitoringEventEntity> events, ApiCallback<JsonObject> callback) {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));

        for (com.smarttracker.data.local.entity.MonitoringEventEntity e : events) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", e.id);
            obj.addProperty("eventType", e.eventType);
            obj.addProperty("severity", e.severity);
            obj.addProperty("source", e.source);
            obj.addProperty("errorCode", e.errorCode != null ? e.errorCode : "UNKNOWN");
            obj.addProperty("message", e.message != null ? e.message : "");
            if (e.stackTrace != null && !e.stackTrace.isEmpty()) {
                obj.addProperty("stackTrace", e.stackTrace);
            }
            if (e.screenName != null && !e.screenName.isEmpty()) {
                obj.addProperty("screenName", e.screenName);
            }
            if (e.operationName != null && !e.operationName.isEmpty()) {
                obj.addProperty("operationName", e.operationName);
            }
            obj.addProperty("appVersion", e.appVersion);
            obj.addProperty("appBuild", e.appBuild);
            obj.addProperty("androidVersion", e.androidVersion != null ? e.androidVersion : "");
            obj.addProperty("deviceModel", e.deviceModel != null ? e.deviceModel : "");
            obj.addProperty("installationId", e.installationId);
            obj.addProperty("correlationId", e.correlationId);
            obj.addProperty("occurredAt", sdf.format(new java.util.Date(e.occurredAt)));
            obj.addProperty("status", "NEW");
            obj.addProperty("occurrenceCount", 1);
            obj.add("metadataJson", new JsonObject());

            if (e.userId != null && !e.userId.isEmpty()) {
                obj.addProperty("userId", e.userId);
            }
            array.add(obj);
        }

        JsonObject root = new JsonObject();
        root.add("events", array);

        String token = sessionManager.getAuthToken();
        postJson("/api/v1/monitoring/events/batch", root.toString(), token, new NetworkCallback() {
            @Override
            public void onResponse(JsonObject json) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(json));
                }
            }

            @Override
            public void onFailure(String error) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(error));
                }
            }
        });
    }

    // 9. Push Account Data Sync
    public void pushSyncData(JsonObject payload, ApiCallback<JsonObject> callback) {
        String token = sessionManager.getAuthToken();
        if (token == null || token.trim().isEmpty()) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("Authentication token required for data sync"));
            }
            return;
        }

        postJson("/api/v1/sync/push", payload.toString(), token, new NetworkCallback() {
            @Override
            public void onResponse(JsonObject json) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(json));
                }
            }

            @Override
            public void onFailure(String error) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(error));
                }
            }
        });
    }

    // 10. Pull Account Data Sync
    public void pullSyncData(String since, ApiCallback<JsonObject> callback) {
        String token = sessionManager.getAuthToken();
        if (token == null || token.trim().isEmpty()) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("Authentication token required for data sync"));
            }
            return;
        }

        String endpoint = "/api/v1/sync/pull" + (since != null && !since.isEmpty() ? "?since=" + since : "");
        getJson(endpoint, token, new NetworkCallback() {
            @Override
            public void onResponse(JsonObject json) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(json));
                }
            }

            @Override
            public void onFailure(String error) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(error));
                }
            }
        });
    }

    private interface NetworkCallback {
        void onResponse(JsonObject json);
        void onFailure(String error);
    }

    private void getJson(String endpoint, String authToken, NetworkCallback callback) {
        executeSingleGet(CLOUD_BASE_URL + endpoint, authToken, new NetworkCallback() {
            @Override
            public void onResponse(JsonObject json) {
                callback.onResponse(json);
            }

            @Override
            public void onFailure(String cloudError) {
                Log.w(TAG, "Cloud GET request failed (" + cloudError + "). Trying local USB host...");
                executeSingleGet(USB_LOCAL_URL + endpoint, authToken, new NetworkCallback() {
                    @Override
                    public void onResponse(JsonObject json) {
                        callback.onResponse(json);
                    }

                    @Override
                    public void onFailure(String usbError) {
                        executeSingleGet(EMULATOR_URL + endpoint, authToken, new NetworkCallback() {
                            @Override
                            public void onResponse(JsonObject json) {
                                callback.onResponse(json);
                            }

                            @Override
                            public void onFailure(String emuError) {
                                callback.onFailure(cloudError);
                            }
                        });
                    }
                });
            }
        });
    }

    private void executeSingleGet(String fullUrl, String authToken, NetworkCallback callback) {
        Request.Builder builder = new Request.Builder()
                .url(fullUrl)
                .get();

        if (authToken != null && !authToken.trim().isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + authToken);
        }

        httpClient.newCall(builder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onFailure(e.getMessage() != null ? e.getMessage() : "Network request failed");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseStr = response.body() != null ? response.body().string() : "{}";
                try {
                    JsonObject json = JsonParser.parseString(responseStr).getAsJsonObject();
                    if (response.isSuccessful()) {
                        callback.onResponse(json);
                    } else {
                        String errMsg = "Request failed with code " + response.code();
                        if (json.has("error") && json.get("error").isJsonObject()) {
                            JsonObject errObj = json.getAsJsonObject("error");
                            if (errObj.has("message")) {
                                errMsg = errObj.get("message").getAsString();
                            }
                        } else if (json.has("message")) {
                            errMsg = json.get("message").getAsString();
                        }
                        callback.onFailure(errMsg);
                    }
                } catch (Exception parseEx) {
                    callback.onFailure("Failed to parse server response");
                }
            }
        });
    }

    private void postJson(String endpoint, String jsonBody, String authToken, NetworkCallback callback) {
        // Try Cloud Render URL first, fallback to USB/Emulator
        RequestBody body = RequestBody.create(jsonBody, JSON);

        executeSinglePost(CLOUD_BASE_URL + endpoint, body, authToken, new NetworkCallback() {
            @Override
            public void onResponse(JsonObject json) {
                callback.onResponse(json);
            }

            @Override
            public void onFailure(String cloudError) {
                Log.w(TAG, "Cloud request failed (" + cloudError + "). Trying local development host...");
                executeSinglePost(USB_LOCAL_URL + endpoint, body, authToken, new NetworkCallback() {
                    @Override
                    public void onResponse(JsonObject json) {
                        callback.onResponse(json);
                    }

                    @Override
                    public void onFailure(String usbError) {
                        executeSinglePost(EMULATOR_URL + endpoint, body, authToken, new NetworkCallback() {
                            @Override
                            public void onResponse(JsonObject json) {
                                callback.onResponse(json);
                            }

                            @Override
                            public void onFailure(String emuError) {
                                callback.onFailure(cloudError);
                            }
                        });
                    }
                });
            }
        });
    }

    private void executeSinglePost(String fullUrl, RequestBody body, String authToken, NetworkCallback callback) {
        Request.Builder builder = new Request.Builder()
                .url(fullUrl)
                .post(body);

        if (authToken != null && !authToken.trim().isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + authToken);
        }

        httpClient.newCall(builder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onFailure(e.getMessage() != null ? e.getMessage() : "Network request failed");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseStr = response.body() != null ? response.body().string() : "{}";
                try {
                    JsonObject json = JsonParser.parseString(responseStr).getAsJsonObject();
                    if (response.isSuccessful()) {
                        callback.onResponse(json);
                    } else {
                        String errMsg = "Request failed with code " + response.code();
                        if (json.has("error") && json.get("error").isJsonObject()) {
                            JsonObject errObj = json.getAsJsonObject("error");
                            if (errObj.has("message")) {
                                errMsg = errObj.get("message").getAsString();
                            }
                        } else if (json.has("message")) {
                            errMsg = json.get("message").getAsString();
                        }
                        callback.onFailure(errMsg);
                    }
                } catch (Exception parseEx) {
                    callback.onFailure("Failed to parse server response");
                }
            }
        });
    }

    // 11. Delete Transaction from Cloud
    public void deleteTransaction(String transactionId, ApiCallback<JsonObject> callback) {
        String token = sessionManager.getAuthToken();
        if (token == null || token.trim().isEmpty()) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("Authentication token required"));
            }
            return;
        }

        deleteJson("/api/v1/transactions/" + transactionId, token, new NetworkCallback() {
            @Override
            public void onResponse(JsonObject json) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(json));
                }
            }

            @Override
            public void onFailure(String error) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(error));
                }
            }
        });
    }

    private void deleteJson(String endpoint, String authToken, NetworkCallback callback) {
        executeSingleDelete(CLOUD_BASE_URL + endpoint, authToken, new NetworkCallback() {
            @Override
            public void onResponse(JsonObject json) {
                callback.onResponse(json);
            }

            @Override
            public void onFailure(String cloudError) {
                Log.w(TAG, "Cloud DELETE failed (" + cloudError + "). Trying local development host...");
                executeSingleDelete(USB_LOCAL_URL + endpoint, authToken, new NetworkCallback() {
                    @Override
                    public void onResponse(JsonObject json) {
                        callback.onResponse(json);
                    }

                    @Override
                    public void onFailure(String usbError) {
                        executeSingleDelete(EMULATOR_URL + endpoint, authToken, new NetworkCallback() {
                            @Override
                            public void onResponse(JsonObject json) {
                                callback.onResponse(json);
                            }

                            @Override
                            public void onFailure(String emuError) {
                                callback.onFailure(cloudError);
                            }
                        });
                    }
                });
            }
        });
    }

    private void executeSingleDelete(String fullUrl, String authToken, NetworkCallback callback) {
        Request.Builder builder = new Request.Builder()
                .url(fullUrl)
                .delete();

        if (authToken != null && !authToken.trim().isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + authToken);
        }

        httpClient.newCall(builder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onFailure(e.getMessage() != null ? e.getMessage() : "Network request failed");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseStr = response.body() != null ? response.body().string() : "{}";
                try {
                    JsonObject json = JsonParser.parseString(responseStr).getAsJsonObject();
                    if (response.isSuccessful()) {
                        callback.onResponse(json);
                    } else {
                        String errMsg = "Request failed with code " + response.code();
                        if (json.has("error") && json.get("error").isJsonObject()) {
                            JsonObject errObj = json.getAsJsonObject("error");
                            if (errObj.has("message")) {
                                errMsg = errObj.get("message").getAsString();
                            }
                        } else if (json.has("message")) {
                            errMsg = json.get("message").getAsString();
                        }
                        callback.onFailure(errMsg);
                    }
                } catch (Exception parseEx) {
                    callback.onFailure("Failed to parse server response");
                }
            }
        });
    }
}
