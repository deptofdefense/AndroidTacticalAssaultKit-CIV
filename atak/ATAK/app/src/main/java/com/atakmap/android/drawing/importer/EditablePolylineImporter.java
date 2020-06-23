
package com.atakmap.android.drawing.importer;

import android.util.SparseArray;

import com.atakmap.android.editableShapes.EditablePolyline;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.ArrayList;
import java.util.List;

public abstract class EditablePolylineImporter extends DrawingImporter {

    private final static String TAG = "EditablePolylineImporter";

    protected EditablePolylineImporter(MapView mapView, MapGroup group,
            String type) {
        super(mapView, group, type);
    }

    /**
     * Load all the points from a CoT event into the polyline
     *
     * @param poly Editable polyline
     * @param event CoT event
     * @return True if successful
     */
    protected boolean loadPoints(EditablePolyline poly, CotEvent event) {
        CotDetail detail = event.getDetail();
        if (detail == null || poly == null)
            return false;

        // Deserialize points and markers first
        List<GeoPointMetaData> points = new ArrayList<>();
        SparseArray<PointMapItem> markers = new SparseArray<>();
        int r = 0;
        List<CotDetail> children = detail.getChildrenByName("link");
        for (CotDetail d : children) {
            // Create marker for link
            PointMapItem marker = createMarker(poly, d);
            if (marker != null) {
                points.add(marker.getGeoPointMetaData());
                markers.append(r, marker);
            } else {
                // Add point for link
                GeoPointMetaData pm = createPoint(poly, d);
                if (pm == null)
                    continue;
                points.add(pm);
            }
            r++;
        }
        if (points.size() < 2) // Invalid - ignore
            return false;

        // Update the line
        poly.setPoints(points, markers);
        return true;
    }

    /**
     * Create a new geo point given link details
     *
     * @param poly Editable polyline
     * @param linkDetails Link details
     * @return New geo point
     */
    protected GeoPointMetaData createPoint(EditablePolyline poly,
            CotDetail linkDetails) {
        GeoPoint point = GeoPoint.parseGeoPoint(linkDetails
                .getAttribute("point"));
        if (point == null)
            return null;
        return GeoPointMetaData.wrap(point);
    }

    /**
     * Given a link from an AssocSet's Cot event, creates the corresponding item. Overwrite if
     * you want a behavior other than simply creating corner points for all items. (ie end
     * points, waypoints, arbitrary cot points, etc)
     *
     * @param poly The associated polyline
     * @param linkDetails The point details
     * @return Marker or null if no marker for this point
     */
    protected PointMapItem createMarker(EditablePolyline poly,
            CotDetail linkDetails) {
        return null;
    }
}
