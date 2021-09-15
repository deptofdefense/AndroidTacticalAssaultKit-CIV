
package com.atakmap.android.user.icon;

import androidx.fragment.app.Fragment;

import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

public class Icon2525cPallet implements IconPallet {

    public static final String COT_MAPPING_2525C = "COT_MAPPING_2525C";
    public static final String COT_MAPPING_2525 = "COT_MAPPING_2525";

    public static final String ASSET_PATH = "mil-std-2525c/";

    private final String title;

    final Icon2525cPalletFragment fragment;

    public Icon2525cPallet(String title) {
        this.title = title;
        fragment = new Icon2525cPalletFragment();
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getUid() {
        return COT_MAPPING_2525C;
    }

    @Override
    public Fragment getFragment() {
        return fragment;
    }

    @Override
    public String toString() {
        return "Icon2525cPallet";
    }

    @Override
    public Marker getPointPlacedIntent(GeoPointMetaData point, String uid)
            throws CreatePointException {
        return fragment.getPointPlacedIntent(point, uid);
    }

    @Override
    public void select(int resId) {
    }

    @Override
    public void clearSelection(boolean bPauseListener) {
        fragment.clearSelection(bPauseListener);
    }

    @Override
    public void refresh() {
    }
}
