package com.cqu.genaiexpensetracker.income;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cqu.genaiexpensetracker.R;
import com.cqu.genaiexpensetracker.transactions.TransactionAdapter;
import com.cqu.genaiexpensetracker.navbar.navbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Locale;

/**
 * Income Fragment showing current and past income filtered by weekly, monthly, or 6 months.
 * FAB shows on scroll up and hides on scroll down; data updates live via Firebase.
 */
public class Income extends Fragment {

    private static final String WEEKLY = "weekly";
    private static final String MONTHLY = "monthly";
    private static final String SIX_MONTHS = "six_months";

    private TextView incomeAmountText, currentLabel, currentRange, pastLabel, pastRange;
    private RecyclerView currentRecyclerView, pastRecyclerView;
    private Button chipWeekly, chipMonthly, chipSixMonths;
    private FloatingActionButton fabAdd;
    private IncomeViewModel viewModel;
    private NestedScrollView scrollView;

    private String currentFilter = WEEKLY;

    public Income() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_nav_income, container, false);

        // Bind UI
        incomeAmountText = view.findViewById(R.id.text_total_income_amount);
        currentLabel = view.findViewById(R.id.income_list_title);
        currentRange = view.findViewById(R.id.income_list_date_range);
        pastLabel = view.findViewById(R.id.past_week_income_title);
        pastRange = view.findViewById(R.id.past_week_income_date_range);
        chipWeekly = view.findViewById(R.id.btn_income_filter_weekly);
        chipMonthly = view.findViewById(R.id.btn_income_filter_monthly);
        chipSixMonths = view.findViewById(R.id.btn_income_filter_six_months);
        currentRecyclerView = view.findViewById(R.id.recycler_income_list);
        pastRecyclerView = view.findViewById(R.id.recycler_income_past_week);
        fabAdd = view.findViewById(R.id.fab_add_income);
        scrollView = view.findViewById(R.id.scroll_view);

        viewModel = new ViewModelProvider(this).get(IncomeViewModel.class);

        currentRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        pastRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // Observe filtered income data
        viewModel.getThisPeriodIncome().observe(getViewLifecycleOwner(), items -> {
            currentRecyclerView.setAdapter(new TransactionAdapter(requireContext(), items));
        });

        // Observe past period data
        viewModel.getPastPeriodIncome().observe(getViewLifecycleOwner(), items -> {
            boolean isSixMonths = currentFilter.equals(SIX_MONTHS);
            pastLabel.setVisibility(isSixMonths ? View.GONE : View.VISIBLE);
            pastRange.setVisibility(isSixMonths ? View.GONE : View.VISIBLE);
            pastRecyclerView.setVisibility(isSixMonths ? View.GONE : View.VISIBLE);
            if (!isSixMonths) {
                pastRecyclerView.setAdapter(new TransactionAdapter(requireContext(), items));
            }
        });

        // Observe total income (lifetime or 6 months)
        viewModel.getTotalIncome().observe(getViewLifecycleOwner(), total -> {
            incomeAmountText.setText(String.format(Locale.getDefault(), "$%.2f", total));
        });

        // Load total income for summary card
        viewModel.loadTotalIncome();

        // Chip click listeners
        chipWeekly.setOnClickListener(v -> switchFilter(WEEKLY));
        chipMonthly.setOnClickListener(v -> switchFilter(MONTHLY));
        chipSixMonths.setOnClickListener(v -> switchFilter(SIX_MONTHS));

        // Default load
        switchFilter(WEEKLY);

        // FAB visibility behavior
        fabAdd.hide();
        scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    if (scrollY > oldScrollY) {
                        fabAdd.hide(); // Scroll down
                    } else if (scrollY < oldScrollY) {
                        fabAdd.show(); // Scroll up
                    }
                });

        // FAB tap -> navigate to addIncome fragment
        fabAdd.setOnClickListener(v -> {
            if (getActivity() instanceof navbar) {
                ((navbar) getActivity()).navigateToAddIncome();
            }
        });


        return view;
    }

    /**
     * Switches filter and reloads UI/data.
     */
    private void switchFilter(String filter) {
        currentFilter = filter;
        updateChipUI(filter);
        viewModel.loadFilteredIncome(filter);
    }

    /**
     * Updates chip states and labels according to selected period.
     */
    private void updateChipUI(String selected) {
        chipWeekly.setBackgroundResource(selected.equals(WEEKLY) ? R.drawable.chip_selected : R.drawable.chip_unselected);
        chipWeekly.setTextColor(getResources().getColor(selected.equals(WEEKLY) ? android.R.color.white : R.color.text_light));

        chipMonthly.setBackgroundResource(selected.equals(MONTHLY) ? R.drawable.chip_selected : R.drawable.chip_unselected);
        chipMonthly.setTextColor(getResources().getColor(selected.equals(MONTHLY) ? android.R.color.white : R.color.text_light));

        chipSixMonths.setBackgroundResource(selected.equals(SIX_MONTHS) ? R.drawable.chip_selected : R.drawable.chip_unselected);
        chipSixMonths.setTextColor(getResources().getColor(selected.equals(SIX_MONTHS) ? android.R.color.white : R.color.text_light));

        if (selected.equals(WEEKLY)) {
            currentLabel.setText("THIS WEEK");
            pastLabel.setText("PAST WEEK");
            currentRange.setText(viewModel.getThisWeekRange());
            pastRange.setText(viewModel.getLastWeekRange());
        } else if (selected.equals(MONTHLY)) {
            currentLabel.setText("THIS MONTH");
            pastLabel.setText("LAST MONTH");
            currentRange.setText(viewModel.getThisMonthRange());
            pastRange.setText(viewModel.getLastMonthRange());
        } else {
            currentLabel.setText("6 MONTHS");
            currentRange.setText(viewModel.getLastSixMonthRange());
        }
    }
}
