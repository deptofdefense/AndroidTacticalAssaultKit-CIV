
package com.atakmap.android.user.icon;

import androidx.fragment.app.Fragment;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

/**
 * 
 * 
 */
public class SpotMapPallet implements IconPallet {

    public static final String COT_MAPPING_SPOTMAP = "COT_MAPPING_SPOTMAP";
    public static final String COT_MAPPING_SPOTMAP_LABEL = "LABEL";

    final SpotMapPalletFragment fragment;

    public SpotMapPallet() {
        fragment = new SpotMapPalletFragment();
    }

    @Override
    public String getTitle() {
        return MapView.getMapView().getContext().getString(R.string.spot_map);
    }

    @Override
    public String getUid() {
        return COT_MAPPING_SPOTMAP;
    }

    @Override
    public Fragment getFragment() {
        return fragment;
    }

    @Override
    public String toString() {
        return "SpotMapPallet";
    }

    @Override
    public Marker getPointPlacedIntent(GeoPointMetaData point, String uid) {
        return fragment.getPointPlacedIntent(point, uid);
    }

    @Override
    public void select(int resId) {
        fragment.select(resId);
    }

    @Override
    public void clearSelection(boolean bPauseListener) {
        fragment.clearSelection(bPauseListener);
    }

    @Override
    public void refresh() {
    }
}
