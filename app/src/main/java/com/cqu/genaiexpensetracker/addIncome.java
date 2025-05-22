package com.cqu.genaiexpensetracker;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Fragment for adding a new income entry.
 * Users can input amount, source, date, note, and optionally upload a payslip.
 * Data is saved to Firestore and optionally uploaded to Firebase Storage.
 */
public class addIncome extends Fragment {

    private static final int PICK_PDF_REQUEST = 1;

    private EditText inputAmount, inputCategory, inputDate, inputNote;
    private TextView textFilename;
    private ImageView uploadIcon, calendarIcon;
    private MaterialButton saveButton;
    private Uri fileUri;
    private Calendar selectedDate;

    public addIncome() {
        // Required empty public constructor
    }

    public static addIncome newInstance() {
        return new addIncome();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.nav_menu_add_income, container, false);

        // Initialize views
        inputAmount = view.findViewById(R.id.input_amount);
        inputCategory = view.findViewById(R.id.text_income_category);
        inputDate = view.findViewById(R.id.edit_text_date);
        inputNote = view.findViewById(R.id.edit_text_note);
        textFilename = view.findViewById(R.id.text_payslip_filename);
        uploadIcon = view.findViewById(R.id.icon_upload_payslip);
        calendarIcon = view.findViewById(R.id.icon_calendar);
        saveButton = view.findViewById(R.id.button_save_income);
        selectedDate = Calendar.getInstance();

        // File picker
        uploadIcon.setOnClickListener(v -> openFileChooser());

        // Calendar trigger from both icon and input field
        inputDate.setOnClickListener(v -> showDatePicker());
        calendarIcon.setOnClickListener(v -> showDatePicker());

        // Save button click
        saveButton.setOnClickListener(v -> validateAndSave());

        return view;
    }

    /**
     * Validates user input and initiates save process.
     */
    private void validateAndSave() {
        String amountStr = inputAmount.getText().toString().trim();
        String category = inputCategory.getText().toString().trim();
        String note = inputNote.getText().toString().trim();
        String dateStr = inputDate.getText().toString().trim();

        if (TextUtils.isEmpty(amountStr)) {
            inputAmount.setError("Enter amount");
            return;
        }

        if (TextUtils.isEmpty(category)) {
            inputCategory.setError("Enter source");
            return;
        }

        if (TextUtils.isEmpty(dateStr)) {
            inputDate.setError("Choose date");
            return;
        }

        double amount = Double.parseDouble(amountStr);
        Timestamp timestamp = new Timestamp(selectedDate.getTime());
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(getContext(), "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (fileUri != null) {
            uploadPayslip(fileUri, payslipUrl ->
                    saveIncomeData(user.getUid(), db, amount, category, timestamp, note, payslipUrl));
        } else {
            saveIncomeData(user.getUid(), db, amount, category, timestamp, note, null);
        }
    }

    /**
     * Uploads the payslip to Firebase Storage and returns the download URL.
     *
     * @param fileUri  Uri of selected file
     * @param callback Callback to continue after upload
     */
    private void uploadPayslip(Uri fileUri, OnUploadComplete callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        StorageReference ref = FirebaseStorage.getInstance()
                .getReference("incomeFiles/" + uid + "/" + System.currentTimeMillis() + "_payslip.pdf");

        ref.putFile(fileUri)
                .addOnSuccessListener(taskSnapshot ->
                        ref.getDownloadUrl().addOnSuccessListener(uri -> callback.onComplete(uri.toString())))
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to upload file", Toast.LENGTH_SHORT).show();
                    callback.onComplete(null);
                });
    }

    /**
     * Saves the income data to Firestore.
     *
     * @param userId     Current user ID
     * @param db         Firestore instance
     * @param amount     Income amount
     * @param source     Source of income
     * @param timestamp  Timestamp of income
     * @param note       Optional note
     * @param payslipUrl Optional payslip URL
     */
    private void saveIncomeData(String userId, FirebaseFirestore db,
                                double amount, String source, Timestamp timestamp,
                                String note, String payslipUrl) {

        Map<String, Object> income = new HashMap<>();
        income.put("amount", amount);
        income.put("source", source);
        income.put("note", note);
        income.put("timestamp", timestamp);
        income.put("payslipUrl", payslipUrl);

        db.collection("users")
                .document(userId)
                .collection("income")
                .add(income)
                .addOnSuccessListener(doc -> {
                    Toast.makeText(getContext(), "Income saved successfully", Toast.LENGTH_SHORT).show();
                    resetForm();

                    // Redirect to Home (Dashboard)
                    if (getActivity() != null) {
                        View homeTab = getActivity().findViewById(R.id.nav_home);
                        if (homeTab != null) {
                            homeTab.performClick();
                        } else {
                            requireActivity().getSupportFragmentManager()
                                    .beginTransaction()
                                    .replace(R.id.main_frame, new Dashboard())
                                    .commit();
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Failed to save income", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onResume() {
        super.onResume();
        clearAllFields(); // Reset input + hide keyboard on fragment return
    }

    /**
     * Clears all input fields, resets state, and hides the keyboard.
     */
    private void clearAllFields() {
        inputAmount.setText("");
        inputCategory.setText("");
        inputNote.setText("");
        inputDate.setText("");
        textFilename.setText("Choose file");
        fileUri = null;

        // Clear focus from inputs
        inputAmount.clearFocus();
        inputCategory.clearFocus();
        inputNote.clearFocus();
        inputDate.clearFocus();

        // Hide keyboard
        View currentFocus = requireActivity().getCurrentFocus();
        if (currentFocus != null) {
            InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
    }


    /**
     * Clears the form inputs after successful save.
     */
    private void resetForm() {
        inputAmount.setText("");
        inputCategory.setText("");
        inputNote.setText("");
        inputDate.setText("");
        textFilename.setText("Choose file");
        fileUri = null;
    }

    /**
     * Opens Android file picker for PDF selection.
     */
    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        startActivityForResult(Intent.createChooser(intent, "Select Payslip"), PICK_PDF_REQUEST);
    }

    /**
     * Displays the calendar dialog for date selection.
     */
    private void showDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(getContext(), (view, year, month, dayOfMonth) -> {
            selectedDate.set(Calendar.YEAR, year);
            selectedDate.set(Calendar.MONTH, month);
            selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            inputDate.setText(sdf.format(selectedDate.getTime()));
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH));

        dialog.show();
    }

    /**
     * Handles result from file picker and displays file name.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_PDF_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            fileUri = data.getData();
            String fileName = getFileName(fileUri);
            textFilename.setText(fileName != null ? fileName : "File Selected");
        }
    }

    /**
     * Extracts file name from URI for display.
     */
    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getActivity().getContentResolver()
                    .query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) result = cursor.getString(index);
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    /**
     * Callback interface to handle post-upload success.
     */
    private interface OnUploadComplete {
        void onComplete(String downloadUrl);
    }
}
