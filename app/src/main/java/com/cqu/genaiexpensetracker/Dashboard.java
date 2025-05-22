package com.cqu.genaiexpensetracker;

import android.graphics.Color;
import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dashboard fragment that displays:
 * - Total balance, income, and expense summary
 * - A RecyclerView with recent transactions
 * - Navigation to overview screen via "See All"
 */
public class Dashboard extends Fragment {

    private final List<TransactionItem> transactionList = new ArrayList<>();
    private RecyclerView recyclerView;
    private LottieAnimationView emptyAnimation;
    private TransactionAdapter adapter;
    private DashboardViewModel viewModel;

    // Summary text views
    private TextView totalBalanceText, incomeText, expenseText;

    public Dashboard() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_nav_home, container, false);

        // Bind RecyclerView and empty state
        recyclerView = view.findViewById(R.id.transactions_recycler_view);
        emptyAnimation = view.findViewById(R.id.empty_transactions_animation);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new TransactionAdapter(requireContext(), transactionList);
        recyclerView.setAdapter(adapter);

        // Bind TextViews for summary values
        totalBalanceText = view.findViewById(R.id.total_balance_amount);
        incomeText = view.findViewById(R.id.income_amount);
        expenseText = view.findViewById(R.id.expense_amount);

        // SEE ALL button click to navigate to Overview
        TextView seeAll = view.findViewById(R.id.see_all);
        seeAll.setOnClickListener(v -> {
            if (getActivity() instanceof navbar) {
                ((navbar) getActivity()).navigateToOverview();
            }
        });

        // Observe ViewModel
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

        // Observe summary values
        viewModel.getTotalIncome().observe(getViewLifecycleOwner(), income -> {
            incomeText.setText(String.format(Locale.getDefault(), "$%.2f", income));
        });

        viewModel.getTotalExpense().observe(getViewLifecycleOwner(), expense -> {
            expenseText.setText(String.format(Locale.getDefault(), "$%.2f", expense));
        });

        viewModel.getBalance().observe(getViewLifecycleOwner(), balance -> {
            if (balance < 0) {
                totalBalanceText.setText(String.format(Locale.getDefault(), "-$%.2f", Math.abs(balance)));
                totalBalanceText.setTextColor(ContextCompat.getColor(requireContext(), R.color.negative_balance));
            } else {
                totalBalanceText.setText(String.format(Locale.getDefault(), "$%.2f", balance));
                totalBalanceText.setTextColor(Color.WHITE); // default color set
            }
        });

        viewModel.loadTransactions();

        return view;
    }
}
