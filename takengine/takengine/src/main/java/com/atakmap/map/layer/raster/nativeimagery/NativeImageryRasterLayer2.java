package com.atakmap.map.layer.raster.nativeimagery;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.raster.AbstractDataStoreRasterLayer2;
import com.atakmap.map.layer.raster.RasterDataStore;
import com.atakmap.map.layer.raster.RasterDataStore.DatasetQueryParameters;

public class NativeImageryRasterLayer2 extends AbstractDataStoreRasterLayer2 {

    private static final String TAG = "NativeImageryRasterLayer2";

    private static Set<String> defaultTypes = new HashSet<String>();
    private static int defaultTypesVersion = 0;

    private DatasetQueryParameters userFilter;
    private final boolean applyDefaultTypes;
    private int filterDefaultTypesVersion;

    public NativeImageryRasterLayer2(String name, RasterDataStore dataStore,
            DatasetQueryParameters filter) {
        this(name, dataStore, filter, (filter==null));
    }
    
    public NativeImageryRasterLayer2(String name, RasterDataStore dataStore,
            DatasetQueryParameters filter, boolean applyDefaultTypes) {
        super(name,
              dataStore,
              (filter != null) ?
                      new DatasetQueryParameters(filter) : null);

        if(filter == null) {
            this.filter.remoteLocalFlag = DatasetQueryParameters.RemoteLocalFlag.LOCAL;
            synchronized(NativeImageryRasterLayer2.class) {
                this.filter.datasetTypes = new HashSet<String>(defaultTypes);
                this.filterDefaultTypesVersion = defaultTypesVersion;
            }
        }
        this.userFilter = filter;
        this.applyDefaultTypes = applyDefaultTypes;
    }

    @Override
    public void filterQueryParams(DatasetQueryParameters params) {
        synchronized(NativeImageryRasterLayer2.class) {
            // if the client has requested that default types be applied to the
            // filter parameters, and the default types have changed, reset the
            // filter
            if(this.applyDefaultTypes && this.filterDefaultTypesVersion != defaultTypesVersion) {
                // clear the filter
                DatasetQueryParameters.clear(this.filter);
                // if the user specified a filter, apply it
                if(this.userFilter != null) {
                    // intersect the user filter
                    this.filter.intersect(this.userFilter);
                    // apply the default types
                    if(this.filter.datasetTypes == null)
                        this.filter.datasetTypes = new HashSet<String>(defaultTypes);
                    else
                        this.filter.datasetTypes.addAll(defaultTypes);
                } else {
                    // no user filter was specified, simply set the types and
                    // the local only flag
                    this.filter.datasetTypes = new HashSet<String>(defaultTypes);
                    this.filter.remoteLocalFlag = DatasetQueryParameters.RemoteLocalFlag.LOCAL;
                }
                if(this.filterDefaultTypesVersion != defaultTypesVersion) {
                    Thread t = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            dispatchOnSelectionOptionsChanged();
                            
                            // XXX - this is hacky,
                            synchronized(NativeImageryRasterLayer2.this) {
                                dispatchOnSelectionChangedNoSync();
                            }
                        }
                    }, TAG + "-OnSelectionChanged");
                    t.setPriority(Thread.MIN_PRIORITY);
                    t.start();
                }
                this.filterDefaultTypesVersion = defaultTypesVersion;
            }

            super.filterQueryParams(params);
        }
    }

    /** @hide */
    public synchronized void setAutoSelectValue(String autoselect) {
        this.setAutoSelectValueNoSync(autoselect);
    }
    
    /**************************************************************************/
    // Raster Layer
    
    @Override
    public Collection<String> getSelectionOptions() {
        DatasetQueryParameters params = new DatasetQueryParameters();
        this.filterQueryParams(params);

        Collection<String> retval = new HashSet<String>();
        RasterDataStore.DatasetDescriptorCursor result = null;
        try {
            result = this.dataStore.queryDatasets(params);
            while(result.moveToNext())
                retval.addAll(result.get().getImageryTypes());
            return retval;
        } finally {
            if(result != null)
                result.close();
        }        
    }

    @Override
    public Geometry getGeometry(String selection) {
        return this.dataStore.getCoverage(null, selection);
    }
    
    @Override
    public double getMinimumResolution(String selection) {
        return this.dataStore.getMinimumResolution(null, selection);
    }
    
    @Override
    public double getMaximumResolution(String selection) {
        return this.dataStore.getMaximumResolution(null, selection);
    }

    public synchronized static void registerDatasetType(String string) {
        defaultTypes.add(string);
        defaultTypesVersion++;
    }

    public synchronized static void unregisterDatasetType(String string) {
        defaultTypes.remove(string);
        defaultTypesVersion++;
    }
    
    public synchronized static void getDefaultDatasetTypes(Collection<String> types) {
        types.addAll(defaultTypes);
    }
}
