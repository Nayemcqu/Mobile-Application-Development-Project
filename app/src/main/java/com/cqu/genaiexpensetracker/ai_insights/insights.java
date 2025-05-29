package com.cqu.genaiexpensetracker.ai_insights;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.cqu.genaiexpensetracker.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Date;
import java.util.Locale;

/**
 * Fragment to display AI-generated financial insights including total income, expense,
 * savings summary cards, and Gemini-based tips or alerts.
 *
 * Features:
 * - Time-based filtering (Weekly, Monthly, Yearly)
 * - Scrollable insight cards with icons
 * - Firestore integration for summary data
 * - Gemini API interaction for insight generation
 */
public class insights extends Fragment {

    private AutoCompleteTextView filterDropdown;
    private LinearLayout insightItemsContainer;
    private MaterialButton generateBtn;
    private String selectedPeriod = "Weekly"; // Persisted filter value

    public insights() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.ai_insights, container, false);

        // Initialize UI components
        filterDropdown = view.findViewById(R.id.filter_dropdown);
        insightItemsContainer = view.findViewById(R.id.insight_items_container);
        generateBtn = view.findViewById(R.id.button_generate_insights);

        setupDropdownFilter(); // Dropdown binding

        generateBtn.setOnClickListener(v -> generateInsights());
        loadSummaryData(selectedPeriod); // Load initial data

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        setupDropdownFilter(); // Rebind dropdown on return
    }

    /**
     * Sets up the dropdown menu for period selection and handles its behavior.
     */
    private void setupDropdownFilter() {
        String[] filterOptions = {"Weekly", "Monthly", "Yearly"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), R.layout.ai_dropdown_menu_item, filterOptions);
        filterDropdown.setAdapter(adapter);
        filterDropdown.setDropDownBackgroundResource(R.drawable.ai_modern_dropdown_bg);
        filterDropdown.setText(selectedPeriod, false); // Restore selection

        filterDropdown.setOnClickListener(v -> filterDropdown.showDropDown());

        filterDropdown.setOnItemClickListener((parent, view, position, id) -> {
            selectedPeriod = filterOptions[position];
            filterDropdown.setText(selectedPeriod, false);
            loadSummaryData(selectedPeriod);
        });
    }

    /**
     * Loads income, expense, and savings summary from Firestore
     * based on selected time filter (weekly/monthly/yearly).
     *
     * @param period The selected filter (Weekly, Monthly, Yearly)
     */
    private void loadSummaryData(String period) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        long now = System.currentTimeMillis();
        long startMillis;

        switch (period.toLowerCase()) {
            case "monthly":
                startMillis = now - 30L * 24 * 60 * 60 * 1000;
                break;
            case "yearly":
                startMillis = now - 365L * 24 * 60 * 60 * 1000;
                break;
            default:
                startMillis = now - 7L * 24 * 60 * 60 * 1000;
        }

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
                                requireView().<TextView>findViewById(R.id.summary_saving_count)
                                        .setText(String.valueOf(incomeCount[0] + expenseCount[0]));
                            });
                });
    }

    /**
     * Sends filtered data to Gemini and renders AI-generated financial insights dynamically.
     */
    private void generateInsights() {
        insightItemsContainer.removeAllViews();
        generateBtn.setEnabled(false);
        generateBtn.setText("Generating...");

        long now = System.currentTimeMillis();
        long startMillis;

        switch (selectedPeriod.toLowerCase()) {
            case "monthly":
                startMillis = now - 30L * 24 * 60 * 60 * 1000;
                break;
            case "yearly":
                startMillis = now - 365L * 24 * 60 * 60 * 1000;
                break;
            default:
                startMillis = now - 7L * 24 * 60 * 60 * 1000;
        }

        Date startDate = new Date(startMillis);

        new AiInsightService().generateInsightFromFirestore(startDate, selectedPeriod, new AiInsightService.GeminiCallback() {
            @Override
            public void onSuccess(String result) {
                if (!isAdded()) return;

                requireActivity().runOnUiThread(() -> {
                    generateBtn.setEnabled(true);
                    generateBtn.setText("Generate AI Insights");

                    try {
                        // Remove ```json ... ``` wrapper from Gemini if present
                        String cleanJson = result.replaceAll("(?s)```json\\s*|```", "").trim();

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
                    generateBtn.setText("Generate AI Insights");
                    Toast.makeText(requireContext(), "Failed: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Adds an individual Gemini insight into the scrollable container with proper icon.
     *
     * @param text  AI-generated insight message
     * @param iconType The icon type returned from Gemini ("alert", "progress", etc.)
     */
    private void addInsightToContainer(String text, String iconType) {
        View insightView = LayoutInflater.from(requireContext())
                .inflate(R.layout.ai_insight_item, insightItemsContainer, false);

        ImageView icon = insightView.findViewById(R.id.insight_icon);
        TextView message = insightView.findViewById(R.id.insight_text);
        message.setText(text);

        // Icon mapping based on returned Gemini type
        switch (iconType.toLowerCase()) {
            case "alert":
                icon.setImageResource(R.drawable.ic_error_triangle_icon);
                break;
            case "progress":
                icon.setImageResource(R.drawable.ic_data_analytics_icon);
                break;
            case "trend":
                icon.setImageResource(R.drawable.ic_growth_arrow);
                break;
            case "info":
                icon.setImageResource(R.drawable.ic_insight_info);
                break;
            default:
                icon.setImageResource(R.drawable.ic_default_insights_icon);
                break;
        }

        insightItemsContainer.addView(insightView);
    }
}
