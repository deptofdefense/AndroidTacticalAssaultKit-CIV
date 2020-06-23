
package com.atakmap.android.user.icon;

import android.support.v4.app.Fragment;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.vehicle.overhead.OverheadMarker;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

/**
 * Vehicle overhead markers
 */
public class OverheadPallet implements IconPallet {

    private static final String TAG = "OverheadPallet";
    public static final String COT_MAPPING_OVERHEAD_MARKERS = "COT_MAPPING_OVERHEAD_MARKERS";

    OverheadPalletFragment fragment;

    public OverheadPallet() {
        fragment = new OverheadPalletFragment();
    }

    @Override
    public String getTitle() {
        return MapView.getMapView().getContext().getString(
                R.string.overhead_markers);
    }

    @Override
    public String getUid() {
        return COT_MAPPING_OVERHEAD_MARKERS;
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
    public OverheadMarker getPointPlacedIntent(GeoPointMetaData point,
            String uid)
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
