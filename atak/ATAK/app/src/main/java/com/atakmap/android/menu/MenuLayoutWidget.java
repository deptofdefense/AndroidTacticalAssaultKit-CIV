
package com.atakmap.android.menu;

import android.content.Intent;
import android.graphics.PointF;
import android.view.KeyEvent;
import android.view.View;

import com.atakmap.android.action.MapAction;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.assets.MapAssets;
import com.atakmap.coremap.maps.coords.GeoPoint;

public class MenuLayoutWidget extends MenuLayoutBase implements
        MapEventDispatcher.MapEventDispatchListener,
        View.OnKeyListener {

    public static final String TAG = "MenuLayoutWidget";

    private final MapView _mapView;

    private boolean _listenersPushed = false;

    private static class ActionDispatcher
            implements MenuLayoutBase.MapActionDispatcher {
        final MapView _mapView;

        ActionDispatcher(MapView mapView) {
            _mapView = mapView;
        }

        @Override
        public void dispatchAction(MapAction mapAction, MapItem mapItem) {
            mapAction.performAction(_mapView, mapItem);
        }
    }

    MenuLayoutWidget(final MapView mapView,
            final MapAssets assets, final MenuMapAdapter adapter) {
        super(mapView, mapView.getMapData(), assets, adapter,
                new ActionDispatcher(mapView));

        _mapView = mapView;
    }

    /**
     * Enable press controls on a menu widget.
     */
    public void enablePressControls() {
        MapEventDispatcher dispatcher = _mapView.getMapEventDispatcher();
        dispatcher.addMapEventListener(MapEvent.ITEM_CLICK, this);
    }

    /**
     * Disable press controls on a menu widget.
     */
    public void disablePressControls() {
        MapEventDispatcher dispatcher = _mapView.getMapEventDispatcher();
        dispatcher.removeMapEventListener(MapEvent.ITEM_CLICK, this);
        dispatcher
                .removeMapEventListener(MapEvent.MAP_CLICK, _mapClickListener);
    }

    @Override
    boolean _clearAllMenus() {
        final boolean r = super._clearAllMenus();

        reinstateListeners();

        MapEventDispatcher dispatcher = _mapView.getMapEventDispatcher();

        dispatcher.removeMapEventListener(MapEvent.ITEM_REMOVED, this);

        _mapView.getMapController().removeOnFocusPointChangedListener(this);
        _mapView.removeOnKeyListener(this);

        return r;
    }

    @Override
    public MapMenuWidget openMenuOnItem(MapItem item, PointF point) {
        MapMenuWidget widget = super.openMenuOnItem(item, point);
        if (widget != null) {
            killListeners(widget._dragDismiss);

            MapEventDispatcher dispatcher = _mapView.getMapEventDispatcher();
            dispatcher.addMapEventListener(MapEvent.ITEM_REMOVED, this);

            if (point == null)
                _mapView.getMapController()
                        .addOnFocusPointChangedListener(this);
        }
        _mapView.addOnKeyListener(this);

        return widget;
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
        else if (type.equals(MapEvent.ITEM_REMOVED)
                && isMapItem(event.getItem())) {
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

    private final MapEventDispatcher.MapEventDispatchListener _mapClickListener = new MapEventDispatcher.MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            PointF p = new PointF();
            p.x = event.getPointF().x;
            p.y = event.getPointF().y;
            _openMenuOnMap(p);

        }
    };

    @Override
    public MapMenuWidget openMenuOnMap(GeoPoint point) {
        MapMenuWidget menuWidget = super.openMenuOnMap(point);
        _mapView.addOnKeyListener(this);
        return menuWidget;
    }
}
