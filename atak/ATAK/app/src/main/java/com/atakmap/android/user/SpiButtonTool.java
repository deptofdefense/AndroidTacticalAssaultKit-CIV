
package com.atakmap.android.user;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.ImageButton;
import android.widget.Toast;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.PointMapItem.OnPointChangedListener;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbars.RangeAndBearingMapComponent;
import com.atakmap.app.R;
import com.atakmap.app.system.ResourceUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.Timer;
import java.util.TimerTask;

/**
 *
 */
public class SpiButtonTool extends SpecialPointButtonTool implements
        MapItem.OnVisibleChangedListener {

    public static final String TAG = "SpiButtonTool";
    public static final String IDENTIFIER = "com.atakmap.android.user.SPIBUTTONTOOL";
    public static final String SPI_OFF = "com.atakmap.android.user.SPI_OFF";

    private final int _index;
    private Timer _timer;
    private int _updateTimeout;
    private final SharedPreferences _prefs;

    /**
     * This tool is similar to the SelectPointButtonTool, but only one instance of each button
     * should exist, making it easy to define.  Unlike the SelectPointButtonTool, the SpiButtonTool
     * needs to publish its information should it be turned on (enabled or disabled state).  The
     * index specified pulls the resources to draw the appropriate icon.  Currently there are only
     * 1, 2, and 3 available, but additional would simply be a matter of creating new resources.
     * Perhaps these can be created on the fly at some point.
     * @param mapView MapView used to construct this button
     * @param button Button to associated with this tool
     * @param index
     */

    public SpiButtonTool(MapView mapView, ImageButton button, int index) {
        super(mapView, button, IDENTIFIER + index);
        _index = index;
        ToolManagerBroadcastReceiver.getInstance().registerTool(
                IDENTIFIER + _index, this);

        DocumentedIntentFilter intentFilter = new DocumentedIntentFilter();
        intentFilter.addAction(SPI_OFF);
        AtakBroadcast.getInstance().registerReceiver(br, intentFilter);

        _iconEnabled = getResourceId("ic_spi" + _index + "_enabled");
        _iconDisabled = getResourceId("ic_spi" + _index + "_disabled");
        _iconOff = getResourceId("ic_spi" + _index + "_off");
        _timer = new Timer();

        _prefs = PreferenceManager.getDefaultSharedPreferences(_mapView
                .getContext());
        _prefs.registerOnSharedPreferenceChangeListener(_prefsChangedListener);
        updateTimeout();
    }

    @Override
    public void dispose() {
        if (_marker != null) {
            _marker.removeOnPointChangedListener(opcl);
            _marker.removeOnVisibleChangedListener(this);
        }
        AtakBroadcast.getInstance().unregisterReceiver(br);

        if (_timer != null) {
            _timer.cancel();
            _timer.purge();
            _timer = null;
        }
        super.dispose();
    }

    synchronized private void startTimer() {
        if (_timer != null) {
            _timer.cancel();
            _timer.purge();
            sendCot(0);
        }
        _timer = new Timer("PublishSPI" + _index);
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

    /**
     * Sending out a SPI with a specific stale out time of 20 seconds.
     */
    private void sendCot(final int staleout) {
        if (_marker == null) {
            Log.w(TAG, "No SPI marker, cannot send");
            return;
        }

        _marker.setMetaInteger("cotDefaultStaleSeconds", staleout);

        GeoPoint point = _marker.getPoint();
        if (point.getLatitude() == 0 && point.getLongitude() == 0)
            return; // Default values, don't publish anything.

        // Force internal replication only if flagged true
        Bundle persistExtras = new Bundle();
        persistExtras.putBoolean("internal", false);
        _marker.persist(_mapView.getMapEventDispatcher(), persistExtras,
                this.getClass());

    }

    synchronized private void stopTimer() {
        if (_timer != null) {
            _timer.cancel();
            _timer.purge();
            sendCot(0);
            _timer = null;
        }
    }

    private void updateTimeout() {
        try {
            _updateTimeout = Integer.parseInt(_prefs.getString(
                    "spiUpdateDelay", "5"));
        } catch (Exception e) {
            _updateTimeout = 0;
        }
        if (_updateTimeout <= 0) {
            _updateTimeout = 5;
            _prefs.edit().putString("spiUpdateDelay", "5").apply();
            Toast.makeText(_mapView.getContext(),
                    ResourceUtil.getResource(R.string.civ_point_dropper_text40,
                            R.string.point_dropper_text40),
                    Toast.LENGTH_LONG).show();
        }
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener _prefsChangedListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(
                SharedPreferences sharedPreferences, String key) {

            if (key == null)
                return;

            if (key.equals("spiUpdateDelay")) {
                // default to 5 seconds
                updateTimeout();
                synchronized (this) {
                    if (_timer != null) {
                        Log.d(TAG,
                                "SPI"
                                        + _index
                                        + " update delay changed restart sending thread");
                        stopTimer();
                        startTimer();
                    }
                }
            }
        }
    };

    @Override
    public boolean onToolBegin(Bundle bundle) {
        String callsign = _mapView.getSelfMarker().getMetaString("callsign",
                null);

        // if the marker has already, been created - remove the original listeners
        // this keeps the behavior close to what is currently is doing - but 
        // does not continually add listeners
        if (_marker != null) {
            _marker.removeOnPointChangedListener(opcl);
            _marker.removeOnVisibleChangedListener(this);
        }
        createMarker(
                "icons/spi" + _index + "_icon.png",
                _mapView.getSelfMarker().getUID() + ".SPI" + _index,
                callsign + "."
                        + ResourceUtil.getString(_mapView.getContext(),
                                R.string.civ_spi_abbrev, R.string.spi_abbrev)
                        + _index,
                "menus/spi_menu.xml",
                "com.atakmap.android.user.SPI_OFF");

        startTimer();
        _marker.addOnVisibleChangedListener(this);
        _marker.addOnPointChangedListener(opcl);

        return super.onToolBegin(bundle);
    }

    @Override
    public void onVisibleChanged(MapItem item) {
        if (item.getVisible())
            startTimer();
        else
            stopTimer();
    }

    private final BroadcastReceiver br = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            // not active
            if (_marker == null)
                return;
            String uid = intent.getStringExtra("targetUID");
            if (SPI_OFF.equals(action) && uid.equals(_marker.getUID())) {
                off();
            }
        }
    };

    private final OnPointChangedListener opcl = new OnPointChangedListener() {
        @Override
        public void onPointChanged(final PointMapItem item) {
            if (item.getVisible()) {
                sendCot(20);
                Marker marker = (Marker) RangeAndBearingMapComponent
                        .getGroup()
                        .deepFindItem("uid", item.getUID() + ".rangeRing");
                GeoPoint p = item.getPoint();
                if (marker != null) {
                    marker.setPoint(p);
                }
            }
        }
    };

}
