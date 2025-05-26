package com.cqu.genaiexpensetracker.transactions;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cqu.genaiexpensetracker.R;
import com.cqu.genaiexpensetracker.expense.expenseModel;
import com.cqu.genaiexpensetracker.income.incomeModel;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView Adapter to display a unified list of income and expense transactions.
 * Handles binding of both expenseModel and incomeModel types in a single layout.
 */
public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    private final Context context;
    private final List<TransactionItem> transactionList;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    /**
     * Constructor to initialize adapter with data.
     *
     * @param context         Application context
     * @param transactionList List of combined income and expense transactions
     */
    public TransactionAdapter(Context context, List<TransactionItem> transactionList) {
        this.context = context;
        this.transactionList = transactionList;
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.single_income_item, parent, false);
        return new TransactionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        TransactionItem item = transactionList.get(position);

        if (item.isExpense()) {
            // Show expense data
            expenseModel expense = item.expense;
            String category = expense.getCategory().toLowerCase(Locale.ROOT);

            // Choose icon based on expense category
            switch (category) {
                case "food":
                    holder.icon.setImageResource(R.drawable.ic_exp_food_icon);
                    break;
                case "rent":
                    holder.icon.setImageResource(R.drawable.ic_exp_rent_icon);
                    break;
                case "grocery":
                    holder.icon.setImageResource(R.drawable.ic_exp_grocery_icon);
                    break;
                case "transport":
                    holder.icon.setImageResource(R.drawable.ic_exp_transport_icon);
                    break;
                default:
                    holder.icon.setImageResource(R.drawable.expense_income_default_icon);
                    break;
            }

            holder.title.setText(expense.getCategory());
            holder.amount.setText(String.format(Locale.getDefault(), "-$%.2f", expense.getAmount()));
            holder.amount.setTextColor(context.getResources().getColor(R.color.expense_red));
            holder.time.setText(expense.getTime());

        } else {
            // Show income data
            incomeModel income = item.income;
            holder.icon.setImageResource(R.drawable.expense_income_default_icon); // Default for all incomes
            holder.title.setText(income.getSource());
            holder.amount.setText(String.format(Locale.getDefault(), "+$%.2f", income.getAmount()));
            holder.amount.setTextColor(context.getResources().getColor(R.color.income_green));
            holder.time.setText(timeFormat.format(income.getTimestamp().toDate()));
        }
    }

    @Override
    public int getItemCount() {
        return transactionList.size();
    }

    public void updateData(List<TransactionItem> newList) {
        transactionList.clear();
        transactionList.addAll(newList);
        notifyDataSetChanged();
    }

    /**
     * ViewHolder for transaction items (shared layout for both income and expense).
     */
    public static class TransactionViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView title, time, amount;

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.category_icon);
            title = itemView.findViewById(R.id.text_income_category);
            time = itemView.findViewById(R.id.income_item_time);
            amount = itemView.findViewById(R.id.text_income_amount);
        }
    }

}
