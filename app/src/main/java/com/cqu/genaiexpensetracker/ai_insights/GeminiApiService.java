package com.cqu.genaiexpensetracker.ai_insights;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

/**
 * Retrofit service interface for interacting with the Google Gemini API.
 * Defines the endpoint and request structure for generating content using the Gemini model.
 */
public interface GeminiApiService {

    /**
     * Sends a prompt to the Gemini model and retrieves a generated response.
     *
     * Endpoint: {@code POST v1beta1/models/gemini-pro:generateContent}
     *
     * @param request The request body containing the user prompt formatted for Gemini.
     * @return A {@link Call} that will asynchronously return a {@link GeminiResponse}.
     */
    @Headers({
            "Content-Type: application/json"
    })
    @POST("v1beta1/models/gemini-pro:generateContent")
    Call<GeminiResponse> generateContent(@Body GeminiRequest request);
}
