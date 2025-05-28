package com.cqu.genaiexpensetracker.notifications;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cqu.genaiexpensetracker.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Notifications Fragment
 *
 * Displays a list of the latest financial insights triggered via Firebase (both Alert and Advice types).
 * These are shown in descending order of time and limited to the latest 10 insights.
 */
public class Notifications extends Fragment {

    /** RecyclerView to display list of notifications */
    private RecyclerView recyclerView;

    /** Adapter used for binding notification data */
    private NotificationAdapter adapter;

    /** List holding the fetched notifications */
    private final List<NotificationItem> notificationList = new ArrayList<>();

    /** Required empty public constructor */
    public Notifications() {}

    /**
     * Called to inflate and initialize the notifications fragment UI.
     *
     * @param inflater LayoutInflater used to inflate the layout
     * @param container ViewGroup container for the fragment
     * @param savedInstanceState Previously saved state
     * @return The inflated view for this fragment
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notifications, container, false);

        recyclerView = view.findViewById(R.id.notification_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new NotificationAdapter(notificationList);
        recyclerView.setAdapter(adapter);

        loadLatestInsights();
        return view;
    }

    /**
     * Loads the most recent 10 insights (Alert and Advice) from Firestore.
     * These are ordered by timestamp in descending order.
     */
    private void loadLatestInsights() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users").document(uid).collection("insights")
                .whereIn("type", Arrays.asList("Alert", "Advice"))
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<NotificationItem> tempList = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot) {
                        tempList.add(toNotificationItem(doc));
                    }
                    notificationList.clear();
                    notificationList.addAll(tempList);
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> Log.e("Notifications", "Failed to load insights", e));
    }

    /**
     * Converts a Firestore document snapshot to a NotificationItem model.
     *
     * @param doc Firestore document snapshot
     * @return A NotificationItem representing an alert or advice
     */
    private NotificationItem toNotificationItem(DocumentSnapshot doc) {
        String id = doc.getId();
        String title = doc.getString("title");
        String message = doc.getString("message");
        String category = doc.getString("category");
        String reason = doc.getString("reason");
        String type = doc.getString("type") != null ? doc.getString("type") : "Alert";
        boolean isRead = doc.getBoolean("read") != null && doc.getBoolean("read");

        Date timestamp = doc.getTimestamp("timestamp") != null
                ? doc.getTimestamp("timestamp").toDate()
                : new Date();

        return new NotificationItem(id, title, message, category, reason, timestamp, isRead, type);
    }
}
