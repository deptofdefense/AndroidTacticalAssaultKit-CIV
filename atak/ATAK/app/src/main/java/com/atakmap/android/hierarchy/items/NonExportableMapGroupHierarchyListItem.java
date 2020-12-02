
package com.atakmap.android.hierarchy.items;

import android.widget.BaseAdapter;

import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Export;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;

/**
 * Simple wrapper which does not export
 * 
 * 
 */
public class NonExportableMapGroupHierarchyListItem extends
        MapGroupHierarchyListItem {

    private static final String TAG = "NonExportableMapGroupHierarchyListItem";

    public NonExportableMapGroupHierarchyListItem(
            NonExportableMapGroupHierarchyListItem parent, MapView mapView,
            MapGroup group, HierarchyListFilter filter, BaseAdapter listener) {
        this(parent, mapView, group, ADD_TO_OBJ_LIST_FUNC, filter, listener);
    }

    public NonExportableMapGroupHierarchyListItem(
            MapGroupHierarchyListItem parent, MapView mapView,
            MapGroup group, MapGroup.MapItemsCallback itemFilter,
            HierarchyListFilter filter, BaseAdapter listener) {
        super(parent, mapView, group, itemFilter, filter, listener);
    }

    @Override
    protected HierarchyListItem createChild(MapGroup group) {
        return new NonExportableMapGroupHierarchyListItem(this, this.mapView,
                group, this.itemFilter, this.filter,
                this.listener);
    }

    @Override
    public <T extends Action> T getAction(Class<T> clazz) {
        if (clazz.equals(Export.class))
            return null;

        return super.getAction(clazz);
    }

    @Override
    public boolean isSupported(Class<?> target) {
        return false;
    }

    @Override
    public Object toObjectOf(Class<?> target, ExportFilters filters)
            throws FormatNotSupportedException {

        return null;
    }
}
