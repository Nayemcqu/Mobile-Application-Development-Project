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
import com.cqu.genaiexpensetracker.ai_insights.AiInsightService.AiCallback;
import com.cqu.genaiexpensetracker.ai_insights.InsightAnalyzer;
import com.cqu.genaiexpensetracker.navbar.navbar;
import com.cqu.genaiexpensetracker.transactions.TransactionAdapter;
import com.cqu.genaiexpensetracker.transactions.TransactionItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Dashboard extends Fragment {

    private final List<TransactionItem> transactionList = new ArrayList<>();
    private RecyclerView recyclerView;
    private LottieAnimationView emptyAnimation;
    private TransactionAdapter adapter;
    private DashboardViewModel viewModel;

    private TextView totalBalanceText, incomeText, expenseText;
    private TextView alertCount, alertMessage, adviceCount, adviceMessage;

    private boolean incomeLoaded = false;
    private boolean expenseLoaded = false;
    private boolean transactionsLoaded = false;

    public Dashboard() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_nav_home, container, false);

        // Bind UI
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
        adviceCount = view.findViewById(R.id.advice_count);
        adviceMessage = view.findViewById(R.id.advice_message);

        TextView seeAll = view.findViewById(R.id.see_all);
        seeAll.setOnClickListener(v -> {
            if (getActivity() instanceof navbar) {
                ((navbar) getActivity()).navigateToOverview();
            }
        });

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
            transactionsLoaded = true;
            triggerInsightAnalysisIfReady();
        });

        viewModel.getTotalIncome().observe(getViewLifecycleOwner(), income -> {
            incomeText.setText(String.format(Locale.getDefault(), "$%.2f", income));
            incomeLoaded = true;
            triggerInsightAnalysisIfReady();
        });

        viewModel.getTotalExpense().observe(getViewLifecycleOwner(), expense -> {
            expenseText.setText(String.format(Locale.getDefault(), "$%.2f", expense));
            expenseLoaded = true;
            triggerInsightAnalysisIfReady();
        });

        viewModel.getBalance().observe(getViewLifecycleOwner(), balance -> {
            if (balance < 0) {
                totalBalanceText.setText(String.format(Locale.getDefault(), "-$%.2f", Math.abs(balance)));
                totalBalanceText.setTextColor(ContextCompat.getColor(requireContext(), R.color.negative_balance));
            } else {
                totalBalanceText.setText(String.format(Locale.getDefault(), "$%.2f", balance));
                totalBalanceText.setTextColor(Color.WHITE);
            }
        });

        viewModel.loadTransactions();
        return view;
    }

    /**
     * Wait until all income/expense/transactions loaded before running Gemini + Firestore.
     */
    private void triggerInsightAnalysisIfReady() {
        if (incomeLoaded && expenseLoaded && transactionsLoaded) {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                Log.d("Dashboard", "Triggering insight for UID: " +
                        FirebaseAuth.getInstance().getCurrentUser().getUid());

                // Automatically reload insights after Gemini finishes
                new InsightAnalyzer().analyzeAndGenerate(this::loadAiInsights);

            } else {
                Log.e("Dashboard", "User not signed in. Skipping insight generation.");
            }
        }
    }

    /**
     * Loads latest alert & advice and their total counts.
     */
    private void loadAiInsights() {
        String uid = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        String latestUrl = "https://api-7carwvirja-uc.a.run.app/latest-insights/" + uid;

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(latestUrl).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("Dashboard", "Failed to fetch AI insights", e);
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> updateAiUi(0, "", 0, ""));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e("Dashboard", "Non-200 response from insights API: " + response.code());
                    requireActivity().runOnUiThread(() -> updateAiUi(0, "", 0, ""));
                    return;
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                try {
                    JSONObject json = new JSONObject(responseBody);
                    JSONObject alert = json.optJSONObject("alert");
                    JSONObject advice = json.optJSONObject("advice");

                    String alertTitle = (alert != null) ? alert.optString("title", "") : "";
                    String alertMsg = (alert != null) ? alert.optString("message", "") : "";

                    String adviceTitle = (advice != null) ? advice.optString("title", "") : "";
                    String adviceMsg = (advice != null) ? advice.optString("message", "") : "";

                    String finalAlertMsg = (!alertTitle.isEmpty() ? alertTitle + ": " : "") + alertMsg;
                    String finalAdviceMsg = (!adviceTitle.isEmpty() ? adviceTitle + ": " : "") + adviceMsg;

                    FirebaseFirestore db = FirebaseFirestore.getInstance();
                    db.collection("users").document(uid).collection("insights")
                            .get().addOnSuccessListener(snapshot -> {
                                AtomicInteger totalAlerts = new AtomicInteger(0);
                                AtomicInteger totalAdvice = new AtomicInteger(0);
                                for (DocumentSnapshot doc : snapshot) {
                                    String type = doc.getString("type");
                                    if ("Alert".equalsIgnoreCase(type)) totalAlerts.incrementAndGet();
                                    else if ("Advice".equalsIgnoreCase(type)) totalAdvice.incrementAndGet();
                                }

                                requireActivity().runOnUiThread(() ->
                                        updateAiUi(totalAlerts.get(), finalAlertMsg, totalAdvice.get(), finalAdviceMsg));
                            })
                            .addOnFailureListener(e -> {
                                Log.e("Dashboard", "Failed to count insights", e);
                                requireActivity().runOnUiThread(() -> updateAiUi(0, "", 0, ""));
                            });

                } catch (Exception e) {
                    Log.e("Dashboard", "JSON parse error", e);
                    requireActivity().runOnUiThread(() -> updateAiUi(0, "", 0, ""));
                }
            }
        });
    }

    /**
     * Updates UI badges and messages for insights.
     */
    private void updateAiUi(int alertNum, String alertMsg, int adviceNum, String adviceMsg) {
        alertCount.setText(String.valueOf(alertNum));
        alertMessage.setText(!alertMsg.isEmpty() ? alertMsg : "No alerts found.");
        adviceCount.setText(String.valueOf(adviceNum));
        adviceMessage.setText(!adviceMsg.isEmpty() ? adviceMsg : "No new advice available.");
    }
}
