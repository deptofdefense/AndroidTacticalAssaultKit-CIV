
package com.atakmap.android.layers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.atakmap.android.maps.CardLayer;
import com.atakmap.android.maps.MapView;
import com.atakmap.annotations.DeprecatedApi;
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

    // Sort datasets by GSD ascending
    private static final Comparator<DatasetDescriptor> SORT_GSD = new Comparator<DatasetDescriptor>() {
        @Override
        public int compare(DatasetDescriptor d1, DatasetDescriptor d2) {
            if (d1 instanceof ImageDatasetDescriptor
                    && d2 instanceof ImageDatasetDescriptor) {
                double g1 = getGSD((ImageDatasetDescriptor) d1);
                double g2 = getGSD((ImageDatasetDescriptor) d2);
                return Double.compare(g1, g2);
            } else if (d1 instanceof ImageDatasetDescriptor)
                return -1;
            else if (d2 instanceof ImageDatasetDescriptor)
                return 1;
            return 0;
        }
    };

    /**
     * Query currently loaded imagery
     * @param params Query parameters
     * @param visibleOnly True to only return visible layers
     * @return List of imagery datasets
     */
    public static List<DatasetDescriptor> queryDatasets(
            DatasetQueryParameters params, boolean visibleOnly) {

        final List<DatasetDescriptor> ret = new ArrayList<>();

        final MapView mv = MapView.getMapView();
        if (mv == null)
            return ret;

        // Find the raster layer
        AbstractRasterLayer2 layer = null;
        List<Layer> layers = mv.getLayers(MapView.RenderStack.MAP_LAYERS);
        for (final Layer l : layers) {
            // ensure both conditions are met before assigning the layer
            if (l.getName().equals("Raster Layers") && l instanceof CardLayer) {
                CardLayer cd = (CardLayer) l;
                layer = (AbstractRasterLayer2) cd.get();
                break;
            }
        }
        if (layer == null)
            return ret;

        // Setup query
        boolean mobile = layer instanceof MobileImageryRasterLayer2;
        String selected = layer.getSelection();
        LocalRasterDataStore db = LayersMapComponent.getLayersDatabase();

        // Filter to mobile imagery if the mobile tab is selected
        if (mobile) {
            params.names = Collections.singleton(selected);
            params.providers = Collections.singleton("mobac");
        }

        // Query datasets
        try (DatasetDescriptorCursor c = db.queryDatasets(params)) {
            while (c != null && c.moveToNext()) {
                DatasetDescriptor d = c.get();
                if (d.getProvider().equals("mobac") && !mobile)
                    continue;

                // Filter out invisible imagery
                if (visibleOnly) {
                    boolean visible = false;
                    for (String type : d.getImageryTypes()) {
                        if (layer.isVisible(type) && (selected == null
                                || selected.equals(type))) {
                            visible = true;
                            break;
                        }
                    }
                    if (!visible)
                        continue;
                }

                ret.add(d);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to query datasets", e);
        }

        // Sort datasets by highest to lowest resolution
        // TODO: Support for non-ImageDatasetDescriptor sorting
        Collections.sort(ret, SORT_GSD);

        return ret;
    }

    /**
     * Query currently loaded imagery
     * @param bounds Geo bounds to query (null to use current map bounds)
     * @param visibleOnly True to only return visible layers
     * @return List of imagery datasets
     */
    public static List<DatasetDescriptor> queryDatasets(GeoBounds bounds,
            boolean visibleOnly) {

        MapView mv = MapView.getMapView();
        if (mv == null)
            return new ArrayList<>();

        // Default to current map view bounds
        if (bounds == null)
            bounds = mv.getBounds();

        DatasetQueryParameters params = new DatasetQueryParameters();

        // Filter that includes imagery within the given bounds
        params.spatialFilter = new DatasetQueryParameters.RegionSpatialFilter(
                new GeoPoint(bounds.getNorth(), bounds.getWest()),
                new GeoPoint(bounds.getSouth(), bounds.getEast()));

        return queryDatasets(params, visibleOnly);
    }

    /**
     * @deprecated Use {@link #queryDatasets(GeoBounds, boolean)} instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public static List<ImageDatasetDescriptor> getCurrentImagery(
            MapView mv, GeoBounds bounds) {
        List<DatasetDescriptor> descs = queryDatasets(bounds, true);
        List<ImageDatasetDescriptor> ret = new ArrayList<>(descs.size());
        for (DatasetDescriptor desc : descs) {
            if (desc instanceof ImageDatasetDescriptor)
                ret.add((ImageDatasetDescriptor) desc);
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

    private static double getGSD(ImageDatasetDescriptor desc) {
        return DatasetDescriptor.computeGSD(desc.getWidth(), desc.getHeight(),
                desc.getUpperLeft(), desc.getUpperRight(),
                desc.getLowerRight(), desc.getLowerLeft());
    }
}
