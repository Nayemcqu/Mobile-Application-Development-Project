/**
 * AiInsightService.java
 *
 * Service responsible for generating AI-driven financial insights using Gemini API.
 *
 * Functionalities:
 * - Pulls user income and expense data from Firestore.
 * - Constructs prompts for UI insights or PDF-style summaries.
 * - Sends prompts to Gemini API with dynamic key from Firebase Remote Config.
 * - Returns structured AI-generated results via callback.
 *
 * Author: Kapil Pandey
 * Year  : 2025
 */

package com.cqu.genaiexpensetracker.ai_insights;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AiInsightService {

    public interface GeminiCallback {
        void onSuccess(String result);
        void onFailure(String error);
    }

    private static final String TAG = "AiInsightService";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final OkHttpClient client = new OkHttpClient();

    /**
     * Generates insights formatted for the UI using the user's financial data.
     *
     * @param startDate Start date for filtering data
     * @param period    Time range label (e.g., "Monthly")
     * @param callback  Callback to receive AI-generated results or errors
     */
    public void generateUiInsightsFromFirestore(Date startDate, String period, GeminiCallback callback) {
        fetchAndBuildPrompt(startDate, period, false, null, callback);
    }

    /**
     * Generates AI insights formatted for the PDF report.
     *
     * @param startDate       Start date for filtering
     * @param period          Time label (e.g., "Monthly")
     * @param categoryTotals  Map of categorized expense totals
     * @param callback        Callback to receive AI result or error
     */
    public void generatePdfInsightsFromFirestore(Date startDate, String period, Map<String, Double> categoryTotals, GeminiCallback callback) {
        fetchAndBuildPrompt(startDate, period, true, categoryTotals, callback);
    }

    /**
     * Fetches income/expense/insight data and builds prompt for AI.
     */
    private void fetchAndBuildPrompt(Date startDate, String period, boolean forPdf, Map<String, Double> categoryTotals, GeminiCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onFailure("User not authenticated.");
            return;
        }

        String uid = user.getUid();
        CollectionReference incomeRef = db.collection("users").document(uid).collection("income");
        CollectionReference expenseRef = db.collection("users").document(uid).collection("expenses");
        CollectionReference insightsRef = db.collection("users").document(uid).collection("insights");

        StringBuilder incomeData = new StringBuilder();
        StringBuilder expenseData = new StringBuilder();
        ArrayList<String> priorInsights = new ArrayList<>();

        insightsRef.get().addOnSuccessListener(snapshot -> {
            for (QueryDocumentSnapshot doc : snapshot) {
                String type = doc.getString("type");
                String text = doc.getString("text");
                if (("alert".equalsIgnoreCase(type) || "advice".equalsIgnoreCase(type)) && text != null) {
                    priorInsights.add("- " + text);
                }
            }

            incomeRef.whereGreaterThanOrEqualTo("timestamp", startDate).get()
                    .addOnSuccessListener(incomes -> {
                        for (QueryDocumentSnapshot doc : incomes) {
                            Double amount = doc.getDouble("amount");
                            String source = doc.getString("source");
                            Date date = doc.getDate("timestamp");
                            if (amount != null && source != null && date != null) {
                                incomeData.append(String.format(Locale.getDefault(),
                                        "- $%.2f from %s on %s\n", amount, source,
                                        new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)));
                            }
                        }

                        expenseRef.whereGreaterThanOrEqualTo("createdAt", startDate).get()
                                .addOnSuccessListener(expenses -> {
                                    for (QueryDocumentSnapshot doc : expenses) {
                                        Double amount = doc.getDouble("amount");
                                        String category = doc.getString("category");
                                        Date date = doc.getDate("createdAt");
                                        if (amount != null && category != null && date != null) {
                                            expenseData.append(String.format(Locale.getDefault(),
                                                    "- $%.2f on %s on %s\n", amount, category,
                                                    new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)));
                                        }
                                    }

                                    StringBuilder prompt = new StringBuilder();

                                    if (forPdf) {
                                        prompt.append("You are a financial analyst writing insights for a user's expense report based on real transaction data.\n")
                                                .append("Each insight must be 4–6 lines long, specific to the actual spending behavior shown in the data.\n")
                                                .append("Group insights by categories such as 'Food & Dining Expenses', 'Transportation & Travel Expenses', etc.\n")
                                                .append("Avoid repeating generic advice. Focus on identifying patterns, spikes, or anomalies in the user's actual income and expenses.\n")
                                                .append("Do NOT include icons, labels like 'alert' or 'advice', or markdown.\n");

                                        if (categoryTotals != null && !categoryTotals.isEmpty()) {
                                            prompt.append("\nCategory Totals:\n");
                                            for (Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
                                                prompt.append(String.format(Locale.getDefault(),
                                                        "- %s: $%.2f\n", entry.getKey(), entry.getValue()));
                                            }
                                        }

                                        prompt.append("\nRespond strictly in raw JSON format:\n")
                                                .append("{ \"insights\": [\n")
                                                .append("  { \"category\": \"Food & Dining Expenses\", \"text\": \"Your food expenses totaled $95.00 this month.\" },\n")
                                                .append("  { \"category\": \"Transportation & Travel Expenses\", \"text\": \"You spent $125.00 on transport.\" }\n")
                                                .append("] }");

                                    } else {
                                        prompt.append("You are a financial assistant. Generate 2-3 short tips for the user's ")
                                                .append(period.toLowerCase())
                                                .append(" activity.\nAvoid these:\n");
                                    }

                                    for (String prev : priorInsights) {
                                        prompt.append(prev).append("\n");
                                    }

                                    prompt.append("\nIncome Transactions:\n").append(incomeData);
                                    prompt.append("\nExpense Transactions:\n").append(expenseData);

                                    if (!forPdf) {
                                        prompt.append("\nRespond in JSON: { \"insights\": [ { \"text\": \"...\", \"icon\": \"alert|advice|info\" } ] }\n");
                                    }

                                    fetchGeminiApiKeyAndSend(prompt.toString(), callback);
                                })
                                .addOnFailureListener(e -> callback.onFailure("Expense fetch failed: " + e.getMessage()));
                    })
                    .addOnFailureListener(e -> callback.onFailure("Income fetch failed: " + e.getMessage()));
        }).addOnFailureListener(e -> callback.onFailure("Insight history fetch failed: " + e.getMessage()));
    }

    /**
     * Fetches Gemini API key from Firebase Remote Config and calls Gemini API.
     */
    private void fetchGeminiApiKeyAndSend(String prompt, GeminiCallback callback) {
        FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();
        remoteConfig.fetchAndActivate().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String key = remoteConfig.getString("gemini_api_key");
                if (TextUtils.isEmpty(key)) {
                    callback.onFailure("Gemini API key not found in Remote Config.");
                } else {
                    sendGeminiRequest(prompt, key, callback);
                }
            } else {
                callback.onFailure("Failed to fetch Gemini API key.");
            }
        });
    }

    /**
     * Sends the actual HTTP request to Gemini with prompt + key.
     */
    private void sendGeminiRequest(String prompt, String apiKey, GeminiCallback callback) {
        try {
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray().put(new JSONObject().put("text", prompt));
            content.put("contents", new JSONArray().put(new JSONObject()
                    .put("role", "user")
                    .put("parts", parts)));

            String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro-latest:generateContent?key=" + apiKey;

            RequestBody body = RequestBody.create(content.toString(), MediaType.get("application/json"));
            Request request = new Request.Builder().url(endpoint).post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Gemini request failed", e);
                    callback.onFailure("Gemini request failed: " + e.getMessage());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        callback.onFailure("Gemini response error: " + response.code());
                        return;
                    }

                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);
                        JSONArray candidates = json.getJSONArray("candidates");
                        JSONObject content = candidates.getJSONObject(0).getJSONObject("content");
                        JSONArray parts = content.getJSONArray("parts");

                        String jsonText = parts.getJSONObject(0).getString("text");
                        callback.onSuccess(jsonText.trim());

                    } catch (Exception e) {
                        callback.onFailure("Parsing error: " + e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            callback.onFailure("Request build error: " + e.getMessage());
        }
    }
}
