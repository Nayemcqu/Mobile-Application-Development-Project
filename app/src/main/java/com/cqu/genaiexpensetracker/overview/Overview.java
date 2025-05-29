package com.cqu.genaiexpensetracker.overview;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.cqu.genaiexpensetracker.R;
import com.cqu.genaiexpensetracker.month.BarChartView;
import com.cqu.genaiexpensetracker.expense.expenseViewModel;
import com.cqu.genaiexpensetracker.income.IncomeViewModel;
import com.cqu.genaiexpensetracker.month.MonthAdapter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Overview Fragment
 * - Displays a bar chart for Income, Expense, and Saving.
 * - Includes a horizontal month selector.
 * - Shows Firestore data via IncomeViewModel & ExpenseViewModel.
 */
public class Overview extends Fragment {

    private final List<String> monthList = new ArrayList<>();
    private RecyclerView monthRecycler;
    private BarChartView customBarChart;
    private TextView selectedMonthText;
    private LinearLayout summaryList;
    private MonthAdapter adapter;
    private String lastSelectedMonth = "";
    private Double latestIncome = null;
    private Double latestExpense = null;
    private boolean isInitialScrollDone = false;
    private IncomeViewModel incomeViewModel;
    private com.cqu.genaiexpensetracker.expense.expenseViewModel expenseViewModel;

    public Overview() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_nav_overview, container, false);

        customBarChart = view.findViewById(R.id.customBarChart);
        selectedMonthText = view.findViewById(R.id.selected_month_text);
        monthRecycler = view.findViewById(R.id.month_recycler);
        summaryList = view.findViewById(R.id.summary_list);

        incomeViewModel = new ViewModelProvider(this).get(IncomeViewModel.class);
        expenseViewModel = new ViewModelProvider(this).get(expenseViewModel.class);

        setupMonthRecycler();

        return view;
    }

    /**
     * Sets up the horizontal month RecyclerView.
     * Shows latest month selected with 4-month padding initially.
     */
    private void setupMonthRecycler() {
        generateLast12Months();

        int estimatedChipWidth = dpToPx(72);
        int sidePadding = (requireContext().getResources().getDisplayMetrics().widthPixels - estimatedChipWidth * 4) / 2;
        monthRecycler.setPadding(sidePadding, 0, sidePadding, 0);
        monthRecycler.setClipToPadding(false);

        adapter = new MonthAdapter(requireContext(), monthList, (month, position) -> {
            selectedMonthText.setText(month);
            adapter.setSelectedPosition(position);
            scrollToCenterMonth(position);

            if (isInitialScrollDone) {
                monthRecycler.setPadding(0, 0, 0, 0);
            } else {
                isInitialScrollDone = true;
            }

            fetchAndDisplayDataForMonth(month);
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false);
        monthRecycler.setLayoutManager(layoutManager);
        monthRecycler.setAdapter(adapter);

        PagerSnapHelper snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(monthRecycler);

        monthRecycler.post(() -> {
            int latestIndex = monthList.size() - 1;
            adapter.setSelectedPosition(latestIndex);
            selectedMonthText.setText(monthList.get(latestIndex));
            adapter.notifyItemChanged(latestIndex);
            scrollToCenterMonth(latestIndex);
            monthRecycler.setPadding(0, 0, 0, 0);

            fetchAndDisplayDataForMonth(monthList.get(latestIndex));
        });
    }

    /**
     * Scrolls the RecyclerView to center the selected month.
     *
     * @param position Selected index
     */
    private void scrollToCenterMonth(int position) {
        monthRecycler.post(() -> {
            int estimatedChipWidth = dpToPx(72);
            int offset = (monthRecycler.getWidth() / 2) - (estimatedChipWidth / 2);
            ((LinearLayoutManager) monthRecycler.getLayoutManager())
                    .scrollToPositionWithOffset(position, offset);
        });
    }

    /**
     * Fetches income and expense from Firestore for a selected month.
     * Updates both the bar chart and summary UI.
     *
     * @param selectedMonth e.g. "May"
     */
    private void fetchAndDisplayDataForMonth(String selectedMonth) {
        latestIncome = null;
        latestExpense = null;

        incomeViewModel.getTotalIncomeByMonth(selectedMonth).observe(getViewLifecycleOwner(), incomeTotal -> {
            latestIncome = incomeTotal;
            updateChartIfReady(selectedMonth);
        });

        expenseViewModel.getTotalExpenseByMonth(selectedMonth).observe(getViewLifecycleOwner(), expenseTotal -> {
            latestExpense = expenseTotal;
            updateChartIfReady(selectedMonth);
        });
    }

    private void updateChartIfReady(String selectedMonth) {
        if (latestIncome == null || latestExpense == null) return;

        double saving = latestIncome - latestExpense;

        int[] chartValues = {
                (int) latestIncome.doubleValue(),
                (int) latestExpense.doubleValue(),
                (int) Math.max(saving, 0) // clamp to 0 if saving is negative
        };

        // Update chart smoothly (only bar value transitions)
        customBarChart.setChartValues(chartValues); // Uses value diff animation

        // Update last selected to prevent re-animating on same month
        lastSelectedMonth = selectedMonth;

        // Update summary rows
        populateSummaryRows(selectedMonth, latestIncome, latestExpense, saving);
    }


    /**
     * Dynamically populates summary rows for income, expense, and saving.
     *
     * @param month   Selected month
     * @param income  Total income value
     * @param expense Total expense value
     * @param saving  Income - Expense
     */
    private void populateSummaryRows(String month, double income, double expense, double saving) {
        summaryList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        addSummaryRow(inflater, "Income", income, R.drawable.dot_green);
        addSummaryRow(inflater, "Expense", expense, R.drawable.dot_red);
        addSummaryRow(inflater, "Saving", saving, R.drawable.dot_yellow);
    }

    /**
     * Reusable helper to inflate and bind a single row.
     */
    private void addSummaryRow(LayoutInflater inflater, String label, double amount, int dotDrawable) {
        View row = inflater.inflate(R.layout.income_expense_saving_item_summary, summaryList, false);

        TextView labelText = row.findViewById(R.id.label_text);
        TextView valueText = row.findViewById(R.id.value_text);
        View dot = row.findViewById(R.id.color_dot);
        dot.setBackgroundResource(dotDrawable);

        labelText.setText(label);

        // Handle negative formatting for expense
        String prefix = label.equals("Expense") ? "-" : "";
        double displayAmount = label.equals("Saving") ? Math.max(0, amount) : amount;
        valueText.setText(String.format(Locale.getDefault(), "%s$%.2f", prefix, displayAmount));

        summaryList.addView(row);

        // Divider
        View divider = new View(requireContext());
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        divider.setPadding(35, 0, 0, 0);
        divider.setBackgroundColor(Color.parseColor("#4DA6A6A6"));
        summaryList.addView(divider);
    }

    /**
     * Converts dp to px for consistent spacing
     */
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    /**
     * Generates the last 12 months ending in current month.
     */
    private void generateLast12Months() {
        monthList.clear();
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat formatter = new SimpleDateFormat("MMM", Locale.getDefault());

        for (int i = 11; i >= 0; i--) {
            calendar.add(Calendar.MONTH, -i);
            monthList.add(formatter.format(calendar.getTime()));
            calendar = Calendar.getInstance();
        }
    }
}
