package com.cqu.genaiexpensetracker.dashboard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.cqu.genaiexpensetracker.expense.expenseModel;
import com.cqu.genaiexpensetracker.income.incomeModel;
import com.cqu.genaiexpensetracker.transactions.TransactionItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * ViewModel for the Dashboard (Home) screen.
 *
 * Responsibilities:
 * Fetches income and expense data from Firestore (past 7 days only)
 * Calculates total income, total expense, and balance
 * Combines both into a unified transaction list for recent display
 * Does NOT trigger AI or alert logic directly — handled by Firebase Functions
 */
public class DashboardViewModel extends ViewModel {

    private final MutableLiveData<List<TransactionItem>> combinedLiveData = new MutableLiveData<>();
    private final MutableLiveData<Double> totalIncome = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> totalExpense = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> balance = new MutableLiveData<>(0.0);

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    public LiveData<List<TransactionItem>> getTransactions() {
        return combinedLiveData;
    }

    public LiveData<Double> getTotalIncome() {
        return totalIncome;
    }

    public LiveData<Double> getTotalExpense() {
        return totalExpense;
    }

    public LiveData<Double> getBalance() {
        return balance;
    }

    /**
     * Loads all-time total income & expense,
     * but only recent 7-day transactions list.
     */
    public void loadTransactions() {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;

        if (uid == null) {
            combinedLiveData.setValue(new ArrayList<>());
            totalIncome.setValue(0.0);
            totalExpense.setValue(0.0);
            balance.setValue(0.0);
            return;
        }

        CollectionReference expensesRef = firestore.collection("users").document(uid).collection("expenses");
        CollectionReference incomesRef = firestore.collection("users").document(uid).collection("income");

        List<TransactionItem> recentMerged = new ArrayList<>();
        final double[] allExpenseSum = {0.0};
        final double[] allIncomeSum = {0.0};

        // Step 1: Get ALL expenses for total
        expensesRef.get().addOnSuccessListener(allExpenses -> {
            for (QueryDocumentSnapshot doc : allExpenses) {
                expenseModel expense = doc.toObject(expenseModel.class);
                allExpenseSum[0] += expense.getAmount();
            }

            // Step 2: Get ALL income for total
            incomesRef.get().addOnSuccessListener(allIncome -> {
                for (QueryDocumentSnapshot doc : allIncome) {
                    incomeModel income = doc.toObject(incomeModel.class);
                    allIncomeSum[0] += income.getAmount();
                }

                // Step 3: Update total income/expense/balance
                totalIncome.setValue(allIncomeSum[0]);
                totalExpense.setValue(allExpenseSum[0]);
                balance.setValue(allIncomeSum[0] - allExpenseSum[0]);

                // Step 4: Load recent 7-day transactions for list
                long sevenDaysMillis = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
                Date oneWeekAgo = new Date(sevenDaysMillis);

                // Load recent expenses
                expensesRef.whereGreaterThanOrEqualTo("timestamp", oneWeekAgo)
                        .get().addOnSuccessListener(recentExp -> {
                            for (QueryDocumentSnapshot doc : recentExp) {
                                expenseModel expense = doc.toObject(expenseModel.class);
                                recentMerged.add(new TransactionItem(expense, null));
                            }

                            // Load recent income
                            incomesRef.whereGreaterThanOrEqualTo("timestamp", oneWeekAgo)
                                    .get().addOnSuccessListener(recentInc -> {
                                        for (QueryDocumentSnapshot doc : recentInc) {
                                            incomeModel income = doc.toObject(incomeModel.class);
                                            recentMerged.add(new TransactionItem(null, income));
                                        }

                                        // Sort recent transactions descending
                                        Collections.sort(recentMerged, (a, b) -> Long.compare(b.getTime(), a.getTime()));
                                        combinedLiveData.setValue(recentMerged);
                                    });
                        });

            });
        });
    }
}

