
package com.atakmap.android.user.icon;

import androidx.fragment.app.Fragment;

import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

public class Icon2525bPallet implements IconPallet {

    public static final String COT_MAPPING_2525B = "COT_MAPPING_2525B";
    private final String title;

    final Icon2525bPalletFragment fragment;

    public Icon2525bPallet(String title) {
        this.title = title;
        fragment = new Icon2525bPalletFragment();
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getUid() {
        return COT_MAPPING_2525B;
    }

    @Override
    public Fragment getFragment() {
        return fragment;
    }

    @Override
    public String toString() {
        return "Icon2525bPallet";
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
