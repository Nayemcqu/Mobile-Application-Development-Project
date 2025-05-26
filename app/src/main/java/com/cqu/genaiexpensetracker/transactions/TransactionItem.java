package com.cqu.genaiexpensetracker.transactions;

import com.cqu.genaiexpensetracker.expense.expenseModel;
import com.cqu.genaiexpensetracker.income.incomeModel;

/**
 * Represents a unified transaction item which can either be an income or expense.
 * Used in RecyclerView to merge both types into a single list.
 */
public class TransactionItem {

    /**
     * Expense data (null if this item is an income)
     */
    public expenseModel expense;

    /**
     * Income data (null if this item is an expense)
     */
    public incomeModel income;

    /**
     * Default no-arg constructor.
     * Required for Firestore deserialization.
     */
    public TransactionItem() {
        // Needed for Firebase Firestore
    }

    /**
     * Constructs a TransactionItem with either expense or income populated.
     *
     * @param expense The expense model (can be null if income)
     * @param income  The income model (can be null if expense)
     */
    public TransactionItem(expenseModel expense, incomeModel income) {
        this.expense = expense;
        this.income = income;
    }

    /**
     * Checks if this item represents an expense.
     *
     * @return true if it is an expense, false if it is income.
     */
    public boolean isExpense() {
        return expense != null;
    }

    /**
     * Returns the timestamp (in milliseconds) for sorting transactions.
     * Uses `createdAt` from expense or `timestamp` from income.
     *
     * @return timestamp in milliseconds, or 0 if missing.
     */
    public long getTime() {
        if (isExpense()) {
            return (expense.getCreatedAt() != null) ? expense.getCreatedAt().getTime() : 0;
        } else {
            return (income.getTimestamp() != null) ? income.getTimestamp().toDate().getTime() : 0;
        }
    }
}
