
package com.atakmap.android.vehicle;

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
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

class VehicleImporter extends MapItemImporter {

    private final static String TAG = "VehicleImporter";

    public VehicleImporter(MapView mapView, MapGroup vehicleGroup) {
        super(mapView, vehicleGroup, "u-d-v");
    }

    @Override
    protected ImportResult importMapItem(MapItem existing, CotEvent cot,
            Bundle extras) {
        if (existing != null && !(existing instanceof VehicleShape))
            return ImportResult.FAILURE;

        VehicleShape veh = (VehicleShape) existing;

        GeoPointMetaData point = new GeoPointMetaData(cot.getGeoPoint());
        CotDetail modelAttr = cot.findDetail("model");
        CotDetail trackAttr = cot.findDetail("track");
        CotDetail showLabelD = cot.findDetail("showLabel");
        // legacy attributes
        CotDetail pointAttr = cot.findDetail("center");
        CotDetail headAttr = cot.findDetail("heading");

        // Legacy support
        if (pointAttr != null) {
            GeoPoint centerPoint = GeoPoint.parseGeoPoint(
                    pointAttr.getAttribute("value"));
            if (centerPoint != null)
                point = new GeoPointMetaData(centerPoint);
        }
        String course = "0";
        if (trackAttr != null)
            course = trackAttr.getAttribute("course");
        else if (headAttr != null)
            course = headAttr.getAttribute("value");

        // No model? Skip it
        if (modelAttr == null)
            return ImportResult.FAILURE;

        String model = modelAttr.getAttribute("value");
        VehicleBlock block = VehicleBlock.getBlock(model);

        // Invalid block - fallback to 3D model
        if (!block.isValid())
            return ImportResult.IGNORE;

        double heading = 0.0;
        try {
            heading = Double.parseDouble(course);
        } catch (NumberFormatException e) {
            Log.e(TAG, "error: ", e);
        }

        if (veh == null)
            veh = new VehicleShape(MapView.getMapView(), cot.getUID());

        veh.setup(model, model, point, heading, false);
        veh.setShowLabel(showLabelD != null && FileSystemUtils.isEquals(
                showLabelD.getAttribute("value"), "true"));

        CotDetailManager.getInstance().processDetails(veh, cot);
        addToGroup(veh);
        veh.setVisible(extras.getBoolean("visible",
                veh.getVisible(true)), false);

        // Update offscreen indicator interest
        if (existing == null && !isStateSaverImport(extras))
            veh.updateOffscreenInterest();

        // Persist to the statesaver if needed
        persist(veh, extras);

        return ImportResult.SUCCESS;
    }

    @Override
    protected int getNotificationIcon(MapItem item) {
        return R.drawable.pointtype_aircraft;
    }
}
