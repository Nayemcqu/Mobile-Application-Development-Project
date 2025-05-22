/**
 * Welcome.java
 * -------------------------
 * Author: Kapil Pandey
 * Syndey Group
 * <p>
 * Description:
 * This activity serves as the app's launcher screen.
 * It also handles Firebase Dynamic Link redirection for:
 * - Password reset
 * - Email verification
 * <p>
 * Features:
 * - Navigates to SignIn when user taps "Get Started"
 * - Detects dynamic link with `mode=resetPassword` or `verifyEmail`
 * - Redirects to SetNewPassword screen with `oobCode` if present
 * - Validates email verification and creates Firestore user record
 */

package com.cqu.genaiexpensetracker;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.firestore.FirebaseFirestore;

public class Welcome extends AppCompatActivity {

    // Dialog to show verifying animation
    private Dialog verifyingDialog;
    private LinearLayout getStartedButton;

    /**
     * Entry point. Checks for dynamic link or sets up "Get Started" button.
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        // Handle dynamic links (cold or warm start)
        Uri link = getIntent().getData();
        if (link != null) {
            handleDynamicLink(link);
        } else {
            FirebaseDynamicLinks.getInstance()
                    .getDynamicLink(getIntent())
                    .addOnSuccessListener(this, data -> {
                        if (data != null && data.getLink() != null) {
                            handleDynamicLink(data.getLink());
                        }
                    })
                    .addOnFailureListener(Throwable::printStackTrace);
        }

        // Default case: "Get Started" button → go to SignIn screen
        getStartedButton = findViewById(R.id.btn_start);
        getStartedButton.setOnClickListener(v -> {
            startActivity(new Intent(Welcome.this, SignIn.class));
            finish();
        });
    }

    /**
     * Called when activity receives a new intent (e.g., resumed via link click).
     *
     * @param intent the incoming intent
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri link = intent.getData();
        if (link != null) {
            handleDynamicLink(link);
        }
    }

    /**
     * Parses and processes Firebase Dynamic Link actions.
     *
     * @param deepLink the parsed URI from Firebase
     */
    private void handleDynamicLink(Uri deepLink) {
        if (deepLink != null) {
            String mode = deepLink.getQueryParameter("mode");
            String oobCode = deepLink.getQueryParameter("oobCode");

            // Case 1: Password Reset
            if ("resetPassword".equals(mode) && oobCode != null) {
                FirebaseAuth.getInstance().verifyPasswordResetCode(oobCode)
                        .addOnSuccessListener(email -> {
                            Intent intent = new Intent(Welcome.this, SetNewPassword.class);
                            intent.putExtra("oobCode", oobCode);
                            startActivity(intent);
                            finish();
                        })
                        .addOnFailureListener(e -> {
                            showErrorDialog("Oops! Password reset link is invalid or expired.");
                        });
            }

            // Case 2: Email Verification
            else if ("verifyEmail".equals(mode) && oobCode != null) {
                FirebaseAuth auth = FirebaseAuth.getInstance();
                FirebaseUser user = auth.getCurrentUser();

                if (user == null) {
                    Toast.makeText(this, "Please sign in to verify your email.", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, SignIn.class));
                    finish();
                    return;
                }

                auth.applyActionCode(oobCode).addOnSuccessListener(unused -> {
                    showVerifyingLoader(); // Show loader before reload

                    user.reload().addOnSuccessListener(reloaded -> {
                        if (user.isEmailVerified()) {
                            // Create Firestore user after verification
                            String uid = user.getUid();
                            String name = user.getDisplayName() != null ? user.getDisplayName() : "User";
                            String email = user.getEmail();

                            FirebaseFirestore.getInstance()
                                    .collection("users")
                                    .document(uid)
                                    .set(new userModel(name, email, uid))
                                    .addOnSuccessListener(success -> {
                                        hideVerifyingLoader();
                                        showEmailVerifiedDialog();
                                    });
                        }
                    });
                }).addOnFailureListener(e -> {
                    showErrorDialog("Oops! Email verification link is invalid or expired.");
                });
            }
        }
    }

    /**
     * Shows a Lottie-based success dialog after email verification, then navigates to SignIn.
     */
    private void showEmailVerifiedDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_email_verified);
        dialog.setCancelable(false);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setDimAmount(0.6f);
            dialog.getWindow().setGravity(Gravity.CENTER);
        }

        dialog.show();

        new Handler().postDelayed(() -> {
            dialog.dismiss();
            startActivity(new Intent(this, SignIn.class));
            finish();
        }, 2500); // Show for 2.5 seconds
    }

    /**
     * Displays the verifying email loading dialog with custom message.
     */
    private void showVerifyingLoader() {
        verifyingDialog = new Dialog(this);
        verifyingDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        verifyingDialog.setContentView(R.layout.dialog_loading_screen); // reused layout
        verifyingDialog.setCancelable(false);

        if (verifyingDialog.getWindow() != null) {
            verifyingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            verifyingDialog.getWindow().setDimAmount(0.4f);
            verifyingDialog.getWindow().setGravity(Gravity.CENTER);
        }

        // Set custom message dynamically
        TextView messageText = verifyingDialog.findViewById(R.id.loading_text);
        if (messageText != null) {
            messageText.setText("Verifying your email...");
        }

        verifyingDialog.show();
    }

    /**
     * Displays a reusable error dialog with a dynamic message.
     * Navigates user back to SignIn after dismissal.
     *
     * @param message The error message to display in the dialog
     */
    private void showErrorDialog(String message) {
        Dialog errorDialog = new Dialog(this);
        errorDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        errorDialog.setContentView(R.layout.dialog_login_error);
        errorDialog.setCancelable(false);

        if (errorDialog.getWindow() != null) {
            // Set transparent background and dim level
            errorDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            errorDialog.getWindow().setDimAmount(0.5f);
            errorDialog.getWindow().setGravity(Gravity.CENTER);
        }

        TextView errorMessage = errorDialog.findViewById(R.id.error_message);
        if (errorMessage != null) {
            // dynamically set your error text
            errorMessage.setText(message);
        }

        Button okButton = errorDialog.findViewById(R.id.error_ok_button);
        okButton.setOnClickListener(v -> {
            errorDialog.dismiss();
            startActivity(new Intent(this, SignIn.class));
            finish();
        });

        errorDialog.show();
    }

    /**
     * Dismisses the verifying email loading dialog.
     */
    private void hideVerifyingLoader() {
        if (verifyingDialog != null && verifyingDialog.isShowing()) {
            verifyingDialog.dismiss();
        }
    }
}
