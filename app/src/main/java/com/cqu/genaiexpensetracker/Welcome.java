/**
 * Welcome.java
 * -------------------------
 * Author: Kapil Pandey
 * Syndey Group
 *
 * Description:
 * This activity serves as the app's launcher screen.
 * It also handles Firebase Dynamic Link redirection for password reset.
 *
 * Features:
 * - Navigates to SignIn when user taps "Get Started"
 * - Detects dynamic link with `mode=resetPassword` and extracts `oobCode`
 * - Redirects to SetNewPassword screen with `oobCode` if present
 */

package com.cqu.genaiexpensetracker;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;

public class Welcome extends AppCompatActivity {

    /**
     * Entry point. Checks for dynamic link or sets up "Get Started" button.
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        // Case 1: Handle cold start or intent URI
        handleIntent(getIntent());

        // Case 2: Handle dynamic links via FirebaseDynamicLinks
        FirebaseDynamicLinks.getInstance()
                .getDynamicLink(getIntent())
                .addOnSuccessListener(this, pendingDynamicLinkData -> {
                    if (pendingDynamicLinkData != null && pendingDynamicLinkData.getLink() != null) {
                        handleDynamicLink(pendingDynamicLinkData.getLink());
                    }
                })
                .addOnFailureListener(e -> {
                    e.printStackTrace(); // Optional: log or report
                });

        // "Get Started" button → SignIn screen
        Button getStartedButton = findViewById(R.id.btn_start);
        getStartedButton.setOnClickListener(v -> {
            startActivity(new Intent(Welcome.this, SignIn.class));
            finish();
        });
    }

    /**
     * Called when activity receives a new intent (e.g. resume with link).
     *
     * @param intent the incoming intent
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    /**
     * Handles raw intent data URI to process dynamic links.
     *
     * @param intent the intent passed during cold start or re-entry
     */
    private void handleIntent(Intent intent) {
        Uri data = intent.getData();
        if (data != null) {
            handleDynamicLink(data);
        }
    }

    /**
     * Parses the deep link and navigates if it's a reset password link.
     *
     * @param deepLink the parsed URI from Firebase
     */
    private void handleDynamicLink(Uri deepLink) {
        if (deepLink != null) {
            String mode = deepLink.getQueryParameter("mode");
            String oobCode = deepLink.getQueryParameter("oobCode");

            // Check for password reset link
            if ("resetPassword".equals(mode) && oobCode != null) {
                Intent intent = new Intent(Welcome.this, SetNewPassword.class);
                intent.putExtra("oobCode", oobCode);
                startActivity(intent);
                finish();
            }
        }
    }
}
