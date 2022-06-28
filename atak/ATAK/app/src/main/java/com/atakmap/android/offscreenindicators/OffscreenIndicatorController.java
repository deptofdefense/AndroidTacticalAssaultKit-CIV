
package com.atakmap.android.offscreenindicators;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.preference.PreferenceManager;

import com.atakmap.android.icons.Icon2525cIconAdapter;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.layer.AbstractLayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class OffscreenIndicatorController extends AbstractLayer implements
        MapEventDispatcher.MapEventDispatchListener,
        MapItem.OnZOrderChangedListener {

    public static final String TAG = "OffscreenIndicatorController";

    private static final float HALO_BITMAP_SIZE = 24 * MapView.DENSITY;
    public static final float HALO_BORDER_SIZE = 48 * MapView.DENSITY;

    private final static int COLOR_FRIENDLY = Color.argb(255, 128, 224, 255);
    private final static int COLOR_HOSTILE = Color.argb(255, 255, 128, 128);
    private final static int COLOR_NEUTRAL = Color.argb(255, 170, 255, 170);
    private final static int COLOR_UNKNOWN = Color.argb(255, 225, 255, 128);
    private final static int COLOR_WAYPOINT = Color.argb(255, 241, 128, 33);
    private final static int DEFAULT_COLOR = Color.argb(255, 0, 0, 0);

    private final Set<Marker> markers = new TreeSet<>(
            MapItem.ZORDER_HITTEST_COMPARATOR);
    private MapView _mapView;
    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;
    private final Set<OnOffscreenIndicatorsEnabledListener> enabledListeners = new HashSet<>();
    private final Set<OnOffscreenIndicatorsThresholdListener> thresholdListeners = new HashSet<>();
    private final Set<OnItemsChangedListener> itemsChangedListeners = new HashSet<>();
    private boolean enabled = true;
    private boolean zDirty;

    /** display threshold, in meters */
    private double threshold = 0d;
    private double timeout = 0d;

    public OffscreenIndicatorController(MapView mapView, Context context) {
        super("Offscreen Indicators");

        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.registerOnSharedPreferenceChangeListener(
                listener = new SharedPreferences.OnSharedPreferenceChangeListener() {

                    @Override
                    public void onSharedPreferenceChanged(
                            SharedPreferences sharedPreferences, String key) {
                        if (key == null)
                            return;

                        switch (key) {
                            case "toggle_offscreen_indicators":
                                enabled = prefs.getBoolean(key, true);
                                dispatchOnOffscreenIndicatorsEnabled();
                                break;
                            case "offscreen_indicator_dist_threshold":
                                try {
                                    threshold = Double
                                            .parseDouble(prefs.getString(key,
                                                    "10"))
                                            * 1000;

                                    dispatchOnOffscreenIndicatorsThresholdChanged();
                                } catch (NumberFormatException e) {
                                    // do nothing
                                }

                                break;
                            case "offscreen_indicator_timeout_threshold":
                                try {
                                    timeout = Double
                                            .parseDouble(prefs.getString(key,
                                                    "60"))
                                            * 1000;

                                    dispatchOnOffscreenIndicatorsThresholdChanged();
                                } catch (NumberFormatException e) {
                                    // do nothing
                                }
                                break;
                        }

                    }

                });

        enabled = prefs.getBoolean("toggle_offscreen_indicators", true);
        try {
            threshold = Double.parseDouble(prefs.getString(
                    "offscreen_indicator_dist_threshold",
                    "10")) * 1000;
        } catch (NumberFormatException e) {
            // TODO
        }
        try {
            timeout = Double.parseDouble(prefs.getString(
                    "offscreen_indicator_timeout_threshold",
                    "60")) * 1000;
        } catch (NumberFormatException e) {
            // TODO
        }

        _mapView = mapView;

        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_CLICK, this);

        this.zDirty = true;
    }

    public synchronized void addOnOffscreenIndicatorsEnabledListener(
            OnOffscreenIndicatorsEnabledListener l) {
        this.enabledListeners.add(l);
    }

    public synchronized void removeOnOffscreenIndicatorsEnabledListener(
            OnOffscreenIndicatorsEnabledListener l) {
        this.enabledListeners.remove(l);
    }

    private synchronized void dispatchOnOffscreenIndicatorsEnabled() {
        for (OnOffscreenIndicatorsEnabledListener l : this.enabledListeners)
            l.onOffscreenIndicatorsEnabled(this);
    }

    public synchronized void addOnOffscreenIndicatorsThresholdListener(
            OnOffscreenIndicatorsThresholdListener l) {
        this.thresholdListeners.add(l);
    }

    public synchronized void removeOnOffscreenIndicatorsThresholdListener(
            OnOffscreenIndicatorsThresholdListener l) {
        this.thresholdListeners.remove(l);
    }

    private synchronized void dispatchOnOffscreenIndicatorsThresholdChanged() {
        for (OnOffscreenIndicatorsThresholdListener l : this.thresholdListeners)
            l.onOffscreenIndicatorsThresholdChanged(this);
    }

    public synchronized void addOnItemsChangedListener(
            OnItemsChangedListener l) {
        this.itemsChangedListeners.add(l);
    }

    public synchronized void removeOnItemsChangedListener(
            OnItemsChangedListener l) {
        this.itemsChangedListeners.remove(l);
    }

    private synchronized void dispatchOnItemsChanged() {
        for (OnItemsChangedListener l : this.itemsChangedListeners)
            l.onItemsChanged(this);
    }

    public synchronized void dispose() {
        _mapView.getMapEventDispatcher().removeMapEventListener(
                MapEvent.MAP_CLICK, this);
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
        listener = null;
        prefs = null;
        this.markers.clear();
        _mapView = null;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        //this.dispatchOnOffscreenIndicatorsEnabled();
        prefs.edit().putBoolean("toggle_offscreen_indicators", enabled)
                .apply();
    }

    public boolean getEnabled() {
        return enabled;
    }

    public synchronized void addMarker(Marker marker) {
        this.markers.add(marker);
        marker.addOnZOrderChangedListener(this);
        this.dispatchOnItemsChanged();
    }

    public synchronized void getMarkers(Set<Marker> markers) {
        markers.addAll(this.markers);
    }

    /**
     * Returns the threshold for indicators, in meters. Items that are further
     * than the returned distance from the center of the view will not have an
     * indicator displayed.
     * 
     * @return  The threshold for indicators in meters.
     */
    public double getThreshold() {
        return this.threshold;
    }

    /**
     * Returns the timeout used before removing an current offscreen indicator
     * @return the timeout in milliseconds
     */
    public double getTimeout() {
        return this.timeout;
    }

    public synchronized void removeMarker(Marker marker) {
        marker.removeOnZOrderChangedListener(this);

        this.validateZOrderedList();
        this.markers.remove(marker);
        this.dispatchOnItemsChanged();
    }

    private void validateZOrderedList() {
        if (this.zDirty) {
            Collection<Marker> unordered = new ArrayList<>(this.markers);
            this.markers.clear();
            this.markers.addAll(unordered);
            this.zDirty = false;
        }
    }

    public float getHaloIconSize() {
        return HALO_BITMAP_SIZE;
    }

    public float getHaloBorderSize() {
        return HALO_BORDER_SIZE;
    }

    public int getArcColor(Marker marker) {
        int color = 0;
        if (marker.hasMetaValue("team")) {

            color = Icon2525cIconAdapter
                    .teamToColor(marker.getMetaString("team", "white"));

        } else if (marker.hasMetaValue("offscreen_arccolor")) {
            color = marker.getMetaInteger("offscreen_arccolor", DEFAULT_COLOR);
        } else if (marker.hasMetaValue("type")) {
            String type = marker.getType();
            if (type.startsWith("a-f")) {
                color = COLOR_FRIENDLY;
            } else if (type.startsWith("a-h")) {
                color = COLOR_HOSTILE;
            } else if (type.startsWith("a-n")) {
                color = COLOR_NEUTRAL;
            } else if (type.startsWith("a-u")) {
                color = COLOR_UNKNOWN;
            } else if (type.equals("b-m-p-w")) { // waypoint marker
                color = COLOR_WAYPOINT;
            }
        }
        return color;
    }

    private void computeHaloPoint(PointF screenPoint, RectF haloBorder) {
        screenPoint.x = Math.min(screenPoint.x, haloBorder.right);
        screenPoint.x = Math.max(screenPoint.x, haloBorder.left);
        screenPoint.y = Math.min(screenPoint.y, haloBorder.bottom);
        screenPoint.y = Math.max(screenPoint.y, haloBorder.top);
    }

    /**************************************************************************/
    // Map Event Dispatch Listener

    @Override
    public synchronized void onMapEvent(MapEvent event) {

        if (!enabled)
            return;

        PointF point = event.getPointF();
        // if the touch occurred on the region inside of where the offscreen
        // indicator halos are rendered, ignore
        final float borderSize = (HALO_BORDER_SIZE + HALO_BITMAP_SIZE);
        if ((point.x > borderSize
                && point.x < (_mapView.getWidth() - borderSize))
                &&
                (point.y > borderSize &&
                        point.y < (_mapView.getHeight() - borderSize))) {

            return;
        }

        GeoPointMetaData geo = _mapView.inverse(point.x, point.y,
                MapView.InverseMode.RayCast);
        final RectF haloRect = new RectF(HALO_BORDER_SIZE, HALO_BORDER_SIZE,
                _mapView.getWidth() - HALO_BORDER_SIZE,
                _mapView.getHeight() - HALO_BORDER_SIZE);

        this.validateZOrderedList();

        Marker candidate = null;
        double distance;
        PointF screen;
        float dx;
        float dy;
        final float haloBitmapSizeSquared = (HALO_BITMAP_SIZE
                * HALO_BITMAP_SIZE);
        for (Marker m : this.markers) {
            if (m.hasMetaValue("disable_offscreen_indicator")
                    && m.getMetaBoolean("disable_offscreen_indicator", false)) {
                continue;
            }
            distance = MapItem.computeDistance(m, geo.get());
            if (Double.isNaN(distance) || distance > this.threshold)
                continue;
            screen = _mapView.forward(m.getPoint());
            if (screen.x >= 0 && screen.x < _mapView.getWidth()
                    && screen.y >= 0 && screen.y < _mapView.getHeight())
                continue;
            computeHaloPoint(screen, haloRect);

            dx = (screen.x - point.x);
            dy = (screen.y - point.y);

            if ((dx * dx + dy * dy) < haloBitmapSizeSquared) {
                candidate = m;
                break;
            }
        }

        if (candidate != null) {
            String uid = candidate.getUID();
            if (uid != null) {
                Intent focusIntent = new Intent();
                focusIntent.setAction("com.atakmap.android.maps.FOCUS");
                focusIntent.putExtra("uid", uid);
                AtakBroadcast.getInstance().sendBroadcast(focusIntent);

                Intent menuIntent = new Intent();
                menuIntent.setAction("com.atakmap.android.maps.SHOW_MENU");
                menuIntent.putExtra("uid", uid);
                AtakBroadcast.getInstance().sendBroadcast(menuIntent);

                Intent detailsIntent = new Intent();
                detailsIntent
                        .setAction("com.atakmap.android.maps.SHOW_DETAILS");
                detailsIntent.putExtra("uid", uid);
                AtakBroadcast.getInstance().sendBroadcast(detailsIntent);
            }
        }
    }

    /**************************************************************************/
    // MapItem OnZOrderChangedListener

    @Override
    public synchronized void onZOrderChanged(MapItem item) {
        this.zDirty = true;
    }

    /**************************************************************************/

    public interface OnOffscreenIndicatorsEnabledListener {
        void onOffscreenIndicatorsEnabled(
                OffscreenIndicatorController controller);
    }

    public interface OnOffscreenIndicatorsThresholdListener {
        void onOffscreenIndicatorsThresholdChanged(
                OffscreenIndicatorController controller);
    }

    public interface OnItemsChangedListener {
        void onItemsChanged(OffscreenIndicatorController controller);
    }
}
