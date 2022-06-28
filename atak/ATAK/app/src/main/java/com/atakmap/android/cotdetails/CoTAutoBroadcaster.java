
package com.atakmap.android.cotdetails;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;

import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 *
 * This class holds a list of markers to
 * auto broadcast to any users on the same network.
 * It uses a timer similar to the SPI tool.
 */
public class CoTAutoBroadcaster implements
        SharedPreferences.OnSharedPreferenceChangeListener,
        MapEventDispatcher.MapEventDispatchListener {

    public static final String TAG = "CoTAutoBroadcaster";
    private static final String FILENAME = "autobroadcastmarkers.dat";

    private Timer _timer;
    private int _updateTimeout;
    private final SharedPreferences _prefs;

    private final ArrayList<String> _markers;

    private final MapView _mapView;
    private static CoTAutoBroadcaster _instance;

    /**
     * Listen to changes to the markers currently being auto broadcasted.
     */
    public interface AutoBroadcastListener {
        /**
         * A marker has been added to the autobroadcast queue. 
         */
        void markerAdded(PointMapItem pmi);

        /**
         * A marker has been removed from the autobroadcast queue. 
         */
        void markerRemoved(PointMapItem pmi);
    }

    private final List<AutoBroadcastListener> listeners = new ArrayList<>();

    private CoTAutoBroadcaster(final MapView view) {
        _mapView = view;

        _markers = new ArrayList<>();
        _prefs = PreferenceManager.getDefaultSharedPreferences(_mapView
                .getContext());
        _prefs.registerOnSharedPreferenceChangeListener(this);
        _updateTimeout = Integer.parseInt(_prefs.getString("hostileUpdateDelay",
                "60")); // default to 60 seconds
        loadMarkers();
        addMapListener();
        _instance = this;
        startTimer();

    }

    /**
     * Add a listener for changes to which markers are auto broadcasted.
     * @param abl the listener for the autobroadcast event.
     */
    public void addAutoBroadcastListener(AutoBroadcastListener abl) {
        synchronized (listeners) {
            listeners.add(abl);
        }
    }

    /**
     * Remove a listener for changes to which markers are auto broadcasted.
     * @param abl the listener for the autobroadcast event.
     */
    public void removeAutoBroadcastListener(AutoBroadcastListener abl) {
        synchronized (listeners) {
            listeners.remove(abl);
        }
    }

    synchronized public static CoTAutoBroadcaster getInstance() {
        if (_instance == null)
            _instance = new CoTAutoBroadcaster(MapView.getMapView());
        return _instance;
    }

    /**
     * Registers the appropriate map listeners used during one of the selection
     * states.   Care should be taken to call _removeMapListener() in order to
     * restore the previous state of the Map Interface.
     */

    private void addMapListener() {
        MapEventDispatcher dispatcher = _mapView.getMapEventDispatcher();
        dispatcher.addMapEventListener(MapEvent.ITEM_REMOVED, this);
    }

    /**
     * This  method will load the marker ID's
     * from a list stored in Databases
     */
    private void loadMarkers() {
        //load markers from list
        File inputFile = new File(Environment.getExternalStorageDirectory()
                .getAbsoluteFile()
                + "/atak/Databases/" + FILENAME);
        if (IOProviderFactory.exists(inputFile)) {
            try (InputStream is = IOProviderFactory.getInputStream(inputFile)) {
                byte[] temp = new byte[is.available()];
                int read = is.read(temp);
                String menuString = new String(temp, 0, read,
                        FileSystemUtils.UTF8_CHARSET);
                String[] lines = menuString.split("\n");
                for (String line : lines) {
                    // mark as autobroadcast
                    synchronized (_markers) {
                        _markers.add(line);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "error occurred reading the list of hostiles", e);
            }
        } else
            Log.d(TAG, "File not found: " + FILENAME);

    }

    /**
     * Save the marker ID's to a
     * file in Databases
     */
    private void saveMarkers() {
        final File outputFile = FileSystemUtils
                .getItem("Databases/" + FILENAME);

        if (IOProviderFactory.exists(outputFile))
            FileSystemUtils.delete(outputFile);

        StringBuilder builder = new StringBuilder();
        synchronized (_markers) {
            if (_markers.isEmpty()) {
                return;
            }
            for (String m : _markers) {
                if (m != null) {
                    builder.append(m);
                    builder.append("\n");
                }
            }
        }

        try (OutputStream os = IOProviderFactory.getOutputStream(outputFile)) {
            try (InputStream is = new ByteArrayInputStream(builder.toString()
                    .getBytes())) {
                FileSystemUtils.copy(is, os);
            }
        } catch (IOException e) {
            Log.e(TAG, "error occurred", e);
        }
    }

    /**
     * When the 'Broadcast' button
     * is toggled on, add the marker ID to
     * the marker list
     */
    public void addMarker(final Marker m) {
        synchronized (_markers) {
            if (_markers.contains(m.getUID())) {
                return;
            }
            stopTimer();
            _markers.add(m.getUID());
            startTimer();
            saveMarkers();
        }
        synchronized (listeners) {
            for (AutoBroadcastListener abl : listeners) {
                try {
                    abl.markerAdded(m);
                } catch (Exception e) {
                    Log.e(TAG, "error", e);
                }
            }
        }
    }

    /**
     * When the 'Broadcast' button
     * is toggled off, remove the marker
     * from the marker list.
     */
    public void removeMarker(final Marker m) {
        synchronized (_markers) {
            if (_markers.contains(m.getUID())) {
                stopTimer();

                Log.d(TAG, "Removing MARKER: " +
                        (m).getTitle());

                _markers.remove(m.getUID());

                send(m.getUID());

                startTimer();
                saveMarkers();
            }
        }
        synchronized (listeners) {
            for (AutoBroadcastListener abl : listeners) {
                try {
                    abl.markerRemoved(m);
                } catch (Exception e) {
                    Log.e(TAG, "error", e);
                }
            }
        }
    }

    /**
     * Check to see if a marker
     * has broadcast 'on'
     * @param m - the marker
     * @return - true if marker
     * is in the brodcast list
     */
    public boolean isBroadcast(final Marker m) {
        synchronized (_markers) {
            return _markers.contains(m.getUID());
        }
    }

    /**
     * Broadcast the markers in the
     * broadcast list
     */
    private void broadcastCoTs() {
        //send the CoT markers
        synchronized (_markers) {
            for (String m : _markers) {
                // no need to synchonize on the timeout
                send(m);
            }
        }
    }

    private void send(String m) {
        MapItem marker = findTarget(m);
        if (marker instanceof Marker) {
            Bundle persistExtras = new Bundle();
            persistExtras.putBoolean("internal", false);
            marker.persist(_mapView.getMapEventDispatcher(),
                    persistExtras,
                    this.getClass());
        }
    }

    private MapItem findTarget(final String targetUID) {
        MapItem item = null;
        if (targetUID != null) {
            MapGroup rootGroup = _mapView.getRootGroup();
            if (rootGroup != null)
                item = rootGroup.deepFindUID(targetUID);
        }
        return item;
    }

    synchronized private void startTimer() {
        stopTimer();

        // disabled
        if (_updateTimeout == 0)
            return;

        Log.d(TAG,
                "restarting the basic auto cot broadcaster: " + _updateTimeout);
        _timer = new Timer("BroadcastCoT");
        _timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    broadcastCoTs();
                } catch (Exception e) {
                    Log.e(TAG, "error: ", e);
                }
            }
        }, 0, _updateTimeout * 1000L);
    }

    synchronized private void stopTimer() {
        if (_timer != null) {
            Log.d(TAG, "stopping the basic auto cot broadcaster");
            _timer.cancel();
            _timer.purge();
            _timer = null;
        }
    }

    @Override
    public void onSharedPreferenceChanged(
            SharedPreferences sharedPreferences, String key) {

        if (key == null)
            return;

        if (key.equals("hostileUpdateDelay")) {
            // default to 60 seconds
            _updateTimeout = Integer.parseInt(_prefs.getString(key,
                    "60"));
            stopTimer();
            startTimer();
        }
    }

    @Override
    public void onMapEvent(MapEvent event) {
        if (event.getType().equals(MapEvent.ITEM_REMOVED)) {
            MapItem item = event.getItem();
            if (item instanceof Marker) {
                Marker m = (Marker) item;
                synchronized (_markers) {
                    if (_markers.contains(m.getUID()))
                        removeMarker(m);
                }
            }
        }
    }

}
