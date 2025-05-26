package com.cqu.genaiexpensetracker.notifications;

import android.os.Bundle;
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
import java.util.Date;
import java.util.List;

/**
 * Notifications fragment that displays AI-generated Alerts and Advice
 * from Firestore under /users/{uid}/insights/. Supports:
 * - Alert: Title, message, category icon
 * - Advice: Message only
 * - Relative timestamps
 */
public class Notifications extends Fragment {

    private RecyclerView recyclerView;
    private NotificationAdapter adapter;
    private final List<NotificationItem> notificationList = new ArrayList<>();

    public Notifications() {
        // Required empty public constructor
    }

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

        loadNotifications();

        return view;
    }

    /**
     * Loads all AI insights (alerts and advice) from Firestore
     * and populates the notifications list.
     */
    private void loadNotifications() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("insights")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    notificationList.clear();

                    for (DocumentSnapshot doc : querySnapshot) {
                        String type = doc.getString("type");
                        String message = doc.getString("message");
                        String title = doc.getString("title");
                        String category = doc.getString("category");
                        Date timestamp = doc.getTimestamp("timestamp") != null
                                ? doc.getTimestamp("timestamp").toDate()
                                : new Date(); // fallback

                        notificationList.add(new NotificationItem(type, title, message, category, timestamp));
                    }

                    adapter.notifyDataSetChanged();
                });
    }
}
