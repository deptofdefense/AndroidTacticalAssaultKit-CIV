
package com.atakmap.android.vehicle;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.cot.detail.CotDetailHandler;
import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.cot.importer.CotImporterManager;
import com.atakmap.android.cot.importer.MapItemImporter;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.user.EnterLocationDropDownReceiver;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.vehicle.model.cot.VehicleModelDetailHandler;
import com.atakmap.android.vehicle.model.cot.VehicleModelImporter;
import com.atakmap.android.imagecapture.opengl.GLOffscreenCaptureService;
import com.atakmap.android.vehicle.model.VehicleModelCache;
import com.atakmap.android.vehicle.model.VehicleModelLayer;
import com.atakmap.android.vehicle.model.ui.VehicleModelPallet;
import com.atakmap.app.R;
import com.atakmap.map.AtakMapView;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements full sized vehicle support that scale correctly with the 
 * map.   This depends on an internally supplied file.
 */
public class VehicleMapComponent extends AbstractMapComponent {

    private static final String TAG = "VehicleMapComponent";

    private MapView _mapView;
    private MapGroup _vehicleGroup;
    private final List<MapItemImporter> _importers = new ArrayList<>();
    private final List<CotDetailHandler> _detailHandlers = new ArrayList<>();
    private VehicleModelLayer _modelLayer;
    private GLOffscreenCaptureService _captureService;
    private EnterLocationDropDownReceiver _pointDropper;
    private VehicleModelPallet _modelPallet;
    private double _mapRes;
    private AtakMapView.OnMapMovedListener _mapMovedListener;

    @Override
    public void onCreate(Context context, Intent intent, final MapView view) {

        _mapView = view;
        _vehicleGroup = getVehicleGroup(view);

        _importers.add(new VehicleImporter(view, _vehicleGroup));
        _importers.add(new VehicleModelImporter(view, _vehicleGroup));

        _detailHandlers.add(new VehicleModelDetailHandler(_vehicleGroup));

        for (MapItemImporter importer : _importers)
            CotImporterManager.getInstance().registerImporter(importer);

        for (CotDetailHandler handler : _detailHandlers)
            CotDetailManager.getInstance().registerHandler(handler);

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(VehicleMapReceiver.ROTATE);
        filter.addAction(VehicleMapReceiver.EDIT);
        filter.addAction(VehicleMapReceiver.TOGGLE_LABEL);
        registerReceiver(context, new VehicleMapReceiver(view), filter);

        _mapMovedListener = new AtakMapView.OnMapMovedListener() {
            @Override
            public void onMapMoved(AtakMapView view,
                    boolean animate) {
                double res = ATAKUtilities.getMetersPerPixel();
                if (_mapRes < 2 && res >= 2 || _mapRes >= 2 && res < 2) {
                    _mapRes = res;
                    updateVehicleDisplay();
                }
            }
        };
        view.addOnMapMovedListener(_mapMovedListener);

        // Load file listing for vehicle models
        VehicleModelCache.getInstance().rescan();
        _captureService = new GLOffscreenCaptureService();

        // 3D vehicle models layer
        _modelLayer = new VehicleModelLayer(view, _vehicleGroup);

        // Register vehicle model pallet (if listing is non-empty)
        _modelPallet = new VehicleModelPallet(view);
        _pointDropper = EnterLocationDropDownReceiver.getInstance(view);
        _pointDropper.addPallet(_modelPallet, 3);

        // Load any vehicle blocks sitting in the block directory
        VehicleBlock.init();
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {

        if (_mapMovedListener != null) {
            view.removeOnMapMovedListener(_mapMovedListener);
            _mapMovedListener = null;
        }
        for (MapItemImporter importer : _importers)
            CotImporterManager.getInstance().unregisterImporter(importer);
        _importers.clear();

        for (CotDetailHandler handler : _detailHandlers)
            CotDetailManager.getInstance().unregisterHandler(handler);
        _detailHandlers.clear();

        if (_modelLayer != null) {
            _modelLayer.dispose();
            _modelLayer = null;
        }
        if (_captureService != null) {
            _captureService.dispose();
            _captureService = null;
        }

        VehicleModelCache.getInstance().dispose();
    }

    private void updateVehicleDisplay() {
        for (MapItem item : _vehicleGroup.findItems("type", "u-d-v")) {
            if (item instanceof VehicleShape)
                ((VehicleShape) item).updateVisibility();
        }
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
}
