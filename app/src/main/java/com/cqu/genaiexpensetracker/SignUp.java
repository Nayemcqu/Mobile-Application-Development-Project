/**
 * SignUp.java
 * -------------------------
 * Author: Kapil Pandey
 * Syndey Group
 *
 * Description:
 * This activity handles user registration using Firebase Authentication and Firestore.
 * It validates inputs (name, email, password, confirm password) with live feedback,
 * enforces strong password rules, and shows a success dialog on successful registration.
 */

package com.cqu.genaiexpensetracker;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Objects;

public class SignUp extends AppCompatActivity {

    // UI Components
    private TextInputEditText nameInput, emailInput, passwordInput, confirmPasswordInput;
    private TextInputLayout nameInputLayout, emailInputLayout, passwordInputLayout, confirmPasswordInputLayout;
    private TextView nameErrorText, emailErrorText, passwordErrorText, confirmPasswordErrorText;
    private Button signupButton;

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
     * Called when the activity is starting. Initializes Firebase, views, and listeners.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initializeViews();
        setupListeners();
        updateSignupButtonState();
    }

    /**
     * Binds all input fields and visual components to their layout counterparts.
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
        TextView signInBtn = findViewById(R.id.text_signIn);

        nameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(50)});
        nameInputLayout.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        emailInputLayout.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);

        nameInputLayout.setEndIconTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.black)));
        emailInputLayout.setEndIconTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.black)));

        signInBtn.setOnClickListener(v -> startActivity(new Intent(this, SignIn.class)));
    }

    /**
     * Registers all text change and click listeners for input validation and toggles.
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
     * Validates all input fields and attempts to register the user using Firebase.
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

            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener(authResult -> {
                        String uid = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();
                        User user = new User(name, email, uid);

                        db.collection("users").document(uid).set(user)
                                .addOnSuccessListener(unused -> showSuccessDialog())
                                .addOnFailureListener(e -> Toast.makeText(this, "Firestore error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    })
                    .addOnFailureListener(e -> {
                        if (e instanceof FirebaseAuthUserCollisionException) {
                            setError(emailInputLayout, emailErrorText, "This email is already registered.");
                        } else {
                            Toast.makeText(this, "Registration failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        }
    }

    /**
     * Displays a success alert dialog after successful registration.
     * Navigates to the main dashboard screen.
     */
    private void showSuccessDialog() {
        new AlertDialog.Builder(SignUp.this)
                .setTitle("Success!")
                .setMessage("New account successfully created.")
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                    Intent intent = new Intent(SignUp.this, navbar.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .show();
    }

    /**
     * Validates the name input to ensure minimum character length and alphabetic characters.
     */
    private void validateName() {
        String name = nameInput.getText().toString().trim().replaceAll("[^a-zA-Z]", "");
        if (name.length() < 4) {
            setError(nameInputLayout, nameErrorText, "Name must be at least 4 letters.");
            isNameValid = false;
        } else {
            setSuccess(nameInputLayout, nameErrorText);
            nameInputLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
            nameInputLayout.setEndIconDrawable(R.drawable.ic_check_green);
            nameInputLayout.setEndIconTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.green)));
            isNameValid = true;
        }
    }

    /**
     * Validates email input format.
     */
    private void validateEmail() {
        String email = emailInput.getText().toString().trim();

        if (email.isEmpty()) {
            setError(emailInputLayout, emailErrorText, "Please enter your email.");
            isEmailValid = false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            setError(emailInputLayout, emailErrorText, "Invalid email format.");
            isEmailValid = false;
        } else {
            setSuccess(emailInputLayout, emailErrorText);
            emailInputLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
            emailInputLayout.setEndIconDrawable(R.drawable.ic_check_green);
            emailInputLayout.setEndIconTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.green)));
            isEmailValid = true;
        }
    }

    /**
     * Validates password complexity based on defined security rules.
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
     * Confirms that password and confirm password inputs match.
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
     * Displays error UI styles and message on invalid input.
     *
     * @param layout     the TextInputLayout to show error on
     * @param errorView  the TextView that displays the error
     * @param message    the error message text
     */
    private void setError(TextInputLayout layout, TextView errorView, String message) {
        layout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.red));
        errorView.setText(message);
        errorView.setTextColor(ContextCompat.getColor(this, R.color.red));
        errorView.setVisibility(View.VISIBLE);
    }

    /**
     * Displays success UI styles by clearing error messages.
     *
     * @param layout    the input layout to update
     * @param errorView the error label to hide
     */
    private void setSuccess(TextInputLayout layout, TextView errorView) {
        layout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.green));
        errorView.setVisibility(View.GONE);
    }

    /**
     * Updates the sign-up button's enabled state based on validation flags.
     */
    private void updateSignupButtonState() {
        boolean enable = isNameValid && isEmailValid && isPasswordValid && isConfirmPasswordValid;
        signupButton.setEnabled(enable);
        signupButton.setBackgroundTintList(ContextCompat.getColorStateList(this, enable ? R.color.black : R.color.grey_light));
        signupButton.setTextColor(ContextCompat.getColor(this, enable ? R.color.white : R.color.grey));
    }

    /**
     * Hides the keyboard when called from a form action.
     */
    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Intercepts touch events to dismiss keyboard when tapping outside of fields.
     *
     * @param ev the MotionEvent
     * @return true if event handled
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
     * Watches input changes and calls corresponding validation methods.
     */
    private class ValidationTextWatcher implements TextWatcher {
        private final View view;

        ValidationTextWatcher(View view) {
            this.view = view;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void afterTextChanged(Editable s) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (view == nameInput) validateName();
            else if (view == emailInput) validateEmail();
            else if (view == passwordInput || view == confirmPasswordInput) {
                validatePassword();
                validateConfirmPassword();
            }
            updateSignupButtonState();
        }
    }
}
