
package com.atakmap.coremap.maps.time;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.Locale;
import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;

/**
 * A date-time value as per the Cursor on Target specification. This class ensures proper parsing
 * and string exporting are properly formated.
 */
public class CoordinatedTime implements Parcelable {

    /**
     * Allows for a Thread Safe usage of any SimpleDateFormat construct.
     */
    public static class SimpleDateFormatThread extends
            ThreadLocal<SimpleDateFormat> {
        final String format;
        final Locale locale;
        final TimeZone tz;

        /**
         * Constructs a thread safe version of the SimpleDateFormatter with the 
         * format, locale and timezone specified.
         * @param format @see java.text.SimpleDateFormat for a list of correct formats.
         * @param locale the locale to be used, can be null to use the default locale.
         * @param tz the time zone to use, can be null to use the default timezone.
         */
        public SimpleDateFormatThread(String format, Locale locale,
                TimeZone tz) {
            this.format = format;
            this.locale = locale;
            this.tz = tz;
        }

        /**
         * Constructs a thread safe version of the SimpleDateFormatter with the 
         * format and locale specified.  This constructor uses the default timezone.
         * @param format @see java.text.SimpleDateFormat for a list of correct formats.
         * @param locale the locale to be used, can be null to use the default locale.
         */
        public SimpleDateFormatThread(String format, Locale locale) {
            this(format, locale, null);
        }

        /**
         * Constructs a thread safe version of the SimpleDateFormatter with the 
         * format specified.  This constructor uses the default locale and timezone.
         * @param format @see java.text.SimpleDateFormat for a list of correct formats.
         */
        public SimpleDateFormatThread(String format) {
            this(format, null, null);
        }

        @Override
        protected SimpleDateFormat initialValue() {
            SimpleDateFormat sdf;
            if (locale != null)
                sdf = new SimpleDateFormat(format, locale);
            else
                sdf = new SimpleDateFormat(format);

            if (tz != null)
                sdf.setTimeZone(tz);
            return sdf;
        }

        /**
         * see SimpleDateFormat.format(Date)
         */
        public String format(Date date) {
            return this.get().format(date);
        }

        /**
         * see SimpleDateFormat.setTimeZone(TimeZone)
         */
        public void setTimeZone(TimeZone tz) {
            this.get().setTimeZone(tz);
        }

        /**
         * see SimpleDateFormat.parse(String)
         */
        public Date parse(String source) throws ParseException {
            return this.get().parse(source);
        }
    }

    private static final String TAG = "CoordinatedTime";

    private final long timestamp;
    private String _toString;
    /** COT Time per ISO 8601 */
    private final static SimpleDateFormatThread _COT_TIME_FORMAT = new SimpleDateFormatThread(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            Locale.US,
            TimeZone.getTimeZone("UTC"));
    /** COT Time per ISO 8601 */
    private final static SimpleDateFormatThread _LIBERAL_COT_TIME_FORMAT = new SimpleDateFormatThread(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            Locale.US,
            TimeZone.getTimeZone("UTC"));

    private static long gpsTimeDelta = 0;
    private static boolean gps = false;

    /**
     * Set the global offset between the current system time in milliseconds since Jan. 1, 1970, 
     * midnight GMT and the GPS time milliseconds since Jan. 1, 1970, midnight GMT.  
     * Setting the gps time to 0 indicates no GPS time exists and the coordinated time will be 
     * the locally set time.
     */
    static public void setCoordinatedTimeMillis(final long gpsTime) {
        if (gpsTime > 0) {
            gpsTimeDelta = System.currentTimeMillis() - gpsTime;
            gps = true;
        } else {
            gpsTimeDelta = 0;
            gps = false;
        }
    }

    /**
     * Indicate if GPS time has been set.
     */
    static public boolean isGPSTime() {
        return gps;
    }

    /**
     * If GPS time has been set, provide the offset from the GPS time and the local device 
     * time.   If GPS has not been set, the offset will be 0.
     * Note:  If GPS time is behind the system time, the number will be negative.
     */
    static public long getCoordinatedTimeOffset() {
        return gpsTimeDelta;
    }

    /**
     * For creating new timestamps within the system, this method should be used directly.
     * 
     * Create a CoT time that represents the current time:
     *       1) based on system time measured as the number of milliseconds since Jan. 1, 1970, midnight GMT.
     *       2) or if a GPS delta has been set, the system time corrected to GPS time
     *              note:   GPS time is not used directly, a correction is used instead, due to the poor granularity 
     *                      of GPS time.
     *  
     */
    public CoordinatedTime() {
        timestamp = System.currentTimeMillis() - gpsTimeDelta;

        // Log.d(TAG, "current requested time corrected with a delta of " + 
        //            gpsTimeDelta + " is = " + _COT_TIME_FORMAT.format(new Date(timestamp)));
    }

    /**
     * Create a CoT time in milliseconds since January 1st 1970 00:00:00 UTC.
     * 
     * @param milliseconds offset in milliseconds from Unix Epoch
     */
    public CoordinatedTime(final long milliseconds) {
        timestamp = milliseconds;
    }

    /**
     * Create a CoT time from its Parcel representation
     * 
     * @param source the parcel with the timestamp
     */
    public CoordinatedTime(final Parcel source) {
        timestamp = source.readLong();
    }

    public long millisecondDiff(CoordinatedTime other) {
        return timestamp - other.timestamp;
    }

    /**
     * Get a CoT time that is a positive or negative number of milliseconds from this Cot time
     * 
     * @param milliseconds number of milliseconds to add (may be negative)
     * @return the coordinated time with the appropriate number of milliseconds added
     */
    public CoordinatedTime addMilliseconds(final int milliseconds) {
        return new CoordinatedTime(timestamp + milliseconds);
    }

    /**
     * Get a CoT time that is a positive or negative number of seconds from this Cot time
     * 
     * @param seconds number of seconds to add (may be negative)
     * @return a new Coordinated time with the appropriate seconds added.
     */
    public CoordinatedTime addSeconds(final int seconds) {
        return new CoordinatedTime(timestamp + seconds * 1000);
    }

    /**
     * Get a CoT time that is a positive or negative number of minutes from this Cot time
     * 
     * @param minutes number of minutes to add (may be negative)
     * @return a new Coordinated time with the appropriate minutes added.
     */
    public CoordinatedTime addMinutes(final int minutes) {
        return addSeconds(minutes * 60);
    }

    /**
     * Get a CoT time that is a positive or negative number of hours from this Cot time
     * 
     * @param hours number of hours to add (may be negative)
     * @return a new Coordinated time with the appropriate hours added.
     */
    public CoordinatedTime addHours(final int hours) {
        return addMinutes(hours * 60);
    }

    /**
     * Get a CoT time that is a positive or negative number of days from this Cot time
     * 
     * @param days number of days to add (may be negative)
     * @return a new Coordinated time with the appropriate days added.
     */
    public CoordinatedTime addDays(final int days) {
        return addHours(days * 24);
    }

    /**
     * Get this CoT time in milliseconds since January 1st 1970 00:00:00 UTC
     * 
     * @return the time in milliseconds from EPOCH
     */
    public long getMilliseconds() {
        return timestamp;
    }

    /**
     * Obtains the current Java/Android date:
     *       1) based on system time measured as the number of milliseconds since Jan. 1, 1970, midnight GMT.
     *       2) or if a GPS delta has been set, the system time corrected to GPS time
     *              note:   GPS time is not used directly, a correction is used instead, due to the poor granularity 
     *                      of GPS time.
     */
    public static Date currentDate() {
        return new Date(System.currentTimeMillis() - gpsTimeDelta);
    }

    /**
     * Obtains the current time in millis accurate to GPS time if available, otherwise accurate to the system
     * clock.
     * @return the time in lilliseconds from EPOCH
     */
    public static long currentTimeMillis() {
        return System.currentTimeMillis() - gpsTimeDelta;
    }

    private static File directory = null;

    /**
     * Designate for refactoring. Set a reasonable log directory for bad_time, until this is
     * refactored.
     */
    static public void setLoggerDirectory(final File dir) {
        directory = dir;
        Log.d(TAG, "publish bad_time messages to: " + directory);
    }

    /**
     * Format a CoT time string from a java Date
     * 
     * @param date the date to format
     * @return the formatted data.
     */
    private static String formatTime(final Date date) {
        String time = "2009-09-15T21:00:00.00Z"; // A not so random date, done in case the date
                                                 // library is sick - Andrew
        try {
            time = _COT_TIME_FORMAT.format(date);
        } catch (IllegalArgumentException e) {
            String msg = "Error in formatTime - the input time is probably not formated correctly or is not a valid time"
                    + "\r\n" + date.toString();

            // XXX Designate for refactoring. Should happen higher up?
            Log.e(TAG, msg);
            if (directory != null) {
                if (!IOProviderFactory.exists(directory)) {
                    if (!IOProviderFactory.mkdirs(directory)) {
                        Log.w(TAG, "Failed to create: " + directory);
                    }
                }

                PrintWriter w = null;
                OutputStreamWriter osw = null;
                try {
                    w = new PrintWriter(osw = new OutputStreamWriter(
                            IOProviderFactory
                                    .getOutputStream(new File(directory,
                                            "bad_time.txt"), true),
                            FileSystemUtils.UTF8_CHARSET));
                    w.append(msg).append("\r\n");
                    e.printStackTrace(w);
                } catch (FileNotFoundException e1) {
                    Log.e(TAG,
                            "File not found while attempting to write bad_time.txt",
                            e1);
                } catch (IOException e2) {
                    Log.e(TAG,
                            "Encountered IO issue while attempting to write bad_time.txt",
                            e2);
                } finally {
                    if (w != null)
                        w.close();
                    if (osw != null)
                        try {
                            osw.close();
                        } catch (IOException ignored) {
                        }
                }
            }
        }
        return time;
    }

    /**
     * Produce a valid Cursor on Target formatted date string from a CoordinatedTime object.
     * 
     * @param time the coordinated time to format for use within a cursor on target message.
     * @return the formatted string.
     */
    public static String toCot(final CoordinatedTime time) {
        return formatTime(new Date(time.getMilliseconds()));
    }

    /**
     * Parse a CoT time String. The parser is somewhat lenient in that it accepts a string with or
     * without the millisecond decimal portion.
     * 
     * @param formattedTime the formatted time as a string
     * @return the coordinated time
     * @throws ParseException if the formatted time does not comply with the CoT specification.
     */
    private static CoordinatedTime fromCotFallback(final String formattedTime)
            throws ParseException {
        Date date;
        try {
            date = _COT_TIME_FORMAT.parse(formattedTime);
        } catch (Exception ex) {
            date = _LIBERAL_COT_TIME_FORMAT.parse(formattedTime);
        }
        return new CoordinatedTime(date.getTime());
    }

    private static final Calendar CachedCalendar = new GregorianCalendar();
    static {
        CachedCalendar.setTimeZone(TimeZone.getTimeZone("GMT"));
        CachedCalendar.setLenient(false);
        CachedCalendar.clear();
    }

    /**
     * Assuming no sign and standard integers without issues.
     */
    public static int parseNonZeroInt(final String s) {
        final int len = s.length();
        int i = 0;
        int tmp;
        int num = 0;

        while (i < len) {
            tmp = s.charAt(i++) - '0';
            if (tmp < 0 || tmp > 9)
                throw new NumberFormatException("string " + s
                        + " contains a non digit in position " + i);
            num = num * 10 + tmp;
        }

        return num;
    }

    /**
     * Parse a CoT time String in the form: 2009-09-15T21:00:00.00Z without using simple date
     * parser. Thread-safe
     * 
     * Note: This will parse the CoT time string in the format 2009-09-15T21:00:00.00Z and will 
     * permissively allow for 2009-09-15T21:00:00.000Z and 2009-09-15T21:00:00.0Z which are not 
     * part of the spec.
     * 
     * @param date  A date from a CoT message.
     * @return the coordinated time from derived from the CoT message date.
     * @throws ParseException if there is not a valid CoT time encountered.
     */
    synchronized public static CoordinatedTime fromCot(final String date)
            throws ParseException {
        try {

            final int datelen = date.length();

            if (datelen > 19) {
                int y = parseNonZeroInt(date.substring(0, 4));
                int m = parseNonZeroInt(date.substring(5, 7));
                --m;
                int d = parseNonZeroInt(date.substring(8, 10));
                int h = parseNonZeroInt(date.substring(11, 13));
                int mm = parseNonZeroInt(date.substring(14, 16));
                int s = parseNonZeroInt(date.substring(17, 19));

                int ms = 0;

                if (datelen > 23) {
                    ms = parseNonZeroInt(date.substring(20, 23));
                } else if (datelen > 22) {
                    ms = parseNonZeroInt(date.substring(20, 22)) * 10;
                } else if (datelen > 21) {
                    ms = parseNonZeroInt(date.substring(20, 21)) * 100;
                }

                CachedCalendar.set(y, m, d, h, mm, s);

                // Log.d(TAG, "original date = " + date );
                // Log.d(TAG, "y = " + y + " m = " + m + " d = " + d + " h = " + h + " mm = " + mm + 
                //            " s = " + s + " ms " + ms);

                return new CoordinatedTime(CachedCalendar.getTime().getTime()
                        + ms);
            }
        } catch (Exception e) {
            Log.d(TAG, "exception occurred parsing: " + date, e);
        }

        // fall back to the original implementation.
        return fromCotFallback(date);
    }

    /**
     * Get the string representation of this CoT time (same as formatTime()).
     */
    public String toString() {
        if (_toString == null) {
            _toString = toCot(this);
        }
        return _toString;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(timestamp);
    }

    public final static Parcelable.Creator<CoordinatedTime> CREATOR = new Parcelable.Creator<CoordinatedTime>() {
        @Override
        public CoordinatedTime createFromParcel(Parcel source) {
            return new CoordinatedTime(source);
        }

        @Override
        public CoordinatedTime[] newArray(int size) {
            return new CoordinatedTime[size];
        }
    };
}
