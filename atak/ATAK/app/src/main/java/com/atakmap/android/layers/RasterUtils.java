
package com.atakmap.android.layers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.atakmap.android.maps.CardLayer;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.raster.AbstractRasterLayer2;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.ImageDatasetDescriptor;
import com.atakmap.map.layer.raster.LocalRasterDataStore;
import com.atakmap.map.layer.raster.RasterDataStore.DatasetDescriptorCursor;
import com.atakmap.map.layer.raster.RasterDataStore.DatasetQueryParameters;
import com.atakmap.map.layer.raster.RasterLayer2;
import com.atakmap.map.layer.raster.mobileimagery.MobileImageryRasterLayer2;

import android.content.SharedPreferences;

public final class RasterUtils {

    private static final String TAG = "RasterUtils";

    /**
     * Get currently loaded imagery
     * @param mv Map view
     * @param bounds Geo bounds to check (null to use current map bounds)
     * @return List of imagery datasets
     */
    public static List<ImageDatasetDescriptor> getCurrentImagery(
            MapView mv, GeoBounds bounds) {
        List<ImageDatasetDescriptor> ret = new ArrayList<>();

        // Find the raster layer
        AbstractRasterLayer2 layer = null;
        List<Layer> layers = mv.getLayers(MapView.RenderStack.MAP_LAYERS);
        for (Layer l : layers) {
            if (!l.getName().equals("Raster Layers"))
                continue;
            CardLayer cd = (CardLayer) l;
            layer = (AbstractRasterLayer2) cd.get();
            break;
        }
        if (layer == null)
            return ret;

        // Default to current map view bounds
        if (bounds == null)
            bounds = mv.getBounds();

        // Setup query
        boolean mobile = layer instanceof MobileImageryRasterLayer2;
        LocalRasterDataStore db = LayersMapComponent.getLayersDatabase();
        DatasetQueryParameters dp = new DatasetQueryParameters();

        // Filter to mobile imagery if the mobile tab is selected
        if (mobile) {
            dp.names = Collections.singleton(layer.getSelection());
            dp.providers = Collections.singleton("mobac");
        }

        // Filter that includes imagery within the given bounds
        dp.spatialFilter = new DatasetQueryParameters.RegionSpatialFilter(
                new GeoPoint(bounds.getNorth(), bounds.getWest()),
                new GeoPoint(bounds.getSouth(), bounds.getEast()));

        // Query datasets
        DatasetDescriptorCursor c = null;
        try {
            c = db.queryDatasets(dp);
            while (c != null && c.moveToNext()) {
                DatasetDescriptor d = c.get();
                if (!(d instanceof ImageDatasetDescriptor))
                    continue;
                ImageDatasetDescriptor info = (ImageDatasetDescriptor) d;
                if (info.getProvider().equals("mobac") && !mobile)
                    continue;
                ret.add(info);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to query datasets", e);
        } finally {
            if (c != null)
                c.close();
        }

        return ret;
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
