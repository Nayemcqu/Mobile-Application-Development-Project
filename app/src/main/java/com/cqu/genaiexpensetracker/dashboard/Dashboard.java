package com.cqu.genaiexpensetracker.dashboard;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.cqu.genaiexpensetracker.R;
import com.cqu.genaiexpensetracker.navbar.navbar;
import com.cqu.genaiexpensetracker.transactions.TransactionAdapter;
import com.cqu.genaiexpensetracker.transactions.TransactionItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dashboard Fragment (Home)
 *
 * Responsibilities:
 * - Displays total balance, income, expense
 * - Shows most recent Firebase alert summary (auto-triggered)
 * - Shows recent transactions (past 7 days)
 */
public class Dashboard extends Fragment {

    private final List<TransactionItem> transactionList = new ArrayList<>();
    private RecyclerView recyclerView;
    private LottieAnimationView emptyAnimation;
    private TransactionAdapter adapter;
    private DashboardViewModel viewModel;

    private TextView totalBalanceText, incomeText, expenseText;
    private TextView alertCount, alertMessage, alertTitle;

    public Dashboard() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_nav_home, container, false);

        // Bind UI elements
        recyclerView = view.findViewById(R.id.transactions_recycler_view);
        emptyAnimation = view.findViewById(R.id.empty_transactions_animation);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new TransactionAdapter(requireContext(), transactionList);
        recyclerView.setAdapter(adapter);

        totalBalanceText = view.findViewById(R.id.total_balance_amount);
        incomeText = view.findViewById(R.id.income_amount);
        expenseText = view.findViewById(R.id.expense_amount);
        alertCount = view.findViewById(R.id.alert_count);
        alertMessage = view.findViewById(R.id.alert_message);
        alertTitle = view.findViewById(R.id.alert_title); // make sure this exists in layout

        view.findViewById(R.id.see_all).setOnClickListener(v -> {
            if (getActivity() instanceof navbar) {
                ((navbar) getActivity()).navigateToOverview();
            }
        });

        // Set up ViewModel observers
        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        viewModel.getTransactions().observe(getViewLifecycleOwner(), items -> {
            transactionList.clear();
            if (items != null && !items.isEmpty()) {
                transactionList.addAll(items);
                emptyAnimation.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            } else {
                emptyAnimation.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            }
            adapter.notifyDataSetChanged();
        });

        viewModel.getTotalIncome().observe(getViewLifecycleOwner(), income -> {
            incomeText.setText(String.format(Locale.getDefault(), "$%.2f", income));
        });

        viewModel.getTotalExpense().observe(getViewLifecycleOwner(), expense -> {
            expenseText.setText(String.format(Locale.getDefault(), "$%.2f", expense));
        });

        viewModel.getBalance().observe(getViewLifecycleOwner(), balance -> {
            totalBalanceText.setText(String.format(Locale.getDefault(), balance < 0 ? "-$%.2f" : "$%.2f", Math.abs(balance)));
            totalBalanceText.setTextColor(
                    balance < 0
                            ? ContextCompat.getColor(requireContext(), R.color.negative_balance)
                            : Color.WHITE
            );
        });

        viewModel.loadTransactions();
        listenToRealtimeAlerts(); // alerts and advice update in real-time

        return view;
    }

    /**
     * Sets up a real-time Firestore listener on the user's "insights" collection,
     * filtered by type ("Alert" or "Advice"). Updates the dashboard UI with the
     * latest insight and count whenever data changes.
     */
    private void listenToRealtimeAlerts() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("insights")
                .whereIn("type", List.of("Alert", "Advice"))
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, e) -> {
                    if (!isAdded() || e != null || snapshot == null) {
                        updateAlertUI(0, "No alerts found.", "Alert");
                        return;
                    }

                    // Get total alert and advice counts separately
                    int alertCountTotal = 0;
                    int adviceCountTotal = 0;

                    for (var doc : snapshot.getDocuments()) {
                        String type = doc.getString("type");
                        if ("Alert".equalsIgnoreCase(type)) alertCountTotal++;
                        else if ("Advice".equalsIgnoreCase(type)) adviceCountTotal++;
                    }

                    String latestMsg = "No alerts found.";
                    String type = "Alert";
                    int finalCount = 0;

                    if (!snapshot.isEmpty()) {
                        String title = snapshot.getDocuments().get(0).getString("title");
                        String message = snapshot.getDocuments().get(0).getString("message");
                        type = snapshot.getDocuments().get(0).getString("type");

                        latestMsg = title + ": " + message;
                        finalCount = "Advice".equalsIgnoreCase(type) ? adviceCountTotal : alertCountTotal;
                    }

                    updateAlertUI(finalCount, latestMsg, type);
                });
    }

    /**
     * Updates the alert badge and message area based on insight type (Alert or Advice).
     *
     * @param count      total number of insights
     * @param message    latest alert/advice message
     * @param type       insight type: "Alert" or "Advice"
     */
    private void updateAlertUI(int count, String message, String type) {
        if (!isAdded()) return;

        alertCount.setText(String.valueOf(count));
        alertMessage.setText(!message.isEmpty() ? message : "No alerts found.");

        if ("Advice".equalsIgnoreCase(type)) {
            alertTitle.setText("ADVICE");
            alertTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.advice_green));
            alertCount.setBackgroundResource(R.drawable.dot_green);
        } else {
            alertTitle.setText("ALERTS");
            alertTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.alert_red));
            alertCount.setBackgroundResource(R.drawable.dot_red);
        }
    }

}
