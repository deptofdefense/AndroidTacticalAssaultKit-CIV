package com.atakmap.map.layer.raster;

import java.util.Collection;
import java.util.HashSet;

import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.raster.service.DefaultOnlineImageryExtension;

public class DatasetRasterLayer2 extends AbstractDataStoreRasterLayer2 {

    protected int datasetLimit;

    public DatasetRasterLayer2(String name, RasterDataStore dataStore, int datasetLimit) {
        this(name, dataStore, null, datasetLimit);
    }

    public DatasetRasterLayer2(String name, RasterDataStore dataStore, RasterDataStore.DatasetQueryParameters filter, int datasetLimit) {
        super(name, dataStore, filter);
        
        this.datasetLimit = datasetLimit;
        
        this.registerExtension(new DefaultOnlineImageryExtension());
    }
    
    public int getDatasetLimit() {
        return this.datasetLimit;
    }
    
    /** @hide */
    public synchronized void setAutoSelectValue(String autoselect) {
        this.setAutoSelectValueNoSync(autoselect);
    }

    /**************************************************************************/
    // Raster Layer

    @Override
    public Collection<String> getSelectionOptions() {
        Collection<String> retval = new HashSet<String>();
        RasterDataStore.DatasetDescriptorCursor result = null;
        try {
            result = this.dataStore.queryDatasets(this.filter);
            while(result.moveToNext())
                retval.add(result.get().getName());
            return retval;
        } finally {
            if(result != null)
                result.close();
        }
    }

    @Override
    public Geometry getGeometry(String selection) {
        return this.dataStore.getCoverage(selection, null);
    }
    
    @Override
    public double getMinimumResolution(String selection) {
        return this.dataStore.getMinimumResolution(selection, null);
    }
    
    @Override
    public double getMaximumResolution(String selection) {
        return this.dataStore.getMaximumResolution(selection, null);
    }
}
