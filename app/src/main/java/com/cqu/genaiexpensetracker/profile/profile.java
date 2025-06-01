/**
 * profile.java
 *
 * Fragment responsible for viewing and updating user profile.
 * - Updates name, phone, DOB, address, and profile image
 * - Integrates Google Places for address autocomplete
 * - Saves updated data to Firestore
 * - Reuses success Lottie dialog after saving
 *
 * Author: Kapil Pandey
 * Year  : 2025
 */

package com.cqu.genaiexpensetracker.profile;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.cqu.genaiexpensetracker.R;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.android.material.textfield.TextInputEditText;
import com.hbb20.CountryCodePicker;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class profile extends Fragment {

    private TextInputEditText editName, editDob, editEmail;
    private EditText editPhone;
    private AutoCompleteTextView editAddress;
    private CountryCodePicker ccp;
    private Button btnSave;
    private CircleImageView profileImage;
    private ImageView editProfilePhoto;
    private Calendar calendar;
    private Dialog successDialog;

    private FirebaseUser firebaseUser;
    private FirebaseFirestore firestore;
    private StorageReference storageReference;
    private PlacesClient placesClient;
    private Uri imageUri;
    private String googleMapsApiKey;
    private final int PICK_IMAGE_REQUEST = 1002;

    public profile() {}

    /** Initializes view, data, and click handlers. */
    @SuppressLint("ClickableViewAccessibility")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.nav_menu_profile, container, false);

        firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        firestore = FirebaseFirestore.getInstance();
        storageReference = FirebaseStorage.getInstance().getReference();

        editName = view.findViewById(R.id.edit_name);
        editPhone = view.findViewById(R.id.edit_phone);
        ccp = view.findViewById(R.id.ccp);
        editDob = view.findViewById(R.id.edit_dob);
        editAddress = view.findViewById(R.id.edit_address);
        editEmail = view.findViewById(R.id.profile_email);
        btnSave = view.findViewById(R.id.btn_save);
        profileImage = view.findViewById(R.id.profile_image);
        editProfilePhoto = view.findViewById(R.id.edit_profile_photo);
        calendar = Calendar.getInstance();

        editName.setFilters(new InputFilter[]{(source, start, end, dest, dstart, dend) -> {
            for (int i = start; i < end; i++) {
                char c = source.charAt(i);
                if (!Character.isLetter(c) && !Character.isWhitespace(c)) return "";
            }
            return null;
        }});

        fetchRemoteConfig();
        loadUserData();
        setupDatePicker();
        setupPhoneFormatter();

        btnSave.setOnClickListener(v -> {
            if (validateInputs()) updateUserData();
        });

        editProfilePhoto.setOnClickListener(v -> chooseImage());

        View scrollContainer = view.findViewById(R.id.profile_scroll);
        scrollContainer.setFocusableInTouchMode(true);
        scrollContainer.setOnTouchListener((v, event) -> {
            View focused = requireActivity().getCurrentFocus();
            if (focused != null) {
                focused.clearFocus();
                InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
            }
            scrollContainer.requestFocus();
            return false;
        });

        return view;
    }

    /** Loads Google Maps API key and configures Places API. */
    private void fetchRemoteConfig() {
        FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();
        FirebaseRemoteConfigSettings settings = new FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(3600).build();
        remoteConfig.setConfigSettingsAsync(settings);

        remoteConfig.fetchAndActivate().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                googleMapsApiKey = remoteConfig.getString("google_maps_api_key");
                if (!TextUtils.isEmpty(googleMapsApiKey)) {
                    if (!Places.isInitialized()) {
                        Places.initialize(requireContext(), googleMapsApiKey, Locale.getDefault());
                    }
                    placesClient = Places.createClient(requireContext());
                    attachAddressWatcher();
                }
            }
        });
    }

    /** Autocomplete prediction attachment for address field. */
    private void attachAddressWatcher() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() < 3) return;
                AutocompleteSessionToken token = AutocompleteSessionToken.newInstance();
                FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                        .setSessionToken(token).setQuery(s.toString()).build();

                Task<List<AutocompletePrediction>> task = placesClient.findAutocompletePredictions(request)
                        .continueWith(t -> t.getResult().getAutocompletePredictions());

                task.addOnSuccessListener(predictions -> {
                    List<String> suggestions = new ArrayList<>();
                    for (AutocompletePrediction prediction : predictions) {
                        suggestions.add(prediction.getFullText(null).toString());
                    }
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, suggestions);
                    editAddress.setAdapter(adapter);
                    adapter.notifyDataSetChanged();
                    editAddress.showDropDown();
                });
            }
        };
        editAddress.setTag(watcher);
        editAddress.addTextChangedListener(watcher);
    }

    /** Retrieves existing user data from Firestore. */
    private void loadUserData() {
        if (firebaseUser == null) return;
        DocumentReference docRef = firestore.collection("users").document(firebaseUser.getUid());
        docRef.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                editEmail.setText(snapshot.getString("email"));
                editName.setText(snapshot.getString("name"));
                editDob.setText(snapshot.getString("dob"));

                if (editAddress.getTag() instanceof TextWatcher) editAddress.removeTextChangedListener((TextWatcher) editAddress.getTag());
                editAddress.setText(snapshot.getString("address"));
                if (editAddress.getTag() instanceof TextWatcher) editAddress.addTextChangedListener((TextWatcher) editAddress.getTag());

                String phone = snapshot.getString("phone");
                if (phone != null && phone.startsWith("+")) {
                    String[] parts = phone.split(" ", 2);
                    if (parts.length == 2) {
                        ccp.setFullNumber(parts[0]);
                        editPhone.setText(parts[1]);
                    } else editPhone.setText(phone);
                }

                if (snapshot.contains("profileImage")) {
                    Glide.with(this).load(snapshot.getString("profileImage")).into(profileImage);
                }
            }
        });
    }

    /** Sets up calendar for DOB selection. */
    private void setupDatePicker() {
        editDob.setOnClickListener(v -> {
            DatePickerDialog dialog = new DatePickerDialog(getContext(), (view, y, m, d) -> {
                calendar.set(y, m, d);
                editDob.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calendar.getTime()));
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
            dialog.show();
        });
    }

    /** Formats user-entered phone numbers. */
    private void setupPhoneFormatter() {
        editPhone.addTextChangedListener(new TextWatcher() {
            boolean isFormatting;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;
                isFormatting = true;
                String clean = s.toString().replaceAll("[^\\d]", "");
                if (clean.length() >= 10) {
                    String formatted = String.format("%s-%s-%s", clean.substring(0, 4), clean.substring(4, 7), clean.substring(7, Math.min(10, clean.length())));
                    editPhone.setText(formatted);
                    editPhone.setSelection(formatted.length());
                }
                isFormatting = false;
            }
        });
    }

    /** Triggers image picker for selecting profile photo. */
    private void chooseImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    /** Receives image URI and uploads. */
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && data != null && data.getData() != null) {
            imageUri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), imageUri);
                profileImage.setImageBitmap(bitmap);
                uploadProfilePhoto();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /** Uploads selected profile picture. */
    private void uploadProfilePhoto() {
        if (imageUri == null || firebaseUser == null) return;
        StorageReference ref = storageReference.child("profileImages/" + firebaseUser.getUid() + ".jpg");
        ref.putFile(imageUri).addOnSuccessListener(task -> ref.getDownloadUrl().addOnSuccessListener(uri ->
                firestore.collection("users").document(firebaseUser.getUid()).update("profileImage", uri.toString())
        ));
    }

    /** Validates form entries. */
    private boolean validateInputs() {
        if (TextUtils.isEmpty(editName.getText())) {
            editName.setError("Name required"); return false;
        }
        String fullPhone = ccp.getSelectedCountryCodeWithPlus() + " " + editPhone.getText().toString().trim();
        if (!fullPhone.matches("^\\+\\d{1,3}\\s\\d{4}-\\d{3}-\\d{3}$")) {
            editPhone.setError("Format: +CC XXXX-XXX-XXX"); return false;
        }
        if (TextUtils.isEmpty(editDob.getText())) {
            editDob.setError("DOB required"); return false;
        }
        if (TextUtils.isEmpty(editAddress.getText())) {
            editAddress.setError("Address required"); return false;
        }
        return true;
    }

    /** Updates Firestore with new data and shows success dialog. */
    private void updateUserData() {
        DocumentReference ref = firestore.collection("users").document(firebaseUser.getUid());
        Map<String, Object> map = new HashMap<>();
        map.put("name", editName.getText().toString().trim());
        String fullPhone = ccp.getSelectedCountryCodeWithPlus() + " " + editPhone.getText().toString().trim();
        map.put("phone", fullPhone);
        map.put("dob", editDob.getText().toString().trim());
        map.put("address", editAddress.getText().toString().trim());

        ref.update(map).addOnSuccessListener(r -> {
            showProfileUpdatedDialog();
            if (getActivity() != null) {
                LinearLayout navHome = getActivity().findViewById(R.id.nav_home);
                if (navHome != null) navHome.performClick();
            }
        }).addOnFailureListener(e ->
                Toast.makeText(getContext(), "Failed to update profile", Toast.LENGTH_SHORT).show()
        );
    }

    /** Reuses dialog_success.xml to show Profile updated message. */
    private void showProfileUpdatedDialog() {
        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(R.layout.dialog_success);
        dialog.setCancelable(false);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView message = dialog.findViewById(R.id.success_message_text);
        if (message != null) {
            message.setText("Updated successfully!");
        }

        dialog.show();
        new android.os.Handler().postDelayed(dialog::dismiss, 2200);
    }
}
