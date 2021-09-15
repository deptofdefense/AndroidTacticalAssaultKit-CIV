
package com.atakmap.android.icons;

import android.content.Context;
import android.graphics.Color;

import com.atakmap.android.maps.MapDataRef;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.SqliteMapDataRef;
import com.atakmap.android.user.icon.Icon2525cPallet;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.assets.Icon;

/**
 * Adapt icon based on iconset path if possible (e.g. <iconsetpath UID>/<group>/<filename>)
 * Otherwise adapt based on best match for 2525C type
 * 
 * 
 */
public class UserIconsetIconAdapter implements IconAdapter {

    private static final String TAG = "UserIconsetIconAdapter";

    private final Context _context;

    public UserIconsetIconAdapter(Context context) {
        _context = context;
    }

    @Override
    public boolean adapt(Marker marker) {
        //TODO finish migrating to a common interface which properly
        //invokes the two methods below
        return false;
    }

    boolean adaptIconsetPath(Marker marker) {

        final String iconsetPath = marker.getMetaString(UserIcon.IconsetPath,
                "");

        //attempt to adapt based on User Specified Iconset if not forced by 
        //user into preferred iconset. Also 2525C based iconset cannot be queried this way
        if (!FileSystemUtils.isEmpty(iconsetPath)
                && !iconsetPath.startsWith(Icon2525cPallet.COT_MAPPING_2525)) {

            //icon URI is a bitmap query optimized for GL layer
            String optimizedQuery = UserIcon.GetIconBitmapQueryFromIconsetPath(
                    iconsetPath, _context);
            if (!FileSystemUtils.isEmpty(optimizedQuery)) {
                MapDataRef iconRef = new SqliteMapDataRef(UserIconDatabase
                        .instance(_context).getDatabaseName(), optimizedQuery);

                Icon.Builder builder = new Icon.Builder();
                builder.setImageUri(0, iconRef.toUri());
                builder.setAnchor(16, 16);
                if (marker.hasMetaValue("color"))
                    builder.setColor(0,
                            marker.getMetaInteger("color", Color.WHITE));

                marker.setIcon(builder.build());
                //set backup icon in case iconset is not loaded, or gets removed
                marker.setMetaString("backupIconUri",
                        "asset:/icons/reference_point.png");
                //Log.d(TAG, "Mapped via IconsetPath=" + iconsetPath);
                return true;
            }
        }

        return false;
    }

    boolean adaptPreferredIconset(Marker marker,
            String preferredCoTMappingUUID) {

        String type = marker.getType();
        if (!FileSystemUtils.isEmpty(type) && type.startsWith("a-")) {

            //find default iconset based on UUID                    
            UserIconSet iconset = UserIconDatabase.instance(_context)
                    .getIconSet(preferredCoTMappingUUID, true, false);
            if (iconset != null && iconset.isValid()) {
                UserIcon icon = iconset.getIconBestMatch(type);
                if (icon != null && icon.isValid()) {
                    //icon URI is a bitmap query optimized for GL layer
                    String optimizedQuery = icon.getIconBitmapQuery();
                    if (!FileSystemUtils.isEmpty(optimizedQuery)) {
                        MapDataRef iconRef = new SqliteMapDataRef(
                                UserIconDatabase.instance(_context)
                                        .getDatabaseName(),
                                optimizedQuery);

                        Icon.Builder builder = new Icon.Builder();
                        builder.setImageUri(0, iconRef.toUri());
                        builder.setAnchor(16, 16);
                        if (marker.hasMetaValue("color")) {
                            builder.setColor(0,
                                    marker.getMetaInteger("color",
                                            Color.WHITE));
                        } else {
                            //derive color from CoT type
                            int color = Color.YELLOW;
                            if (type.startsWith("a-f")) // Friendly
                                color = Color.BLUE;
                            else if (type.startsWith("a-h")) // Hostile
                                color = Color.RED;
                            else if (type.startsWith("a-n")) // Neutral
                                color = Color.GREEN;
                            else if (type.startsWith("a-u")) // Unknown
                                color = Color.YELLOW;
                            builder.setColor(0, color);
                        }

                        marker.setIcon(builder.build());
                        //set backup icon in case iconset is not loaded, or gets removed
                        marker.setMetaString("backupIconUri",
                                "asset:/icons/reference_point.png");

                        //Note we do not set UserIcon.IconsetPath, so this mapping is not persisted
                        //Log.d(TAG, "Mapped via Default CoT Mapping=" + icon.toString());
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Override
    public void dispose() {
    }

}
