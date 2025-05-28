package com.cqu.genaiexpensetracker.notifications;

import java.util.Date;

/**
 * Model representing a single Firebase-triggered financial insight.
 * Displayed inside the Notifications screen.
 *
 * This model supports both Alert-type and Advice-type insights.
 */
public class NotificationItem {

    /** Firestore document ID */
    private String id;

    /** Insight title (e.g., "High Spending on Food" or "Balance Back to Positive") */
    private String title;

    /** Main message body (e.g., "You spent $140 on Food. Avg: $90.") */
    private String message;

    /** Expense or income category (e.g., Food, Transport, Income) */
    private String category;

    /** Explanation or reason for why the insight was triggered */
    private String reason;

    /** Timestamp when this insight was generated (from Firestore) */
    private Date timestamp;

    /** Whether this insight has been marked as read */
    private boolean read = false;

    /** Type of insight: "Alert" or "Advice" */
    private String type;

    /**
     * Required empty constructor for Firestore deserialization.
     */
    public NotificationItem() {}

    /**
     * Full constructor used when loading data from Firestore.
     *
     * @param id        Firestore document ID
     * @param title     Insight title
     * @param message   Insight message body
     * @param category  Related category (e.g., "Food")
     * @param reason    Reason for this insight (explanation)
     * @param timestamp Timestamp when insight was generated
     * @param read      Whether the insight has been marked as read
     * @param type      Type of insight: "Alert" or "Advice"
     */
    public NotificationItem(String id, String title, String message,
                            String category, String reason, Date timestamp, boolean read, String type) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.category = category;
        this.reason = reason;
        this.timestamp = timestamp;
        this.read = read;
        this.type = type;
    }

    /**
     * Gets the Firestore document ID.
     *
     * @return ID of the insight document
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the Firestore document ID.
     *
     * @param id Firestore document ID
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Gets the insight title.
     *
     * @return Title of the insight
     */
    public String getTitle() {
        return title;
    }

    /**
     * Gets the insight message content.
     *
     * @return Main insight message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Gets the category related to this insight.
     *
     * @return Category (e.g., Food, Rent)
     */
    public String getCategory() {
        return category;
    }

    /**
     * Gets the reason why this insight was generated.
     *
     * @return Reason for the insight
     */
    public String getReason() {
        return reason;
    }

    /**
     * Sets the reason for this insight.
     *
     * @param reason Explanation for this insight
     */
    public void setReason(String reason) {
        this.reason = reason;
    }

    /**
     * Gets the timestamp of the insight.
     *
     * @return Firestore timestamp
     */
    public Date getTimestamp() {
        return timestamp;
    }

    /**
     * Checks if the insight has been marked as read.
     *
     * @return true if read, false otherwise
     */
    public boolean isRead() {
        return read;
    }

    /**
     * Sets the read status of this insight.
     *
     * @param read true if marked as read, false if unread
     */
    public void setRead(boolean read) {
        this.read = read;
    }

    /**
     * Gets the insight type ("Alert" or "Advice").
     *
     * @return Type of insight
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the insight type ("Alert" or "Advice").
     *
     * @param type Insight type
     */
    public void setType(String type) {
        this.type = type;
    }
}
