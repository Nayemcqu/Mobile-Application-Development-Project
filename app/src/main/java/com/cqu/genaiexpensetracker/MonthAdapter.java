package com.cqu.genaiexpensetracker;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * MonthAdapter
 * - Adapter for displaying a horizontal list of months.
 * - Highlights the selected month using selector background.
 * - Notifies listener on selection changes.
 */
public class MonthAdapter extends RecyclerView.Adapter<MonthAdapter.MonthViewHolder> {

    private final List<String> months;
    private final Context context;
    private final OnMonthSelectedListener listener;
    private int selectedPosition;

    /**
     * Constructor for MonthAdapter.
     *
     * @param context  Parent context for inflating views.
     * @param months   List of month strings (e.g. "Jan", "Feb", etc.)
     * @param listener Callback for month selection.
     */
    public MonthAdapter(Context context, List<String> months, OnMonthSelectedListener listener) {
        this.context = context;
        this.months = months;
        this.listener = listener;
        this.selectedPosition = months.size() - 1; // Default to latest month
    }

    @NonNull
    @Override
    public MonthViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_month_chip, parent, false);
        return new MonthViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MonthViewHolder holder, int position) {
        String month = months.get(position);
        boolean isSelected = position == selectedPosition;

        holder.monthText.setText(month);
        holder.monthText.setSelected(isSelected);

        // Set appropriate text color based on selection
        holder.monthText.setTextColor(isSelected
                ? Color.WHITE
                : ContextCompat.getColor(context, R.color.black));

        // Handle month chip click
        holder.itemView.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos == RecyclerView.NO_POSITION || currentPos == selectedPosition) return;

            int previousPos = selectedPosition;
            selectedPosition = currentPos;

            notifyItemChanged(previousPos);      // Un-highlight previous
            notifyItemChanged(selectedPosition); // Highlight current

            listener.onMonthSelected(months.get(currentPos), currentPos);
        });
    }

    @Override
    public int getItemCount() {
        return months.size();
    }

    /**
     * Updates the selected position programmatically.
     *
     * @param position Index of month to be selected
     */
    public void setSelectedPosition(int position) {
        selectedPosition = position;
        notifyDataSetChanged(); // Redraw all for selector application
    }

    /**
     * Callback interface for month selection changes.
     */
    public interface OnMonthSelectedListener {
        void onMonthSelected(String month, int position);
    }

    /**
     * ViewHolder for month item view.
     */
    static class MonthViewHolder extends RecyclerView.ViewHolder {
        TextView monthText;

        public MonthViewHolder(@NonNull View itemView) {
            super(itemView);
            monthText = itemView.findViewById(R.id.month_text);
        }
    }
}
