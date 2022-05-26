package com.atakmap.map.layer;


import gov.tak.api.annotation.DontObfuscate;

/**
 * Map layer interface. A layer is a graphical overlay on the map that may
 * provide additional interactive capabilities. The layer's content may be
 * spatial in nature (e.g. raster or feature data) or may be fixed relative to
 * the map's pixel coordinate system (e.g. a heads-up display).
 * 
 * <P>The base interface supports <I>name</I> and <I>visibility</I> properties.
 * Subinterfaces should define additional properties and callbacks as necessary.
 * 
 * <H2>Pluggable Layers Framework</H2>
 * 
 * <P>The layer interface represents the <I>model</I> for the layer. It
 * describes the data or content and may provide guidance on how that content is
 * to be rendered. However, rendering of the layer is delegated to a
 * {@link com.atakmap.layer.opengl.GLLayer GLLayer} implementation.
 * 
 * <P>{@link com.atakmap.layer.opengl.GLLayer GLLayer} instances are
 * automatically created when the layer is added to the map. The
 * {@link com.atakmap.layer.opengl.GLLayer GLLayer} is instantiated using
 * a {@link com.atakmap.layer.opengl.GLLayerSpi GLLayerSpi} that has been
 * previously registered with the
 * {@link com.atakmap.layer.opengl.GLLayerFactory GLLayerFactory}. Users
 * may choose to explicitly register their
 * {@link com.atakmap.layer.opengl.GLLayerSpi GLLayerSpi} before or after
 * map creation; applications subclassing
 * {@link com.atakmap.map.AtakMapView AtakMapView} may also register during
 * map initialization via
 * {@link com.atakmap.map.AtakMapView#initGLSurface() AtakMapView.initGLSurface()}.
 * Alternatively, users may choose to register their
 * {@link com.atakmap.layer.opengl.GLLayerSpi GLLayerSpi} using a static
 * initializer in the <code>Layer</code> class itself. The various
 * implementations that are part of the SDK can be registered via the static
 * method,
 * {@link com.atakmap.map.layer.Layers#registerAll() Layers.registerAll()}.
 * 
 * @author Developer
 * 
 * @see com.atakmap.map.layer.opengl.GLLayer
 * @see com.atakmap.map.layer.opengl.GLLayerSpi
 * @see com.atakmap.map.layer.opengl.GLLayerFactory
 * @see com.atakmap.map.layer.Layers#registerAll()
 */
@DontObfuscate
public interface Layer {

    /**
     * Callback interface for layer visibility changes.
     *  
     * @author Developer
     */
    @DontObfuscate
    public static interface OnLayerVisibleChangedListener {
        /**
         * This method is invoked when the layer's visibility has changed.
         * 
         * @param layer The layer whose visibility changed
         */
        public void onLayerVisibleChanged(Layer layer);
    } // OnLayerVisibleChangedListener

    /**
     * Sets the visibility of the layer. Any registered
     * {@link OnLayerVisibleChangedListener} instances should be notified if the
     * visibility of the layer changes as a result of the invocation of this
     * method.
     * 
     * @param visible   <code>true</code> to make the layer visible,
     *                  <code>false</code> to make it invisible.
     */
    public void setVisible(boolean visible);
    
    /**
     * Returns a flag indicating whether or not the layer is currently visible.
     * 
     * @return  <code>true</code> if the layer is visible, <code>false</code>
     *          otherwise.
     */
    public boolean isVisible();
    
    /**
     * Adds the specified {@link OnLayerVisibleChangedListener}.
     * 
     * @param l The listener to add
     */
    public void addOnLayerVisibleChangedListener(OnLayerVisibleChangedListener l);
    
    /**
     * Removes the specified {@link OnLayerVisibleChangedListener}.
     * 
     * @param l The listener to remove
     */
    public void removeOnLayerVisibleChangedListener(OnLayerVisibleChangedListener l);
    
    // XXX - min/max map scale ???
    
    /**
     * Returns the name of the layer.
     * 
     * @return  The name of the layer
     */
    public String getName();
}
