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
 * Firebase Messaging Service to receive FCM alert-type messages.
 * - Displays modern notifications when an alert is received
 * - Redirects to Notifications screen
 * - Saves FCM token to Firestore on refresh
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCM_Service";
    private static final String CHANNEL_ID = "ai_insight_channel";

    /**
     * Handles incoming FCM alert messages.
     */
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "FCM Message received: " + remoteMessage.getData());

        String title = "Financial Alert";
        String message = "You have a new financial alert.";
        int iconResId = R.drawable.ic_stat_alert;

        // Override with payload (if present)
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            message = remoteMessage.getNotification().getBody();
        }

        Map<String, String> data = remoteMessage.getData();
        if ("Alert".equalsIgnoreCase(data.get("type"))) {
            title = "Financial Alert";
            iconResId = R.drawable.ic_stat_alert;
        }

        if (data.containsKey("message")) {
            message = data.get("message");
        }

        sendNotification(title, message, iconResId);
    }

    /**
     * Builds and shows the alert notification.
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
                    "AI Alert Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Receive financial alerts based on income and spending.");
            manager.createNotificationChannel(channel);
        }

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    /**
     * Saves the new FCM token to Firestore for the signed-in user.
     */
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "FCM token refreshed: " + token);

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .update("fcmToken", token)
                    .addOnSuccessListener(aVoid ->
                            Log.d(TAG, "FCM token updated in Firestore"))
                    .addOnFailureListener(e ->
                            Log.e(TAG, "Failed to update FCM token", e));
        }
    }
}
