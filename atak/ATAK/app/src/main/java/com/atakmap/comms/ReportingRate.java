
package com.atakmap.comms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.content.SharedPreferences;
import android.os.SystemClock;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.android.ipc.AtakBroadcast;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Manages reporting rate based on device & position changes
 *  Monitors a list of preferences to trigger immediate report
 *  Monitors changes in altitude (not accounted for in device speed)
 *  Dynamic reporting rate based on speed
 */
public class ReportingRate extends BroadcastReceiver implements
        SharedPreferences.OnSharedPreferenceChangeListener {
    private final static String TAG = "ReportingRate";

    public static final String REPORT_LOCATION = "com.atakmap.cot.reporting.REPORT_LOCATION";

    /**
     * Above this speed, use max reporting rate
     */
    private final static double MAX_SPEED_MS = 13.4112; //30 MPH

    /**
     * Below this speed, use still reporting rate.
     * Between MIN & MAX, scale reporting rate
     */
    private final static double MIN_SPEED_MS = 0.44704; //1 MPH

    private final static List<String> prefsToMonitor;

    /**
     * Immediate report if altitude changes this much since last report
     */
    private final static double ALT_THRESHOLD = 50; //50 meters

    /**
     * Immediate report if speed changes this much since last report
     */
    private final static double SPEED_THRESHOLD_MS = 3.12928; //7 MPH

    /**
     * Used as a buffer when determining if its time to report
     */
    private static final long STALE_BUFFER = 10000; //10 seconds

    /**
     * Used as a padding in stale time
     */
    private static final int STALE_PADDING = 15000; //15 seconds

    /**
     * Stale time is rate multiplied by this
     */
    private static final int UNRELIABLE_RATE_MULTIPLIER = 4; //rate*4

    /**
     * Stale time is rate multiplied by this
     */
    private static final int RELIABLE_RATE_MULTIPLIER = 2; //rate*2

    private final SharedPreferences _preferences;
    private Timer _publishContactInfo;
    private double _lastReportedAltitudeUnreliable = Double.NaN;
    private double _lastReportedAltitudeReliable = Double.NaN;
    private double _lastReportedSpeedUnreliableMs = -1;
    private double _lastReportedSpeedReliableMs = -1;

    private boolean _publishSA = false;

    /**
     * Current reporting strategy
     */
    private String locationReportingStrategy = dynamic_string;

    /**
     * Flag to indicate we should report asap
     */
    private boolean bReportAsap;

    //store rate and last send for constant rate
    private int constantReportingRateUnreliable = 3000;
    private int constantReportingRateReliable = 15000;
    private long lastTime_constantReportingRateUnreliable = 0;
    private long lastTime_constantReportingRateReliable = 0;
    private int lastStale_constantReportingRateUnreliable = 0;
    private int lastStale_constantReportingRateReliable = 0;

    //store rate and last send for dynamic rates
    private int dynamicReportingRateStationaryUnreliable = 30000;
    private int dynamicReportingRateStationaryReliable = 180000;
    private int dynamicReportingRateMinUnreliable = 20000;
    private int dynamicReportingRateMaxUnreliable = 2000;
    private int dynamicReportingRateMinReliable = 20000;
    private int dynamicReportingRateMaxReliable = 2000;
    private long lastTime_dynamicReportingRateUnreliable = 0;
    private long lastTime_dynamicReportingRateReliable = 0;
    private int lastStale_dynamicReportingRateUnreliable = 0;
    private int lastStale_dynamicReportingRateReliable = 0;

    private static final String dynamic_string = "Dynamic";
    private static final String constant_string = "Constant";

    /**
     * Interface to get information about self, and to report via network
     */
    private final Callback _callback;

    static {
        prefsToMonitor = new ArrayList<>();
        prefsToMonitor.add("locationUnitType");
        prefsToMonitor.add("locationCallsign");
        prefsToMonitor.add("locationTeam");
        prefsToMonitor.add("atakRoleType");
        prefsToMonitor.add("locationUseWRCallsign");
    }

    public interface Callback {
        GeoPoint getReportingPoint();

        double getReportingSpeed();

        void report(int stale, int flags);
    }

    public ReportingRate(Callback callback,
            SharedPreferences prefs) {

        _preferences = prefs;
        _callback = callback;
    }

    public void init() {

        DocumentedIntentFilter reportfilter = new DocumentedIntentFilter(
                REPORT_LOCATION);
        AtakBroadcast.getInstance().registerReceiver(this,
                reportfilter);

        initReportingRates();

        _publishContactInfo = new Timer("PublishContactInfoThread");

        //check if we need to report, once per second. Commercial GPS doesn't currently
        //update more often, on average
        _publishContactInfo.schedule(new TimerTask() {
            @Override
            public void run() {
                checkIfTimeToReport();
            }
        }, 0, 1000);

        _preferences
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String reason = intent.getStringExtra("reason");
        setReportAsap(reason);
    }

    private void initReportingRates() {
        locationReportingStrategy = _preferences.getString(
                "locationReportingStrategy", dynamic_string);

        _publishSA = _preferences.getBoolean("dispatchLocationCotExternal",
                true);

        try {
            constantReportingRateUnreliable = Integer
                    .parseInt(_preferences.getString(
                            "constantReportingRateUnreliable", "3"))
                    * 1000;
        } catch (NumberFormatException nfe) {
            constantReportingRateUnreliable = 3000;
        }
        if (constantReportingRateUnreliable < 0)
            constantReportingRateUnreliable = 3000;

        try {
            constantReportingRateReliable = Integer
                    .parseInt(_preferences.getString(
                            "constantReportingRateReliable", "15"))
                    * 1000;
        } catch (NumberFormatException nfe) {
            constantReportingRateReliable = 15000;
        }
        if (constantReportingRateReliable < 0)
            constantReportingRateReliable = 15000;

        try {
            dynamicReportingRateStationaryUnreliable = Integer
                    .parseInt(_preferences.getString(
                            "dynamicReportingRateStationaryUnreliable", "30"))
                    * 1000;
        } catch (NumberFormatException nfe) {
            dynamicReportingRateStationaryUnreliable = 30000;
        }
        if (dynamicReportingRateStationaryUnreliable < 0)
            dynamicReportingRateStationaryUnreliable = 30000;
        try {
            dynamicReportingRateMinUnreliable = Integer
                    .parseInt(_preferences.getString(
                            "dynamicReportingRateMinUnreliable", "20"))
                    * 1000;
        } catch (NumberFormatException nfe) {
            dynamicReportingRateMinUnreliable = 20000;
        }
        if (dynamicReportingRateMinUnreliable < 0)
            dynamicReportingRateMinUnreliable = 20000;
        try {
            dynamicReportingRateMaxUnreliable = Integer
                    .parseInt(_preferences.getString(
                            "dynamicReportingRateMaxUnreliable", "2"))
                    * 1000;
        } catch (NumberFormatException nfe) {
            dynamicReportingRateMaxUnreliable = 2000;
        }
        if (dynamicReportingRateMaxUnreliable < 0)
            dynamicReportingRateMaxUnreliable = 2000;

        try {
            dynamicReportingRateStationaryReliable = Integer
                    .parseInt(_preferences.getString(
                            "dynamicReportingRateStationaryReliable", "180"))
                    * 1000;
        } catch (NumberFormatException nfe) {
            dynamicReportingRateStationaryReliable = 180000;
        }
        if (dynamicReportingRateStationaryReliable < 0)
            dynamicReportingRateStationaryReliable = 180000;
        try {
            dynamicReportingRateMinReliable = Integer
                    .parseInt(_preferences.getString(
                            "dynamicReportingRateMinReliable", "20"))
                    * 1000;
        } catch (NumberFormatException nfe) {
            dynamicReportingRateMinReliable = 20000;
        }
        if (dynamicReportingRateMinReliable < 0)
            dynamicReportingRateMinReliable = 20000;
        try {
            dynamicReportingRateMaxReliable = Integer
                    .parseInt(_preferences.getString(
                            "dynamicReportingRateMaxReliable", "2"))
                    * 1000;
        } catch (NumberFormatException nfe) {
            dynamicReportingRateMaxReliable = 2000;
        }
        if (dynamicReportingRateMaxReliable < 0)
            dynamicReportingRateMaxReliable = 2000;

    }

    @Override
    public void onSharedPreferenceChanged(
            SharedPreferences cotPrefs, String key) {

        if (key == null)
            return;

        //check if this pref change means we need to report now
        checkMonitoring(key);

        if (FileSystemUtils.isEquals(key, "constantReportingRateUnreliable")
                ||
                FileSystemUtils.isEquals(key, "constantReportingRateReliable")
                ||
                FileSystemUtils.isEquals(key,
                        "dynamicReportingRateMinUnreliable")
                ||
                FileSystemUtils.isEquals(key,
                        "dynamicReportingRateMaxUnreliable")
                ||
                FileSystemUtils
                        .isEquals(key, "dynamicReportingRateMinReliable")
                ||
                FileSystemUtils
                        .isEquals(key, "dynamicReportingRateMaxReliable")
                ||
                FileSystemUtils.isEquals(key, "locationReportingStrategy")
                ||
                FileSystemUtils.isEquals(key, "dispatchLocationCotExternal")
                ||
                FileSystemUtils.isEquals(key,
                        "dynamicReportingRateStationaryUnreliable")
                ||
                FileSystemUtils.isEquals(key,
                        "dynamicReportingRateStationaryReliable")) {
            initReportingRates();
        }
    }

    public void dispose() {
        AtakBroadcast.getInstance().unregisterReceiver(this);

        if (_publishContactInfo != null) {
            _publishContactInfo.cancel();
            _publishContactInfo.purge();
            _publishContactInfo = null;
        }

        _preferences
                .unregisterOnSharedPreferenceChangeListener(this);

    }

    public synchronized void setReportAsap(String reason) {
        bReportAsap = true;
        Log.d(TAG, "Report ASAP, reason=" + reason);
    }

    private synchronized boolean getReportAsap() {
        return bReportAsap;
    }

    private synchronized void resetReportAsap() {
        bReportAsap = false;
    }

    private void checkMonitoring(String key) {
        if (prefsToMonitor.contains(key)) {
            setReportAsap("Preference changed: " + key);
        }
    }

    private void checkIfTimeToReport() {

        if (!_publishSA)
            return;

        final GeoPoint selfPoint = _callback.getReportingPoint();
        final double curSpeed = _callback.getReportingSpeed();

        if (getReportAsap()) {
            //Log.d(TAG, "bReportAsap is true (1)");
            reportBothNow(selfPoint, curSpeed);
            resetReportAsap();
            return;
        }

        //check current altitude, if much change, then report now
        double curAlt = GeoPoint.UNKNOWN;
        if (selfPoint != null && selfPoint.isAltitudeValid()) {
            curAlt = selfPoint.getAltitude();
        }

        if (GeoPoint.isAltitudeValid(curAlt) &&
                GeoPoint.isAltitudeValid(_lastReportedAltitudeUnreliable)
                && ((Math.abs(curAlt
                        - _lastReportedAltitudeUnreliable)) > ALT_THRESHOLD)) {
            setReportAsap("Altitude changed (unreliable)");
        } else if (GeoPoint.isAltitudeValid(curAlt) &&
                GeoPoint.isAltitudeValid(_lastReportedAltitudeReliable)
                && ((Math.abs(curAlt
                        - _lastReportedAltitudeReliable)) > ALT_THRESHOLD)) {
            setReportAsap("Altitude changed (reliable)");
        }

        //check current speed, if big change, then report now
        if (Math.abs(curSpeed
                - _lastReportedSpeedUnreliableMs) > SPEED_THRESHOLD_MS) {
            setReportAsap("Speed change detected (unreliable)");
        } else if (Math.abs(
                curSpeed - _lastReportedSpeedReliableMs) > SPEED_THRESHOLD_MS) {
            setReportAsap("Speed change detected (reliable)");
        }

        final long currTime = SystemClock.elapsedRealtime();

        //check again based on altitude and speed calculations
        if (getReportAsap()) {
            //Log.d(TAG, "bReportAsap is true (2)");
            reportBothNow(selfPoint, curSpeed);
            resetReportAsap();
            return;
        }

        //check reporting strategy
        if (!FileSystemUtils.isEmpty(locationReportingStrategy)
                && locationReportingStrategy.equals(constant_string)) {
            //Log.d(TAG, "Checking whether to report at constant rate");

            //constant reporting rate, unreliable protocols
            long delta = currTime - lastTime_constantReportingRateUnreliable;
            //check if time to report based on reporting rate, or if our stale time is running out
            if (delta >= constantReportingRateUnreliable
                    || (lastStale_constantReportingRateUnreliable
                            - delta) <= STALE_BUFFER) {
                // wait for 4 publications + a reasonable network delay.
                int stale = (constantReportingRateUnreliable
                        * UNRELIABLE_RATE_MULTIPLIER)
                        + STALE_PADDING;
                _callback.report(stale,
                        DispatchFlags.EXTERNAL | DispatchFlags.UNRELIABLE);
                updateLastReport(currTime, curSpeed, selfPoint, stale, true,
                        true);
            }
            //else {
            //Log.d(TAG, "not reporting constant unreliable last sent (ms): " + delta);
            //}

            //constant reporting rate, reliable protocols
            delta = currTime - lastTime_constantReportingRateReliable;
            if (delta >= constantReportingRateReliable
                    || (lastStale_constantReportingRateReliable
                            - delta) <= STALE_BUFFER) {
                // wait for two publications plus a reasonable network delay.
                int stale = (constantReportingRateReliable
                        * RELIABLE_RATE_MULTIPLIER)
                        + STALE_PADDING;
                //Log.d(TAG, "reporting constant reliable last sent (ms): " + delta + ", stale=" + stale);
                _callback.report(stale, DispatchFlags.EXTERNAL
                        | DispatchFlags.RELIABLE);
                updateLastReport(currTime, curSpeed, selfPoint, stale, true,
                        false);
            }
            //else {
            //Log.d(TAG, "not reporting constant reliable last sent (ms): " + delta);
            //}
        } else {
            //Log.d(TAG, "Checking whether to report at dynamic rate");
            //dynamic reporting based on speed

            //first report unreliable
            int max = dynamicReportingRateMaxUnreliable;
            int min = dynamicReportingRateMinUnreliable;
            int stationary = dynamicReportingRateStationaryUnreliable;
            int stale = checkIfTimeToReport(curSpeed, max, min, stationary,
                    currTime,
                    lastTime_dynamicReportingRateUnreliable,
                    lastStale_dynamicReportingRateUnreliable);
            if (stale > 0) {
                //Log.d(TAG, "reporting now dynamic unreliable stale=" + stale);
                _callback.report(stale,
                        DispatchFlags.EXTERNAL | DispatchFlags.UNRELIABLE);
                updateLastReport(currTime, curSpeed, selfPoint, stale, false,
                        true);
            }

            //now report reliable
            max = dynamicReportingRateMaxReliable;
            min = dynamicReportingRateMinReliable;
            stationary = dynamicReportingRateStationaryReliable;
            stale = checkIfTimeToReport(curSpeed, max, min, stationary,
                    currTime,
                    lastTime_dynamicReportingRateReliable,
                    lastStale_dynamicReportingRateReliable);
            if (stale > 0) {
                //Log.d(TAG, "reporting now dynamic reliable stale=" + stale);
                _callback.report(stale, DispatchFlags.EXTERNAL
                        | DispatchFlags.RELIABLE);
                updateLastReport(currTime, curSpeed, selfPoint, stale, false,
                        false);
            }
        }
    }

    /**
     * Update transient variables about our last report
     * @param currTime the current time in millis since EPOCH
     * @param curSpeed the current speed
     * @param selfPoint current point (lat, lon)
     * @param stale the stale time to use
     * @param bConstant if the reporting rate is constant or dynamic
     * @param bUnreliable if the method of transmission is reliable or unreliable
     */
    private void updateLastReport(long currTime, double curSpeed,
            GeoPoint selfPoint, int stale,
            boolean bConstant, boolean bUnreliable) {
        if (bConstant) {
            if (bUnreliable) {
                lastTime_constantReportingRateUnreliable = currTime;
                lastStale_constantReportingRateUnreliable = stale;
                _lastReportedSpeedUnreliableMs = curSpeed;
                if (selfPoint != null)
                    _lastReportedAltitudeUnreliable = selfPoint.getAltitude();
                else
                    _lastReportedAltitudeUnreliable = GeoPoint.UNKNOWN;
            } else {
                lastTime_constantReportingRateReliable = currTime;
                lastStale_constantReportingRateReliable = stale;
                _lastReportedSpeedReliableMs = curSpeed;
                if (selfPoint != null)
                    _lastReportedAltitudeReliable = selfPoint.getAltitude();
                else
                    _lastReportedAltitudeReliable = GeoPoint.UNKNOWN;
            }
        } else {
            if (bUnreliable) {
                lastTime_dynamicReportingRateUnreliable = currTime;
                lastStale_dynamicReportingRateUnreliable = stale;
                _lastReportedSpeedUnreliableMs = curSpeed;
                if (selfPoint != null)
                    _lastReportedAltitudeUnreliable = selfPoint.getAltitude();
                else
                    _lastReportedAltitudeUnreliable = GeoPoint.UNKNOWN;
            } else {
                lastTime_dynamicReportingRateReliable = currTime;
                lastStale_dynamicReportingRateReliable = stale;
                _lastReportedSpeedReliableMs = curSpeed;
                if (selfPoint != null)
                    _lastReportedAltitudeReliable = selfPoint.getAltitude();
                else
                    _lastReportedAltitudeReliable = GeoPoint.UNKNOWN;
            }
        }
    }

    /**
     * See if it is time to report based on speed
     * if speed < MIN_SPEED
     *  use stationary reporting rate
     * ele if speed >= MAX_SPEED
     *  use maxRate reporting rate
     * otherwise
     *  scale reporting rate between minRate & maxRate reporting rate
     *      based on speed relative to [MIN_SPEED, MAX_SPEED]
     *
     * @param speed (m/s)
     * @param maxRate (millis)  maxRate report rate (between reports, fast moving, lower number)
     * @param minRate (millis)  minRate report rate (between reports, slow moving, higher number)
     * @param stationary (millis) reporting rate when not moving
     * @param currTime (millis)
     * @param lastReportTime (millis)
     * @return  Return current stale time if time to report, Otherwise return -1
     */
    private static int checkIfTimeToReport(double speed, int maxRate,
            int minRate, int stationary, long currTime, long lastReportTime,
            long lastStaleTime) {
        long delta = currTime - lastReportTime;
        //Log.d(TAG, String.format(LocaleUtil.getCurrent(),
        //        "checkIfTimeToReport speed=%f, maxRate=%d, minRate=%d, stationary=%d, currTime=%d, lastReportTime=%d, lastStaleTime=%d, delta=%d",
        //       speed, maxRate, minRate, stationary, currTime, lastReportTime, lastStaleTime, delta));

        int rate;
        if (Double.isNaN(speed) || speed < MIN_SPEED_MS) {
            //not moving
            rate = stationary;
            if (delta >= rate || (lastStaleTime - delta) <= STALE_BUFFER) {
                //Log.d(TAG, "< MIN_SPEED_MS, reporting rate= " + rate);
            } else {
                //Log.d(TAG, "< MIN_SPEED_MS, not reporting delta= " + delta);
                rate = -1;
            }
        } else if (speed >= MAX_SPEED_MS) {
            //moving over 30MPH, use maxRate
            rate = maxRate;
            if (delta >= rate || (lastStaleTime - delta) <= STALE_BUFFER) {
                //Log.d(TAG, "Moving > MAX_SPEED_MS, reporting rate= " + rate);
            } else {
                //Log.d(TAG, "Moving > MAX_SPEED_MS, not reporting delta= " + delta);
                rate = -1;
            }
        } else {
            //TODO error checking e.g. minRate > maxRate, and not equal, etc

            //normalize speed of travel and reporting rate
            //get speed range, normalized down to 1
            double s2 = MAX_SPEED_MS - MIN_SPEED_MS;
            //solve for speed scaled to range, as a percentage of the normalized range
            double scaledspeed = MIN_SPEED_MS
                    + (((speed - MIN_SPEED_MS) * 100D) / s2);

            //get rate range, normalized down to 1
            int r2 = minRate - maxRate;
            //solve for rate scaled to range, as a percentage of the normalized range
            //switch to slow rate as speed increases
            double scaledrate = minRate - ((scaledspeed * r2) / 100D);

            rate = (int) Math.round(scaledrate);
            //Log.d(TAG, String.format(LocaleUtil.getCurrent(), "Dynamic [minspeed=%f, maxspeed=%f] scaledspeed=%f, [minrange=%d, maxrange=%d] scaledrate=%f, rate=%d", s1, s2, scaledspeed, r1, r2, scaledrate, rate));
            if (delta >= rate || (lastStaleTime - delta) <= STALE_BUFFER) {
                //Log.d(TAG, "Moving, reporting rate= " + rate);
            } else {
                //Log.d(TAG, "Moving, not reporting delta= " + delta);
                rate = -1;
            }
        }

        if (rate < 0) {
            //Log.d(TAG, "Not dynamic reporting");
            return -1;
        }

        //use stale twice the rate, plus a 15 second buffer
        return rate * RELIABLE_RATE_MULTIPLIER + STALE_PADDING;
    }

    private void reportBothNow(final GeoPoint selfPoint,
            final double curSpeed) {
        long currTime = SystemClock.elapsedRealtime();

        //first report unreliable, using most current stale time
        int stale;
        if (!FileSystemUtils.isEmpty(locationReportingStrategy)
                && locationReportingStrategy.equals(constant_string)) {
            stale = lastStale_constantReportingRateUnreliable;
            updateLastReport(currTime, curSpeed, selfPoint, stale, true, true);
        } else {
            stale = lastStale_dynamicReportingRateUnreliable;
            updateLastReport(currTime, curSpeed, selfPoint, stale, false, true);
        }
        if (stale < 1) {
            //we have not yet reported, so set a default stale time
            stale = (constantReportingRateUnreliable
                    * UNRELIABLE_RATE_MULTIPLIER)
                    + STALE_PADDING;
        }

        //Log.d(TAG, "reporting now unreliable stale=" + stale);
        _callback.report(stale,
                DispatchFlags.EXTERNAL | DispatchFlags.UNRELIABLE);

        //now report reliable, using most current stale time
        if (!FileSystemUtils.isEmpty(locationReportingStrategy)
                && locationReportingStrategy.equals(constant_string)) {
            stale = lastStale_constantReportingRateReliable;
            updateLastReport(currTime, curSpeed, selfPoint, stale, true, false);
        } else {
            stale = lastStale_dynamicReportingRateReliable;
            updateLastReport(currTime, curSpeed, selfPoint, stale, false,
                    false);
        }
        if (stale < 1) {
            //we have not yet reported, so set a default stale time
            stale = (constantReportingRateReliable * RELIABLE_RATE_MULTIPLIER)
                    + STALE_PADDING;
        }

        //Log.d(TAG, "reporting now reliable stale=" + stale);
        _callback.report(stale, DispatchFlags.EXTERNAL
                | DispatchFlags.RELIABLE);
    }

}
