
package com.atakmap.android.importexport;

import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Shape;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.ArrayList;
import java.util.List;

/**
 * Provide a late binding filter capability e.g. to filter items within a 
 * <code>MapOverlay</code> when the entire MapOverlay was selected for export
 * 
 * 
 */
public class ExportFilters implements ExportFilter {
    private final List<ExportFilter> filters;

    public ExportFilters() {
        filters = new ArrayList<>();
    }

    public void add(ExportFilter filter) {
        if (filter == null)
            return;

        if (!filters.contains(filter))
            filters.add(filter);
    }

    public void clear() {
        filters.clear();
    }

    @Override
    public boolean filter(MapItem item) {
        for (ExportFilter filter : filters) {
            if (filter.filter(item)) {
                //Log.d("ExportFilters", filter.getClass().getName() + " filtered " + item.getUID());
                return true;
            }
        }

        return false;
    }

    /**
     * Filters objects which are not exportable to the specified target class
     * 
     * 
     */
    public static class TargetClassFilter implements ExportFilter {

        private final Class<?> targetClass;

        public TargetClassFilter(Class<?> targetClass) {
            this.targetClass = targetClass;
        }

        @Override
        public boolean filter(MapItem item) {
            return !(item instanceof Exportable)
                    || !(((Exportable) item).isSupported(targetClass));
        }
    }

    /**
     * Filters objects which are not in the specified bounding box
     * 
     * 
     */
    public static class BoundingBoxFilter implements ExportFilter {

        private static final String TAG = "BoundingBoxFilter";

        private final GeoBounds bbox;

        public BoundingBoxFilter(GeoBounds bbox) {
            this.bbox = bbox;
        }

        @Override
        public boolean filter(MapItem item) {
            return !isInBBox(item);
        }

        public boolean isInBBox(MapGroup group) {
            if (group == null) {
                //e.g. FilterMapOverlay has no associated mapgroup, so include it...
                return true;
            }

            for (MapItem item : group.getItems()) {
                if (item != null) {
                    if (isInBBox(item))
                        return true;
                }
            }

            for (MapGroup childGroup : group.getChildGroups()) {
                if (isInBBox(childGroup))
                    return true;
            }

            //Log.d(TAG, "Group not in view " + group.getFriendlyName());
            return false;
        }

        public boolean isInBBox(MapItem item) {
            if (item == null)
                return true;

            GeoPointMetaData p = null;
            if (item instanceof PointMapItem)
                p = ((PointMapItem) item).getGeoPointMetaData();
            else if (item instanceof Shape)
                p = ((Shape) item).getCenter();
            else if (item instanceof AnchoredMapItem)
                p = ((AnchoredMapItem) item).getAnchorItem()
                        .getGeoPointMetaData();
            if (p == null)
                return true; //default to in view

            //see if in specified geo-region
            if (p.get().getLongitude() >= bbox.getWest()
                    && p.get().getLatitude() > bbox.getSouth() &&
                    p.get().getLongitude() <= bbox.getEast()
                    && p.get().getLatitude() < bbox.getNorth()) {
                Log.d(TAG,
                        "hit item: "
                                + item.getMetaString("callsign",
                                        item.getUID()));
                return true;
            }

            //Log.d(TAG, "Item not in view " + item.getUID());
            return false;
        }
    }

    /**
     * Filters objects based on inclusion in a list of UIDs
     *
     * 
     */
    public static class UIDFilter implements ExportFilter {

        private final List<String> _uids;

        public UIDFilter(List<String> uids) {
            this._uids = uids;
        }

        @Override
        public boolean filter(MapItem item) {
            if (item == null || FileSystemUtils.isEmpty(_uids))
                return true;

            return !_uids.contains(item.getUID());
        }
    }
}
