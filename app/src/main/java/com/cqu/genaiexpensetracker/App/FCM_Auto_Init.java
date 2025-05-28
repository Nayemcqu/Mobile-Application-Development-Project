package com.cqu.genaiexpensetracker.App;

import android.app.Application;
import com.google.firebase.messaging.FirebaseMessaging;
import android.util.Log;

public class FCM_Auto_Init extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Enable Firebase Cloud Messaging auto-init
        FirebaseMessaging.getInstance().setAutoInitEnabled(true);

        // log FCM token (for debug)
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String token = task.getResult();
                        Log.d("FCM", "FCM Token: " + token);
                    } else {
                        Log.w("FCM", "Fetching FCM token failed", task.getException());
                    }
                });
    }
}
