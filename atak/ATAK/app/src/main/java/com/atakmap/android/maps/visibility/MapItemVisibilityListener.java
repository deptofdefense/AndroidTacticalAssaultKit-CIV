
package com.atakmap.android.maps.visibility;

import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher.MapEventDispatchListener;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;

import java.util.List;

/**
 * Controls map item visibility conditions
 */
public class MapItemVisibilityListener implements VisibilityListener,
        MapEventDispatchListener {

    protected final MapView _mapView;
    protected final MapGroup _group;
    protected final VisibilityManager _manager;

    public MapItemVisibilityListener(MapView view) {
        _mapView = view;
        _group = view.getRootGroup();
        _manager = VisibilityManager.getInstance();

        _manager.addListener(this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_ADDED, this);
    }

    public void dispose() {
        _manager.removeListener(this);
        _mapView.getMapEventDispatcher().removeMapEventListener(
                MapEvent.ITEM_ADDED, this);
    }

    @Override
    public void onVisibilityConditions(List<VisibilityCondition> conditions) {
        // Apply visibility conditions to all items
        for (MapItem item : _group.getItemsRecursive())
            item.onVisibilityConditions(conditions);
    }

    @Override
    public void onMapEvent(MapEvent event) {
        MapItem item = event.getItem();
        if (item != null)
            item.onVisibilityConditions(_manager.getConditions());
    }
}
