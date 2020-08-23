
package com.atakmap.android.math;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.spatial.kml.KMLUtil;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class MathUtils {

    private static final String TAG = "MathUtils";

    private static final long HOUR_MILLIS = 1000 * 60 * 60;
    private static final long DAY_MILLIS = HOUR_MILLIS * 24;
    private static final long WEEK_MILLIS = DAY_MILLIS * 7;
    private final static double LOG2 = Math.log(2);

    /**
     * Format file size for UI
     * 
     * @param lengthInBytes the files size in bytes
     * @return the file size formatted for human consumption
     */
    public static String GetLengthString(final long lengthInBytes) {

        if (lengthInBytes < 1024)
            return String
                    .format(LocaleUtil.getCurrent(), "%d B", lengthInBytes);

        double v = lengthInBytes / 1024D;
        if (v < 1024)
            return String.format(LocaleUtil.getCurrent(), "%.1f KB", v);

        v = v / 1024D;
        if (v < 1024)
            return String.format(LocaleUtil.getCurrent(), "%.1f MB", v);

        return String.format(LocaleUtil.getCurrent(), "%.1f GB", v / 1024D);
    }

    /**
     * Get the amount of millis specified formatted for UI display of time remaining
     * for an ongoing task, or for an elapsed amount of time
     * @param timeRemainingMillis the time representation in milliseconds
     * @return the formatted as hours minutes seconds
     */
    public static String GetTimeRemainingString(long timeRemainingMillis) {
        if (timeRemainingMillis < 500)
            return "0s";
        if (timeRemainingMillis > DAY_MILLIS) {
            return ">1 day";
        }
        if (timeRemainingMillis > HOUR_MILLIS) {
            long v = timeRemainingMillis;
            long h = TimeUnit.MILLISECONDS.toHours(v);
            v -= TimeUnit.HOURS.toMillis(h);
            long m = TimeUnit.MILLISECONDS.toMinutes(v);
            v -= TimeUnit.MINUTES.toMillis(m);
            long s = TimeUnit.MILLISECONDS.toSeconds(v);

            // hour/min/sec
            return String.format(LocaleUtil.getCurrent(), "%dh %dm %ds", h, m,
                    s);
        }

        // min/sec
        return String.format(
                LocaleUtil.getCurrent(),
                "%dm %ds",
                TimeUnit.MILLISECONDS.toMinutes(timeRemainingMillis),
                TimeUnit.MILLISECONDS.toSeconds(timeRemainingMillis)
                        -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS
                                .toMinutes(timeRemainingMillis)));
    }

    /**
     * Similar to GetTimeRemainingString, but not only useful for ongoing tasks, as it will
     * display a date if the specified time range is more than 1 week
     *
     * @param now       time now
     * @param elapsed   time to display
     * @param bDate     true to display date for >1week, false to display "2 weeks ago"
     * @return the time remaining string
     */
    public static String GetTimeRemainingOrDateString(final long now,
            final long elapsed,
            final boolean bDate) {
        if (elapsed < 500)
            return "0s";
        if (elapsed > WEEK_MILLIS) {
            if (bDate) {
                //return the date
                Date d = new Date(now - elapsed);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd",
                        LocaleUtil.getCurrent());
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                return sdf.format(d);
            } else {
                //return number of weeks
                long weeks = elapsed / WEEK_MILLIS;
                return String.format(LocaleUtil.getCurrent(), "%d week"
                        + (weeks < 2 ? " ago" : "s ago"), weeks);
            }
        }
        if (elapsed > DAY_MILLIS) {
            long days = elapsed / DAY_MILLIS;
            return String.format(LocaleUtil.getCurrent(), "%d day"
                    + (days < 2 ? " ago" : "s ago"), days);
        }
        if (elapsed > 12 * HOUR_MILLIS) {
            //more than 12 hours, do not include minutes or seconds
            long h = TimeUnit.MILLISECONDS.toHours(elapsed);

            // hour/min/sec
            return String.format(LocaleUtil.getCurrent(), "%d hour"
                    + (h < 2 ? " ago" : "s ago"), h);
        }

        if (elapsed > HOUR_MILLIS) {
            //do not include seconds
            long v = elapsed;
            long h = TimeUnit.MILLISECONDS.toHours(v);
            v -= TimeUnit.HOURS.toMillis(h);
            long m = TimeUnit.MILLISECONDS.toMinutes(v);

            // hour/min/sec
            return String.format(LocaleUtil.getCurrent(), "%dh %dm ago", h, m);
        }

        // min/sec
        return String.format(
                LocaleUtil.getCurrent(),
                "%dm %ds ago",
                TimeUnit.MILLISECONDS.toMinutes(elapsed),
                TimeUnit.MILLISECONDS.toSeconds(elapsed)
                        -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS
                                .toMinutes(elapsed)));
    }

    /**
     * Parse the ISO 8601 timestamp and format it relative now e.g. 5 min ago
     *
     * @param timestamp the ISO 8601 timestamp
     * @return the resulting offset from the current time in human language.
     */
    public static String parseTimeRelativeToNow(final String timestamp) {
        if (FileSystemUtils.isEmpty(timestamp))
            return null;

        long subTime = -1;
        try {
            SimpleDateFormat sdf = KMLUtil.KMLDateTimeFormatterMillis.get();
            if (sdf != null) {
                Date d = sdf.parse(timestamp);
                if (d != null)
                    subTime = d.getTime();
            }
        } catch (ParseException e) {
            Log.w(TAG, "Failed to parse time: " + timestamp, e);
        }
        if (subTime > 0) {
            long millisNow = (new CoordinatedTime()).getMilliseconds();
            long millisAgo = millisNow - subTime;
            return MathUtils.GetTimeRemainingOrDateString(millisNow, millisAgo,
                    true);
        } else {
            return null;
        }
    }

    /**
     * Format for UI
     * 
     * @param averageSpeed bytes per millis
     * @return the download speed string
     */
    public static String GetDownloadSpeedString(double averageSpeed) {
        if (averageSpeed <= 0)
            return "0 B/s";

        double bps = averageSpeed * 1024D;
        if (bps < 1024)
            return String.format(LocaleUtil.getCurrent(), "%.1f B/s", bps);

        bps = bps / 1024;
        if (bps < 1024)
            return String.format(LocaleUtil.getCurrent(), "%.1f KB/s", bps);

        bps = bps / 1024;
        if (bps < 1024)
            return String.format(LocaleUtil.getCurrent(), "%.1f MB/s", bps);

        return String.format(LocaleUtil.getCurrent(), "%.1f GB/s", bps / 1024D);
    }

    /**
     * Implementation of base 2 logorithm using the straightforward unoptimized method of 
     * log(x)/log(2).
     * @param v the input value to used.
     */
    public static double log2(final double v) {
        return Math.log(v) / MathUtils.LOG2;
    }

    /**
     * Convert a string to a double (exceptions caught)
     *
     * @param value String value
     * @param defaultVal Default value if conversion fails
     * @return Converted value or default value if conversion failed
     */
    public static double parseDouble(String value, double defaultVal) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return defaultVal;
        }
    }

    /**
     * Convert a string to an integer (exceptions caught)
     * @param value String value
     * @param defaultVal Default value if conversion fails
     * @return Converted value or default if failed
     */
    public static int parseInt(String value, int defaultVal) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return defaultVal;
        }
    }
}
