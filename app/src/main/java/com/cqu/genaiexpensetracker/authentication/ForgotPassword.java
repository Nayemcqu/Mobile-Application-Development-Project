/**
 * ForgotPassword.java
 * -------------------------
 * Author: Kapil Pandey
 * Syndey Group
 * <p>
 * Description:
 * This activity allows users to reset their password by:
 * - Validating email format
 * - Checking user existence in FirebaseAuth
 * - Sending Firebase reset email if valid and password-based
 * <p>
 * Features:
 * - Live email validation with green check indicator
 * - Custom progress dialog for feedback
 * - Integration with Firebase Authentication only
 */

package com.cqu.genaiexpensetracker.authentication;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.cqu.genaiexpensetracker.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Objects;

public class ForgotPassword extends AppCompatActivity {

    // UI Components
    private ImageView backBtn;
    private TextInputEditText emailInput;
    private TextInputLayout emailLayout;
    private TextView emailError;
    private LinearLayout nextBtn;
    private Dialog loadingDialog;

    // Firebase Authentication instance
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.auth_activity_forgot_password);

        // Initialize FirebaseAuth instance
        mAuth = FirebaseAuth.getInstance();

        // Initialize UI components
        initViews();

        // Set up event listeners for UI interactions
        setupListeners();
    }

    /**
     * Initializes UI components by linking them with their corresponding views in the layout.
     */
    private void initViews() {
        backBtn = findViewById(R.id.login_back_btn);
        emailInput = findViewById(R.id.forgot_email);
        emailLayout = findViewById(R.id.forgot_email_input_layout);
        emailError = findViewById(R.id.forgot_email_error);
        nextBtn = findViewById(R.id.forgot_password);
    }

    /**
     * Sets up event listeners for UI components to handle user interactions.
     */
    private void setupListeners() {
        // Navigate back to SignIn activity when back button is clicked
        backBtn.setOnClickListener(v -> {
            startActivity(new Intent(ForgotPassword.this, SignIn.class));
            finish();
        });

        // Validate email and initiate password reset when next button is clicked
        nextBtn.setOnClickListener(v -> {
            hideKeyboard();
            validateAndCheckEmail();
        });

        // Add text change listener to email input for real-time validation
        emailInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String email = s.toString().trim();
                if (TextUtils.isEmpty(email)) {
                    removeGreenTick();
                    emailLayout.setBoxStrokeColor(ContextCompat.getColor(ForgotPassword.this, R.color.red));
                    emailError.setText("Email is required.");
                    emailError.setVisibility(View.VISIBLE);
                } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    removeGreenTick();
                    emailLayout.setBoxStrokeColor(ContextCompat.getColor(ForgotPassword.this, R.color.red));
                    emailError.setText("Enter a valid email address.");
                    emailError.setVisibility(View.VISIBLE);
                } else {
                    showGreenTick();
                    clearError();
                }
            }
        });
    }

    /**
     * Validates the entered email and initiates the password reset process if valid.
     */
    private void validateAndCheckEmail() {
        String email = Objects.requireNonNull(emailInput.getText()).toString().trim().toLowerCase();

        if (TextUtils.isEmpty(email)) {
            showError("Email is required.");
            removeGreenTick();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Enter a valid email address.");
            removeGreenTick();
            return;
        }

        clearError();
        sendResetEmail(email); // Directly send reset email
    }

    /**
     * Sends a password reset email to the provided email address using Firebase Authentication.
     *
     * @param email The email address to send the password reset email to.
     */
    private void sendResetEmail(String email) {
        showVerifyingLoader();
        mAuth.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
            hideVerifyingLoader();
            if (task.isSuccessful()) {
                showPasswordResetSuccessDialog();
            } else {
                // Show generic message to prevent email enumeration
                showError("If this email is registered, you will receive a password reset email.");
                removeGreenTick();
            }
        });
    }

    /**
     * Displays a loading dialog indicating that the email verification is in progress.
     */
    private void showVerifyingLoader() {
        loadingDialog = new Dialog(this);
        loadingDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        loadingDialog.setContentView(R.layout.dialog_loading_screen);
        loadingDialog.setCancelable(false);

        if (loadingDialog.getWindow() != null) {
            loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            loadingDialog.getWindow().setDimAmount(0.4f);
            loadingDialog.getWindow().setGravity(android.view.Gravity.CENTER);
        }

        TextView messageText = loadingDialog.findViewById(R.id.loading_text);
        if (messageText != null) {
            messageText.setText("Checking your email...");
        }

        loadingDialog.show();
    }

    /**
     * Hides the verifying loader dialog if it is currently displayed.
     */
    private void hideVerifyingLoader() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }

    /**
     * Displays a success dialog indicating that the password reset email has been sent.
     * After a short delay, navigates back to the SignIn activity.
     */
    private void showPasswordResetSuccessDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_verification_sent);
        dialog.setCancelable(false);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setDimAmount(0.6f);
            dialog.getWindow().setGravity(android.view.Gravity.CENTER);
        }

        TextView titleText = dialog.findViewById(R.id.email_verification);
        TextView messageText = dialog.findViewById(R.id.email_message);

        if (titleText != null) titleText.setText("Password reset email sent");
        if (messageText != null)
            messageText.setText("Please check your inbox to reset your password.");

        dialog.show();

        new Handler().postDelayed(() -> {
            dialog.dismiss();
            startActivity(new Intent(ForgotPassword.this, SignIn.class));
            finish();
        }, 2500);
    }

    /**
     * Displays an error message below the email input field.
     *
     * @param message The error message to display.
     */
    private void showError(String message) {
        emailLayout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.red));
        emailError.setText(message);
        emailError.setTextColor(ContextCompat.getColor(this, R.color.red));
        emailError.setVisibility(View.VISIBLE);
    }

    /**
     * Clears any displayed error messages and sets the email input field to indicate success.
     */
    private void clearError() {
        emailLayout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.green));
        emailError.setVisibility(View.GONE);
    }

    /**
     * Displays a green tick icon in the email input field to indicate valid input.
     */
    private void showGreenTick() {
        emailLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        emailLayout.setEndIconDrawable(R.drawable.ic_check_green);
        emailLayout.setEndIconTintList(ContextCompat.getColorStateList(this, R.color.green));
    }

    /**
     * Removes the green tick icon from the email input field.
     */
    private void removeGreenTick() {
        emailLayout.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        emailLayout.setEndIconDrawable(null);
    }

    /**
     * Overrides the dispatchTouchEvent to hide the keyboard when the user touches outside the input field.
     *
     * @param ev The motion event.
     * @return True if the event was handled, false otherwise.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View view = getCurrentFocus();
            if (view != null) {
                view.clearFocus();
                hideKeyboard();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * Hides the soft keyboard if it is currently displayed.
     */
    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
