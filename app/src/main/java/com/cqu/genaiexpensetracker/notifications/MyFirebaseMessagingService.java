package com.cqu.genaiexpensetracker.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.cqu.genaiexpensetracker.R;
import com.cqu.genaiexpensetracker.navbar.navbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Handles incoming FCM messages and displays system notifications.
 * Dynamically updates icons/messages based on the type (Alert or Advice).
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCM_Service";
    private static final String CHANNEL_ID = "ai_insight_channel";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "FCM Message received: " + remoteMessage.getData());

        // Default values
        String title = "AI Insight";
        String message = "You have a new financial insight!";
        String type = "Advice";  // Default type
        int iconResId = R.drawable.ic_stat_advice;

        // Extract notification payload (if available)
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            message = remoteMessage.getNotification().getBody();
        }

        // Extract data payload
        Map<String, String> data = remoteMessage.getData();
        if (data.containsKey("type")) {
            type = data.get("type");
        }
        if (data.containsKey("message")) {
            message = data.get("message");
        }

        // Determine icon and message based on type
        if ("Alerts".equalsIgnoreCase(type)) {
            iconResId = R.drawable.ic_stat_alert;
            title = "Financial Alert";
        } else if ("Advice".equalsIgnoreCase(type)) {
            iconResId = R.drawable.ic_stat_advice;
            title = "Financial Advice";
        }

        sendNotification(title, message, iconResId);
    }

    /**
     * Displays a system notification with the given title, message, and icon.
     * Navigates to Notifications screen when tapped.
     */
    private void sendNotification(String title, String message, int iconResId) {
        Intent intent = new Intent(this, navbar.class);
        intent.putExtra("navigate_to", "notifications");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(iconResId)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setSound(soundUri)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "AI Insight Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Get alerts and advice from your financial AI assistant.");
            manager.createNotificationChannel(channel);
        }

        manager.notify((int) System.currentTimeMillis(), builder.build()); // unique ID per notification
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "FCM token refreshed: " + token);

        // Save to Firestore if user is logged in
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .update("fcmToken", token)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM token updated in Firestore"))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to update FCM token", e));
        }
    }



}
