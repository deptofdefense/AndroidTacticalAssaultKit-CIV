package com.atakmap.map.elevation.quadtree;


import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.math.Rectangle;

import java.util.ArrayList;
import java.util.List;


/**
 * This object is used to store the elevation data for a given quad tree node
 *
 */
public class NodeElevationData
{
    /**
     * This is a list of all the GeoPoints that is used to allow for bulk lookup operations
     */
    private static final List<GeoPoint> pointCache = new ArrayList<>();

    /**
     * This is a list that is used for the bulk lookup operations
     */
    private static final List<GeoPoint> lookupList = new ArrayList<>();

    /**
     * This is a single dimensional array which represents all the y values for the grid
     */
    private final double[] latitudeArray;

    /**
     * This is a single dimensional array which represents all the x values for the grid
     */
    private final double[] longitudeArray;

    /**
     * This is a single dimensional array which is used as a 2D array to store all the
     * elevation values in the grid covered by the node that will need to be computed
     */
    private final double[] elevationArray;

    /**
     * This is the constructor for the elevation data node
     *
     * @param level
     *             The level of the node
     * @param minLatitude
     *             The minimum latitude contained in the node
     * @param maxLatitude
     *             The maximum latitude contained in the node
     * @param minLongitude
     *             The minimum longitude contained in the node
     * @param maxLongitude
     *             The maximum longitude contained in the node
     */
    public NodeElevationData( int level, double minLatitude, double maxLatitude,
                double minLongitude, double maxLongitude )
    {
        int gridSubdivisions = 15;

        double drawGridCellWidth = (maxLongitude-minLongitude) / gridSubdivisions;
        double drawGridCellHeight = (maxLatitude-minLatitude) / gridSubdivisions;

        double drawGridOffsetLng = minLongitude;
        double drawGridOffsetLat = minLatitude;

        int widthOfData = gridSubdivisions + 1;
        int heightOfData = gridSubdivisions + 1;
        latitudeArray = new double[heightOfData];
        longitudeArray = new double[widthOfData];
        int size = widthOfData * heightOfData;
        elevationArray = new double[size];

        for( int x = 0; x < longitudeArray.length; x++ )
        {
            longitudeArray[x] = drawGridOffsetLng + ( x * drawGridCellWidth );
        }

        for( int y = 0; y < latitudeArray.length; y++ )
        {
            latitudeArray[y] = drawGridOffsetLat + ( y * drawGridCellHeight );
        }

        for( int i = 0; i < size; i++ )
        {
            elevationArray[i] = Double.NaN;
        }
    }

    /**
     * When called this will use the {@link ElevationManager} to compute the elevation for all
     * the slots in the grid defined by the elevation data
     */
    void populateElevation( )
    {
        synchronized( pointCache )
        {
            pointCache.addAll(lookupList);
            GeoPoint point;
            int size;
            lookupList.clear( );
            for( double latitude : latitudeArray )
            {
                for( double longitude : longitudeArray )
                {
                    size = pointCache.size( );
                    if( size > 0 )
                    {
                        point = pointCache.remove( size - 1 );
                        point.set( latitude, longitude );
                    }
                    else
                    {
                        point = GeoPoint.createMutable().set( latitude, longitude );
                    }
                    lookupList.add( point );
                }
            }
            ElevationManager.getElevation( lookupList.iterator( ), elevationArray, null, null );

            for (int i = 0; i < elevationArray.length;++i) 
                if (Double.isNaN(elevationArray[i]))
                   elevationArray[i] = GeoPoint.UNKNOWN;
        }
    }

    /**
     * This will look up the elevation stored in this given node
     *
     * @param latitude
     *             The longitude of the point to get the elevation for
     * @param longitude
     *             The longitude of the point to get the elevation for
     * @param interpolate
     *             true if interpolation should be preformed to improve the results, but
     *             also taking more time to compute
     * @return The elevation or NaN if this is not a resolved data node or the elevation is unknown
     */
    public double getElevation( double latitude, double longitude, boolean interpolate )
    {
        double minLongitude = longitudeArray[0];
        int lastXIndex = longitudeArray.length - 1;
        double maxLongitude = longitudeArray[lastXIndex];
        double xIndex =
                    ( ( longitude - minLongitude ) / ( maxLongitude - minLongitude ) ) * lastXIndex;

        double minLatitude = latitudeArray[0];
        int lastYIndex = latitudeArray.length - 1;
        double maxLatitude = latitudeArray[lastYIndex];
        double yIndex = ( ( latitude - minLatitude ) / ( maxLatitude - minLatitude ) ) * lastYIndex;

        if(!Rectangle.contains(minLongitude, minLatitude, maxLongitude, maxLatitude, longitude, latitude))
            throw new IllegalArgumentException();
            
        double elevation;
        if( interpolate )
        {
            int leftX = (int) ( xIndex );
            double xRemainder = ( xIndex - leftX );
            int bottomY = (int) ( yIndex );
            double yRemainder = ( yIndex - bottomY );

            int index = ( bottomY * longitudeArray.length ) + leftX;
            double bottomLeft = elevationArray[index];
            double bottomRight = bottomLeft;
            if( leftX < lastXIndex )
            {
                bottomRight = elevationArray[( index + 1 )];
            }
            double topLeft = bottomLeft;
            double topRight = bottomRight;
            if( bottomY < lastYIndex )
            {
                index += longitudeArray.length;
                topLeft = elevationArray[index];
                if( leftX < lastXIndex )
                {
                    topRight = elevationArray[( index + 1 )];
                }
                else
                {
                    topRight = topLeft;
                }
            }
            double interpolatedTop = ( topLeft * ( 1 - xRemainder ) ) + ( topRight * xRemainder );
            double interpolatedBottom =
                        ( bottomLeft * ( 1 - xRemainder ) ) + ( bottomRight * xRemainder );
            elevation = ( interpolatedBottom * ( 1 - yRemainder ) ) + ( interpolatedTop
                        * yRemainder );
        }
        else
        {
            int x = (int) ( xIndex + 0.5 );
            int y = (int) ( yIndex + 0.5 );
            int index = ( y * longitudeArray.length ) + x;
            elevation = elevationArray[index];
        }
        return elevation;
    }
}
