
package com.atakmap.android.user;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.widgets.FahArrowWidget;
import com.atakmap.android.toolbars.RangeAndBearingMapItem;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.UUID;

import com.atakmap.android.maps.PointMapItem;
import com.atakmap.math.MathUtils;

/**
 *
 */
public class SelectPointButtonTool extends SpecialPointButtonTool
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "SelectPointButtonTool";
    private static final String IDENTIFIER = "com.atakmap.android.user.SELECTPOINTBUTTONTOOL";
    protected static final String REDX_FAH = "com.atakmap.android.maps.REDX_FAH";
    protected static final String REDX_LB = "com.atakmap.android.maps.REDX_LB";
    public static final String REDX_SELF = "com.atakmap.android.maps.PAIRING_LINE_SELF";
    public static final String SPI_SELF = "com.atakmap.android.maps.SPI_PAIRING_LINE_SELF";
    protected static final String SPI_FAH = "com.atakmap.android.maps.SPI_FAH";
    protected static final String SPI_LB = "com.atakmap.android.maps.SPI_LB";

    private static SelectPointButtonTool _instance;
    protected RangeAndBearingMapItem rb;
    private MapGroup _rootLayoutWidget;

    private final SharedPreferences _prefs;

    // inventory of DesignatorTargetLines and the associated uid's should only be manipulated by the fetchDesignatorTargetLine
    private static final Map<String, DesignatorTargetLine> lbs = new HashMap<>();
    // inventory of FahArrowWidget.Item and the associated uid's should only be manipulated by the fetchFahArrowWidgetTargetLine
    private static final Map<String, FahArrowWidget.Item> fahs = new HashMap<>();

    /**
     * This has a getInstance method because there are multiple places where the button can
     * reside.  Currently it exists in the R+B toolbar and the Fires toolbar.  There needs to
     * be only one instance of the tool, but due to UI constraints, it is necessary to have a new
     * button for each toolbar view.  To achieve this getInstance creates a new instance if one
     * does not exist, and appends the button view to the list of buttons associated with this tool
     * if it already exists.  All the buttons are updated whenever an action is executed.
     * @param mapView MapView to use to construct this instance
     * @param button Button that will invoke this tool
     * @return New instance if none previously existed, or previous instance with addition of
     * specified button
     */

    synchronized public static SelectPointButtonTool getInstance(
            MapView mapView,
            ImageButton button) {
        if (_instance == null)
            _instance = new SelectPointButtonTool(mapView, button);
        else if (button != null)
            _instance.addButton(button);
        return _instance;
    }

    protected SelectPointButtonTool(MapView mapView, ImageButton button) {
        super(mapView, button, IDENTIFIER);
        ToolManagerBroadcastReceiver.getInstance().registerTool(IDENTIFIER,
                this);
        _iconEnabled = _iconOff = R.drawable.nav_redx;
        _iconDisabled = R.drawable.nav_redx_locked;

        _rootLayoutWidget = mapView.getRootGroup().findMapGroup("REDX");
        if (_rootLayoutWidget == null) {
            _rootLayoutWidget = mapView.getRootGroup().addGroup("REDX");
        }

        _prefs = PreferenceManager
                .getDefaultSharedPreferences(mapView.getContext());
        _prefs.registerOnSharedPreferenceChangeListener(this);

        DocumentedIntentFilter intentFilter = new DocumentedIntentFilter();
        intentFilter.addAction(REDX_LB);
        intentFilter.addAction(REDX_FAH);
        intentFilter.addAction(REDX_SELF);
        intentFilter.addAction(SPI_SELF);
        intentFilter.addAction(SPI_FAH);
        intentFilter.addAction(SPI_LB);
        AtakBroadcast.getInstance().registerReceiver(br, intentFilter);

    }

    @Override
    protected String getNavReference() {
        return "redx.xml";
    }

    protected FahArrowWidget.Item fetchFahArrowWidget(PointMapItem mi) {
        boolean newFah = false;
        FahArrowWidget.Item item;
        synchronized (fahs) {
            item = fahs.get(mi.getUID());
        }
        if (item == null) {
            item = new FahArrowWidget.Item(_mapView);
            item.getFAH().setDrawReverse(false);
            item.getFAH().enableStandaloneManipulation();
            item.getFAH().setStrokeColor(Color.argb(255, 255, 255, 0));
            item.getFAH().setTargetItem(mi);
            item.setClickable(mi.hasMetaValue("fahon"));
            item.getFAH().setVisible(mi.hasMetaValue("fahon"));
            item.getFAH().setDesignatorItem(null);
            _rootLayoutWidget.addItem(item);
            newFah = true;
        }
        updateFahWidth(item);
        if (newFah) {
            synchronized (fahs) {
                fahs.put(mi.getUID(), item);
            }
        }
        return item;
    }

    private void updateFahWidth(FahArrowWidget.Item item) {
        if (item == null || item.getFAH() == null)
            return;
        try {
            int width = Integer.parseInt(_prefs.getString(
                    "spiFahSize", "30"));
            item.getFAH().setFahWidth(MathUtils.clamp(
                    Math.round(width / 10f) * 10, 10, 350));
        } catch (Exception nfe) {
            Log.d(TAG, "error occurred parsing number", nfe);
        }
    }

    public DesignatorTargetLine fetchDesignatorTargetLine(String uid) {
        boolean newLine = false;
        DesignatorTargetLine line;
        synchronized (lbs) {
            line = lbs.get(uid);
        }
        if (line == null) {
            line = new DesignatorTargetLine(_mapView, _rootLayoutWidget);
            newLine = true;
        }
        if (newLine) {
            synchronized (lbs) {
                lbs.put(uid, line);
            }
        }
        return line;
    }

    private void destroyDesignatorTargetLine(String uid) {
        DesignatorTargetLine val;
        synchronized (lbs) {
            val = lbs.remove(uid);
        }
        if (val != null)
            val.end();
    }

    private void destroyFahArrowWidget(String uid) {
        FahArrowWidget.Item val;
        synchronized (fahs) {
            val = fahs.remove(uid);
        }
        if (val != null)
            _rootLayoutWidget.removeItem(val);
    }

    @Override
    public boolean onToolBegin(Bundle bundle) {
        createMarker("icons/select_point_icon.png", "Red X",
                "Red X", "menus/redx_menu.xml",
                "com.atakmap.android.user.REDX_OFF");
        final FahArrowWidget.Item fah = fetchFahArrowWidget(_marker);
        final DesignatorTargetLine _laserbasket = fetchDesignatorTargetLine(
                _marker.getUID());

        synchronized (this) {
            PointMapItem self = _mapView.getSelfMarker();
            if (rb == null) {
                rb = RangeAndBearingMapItem
                        .createOrUpdateRABLine(UUID.randomUUID().toString(),
                                self, _marker, false);
                rb.setTitle("Self to Red X");
            }
            rb.setMetaBoolean("removable", false);
            _mapView.getRootGroup().addItem(rb);

            _marker.addOnVisibleChangedListener(
                    new MapItem.OnVisibleChangedListener() {
                        @Override
                        public void onVisibleChanged(MapItem item) {
                            rb.setVisible(_marker.getVisible()
                                    && _marker.hasMetaValue(
                                            "pairinglineself_on"));
                            fah.getFAH().setVisible(_marker.getVisible()
                                    && _marker.hasMetaValue("fahon"));
                            _laserbasket.showAll(_marker.getVisible()
                                    && _marker.hasMetaValue("lbon"));
                        }
                    });
        }

        rb.setVisible(_marker.hasMetaValue("pairinglineself_on"));

        _marker.setMetaBoolean("doNotDisplayAgl", true);
        return super.onToolBegin(bundle);
    }

    @Override
    public void dispose() {
        super.dispose();
        if (br != null) {
            try {
                AtakBroadcast.getInstance().unregisterReceiver(br);
            } catch (Exception e) {
                Log.d(TAG, "broadcast receiver not registered");
            }
        }
        _prefs.unregisterOnSharedPreferenceChangeListener(this);

        List<FahArrowWidget.Item> fahList;
        synchronized (fahs) {
            fahList = new ArrayList<>(fahs.values());
            fahs.clear();
        }
        for (FahArrowWidget.Item item : fahList) {
            if (item != null)
                item.removeFromGroup();
        }

        List<DesignatorTargetLine> lineList;
        synchronized (lbs) {
            lineList = new ArrayList<>(lbs.values());
            lbs.clear();
        }
        for (DesignatorTargetLine line : lineList) {
            if (line != null)
                line.end();
        }

        synchronized (this) {
            if (rb != null) {
                _mapView.getRootGroup().removeItem(rb);
                rb.dispose();
                rb = null;
            }
        }

        _instance = null;
    }

    @Override
    protected void setCurrentState(States s) {
        super.setCurrentState(s);
        _mapView.getMapTouchController().skipDeconfliction(s == States.ENABLED);
    }

    public final BroadcastReceiver br = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (REDX_FAH.equals(action)) {
                final FahArrowWidget.Item fah = fetchFahArrowWidget(_marker);
                if (_marker.hasMetaValue("fahon")) {
                    _marker.removeMetaData("fahon");
                    fah.setClickable(false);
                    fah.getFAH().setVisible(false);
                } else {
                    _marker.setMetaBoolean("fahon", true);
                    fah.setClickable(true);
                    fah.getFAH().setVisible(true);
                }
            } else if (REDX_LB.equals(action)) {
                final DesignatorTargetLine _laserbasket = fetchDesignatorTargetLine(
                        _marker.getUID());
                if (_marker.hasMetaValue("lbon")) {
                    _marker.removeMetaData("lbon");
                    _laserbasket.showAll(false);
                } else {
                    _laserbasket.set(_marker);
                    _marker.setMetaBoolean("lbon", true);
                    _laserbasket.showAll(true);
                }
            } else if (REDX_SELF.equals(action)) {

                if (_mapView.getSelfMarker().getGroup() == null) {
                    Toast.makeText(_mapView.getContext(),
                            R.string.self_marker_required,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (_marker.hasMetaValue("pairinglineself_on")) {
                    _marker.removeMetaData("pairinglineself_on");
                    rb.setVisible(false);
                } else {
                    _marker.setMetaBoolean("pairinglineself_on", true);
                    rb.setVisible(true);
                }
            } else if (SPI_SELF.equals(action)) {
                String uid = intent.getStringExtra("startingUID");
                PointMapItem mi = (PointMapItem) _mapView.getRootGroup()
                        .deepFindItem("uid", uid);
                mi.removeOnVisibleChangedListener(removeListener);
                mi.addOnVisibleChangedListener(removeListener);
                mi.removeOnGroupChangedListener(groupChangedListener);
                mi.addOnGroupChangedListener(groupChangedListener);
                if (_mapView.getSelfMarker().getGroup() == null) {
                    Toast.makeText(_mapView.getContext(),
                            R.string.self_marker_required,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                PointMapItem self = _mapView.getSelfMarker();
                RangeAndBearingMapItem rb = (RangeAndBearingMapItem) _mapView
                        .getRootGroup().deepFindItem("uid",
                                mi.getMetaString("self_to_spi", ""));
                if (rb == null) {
                    String rbUID = UUID.randomUUID().toString();
                    rb = RangeAndBearingMapItem
                            .createOrUpdateRABLine(rbUID,
                                    self, mi, false);
                    String title = mi.getMetaString("title", "");
                    rb.setTitle("Self to " + title);
                    rb.setMetaBoolean("removable", false);
                    _mapView.getRootGroup().addItem(rb);
                    rb.setVisible(true);
                    mi.setMetaString("self_to_spi", rbUID);
                }
                if (mi.hasMetaValue("pairinglineself_on")) {
                    mi.removeMetaData("pairinglineself_on");
                    rb.setVisible(false);
                } else {
                    mi.setMetaBoolean("pairinglineself_on", true);
                    rb.setVisible(true);
                }
            } else if (SPI_FAH.equals(action)) {

                String uid = intent.getStringExtra("startingUID");
                PointMapItem mi = (PointMapItem) _mapView.getRootGroup()
                        .deepFindItem("uid", uid);
                if (mi == null)
                    return;

                mi.removeOnVisibleChangedListener(removeListener);
                mi.addOnVisibleChangedListener(removeListener);
                mi.removeOnGroupChangedListener(groupChangedListener);
                mi.addOnGroupChangedListener(groupChangedListener);

                FahArrowWidget.Item fahItem = fetchFahArrowWidget(mi);

                if (mi.hasMetaValue("fahon")) {
                    mi.removeMetaData("fahon");
                    fahItem.setClickable(false);
                    fahItem.getFAH().setVisible(false);

                } else {
                    mi.setMetaBoolean("fahon", true);
                    fahItem.setClickable(true);
                    fahItem.getFAH().setVisible(true);
                }
            } else if (SPI_LB.equals(action)) {
                String uid = intent.getStringExtra("startingUID");
                PointMapItem mi = (PointMapItem) _mapView.getRootGroup()
                        .deepFindItem("uid", uid);
                if (mi == null)
                    return;

                mi.removeOnVisibleChangedListener(removeListener);
                mi.addOnVisibleChangedListener(removeListener);
                mi.removeOnGroupChangedListener(groupChangedListener);
                mi.addOnGroupChangedListener(groupChangedListener);

                DesignatorTargetLine lbItem = fetchDesignatorTargetLine(
                        mi.getUID());

                if (mi.hasMetaValue("lbon")) {
                    mi.removeMetaData("lbon");
                    lbItem.showAll(false);
                } else {
                    lbItem.set(mi);
                    mi.setMetaBoolean("lbon", true);
                    lbItem.showAll(true);
                }
            }

        }
    };

    MapItem.OnVisibleChangedListener removeListener = new MapItem.OnVisibleChangedListener() {
        @Override
        public void onVisibleChanged(MapItem item) {
            item.removeMetaData("pairinglineself_on");
            RangeAndBearingMapItem rb = (RangeAndBearingMapItem) _mapView
                    .getRootGroup().deepFindItem("uid",
                            item.getMetaString("self_to_spi", ""));
            if (rb != null) {
                rb.setVisible(false);
            }

            /**
            //mi.hasMetaValue("lbon");
            //mi.hasMetaValue("fahon");
            Log.d(TAG, "calling fah arrow (visible)");
            FahArrowWidget.Item fah = fetchFahArrowWidget(item,false);
            if (fah != null)
                 fah.setVisible(false);
            
            Log.d(TAG, "calling designator (visible)");
            DesignatorTargetLine dtl = fetchDesignatorTargetLine(item.getUID(),false);
            if (dtl != null)
                 dtl.setVisible(false);
            **/

            item.removeMetaData("lbon");
            item.removeMetaData("fahon");

            destroyFahArrowWidget(item.getUID());
            destroyDesignatorTargetLine(item.getUID());

        }
    };

    MapItem.OnGroupChangedListener groupChangedListener = new MapItem.OnGroupChangedListener() {
        @Override
        public void onItemAdded(MapItem item, MapGroup group) {

        }

        @Override
        public void onItemRemoved(MapItem item, MapGroup group) {
            item.removeMetaData("pairinglineself_on");
            RangeAndBearingMapItem rb = (RangeAndBearingMapItem) _mapView
                    .getRootGroup().deepFindItem("uid",
                            item.getMetaString("self_to_spi", ""));
            if (rb != null) {
                rb.setVisible(false);
            }

            destroyFahArrowWidget(item.getUID());
            destroyDesignatorTargetLine(item.getUID());
        }
    };

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {

        if (key == null)
            return;

        if (key.equals("spiFahSize")) {
            List<FahArrowWidget.Item> items;
            synchronized (fahs) {
                items = new ArrayList<>(fahs.values());
            }
            for (FahArrowWidget.Item item : items)
                updateFahWidth(item);
        }
    }
}
