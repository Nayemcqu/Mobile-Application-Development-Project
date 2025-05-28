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

    /**
     * Returns the expense amount.
     *
     * @return The amount of the transaction.
     */
    public double getAmount() {
        return amount;
    }

    /**
     * Sets the expense amount.
     *
     * @param amount The amount to set for the transaction.
     */
    public void setAmount(double amount) {
        this.amount = amount;
    }

    /**
     * Returns the source of income or expense (e.g., salary, food, rent).
     *
     * @return The source/category of the transaction.
     */
    public String getSource() {
        return source;
    }

    /**
     * Sets the source of income or expense.
     *
     * @param source The source or category to assign.
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * Returns the timestamp of when the transaction occurred.
     *
     * @return The timestamp of the transaction.
     */
    public Timestamp getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the timestamp for the transaction.
     *
     * @param timestamp The timestamp to set.
     */
    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Returns the additional note or description for the transaction.
     *
     * @return The note associated with the transaction.
     */
    public String getNote() {
        return note;
    }

    /**
     * Sets the note or description for the transaction.
     *
     * @param note The note to assign.
     */
    public void setNote(String note) {
        this.note = note;
    }

    /**
     * Returns the receipt URL if a receipt was uploaded.
     *
     * @return The URL of the uploaded receipt image or file.
     */
    public String getReceiptUrl() {
        return receiptUrl;
    }

    /**
     * Sets the receipt URL for the transaction.
     *
     * @param receiptUrl The URL of the receipt to set.
     */
    public void setReceiptUrl(String receiptUrl) {
        this.receiptUrl = receiptUrl;
    }

}
