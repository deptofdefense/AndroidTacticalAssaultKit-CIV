package transapps.geom;

import android.os.Parcelable;

/**
 * An interface that represents a point in model space.  Not called "point" because thats a bit overused.
 * 
 * For geo points, x, y, and z will be longitude*1e6, latitude*1e6, and meters HAE respectively.
 * 
 * @author mriley
 */
public interface Coordinate extends Parcelable {

    /**
     * @return X coord
     */
    int getX();
    
    /**
     * @return Y coord
     */
    int getY();
    
    /**
     * @return Z coord
     */
    int getZ();
    
    /**
     * @param x coord
     */
    void setX( int x );
    
    /**
     * @param y coord
     */
    void setY( int y );
    
    /**
     * @param z coord
     */
    void setZ( int z );
    
    /**
     * Set both x and y at once
     * @param x
     * @param y
     */
    void setCoords( int x, int y );
    
    /**
     * @param point
     * @return The distance from this Coordinate to Coordinate in
     * model units
     */
    int distanceTo( Coordinate point );
    
    /**
     * @param point
     * @return The bearing from this Coordinate to the one passed in
     * model 0
     */
    float bearingTo( Coordinate point );
    
    /**
     * Calculate a Coordinate that is the specified distance and bearing away from this Coordinate.
     *
     */
    Coordinate destinationPoint(final double aDistanceInMeters, final float aBearingInDegrees);
    

    String toDoubleString();
    
}
