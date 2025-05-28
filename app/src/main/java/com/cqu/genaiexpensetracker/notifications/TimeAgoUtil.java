package com.cqu.genaiexpensetracker.notifications;

import android.text.format.DateUtils;

/**
 * Utility class for formatting timestamps into relative "time ago" strings.
 */
public class TimeAgoUtil {
    /**
     * Returns a human-readable relative time string (e.g., "5 minutes ago", "Yesterday")
     * for the given time in milliseconds.
     *
     * @param timeMillis The timestamp in milliseconds (e.g., from System.currentTimeMillis()).
     * @return A relative time string describing how long ago the event occurred.
     */
    public static String getTimeAgo(long timeMillis) {
        return DateUtils.getRelativeTimeSpanString(
                timeMillis,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString();
    }
}
