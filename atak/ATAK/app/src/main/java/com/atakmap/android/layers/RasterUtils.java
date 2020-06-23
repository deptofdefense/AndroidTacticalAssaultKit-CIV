
package com.atakmap.android.layers;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.atakmap.map.layer.raster.RasterLayer2;

import android.content.SharedPreferences;

final class RasterUtils {
    private RasterUtils() {
    }

    static void saveSelectionVisibility(RasterLayer2 layer,
            SharedPreferences.Editor prefs) {
        final String id = layer.getName();

        Collection<String> selections = layer.getSelectionOptions();
        prefs.putInt("num-" + id + "-type-visibility", selections.size());
        int i = 0;
        for (String selection : selections) {
            prefs.putString(id + "-visibility.type" + i,
                    selection);
            prefs.putBoolean(id + "-visibility.value" + i,
                    layer.isVisible(selection));
            prefs.putString(id + "-transparency.type" + i,
                    selection);
            prefs.putFloat(id + "-transparency.value" + i,
                    layer.getTransparency(selection));
            i++;
        }
    }

    static void loadSelectionVisibility(RasterLayer2 layer,
            boolean visibleByDefault, SharedPreferences prefs) {
        final String id = layer.getName();

        Set<String> deviants = new HashSet<>();

        final int numVisibilitySettings = prefs.getInt(
                "num-" + id + "-type-visibility", 0);
        for (int i = 0; i < numVisibilitySettings; i++) {
            String type;
            type = prefs.getString(
                    id + "-visibility.type" + i, null);
            if (type != null) {
                boolean visible = prefs.getBoolean(
                        id + "-visibility.value" + i,
                        visibleByDefault);
                if (visible != visibleByDefault)
                    deviants.add(type);
            }
            type = prefs.getString(
                    id + "-transparency.type" + i, null);
            if (type != null) {
                float transparency = prefs.getFloat(
                        id + "-transparency.value" + i,
                        1f);
                if (transparency != 1f)
                    layer.setTransparency(type, transparency);
            }
        }

        Collection<String> selections = layer.getSelectionOptions();
        for (String opt : selections)
            layer.setVisible(opt, deviants.contains(opt) != visibleByDefault);
    }
}
