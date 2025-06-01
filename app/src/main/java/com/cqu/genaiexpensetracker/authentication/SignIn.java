/**
 * SignIn.java
 * -------------------------
 * Author: Kapil Pandey
 * Syndey Group
 * <p>
 * Description:
 * This activity manages user authentication using Firebase for:
 * - Email & Password
 * - Google Sign-In
 * - Facebook Login
 * <p>
 * Features:
 * - Live input validation and visual feedback
 * - "Remember Me" support using SharedPreferences
 * - Firestore integration to save user details
 * - Custom loading & success/error dialogs for smoother transitions
 */

package com.cqu.genaiexpensetracker.authentication;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.cqu.genaiexpensetracker.R;
import com.cqu.genaiexpensetracker.navbar.navbar;
import com.cqu.genaiexpensetracker.userModel.userModel;
import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.Arrays;

public class SignIn extends AppCompatActivity {

    private static final String PREFS_NAME = "LoginPrefs";
    // Firebase and social login
    private FirebaseAuth mAuth;
    private GoogleSignInClient googleSignInClient;
    private CallbackManager callbackManager;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    // UI components
    private TextInputEditText emailInput, passwordInput;
    private TextInputLayout emailInputLayout, passwordInputLayout;
    private TextView emailError, passwordError;
    private LinearLayout signInButton;
    private TextView signInText;
    private Button googleSignInButton, facebookSignInButton;
    private CheckBox rememberMeCheckBox;
    // Dialogs
    private Dialog loaderDialog;
    private Dialog errorDialog;
    // SharedPreferences for Remember Me
    private SharedPreferences sharedPreferences;
    // State flags
    private boolean isPasswordVisible = false;
    private boolean isEmailValid = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.auth_activity_sign_in);

        // Initialize Firebase authentication and Facebook callback manager
        initializeFirebase();
        // Initialize UI components and configure default settings
        initializeViews();
        // Set up Google Sign-In client and launcher
        initializeGoogleSignIn();
        // Set up Facebook Login callback
        initializeFacebookLogin();
        // Set up listeners for user interactions
        setupListeners();
        // Load saved credentials if "Remember Me" was checked
        loadRememberedCredentials();
        // Update the state of the login button based on input fields
        updateLoginButtonState();
    }

    /**
     * Initializes Firebase authentication and Facebook callback manager.
     */
    private void initializeFirebase() {
        mAuth = FirebaseAuth.getInstance();
        callbackManager = CallbackManager.Factory.create();
    }

    /**
     * Initializes all UI view components and sets default configurations,
     * including setting up SharedPreferences and input layouts.
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
        signInText = findViewById(R.id.sign_in_text);

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        // Configure loader dialog
        loaderDialog = new Dialog(this);
        loaderDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        loaderDialog.setContentView(R.layout.dialog_loading_screen);
        loaderDialog.setCancelable(false);
        if (loaderDialog.getWindow() != null) {
            loaderDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            loaderDialog.getWindow().setDimAmount(0.6f);
            loaderDialog.getWindow().setGravity(Gravity.CENTER);
        }
        // Configure password visibility toggle
        passwordInputLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        passwordInputLayout.setEndIconDrawable(R.drawable.ic_eye_hidden);
        passwordInputLayout.setEndIconTintList(ContextCompat.getColorStateList(this, R.color.black));
        // Set up navigation to Forgot Password and Sign Up activities
        findViewById(R.id.btn_forgot_password).setOnClickListener(v -> startActivity(new Intent(this, ForgotPassword.class)));
        findViewById(R.id.text_sign_up).setOnClickListener(v -> startActivity(new Intent(this, SignUp.class)));
    }

    /**
     * Loads saved credentials if "Remember Me" was previously checked.
     */
    private void loadRememberedCredentials() {
        if (sharedPreferences.getBoolean("remember", false)) {
            emailInput.setText(sharedPreferences.getString("email", ""));
            passwordInput.setText(sharedPreferences.getString("password", ""));
            rememberMeCheckBox.setChecked(true);
        }
    }

    /**
     * Configures Google Sign-In client and sets up a launcher for result handling.
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
     * Initializes Facebook Login and registers callback for login results.
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
     * Sets up listeners for user interactions, including input validation and button clicks.
     */
    private void setupListeners() {
        emailInput.setOnFocusChangeListener((v, hasFocus) -> validateEmail());
        passwordInput.setOnFocusChangeListener((v, hasFocus) -> validatePassword());

        emailInput.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                validateEmail();
                updateLoginButtonState();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        passwordInput.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                validatePassword();
                updateLoginButtonState();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
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
     * Validates input fields and initiates login if inputs are valid.
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
     * Performs login using Firebase Authentication with email and password.
     *
     * @param email    User's email address
     * @param password User's password
     */
    private void performLogin(String email, String password) {
        showLoader();
        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null && user.isEmailVerified()) {
                    if (rememberMeCheckBox.isChecked()) {
                        sharedPreferences.edit()
                                .putString("email", email)
                                .putString("password", password)
                                .putBoolean("remember", true)
                                .apply();
                    } else {
                        sharedPreferences.edit().clear().apply();
                    }
                    showSuccessAndNavigate();
                } else {
                    mAuth.signOut();
                    hideLoader();
                    showEmailNotVerifiedDialog();
                }
            } else {
                hideLoader();
                showLoginErrorDialog();
            }
        });
    }

    /**
     * Displays a dialog informing the user that their email is not verified.
     */
    private void showEmailNotVerifiedDialog() {
        Dialog errorDialog = new Dialog(this);
        errorDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        errorDialog.setContentView(R.layout.dialog_login_error);
        errorDialog.setCancelable(false);

        if (errorDialog.getWindow() != null) {
            errorDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            errorDialog.getWindow().setDimAmount(0.6f);
            errorDialog.getWindow().setGravity(Gravity.CENTER);
        }

        TextView messageText = errorDialog.findViewById(R.id.error_message);
        Button okButton = errorDialog.findViewById(R.id.error_ok_button);

        messageText.setText("Opps! Please verify your email before signing in.");

        okButton.setOnClickListener(v -> {
            errorDialog.dismiss();
        });

        errorDialog.show();
    }

    /**
     * Shows Lottie-based error dialog on incorrect login
     */
    private void showLoginErrorDialog() {
        errorDialog = new Dialog(this);
        errorDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        errorDialog.setContentView(R.layout.dialog_login_error);
        errorDialog.setCancelable(false);

        if (errorDialog.getWindow() != null) {
            errorDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            errorDialog.getWindow().setDimAmount(0.5f);
            errorDialog.getWindow().setGravity(Gravity.CENTER);
        }

        Button okButton = errorDialog.findViewById(R.id.error_ok_button);
        okButton.setOnClickListener(v -> errorDialog.dismiss());

        errorDialog.show();
    }

    /**
     * Handles the result of the Google Sign-In task and authenticates with Firebase.
     *
     * @param task Task containing the GoogleSignInAccount result.
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
     * Handles Facebook access token and authenticates with Firebase.
     *
     * @param token The Facebook AccessToken received upon successful login.
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
     * Saves user data to Firestore and navigates to the main application screen.
     *
     * @param user The authenticated FirebaseUser object.
     */
    private void saveUserAndNavigate(FirebaseUser user) {
        if (user == null) return;

        String uid = user.getUid();
        String name = user.getDisplayName() != null ? user.getDisplayName() : "Unknown";
        String email = user.getEmail();

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .set(new userModel(name, email, uid))
                .addOnSuccessListener(unused -> showSuccessAndNavigate())
                .addOnFailureListener(e -> {
                    hideLoader();
                    Toast.makeText(this, "Firestore error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Shows a success dialog and navigates to the navbar activity after a delay.
     * Also syncs the device's FCM token to Firestore under the user's document.
     */
    private void showSuccessAndNavigate() {
        hideLoader();
        Dialog successDialog = new Dialog(this);
        successDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        successDialog.setContentView(R.layout.dialog_success);
        successDialog.setCancelable(false);

        if (successDialog.getWindow() != null) {
            successDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            successDialog.getWindow().setDimAmount(0.6f);
            successDialog.getWindow().setGravity(Gravity.CENTER);
        }

        successDialog.show();

        new Handler().postDelayed(() -> {
            successDialog.dismiss();
            syncFcmTokenToFirestore(); // Sync FCM token before navigating
            Intent intent = new Intent(SignIn.this, navbar.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }, 2000);
    }

    /**
     * Displays the loading dialog.
     */
    private void showLoader() {

        if (!loaderDialog.isShowing()) loaderDialog.show();
    }

    /**
     * Hides the loading dialog.
     */
    private void hideLoader() {

        if (loaderDialog.isShowing()) loaderDialog.dismiss();
    }

    /**
     * Retrieves the device's FCM token and updates it in Firestore
     * under users/{uid}/fcmToken. Called after login success.
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

    /**
     * Handles the result from external activities (e.g., Facebook Login).
     *
     * @param requestCode The request code originally supplied to startActivityForResult.
     * @param resultCode  The result code returned by the child activity.
     * @param data        An Intent containing the result data.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        callbackManager.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Validates the email input and provides real-time feedback.
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
     * Validates the password input and provides feedback.
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
     * Toggles the visibility of the password input field.
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
     * Updates the state of the login button based on input field contents.
     */
    private void updateLoginButtonState() {
        boolean enable = !emailInput.getText().toString().trim().isEmpty()
                && !passwordInput.getText().toString().trim().isEmpty();

        signInButton.setEnabled(enable);
        signInButton.setBackgroundResource(R.drawable.rounded_btn_selector);
        signInText.setTextColor(ContextCompat.getColor(this, enable ? R.color.white : R.color.grey));
    }


    /**
     * Displays an error message with a red border on the input layout.
     *
     * @param layout    The TextInputLayout to show error on.
     * @param errorView The TextView for showing the error message.
     * @param message   The error message to display.
     */
    private void showError(TextInputLayout layout, TextView errorView, String message) {
        layout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.dark_red));
        errorView.setText(message);
        errorView.setTextColor(ContextCompat.getColor(this, R.color.dark_red));
        errorView.setVisibility(View.VISIBLE);
    }

    /**
     * Clears any existing error message from the input layout.
     *
     * @param layout    The TextInputLayout to clear error from.
     * @param errorView The associated TextView for the error.
     */
    private void clearError(TextInputLayout layout, TextView errorView) {
        layout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.grey));
        errorView.setVisibility(View.GONE);
    }

    /**
     * Shows a green check icon in the input layout indicating valid input.
     *
     * @param layout The TextInputLayout to show the icon in.
     */
    private void showValidIcon(TextInputLayout layout) {
        layout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        layout.setEndIconDrawable(R.drawable.ic_check_green);
        layout.setEndIconTintList(ContextCompat.getColorStateList(this, R.color.green));
    }

    /**
     * Hides the keyboard and removes focus from the current input field.
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
     * Handles touch events to dismiss keyboard when tapping outside input fields.
     *
     * @param event The MotionEvent being dispatched.
     * @return true if the event was handled, false otherwise.
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
