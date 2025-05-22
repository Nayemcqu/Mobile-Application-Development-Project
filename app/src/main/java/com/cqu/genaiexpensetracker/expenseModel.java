package com.cqu.genaiexpensetracker;

import java.util.Date;

/**
 * Model class representing a single expense record stored in Firestore.
 * Each expense includes category, amount, time (HH:mm), optional note,
 * optional receipt URL, and a creation timestamp.
 */
public class expenseModel {

    private String category;
    private double amount;
    private String time;
    private String note;
    private String receiptUrl;
    private Date createdAt;

    /**
     * Default no-argument constructor required for Firestore deserialization.
     */
    public expenseModel() {
        // Required for Firebase
    }

    /**
     * Constructs an expenseModel with all properties.
     *
     * @param category   Expense category (e.g., Food, Rent)
     * @param amount     Expense amount (double)
     * @param time       Time string (e.g., 09:30)
     * @param note       Optional note provided by user
     * @param receiptUrl Optional receipt image URL from Firebase Storage
     * @param createdAt  Creation timestamp (used for sorting and filtering)
     */
    public expenseModel(String category, double amount, String time, String note, String receiptUrl, Date createdAt) {
        this.category = category;
        this.amount = amount;
        this.time = time;
        this.note = note;
        this.receiptUrl = receiptUrl;
        this.createdAt = createdAt;
    }

    // ------------------- Getters and Setters -------------------

    /**
     * Returns the expense category.
     */
    public String getCategory() {
        return category;
    }

    /**
     * Sets the expense category.
     */
    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * Returns the expense amount.
     */
    public double getAmount() {
        return amount;
    }

    /**
     * Sets the expense amount.
     */
    public void setAmount(double amount) {
        this.amount = amount;
    }

    /**
     * Returns the time of expense (as string).
     */
    public String getTime() {
        return time;
    }

    /**
     * Sets the time of expense.
     */
    public void setTime(String time) {
        this.time = time;
    }

    /**
     * Returns the user-provided note for the expense.
     */
    public String getNote() {
        return note;
    }

    /**
     * Sets the note for the expense.
     */
    public void setNote(String note) {
        this.note = note;
    }

    /**
     * Returns the URL of the uploaded receipt (if any).
     */
    public String getReceiptUrl() {
        return receiptUrl;
    }

    /**
     * Sets the receipt image URL.
     */
    public void setReceiptUrl(String receiptUrl) {
        this.receiptUrl = receiptUrl;
    }

    /**
     * Returns the creation timestamp.
     */
    public Date getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the creation timestamp.
     */
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
