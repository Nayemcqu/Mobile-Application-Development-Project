package com.cqu.genaiexpensetracker.income;

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

import com.cqu.genaiexpensetracker.R;
import com.cqu.genaiexpensetracker.dashboard.Dashboard;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
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
 * Fragment for adding a new income entry in the GenAI Expense Tracker app.
 *
 * Features:
 * - Enter amount, source, note, and date
 * - Upload optional payslip (PDF)
 * - Saves income to Firestore under /users/{uid}/income/
 * - `timestamp`: used by backend for AI alert logic
 * - `createdAt`: used in UI for sorting/filtering
 */
public class addIncome extends Fragment {

    private static final int PICK_PDF_REQUEST = 1;

    private EditText inputAmount, inputCategory, inputDate, inputNote;
    private TextView textFilename;
    private ImageView uploadIcon, calendarIcon;
    private MaterialButton saveButton;

    private Uri fileUri;
    private Calendar selectedDate;

    public addIncome() {}

    public static addIncome newInstance() {
        return new addIncome();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.nav_menu_add_income, container, false);

        // Bind views
        inputAmount = view.findViewById(R.id.input_amount);
        inputCategory = view.findViewById(R.id.text_income_category);
        inputDate = view.findViewById(R.id.edit_text_date);
        inputNote = view.findViewById(R.id.edit_text_note);
        textFilename = view.findViewById(R.id.text_payslip_filename);
        uploadIcon = view.findViewById(R.id.icon_upload_payslip);
        calendarIcon = view.findViewById(R.id.icon_calendar);
        saveButton = view.findViewById(R.id.button_save_income);
        selectedDate = Calendar.getInstance();

        // Date picker
        inputDate.setOnClickListener(v -> showDatePicker());
        calendarIcon.setOnClickListener(v -> showDatePicker());

        // File picker
        uploadIcon.setOnClickListener(v -> openFileChooser());

        // Save button
        saveButton.setOnClickListener(v -> validateAndSave());

        return view;
    }

    /**
     * Validates input fields and starts save process.
     */
    private void validateAndSave() {
        String amountStr = inputAmount.getText().toString().trim();
        String source = inputCategory.getText().toString().trim();
        String note = inputNote.getText().toString().trim();
        String dateStr = inputDate.getText().toString().trim();

        if (TextUtils.isEmpty(amountStr)) {
            inputAmount.setError("Enter amount");
            return;
        }
        if (TextUtils.isEmpty(source)) {
            inputCategory.setError("Enter income source");
            return;
        }
        if (TextUtils.isEmpty(dateStr)) {
            inputDate.setError("Select a date");
            return;
        }

        double amount = Double.parseDouble(amountStr);
        Timestamp selectedTimestamp = new Timestamp(selectedDate.getTime());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(getContext(), "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (fileUri != null) {
            uploadPayslip(fileUri, payslipUrl ->
                    saveIncomeData(user.getUid(), db, amount, source, selectedTimestamp, note, payslipUrl));
        } else {
            saveIncomeData(user.getUid(), db, amount, source, selectedTimestamp, note, null);
        }
    }

    /**
     * Uploads payslip to Firebase Storage and calls callback with URL.
     */
    private void uploadPayslip(Uri uri, OnUploadComplete callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        StorageReference ref = FirebaseStorage.getInstance()
                .getReference("incomeFiles/" + uid + "/" + System.currentTimeMillis() + "_payslip.pdf");

        ref.putFile(uri)
                .addOnSuccessListener(taskSnapshot ->
                        ref.getDownloadUrl().addOnSuccessListener(downloadUri ->
                                callback.onComplete(downloadUri.toString())))
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Upload failed", Toast.LENGTH_SHORT).show();
                    callback.onComplete(null);
                });
    }

    /**
     * Saves income data to Firestore and redirects to Dashboard.
     *
     * @param uid         Firebase user ID
     * @param db          Firestore instance
     * @param amount      Entered amount
     * @param source      Income source (e.g., Salary)
     * @param timestamp   Selected date (used for AI insight triggers)
     * @param note        Optional user note
     * @param payslipUrl  Optional uploaded file URL
     */
    private void saveIncomeData(String uid, FirebaseFirestore db,
                                double amount, String source,
                                Timestamp timestamp,
                                String note, String payslipUrl) {

        Map<String, Object> income = new HashMap<>();
        income.put("amount", amount);
        income.put("source", source);
        income.put("note", note);
        income.put("timestamp", timestamp);   // Used by backend (FCM)
        income.put("createdAt", timestamp);   // Used by UI for filtering/sorting
        income.put("payslipUrl", payslipUrl);

        db.collection("users").document(uid).collection("income")
                .add(income)
                .addOnSuccessListener(docRef -> {
                    Snackbar.make(requireView(), "Income saved", Snackbar.LENGTH_SHORT).show();
                    resetForm();
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Save failed", Toast.LENGTH_SHORT).show());
    }

    /**
     * Opens file chooser to select a PDF.
     */
    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        startActivityForResult(Intent.createChooser(intent, "Select Payslip"), PICK_PDF_REQUEST);
    }

    /**
     * Shows a calendar dialog to choose income date.
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
     * Extracts display name of file from URI.
     */
    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = requireActivity().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) result = cursor.getString(nameIndex);
                }
            }
        }
        if (result == null) result = uri.getLastPathSegment();
        return result;
    }

    @Override
    public void onResume() {
        super.onResume();
        clearAllFields();
    }

    /**
     * Clears all input fields and resets state.
     */
    private void clearAllFields() {
        inputAmount.setText("");
        inputCategory.setText("");
        inputNote.setText("");
        inputDate.setText("");
        textFilename.setText("Choose file");
        fileUri = null;

        inputAmount.clearFocus();
        inputCategory.clearFocus();
        inputNote.clearFocus();
        inputDate.clearFocus();

        View currentFocus = requireActivity().getCurrentFocus();
        if (currentFocus != null) {
            InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
    }

    /**
     * Wrapper for clearing the form after save.
     */
    private void resetForm() {
        clearAllFields();
    }

    /**
     * Callback interface for uploading file and returning its URL.
     */
    private interface OnUploadComplete {
        void onComplete(String downloadUrl);
    }
}
