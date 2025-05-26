package com.cqu.genaiexpensetracker.notifications;

import java.util.Date;

/**
 * Model representing a single AI-generated insight (Alert or Advice)
 * displayed in the notifications screen.
 */
public class NotificationItem {

    /** Insight type: "Advice" or "Alert" */
    private String type;

    /** Body or subtitle of the insight (e.g., "You spent 50% more than...") */
    private String message;

    /** Title of the alert (e.g., "High Spending on Food") — optional for Advice */
    private String title;

    /** Spending category (e.g., "Food", "Transport") — used for Alerts */
    private String category;

    /** Firestore timestamp for relative time display */
    private Date timestamp;

    /**
     * No-arg constructor required by Firebase Firestore
     */
    public NotificationItem() {}

    /**
     * Full constructor for creating notification items.
     *
     * @param type     Type of insight ("Advice" or "Alert")
     * @param title    Title for alerts (can be null for advice)
     * @param message  Insight body message
     * @param category Category like "Food", "Rent", etc.
     * @param timestamp Timestamp of creation
     */
    public NotificationItem(String type, String title, String message, String category, Date timestamp) {
        this.type = type;
        this.title = title;
        this.message = message;
        this.category = category;
        this.timestamp = timestamp;
    }

    /** @return "Advice" or "Alert" */
    public String getType() {
        return type;
    }

    /** @return Message content of the insight */
    public String getMessage() {
        return message;
    }

    /** @return Title of the alert (may be null for advice) */
    public String getTitle() {
        return title;
    }

    /** @return Expense category (e.g., Food, Transport) */
    public String getCategory() {
        return category;
    }

    /** @return Date and time the insight was generated */
    public Date getTimestamp() {
        return timestamp;
    }
}
