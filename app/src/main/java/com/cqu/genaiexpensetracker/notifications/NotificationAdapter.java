package com.cqu.genaiexpensetracker.notifications;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.cqu.genaiexpensetracker.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.List;

/**
 * RecyclerView Adapter for displaying financial insights in the Notifications screen.
 *
 * Features:
 * - Supports both "Alert" and "Advice" type insights
 * - Category-based icon rendering (Food, Rent, Transport, etc.)
 * - Highlights unread items with bold font and background
 * - Shows relative timestamp using TimeAgoUtil
 * - Displays optional reason if available
 * - Marks insights as read in Firestore on click
 */
public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    /** List of insight items fetched from Firestore */
    private final List<NotificationItem> itemList;

    /**
     * Constructor
     * @param itemList List of NotificationItem to display
     */
    public NotificationAdapter(List<NotificationItem> itemList) {
        this.itemList = itemList;
    }

    /**
     * ViewHolder pattern class for mapping single_notification_item.xml
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView typeText, titleText, messageText, reasonText, timeText;
        ImageView categoryIcon;
        View alertContainer, rootView;

        public ViewHolder(View view) {
            super(view);
            rootView = view;
            typeText = view.findViewById(R.id.notification_type);
            titleText = view.findViewById(R.id.notification_title);
            messageText = view.findViewById(R.id.notification_message);
            reasonText = view.findViewById(R.id.notification_reason);
            timeText = view.findViewById(R.id.notification_time);
            categoryIcon = view.findViewById(R.id.notification_icon);
            alertContainer = view.findViewById(R.id.alert_container);
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
        String category = item.getCategory() != null ? item.getCategory().toLowerCase() : "";
        String type = item.getType() != null ? item.getType() : "Alert";

        // Unread item highlighting
        if (!item.isRead()) {
            holder.rootView.setBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.grey_light));
            holder.messageText.setTypeface(null, Typeface.BOLD);
        } else {
            holder.rootView.setBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.white));
            holder.messageText.setTypeface(null, Typeface.NORMAL);
        }

        // Type styling (Alert vs Advice)
        holder.typeText.setText(type.toUpperCase());
        if ("Advice".equalsIgnoreCase(type)) {
            holder.typeText.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.advice_green));
            holder.alertContainer.setVisibility(View.GONE); // optional styling choice
        } else {
            holder.typeText.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.holo_red_dark));
            holder.alertContainer.setVisibility(View.VISIBLE);
        }

        // Title and message
        holder.titleText.setText(item.getTitle());
        holder.messageText.setText(item.getMessage());

        // set the reason
        if (item.getReason() != null && !item.getReason().isEmpty()) {
            holder.reasonText.setText(item.getReason());
            holder.reasonText.setVisibility(View.VISIBLE);
        } else {
            holder.reasonText.setVisibility(View.GONE);
        }

        // Set category-based icon
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

        // Show relative timestamp
        Date timestamp = item.getTimestamp();
        holder.timeText.setText(TimeAgoUtil.getTimeAgo(timestamp.getTime()));

        // Mark as read in Firestore on click
        holder.rootView.setOnClickListener(v -> {
            if (!item.isRead()) {
                item.setRead(true);
                notifyItemChanged(position);

                String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                String docId = item.getId();
                FirebaseFirestore.getInstance()
                        .collection("users").document(uid)
                        .collection("insights").document(docId)
                        .update("read", true);
            }
        });
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }
}
