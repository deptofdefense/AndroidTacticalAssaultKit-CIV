
package com.atakmap.android.menu;

import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.ipc.DocumentedExtra;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapActivity;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.widgets.LayoutWidget;
import com.atakmap.coremap.log.Log;

import java.io.IOException;

public class MenuMapComponent extends AbstractMapComponent {

    private static final String _TAG = "MenuMapComponent";
    protected MenuMapAdapter _adapter;
    private MapView _mapView;
    private MenuLayoutWidget _menuLayout;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        MapActivity mapActivity = (MapActivity) context;
        _mapView = view;

        //load up the default menu filters for a marker.
        _adapter = new MenuMapAdapter();
        try {
            _adapter.loadMenuFilters(mapActivity.getMapAssets(),
                    "filters/menu_filters.xml");
        } catch (IOException e) {
            Log.e(_TAG, "Error loading menu filters", e);
        }

        _menuLayout = new MenuLayoutWidget(view,
                mapActivity.getMapAssets(), _adapter);
        _menuLayout.setZOrder(0d);
        _menuLayout.setName("Radial Menu");
        LayoutWidget rootLayout = (LayoutWidget) view
                .getComponentExtra("rootLayoutWidget");
        rootLayout.addWidget(_menuLayout);

        MapMenuReceiver _menuReceiver = new MapMenuReceiver(_mapView,
                _menuLayout, _adapter);
        DocumentedIntentFilter menuFilter = new DocumentedIntentFilter();
        menuFilter.addAction(MapMenuReceiver.SHOW_MENU,
                "Display a map item's radial menu",
                new DocumentedExtra[] {
                        new DocumentedExtra("uid", "The map item UID",
                                false, String.class),
                        new DocumentedExtra("point",
                                "Opens a generic point-dropper radial at this point (obsolete?)",
                                true, String.class)
                });
        menuFilter.addAction(MapMenuReceiver.HIDE_MENU,
                "Hide any open radial menus");
        menuFilter.addAction(MapMenuReceiver.REGISTER_MENU,
                "Allows for the registration of a radial menu",
                new DocumentedExtra[] {
                        new DocumentedExtra("type",
                                "The applicable map item type",
                                false, String.class),
                        new DocumentedExtra("menu",
                                "The radial menu (path or stringified)",
                                false, String.class)
                });
        menuFilter.addAction(MapMenuReceiver.UNREGISTER_MENU,
                "Allows for the unregistration of a menu",
                new DocumentedExtra[] {
                        new DocumentedExtra("type",
                                "The applicable map item type",
                                false, String.class)
                });
        menuFilter.addAction(ToolManagerBroadcastReceiver.BEGIN_TOOL);
        this.registerReceiver(context, _menuReceiver, menuFilter);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
    }

}
