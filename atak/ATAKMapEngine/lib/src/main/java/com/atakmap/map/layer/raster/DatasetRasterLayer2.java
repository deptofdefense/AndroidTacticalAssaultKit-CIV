package com.atakmap.map.layer.raster;

import android.graphics.Color;

import java.util.Collection;
import java.util.HashSet;

import com.atakmap.coremap.filesystem.FileSystemUtils;
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

    /**
     * Initialize transparency and visibility vars for dataset descriptor
     * based on its imported state
     * @param desc Dataset
     */
    public void initDataset(DatasetDescriptor desc) {
        // Initial alpha value (default = 255)
        String colorStr = desc.getExtraData("color");
        if (!FileSystemUtils.isEmpty(colorStr)) {
            try {
                int alpha = Color.alpha(Integer.parseInt(colorStr));
                setTransparency(desc.getName(), alpha / 255f);
            } catch (Exception ignored) {
            }
        }

        // Initial visibility (default = true)
        String vizStr = desc.getExtraData("visible");
        if (!FileSystemUtils.isEmpty(vizStr))
            setVisible(desc.getName(), Boolean.parseBoolean(vizStr));
    }
}
