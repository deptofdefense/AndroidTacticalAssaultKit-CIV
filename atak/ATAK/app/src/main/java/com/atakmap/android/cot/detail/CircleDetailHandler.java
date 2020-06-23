
package com.atakmap.android.cot.detail;

import android.graphics.Color;

import com.atakmap.android.drawing.mapItems.DrawingCircle;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;

/**
 * Handle the <circle radius="" strokeWeight="" fillColor="" shapeName="" circleType="" radiusUid="" numRings="" shapeUID=""/>
 */
class CircleDetailHandler extends CotDetailHandler {

    private final MapView _mapView;

    CircleDetailHandler(MapView mapView) {
        super("__circle");
        _mapView = mapView;
    }

    @Override
    public boolean isSupported(MapItem item, CotEvent event, CotDetail detail) {
        return item instanceof DrawingCircle;
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        //Do nothing
        return false;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        DrawingCircle circle = (DrawingCircle) item;
        int numRings = parseInt(detail.getAttribute("numRings"), 0);
        double radius = parseDouble(detail.getAttribute("radius"), 0);
        if (radius <= 0)
            return ImportResult.FAILURE;

        double strokeWeight = parseDouble(detail.getAttribute(
                "strokeWeight"), 4);
        int fillColor = parseInt(detail.getAttribute("fillColor"),
                Color.WHITE);
        String shapeName = detail.getAttribute("shapeName");
        String radiusUid = detail.getAttribute("radiusUid");

        circle.setTitle(shapeName);
        circle.setRadius(radius);
        circle.setColor(fillColor, true);
        circle.setStrokeWeight(strokeWeight);
        circle.setNumRings(numRings);

        if (!FileSystemUtils.isEmpty(radiusUid)) {
            MapItem mi = _mapView.getRootGroup().deepFindUID(radiusUid);
            if (mi == null)
                return ImportResult.DEFERRED;
            else if (mi instanceof Marker)
                circle.setRadiusMarker((Marker) mi);
        }
        return ImportResult.SUCCESS;
    }
}
