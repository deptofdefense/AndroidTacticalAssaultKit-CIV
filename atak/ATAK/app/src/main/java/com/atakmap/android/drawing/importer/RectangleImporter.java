
package com.atakmap.android.drawing.importer;

import android.os.Bundle;

import com.atakmap.android.bpha.BPHARectangleCreator;
import com.atakmap.android.cot.CotUtils;
import com.atakmap.android.drawing.mapItems.DrawingRectangle;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.ArrayList;
import java.util.List;

public class RectangleImporter extends DrawingImporter {

    private final static String TAG = "RectangleImporter";

    public RectangleImporter(MapView mapView, MapGroup group) {
        super(mapView, group, "u-d-r");
    }

    @Override
    protected ImportResult importMapItem(MapItem existing, CotEvent event,
            Bundle extras) {
        if (existing != null && !(existing instanceof DrawingRectangle))
            return ImportResult.FAILURE;

        CotDetail detail = event.getDetail();
        if (detail == null)
            return ImportResult.FAILURE;

        DrawingRectangle rect = (DrawingRectangle) existing;

        // Process points
        List<GeoPointMetaData> points = new ArrayList<>(4);
        List<CotDetail> children = detail.getChildrenByName("link");
        for (CotDetail d : children) {
            if (d.getAttribute("relation") != null)
                continue;
            String linkPoint = d.getAttribute("point");
            GeoPoint geoPoint = GeoPoint.parseGeoPoint(linkPoint);
            points.add(GeoPointMetaData.wrap(geoPoint));
        }
        if (points.size() < 4) {
            Log.e(TAG, "Rectangle only has " + points.size() + " points!");
            return ImportResult.FAILURE;
        }

        String title = CotUtils.getCallsign(event);

        if (rect == null) {
            MapGroup childGroup = _group.addGroup(title);
            rect = new DrawingRectangle(childGroup, points.get(0),
                    points.get(1), points.get(2), points.get(3),
                    event.getUID());
        } else {
            rect.setTitle(title);
            rect.setPoints(points.get(0), points.get(1),
                    points.get(2), points.get(3));
        }

        if (event.findDetail(DrawingRectangle.KEY_BPHA) != null)
            rect.setMetaString(DrawingRectangle.KEY_BPHA,
                    DrawingRectangle.KEY_BPHA);

        return super.importMapItem(rect, event, extras);
    }

    @Override
    protected void addToGroup(MapItem item) {
        MapGroup group = null;
        if (item.hasMetaValue(DrawingRectangle.KEY_BPHA))
            // BP/HA rectangles go in the Mission group
            group = BPHARectangleCreator.getGroup();
        if (group == null)
            group = _group;
        super.addToGroup(item, group);
    }

    @Override
    protected int getNotificationIcon(MapItem item) {
        return R.drawable.rectangle;
    }
}
