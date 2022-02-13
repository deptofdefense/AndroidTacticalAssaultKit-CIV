package com.atakmap.map.layer.raster;

import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.math.PointD;

/**
 * Provides access to the underlying raster data in the {@link RasterLayer}.
 * 
 * <P>The image-to-ground and ground-to-image functions provide conversion
 * between pixel coordinates in the image and geodetic coordinates. This
 * coordinate transformation is based on the projection of the imagery as well
 * as the registration of the imagery on the map and should be considered an
 * interpolative transformation. These functions are not sufficient for imagery
 * with robust image-to-ground and ground-to-image functions. Data with robust
 * functions should utilize the {@link PrecisionRasterDataAccess} subinterface. 
 * 
 * @author Developer
 * 
 * @see com.atakmap.map.layer.raster.PrecisionRasterDataAccess
 * @see com.atakmap.map.layer.raster.RasterLayer#getService(Class)
 * @see com.atakmap.map.layer.raster.service.RasterDataAccessService
 */
public interface RasterDataAccess2 {
    /**
     * The URI for the raster data.
     * 
     * @return The URI for the raster data
     */
    public String getUri();
    
    /**
     * Performs the image-to-ground function for the specified pixel in the
     * image.
     * 
     * @param image     The image pixel
     * @param ground    If non-<code>null</code> returns the geodetic coordinate
     *                  for the given pixel coordinate
     *                  
     * @return  The geodetic coordinate for the specified pixel coordinate. If
     *          <code>ground</code> was non-<code>null</code> and READ_WRITE,
     *          <code>ground</code> is updated and returned.
     */
    public boolean imageToGround(PointD image, GeoPoint ground, boolean[] precise);
    
    /**
     * Performs the ground-to-image function for the specified geodetic
     * coordinate.
     * 
     * @param ground    A geodetic coordinate
     * @param image     If non-<code>null</code> returns the pixel coordinate
     *                  for the given geodetic coordinate
     *                  
     * @return  The pixel coordinate for the specified geodetic coordinate. If
     *          <code>image</code> was non-<code>null</code>, <code>image</code>
     *          is updated and returned.
     */
    public boolean groundToImage(GeoPoint ground, PointD image, boolean[] precise);
    
    /**
     * Returns the type of the raster data.
     * 
     * @return  The type of the raster data
     * 
     * @see com.atakmap.map.layer.raster.DatasetDescriptor#getImageryTypes()
     */
    public String getType();
    
    /**
     * Returns the spatial reference ID for the raster data.
     * 
     * @return  The spatial reference ID for the raster data or <code>-1</code>
     *          if the spatial reference is not well-defined.
     */
    public int getSpatialReferenceId();
    
    public boolean hasPreciseCoordinates();
    
    public int getWidth();
    public int getHeight();
} // RasterDataAccess
