/**
 * SetNewPassword.java
 * -------------------------
 * Author: Kapil Pandey
 * Syndey Group
 *
 * Description:
 * This activity allows users to reset their password via Firebase Dynamic Link.
 * The user must enter a strong password and confirm it before it is updated in Firebase.
 *
 * Features:
 * - Password strength validation
 * - Real-time input feedback
 * - Firebase confirmPasswordReset integration
 * - Redirects to SignIn screen after success
 */

package com.cqu.genaiexpensetracker;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Objects;

public class SetNewPassword extends AppCompatActivity {

    // UI components
    private TextInputEditText newPasswordInput, confirmPasswordInput;
    private TextInputLayout newPasswordLayout, confirmPasswordLayout;
    private TextView newPasswordError, confirmPasswordError;
    private Button updatePasswordBtn;

    // State flags
    private boolean isNewPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    // Firebase and oobCode from reset link
    private FirebaseAuth mAuth;
    private String oobCode = null;
    private ProgressDialog progressDialog;

    /**
     * Entry point. Sets layout and initializes components.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_new_password);

        mAuth = FirebaseAuth.getInstance();
        extractOobCodeFromIntent();
        initializeViews();
        setupListeners();
    }

    /**
     * Extracts the oobCode from the incoming intent (Firebase dynamic link).
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
     * Binds UI components and sets up the progress dialog.
     */
    private void initializeViews() {
        newPasswordInput = findViewById(R.id.update_new_password);
        confirmPasswordInput = findViewById(R.id.update_confirm_password);

        newPasswordLayout = findViewById(R.id.new_password_input_layout);
        confirmPasswordLayout = findViewById(R.id.confirm_password_input_layout);

        newPasswordError = findViewById(R.id.new_password_error);
        confirmPasswordError = findViewById(R.id.update_password_error);

        updatePasswordBtn = findViewById(R.id.update_btn);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Updating password...");
        progressDialog.setCancelable(false);
    }

    /**
     * Sets up real-time input validation and button behavior.
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
     * Toggles password visibility between masked and plain text.
     *
     * @param isPrimary true if toggling new password field, false for confirm field
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
     * Validates both password fields and updates password via Firebase if valid.
     */
    private void validateAndUpdatePassword() {
        String newPassword = Objects.requireNonNull(newPasswordInput.getText()).toString().trim();
        String confirmPassword = Objects.requireNonNull(confirmPasswordInput.getText()).toString().trim();
        boolean isValid = true;

        if (newPassword.isEmpty()) {
            showError(newPasswordLayout, newPasswordError, "Password is required.");
            isValid = false;
        } else if (!newPassword.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$")) {
            showError(newPasswordLayout, newPasswordError,
                    "Password must include uppercase, lowercase, digit, special char, and 8+ chars.");
            isValid = false;
        } else {
            clearError(newPasswordLayout, newPasswordError);
        }

        if (confirmPassword.isEmpty()) {
            showError(confirmPasswordLayout, confirmPasswordError, "Please confirm your password.");
            isValid = false;
        } else if (!confirmPassword.equals(newPassword)) {
            showError(confirmPasswordLayout, confirmPasswordError, "Passwords do not match.");
            isValid = false;
        } else {
            clearError(confirmPasswordLayout, confirmPasswordError);
            confirmPasswordError.setText(R.string.password_match);
            confirmPasswordError.setTextColor(ContextCompat.getColor(this, R.color.green));
            confirmPasswordError.setVisibility(View.VISIBLE);
        }

        if (isValid && oobCode != null) {
            progressDialog.show();
            mAuth.confirmPasswordReset(oobCode, newPassword)
                    .addOnSuccessListener(unused -> {
                        progressDialog.dismiss();
                        showSuccessDialog();
                    })
                    .addOnFailureListener(e -> {
                        progressDialog.dismiss();
                        showDialog("Failed", "Could not update password. Try requesting another link.");
                    });
        } else if (oobCode == null) {
            showDialog("Error", "Reset code missing. Please try again.");
        }
    }

    /**
     * Shows a red error message and border for the input field.
     */
    private void showError(TextInputLayout layout, TextView errorView, String message) {
        layout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.red));
        errorView.setText(message);
        errorView.setTextColor(ContextCompat.getColor(this, R.color.red));
        errorView.setVisibility(View.VISIBLE);
    }

    /**
     * Clears the error and displays a green border for valid input.
     */
    private void clearError(TextInputLayout layout, TextView errorView) {
        layout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.green));
        errorView.setVisibility(View.GONE);
    }

    /**
     * Real-time validation watcher to clear error on user input.
     */
    private class ValidationWatcher implements TextWatcher {
        private final View view;

        public ValidationWatcher(View view) {
            this.view = view;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            if (view == newPasswordInput) {
                clearError(newPasswordLayout, newPasswordError);
            } else if (view == confirmPasswordInput) {
                clearError(confirmPasswordLayout, confirmPasswordError);
            }
        }
    }

    /**
     * Shows an error dialog with custom icon and message.
     *
     * @param title   Dialog title
     * @param message Dialog message
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
     * Shows success dialog and redirects to the SignIn screen.
     */
    private void showSuccessDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Password Updated")
                .setMessage("Your password has been reset successfully. Please sign in with your new password.")
                .setIcon(R.drawable.ic_success_updated_password)
                .setCancelable(false)
                .setPositiveButton("Sign In", (dialog, which) -> {
                    dialog.dismiss();
                    Intent intent = new Intent(SetNewPassword.this, SignIn.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .show();
    }

    /**
     * Hides the soft keyboard from screen.
     */
    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Dismisses keyboard when user touches outside input fields.
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
}
