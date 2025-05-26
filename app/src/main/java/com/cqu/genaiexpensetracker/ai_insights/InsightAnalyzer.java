package com.cqu.genaiexpensetracker.ai_insights;

import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cqu.genaiexpensetracker.ai_insights.AiInsightService.AiCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * InsightAnalyzer analyzes user's income and expense behavior,
 * determines financial imbalance or top spending patterns,
 * builds a Gemini prompt, and delegates insight generation.
 */
public class InsightAnalyzer {

    private static final String TAG = "InsightAnalyzer";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final String uid = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getUid()
            : null;

    private static boolean alreadyTriggered = false;

    /**
     * Trigger Gemini insight generation.
     * Optional `onComplete` will run after insight finishes (for UI refresh).
     */
    public void analyzeAndGenerate(@Nullable Runnable onComplete) {
        if (uid == null) {
            Log.e(TAG, "User not authenticated.");
            if (onComplete != null) onComplete.run();
            return;
        }

        if (alreadyTriggered) {
            Log.d(TAG, "Insight generation already triggered. Skipping this call.");
            if (onComplete != null) onComplete.run();
            return;
        }

        alreadyTriggered = true;
        new Handler().postDelayed(() -> alreadyTriggered = false, 30000); // reset after 30s

        final double[] totalIncome = {0};
        final Map<String, Double> categoryTotals = new HashMap<>();

        db.collection("users").document(uid).collection("income").get()
                .addOnSuccessListener(incomeSnapshot -> {
                    for (QueryDocumentSnapshot doc : incomeSnapshot) {
                        Number amount = doc.getDouble("amount");
                        totalIncome[0] += amount != null ? amount.doubleValue() : 0;
                    }

                    db.collection("users").document(uid).collection("expenses").get()
                            .addOnSuccessListener(expenseSnapshot -> {
                                double totalExpense = 0;

                                for (QueryDocumentSnapshot doc : expenseSnapshot) {
                                    Number amount = doc.getDouble("amount");
                                    String category = doc.getString("category");

                                    if (amount != null) {
                                        totalExpense += amount.doubleValue();
                                        if (category != null) {
                                            categoryTotals.put(category,
                                                    categoryTotals.getOrDefault(category, 0.0) + amount.doubleValue());
                                        }
                                    }
                                }

                                if (totalIncome[0] == 0 && totalExpense == 0) {
                                    Log.d(TAG, "No income or expense data available. Skipping AI generation.");
                                    if (onComplete != null) onComplete.run();
                                    return;
                                }

                                double balance = totalIncome[0] - totalExpense;
                                String topCategory = getTopSpendingCategory(categoryTotals);
                                double topAmount = categoryTotals.getOrDefault(topCategory, 0.0);

                                String prompt = buildPrompt(totalIncome[0], totalExpense, balance, topCategory, topAmount);
                                String fallbackTitle = balance < 0
                                        ? String.format(Locale.getDefault(), "High Spending on %s", topCategory)
                                        : "Good Budgeting";
                                String tag = topCategory != null ? topCategory : "General";

                                Log.d(TAG, "Sending Gemini prompt:\n" + prompt);

                                AiInsightService.generateInsight(prompt, tag, fallbackTitle, new AiCallback() {
                                    @Override
                                    public void onSuccess(@NonNull String message) {
                                        Log.d(TAG, "AI insight generated: " + message);
                                        if (onComplete != null) onComplete.run();
                                    }

                                    @Override
                                    public void onFailure(@NonNull String error) {
                                        Log.e(TAG, "AI insight failed: " + error);
                                        if (onComplete != null) onComplete.run();
                                    }
                                });

                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to fetch expenses", e);
                                if (onComplete != null) onComplete.run();
                            });

                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch income", e);
                    if (onComplete != null) onComplete.run();
                });
    }

    /**
     * Returns the category with the highest total expense.
     */
    private String getTopSpendingCategory(Map<String, Double> map) {
        return map.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("General");
    }

    /**
     * Builds a formatted prompt for Gemini to analyze financial behavior.
     */
    private String buildPrompt(double income, double expense, double balance, String category, double categoryAmount) {
        return String.format(Locale.getDefault(),
                "The user's total income is $%.2f and expenses are $%.2f, resulting in a balance of $%.2f. " +
                        "The highest spending category is %s with $%.2f.\n\n" +
                        "Return a JSON array with one Alert and one Advice. Each should be structured like:\n" +
                        "{ \"type\": \"Alert\", \"title\": \"High Spending on %s\", " +
                        "\"body\": \"You spent $%.2f on %s, which is your highest category.\", " +
                        "\"category\": \"%s\" }\n" +
                        "If balance is negative, also suggest:\n" +
                        "{ \"type\": \"Advice\", \"title\": \"Reduce Expenses\", " +
                        "\"body\": \"Your expenses exceed your income by $%.2f. Consider reducing spending or finding ways to increase your income.\", " +
                        "\"category\": \"General\" }\n" +
                        "Respond only with a valid JSON array. Do not use markdown or code blocks.",
                income, expense, balance,
                category, categoryAmount,
                category, categoryAmount, category, category,
                -balance);
    }
}
