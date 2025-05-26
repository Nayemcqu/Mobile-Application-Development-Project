package com.cqu.genaiexpensetracker.ai_insights;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GetTokenResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.security.MessageDigest;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * AiInsightService communicates with Gemini API,
 * parses structured Alerts and Advice,
 * and sends them to the backend via Cloud Run for:
 * - Deduplication
 * - Firestore storage
 * - FCM push notification
 */
public class AiInsightService {

    private static final String TAG = "AiInsightService";

    private static final String GEMINI_API_KEY = "AIzaSyCbsnh-eQj6kVAI1DmX3GORxhxyQmFYK_c";
    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=";
    private static final String FCM_NOTIFY_URL =
            "https://api-7carwvirja-uc.a.run.app/notify-insight";

    /**
     * Callback interface to get async status.
     */
    public interface AiCallback {
        void onSuccess(String message);
        void onFailure(String error);
    }

    /**
     * Starts the Gemini insight generation process.
     *
     * @param prompt        The Gemini prompt to send.
     * @param fallbackTag   Category fallback for labeling (e.g., "Food").
     * @param fallbackTitle Title fallback if none returned from Gemini.
     * @param callback      Result callback for success/failure.
     */
    public static void generateInsight(String prompt, String fallbackTag, String fallbackTitle, AiCallback callback) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            callback.onFailure("User not signed in.");
            return;
        }

        FirebaseAuth.getInstance().getCurrentUser().getIdToken(true)
                .addOnSuccessListener((GetTokenResult result) ->
                        sendGeminiRequest(prompt, fallbackTag, fallbackTitle, callback))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Auth token fetch failed", e);
                    callback.onFailure("Firebase Auth error: " + e.getMessage());
                });
    }

    /**
     * Sends the prompt to Gemini API using HTTP.
     */
    private static void sendGeminiRequest(String prompt, String tag, String fallbackTitle, AiCallback callback) {
        try {
            JSONObject part = new JSONObject().put("text", prompt);
            JSONObject content = new JSONObject().put("parts", new JSONArray().put(part));
            JSONObject requestBody = new JSONObject().put("contents", new JSONArray().put(content));

            Request request = new Request.Builder()
                    .url(BASE_URL + GEMINI_API_KEY)
                    .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                    .build();

            new OkHttpClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Gemini API failure", e);
                    callback.onFailure("Gemini request failed: " + e.getMessage());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String json = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        callback.onFailure("Gemini HTTP error: " + response.code());
                        return;
                    }
                    handleGeminiResponse(json, tag, fallbackTitle, callback);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Gemini build request error", e);
            callback.onFailure("Gemini request build error: " + e.getMessage());
        }
    }

    /**
     * Parses the Gemini response and sends each item to your backend.
     */
    private static void handleGeminiResponse(String json, String fallbackTag, String fallbackTitle, AiCallback callback) {
        try {
            JSONObject responseJson = new JSONObject(json);
            String text = responseJson
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");

            // Remove markdown ```json if present
            text = text.trim();
            if (text.startsWith("```json")) text = text.replaceFirst("```json", "").trim();
            if (text.endsWith("```")) text = text.substring(0, text.length() - 3).trim();

            JSONArray insights;
            try {
                insights = new JSONArray(text);
            } catch (Exception e) {
                insights = new JSONArray();
                insights.put(new JSONObject(text)); // fallback if Gemini returned object
            }

            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

            for (int i = 0; i < insights.length(); i++) {
                JSONObject insight = insights.getJSONObject(i);

                String type = insight.optString("type", "Advice");
                String title = insight.optString("title", fallbackTitle);
                String body = insight.optString("body", "");
                String category = insight.optString("category", fallbackTag);
                String hash = hashMessage(title + body);

                sendToBackend(uid, title, body, type, category, hash, callback);
            }

            callback.onSuccess("Gemini response processed.");

        } catch (Exception e) {
            Log.e(TAG, "Gemini JSON parse error", e);
            callback.onFailure("Parsing error: " + e.getMessage());
        }
    }

    /**
     * Sends one insight to your backend to be stored, deduplicated, and notified via FCM.
     */
    private static void sendToBackend(String uid, String title, String body, String type,
                                      String category, String hash, AiCallback callback) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("uid", uid);
            payload.put("title", title);
            payload.put("message", body);
            payload.put("type", type);
            payload.put("category", category);
            payload.put("messageHash", hash);

            Request request = new Request.Builder()
                    .url(FCM_NOTIFY_URL)
                    .post(RequestBody.create(payload.toString(), MediaType.parse("application/json")))
                    .addHeader("Content-Type", "application/json")
                    .build();

            new OkHttpClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "notify-insight API failure", e);
                    callback.onFailure("notify-insight failed: " + e.getMessage());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    Log.d(TAG, "notify-insight status: " + response.code());
                    if (response.code() == 200) {
                        callback.onSuccess("Insight delivered to server");
                    } else {
                        callback.onFailure("Server returned error code: " + response.code());
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Payload creation error", e);
            callback.onFailure("Payload error: " + e.getMessage());
        }
    }

    /**
     * SHA-256 hash for deduplication key, returns first 8 bytes.
     */
    private static String hashMessage(String message) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(message.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(message.hashCode());
        }
    }
}
