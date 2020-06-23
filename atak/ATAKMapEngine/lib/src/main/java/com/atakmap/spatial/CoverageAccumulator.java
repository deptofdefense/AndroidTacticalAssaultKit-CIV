package com.atakmap.spatial;

import java.nio.ByteBuffer;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;
import com.atakmap.spatial.SpatialCalculator;


/**
 * Helper class used to accumulate and union boundries into a single polygonal
 * bounding box. This class uses a SpatialCalculator behind the scenes to
 * do it's calculations.
 *
 */
public class CoverageAccumulator {
    
    private long aggrigate = Long.MAX_VALUE;
    private static double BUFFER_SIZE = .001;
    
    private SpatialCalculator calculator;
    
    private double minGSD = Double.NaN;
    private double maxGSD = Double.NaN;
    
    /**
     * Creates a CoverageAccumulator instance backed by a
     * provided SpatialCalculator. This allows the creation
     * of a CoverageAccumulator that does not use a static
     * instance of SpatialCalculator to back it. When using
     * this constructor, the user is responsible for cleanup/disposal
     * of the provided SpatialCalculator.
     * @param calculator SpatialCalculator to back this CoverageAccumulator
     * with. If this value is null, a static instance of SpatialCalculator
     * will be used. Calling this method with null is functionally 
     * equivalent to calling {@link #CoverageAccumulator()}.
     */
    public CoverageAccumulator(SpatialCalculator calculator){
        if(calculator == null)
            throw new NullPointerException();
        this.calculator = calculator;
    }
    
    public long getCoverageHandle() {
        return this.aggrigate;
    }
    
    /**
     * Adds a rectangular bounding box to the overall bounding polygon currently being 
     * accumulated.
     * @param ul Upper Left corner of the rectangular bounding box.
     * @param ur Upper right corner of the rectangular bounding box.
     * @param lr Lower right corner of the rectangular bounding box.
     * @param ll Lower left corner of the rectangular bounding box.
     */
    public void addBounds(GeoPoint ul, GeoPoint ur, GeoPoint lr, GeoPoint ll){
        long newPoly = calculator.createPolygon(ul, ur, lr, ll);
        
        if(aggrigate == Long.MAX_VALUE){
            aggrigate = newPoly;
        }else{
            calculator.unionWithBuffer(aggrigate, newPoly, BUFFER_SIZE, aggrigate);
        }
    }
    
    /**
     * Adds a geometric bounding box to the overall bounding polygon currently being accumulated.
     * @param geometryBlob Blob of geometric data stored in the spatialite blob format. This method
     * is suitable for use with data directly returned by the {@link SpatialCalculator#getGeometryAsBlob(long)}
     * method.
     * 
     * @see http://www.gaia-gis.it/gaia-sins/BLOB-Geometry.html
     */
    public void addBounds(byte[] geometryBlob){
        long newPoly = calculator.createGeometry(geometryBlob);
        
        if(aggrigate == Long.MAX_VALUE){
            aggrigate = newPoly;
        }else{
            calculator.unionWithBuffer(aggrigate, newPoly, BUFFER_SIZE, aggrigate);
        }
    }
    
    /**
     * Adds a geometric bounding box to the overall bounding polygon currently being accumulated.
     * @param geometry Geometry object that will be converted to wkb format before being added
     * to the accumulated area.
     */
    public void addBounds(Geometry geometry) {
        byte[] wkb = new byte[geometry.computeWkbSize()];
        geometry.toWkb(ByteBuffer.wrap(wkb));
        
        addWkbBounds(wkb);
    }
    
    /**
     * Adds a geometric bounding box to the overall bounding polygon currently being accumulated.
     * @param wkbGeometry Blob of geometric data stored in the WKB (Well Known Blob) format. Suitable
     * for use with data returned by the {@link Geometry#toWkb(java.nio.ByteBuffer)} method of a
     * class extending the Geometry abstract class.
     * 
     * @see https://en.wikipedia.org/wiki/Well-known_text#Well-known_binary
     */
    public void addWkbBounds(byte[] wkbGeometry){
        long newPoly = calculator.createWkbGeometry(wkbGeometry);
        
        if(aggrigate == Long.MAX_VALUE){
            aggrigate = newPoly;
        }else{
            calculator.unionWithBuffer(aggrigate, newPoly, BUFFER_SIZE, aggrigate);
        }
    }
    
    /**
     * Clears the geometry/bounding boxes for this CoverageAccumulator. If this 
     * CoverageAccumulator is backed by a static SpatialCalculator instance, this
     * method will not effect the bounding boxes of other instances of 
     * CoverageAccumulator also backed by that same static instance.
     */
    public void clear(){
        calculator.deleteGeometry(aggrigate);
    }
    
    /**
     * Gets the current combined coverage of all bounding boxes that have been
     * added to this instance.
     * @return Geometry data stored in the spatialite blob format. This data can be
     * transformed into {@link Geometry} instances using the 
     * {@link GeometryFactory#parseSpatiaLiteBlob(byte[])} method.
     */
    public byte[] getCoverageGeometryBlob(){
        return calculator.getGeometryAsBlob(aggrigate);
    }
    
    /**
     * Gets the current combined coverage of all bounding boxes that have been added
     * to this instance.
     * @return Geometry object representing the accumulated coverage.
     */
    public Geometry getCoverageGeometry() {
        byte[] blob = getCoverageGeometryBlob();
        if(blob == null){
            return null;
        }
        
        return GeometryFactory.parseSpatiaLiteBlob(blob);
    }
    
    /**
     * Adds a GSD to be tracked by this CoverageAccumulator. This GSD will automatically
     * be classified as either a max or min GSD, if appropriate.
     * @param gsd Ground Sample Distance in Meters per Pixel.
     */
    public void addGSD(double gsd) {
        if (Double.isNaN(this.minGSD) || gsd > this.minGSD)
            this.minGSD = gsd;
        if (Double.isNaN(this.maxGSD) || gsd < this.maxGSD)
            this.maxGSD = gsd;
    }
    
    
    /**
     * Get the minimum ground sample distance for this area of coverage. This value is not
     * calculated from data added by the {@link #addBounds(byte[])} or 
     * {@link CoverageAccumulator#addBounds(GeoPoint, GeoPoint, GeoPoint, GeoPoint)
     * methods, but is instead based on data added through the {@link #addGSD(double)}
     * method.
     * @return Minimum Ground Sample Distance for this area of coverage in Meters per Pixel.
     */
    public double getMinGSD(){
        return this.minGSD;
    }
    
    /**
     * Get the maximum ground sample distance for this area of coverage. This value is not
     * calculated from data added by the {@link #addBounds(byte[])} or 
     * {@link CoverageAccumulator#addBounds(GeoPoint, GeoPoint, GeoPoint, GeoPoint)
     * methods, but is instead based on data added through the {@link #addGSD(double)}
     * method.
     * @return Maximum Ground Sample Distance for this area of coverage in Meters per Pixel.
     */
    public double getMaxGSD(){
        return this.maxGSD;
    }
    
}
