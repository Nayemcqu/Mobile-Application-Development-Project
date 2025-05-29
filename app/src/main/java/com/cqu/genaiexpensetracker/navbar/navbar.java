package com.cqu.genaiexpensetracker.navbar;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.bumptech.glide.Glide;
import com.cqu.genaiexpensetracker.overview.Overview;
import com.cqu.genaiexpensetracker.R;
import com.cqu.genaiexpensetracker.authentication.SignIn;
import com.cqu.genaiexpensetracker.dashboard.Dashboard;
import com.cqu.genaiexpensetracker.expense.Expense;
import com.cqu.genaiexpensetracker.expense.addExpense;
import com.cqu.genaiexpensetracker.income.Income;
import com.cqu.genaiexpensetracker.income.addIncome;
import com.cqu.genaiexpensetracker.ai_insights.insights;
import com.cqu.genaiexpensetracker.navbar.navbarmenu.profile;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code navbar} activity handles the navigation drawer and floating bottom navigation bar.
 * It enables fragment switching, profile image updates, and sign-out functionality using Firebase.
 * <p>
 * Features:
 * - Drawer and BottomNavigation interaction
 * - Profile image upload and load from Firebase Storage
 * - Sign out with confirmation dialog
 * - Modern floating tab bar with elevation and animation
 */
public class navbar extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1001;
    private String currentFragmentTag = "HOME"; // Default screen is Home


    // Fragments
    private final Dashboard dashboard = new Dashboard();
    private final Income income = new Income();
    private final Expense expense = new Expense();
    private final Overview overview = new Overview();
    private final com.cqu.genaiexpensetracker.income.addIncome addIncome = new addIncome();
    private final com.cqu.genaiexpensetracker.expense.addExpense addExpense = new addExpense();
    private final com.cqu.genaiexpensetracker.navbar.navbarmenu.profile profile = new profile();
    private final com.cqu.genaiexpensetracker.ai_insights.insights insights = new insights();

    private TextView titleText;
    private DrawerLayout drawerLayout;
    private ImageButton buttonDrawerToggle;
    private NavigationView navigationView;
    private ImageView profileImage, iconAddPhoto;
    private TextView emailTextView;
    private Uri imageUri;
    private TextView alertBadge;


    /**
     * Called when the activity is starting. Initializes drawer, bottom nav, and profile.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_navbar);
        titleText = findViewById(R.id.titleText);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Setup drawer
        drawerLayout = findViewById(R.id.drawer_layout);
        buttonDrawerToggle = findViewById(R.id.buttonDrawerToggle);
        navigationView = findViewById(R.id.navigation_view);

        View headerView = navigationView.getHeaderView(0);
        emailTextView = headerView.findViewById(R.id.profile_email);
        profileImage = headerView.findViewById(R.id.profile_image);
        iconAddPhoto = headerView.findViewById(R.id.icon_add_photo);

        alertBadge = findViewById(R.id.alertBadge);

        loadUnreadAlertCount();

        loadUserProfile();
        iconAddPhoto.setOnClickListener(v -> openFileChooser());
        buttonDrawerToggle.setOnClickListener(v -> drawerLayout.open());

        // Handle drawer item selections
        navigationView.setNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            resetBottomNavHighlight();

            if (itemId == R.id.income_menu) {
                getSupportFragmentManager().beginTransaction().replace(R.id.main_frame, addIncome).commit();
                titleText.setText("Add Income");
            } else if (itemId == R.id.expense_menu) {
                getSupportFragmentManager().beginTransaction().replace(R.id.main_frame, addExpense).commit();
                titleText.setText("Add Expense");
            } else if (itemId == R.id.insights_menu) {
                getSupportFragmentManager().beginTransaction().replace(R.id.main_frame, insights).commit();
                titleText.setText("AI Insights");
            } else if (itemId == R.id.profile_menu) {
                getSupportFragmentManager().beginTransaction().replace(R.id.main_frame, profile).commit();
                titleText.setText("Profile");
            } else if (itemId == R.id.logout_menu) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(navbar.this, SignIn.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            drawerLayout.close();
            return true;
        });

        setupCustomBottomNavigation();
    }

    /**
     * Handles fragment switching and tab highlighting for floating modern nav bar.
     */
    private void setupCustomBottomNavigation() {
        LinearLayout navHome = findViewById(R.id.nav_home);
        LinearLayout navIncome = findViewById(R.id.nav_income);
        LinearLayout navOverview = findViewById(R.id.nav_overview);
        LinearLayout navExpense = findViewById(R.id.nav_expense);

        List<LinearLayout> tabs = Arrays.asList(navHome, navIncome, navOverview, navExpense);

        Map<LinearLayout, Integer> iconIds = new HashMap<>() {{
            put(navHome, R.id.icon_home);
            put(navIncome, R.id.icon_income);
            put(navOverview, R.id.icon_overview);
            put(navExpense, R.id.icon_expense);
        }};

        Map<LinearLayout, Integer> textIds = new HashMap<>() {{
            put(navHome, R.id.text_home);
            put(navIncome, R.id.text_income);
            put(navOverview, R.id.text_overview);
            put(navExpense, R.id.text_expense);
        }};

        // Notification icon click
        ImageView notificationIcon = findViewById(R.id.notificationIcon);
        notificationIcon.setOnClickListener(v -> {
            if ("NOTIFICATIONS".equals(currentFragmentTag)) {
                // Already on notifications ---- go back to Home
                currentFragmentTag = "HOME";
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.main_frame, dashboard)
                        .commit();
                titleText.setText("Home");
                findViewById(R.id.nav_home).performClick(); // also re-highlight Home tab
            } else {
                // Navigate to Notifications
                currentFragmentTag = "NOTIFICATIONS";
                resetBottomNavHighlight(); // visually deselect bottom tabs
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.main_frame, new com.cqu.genaiexpensetracker.notifications.Notifications())
                        .commit();
                titleText.setText("Notifications");
            }
        });


        Map<LinearLayout, androidx.fragment.app.Fragment> fragmentMap = new HashMap<>() {{
            put(navHome, dashboard);
            put(navIncome, income);
            put(navOverview, overview);
            put(navExpense, expense);
        }};

        for (LinearLayout tab : tabs) {
            tab.setOnClickListener(v -> {
                for (LinearLayout t : tabs) {
                    FrameLayout iconWrapper = (FrameLayout) t.getChildAt(0);
                    ImageView icon = t.findViewById(iconIds.get(t));
                    TextView label = t.findViewById(textIds.get(t));

                    if (t == tab) {
                        iconWrapper.setBackgroundResource(R.drawable.bg_circle_selected_nav);
                        iconWrapper.setTranslationY(dpToPx(-8));
                        iconWrapper.setElevation(dpToPx(4));
                        iconWrapper.getLayoutParams().width = dpToPx(64);
                        iconWrapper.getLayoutParams().height = dpToPx(64);
                        iconWrapper.requestLayout();
                        label.setTextColor(ContextCompat.getColor(this, R.color.primary));
                        label.setTypeface(null, Typeface.BOLD);
                    } else {
                        iconWrapper.setBackgroundColor(Color.TRANSPARENT);
                        iconWrapper.setTranslationY(0);
                        iconWrapper.setElevation(0);
                        iconWrapper.getLayoutParams().width = dpToPx(48);
                        iconWrapper.getLayoutParams().height = dpToPx(48);
                        iconWrapper.requestLayout();
                        label.setTextColor(ContextCompat.getColor(this, R.color.unselected));
                        label.setTypeface(null, Typeface.NORMAL);
                    }
                }

                androidx.fragment.app.Fragment selectedFragment = fragmentMap.get(tab);
                if (selectedFragment != null) {
                    getSupportFragmentManager().beginTransaction().replace(R.id.main_frame, selectedFragment).commit();
                    TextView selectedTextView = tab.findViewById(textIds.get(tab));
                    if (selectedTextView != null) {
                        titleText.setText(selectedTextView.getText().toString());
                    }
                }
            });
        }

        navHome.performClick();
    }

    /**
     * Clears highlight from all bottom tabs (used when switching via drawer).
     */
    private void resetBottomNavHighlight() {
        List<LinearLayout> tabs = Arrays.asList(
                findViewById(R.id.nav_home),
                findViewById(R.id.nav_income),
                findViewById(R.id.nav_overview),
                findViewById(R.id.nav_expense)
        );

        Map<LinearLayout, Integer> iconIds = new HashMap<>() {{
            put(findViewById(R.id.nav_home), R.id.icon_home);
            put(findViewById(R.id.nav_income), R.id.icon_income);
            put(findViewById(R.id.nav_overview), R.id.icon_overview);
            put(findViewById(R.id.nav_expense), R.id.icon_expense);
        }};

        Map<LinearLayout, Integer> textIds = new HashMap<>() {{
            put(findViewById(R.id.nav_home), R.id.text_home);
            put(findViewById(R.id.nav_income), R.id.text_income);
            put(findViewById(R.id.nav_overview), R.id.text_overview);
            put(findViewById(R.id.nav_expense), R.id.text_expense);
        }};

        for (LinearLayout tab : tabs) {
            FrameLayout iconWrapper = (FrameLayout) tab.getChildAt(0);
            ImageView icon = tab.findViewById(iconIds.get(tab));
            TextView label = tab.findViewById(textIds.get(tab));

            iconWrapper.setBackgroundColor(Color.TRANSPARENT);
            iconWrapper.setTranslationY(0);
            iconWrapper.getLayoutParams().width = dpToPx(48);
            iconWrapper.getLayoutParams().height = dpToPx(48);
            iconWrapper.setElevation(0);
            iconWrapper.requestLayout();
            icon.setColorFilter(null);
            label.setTextColor(ContextCompat.getColor(this, R.color.unselected));
            label.setTypeface(null, Typeface.NORMAL);
        }
    }

    /**
     * Converts dp to pixel units.
     */
    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
    }

    /**
     * Opens the device's file picker for selecting an image.
     */
    private void openFileChooser() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST);
    }

    /**
     * Called when the user selects an image from the picker.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            imageUri = data.getData();
            profileImage.setImageURI(imageUri);
            uploadImageToFirebase(imageUri);
        }
    }

    /**
     * Uploads the selected profile image to Firebase Storage and updates Firestore.
     */
    private void uploadImageToFirebase(Uri imageUri) {
        if (imageUri == null) return;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        StorageReference fileRef = FirebaseStorage.getInstance()
                .getReference("profileImages/" + uid + "/profile.jpg");

        fileRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot ->
                        fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                            String imageUrl = uri.toString();

                            Map<String, Object> profileData = new HashMap<>();
                            profileData.put("profileImage", imageUrl);

                            FirebaseFirestore.getInstance()
                                    .collection("users")
                                    .document(uid)
                                    .set(profileData, SetOptions.merge())
                                    .addOnSuccessListener(unused -> {
                                        // Show toast on success
                                        Toast.makeText(navbar.this, "Profile image updated successfully", Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(navbar.this, "Failed to update profile image", Toast.LENGTH_SHORT).show();
                                    });
                        }))
                .addOnFailureListener(e -> {
                    Toast.makeText(navbar.this, "Image upload failed", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Loads the current user's email and profile image from Firestore.
     */
    private void loadUserProfile() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        emailTextView.setText(user.getEmail());

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists() && document.contains("profileImage")) {
                        String imageUrl = document.getString("profileImage");
                        Glide.with(this)
                                .load(imageUrl)
                                .placeholder(R.drawable.ic_user_profile)
                                .error(R.drawable.ic_user_profile)
                                .circleCrop()
                                .into(profileImage);
                    }
                });
    }
    /**
     * Programmatically simulates a click on the "Overview" tab in the bottom navigation bar
     * to navigate the user to the Overview screen.
     */
    public void navigateToOverview() {
        LinearLayout navOverview = findViewById(R.id.nav_overview);
        if (navOverview != null) {
            navOverview.performClick();
        }
    }
    /**
     * Navigates the user to the Add Income screen by:
     * - Updating the screen title to "Add Income"
     * - Resetting the bottom navigation highlight
     * - Replacing the current fragment with the AddIncome fragment
     */
    public void navigateToAddIncome() {
        // Update title
        titleText.setText("Add Income");

        // Clear bottom nav tab highlight
        resetBottomNavHighlight();

        // Load addIncome fragment fullscreen
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.main_frame, new addIncome())
                .addToBackStack("AddIncome")
                .commit();
    }
    /**
     * Navigates the user to the Add Expense screen by:
     * - Updating the screen title to "Add Expense"
     * - Resetting the bottom navigation highlight
     * - Replacing the current fragment with the AddExpense fragment
     */
    public void navigateToAddExpense() {
        // Update title
        titleText.setText("Add Expense");

        // Clear bottom nav tab highlight
        resetBottomNavHighlight();

        // Load addIncome fragment fullscreen
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.main_frame, new addExpense())
                .addToBackStack("AddExpense")
                .commit();
    }

    /**
     * Attaches a real-time Firestore listener to track unread alert notifications.
     * Updates the notification badge automatically when new alerts arrive or are read.
     */
    private void loadUnreadAlertCount() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .collection("insights")
                .whereEqualTo("type", "Alert")
                .whereEqualTo("read", false)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        alertBadge.setVisibility(View.GONE);
                        return;
                    }


                    if (snapshot != null) {
                        int unreadCount = snapshot.size();
                        if (unreadCount > 0) {
                            alertBadge.setText(String.valueOf(unreadCount));
                            alertBadge.setVisibility(View.VISIBLE);
                        } else {
                            alertBadge.setVisibility(View.GONE);
                        }
                    }
                });
    }

}
