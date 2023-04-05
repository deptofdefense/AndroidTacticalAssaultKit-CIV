
package com.atakmap.android.location;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.GpsStatus.NmeaListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.location.OnNmeaMessageListener;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import com.atakmap.android.gui.HintDialogHelper;
import com.atakmap.android.icons.Icon2525cIconAdapter;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.Ellipse;
import com.atakmap.android.maps.MapData;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapMode;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.Marker.OnTrackChangedListener;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.PointMapItem.OnPointChangedListener;
import com.atakmap.android.navigation.views.NavView;
import com.atakmap.android.selfcoordoverlay.SelfCoordOverlayUpdater;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.Permissions;
import com.atakmap.app.R;
import com.atakmap.comms.ReportingRate;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.map.AtakMapController;
import com.atakmap.map.CameraController;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.elevation.ElevationManager;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Provides a location Marker and various device information.
 * <p>
 * Provided <i>Global Map Data</i>:
 * <ul>
 * <li>{@link java.lang.String String} <b>deviceType</b> - from preferences (default: "a-f-G-U-C-I")
 * </li>
 * <li>{@link java.lang.String String} <b>deviceCallsign</b> - from preferences (default: "ATAK")</li>
 * <li>{@link java.lang.String String} <b>devicePhoneNumber</b> - requires
 * android.permission.READ_PHONE_STATE</li>
 * <li>{@link com.atakmap.coremap.maps.coords.GeoPoint GeoPoint} <b>fineLocation</b> - requires
 * android.permission.ACCESS_FINE_LOCATION and enabled in preferences</li>
 * <li>{@code double} <b>fineLocationBearing</b> - requires android.permission.ACCESS_FINE_LOCATION
 * and enabled in preferences</li>
 * <li>{@code double} <b>fineLocationSpeed</b> - requires android.permission.ACCESS_FINE_LOCATION
 * and enabled in preferences</li>
 * <li>{@code double} <b>deviceAzimuth</b> - modifications to deviceAzimuth outside of 
 * LocationMapComponent will not be used by LocationMapComponent since this is just for read only 
 * purposes.    This bundle variable mirrors the local field mapDataDeviceAzimuth.
 * </ul>
 * </p>
 * <p>
 * </p>
 *
 */

public class LocationMapComponent extends AbstractMapComponent implements
        SensorEventListener, NmeaListener, LocationListener,
        SharedPreferences.OnSharedPreferenceChangeListener, GpsStatus.Listener {

    final static String UID_PREFIX = "ANDROID-";

    private final float[] Rbuf = new float[9];
    private final float[] values = new float[3];

    final private static double PRECISION_7 = 10000000;
    final private static double PRECISION_6 = 1000000;
    final private static double PRECISION_4 = 1000;

    final private static int SELF_MARKER_WIDTH = 32;
    final private static int SELF_MARKER_HEIGHT = 43;

    private String _deviceCallsign;
    private String _deviceType;
    private String _deviceTeam;
    private float[] _gravityMatrix;// = new float[9];
    private float[] _geoMagnetic;// = new float[3];
    private SensorManager _sensorMgr;
    private Sensor accelSensor = null;
    private Sensor magSensor = null;
    private double _orientationOffset = 0d;
    private Marker _locationMarker;
    private MapGroup _locationGroup;

    private boolean useOnlyGPSBearing = false;

    private MapView _mapView;
    private Context context;
    private final AlphaBetaFilter _filteredHeading = new AlphaBetaFilter(
            _FILTER_ALPHA, _FILTER_BETA);
    private long _lastHeadingUpdate = 0;
    private long _lastMarkerRefresh = 0;

    private double _lastMeasuredHeading = 0;
    private boolean lastMeasurementInvalid = false;

    private Ellipse _accuracyEllipse;
    private String _lastPrefix = "fine";
    private LocationManager locMgr;

    private Timer _headingRefreshTimer;

    private static final int gpsUpdateInterval = 1000;
    private static final double MILLIS_IN_SECOND = 1000d;

    private static final double VALID_GPS_BEARING_SPEED = 0.44704; // 1 mph

    private static final double _FILTER_GRANULARITY = 1d / 30d;
    private static final double _FILTER_ALPHA = .1d; // .1 or .2 seems to work
    // well here
    private static final double _FILTER_BETA = 0.005d; // 0.005 or other very
    // low values (maybe
    // .01) seem to work
    // well here.

    // This is the rate that the self marker will visually update when no 
    // other actions are occuring.
    private static final int REFRESH_RATE = 1000;

    // This is the rate that the self location Marker will update its 
    // heading.
    private static final int HEADING_REFRESH_RATE = 150;

    // Just in case additional updates come in for the Markers track change
    // ignore ones that come faster.
    private static final int HEADING_REFRESH_MIN_ELAPSE_MS = 100;

    public static final int GPS_TIMEOUT_MILLIS = 10000; // 10 sec

    private SpeedComputer avgSpeed;

    private final static String LOCATION_INIT = "com.atakmap.android.location.LOCATION_INIT";
    private SharedPreferences locationPrefs;

    private static final String TAG = "LocationMapComponent";

    // value in microseconds, different phones define SENSOR_DELAY_GAME
    // differently, some as high as 100hz. We should only need 60hz.
    private static final int SENSOR_RATE = 60000;

    // private static long SENSOR_RATE = SensorManager.SENSOR_DELAY_GAME;
    private BroadcastReceiver selflocrec;
    private BroadcastReceiver snapselfrec;

    private boolean _gpserrorenabled = true;
    private boolean recvInitialTimestamp = false;

    private OnNmeaMessageListener newerNmeaListener;

    // used only in conjunction with the LocationListener and only guaranteed to be valid
    // if the Location.hasAltitude() is true.
    private double nmeaMSLAltitude = Double.NaN;

    // matches the default set by the xml
    private boolean useGPSTime = true;

    private Display display;

    private double mapDataDeviceAzimuth;

    class SpeedComputer {
        // speed for switching from compass to GPS bearing
        private final static long VALID_TIME = 5 * 60000; // 5 * 1min

        private final static double GPS_BEARING_SPEED = 0.44704 * 5; // 1.0mph * 5 

        private final double[] vlist;
        int idx = 0;
        boolean reset;
        long lastHighSpeed = SystemClock.elapsedRealtime() - VALID_TIME;
        boolean using = false;

        SpeedComputer(final int size) {
            vlist = new double[size];
        }

        private void queue(double v) {
            if (idx == 0)
                idx = vlist.length;
            --idx;
            vlist[idx] = v;
        }

        public void add(double v) {
            reset = false;
            queue(v);

            if (!Double.isNaN(v)) {
                if (getAverageSpeed() > GPS_BEARING_SPEED) {
                    lastHighSpeed = SystemClock.elapsedRealtime();
                } else {
                    if (v >= GPS_BEARING_SPEED) { // on the move
                        //Log.d(TAG, "on the move (external)" + instantSpeed +
                        //           " averagespeed = " + avgSpeed.getSpeed());

                        // do not call reset because that will unset the driving
                        // flag and cause the cam lock to screw up.
                        Arrays.fill(vlist, Double.NaN);
                        queue(v);
                        // dismiss the driving widget
                        setDrivingWidgetVisible(false);
                    }

                }
            }
        }

        public void reset() {
            if (reset)
                return;

            Arrays.fill(vlist, Double.NaN);

            lastHighSpeed = SystemClock.elapsedRealtime() - VALID_TIME;
            _locationMarker.setMetaBoolean("driving", false);
            setDrivingWidgetVisible(false);
            using = false;

            reset = true;
        }

        boolean useGPSBearing() {

            // if the system is configured only to respond to the GPS bearing then 
            // only use GPS bearing.
            if (useOnlyGPSBearing)
                return true;

            if (SystemClock.elapsedRealtime() - lastHighSpeed < 0) {
                lastHighSpeed = SystemClock.elapsedRealtime() - VALID_TIME;
                Log.d(TAG,
                        "non-monotonic elapsedRealtime encountered, correcting");
            }

            boolean use = (SystemClock.elapsedRealtime()
                    - lastHighSpeed) < VALID_TIME;
            //Log.d(TAG, "use=" + use + " using=" + using + "driving=" + _locationMarker.getMetaBoolean("driving", false));
            if (getMapMode() != MapMode.MAGNETIC_UP) {
                if (use != using) {
                    if (!use)
                        setDrivingWidgetVisible(false);

                    _locationMarker.setMetaBoolean("driving", use);

                    using = use;
                }
                if (use && getAverageSpeed() < GPS_BEARING_SPEED) {
                    setDrivingWidgetVisible(true);
                }
            }
            return use;
        }

        double getAverageSpeed() {

            if (reset)
                return 0.0;

            double avgV = 0.0;
            int count = 0;

            for (double aVlist : vlist) {
                if (!Double.isNaN(aVlist)) {
                    avgV = aVlist + avgV;
                    count++;
                }
            }
            if (count == 0)
                return 0.0;
            else
                return avgV / count;
        }
    }

    private int currAccuracy;

    private String acc2String(int accuracy) {
        if (accuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
            return "high accuracy";
        else if (accuracy == SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM)
            return "medium accuracy";
        else if (accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW)
            return "low accuracy";
        else
            return "invalid";

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (accuracy != currAccuracy)
            Log.d(TAG, "magnetic sensor accuracy changed: "
                    + acc2String(accuracy));
        currAccuracy = accuracy;

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        try {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                if (_gravityMatrix == null) {
                    _gravityMatrix = new float[3];
                }
                System.arraycopy(event.values, 0, _gravityMatrix, 0,
                        event.values.length);
            } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                if (_geoMagnetic == null) {
                    _geoMagnetic = new float[3];
                }
                System.arraycopy(event.values, 0, _geoMagnetic, 0,
                        event.values.length);
            }

            if (_gravityMatrix != null && _geoMagnetic != null) {

                SensorManager.getRotationMatrix(Rbuf, null, _gravityMatrix,
                        _geoMagnetic);
                SensorManager.getOrientation(Rbuf, values);

                // the orientation offset remains constant through the lifespan of ATAK.
                // do not recheck as the inner call to display.getRotation() consumes a fair
                // amount of CPU resources.
                // _updateOrientationOffset();
                double magAzimuth = _orientationOffset
                        + (180d * values[0] / Math.PI);

                while (magAzimuth < 0) {
                    magAzimuth += 360d;
                }
                magAzimuth %= 360d;

                double deltaAzimuth = (magAzimuth - mapDataDeviceAzimuth);
                if (Math.abs(deltaAzimuth) > 3) {
                    mapDataDeviceAzimuth = magAzimuth;
                    _mapView.getMapData().putDouble("deviceAzimuth",
                            mapDataDeviceAzimuth);
                    _locationMarker.setMetaDouble("deviceAzimuth",
                            mapDataDeviceAzimuth);

                    /*
                     * we only want the orientation set via the sensor when the system is not in
                     * trackup mode, or if it is in track up mode, then only set via the sensor
                     * when the speed of the device is below the GPS threshold. This means that
                     * there are a few different states that the system can be in in which we
                     * want to update the orientation. Also, if we have been unable to retrieve
                     * a position either from mocking or from the local GPS, we want to set the
                     * orientation via sensor. Finally, we will want to make sure that the
                     * orientation is set via sensor if we get a fix and then loose it.
                     */

                    if (!avgSpeed.useGPSBearing()
                            || getMapMode() == MapMode.MAGNETIC_UP) {
                        final double trueAzimuth = ATAKUtilities
                                .convertFromMagneticToTrue(
                                        _locationMarker.getPoint(),
                                        magAzimuth);
                        _updateHeading(trueAzimuth);

                        _locationMarker.setTrack(
                                _filteredHeading.getEstimate(), 0d);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "error: ", e);
        }

    }

    private final Pattern comma = Pattern.compile(","); // thread safe

    @Override
    public void onNmeaReceived(final long timestamp, final String nmea) {

        // try the three different variations of the GGA message
        if (nmea == null)
            return;

        if (nmea.startsWith("$GPGGA") ||
                nmea.startsWith("$GNGGA") ||
                nmea.startsWith("$GLGGA")) {

            String[] parts = comma.split(nmea, 0);
            if ((parts.length > 9) && (parts[9].length() > 0)) {
                try {
                    nmeaMSLAltitude = Double.parseDouble(parts[9]);
                    // carry out to 4 digits
                    nmeaMSLAltitude = Math.round(nmeaMSLAltitude
                            * PRECISION_4)
                            / PRECISION_4;
                } catch (NumberFormatException nfe) {
                    // unable to parse the double
                }
            }
        }
    }

    @SuppressLint({
            "MissingPermission"
    })
    @Override
    public void onGpsStatusChanged(int status) {
        try {
            if (locMgr != null &&
                    Permissions.checkPermission(context,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION)) {
                GpsStatus gpsStatus = locMgr.getGpsStatus(null);
                if (gpsStatus != null) {
                    Iterable<GpsSatellite> satellites = gpsStatus
                            .getSatellites();
                    Iterator<GpsSatellite> sat = satellites.iterator();
                    //String lSatellites = null;
                    //int i = 0;

                    int satellitesInFix = 0;
                    while (sat.hasNext()) {
                        GpsSatellite satellite = sat.next();

                        if (satellite.usedInFix()) {
                            satellitesInFix++;
                        }
                        //lSatellites = "Satellite" + (i++) + ": " 
                        //     + satellite.getPrn() + "," 
                        //     + satellite.usedInFix() + "," 
                        //     + satellite.getSnr() + "," 
                        //     + satellite.getAzimuth() + "," 
                        //     + satellite.getElevation()+ "\n\n";

                        //Log.d(TAG,lSatellites);
                    }
                    //Log.d(TAG, "sats in fix: " + satellitesInFix);
                }
            }
        } catch (Exception ioe) {
            Log.d(TAG, "error occurred", ioe);
        }
    }

    @Override
    public void onLocationChanged(final Location loc) {
        try {
            // ATAK-9978 ATAK sent event with invalid location (NaN)
            // in the rare case that latitude or longitude
            // is Double.NaN - do not fire a update to the 
            // location.
            if (Double.isNaN(loc.getLatitude()) ||
                    Double.isNaN(loc.getLongitude())) {
                return;
            }

            _updateLocation(loc);
        } catch (Exception e) {
            Log.d(TAG, "error updating the location", e);
        }
    }

    @Override
    public void onProviderDisabled(final String provider) {
        _mapView.getMapData().putBoolean("fineLocationAvailable", false);
        Log.d(TAG, "internal GPS disabled");
        _reportNoGps();

    }

    @Override
    public void onProviderEnabled(final String provider) {
        Log.d(TAG, "internal GPS enabled");
    }

    @Override
    public void onStatusChanged(final String provider,
            final int status,
            final Bundle extras) {
        if (status == LocationProvider.OUT_OF_SERVICE) {
            Log.d(TAG, "internal GPS out of service");
            _mapView.getMapData()
                    .putBoolean("fineLocationAvailable", false);
            _reportNoGps();
        } else if (status == LocationProvider.TEMPORARILY_UNAVAILABLE) {
            Log.d(TAG, "internal GPS temporarily unavailable");
            _mapView.getMapData()
                    .putBoolean("fineLocationAvailable", false);
            _reportNoGps();
        } else {// LocationProvider.AVAILABLE

            // does not seem to be called when the program starts up and 
            // the GPS fix is already present but stops receiving information
            // although this is good, a further fix was needed to be added 
            // to the CotMapComponent to check for GPS expire.

            Log.d(TAG, "internal GPS Connected");
            if (_locationMarker != null
                    &&
                    SystemClock.elapsedRealtime()
                            - _locationMarker.getMetaLong("gpsUpdateTick",
                                    0) > LocationMapComponent.GPS_TIMEOUT_MILLIS) {
                _mapView.getMapData().putBoolean("fineLocationAvailable",
                        false);
                _reportNoGps();
            } else {
                _mapView.getMapData().putBoolean("fineLocationAvailable",
                        true);
            }
        }
    }

    // cached version of the callsign produced by a call to callsignGen(Context)
    private static String cachedCallsign = null;

    /**
     * Produce a callsign based on a file that is included with the distribution ATAK that is
     * selected using the an algorithm seeded with the best determined device uid.
     */
    public static String callsignGen(Context ctx) {

        if (cachedCallsign == null) {
            // just to avoid reading the whole file first...
            final int NCALLSIGNS = 1298;

            String str = _determineBestDeviceUID(ctx);

            // Log.v(TAG,
            // "Generated Hash Code: " + str.hashCode());

            // On several lab devices I have observed the hashCode
            // spit back from the string is a negative number.
            // For all of these devices, the callsign is set to
            // the first entry.

            int index = Math.abs(str.hashCode()) % NCALLSIGNS;

            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                ctx.getAssets().open("callsigns.txt")));

                String line;
                try {
                    line = reader.readLine();
                    int i = 0;
                    while (line != null && i++ < index) {
                        line = reader.readLine();
                    }
                } finally {
                    reader.close();
                }
                cachedCallsign = line;
            } catch (Exception e) {
                cachedCallsign = "ERROR";
                Log.e(TAG, "error: ", e);
            }
        }
        return cachedCallsign;
    }

    private void setDrivingWidgetVisible(final boolean visible) {
        if (visible) {
            NavView.getInstance().setGPSLockAction(new Runnable() {
                @Override
                public void run() {
                    avgSpeed.reset();
                }
            });
        } else {
            NavView.getInstance().setGPSLockAction(null);
        }
    }

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        _mapView = view;
        this.context = context;

        mapDataDeviceAzimuth = _mapView.getMapData().getDouble("deviceAzimuth",
                0);

        if (!view.getMapData().containsKey("locationSourcePrefix")) {
            view.getMapData().putString("locationSourcePrefix", "fine");
        }

        WindowManager _winManager = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        if (_winManager != null)
            display = _winManager.getDefaultDisplay();

        avgSpeed = new SpeedComputer(30);

        // listen for preference changes
        locationPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!locationPrefs.contains("locationTeam"))
            locationPrefs.edit().putString("locationTeam", "Cyan").apply();

        locationPrefs
                .registerOnSharedPreferenceChangeListener(this);

        useGPSTime = locationPrefs.getBoolean("useGPSTime", useGPSTime);
        useOnlyGPSBearing = locationPrefs.getBoolean("useOnlyGPSBearing",
                false);

        // After user specifies a self location when there's no GPS, update the
        // overlay
        DocumentedIntentFilter selfLocationSpecifiedFilter = new DocumentedIntentFilter();
        selfLocationSpecifiedFilter
                .addAction("com.atakmap.android.map.SELF_LOCATION_SPECIFIED");
        AtakBroadcast.getInstance().registerReceiver(
                selflocrec = new BroadcastReceiver() {

                    @Override
                    public void onReceive(Context context, Intent intent) {
                        _mapView.post(updateRefreshLocationMarker);
                        _reportGpsBack();
                        _reportNoGps();
                    }
                }, selfLocationSpecifiedFilter);

        DocumentedIntentFilter snapToSelfLocationSpecifiedFilter = new DocumentedIntentFilter();
        snapToSelfLocationSpecifiedFilter
                .addAction("com.atakmap.android.maps.SNAP_TO_SELF");
        AtakBroadcast.getInstance().registerReceiver(
                snapselfrec = new BroadcastReceiver() {

                    @Override
                    public void onReceive(Context context, Intent intent) {
                        Marker placedSelf = ATAKUtilities.findSelf(_mapView);
                        if (placedSelf == null || placedSelf.getPoint() == null
                                || !placedSelf.getPoint().isValid()) {
                            Log.w(TAG,
                                    "Cannot snap to self w/out self location");
                            return;
                        }

                        String uid = intent.getStringExtra("uid");
                        if (FileSystemUtils.isEmpty(uid)) {
                            Log.w(TAG, "Cannot snap to self w/out UID");
                            return;
                        }

                        MapItem item = _mapView.getRootGroup().deepFindUID(uid);
                        if (!(item instanceof PointMapItem)) {
                            Log.w(TAG,
                                    "Cannot snap to self w/out point marker");
                            return;
                        }

                        Log.d(TAG, "Snapping to self location: " + uid);
                        ((PointMapItem) item).setPoint(placedSelf.getPoint());
                        item.refresh(_mapView.getMapEventDispatcher(), null,
                                this.getClass());
                        item.persist(_mapView.getMapEventDispatcher(), null,
                                this.getClass());

                        //now zoom map to self/new location
                        Intent zoomIntent = new Intent(
                                "com.atakmap.android.maps.FOCUS");
                        zoomIntent.putExtra("uid", uid);
                        zoomIntent.putExtra("useTightZoom", true);
                        AtakBroadcast.getInstance().sendBroadcast(zoomIntent);
                    }
                }, snapToSelfLocationSpecifiedFilter);

        MapData mapData = view.getMapData();

        String deviceLine1Number = _fetchTelephonyLine1Number(context);
        if (deviceLine1Number != null) {
            mapData.putString("devicePhoneNumber", deviceLine1Number);
        }

        _updateContactPreferences(mapData, locationPrefs);

        // Set the locationCallsign to be something better than ATAK on a new
        // device. At the end of the
        // day alot of our devices are set to ATAK as the callsign which makes
        // mass loading a pain.
        _deviceCallsign = locationPrefs.getString("locationCallsign", "");
        if (_deviceCallsign.length() == 0) {
            _deviceCallsign = callsignGen(context);
            Log.d(TAG, "making new callsign:" + _deviceCallsign);
            locationPrefs.edit().putString("locationCallsign", _deviceCallsign)
                    .apply();
        }

        mapData.putString("deviceCallsign", _deviceCallsign);

        if (!(locationPrefs.getString("mockingOption", "WRGPS")
                .equals("IgnoreInternalGPS"))) {
            _startLocationGathering(context, gpsUpdateInterval);
        }

        _startOrientationGathering(context);
        _headingRefreshTimer = new Timer("LocationRefreshTimer");

        createLocationMarker();
        _mapView.setSelfMarker(_locationMarker);
        _mapView.getMapData().putBoolean("fakeLocationAvailable", true);

        _updateOrientationOffset();

        // very last thing to do
        _headingRefreshTimer.schedule(refreshTask, 0, HEADING_REFRESH_RATE);

        DeadReckoningManager.getInstance();

    }

    final TimerTask refreshTask = new TimerTask() {

        @Override
        public void run() {
            if (_locationMarker != null) {
                long startTime = SystemClock.elapsedRealtime();

                long deltaHeading = SystemClock.elapsedRealtime()
                        - _lastHeadingUpdate;

                // In case we stop getting compass updates, and the estimate
                // hasn't converged with the measured yet, schedule a filter
                // update
                // This can happen if the device stops rotating; the compass
                // will only send updates every half second then, which is quite
                // jerky.
                // TODO: Ensure this timer doesn't have performance
                // implications? Tests out ok on pretty heavily loaded devices
                // though. -ts
                if (deltaHeading > HEADING_REFRESH_RATE
                        && Math.abs(_lastMeasuredHeading
                                - _filteredHeading.getEstimate()) > 2) {

                    _updateHeading(_lastMeasuredHeading);

                    _locationMarker
                            .setTrack(_filteredHeading.getEstimate(), 0d);
                }

                long deltaRefresh = SystemClock.elapsedRealtime()
                        - _lastMarkerRefresh;
                if (deltaRefresh > REFRESH_RATE) {
                    _lastMarkerRefresh = SystemClock.elapsedRealtime();

                    _mapView.post(updateRefreshLocationMarker);

                    long end = (SystemClock.elapsedRealtime() - startTime);
                    if (end > 100)
                        Log.d(TAG,
                                "warning: refresh took longer than expected ("
                                        + (SystemClock.elapsedRealtime()
                                                - startTime)
                                        + ")");
                }

            }

        }
    };

    private final Runnable updateRefreshLocationMarker = new Runnable() {
        @Override
        public void run() {

            // The location manager has been disposed, just return
            if (_headingRefreshTimer == null)
                return;

            String prefix = _getUpdatedPrefix();

            _updateLocationMarker(prefix);
        }
    };

    /**
     * Returns the current effective location source prefix. If the location prefix in
     * locationSourcePrefix is online, it returns that, otherwise if the internal GPS is online, it
     * returns fine, and if that is not the case then it returns the fake location source.
     *
     * @return the updated prefix
     */
    private String _getUpdatedPrefix() {
        final MapData mapData = _mapView.getMapData();

        String prefix = mapData.getString("locationSourcePrefix");

        // Default to fine if no current prefix
        if (prefix == null || prefix.equals("")) {
            prefix = "fine";

            _mapView.getMapData().putString("locationSourcePrefix", prefix);
        }

        String effectivePrefix = prefix;
        GeoPoint point = mapData.getParcelable(effectivePrefix + "Location");

        // All *LocationTime is used for to determine when the last GPS pump occurred.
        // should be based on SystemClock which is not prone to error by setting the
        // System Date/Time.   It is not indicative of any coordinated time, just used
        // for measuring time deltas.
        //
        // Fall back to fine if current prefix is not available or not active within last 
        // GPS_TIMEOUT_MILLIS

        if (!prefix.equals("fine")
                && (!mapData.getBoolean(effectivePrefix + "LocationAvailable")
                        || _checkNoGps(point)
                        || (SystemClock.elapsedRealtime() - _mapView
                                .getMapData().getLong(effectivePrefix
                                        + "LocationTime")) > GPS_TIMEOUT_MILLIS)) {
            effectivePrefix = "fine";
            mapData.remove(prefix + "LocationAvailable");
            point = mapData.getParcelable("fineLocation");

        }

        if (!mapData.getBoolean(effectivePrefix + "LocationAvailable")
                || _checkNoGps(point)
                || (SystemClock.elapsedRealtime() - _mapView.getMapData()
                        .getLong(
                                effectivePrefix
                                        + "LocationTime")) > GPS_TIMEOUT_MILLIS) {

            effectivePrefix = "fake";
            GeoPoint lastPrefixPoint = mapData.getParcelable(_lastPrefix
                    + "Location");
            String altitudeSource = mapData
                    .getString(_lastPrefix + "LocationAltSrc", "???");
            String geoLocationSource = mapData
                    .getString(_lastPrefix + "LocationSrc", "???");

            // Initialize the fake location prefix with the location of the last
            // prefix we were using with also the last source (alt, geo)
            if (!_lastPrefix.equals("fake") && lastPrefixPoint != null) {
                mapData.putBoolean("fakeLocationAvailable", true);
                mapData.putParcelable("fakeLocation", lastPrefixPoint);
                mapData.putLong("fakeLocationTime", 0);
                mapData.putDouble("fakeLocationSpeed", Double.NaN);
                mapData.putString("fakeLocationAltSrc", altitudeSource);
                mapData.putString("fakeLocationSrc", geoLocationSource);
            }
        }

        _lastPrefix = effectivePrefix;
        mapData.putString("locationSourceEffectivePrefix", effectivePrefix);
        return effectivePrefix;
    }

    /**
     * Determines the usable UID for the app.   This method is used to retrieve an
     * appropriate fingerprint.   As of 4.0, this will no longer attempt to use the IMEI as a
     * a possible identifier
     * @param context is the context for the app.
     * @return null if the fingerprint could not be identified based on the device.
     */
    synchronized public static String _determineDeviceUID(
            final Context context) {
        String suffix = null;
        String possibleSuffix = _fetchSerialNumber(context);
        //Log.v(TAG, "Checking (serialNumber): " + possibleSuffix);
        if (possibleSuffix != null) {
            suffix = possibleSuffix;
        }

        possibleSuffix = _fetchWifiMacAddress(context);
        if (possibleSuffix != null) {
            // Potentially broken UID generation on a Android 6 device
            // see bug https://atakmap.com/bugz/show_bug.cgi?id=5178
            if (!possibleSuffix.endsWith("00:00:00:00:00"))
                suffix = possibleSuffix;
        }
        return suffix;
    }

    /**
     * On the first run, determines the best possible device uid and persists it for the lifetime of
     * the saved preferences.   This method is also used by the AtakCertificateDatabase.setDeviceId and 
     * AtakAuthenticationDatabase.setDeviceId.   Any changes to this value will disrupt both 
     * device / tak server history as well as the loss of keys in the system.
     *
     * UID will contain 2 parts:
     *  Hard coded prefix: ANDROID-
     *  Device specific suffix: preferred in this order
     *      WiFi MAC Address
     *      Telephony Device ID
     *      Serial Number
     *      Random UUID
     */
    synchronized public static String _determineBestDeviceUID(Context context) {

        String bestDeviceUID;
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        if (prefs.contains("bestDeviceUID")) {
            // the devices best possible uid has been determined during a previous run.
            // the panasonic toughpad generates a different bestDeviceUID after each
            // power cycle.
            bestDeviceUID = prefs.getString("bestDeviceUID", null);
            // check to see if it is not null and not the empty string

            if ((bestDeviceUID != null) && (bestDeviceUID.trim().length() > 0)
                    && !bestDeviceUID.endsWith("00:00:00:00:00"))
                return bestDeviceUID;
        }

        String suffix = UUID.randomUUID().toString();

        String possibleSuffix = _fetchSerialNumber(context);
        Log.v(TAG, "Checking (serialNumber): " + possibleSuffix);
        if (possibleSuffix != null) {
            suffix = possibleSuffix;
        }

        possibleSuffix = _fetchTelephonyDeviceId(context);
        Log.v(TAG, "Checking (telephonyDeviceId): " + possibleSuffix);
        if (possibleSuffix != null) {
            suffix = possibleSuffix;
        }

        possibleSuffix = _fetchWifiMacAddress(context);
        Log.v(TAG, "Checking (WifiMacAddress): " + possibleSuffix);
        if (possibleSuffix != null) {
            // Potentially broken UID generation on a Android 6 device
            // see bug https://atakmap.com/bugz/show_bug.cgi?id=5178
            if (!possibleSuffix.endsWith("00:00:00:00:00"))
                suffix = possibleSuffix;
        }

        bestDeviceUID = UID_PREFIX + suffix;

        prefs.edit().putString("bestDeviceUID", bestDeviceUID)
                .apply();

        return bestDeviceUID;

    }

    synchronized private void _startOrientationGathering(Context context) {
        if (_sensorMgr == null) {
            _sensorMgr = (SensorManager) context
                    .getSystemService(Context.SENSOR_SERVICE);

            if (_sensorMgr == null) {
                HintDialogHelper
                        .showHint(
                                context,
                                context.getString(R.string.tool_text34),
                                context.getString(R.string.tool_text35),
                                "device.accelerometer.issue");
                return;
            }
            accelSensor = _sensorMgr
                    .getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magSensor = _sensorMgr
                    .getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            boolean accPresent = _sensorMgr.registerListener(
                    this, accelSensor,
                    SENSOR_RATE);
            boolean magPresent = _sensorMgr.registerListener(
                    this, magSensor,
                    SENSOR_RATE);

            if (!accPresent) {
                HintDialogHelper
                        .showHint(
                                context,
                                context.getString(R.string.tool_text34),
                                context.getString(R.string.tool_text35),
                                "device.accelerometer.issue");
            }

            if (!magPresent) {
                HintDialogHelper
                        .showHint(
                                context,
                                context.getString(R.string.tool_text36),
                                context.getString(R.string.tool_text37),
                                "device.compass.issue");

            }
        }
    }

    private void _updateOrientationOffset() {
        try {
            int rotation = (display != null) ? display.getRotation()
                    : Surface.ROTATION_0;
            switch (rotation) {
                case Surface.ROTATION_0:
                    _orientationOffset = 0d;
                    break;
                case Surface.ROTATION_90:
                    _orientationOffset = 90d;
                    break;
                case Surface.ROTATION_180:
                    _orientationOffset = 180d;
                    break;
                case Surface.ROTATION_270:
                    _orientationOffset = 270d;
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG,
                    "Error has occurred getting the window and rotation, setting 0",
                    e);
            _orientationOffset = 0d;
        }

        Log.d(TAG, "orientation changed requested: " + _orientationOffset);

    }

    /**
     * Modifies the resolution of the GPS sampling.
     */
    @SuppressLint({
            "MissingPermission"
    })
    synchronized private void modifyGPSRequestInterval(int newInterval) {
        if (locMgr != null &&
                Permissions.checkPermission(context,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION)) {
            locMgr.removeUpdates(this);
            locMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    newInterval, 0f, this);

        }
    }

    @SuppressLint({
            "MissingPermission"
    })
    synchronized private void _startLocationGathering(final Context context,
            final long interval) {

        // only try to start the location gathering if we haven't
        // started already

        if (locMgr == null &&
                Permissions.checkPermission(context,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION)) {

            try {
                locMgr = (LocationManager) context
                        .getSystemService(Service.LOCATION_SERVICE);

                if (locMgr != null) {
                    final boolean gpsEnabled = locMgr
                            .isProviderEnabled(LocationManager.GPS_PROVIDER);
                    Log.d(TAG, "gps detected correct mode: " + gpsEnabled);

                    if (!gpsEnabled) {
                        HintDialogHelper
                                .showHint(
                                        context,
                                        context.getString(
                                                R.string.location_mode_title),
                                        context.getString(
                                                R.string.location_mode_desc),
                                        "gps.device.mode",
                                        new HintDialogHelper.HintActions() {
                                            @Override
                                            public void preHint() {

                                            }

                                            @Override
                                            public void postHint() {
                                                try {
                                                    final Intent intent = new Intent(
                                                            Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                                    _mapView.getContext()
                                                            .startActivity(
                                                                    intent);
                                                } catch (ActivityNotFoundException ane) {
                                                    Log.d(TAG,
                                                            "no Settings.ACTION_LOCATION_SOURCE_SETTINGS activity found on this device");
                                                }
                                            }
                                        }, false);
                    }
                }

                if (locMgr != null) {
                    locMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                            interval, 0f, this);
                    locMgr.addGpsStatusListener(this);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        locMgr.addNmeaListener(
                                newerNmeaListener = new OnNmeaMessageListener() {
                                    @Override
                                    public void onNmeaMessage(String message,
                                            long timestamp) {
                                        onNmeaReceived(timestamp, message);
                                    }
                                });
                    } else {
                        addNmeaListener(locMgr, this);
                    }
                } else {
                    throw new IllegalArgumentException("no location manager");
                }
            } catch (IllegalArgumentException iae) {
                Log.d(TAG,
                        "location updates are not available on this device: ",
                        iae);
                HintDialogHelper
                        .showHint(
                                context,
                                context.getString(R.string.tool_text38),
                                context.getString(R.string.tool_text39),
                                "device.gps.issue");
            }
            _mapView.getMapData().putBoolean("fineLocationAvailable", false);
        }
    }

    @SuppressLint({
            "MissingPermission"
    })
    synchronized private void _stopLocationGathering() {
        if (locMgr != null) {
            locMgr.removeUpdates(this);
            _removeLocationMapData(_mapView.getMapData());
            removeNmeaListener(locMgr, this);

            if (Build.VERSION.SDK_INT >= 24) {
                try {
                    if (newerNmeaListener != null)
                        locMgr.removeNmeaListener(newerNmeaListener);
                } catch (Exception e) {
                    Log.d(TAG, "error removing the newer nmea listener");
                }
                locMgr = null;
            }
        }
    }

    /**
     * Reflective calling of addNmeaLIstener(GpsStatus.NmeaListener) for compilation using Android 29
     * but for systems that need it (Android 21, 22, 23).
     * @param locMgr the location manager to use
     * @param listener the listener to register
     */
    private void addNmeaListener(final LocationManager locMgr,
            final GpsStatus.NmeaListener listener) {
        Log.d(TAG, "adding the GpsStatus.NmeaListener listener "
                + listener.getClass());
        try {
            final Class<?> c = locMgr.getClass();
            Method addNmeaListener = c.getMethod("addNmeaListener",
                    GpsStatus.NmeaListener.class);
            if (addNmeaListener != null)
                addNmeaListener.invoke(locMgr, listener);
            else
                Log.e(TAG,
                        "error occurred trying to find the addNmeaListener method");
        } catch (Exception e) {
            Log.e(TAG,
                    "error occurred trying to reflectively add GpsStatus.NmeaListener",
                    e);
        }
    }

    /**
     * Reflective calling of addNmeaLIstener(GpsStatus.NmeaListener) for compilation using Android 29
     * but for systems that need it (Android 21, 22, 23).
     * @param locMgr the location manager to use
     * @param listener the listener to register
     */
    private void removeNmeaListener(final LocationManager locMgr,
            final GpsStatus.NmeaListener listener) {
        Log.d(TAG, "removing the GpsStatus.NmeaListener listener "
                + listener.getClass());
        try {
            final Class<?> c = locMgr.getClass();
            Method removeNmeaListener = c.getMethod("removeNmeaListener",
                    GpsStatus.NmeaListener.class);
            if (removeNmeaListener != null)
                removeNmeaListener.invoke(locMgr, listener);
            else
                Log.e(TAG,
                        "error occurred trying to find the removeNmeaListener method");
        } catch (Exception e) {
            Log.e(TAG,
                    "error occurred trying to reflectively remove GpsStatus.NmeaListener",
                    e);
        }
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {

        if (_headingRefreshTimer != null) {
            _headingRefreshTimer.cancel();
            _headingRefreshTimer.purge();
            _headingRefreshTimer = null;
        }

        try {
            _sensorMgr.unregisterListener(this, accelSensor);
        } catch (Exception e) {
            Log.e(TAG, "error: ", e);
            // not sure if it is registered yet, but cannot check
        }

        try {
            _sensorMgr.unregisterListener(this, magSensor);
        } catch (Exception e) {
            Log.e(TAG, "error: ", e);
            // not sure if it is registered yet, but cannot check
        }
        try {
            _sensorMgr.unregisterListener(this);
        } catch (Exception e) {
            Log.e(TAG, "error: ", e);
            // not sure if it is registered yet, but cannot check
        }

        _sensorMgr = null;

        _stopLocationGathering();

        if (selflocrec != null) {
            AtakBroadcast.getInstance().unregisterReceiver(selflocrec);
        }
        if (snapselfrec != null) {
            AtakBroadcast.getInstance().unregisterReceiver(snapselfrec);
        }

        locationPrefs
                .unregisterOnSharedPreferenceChangeListener(this);

        // cant unregister something that has not been registered
        try {
            AtakBroadcast.getInstance().unregisterReceiver(_receiver);
        } catch (Exception e) {
            // XXX: probably should not instatiate the _receiver unless it is ready to be
            // registered.
        }

        DeadReckoningManager.getInstance().dispose();
    }

    @Override
    public void onSharedPreferenceChanged(
            SharedPreferences sharedPreferences, String key) {

        if (key == null)
            return;

        switch (key) {
            case "useGPSTime":
                useGPSTime = sharedPreferences.getBoolean(key,
                        useGPSTime);
                Log.d(TAG,
                        "configuration changed for using GPS time using = "
                                + useGPSTime);
                CoordinatedTime.setCoordinatedTimeMillis(0);
                break;
            case "locationUnitType":
            case "locationCallsign":
                final String nlc = locationPrefs.getString("locationCallsign",
                        "");
                if (nlc.length() == 0) {
                    locationPrefs.edit()
                            .putString("locationCallsign", callsignGen(context))
                            .apply();
                }
            case "locationTeam":
                if (key.equals("locationCallsign"))
                    _mapView.getMapData().putBoolean(
                            "mockLocationCallsignValid", false);
                _updateContactPreferences(_mapView.getMapData(),
                        sharedPreferences);
                break;
            case "locationUseWRCallsign":
                _updateContactPreferences(_mapView.getMapData(),
                        sharedPreferences);
                break;
            case "tadiljId":
            case "tadiljSelfPositionType":
                _updateContactPreferences(_mapView.getMapData(),
                        sharedPreferences);
                break;
            case "custom_gps_icon_setting":
            case "location_marker_scale_key":
                //refresh gps icon and set new icon based on pref
                break;
            case "mockingOption":
                if (!(locationPrefs.getString("mockingOption", "WRGPS")
                        .equals("IgnoreInternalGPS"))) {
                    _startLocationGathering(context, gpsUpdateInterval);
                } else {
                    _stopLocationGathering();
                }
                break;
            case "useOnlyGPSBearing":
                useOnlyGPSBearing = locationPrefs.getBoolean(key, false);
                break;
        }
        if (key.equals("locationTeam")) {
            if (_locationMarker != null)
                _locationMarker.setMetaString("team",
                        locationPrefs.getString("locationTeam",
                                "Cyan"));
        }
        if (key.equals("location_marker_scale_key") ||
                key.equals("custom_gps_icon_setting") ||
                key.equals("locationTeam") ||
                key.equals("custom_color_selected") ||
                key.equals("custom_outline_color_selected")) {
            refreshGpsIcon();
        }

        //else { Log.d(TAG, "unhandled preference key changed: " + key); }
    }

    private void _updateContactPreferences(MapData mapData,
            SharedPreferences prefs) {
        if (!prefs.getBoolean("locationUseWRCallsign", false)
                || !mapData.getBoolean("mockLocationCallsignValid", false)) {
            _deviceCallsign = prefs.getString("locationCallsign",
                    callsignGen(context));
            _mapView.setDeviceCallsign(_deviceCallsign);
            if (_locationMarker != null)
                _locationMarker.setMetaString("callsign", _deviceCallsign);
        }
        _deviceTeam = prefs.getString("locationTeam", "Cyan");
        _deviceType = prefs.getString("locationUnitType",
                _mapView.getContext().getString(R.string.default_cot_type));

        mapData.putString("deviceTeam", _deviceTeam);
        mapData.putString("deviceType", _deviceType);

        if (prefs.getString("tadiljId", "").length() > 0) {
            mapData.putString("tadiljSelfPositionType",
                    prefs.getString("tadiljSelfPositionType", "J2.0"));
            mapData.putString("tadiljId", prefs.getString("tadiljId", ""));
        } else if (mapData.containsKey("tadiljId"))
            mapData.remove("tadiljId");
    }

    private void gpsDrift() {

        // final gpsTimestamp to use
        final long gpsTimestamp;

        MapData mapData = _mapView.getMapData();

        final long internalGpsTime = mapData.getLong("fineGPSTime", 0);
        final long externalGpsTime = mapData.getLong("mockGPSTime", 0);

        // do not reuse the drift time.
        mapData.remove("fineGPSTime");
        mapData.remove("mockGPSTime");

        // order of importance, gonna take external gps time over internal any day.

        if (externalGpsTime > 0) {
            gpsTimestamp = externalGpsTime;
        } else if (internalGpsTime > 0) {
            gpsTimestamp = internalGpsTime;
        } else {
            return;
        }

        if (useGPSTime) {
            CoordinatedTime.setCoordinatedTimeMillis(gpsTimestamp);
        } else {
            CoordinatedTime.setCoordinatedTimeMillis(0);
        }

        // Perform a one time notification if the drift between GPS time and the System time
        // is greater that 5 seconds.   This will serve two functions.
        // - notify the user the system time is probably wrong and should be corrected.
        // - remind the user that the system time is being corrected to gps time.

        if (!recvInitialTimestamp) {
            recvInitialTimestamp = true;
            if (Math.abs(CoordinatedTime.getCoordinatedTimeOffset()) > 10000) {

                // Announce to the rest of the system the date should be 
                // changed
                Intent i = new Intent("com.atakmap.utc_time_set");
                i.putExtra("millisec_epoch", gpsTimestamp);
                AtakBroadcast.getInstance().sendSystemBroadcast(i);

                // also send a local broadcast for CotMarkerRefresher
                AtakBroadcast.getInstance().sendBroadcast(i);

                // disable for 3.4 
                //NetworkManagerLite.setDateTime(gpsTimestamp);

                SimpleDateFormat dformatter = new SimpleDateFormat(
                        "dd MMMMM yyyy  HH:mm:ss", LocaleUtil.getCurrent());
                NotificationUtil
                        .getInstance()
                        .postNotification(
                                R.drawable.smallclock, NotificationUtil.WHITE,
                                context.getString(R.string.notification_text22),
                                context.getString(R.string.notification_text23)
                                        + dformatter.format(gpsTimestamp),
                                context.getString(R.string.notification_text24)
                                        +
                                        (int) Math.abs(CoordinatedTime
                                                .getCoordinatedTimeOffset()
                                                / 1000.0)
                                        + context
                                                .getString(
                                                        R.string.notification_text25)
                                        +
                                        dformatter.format(gpsTimestamp)
                                        + context
                                                .getString(
                                                        R.string.notification_text26)
                                        +
                                        dformatter.format(System
                                                .currentTimeMillis()));

            }
        }
    }

    private void _updateLocation(final Location loc) {
        final GeoPoint point;

        double alt;

        // note: http://gis.stackexchange.com/questions/8650/how-to-measure-the-accuracy-of-latitude-and-longitude/8674#8674
        // The fourth decimal place is worth up to 11 m: it can identify a parcel 
        // of land. It is comparable to the typical accuracy of an uncorrected 
        // GPS unit with no interference.
        // 
        // The fifth decimal place is worth up to 1.1 m: it distinguish trees from 
        // each other. Accuracy to this level with commercial GPS units can only be 
        // achieved with differential correction.
        //
        // The sixth decimal place is worth up to 0.11 m: you can use this for 
        // laying out structures in detail, for designing landscapes, building 
        // roads. It should be more than good enough for tracking movements 
        // of glaciers and rivers. This can be achieved by taking painstaking 
        // measures with GPS, such as differentially corrected GPS.
        //
        // The seventh decimal place is worth up to 11 mm: this is good for much 
        // surveying and is near the limit of what GPS-based techniques can achieve.

        // carry out to 6 digits
        final double locLat = Math.round(loc.getLatitude() * PRECISION_6)
                / PRECISION_6;
        // carry out to 6 digits
        final double locLon = Math.round(loc.getLongitude() * PRECISION_6)
                / PRECISION_6;

        String altSrc = GeoPointMetaData.UNKNOWN;
        if (loc.hasAltitude()) {
            // please see:
            // http://stackoverflow.com/questions/11168306/is-androids-gps-altitude-incorrect-due-to-not-including-geoid-height
            // due to differences in interpretation, it seems as though some devices
            // and android combinations may return MSL and others may return HAE.
            // As Matt Gillen pointed out, the end suggestion is to parse the GPGGA NMEA
            // message.
            //Log.d(TAG,"raw altitude as read from the sensor in meters: " + loc.getAltitude());
            //Log.d(TAG,"constructed altitude: "+ alt);
            //Log.d(TAG,"msl altitude from GPGGA: "+ nmeaMSLAltitude);

            altSrc = GeoPointMetaData.GPS;
            alt = EGM96.getHAE(locLat, locLon, nmeaMSLAltitude);

        } else {
            try {
                GeoPointMetaData gpm = ElevationManager.getElevationMetadata(
                        locLat, locLon, null);
                alt = gpm.get().getAltitude();
                altSrc = gpm.getAltitudeSource();
            } catch (Exception e) {
                alt = GeoPoint.UNKNOWN;
            }
        }

        if (loc.hasAccuracy())
            point = new GeoPoint(locLat, locLon,
                    alt, AltitudeReference.HAE,
                    loc.getAccuracy(),
                    GeoPoint.UNKNOWN);

        else
            point = new GeoPoint(locLat, locLon,
                    alt, AltitudeReference.HAE,
                    GeoPoint.UNKNOWN,
                    GeoPoint.UNKNOWN);

        // note some device did not return 0.0
        final double instantSpeed;
        if (loc.hasSpeed()) {
            instantSpeed = loc.getSpeed();
        } else {
            instantSpeed = 0.0;
        }

        final MapData mapData = _mapView.getMapData();
        mapData.putLong("internalGPSTime", loc.getTime());
        mapData.putLong("internalLocationTime", SystemClock.elapsedRealtime());
        mapData.putParcelable("internalLocation", point);
        mapData.putString("internalLocationSrc", GeoPointMetaData.GPS);
        mapData.putString("internalLocationAltSrc", altSrc);
        mapData.putDouble("internalLocationSpeed", instantSpeed);

        // TODO: mirror what is being done for fineLocationBearing and correct for 4.3
        if (loc.hasBearing())
            mapData.putDouble("internalLocationBearing", loc.getBearing());

        if (_mapView.getMapData().getBoolean("mockLocationAvailable", false)) {
            _locationMarker.removeMetaData("movable");
            _locationMarker.setMetaString("how", "m-g");
            if (mapData.getBoolean("fineLocationAvailable", false)) {
                Log.d(TAG, "mocking engaged, removing internal location data");
                _removeLocationMapData(mapData);
            }
            return;
        }

        mapData.putBoolean("fineLocationAvailable", true);

        /**
         * Using SystemClock so that accurate delta measurements can be made, not
         * subject to wall clock changes.
         */
        mapData.putLong("fineGPSTime", loc.getTime());
        mapData.putLong("fineLocationTime", SystemClock.elapsedRealtime());
        mapData.putParcelable("fineLocation", point);

        // XXY preserve that fineLocation is actually a composite of either GPS information
        // or GPS and DTED
        mapData.putString("fineLocationSrc", GeoPointMetaData.GPS);
        mapData.putString("fineLocationAltSrc", altSrc);

        mapData.putDouble("fineLocationSpeed", instantSpeed);

        avgSpeed.add(instantSpeed);
        if (_locationMarker != null)
            _locationMarker.setMetaDouble("avgSpeed30",
                    avgSpeed.getAverageSpeed());

        //Log.d(TAG, "added new instant speed " + instantSpeed + " averagespeed = " + avgSpeed.getAverageSpeed());

        if (loc.hasBearing())
            mapData.putDouble("fineLocationBearing", loc.getBearing());

        // use the GPS bearing when we're over a certain speed
        if (avgSpeed.useGPSBearing() && getMapMode() != MapMode.MAGNETIC_UP) {

            // according to all of the documentation I have read
            // Location::getBearing is a direction east of true north.
            if (loc.hasBearing() && (instantSpeed > VALID_GPS_BEARING_SPEED)
                    || useOnlyGPSBearing) {
                _updateHeading(loc.getBearing());
            }
            if (_locationMarker != null) {
                _locationMarker.setTrack(_filteredHeading.getEstimate(), 0d);
            }
        }
    }

    /**
     * Update the heading for the filter based on a measurement east of true
     * north. Will protect against NaN
     *
     * @param measurement east of true north.
     */
    private void _updateHeading(final double measurement) {
        if (Double.isNaN(measurement)) {
            lastMeasurementInvalid = true;
            //Log.d(TAG, "heading is invalid, ignore: " + measurement);
            return;
        } else if (lastMeasurementInvalid) {
            lastMeasurementInvalid = false;
            //Log.d(TAG, "good heading but last one was invalid, ignore: " + measurement);
            return;
        }

        if (_lastHeadingUpdate == 0) {
            _filteredHeading.reset(measurement);
        } else {
            long delta = SystemClock.elapsedRealtime() - _lastHeadingUpdate;
            if (delta > 0) {
                double deltaSeconds = delta / MILLIS_IN_SECOND;
                _filteredHeading.update(measurement, deltaSeconds,
                        _FILTER_GRANULARITY);
            }
        }
        _lastMeasuredHeading = measurement;
        _lastHeadingUpdate = SystemClock.elapsedRealtime();
    }

    private synchronized void createLocationMarker() {
        if (_locationGroup == null) {
            _locationGroup = _mapView.getRootGroup();
        }
        if (_locationMarker == null) {
            final String _deviceUID = _determineBestDeviceUID(context);
            _locationMarker = new Marker(GeoPoint.ZERO_POINT, _deviceUID);
            _locationMarker.setType("self");
            _locationMarker.setMetaString("team",
                    locationPrefs.getString("locationTeam", "Cyan"));
            _locationMarker.setMetaBoolean("addToObjList", false);
            _locationMarker
                    .addOnTrackChangedListener(_trackChangedListener);
            _locationMarker
                    .addOnPointChangedListener(new OnPointChangedListener() {

                        @Override
                        public void onPointChanged(PointMapItem item) {

                            // movable empty is false
                            if (item.getMovable()) {
                                // User moved point, use that location until
                                // GPS comes back

                                Log.d(TAG, "looking up the altitude for: "
                                        + item.getPoint());
                                GeoPointMetaData newPoint = ElevationManager
                                        .getElevationMetadata(item.getPoint());

                                _mapView.getMapData().putBoolean(
                                        "fakeLocationAvailable", true);
                                _mapView.getMapData().putParcelable(
                                        "fakeLocation", newPoint.get());
                                _mapView.getMapData().putLong(
                                        "fakeLocationTime", 0);
                            }
                        }
                    });
            _locationMarker.setMetaString("callsign", _deviceCallsign);
            _locationMarker.setStyle(Marker.STYLE_ROTATE_HEADING_MASK);
            _locationMarker.setMetaInteger("color", Color.BLUE);

            _locationMarker.setMetaString("menu", "menus/self_menu.xml");
            // _locationMarker.setMovable(false); // empty is false
            _locationMarker.setZOrder(Double.NEGATIVE_INFINITY);

            DocumentedIntentFilter filter = new DocumentedIntentFilter();
            filter.addAction("com.atakmap.android.map.action.TOGGLE_GPS_ERROR");
            AtakBroadcast.getInstance().registerReceiver(_receiver, filter);

            if (_accuracyEllipse == null) {
                _accuracyEllipse = new Ellipse(UUID.randomUUID().toString());
                _accuracyEllipse
                        .setCenter(_locationMarker.getGeoPointMetaData());
                _accuracyEllipse.setFillColor(Color.argb(50, 187, 238, 255));
                _accuracyEllipse.setFillStyle(2);
                _accuracyEllipse.setStrokeColor(Color.BLUE);
                _accuracyEllipse.setStrokeWeight(4);
                _accuracyEllipse.setMetaString("shapeName", "GPS Error");
                _accuracyEllipse.setMetaBoolean("addToObjList", false);
                _accuracyEllipse.setClickable(false);
            }
            _locationGroup.addItem(_accuracyEllipse);
            enableGPSError(true);

            _locationMarker
                    .addOnPointChangedListener(new OnPointChangedListener() {
                        GeoPoint lastPoint = null;

                        @Override
                        public void onPointChanged(PointMapItem item) {
                            final GeoPointMetaData gp = item
                                    .getGeoPointMetaData();
                            if (!Double.isNaN(gp.get().getCE())) {
                                _accuracyEllipse.setVisible(
                                        item.getVisible() && _gpserrorenabled);
                                if (lastPoint == null
                                        || lastPoint.distanceTo(gp.get()) > 0.25
                                        || Double.compare(gp.get().getCE(),
                                                lastPoint.getCE()) != 0) {
                                    _accuracyEllipse.setDimensions(gp,
                                            (int) gp.get().getCE(),
                                            (int) gp.get().getCE());
                                    lastPoint = gp.get();
                                }
                            } else {
                                _accuracyEllipse.setVisible(false);
                            }

                        }
                    });

            _locationMarker.addOnVisibleChangedListener(
                    new MapItem.OnVisibleChangedListener() {
                        @Override
                        public void onVisibleChanged(MapItem item) {
                            if (_accuracyEllipse != null) {
                                _accuracyEllipse.setVisible(
                                        item.getVisible() && _gpserrorenabled);
                            }
                        }
                    });
        }
    }

    /* 
     * Upades the location Marker
     * Checks for custom color set by user in preferences
     * if not color is set return default icon used for application
     * if custom colored is selected recolor image and set in /atak/gps_icons/
     * depends on icon.
     * This is a pretty expensive method and should only be called when one of the 4 governing preferences 
     * are changed.
     *
     * @return Icon containing drawable/file path used for displaying GPS user self location
     */
    private void refreshGpsIcon() {
        if (_locationMarker != null) {

            Icon.Builder builder = new Icon.Builder();

            final int size = Integer.parseInt(locationPrefs.getString(
                    "location_marker_scale_key", "-1"));
            final int userSelfColor = locationPrefs.getInt(
                    "custom_gps_icon_setting", 0);
            final String userTeam = locationPrefs.getString("locationTeam",
                    "Cyan");
            final String userCustomColor = locationPrefs.getString(
                    "custom_color_selected", "#FFFFFF");
            final String userCustomStrokeColor = locationPrefs.getString(
                    "custom_outline_color_selected", "#FFFFFF");

            final Drawable selfMarker = context
                    .getDrawable(R.drawable.ic_self_tintable);
            final Drawable selfStroke = context
                    .getDrawable(R.drawable.ic_self_stroke_tintable);

            switch (userSelfColor) {
                case 0: //default
                    selfMarker.setTint(0xff44b2dd);
                    selfStroke.setTint(Color.WHITE);
                    break;
                case 1: //use team color , image is created when user selects using team color
                    selfMarker.setTint(
                            Icon2525cIconAdapter.teamToColor(userTeam));
                    selfStroke.setTint(Color.WHITE);
                    break;
                case 2:
                    selfMarker.setTint(Color.parseColor(userCustomColor));
                    selfStroke.setTint(Color.parseColor(userCustomStrokeColor));
                    break;
                default:
                    break;
            }

            final LayerDrawable composite = new LayerDrawable(new Drawable[] {
                    selfMarker, selfStroke
            });
            final Bitmap marker = ATAKUtilities.getBitmap(composite);

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            marker.compress(Bitmap.CompressFormat.PNG, 100, baos);
            final byte[] b = baos.toByteArray();
            final String encoded = "base64://" + Base64.encodeToString(b,
                    Base64.NO_WRAP | Base64.URL_SAFE);

            final int width;
            final int height;

            if (size == -1) {
                width = SELF_MARKER_WIDTH;
                height = SELF_MARKER_HEIGHT;
            } else {
                float factor = size / (float) SELF_MARKER_WIDTH;
                width = size;
                height = ((int) (SELF_MARKER_HEIGHT * factor));
            }

            builder.setImageUri(0, encoded);
            builder.setSize(width, height);
            _locationMarker.setIcon(builder.build());
        }
    }

    private void _updateLocationMarker(final String prefix) {
        MapData data = _mapView.getMapData();

        gpsDrift();

        if (_locationMarker == null) {
            Log.w(TAG, "Cannot update null location marker");
            return;
        }

        if (data.containsKey(prefix + "Location")

                && (SystemClock.elapsedRealtime() - _mapView.getMapData()
                        .getLong(
                                prefix + "LocationTime")) < GPS_TIMEOUT_MILLIS) {

            SelfCoordOverlayUpdater.getInstance().change();

            GeoPoint point = data.getParcelable(prefix + "Location");

            // instead of making GeoMetaDataPoint persistant, go ahead and pass the information
            // via data extras.
            String altitudeSource = data.getString(prefix + "LocationAltSrc",
                    "???");
            String geoLocationSource = data.getString(prefix + "LocationSrc",
                    "???");

            // GPS tick is used only to indicate that data is still flowing and the noGPS intent
            // will not get sent.    This is the same tick for both mock and fine location.
            _locationMarker.setMetaLong("gpsUpdateTick",
                    SystemClock.elapsedRealtime());

            if (_locationMarker.getGroup() == null) {
                _updateOrientationOffset();
                Intent intent = new Intent();
                intent.setAction(LOCATION_INIT);
                AtakBroadcast.getInstance().sendBroadcast(intent);
                _locationGroup.addItem(_locationMarker);
                refreshGpsIcon();
            }

            if (point != null && !point.equals(_locationMarker.getPoint())) {
                _locationMarker.setPoint(GeoPointMetaData.wrap(point,
                        geoLocationSource, altitudeSource));
            }

            /*
             * this data is shoved in here so the self coord display updates correctly
             */
            final double instantSpeed = data.getDouble(
                    prefix + "LocationSpeed", Double.NaN);
            _locationMarker.setMetaDouble("Speed", instantSpeed);

            if (!Double.isNaN(instantSpeed)) {
                avgSpeed.add(instantSpeed);
            } else {
                // speed is invalid.
                avgSpeed.add(0.0);
            }
            //Log.d(TAG, "added new instant speed (prefix) " + instantSpeed + " averagespeed = " + avgSpeed.getAverageSpeed());
            _locationMarker.setMetaDouble("avgSpeed30",
                    avgSpeed.getAverageSpeed());

            /**
             * gpsUpdateTick is not a real timestamp, but a guaranteed increasing count in milliseconds independent
             * from System Time/Date manipulation.     Comparisons of gpsUpdateTick should only be against a call to
             * SystemClock.elapsedRealtime().
             */
            _locationMarker.setMetaLong("gpsUpdateTick",
                    SystemClock.elapsedRealtime());

            if (avgSpeed.useGPSBearing()
                    && getMapMode() != MapMode.MAGNETIC_UP) {
                final double h = _mapView.getMapData().getDouble(
                        prefix + "LocationBearing");

                // no valid speed no bearing
                if (instantSpeed > VALID_GPS_BEARING_SPEED
                        || useOnlyGPSBearing) {
                    _updateHeading(h);
                    _locationMarker
                            .setTrack(_filteredHeading.getEstimate(), 0d);
                }
            }

            if (point != null && !Double.isNaN(point.getCE())) {
                // Only update the accuracy, otherwise the marker will
                // move after the location estimate

                if (!_locationGroup.containsItem(_accuracyEllipse)) {
                    _locationGroup.addItem(_accuracyEllipse);
                }
            } else {
                if (_accuracyEllipse.getGroup() != null)
                    _locationGroup.removeItem(_accuracyEllipse);
            }
        } else if (data.containsKey(prefix + "Location")
                && (SystemClock.elapsedRealtime() - _mapView.getMapData()
                        .getLong(
                                prefix + "LocationTime")) > GPS_TIMEOUT_MILLIS) {

            if (_accuracyEllipse != null) {
                if (_accuracyEllipse.getGroup() != null)
                    _locationGroup.removeItem(_accuracyEllipse);
            }

            // in case the GPS is lost, continue to track for a period of time as per
            // JS 12/31

            // remove the speed from the marker as GPS has timed out.
            //_locationMarker.removeMetaData("Speed");
            //avgSpeed.reset();

            SelfCoordOverlayUpdater.getInstance().change();
        } else {
            //No GPS available but still need to update location/connectcion widgets
            SelfCoordOverlayUpdater.getInstance().change();
        }

        /*
         * Hide / show the "NO GPS" overlay, turn on/off ability to move the marker. I think this
         * should work for both mock and fine now.
         */

        if (lastPrefix == null || !lastPrefix.equals(prefix)) {
            if (prefix.equals("fake")) {
                _reportNoGps();
            } else {
                _reportGpsBack();
            }
            lastPrefix = prefix;
        }

        _trackChangedListener.onTrackChanged(_locationMarker);

    }

    private String lastPrefix = null;

    private final BroadcastReceiver _receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context arg0, Intent arg1) {
            enableGPSError(!_gpserrorenabled);
        }

    };

    private void enableGPSError(boolean error) {
        _accuracyEllipse.setVisible(error);
        _locationMarker.setMetaBoolean("gpserrorenabled", error);
        _gpserrorenabled = error;
    }

    // TODO : clean up track up filtering code
    private final OnTrackChangedListener _trackChangedListener = new OnTrackChangedListener() {
        private float lastHeading = 0f;
        private long lastUpdateTime = 0;

        @Override
        public void onTrackChanged(Marker marker) {
            AtakMapController ctrl = _mapView.getMapController();
            final float heading = (float) marker.getTrackHeading();

            if (Double.isNaN(heading) || heading < 0f || heading > 360f) {
                //Log.d(TAG, "heading is invalid, ignore: " + heading);
                return;
            }

            long ctime = SystemClock.elapsedRealtime();

            if (lastUpdateTime == 0)
                lastUpdateTime = ctime;

            long timeSinceLast = ctime - lastUpdateTime;

            if (timeSinceLast < HEADING_REFRESH_MIN_ELAPSE_MS) {
                // Log.d(TAG,"time between:  --SKIPPED--  "+timeSinceLast+
                // "  /  "+currentTimeBetweenUpdatesMS);
                return;
            }

            lastUpdateTime = ctime;

            // Log.d(TAG,"time between:  "+timeSinceLast+
            // "  /  "+currentTimeBetweenUpdatesMS);

            float dHeading = heading - lastHeading;

            // Account for rotating through zero degrees
            if (dHeading > 180f)
                dHeading -= 360f;
            else if (dHeading < -180f)
                dHeading += 360f;

            float smoothed = dHeading;

            lastHeading += smoothed;

            if (lastHeading > 360f)
                lastHeading -= 360f;
            if (lastHeading < 0)
                lastHeading += 360f;

            MapMode orientationMethod = getMapMode();
            if (orientationMethod == MapMode.TRACK_UP
                    || orientationMethod == MapMode.MAGNETIC_UP) {

                if (_locationMarker.getMetaBoolean("camLocked", false)) {
                    PointF p = _mapView.forward(_locationMarker.getPoint());
                    CameraController.Interactive.rotateTo(
                            _mapView.getRenderer3(), lastHeading,
                            _locationMarker.getPoint(), p.x, p.y,
                            MapRenderer3.CameraCollision.AdjustCamera,
                            true);
                } else {
                    ctrl.rotateTo(lastHeading, true);
                }
            }
        }
    };

    private void _reportNoGps() {
        boolean reportAsap = _locationMarker.getMetaString("how", "h-e")
                .equals("m-g");

        _locationMarker.setMovable(true);
        _locationMarker.setMetaString("how", "h-e");
        _locationMarker.removeMetaData("Speed");

        if (reportAsap && _mapView != null && _mapView.getContext() != null) {
            Log.d(TAG,
                    "No GPS available, sending a system broadcast for now until CotService is augmented");
            AtakBroadcast.getInstance().sendBroadcast(
                    new Intent(ReportingRate.REPORT_LOCATION)
                            .putExtra("reason", "No GPS available"));
        }
    }

    private void _reportGpsBack() {
        boolean reportAsap = _locationMarker.getMetaString("how", "m-g")
                .equals("h-e");

        // empty is false, !empty is true
        _locationMarker.removeMetaData("movable");
        _locationMarker.setMetaString("how", "m-g");

        if (reportAsap && _mapView != null && _mapView.getContext() != null) {
            Log.d(TAG,
                    "GPS available, sending a system broadcast for now until CotService is augmented");
            AtakBroadcast.getInstance().sendBroadcast(
                    new Intent(ReportingRate.REPORT_LOCATION)
                            .putExtra("reason", "GPS now available"));
        }
    }

    /*
     * If we're at (0,0), we probably don't have GPS
     */
    private boolean _checkNoGps(GeoPoint point) {
        return point == null
                || (point.getLatitude() == 0d && point.getLongitude() == 0d);
    }

    /**
     * Removes all trace of the fine location from the mapData bundle. It is important to start with
     * the removal of the availability flag.
     */
    private void _removeLocationMapData(final MapData mapData) {
        mapData.remove("fineLocationAvailable");

        mapData.remove("fineLocation");
        mapData.remove("fineLocationTime");
        mapData.remove("fineLocationBearing");
        mapData.remove("fineLocationSpeed");
    }

    /**
     * Get the current map mode/orientation
     * @return Map mode - one of {@link MapMode}
     */
    private MapMode getMapMode() {
        return NavView.getInstance().getMapMode();
    }

    /**
     * Retrieves a TAK generated serial number based on the device.   If the serial number
     * is not available, null will be returned.   Recent versions of Android rely on the ANDROID_ID
     * as the app accessible serial number
     * @param context the context to use
     * @return the serial number or null
     */
    public static String fetchSerialNumber(final Context context) {
        return _fetchSerialNumber(context);
    }

    /**
     * Retrives a TAK accessible telephony identifier based on the device.  If the telephony id is
     * not available, then return it will return null.
     * @param context the context to use
     * @return the telephony identifier
     */
    public static String fetchTelephonyDeviceId(final Context context) {
        return _fetchTelephonyDeviceId(context);
    }

    /**
     * Retrives a TAK accessible wifi mac address based on the device.  If the mac address is
     * not available, then return it will return null.
     * @param context the context to use
     * @return the telephony identifier
     */
    public static String fetchWifiMacAddress(final Context context) {
        return _fetchWifiMacAddress(context);
    }

    @SuppressLint({
            "PrivateApi", "HardwareIds"
    })
    private static String _fetchSerialNumber(final Context context) {
        String serialNumber = null;
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class, String.class);

            serialNumber = (String) get.invoke(c, "sys.serialnumber",
                    Build.UNKNOWN);
            if (serialNumber != null
                    && serialNumber.equalsIgnoreCase(Build.UNKNOWN)) {
                serialNumber = (String) get.invoke(c, "ril.serialnumber",
                        Build.UNKNOWN);
            }
            if (serialNumber != null
                    && serialNumber.equalsIgnoreCase(Build.UNKNOWN)) {
                try {
                    if (Build.SERIAL != null
                            && !Build.UNKNOWN.equalsIgnoreCase(Build.SERIAL)) {
                        serialNumber = Build.SERIAL;
                    } else {
                        // attempt to get the Android_ID which does change on a factory reset but 
                        // does not change on application uninstall/reinstall.

                        // Please note  -
                        // On Android 8.0 (API level 26) and higher versions of the platform, a
                        // 64-bit number (expressed as a hexadecimal string), unique to each
                        // combination of app-signing key, user, and device. Values of ANDROID_ID
                        // are scoped by signing key and user. The value may change if a factory
                        // reset is performed on the device or if an APK signing key changes. For
                        // more information about how the platform handles ANDROID_ID in Android
                        // 8.0 (API level 26) and higher, see Android 8.0 Behavior Changes.
                        //
                        // In versions of the platform lower than Android 8.0 (API level 26), a
                        // 64-bit number (expressed as a hexadecimal string) that is randomly
                        // generated when the user first sets up the device and should remain
                        // constant for the lifetime of the user's device. On devices that have
                        // multiple users, each user appears as a completely separate device, so
                        // the ANDROID_ID value is unique to each user.

                        serialNumber = Secure.getString(
                                context.getContentResolver(),
                                Secure.ANDROID_ID);
                    }
                } catch (Exception e) {
                    return null;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "error obtaining the serial number via reflection", e);
        }

        // possibly serialNumber is getting set to unknown, all zeros or the emulator default / just return null instead
        if (serialNumber != null) {
            if (("9774d56d682e549c".equalsIgnoreCase(serialNumber)) ||
                    (Build.UNKNOWN.equalsIgnoreCase(serialNumber)) ||
                    ("000000000000000".equalsIgnoreCase(serialNumber)) ||
                    ("00".equalsIgnoreCase(serialNumber))) {
                return null;
            }
        }

        return serialNumber;
    }

    @SuppressLint("HardwareIds")
    private static String _fetchWifiMacAddress(Context context) {
        String wifiMacAddress = null;
        final WifiManager wifi = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);

        // a possible way to uniquely identify the phone 
        if (wifi != null && wifi.getConnectionInfo() != null) {
            wifiMacAddress = wifi.getConnectionInfo().getMacAddress();
        } else {
            Log.d(TAG, "unable to  obtain the wifi device id");
        }
        return wifiMacAddress;
    }

    @SuppressLint("HardwareIds")
    @SuppressWarnings({
            "MissingPermission"
    })
    private static String _fetchTelephonyDeviceId(Context context) {
        String telephonyDeviceId = null;
        TelephonyManager tm = (TelephonyManager) context
                .getSystemService(Context.TELEPHONY_SERVICE);

        // one way of making this uid truly globally unique -- another way is Wifi MAC address
        //
        if (tm != null) {
            try {
                // on newer devices this could throw a security exception
                // "android.permission.READ_PRIVILEGED_PHONE_STATE" 
                telephonyDeviceId = tm.getDeviceId();
            } catch (Exception ignored) {
            }
        } else {
            Log.d(TAG, "unable to obtain the telephony device id");
        }

        // possibly telephonyDeviceId is getting set to unknown / just return null instead
        // Note: in the case of the herelink controllers which run Android 7, the telephony
        // id is set to 862391030003883.
        if (telephonyDeviceId != null &&
                (telephonyDeviceId.equalsIgnoreCase(Build.UNKNOWN) ||
                        (telephonyDeviceId
                                .equalsIgnoreCase("862391030003883"))))
            return null;

        return telephonyDeviceId;
    }

    /**
     * Obtains the telephone numvber associated with the device.
     * @param context the context used to determine the telephone number.
     * @return null if no telephone number is found, otherwise the telephone number of the device.
     */
    @SuppressLint({
            "MissingPermission", "HardwareIds"
    })
    public static String _fetchTelephonyLine1Number(Context context) {
        String telephonyLineNumber = null;
        final TelephonyManager tm = (TelephonyManager) context
                .getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null &&
                Permissions.checkPermission(context,
                        android.Manifest.permission.READ_PHONE_STATE)) {

            try {
                telephonyLineNumber = tm.getLine1Number();
            } catch (SecurityException ignored) {
            }
        } else {
            Log.d(TAG, "unable to get the line number");
        }

        return telephonyLineNumber;
    }

    /**
     * Check empty or known invalid
     *
     * @param phone the phone number to check
     * @return true if the phone number is not empty and does not contain a series of zeros.
     */
    public static boolean isValidTelephoneNumber(final String phone) {
        return !FileSystemUtils.isEmpty(phone) && !phone.contains("0000000");
    }

    @Override
    public void onStart(Context context, MapView view) {
        _updateOrientationOffset();
    }

    @Override
    public void onResume(Context context, MapView view) {
        _updateOrientationOffset();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        _updateOrientationOffset();
    }
}
