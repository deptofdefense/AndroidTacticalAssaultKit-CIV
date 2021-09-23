package com.atakmap.map.layer.raster.mobileimagery;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.raster.AbstractDataStoreRasterLayer2;
import com.atakmap.map.layer.raster.RasterDataStore;
import com.atakmap.map.layer.raster.RasterDataStore.DatasetQueryParameters;
import com.atakmap.map.layer.raster.mobac.GoogleCRS84Quad;
import com.atakmap.map.layer.raster.osm.OSMWebMercator;
import com.atakmap.map.layer.raster.service.DefaultOnlineImageryExtension;
import com.atakmap.map.layer.raster.service.OnlineImageryExtension;
import com.atakmap.map.projection.Projection;
import com.atakmap.map.projection.ProjectionFactory;

public class MobileImageryRasterLayer2 extends AbstractDataStoreRasterLayer2 {

    private static Set<String> defaultTypes = new HashSet<String>();
    private static int defaultTypesVersion = 0;

    private OnlineImageryExtension offlineModeSvc;

    private DatasetQueryParameters userFilter;
    private final boolean applyDefaultTypes;
    private int filterDefaultTypesVersion;

    public MobileImageryRasterLayer2(String name, RasterDataStore dataStore,
            DatasetQueryParameters filter) {
        this(name, dataStore, filter, (filter==null));
    }
    
    public MobileImageryRasterLayer2(String name, RasterDataStore dataStore,
            DatasetQueryParameters filter, boolean applyDefaultTypes) {
        super(name,
              dataStore,
              (filter != null) ?
                      new DatasetQueryParameters(filter) : null);
        
        if(filter == null) {
            synchronized(MobileImageryRasterLayer2.class) {
                this.filter.datasetTypes = new HashSet<String>(defaultTypes);
                this.filterDefaultTypesVersion = defaultTypesVersion;
            }
        }
        this.userFilter = filter;
        this.applyDefaultTypes = applyDefaultTypes;

        this.offlineModeSvc = new DefaultOnlineImageryExtension();
        this.registerExtension(this.offlineModeSvc);
    }

    /** @hide */
    public synchronized void setAutoSelectValue(String autoselect) {
        this.setAutoSelectValueNoSync(autoselect);
    }
    
    @Override
    public void filterQueryParams(DatasetQueryParameters params) {
        synchronized(MobileImageryRasterLayer2.class) {
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
                }
                this.filterDefaultTypesVersion = defaultTypesVersion;
            }

            super.filterQueryParams(params);
        }
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
    
    /**************************************************************************/

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
    
    /**************************************************************************/
    
    /**
     * Returns a {@link Projection} instance appropriate for use with mobile
     * imagery.  The projection instances may be different from those returned
     * via {@link ProjectionFactory#getProjection(int)}.
     * 
     * @param srid  The SRID
     * 
     * @return  A <code>Projection</code> instance, or <code>null</code> if no
     *          projection for the given code is available.
     */
    public static Projection getProjection(int srid) {
        switch(srid) {
            case 3857 :
            case 900913 :
                return OSMWebMercator.INSTANCE;
            case 90094326 :
                return GoogleCRS84Quad.INSTANCE;
            default :
                return ProjectionFactory.getProjection(srid);
        }
    }
}
