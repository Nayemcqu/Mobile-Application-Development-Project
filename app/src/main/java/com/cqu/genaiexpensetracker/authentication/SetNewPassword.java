/**
 * SetNewPassword.java
 * -------------------------
 * Author: Kapil Pandey
 * Syndey Group
 * <p>
 * Description:
 * This activity allows users to reset their password using a Firebase Dynamic Link (`oobCode`).
 * Users must enter a strong password and confirm it. On success, the password is updated via FirebaseAuth.
 * <p>
 * Features:
 * - Real-time password strength validation
 * - Live matching of confirm password
 * - Firebase confirmPasswordReset integration
 * - Lottie-style success dialog with smooth transition to SignIn
 */

package com.cqu.genaiexpensetracker.authentication;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.cqu.genaiexpensetracker.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Objects;

public class SetNewPassword extends AppCompatActivity {

    // Strong password regex: upper, lower, digit, special char, min 8 chars
    private static final String PASSWORD_REGEX =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$";
    // UI components
    private TextInputEditText newPasswordInput, confirmPasswordInput;
    private TextInputLayout newPasswordLayout, confirmPasswordLayout;
    private TextView newPasswordError, confirmPasswordError;
    private LinearLayout updatePasswordBtn;
    // Visibility toggles
    private boolean isNewPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;
    // Firebase
    private FirebaseAuth mAuth;
    private String oobCode = null;

    /**
     * Lifecycle method. Initializes the layout and FirebaseAuth.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.auth_activity_set_new_password);

        mAuth = FirebaseAuth.getInstance();
        extractOobCodeFromIntent();
        initializeViews();
        setupListeners();
    }

    /**
     * Extracts the one-time reset code from the Firebase dynamic link.
     */
    private void extractOobCodeFromIntent() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("oobCode")) {
            oobCode = intent.getStringExtra("oobCode");
        }

        if (oobCode == null || oobCode.isEmpty()) {
            showDialog("Invalid Link", "Reset link is invalid or expired. Please request a new one.");
        }
    }

    /**
     * Binds UI elements and buttons to their XML IDs.
     */
    private void initializeViews() {
        newPasswordInput = findViewById(R.id.update_new_password);
        confirmPasswordInput = findViewById(R.id.update_confirm_password);

        newPasswordLayout = findViewById(R.id.new_password_input_layout);
        confirmPasswordLayout = findViewById(R.id.confirm_password_input_layout);

        newPasswordError = findViewById(R.id.new_password_error);
        confirmPasswordError = findViewById(R.id.update_password_error);

        updatePasswordBtn = findViewById(R.id.update_btn);
    }

    /**
     * Sets up visibility toggles and real-time input validation.
     */
    private void setupListeners() {
        newPasswordInput.addTextChangedListener(new ValidationWatcher(newPasswordInput));
        confirmPasswordInput.addTextChangedListener(new ValidationWatcher(confirmPasswordInput));

        newPasswordLayout.setEndIconOnClickListener(v -> toggleVisibility(true));
        confirmPasswordLayout.setEndIconOnClickListener(v -> toggleVisibility(false));

        updatePasswordBtn.setOnClickListener(v -> {
            hideKeyboard();
            validateAndUpdatePassword();
        });
    }

    /**
     * Shows or hides password fields.
     *
     * @param isPrimary true for newPasswordInput, false for confirmPasswordInput
     */
    private void toggleVisibility(boolean isPrimary) {
        TextInputEditText input = isPrimary ? newPasswordInput : confirmPasswordInput;
        boolean visible = isPrimary ? isNewPasswordVisible : isConfirmPasswordVisible;

        input.setInputType(visible
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setSelection(Objects.requireNonNull(input.getText()).length());

        TextInputLayout layout = isPrimary ? newPasswordLayout : confirmPasswordLayout;
        layout.setEndIconDrawable(visible ? R.drawable.ic_eye_hidden : R.drawable.ic_eye_visible);

        if (isPrimary) isNewPasswordVisible = !isNewPasswordVisible;
        else isConfirmPasswordVisible = !isConfirmPasswordVisible;
    }

    /**
     * Validates password and confirmPassword fields, then updates via Firebase if valid.
     */
    private void validateAndUpdatePassword() {
        String password = Objects.requireNonNull(newPasswordInput.getText()).toString().trim();
        String confirm = Objects.requireNonNull(confirmPasswordInput.getText()).toString().trim();
        boolean isValid = true;

        if (password.isEmpty()) {
            showError(newPasswordLayout, newPasswordError, "Password is required.");
            isValid = false;
        } else if (!password.matches(PASSWORD_REGEX)) {
            showError(newPasswordLayout, newPasswordError,
                    "Password must include uppercase, lowercase, digit, special char, and 8+ chars.");
            isValid = false;
        } else {
            clearError(newPasswordLayout, newPasswordError);
        }

        if (confirm.isEmpty()) {
            showError(confirmPasswordLayout, confirmPasswordError, "Please confirm your password.");
            isValid = false;
        } else if (!confirm.equals(password)) {
            showError(confirmPasswordLayout, confirmPasswordError, "Passwords do not match.");
            isValid = false;
        } else {
            clearError(confirmPasswordLayout, confirmPasswordError);
            confirmPasswordError.setText(R.string.password_match);
            confirmPasswordError.setTextColor(ContextCompat.getColor(this, R.color.green));
            confirmPasswordError.setVisibility(View.VISIBLE);
        }

        if (isValid && oobCode != null) {
            mAuth.confirmPasswordReset(oobCode, password)
                    .addOnSuccessListener(unused -> showPasswordResetSuccessDialog())
                    .addOnFailureListener(e -> showDialog("Failed", "Could not update password. Try requesting another link."));
        } else if (oobCode == null) {
            showDialog("Error", "Reset code missing. Please try again.");
        }
    }

    /**
     * Displays a success dialog and navigates to SignIn after a delay.
     */
    private void showPasswordResetSuccessDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_login_success);
        dialog.setCancelable(false);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setDimAmount(0.5f);
            dialog.getWindow().setGravity(Gravity.CENTER);
        }

        TextView successText = dialog.findViewById(R.id.success_message_text);
        if (successText != null) {
            successText.setText("Updated Successfully!");
        }

        dialog.show();

        new Handler().postDelayed(() -> {
            dialog.dismiss();
            Intent intent = new Intent(SetNewPassword.this, SignIn.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }, 3200);
    }

    /**
     * Shows red error styling on a field.
     */
    private void showError(TextInputLayout layout, TextView errorView, String message) {
        layout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.red));
        errorView.setText(message);
        errorView.setTextColor(ContextCompat.getColor(this, R.color.red));
        errorView.setVisibility(View.VISIBLE);
    }

    /**
     * Clears red styling and sets green border when valid.
     */
    private void clearError(TextInputLayout layout, TextView errorView) {
        layout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.green));
        errorView.setVisibility(View.GONE);
    }

    /**
     * Displays an alert dialog with title and message.
     */
    private void showDialog(String title, String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setIcon(R.drawable.ic_error_icon)
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * Hides keyboard when user taps outside fields.
     */
    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Detects screen touches to dismiss keyboard.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v != null) {
                v.clearFocus();
                hideKeyboard();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * Live validation for both password and confirmPassword fields.
     * Shows appropriate error messages and live feedback as user types.
     */
    private class ValidationWatcher implements TextWatcher {
        private final View view;

        public ValidationWatcher(View view) {
            this.view = view;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            String password = newPasswordInput.getText().toString().trim();
            String confirm = confirmPasswordInput.getText().toString().trim();

            if (view == newPasswordInput) {
                // Validate new password field
                if (password.isEmpty()) {
                    showError(newPasswordLayout, newPasswordError, "Password is required.");
                } else if (!password.matches(PASSWORD_REGEX)) {
                    showError(newPasswordLayout, newPasswordError,
                            "Password must include uppercase, lowercase, digit, special char, and 8+ chars.");
                } else {
                    clearError(newPasswordLayout, newPasswordError);
                }

                // Also validate confirm field if already typed
                if (!confirm.isEmpty()) {
                    if (confirm.equals(password)) {
                        clearError(confirmPasswordLayout, confirmPasswordError);
                        confirmPasswordError.setText(R.string.password_match);
                        confirmPasswordError.setTextColor(ContextCompat.getColor(SetNewPassword.this, R.color.green));
                        confirmPasswordError.setVisibility(View.VISIBLE);
                    } else {
                        showError(confirmPasswordLayout, confirmPasswordError, "Passwords do not match.");
                    }
                }

            } else if (view == confirmPasswordInput) {

                // Validate confirm password field
                if (confirm.isEmpty()) {
                    showError(confirmPasswordLayout, confirmPasswordError, "Please confirm your password.");
                } else if (confirm.equals(password)) {
                    clearError(confirmPasswordLayout, confirmPasswordError);
                    confirmPasswordError.setText(R.string.password_match);
                    confirmPasswordError.setTextColor(ContextCompat.getColor(SetNewPassword.this, R.color.green));
                    confirmPasswordError.setVisibility(View.VISIBLE);
                } else {
                    showError(confirmPasswordLayout, confirmPasswordError, "Passwords do not match.");
                }
            }
        }
    }
}
