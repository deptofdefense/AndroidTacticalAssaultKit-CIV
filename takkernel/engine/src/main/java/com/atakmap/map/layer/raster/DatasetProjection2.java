package com.atakmap.map.layer.raster;

import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.math.PointD;
import com.atakmap.util.Releasable;

public interface DatasetProjection2 extends Releasable {
    /**
     * Performs the image-to-ground function for the specified pixel in the
     * image.
     * 
     * @param image     The image pixel
     * @param ground    If non-<code>null</code> returns the geodetic coordinate
     *                  for the given pixel coordinate
     *                  
     * @return  The geodetic coordinate for the specified pixel coordinate. If
     *          <code>ground</code> was non-<code>null</code>,
     *          <code>ground</code> is updated and returned.
     */
    public boolean imageToGround(PointD image, GeoPoint ground);
    
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
    public boolean groundToImage(GeoPoint ground, PointD image);
}
