
package com.atakmap.android.navigation.widgets;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.ipc.DocumentedExtra;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.menu.MapMenuEventListener;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.navigation.views.NavView;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.android.widgets.DrawableWidget;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.app.DeveloperOptions;
import com.atakmap.app.R;
import com.atakmap.app.preferences.CustomActionBarFragment;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.CameraController;
import com.atakmap.map.MapRenderer2;
import com.atakmap.map.MapSceneModel;

import androidx.core.graphics.ColorUtils;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.platform.ui.MotionEvent;

/**
 * Contains widget and data management for the 3D free-look capability
 * For the gesture part of the implementation see {@link MapTouchController}
 */
public class FreeLookMapComponent extends AbstractMapComponent implements
        MapMenuEventListener,
        PointMapItem.OnPointChangedListener,
        MapItem.OnGroupChangedListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String FREE_LOOK = "com.atakmap.android.maps.TOGGLE_FREE_LOOK";

    private static FreeLookMapComponent _instance;

    public static FreeLookMapComponent getInstance() {
        return _instance;
    }

    private MapView _mapView;
    private AtakPreferences _prefs;
    private Resources _res;
    private MapTouchController _mtc;

    // Main parameters
    private boolean _enabled;
    private GeoPoint _point;
    private PointMapItem _item;

    // Widgets
    private LinearLayoutWidget _layoutV;
    private LinearLayoutWidget _freeLookWidget;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        _mapView = view;
        _prefs = new AtakPreferences(view);
        _res = context.getResources();
        _mtc = view.getMapTouchController();
        registerListeners();
        addWidgets();
        _instance = this;
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        unregisterListeners();
        removeWidgets();
    }

    /**
     * Set whether 3D free look is enabled
     * @param enabled True if enabled
     */
    public void setEnabled(boolean enabled) {

        if (DeveloperOptions.getIntOption("disable-3D-mode", 0) == 1)
            return;

        final MapSceneModel sm = _mapView.getRenderer3().getMapSceneModel(false,
                MapRenderer2.DisplayOrigin.UpperLeft);
        final double tilt = 90d + sm.camera.elevation;
        GeoPointMetaData focus = _mapView.inverseWithElevation(
                sm.focusx,
                sm.focusy);
        GeoPoint ffPoint = _point;
        if (ffPoint == null)
            ffPoint = focus.get();
        _enabled = enabled;
        setPoint(enabled ? focus.get() : null);
        _freeLookWidget.setVisible(enabled);
        updateColors();
        if (!enabled) {
            if (_mtc.getTiltEnabledState() != MapTouchController.STATE_TILT_ENABLED)
                _mapView.getMapController().tiltBy(-tilt, ffPoint, true);
            if (!_mtc.isUserOrientationEnabled())
                CameraController.Programmatic.rotateTo(
                        _mapView.getRenderer3(),
                        0, true);
            setItem(null);
        }

        // Toggle the DEX controls if they're enabled
        NavView.getInstance().setDexControlsEnabled(
                _prefs.get("dexControls", false));
    }

    /**
     * Check if free look is currently enabled
     * @return True if enabled
     */
    public boolean isEnabled() {
        return _enabled;
    }

    /**
     * Set the focused map item in free form 3D mode
     * @param item Map item
     */
    public void setItem(MapItem item) {

        // Remove listener on existing point
        if (_item != null) {
            _item.removeOnGroupChangedListener(this);
            _item.removeOnPointChangedListener(this);
        }

        // Associate with anchor item
        _item = getAnchorItem(item);

        if (_item != null) {
            GeoPoint point = _item.getPoint();
            double height = item.getHeight();
            if (item instanceof Marker && !Double.isNaN(height)) {
                // Apply height offset to marker
                point = new GeoPoint(point, GeoPoint.Access.READ_WRITE);
                double alt = point.getAltitude();
                if (Double.isNaN(alt))
                    alt = 0;
                alt += height;
                point.set(alt);
            } else if (item instanceof Shape)
                point = ((Shape) item).getClickPoint();
            if (point != null)
                setPoint(point);
            _item.addOnGroupChangedListener(this);
            _item.addOnPointChangedListener(this);
        }
    }

    /**
     * Get the current focused free look item
     * @return Map item
     */
    public PointMapItem getItem() {
        return _item;
    }

    /**
     * Set the current focus point for free look
     * @param point Focus point
     */
    public void setPoint(GeoPoint point) {
        _point = point;
    }

    /**
     * Get the current focus point for free look
     * @return Focus point
     */
    public GeoPoint getPoint() {
        return _point;
    }

    private void registerListeners() {
        _prefs.registerListener(this);
        MapMenuReceiver.getInstance().addEventListener(this);
        DocumentedIntentFilter f = new DocumentedIntentFilter(FREE_LOOK,
                "Toggle 3D free look on a map item",
                new DocumentedExtra[] {
                        new DocumentedExtra("uid", "Map item UID to focus on",
                                false, String.class)
                });
        AtakBroadcast.getInstance().registerReceiver(_receiver, f);
    }

    private void unregisterListeners() {
        _prefs.unregisterListener(this);
        MapMenuReceiver.getInstance().removeEventListener(this);
        AtakBroadcast.getInstance().unregisterReceiver(_receiver);
    }

    private void addWidgets() {
        RootLayoutWidget root = (RootLayoutWidget) _mapView.getComponentExtra(
                "rootLayoutWidget");
        LinearLayoutWidget tlLayout = root.getLayout(RootLayoutWidget.TOP_LEFT);
        _layoutV = tlLayout.getOrCreateLayout("TL_V");

        float size = _res.getDimensionPixelSize(R.dimen.button_small);
        float pd = _res.getDimensionPixelSize(R.dimen.auto_margin);
        _freeLookWidget = new LinearLayoutWidget();
        _freeLookWidget.setNinePatchBG(true);
        _freeLookWidget.setPadding(pd, pd, pd, pd);
        _freeLookWidget.setVisible(false);
        DrawableWidget dr = new DrawableWidget(_res.getDrawable(
                R.drawable.ic_password_show));
        dr.setSize(size, size);
        dr.setColor(_res.getColor(R.color.maize));
        dr.addOnClickListener(new IMapWidget.OnClickListener() {
            @Override
            public void onMapWidgetClick(IMapWidget widget, MotionEvent event) {
                setEnabled(false);
            }
        });
        _freeLookWidget.addWidget(dr);
        _layoutV.addWidget(_freeLookWidget);
    }

    private void removeWidgets() {
        _layoutV.removeWidget(_freeLookWidget);
    }

    private PointMapItem getAnchorItem(MapItem item) {
        if (item == null)
            return null;
        if (item instanceof PointMapItem)
            return (PointMapItem) item;
        if (item instanceof AnchoredMapItem)
            return ((AnchoredMapItem) item).getAnchorItem();
        return null;
    }

    @Override
    public boolean onShowMenu(MapItem item) {
        // Highlight the free-look radial action when active, if there is one
        if (item != null)
            item.toggleMetaData("freeLook", isEnabled());
        return false;
    }

    @Override
    public void onHideMenu(MapItem item) {
    }

    @Override
    public void onPointChanged(PointMapItem item) {
        if (_item == item) {
            CameraController.Programmatic.panTo(
                    _mapView.getRenderer3(),
                    item.getPoint(), true);
            setPoint(item.getPoint());
        }
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
        if (_item == item)
            setEnabled(false);
    }

    private final BroadcastReceiver _receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null)
                return;

            switch (action) {
                case FREE_LOOK: {
                    boolean enabled = !_enabled;
                    setEnabled(enabled);

                    // Set proper focus point on item
                    String uid = intent.getStringExtra("uid");
                    if (enabled && !FileSystemUtils.isEmpty(uid))
                        setItem(_mapView.getMapItem(uid));
                    break;
                }
            }
        }
    };

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {

        if (key == null)
            return;

        if (key.equals(CustomActionBarFragment.ACTIONBAR_BACKGROUND_COLOR_KEY))
            updateColors();
    }

    private void updateColors() {
        int bgColor = ColorUtils.setAlphaComponent(NavView.getInstance()
                .getUserIconShadowColor(), 128);
        _freeLookWidget.setBackingColor(bgColor);
    }
}
