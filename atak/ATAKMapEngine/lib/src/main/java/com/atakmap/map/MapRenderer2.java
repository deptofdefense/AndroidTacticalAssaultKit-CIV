package com.atakmap.map;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.Layer2;
import com.atakmap.math.PointD;
import com.atakmap.util.Visitor;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * <P>All methods defined by this interface are thread-safe unless otherwise
 * noted.
 *
 * @author Developer
 */
public interface MapRenderer2 extends MapRendererBase {
    enum DisplayMode {
        Flat,
        Globe,
    }

    enum DisplayOrigin {
        UpperLeft,
        Lowerleft,
    }

    enum InverseMode {
        /**
         * Transforms all three components (x,y,z) of the viewspace coordinate
         * into world space, using the applicable transforms. No intersections
         * are performed.
         */
        Transform,
        /**
         * Coonstructs a ray that passes through the specified viewspace
         * coordinate and computes the intersection with the current surface
         * model of the globe, including any terrain or environment meshes.
         */
        RayCast,
    }

    /** If specified, instructs the raycast operation to ignore terrain meshes */
    int HINT_RAYCAST_IGNORE_TERRAIN_MESH = 0x01;
    /** If specified, instructs the raycast operation to ignore surface meshes */
    int HINT_RAYCAST_IGNORE_SURFACE_MESH = 0x02;

    enum InverseResult {
        /** no intersection */
        None,
        /** Transformation only, no intersection */
        Transformed,
        /** Intersect with globe geometry model */
        GeometryModel,
        /** Intersect with surface mesh */
        SurfaceMesh,
        /** Intersect with terrain mesh */
        TerrainMesh,
    }

    interface OnCameraChangedListener {
        void onCameraChanged(MapRenderer2 renderer);
    }

    /**
     * Returns the {@link RenderContext} associated with the
     * <code>MapRenderer</code>.
     *
     * @return
     */
    RenderContext getRenderContext();

    // Camera management

    /**
     * Looks from the specified location at the specified location
     */
    boolean lookAt(GeoPoint from, GeoPoint at, boolean animate);

    /**
     * Looks at the specified location
     *
     * @param at            The location to look at
     * @param resolution    The nominal display resolution at the location
     * @param azimuth       The rotation, degrees from north CW
     * @param tilt          The tilt angle from the surface tangent plane at the location
     */
    boolean lookAt(GeoPoint at, double resolution, double azimuth, double tilt, boolean animate);

    /**
     * Looks from the specified location per the specified angles.
     *
     * @param azimuth   Rotation from north, clockwise in degrees
     * @param elevation Angle in degrees. Zero is horizontal to the tangent
     *                  plane at the from location, greater than zero is
     *                  upward with 90 straight up, less than zero is downward
     *                  with -90 straight down
     */
    boolean lookFrom(GeoPoint from, double azimuth, double elevation, boolean animate);

    boolean isAnimating();

    /**
     * Retrieves the model for the scene.
     *
     * @param instant   If <code>true</code>, returns the current, intra
     *                  animation state. If <code>false</code> returns the
     *                  target state for any animation that is currently
     *                  processing.
     * @param origin    The desired origin representation for the returned
     *                  scene model
     * @return  The scene model
     */
    MapSceneModel getMapSceneModel(boolean instant, DisplayOrigin origin);

    DisplayMode getDisplayMode();
    void setDisplayMode(DisplayMode mode);

    void setFocusPointOffset(float x, float y);
    float getFocusPointOffsetX();
    float getFocusPointOffsetY();

    DisplayOrigin getDisplayOrigin();

    void addOnCameraChangedListener(OnCameraChangedListener l);
    void removeOnCameraChangedListener(OnCameraChangedListener l);

    /**
     * Transforms the specified world coordinate into view space.
     *
     * @param lla       The world location, altitude is considered if valid
     * @param xyz       Returns the view space location on success
     * @param origin    The relative origin for the viewspace
     * @return  <code>true</code> if the coordinate was trasnformed,
     *          <code>false</code>> otherwise
     */
    boolean forward(GeoPoint lla, PointD xyz, DisplayOrigin origin);
    /**
     * Transforms the specified view space coordinate into world space.
     *
     * @param xyz       The view space location; <code>xyz.z</code> is ignored
     *                  if <code>mode</code> is
     *                  {@link InverseMode#RayCast} or
     * @param lla       Returns the world location, per the specified mode
     * @param mode      The inverse mode to be used, refer to
     *                  {@link InverseMode}
     * @param modeHints The bitwise OR of any hints to be applied. See
     *                  {@link #HINT_RAYCAST_IGNORE_SURFACE_MESH}.
     *                  {@link #HINT_RAYCAST_IGNORE_TERRAIN_MESH}
     * @param origin    The relative origin for the viewspace
     * @return  <code>true</code> if the coordinate was trasnformed,
     *          <code>false</code>> otherwise
     */
    InverseResult inverse(PointD xyz, GeoPoint lla, InverseMode mode, int modeHints, DisplayOrigin origin);

    void setElevationExaggerationFactor(double factor);
    double getElevationExaggerationFactor();
}
