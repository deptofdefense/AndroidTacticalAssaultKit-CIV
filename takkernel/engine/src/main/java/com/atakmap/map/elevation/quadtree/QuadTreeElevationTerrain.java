package com.atakmap.map.elevation.quadtree;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.raster.osm.OSMUtils;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLOffscreenVertex;
import com.atakmap.map.opengl.GLResolvableMapRenderable;
import com.atakmap.map.opengl.GLTerrain;
import com.atakmap.math.MathUtils;

import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;


/**
 * This is the implementation of the {@link GLTerrain} which uses a quad tree for
 * storage and lookup of the elevation data.
 */
public class QuadTreeElevationTerrain implements GLTerrain, Runnable
{
    /**
     * This is the max node zoom level that is allowed
     */
    private final int maxNodeLevel;

    /**
     * This is the queue that is used to store the elevation nodes that need to have the elevations
     * resolved for
     */
    private final PriorityQueue<ElevationNode> queue;

    /**
     * This is used to quickly check which items are currently marked for being resolved
     */
    private final Set<ElevationNode> resolving;

    /**
     * This thread is used to handle he elevation resolution in the {@link #queue}
     */
    private Thread worker;

    /**
     * This stores the level that the view is currently at
     */
    private int viewLevel = 0;

    /**
     * This stores the last render time in milliseconds
     */
    private long lastRenderTime = 0;

    /**
     * This flag tells if during the last frame elevation was rendered
     */
    private boolean lastRenderedElevation = false;

    /**
     * This object is used to help manage the elevation information
     */
    private final ElevationQuadTree elevationQuadTree = new ElevationQuadTree( );

    /**
     * This is used to store the version of the terrain
     */
    private int terrainVersion = 1;

    public QuadTreeElevationTerrain( int maxNodeLevel )
    {
        this.maxNodeLevel = maxNodeLevel;
        this.queue = new PriorityQueue<ElevationNode>( );
        this.resolving = new HashSet<ElevationNode>( );
        this.worker = null;
    }

    @Override
    public void update( GLMapView view )
    {
        boolean renderingElevation = ( view.drawTilt > 0 );
        if( renderingElevation )
        {
            viewLevel = OSMUtils.mapnikTileLevel( view.drawMapResolution );
            lastRenderTime = System.currentTimeMillis( );
        }
        else if( lastRenderedElevation )
        {
            tryToFreeUnusedMemory( );
        }
        lastRenderedElevation = renderingElevation;
    }

    @Override
    public synchronized int getTerrainVersion( )
    {
        return terrainVersion;
    }

    @Override
    public synchronized double getElevation( GeoPoint g, GLResolvableMapRenderable.State[] state )
    {
        if( this.worker == null )
        {
            startWorkerThread( );
        }

        double elevation = Double.NaN;
        double latitude = g.getLatitude( );
        double longitude = g.getLongitude( );
        if( Double.isNaN( latitude) || Double.isNaN( longitude ) )
        {
            return elevation;
        }

        if(longitude < -180d || longitude > 180d) {
            longitude += 180d;
            longitude = (longitude%360d);
            if(longitude < 0d)
                longitude += 360d;
            longitude -= 180d;
        }

        int maxLevel = MathUtils.clamp( viewLevel - 3, 0, maxNodeLevel );
        ElevationNode node = elevationQuadTree.getElevationNode( latitude, longitude, maxLevel );
        if (node != null) 
            node.setLastRequest( lastRenderTime, terrainVersion );
        if(node != null && node.isResolved( ) )
        {
            elevation = node.getElevation( latitude, longitude, true );
            if( Double.isNaN( elevation ) )
            {
                if( state != null )
                {
                    state[0] = GLResolvableMapRenderable.State.UNRESOLVABLE;
                }
                return elevation;
            }
            else if( node.getLevel( ) >= maxLevel )
            {
                if( state != null )
                {
                    state[0] = GLResolvableMapRenderable.State.RESOLVED;
                }
                return elevation;
            }
            else
            {
                node = node.getChildNodeAt( latitude, longitude );
                if (node != null)
                      node.setLastRequest( lastRenderTime, terrainVersion );
            }
        }

        if(node != null && !resolving.contains( node ) )
        {
            queue.add( node );
            resolving.add( node );
            this.notify( );
        }
        if( state != null )
        {
            state[0] = GLResolvableMapRenderable.State.RESOLVING;
        }
        return elevation;
    }

    private void startWorkerThread( )
    {
        worker = new Thread( this );
        worker.setName( "GLTerrain-worker@" + Integer.toString( this.hashCode( ), 16 ) );
        worker.setPriority( Thread.MIN_PRIORITY );
        worker.start( );
    }

    @Override
    public synchronized void updateAltitude( GLOffscreenVertex vertex )
    {
        if( this.worker == null )
        {
            startWorkerThread( );
        }

        double latitude = vertex.geo.getLatitude( );
        double longitude = vertex.geo.getLongitude( );
        int maxLevel = Math.min( viewLevel - 1, maxNodeLevel );
        final ElevationNode node = 
             elevationQuadTree.getElevationNode( latitude, longitude, maxLevel );
        if (node != null) { 
           node.setLastRequest( lastRenderTime, terrainVersion );
           updateAltitude( vertex, node, maxLevel );
        }
    }

    @Override
    public synchronized int updateAltitude( GLOffscreenVertex[] vertices, int amount )
    {
        if( this.worker == null )
        {
            startWorkerThread( );
        }

        int version = terrainVersion;

        GeoPoint geoPoint;
        int maxLevel = Math.min( viewLevel - 1, maxNodeLevel );
        ElevationNode node = null;
        GLOffscreenVertex vertex;
        for( int i = 0; i < amount; i++ )
        {
            vertex = vertices[i];
            if( vertex.geoVersion != vertex.altVersion )
            {
                geoPoint = vertex.geo;
                double latitude = geoPoint.getLatitude( );
                double longitude = geoPoint.getLongitude( );

                if( ( node == null ) || ( !( node.contains( latitude, longitude ) ) ) )
                {
                    node = elevationQuadTree.getElevationNode( latitude, longitude, maxLevel );
                    if (node != null)
                        node.setLastRequest( lastRenderTime, version );
                }

                if (node != null)
                    updateAltitude( vertex, node, maxLevel );
            }
        }

        return version;
    }

    private void updateAltitude( GLOffscreenVertex vertex, ElevationNode node, int maxLevel )
    {
        GLResolvableMapRenderable.State resolveState = GLResolvableMapRenderable.State.RESOLVING;
        ElevationNode nodeToResolve = node;
        double elevation = Double.NaN;
        boolean elevationInvalid = true;
        if( node.isResolved( ) )
        {
            double latitude = vertex.geo.getLatitude( );
            double longitude = vertex.geo.getLongitude( );

            nodeToResolve = null;
            elevation = node.getElevation( latitude, longitude, true );
            elevationInvalid = Double.isNaN( elevation );
            if( elevationInvalid )
            {
                resolveState = GLResolvableMapRenderable.State.UNRESOLVABLE;
            }
            else if( node.getLevel( ) >= maxLevel )
            {
                resolveState = GLResolvableMapRenderable.State.RESOLVED;
            }
            else
            {
                nodeToResolve = node.getChildNodeAt( latitude, longitude );
                if (nodeToResolve != null)
                     nodeToResolve.setLastRequest( lastRenderTime, terrainVersion );
            }
        }

        if( ( nodeToResolve != null ) && ( !resolving.contains( nodeToResolve ) ) )
        {
            queue.add( nodeToResolve );
            resolving.add( nodeToResolve );
            this.notify( );
        }

        if( elevationInvalid )
        {
            vertex.setElevation( Double.NaN );
        }
        else
        {
            vertex.setElevation( elevation );
        }

        if( resolveState != GLResolvableMapRenderable.State.RESOLVING )
        {
            vertex.altVersion = vertex.geoVersion;
        }
    }

    @Override
    public void tryToFreeUnusedMemory( )
    {
        elevationQuadTree.pruneTree( );
        synchronized( this )
        {
            terrainVersion++;
        }
    }

    @Override
    public void run( )
    {
        ElevationNode node;
        long currentTime;
        int version;
        while( true )
        {
            synchronized( this )
            {
                if( this.queue.isEmpty( ) )
                {
                    try
                    {
                        this.wait( );
                    }
                    catch( InterruptedException ignored )
                    {
                    }
                    continue;
                }

                node = this.queue.poll( );
                currentTime = lastRenderTime;
                version = terrainVersion;
            }

            if( node != null )
            {
                // Don't process old nodes since they are probably no longer needed
                long timeDifference = currentTime - node.getLastRequestTime( );
                boolean resolve = ( ( timeDifference < 1000 ) || ( ( version - 1 ) <= node
                            .getLastTerrainVersion( ) ) );
                if( resolve )
                {
                    node.resolveElevations( );
                }

                synchronized( this )
                {
                    resolving.remove( node );
                    if( resolve )
                    {
                        terrainVersion++;
                    }
                }
            }
        }
    }
}
