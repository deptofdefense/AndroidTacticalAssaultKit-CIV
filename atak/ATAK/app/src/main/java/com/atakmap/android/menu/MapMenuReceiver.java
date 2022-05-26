
package com.atakmap.android.menu;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.widgets.LayoutWidget;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import gov.tak.api.widgets.IMapWidget;

public class MapMenuReceiver extends BroadcastReceiver {

    public static final String TAG = "MapMenuReceiver";

    private final MapView _mapView;
    private final MenuMapAdapter _adapter;
    private final MenuLayoutWidget _layoutWidget;
    private final ConcurrentLinkedQueue<MapMenuEventListener> _listeners = new ConcurrentLinkedQueue<>();

    public static final String SHOW_MENU = "com.atakmap.android.maps.SHOW_MENU";
    public static final String HIDE_MENU = "com.atakmap.android.maps.HIDE_MENU";
    public static final String REGISTER_MENU = "com.atakmap.android.maps.REGISTER_MENU";
    public static final String UNREGISTER_MENU = "com.atakmap.android.maps.UNREGISTER_MENU";

    private static MapMenuReceiver _instance;

    public MapMenuReceiver(final MapView mapView,
            final MenuLayoutWidget menuLayout,
            final MenuMapAdapter adapter) {
        _layoutWidget = menuLayout;
        _adapter = adapter;
        _mapView = mapView;
        if (_instance == null)
            _instance = this;
    }

    public static MapMenuReceiver getInstance() {
        return _instance;
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();
        if (action != null) {
            switch (action) {
                case SHOW_MENU:
                    _showMenu(intent);
                    break;
                case HIDE_MENU:
                    hideMenu();
                    break;
                case REGISTER_MENU: {
                    String type = intent.getStringExtra("type");
                    String menu = intent.getStringExtra("menu");
                    if (type != null && menu != null) {
                        Log.d(TAG,
                                "adding dynamic type group menu: " + type + " "
                                        + menu);
                        _adapter.addFilter(type, menu);
                    }
                    break;
                }
                case UNREGISTER_MENU: {
                    String type = intent.getStringExtra("type");
                    if (type != null) {
                        Log.d(TAG, "removing dynamic type group menu: " + type);
                        _adapter.removeFilter(type);
                    }
                    break;
                }
                case ToolManagerBroadcastReceiver.BEGIN_TOOL:
                    _layoutWidget.clearMenu();
                    break;
            }
        }

    }

    /**
     * Allows for an application to lookup what type of menu might be provided for a specific 
     * map menu type.
     * @param type the string of the type to match
     * @return the stringified menu - this could have submenus which is the responsibility of 
     * the caller of this method to lookup.
     */
    public String lookupMenu(final String type) {
        if (type != null) {
            Log.d(TAG, "lookup up menu for type: " + type);
            return _adapter.lookup(type);
        }
        return null;
    }

    /**
     * Allows for an application to lookup what type of menu might be provided for a specific 
     * map menu type.
     * @param mi the map item to match
     * @return the stringified menu - this could have submenus which is the responsibility of 
     * the caller of this method to lookup.
     */
    public String lookupMenu(final MapItem mi) {
        if (mi != null)
            return _adapter.lookup(mi);
        return null;
    }

    /**
     * Allows for an application to register a globally used menu for a specific Map Item type.   
     * This is the same as calling sendBroadcast of the intent com.atakmap.android.maps.REGISTER_MENU
     * with the String type and the String menu.
     * @param type the string of the type to match
     * @param menu the stringified menu
     */
    public void registerMenu(final String type, final String menu) {
        if (type != null && menu != null) {
            Log.d(TAG, "adding dynamic type group menu: " + type + " " + menu);
            _adapter.addFilter(type, menu);
        }
    }

    /**
     * Allows for an application to unregister a globally used menu for a specific Map Item type.   
     * @param type the string of the type to match
     */
    public void unregisterMenu(final String type) {
        if (type != null) {
            Log.d(TAG, "removing dynamic type group menu: " + type);
            _adapter.removeFilter(type);
        }
    }

    private void _showMenu(Intent intent) {
        if (intent.hasExtra("point")) {
            try {
                GeoPoint point = GeoPoint.parseGeoPoint(intent
                        .getStringExtra("point"));
                _layoutWidget.openMenuOnMap(point);
            } catch (Exception ex) {
                Log.e(TAG, "error: ", ex);
            }
        } else if (intent.hasExtra("uid")) {
            String uid = intent.getStringExtra("uid");
            if (FileSystemUtils.isEmpty(uid))
                return;

            // Find item - ignore if the "ignoreMenu" bool is set
            MapItem item = _mapView.getRootGroup().deepFindUID(uid);
            if (item == null || item.getMetaBoolean("ignoreMenu", false))
                return;

            // Run through event listeners
            boolean handled = false;
            for (MapMenuEventListener l : getListeners()) {
                if (l.onShowMenu(item)) {
                    handled = true;
                    break;
                }
            }

            // Open the menu
            if (!handled)
                _layoutWidget.openMenuOnItem(item);
        }
    }

    public void hideMenu() {
        MapItem item = _layoutWidget.getMapItem();
        if (item != null) {
            for (MapMenuEventListener l : getListeners())
                l.onHideMenu(item);
        }
        _layoutWidget.clearMenu();
    }

    /**
     * Add a listener to <a href="#{@link}">{@link MapMenuWidget}</a>
     * lifecycle events. Listeners are visited prior to menu display.
     * Listeners should return true when the menu should <b>not</b> be created.
     * @param listener receives fully formed menus after creation prior to display
     */
    public synchronized void addEventListener(MapMenuEventListener listener) {
        if (!_listeners.contains(listener))
            _listeners.add(listener);
    }

    /**
     * Remove menu event listener from receiving lifecycle events.
     * @param listener
     */
    public synchronized void removeEventListener(
            MapMenuEventListener listener) {
        _listeners.remove(listener);
    }

    private synchronized List<MapMenuEventListener> getListeners() {
        return new ArrayList<>(_listeners);
    }

    /**
     * Gets root level <a href="#{@link}">{@link MenuLayoutWidget}</a>.
     * This layout widget is the parent of all displayed radial menus.
     * @return layout widget
     */
    public static MenuLayoutWidget getMenuWidget() {
        MapView mv = MapView.getMapView();
        if (mv == null)
            return null;
        Object o = mv.getComponentExtra("rootLayoutWidget");
        if (!(o instanceof LayoutWidget))
            return null;
        LayoutWidget root = (LayoutWidget) o;
        Collection<IMapWidget> children = root.getChildren();
        for (IMapWidget mw : children) {
            if (mw instanceof MenuLayoutWidget)
                return (MenuLayoutWidget) mw;
        }
        return null;
    }

    /**
     * Find the map item that the radial is currently opened on
     * @return Focused map item
     */
    public static MapItem getCurrentItem() {
        MenuLayoutWidget menu = getMenuWidget();
        return menu != null ? menu.getMapItem() : null;
    }

    /**
     * Add an additional map menu factory that creates radial menus.
     * Factories return a <a href="#{@link}">{@link MapMenuWidget}</a>
     * in response to a <a href="#{@link}">{@link MapItem}</a>. Factories
     * should return a "default" menu when the provided item is null.
     * @param factory creator of fully populated MapItemWidgets
     **/
    public boolean registerMapMenuFactory(MapMenuFactory factory) {
        return _layoutWidget.registerMapMenuFactory(factory);
    }

    /**
     * Removes an added map menu factory.
     * @param factory instance to be removed from list of factories
     * @return whether a factory was actually removed from the list.
     */
    public boolean unregisterMapMenuFactory(MapMenuFactory factory) {
        return _layoutWidget.unregisterMapMenuFactory(factory);
    }
}
