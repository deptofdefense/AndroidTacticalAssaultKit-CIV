
package com.atakmap.android.vehicle.model.cot;

import com.atakmap.android.cot.detail.CotDetailHandler;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.vehicle.model.VehicleModel;
import com.atakmap.android.vehicle.model.VehicleModelCache;
import com.atakmap.android.vehicle.model.VehicleModelInfo;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

/**
 * Handling for vehicle models attached to markers
 */
public class VehicleModelDetailHandler extends CotDetailHandler {

    private static final String TAG = "VehicleModelDetailHandler";

    private final MapGroup _vehicleGroup;

    public VehicleModelDetailHandler(MapGroup vehicleGroup) {
        super("model");
        _vehicleGroup = vehicleGroup;
    }

    @Override
    public ImportResult toItemMetadata(MapItem mi, CotEvent evt, CotDetail d) {

        // Not a vehicle model
        String type = d.getAttribute("type");
        if (type == null || !type.equalsIgnoreCase("vehicle"))
            return ImportResult.IGNORE;

        // Stand-alone vehicle - already processed
        if (mi.getType().equals(VehicleModel.COT_TYPE))
            return ImportResult.IGNORE;

        // Not a marker
        if (!(mi instanceof Marker))
            return ImportResult.IGNORE;

        Marker marker = (Marker) mi;

        String name = d.getAttribute("name");
        String category = d.getAttribute("category");
        String outline = d.getAttribute("outline");

        // Missing vehicle category
        if (FileSystemUtils.isEmpty(category)) {
            Log.e(TAG, "Vehicle model detail missing category: " + d);
            return ImportResult.FAILURE;
        }

        // Missing vehicle name
        if (FileSystemUtils.isEmpty(name)) {
            Log.e(TAG, "Vehicle model detail missing name: " + d);
            return ImportResult.FAILURE;
        }

        VehicleModelInfo info = VehicleModelCache.getInstance()
                .get(category, name);

        // Vehicle model does not exist
        if (info == null) {
            Log.e(TAG,
                    "Vehicle model does not exist: " + category + "/" + name);
            return ImportResult.FAILURE;
        }

        // Check for existing vehicle
        String vehicleUID = mi.getMetaString("vehicleUID",
                mi.getUID() + ".vehicle_model");

        VehicleModel vehicle = getMapItem(vehicleUID);
        if (vehicle == null) {
            // Center point
            GeoPointMetaData center = getPoint(mi);

            // Pull from CoT event
            if (center == null)
                center = GeoPointMetaData.wrap(evt.getGeoPoint());

            vehicle = new VehicleModel(info, center, vehicleUID);
            vehicle.removeMetaData("archive");
            vehicle.setMetaBoolean("nevercot", true);
            vehicle.removeMetaData("editable");
            vehicle.setCenterMarker(marker);
            _vehicleGroup.addItem(vehicle);
            mi.setMetaString("vehicleUID", vehicleUID);
        } else
            vehicle.setVehicleInfo(info);

        // Track heading used by marker
        double heading = marker.getTrackHeading();
        if (!Double.isNaN(heading))
            vehicle.setHeading(heading);

        vehicle.setShowOutline(Boolean.parseBoolean(outline));

        // TODO: Color vehicle based on marker affiliation?

        return ImportResult.SUCCESS;
    }

    @Override
    public boolean toCotDetail(MapItem mi, CotEvent evt, CotDetail root) {

        // Stand-alone vehicle - already processed
        if (mi.getType().equals(VehicleModel.COT_TYPE))
            return false;

        // Check for existing vehicle
        VehicleModel vehicle = getMapItem(mi.getMetaString("vehicleUID", null));
        if (vehicle == null)
            return false;

        //     <model type='vehicle' name='HH-60' category='Aircraft'/>
        VehicleModelInfo vmi = vehicle.getVehicleInfo();
        CotDetail model = new CotDetail("model");
        model.setAttribute("type", "vehicle");
        model.setAttribute("name", vmi.name);
        model.setAttribute("category", vmi.category);
        model.setAttribute("outline", String.valueOf(vehicle.showOutline()));
        root.addChild(model);
        return true;
    }
}
