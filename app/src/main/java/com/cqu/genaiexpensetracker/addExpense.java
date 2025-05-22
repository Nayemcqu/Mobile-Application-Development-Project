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
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

/**
 * Fragment for adding a new expense entry.
 * Includes inputs for amount, category, optional note, and receipt upload.
 */
public class addExpense extends Fragment {

    // UI Components
    private EditText inputAmount, editTextDate, editTextNote;
    private Spinner spinnerCategory;
    private TextView receiptText;
    private LinearLayout uploadReceipt;
    private MaterialButton buttonSave;

    // Firebase instances
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private FirebaseAuth auth;

    // Optional receipt URI
    private Uri receiptUri;
    // File picker launcher
    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    receiptUri = result.getData().getData();
                    String fileName = getFileName(receiptUri);
                    receiptText.setText(fileName != null ? fileName : "File selected");
                }
            }
    );
    // User-selected expense date (used as shown date only)
    private Calendar calendar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.nav_menu_add_expense, container, false);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        auth = FirebaseAuth.getInstance();
        calendar = Calendar.getInstance();

        // Bind UI elements
        inputAmount = view.findViewById(R.id.input_amount);
        editTextDate = view.findViewById(R.id.edit_text_date);
        editTextNote = view.findViewById(R.id.edit_text_note);
        spinnerCategory = view.findViewById(R.id.spinner_expense_category);
        receiptText = view.findViewById(R.id.text_receipt_filename);
        uploadReceipt = view.findViewById(R.id.receipt_upload_box);
        buttonSave = view.findViewById(R.id.button_save_expense);

        // Spinner setup
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.expense_categories,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(adapter);

        // Date picker
        editTextDate.setOnClickListener(v -> openDatePicker());

        // File picker for receipt
        uploadReceipt.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            filePickerLauncher.launch(intent);
        });

        // Save handler
        buttonSave.setOnClickListener(v -> saveExpenseToFirestore());

        return view;
    }

    /**
     * Opens a calendar dialog for selecting date.
     */
    private void openDatePicker() {
        Calendar now = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(requireContext(),
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                    editTextDate.setText(sdf.format(calendar.getTime()));
                },
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    /**
     * Extracts filename from URI using content resolver.
     */
    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
                }
            }
        }
        return result;
    }

    /**
     * Validates fields and initiates upload or direct save.
     */
    private void saveExpenseToFirestore() {
        String amountText = inputAmount.getText().toString().trim();
        if (TextUtils.isEmpty(amountText)) {
            inputAmount.setError("Amount required");
            return;
        }

        double amount = Double.parseDouble(amountText); // string to double parse
        String category = spinnerCategory.getSelectedItem().toString();
        String note = editTextNote.getText().toString();
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        Date createdAt = calendar.getTime();

        if (TextUtils.isEmpty(editTextDate.getText().toString())) {
            editTextDate.setError("Date required");
            return;
        }

        if (category.equals("Select Category")) {
            Toast.makeText(requireContext(), "Please select a valid category", Toast.LENGTH_SHORT).show();
            return;
        }

        if (receiptUri != null) {
            uploadReceiptAndSaveData(amount, category, time, note, createdAt);
        } else {
            saveToFirestore(amount, category, time, note, null, createdAt);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        clearAllFields(); // Clears all inputs when fragment is resumed
    }

    private void clearAllFields() {
        inputAmount.setText("");
        editTextDate.setText("");
        editTextNote.setText("");
        spinnerCategory.setSelection(0);
        receiptUri = null;
        receiptText.setText("Choose file");

        // Clear focus from all input fields
        inputAmount.clearFocus();
        editTextDate.clearFocus();
        editTextNote.clearFocus();

        // Hide the keyboard if any field was focused
        View currentFocus = requireActivity().getCurrentFocus();
        if (currentFocus != null) {
            InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
    }

    /**
     * Uploads receipt image to Firebase Storage.
     */
    private void uploadReceiptAndSaveData(double amount, String category, String time, String note, Date createdAt) {
        String userId = Objects.requireNonNull(auth.getCurrentUser()).getUid();
        String filename = "receipts/" + userId + "/" + System.currentTimeMillis() + ".jpg";
        StorageReference fileRef = storage.getReference().child(filename);

        fileRef.putFile(receiptUri)
                .addOnSuccessListener(taskSnapshot ->
                        fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                            String receiptUrl = uri.toString();
                            saveToFirestore(amount, category, time, note, receiptUrl, createdAt);
                        }))
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Receipt upload failed. Saving without receipt.", Toast.LENGTH_SHORT).show();
                    saveToFirestore(amount, category, time, note, null, createdAt);
                });
    }

    /**
     * Saves all expense fields to Firestore.
     */
    private void saveToFirestore(double amount, String category, String time, String note, String receiptUrl, Date createdAt) {
        String userId = Objects.requireNonNull(auth.getCurrentUser()).getUid();
        expenseModel expense = new expenseModel(category, amount, time, note, receiptUrl, createdAt);

        db.collection("users").document(userId).collection("expenses")
                .add(expense)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(requireContext(), "Expense added", Toast.LENGTH_SHORT).show();

                    // Reset state
                    receiptUri = null;
                    receiptText.setText("Choose file");
                    inputAmount.setText("");
                    editTextDate.setText("");
                    editTextNote.setText("");
                    spinnerCategory.setSelection(0);

                    // Redirect to Expense screen via tab
                    if (getActivity() != null) {
                        View expenseTab = getActivity().findViewById(R.id.nav_home);
                        if (expenseTab != null) {
                            expenseTab.performClick();
                        } else {
                            requireActivity().getSupportFragmentManager()
                                    .beginTransaction()
                                    .replace(R.id.main_frame, new Expense())
                                    .commit();
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Error saving expense", Toast.LENGTH_SHORT).show());
    }
}
