package com.cqu.genaiexpensetracker.App;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.cqu.genaiexpensetracker.R;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Preload Remote Config before anything else
        FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600) // 1 hour
                .build();
        remoteConfig.setConfigSettingsAsync(configSettings);

        remoteConfig.fetchAndActivate().addOnCompleteListener(task -> {
            // Now Remote Config values are ready
            startActivity(new Intent(MainActivity.this, Welcome.class));
            finish(); // close splash immediately after config load
        });
    }
}
