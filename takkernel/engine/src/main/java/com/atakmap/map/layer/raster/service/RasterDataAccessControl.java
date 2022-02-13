package com.atakmap.map.layer.raster.service;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapControl;
import com.atakmap.map.layer.raster.RasterDataAccess2;

/**
 * Control to acquire the
 * {@link com.atakmap.map.layer.raster.RasterDataAccess RasterDataAccess} for
 * the associated {@link com.atakmap.map.layer.raster.RasterLayer RasterLayer2}.
 *  
 * @author Developer
 */
public interface RasterDataAccessControl extends MapControl {
    
    /**
     * Returns the 
     * {@link com.atakmap.map.layer.raster.RasterDataAccess RasterDataAccess} at
     * the specified point or <code>null</code> if there is no data at the
     * specified point or if it is not accessible.
     *
     * @param point The point
     * 
     * @return  The
     *          {@link com.atakmap.map.layer.raster.RasterDataAccess RasterDataAccess}
     *          at the specified point or <code>null</code> if there is no data
     *          at the specified point or if it is not accessible.
     */
    public RasterDataAccess2 accessRasterData(GeoPoint point);

} // RasterDataAccessService
