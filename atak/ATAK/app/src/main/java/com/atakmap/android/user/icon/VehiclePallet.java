
package com.atakmap.android.user.icon;

import androidx.fragment.app.Fragment;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

/**
 * Vehicle polyline markers
 */
public class VehiclePallet implements IconPallet {

    private static final String TAG = "VehiclePallet";
    private static final String COT_MAPPING_VEHICLE = "COT_MAPPING_VEHICLE";

    VehiclePalletFragment fragment;

    public VehiclePallet() {
        fragment = new VehiclePalletFragment();
    }

    @Override
    public String getTitle() {
        return MapView.getMapView().getContext().getString(
                R.string.vehicle_outlines);
    }

    @Override
    public String getUid() {
        return COT_MAPPING_VEHICLE;
    }

    @Override
    public Fragment getFragment() {
        return fragment;
    }

    @Override
    public String toString() {
        return TAG;
    }

    @Override
    public MapItem getPointPlacedIntent(GeoPointMetaData point, String uid)
            throws CreatePointException {
        return fragment.getPointPlacedIntent(point, uid);
    }

    @Override
    public void select(int resId) {
    }

    @Override
    public void clearSelection(boolean bPauseListener) {
        fragment.clearSelection();
    }

    @Override
    public void refresh() {
    }
}
