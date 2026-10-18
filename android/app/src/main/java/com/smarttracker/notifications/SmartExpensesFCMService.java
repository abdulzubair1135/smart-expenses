package com.smarttracker.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.smarttracker.R;
import com.smarttracker.core.SessionManager;
import com.smarttracker.ui.home.MainActivity;

/**
 * Smart Expenses FCM Service
 * Handles incoming push notifications and token refresh from Firebase Cloud Messaging.
 * Pure Java — zero Kotlin.
 */
public class SmartExpensesFCMService extends FirebaseMessagingService {

    private static final String TAG = "SmartExpensesFCM";
    private static final String CHANNEL_ID = "smart_expenses_notifications";
    private static final String CHANNEL_NAME = "Smart Expenses Alerts";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token: " + token);
        // Save token locally for backend sync
        uploadTokenToBackend(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "FCM message received from: " + remoteMessage.getFrom());

        // Build notification title & body
        String title = "Smart Expenses";
        String body = "New notification";

        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null) {
                title = remoteMessage.getNotification().getTitle();
            }
            if (remoteMessage.getNotification().getBody() != null) {
                body = remoteMessage.getNotification().getBody();
            }
        } else if (!remoteMessage.getData().isEmpty()) {
            // Data-only message
            if (remoteMessage.getData().containsKey("title")) {
                title = remoteMessage.getData().get("title");
            }
            if (remoteMessage.getData().containsKey("body")) {
                body = remoteMessage.getData().get("body");
            }
        }

        showNotification(title, body);
    }

    private void showNotification(String title, String body) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Create notification channel (required for Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Smart Expenses transaction and reminder alerts");
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        // Intent to open app on notification tap
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private void uploadTokenToBackend(String token) {
        // Use WorkManager to sync token to backend in background
        // SessionManager stores the token locally for retry on next API call
        try {
            Context context = getApplicationContext();
            // Save token in SharedPreferences for ApiClient to pick up on next sync
            context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("fcm_token", token)
                    .apply();
            Log.d(TAG, "FCM token saved locally for backend sync");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save FCM token: " + e.getMessage());
        }
    }
}
