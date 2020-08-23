
package com.atakmap.android.vehicle.model.ui;

import android.content.Context;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.user.icon.IconPallet;
import com.atakmap.android.vehicle.model.VehicleModel;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import androidx.fragment.app.Fragment;

/**
 * Vehicle overhead markers
 */
public class VehicleModelPallet implements IconPallet {

    private static final String TAG = "VehicleModelPallet";
    public static final String COT_MAPPING_VEHICLE_MODELS = "COT_MAPPING_VEHICLE_MODELS";

    private final Context _context;
    private final VehicleModelPalletFragment _fragment;

    public VehicleModelPallet(MapView mapView) {
        _context = mapView.getContext();
        _fragment = new VehicleModelPalletFragment();
    }

    @Override
    public String getTitle() {
        return _context.getString(R.string.vehicle_models);
    }

    @Override
    public String getUid() {
        return COT_MAPPING_VEHICLE_MODELS;
    }

    @Override
    public Fragment getFragment() {
        return _fragment;
    }

    @Override
    public String toString() {
        return TAG;
    }

    @Override
    public VehicleModel getPointPlacedIntent(GeoPointMetaData point,
            String uid) {
        return _fragment.getPointPlacedIntent(point, uid);
    }

    @Override
    public void select(int resId) {
    }

    @Override
    public void clearSelection(boolean bPauseListener) {
        _fragment.clearSelection();
    }

    @Override
    public void refresh() {
    }
}
