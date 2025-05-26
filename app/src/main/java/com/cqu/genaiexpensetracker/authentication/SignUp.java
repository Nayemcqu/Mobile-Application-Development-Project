/**
 * SignUp.java
 * -------------------------
 * Author: Kapil Pandey
 * Syndey Group
 * <p>
 * Description:
 * This activity handles user sign-up using Firebase Authentication.
 * It validates the user's name, email, and password (with confirm password) using live feedback.
 * Enforces strong password rules and input formatting. Shows loading and success dialogs.
 * After sign-up, an email verification is sent, and the user is redirected to the SignIn screen.
 */

package com.cqu.genaiexpensetracker.authentication;

import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.cqu.genaiexpensetracker.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.Objects;

/**
 * Handles user registration including:
 * - Input validation (name, email, password)
 * - Live feedback as users type
 * - Firebase Authentication sign-up
 * - Email verification
 * - UI feedback (success and error dialogs)
 */

public class SignUp extends AppCompatActivity {

    private static final String NAME_PATTERN = "^[a-zA-Z\\s\\-'.]{4,}$";
    private static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$";
    // UI Components
    private TextInputEditText nameInput, emailInput, passwordInput, confirmPasswordInput;
    private TextInputLayout nameInputLayout, emailInputLayout, passwordInputLayout, confirmPasswordInputLayout;
    private TextView nameErrorText, emailErrorText, passwordErrorText, confirmPasswordErrorText;
    private LinearLayout signupButton;
    private TextView signUpText;
    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    // Input validation flags
    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;
    private boolean isNameValid = false;
    private boolean isEmailValid = false;
    private boolean isPasswordValid = false;
    private boolean isConfirmPasswordValid = false;

    /**
     * Initializes the activity and sets up Firebase and UI.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.auth_activity_signup);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initializeViews();
        setupListeners();
        updateSignupButtonState();
    }

    /**
     * Finds and binds all UI components from the layout.
     * Also sets input filters and icon behaviors.
     */
    private void initializeViews() {
        nameInput = findViewById(R.id.signup_name);
        emailInput = findViewById(R.id.signup_email);
        passwordInput = findViewById(R.id.signup_password);
        confirmPasswordInput = findViewById(R.id.signup_confirm_password);

        nameInputLayout = findViewById(R.id.name_input_layout);
        emailInputLayout = findViewById(R.id.email_input_layout);
        passwordInputLayout = findViewById(R.id.password_input_layout);
        confirmPasswordInputLayout = findViewById(R.id.confirm_password_input_layout);

        nameErrorText = findViewById(R.id.name_error);
        emailErrorText = findViewById(R.id.email_error);
        passwordErrorText = findViewById(R.id.password_error);
        confirmPasswordErrorText = findViewById(R.id.confirm_password_error);

        signupButton = findViewById(R.id.signup_btn);
        signUpText = findViewById(R.id.sign_up_text);
        TextView signInBtn = findViewById(R.id.text_signIn);

        nameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(50)});
        nameInputLayout.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        emailInputLayout.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);

        nameInputLayout.setEndIconTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.black)));
        emailInputLayout.setEndIconTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.black)));

        signInBtn.setOnClickListener(v -> startActivity(new Intent(this, SignIn.class)));
    }

    /**
     * Attaches input listeners and click handlers.
     * Applies real-time validation and visibility toggles.
     */
    private void setupListeners() {
        nameInput.addTextChangedListener(new ValidationTextWatcher(nameInput));
        emailInput.addTextChangedListener(new ValidationTextWatcher(emailInput));
        passwordInput.addTextChangedListener(new ValidationTextWatcher(passwordInput));
        confirmPasswordInput.addTextChangedListener(new ValidationTextWatcher(confirmPasswordInput));

        passwordInputLayout.setEndIconOnClickListener(v -> togglePasswordVisibility());
        confirmPasswordInputLayout.setEndIconOnClickListener(v -> toggleConfirmPasswordVisibility());

        signupButton.setOnClickListener(v -> validateAndRegister());
    }

    /**
     * Validates all user inputs and triggers registration via Firebase Auth.
     * Shows loader and handles Firebase success/failure callbacks.
     */
    private void validateAndRegister() {
        hideKeyboard();
        validateName();
        validateEmail();
        validatePassword();
        validateConfirmPassword();


        if (isNameValid && isEmailValid && isPasswordValid && isConfirmPasswordValid) {
            String name = Objects.requireNonNull(nameInput.getText()).toString().trim();
            String email = Objects.requireNonNull(emailInput.getText()).toString().trim();
            String password = Objects.requireNonNull(passwordInput.getText()).toString();

            Dialog loadingDialog = new Dialog(this);
            loadingDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            loadingDialog.setContentView(R.layout.dialog_loading_screen);
            loadingDialog.setCancelable(false);

            if (loadingDialog.getWindow() != null) {
                loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                loadingDialog.getWindow().setDimAmount(0.5f);
                loadingDialog.getWindow().setGravity(Gravity.CENTER);
            }

            TextView messageText = loadingDialog.findViewById(R.id.loading_text);
            if (messageText != null) {
                messageText.setText("Please wait...");
            }

            loadingDialog.show();

            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener(authResult -> {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            // Update display name so Welcome.java can fetch correctly
                            firebaseUser.updateProfile(
                                    new UserProfileChangeRequest.Builder()
                                            .setDisplayName(name)
                                            .build()
                            );

                            firebaseUser.sendEmailVerification()
                                    .addOnSuccessListener(unused -> {
                                        syncFcmTokenToFirestore(); // FCM Token Store the Firebase
                                        loadingDialog.dismiss();
                                        showVerificationSentDialog();
                                    })

                                    .addOnFailureListener(e -> {
                                        loadingDialog.dismiss();
                                        Toast.makeText(SignUp.this, "Failed to send verification email: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    });
                        }
                    })
                    .addOnFailureListener(e -> {
                        loadingDialog.dismiss();
                        if (e instanceof FirebaseAuthUserCollisionException) {
                            setError(emailInputLayout, emailErrorText, "This email is already registered.");
                        } else {
                            Toast.makeText(this, "Registration failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        }
    }

    /**
     * Displays a success dialog once verification email is sent.
     * Redirects to the SignIn screen after a delay.
     */
    private void showVerificationSentDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_verification_sent);
        dialog.setCancelable(false);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setDimAmount(0.6f);
            dialog.getWindow().setGravity(Gravity.CENTER);
        }

        dialog.show();

        new Handler().postDelayed(() -> {
            dialog.dismiss();
            Intent intent = new Intent(SignUp.this, SignIn.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }, 2800);
    }

    /**
     * Toggles the visibility of the password input field.
     */
    private void togglePasswordVisibility() {
        int inputType = isPasswordVisible ? 129 : 144;
        passwordInput.setInputType(inputType);
        passwordInput.setSelection(Objects.requireNonNull(passwordInput.getText()).length());
        passwordInputLayout.setEndIconDrawable(isPasswordVisible ? R.drawable.ic_eye_hidden : R.drawable.ic_eye_visible);
        isPasswordVisible = !isPasswordVisible;
    }

    /**
     * Toggles the visibility of the confirm password input field.
     */
    private void toggleConfirmPasswordVisibility() {
        int inputType = isConfirmPasswordVisible ? 129 : 144;
        confirmPasswordInput.setInputType(inputType);
        confirmPasswordInput.setSelection(Objects.requireNonNull(confirmPasswordInput.getText()).length());
        confirmPasswordInputLayout.setEndIconDrawable(isConfirmPasswordVisible ? R.drawable.ic_eye_hidden : R.drawable.ic_eye_visible);
        isConfirmPasswordVisible = !isConfirmPasswordVisible;
    }

    /**
     * Validates the name input against required format and rules.
     */
    private void validateName() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            nameInputLayout.setEndIconDrawable(null);
            nameInputLayout.setEndIconMode(TextInputLayout.END_ICON_NONE);
            setError(nameInputLayout, nameErrorText, "Please enter your name.");
            isNameValid = false;
        } else if (name.length() < 4) {
            nameInputLayout.setEndIconDrawable(null);
            nameInputLayout.setEndIconMode(TextInputLayout.END_ICON_NONE);
            setError(nameInputLayout, nameErrorText, "Name must be at least 4 characters.");
            isNameValid = false;
        } else if (!name.matches(NAME_PATTERN)) {
            nameInputLayout.setEndIconDrawable(null);
            nameInputLayout.setEndIconMode(TextInputLayout.END_ICON_NONE);
            setError(nameInputLayout, nameErrorText, "Name can include letters, spaces, hyphens, apostrophes, or periods only.");
            isNameValid = false;
        } else {
            setSuccess(nameInputLayout, nameErrorText);
            nameInputLayout.setEndIconDrawable(R.drawable.ic_check_green);
            nameInputLayout.setEndIconTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.green)));
            nameInputLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
            isNameValid = true;
        }
    }

    /**
     * Validates the email input using Android's pattern matcher.
     */
    private void validateEmail() {
        String email = emailInput.getText().toString().trim();
        if (email.isEmpty()) {
            emailInputLayout.setEndIconDrawable(null);
            emailInputLayout.setEndIconMode(TextInputLayout.END_ICON_NONE);
            setError(emailInputLayout, emailErrorText, "Please enter your email.");
            isEmailValid = false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInputLayout.setEndIconDrawable(null);
            emailInputLayout.setEndIconMode(TextInputLayout.END_ICON_NONE);
            setError(emailInputLayout, emailErrorText, "Invalid email format.");
            isEmailValid = false;
        } else {
            setSuccess(emailInputLayout, emailErrorText);
            emailInputLayout.setEndIconDrawable(R.drawable.ic_check_green);
            emailInputLayout.setEndIconTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.green)));
            emailInputLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
            isEmailValid = true;
        }
    }

    /**
     * Validates the password input using a strong password regex.
     */
    private void validatePassword() {
        String password = passwordInput.getText().toString();
        if (password.isEmpty()) {
            setError(passwordInputLayout, passwordErrorText, "Password is required.");
            isPasswordValid = false;
        } else if (!password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$")) {
            setError(passwordInputLayout, passwordErrorText, "Password must include uppercase, lowercase, digit, special char, and 8+ chars.");
            isPasswordValid = false;
        } else {
            setSuccess(passwordInputLayout, passwordErrorText);
            isPasswordValid = true;
        }
    }

    /**
     * Validates the confirm password input and checks if it matches the original password.
     */
    private void validateConfirmPassword() {
        String password = passwordInput.getText().toString();
        String confirmPassword = confirmPasswordInput.getText().toString();
        if (confirmPassword.isEmpty()) {
            setError(confirmPasswordInputLayout, confirmPasswordErrorText, "Please confirm your password.");
            isConfirmPasswordValid = false;
        } else if (!confirmPassword.equals(password)) {
            setError(confirmPasswordInputLayout, confirmPasswordErrorText, "Passwords do not match.");
            isConfirmPasswordValid = false;
        } else {
            setSuccess(confirmPasswordInputLayout, confirmPasswordErrorText);
            confirmPasswordErrorText.setText(R.string.password_match);
            confirmPasswordErrorText.setTextColor(ContextCompat.getColor(this, R.color.green));
            confirmPasswordErrorText.setVisibility(View.VISIBLE);
            isConfirmPasswordValid = true;
        }
    }

    /**
     * Applies error styling and message to the given TextInputLayout and error text view.
     *
     * @param layout    The layout to style
     * @param errorView The error text view to update
     * @param message   The error message to display
     */
    private void setError(TextInputLayout layout, TextView errorView, String message) {
        layout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.red));
        errorView.setText(message);
        errorView.setTextColor(ContextCompat.getColor(this, R.color.red));
        errorView.setVisibility(View.VISIBLE);
    }

    /**
     * Clears error state and applies success styling to the given field.
     *
     * @param layout    The layout to update
     * @param errorView The error text view to hide
     */
    private void setSuccess(TextInputLayout layout, TextView errorView) {
        layout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.green));
        errorView.setVisibility(View.GONE);
    }

    /**
     * Enables or disables the Sign Up button based on validation state of all inputs.
     */
    private void updateSignupButtonState() {
        boolean enable = isNameValid && isEmailValid && isPasswordValid && isConfirmPasswordValid;
        signupButton.setEnabled(enable);
        signupButton.setBackgroundResource(R.drawable.rounded_btn_selector);
        signUpText.setTextColor(ContextCompat.getColor(this, enable ? R.color.white : R.color.grey));
    }

    /**
     * Hides the soft keyboard from the screen.
     */
    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Dismisses keyboard when tapping outside input fields.
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
     * Custom TextWatcher that validates input fields as the user types.
     * Applies rules for name, email, password, and confirm password.
     */
    private class ValidationTextWatcher implements TextWatcher {
        private final View view;

        ValidationTextWatcher(View view) {
            this.view = view;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void afterTextChanged(Editable s) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            String password = passwordInput.getText().toString().trim();
            String confirm = confirmPasswordInput.getText().toString().trim();

            if (view == nameInput) {
                String name = s.toString().trim();
                if (name.isEmpty()) {
                    setError(nameInputLayout, nameErrorText, "Please enter your name.");
                    isNameValid = false;
                } else if (name.length() < 4) {
                    setError(nameInputLayout, nameErrorText, "Name must be at least 4 characters.");
                    isNameValid = false;
                } else if (!name.matches(NAME_PATTERN)) {
                    setError(nameInputLayout, nameErrorText, "Name can include letters, spaces, hyphens, apostrophes, or periods only.");
                    isNameValid = false;
                } else {
                    setSuccess(nameInputLayout, nameErrorText);
                    nameInputLayout.setEndIconDrawable(R.drawable.ic_check_green);
                    nameInputLayout.setEndIconTintList(ColorStateList.valueOf(ContextCompat.getColor(SignUp.this, R.color.green)));
                    nameInputLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
                    isNameValid = true;
                }
            } else if (view == emailInput) {
                String email = s.toString().trim();
                if (email.isEmpty()) {
                    setError(emailInputLayout, emailErrorText, "Please enter your email.");
                    isEmailValid = false;
                } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    setError(emailInputLayout, emailErrorText, "Invalid email format.");
                    isEmailValid = false;
                } else {
                    setSuccess(emailInputLayout, emailErrorText);
                    emailInputLayout.setEndIconDrawable(R.drawable.ic_check_green);
                    emailInputLayout.setEndIconTintList(ColorStateList.valueOf(ContextCompat.getColor(SignUp.this, R.color.green)));
                    emailInputLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
                    isEmailValid = true;
                }
            } else if (view == passwordInput) {
                if (password.isEmpty()) {
                    setError(passwordInputLayout, passwordErrorText, "Password is required.");
                    isPasswordValid = false;
                } else if (!password.matches(PASSWORD_REGEX)) {
                    setError(passwordInputLayout, passwordErrorText,
                            "Password must include uppercase, lowercase, digit, special char, and 8+ chars.");
                    isPasswordValid = false;
                } else {
                    setSuccess(passwordInputLayout, passwordErrorText);
                    isPasswordValid = true;
                }

                // Only validate confirm field if it's not empty
                if (!confirm.isEmpty()) {
                    if (confirm.equals(password)) {
                        setSuccess(confirmPasswordInputLayout, confirmPasswordErrorText);
                        confirmPasswordErrorText.setText(R.string.password_match);
                        confirmPasswordErrorText.setTextColor(ContextCompat.getColor(SignUp.this, R.color.green));
                        confirmPasswordErrorText.setVisibility(View.VISIBLE);
                        isConfirmPasswordValid = true;
                    } else {
                        setError(confirmPasswordInputLayout, confirmPasswordErrorText, "Passwords do not match.");
                        isConfirmPasswordValid = false;
                    }
                }
            } else if (view == confirmPasswordInput) {
                if (confirm.isEmpty()) {
                    setError(confirmPasswordInputLayout, confirmPasswordErrorText, "Please confirm your password.");
                    isConfirmPasswordValid = false;
                } else if (confirm.equals(password)) {
                    setSuccess(confirmPasswordInputLayout, confirmPasswordErrorText);
                    confirmPasswordErrorText.setText(R.string.password_match);
                    confirmPasswordErrorText.setTextColor(ContextCompat.getColor(SignUp.this, R.color.green));
                    confirmPasswordErrorText.setVisibility(View.VISIBLE);
                    isConfirmPasswordValid = true;
                } else {
                    setError(confirmPasswordInputLayout, confirmPasswordErrorText, "Passwords do not match.");
                    isConfirmPasswordValid = false;
                }
            }

            updateSignupButtonState();
        }
    }

    /**
     * Syncs the current device's FCM token to Firestore under users/{uid}/fcmToken.
     * This ensures newly registered users receive notifications immediately.
     */
    private void syncFcmTokenToFirestore() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.getUid())
                    .update("fcmToken", token);
        });
    }


}
