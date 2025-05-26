package com.cqu.genaiexpensetracker.notifications;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.cqu.genaiexpensetracker.R;

import java.util.Date;
import java.util.List;

/**
 * Adapter for displaying a list of AI-generated Alerts and Advice
 * in the notifications screen using single_notification_item layout.
 */
public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private final List<NotificationItem> itemList;

    public NotificationAdapter(List<NotificationItem> itemList) {
        this.itemList = itemList;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView typeText, titleText, messageText, timeText;
        ImageView categoryIcon;
        View alertContainer;

        public ViewHolder(View view) {
            super(view);
            typeText = view.findViewById(R.id.notification_type);
            titleText = view.findViewById(R.id.notification_title);
            messageText = view.findViewById(R.id.notification_message);
            timeText = view.findViewById(R.id.notification_time);
            categoryIcon = view.findViewById(R.id.notification_icon);
            alertContainer = view.findViewById(R.id.alert_container); // new wrapper layout
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.single_notification_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        NotificationItem item = itemList.get(position);

        String type = item.getType();
        String category = item.getCategory() != null ? item.getCategory().toLowerCase() : "";

        if ("advice".equalsIgnoreCase(type)) {
            holder.typeText.setText("Advice");
            holder.typeText.setTextColor(holder.itemView.getResources().getColor(android.R.color.holo_green_dark));

            // Hide Alert-specific UI
            holder.alertContainer.setVisibility(View.GONE);
            holder.messageText.setText(item.getMessage());

        } else { // ALERT
            holder.typeText.setText("Alert");
            holder.typeText.setTextColor(holder.itemView.getResources().getColor(android.R.color.holo_red_dark));

            // Show Alert-specific fields
            holder.alertContainer.setVisibility(View.VISIBLE);
            holder.titleText.setText(item.getTitle());
            holder.messageText.setText(item.getMessage());

            int iconRes = R.drawable.ic_category_general;
            switch (category) {
                case "food":
                    iconRes = R.drawable.ic_exp_food_icon;
                    break;
                case "grocery":
                    iconRes = R.drawable.ic_exp_grocery_icon;
                    break;
                case "rent":
                    iconRes = R.drawable.ic_exp_rent_icon;
                    break;
                case "transport":
                    iconRes = R.drawable.ic_exp_transport_icon;
                    break;
            }
            holder.categoryIcon.setImageResource(iconRes);
        }

        Date timestamp = item.getTimestamp();
        holder.timeText.setText(TimeAgoUtil.getTimeAgo(timestamp.getTime()));
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }
}
