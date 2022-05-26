package com.atakmap.map.elevation.quadtree;


/**
 * This object represents a quad tree implementation that allows the storage of elevation data
 * in grid aligned nodes where lower nodes can provide more detailed grid representations.
 *
 */
public class ElevationQuadTree
{
    /**
     * This value is used to tell how long (in milliseconds) that nodes should be kept in the
     * tree if they were not used. So if a node was not used for this amount of time it
     * is susceptible to being pruned
     */
    private static final long PRUNE_TIME_IN_MILLS = 30 * 1000;

    /**
     * This is the root node for the tree
     */
    private final ElevationNode rootNode;

    /**
     * This is the constructor for the quad tree
     */
    public ElevationQuadTree( )
    {
        rootNode = new ElevationNode( 0, -270, 90, -180, 180 );
    }

    /**
     * This will get the node at the given latitude and longitude that is either at the max level
     * or is the lowest data node that is resolved or the first data node that is also not resolved
     * @param latitude The latitude to get the node for
     * @param longitude The longitude to get the node for
     * @param maxLevel The max level of the node to return
     * @return The node at the given location
     */
    public ElevationNode getElevationNode( double latitude, double longitude, int maxLevel )
    {
        ElevationNode node = rootNode.getNodeAt( latitude, longitude, maxLevel );
        if( node == null )
        {
            while( latitude < -90 )
            {
                latitude = -latitude - 180;
                longitude += 180;
            }
            while( latitude > 90 )
            {
                latitude = 180 - latitude;
                longitude += 180;
            }
            while( longitude < -180 )
            {
                longitude += 360;
            }
            while( longitude > 180 )
            {
                longitude -= 360;
            }
            node = rootNode.getNodeAt( latitude, longitude, maxLevel );
        }

        if( ( node.getLevel( ) < maxLevel ) && ( !( node.isDataNode( ) ) ) )
        {
            node = node.getChildNodeAt( latitude, longitude );
        }
        return node;
    }

    /**
     * This will get the interpolated elevation at the given location up to the max level
     *
     * @param latitude
     *             The latitude to get the elevation for
     * @param longitude
     *             The longitude to get the elevation for
     * @param maxLevel
     *             The max level of the node to return
     * @return The elevation at the given location or {@link Double#NaN} if the elevation is unknown
     * or hasn't been computed yet
     */
    public double getElevation( double latitude, double longitude, int maxLevel )
    {
        ElevationNode node = rootNode.getNodeAt( latitude, longitude, maxLevel );
        if( node == null )
        {
            while( latitude < -90 )
            {
                latitude = -latitude - 180;
                longitude += 180;
            }
            while( latitude > 90 )
            {
                latitude = 180 - latitude;
                longitude += 180;
            }
            while( longitude < -180 )
            {
                longitude += 360;
            }
            while( longitude > 180 )
            {
                longitude -= 360;
            }
            node = rootNode.getNodeAt( latitude, longitude, maxLevel );
        }

        double elevation = Double.NaN;
        if( node.isDataNode( ) )
        {
            elevation = node.getElevation( latitude, longitude, true );
        }
        return elevation;
    }

    /**
     * This will cause the tree to be pruned so that old data nodes are removed and the tree
     * becomes thinner
     */
    public void pruneTree( )
    {
        long currentTime = System.currentTimeMillis( );
        long pruneTime = currentTime - PRUNE_TIME_IN_MILLS;
        rootNode.pruneNode( pruneTime );
    }

}
