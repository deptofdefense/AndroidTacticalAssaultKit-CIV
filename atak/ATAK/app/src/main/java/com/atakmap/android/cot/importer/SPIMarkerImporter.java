
package com.atakmap.android.cot.importer;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoPoint;

/**
 * Importer for SPI markers
 */
public class SPIMarkerImporter extends MarkerImporter {

    public SPIMarkerImporter(MapView mapView) {
        super(mapView, "SPIs", "b-m-p-s-p-i", true);
    }

    @Override
    protected void addToGroup(MapItem item) {

        if (item.getGroup() == null) {
            item.setMetaBoolean("archive", false);
            item.setMetaBoolean("removable", true);
            item.setMovable(false);
            item.setMetaBoolean("editable", false);
        } else {
            MapItem center = findItem(item.getUID() + ".rangeRing");
            if (center instanceof Marker) {
                GeoPoint p = ((Marker) item).getPoint();
                ((Marker) center).setPoint(p);
            }
        }
        super.addToGroup(item);
    }

    @Override
    protected int getNotificationIcon(MapItem item) {
        return R.drawable.spoi_icon;
    }
}
