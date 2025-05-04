/**
 * SignIn.java
 * -------------------------
 * Author: Kapil Pandey
 * Syndey Group
 *
 * Description:
 * This activity manages user authentication using Firebase for:
 * - Email & Password
 * - Google Sign-In
 * - Facebook Login
 *
 * Features:
 * - Live input validation and visual feedback
 * - "Remember Me" support using SharedPreferences
 * - Firestore integration to save user details
 * - Custom loading dialog for smoother transitions
 */

package com.cqu.genaiexpensetracker;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.facebook.*;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Arrays;

public class SignIn extends AppCompatActivity {

    // Firebase and social login
    private FirebaseAuth mAuth;
    private GoogleSignInClient googleSignInClient;
    private CallbackManager callbackManager;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    // UI components
    private TextInputEditText emailInput, passwordInput;
    private TextInputLayout emailInputLayout, passwordInputLayout;
    private TextView emailError, passwordError, loginErrorTitle, loginErrorDescription;
    private Button signInButton, googleSignInButton, facebookSignInButton;
    private CheckBox rememberMeCheckBox;
    private LinearLayout loginErrorContainer;

    // Dialog
    private Dialog loaderDialog;

    // SharedPreferences for Remember Me
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "LoginPrefs";

    // State flags
    private boolean isPasswordVisible = false;
    private boolean isEmailValid = false;

    /**
     * Initializes the activity lifecycle and all components.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);

        initializeFirebase();
        initializeViews();
        initializeGoogleSignIn();
        initializeFacebookLogin();
        setupListeners();
        loadRememberedCredentials();
        updateLoginButtonState();
    }

    /**
     * Initializes FirebaseAuth and Facebook callback manager.
     */
    private void initializeFirebase() {
        mAuth = FirebaseAuth.getInstance();
        callbackManager = CallbackManager.Factory.create();
    }

    /**
     * Initializes all UI views and sets up loader dialog.
     */
    private void initializeViews() {
        emailInput = findViewById(R.id.login_email);
        passwordInput = findViewById(R.id.login_password);
        emailInputLayout = findViewById(R.id.email_input_layout);
        passwordInputLayout = findViewById(R.id.password_input_layout);
        emailError = findViewById(R.id.email_error);
        passwordError = findViewById(R.id.password_error);
        signInButton = findViewById(R.id.sign_in_btn);
        googleSignInButton = findViewById(R.id.button_login_google);
        facebookSignInButton = findViewById(R.id.button_login_facebook);
        rememberMeCheckBox = findViewById(R.id.checkbox_remember);
        loginErrorContainer = findViewById(R.id.login_error_container);
        loginErrorTitle = findViewById(R.id.login_error_title);
        loginErrorDescription = findViewById(R.id.login_error_description);

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        loaderDialog = new Dialog(this);
        loaderDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        loaderDialog.setContentView(R.layout.dialog_loader);
        loaderDialog.setCancelable(false);
        if (loaderDialog.getWindow() != null) {
            loaderDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            loaderDialog.getWindow().setDimAmount(0.6f);
            loaderDialog.getWindow().setGravity(Gravity.CENTER);
        }

        passwordInputLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        passwordInputLayout.setEndIconDrawable(R.drawable.ic_eye_hidden);
        passwordInputLayout.setEndIconTintList(ContextCompat.getColorStateList(this, R.color.black));

        findViewById(R.id.btn_forgot_password).setOnClickListener(v -> startActivity(new Intent(this, ForgotPassword.class)));
        findViewById(R.id.text_sign_up).setOnClickListener(v -> startActivity(new Intent(this, SignUp.class)));
    }

    /**
     * Loads saved email/password if Remember Me was previously selected.
     */
    private void loadRememberedCredentials() {
        if (sharedPreferences.getBoolean("remember", false)) {
            emailInput.setText(sharedPreferences.getString("email", ""));
            passwordInput.setText(sharedPreferences.getString("password", ""));
            rememberMeCheckBox.setChecked(true);
        }
    }

    /**
     * Configures Google Sign-In and handles result.
     */
    private void initializeGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        handleGoogleSignInResult(task);
                    }
                });
    }

    /**
     * Initializes Facebook Login and handles callback events.
     */
    private void initializeFacebookLogin() {
        LoginManager.getInstance().registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            public void onSuccess(LoginResult loginResult) {
                showLoader();
                handleFacebookAccessToken(loginResult.getAccessToken());
            }

            public void onCancel() {
                Toast.makeText(SignIn.this, R.string.error_facebook_cancelled, Toast.LENGTH_SHORT).show();
            }

            public void onError(FacebookException error) {
                Toast.makeText(SignIn.this, R.string.error_facebook_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Attaches field validation and click listeners to inputs and buttons.
     */
    private void setupListeners() {
        emailInput.setOnFocusChangeListener((v, hasFocus) -> validateEmail());
        passwordInput.setOnFocusChangeListener((v, hasFocus) -> validatePassword());

        emailInput.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                validateEmail();
                updateLoginButtonState();
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        passwordInput.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                validatePassword();
                updateLoginButtonState();
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        signInButton.setOnClickListener(v -> validateInputsAndLogin());

        googleSignInButton.setOnClickListener(v ->
                googleSignInClient.signOut().addOnCompleteListener(task -> {
                    showLoader();
                    googleSignInLauncher.launch(googleSignInClient.getSignInIntent());
                }));

        facebookSignInButton.setOnClickListener(v ->
                LoginManager.getInstance().logInWithReadPermissions(this, Arrays.asList("email", "public_profile")));

        passwordInputLayout.setEndIconOnClickListener(v -> togglePasswordVisibility());
    }

    /**
     * Validates user input and performs Firebase login if valid.
     */
    private void validateInputsAndLogin() {
        hideKeyboardAndClearFocus();
        validateEmail();
        validatePassword();

        if (isEmailValid && passwordError.getVisibility() == View.GONE) {
            performLogin(emailInput.getText().toString().trim(), passwordInput.getText().toString().trim());
        }
    }

    /**
     * Logs the user in with email/password via Firebase.
     *
     * @param email    The entered email
     * @param password The entered password
     */
    private void performLogin(String email, String password) {
        showLoader();
        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                if (rememberMeCheckBox.isChecked()) {
                    sharedPreferences.edit()
                            .putString("email", email)
                            .putString("password", password)
                            .putBoolean("remember", true)
                            .apply();
                } else {
                    sharedPreferences.edit().clear().apply();
                }
                navigateToDashboard();
            } else {
                hideLoader();
                showLoginError();
            }
        });
    }

    /**
     * Processes result from Google Sign-In flow.
     *
     * @param task Google Sign-In task
     */
    private void handleGoogleSignInResult(@NonNull Task<GoogleSignInAccount> task) {
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account != null && account.getIdToken() != null) {
                AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
                mAuth.signInWithCredential(credential).addOnCompleteListener(this, result -> {
                    if (result.isSuccessful()) {
                        saveUserAndNavigate(mAuth.getCurrentUser());
                    } else {
                        hideLoader();
                        Toast.makeText(this, R.string.error_google_failed, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } catch (ApiException e) {
            hideLoader();
            Toast.makeText(this, R.string.error_google_login, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Authenticates Firebase using Facebook credentials.
     *
     * @param token Facebook access token
     */
    private void handleFacebookAccessToken(AccessToken token) {
        AuthCredential credential = FacebookAuthProvider.getCredential(token.getToken());
        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                saveUserAndNavigate(mAuth.getCurrentUser());
            } else {
                hideLoader();
                Toast.makeText(this, "Facebook login failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Stores user info in Firestore and navigates to Dashboard.
     *
     * @param user The authenticated FirebaseUser
     */
    private void saveUserAndNavigate(FirebaseUser user) {
        if (user == null) return;

        String uid = user.getUid();
        String name = user.getDisplayName() != null ? user.getDisplayName() : "Unknown";
        String email = user.getEmail();

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .set(new User(name, email, uid))
                .addOnSuccessListener(unused -> navigateToDashboard())
                .addOnFailureListener(e -> {
                    hideLoader();
                    Toast.makeText(this, "Firestore error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Navigates to the Dashboard screen and clears back stack.
     */
    private void navigateToDashboard() {
        hideLoader();
        Intent intent = new Intent(SignIn.this, navbar.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /** Shows the loader dialog */
    private void showLoader() {
        if (!loaderDialog.isShowing()) loaderDialog.show();
    }

    /** Hides the loader dialog */
    private void hideLoader() {
        if (loaderDialog.isShowing()) loaderDialog.dismiss();
    }

    /**
     * Required override for Facebook login result callback.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        callbackManager.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Validates email format and shows error if invalid.
     */
    private void validateEmail() {
        String email = emailInput.getText().toString().trim();
        boolean isValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();

        if (email.isEmpty()) {
            showError(emailInputLayout, emailError, getString(R.string.error_email_required));
            isEmailValid = false;
        } else if (!isValid) {
            showError(emailInputLayout, emailError, getString(R.string.error_email_invalid));
            isEmailValid = false;
        } else {
            clearError(emailInputLayout, emailError);
            showValidIcon(emailInputLayout);
            isEmailValid = true;
        }
    }

    /**
     * Validates that the password field is not empty.
     */
    private void validatePassword() {
        String password = passwordInput.getText().toString().trim();
        if (password.isEmpty()) {
            showError(passwordInputLayout, passwordError, getString(R.string.error_password_required));
        } else {
            clearError(passwordInputLayout, passwordError);
        }
    }

    /**
     * Toggles password field visibility and icon.
     */
    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            passwordInputLayout.setEndIconDrawable(R.drawable.ic_eye_hidden);
        } else {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            passwordInputLayout.setEndIconDrawable(R.drawable.ic_eye_visible);
        }
        isPasswordVisible = !isPasswordVisible;
        passwordInput.setSelection(passwordInput.getText().length());
    }

    /**
     * Enables or disables the login button based on input state.
     */
    private void updateLoginButtonState() {
        boolean enable = !emailInput.getText().toString().trim().isEmpty()
                && !passwordInput.getText().toString().trim().isEmpty();
        signInButton.setEnabled(enable);
        signInButton.setBackgroundTintList(ContextCompat.getColorStateList(this, enable ? R.color.black : R.color.grey_light));
        signInButton.setTextColor(ContextCompat.getColor(this, enable ? R.color.white : R.color.grey));
    }

    /**
     * Shows red error styling and message for failed login.
     */
    private void showLoginError() {
        loginErrorContainer.setVisibility(View.VISIBLE);
        loginErrorTitle.setText(R.string.error_login_title);
        loginErrorDescription.setText(R.string.error_login_description);
        emailInputLayout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.red));
        passwordInputLayout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.red));
    }

    /**
     * Shows error text and red border on input field.
     */
    private void showError(TextInputLayout layout, TextView errorView, String message) {
        layout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.red));
        errorView.setText(message);
        errorView.setTextColor(ContextCompat.getColor(this, R.color.red));
        errorView.setVisibility(View.VISIBLE);
    }

    /**
     * Clears validation error visuals on input field.
     */
    private void clearError(TextInputLayout layout, TextView errorView) {
        layout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.grey));
        errorView.setVisibility(View.GONE);
    }

    /**
     * Shows a green check icon for valid input.
     */
    private void showValidIcon(TextInputLayout layout) {
        layout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        layout.setEndIconDrawable(R.drawable.ic_check_green);
        layout.setEndIconTintList(ContextCompat.getColorStateList(this, R.color.green));
    }

    /**
     * Hides keyboard and clears input focus.
     */
    private void hideKeyboardAndClearFocus() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            view.clearFocus();
        }
    }

    /**
     * Dismisses keyboard when tapping outside of text fields.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View view = getCurrentFocus();
            if (view != null) {
                Rect outRect = new Rect();
                view.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    view.clearFocus();
                    hideKeyboardAndClearFocus();
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }
}
