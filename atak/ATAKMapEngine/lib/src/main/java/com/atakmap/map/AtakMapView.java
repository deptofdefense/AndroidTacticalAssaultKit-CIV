
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
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
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
import com.atakmap.map.projection.MapProjectionDisplayModel;
import com.atakmap.map.projection.Projection;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.opengl.GLSLUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.RandomAccess;
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
     */
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
       @deprecated this does not support rotation
    */
    public GeoBounds getBounds() {
        GeoPoint sw = this.inverse(new PointF(0, getHeight())).get();
        GeoPoint ne = this.inverse(new PointF(getWidth(), 0)).get();
        return new GeoBounds(sw, ne);
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
        this.displayDPI = displayMetrics.densityDpi;
        this.displayResolution = ((1.0d / this.displayDPI) * (1.0d / INCHES_PER_METER));

        this.fullEquitorialExtentPixels = (WGS84_EQUITORIAL_CIRCUMFERENCE * INCHES_PER_METER * this.displayDPI);

        //flagged during scan - can reenable if needed for testing
        //String gdalDataPath = System.getProperty("GDAL_DATA_DIR", context.getFilesDir().getAbsolutePath() + File.separator + "GDAL");

        String gdalDataPath = context.getFilesDir().getAbsolutePath() + File.separator + "GDAL";
        GdalLibrary.init(new File(gdalDataPath));

        this.setProjection(ProjectionFactory.getProjection(4326));

        if (attrs != null) {
            this.minMapScale = _getAttributeDouble(attrs, null, "minMapScale", this.minMapScale);
            this.maxMapScale = _getAttributeDouble(attrs, null, "maxMapScale", this.maxMapScale);
        }

        final double minMapScale180 = Math.max(displayMetrics.heightPixels+2d, displayMetrics.widthPixels+2d)/fullEquitorialExtentPixels;
        this.minMapScale = Math.max(minMapScale180, minMapScale);

        this.layersChangedListeners = Collections.newSetFromMap(new IdentityHashMap<OnLayersChangedListener, Boolean>());
        this.layers = new ArrayList<Layer>();

        controller = new AtakMapController(this);

        _latitude = (this.getMinLatitude() + this.getMaxLatitude()) / 2d;
        _longitude = (this.getMinLongitude() + this.getMaxLongitude()) / 2d;
        this.mapScale = this.minMapScale;

        unscaledDensity = getResources().getDisplayMetrics().density / 1.5f; // 1.5 is the density of the
                                                                     // SII, which our sizes were
                                                                     // originally based on.

        DENSITY = unscaledDensity;
        // Things don't scale down very well, so let's keep 1 as the minimum.
        if (DENSITY < 1.0f)
            DENSITY = 1.0f;

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

        tMapSceneModel = new MapSceneModel(this);
    }
    
    protected void initGLSurface() {
        GLSLUtil.setContext(this.getContext());

        this.glSurface = new GLMapSurface(this, new GLMapRenderer());
        this.addView(this.glSurface);
        
        this.addOnDisplayFlagsChangedListener(this.glSurface);

        final GLMapView glMapView = this.glSurface.getGLMapView();
        this.addOnMapMovedListener (glMapView);
        this.addOnMapProjectionChangedListener(glMapView);
        this.addOnLayersChangedListener(glMapView);
        this.addOnElevationExaggerationFactorChangedListener(glMapView);
        this.addOnContinuousScrollEnabledChangedListener(glMapView);
        
        this.getMapController ().addOnFocusPointChangedListener (glMapView);
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

    /** @deprecated use {@link #updateView(double, double, double, double, double, boolean)} */
    public void updateView (double latitude,
                             double longitude,
                             double scale,
                             double rotation,
                             boolean animate)
      {
        this.updateView(latitude, longitude, scale, rotation, _tilt, animate);
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
                              boolean animate)
      {
        _latitude = MathUtils.clamp (Double.isNaN (latitude) ? 0.0 : latitude,
                           getMinLatitude (),
                           Math.min(getMaxLatitude (), 90d-(1d-scale)));
        _longitude = MathUtils.clamp (Double.isNaN (longitude) ? 0.0 : longitude,
                            getMinLongitude (),
                            getMaxLongitude ());
        mapScale = MathUtils.clamp (Double.isNaN (scale) ? 0.0 : scale,
                          getMinMapScale (),
                          getMaxMapScale ());
        if (Double.isNaN (rotation))
          {
            rotation = 0.0;
          }
        else if (rotation < 0.0)
          {
            rotation = 360.0 + rotation % 360.0;
          }
        else if (rotation >= 360.0)
          {
            rotation %= 360.0;
          }
        _rotation = rotation;
        if(Double.isNaN(tilt)) {
            tilt = 0d;
        } else {
            tilt = MathUtils.clamp(tilt, 0d, 89d);
        }

        _tilt = tilt;
        _animate = animate;
        tMapSceneModel = new MapSceneModel(this);
        this.onMapMoved();
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
        _onMapViewResized.clear();
        _onMapMoved.clear();
        _onActionBarToggled.clear();
        _onMapProjectionChanged.clear();
        
        this.removeAllLayers();

        if(this.glSurface != null) {
            this.glSurface.onPause();
            this.removeOnDisplayFlagsChangedListener(this.glSurface);
            
            final GLMapView glMapView = this.glSurface.getGLMapView();

            this.removeOnMapMovedListener (glMapView);
            this.removeOnMapProjectionChangedListener(glMapView);
            this.removeOnElevationExaggerationFactorChangedListener(glMapView);
            this.removeOnContinuousScrollEnabledChangedListener(glMapView);
            this.removeOnLayersChangedListener(glMapView);

            this.glSurface.dispose();
            this.glSurface = null;
        }
    }

    /**************************************************************************/
    // LAYERS
    
    /**
     * Adds the specified {@link com.atakmap.map.layer.Layer Layer} to the map.
     * 
     * @param layer The layer to be added
     */
    public synchronized void addLayer(Layer layer) {
        this.layers.add(layer);
        this.dispatchOnLayerAddedNoSync(layer);
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
        this.layers.add(position, layer);
        this.dispatchOnLayerAddedNoSync(layer);
    }
    
    /**
     * Removes the specified {@link com.atakmap.map.layer.Layer Layer}.
     * 
     * @param layer The layer to remove.
     */
    public synchronized void removeLayer(Layer layer) {
        if(this.layers.remove(layer))
            this.dispatchOnLayerRemovedNoSync(Collections.singleton(layer));
    }
    
    /**
     * Removes all {@link com.atakmap.map.layer.Layer Layer} objects from the
     * map.
     */
    public synchronized void removeAllLayers() {
        LinkedList<Layer> scratch = new LinkedList<Layer>();
        if(this.layers instanceof RandomAccess) {
            scratch.addAll(this.layers);
            this.layers.clear();
        } else {
            Iterator<Layer> iter = this.layers.iterator();
            while(iter.hasNext()) {
                scratch.add(iter.next());
                iter.remove();
            }
        }
        this.dispatchOnLayerRemovedNoSync(scratch);
        scratch.clear();
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
    public synchronized void setLayerPosition(Layer layer, int position) {
        final int oldPos = this.layers.indexOf(layer);
        if(oldPos < 0)
            throw new IllegalArgumentException();
        if(position == oldPos)
            return;

        this.layers.remove(oldPos);
        if(position > oldPos) {
            this.layers.add(position-1, layer);
        } else if(position < oldPos) {
            this.layers.add(position, layer);
        } else {
            throw new IllegalStateException();
        }
        
        this.dispatchOnLayerPositionChanged(layer, oldPos, position);
    }
    
    /**
     * Returns the number of {@link com.atakmap.map.layer.Layer Layer} objects
     * in the layer stack.
     * 
     * @return  The number of layers.
     */
    public synchronized int getNumLayers() {
        return this.layers.size();
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
    public synchronized Layer getLayer(int position) {
        return this.layers.get(position);
    }
    
    /**
     * Returns a {@link java.util.List List} of all of the
     * {@link com.atakmap.map.layer.Layer Layer} objects in the layer stack.
     * 
     * @return  A list of the current layers
     */
    public synchronized List<Layer> getLayers() {
        return new LinkedList<Layer>(this.layers);
    }

    /**
     * Adds the specified {@link OnLayersChangedListener}.
     * 
     * @param l The listener to add
     */
    public synchronized void addOnLayersChangedListener(OnLayersChangedListener l) {
        this.layersChangedListeners.add(l);
    }
    
    /**
     * Remove the specified {@link OnLayersChangedListener}.
     * 
     * @param l The listener to remove
     */
    public synchronized void removeOnLayersChangedListener(OnLayersChangedListener l) {
        this.layersChangedListeners.remove(l);
    }
    
    protected void dispatchOnLayerAddedNoSync(Layer layer) {
        for(OnLayersChangedListener listener : this.layersChangedListeners)
            listener.onLayerAdded(this, layer);
    }
    
    protected void dispatchOnLayerRemovedNoSync(Collection<Layer> layers) {
        for(Layer layer : layers)
            for(OnLayersChangedListener listener : this.layersChangedListeners)
                listener.onLayerRemoved(this, layer);
    }
    
    protected void dispatchOnLayerPositionChanged(Layer l, int oldPos, int newPos) {
        for(OnLayersChangedListener listener : this.layersChangedListeners)
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
        return _latitude;
    }

    /**
     * Get the longitude value at the center of the view
     * 
     * @return [-180, 180]
     */
    public double getLongitude() {
        return _longitude;
    }

    /**
     * Returns the map center
     * 
     * @return  The map center
     */
    public GeoPointMetaData getPoint() {
        return GeoPointMetaData.wrap(new GeoPoint(_latitude, _longitude));
    }

    /**
     * Get the minimum latitude possible at the center of the view
     * 
     * @return
     */
    public double getMinLatitude() {
        return this.projection.getMinLatitude();
    }

    /**
     * Get the maximum latitude possible at the center of the view
     * 
     * @return
     */
    public double getMaxLatitude() {
        return this.projection.getMaxLatitude();
    }

    /**
     * Get the minimum longitude possible at the center of the view
     * 
     * @return
     */
    public double getMinLongitude() {
        return this.projection.getMinLongitude();
    }

    /**
     * Get the maximum longitude possible at the center of the view
     * 
     * @return
     */
    public double getMaxLongitude() {
        return this.projection.getMaxLongitude();
    }

    /**
     * Returns the current map rotation, in degrees.
     * 
     * @return  The current map rotation, in degrees
     */
    public double getMapRotation() {
        return _rotation;
    }

    public double getMapTilt() {
        return _tilt;
    }

    public void addOnMapViewResizedListener(OnMapViewResizedListener l) {
        _onMapViewResized.add(l);
    }

    public void removeOnMapViewResizedListener(OnMapViewResizedListener l) {
        _onMapViewResized.remove(l);
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
                    if (event.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0.0f)
                    {
                        getMapController().zoomTo (this.mapScale / 2, true);
                    }
                    else
                    {
                        getMapController().zoomTo (this.mapScale * 2, true);
                    }
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
        _onMapMoved.add(l);
    }

    public void removeOnMapMovedListener(OnMapMovedListener l) {
        _onMapMoved.remove(l);
    }

    public void addOnMapProjectionChangedListener(OnMapProjectionChangedListener l) {
        _onMapProjectionChanged.add(l);
    }

    public void removeOnMapProjectionChangedListener(OnMapProjectionChangedListener l) {
        _onMapProjectionChanged.remove(l);
    }

    @Override
    protected void onLayout(boolean arg0, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); ++i) {
            final View child = this.getChildAt(i);
            if (child != null)
                child.layout(l, t, r, b);
        }

        this.controller.setDefaultFocusPoint(this.getWidth() / 2,
                this.getHeight() / 2 + (getDefaultActionBarHeight() / 2));
        tMapSceneModel = new MapSceneModel(this);
        onMapViewResized();
    }

    protected void onMapViewResized() {
        for (OnMapViewResizedListener l : _onMapViewResized) {
            l.onMapViewResized(this);
        }
    }

    protected void onMapMoved() {
        for (OnMapMovedListener l : _onMapMoved) {
            l.onMapMoved(this, _animate);
        }
    }

    protected void onMapProjectionChanged() {
        for (OnMapProjectionChangedListener l : _onMapProjectionChanged)
            l.onMapProjectionChanged(this);
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

        // Default focus point depends on default AB height, so update now
        this.controller.setDefaultFocusPoint(this.getWidth() / 2,
                this.getHeight() / 2 + (getDefaultActionBarHeight() / 2));
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
        return this.projection;
    }

    /**
     * Sets the current projection.
     * 
     * @param proj The new projection to be used.
     */
    public synchronized void setProjection(Projection proj) {
        if (this.projection == proj
                || (this.projection != null && this.projection.getSpatialReferenceID() == proj
                        .getSpatialReferenceID()))
            return;

        if(!MapProjectionDisplayModel.isSupported(proj.getSpatialReferenceID())) {
            Log.w("AtakMapView", "SRID " + proj.getSpatialReferenceID() + " not supported for display");
            return;
        }

        this.projection = proj;

        // null check to account for initialization call to setProjection
        if (controller != null)
              tMapSceneModel = new MapSceneModel(this);
        this.onMapProjectionChanged();        
    }

    /**
     * Returns the current scale of the map.
     * 
     * @return The current scale of the map.
     */
    public double getMapScale() {
        return this.mapScale;
    }

    /**
     * Returns the minimum allowed scale for the map.
     * 
     * @return  The minimum allowed scale for the map.
     */
    public double getMinMapScale() {
        return this.minMapScale;
    }

    /**
     * Returns the maximum allowed scale for the map.
     * 
     * @return  The maximum allowed scale for the map.
     */
    public double getMaxMapScale() {
        return this.maxMapScale;
    }

    /**
     * Returns the equitorial pixel resolution, in meters, at the current scale
     * of the map.
     * 
     * @return The number of meters per pixel.
     */
    public double getMapResolution() {
        return this.getMapResolution(this.mapScale);
    }

    /**
     * Returns the equitorial pixel resolution, in meters, at the specified map
     * scale.
     * 
     * @return The number of meters per pixel.
     */
    public double getMapResolution(double mapScale) {
        return (this.displayResolution / mapScale);
    }

    /**
     * Converts a map resolution value, in meters per pixel, to map scale.
     * 
     * @param resolution    The resolution
     * 
     * @return  The equivalent map scale
     */
    public double mapResolutionAsMapScale(double resolution) {
        return (this.displayResolution / resolution);
    }

    public synchronized void setElevationExaggerationFactor(double factor) {
        if(factor == this.elevationExaggerationFactor)
            return;
        
        this.elevationExaggerationFactor = factor;
        for(OnElevationExaggerationFactorChangedListener l : this.elevationExaggerationFactorListeners)
            l.onTerrainExaggerationFactorChanged(this, this.elevationExaggerationFactor);
    }
    
    public synchronized double getElevationExaggerationFactor() {
        return this.elevationExaggerationFactor;
    }
    
    public synchronized void addOnElevationExaggerationFactorChangedListener(OnElevationExaggerationFactorChangedListener l) {
        this.elevationExaggerationFactorListeners.add(l);
    }
    
    public synchronized void removeOnElevationExaggerationFactorChangedListener(OnElevationExaggerationFactorChangedListener l) {
        this.elevationExaggerationFactorListeners.remove(l);
    }
    
    public synchronized void setContinuousScrollEnabled(boolean enabled) {
        if(enabled == this.continuousScrollEnabled)
            return;
        this.continuousScrollEnabled = enabled;
        for(OnContinuousScrollEnabledChangedListener l : this.continuousScrollEnabledListeners)
            l.onContinuousScrollEnabledChanged(this, this.continuousScrollEnabled);
    }
    
    public synchronized boolean isContinuousScrollEnabled() {
        return this.continuousScrollEnabled;
    }
    
    public synchronized void addOnContinuousScrollEnabledChangedListener(OnContinuousScrollEnabledChangedListener l) {
        this.continuousScrollEnabledListeners.add(l);
    }
    
    public synchronized void removeOnContinuousScrollEnabledChangedListener(OnContinuousScrollEnabledChangedListener l) {
        this.continuousScrollEnabledListeners.remove(l);
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
        switch(mode) {
            case Model :
                return inverseGeom(p);
            case RayCast :
                return projectToTerrain(p.x, p.y);
            default :
                throw new IllegalStateException();
        }
    }

    public GeoPointMetaData inverse(float x, float y, InverseMode mode) {
        return this.inverse(new PointF(x, y), mode);
    }

    private GeoPointMetaData inverseGeom(PointF p) {
        GeoPoint retval = GeoPoint.createMutable();
        if(tMapSceneModel.inverse(p, retval) == null)
            return new GeoPointMetaData();

        if(this.continuousScrollEnabled) {
            if(retval.getLongitude() < -180d)
                retval.set(retval.getLatitude(), retval.getLongitude()+360d);
            else if(retval.getLongitude() > 180d)
                retval.set(retval.getLatitude(), retval.getLongitude()-360d);
        }
        return GeoPointMetaData.wrap(retval);
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
     * This method will project the point onto the map returning the intersection
     * with the point which will also include the elevation.
     *
     * @param x
     *             The x screen coordinate to project
     * @param y
     *             The y screen coordinate to project
     * @return The geo coordinates on the map that the screen coordinate was projected to
     */
    private GeoPointMetaData projectToTerrain( final float x, final float y )
    {
        GeoPointMetaData point = null;
        boolean oldLookup = true;
        if( getMapTilt( ) > 0 )
        {
            GLMapView mapView = null;
            GLMapSurface surface = getGLSurface( );
            if( surface != null )
            {
                mapView = surface.getGLMapView( );
            }

            if( mapView != null )
            {
                point = projectToTerrainImpl( x, y, mapView );
                oldLookup = false;
            }
        }
        if( oldLookup )
        {
            point = inverseGeom( new PointF(x, y) );
        }

        return point;
    }

    private GeoPointMetaData projectToTerrainImpl( final float x, final float y, GLMapView mapView ) {
        MapSceneModel sceneModel = getSceneModel( );
        GeoPointMetaData gpm = new GeoPointMetaData();
        GeoPoint hit = mapView.intersectWithTerrain2(sceneModel, x, y);
        if( hit != null )
            return gpm.set(hit);
        else
            return gpm.set(sceneModel.inverse(new PointF(x, y), null));
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
            this.glSurface.updateDisplayDensity();
        } finally {
            this.resume();
        }
    }
    /**************************************************************************/

    private static MapTextFormat _defaultTextFormat;
    private ConcurrentLinkedQueue<OnMapViewResizedListener> _onMapViewResized = new ConcurrentLinkedQueue<OnMapViewResizedListener>();
    private ConcurrentLinkedQueue<OnActionBarToggledListener> _onActionBarToggled = new ConcurrentLinkedQueue<OnActionBarToggledListener>();
    private final AtakMapController controller;

    protected SharedPreferences preferenceManager;

    private double _latitude;
    private double _longitude;
    private double _rotation = 0d;
    private double _tilt = 0d;
    private boolean _animate = false;

    private ConcurrentLinkedQueue<OnMapMovedListener> _onMapMoved = new ConcurrentLinkedQueue<OnMapMovedListener>();
    private ConcurrentLinkedQueue<OnMapProjectionChangedListener> _onMapProjectionChanged = new ConcurrentLinkedQueue<OnMapProjectionChangedListener>();
    private Set<OnElevationExaggerationFactorChangedListener> elevationExaggerationFactorListeners = Collections.newSetFromMap(new IdentityHashMap<OnElevationExaggerationFactorChangedListener, Boolean>());
    private Set<OnContinuousScrollEnabledChangedListener> continuousScrollEnabledListeners = Collections.newSetFromMap(new IdentityHashMap<OnContinuousScrollEnabledChangedListener, Boolean>());
    private MapTouchHandler touchHandler;

    /**************************************************************************/

    private final static double WGS84_EQUITORIAL_RADIUS = 6378137.0d;
    private final static double WGS84_EQUITORIAL_CIRCUMFERENCE = 2.0d * WGS84_EQUITORIAL_RADIUS
            * Math.PI;
    private final static double INCHES_PER_METER = 39.37d;

    /** The dots-per-inch (pixels-per-inch) of the display */
    private double displayDPI;

    /** The font-size for the construction of the default text format **/
    private static int FONT_SIZE;

    /** The current height of the action bar. 0 when hidden, otherwise equal to DEFAULT_ACTION_BAR_HEIGHT **/
    private static int ACTION_BAR_HEIGHT;

    /** Height of action bar when displayed  */
    private static int DEFAULT_ACTION_BAR_HEIGHT;

    /** The resolution of the screen, in meters, at a scale of 1:1 */
    private final double displayResolution;

    /** the extent, in pixels, of the equator at a scale of 1:1 */
    public final double fullEquitorialExtentPixels;
    /** the current scale of the map */
    private double mapScale;

    private double minMapScale = 2.5352504279048383E-9d;
    private double maxMapScale = 0.01d;

    /** the current projection */
    private Projection projection;

    private GLMapSurface glSurface;
    
    private List<Layer> layers;
    private Set<OnLayersChangedListener> layersChangedListeners;


    private MapSceneModel tMapSceneModel;
    private double elevationExaggerationFactor = 1d;
    private boolean continuousScrollEnabled = true;
}
