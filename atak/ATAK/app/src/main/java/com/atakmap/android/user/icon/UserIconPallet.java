
package com.atakmap.android.user.icon;

import androidx.fragment.app.Fragment;

import com.atakmap.android.icons.UserIconSet;
import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

/**
 * 
 * 
 */
public class UserIconPallet implements IconPallet {

    final UserIconPalletFragment fragment;
    final UserIconSet iconset;

    public UserIconPallet(UserIconSet iconset) {
        this.iconset = iconset;
        this.fragment = UserIconPalletFragment.newInstance(iconset);
    }

    @Override
    public String getTitle() {
        return iconset.getName();
    }

    @Override
    public String getUid() {
        return iconset.getUid();
    }

    @Override
    public Fragment getFragment() {
        return fragment;
    }

    @Override
    public String toString() {
        return "UserIconPallet "
                + (iconset == null ? "<iconset not available>"
                        : iconset
                                .toString());
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
        fragment.refresh();
    }
}
