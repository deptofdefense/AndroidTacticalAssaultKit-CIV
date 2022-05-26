package com.atakmap.map.opengl;


import com.atakmap.coremap.maps.coords.GeoPoint;

/**
 * Interface for controlling terrain loading for the renderer.
 *  
 * @author Developer
 */
public interface GLTerrain
{
    /**
     * Initiates terrain fetch given the current view. This method should be
     * invoked at the start of the render pump.
     * 
     * @param view  The current view
     */
    void update(GLMapView view);

    /**
     * This will get the current version of the terrain information. This number can be used to
     * tell if new terrain information is available. When the number is the same it is assumed
     * that any elevation requests will always return the same value.
     * @return The current terrain version
     */
    int getTerrainVersion();

    /**
     * Retrieves the elevation value at the specified point.
     * 
     * @param g   The point
     * @param state If non-<code>null</code>, returns the status of the
     *              elevation value returned. Results should be interpreted as
     *              follows
     *              <UL>
     *                  <LI>{@link GLResolvableMapRenderable.State#RESOLVING} - The derived value (if any) was interpolated, but may be further refined
     *                  <LI>{@link GLResolvableMapRenderable.State#RESOLVED} - The derived value will not be refined further
     *                  <LI>{@link GLResolvableMapRenderable.State#UNRESOLVABLE} - The value cannot be computed for the specified location
     *              </UL>
     * 
     * @return  The elevation value, as meters HAE at the specified location or
     *          <code>Double.NaN</code> if the value is not available. The value
     *          returned by <code>state</code> should be evaluated to determine
     *          how the value should be treated or if this method should be
     *          invoked with the given point again in the future.
     */
    double getElevation( GeoPoint g, GLResolvableMapRenderable.State[] state );

    /**
     * This method will try to lookup the altitude/elevation for the vertex updating the
     * properties when the altitude has been found which includes incrementing the version
     * values
     *
     * @param vertex
     *             The vertex to update the altitude for
     */
    void updateAltitude( GLOffscreenVertex vertex );

    /**
     * This method will try to lookup the altitude/elevation for the vertices in the array
     * starting at index 0 and going up to but excluding the vertex at index amount. Each
     * vertex processed will have its properties updated when the altitude has been found
     * which includes incrementing the version values. A vertex will only be updated if the
     * {@link GLOffscreenVertex#geoVersion} does not equal the {@link GLOffscreenVertex#altVersion}
     * value
     *
     * @param vertices
     *             The array of the vertices to update the altitudes for
     * @param amount
     *             The amount of the vertices to update
     * @return The current terrain version {@link #getTerrainVersion()}
     */
    int updateAltitude( GLOffscreenVertex[] vertices, int amount );

    /**
     * When called this will try to free any memory that is not being used. The goal of this
     * method is to reduce the memory footprint of the class, but it is not guaranteed to
     * free anything when the method is called.
     */
    void tryToFreeUnusedMemory( );
}
