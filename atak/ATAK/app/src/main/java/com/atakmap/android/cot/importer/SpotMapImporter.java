
package com.atakmap.android.cot.importer;

import android.graphics.Color;

import com.atakmap.android.icons.UserIcon;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.user.icon.SpotMapPalletFragment;
import com.atakmap.android.user.icon.SpotMapReceiver;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;

/**
 * Importer for spot map markers and labels
 */
public class SpotMapImporter extends MarkerImporter {

    public SpotMapImporter(MapView mapView) {
        super(mapView, "Spot Map", SpotMapReceiver.SPOT_MAP_POINT_COT_TYPE,
                true);
    }

    @Override
    protected void addToGroup(MapItem item) {
        if (item instanceof Marker && item.getGroup() == null) {
            String path = item.getMetaString(UserIcon.IconsetPath, null);
            if (FileSystemUtils.isEquals(path,
                    SpotMapPalletFragment.LABEL_ONLY_ICONSETPATH))
                ((Marker) item).setTextColor(item.getMetaInteger("color",
                        Color.WHITE));
        }
        super.addToGroup(item);
    }

    @Override
    protected int getNotificationIcon(MapItem item) {
        String path = item.getMetaString(UserIcon.IconsetPath, null);
        if (FileSystemUtils.isEquals(path,
                SpotMapPalletFragment.LABEL_ONLY_ICONSETPATH))
            return R.drawable.enter_location_label_icon;
        return R.drawable.reference_point;
    }
}
