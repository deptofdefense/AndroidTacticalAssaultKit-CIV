
package com.atakmap.map;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.Layers;
import com.atakmap.map.layer.feature.FeatureDataSourceContentFactory;
import com.atakmap.map.layer.feature.ogr.OgrFeatureDataSource;
import com.atakmap.map.layer.raster.gpkg.GeoPackageMosaicDatabase;
import com.atakmap.map.layer.raster.mbtiles.MBTilesMosaicDatabase;
import com.atakmap.map.layer.raster.mbtiles.MOMAPMosaicDatabase;
import com.atakmap.map.layer.raster.mosaic.ATAKMosaicDatabase3;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabaseFactory2;
import com.atakmap.map.layer.raster.nativeimagery.NativeImageryMosaicDatabase2;
import com.atakmap.map.layer.raster.osm.OSMDroidMosaicDatabase;
import com.atakmap.map.opengl.GLMapRenderer;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.map.projection.ECEFProjection;
import com.atakmap.map.projection.EquirectangularMapProjection;
import com.atakmap.map.projection.Projection;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLSLUtil;
import com.atakmap.util.Collections2;
import com.atakmap.util.ConfigOptions;
import com.atakmap.util.ReadWriteLock;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * <H2>Coordinate Systems</H2>
 * 
 * <P>The map is the result of the composition of several different coordinate
 * systems. At a high level, these coordinate systems allow for conversion
 * between Earth coordinates (geodetic latitude and longitude) and pixel
 * coordinates of the {@see android.graphics.View View}. The <I>forward</I>
 * coordinate system stack transforms latitude, longitude into pixel x, y; the
 * <I>inverse</I> coordinate stack transform pixel x, y into latitude,
 * longitude. The map provides corresponding methods,
 * {@link AtakMapView#forward(GeoPoint)} and
 * {@link AtakMapView#inverse(PointF)}, respectively.
 * 
 * <P>For users implementing their own Open GL rendering code, the
 * {@link com.atakmap.map.opengl.GLMapView GLMapView} class provides a set of
 * its own <I>forward</I> and <I>inverse</I> methods that will transform points
 * between OpenGL scene coordinates and latitude, longitude.
 * 
 * <P>Map scale is based on the physical size of the pixels on the device, with
 * a scale of <code>1.0d</code> being achieved when a distance of one inch
 * measured on the map is equal to one inch on the physical screen. The scale is
 * <I>nominal</I> and does not take into account local scale factors based on
 * the projection. Map resolution is the measure of map distance, in meters, per
 * pixel on the screen. For example, a resolution of <code>10</code> means that
 * a line 5 pixels long nominally equates to 50 meters. As with scale, this
 * value is nominal and should not be used for mensuration purposes.
 *  
 * <H2>Map Life-Cycle</H2>
 * 
 * <P>Users should couple several of the {@link android.app.Activity Activity}
 * callbacks with method calls in the {@link AtakMapView}.
 * 
 * <UL>
 *   <LI>{@link android.app.Activity#onPause() Activity.onPause()}
 *    <P>When the <code>Activity</code> is paused, {@link AtakMapView#pause()}
 *       should be invoked.
 *   </LI>
 *   <LI>{@link android.app.Activity#onResume() Activity.onResume()}
 *    <P>When the <code>Activity</code> is resumed, {@link AtakMapView#pause()}
 *       should be invoked.
 *   </LI>
 *   <LI>{@link android.app.Activity#onDestroy() Activity.onDestroy()}
 *    <P>When the <code>Activity</code> is paused, {@link AtakMapView#destroy()}
 *       should be invoked.
 *   </LI>
 * </UL> 
 * 
 * <P>The {@link AtakMapView#destroy()} method should only be invoked when the
 * map is no longer needed. This method will release all associated resources
 * and any subsequent attempt to use the map may result in undefined and
 * undesirable behavior.
 * 
 * <H2>Map Layers</H2>
 * 
 * The map supports programmatic addition, removal and ordering of map layers.
 * The layers framework is completely pluggable, meaning that users may
 * seamlessly integrate their own custom layers at compile time or runtime.
 * A description pluggable framework can be found in the documentation for the
 * interface, {@link com.atakmap.map.layer.Layer Layer}.
 *       
 */
public class AtakMapView extends ViewGroup {

    static {
        EngineLibrary.initialize();
    }

    public enum InverseMode {
        Model,
        RayCast,
    };
    
    public static final int DISPLAY_NO_LABELS = 1;
    public static final int DISPLAY_IGNORE_SHORT_LABELS = (1 << 1);
    public static final int DISPLAY_LIMIT_TEXTURE_UNITS = 0x00000004;
    public static final int DISABLE_TEXTURE_FBO =         0x00000008;

    /**
     * Display density factor relative to 240DPI. For example, 120DPI would have
     * a <code>DENSITY</code> value of 0.5f and 480DPI would have a
     * <code>DENSITY</code> value of 2.0f.
     *
     * @deprecated Use {@link #getDisplayDpi()}<code>*240f</code>
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public static float DENSITY = 1f; // Used by map items to convert their SII-era sizes to scale
                                      // with modern devices.

    private static float unscaledDensity = 1f;
    
    public static interface OnDisplayFlagsChangedListener {
        public void onDisplayFlagsChanged(AtakMapView view);
    }
    
    public static interface OnLayersChangedListener {
        public void onLayerAdded(AtakMapView mapView, Layer layer);
        public void onLayerRemoved(AtakMapView mapView, Layer layer);
        
        /**
         * Notifies the user when the position of the layer has been explicitly
         * changed. This callback will <B>NOT</B> be invoked when a layer's
         * position changes due to the addition or removal of other layers.
         * 
         * @param mapView       The map view
         * @param layer         The layer
         * @param oldPosition   The layer's old position
         * @param newPosition   The layer's new position
         */
        public void onLayerPositionChanged(AtakMapView mapView, Layer layer, int oldPosition, int newPosition);
    }
    
    public static interface OnElevationExaggerationFactorChangedListener {
        public void onTerrainExaggerationFactorChanged(AtakMapView mapView, double factor);
    }

    public static interface OnContinuousScrollEnabledChangedListener {
        public void onContinuousScrollEnabledChanged(AtakMapView mapView, boolean enabled);
    }

    public void addOnDisplayFlagsChangedListener(OnDisplayFlagsChangedListener l) {
        _onDFChanged.add(l);
    }

    public void removeOnDisplayFlagsChangedListener(OnDisplayFlagsChangedListener l) {
        _onDFChanged.remove(l);
    }

    protected void onDisplayFlagsChanged() {
        for (OnDisplayFlagsChangedListener l : _onDFChanged) {
            l.onDisplayFlagsChanged(this);
        }
    }

    public void setDisplayFlags(int flags) {
        if (_displayFlags != flags) {
            _displayFlags = flags;
            onDisplayFlagsChanged();
        }
    }

    private ConcurrentLinkedQueue<OnDisplayFlagsChangedListener> _onDFChanged = new ConcurrentLinkedQueue<OnDisplayFlagsChangedListener>();
    private int _displayFlags;

    public int getDisplayFlags() {
        return _displayFlags;
    }

    /**
     * View bounds resize listener
     *
     */
    public static interface OnMapViewResizedListener {
        /**
         * The MapView's bounds changed
         * 
         * @param view
         */
        public void onMapViewResized(AtakMapView view);
    }

    /**
     */
    public static interface OnMapMovedListener {
        /**
         * @param view
         * @param animate       smooth transition requested
         */
        public void onMapMoved (AtakMapView view,
                                boolean animate);
    }

    /**
     * Callback interface that notifies on map projection changes.
     * 
     * @author Developer
     */
    public static interface OnMapProjectionChangedListener {
        /**
         * Invoked when the map projection changes
         * 
         * @param view  The map whose projection changed
         */
        public void onMapProjectionChanged(AtakMapView view);
    } // OnMapProjectionChangedListener

    /**
     * Action bar toggled listener
     */
    public static interface OnActionBarToggledListener {
        /**
         * The Activity's action bar has been toggled
         *
         * @param showing
         */
        public void onActionBarToggled(boolean showing);
    }

    /**
     * Get the axis-aligned geo boundaries of the map view
     * @return Geo-bounds
     */
    public GeoBounds getBounds() {
        return GeoBounds.createFromPoints(getGeoCorners(),
                isContinuousScrollEnabled());
    }

    /**
     * Get the geodetic coordinates of each corner of the map view in
     * clockwise order starting from north-west
     * @return Geo-points
     */
    public GeoPoint[] getGeoCorners() {
        int w = getWidth(), h = getHeight();
        return new GeoPoint[] {
                inverse(new PointF(0, 0)).get(), // North-west
                inverse(new PointF(w, 0)).get(), // North-east
                inverse(new PointF(w, h)).get(), // South-east
                inverse(new PointF(0, h)).get()  // South-west
        };
    }

    /**
     * Create the view in a given context and attributes
     * 
     * @param context the context
     * @param attrs the attributes
     */
    public AtakMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        ACTION_BAR_HEIGHT = 0;
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);

        final double displayDpi = Math.min(Math.sqrt(displayMetrics.xdpi*displayMetrics.ydpi), displayMetrics.densityDpi);
        this.fullEquitorialExtentPixels = Globe.getFullEquitorialExtentPixels(displayDpi);

        //flagged during scan - can reenable if needed for testing
        //String gdalDataPath = System.getProperty("GDAL_DATA_DIR", context.getFilesDir().getAbsolutePath() + File.separator + "GDAL");

        String gdalDataPath = context.getFilesDir().getAbsolutePath() + File.separator + "GDAL";
        GdalLibrary.init(new File(gdalDataPath));

        double minMapScale = 2.5352504279048383E-9d;
        double maxMapScale = 0.01d;
        if (attrs != null) {
            minMapScale = _getAttributeDouble(attrs, null, "minMapScale", minMapScale);
            maxMapScale = _getAttributeDouble(attrs, null, "maxMapScale", maxMapScale);
        }

        final double pad = 2d;
        final double minMapScale180 = Math.max(displayMetrics.heightPixels+pad, displayMetrics.widthPixels+pad)/fullEquitorialExtentPixels;
        minMapScale = Math.max(minMapScale180, minMapScale);

        this.globe = new Globe(displayMetrics.widthPixels, displayMetrics.heightPixels, displayDpi, minMapScale, maxMapScale);
        if(this.globe.pointer.raw == 0L)
            throw new OutOfMemoryError();
        this.rwlock = this.globe.rwlock;
        Globe.setMaxMapTilt(this.globe.pointer.raw, 89d);
        controller = new AtakMapController(this);

        this.callbackForwarder = new CallbackForwarder(this);
        this.globe.addOnContinuousScrollEnabledChangedListener(this.callbackForwarder);
        this.globe.addOnElevationExaggerationFactorChangedListener(this.callbackForwarder);
        this.globe.addOnLayersChangedListener(this.callbackForwarder);
        this.globe.addOnMapMovedListener(this.callbackForwarder);
        this.globe.addOnMapProjectionChangedListener(this.callbackForwarder);
        this.globe.addOnMapViewResizedListener(this.callbackForwarder);

        unscaledDensity = getResources().getDisplayMetrics().density / 1.5f; // 1.5 is the density of the
                                                                     // SII, which our sizes were
                                                                     // originally based on.

        DENSITY = unscaledDensity;
        // Things don't scale down very well, so let's keep 1 as the minimum.
        if (DENSITY < 1.0f)
            DENSITY = 1.0f;

        GLRenderGlobals.setRelativeScaling(DENSITY);

        preferenceManager = PreferenceManager.getDefaultSharedPreferences(getContext());

        // making use of the preference maanger that existed in the code for the text_size changes.
        // this implementation will require the user to restart ATAK for all pre-existing map items
        // to render at the newly selected font size.    I do not really like where this code lives 
        // and I believe it might work better somewhere else.   
        try { 
            FONT_SIZE = Integer.parseInt(preferenceManager.getString("label_text_size", "14"));
            if (FONT_SIZE < 14) FONT_SIZE = 14;
        } catch (Exception e) { 
            FONT_SIZE = 14;
        }
        preferenceManager.registerOnSharedPreferenceChangeListener(
            new OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
                     if (key.equals("label_text_size")) {
                        try {
                            FONT_SIZE = Integer.parseInt(preferenceManager.getString("label_text_size", "14"));
                            if (FONT_SIZE < 14) FONT_SIZE = 14;
                            _defaultTextFormat = new MapTextFormat(Typeface.DEFAULT, FONT_SIZE);
                        } catch (Exception e) {
                            FONT_SIZE = 14;
                        }
                      }
                }
            });

        
        this.touchHandler = new DefaultMapTouchHandler(this);

        this.registerServiceProviders();
        
        this.initGLSurface();

        this.renderer.setDisplayMode(MapRenderer2.DisplayMode.Flat);

        this.tMapSceneModel = this.renderer.getMapSceneModel(false, MapRenderer2.DisplayOrigin.UpperLeft);
    }
    
    protected void initGLSurface() {
        GLSLUtil.setContext(this.getContext());

        ConfigOptions.setOption("glmapview.tilt-skew-offset", 1.0);
        ConfigOptions.setOption("glmapview.tilt-skew-mult", 2.5);

        this.glSurface = new GLMapSurface(this, new GLMapRenderer());
        this.addView(this.glSurface);

        this.renderer = this.glSurface.getGLMapView();
        this.renderSurface = this.renderer.getRenderContext().getRenderSurface();


        this.addOnDisplayFlagsChangedListener(this.glSurface);

        final GLMapView glMapView = this.glSurface.getGLMapView();
        // start GLMapView to sync with the globe and start receiving events
        glMapView.start();
    }

    protected void registerServiceProviders() {
        // XXX - feature / raster providers registered in in Layers.registerAll?
        
        // FeatureDataSource

        FeatureDataSourceContentFactory.register(new OgrFeatureDataSource());

        // MosaicDatabaseSpi
        MosaicDatabaseFactory2.register(NativeImageryMosaicDatabase2.SPI);
        MosaicDatabaseFactory2.register(GeoPackageMosaicDatabase.SPI);
        MosaicDatabaseFactory2.register(OSMDroidMosaicDatabase.SPI);
        MosaicDatabaseFactory2.register(MBTilesMosaicDatabase.SPI);
        MosaicDatabaseFactory2.register(MOMAPMosaicDatabase.SPI);
        MosaicDatabaseFactory2.register(ATAKMosaicDatabase3.SPI);
        
        Layers.registerAll();
    }

    public final Globe getGlobe() {
        return this.globe;
    }

    /**
     * @deprecated use {@link #updateView(double, double, double, double, double, boolean)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public void updateView (double latitude,
                             double longitude,
                             double scale,
                             double rotation,
                             boolean animate)
      {
        this.updateView(latitude, longitude, scale, rotation, getMapTilt(), animate);
      }

    /**
     * Call to update the view provided a latitude, longitude, scale, rotation, tilt and a 
     * flag for animation. 
     * @param latitude the latitude in decimal degrees
     * @param longitude the longitude in decimal degrees
     * @param scale a number between getMinMapScale() and getMaxMapScale()
     * @param tilt a number between 0 (nadir) and 89
     * @param animate if the resulting update should animate (fly) to the new location 
     */
    public void updateView (double latitude,
                              double longitude,
                              double scale,
                              double rotation,
                              double tilt,
                              boolean animate) {

        if(this.glSurface != null) {
            final MapRenderer2 renderer = this.glSurface.getGLMapView();
            renderer.lookAt(new GeoPoint(latitude, longitude), Globe.getMapResolution(getDisplayDpi(), scale), rotation, tilt, animate);
            this.tMapSceneModel = renderer.getMapSceneModel(false, MapRenderer2.DisplayOrigin.UpperLeft);
        }
    }

    public double getDisplayDpi() {
        return renderSurface.getDpi();
    }

    /**************************************************************************/
    // LIFECYCLE

    /**
     * Pauses the map rendering engine. It is recommended that this method is
     * invoked when the activity containing the map is paused.
     */
    public void pause() {
        if(this.glSurface != null) {
            // XXX - need to invalidate textures/context
            //this.glSurface.onPause();
            this.glSurface.tryToFreeUnusedMemory( ); // TODO: Remove this call if onPause is called instead
            if(this.getRenderer().isContinuousRenderEnabled())
                this.glSurface.setRenderMode(GLMapSurface.RENDERMODE_WHEN_DIRTY);
        }
    }
    
    /**
     * Resumes the map rendering engine. This method should always be invoked
     * in response to a previous invocation of {@link #pause()}.
     */
    public void resume() {
        if(this.glSurface != null) {
            // XXX - 
            //this.glSurface.onResume();
            if(this.getRenderer().isContinuousRenderEnabled())
                this.glSurface.setRenderMode(GLMapSurface.RENDERMODE_CONTINUOUSLY);
        }
    }
    
    /**
     * Destroys the map and releases any allocated resources. This method should
     * only be invoked when the map is no longer going to be used; subsequent
     * invocation of any method will result in undefined behavior, including
     * exceptions being thrown.
     */
    public void destroy() {
        this.callbackForwarder._onMapViewResized.clear();
        this.callbackForwarder._onMapMoved.clear();
        _onActionBarToggled.clear();
        this.callbackForwarder._onMapProjectionChanged.clear();
        
        this.removeAllLayers();

        if(this.glSurface != null) {
            this.glSurface.onPause();
            this.removeOnDisplayFlagsChangedListener(this.glSurface);
            
            final GLMapView glMapView = this.glSurface.getGLMapView();
            // stop GLMapView to stop receiving further events
            glMapView.stop();

            this.glSurface.dispose();
            this.glSurface = null;
        }

        this.rwlock.acquireWrite();
        try {
            if(this.globe.pointer.raw == 0L)
                return;

            this.globe.removeOnContinuousScrollEnabledChangedListener(this.callbackForwarder);
            this.globe.removeOnElevationExaggerationFactorChangedListener(this.callbackForwarder);
            this.globe.removeOnLayersChangedListener(this.callbackForwarder);
            this.globe.removeOnMapMovedListener(this.callbackForwarder);
            this.globe.removeOnMapProjectionChangedListener(this.callbackForwarder);
            this.globe.removeOnMapViewResizedListener(this.callbackForwarder);

            // destroy the pointer
            this.globe.dispose();
        } finally {
            this.rwlock.releaseWrite();
        }
    }

    /**************************************************************************/
    // LAYERS
    
    /**
     * Adds the specified {@link com.atakmap.map.layer.Layer Layer} to the map.
     * 
     * @param layer The layer to be added
     */
    public void addLayer(Layer layer) {
        this.globe.addLayer(layer);
    }
    
    /**
     * Adds the specified {@link com.atakmap.map.layer.Layer Layer} to the map
     * at the specified index in the layer stack.
     * 
     * @param position  The index
     * @param layer     The layer
     * 
     * @throws IndexOutOfBoundsException    If <code>position</code> is less
     *                                      than <code>0</code> or greater than
     *                                      or equal to
     *                                      {@link AtakMapView#getNumLayers()}.
     */
    public synchronized void addLayer(int position, Layer layer) {
        this.globe.addLayer(position, layer);
    }
    
    /**
     * Removes the specified {@link com.atakmap.map.layer.Layer Layer}.
     * 
     * @param layer The layer to remove.
     */
    public synchronized void removeLayer(Layer layer) {
        this.globe.removeLayer(layer);
    }
    
    /**
     * Removes all {@link com.atakmap.map.layer.Layer Layer} objects from the
     * map.
     */
    public synchronized void removeAllLayers() {
        this.globe.removeAllLayers();
    }
    
    /**
     * Sets the position of the specified
     * {@link com.atakmap.map.layer.Layer Layer} in the layer stack.
     * 
     * @param layer     The layer
     * @param position  The new position for the layer.
     * 
     * @throws IndexOutOfBoundsException    If <code>position</code> is less
     *                                      than <code>0</code> or greater than
     *                                      or equal to
     *                                      {@link AtakMapView#getNumLayers()}.
     */
    public void setLayerPosition(Layer layer, int position) {
        this.globe.setLayerPosition(layer, position);
    }
    
    /**
     * Returns the number of {@link com.atakmap.map.layer.Layer Layer} objects
     * in the layer stack.
     * 
     * @return  The number of layers.
     */
    public int getNumLayers() {
        return this.globe.getNumLayers();
    }
    
    /**
     * Returns the number of {@link com.atakmap.map.layer.Layer Layer} at the
     * specified index in the layer stack.
     * 
     * @param position  The index
     * 
     * @return  The layer at the specified index
     * 
     * @throws IndexOutOfBoundsException    If <code>position</code> is less
     *                                      than <code>0</code> or greater than
     *                                      or equal to
     *                                      {@link AtakMapView#getNumLayers()}.
     */
    public Layer getLayer(int position) {
        return this.globe.getLayer(position);
    }
    
    /**
     * Returns a {@link java.util.List List} of all of the
     * {@link com.atakmap.map.layer.Layer Layer} objects in the layer stack.
     * 
     * @return  A list of the current layers
     */
    public List<Layer> getLayers() {
        return this.globe.getLayers();
    }

    /**
     * Adds the specified {@link OnLayersChangedListener}.
     * 
     * @param l The listener to add
     */
    public synchronized void addOnLayersChangedListener(OnLayersChangedListener l) {
        this.callbackForwarder.layersChangedListeners.add(l);
    }
    
    /**
     * Remove the specified {@link OnLayersChangedListener}.
     * 
     * @param l The listener to remove
     */
    public synchronized void removeOnLayersChangedListener(OnLayersChangedListener l) {
        this.callbackForwarder.layersChangedListeners.remove(l);
    }
    
    protected void dispatchOnLayerAddedNoSync(Layer layer) {
        for(OnLayersChangedListener listener : this.callbackForwarder.layersChangedListeners)
            listener.onLayerAdded(this, layer);
    }
    
    protected void dispatchOnLayerRemovedNoSync(Collection<Layer> layers) {
        for(Layer layer : layers)
            for(OnLayersChangedListener listener : this.callbackForwarder.layersChangedListeners)
                listener.onLayerRemoved(this, layer);
    }
    
    protected void dispatchOnLayerPositionChanged(Layer l, int oldPos, int newPos) {
        for(OnLayersChangedListener listener : this.callbackForwarder.layersChangedListeners)
            listener.onLayerPositionChanged(this, l, oldPos, newPos);
    }
    
    private static double _getAttributeDouble(AttributeSet attrs, String ns, String n,
            double fallback) {
        String v = attrs.getAttributeValue(ns, n);
        double r = fallback;
        try {
            r = Double.parseDouble(v);
        } catch (Exception ex) {
            // nothing
        }
        return r;
    }

    /**
     * Returns the map controller.
     * 
     * @return  The map controller
     */
    public AtakMapController getMapController() {
        return this.controller;
    }
    
    /**
     * Returns the {@link com.atakmap.map.opengl.GLMapSurface GLMapSurface} that
     * is being used to render the map.
     * 
     * <P>This method should not be invoked by most users. All rendering objects
     * will have access to the returned
     * {@link com.atakmap.map.opengl.GLMapSurface GLMapSurface} via the
     * {@link com.atakmap.map.opengl.GLMapView GLMapView} instance provided to
     * the renderable's
     * {@link com.atakmap.map.opengl.GLMapRenderable#draw(GLMapView) draw}
     * method.
     * 
     * @return  the {@link com.atakmap.map.opengl.GLMapSurface GLMapSurface}
     *          that is being used to render the map.
     */
    public GLMapSurface getGLSurface() {
        return this.glSurface;
    }

    public MapRenderer getRenderer() {
        return this.glSurface.getGLMapView();
    }

    /**
     * Get the latitude value at the center of the view
     * 
     * @return [-90, 90]
     */
    public double getLatitude() {
        final GeoPointMetaData gpm = getPoint();
        if(gpm == null)
            return 0d;
        return gpm.get().getLatitude();
    }

    /**
     * Get the longitude value at the center of the view
     * 
     * @return [-180, 180]
     */
    public double getLongitude() {
        final GeoPointMetaData gpm = getPoint();
        if(gpm == null)
            return 0d;
        return gpm.get().getLongitude();
    }

    /**
     * Returns the map center
     * 
     * @return  The map center
     */
    public GeoPointMetaData getPoint() {
        final MapSceneModel scene = tMapSceneModel;
        if(scene.mapProjection.getSpatialReferenceID() == 4326 && scene.camera.elevation == -90d)
            return GeoPointMetaData.wrap(new GeoPoint(scene.camera.target.y, scene.camera.target.x));

        GeoPoint geo = scene.inverse(new PointF(scene.focusx, scene.focusy), null);
        if(geo == null)
            geo = GeoPoint.ZERO_POINT;
        return GeoPointMetaData.wrap(geo);
    }

    /**
     * Get the minimum latitude possible at the center of the view
     * 
     * @return
     */
    public double getMinLatitude() {
        return this.globe.getMinLatitude();
    }

    /**
     * Get the maximum latitude possible at the center of the view
     * 
     * @return
     */
    public double getMaxLatitude() {
        return this.globe.getMaxLatitude();
    }

    /**
     * Get the minimum longitude possible at the center of the view
     * 
     * @return
     */
    public double getMinLongitude() {
        return this.globe.getMinLongitude();
    }

    /**
     * Get the maximum longitude possible at the center of the view
     * 
     * @return
     */
    public double getMaxLongitude() {
        return this.globe.getMaxLongitude();
    }

    /**
     * Returns the current map rotation, in degrees.
     * 
     * @return  The current map rotation, in degrees
     */
    public double getMapRotation() {
        return tMapSceneModel.camera.azimuth;
    }

    public double getMapTilt() {
        return 90d+tMapSceneModel.camera.elevation;
    }

    public void addOnMapViewResizedListener(OnMapViewResizedListener l) {
        this.callbackForwarder._onMapViewResized.add(l);
    }

    public void removeOnMapViewResizedListener(OnMapViewResizedListener l) {
        this.callbackForwarder._onMapViewResized.remove(l);
    }

    public void addOnActionBarToggledListener(OnActionBarToggledListener l) {
        _onActionBarToggled.add(l);
    }

    public void removeOnActionBarToggledListener(OnActionBarToggledListener l) {
        _onActionBarToggled.remove(l);
    }

    /**
     * Handle mouse Scroll event, zoom map in or out
     */
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event != null) && 0 != (event.getSource() & InputDevice.SOURCE_CLASS_POINTER))
        {
            switch (event.getAction()) {
                case MotionEvent.ACTION_SCROLL: {
                    final double mapScale = Globe.getMapScale(getDisplayDpi(), tMapSceneModel.gsd);
                    final double dir = (event.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0.0f) ? 0.5d : 2d;

                    getMapController().zoomTo (mapScale*dir, true);
                    return true;
                }
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        // XXX - need to call super?
        boolean retval = super.onTouchEvent(e);
        // XXX - should do logical OR?
        final MapTouchHandler h = this.touchHandler;
        if(h != null)
            retval |= h.onTouch(this, e);
        return retval;
    }
    
    public void setMapTouchHandler(MapTouchHandler h) {
        this.touchHandler = h;
    }

    /**
     * Get the default MapTextFormat for the system.   This is set using the 
     * FONT_SIZE that is set as the "default".
     * {@link com.atakmap.android.maps.MapTextFormat MapTextFormat} instance
     * for the runtime.
     * 
     * @return  The default text format
     */
    public synchronized static MapTextFormat getDefaultTextFormat() {
        if (_defaultTextFormat == null) {
            _defaultTextFormat = new MapTextFormat(Typeface.DEFAULT, FONT_SIZE);
        }
        return _defaultTextFormat;
    }

    /**
     * Get a MapTextFormat offset in size from the default
     * @param tf is the typeface desired for the textFormat
     * @param offset is the offset from the default text format.
     * @return  A new text format with the size offset from the default in the positive or negative direction.
     */
    public synchronized static MapTextFormat getTextFormat(Typeface tf, int offset) {
        return new MapTextFormat(tf, FONT_SIZE+offset);
    }

    public void addOnMapMovedListener(OnMapMovedListener l) {
        this.callbackForwarder._onMapMoved.add(l);
    }

    public void removeOnMapMovedListener(OnMapMovedListener l) {
        this.callbackForwarder._onMapMoved.remove(l);
    }

    public void addOnMapProjectionChangedListener(OnMapProjectionChangedListener l) {
        this.callbackForwarder._onMapProjectionChanged.add(l);
    }

    public void removeOnMapProjectionChangedListener(OnMapProjectionChangedListener l) {
        this.callbackForwarder._onMapProjectionChanged.remove(l);
    }

    @Override
    protected void onLayout(boolean arg0, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); ++i) {
            final View child = this.getChildAt(i);
            if (child != null)
                child.layout(l, t, r, b);
        }
    }

    protected void onMapViewResized() {
        this.callbackForwarder.onMapViewResized(this.globe);
    }

    protected void onMapMoved() {
        this.callbackForwarder.onMapMoved(this.globe, this.renderer.isAnimating());
    }

    protected void onMapProjectionChanged() {
        this.callbackForwarder.onMapProjectionChanged(this.globe);
    }

    public void onActionBarToggled(int height) {
        ACTION_BAR_HEIGHT = height;
        for (OnActionBarToggledListener l : _onActionBarToggled) {
            l.onActionBarToggled(height != 0);
        }
    }

    public void setDefaultActionBarHeight(int height) {
        DEFAULT_ACTION_BAR_HEIGHT = height;
        onActionBarToggled(height);

        this.rwlock.acquireRead();
        try {
            if(this.globe.pointer.raw == 0L)
                throw new IllegalStateException();
            // Default focus point depends on default AB height, so update now
            float offy = (getDefaultActionBarHeight() / 2f);
            if(renderer.getDisplayOrigin() == MapRenderer2.DisplayOrigin.Lowerleft)
                offy *= -1f;
            renderer.setFocusPointOffset(0, offy);
            tMapSceneModel = renderer.getMapSceneModel(false, MapRenderer2.DisplayOrigin.UpperLeft);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Get current height of action bar. States include:
     *  a) action bar hidden (height = 0)
     *  b) action bar displayed
     *  c) action bar displayed in "large mode"
     *
     * @return
     */
    public int getActionBarHeight(){
        return ACTION_BAR_HEIGHT;
    }

    /**
     * Get default action bar height
     * Typically equal to state b) in comments for getActionBarHeight
     *
     * @return
     */
    public int getDefaultActionBarHeight(){
        return DEFAULT_ACTION_BAR_HEIGHT;
    }

    /**
     * Get the max of current and default action bar height
     * @return
     */
    public int getMaxActionBarHeight() {
        return Math.max(ACTION_BAR_HEIGHT, DEFAULT_ACTION_BAR_HEIGHT);
    }

    /**************************************************************************/

    /*
     * PROJECTIONS AND COORDINATE SPACES The MapView is the aggregation of 4
     * coordinate spaces. The AtakMapView class provides forward and inverse
     * methods for external Objects allowing them to convert between coordinates
     * in the display coordinate space (what is visible on the screen) and the
     * geodetic coordinate space (latitude/longitude). The coordinate spaces are
     * as follows:
     * 
     * 1. Geodetic Coordinate Space. This is the coordinate space for the Earth
     * and is expressed in Latitude and Longitude. Coordinates are in degrees.
     * 2. Projection Coordinate Space. This is the coordinate space of the
     * current projection (e.g. plate carree/equirectangular, web mercator,
     * etc.). The units of this coordinate space are defined by the projection;
     * x should always define the horizontal component and y the vertical
     * component. 3. Global Pixel Coordinate Space. This is the coordinate space
     * formed by scaling the horizontal and vertical axes of the Projection
     * Coordinate Space such that one inch of screen space covers one inch of
     * the earth (i.e. a scale of 1:1). The scale factor is computed along the
     * equator; the point scale factor may not be 1:1 as the latitudinal
     * distance from the equator increases.
     * 4. Display Coordinate Space. This is the current window into the Global
     * Pixel Coordinate Space. This coordinate space takes into account the
     * current scale and rotation of the map and is always relative to the
     * origin of the display (0, 0).
     */

    /**
     * Returns the current projection.
     * 
     * @return The current projection.
     */
    public Projection getProjection() {
        switch(this.renderer.getDisplayMode()) {
            case Flat :
                return EquirectangularMapProjection.INSTANCE;
            case Globe:
                return ECEFProjection.INSTANCE;
            default :
                throw new IllegalStateException();

        }
    }

    /**
     * Sets the current projection.
     * 
     * @param proj The new projection to be used.
     */
    public synchronized boolean setProjection(Projection proj) {
        if(proj == null)
            return false;
        final int srid = proj.getSpatialReferenceID();
        if(srid == 4326) {
            this.renderer.setDisplayMode(MapRenderer2.DisplayMode.Flat);
        } else if(srid == 4978) {
            this.renderer.setDisplayMode(MapRenderer2.DisplayMode.Globe);
        } else {

            return false;
        }
        tMapSceneModel = renderer.getMapSceneModel(false, MapRenderer2.DisplayOrigin.UpperLeft);
        return true;
    }

    /**
     * Returns the current scale of the map.
     * 
     * @return The current scale of the map.
     */
    public double getMapScale() {
        return Globe.getMapScale(renderSurface.getDpi(), tMapSceneModel.gsd);
    }

    /**
     * Returns the minimum allowed scale for the map.
     * 
     * @return  The minimum allowed scale for the map.
     */
    public double getMinMapScale() {
        return this.globe.getMinMapScale();
    }

    /**
     * Returns the maximum allowed scale for the map.
     * 
     * @return  The maximum allowed scale for the map.
     */
    public double getMaxMapScale() {
        return this.globe.getMaxMapScale();
    }

    /**
     * Returns the equitorial pixel resolution, in meters, at the current scale
     * of the map.
     * 
     * @return The number of meters per pixel.
     */
    public double getMapResolution() {
        return tMapSceneModel.gsd;
    }



    /**
     * Returns the equitorial pixel resolution, in meters, at the specified map
     * scale.
     * 
     * @return The number of meters per pixel.
     */
    public double getMapResolution(double mapScale) {
        return Globe.getMapResolution(renderSurface.getDpi(), mapScale);
    }



    /**
     * Converts a map resolution value, in meters per pixel, to map scale.
     * 
     * @param resolution    The resolution
     * 
     * @return  The equivalent map scale
     */
    public double mapResolutionAsMapScale(double resolution) {
        return Globe.getMapScale(this.renderSurface.getDpi(), resolution);
    }


    public synchronized void setElevationExaggerationFactor(double factor) {
        this.renderer.setElevationExaggerationFactor(factor);
    }

    public double getElevationExaggerationFactor() {
        return this.renderer.getElevationExaggerationFactor();
    }

    public synchronized void addOnElevationExaggerationFactorChangedListener(OnElevationExaggerationFactorChangedListener l) {
        this.callbackForwarder.elevationExaggerationFactorListeners.add(l);
    }
    
    public synchronized void removeOnElevationExaggerationFactorChangedListener(OnElevationExaggerationFactorChangedListener l) {
        this.callbackForwarder.elevationExaggerationFactorListeners.remove(l);
    }
    
    public void setContinuousScrollEnabled(boolean enabled) {
        this.globe.setContinuousScrollEnabled(enabled);
    }
    
    public boolean isContinuousScrollEnabled() {
        return this.globe.isContinuousScrollEnabled();
    }
    
    public synchronized void addOnContinuousScrollEnabledChangedListener(OnContinuousScrollEnabledChangedListener l) {
        this.callbackForwarder.continuousScrollEnabledListeners.add(l);
    }
    
    public synchronized void removeOnContinuousScrollEnabledChangedListener(OnContinuousScrollEnabledChangedListener l) {
        this.callbackForwarder.continuousScrollEnabledListeners.remove(l);
    }
    
    /**
     * Computes the corresponding screen coordinate for the specified geodetic
     * coordinate.
     * 
     * @param g A geodetic coordinate
     * @param unwrap Longitudinal unwrap value (0 to ignore)
     * @return The corresponding screen coordinate
     */
    public PointF forward(GeoPoint g, double unwrap) {
        if (isContinuousScrollEnabled() && (unwrap < 0 && g.getLongitude() > 0
                || unwrap > 0 && g.getLongitude() < 0))
            g = new GeoPoint(g.getLatitude(), g.getLongitude() + unwrap);
        return tMapSceneModel.forward(g, (PointF)null);
    }

    public PointF forward(GeoPoint g) {
        return forward(g, 0);
    }

    /**
     * Computes the corresponding screen coordinate for the specified geodetic
     * coordinate.
     * 
     * @param latitude  The latitude
     * @param longitude The longitude
     * @return The corresponding screen coordinate
     */
    public PointF forward(double latitude, double longitude) {
        return this.forward(new GeoPoint(latitude, longitude));
    }

    /**
     * Returns a model of the current scene. This model can be used to obtain
     * the projection and forward/inverse matrices.
     * 
     * @return  A model of the current map scene
     */
    public MapSceneModel getSceneModel() {
        return tMapSceneModel;
    }
    
    /**
     * Computes the corresponding geodetic coordinate for the specified screen
     * coordinate
     * 
     * @param p A screen coordinate
     * @return The corresponding geodetic coordinate
     */
    public GeoPointMetaData inverse(PointF p) {
        return this.inverse(p, InverseMode.Model);
    }
    
    public GeoPointMetaData inverse(PointF p, InverseMode mode) {
        GeoPointMetaData gpm = new GeoPointMetaData();
        inverseImpl(p, mode, gpm);
        return gpm;
    }

    protected MapRenderer2.InverseResult inverseImpl(PointF p, InverseMode mode, GeoPointMetaData gpm) {
        if(this.renderer == null)
            return MapRenderer2.InverseResult.None;

        int hints;
        switch(mode) {
            case RayCast:
                // XXX - legacy only intersected with terrain here
                hints = MapRenderer2.HINT_RAYCAST_IGNORE_SURFACE_MESH;
                break;
            case Model :
                // ignore surface and terrain meshes
                hints = MapRenderer2.HINT_RAYCAST_IGNORE_SURFACE_MESH|MapRenderer2.HINT_RAYCAST_IGNORE_TERRAIN_MESH;
                break;
            default :
                throw new IllegalStateException();
        }

        GeoPoint lla = GeoPoint.createMutable();
        final MapRenderer2.InverseResult result = this.renderer.inverse(
                new PointD(p.x, p.y, 0d),
                lla,
                MapRenderer2.InverseMode.RayCast,
                hints,
                MapRenderer2.DisplayOrigin.UpperLeft);

        if(result != MapRenderer2.InverseResult.None) {
            lla.set(lla.getLatitude(), GeoCalculations.wrapLongitude(lla.getLongitude()), lla.getAltitude(), lla.getAltitudeReference(), lla.getCE(), lla.getLE());
            gpm.set(lla);
        }
        return result;
    }

    public GeoPointMetaData inverse(float x, float y, InverseMode mode) {
        return this.inverse(new PointF(x, y), mode);
    }

    /**
     * Computes the corresponding geodetic coordinate for the specified screen
     * coordinate.   This is a convienence method for calling
     * inverse(float, float, InverseMode.Raycast)
     * 
     * @param x A screen coordinate, x pixel
     * @param y A screen coordinate, y pixel
     * @return The corresponding geodetic coordinate
     */
    public GeoPointMetaData inverse(float x, float y) {
        return this.inverse(new PointF(x, y), InverseMode.RayCast);
    }

    /**
     * <P><B>This method should generally not be invoked, use
     * {@link #inverse(PointF)} instead!</B>
     * 
     * <P>Obtains the inverse transform to transform a point on the screen into
     * a geodetic coordinate, assuming the following parameters about the
     * display, using the current
     * {@link com.atakmap.map.projection.Projection Projection}.
     * 
     * @param xform     Returns the projection
     * @param focusGeo  The focus of the screen, as a geodetic coordinate
     * @param focusX    The focus of the screen, x-pixel coordinate
     * @param focusY    The focus of the screen, y-pixel coordinate
     * @param rotation  The rotation of the screen in degrees
     * @param scale     The scale of the map displayed on the screen
     * 
     * @return The current projection for which the inverse transform is valid
     */
    public synchronized Projection getInverseTransform(Matrix xform,
            GeoPoint focusGeo,
            float focusX, float focusY, double rotation, double scale) {

        final MapSceneModel sm = tMapSceneModel;
        xform.set(sm.inverse);
        return sm.mapProjection;

    }

    /**
     * <P><B>This method should generally not be invoked, use
     * {@link #inverse(PointF)} instead!</B>
     * 
     * <P>Obtains the inverse transform to transform a point on the screen into
     * a geodetic coordinate, assuming the following parameters about the
     * display, using the current
     * {@link com.atakmap.map.projection.Projection Projection}.
     * 
     * @param proj      The projection to be used
     * @param xform     Returns the projection to transform screen coordinates
     *                  into the projected coordinate space
     * @param focusGeo  The focus of the screen, as a geodetic coordinate
     * @param focusX    The focus of the screen, x-pixel coordinate
     * @param focusY    The focus of the screen, y-pixel coordinate
     * @param rotation  The rotation of the screen in degrees
     * @param mapScale  The scale of the map displayed on the screen
     * 
     * @return The current projection for which the inverse transform is valid
     */
    public void getInverseTransform(Projection proj, Matrix xform, GeoPoint focusGeo,
            float focusX, float focusY, double rotation, double tilt, double mapScale) {
        
        MapSceneModel sm = new MapSceneModel(this, proj, focusGeo,
                focusX, focusY, rotation, tilt, mapScale,
                isContinuousScrollEnabled());
        xform.set(sm.inverse);
    }

    /**************************************************************************/
    
    public void setRelativeScaling(float relative) {
        //System.out.println("SET RELATIVE SCALING " + relative);
        float d = unscaledDensity*relative;
        // Things don't scale down very well, so let's keep 1 as the minimum.
        if (d < 1.0f)
            d = 1.0f;

        this.pause();
        try {
            DENSITY = d;
            GLRenderGlobals.setRelativeScaling(d);
            if(this.glSurface != null) {
                this.glSurface.updateDisplayDensity();
            }
        } finally {
            this.resume();
        }
    }

    /**
     * @deprecated place holder pending better API
     */
    @Deprecated
    @DeprecatedApi(since = "4.1")
    public GeoPoint getRenderElevationAdjustedPoint(final GeoPoint point,
            final double elevOffset) {
        if (point == null || this.getMapTilt() == 0d)
            return point;

        final GLMapView glview = this.getGLSurface().getGLMapView();

        // XXX - not really ideal to reach down into the renderer, but the hit
        //       test should be totally deferred to the renderer
        double alt = point.getAltitude();
        if (!point.isAltitudeValid())
            alt = glview
                    .getElevation(point.getLatitude(), point.getLongitude());
        double el = GeoPoint.isAltitudeValid(alt) ? alt : 0d;
        if (!Double.isNaN(elevOffset))
            el += elevOffset;
        return new GeoPoint(
                point.getLatitude(),
                point.getLongitude(),
                (el + GLMapView.elevationOffset)
                        * this.getElevationExaggerationFactor());
    }

    /**
     * @deprecated place holder pending better API
     */
    @Deprecated
    @DeprecatedApi(since = "4.1")
    public GeoPoint getRenderElevationAdjustedPoint(final GeoPoint point) {
        return getRenderElevationAdjustedPoint(point, 0d);
    }

    /**************************************************************************/

    private static MapTextFormat _defaultTextFormat;
    private ConcurrentLinkedQueue<OnActionBarToggledListener> _onActionBarToggled = new ConcurrentLinkedQueue<OnActionBarToggledListener>();
    private final AtakMapController controller;

    protected SharedPreferences preferenceManager;

    private CallbackForwarder callbackForwarder;

    private MapTouchHandler touchHandler;

    final ReadWriteLock rwlock;
    Globe globe;

    /**************************************************************************/

    private static class CallbackForwarder implements
            Globe.OnMapMovedListener,
            Globe.OnLayersChangedListener,
            Globe.OnMapProjectionChangedListener,
            Globe.OnMapViewResizedListener,
            Globe.OnElevationExaggerationFactorChangedListener,
            Globe.OnContinuousScrollEnabledChangedListener {

        final WeakReference<AtakMapView> ownerRef;
        final ConcurrentLinkedQueue<OnMapMovedListener> _onMapMoved = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<OnMapProjectionChangedListener> _onMapProjectionChanged = new ConcurrentLinkedQueue<>();
        final Set<OnElevationExaggerationFactorChangedListener> elevationExaggerationFactorListeners = Collections2.newIdentityHashSet();
        final Set<OnContinuousScrollEnabledChangedListener> continuousScrollEnabledListeners = Collections2.newIdentityHashSet();
        final ConcurrentLinkedQueue<OnMapViewResizedListener> _onMapViewResized = new ConcurrentLinkedQueue<OnMapViewResizedListener>();
        final Set<OnLayersChangedListener> layersChangedListeners = Collections2.newIdentityHashSet();

        CallbackForwarder(AtakMapView owner) {
            this.ownerRef = new WeakReference<>(owner);
        }


        @Override
        public void onMapMoved(Globe ignored, boolean animate) {
            final AtakMapView view = this.ownerRef.get();
            view.tMapSceneModel = view.renderer.getMapSceneModel(false, MapRenderer2.DisplayOrigin.UpperLeft);
            for(OnMapMovedListener l : _onMapMoved)
                l.onMapMoved(view, animate);
        }

        @Override
        public void onLayerAdded(Globe ignored, Layer layer) {
            final AtakMapView view = this.ownerRef.get();
            synchronized(view) {
                for(OnLayersChangedListener l : layersChangedListeners)
                    l.onLayerAdded(view, layer);
            }
        }

        @Override
        public void onLayerRemoved(Globe ignored, Layer layer) {
            final AtakMapView view = this.ownerRef.get();
            synchronized(view) {
                for(OnLayersChangedListener l : layersChangedListeners)
                    l.onLayerRemoved(view, layer);
            }
        }

        @Override
        public void onLayerPositionChanged(Globe ignored, Layer layer, int oldPosition, int newPosition) {
            final AtakMapView view = this.ownerRef.get();
            synchronized(view) {
                for(OnLayersChangedListener l : layersChangedListeners)
                    l.onLayerPositionChanged(view, layer, oldPosition, newPosition);
            }
        }

        @Override
        public void onTerrainExaggerationFactorChanged(Globe ignored, double factor) {
            final AtakMapView view = this.ownerRef.get();
            synchronized(view) {
                for(OnElevationExaggerationFactorChangedListener l : elevationExaggerationFactorListeners)
                    l.onTerrainExaggerationFactorChanged(view, factor);
            }
        }

        @Override
        public void onContinuousScrollEnabledChanged(Globe ignored, boolean enabled) {
            final AtakMapView view = this.ownerRef.get();
            synchronized(view) {
                for(OnContinuousScrollEnabledChangedListener l : continuousScrollEnabledListeners)
                    l.onContinuousScrollEnabledChanged(view, enabled);
            }
        }

        @Override
        public void onMapViewResized(Globe ignored) {
            final AtakMapView view = this.ownerRef.get();
            view.tMapSceneModel = view.renderer.getMapSceneModel(false, MapRenderer2.DisplayOrigin.UpperLeft);
            for (OnMapViewResizedListener l : _onMapViewResized) {
                l.onMapViewResized(view);
            }
        }

        @Override
        public void onMapProjectionChanged(Globe ignored) {
            final AtakMapView view = this.ownerRef.get();
            view.tMapSceneModel = view.renderer.getMapSceneModel(false, MapRenderer2.DisplayOrigin.UpperLeft);
            for (OnMapProjectionChangedListener l : _onMapProjectionChanged)
                l.onMapProjectionChanged(view);
        }
    }

    /**************************************************************************/


    /** The font-size for the construction of the default text format **/
    private static int FONT_SIZE;

    /** The current height of the action bar. 0 when hidden, otherwise equal to DEFAULT_ACTION_BAR_HEIGHT **/
    private static int ACTION_BAR_HEIGHT;

    /** Height of action bar when displayed  */
    private static int DEFAULT_ACTION_BAR_HEIGHT;

    /** the extent, in pixels, of the equator at a scale of 1:1 */
    public final double fullEquitorialExtentPixels;

    private GLMapSurface glSurface;

    MapSceneModel tMapSceneModel;
    MapRenderer2 renderer;
    private RenderSurface renderSurface;
}
