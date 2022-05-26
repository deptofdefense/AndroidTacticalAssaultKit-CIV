package com.atakmap.map;

import com.atakmap.annotations.IncubatingApi;
import com.atakmap.coremap.maps.coords.GeoPoint;

@IncubatingApi(since="4.3")
public interface MapRenderer3 extends MapRenderer2 {
    // Camera management

    /**
     * Looks from the specified location at the specified location
     */
    boolean lookAt(GeoPoint from, GeoPoint at, CameraCollision collision, boolean animate);

    /**
     * Looks at the specified location
     *
     * @param at            The location to look at
     * @param resolution    The nominal display resolution at the location
     * @param azimuth       The rotation, degrees from north CW
     * @param tilt          The tilt angle from the surface tangent plane at the location
     */
    boolean lookAt(GeoPoint at, double resolution, double azimuth, double tilt, CameraCollision collision, boolean animate);

    /**
     * Looks from the specified location per the specified angles.
     *
     * @param azimuth   Rotation from north, clockwise in degrees
     * @param elevation Angle in degrees. Zero is horizontal to the tangent
     *                  plane at the from location, greater than zero is
     *                  upward with 90 straight up, less than zero is downward
     *                  with -90 straight down
     */
    boolean lookFrom(GeoPoint from, double azimuth, double elevation, CameraCollision collision, boolean animate);

    /**
     * Retrives the specified renderer control. Renderer controls are valid for
     * the lifetime of the associated renderer.
     *
     * <P>Note that renderer controls may also be obtained via the various
     * <code>visitControls(...)</code> methods by specifying a
     * <code>null</code> {@link com.atakmap.map.layer.Layer Layer}.
     *
     * @param controlType   The control type
     *
     * @return  The renderer control, or <code>null</code> if the renderer does
     *          not have a control of the specified type.
     */
    <T> T getControl(Class<T> controlType);
}
