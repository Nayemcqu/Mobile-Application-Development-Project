package com.cqu.genaiexpensetracker.income;

import com.google.firebase.Timestamp;

/**
 * Model class representing a single income entry.
 * Used for storing and retrieving income data from Firestore.
 */
public class incomeModel {

    private double amount;
    private String source;
    private Timestamp timestamp;
    private String note;
    private String receiptUrl;

    /**
     * Default constructor required for Firebase deserialization.
     */
    public incomeModel() {
        // Required empty public constructor
    }

    /**
     * Constructs an IncomeModel with all properties.
     *
     * @param amount     The amount of income (e.g., 500.00).
     * @param source     The source of income (e.g., "Salary").
     * @param timestamp  The timestamp of income entry.
     * @param note       Optional note about the income.
     * @param receiptUrl Optional URL to the uploaded payslip.
     */
    public incomeModel(double amount, String source, Timestamp timestamp, String note, String receiptUrl) {
        this.amount = amount;
        this.source = source;
        this.timestamp = timestamp;
        this.note = note;
        this.receiptUrl = receiptUrl;
    }

    // --------------------
    // Getter and Setter methods
    // --------------------

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getReceiptUrl() {
        return receiptUrl;
    }

    public void setReceiptUrl(String receiptUrl) {
        this.receiptUrl = receiptUrl;
    }
}
