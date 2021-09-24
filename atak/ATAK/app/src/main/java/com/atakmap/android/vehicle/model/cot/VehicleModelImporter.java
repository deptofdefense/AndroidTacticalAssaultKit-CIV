
package com.atakmap.android.vehicle.model.cot;

import android.os.Bundle;

import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.cot.detail.PrecisionLocationHandler;
import com.atakmap.android.cot.importer.MapItemImporter;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.vehicle.VehicleShape;
import com.atakmap.android.vehicle.model.VehicleModel;
import com.atakmap.android.vehicle.model.VehicleModelCache;
import com.atakmap.android.vehicle.model.VehicleModelInfo;
import com.atakmap.app.R;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.NorthReference;
import com.atakmap.map.elevation.ElevationManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * For importing vehicle model markers from CoT
 */
public class VehicleModelImporter extends MapItemImporter {

    private final static String TAG = "VehicleModelImporter";

    // Legacy vehicle models
    private static final String OVERHEAD_TYPE = "overhead_marker";
    private static final Map<String, String> _ovCategories = new HashMap<>();
    private static final Map<String, String> _ovAliases = new HashMap<>();
    static {
        _ovAliases.put("MV-22B", "CV-22");
        _ovAliases.put("CH-53E", "HH-53");
        _ovAliases.put("LIMO", "Limousine");
    }

    public VehicleModelImporter(MapView mapView, MapGroup vehicleGroup) {
        super(mapView, vehicleGroup, new HashSet<>(Arrays.asList(
                VehicleModel.COT_TYPE, VehicleShape.COT_TYPE, OVERHEAD_TYPE)));
    }

    @Override
    protected ImportResult importMapItem(MapItem existing, CotEvent cot,
            Bundle extras) {
        if (existing != null && !(existing instanceof VehicleModel))
            return ImportResult.FAILURE;

        String type = cot.getType();

        VehicleModel veh = (VehicleModel) existing;

        CotDetail modelAttr = cot.findDetail("model");
        CotDetail trackAttr = cot.findDetail("track");

        // No model? Skip it
        if (modelAttr == null)
            return ImportResult.FAILURE;

        String name = modelAttr.getAttribute("name");
        String category = modelAttr.getAttribute("category");
        String outline = modelAttr.getAttribute("outline");

        if (type.equals(VehicleShape.COT_TYPE)) {

            // Vehicle shapes stored the model name in the "value" attribute
            name = modelAttr.getAttribute("value");

            // Outline always on
            outline = "true";

            // Make sure fill is empty so model doesn't show up half transparent
            CotDetail fc = cot.findDetail("fillColor");
            CotDetail sc = cot.findDetail("strokeColor");
            if (fc != null && sc != null)
                fc.setAttribute("value", String.valueOf(
                        parseColor(sc.getAttribute("value"),
                                VehicleShape.DEFAULT_STROKE) & 0xFFFFFF));
        }

        VehicleModelInfo info;

        // Vehicle shape and overhead marker conversion
        if (type.equals(OVERHEAD_TYPE) || type.equals(VehicleShape.COT_TYPE))
            info = findModel(name);
        else
            info = VehicleModelCache.getInstance().get(category, name);

        if (info == null) {
            Log.e(TAG,
                    "Failed to find model info for " + category + "/" + name);
            return ImportResult.FAILURE;
        }

        // Heading
        double azimuth = 0;
        if (trackAttr != null)
            azimuth = parseDouble(trackAttr.getAttribute("course"), 0);

        GeoPoint p = cot.getGeoPoint();
        GeoPointMetaData gpmd = new GeoPointMetaData(p);

        // Lookup point altitude if there isn't one, otherwise the vehicle
        // will render underground
        if (!p.isAltitudeValid()) {
            ElevationManager.getElevation(p.getLatitude(), p.getLongitude(),
                    null, gpmd);

            // Remove invalid precision location detail
            CotDetail pl = cot
                    .findDetail(PrecisionLocationHandler.PRECISIONLOCATION);
            cot.getDetail().removeChild(pl);
        }

        if (veh == null)
            veh = new VehicleModel(info, gpmd, cot.getUID());
        else {
            veh.setVehicleInfo(info);
            veh.setCenter(gpmd);
        }

        veh.setAzimuth(azimuth, NorthReference.TRUE);
        veh.setShowOutline(Boolean.parseBoolean(outline));

        CotDetailManager.getInstance().processDetails(veh, cot);
        addToGroup(veh);
        veh.setVisible(extras.getBoolean("visible",
                veh.getVisible(true)), false);

        // Update offscreen indicator timeout if this is a newly received item
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

    /**
     * Find the model for an overhead marker (legacy)
     * @param name Vehicle name
     * @return Vehicle info or null if not found
     */
    public synchronized static VehicleModelInfo findModel(String name) {
        // Some overhead markers had slightly different name/model than
        // their model counterpart
        String alias = _ovAliases.get(name);
        if (alias != null)
            name = alias;

        // Overhead CoT does not specify the category - need to find it
        VehicleModelCache cache = VehicleModelCache.getInstance();
        String category = _ovCategories.get(name);
        if (category == null) {
            List<String> categories = cache.getCategories();
            for (String c : categories) {
                VehicleModelInfo info = cache.get(c, name);
                if (info != null) {
                    _ovCategories.put(name, info.category);
                    return info;
                }
            }
            Log.e(TAG, "Failed to find category for overhead marker: " + name);
            return null;
        }

        return cache.get(category, name);
    }
}
