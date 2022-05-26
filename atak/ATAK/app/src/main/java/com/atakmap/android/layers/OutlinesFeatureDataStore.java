
package com.atakmap.android.layers;

import androidx.annotation.NonNull;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.RuntimeFeatureDataStore;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.feature.style.LabelPointStyle;
import com.atakmap.map.layer.feature.style.LabelPointStyle.ScrollMode;
import com.atakmap.map.layer.raster.AbstractDataStoreRasterLayer2;
import com.atakmap.map.layer.raster.RasterDataStore;
import com.atakmap.map.layer.raster.RasterLayer2;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class OutlinesFeatureDataStore extends RuntimeFeatureDataStore
        implements RasterDataStore.OnDataStoreContentChangedListener, Runnable {

    private final static int[] COLOR_ARRAY = new int[] {
            0xFF00FF00,
            0xFFFFFF00,
            0xFFFF00FF,
            0xFF0000FF,
            0xFF00FFFF,
            0xFFFF0000,
            0xFF007F00,
            0xFF7FFF00,
            0xFF7F00FF,
            0xFF00FFFF,
            0xFF7F0000,
            0xFFFF7F00,
            0xFFFF007F,
            0xFF007FFF,
            0xFF7F7F00,
            0xFF7F007F,
            0xFF007F7F,
            0xFF7F7F7F,
    };

    private final static AttributeSet EMPTY_ATTR = new AttributeSet();

    private int strokeColor;

    private final Map<String, Integer> selectionColors;
    private final Map<String, Long> selectionLayerFSIDs;

    protected final RasterLayer2 layer;

    private final long outlinesFSID;

    private final boolean labels;

    final ExecutorService refresher;

    public OutlinesFeatureDataStore(RasterLayer2 layer,
            int strokeColor, boolean labels) {
        super(MODIFY_FEATURESET_FEATURE_UPDATE | MODIFY_FEATURE_STYLE,
                FeatureDataStore.VISIBILITY_SETTINGS_FEATURE
                        | FeatureDataStore.VISIBILITY_SETTINGS_FEATURESET);

        this.layer = layer;
        this.strokeColor = strokeColor;
        this.labels = labels;

        this.selectionColors = new HashMap<>();
        this.selectionLayerFSIDs = new HashMap<>();

        this.outlinesFSID = this.insertFeatureSetImpl("user", "user",
                "outlines", Double.MAX_VALUE, 0, true).getId();

        this.setFeatureSetVisible(this.outlinesFSID, false);

        this.refresher = Executors.newFixedThreadPool(1, new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable r) {
                Thread t = new Thread(r);
                t.setName("OutlinesFeatureDataStore@"
                        + Integer.toString(
                                OutlinesFeatureDataStore.this.hashCode(), 16)
                        + "-refresher");
                t.setPriority(Thread.NORM_PRIORITY);
                return t;
            }

        });

        if (this.layer instanceof AbstractDataStoreRasterLayer2)
            ((AbstractDataStoreRasterLayer2) this.layer).getDataStore()
                    .addOnDataStoreContentChangedListener(this);
        this.refresh();
    }

    @Override
    public final void refresh() {
        this.refresher.execute(this);
    }

    protected void refreshImpl() {
        Map<String, Boolean> featureVis = new HashMap<>();
        FeatureDataStore.FeatureQueryParameters params = new FeatureDataStore.FeatureQueryParameters();
        params.featureSetIds = Collections.singleton(this.outlinesFSID);

        FeatureCursor result = null;
        try {
            result = this.queryFeatures(params);
            Feature f;
            while (result.moveToNext()) {
                f = result.get();
                featureVis.put(f.getName(), this.isFeatureVisible(f.getId()));
            }
        } finally {
            if (result != null)
                result.close();
        }

        // obtain the selections OUTSIDE of the synchronized block
        Map<String, Geometry> sel = this.getSelections();

        // lock the database and update the geometry
        synchronized (this) {
            this.beginBulkModificationImpl();
            try {
                this.deleteAllFeaturesImpl(this.outlinesFSID);

                for (Long fsid : this.selectionLayerFSIDs.values())
                    this.deleteFeatureSetImpl(fsid);

                this.selectionLayerFSIDs.clear();
                for (Map.Entry<String, Geometry> entry : sel.entrySet()) {
                    String s = entry.getKey();
                    Geometry cov = entry.getValue();

                    if (cov != null) {
                        int color = this.strokeColor;
                        if (color == 0) {
                            Integer c = this.selectionColors.get(s);
                            if (c == null) {
                                c = getColor(s);
                                this.selectionColors.put(s, c);
                            }
                            color = c;
                        }
                        // XXX - stroke width
                        final long outlineFid = this
                                .insertFeatureImpl(
                                        this.outlinesFSID,
                                        s,
                                        cov,
                                        new BasicStrokeStyle(color,
                                                2 * AtakMapView.DENSITY),
                                        EMPTY_ATTR, true)
                                .getId();

                        if (!this.labels)
                            continue;

                        Envelope bounds = cov.getEnvelope();

                        Point labelLoc = new Point(
                                (bounds.maxX + bounds.minX) / 2d, bounds.minY);

                        double minResolution = Double.NaN;
                        if (bounds.maxX != bounds.minX) {
                            // XXX - labels for layers covering the entire globe are not showing
                            // up, but in reality they will all stack on top of themselves
                            // so I don't think it's that big of a deal...

                            // XXX - assumes 720x1280, 96DPI
                            final double screenResolution = ((1.0d / 96.0d)
                                    * (1.0d / 39.37d));
                            final double screenHorizontalExtentMeters = 1280.0d
                                    / 96.0d / 39.37d;
                            final double screenVerticalExtentMeters = 720.0d
                                    / 96.0d
                                    / 39.37d;
                            final double lonExtentMeters = GeoCalculations
                                    .distanceTo(new GeoPoint(
                                            (bounds.maxY + bounds.minY)
                                                    / 2,
                                            bounds.minX),
                                            new GeoPoint(
                                                    (bounds.maxY + bounds.minY)
                                                            / 2,
                                                    bounds.maxX));
                            final double latExtentMeters = GeoCalculations
                                    .distanceTo(new GeoPoint(bounds.maxY,
                                            (bounds.maxX + bounds.minX)
                                                    / 2),
                                            new GeoPoint(bounds.minY,
                                                    (bounds.maxX + bounds.minX)
                                                            / 2));

                            final double C = 20.0d;

                            final double hMinRenderScale = Math.min(Math.max(
                                    screenHorizontalExtentMeters
                                            / (lonExtentMeters * C),
                                    Double.MIN_VALUE), 1.0d);
                            final double vMinRenderScale = Math.min(
                                    Math.max(screenVerticalExtentMeters
                                            / (latExtentMeters * C),
                                            Double.MIN_VALUE),
                                    1.0d);

                            minResolution = screenResolution
                                    / Math.min(hMinRenderScale,
                                            vMinRenderScale);
                        }

                        final long labelFSID = this.insertFeatureSetImpl("user",
                                "user", "label-" + s, minResolution, Double.NaN,
                                true).getId();
                        final long labelFID = this.insertFeatureImpl(
                                labelFSID,
                                s,
                                labelLoc,
                                new LabelPointStyle(s, 0xFFFFFFFF, 0xFF000000,
                                        ScrollMode.OFF),
                                EMPTY_ATTR, true).getId();
                        this.selectionLayerFSIDs.put(s,
                                labelFSID);
                    }
                }

                for (Map.Entry<String, Boolean> entry : featureVis.entrySet()) {
                    params = new FeatureQueryParameters();
                    params.featureNames = Collections.singleton(entry.getKey());

                    this.setFeaturesVisible(params,
                            entry.getValue());
                }
            } finally {
                this.endBulkModificationImpl(true);
            }
        }
    }

    /**
     * Deterministic mapping of string s to a color
     *
     * @param s the string describing the color
     * @return the integer representation of the color
     */
    public static int getColor(final String s) {
        if (FileSystemUtils.isEmpty(s))
            return 0;

        return COLOR_ARRAY[Math.abs(s.hashCode() % COLOR_ARRAY.length)];
    }

    @Override
    public void dispose() {
        if (this.layer instanceof AbstractDataStoreRasterLayer2)
            ((AbstractDataStoreRasterLayer2) this.layer).getDataStore()
                    .removeOnDataStoreContentChangedListener(this);
        this.refresher.shutdown();
        try {
            this.refresher.awaitTermination(Long.MAX_VALUE,
                    TimeUnit.NANOSECONDS);
        } catch (InterruptedException ignored) {
        }
        super.dispose();
    }

    @Override
    public void onDataStoreContentChanged(RasterDataStore dataStore) {
        this.refresh();
    }

    synchronized void setOutlineColor(int color) {
        this.strokeColor = color;
        this.refresh();
    }

    @Override
    public final void run() {
        this.refreshImpl();
    }

    protected Map<String, Geometry> getSelections() {
        Map<String, Geometry> retval = new HashMap<>();
        final Collection<String> sel = this.layer.getSelectionOptions();
        for (String s : sel)
            retval.put(s, this.layer.getGeometry(s));
        return retval;
    }
}
