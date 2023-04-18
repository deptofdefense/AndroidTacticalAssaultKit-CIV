package com.atakmap.map.layer.raster;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.DataStoreException;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.FeatureDefinition2;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.FeatureSetCursor;
import com.atakmap.map.layer.feature.Utils;
import com.atakmap.map.layer.feature.datastore.FeatureSetDatabase2;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.feature.style.LabelPointStyle;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.opengl.GLRenderGlobals;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import gov.tak.api.annotation.NonNull;

public class OutlinesFeatureDataStore2 implements FeatureDataStore2, RasterDataStore.OnDataStoreContentChangedListener, Runnable {

    private static final String TAG = "OutlinesFeatureDataStore2";
    final FeatureDataStore2 dataStore;

    private int strokeColor;
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

    private Map<String, Integer> selectionColors = null;
    private Map<String, Long> selectionLayerFSIDs = null;

    protected RasterLayer2 layer = null;

    private long outlinesFSID;

    private boolean labels = false;

    protected ExecutorService refresher = null;

    private final static AttributeSet EMPTY_ATTR = new AttributeSet();

    public OutlinesFeatureDataStore2(RasterLayer2 layer,
                                     int strokeColor, boolean labels, FeatureDataStore2 dataStore) {

        if(dataStore == null) {
            this.dataStore = new FeatureSetDatabase2(null);
        }
        else {
            this.dataStore = dataStore;
        }
        this.layer = layer;
        this.strokeColor = strokeColor;
        this.labels = labels;

        this.selectionColors = new HashMap<>();
        this.selectionLayerFSIDs = new HashMap<>();

        FeatureSet featureSet = new FeatureSet("user", "user", "outlines", Double.MAX_VALUE, 0);

        try {
            this.outlinesFSID = insertFeatureSet(featureSet);
            setFeatureSetVisible(this.outlinesFSID, false);
        }
        catch(DataStoreException dataStoreException) {
            Log.e(TAG, "Exception in OutlinesFeatureDataStore2()", dataStoreException);

        }
        this.refresher = Executors.newFixedThreadPool(1, new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable r) {
                Thread t = new Thread(r);
                t.setName("OutlinesFeatureDataStore@"
                        + Integer.toString(
                        OutlinesFeatureDataStore2.this.hashCode(), 16)
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

    public OutlinesFeatureDataStore2(RasterLayer2 layer,
                                     int strokeColor, boolean labels) {
        this(layer, strokeColor, labels, null);
    }

    @Override
    public void dispose() { dataStore.dispose(); }

    @Override
    public FeatureCursor queryFeatures(FeatureQueryParameters params) throws DataStoreException {
        return dataStore.queryFeatures(params);
    }

    @Override
    public int queryFeaturesCount(FeatureQueryParameters params) throws DataStoreException {
        return dataStore.queryFeaturesCount(params);
    }

    @Override
    public FeatureSetCursor queryFeatureSets(FeatureSetQueryParameters params) throws DataStoreException {
        return dataStore.queryFeatureSets(params);
    }

    @Override
    public int queryFeatureSetsCount(FeatureSetQueryParameters params) throws DataStoreException {
        return dataStore.queryFeatureSetsCount(params);
    }

    @Override
    public long insertFeature(long fsid, long fid, FeatureDefinition2 def, long version)
            throws DataStoreException {
        return dataStore.insertFeature(fsid, fid, def, version);

    }

    @Override
    public long insertFeature(final Feature feature) throws DataStoreException {
        return dataStore.insertFeature(feature);
    }

    @Override
    public void insertFeatures(FeatureCursor features) throws DataStoreException {
        dataStore.insertFeatures(features);
    }

    @Override
    public long insertFeatureSet(FeatureSet featureSet) throws DataStoreException {
        return dataStore.insertFeatureSet(featureSet);
    }

    @Override
    public void insertFeatureSets(FeatureSetCursor featureSet) throws DataStoreException {
        dataStore.insertFeatureSets(featureSet);
    }

    @Override
    public void updateFeature(long fid, int updatePropertyMask, String name, Geometry geometry, Style style, AttributeSet attributes, int attrUpdateType) throws DataStoreException {
        dataStore.updateFeature(fid, updatePropertyMask, name, geometry, style, attributes, attrUpdateType);
    }

    @Override
    public void updateFeatureSet(long fsid, String name, double minResolution, double maxResolution) throws DataStoreException {
        dataStore.updateFeatureSet(fsid, name, minResolution, maxResolution);
    }
    @Override
    public void updateFeatureSet(long fsid, String name) throws DataStoreException {
        dataStore.updateFeatureSet(fsid, name);
    }
    @Override
    public void updateFeatureSet(long fsid, double minResolution, double maxResolution) throws DataStoreException {
        dataStore.updateFeatureSet(fsid, minResolution, maxResolution);
    }

    @Override
    public void deleteFeature(long fid) throws DataStoreException { dataStore.deleteFeature(fid); }

    @Override
    public void deleteFeatures(FeatureQueryParameters params) throws DataStoreException {
        dataStore.deleteFeatures(params);
    }

    @Override
    public void deleteFeatureSet(long fsid) throws DataStoreException { dataStore.deleteFeatureSet(fsid); }
    @Override
    public void deleteFeatureSets(FeatureSetQueryParameters params) throws DataStoreException {
        dataStore.deleteFeatureSets(params);
    }

    @Override
    public void setFeatureVisible(long fid, boolean visible) throws DataStoreException {
        dataStore.setFeatureVisible(fid, visible);
    }

    @Override
    public void setFeaturesVisible(FeatureQueryParameters params, boolean visible) throws DataStoreException {
        dataStore.setFeaturesVisible(params, visible);
    }

    @Override
    public void setFeatureSetVisible(long fsid, boolean visible) throws DataStoreException {
        dataStore.setFeatureSetVisible(fsid, visible);
    }

    @Override
    public void setFeatureSetsVisible(FeatureSetQueryParameters params, boolean visible) throws DataStoreException {
        dataStore.setFeatureSetsVisible(params, visible);
    }

    @Override
    public boolean hasTimeReference() { return dataStore.hasTimeReference(); }
    @Override
    public long getMinimumTimestamp() { return dataStore.getMinimumTimestamp(); }
    @Override
    public long getMaximumTimestamp() { return dataStore.getMaximumTimestamp(); }

    @Override
    public int getModificationFlags() {
        return dataStore.getModificationFlags();
    }

    @Override
    public int getVisibilityFlags() {
        return dataStore.getVisibilityFlags();
    }

    @Override
    public String getUri() { return dataStore.getUri(); }

    @Override
    public boolean hasCache() { return dataStore.hasCache(); }
    @Override
    public void clearCache() { dataStore.clearCache();}
    @Override
    public long getCacheSize() { return dataStore.getCacheSize(); }

    @Override
    public void addOnDataStoreContentChangedListener(OnDataStoreContentChangedListener l) {
        dataStore.addOnDataStoreContentChangedListener(l);
    }

    @Override
    public void removeOnDataStoreContentChangedListener(OnDataStoreContentChangedListener l) {
        dataStore.removeOnDataStoreContentChangedListener(l);
    }

    @Override
    public void acquireModifyLock(boolean bulkModify) throws InterruptedException {
        dataStore.acquireModifyLock(bulkModify);
    }
    @Override
    public void releaseModifyLock() {
        dataStore.releaseModifyLock();
    }

    @Override
    public boolean supportsExplicitIDs() { return dataStore.supportsExplicitIDs(); }

    @Override
    public void onDataStoreContentChanged(RasterDataStore dataStore) {
        this.refresher.execute(this);
    }

    public final void refresh() {
        this.refresher.execute(this);
    }

    @Override
    public void run() {
        this.refreshImpl();
    }

    protected void refreshImpl() {
        Map<String, Boolean> featureVis = new HashMap<>();
        FeatureDataStore2.FeatureQueryParameters params = new FeatureDataStore2.FeatureQueryParameters();
        params.ids = Collections.singleton(this.outlinesFSID);

        FeatureCursor result = null;
        try {
            result = dataStore.queryFeatures(params);
            Feature f;
            while (result.moveToNext()) {
                f = result.get();
                featureVis.put(f.getName(), this.isFeatureVisible(f.getId()));
            }
        }
        catch(DataStoreException dataStoreException) {
            Log.e(TAG, "queryFeatures failed", dataStoreException);
        }
        finally {
            if (result != null)
                result.close();
        }

        // obtain the selections OUTSIDE of the synchronized block
        Map<String, Geometry> sel = this.getSelections();

        // lock the database and update the geometry
        synchronized (this) {
            try {
                this.acquireModifyLock(true);
                try {
                    Utils.deleteFeatures(this, params);

                }
                catch(DataStoreException dataStoreException) {
                    Log.e(TAG, "deleteFeatures failed", dataStoreException);
                }

                try {
                    FeatureDataStore2.FeatureSetQueryParameters setParams = new FeatureDataStore2.FeatureSetQueryParameters();

                    for (Long fsid : this.selectionLayerFSIDs.values()) {
                        setParams.ids = Collections.singleton(fsid);
                        Utils.deleteFeatureSets(this, setParams);
                    }
                }
                catch(DataStoreException dataStoreException) {
                    Log.e(TAG, "deleteFeatureSets failed", dataStoreException);

                }

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
                        Feature feature = new Feature(this.outlinesFSID, s, cov, new BasicStrokeStyle(color,
                                2 * GLRenderGlobals
                                        .getRelativeScaling()), EMPTY_ATTR);

                        try {
                            this.insertFeature(feature);

                        }
                        catch(DataStoreException dataStoreException) {
                            Log.e(TAG, "insertFeature failed", dataStoreException);

                        }

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

                        FeatureSet featureSet = new FeatureSet("user", "user", "label-" + s, minResolution, Double.NaN);

                        try {

                            final long labelFSID = this.insertFeatureSet(featureSet);
                            feature = new Feature(labelFSID, s, labelLoc, new LabelPointStyle(s, 0xFFFFFFFF, 0xFF000000,
                                    LabelPointStyle.ScrollMode.OFF), EMPTY_ATTR);

                            this.insertFeature(feature);
                            this.selectionLayerFSIDs.put(s,
                                    labelFSID);
                        }
                        catch(DataStoreException dataStoreException) {
                            Log.e(TAG, "insertFeatureSet failed", dataStoreException);

                        }
                    }
                }

                for (Map.Entry<String, Boolean> entry : featureVis.entrySet()) {
                    params = new FeatureDataStore2.FeatureQueryParameters();
                    params.names = Collections.singleton(entry.getKey());

                    try {
                        this.setFeaturesVisible(params,
                                entry.getValue());
                    }
                    catch(DataStoreException dataStoreException) {
                        Log.e(TAG, "setFeaturesVisible failed", dataStoreException);

                    }
                }
            }
            catch(InterruptedException interruptedException) {

            }
            finally {
                this.releaseModifyLock();
            }
        }
    }

    private Integer getColor(String s) {
        if (FileSystemUtils.isEmpty(s))
            return 0;

        return COLOR_ARRAY[Math.abs(s.hashCode() % COLOR_ARRAY.length)];
    }

    public synchronized boolean isFeatureVisible(long fid) {
        FeatureDataStore2.FeatureQueryParameters params = new FeatureDataStore2.FeatureQueryParameters();
        params.ids = Collections.singleton(Long.valueOf(fid));
        params.visibleOnly = true;
        params.limit = 1;
        try {
            return this.queryFeaturesCount(params)>0;
        }
        catch(DataStoreException dataStoreException) {
            Log.e(TAG, "queryFeaturesCount failed", dataStoreException);
        }
        return false;
    }

    protected Map<String, Geometry> getSelections() {
        Map<String, Geometry> retval = new HashMap<>();
        final Collection<String> sel = this.layer.getSelectionOptions();
        for (String s : sel)
            retval.put(s, this.layer.getGeometry(s));
        return retval;
    }
    public synchronized void setOutlineColor(int color) {
        this.strokeColor = color;
        this.refresh();
    }

}
