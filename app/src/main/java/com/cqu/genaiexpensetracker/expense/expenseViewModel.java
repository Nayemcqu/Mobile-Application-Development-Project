package com.cqu.genaiexpensetracker.expense;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.cqu.genaiexpensetracker.transactions.TransactionItem;
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
 * ViewModel class for managing expense-related data including filters like
 * weekly, monthly, and six-months, as well as lifetime total expenses.
 */
public class expenseViewModel extends ViewModel {

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    private final MutableLiveData<List<TransactionItem>> thisPeriodExpenses = new MutableLiveData<>();
    private final MutableLiveData<List<TransactionItem>> pastPeriodExpenses = new MutableLiveData<>();
    private final MutableLiveData<Double> totalExpenses = new MutableLiveData<>();

    private final MutableLiveData<Double> lifetimeTotalExpenses = new MutableLiveData<>();

    /**
     * Returns LiveData for the fixed lifetime total expenses.
     */
    public LiveData<Double> getLifetimeTotalExpenses() {
        return lifetimeTotalExpenses;
    }

    /**
     * Loads total expenses from all records regardless of date.
     */
    public void loadLifetimeTotalExpenses() {
        String uid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        if (uid == null) return;

        firestore.collection("users")
                .document(uid)
                .collection("expenses")
                .get()
                .addOnSuccessListener(snapshot -> {
                    double total = 0;
                    for (QueryDocumentSnapshot doc : snapshot) {
                        expenseModel model = doc.toObject(expenseModel.class);
                        total += model.getAmount();
                    }
                    lifetimeTotalExpenses.setValue(total);
                });
    }


    public LiveData<List<TransactionItem>> getThisPeriodExpenses() {
        return thisPeriodExpenses;
    }

    public LiveData<List<TransactionItem>> getPastPeriodExpenses() {
        return pastPeriodExpenses;
    }

    public LiveData<Double> getTotalExpenses() {
        return totalExpenses;
    }

    public void loadFilteredExpenses(String filter) {
        String uid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        if (uid == null) return;

        Calendar now = Calendar.getInstance();
        Date startThis, endThis;
        Date startPast = null, endPast = null;

        if (filter.equalsIgnoreCase("Weekly")) {
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

        } else if (filter.equalsIgnoreCase("Monthly")) {
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
                .collection("expenses")
                .whereGreaterThanOrEqualTo("createdAt", new Timestamp(startThis))
                .whereLessThanOrEqualTo("createdAt", new Timestamp(endThis))
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<TransactionItem> list = new ArrayList<>();
                    double sum = 0;
                    for (QueryDocumentSnapshot doc : snapshot) {
                        expenseModel model = doc.toObject(expenseModel.class);
                        sum += model.getAmount();
                        list.add(new TransactionItem(model, null));
                    }
                    thisPeriodExpenses.setValue(list);
                    totalExpenses.setValue(sum);
                });

        if (!filter.equalsIgnoreCase("6 Months")) {
            firestore.collection("users")
                    .document(uid)
                    .collection("expenses")
                    .whereGreaterThanOrEqualTo("createdAt", new Timestamp(startPast))
                    .whereLessThanOrEqualTo("createdAt", new Timestamp(endPast))
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        List<TransactionItem> list = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : snapshot) {
                            expenseModel model = doc.toObject(expenseModel.class);
                            list.add(new TransactionItem(model, null));
                        }
                        pastPeriodExpenses.setValue(list);
                    });
        } else {
            pastPeriodExpenses.setValue(new ArrayList<>());
        }
    }

    public String getThisWeekRange() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        Date start = cal.getTime();
        cal.add(Calendar.DAY_OF_YEAR, 6);
        return formatRange(start, cal.getTime());
    }

    public String getLastWeekRange() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.add(Calendar.DAY_OF_YEAR, -7);
        Date start = cal.getTime();
        cal.add(Calendar.DAY_OF_YEAR, 6);
        return formatRange(start, cal.getTime());
    }

    public String getThisMonthRange() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        Date start = cal.getTime();
        cal = Calendar.getInstance();
        return formatRange(start, cal.getTime());
    }

    public String getLastMonthRange() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -1);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        Date start = cal.getTime();
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        return formatRange(start, cal.getTime());
    }

    public String getLastSixMonthRange() {
        Date start = getDateMonthsAgo(6);
        Date end = new Date();
        return formatRange(start, end);
    }

    private String formatRange(Date start, Date end) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM", Locale.getDefault());
        return sdf.format(start) + " - " + sdf.format(end);
    }

    private Date getDateMonthsAgo(int monthsAgo) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -monthsAgo);
        return cal.getTime();
    }

    /**
     * Loads the total expense for a specific month (e.g., "May").
     *
     * @param month The 3-letter month abbreviation (e.g., "May")
     * @return LiveData with total expense for that month
     */
    public LiveData<Double> getTotalExpenseByMonth(String month) {
        MutableLiveData<Double> total = new MutableLiveData<>(0.0);
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM", Locale.getDefault());

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
                .collection("expenses")
                .whereGreaterThanOrEqualTo("createdAt", new Timestamp(start.getTime()))
                .whereLessThanOrEqualTo("createdAt", new Timestamp(end.getTime()))
                .get()
                .addOnSuccessListener(snapshot -> {
                    double sum = 0;
                    for (QueryDocumentSnapshot doc : snapshot) {
                        expenseModel model = doc.toObject(expenseModel.class);
                        sum += model.getAmount();
                    }
                    total.setValue(sum);
                });

        return total;
    }


}
