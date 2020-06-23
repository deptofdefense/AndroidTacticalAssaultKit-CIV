
package com.atakmap.android.menu;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PointF;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.atakmap.android.action.MapAction;
import com.atakmap.android.action.MapActionFactory;
import com.atakmap.android.config.ConfigEnvironment;
import com.atakmap.android.config.ConfigLoader;
import com.atakmap.android.config.PhraseParser;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.assets.MapAssets;
import com.atakmap.android.toolbars.RangeAndBearingMapItem;
import com.atakmap.android.widgets.AbstractParentWidget;
import com.atakmap.android.widgets.LayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.RadialButtonWidget;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.AtakMapController;

import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

public class MenuLayoutWidget extends LayoutWidget implements
        AtakMapController.OnFocusPointChangedListener,
        MapEventDispatcher.MapEventDispatchListener, View.OnKeyListener {

    public static final String TAG = "MenuLayoutWidget";
    private static final int RING_PADDING = 5;

    private final MapView _mapView;
    private MapItem _mapItem;
    private final MapAssets _mapAssets;
    private final ConfigLoader<MapWidget> _menuLoader;
    private final ConfigEnvironment _baseConfigEnviron;
    private final MenuMapAdapter _adapter;

    private boolean _listenersPushed = false;

    @Override
    public MapWidget seekHit(float x, float y) {
        MapWidget r = super.seekHit(x, y);
        return r;
    }

    @Override
    public void onClick(MotionEvent event) {
        super.onClick(event);
        _clearAllMenus();
    }

    public MenuLayoutWidget(final Context context, final MapView mapView,
            final MapAssets assets, final MenuMapAdapter adapter) {
        _mapView = mapView;
        _mapAssets = assets;
        _adapter = adapter;

        // create the config objects
        ConfigEnvironment.Builder configBuilder = new ConfigEnvironment.Builder();
        _baseConfigEnviron = configBuilder.setMapAssets(assets)
                .build();
        _menuLoader = new ConfigLoader<>();
        _menuLoader.addFactory(
                "menu",
                new MapMenuWidget.Factory(Math.min(mapView.getWidth(),
                        mapView.getHeight()) * 0.475f));

        // enablePressControls();
    }

    public MapItem getMapItem() {
        return _mapItem;
    }

    private MapMenuWidget _openMapMenu(PointF p) {
        MapMenuWidget widget = null;

        PhraseParser.Parameters params = new PhraseParser.Parameters();
        params.setResolver('@',
                new PhraseParser.BundleResolver(_mapView.getMapData()));
        params.setResolver('!', new PhraseParser.NotEmptyResolver());
        params.setResolver('?', new PhraseParser.IsEmptyResolver());

        ConfigEnvironment config = _baseConfigEnviron.buildUpon()
                .setPhraseParserParameters(params)
                .build();
        widget = _openMenu(load("menus/default.xml", config), config);
        if (widget != null) {
            widget.setPoint(p.x, p.y);
        }
        return widget;
    }

    private MapMenuWidget _openSubmenu(MapMenuWidget parent, String menu,
            float angle) {
        if (_mapItem == null)
            return null;
        MapMenuWidget widget = null;
        if (parent != null) {

            PhraseParser.Parameters params = new PhraseParser.Parameters();
            params.setResolver('$', new PhraseParser.BundleResolver(_mapItem));
            params.setResolver('@',
                    new PhraseParser.BundleResolver(_mapView.getMapData()));
            params.setResolver('?', new PhraseParser.NotEmptyResolver());
            params.setResolver('!', new PhraseParser.IsEmptyResolver());

            ConfigEnvironment config = _baseConfigEnviron.buildUpon()
                    .setPhraseParserParameters(params)
                    .build();

            widget = _openMenu(load(menu, config), config);
            if (widget != null) {
                widget.setPoint(parent.getPointX(), parent.getPointY());
                widget.setParent(parent);
                widget.cullDisabledWidgets();
                widget.reorient(angle);
            }
        }

        return widget;
    }

    private MapMenuWidget _openMenu(final InputStream is,
            final ConfigEnvironment config) {
        MapWidget widget = null;

        if (is == null)
            return null;

        try {
            widget = _menuLoader.loadFromConfig(is, config);
        } catch (IOException e) {
            Log.e(TAG, "error: ", e);
        } catch (SAXException e) {
            Log.e(TAG, "error: ", e);
        } catch (ParserConfigurationException e) {
            Log.e(TAG, "error: ", e);
        }

        MapMenuWidget menuWidget = null;
        if (widget instanceof MapMenuWidget)
            menuWidget = (MapMenuWidget) widget;

        if (menuWidget != null) {
            menuWidget.setZOrder(getZOrder());
            menuWidget.fadeAlpha(0, 255, 150); // Perform 150ms fade-in
        }

        return menuWidget;
    }

    private MapMenuWidget _openMenuURI(String menuUri,
            ConfigEnvironment config) {
        MapWidget widget = null;
        try {
            widget = _menuLoader.loadFromConfigUri(Uri.parse(menuUri), config);
        } catch (IOException e) {
            Log.e(TAG, "error: ", e);
        } catch (SAXException e) {
            Log.e(TAG, "error: ", e);
        } catch (ParserConfigurationException e) {
            Log.e(TAG, "error: ", e);
        }

        MapMenuWidget menuWidget = null;
        if (widget instanceof MapMenuWidget) {
            menuWidget = (MapMenuWidget) widget;
        }

        return menuWidget;
    }

    /**
     * If the string is the menu and not a uri, just return the menu otherwise load 
     * the uri and return the String.
     */
    private InputStream load(String s, ConfigEnvironment configEnvironment) {
        if (s.endsWith(".xml")) {
            MapAssets mapAssets = configEnvironment.getMapAssets();
            try {
                return mapAssets.getInputStream(Uri.parse(s));
            } catch (IOException ioe) {
                Log.e(TAG, "invalid menu uri: " + s, ioe);
                return null;
            }
        } else if (s.startsWith("base64:/")) {
            s = s.substring(8);
            if (s.startsWith("/")) {
                s = s.substring(1);
            }
            final byte[] a = Base64.decode(s, Base64.URL_SAFE
                    | Base64.NO_WRAP);
            return new ByteArrayInputStream(a);
        } else {
            try {
                return new ByteArrayInputStream(
                        s.getBytes(FileSystemUtils.UTF8_CHARSET));
            } catch (UnsupportedEncodingException uee) {
                Log.e(TAG, "invalid menu: " + s, uee);
                return null;
            }
        }

    }

    private MapMenuWidget _openItemMenu(MapItem item) {
        MapMenuWidget widget = null;
        String menu = null;

        if (menu == null) {
            menu = item.getMetaString("menu", null);
        }
        if (menu == null) {
            menu = _adapter.lookup(item);
        }

        if (menu != null) {

            PhraseParser.Parameters params = new PhraseParser.Parameters();
            params.setResolver('$', new PhraseParser.BundleResolver(item));
            params.setResolver('@',
                    new PhraseParser.BundleResolver(_mapView.getMapData()));
            params.setResolver('!', new PhraseParser.IsEmptyResolver());
            params.setResolver('?', new PhraseParser.NotEmptyResolver());

            ConfigEnvironment config = _baseConfigEnviron.buildUpon()
                    .setPhraseParserParameters(params).build();

            widget = _openMenu(load(menu, config), config);
            if (widget != null) {
                PointF point = null;

                if (item.getMetaString("menu_point", null) != null) {
                    try {
                        GeoPoint gp = GeoPoint.parseGeoPoint(
                                item.getMetaString("menu_point", null));
                        // treat the menu point as one and done communication
                        item.removeMetaData("menu_point");
                        gp = _mapView.getRenderElevationAdjustedPoint(gp);
                        point = _mapView.forward(gp);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing Geo Point");
                    }
                }

                // why on this green earth do we call out RangeAndBearingMapItem
                // separate when we are setting the closest touch location in the 
                // arrow tool, leave in just in case menu_point is not set in some 
                // bizaro world.   shame shame shame.

                if (point != null) {

                    // do nothing, was set in menu_point and was processed without 
                    // an issue.
                } else if (item instanceof PointMapItem) {
                    GeoPoint geo = ((PointMapItem) item).getPoint();
                    geo = _mapView.getRenderElevationAdjustedPoint(geo);
                    point = _mapView.forward(geo);
                } else if (item instanceof RangeAndBearingMapItem) {
                    Log.e(TAG,
                            "tripping into legacy code for RangeAndBearing center point, please fix");
                    GeoPoint geo = ((RangeAndBearingMapItem) item)
                            .getCenter().get();
                    geo = _mapView.getRenderElevationAdjustedPoint(geo);
                    point = _mapView.forward(geo);
                }

                if (point != null) {
                    widget.setPoint(point.x, point.y);
                    processMenuButtons(widget, config, item);
                } else if (item.getMetaString("menu_point", null) != null) {
                    try {

                        GeoPoint gp = GeoPoint
                                .parseGeoPoint(item.getMetaString("menu_point",
                                        null));
                        gp = _mapView.getRenderElevationAdjustedPoint(gp);
                        PointF p = _mapView.forward(gp);
                        widget.setPoint(p.x, p.y);
                    } catch (Exception e) {
                        widget.setPoint(
                                _mapView.getMapController().getFocusX(),
                                _mapView
                                        .getMapController().getFocusY());
                    }
                } else {
                    widget.setPoint(_mapView.getMapController().getFocusX(),
                            _mapView
                                    .getMapController().getFocusY());
                }
            }
        }
        return widget;
    }

    public void enablePressControls() {
        MapEventDispatcher dispatcher = _mapView.getMapEventDispatcher();
        dispatcher.addMapEventListener(MapEvent.ITEM_CLICK, this);
    }

    public void disablePressControls() {
        MapEventDispatcher dispatcher = _mapView.getMapEventDispatcher();
        dispatcher.removeMapEventListener(MapEvent.ITEM_CLICK, this);
        dispatcher
                .removeMapEventListener(MapEvent.MAP_CLICK, _mapClickListener);
    }

    private void processMenuButtons(
            MapMenuWidget widget, ConfigEnvironment config, MapItem item) {

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(_mapView.getContext());

        Collection<MapWidget> buttons = widget.getChildWidgets();
        for (MapWidget wid : buttons) {

            // Expecting all be menu buttons
            if (!(wid instanceof MapMenuButtonWidget))
                continue;

            MapMenuButtonWidget menuButton = (MapMenuButtonWidget) wid;
            String subm = menuButton.getSubmenu();
            if (subm == null)
                continue;

            //peek at submenu, get list of enabled sub buttons
            MapMenuWidget submWidget = _openMenu(
                    load(subm, config), config);

            // Widget failed to load
            if (submWidget == null)
                continue;

            ArrayList<MapWidget> subbuttons = (ArrayList<MapWidget>) submWidget
                    .getChildWidgets();

            ArrayList<MapWidget> subbuttonsToRem = new ArrayList<>();
            for (MapWidget wd : subbuttons) {
                if (wd instanceof MapMenuButtonWidget) {
                    MapMenuButtonWidget subButton = ((MapMenuButtonWidget) wd);
                    if (menuButton.isDisabled() || subButton.isDisabled()) {
                        subButton.setDisabled(true);
                        subbuttonsToRem.add(wd);
                    }
                }
            }
            // Remove disabled sub-buttons from processing below
            for (MapWidget mwtr : subbuttonsToRem) {
                subbuttons.remove(mwtr);
            }

            // Disable button if sub-menu is empty
            if (subbuttons.isEmpty()) {
                menuButton.setState(MapMenuButtonWidget.STATE_DISABLED);
                continue;
            }

            // Set default option to preference value
            List<String> prefKeys = menuButton.getPrefKeys();
            List<String> prefValues = new ArrayList<>();
            for (String k : prefKeys)
                prefValues.add(prefs.getString(k, null));

            //if only one available, swap to that
            if (subbuttons.size() == 1) {
                MapMenuButtonWidget thesubb = (MapMenuButtonWidget) subbuttons
                        .get(0);
                menuButton.copyAction(thesubb);
            } else {
                Map<String, Object> submenuMap = item.getMetaMap("submenu_map");
                for (MapWidget swid : subbuttons) {
                    MapMenuButtonWidget swidb = (MapMenuButtonWidget) swid;
                    //swap out button for chosen sub button
                    if (!prefValues.isEmpty()) {
                        // Set default based on matching pref value
                        List<String> subPrefValues = swidb.getPrefValues();
                        if (!subPrefValues.isEmpty() &&
                                subPrefValues.size() == prefValues.size()) {
                            boolean same = true;
                            for (int i = 0; i < prefValues.size(); i++) {
                                String v1 = prefValues.get(i);
                                String v2 = subPrefValues.get(i);
                                if (v1 == null || !v1.equals(v2)) {
                                    same = false;
                                    break;
                                }
                            }
                            if (same) {
                                menuButton.copyAction(swidb);
                                break;
                            }
                        }
                    } else if (submenuMap != null) {
                        // Set default based on matching click action
                        String submenuString = (String) submenuMap
                                .get(subm);
                        if (submenuString != null
                                && submenuString.equals(swidb.getOnClick())) {
                            menuButton.copyAction(swidb);
                            // Why?
                            //submenuMap.put(submenuString, swidb.getOnClick());
                            break;
                        }
                    }
                }
            }
        }
    }

    private boolean _clearAllMenus() {
        boolean r = false;
        if (_mapItem != null) {
            // Clear out any custom selection states
            _mapItem.removeMetaData("overlay_manager_select");
            _mapItem = null;
        }
        while (getChildCount() > 0) {
            removeWidgetAt(getChildCount() - 1);
            r = true;
        }
        reinstateListeners();

        MapEventDispatcher dispatcher = _mapView.getMapEventDispatcher();

        dispatcher.removeMapEventListener(MapEvent.ITEM_REMOVED, this);

        _mapView.getMapController().removeOnFocusPointChangedListener(this);
        _mapView.removeOnKeyListener(this);

        return r;
    }

    private boolean _clearUntilMenu(MapMenuWidget menu) {
        boolean r = false;
        while (getChildCount() > 0 && getChildAt(getChildCount() - 1) != menu) {
            removeWidgetAt(getChildCount() - 1);
            r = true;
        }
        return r;
    }

    public void openMenuOnItem(MapItem item, PointF point) {
        _clearAllMenus();
        MapMenuWidget widget = _openItemMenu(item);
        if (widget != null) {
            _mapItem = item;
            _addPressExpanders(widget);
            addWidget(widget);
            killListeners(widget._dragDismiss);

            MapEventDispatcher dispatcher = _mapView.getMapEventDispatcher();
            dispatcher.addMapEventListener(MapEvent.ITEM_REMOVED, this);

            if (point != null)
                widget.setPoint(point.x, point.y);
            else
                _mapView.getMapController()
                        .addOnFocusPointChangedListener(this);
        }
        _mapView.addOnKeyListener(this);
    }

    public void openMenuOnItem(MapItem item) {
        openMenuOnItem(item, null);
    }

    @Override
    public void onFocusPointChanged(float x, float y) {
        for (int i = 0; i < getChildCount(); i++) {
            MapWidget widget = getChildAt(i);
            if (widget instanceof MapMenuWidget)
                widget.setPoint(x, y);
        }
    }

    @Override
    public void onMapEvent(MapEvent event) {
        String type = event.getType();

        // Hide radial when map is clicked or long-pressed
        if ((type.equals(MapEvent.MAP_PRESS)
                || type.equals(MapEvent.MAP_CLICK)
                || type.equals(MapEvent.MAP_LONG_PRESS))
                && _listenersPushed) {
            AtakBroadcast.getInstance().sendBroadcast(
                    new Intent("com.atakmap.android.maps.UNFOCUS"));
            AtakBroadcast.getInstance().sendBroadcast(
                    new Intent("com.atakmap.android.maps.HIDE_DETAILS"));
            AtakBroadcast.getInstance().sendBroadcast(
                    new Intent(MapMenuReceiver.HIDE_MENU));
        }

        // Hide radial when map item is removed
        else if (type.equals(MapEvent.ITEM_REMOVED) && _mapItem != null
                && _mapItem == event.getItem()) {
            // apparently, other things need to know when the menu is closed
            // this should work for now
            AtakBroadcast.getInstance().sendBroadcast(
                    new Intent(MapMenuReceiver.HIDE_MENU));
        }

        // Open radial on another map item when clicked
        else if (type.equals(MapEvent.ITEM_CLICK)) {
            openMenuOnItem(event.getItem());
        }
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            AtakBroadcast.getInstance().sendBroadcast(new Intent(
                    "com.atakmap.android.maps.UNFOCUS"));
            // Keeping the details opened when tapping the back button is
            // intended behavior
            /*AtakBroadcast.getInstance().sendBroadcast(new Intent(
                    "com.atakmap.android.maps.HIDE_DETAILS"));*/
            AtakBroadcast.getInstance().sendBroadcast(
                    new Intent(MapMenuReceiver.HIDE_MENU));
            _mapView.removeOnKeyListener(this);
            return true;
        }
        return false;
    }

    /**
     * @param dragDismiss allows for dragging to dismiss the menu
     */
    private void killListeners(boolean dragDismiss) {
        if (!_listenersPushed) {
            MapTouchController touchCtrl = _mapView.getMapTouchController();
            touchCtrl.lockControls();
            _mapView.getMapEventDispatcher().pushListeners();
            _mapView.getMapEventDispatcher()
                    .clearUserInteractionListeners(true);
            if (dragDismiss) {
                _mapView.getMapEventDispatcher().addMapEventListener(
                        MapEvent.MAP_PRESS, this);
            } else {
                _mapView.getMapEventDispatcher().addMapEventListener(
                        MapEvent.MAP_CLICK, this);
            }
            _mapView.getMapEventDispatcher().addMapEventListener(
                    MapEvent.MAP_LONG_PRESS, this);
            _listenersPushed = true;
        }
    }

    private void reinstateListeners() {
        if (_listenersPushed) {
            MapTouchController touchCtrl = _mapView.getMapTouchController();
            touchCtrl.unlockControls();
            _mapView.getMapEventDispatcher().clearListeners();
            _mapView.getMapEventDispatcher().popListeners();
            _listenersPushed = false;
        }
    }

    public void clearMenu() {
        _clearAllMenus();
    }

    /**
     * Controls what actions are to be taken when a MapMenuButtonWidget is pressed.  
     */
    private void _addPressExpanders(final MapMenuWidget menuWidget) {
        for (int i = 0; i < menuWidget.getChildCount(); ++i) {
            final MapMenuButtonWidget button = (MapMenuButtonWidget) menuWidget
                    .getChildAt(i);

            AbstractParentWidget layoutParent = menuWidget.getParent();
            if (layoutParent != null) {

                float previousButtonWidth = ((RadialButtonWidget) layoutParent
                        .getChildAt(0)).getButtonWidth();
                float radius = menuWidget._buttonRadius + previousButtonWidth
                        + RING_PADDING;
                button.setOrientation(button.getOrientationAngle(), radius);
            }

            button.addOnPressListener(new MapWidget.OnPressListener() {

                @Override
                public void onMapWidgetPress(MapWidget widget,
                        MotionEvent event) {

                }
            });

            button.addOnClickListener(new MapWidget.OnClickListener() {
                @Override
                public void onMapWidgetClick(MapWidget widget,
                        MotionEvent event) {
                    if (button.getOnClick() != null) {
                        try {
                            _doAction(menuWidget, button.getOnClick());
                        } catch (IOException e) {
                            Log.e(TAG, "error: ", e);
                        }
                    } else {
                        String submenu = button.getSubmenu();
                        if (submenu != null)
                            _expandPressSubmenu(menuWidget, submenu, button);
                        else if (!button.isDisabled())
                            _clearAllMenus();
                    }

                }
            });

            button.addOnLongPressListener(new MapWidget.OnLongPressListener() {

                @Override
                public void onMapWidgetLongPress(MapWidget widget) {
                    String submenu = button.getSubmenu();
                    if (!button.isDisabled() && submenu != null)
                        _expandPressSubmenu(menuWidget, submenu, button);
                }
            });
        }
    }

    /**
     * Controls what actions are to be taken when a MapMenuButtonWidget is pressed.
     */
    private void _addPressExpanders(final String submenu,
            final MapMenuWidget menuWidget,
            final MapMenuButtonWidget parentButton) {
        for (int i = 0; i < menuWidget.getChildCount(); ++i) {
            final MapMenuButtonWidget button = (MapMenuButtonWidget) menuWidget
                    .getChildAt(i);

            if (parentButton.isDisabled())
                button.setDisabled(true);

            AbstractParentWidget layoutParent = menuWidget.getParent();
            if (layoutParent != null) {

                float previousButtonWidth = ((RadialButtonWidget) layoutParent
                        .getChildAt(0)).getButtonWidth();
                float radius = menuWidget._buttonRadius + previousButtonWidth
                        + RING_PADDING;
                button.setOrientation(button.getOrientationAngle(), radius);
            }

            button.addOnPressListener(new MapWidget.OnPressListener() {

                @Override
                public void onMapWidgetPress(MapWidget widget,
                        MotionEvent event) {

                }
            });

            button.addOnClickListener(new MapWidget.OnClickListener() {
                @Override
                public void onMapWidgetClick(MapWidget widget,
                        MotionEvent event) {
                    if (_mapItem == null)
                        return;
                    String clickAction = button.getOnClick();
                    List<String> prefKeys = parentButton.getPrefKeys();
                    List<String> prefValues = button.getPrefValues();
                    boolean prefsValid = !prefKeys.isEmpty()
                            && !prefValues.isEmpty()
                            && prefKeys.size() == prefValues.size();
                    if (clickAction != null || prefsValid) {
                        try {
                            if (prefsValid) {
                                SharedPreferences prefs = PreferenceManager
                                        .getDefaultSharedPreferences(_mapView
                                                .getContext());

                                // Apply preferences
                                for (int i = 0; i < prefKeys.size(); i++) {
                                    String k = prefKeys.get(i);
                                    String v = prefValues.get(i);
                                    if (k != null && v != null)
                                        prefs.edit().putString(k, v).apply();
                                }

                                // Make sure to close the sub-menu when clicked
                                if (clickAction == null)
                                    clickAction = "actions/cancel.xml";
                            }

                            Map<String, Object> itemmap = _mapItem
                                    .getMetaMap("submenu_map");
                            if (itemmap == null)
                                itemmap = new HashMap<>();
                            itemmap.put(submenu, clickAction);
                            _mapItem.setMetaMap("submenu_map", itemmap);

                            _doAction(menuWidget, clickAction);
                        } catch (IOException e) {
                            Log.e(TAG, "error: ", e);
                        }
                    } else if (!button.isDisabled()) {
                        _clearAllMenus();
                    } else {
                        String submenu = button.getSubmenu();
                        if (submenu != null) {
                            _expandPressSubmenu(menuWidget, submenu, button);
                        }
                    }
                }
            });

            button.addOnLongPressListener(new MapWidget.OnLongPressListener() {

                @Override
                public void onMapWidgetLongPress(MapWidget widget) {
                    String submenu = button.getSubmenu();
                    if (submenu != null) {
                        _expandPressSubmenu(menuWidget, submenu, button);
                    }
                }
            });
        }
    }

    private void _doAction(MapMenuWidget menuWidget, String action)
            throws IOException {
        try {
            if (_mapItem == null)
                return;
            if (action != null) {
                if (action.endsWith(".xml")) {
                    MapAction mapAction = MapActionFactory.createFromUri(
                            Uri.parse(action),
                            _baseConfigEnviron);
                    if (mapAction != null)
                        mapAction.performAction(_mapView, _mapItem);
                } else {
                    if (action.startsWith("base64:/")) {
                        action = action.substring(8);
                        if (action.startsWith("/")) {
                            action = action.substring(1);
                        }
                    }
                    final byte[] a = Base64.decode(action, Base64.URL_SAFE
                            | Base64.NO_WRAP);
                    MapAction mapAction = MapActionFactory
                            .createFromInputStream(
                                    new ByteArrayInputStream(a),
                                    _baseConfigEnviron);
                    if (mapAction != null)
                        mapAction.performAction(_mapView, _mapItem);
                    else
                        Log.d(TAG, "was unable to resolve the mapaction for: "
                                + _mapItem.getUID());
                }
            }
        } catch (SAXException e) {
            Log.e(TAG, "error: " + e, e);
        } catch (ParserConfigurationException e) {
            Log.e(TAG, "error: " + e, e);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "error: " + e, e);
        }
    }

    private void _expandPressSubmenu(MapMenuWidget parent,
            String submenu,
            MapMenuButtonWidget parentButton) {
        MapMenuWidget widget = _openSubmenu(parent,
                submenu, parentButton.getOrientationAngle());
        if (widget != null) {
            _addPressExpanders(submenu, widget, parentButton);
            _clearUntilMenu(parent);
            addWidget(widget);
        }
    }

    private final MapEventDispatcher.MapEventDispatchListener _mapClickListener = new MapEventDispatcher.MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            PointF p = new PointF();
            p.x = event.getPoint().x;
            p.y = event.getPoint().y;
            _openMenuOnMap(p);
        }
    };

    public void openMenuOnMap(GeoPoint point) {
        PointF p = _mapView.forward(point);
        _mapItem = null;
        _openMenuOnMap(p);
        _mapView.addOnKeyListener(this);
    }

    private void _openMenuOnMap(PointF p) {
        if (!_clearAllMenus()) {
            MapMenuWidget widget = _openMapMenu(p);
            if (widget != null) {
                int threeQuartersHeight = 3 * _mapView.getHeight() / 4;
                int threeQuartersWidth = 3 * _mapView.getWidth() / 4;
                int squareSide = threeQuartersHeight;
                if (threeQuartersWidth < threeQuartersHeight) {
                    squareSide = threeQuartersWidth;
                }

                float radius = squareSide / 2f;

                for (int i = 0; i < widget.getChildCount(); ++i) {
                    MapMenuButtonWidget button = (MapMenuButtonWidget) widget
                            .getChildAt(i);
                    button.setOrientation(button.getOrientationAngle(),
                            radius - button.getButtonWidth());
                }

                _addPressExpanders(widget);
                addWidget(widget);
            }
        }
    }

}
