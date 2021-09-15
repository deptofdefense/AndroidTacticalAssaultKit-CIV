
package com.atakmap.android.icons;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Color;

import java.util.ConcurrentModificationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import com.atakmap.android.config.FiltersConfig;
import com.atakmap.android.maps.AssetMapDataRef;
import com.atakmap.android.maps.MapDataRef;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.user.icon.Icon2525cPallet;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;

import java.io.IOException;
import java.util.HashMap;
import com.atakmap.coremap.locale.LocaleUtil;

import java.util.Map;

/**
 * Moved logic from IconsMapAdapter into this class
 * Adapt icon based on CoT/2525C symbols
 * 
 * 
 */
public class Icon2525cIconAdapter implements IconAdapter {

    private static final String TAG = "Icon2525cIconAdapter";

    private Context _context;
    private final HashMap<String, MapDataRef> _refCache = new HashMap<>();
    private final Map<String, MapDataRef> typeIconMap = new HashMap<>();
    private static final Map<String, Integer> teamColors = new HashMap<>();

    // Ability to add additional implementations of icon adapters to the system 
    // that still perform the work on 2525c markers
    private final static ConcurrentLinkedQueue<IconAdapter> adapters = new ConcurrentLinkedQueue<>();

    /** 
     * Register in a more capable 2525 icon adapter to be used as a higher priority than the 
     * current 2525 adapter.
     * @param adapter the custom adapter which would further augment  the current 2525c
     * capability.
     */
    public static void addAdapter(final IconAdapter adapter) {
        if (!adapters.contains(adapter))
            adapters.add(adapter);
    }

    /** 
     * Removes an adapter previously registered by the call to addAdapter
     * @param adapter the custom adapter which would further augment  the current 2525c
     * capability.
     */
    public static void removeAdapter(final IconAdapter adapter) {
        adapters.remove(adapter);
    }

    private FiltersConfig _filters; // do not use directly for this class 
    private final HashMap<String, String> type2icon = new HashMap<>();

    public Icon2525cIconAdapter(final Context context) {
        try {
            _context = context;

            AssetManager assetMgr = context.getAssets();
            _filters = FiltersConfig.parseFromStream(assetMgr
                    .open("filters/icon_filters.xml"));
            if (_filters != null)
                _filters.setComparator("type",
                        new FiltersConfig.StringStartsWithComparator());
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize", e);
        }
    }

    @Override
    public void dispose() {
        for (IconAdapter adapter : adapters) {
            try {
                adapter.dispose();
            } catch (Exception e) {
                Log.e(TAG, "error disposing of an adapter", e);
            }
        }

        _context = null;
        _filters = null;
        _refCache.clear();
        type2icon.clear();
    }

    /**
     * Given a marker, will lookup the assetPath for the icon.   Use as a wrapper 
     * for the robust yet slow _filter implementation.
     */
    public String lookupFromFilter(final Marker m) {
        String type = m.getType();
        String assetPath = null;

        if (type2icon.containsKey(type))
            return type2icon.get(type);

        Map<String, Object> metadata = new HashMap<>();

        try {
            m.getMetaData(metadata);
        } catch (ConcurrentModificationException cme) {
            // XXX - Bandaid for ATAK-11746 IconsMapAdapter ConcurrentModificationException
            Log.e(TAG,
                    "bad things might happen due to a concurrent modification on a marker",
                    cme);
        }
        FiltersConfig.Filter f = _filters.lookupFilter(metadata);

        if (f != null) {
            assetPath = f.getValue();
        }

        type2icon.put(m.getType(), assetPath);
        return assetPath;

    }

    private String jumperAbove(final String name) {
        String retval = name.toLowerCase(LocaleUtil.getCurrent());
        if (retval.equals("dark blue"))
            retval = "darkblue";
        else if (retval.equals("dark green"))
            retval = "darkgreen";
        return "icons/above_" + retval + ".png";
    }

    private String jumperBelow(final String name) {
        String retval = name.toLowerCase(LocaleUtil.getCurrent());
        if (retval.equals("dark blue"))
            retval = "darkblue";
        else if (retval.equals("dark green"))
            retval = "darkgreen";
        return "icons/below_" + retval + ".png";
    }

    public static int teamToColor(final String name) {
        Integer retval = teamColors.get(name);
        if (retval != null)
            return retval;

        String val = name.toLowerCase(LocaleUtil.getCurrent());
        switch (val) {
            case "orange":
                val = "#FFFF7700";
                break;
            case "maroon":
                val = "#FF7F0000";
                break;
            case "purple":
                val = "#FF7F007F";
                break;
            case "dark blue":
                val = "#FF00007F";
                break;
            case "teal":
                val = "#FF007F7F";
                break;
            case "dark green":
                val = "#FF007F00";
                break;
            case "brown":
                val = "#FFA0714F";
                break;
            case "cyan":
                val = "#FF00FFFF";
                break;
            case "blue":
                val = "#FF0000FF";
                break;
            case "green":
                val = "#FF00FF00";
                break;
            case "red":
                val = "#FFFF0000";
                break;
            case "magenta":
                val = "#FFFF00FF";
                break;
            case "yellow":
                val = "#FFFFFF00";
                break;
            case "rad sensor":
                val = "yellow";
                break;
        }

        try {
            retval = Color.parseColor(val);
        } catch (Exception e) {
            retval = Color.WHITE;
        }

        teamColors.put(name, retval);

        return retval;
    }

    @Override
    public boolean adapt(final Marker marker) {
        return adapt2525cMarkerIcon(marker);
    }

    /**
     * Return true if marker icon was adapted
     * 
     * @param marker the marker to adapt
     * @return true if the marker was adapted
     */
    boolean adapt2525cMarkerIcon(final Marker marker) {
        MapDataRef iconRef = null;
        int color = 0;
        final String type = marker.getType();

        // Check any user added adapters
        for (IconAdapter adapter : adapters) {
            try {
                if (adapter.adapt(marker))
                    return true;
            } catch (Exception e) {
                Log.e(TAG, "error attempting to adapt marker", e);
            }
        }

        if (marker.hasMetaValue("iconUri")) {
            String uri = marker.getMetaString("iconUri", null);
            // FIXME: need to revisit to make more flexible - right now 
            // MapDataRef.parseUri() is being too simple for what I am doing
            // parse of asset://icons/foo gives me AssetMapDataRef point to just 
            // foo and not icons/foo
            if (uri != null) {
                iconRef = new AssetMapDataRef(uri);
            }

        }

        if (marker.hasMetaValue("readiness")
                && !marker.getMetaBoolean("readiness", false)) {
            if (type != null) // Check for affiliation and display correct icon. 99.9% of the time
                              // it should be friendly.
            {
                String iconPath = "icons/damaged.png"; // Friendly by default
                if (type.startsWith("a-h")) // Hostile
                    iconPath = "icons/damaged_hostile.png";
                else if (type.startsWith("a-n")) // Neutral
                    iconPath = "icons/damaged_neutral.png";
                else if (type.startsWith("a-u")) // Unknown
                    iconPath = "icons/damaged_unknown.png";
                iconRef = new AssetMapDataRef(iconPath);
            }
        }

        if (marker.hasMetaValue("team")) {
            color = teamToColor(marker.getMetaString("team", "white"));

            //build out the ATAK icon
            final String role = marker.getMetaString("atakRoleType",
                    "Team Member");
            String iconPath = "icons/roles/";
            if (role.equalsIgnoreCase("HQ")) {
                iconPath += "hq";
            } else if (role.equalsIgnoreCase("Team Lead")) {
                iconPath += "teamlead";
            } else if (role.equalsIgnoreCase("Sniper")) {
                iconPath += "sniper";
            } else if (role.equalsIgnoreCase("K9")) {
                iconPath += "k9";
            } else if (role.equalsIgnoreCase("RTO")) {
                iconPath += "rto";
            } else if (role.equalsIgnoreCase("Medic")) {
                iconPath += "medic";
            } else if (role.equalsIgnoreCase("Forward Observer")) {
                iconPath += "forwardobserver";
            } else {
                iconPath += "team";
            }

            final String how = marker.getMetaString("how", "");
            if (how.startsWith("h-"))
                iconPath += "_human";
            else if (how.startsWith("m-g-l"))
                iconPath += "_nogps";

            iconPath += ".png";

            if (!how.equals("h-e") && marker.hasMetaValue("jumper")) {
                String position = marker.getMetaString("jumper", "");
                String team = marker.getMetaString("team", "white");
                if (position.equals("below"))
                    iconPath = jumperBelow(team);
                else if (position.equals("above"))
                    iconPath = jumperAbove(team);
                color = 0; // bit of a hack, but don't want the blended color for the jumper
                           // icons, the icon's color
                           // will get reset to it's original color when the jumper reaches
                           // the ground and the
                           // default team icon is used
            }
            iconRef = new AssetMapDataRef(iconPath);
        } else if (marker.hasMetaValue("color")
                && marker.getMetaInteger("color", 0) != 0)
            color = marker.getMetaInteger("color", 0);

        if (iconRef == null)
            iconRef = typeIconMap.get(type);

        if (iconRef == null && (_filters != null)) {
            String assetPath = lookupFromFilter(marker);
            if (assetPath != null) {
                String assetUri = AssetMapDataRef.toUri(assetPath);
                iconRef = _refCache.get(assetUri);
                if (iconRef == null) {
                    iconRef = new AssetMapDataRef(assetPath);
                    _refCache.put(iconRef.toUri(), iconRef);
                    typeIconMap.put(type, iconRef);
                }
            }
        }

        if (iconRef == null && marker.hasMetaValue("type")) {
            // look up 2525c
            String assetPath;
            assetPath = _findAssetPath(type);
            if (assetPath != null) {
                String assetUri = AssetMapDataRef.toUri(assetPath);
                iconRef = _refCache.get(assetUri);
                if (iconRef == null) {
                    iconRef = new AssetMapDataRef(assetPath);
                    _refCache.put(iconRef.toUri(), iconRef);
                }
            }
        }

        if (iconRef == null && !MapItem.EMPTY_TYPE.equals(type) &&
                !ATAKUtilities.isSelf(MapView.getMapView(), marker) &&
                marker.getIcon() == null) {
            //Log.d(TAG, "No icon found for type: " + type);
            color = 0;

            if (type != null) {
                if (type.startsWith("a-f") || type.startsWith("a-a")) { //friendly, assumed
                    iconRef = new AssetMapDataRef("icons/unknown-type-f.png");
                } else if (type.startsWith("a-n")) { //neutral
                    iconRef = new AssetMapDataRef("icons/unknown-type-n.png");
                } else if (type.startsWith("a-h") || type.startsWith("a-j")
                        || type.startsWith("a-k") || type.startsWith("a-s")) {//hostile, joker, faker, suspect
                    iconRef = new AssetMapDataRef("icons/unknown-type-h.png");
                } else if (type.startsWith("a-u") || type.startsWith("a-p")) {// unknown, pending
                    iconRef = new AssetMapDataRef("icons/unknown-type-u.png");
                } else {
                    iconRef = new AssetMapDataRef("icons/unknown-type.png");
                }
            }
        }

        if (iconRef != null) {
            Icon.Builder builder = new Icon.Builder();
            builder.setImageUri(0, iconRef.toUri());
            builder.setAnchor(Icon.ANCHOR_CENTER, Icon.ANCHOR_CENTER);
            if (color != 0) {
                builder.setColor(0, color);
            }
            marker.setIcon(builder.build());
        }

        return iconRef != null;
    }

    private boolean _checkAsset(String pathName) {
        boolean found = false;
        try {
            AssetFileDescriptor fd = _context.getAssets().openFd(pathName);
            fd.close();
            found = true;
        } catch (IOException e) {
            // nothing
        }
        return found;
    }

    private String _findAssetPath(final String cotType) {
        String r = null;
        String type2525 = Icon2525cTypeResolver.mil2525cFromCotType(cotType);
        String fileName = Icon2525cPallet.ASSET_PATH + type2525 + ".png";
        if (_checkAsset(fileName)) {
            r = fileName;
        } else {
            int lastDashIdx = cotType.lastIndexOf('-');
            if (lastDashIdx != -1) {
                r = _findAssetPath(cotType.substring(0, lastDashIdx));
            }
        }
        return r;
    }

}
