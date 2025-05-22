package com.cqu.genaiexpensetracker;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ViewModel for the Dashboard screen.
 * Manages retrieval of transactions and calculates total income, expense, and balance.
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
     * Loads transactions from Firestore and calculates totals and balance.
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

        List<TransactionItem> merged = new ArrayList<>();
        final double[] expenseSum = {0.0};
        final double[] incomeSum = {0.0};

        expensesRef.get().addOnSuccessListener(expenseSnapshot -> {
            for (QueryDocumentSnapshot doc : expenseSnapshot) {
                expenseModel expense = doc.toObject(expenseModel.class);
                merged.add(new TransactionItem(expense, null));
                expenseSum[0] += expense.getAmount();
            }

            incomesRef.get().addOnSuccessListener(incomeSnapshot -> {
                for (QueryDocumentSnapshot doc : incomeSnapshot) {
                    incomeModel income = doc.toObject(incomeModel.class);
                    merged.add(new TransactionItem(null, income));
                    incomeSum[0] += income.getAmount();
                }

                // Sort all by descending creation time
                Collections.sort(merged, (a, b) -> Long.compare(b.getTime(), a.getTime()));

                combinedLiveData.setValue(merged);
                totalIncome.setValue(incomeSum[0]);
                totalExpense.setValue(expenseSum[0]);
                balance.setValue(incomeSum[0] - expenseSum[0]);
            });
        });
    }
}
