
package com.atakmap.android.layers;

import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.raster.AbstractDataStoreRasterLayer2;
import com.atakmap.map.layer.raster.RasterDataStore;
import com.atakmap.map.layer.raster.mobileimagery.MobileImageryRasterLayer2;
import com.atakmap.spatial.SpatialCalculator;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class MobileOutlinesDataStore extends OutlinesFeatureDataStore {

    private boolean union = true;

    MobileOutlinesDataStore(MobileImageryRasterLayer2 layer,
            int strokeColor) {
        super(layer, strokeColor, false);
    }

    public void setUnion(boolean union) {
        this.union = union;
        this.refresher.execute(this);

    }

    @Override
    protected void refreshImpl() {
        final boolean doUnion = this.union;

        FeatureCursor result = null;
        Map<Long, Geometry> unions;
        try {
            // perform the basic refresh
            super.refreshImpl();
            if (doUnion) {
                // query all Features that have a GeometryCollection as the
                // Geometry -- we are going to perform UnaryUnion on all
                // GeometryCollections
                FeatureQueryParameters params = new FeatureQueryParameters();
                params.geometryTypes = Collections
                        .<Class<? extends Geometry>> singleton(
                                GeometryCollection.class);
                result = this.queryFeatures(params);
            } else {
                // if we aren't supposed to union, that's it
                return;
            }

            unions = new HashMap<>();

            SpatialCalculator calc = null;
            try {
                calc = new SpatialCalculator.Builder().inMemory().build();

                // iterate the GeometryCollections and perform the UnaryUnion
                Feature feature;
                Geometry unionGeom;
                while (result.moveToNext()) {
                    feature = result.get();
                    try {
                        calc.beginBatch();
                        unionGeom = calc.getGeometry(calc.unaryUnion(calc
                                .createGeometry(feature.getGeometry())));
                    } finally {
                        calc.endBatch(false);
                    }
                    if (unionGeom == null)
                        continue;
                    unions.put(feature.getId(), unionGeom);
                }
            } finally {
                if (calc != null)
                    calc.dispose();
            }
        } finally {
            if (result != null)
                result.close();
        }

        // nothing to update
        if (unions.isEmpty())
            return;

        // bulk update the GeometryCollection to the union results
        synchronized (this) {
            this.beginBulkModificationImpl();
            try {
                for (Map.Entry<Long, Geometry> entry : unions.entrySet()) {
                    // XXX - make sure the feature is still present since we
                    //       temporarily released the lock. the need for this
                    //       will become obsolete with the augmented interface
                    if (this.getFeature(entry.getKey()) == null)
                        continue;
                    this.updateFeatureImpl(entry.getKey(),
                            entry.getValue());
                }
            } finally {
                this.endBulkModificationImpl(true);
            }
        }
    }

    @Override
    protected Map<String, Geometry> getSelections() {
        if (this.union)
            return getImageryTypeSelections();
        else
            return getDatasetSelections();
    }

    protected Map<String, Geometry> getImageryTypeSelections() {
        RasterDataStore.DatasetQueryParameters params = new RasterDataStore.DatasetQueryParameters();
        ((MobileImageryRasterLayer2) this.layer).filterQueryParams(params);
        params.remoteLocalFlag = RasterDataStore.DatasetQueryParameters.RemoteLocalFlag.LOCAL;

        RasterDataStore.DatasetDescriptorCursor result = null;
        try {
            result = ((AbstractDataStoreRasterLayer2) this.layer)
                    .getDataStore().queryDatasets(params);
            Map<String, Geometry> retval = new HashMap<>();
            Collection<String> types;
            while (result.moveToNext()) {
                types = result.get().getImageryTypes();
                for (String type : types) {
                    Geometry geom = retval.get(type);
                    if (geom == null) {
                        retval.put(type, result.get().getCoverage(type));
                    } else {
                        if (!(geom instanceof GeometryCollection)) {
                            GeometryCollection c = new GeometryCollection(2);
                            c.addGeometry(geom);
                            geom = c;
                            retval.put(type, geom);
                        }
                        ((GeometryCollection) geom).addGeometry(result.get()
                                .getCoverage(type));
                    }
                }
            }
            return retval;
        } finally {
            if (result != null)
                result.close();
        }
    }

    protected Map<String, Geometry> getDatasetSelections() {
        RasterDataStore.DatasetQueryParameters params = new RasterDataStore.DatasetQueryParameters();
        ((MobileImageryRasterLayer2) this.layer).filterQueryParams(params);
        params.remoteLocalFlag = RasterDataStore.DatasetQueryParameters.RemoteLocalFlag.LOCAL;

        RasterDataStore.DatasetDescriptorCursor result = null;
        try {
            result = ((AbstractDataStoreRasterLayer2) this.layer)
                    .getDataStore().queryDatasets(params);
            Map<String, Geometry> retval = new HashMap<>();
            while (result.moveToNext()) {
                final String type = result.get().getName();
                Geometry geom = retval.get(type);
                if (geom == null) {
                    retval.put(type, result.get().getCoverage(null));
                } else {
                    if (!(geom instanceof GeometryCollection)) {
                        GeometryCollection c = new GeometryCollection(2);
                        c.addGeometry(geom);
                        geom = c;
                        retval.put(type, geom);
                    }
                    ((GeometryCollection) geom).addGeometry(result.get()
                            .getCoverage(null));
                }
            }
            return retval;
        } finally {
            if (result != null)
                result.close();
        }
    }
}
