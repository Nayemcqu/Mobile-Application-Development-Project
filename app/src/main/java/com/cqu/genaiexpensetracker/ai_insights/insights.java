/**
 * insights.java
 *
 * Fragment responsible for displaying AI-generated financial insights
 * and enabling users to generate and download PDF reports.
 *
 * Functionalities:
 * - Filters insights by Weekly, Monthly, or Yearly.
 * - Displays summary of income, expenses, and savings.
 * - Generates insights using Gemini API for UI or PDF.
 * - Shows Lottie animation while report is being generated and uploaded.
 * - Shows reusable animated success dialog upon successful save.
 *
 * Author: Kapil Pandey
 * Year  : 2025
 */

package com.cqu.genaiexpensetracker.ai_insights;

import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.airbnb.lottie.LottieAnimationView;
import com.cqu.genaiexpensetracker.PDFReportGenerator.PdfReportCreator;
import com.cqu.genaiexpensetracker.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class insights extends Fragment {

    private AutoCompleteTextView filterDropdown;
    private LinearLayout insightItemsContainer;
    private MaterialButton generateBtn;
    private String selectedPeriod = "Weekly";
    private Dialog loadingDialog;

    /**
     * Required empty public constructor
     */
    public insights() {}

    /**
     * Inflates the fragment view and sets up listeners.
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.ai_insights, container, false);

        filterDropdown = view.findViewById(R.id.filter_dropdown);
        insightItemsContainer = view.findViewById(R.id.insight_items_container);
        generateBtn = view.findViewById(R.id.button_generate_insights);

        setupDropdownFilter();
        generateBtn.setOnClickListener(v -> generateUiInsights());
        view.findViewById(R.id.btn_download_report).setOnClickListener(v -> generatePdfReport());

        loadSummaryData(selectedPeriod);
        return view;
    }

    /**
     * Sets up the period filter dropdown and listener.
     */
    private void setupDropdownFilter() {
        String[] filterOptions = {"Weekly", "Monthly", "Yearly"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), R.layout.ai_dropdown_menu_item, filterOptions);
        filterDropdown.setAdapter(adapter);
        filterDropdown.setDropDownBackgroundResource(R.drawable.ai_modern_dropdown_bg);
        filterDropdown.setText(selectedPeriod, false);

        filterDropdown.setOnClickListener(v -> filterDropdown.showDropDown());
        filterDropdown.setOnItemClickListener((parent, view, position, id) -> {
            selectedPeriod = filterOptions[position];
            filterDropdown.setText(selectedPeriod, false);
            loadSummaryData(selectedPeriod);
        });
    }

    /**
     * Loads and displays income/expense/saving summaries.
     */
    private void loadSummaryData(String period) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        long startMillis = calculateStartMillis(period);
        Date startDate = new Date(startMillis);
        double[] totalIncome = {0.0}, totalExpense = {0.0};
        int[] incomeCount = {0}, expenseCount = {0};

        db.collection("users").document(uid).collection("income")
                .whereGreaterThanOrEqualTo("timestamp", startDate)
                .get()
                .addOnSuccessListener(incomes -> {
                    for (var doc : incomes) {
                        Double amount = doc.getDouble("amount");
                        if (amount != null) {
                            totalIncome[0] += amount;
                            incomeCount[0]++;
                        }
                    }

                    db.collection("users").document(uid).collection("expenses")
                            .whereGreaterThanOrEqualTo("timestamp", startDate)
                            .get()
                            .addOnSuccessListener(expenses -> {
                                for (var doc : expenses) {
                                    Double amount = doc.getDouble("amount");
                                    if (amount != null) {
                                        totalExpense[0] += amount;
                                        expenseCount[0]++;
                                    }
                                }

                                double savings = totalIncome[0] - totalExpense[0];
                                if (!isAdded()) return;

                                requireView().<TextView>findViewById(R.id.summary_income_amount)
                                        .setText(String.format(Locale.getDefault(), "$%.2f", totalIncome[0]));
                                requireView().<TextView>findViewById(R.id.summary_income_count)
                                        .setText(String.valueOf(incomeCount[0]));
                                requireView().<TextView>findViewById(R.id.summary_expense_amount)
                                        .setText(String.format(Locale.getDefault(), "$%.2f", totalExpense[0]));
                                requireView().<TextView>findViewById(R.id.summary_expense_count)
                                        .setText(String.valueOf(expenseCount[0]));
                                requireView().<TextView>findViewById(R.id.summary_saving_amount)
                                        .setText(String.format(Locale.getDefault(), "$%.2f", savings));
                            });
                });
    }

    /**
     * Converts period string to starting timestamp in milliseconds.
     */
    private long calculateStartMillis(String period) {
        long now = System.currentTimeMillis();
        switch (period.toLowerCase()) {
            case "monthly": return now - 30L * 86400000L;
            case "yearly":  return now - 365L * 86400000L;
            default:         return now - 7L * 86400000L;
        }
    }

    /**
     * Calls Gemini API to generate and display insights in UI.
     */
    private void generateUiInsights() {
        insightItemsContainer.removeAllViews();
        generateBtn.setEnabled(false);
        generateBtn.setText(R.string.generating);

        Date startDate = new Date(calculateStartMillis(selectedPeriod));

        new AiInsightService().generateUiInsightsFromFirestore(startDate, selectedPeriod, new AiInsightService.GeminiCallback() {
            @Override
            public void onSuccess(String result) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    generateBtn.setEnabled(true);
                    generateBtn.setText(R.string.generate_ai_insights);
                    try {
                        String cleanJson = result.replaceAll("(?s)`{3}json\\s*|`{3}", "").trim();
                        JSONObject root = new JSONObject(cleanJson);
                        JSONArray insights = root.getJSONArray("insights");
                        for (int i = 0; i < insights.length(); i++) {
                            JSONObject insight = insights.getJSONObject(i);
                            String text = insight.getString("text");
                            String iconType = insight.optString("icon", "default");
                            addInsightToContainer(text, iconType);
                        }
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "Parsing error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    generateBtn.setEnabled(true);
                    generateBtn.setText(R.string.generate_ai_insights);
                    Toast.makeText(requireContext(), "Gemini error: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Dynamically adds a single insight view to the container.
     */
    private void addInsightToContainer(String text, String iconType) {
        View insightView = LayoutInflater.from(requireContext())
                .inflate(R.layout.ai_insight_item, insightItemsContainer, false);

        ImageView icon = insightView.findViewById(R.id.insight_icon);
        TextView message = insightView.findViewById(R.id.insight_text);
        message.setText(text);

        switch (iconType.toLowerCase()) {
            case "alert": icon.setImageResource(R.drawable.ic_error_triangle_icon); break;
            case "progress": icon.setImageResource(R.drawable.ic_data_analytics_icon); break;
            case "trend": icon.setImageResource(R.drawable.ic_growth_arrow); break;
            case "info": icon.setImageResource(R.drawable.ic_insight_info); break;
            default: icon.setImageResource(R.drawable.ic_default_insights_icon); break;
        }

        insightItemsContainer.addView(insightView);
    }

    /**
     * Generates the PDF report, uploads it, and logs it in Firestore.
     */
    private void generatePdfReport() {
        showDownloadingDialog();

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Date startDate = new Date(calculateStartMillis(selectedPeriod));
        double[] incomeTotal = {0.0}, expenseTotal = {0.0};
        Map<String, Double> categoryTotals = new HashMap<>();

        db.collection("users").document(uid).collection("income")
                .whereGreaterThanOrEqualTo("timestamp", startDate)
                .get().addOnSuccessListener(incomes -> {
                    for (var doc : incomes) {
                        Double amount = doc.getDouble("amount");
                        if (amount != null) incomeTotal[0] += amount;
                    }

                    db.collection("users").document(uid).collection("expenses")
                            .whereGreaterThanOrEqualTo("timestamp", startDate)
                            .get().addOnSuccessListener(expenses -> {
                                for (var doc : expenses) {
                                    Double amount = doc.getDouble("amount");
                                    String category = doc.getString("category");
                                    if (amount != null && category != null) {
                                        expenseTotal[0] += amount;
                                        categoryTotals.put(category, categoryTotals.getOrDefault(category, 0.0) + amount);
                                    }
                                }

                                double savingTotal = incomeTotal[0] - expenseTotal[0];

                                new AiInsightService().generatePdfInsightsFromFirestore(
                                        startDate, selectedPeriod, categoryTotals,
                                        new AiInsightService.GeminiCallback() {
                                            @Override
                                            public void onSuccess(String result) {
                                                requireActivity().runOnUiThread(() -> {
                                                    try {
                                                        String cleaned = result.replaceAll("(?s)`{3}json\\s*|`{3}", "").trim();
                                                        JSONObject root = new JSONObject(cleaned);
                                                        JSONArray insightsArray = root.getJSONArray("insights");

                                                        File file = new File(requireContext().getCacheDir(), "GenAI_Insights_Report.pdf");

                                                        PdfReportCreator.generateReport(
                                                                requireContext(),
                                                                file,
                                                                insightsArray,
                                                                selectedPeriod,
                                                                incomeTotal[0],
                                                                expenseTotal[0],
                                                                savingTotal,
                                                                categoryTotals
                                                        );

                                                        uploadPdfAndSaveToFirestore(file, selectedPeriod);
                                                    } catch (Exception e) {
                                                        hideDownloadingDialog();
                                                        Toast.makeText(requireContext(), "Parsing failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                                    }
                                                });
                                            }

                                            @Override
                                            public void onFailure(String error) {
                                                hideDownloadingDialog();
                                                requireActivity().runOnUiThread(() ->
                                                        Toast.makeText(requireContext(), "Gemini error: " + error, Toast.LENGTH_LONG).show());
                                            }
                                        });
                            });
                });
    }

    /**
     * Uploads the generated PDF to Firebase Storage and records metadata in Firestore.
     */
    private void uploadPdfAndSaveToFirestore(File file, String period) {
        if (!file.exists()) {
            hideDownloadingDialog();
            Toast.makeText(requireContext(), "File does not exist.", Toast.LENGTH_LONG).show();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child("reports/" + uid + "/AI_Insights_Report.pdf");
        Uri fileUri = Uri.fromFile(file);

        storageRef.putFile(fileUri).addOnSuccessListener(taskSnapshot ->
                storageRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("reportUrl", downloadUri.toString());
                    data.put("generatedAt", new Timestamp(new Date()));
                    data.put("period", period);
                    data.put("fileName", "AI_Insights_" + period + ".pdf");
                    data.put("title", "AI Insights Report");

                    FirebaseFirestore.getInstance()
                            .collection("users").document(uid)
                            .collection("ai_report")
                            .add(data)
                            .addOnSuccessListener(doc -> {
                                hideDownloadingDialog();
                                showSuccessDialog("Report saved successfully!");
                            })
                            .addOnFailureListener(e -> {
                                hideDownloadingDialog();
                                Toast.makeText(requireContext(), "Firestore error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
        ).addOnFailureListener(e -> {
            hideDownloadingDialog();
            Toast.makeText(requireContext(), "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Shows the Lottie downloading animation dialog.
     */
    private void showDownloadingDialog() {
        loadingDialog = new Dialog(requireContext());
        loadingDialog.setContentView(R.layout.dialog_downloading_report);
        loadingDialog.setCancelable(false);
        loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        loadingDialog.show();
    }

    /**
     * Dismisses the Lottie loading dialog.
     */
    private void hideDownloadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }

    /**
     * Displays a reusable success dialog after saving report.
     *
     * @param message The message to show in the success dialog.
     */
    private void showSuccessDialog(String message) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_success);
        dialog.setCancelable(false);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setDimAmount(0.4f);
            dialog.getWindow().setGravity(Gravity.CENTER);
        }

        TextView messageText = dialog.findViewById(R.id.success_message_text);
        if (messageText != null) {
            messageText.setText(message);
        }

        dialog.show();

        new Handler().postDelayed(dialog::dismiss, 2000);
    }
}
