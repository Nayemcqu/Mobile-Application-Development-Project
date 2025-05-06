/**
 * ForgotPassword.java
 * -------------------------
 * Author: Kapil Pandey
 * Syndey Group
 *
 * Description:
 * This activity allows users to reset their password by:
 * - Validating email format
 * - Checking user existence in Firestore
 * - Sending Firebase reset email if valid
 *
 * Features:
 * - Live email validation with green check indicator
 * - Custom progress dialog for feedback
 * - Integration with Firebase Authentication and Firestore
 */

package com.cqu.genaiexpensetracker;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Objects;

public class ForgotPassword extends AppCompatActivity {

    // UI Components
    private ImageView backBtn;
    private TextInputEditText emailInput;
    private TextInputLayout emailLayout;
    private TextView emailError;
    private Button nextBtn;
    private ProgressDialog progressDialog;

    // Firebase
    private FirebaseAuth mAuth;

    /**
     * Initializes the activity and binds views.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        mAuth = FirebaseAuth.getInstance();
        initViews();
        setupListeners();
    }

    /**
     * Initializes UI components and progress dialog.
     */
    private void initViews() {
        backBtn = findViewById(R.id.login_back_btn);
        emailInput = findViewById(R.id.forgot_email);
        emailLayout = findViewById(R.id.forgot_email_input_layout);
        emailError = findViewById(R.id.forgot_email_error);
        nextBtn = findViewById(R.id.forgot_password);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Checking email...");
        progressDialog.setCancelable(false);
    }

    /**
     * Sets up event listeners for buttons and input fields.
     */
    private void setupListeners() {
        // Navigate back to SignIn
        backBtn.setOnClickListener(v -> {
            startActivity(new Intent(ForgotPassword.this, SignIn.class));
            finish();
        });

        // Submit button to validate and check email
        nextBtn.setOnClickListener(v -> {
            hideKeyboard();
            validateAndCheckEmail();
        });

        // Email input validation with real-time feedback
        emailInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

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
     * Validates input and checks Firestore for existing account.
     */
    private void validateAndCheckEmail() {
        String rawEmail = Objects.requireNonNull(emailInput.getText()).toString();
        String email = rawEmail.trim().toLowerCase();

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

        progressDialog.setMessage("Checking email...");
        progressDialog.show();

        FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();
                    if (task.isSuccessful()) {
                        if (!task.getResult().isEmpty()) {
                            clearError();
                            sendResetEmail(email);
                        } else {
                            showError("This email is not registered.");
                            removeGreenTick();
                        }
                    } else {
                        showError("Failed to check registration. Try again.");
                        removeGreenTick();
                    }
                });
    }

    /**
     * Sends a Firebase password reset email.
     *
     * @param email The validated and registered email address
     */
    private void sendResetEmail(String email) {
        progressDialog.show();
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Password reset email sent!", Toast.LENGTH_LONG).show();
                        startActivity(new Intent(ForgotPassword.this, SignIn.class));
                        finish();
                    } else {
                        showError("Failed to send reset email. Try again.");
                        removeGreenTick();
                    }
                });
    }

    /**
     * Displays an error with red border and error message.
     *
     * @param message The message to show
     */
    private void showError(String message) {
        emailLayout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.red));
        emailError.setText(message);
        emailError.setTextColor(ContextCompat.getColor(this, R.color.red));
        emailError.setVisibility(View.VISIBLE);
    }

    /**
     * Clears validation error and shows green border.
     */
    private void clearError() {
        emailLayout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.green));
        emailError.setVisibility(View.GONE);
    }

    /**
     * Displays green checkmark on valid email format.
     */
    private void showGreenTick() {
        emailLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        emailLayout.setEndIconDrawable(R.drawable.ic_check_green);
        emailLayout.setEndIconTintList(ContextCompat.getColorStateList(this, R.color.green));
    }

    /**
     * Removes green tick from the email field.
     */
    private void removeGreenTick() {
        emailLayout.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        emailLayout.setEndIconDrawable(null);
    }

    /**
     * Hides the keyboard when user taps outside the input field.
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
     * Hides the on-screen keyboard.
     */
    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
