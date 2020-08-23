
package com.atakmap.android.maps;

import java.util.Collection;

public final class MapGroupItemsChangedEventForwarder implements
        MapGroup.OnItemListChangedListener,
        MapGroup.OnGroupListChangedListener {

    MapEventDispatcher _mapEventDispatcher;

    public MapGroupItemsChangedEventForwarder(
            MapEventDispatcher mapEventDispatcher) {
        this._mapEventDispatcher = mapEventDispatcher;
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
        // XXX - if we are transferring between groups, post a refresh
        //       rather than an add
        final String type;
        if (item.getMetaBoolean("__groupTransfer", false))
            type = MapEvent.ITEM_GROUP_CHANGED;
        else
            type = MapEvent.ITEM_ADDED;
        MapEventDispatcher d = MapGroupItemsChangedEventForwarder.this
                .getMapEventDispatcher();
        MapEvent.Builder b = new MapEvent.Builder(type);
        b.setGroup(group)
                .setItem(item);
        d.dispatch(b.build());
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
        // XXX - if we are transferring between groups, don't post remove
        if (item.getMetaBoolean("__groupTransfer", false))
            return;

        MapEventDispatcher d = MapGroupItemsChangedEventForwarder.this
                .getMapEventDispatcher();
        MapEvent.Builder b = new MapEvent.Builder(MapEvent.ITEM_REMOVED);
        b.setGroup(group)
                .setItem(item);
        d.dispatch(b.build());
    }

    @Override
    public void onGroupAdded(MapGroup group, MapGroup parent) {
        group.addOnItemListChangedListener(
                MapGroupItemsChangedEventForwarder.this);
        final Collection<MapItem> items = group.getItems();
        for (MapItem item : items)
            MapGroupItemsChangedEventForwarder.this.onItemAdded(item, group);
        group.addOnGroupListChangedListener(this);
        final Collection<MapGroup> children = group.getChildGroups();
        for (MapGroup child : children)
            this.onGroupAdded(child, group);

        MapEvent.Builder b = new MapEvent.Builder(MapEvent.GROUP_ADDED);
        getMapEventDispatcher().dispatch(b.setGroup(group)
                .build());
    }

    @Override
    public void onGroupRemoved(MapGroup group, MapGroup parent) {
        group.removeOnItemListChangedListener(
                MapGroupItemsChangedEventForwarder.this);
        group.removeOnGroupListChangedListener(this);

        MapEvent.Builder b = new MapEvent.Builder(MapEvent.GROUP_REMOVED);
        getMapEventDispatcher().dispatch(b.setGroup(group)
                .build());
    }

    private MapEventDispatcher getMapEventDispatcher() {
        return _mapEventDispatcher;
    }
}
