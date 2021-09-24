package com.atakmap.map.layer.feature.opengl;

import android.util.Pair;

import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerSpi2;

public class PersistentDataSourceFeatureDataStoreGLLayerSpi2 implements GLLayerSpi2 {

    public final static GLLayerSpi2 INSTANCE = new PersistentDataSourceFeatureDataStoreGLLayerSpi2();

    private PersistentDataSourceFeatureDataStoreGLLayerSpi2() {}

    @Override
    public int getPriority() {
        // PersistentDataSourceFeatureDataStore : DataSourceFeatureDataStore : FeatureDataStore
        // FeatureLayer : Layer
        return 3;
    }

    @Override
    public GLLayer2 create(Pair<MapRenderer, Layer> arg) {
        return GLBatchGeometryFeatureDataStoreRenderer.SPI.create(arg);
    }
}
