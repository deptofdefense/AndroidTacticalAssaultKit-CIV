package gov.tak.api.engine.map;

import java.util.Map;
import java.util.Iterator;
import java.util.Collection;

import gov.tak.api.engine.map.coords.GeoPoint;
import gov.tak.api.engine.math.PointD;
import gov.tak.api.util.Visitor;

public interface MapRenderer<RenderContextImpl extends RenderContext> extends IMapRendererEnums {
    interface OnCameraChangedListener {
        void onCameraChanged(MapRenderer renderer);
    }

    /**
     * Callback interface providing notification when controls are added or
     * removed on a {@link ILayer}.
     *
     * <P>Client code that is interested in receiving such notifications should
     * use registration and unregistration for state information purposes only;
     * the {@link MapRenderer#visitControl(ILayer, Visitor, Class)} and
     * {@link MapRenderer#visitControls(ILayer, Visitor)} methods should still
     * be used to safely access controls asynchronously.
     *
     * @author Developer
     */
    interface OnControlsChangedListener {
        /**
         * Invoked when a control is registered.
         *
         * @param layer The layer the control is registered against.
         * @param ctrl  The control
         */
        void onControlRegistered(ILayer layer, Object ctrl);
        /**
         * Invoked when a control is unregistered.
         *
         * @param layer The layer the control was previously registered against.
         * @param ctrl  The control
         */
        void onControlUnregistered(ILayer layer, Object ctrl);
    }

    /**
     * Registers the specified control for the specified layer.
     *
     * @param layer A layer
     * @param ctrl  The control
     */
    void registerControl(ILayer layer, Object ctrl);
    /**
     * Unregisters the specified control for the specified layer.
     *
     * @param layer A layer
     * @param ctrl  The control
     */
    void unregisterControl(ILayer layer, Object ctrl);
    /**
     * Invokes the specified visitor on the specified control for the specified
     * layer. The visitor <B>MAY NOT</B> raise an exception during its
     * invocation. If invoked, the visitor's invocation is always completed
     * before this method returns.
     *
     * <P>Client code should only interact with the control during the
     * invocation of the visitor. Caching the reference to the control and
     * attempting to use it outside of the invocation may lead to undefined
     * results.
     *
     * @param layer     The layer
     * @param visitor   The visitor
     * @param ctrlClazz The class that the control derives from
     *
     * @return  <code>true</code> if the control could be found and the visitor
     *          was invoked, <code>false</code> otherwise.
     */
    <T> boolean visitControl(ILayer layer, Visitor<T> visitor, Class<T> ctrlClazz);
    /**
     * Invokes the specified visitor on the controls for the specified layer.
     * The visitor <B>MAY NOT</B> raise an exception during its invocation. If
     * invoked, the visitor's invocation is always completed before this method
     * returns.
     *
     * <P>Client code should only interact with the controls during the
     * invocation of the visitor. Caching the reference to the controls and
     * attempting to use any outside of the invocation may lead to undefined
     * results.
     *
     * @param layer     The layer
     * @param visitor   The visitor
     *
     * @return  <code>true</code> if controls for the layer were available and
     *          the visitor was invoked, <code>false</code> otherwise.
     */
    boolean visitControls(ILayer layer, Visitor<Iterator<Object>> visitor);
    /**
     * Invokes the specified visitor on the controls across all layers that have
     * had controls registered. The visitor <B>MAY NOT</B> raise an exception
     * during its invocation. If invoked, the visitor's invocation is always
     * completed before this method returns.
     *
     * <P>Client code should only interact with the controls during the
     * invocation of the visitor. Caching the reference to the controls and
     * attempting to use any outside of the invocation may lead to undefined
     * results.
     *
     * @param visitor   The visitor
     */
    void visitControls(Visitor<Iterator<Map.Entry<ILayer, Collection<Object>>>> visitor);

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

    void addOnControlsChangedListener(OnControlsChangedListener l);
    void removeOnControlsChangedListener(OnControlsChangedListener l);
    /**
     * Returns the {@link RenderContext} associated with the
     * <code>MapRenderer</code>.
     *
     * @return
     */
    RenderContextImpl getRenderContext();

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
