package com.atakmap.map.elevation;

import java.util.Iterator;

import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;

public interface ElevationData {
    public final static int MODEL_TERRAIN = 0x01;
    public final static int MODEL_SURFACE = 0x02;

    /**
     * Returns a flag indicating the elevation model (terrain or surface).
     * 
     * @return  A flag indicating the model
     */
    public int getElevationModel();
    /**
     * Obtains the underlying data type (e.g. DTED1)
     * 
     * @return
     */
    public String getType();
    
    /**
     * The nominal resolution, in meters, of the data.
     * 
     * @return
     */
    public double getResolution();
    
    /**
     * Returns the elevation, as meters HAE, at the specified location. A value
     * of <code>Double.NaN</code> is returned if no elevation is available.
     * 
     * @param latitude  The latitude
     * @param longitude The longitude
     * 
     * @return  The elevation value at the specified location, in meters HAE, or
     *          <code>Double.NaN</code> if not available.
     */
    public double getElevation(double latitude, double longitude);
    
    /**
     * Returns elevation values for a set of points.
     * 
     * @param points        The points 
     * @param elevations    Returns the elevation values for the specified
     *                      points
     * @param hint          If non-<code>null</code> specifies a minimum
     *                      bounding box containing all points. The
     *                      implementation may use this information to prefetch
     *                      all data that will be required up front, possibly
     *                      reducing IO.
     */
    public void getElevation(Iterator<GeoPoint> points, double[] elevations, Hints hints);
    
    public final static class Hints {
        /** If <code>true</code> prefer fast read time over data precision */ 
        public boolean preferSpeed;
        /** Indicates the sampling resolution */
        public double resolution;
        /** If <code>true</code> values will be interpolated */
        public boolean interpolate;
        /** The query region */
        public GeoBounds bounds;
        
        public Hints() {
            this(false, Double.NaN, true, null);
        }
        
        public Hints(Hints other) {
            this(other.preferSpeed, other.resolution, other.interpolate, other.bounds);
        }
        
        private Hints(boolean preferSpeed, double resolution, boolean interpolate, GeoBounds bounds) {
            this.preferSpeed = preferSpeed;
            this.resolution = resolution;
            this.interpolate = interpolate;
            this.bounds = bounds;
        }
    }
}
