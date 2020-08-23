package com.atakmap.map.elevation.quadtree;


import com.atakmap.coremap.maps.coords.GeoPoint;


/**
 * This object represents a node in the {@link ElevationQuadTree} which can store data for
 * for the elevation based on the area that it covers.
 *
 */
public class ElevationNode implements Comparable<ElevationNode>
{
    /**
     * This is the level that the node exists at
     */
    private final int level;

    /**
     * This stores the min latitude that is contained in this node
     */
    private final double minLatitude;

    /**
     * This stores the max latitude that is contained in this node
     */
    private final double maxLatitude;

    /**
     * This stores the min longitude that is contained in this node
     */
    private final double minLongitude;

    /**
     * This stores the max longitude that is contained in this node
     */
    private final double maxLongitude;

    /**
     * This object is used to store the elevation data for the node
     */
    private final NodeElevationData elevationData;

    /**
     * This flag tells if the data for the node is resolved. This should always be true
     * if {@link #elevationData} is null
     */
    private boolean resolved;

    /**
     * This is the child node that contains information for the smaller longitude and
     * larger latitude values
     */
    private ElevationNode northWestNode = null;

    /**
     * This is the child node that contains information for the larger longitude and
     * larger latitude values
     */
    private ElevationNode northEastNode = null;

    /**
     * This is the child node that contains information for the smaller longitude and
     * smaller latitude values
     */
    private ElevationNode southWestNode = null;

    /**
     * This is the child node that contains information for the larger longitude and
     * smaller latitude values
     */
    private ElevationNode southEastNode = null;

    /**
     * This is a node that is used to help to speed up the lookup process to the lower nodes
     */
    private ElevationNode quickSkip = null;

    /**
     * This stores the last time this node was requested in milliseconds from the epoch
     */
    private long lastRequestTime = 0;

    /**
     * This stores the last version of the terrain this node was requested in
     */
    private int lastTerrainVersion = 0;

    /**
     * This is the constructor for the elevation node
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
    public ElevationNode( int level, double minLatitude, double maxLatitude, double minLongitude,
                double maxLongitude )
    {
        this.level = level;
        boolean dataNode = ( level > 8 );
        resolved = !( dataNode );
        this.minLatitude = minLatitude;
        this.maxLatitude = maxLatitude;
        this.minLongitude = minLongitude;
        this.maxLongitude = maxLongitude;
        if( dataNode )
        {
            elevationData = new NodeElevationData( level, minLatitude, maxLatitude, minLongitude,
                        maxLongitude );
        }
        else
        {
            elevationData = null;
        }
    }

    /**
     * This will check to see if the given point is contained in this node
     *
     * @param point
     *             The point to see if it is contained in this node
     * @return true if it is contained in the node, false otherwise
     */
    public boolean contains( GeoPoint point )
    {
        return contains( point.getLatitude( ), point.getLongitude( ) );
    }

    /**
     * This will check to see if the given point defined by the individual latitude and longitude
     * values are contained in this node
     *
     * @param latitude
     *             The latitude value to check
     * @param longitude
     *             The longitude value to check
     * @return true if it is contained in the node, false otherwise
     */
    public boolean contains( double latitude, double longitude )
    {
        return ( ( minLatitude <= latitude ) && ( latitude <= maxLatitude ) && ( minLongitude
                    <= longitude ) && ( longitude <= maxLongitude ) );
    }

    /**
     * This will find the most resolved node stopping at the max level or right before the
     * unresolved data node has been found
     *
     * @param latitude
     *             The latitude that needs to be contained for the node
     * @param longitude
     *             The longitude that needs to be contained by the node
     * @param maxLevel
     *             The maximum level to go to
     * @return The elevation node or null if this node doesn't contain the location
     */
    public ElevationNode getNodeAt( double latitude, double longitude, int maxLevel )
    {
        ElevationNode node = null;

        if( contains( latitude, longitude ) )
        {
            if( ( quickSkip != null ) && ( quickSkip.contains( latitude, longitude ) ) )
            {
                node = quickSkip.getNodeAt( latitude, longitude, maxLevel );
            }
            else
            {
                quickSkip = null;

                node = this;
                if( level < maxLevel )
                {
                    ElevationNode childNode = getChildNode( latitude, longitude );
                    if( childNode.resolved )
                    {
                        node = childNode.getNodeAt( latitude, longitude, maxLevel );
                        if( childNode.quickSkip != null )
                        {
                            quickSkip = childNode.quickSkip;
                        }
                        else if( !childNode.isDataNode( ) )
                        {
                            quickSkip = childNode;
                        }
                    }
                }
            }
        }
        else
        {
            throw new IllegalStateException();
        }

        return node;
    }

    /**
     * This will get the child node at the given latitude and longitude
     *
     * @param latitude
     *             The latitude of the child node to get
     * @param longitude
     *             The longitude of the child node to get
     * @return The child node that contains the latitude and longitude location or null if the
     * location doesn't exist in this node
     */
    public ElevationNode getChildNodeAt( double latitude, double longitude )
    {
        ElevationNode node = null;

        if( contains( latitude, longitude ) )
        {
            node = getChildNode( latitude, longitude );
        }
        return node;
    }

    private ElevationNode getChildNode( double latitude, double longitude )
    {
        ElevationNode node;

        double midLatitude = minLatitude + ( ( maxLatitude - minLatitude ) / 2 );
        double midLongitude = minLongitude + ( ( maxLongitude - minLongitude ) / 2 );
        if( latitude < midLatitude )
        {
            if( longitude < midLongitude )
            {
                if( southWestNode == null )
                {
                    southWestNode = new ElevationNode( level + 1, minLatitude, midLatitude,
                                minLongitude, midLongitude );
                }
                node = southWestNode;
            }
            else
            {
                if( southEastNode == null )
                {
                    southEastNode = new ElevationNode( level + 1, minLatitude, midLatitude,
                                midLongitude, maxLongitude );
                }
                node = southEastNode;
            }
        }
        else if( longitude < midLongitude )
        {
            if( northWestNode == null )
            {
                northWestNode =
                            new ElevationNode( level + 1, midLatitude, maxLatitude, minLongitude,
                                        midLongitude );
            }
            node = northWestNode;
        }
        else
        {
            if( northEastNode == null )
            {
                northEastNode =
                            new ElevationNode( level + 1, midLatitude, maxLatitude, midLongitude,
                                        maxLongitude );
            }
            node = northEastNode;
        }

        return node;
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
        double elevation = Double.NaN;

        if( ( resolved ) && ( elevationData != null ) )
        {
            elevation = elevationData.getElevation( latitude, longitude, interpolate );
        }

        return elevation;
    }

    /**
     * This tells if the node is designed to hold data inside it
     *
     * @return true if it is a data node
     */
    public boolean isDataNode( )
    {
        return ( elevationData != null );
    }

    /**
     * This flag tells if the node is resolved or not
     *
     * @return true if the node is resolved which is always true for non-data nodes
     */
    public boolean isResolved( )
    {
        return resolved;
    }

    /**
     * This gets the level of the node
     *
     * @return The level of the node
     */
    public int getLevel( )
    {
        return level;
    }

    /**
     * This will resolve the elevation in the node by computing all the values before returning
     */
    public void resolveElevations( )
    {
        if( ( !resolved ) && ( elevationData != null ) )
        {
            elevationData.populateElevation( );
            resolved = true;
        }
    }

    /**
     * This will lookup the last request time for the node taking into account child nodes
     *
     * @return The last time the node was requested in milliseconds since the epoch
     */
    public long getRequestTime( )
    {
        long lastTime = lastRequestTime;
        if( northWestNode != null )
        {
            long childTime = northWestNode.getRequestTime( );
            if( childTime > lastTime )
            {
                lastTime = childTime;
            }
        }
        if( northEastNode != null )
        {
            long childTime = northEastNode.getRequestTime( );
            if( childTime > lastTime )
            {
                lastTime = childTime;
            }
        }
        if( southWestNode != null )
        {
            long childTime = southWestNode.getRequestTime( );
            if( childTime > lastTime )
            {
                lastTime = childTime;
            }
        }
        if( southEastNode != null )
        {
            long childTime = southEastNode.getRequestTime( );
            if( childTime > lastTime )
            {
                lastTime = childTime;
            }
        }
        return lastTime;
    }

    /**
     * This will return the last request time for the node and does not take into account children
     *
     * @return The last time the node was requested in milliseconds since the epoch
     */
    public long getLastRequestTime( )
    {
        return lastRequestTime;
    }

    /**
     * This will lookup the last terrain version that the node was requested under. This does not
     * take into account child nodes
     *
     * @return The last terrain version the node was requested at
     */
    public int getLastTerrainVersion( )
    {
        return lastTerrainVersion;
    }

    /**
     * This will set the last time the node was requested
     *
     * @param lastRequestTime
     *             The last time the node was requested in milliseconds since the epoch
     * @param lastTerrainVersion
     *             The last terrain version the node was requested for
     */
    public void setLastRequest( long lastRequestTime, int lastTerrainVersion )
    {
        this.lastRequestTime = lastRequestTime;
        this.lastTerrainVersion = lastTerrainVersion;
    }

    /**
     * This will prune the node and all children based on the prune time. If the last request
     * time for the node and its children is before the pruneTime the node will be removed
     * from the tree
     *
     * @param pruneTime
     *             The time where only nodes updated after this time should be kept
     * @return true if this node should be removed because it was pruned
     */
    public boolean pruneNode( long pruneTime )
    {
        if( ( northEastNode != null ) && ( northEastNode.pruneNode( pruneTime ) ) )
        {
            northEastNode = null;
        }
        if( ( northWestNode != null ) && ( northWestNode.pruneNode( pruneTime ) ) )
        {
            northWestNode = null;
        }
        if( ( southEastNode != null ) && ( southEastNode.pruneNode( pruneTime ) ) )
        {
            southEastNode = null;
        }
        if( ( southWestNode != null ) && ( southWestNode.pruneNode( pruneTime ) ) )
        {
            southWestNode = null;
        }

        boolean removeThisNode =
                    ( ( northEastNode == null ) && ( northWestNode == null ) && ( southEastNode
                                == null ) && ( southWestNode == null ) );
        if( ( removeThisNode ) && ( lastRequestTime > pruneTime ) )
        {
            removeThisNode = false;
        }

        return removeThisNode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString( )
    {
        StringBuilder builder = new StringBuilder( "[Level: " + level );
        builder.append( ", latitude (" );
        builder.append( minLatitude );
        builder.append( " to " );
        builder.append( maxLatitude );
        builder.append( "), longitude (" );
        builder.append( minLongitude );
        builder.append( " to " );
        builder.append( maxLongitude );
        builder.append( ")]" );
        return builder.toString( );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo( ElevationNode another )
    {
        return level - another.level;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ElevationNode that = (ElevationNode) o;

        if (level != that.level) return false;
        if (Double.compare(that.minLatitude, minLatitude) != 0) return false;
        if (Double.compare(that.maxLatitude, maxLatitude) != 0) return false;
        if (Double.compare(that.minLongitude, minLongitude) != 0) return false;
        if (Double.compare(that.maxLongitude, maxLongitude) != 0) return false;
        if (resolved != that.resolved) return false;
        if (lastRequestTime != that.lastRequestTime) return false;
        if (lastTerrainVersion != that.lastTerrainVersion) return false;
        if (elevationData != null ? !elevationData.equals(that.elevationData) : that.elevationData != null)
            return false;
        if (northWestNode != null ? !northWestNode.equals(that.northWestNode) : that.northWestNode != null)
            return false;
        if (northEastNode != null ? !northEastNode.equals(that.northEastNode) : that.northEastNode != null)
            return false;
        if (southWestNode != null ? !southWestNode.equals(that.southWestNode) : that.southWestNode != null)
            return false;
        if (southEastNode != null ? !southEastNode.equals(that.southEastNode) : that.southEastNode != null)
            return false;
        return quickSkip != null ? quickSkip.equals(that.quickSkip) : that.quickSkip == null;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = level;
        temp = Double.doubleToLongBits(minLatitude);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(maxLatitude);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(minLongitude);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(maxLongitude);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (elevationData != null ? elevationData.hashCode() : 0);
        result = 31 * result + (resolved ? 1 : 0);
        result = 31 * result + (northWestNode != null ? northWestNode.hashCode() : 0);
        result = 31 * result + (northEastNode != null ? northEastNode.hashCode() : 0);
        result = 31 * result + (southWestNode != null ? southWestNode.hashCode() : 0);
        result = 31 * result + (southEastNode != null ? southEastNode.hashCode() : 0);
        result = 31 * result + (quickSkip != null ? quickSkip.hashCode() : 0);
        result = 31 * result + (int) (lastRequestTime ^ (lastRequestTime >>> 32));
        result = 31 * result + lastTerrainVersion;
        return result;
    }
}
