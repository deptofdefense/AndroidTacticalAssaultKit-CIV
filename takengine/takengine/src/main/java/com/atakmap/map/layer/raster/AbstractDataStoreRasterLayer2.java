package com.atakmap.map.layer.raster;

import com.atakmap.map.projection.Projection;

/**
 * A {@link RasterLayer} backed by a {@link RasterDataStore}.
 * 
 * @author Developer
 */
public abstract class AbstractDataStoreRasterLayer2 extends AbstractRasterLayer2 {


    protected RasterDataStore dataStore;
    protected Projection preferredProjection;
    protected RasterDataStore.DatasetQueryParameters filter;    
    
    protected AbstractDataStoreRasterLayer2(String name, RasterDataStore dataStore, RasterDataStore.DatasetQueryParameters filter) {
        super(name);

        if(filter == null)
            filter = new RasterDataStore.DatasetQueryParameters();

        this.dataStore = dataStore;
        this.filter = filter;
        this.preferredProjection = null;
    }

    public void filterQueryParams(RasterDataStore.DatasetQueryParameters params) {
        if(this.filter != null)
            params.intersect(this.filter);
    }

    public synchronized void setPreferredProjection(Projection projection) {
        this.preferredProjection = projection;
        this.dispatchOnPreferredProjectionChangedNoSync();
    }
    
    /**
     * Returns the data store associated with this layer.
     * 
     * @return  The data store associated with this layer.
     */
    public RasterDataStore getDataStore() {
        return this.dataStore;
    }

    /**************************************************************************/
    // Raster Layer

    @Override
    public synchronized Projection getPreferredProjection() {
        return this.preferredProjection;
    }
}
