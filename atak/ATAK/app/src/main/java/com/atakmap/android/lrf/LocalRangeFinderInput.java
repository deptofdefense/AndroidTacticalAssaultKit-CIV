
package com.atakmap.android.lrf;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;

import com.atakmap.android.util.DragMarkerHelper;
import com.atakmap.app.system.ResourceUtil;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.conversion.GeomagneticField;
import android.preference.PreferenceManager;
import android.widget.Toast;
import android.os.Bundle;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.icons.IconsMapAdapter;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.PointMapItem.OnPointChangedListener;
import com.atakmap.android.maps.SensorFOV;
import com.atakmap.android.toolbars.RangeAndBearingMapItem;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.Circle;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;

import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import gov.tak.api.util.Disposable;
import com.atakmap.android.gui.HintDialogHelper;
import com.atakmap.android.toolbar.widgets.TextContainer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/**
 * Allows for local range finder data receipt over port 17211. It is assumed that the publication of
 * local data will be done using a TTL of 0. The input data at this time will only be for supporting
 * PC mode style data from the PLFR in a receive only fashion. The data will be transmitted as
 * triplets D,A,E where A is magnetic with no declination applied. The packets listened for are
 * (currently specversion = 1): specversion,uid,timestamp,d,a,e - d is distance in meters - a is the
 * azimuth in degrees (magnetic with no declination applied) - e elevation/inclination in degrees
 * The distance is considered raw and attempts to modify it based on the inclination (elevation) of
 * the unit should be done by the user (this code) with an understanding of the underlying terrain
 * model. specversion,uid,timestamp,UNKNOWN_MESSAGE specversion,uid,timestamp,COMPASS_ERROR
 * specversion,uid,timestamp,MAINBOARD_ERROR specversion,uid,timestamp,RANGE_ERROR TODO: socket
 * timeout alternative local intent passing, to compliment socket version
 * 
 * 
 * 
 */
public class LocalRangeFinderInput implements Runnable, RangeFinderAction,
        SharedPreferences.OnSharedPreferenceChangeListener {

    final static String TAG = "LocalRangeFinderInput";

    final static String HIDE_PLRF = "com.atakmap.android.maps.HIDE_PLRF";

    DatagramSocket s;

    private final byte[] receiveData = new byte[64 * 1024];
    private final DatagramPacket receivePacket = new DatagramPacket(
            receiveData, receiveData.length);
    private final static int port = 17211;
    private final static int RETRY_READ_WAIT_ERROR = 3000;

    final String spiUID;

    private boolean cancelled = false;

    private final MapView mapView;
    private final Context context;
    private Circle _circle = null;

    private SensorFOV _fov = null;
    private final Marker _formerSelf;

    private GeoPoint origDstPoint;
    private GeoPointMetaData origSrcPoint;
    private double hae;

    private RangeAndBearingMapItem rb = null;

    private Timer _timer;
    private Timer _editTimer;
    private int _updateTimeout;

    private final Icon _defaultIcon;
    private final Icon _originIcon;
    private final Updater calc;

    private final SharedPreferences _prefs;

    private boolean showing = false;

    private final TextContainer _container;

    private static LocalRangeFinderInput _instance;

    private RangeFinderAction externalAction;

    public LocalRangeFinderInput(final MapView mapView,
            final Context context) {
        this.mapView = mapView;
        this.context = context;

        _container = TextContainer.getInstance();

        spiUID = mapView.getSelfMarker().getUID() + ".PLRF";

        _prefs = PreferenceManager.getDefaultSharedPreferences(mapView
                .getContext());

        _prefs.registerOnSharedPreferenceChangeListener(this);
        updateTimeout();

        DocumentedIntentFilter intentFilter = new DocumentedIntentFilter();
        intentFilter.addAction(HIDE_PLRF);
        AtakBroadcast.getInstance().registerReceiver(br, intentFilter);

        _originIcon = new Icon.Builder().setImageUri(0, ATAKUtilities
                .getResourceUri(context, R.drawable.reference_point))
                .build();

        _defaultIcon = new Icon.Builder().setImageUri(0, ATAKUtilities
                .getResourceUri(context, R.drawable.spip_icon))
                .build();

        _formerSelf = new Marker(GeoPoint.ZERO_POINT, spiUID + "_origin");
        _formerSelf.setMetaString("callsign", "origin");
        _formerSelf.setIcon(_originIcon);
        _formerSelf.setClickable(false);
        _formerSelf.setClickable(false);
        _formerSelf.setMovable(false);
        _formerSelf.setMetaBoolean("removable", false);
        _formerSelf.setMetaBoolean("addToObjList", false);
        _formerSelf.setZOrder(-2000d);

        calc = new Updater();

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction("com.atakmap.android.lrf.TOGGLE_SLIDE");
        AtakBroadcast.getInstance().registerReceiver(_receiver, filter);

        Log.d(TAG, "starting local range finder listening");
        _instance = this;

    }

    /**
     * Obtains the instance of the LocalRangeFinderInput.
     */
    public static LocalRangeFinderInput getInstance() {
        return _instance;
    }

    /**
     * Allow for plugin's to override the action that occurs when a laser range finder is 
     * used. 
     */
    public void registerAction(final RangeFinderAction externalAction) {
        this.externalAction = externalAction;
    }

    private void updateTimeout() {
        try {
            _updateTimeout = Integer.parseInt(_prefs.getString(
                    "spiUpdateDelay", "5"));
        } catch (NumberFormatException e) {
            _updateTimeout = 5;
            _prefs.edit().putString("spiUpdateDelay", "5").apply();
        }
    }

    private final BroadcastReceiver br = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "received an intent: " + action);
            if (HIDE_PLRF.equals(action)) {
                String uid = intent.getStringExtra("from");
                if (uid != null && uid.equals(spiUID))
                    remove();
            }
        }
    };

    synchronized private void remove() {

        if (_fov != null)
            _fov.setVisible(false);
        _container.closePrompt();
        editTimer(false);

        stopTimer();

        if (rb != null) {
            rb.removeFromGroup();
            rb.dispose();
            rb = null;
        }
        if (_circle != null)
            _circle.removeFromGroup();
        _circle = null;

        if (_fov != null)
            _fov.removeFromGroup();
        _fov = null;

        if (_formerSelf != null)
            _formerSelf.removeFromGroup();

        MapItem mi = mapView.getMapItem(spiUID);
        if (mi != null)
            mi.removeFromGroup();

        //Intent intent = new Intent();
        //intent.setAction("com.atakmap.android.maps.HIDE_POINT_DETAILS");
        //intent.putExtra("uid", spiUID);
        //AtakBroadcast.getInstance().sendBroadcast(intent);

    }

    synchronized private Marker getSPI() {

        Marker mi = (Marker) mapView.getMapItem(spiUID);

        if (mi == null) {
            Marker blankMarker = new Marker(GeoPoint.ZERO_POINT, spiUID);
            blankMarker.setZOrder(-2000d);
            blankMarker.setMetaBoolean("editable", false);
            blankMarker.setMovable(false);
            blankMarker.setMetaBoolean("removable", true);
            blankMarker.setType("b-m-p-s-p-i");
            blankMarker.setMetaString("how", "h-e");

            // important to put this marker under the control of the CotMarkerRefresher
            blankMarker.setMetaString("entry", "user");

            blankMarker.setMetaString("parent_type",
                    mapView.getMapData().getString("deviceType"));
            blankMarker.setMetaString("parent_uid",
                    mapView.getSelfMarker().getUID());

            blankMarker.setMetaString("iconUri", "icons/spip_icon.png");
            IconsMapAdapter iconAdapter = new IconsMapAdapter(context);

            iconAdapter.adaptMarkerIcon(blankMarker);
            blankMarker.setVisible(true);
            blankMarker.setMetaString("menu", "menus/b-m-p-s-p-i-lrf.xml");
            final MapGroup mg = mapView.getRootGroup().findMapGroup("SPIs");
            mg.addItem(blankMarker);

            blankMarker
                    .addOnVisibleChangedListener(
                            new MapItem.OnVisibleChangedListener() {
                                @Override
                                public void onVisibleChanged(MapItem item) {

                                    final MapItem frb = rb;
                                    if (item.getVisible()) {
                                        if (frb != null) {
                                            frb.setVisible(true);
                                        }
                                        startTimer();
                                    } else {
                                        if (frb != null) {
                                            frb.setVisible(false);
                                        }
                                        stopTimer();
                                    }
                                }
                            });

            blankMarker.addOnPointChangedListener(opcl);

            mapView.getMapEventDispatcher().addMapItemEventListener(
                    blankMarker, _mapItemEventListener);

            return blankMarker;

        } else {
            return mi;
        }
    }

    /**
     * Cancel this activity.
     */
    void cancel() {
        cancelled = true;
        try {
            s.close();
            s = null;
        } catch (Exception ignore) {
        }

    }

    private final MapEventDispatcher.OnMapEventListener _dragListener = new MapEventDispatcher.OnMapEventListener() {
        @Override
        public void onMapItemMapEvent(MapItem item, MapEvent event) {
            final String type = event.getType();
            if ((type.equals(MapEvent.ITEM_DRAG_STARTED) ||
                    type.equals(MapEvent.ITEM_DRAG_CONTINUED) ||
                    type.equals(MapEvent.ITEM_DRAG_DROPPED))
                    && item == getSPI()) {

                boolean stopped = type.equals(MapEvent.ITEM_DRAG_DROPPED);

                editTimer(false);
                if (stopped) {
                    stopDrag();
                    DragMarkerHelper.getInstance().hideWidget();
                }

                double lat = item.getMetaDouble("sourceLat", Double.NaN);
                double lon = item.getMetaDouble("sourceLon", Double.NaN);
                if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
                    Marker marker = (Marker) item;
                    GeoPoint anchorPoint = new GeoPoint(lat, lon);
                    double bearing = DistanceCalculations
                            .bearingFromSourceToTarget(
                                    anchorPoint,
                                    mapView.inverse(
                                            event.getPointF().x,
                                            event.getPointF().y).get());
                    double range = GeoCalculations.distanceTo(
                            anchorPoint, marker.getPoint());
                    final GeoPoint targetPoint = GeoCalculations
                            .pointAtDistance(anchorPoint, bearing, range);

                    final GeoPointMetaData tpWithAlt = GeoPointMetaData.wrap(
                            new GeoPoint(
                                    targetPoint.getLatitude(),
                                    targetPoint.getLongitude(),
                                    hae),
                            GeoPointMetaData.CALCULATED,
                            GeoPointMetaData.CALCULATED);
                    marker.setPoint(tpWithAlt);
                    if (!stopped)
                        DragMarkerHelper.getInstance().updateWidget(marker);
                }
            }
        }
    };

    private synchronized void createOrUpdate(final PointMapItem start,
            final PointMapItem end) {

        Log.d(TAG, "deleting the rb because of a new shot: " + end.getUID());
        if (rb != null) {
            rb.removeFromGroup();
            rb.dispose();
            rb = null;
        }

        if (rb == null) {
            rb = RangeAndBearingMapItem
                    .createOrUpdateRABLine(spiUID + ".rb",
                            start, end, false);
            if (rb == null) {
                Log.e(TAG, "error occurred during creation of arrow");
                return;
            }
            rb.setType("rb");
            rb.setZOrder(-1000d);

            rb.setMetaBoolean("removable", false);
            rb.setMetaBoolean("addToObjList", false);

            final MapGroup _linkGroup = mapView.getRootGroup().findMapGroup(
                    "Range & Bearing");
            _linkGroup.addItem(rb);
        }

    }

    private final MapEventDispatcher.OnMapEventListener _mapItemEventListener = new MapEventDispatcher.OnMapEventListener() {
        @Override
        public void onMapItemMapEvent(final MapItem item,
                final MapEvent event) {
            if (event.getType().equals(MapEvent.ITEM_REMOVED)) {
                Log.d(TAG, "deleting the rb because the spi was deleted: "
                        + item.getUID());
                remove();
            }
        }
    };

    private void stopDrag() {
        final Marker mi = getSPI();
        if (mi.hasMetaValue("lrfslide")) {
            if (_fov != null)
                _fov.setVisible(false);
            _container.closePrompt();
            mi.removeMetaData("lrfslide");
            mi.setMetaBoolean("drag", false);
            mapView.getMapEventDispatcher().removeMapItemEventListener(
                    mi, _dragListener);

            if (_circle != null) {
                _circle.removeFromGroup();
                _circle = null;
            }
        }
    }

    private final BroadcastReceiver _receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String uid = intent.getStringExtra("id");
            if (uid != null) {

                final Marker mi = getSPI();
                if (mi.hasMetaValue("lrfslide")) {
                    mi.removeMetaData("lrfslide");
                    mi.setMetaBoolean("drag", false);
                    mapView.getMapEventDispatcher().removeMapItemEventListener(
                            mi, _dragListener);
                    if (_circle != null) {
                        _circle.removeFromGroup();
                        _circle = null;
                    }
                    if (_fov != null)
                        _fov.setVisible(false);
                    Log.d(TAG, "fov end visible = " + _fov.getVisible());
                    _container.closePrompt();
                } else {
                    if (_fov != null)
                        _fov.setVisible(true);
                    Intent i = new Intent(
                            "com.atakmap.android.maps.SHOW_DETAILS");
                    i.putExtra("uid", mi.getUID());
                    AtakBroadcast.getInstance().sendBroadcast(i);

                    _container
                            .displayPrompt(
                                    "drag the SPI along the circle to adjust");
                    mi.setMetaBoolean("lrfslide", true);
                    mi.setMetaBoolean("drag", true);
                    mapView.getMapEventDispatcher().addMapItemEventListener(mi,
                            _dragListener);

                    if (_circle != null) {
                        _circle.removeFromGroup();
                        _circle = null;
                    }
                    double lat = mi.getMetaDouble("sourceLat", Double.NaN);
                    double lon = mi.getMetaDouble("sourceLon", Double.NaN);
                    if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
                        _circle = new Circle();
                        GeoPoint anchorPoint = new GeoPoint(lat, lon);
                        double range = GeoCalculations.distanceTo(anchorPoint,
                                mi.getPoint());
                        _circle.setRadius(range);
                        _circle.setCenterPoint(
                                GeoPointMetaData.wrap(anchorPoint));
                        _circle.setStrokeColor(Color.GRAY);
                        _circle.setClickable(false);
                        _circle.setMetaBoolean("removable", false);
                        _circle.setMetaBoolean("addToObjList", false);
                        mi.getGroup().addItem(_circle);
                    }

                }
            }
        }
    };

    /**
     * When a LRF point is dropped on the map, start the editor.    If time elapses without the 
     * user dragging the shot location, cancel the edit.   If the drag is started, called editTimer
     * false to cancel the timer since the user is actively engaged with the editing process.
     */
    synchronized private void editTimer(boolean start) {
        if (_editTimer != null) {
            _editTimer.cancel();
            _editTimer.purge();
            _editTimer = null;
        }
        if (start) {
            _editTimer = new Timer("editTimer.PLRF");
            _editTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        Log.d(TAG,
                                "stopping the editor, no interaction has occurred");
                        Intent intent = new Intent(
                                "com.atakmap.android.lrf.TOGGLE_SLIDE");
                        intent.putExtra("id", spiUID);
                        AtakBroadcast.getInstance().sendBroadcast(intent);

                    } catch (Exception e) {
                        Log.e(TAG, "error: ", e);
                    }
                }
            }, 10000);

            Intent intent = new Intent("com.atakmap.android.lrf.TOGGLE_SLIDE");
            intent.putExtra("id", spiUID);
            AtakBroadcast.getInstance().sendBroadcast(intent);
        }

    }

    @Override
    public void run() {
        cancelled = false;

        while (!cancelled) {
            try {
                if (s == null) {
                    s = new DatagramSocket(null);
                    try {
                        s.setReuseAddress(true);
                    } catch (Exception e) {
                        Log.e(TAG, "error: ", e);
                    }
                    s.bind(new InetSocketAddress(port));
                }
                receivePacket.setLength(receiveData.length);
                s.receive(receivePacket);

                final String input = new String(receivePacket.getData(), 0,
                        receivePacket.getLength(),
                        FileSystemUtils.UTF8_CHARSET);

                process(input);

            } catch (IOException ioe) {
                if (!cancelled) {
                    // an IOException occurred and this thread has not yet been cancelled.
                    // close the socket, and set s to null so the next time around everything
                    // will be reinitialized.
                    try {
                        if (s != null)
                            s.close();
                        s = null;
                    } catch (Exception ignore) {
                    }
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignore) {
                    }
                }
            } catch (Exception e) {
                // catches really screwed up input parse exceptions, etc.
                Log.e(TAG, "error: ", e);
                // protect against a denial of service like condition.
                try {
                    Thread.sleep(RETRY_READ_WAIT_ERROR);
                } catch (InterruptedException ignore) {
                }
            }

        }

    }

    /**
     * This is the primary mechanism for handling all laser range finder data into the system.
     * Process a laser range finder input in the form of
     *
     *      laserRangeFinderUid, time, meters, azimuth, inclination
     *
     * or in the case of an error mode
     *
     *      laserRangeFinderUid, time, ERROR
     *
     * @param input the string in the above format.
     */
    public void process(String input) {
        // received data, clear out the SPI if it is visible
        Marker pmi = getSPI();
        pmi.setVisible(false);

        Log.i(TAG, "receive: " + input);
        String[] tokens = input.split(",");

        remove();
        if (tokens.length < 4) {
            Log.e(TAG, "bad input received: " + input);
        } else if (tokens.length < 6) {
            if (tokens[3].startsWith("UNKNOWN")) {
                toastMessage(
                        "Unknown message received from the LRF",
                        Toast.LENGTH_LONG);
            } else if (tokens[3].startsWith("COMPASS")) {
                toastMessage("Compass error reported from the LRF",
                        Toast.LENGTH_LONG);
            } else if (tokens[3].startsWith("RANGE")) {
                toastMessage("Range error reported from the LRF",
                        Toast.LENGTH_LONG);
            } else if (tokens[3].startsWith("MAIN")) {
                toastMessage("Computer error reported from the LRF",
                        Toast.LENGTH_LONG);
            }
        } else {
            RangeFinderAction rfa = this;
            if (externalAction != null) {
                Log.d(TAG, "overriding default behavior of the lrf: "
                        + externalAction.getClass());
                rfa = externalAction;
            }
            rfa.onRangeFinderInfo(tokens[1],
                    Double.parseDouble(tokens[3]),
                    Double.parseDouble(tokens[4]),
                    Double.parseDouble(tokens[5]));
        }
    }

    /**
     * Copy of the implementation of onRangeFinderInfo from Dan Carpenter. This method takes in a
     * distance, azimuth, elevation and based on the self positional computes the exact location of
     * the target.
     * 
     * @param zAngle - angle from source to target in degrees
     */
    @Override
    public void onRangeFinderInfo(String uidPrefix, final double distance,
            double azimuth,
            final double zAngle) {

        if (!showing) {
            showing = true;
            mapView.post(new Runnable() {
                @Override
                public void run() {
                    HintDialogHelper.showHint(
                            context,
                            context.getString(R.string.tool_text30),
                            ResourceUtil.getString(context,
                                    R.string.civ_tool_text31,
                                    R.string.tool_text31),
                            "lrf_menu");
                }
            });
        }
        if (Double.isNaN(distance)) {
            toastMessage("the range finder failed to provide a distance",
                    Toast.LENGTH_SHORT);
            return;
        }

        if (Double.isNaN(zAngle)) {
            toastMessage("the range finder failed to provide an inclination",
                    Toast.LENGTH_SHORT);
            return;
        }

        if (Double.isNaN(azimuth)) {
            toastMessage(context.getString(
                    R.string.lrf_without_compass), Toast.LENGTH_SHORT);
            azimuth = mapView.getMapData().getDouble("deviceAzimuth", 0.0);
        }

        PointMapItem self = ATAKUtilities.findSelf(mapView);
        if (self != null) {

            GeoPointMetaData selfPoint = self.getGeoPointMetaData();
            _formerSelf.setPoint(selfPoint);

            float declination = getDeclination(selfPoint.get(), 0);

            double levelDistance = distance * Math.cos(Math.toRadians(zAngle));
            double altDiff = Math.sqrt((distance * distance)
                    - (levelDistance * levelDistance));

            if (selfPoint.get().isAltitudeValid()) {

                final double ohae = selfPoint.get().getAltitude();

                if (zAngle > 0 && zAngle < 180)
                    hae = ohae + altDiff;
                else
                    hae = ohae - altDiff;

            }

            final GeoPoint targetPoint = GeoCalculations.pointAtDistance(
                    selfPoint.get(), (azimuth + (double) declination),
                    levelDistance);

            final GeoPoint tpWithAlt = new GeoPoint(
                    targetPoint.getLatitude(),
                    targetPoint.getLongitude(),
                    hae);

            final Marker pmi = getSPI();
            pmi.setPoint(tpWithAlt);

            origSrcPoint = self.getGeoPointMetaData();
            origDstPoint = tpWithAlt;

            pmi.setVisible(true);
            final String title = mapView.getSelfMarker().getMetaString(
                    "callsign", "")
                    + ".PLRF";
            //pmi.setTitle(title);
            pmi.setMetaString("callsign", title);
            pmi.setMetaDouble("sourceLat", self.getPoint().getLatitude());
            pmi.setMetaDouble("sourceLon", self.getPoint().getLongitude());

            final Point focus = mapView.getMapController().getFocusPoint();
            ATAKUtilities.scaleToFit(mapView, new MapItem[] {
                    _formerSelf, pmi
            },
                    focus.x * 2, focus.y * 2);
            createOrUpdate(_formerSelf, pmi);

            if (_fov != null) {
                _fov.removeFromGroup();
                _fov = null;
            }
            _fov = new SensorFOV(UUID.randomUUID().toString());
            _fov.setPoint(origSrcPoint);
            _fov.setMetaBoolean("removable", false);
            _fov.setMetaBoolean("addToObjList", false);
            final double d = origSrcPoint.get().distanceTo(origDstPoint);
            final double a = origSrcPoint.get().bearingTo(origDstPoint);
            pmi.getGroup().addItem(_fov);
            _fov.setMetrics((float) a, 20f, (float) d);
            _fov.setAlpha(.20f);
            _fov.setColor(1f, 0f, 0f);

            if (_formerSelf != null) {
                _formerSelf.removeFromGroup();
            }
            pmi.getGroup().addItem(_formerSelf);

            Intent intent = new Intent(
                    "com.atakmap.android.maps.SHOW_DETAILS");
            intent.putExtra("uid", pmi.getUID());
            AtakBroadcast.getInstance().sendBroadcast(intent);

            startTimer();
            editTimer(true);
        } else {

            Marker pmi = getSPI();
            pmi.setVisible(false);

            remove();
            toastMessage(
                    context.getString(R.string.lrf_without_self_marker)
                            + Math.round(distance) + " meters.",
                    Toast.LENGTH_LONG);

        }
    }

    /**
     * returns the magnetic declination for a given GeoPoint and altitude
     * 
     * @param gp - a valid geoPoint, if null 0 is returned as the declination
     * @param altitudeFeetAGL - altitude in feet above ground level
     */
    private static float getDeclination(GeoPoint gp, int altitudeFeetAGL) {
        if (gp == null)
            return 0;

        // convert to altitude in meters
        float altMeters = (float) (altitudeFeetAGL
                * ConversionFactors.FEET_TO_METERS);

        // find the declination in
        Date d = CoordinatedTime.currentDate();
        GeomagneticField gmf = new GeomagneticField((float) gp.getLatitude(),
                (float) gp.getLongitude(), altMeters, d.getTime());
        return gmf.getDeclination();
    }

    /**
     * The original implementation utilized toasts to notify the user of an issue.
     */
    public void toastMessage(final String message, final int toast_length) {
        ((Activity) context).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message, toast_length).show();
            }
        });
    }

    synchronized private void startTimer() {
        if (_timer != null) {
            _timer.cancel();
            _timer.purge();
            sendCot(0);
        }

        _timer = new Timer("PublishSPI.PLRF");
        _timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    sendCot(20);
                } catch (Exception e) {
                    Log.e(TAG, "error: ", e);
                }
            }
        }, 0, _updateTimeout * 1000);
    }

    Marker mi;

    private void sendCot(final int staleout) {

        if (staleout > 0) {
            mi = (Marker) mapView.getMapItem(spiUID);
        } else {
            Log.d(TAG, "send a stale out message:" + spiUID);
        }

        if (mi == null) {
            Log.d(TAG, "marker not found on the map:" + spiUID);
            return;
        }
        mi.setMetaInteger("cotDefaultStaleSeconds", staleout);

        GeoPoint point = mi.getPoint();
        if (point.getLatitude() == 0 && point.getLongitude() == 0)
            return; // Default values, don't publish anything.

        // Force internal replication only if flagged true
        Bundle persistExtras = new Bundle();
        persistExtras.putBoolean("internal", false);
        mi.persist(mapView.getMapEventDispatcher(), persistExtras,
                this.getClass());

        if (staleout == 0) {
            // 8/29/2016 if staleout == 0, then the marker likely has been deleted.
            // so CotMarkerRefresher will no longer act on the persist.   Send it out 
            // the older way.
            CotEvent event = com.atakmap.android.importexport.CotEventFactory
                    .createCotEvent(mi);
            CotMapComponent.getExternalDispatcher().dispatch(event);
        }
    }

    synchronized private void stopTimer() {
        if (_timer != null) {
            _timer.cancel();
            _timer.purge();
            sendCot(0);
            _timer = null;
        }
    }

    private final OnPointChangedListener opcl = new OnPointChangedListener() {
        @Override
        public void onPointChanged(final PointMapItem item) {
            if (item.getVisible())
                calc.change();
        }

    };

    private class Updater implements Runnable, Disposable {
        boolean disposed;
        int state;
        final Thread thread;

        public Updater() {
            this.disposed = false;
            this.state = 0;

            this.thread = new Thread(this);
            this.thread.setPriority(Thread.NORM_PRIORITY);
            this.thread.setName("Updater-LocalRangeFinder");
            this.thread.start();
        }

        public synchronized void change() {
            this.state++;
            this.notify();
        }

        @Override
        public void dispose() {
            synchronized (this) {
                this.disposed = true;
                this.notify();
            }
        }

        @Override
        public void run() {
            try {
                int compute = 0;
                while (true) {
                    synchronized (this) {
                        if (this.disposed)
                            break;
                        if (compute == this.state) {
                            try {
                                this.wait();
                            } catch (InterruptedException ignored) {
                            }
                            continue;
                        }
                        compute = this.state;
                    }

                    sendCot(20);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                    }
                }
            } finally {
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(
            SharedPreferences sharedPreferences, String key) {

        if (key == null)
            return;

        if (key.equals("spiUpdateDelay")) {

            updateTimeout();

            // protection for if the update timeout is set to 0;
            if (_updateTimeout < 1)
                _updateTimeout = 1;

            synchronized (LocalRangeFinderInput.this) {
                if (_timer != null) {
                    Log.d(TAG,
                            "SPIP update delay changed restart sending thread");
                    stopTimer();
                    startTimer();
                }
            }
        }
    }

    public void dispose() {
        _prefs.unregisterOnSharedPreferenceChangeListener(this);
        AtakBroadcast.getInstance().unregisterReceiver(_receiver);
        AtakBroadcast.getInstance().unregisterReceiver(br);
        cancel();
    }

}
