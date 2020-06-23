
package com.atakmap.android.vehicle.overhead;

import android.os.Bundle;

import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.cot.importer.MapItemImporter;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;

import com.atakmap.app.R;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

public class OverheadImporter extends MapItemImporter {

    private final static String TAG = "OverheadImporter";

    public OverheadImporter(MapView mapView, MapGroup overheadGroup) {
        super(mapView, overheadGroup, OverheadMarker.COT_TYPE);
    }

    @Override
    protected ImportResult importMapItem(MapItem existing, CotEvent cot,
            Bundle extras) {
        if (existing != null && !(existing instanceof OverheadMarker))
            return ImportResult.FAILURE;

        OverheadMarker marker = (OverheadMarker) existing;

        CotDetail modelAttr = cot.findDetail("model");
        CotDetail trackAttr = cot.findDetail("track");

        GeoPointMetaData point = new GeoPointMetaData(cot.getGeoPoint());

        String course = "0";
        if (trackAttr != null)
            course = trackAttr.getAttribute("course");

        // No model? Skip it
        if (modelAttr == null)
            return ImportResult.FAILURE;

        String model = modelAttr.getAttribute("name");
        OverheadImage image = OverheadParser.getImageByName(model);

        // Invalid model or center
        if (image == null)
            return ImportResult.FAILURE;

        double heading = 0.0;
        try {
            heading = Double.parseDouble(course);
        } catch (NumberFormatException e) {
            Log.e(TAG, "error: ", e);
        }
        if (marker == null)
            marker = new OverheadMarker(image, point, cot.getUID());
        else {
            marker.setImage(image);
            marker.setPoint(point);
        }
        marker.setAzimuth(heading);

        CotDetailManager.getInstance().processDetails(marker, cot);
        addToGroup(marker);
        marker.setVisible(extras.getBoolean("visible", marker.getVisible()));

        // Persist to the statesaver if needed
        persist(marker, extras);

        return ImportResult.SUCCESS;
    }

    @Override
    protected int getNotificationIcon(MapItem item) {
        if (item instanceof OverheadMarker) {
            OverheadImage image = ((OverheadMarker) item).getImage();
            if (image != null && image.resId != 0)
                return image.resId;
        }
        return R.drawable.obj_c_130;
    }
}
