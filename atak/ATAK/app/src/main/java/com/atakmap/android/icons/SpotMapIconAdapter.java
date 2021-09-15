
package com.atakmap.android.icons;

import android.graphics.Color;

import com.atakmap.android.maps.Marker;
import com.atakmap.android.user.icon.SpotMapPallet;
import com.atakmap.android.user.icon.SpotMapReceiver;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.assets.Icon;

/**
 * Adapt icon to a colored dot, based on 2525C type
 * 
 * 
 */
public class SpotMapIconAdapter implements IconAdapter {

    private static final String TAG = "SpotMapIconAdapter";

    @Override
    public boolean adapt(Marker marker) {
        final String type = marker.getType();

        if (!FileSystemUtils.isEmpty(type)
                && type.startsWith(SpotMapReceiver.SPOT_MAP_POINT_COT_TYPE)) {

            final String iconsetPath = marker.getMetaString(
                    UserIcon.IconsetPath, "");
            int color = marker.getMetaInteger("color", Color.WHITE);
            if (iconsetPath.endsWith(SpotMapPallet.COT_MAPPING_SPOTMAP_LABEL)) {
                Icon.Builder builder = new Icon.Builder();
                builder.setImageUri(0, "asset://icons/spot_map_label.png");
                builder.setAnchor(16, 16);
                builder.setColor(0, color);
                marker.setIcon(builder.build());
                marker.setIconVisibility(Marker.ICON_GONE);
                marker.setAlwaysShowText(true);
                //Log.d(TAG, "Mapped label via Spot Map");
            } else {
                marker.setAlwaysShowText(false);
                marker.setShowLabel(!marker.hasMetaValue("hideLabel"));
                Icon.Builder builder = new Icon.Builder();
                builder.setImageUri(0, "asset://icons/reference_point.png");
                builder.setAnchor(16, 16);
                builder.setColor(0, color);
                marker.setIcon(builder.build());
                marker.setIconVisibility(Marker.ICON_VISIBLE);

                // Log.d(TAG, "Mapped via Spot Map: " + color);
            }

            return true;
        } else if (!FileSystemUtils.isEmpty(type) && type.startsWith("a-")) {
            //use colored dots
            int color = Color.YELLOW;
            if (type.startsWith("a-f")) // Friendly
                color = Color.BLUE;
            else if (type.startsWith("a-h")) // Hostile
                color = Color.RED;
            else if (type.startsWith("a-n")) // Neutral
                color = Color.GREEN;
            else if (type.startsWith("a-u")) // Unknown
                color = Color.YELLOW;

            Icon.Builder builder = new Icon.Builder();
            builder.setImageUri(0, "asset://icons/reference_point.png");
            builder.setAnchor(16, 16);
            builder.setColor(0, color);
            marker.setIcon(builder.build());
            //Log.d(TAG, "Mapped via Dot Map");
            return true;
        }

        return false;
    }

    @Override
    public void dispose() {
    }

}
