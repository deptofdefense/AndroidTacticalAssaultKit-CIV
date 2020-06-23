
package com.atakmap.android.drawing.importer;

import android.os.Bundle;

import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.maps.Association;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

public class DrawingShapeImporter extends EditablePolylineImporter {

    private final static String TAG = "DrawingShapeImporter";

    public DrawingShapeImporter(MapView mapView, MapGroup group) {
        super(mapView, group, "u-d-f");
    }

    @Override
    protected ImportResult importMapItem(MapItem existing, CotEvent event,
            Bundle extras) {
        if (existing != null && !(existing instanceof DrawingShape))
            return ImportResult.FAILURE;

        CotDetail detail = event.getDetail();
        if (detail == null)
            return ImportResult.FAILURE;

        DrawingShape shape = (DrawingShape) existing;

        if (shape == null)
            shape = new DrawingShape(_mapView, _group, event.getUID());

        if (!loadPoints(shape, event)) {
            shape.removeFromGroup();
            return ImportResult.FAILURE;
        }

        shape.setLineStyle(Association.STYLE_SOLID);

        return super.importMapItem(shape, event, extras);
    }

    @Override
    protected int getNotificationIcon(MapItem item) {
        return R.drawable.sse_shape;
    }
}
