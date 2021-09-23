
package com.atakmap.android.menu;

import android.net.Uri;

import com.atakmap.android.config.FiltersConfig;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.assets.MapAssets;
import com.atakmap.coremap.log.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MenuMapAdapter {

    private FiltersConfig _filters;
    private final Map<String, String> _menus = new HashMap<>();

    private static final String _TAG = "MenuMapAdapter";

    public void loadMenuFilters(MapAssets mapAssets, String filtersPath)
            throws IOException {
        try (InputStream in = mapAssets
                .getInputStream(Uri.parse(filtersPath))) {
            _filters = FiltersConfig.parseFromStream(in);
            if (_filters != null)
                _filters.setComparator("type",
                        new FiltersConfig.StringStartsWithComparator());
        } catch (Exception e) {
            Log.e(_TAG, "Error loading filters for MenuMapAdapter", e);
        }
    }

    /**
     * Registers a type where the match will be if and items type starts with this type, then
     * use the menu.
     */
    public void addFilter(final String typeGroup, final String menu) {
        _menus.put(typeGroup, menu);
    }

    /**
     * Registers a type where the match will be if and items type starts with this type, then
     * use the menu.
     */
    public void removeFilter(final String typeGroup) {
        _menus.remove(typeGroup);
    }

    public String lookup(final String type) {
        // temporary implementation
        final MapItem mi = new com.atakmap.android.maps.Marker(
                com.atakmap.coremap.maps.coords.GeoPoint.ZERO_POINT,
                "not-a-valid-item");
        mi.setType(type);
        return lookup(mi);
    }

    public String lookup(final MapItem item) {
        FiltersConfig.Filter filter;
        if (_filters != null && !item.getMetaBoolean("ignoreMenu", false)) {

            try {
                String item_type = item.getMetaString("type", "");

                for (Map.Entry<String, String> entry : _menus.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    if (item_type.startsWith(key))
                        return value;
                }
            } catch (Exception e) {
                Log.w(_TAG, "Error finding menu", e);
            }

            final Map<String, Object> metadata = new HashMap<>();
            try {
                item.getMetaData(metadata);
            } catch (Exception e) {
                Log.e(_TAG,
                        "error looking up the specific menu, show the generic one for: "
                                + item.getType());
                metadata.put("type", item.getType());
            }
            if ((filter = _filters.lookupFilter(metadata)) != null) {

                item.setClickable(true);

                // MenuFilter checks the "type" of an object, this creates a problem with damaged
                // units since they have a special menu but have to have a cot type as well.
                // So we check here to see if the object is ready (which should default to true)
                if (item.getType().equals("a-f-G")
                        && item.hasMetaValue("readiness")
                        && !item.getMetaBoolean("readiness", false))
                    return "menus/damaged_friendly.xml";
                else
                    return filter.getValue();
            }
        }

        if (!item.hasMetaValue("menu")
                && !item.getMetaBoolean("ignoreMenu", false)) {
            item.setClickable(true);
            if (item.hasMetaValue("type")) // if it has a type defined, allow interface to change
                                           // it
                return "menus/default_item_w_type.xml";
            else
                return "menus/default_item.xml";
        }

        return null;
    }
}
