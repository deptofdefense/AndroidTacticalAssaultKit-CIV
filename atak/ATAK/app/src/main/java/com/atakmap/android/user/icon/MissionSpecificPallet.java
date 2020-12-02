
package com.atakmap.android.user.icon;

import androidx.fragment.app.Fragment;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

/**
 * 
 * 
 */
public class MissionSpecificPallet implements IconPallet {

    public static final String COT_MAPPING_MISSION_SPECIFIC = "COT_MAPPING_MISSION_SPECIFIC";

    final MissionSpecificPalletFragment fragment;

    public MissionSpecificPallet() {
        fragment = new MissionSpecificPalletFragment();
    }

    @Override
    public String getTitle() {
        return MapView.getMapView().getContext().getString(R.string.mission);
    }

    @Override
    public String getUid() {
        return COT_MAPPING_MISSION_SPECIFIC;
    }

    @Override
    public Fragment getFragment() {
        return fragment;
    }

    @Override
    public String toString() {
        return "MissionPallet";
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
    public void clearSelection(final boolean bPauseListener) {
        fragment.clearSelection(bPauseListener);
    }

    @Override
    public void refresh() {
    }
}
