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
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.smarttracker.R;
import com.smarttracker.data.local.AppDatabase;
import com.smarttracker.data.session.SessionManager;
import com.smarttracker.ui.home.MainActivity;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Pure Java Periodic Notification Worker
 * Triggers every 30 minutes to remind users to log their expenses and updates.
 */
public class ExpenseReminderWorker extends Worker {

    private static final String TAG = "ExpenseReminderWorker";
    public static final String WORK_NAME = "PeriodicExpenseReminderWork";
    private static final String CHANNEL_ID = "smart_expenses_periodic_reminders";
    private static final String CHANNEL_NAME = "Kharcha & Expense Reminders";

    private static final String[] PROMPTS = new String[]{
            "Aaj kya kharida? 🛍️ Kharcha note karna na bhoolein!",
            "Chai, nashta ya lunch kiya? ☕ Turant track karein!",
            "30 min check-in: Koi naya transaction hua kya? ✍️",
            "Smart Saver Reminder: Apne kharche update rakhein aur paise bachayein! 💰",
            "Petrol, recharge ya shopping? Ek click mein mic se bol kar add karein! 🎙️",
            "Aaj ka hisab kitab complete hai? Check karein Smart Expenses mein!"
    };

    public ExpenseReminderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SessionManager sessionManager = SessionManager.getInstance(context);

        // Only show notification if user is logged in
        if (sessionManager.isLoggedIn()) {
            sendReminderNotification(context);
        }
        return Result.success();
    }

    public static void sendReminderNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Smart Expenses regular periodic expense reminders");
            channel.enableVibration(true);
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                1024,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Random random = new Random();
        String message = PROMPTS[random.nextInt(PROMPTS.length)];

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setColor(androidx.core.content.ContextCompat.getColor(context, R.color.brand_primary))
                .setContentTitle("Smart Expenses Reminder 🔔")
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        manager.notify(2001, builder.build());
        Log.d(TAG, "Sent 30-min periodic expense reminder");
    }

    /**
     * Schedules the worker to run every 30 minutes using WorkManager
     */
    public static void schedulePeriodicReminder(@NonNull Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                ExpenseReminderWorker.class,
                30,
                TimeUnit.MINUTES
        ).build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
        );
        Log.d(TAG, "Enqueued periodic 30-min expense reminder work");
    }
}
