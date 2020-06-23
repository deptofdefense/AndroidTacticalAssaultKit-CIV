
package com.atakmap.android.overlay;

import android.widget.BaseAdapter;

import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.items.MapGroupHierarchyListItem;
import com.atakmap.android.hierarchy.items.NonExportableMapGroupHierarchyListItem;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;

/**
 * Simple wrapper to set non-exportable Hierarchy List items
 * 
 * 
 */
public class NonExportableMapGroupOverlay extends DefaultMapGroupOverlay {

    public NonExportableMapGroupOverlay(MapView mapView, MapGroup group) {
        super(mapView, group.getFriendlyName(), group, group.getMetaString(
                "iconUri", null), null);
    }

    public NonExportableMapGroupOverlay(MapView mapView, MapGroup group,
            MapGroup.MapItemsCallback filter) {
        super(mapView, group.getFriendlyName(), group, group.getMetaString(
                "iconUri", null), filter);
    }

    protected NonExportableMapGroupOverlay(MapView mapView, String id,
            MapGroup group, String iconUri,
            MapGroup.MapItemsCallback filter) {
        super(mapView, id, group, iconUri, filter);
    }

    @Override
    public HierarchyListItem getListModel(BaseAdapter callback, long actions,
            HierarchyListFilter filter) {
        if (!MapGroupHierarchyListItem.addToObjList(this.rootGroup))
            return null;

        return new NonExportableMapGroupHierarchyListItem(null, this.mapView,
                this.rootGroup, this.filter, filter,
                callback);
    }
}
