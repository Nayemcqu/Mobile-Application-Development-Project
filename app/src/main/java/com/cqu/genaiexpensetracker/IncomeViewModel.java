package com.cqu.genaiexpensetracker;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ViewModel class for managing income-related data including filters like
 * weekly, monthly, and six-months, as well as lifetime total income.
 */
public class IncomeViewModel extends ViewModel {

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    private final MutableLiveData<List<TransactionItem>> thisPeriodIncome = new MutableLiveData<>();
    private final MutableLiveData<List<TransactionItem>> pastPeriodIncome = new MutableLiveData<>();
    private final MutableLiveData<Double> totalIncome = new MutableLiveData<>();

    /**
     * Returns the income list for the currently selected period (week/month/6 months).
     */
    public LiveData<List<TransactionItem>> getThisPeriodIncome() {
        return thisPeriodIncome;
    }

    /**
     * Returns the income list for the previous period (last week/last month).
     */
    public LiveData<List<TransactionItem>> getPastPeriodIncome() {
        return pastPeriodIncome;
    }

    /**
     * Returns the total income value aggregated from all records.
     */
    public LiveData<Double> getTotalIncome() {
        return totalIncome;
    }

    /**
     * Loads total income from all available income records in Firestore.
     */
    public void loadTotalIncome() {
        String uid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        if (uid == null) return;

        firestore.collection("users")
                .document(uid)
                .collection("income")
                .get()
                .addOnSuccessListener(snapshot -> {
                    double sum = 0;
                    for (QueryDocumentSnapshot doc : snapshot) {
                        incomeModel model = doc.toObject(incomeModel.class);
                        sum += model.getAmount();
                    }
                    totalIncome.setValue(sum);
                });
    }

    /**
     * Loads filtered income data for current and past periods based on selected chip.
     *
     * @param filter String value: "weekly", "monthly", or "six_months"
     */
    public void loadFilteredIncome(String filter) {
        String uid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        if (uid == null) return;

        Calendar now = Calendar.getInstance();
        Date startThis, endThis;
        Date startPast = null, endPast = null;

        if (filter.equals("weekly")) {
            Calendar thisWeekStart = (Calendar) now.clone();
            thisWeekStart.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            startThis = thisWeekStart.getTime();

            Calendar thisWeekEnd = (Calendar) thisWeekStart.clone();
            thisWeekEnd.add(Calendar.DAY_OF_YEAR, 6);
            endThis = thisWeekEnd.getTime();

            Calendar lastWeekStart = (Calendar) thisWeekStart.clone();
            lastWeekStart.add(Calendar.DAY_OF_YEAR, -7);
            startPast = lastWeekStart.getTime();

            Calendar lastWeekEnd = (Calendar) thisWeekEnd.clone();
            lastWeekEnd.add(Calendar.DAY_OF_YEAR, -7);
            endPast = lastWeekEnd.getTime();

        } else if (filter.equals("monthly")) {
            Calendar thisMonthStart = (Calendar) now.clone();
            thisMonthStart.set(Calendar.DAY_OF_MONTH, 1);
            startThis = thisMonthStart.getTime();
            endThis = now.getTime();

            Calendar lastMonthStart = (Calendar) thisMonthStart.clone();
            lastMonthStart.add(Calendar.MONTH, -1);
            startPast = lastMonthStart.getTime();

            Calendar lastMonthEnd = (Calendar) lastMonthStart.clone();
            lastMonthEnd.set(Calendar.DAY_OF_MONTH, lastMonthEnd.getActualMaximum(Calendar.DAY_OF_MONTH));
            endPast = lastMonthEnd.getTime();

        } else {
            startThis = getDateMonthsAgo(6);
            endThis = new Date();
        }

        firestore.collection("users")
                .document(uid)
                .collection("income")
                .whereGreaterThanOrEqualTo("timestamp", new Timestamp(startThis))
                .whereLessThanOrEqualTo("timestamp", new Timestamp(endThis))
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<TransactionItem> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        incomeModel model = doc.toObject(incomeModel.class);
                        list.add(new TransactionItem(null, model));
                    }
                    thisPeriodIncome.setValue(list);
                });

        if (!filter.equals("six_months")) {
            firestore.collection("users")
                    .document(uid)
                    .collection("income")
                    .whereGreaterThanOrEqualTo("timestamp", new Timestamp(startPast))
                    .whereLessThanOrEqualTo("timestamp", new Timestamp(endPast))
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        List<TransactionItem> list = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : snapshot) {
                            incomeModel model = doc.toObject(incomeModel.class);
                            list.add(new TransactionItem(null, model));
                        }
                        pastPeriodIncome.setValue(list);
                    });
        } else {
            pastPeriodIncome.setValue(new ArrayList<>());
        }
    }

    /**
     * Utility to get a date object X months ago from today.
     */
    private Date getDateMonthsAgo(int monthsAgo) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -monthsAgo);
        return cal.getTime();
    }

    /**
     * Returns formatted date range for this week.
     */
    public String getThisWeekRange() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        Date start = cal.getTime();
        cal.add(Calendar.DAY_OF_YEAR, 6);
        return formatRange(start, cal.getTime());
    }

    /**
     * Returns formatted date range for last week.
     */
    public String getLastWeekRange() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.add(Calendar.DAY_OF_YEAR, -7);
        Date start = cal.getTime();
        cal.add(Calendar.DAY_OF_YEAR, 6);
        return formatRange(start, cal.getTime());
    }

    /**
     * Returns formatted date range for this month.
     */
    public String getThisMonthRange() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        Date start = cal.getTime();
        cal = Calendar.getInstance();
        return formatRange(start, cal.getTime());
    }

    /**
     * Returns formatted date range for last month.
     */
    public String getLastMonthRange() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -1);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        Date start = cal.getTime();
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        return formatRange(start, cal.getTime());
    }

    /**
     * Returns formatted date range for the last 6 months.
     */
    public String getLastSixMonthRange() {
        Date start = getDateMonthsAgo(6);
        Date end = new Date();
        return formatRange(start, end);
    }

    /**
     * Utility method to format start and end dates to display range.
     */
    private String formatRange(Date start, Date end) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM", Locale.getDefault());
        return sdf.format(start) + " - " + sdf.format(end);
    }

    /**
     * Loads the total income for a specific month (e.g., "May").
     *
     * @param month The 3-letter month abbreviation (e.g., "May")
     * @return LiveData with total income for that month
     */
    public LiveData<Double> getTotalIncomeByMonth(String month) {
        MutableLiveData<Double> total = new MutableLiveData<>(0.0);
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM", Locale.getDefault());

        // Parse month name into month index
        for (int i = 0; i < 12; i++) {
            Calendar temp = Calendar.getInstance();
            temp.set(Calendar.MONTH, i);
            if (sdf.format(temp.getTime()).equalsIgnoreCase(month)) {
                start.set(Calendar.MONTH, i);
                end.set(Calendar.MONTH, i);
                break;
            }
        }

        start.set(Calendar.DAY_OF_MONTH, 1);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        end.set(Calendar.MILLISECOND, 999);

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("income")
                .whereGreaterThanOrEqualTo("timestamp", new Timestamp(start.getTime()))
                .whereLessThanOrEqualTo("timestamp", new Timestamp(end.getTime()))
                .get()
                .addOnSuccessListener(snapshot -> {
                    double sum = 0;
                    for (QueryDocumentSnapshot doc : snapshot) {
                        incomeModel model = doc.toObject(incomeModel.class);
                        sum += model.getAmount();
                    }
                    total.setValue(sum);
                });

        return total;
    }


}
