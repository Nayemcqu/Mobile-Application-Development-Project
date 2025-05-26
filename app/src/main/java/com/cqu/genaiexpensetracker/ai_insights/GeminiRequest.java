package com.cqu.genaiexpensetracker.ai_insights;

import java.util.Collections;
import java.util.List;

/**
 * Represents the request payload structure for the Gemini API.
 * This class builds the expected JSON format for sending user prompts
 * to Google's Vertex AI Gemini generative model.
 */
public class GeminiRequest {

    /** The list of conversation contents (e.g., user prompts). */
    public List<Content> contents;

    /**
     * Constructs a GeminiRequest with a single user prompt.
     *
     * @param prompt The prompt text to be sent to Gemini.
     */
    public GeminiRequest(String prompt) {
        this.contents = Collections.singletonList(new Content(prompt));
    }

    /**
     * Inner class representing a single message content.
     */
    public static class Content {

        /** The role of the message sender (e.g., "user"). */
        public String role = "user";

        /** The list of message parts (typically one Part object with text). */
        public List<Part> parts;

        /**
         * Constructs a Content object with the provided prompt.
         *
         * @param prompt The prompt text to be wrapped in a Part object.
         */
        public Content(String prompt) {
            this.parts = Collections.singletonList(new Part(prompt));
        }
    }

    /**
     * Inner class representing a part of a message.
     */
    public static class Part {

        /** The textual content of this message part. */
        public String text;

        /**
         * Constructs a Part object with the given text.
         *
         * @param text The actual text content of the message.
         */
        public Part(String text) {
            this.text = text;
        }
    }
}
