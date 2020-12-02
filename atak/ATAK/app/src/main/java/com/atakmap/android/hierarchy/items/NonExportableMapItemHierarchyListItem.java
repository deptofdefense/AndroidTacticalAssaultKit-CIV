
package com.atakmap.android.hierarchy.items;

import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Export;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;

public class NonExportableMapItemHierarchyListItem extends
        MapItemHierarchyListItem {

    public NonExportableMapItemHierarchyListItem(MapView mapView,
            MapItem item) {
        super(mapView, item);
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
