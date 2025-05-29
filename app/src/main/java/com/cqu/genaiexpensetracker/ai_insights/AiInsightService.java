package com.cqu.genaiexpensetracker.ai_insights;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Service class responsible for:
 * - Fetching income and expense data from Firestore
 * - Filtering them based on a given date
 * - Generating a structured natural language prompt for Gemini
 * - Parsing Gemini's AI-generated financial insights
 */
public class AiInsightService {

    /**
     * Callback interface to handle Gemini response.
     */
    public interface GeminiCallback {
        void onSuccess(String result);
        void onFailure(String error);
    }

    private static final String TAG = "AiInsightService";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final OkHttpClient client = new OkHttpClient();

    private static final String API_KEY = "AIzaSyCbsnh-eQj6kVAI1DmX3GORxhxyQmFYK_c";
    private static final String GEMINI_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro-latest:generateContent?key=" + API_KEY;

    /**
     * Generates AI-based financial insights using income and expense data
     * filtered from Firestore starting at a specific date.
     *
     * @param startDate Start date to filter transactions
     * @param period The selected period (e.g., Weekly, Monthly, Yearly)
     * @param callback Callback to return the AI response or failure
     */
    public void generateInsightFromFirestore(Date startDate, String period, GeminiCallback callback) {
        String uid = auth.getCurrentUser().getUid();
        CollectionReference incomeRef = db.collection("users").document(uid).collection("income");
        CollectionReference expenseRef = db.collection("users").document(uid).collection("expenses");

        StringBuilder incomeData = new StringBuilder();
        StringBuilder expenseData = new StringBuilder();

        // Step 1: Fetch income data
        incomeRef.whereGreaterThanOrEqualTo("timestamp", startDate)
                .get()
                .addOnSuccessListener(incomes -> {
                    for (QueryDocumentSnapshot doc : incomes) {
                        Double amount = doc.getDouble("amount");
                        String source = doc.getString("source");
                        Date date = doc.getDate("timestamp");

                        if (amount != null && source != null && date != null) {
                            incomeData.append(String.format(Locale.getDefault(),
                                    "- $%.2f from %s on %s\n",
                                    amount, source,
                                    new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)));
                        }
                    }

                    // Step 2: Fetch expense data
                    expenseRef.whereGreaterThanOrEqualTo("createdAt", startDate)
                            .get()
                            .addOnSuccessListener(expenses -> {
                                for (QueryDocumentSnapshot doc : expenses) {
                                    Double amount = doc.getDouble("amount");
                                    String category = doc.getString("category");
                                    Date date = doc.getDate("createdAt");

                                    if (amount != null && category != null && date != null) {
                                        expenseData.append(String.format(Locale.getDefault(),
                                                "- $%.2f on %s on %s\n",
                                                amount, category,
                                                new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)));
                                    }
                                }

                                // Step 3: Construct Gemini prompt with structured icon-based output
                                String prompt =
                                        "You are a financial assistant generating insights from a user's financial behavior.\n" +
                                                "Based on the user's " + period.toLowerCase() + " income and expense data, return exactly 3 insights.\n\n" +
                                                "For each insight, respond in the following JSON format:\n" +
                                                "{ \"text\": \"short insight message\", \"icon\": \"one of: alert, progress, trend, info, default\" }\n\n" +
                                                "Rules:\n" +
                                                "- Keep 'text' to a maximum of 2 short lines (1-2 sentences).\n" +
                                                "- Use icon types based on tone:\n" +
                                                "   - alert: for warnings, overspending, negative cash flow\n" +
                                                "   - progress: for savings, good habits\n" +
                                                "   - trend: for investment or growth advice\n" +
                                                "   - info: for neutral/analytical observations\n" +
                                                "   - default: fallback\n\n" +
                                                "Income Transactions:\n" + incomeData + "\n\n" +
                                                "Expense Transactions:\n" + expenseData + "\n\n" +
                                                "Respond ONLY as:\n" +
                                                "{ \"insights\": [ { \"text\": \"...\", \"icon\": \"...\" }, { ... }, { ... } ] }";

                                // Step 4: Send prompt to Gemini
                                sendGeminiRequest(prompt, callback);
                            })
                            .addOnFailureListener(e -> callback.onFailure("Expense fetch failed: " + e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onFailure("Income fetch failed: " + e.getMessage()));
    }

    /**
     * Sends a formatted prompt to Gemini and parses the response.
     *
     * @param prompt The constructed instruction prompt for Gemini
     * @param callback Callback to handle success or failure response
     */
    private void sendGeminiRequest(String prompt, GeminiCallback callback) {
        try {
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray().put(new JSONObject().put("text", prompt));
            content.put("contents", new JSONArray().put(new JSONObject()
                    .put("role", "user")
                    .put("parts", parts)));

            RequestBody body = RequestBody.create(content.toString(), MediaType.get("application/json"));
            Request request = new Request.Builder().url(GEMINI_ENDPOINT).post(body).build();

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

                        // Gemini returns full JSON object string inside 'text'
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
