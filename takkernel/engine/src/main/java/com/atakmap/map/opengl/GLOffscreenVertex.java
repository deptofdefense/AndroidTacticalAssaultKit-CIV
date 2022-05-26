package com.atakmap.map.opengl;


import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.math.PointD;
import com.atakmap.math.Statistics;


/**
 * This is a simple structure used by the {@link GLMapView} to represent a vertex in the offscreen
 * surface for rendering
 *
 */
public class GLOffscreenVertex
{
    /**
     * This is the geo point that defines the location of the vertex on the map
     */
    public final GeoPoint geo;
    public final GeoPoint altitudeAdjustedGeo;

    /**
     * This point is used to store the projection for the geo point
     */
    public final PointD proj;

    /**
     * This value tells the version of the of the geo point which needs to be incremented
     * every time it changes. It is also the base version that the {@link #projVersion} and
     * {@link #altVersion} try to match
     */
    public int geoVersion;

    /**
     * This value tells the version of the projection which helps to detect when it is not
     * in sync with the {@link #geoVersion}
     */
    public int projVersion;

    /**
     * This value tells the version of the altitude which helps to detect when it is not
     * in sync with the {@link #geoVersion}
     */
    public int altVersion;
    
    double elevationOffset;
    
    double elevationScaleFactor;

    /**
     * The raw elevation value for this vertex
     */
    private double elevation;

    /**
     * This flag tells if the elevation value is valid (not NaN)
     */
    private boolean elevationValid;

    private double adjustedElevation;
    private boolean altValid;

    /**
     * This is the constructor for the GLOffscreenVertex
     */
    GLOffscreenVertex( )
    {
        this.geo = GeoPoint.createMutable();
        this.altitudeAdjustedGeo = GeoPoint.createMutable();
        this.proj = new PointD( 0d, 0d, 0d );
        this.altValid = false;
        this.elevation = Double.NaN;
        elevationValid = false;
        this.adjustedElevation = Double.NaN;
        clearVersions( );
    }

    /**
     * This will clear all the version values
     */
    void clearVersions( )
    {
        this.geoVersion = 0;
        this.projVersion = -1;
        this.altVersion = -1;
        this.elevationOffset = 0d;
        this.elevationScaleFactor = 1d;
    }

    /**
     * When called this will use the altitude value stored in the {@link #geo} and then
     * if it is valid it will update the altitude value in the {@link #altitudeAdjustedGeo}
     * after applying the elevation offset and scale value.
     */
    public void adjust()
    {
        if( this.elevationValid ) {
            adjustedElevation = ( elevation+elevationOffset )*elevationScaleFactor;

            // XXY what about AGL
            (this.altitudeAdjustedGeo).set(
                    adjustedElevation );
        } else {
            (this.altitudeAdjustedGeo).set(GeoPoint.UNKNOWN);
        }
    }
    
    public double getElevation()
    {
        return this.elevation;
    }
    
    public double getAdjustedElevation()
    {
        return this.adjustedElevation;
    }
    
    public void setLocation(double latitude, double longitude) {
        (this.geo).set(latitude, longitude);
        (this.geo).set(GeoPoint.UNKNOWN);
        (this.altitudeAdjustedGeo).set(latitude, longitude);
        (this.altitudeAdjustedGeo).set(GeoPoint.UNKNOWN);
        
        this.altValid = false;
        this.elevation = Double.NaN;
        elevationValid = false;
        this.adjustedElevation = Double.NaN;
        this.geoVersion++;
    }

    public void setElevation( double elevation )
    {
        if( this.elevation != elevation )
        {
            elevationValid = !( Double.isNaN( elevation ) );
            if( elevationValid )
            {
                this.elevation = elevation;
                (  this.geo ).set(
                            elevation );
                this.adjust( );
                this.altValid = true;

                this.geoVersion++;
            }
            else
            {
                if( this.altValid )
                {
                    this.geoVersion++;
                }

                (  this.geo ).set(GeoPoint.UNKNOWN);
                (  this.altitudeAdjustedGeo ).set(GeoPoint.UNKNOWN);
                this.elevation = Double.NaN;
                this.adjustedElevation = Double.NaN;
                this.altValid = false;
            }
        }
    }

    /**
     * This will take the array of vertices and compute an offset value by using taking an average
     * of all the vertices with a valid elevation set
     * @param vertices The vertices to compute the elevation offset for
     * @param count The number of vertices in the array to include so going from [0, count)
     * @return The elevation offset that was computed
     */
    public static void computeElevationStatistics(GLOffscreenVertex [] vertices, int offset, int count, Statistics stats) {
        if(vertices.length < count) {
            count = vertices.length;
        }

        for(int i = 0; i < count; i++)
        {
            if(vertices[i].elevationValid)
            {
                stats.observe(vertices[i].elevation);
            }
        }
    }
}
