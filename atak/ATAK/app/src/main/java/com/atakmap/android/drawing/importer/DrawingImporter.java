
package com.atakmap.android.drawing.importer;

import android.os.Bundle;

import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.cot.detail.PrecisionLocationHandler;
import com.atakmap.android.cot.importer.MapItemImporter;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Shape;
import com.atakmap.app.R;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

public abstract class DrawingImporter extends MapItemImporter {

    private final static String TAG = "RectangleImporter";

    protected DrawingImporter(MapView mapView, MapGroup group, String type) {
        super(mapView, group, type);
    }

    @Override
    protected ImportResult importMapItem(MapItem existing, CotEvent event,
            Bundle extras) {
        if (!(existing instanceof Shape))
            return ImportResult.IGNORE;

        // Let other tools know where this item is coming from
        String from = extras.getString("from");
        if (from != null)
            existing.setMetaString("from", from);

        // Set visibility
        existing.setVisible(extras.getBoolean("visible",
                existing.getVisible(true)), false);

        // Add to group (if needed)
        addToGroup(existing);

        // Add an empty precision location so the center is processed correctly
        // Free-form shapes sent from WinTAK (and pre 3.12 version of ATAK)
        // do not have a precision location
        if (event.findDetail(
                PrecisionLocationHandler.PRECISIONLOCATION) == null) {
            CotDetail detail = event.getDetail();
            if (detail == null)
                event.setDetail(detail = new CotDetail());
            detail.addChild(
                    new CotDetail(PrecisionLocationHandler.PRECISIONLOCATION));
        }

        // Process detail nodes
        CotDetailManager.getInstance().processDetails(existing, event);

        // Persist to the statesaver if needed
        persist(existing, extras);

        return ImportResult.SUCCESS;
    }

    @Override
    protected int getNotificationIcon(MapItem item) {
        return R.drawable.ic_menu_drawing;
    }
}
