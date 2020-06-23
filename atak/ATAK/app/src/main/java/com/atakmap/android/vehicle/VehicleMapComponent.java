
package com.atakmap.android.vehicle;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.cot.importer.CotImporterManager;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.graphics.GLMapItemFactory;
import com.atakmap.android.overlay.DefaultMapGroupOverlay;
import com.atakmap.android.vehicle.overhead.GLOverheadMarker;
import com.atakmap.android.vehicle.overhead.OverheadDetailsReceiver;
import com.atakmap.android.vehicle.overhead.OverheadImage;
import com.atakmap.android.vehicle.overhead.OverheadImporter;
import com.atakmap.android.vehicle.overhead.OverheadMarker;
import com.atakmap.android.vehicle.overhead.OverheadParser;
import com.atakmap.app.R;
import com.atakmap.map.AtakMapView;

import java.util.HashMap;
import java.util.Map;

/**
 * Implements full sized vehicle support that scale correctly with the 
 * map.   This depends on an internally supplied file.
 */
public class VehicleMapComponent extends AbstractMapComponent {

    private static final String TAG = "VehicleMapComponent";

    private MapView _mapView;
    private MapGroup _vehicleGroup, _overheadGroup;
    private VehicleImporter _vImporter;
    private OverheadImporter _oImporter;
    private double _mapRes;

    @Override
    public void onCreate(Context context, Intent intent, final MapView view) {

        _mapView = view;

        _vehicleGroup = getVehicleGroup(view);
        _overheadGroup = getOverheadGroup(view);
        _overheadGroup
                .addOnItemListChangedListener(
                        new MapGroup.OnItemListChangedListener() {
                            @Override
                            public void onItemAdded(MapItem item,
                                    MapGroup group) {
                                updateOverheadIcon();
                            }

                            @Override
                            public void onItemRemoved(MapItem item,
                                    MapGroup group) {
                                updateOverheadIcon();
                            }
                        });
        updateOverheadIcon();

        _vImporter = new VehicleImporter(view, _vehicleGroup);
        CotImporterManager.getInstance().registerImporter(_vImporter);

        _oImporter = new OverheadImporter(view, _overheadGroup);
        CotImporterManager.getInstance().registerImporter(_oImporter);

        GLMapItemFactory.registerSpi(GLOverheadMarker.SPI);
        OverheadParser.init(context);

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(VehicleMapReceiver.ROTATE);
        filter.addAction(VehicleMapReceiver.TOGGLE_LABEL);
        registerReceiver(context, new VehicleMapReceiver(view), filter);
        registerReceiver(context, new OverheadDetailsReceiver(view),
                new DocumentedIntentFilter(OverheadDetailsReceiver.ACTION));

        view.addOnMapMovedListener(new AtakMapView.OnMapMovedListener() {
            @Override
            public void onMapMoved(AtakMapView view,
                    boolean animate) {
                double res = view.getMapResolution();
                if (_mapRes < 2 && res >= 2 || _mapRes >= 2 && res < 2) {
                    _mapRes = res;
                    updateVehicleDisplay();
                }
            }
        });
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        CotImporterManager.getInstance().unregisterImporter(_vImporter);
        CotImporterManager.getInstance().unregisterImporter(_oImporter);
        GLMapItemFactory.unregisterSpi(GLOverheadMarker.SPI);
    }

    private void updateVehicleDisplay() {
        for (MapItem item : _vehicleGroup.findItems("type", "u-d-v")) {
            if (item instanceof VehicleShape)
                ((VehicleShape) item).updateVisibility();
        }
    }

    /**
     * Update the overhead markers overlay icon to match
     * the most commonly used marker type
     */
    private void updateOverheadIcon() {
        Map<String, Integer> counts = new HashMap<>();
        int highest = 0;
        OverheadImage mostCommon = null;
        for (MapItem mi : _overheadGroup.getItems()) {
            if (!(mi instanceof OverheadMarker))
                continue;

            OverheadImage oh = ((OverheadMarker) mi).getImage();
            if (oh != null) {
                final String name = oh.name;
                int existing = counts.containsKey(name) ? counts.get(name) : 0;
                counts.put(name, ++existing);
                if (existing > highest) {
                    highest = existing;
                    mostCommon = OverheadParser.getImageByName(name);
                }
            }
        }
        if (mostCommon != null)
            _overheadGroup.setMetaString("iconUri", mostCommon.imageUri);
    }

    public static MapGroup getVehicleGroup(MapView view) {
        String name = view.getContext().getString(R.string.vehicles);
        MapGroup group = view.getRootGroup().findMapGroup(name);
        if (group == null) {
            group = new DefaultMapGroup(name);
            group.setMetaString("overlay", "vehicles");
            group.setMetaBoolean("permaGroup", true);
            view.getRootGroup().addGroup(group);
        }
        return group;
    }

    public static MapGroup getOverheadGroup(MapView view) {
        String name = view.getContext().getString(R.string.overhead_markers);
        MapGroup group = view.getRootGroup().findMapGroup(name);
        if (group == null) {
            // Dynamically create the overhead markers overlay
            // so we have future control of the icon URI (filter overlay is constant)
            group = new DefaultMapGroup(name);
            group.setMetaBoolean("permaGroup", true);
            view.getMapOverlayManager().addMarkersOverlay(
                    new DefaultMapGroupOverlay(view, group,
                            "asset://icons/aircraft.png"));
        }
        return group;
    }
}
