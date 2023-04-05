
package com.atakmap.android.maps;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.atakmap.android.devtools.DeveloperTools;
import com.atakmap.android.items.GLMapItemsDatabaseRenderer;
import com.atakmap.android.location.LocationMapComponent;
import com.atakmap.android.maps.graphics.GLMapGroup2;
import com.atakmap.android.maps.graphics.GLMapItemFactory;
import com.atakmap.android.maps.graphics.GLRootMapGroupLayer;
import com.atakmap.android.maps.graphics.widgets.GLWidgetsLayer;
import com.atakmap.android.targetbubble.graphics.GLMapTargetBubble;
import com.atakmap.android.widgets.AttributionWidget;
import com.atakmap.android.widgets.LayoutWidget;
import com.atakmap.android.widgets.TextWidget;
import com.atakmap.android.widgets.WidgetsLayer;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.annotations.ModifierApi;
import com.atakmap.app.BuildConfig;
import com.atakmap.app.DeveloperOptions;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.MapControl;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapRenderer2;
import com.atakmap.map.MapTouchHandler;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.MultiLayer;
import com.atakmap.map.layer.ProxyLayer;
import com.atakmap.map.layer.feature.ogr.OgrFeatureDataSource;
import com.atakmap.map.layer.opengl.GLLayerFactory;
import com.atakmap.map.layer.raster.RasterLayer2;
import com.atakmap.map.layer.raster.service.RasterDataAccessControl;
import com.atakmap.map.opengl.GLAntiMeridianHelper;
import com.atakmap.map.projection.ECEFProjection;
import com.atakmap.map.projection.EquirectangularMapProjection;
import com.atakmap.map.projection.Projection;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.util.ConfigOptions;
import com.atakmap.util.Visitor;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The content view for a MapActivity
 * <p>
 * Responsible for dispatched {@link com.atakmap.android.maps.MapEvent}s:
 * <ul>
 * <li>{@link com.atakmap.android.maps.MapEvent#MAP_MOVED} when the position, zoom, or focus
 * changes</li>
 * <li></li>
 * </ul>
 * </p>
 *
 *
 */
public class MapView extends AtakMapView {

    final static String TAG = "MapView";

    public static MapView _mapView;
    private Marker self;
    private OnSharedPreferenceChangeListener prefListener;

    // NOTE: `DeveloperOptions` static initialization _needs_ to be invoked prior to
    // `GLMapView` construct to initialize `ConfigOptions`
    private static double maximumTilt = DeveloperOptions.getDoubleOption(
            "mapengine.atakmapview.maximum-tilt", Double.NaN);

    public static final String GDAL_DIRNAME = "Databases" + File.separatorChar
            + "GDAL";

    static {
        System.setProperty("GDAL_DATA_DIR",
                FileSystemUtils.getItem(GDAL_DIRNAME)
                        .getAbsolutePath());
    }

    public enum RenderStack {
        /* the bottom-most layer in the stack; basemap data */
        BASEMAP,
        /* map layer data */
        MAP_LAYERS,
        /* map surface data */
        MAP_SURFACE_OVERLAYS,
        /* raster overlay data rendered directly on top of the map layers */
        RASTER_OVERLAYS,
        /* vector overlay data */
        VECTOR_OVERLAYS,
        /* point overlay data */
        POINT_OVERLAYS,
        /* */
        TARGETING,
        /* the topmost layer in the stack; never occluded */
        WIDGETS,
    }

    private Projection preferredProjection;

    /**
     * Create the view in a given context and attributes
     *
     * @param context the context
     * @param attrs the attributes
     */
    public MapView(Context context, AttributeSet attrs) {
        super(context, attrs);

        this.autoSelectProjection = (DeveloperOptions.getIntOption(
                "auto-map-projection", 0) == 1);

        final int defaultProjection = DeveloperOptions.getIntOption(
                "default-map-projection", 4326);
        if (defaultProjection != -1) {
            Projection proj = ProjectionFactory
                    .getProjection(defaultProjection);
            if (proj != null)
                this.setProjection(proj);
        }

        this.overlayManager = new MapOverlayManager(this);

        _rootGroup = new RootMapGroup();
        _touchController = new MapTouchController(this);

        _rootGroup.addOnGroupListChangedListener(groupListChangedListener);
        _rootGroup.addOnItemListChangedListener(itemListChangedListener);

        this.renderStack = new LayerBinLayer("Map Layers");
        this.renderStack.addLayerBin(RenderStack.BASEMAP.name());
        this.renderStack.addLayerBin(RenderStack.MAP_LAYERS.name());
        this.renderStack.addLayerBin(RenderStack.RASTER_OVERLAYS.name());
        this.renderStack.addLayerBin(RenderStack.MAP_SURFACE_OVERLAYS.name());
        this.renderStack.addLayerBin(RenderStack.VECTOR_OVERLAYS.name());
        this.renderStack.addLayerBin(RenderStack.POINT_OVERLAYS.name());
        this.renderStack.addLayerBin(RenderStack.TARGETING.name());
        this.renderStack.addLayerBin(RenderStack.WIDGETS.name());

        super.addLayer(this.renderStack);

        // add layers
        this.addLayer(RenderStack.POINT_OVERLAYS, new RootMapGroupLayer(
                _rootGroup));
        final String banner = BuildConfig.DEV_BANNER;
        if (!FileSystemUtils.isEmpty(banner)) {
            final LayoutWidget root = new LayoutWidget();
            final TextWidget text = new TextWidget(banner, new MapTextFormat(
                    Typeface.DEFAULT, +42), false);
            text.setColor(0x77FF0000);
            text.setPoint(this.getWidth() / 2f
                    - text.getTextFormat().measureTextWidth(banner) / 2f,
                    getHeight()
                            - (text.getTextFormat().measureTextHeight(banner)
                                    + 5f));
            root.addWidget(text);
            this.renderStack.addLayerBin("");
            this.renderStack.getBin("").addLayer(
                    new WidgetsLayer("Banner", root));

            this.addOnLayoutChangeListener(new OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top,
                        int right, int bottom, int oldLeft,
                        int oldTop, int oldRight, int oldBottom) {

                    text.setPoint(
                            left + (right - left) / 2f
                                    - text.getTextFormat()
                                            .measureTextWidth(banner) / 2f,
                            getHeight() - (text.getTextFormat()
                                    .measureTextHeight(banner) + 5f));
                }
            });
        }
        this.setMapTouchHandler(new MapTouchHandler() {
            @Override
            public boolean onTouch(AtakMapView view, MotionEvent event) {
                boolean r = false;
                if (MapView.this.getMapTouchController().getInGesture()) { // check if we're gestureing
                    r = MapView.this.getMapTouchController().onTouch(
                            MapView.this, event); // if we are send the event
                    // to the map
                } else {
                    for (View.OnTouchListener l : _onTouchListener) {
                        if (r = l.onTouch(MapView.this, event)) // find the correct listener and send it
                            break;
                    }
                }
                return r;
            }
        });

        getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_ADDED, _rootGroup.getUidIndex());
        getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_REMOVED, _rootGroup.getUidIndex());

        _mapView = this;

        final LayoutWidget attributionWidgetLayout = new LayoutWidget();
        final AttributionWidget attributionWidget = new AttributionWidget(this
                .getGLSurface().getGLMapView());
        attributionWidget.setColor(0xCFFFFFFF);

        attributionWidgetLayout.addWidget(attributionWidget);
        this.addLayer(
                RenderStack.WIDGETS,
                new WidgetsLayer("Content Attribution",
                        attributionWidgetLayout));

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);
        if (prefs.contains("map_display_mode")) {
            final String mapDisplayMode = prefs.getString("map_display_mode",
                    "");
            if (mapDisplayMode.equals("MAP")) {
                this.setProjection(EquirectangularMapProjection.INSTANCE);
            } else if (mapDisplayMode.equals("GLOBE")) {
                this.setProjection(ECEFProjection.INSTANCE);
            }
        }

        // toggle developer tools per preference
        prefListener.onSharedPreferenceChanged(preferenceManager,
                "atakDeveloperTools");
    }

    /**
     * Obtain the MapView.   The obtained reference to the mapView is only
     * guaranteed to be non-null after MapView construction and prior to a
     * call to dispose()
     */
    public static MapView getMapView() {
        return _mapView;
    }

    @Override
    public void destroy() {
        // remove the Map Items layer
        if (_rootGroup != null)
            this.removeLayer(RenderStack.POINT_OVERLAYS, new RootMapGroupLayer(
                    _rootGroup));

        super.destroy();

        if (_onTouchListener != null) {
            _onTouchListener.clear();
        }

        if (_onKeyListener != null) {
            _onKeyListener.clear();
        }
        if (_onGenericMotionListener != null) {
            _onGenericMotionListener.clear();
        }
        if (_touchController != null)
            _touchController.dispose();

        if (_eventDispatcher != null)
            _eventDispatcher.clearListeners();

        this.overlayManager.dispose();
        this.overlayManager = null;

        if (_rootGroup != null) {
            _rootGroup.dispose();
            _rootGroup = null;

            //       post(new Runnable() {
            //           public void run() {
            //                removeAllViews();
            //           }
            //
        }

        if (_extras != null)
            _extras.clear();
        _extras = null;

        preferenceManager
                .unregisterOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    protected void registerServiceProviders() {
        // set up system properties for various service providers based on the
        // preferences
        copyPrefToConfigOptions(this.preferenceManager,
                "pref_overlay_style_outline_color",
                OgrFeatureDataSource.SYS_PROP_DEFAULT_STROKE_COLOR);
        ConfigOptions.setOption(OgrFeatureDataSource.SYS_PROP_DEFAULT_ICON_URI,
                "asset:/icons/reference_point.png");

        super.registerServiceProviders();
    }

    @Override
    protected void initGLSurface() {
        if (IOProviderFactory.exists(FileSystemUtils.getItem("opengl.broken")))
            System.setProperty("USE_GENERIC_EGL_CONFIG", "true");
        super.initGLSurface();

        GLMapItemFactory.registerSpi(GLMapGroup2.DEFAULT_GLMAPITEM_SPI3);

        GLLayerFactory.register(GLRootMapGroupLayer.SPI2);
        GLLayerFactory.register(GLWidgetsLayer.SPI2);
        GLLayerFactory.register(GLMapItemsDatabaseRenderer.SPI);
        GLLayerFactory.register(GLMapTargetBubble.SPI2);

        // apply frame rate limiter preference
        preferenceManager = PreferenceManager
                .getDefaultSharedPreferences(getContext());
        prefListener = new OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sp,
                    String key) {

                if (key == null)
                    return;

                switch (key) {
                    case "pref_overlay_style_outline_color":
                        copyPrefToConfigOptions(preferenceManager,
                                "pref_overlay_style_outline_color",
                                OgrFeatureDataSource.SYS_PROP_DEFAULT_STROKE_COLOR);
                        break;
                    case "atakContinuousRender":
                        getRenderer().setContinuousRenderEnabled(
                                sp.getBoolean(key, false));
                        break;
                    case "atakDeveloperTools":
                        // install the developer tools on non-release builds
                        if (!BuildConfig.BUILD_TYPE
                                .equalsIgnoreCase("release")) {
                            if (sp.getBoolean(key, false)) {
                                if (_devtools == null)
                                    _devtools = new DeveloperTools(
                                            MapView.this);
                                overlayManager.addOverlay(_devtools);
                            } else if (_devtools != null) {
                                overlayManager.removeOverlay(_devtools);
                            }
                        }
                        break;
                    case "frame_limit":
                        final boolean limitFrameRate = (Short
                                .parseShort(sp.getString(key, "0")) != 0);

                        getGLSurface().getRenderer()
                                .setFrameRate(limitFrameRate ? 30.0f : 0.0f);
                        break;
                }
            }
        };
        preferenceManager
                .registerOnSharedPreferenceChangeListener(prefListener);

        prefListener.onSharedPreferenceChanged(preferenceManager,
                "atakContinuousRender");

        prefListener.onSharedPreferenceChanged(preferenceManager,
                "frame_limit");
    }

    /**
     * Returns the current calculated framerate of the app.
     */
    public double getFramerate() {
        return getGLSurface().getRenderer().getFramerate();
    }

    /**
     * Returns a bundle with some basic GPU information.
     */
    public Bundle getGPUInfo() {
        return getGLSurface().getRenderer().getGPUInfo();
    }

    @Override
    public void setRelativeScaling(float s) {
        super.setRelativeScaling(s);
        // XXX -
        MapTextFormat.invalidate();
        invalidateWidgets();
    }

    /**
     * Get the object that controls the map based on touch gestures
     *
     * @return the MapTouchController for the map view.
     */
    public MapTouchController getMapTouchController() {
        return _touchController;
    }

    /**
     * Adds the specified {@link View.OnTouchListener}.
     *
     * @param l The {@link View.OnTouchListener} to add
     */
    public void addOnTouchListener(View.OnTouchListener l) {
        _onTouchListener.add(l);
    }

    /**
     * Adds the specified {@link View.OnTouchListener} at the specified index.
     * The specified listener may intercept touch events prior to all other
     * registered listeners by specifying an index of <code>0</code>.
     *
     * @param index The index
     * @param l     The {@link View.OnTouchListener} to add
     */
    public void addOnTouchListenerAt(int index, View.OnTouchListener l) {
        ConcurrentLinkedQueue<OnTouchListener> newList = new ConcurrentLinkedQueue<>();
        Iterator<OnTouchListener> itr = _onTouchListener.iterator();
        int i = 0;
        do {
            if (i == index) {
                newList.add(l);
            } else {
                if (itr.hasNext())
                    newList.add(itr.next());
            }
            ++i;
        } while (itr.hasNext());

        _onTouchListener = newList;
    }

    /**
     * Removes the specified {@link View.OnTouchListener}.
     *
     * @param l The {@link View.OnTouchListener} to add
     */
    public boolean removeOnTouchListener(View.OnTouchListener l) {
        return _onTouchListener.remove(l);
    }

    /**
     * Adds the specified {@link View.OnKeyListener}
     * @param l The {@link View.OnKeyListener} to add
     */
    public void addOnKeyListener(View.OnKeyListener l) {
        _onKeyListener.add(l);
    }

    /**
     * Removes the specified {@link View.OnKeyListener}
     * @param l The {@link View.OnKeyListener} to remove
     */
    public void removeOnKeyListener(View.OnKeyListener l) {
        _onKeyListener.remove(l);
    }

    /**
     * Adds the specified {@link View.OnGenericMotionListener}
     * @param l The {@link View.OnGenericMotionListener} to add
     */
    public void addOnGenericMotionListener(View.OnGenericMotionListener l) {
        _onGenericMotionListener.add(l);
    }

    /**
     * Removes the specified {@link View.OnGenericMotionListener}
     * @param l The {@link View.OnGenericMotionListener} to remove
     */
    public void removeOnGenericMotionListener(View.OnGenericMotionListener l) {
        _onGenericMotionListener.remove(l);
    }

    @Override
    final public boolean dispatchKeyEvent(KeyEvent event) {

        boolean noSuperDispatch = false;

        if (event != null) {
            for (View.OnKeyListener l : _onKeyListener) {
                try {
                    if (noSuperDispatch = l.onKey(this, event.getKeyCode(),
                            event)) {
                        break;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "protect against bad listener", e);
                }
            }
            return noSuperDispatch || super.dispatchKeyEvent(event);
        }

        return noSuperDispatch;

    }

    @Override
    final public boolean onGenericMotionEvent(MotionEvent event) {

        if (event != null) {
            for (View.OnGenericMotionListener l : _onGenericMotionListener) {
                try {
                    if (l.onGenericMotion(this, event)) {
                        return true;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "protect against bad listener", e);
                }
            }
        }

        return super.onGenericMotionEvent(event);
    }

    @Override
    protected void onMapMoved() {
        super.onMapMoved();
        MapEvent.Builder b = new MapEvent.Builder(MapEvent.MAP_MOVED);
        getMapEventDispatcher().dispatch(b.build());
    }

    public MapEventDispatcher getMapEventDispatcher() {
        return _eventDispatcher;
    }

    public MapOverlayManager getMapOverlayManager() {
        return this.overlayManager;
    }

    /**
     * Get the root MapGroup
     *
     * @return return the root map group
     */
    public RootMapGroup getRootGroup() {
        return _rootGroup;
    }

    /**
     * Return the MapItem if contained on the MapView provided a UID.
     *
     * Although this is not marked deprecated, please use
     *  getRootGroup().deepFindItemWithDataString("uid", uid) or
     *  getRootGroup().deepFindUID(uid)
     */
    @ModifierApi(since = "4.5", target = "4.8", modifiers = {
            "@Nullable", "public"
    })
    public MapItem getMapItem(final String uid) {
        if (uid == null)
            return null;
        return _rootGroup.deepFindUID(uid);
    }

    public void addLayer(RenderStack stack, Layer layer) {
        this.addLayer(stack, -1, layer);
    }

    public void addLayer(RenderStack stack, int position, Layer layer) {
        if (stack != null && this.renderStack.getBin(stack.name()) != null) {
            if (position < 0)
                this.renderStack.getBin(stack.name()).addLayer(layer);
            else
                this.renderStack.getBin(stack.name()).addLayer(position, layer);
        }
    }

    public void removeLayer(RenderStack stack, Layer layer) {
        if (stack != null && this.renderStack.getBin(stack.name()) != null) {
            this.renderStack.getBin(stack.name()).removeLayer(layer);
        }
    }

    public void pushStack(RenderStack stack) {
        this.renderStack.pushBin(stack.name());
    }

    public void popStack(RenderStack stack) {
        this.renderStack.popBin(stack.name());
    }

    public List<Layer> getLayers(RenderStack stack) {
        if (stack != null && this.renderStack.getBin(stack.name()) != null)
            return this.renderStack.getBin(stack.name()).getLayers();
        return new LinkedList<>();
    }

    public RasterLayer2 getRasterLayerAt2(final GeoPoint point) {
        List<RasterLayer2> rasterLayers = new LinkedList<>();
        findLayers(RasterLayer2.class, this.renderStack, rasterLayers);

        final boolean[] success = {
                false
        };
        final Visitor<RasterDataAccessControl> visitor = new Visitor<RasterDataAccessControl>() {
            @Override
            public void visit(RasterDataAccessControl service) {
                success[0] = (service.accessRasterData(point) != null);
            }
        };
        for (RasterLayer2 l : rasterLayers) {
            success[0] = false;
            this.getGLSurface().getGLMapView()
                    .visitControl(l, visitor, RasterDataAccessControl.class);
            if (success[0])
                return l;
        }
        return null;
    }

    private static <T extends Layer> void findLayers(Class<T> clazz,
            Layer layer, List<T> layers) {
        if (clazz.isAssignableFrom(layer.getClass())) {
            layers.add(clazz.cast(layer));
        } else if (layer instanceof MultiLayer) {
            for (Layer child : ((MultiLayer) layer).getLayers())
                findLayers(clazz, child, layers);
        } else if (layer instanceof ProxyLayer) {
            findLayers(clazz, ((ProxyLayer) layer).get(), layers);
        }
    }

    private static <T extends MapControl> boolean findService(
            MapRenderer renderer, Class<T> clazz, Layer layer,
            Visitor<T> visitor) {
        List<RasterLayer2> rasterLayers = new LinkedList<>();
        findLayers(RasterLayer2.class, layer, rasterLayers);
        for (RasterLayer2 l : rasterLayers)
            if (renderer.visitControl(l, visitor, clazz))
                return true;
        return false;
    }

    /**
     * Retreives the device callsign.    This may be different than the callsign stored
     * in shared preferences if the device is making use of the wave relay callsign that
     * is being passed to it via callsign mocking.
     * Callsign mocking is considered to be temporary and when the callsign is no longer
     * being mocked, the device will revert back to the callsign set in shared preferences.
     * If you would like to know the shared preference version of the callsign, please use
     * the key "locationCallsign" to query the saved preference value.
     *
     * @return the current callsign based on a combination of the shared preference and the
     * validitity of the mocking state.
     */
    public String getDeviceCallsign() {
        String callsign;
        String internalCallsign = LocationMapComponent
                .callsignGen(getContext());

        if (!preferenceManager.getBoolean("locationUseWRCallsign", false) ||
                !_mapData.getBoolean("mockLocationCallsignValid", false)) {
            callsign = preferenceManager.getString("locationCallsign",
                    internalCallsign);
            _mapData.putString("deviceCallsign", callsign);
        } else
            callsign = _mapData.getString("deviceCallsign",
                    internalCallsign);
        return callsign;
    }

    /**
     * Returns the device uid which is exactly the same as the self markers uid.
     */
    public static String getDeviceUid() {
        if (_mapView != null && _mapView.self != null)
            return _mapView.self.getUID();
        return "";
    }

    /**
     * Used to set the transient value of the device callsign and does not effect the
     * default user callsign between system restarts.   To change the device callsign
     * please make use of the shared preference key "locationCallsign".
     * @param callsign  the transient state of the device callsign which does not persist.
     */
    public void setDeviceCallsign(final String callsign) {
        _mapData.putString("deviceCallsign", callsign);
    }

    private static void copyPrefToConfigOptions(SharedPreferences prefs,
            String prefsKey, String propsKey) {
        final String v = prefs.getString(prefsKey, null);
        if (v != null)
            ConfigOptions.setOption(propsKey, v);
    }

    /**
     * Set arbitrary data from a {@link com.atakmap.android.maps.MapComponent}. This makes the
     * data available to other {@code MapComponent}s. This is useful for loosely coupling
     * {@code MapComponents}.
     *
     * @param name a unique name for the extra data Object
     * @param extra the extra data Object
     */
    public void setComponentExtra(String name, Object extra) {
        if (_extras != null)
            _extras.put(name, extra);
    }

    /**
     * Get arbitrary data set by a {@link com.atakmap.android.maps.MapComponent}. This allows for
     * dynamic coupling of {@code MapComponent}s.
     *
     * @param name the unique name of the extra Object
     * @return an extra related to the component
     */
    public Object getComponentExtra(String name) {
        return _extras != null ? _extras.get(name) : null;
    }

    /**
     * Obtain data associated with the map that is not defined by
     * any other MapView getters and setters.
     * @return MapData a bundle like structure of name value pairs.
     */
    public MapData getMapData() {
        return _mapData;
    }

    /**
     * Return the state of the map if auto selection of the projection
     * is enabled.
     */
    public boolean isAutoSelectProjection() {
        return this.autoSelectProjection;
    }

    public void setAutoSelectProjection(boolean auto) {
        this.autoSelectProjection = auto;
    }

    /**************************************************************************/

    private RootMapGroup _rootGroup;
    private MapOverlayManager overlayManager;
    private final LayerBinLayer renderStack;
    private HashMap<String, Object> _extras = new HashMap<>();
    private final MapData _mapData = new MapData();
    private boolean autoSelectProjection;

    private final MapTouchController _touchController;
    private final MapEventDispatcher _eventDispatcher = new MapEventDispatcher();
    private final MapGroupItemsChangedEventForwarder _mapGroupItemsChangedEventForwarder = new MapGroupItemsChangedEventForwarder(
            _eventDispatcher);
    private ConcurrentLinkedQueue<OnTouchListener> _onTouchListener = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnKeyListener> _onKeyListener = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnGenericMotionListener> _onGenericMotionListener = new ConcurrentLinkedQueue<>();

    private DeveloperTools _devtools;

    /**************************************************************************/

    // XXX - IMapGroup bridge

    private final MapGroup.OnItemListChangedListener itemListChangedListener = _mapGroupItemsChangedEventForwarder;

    private final MapGroup.OnGroupListChangedListener groupListChangedListener = _mapGroupItemsChangedEventForwarder;

    // XXX - recommend static utility as transition plan

    /**
     * Computes the corresponding geodetic coordinate for the specified screen coordinate with the 
     * addition of looking up the elevation data.
     * @param x the x value for the screen coordinate
     * @param y the y value for the screen coordinate
     * @returns the corresponding geopoint for a given screen location, 
     * null if there is no corresponding point.
     * @deprecated See {@link MapRenderer2#inverse(PointD, GeoPoint, MapRenderer2.InverseMode, int, MapRenderer2.DisplayOrigin)}
     *             and {@link ElevationManager#getElevation(double, double, ElevationManager.QueryParameters, GeoPointMetaData)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = false)
    public GeoPointMetaData inverseWithElevation(final float x, final float y) {
        GeoPointMetaData p = new GeoPointMetaData();
        if (getGLSurface().getGLMapView() == null)
            return p;

        GeoPoint lla = GeoPoint.createMutable();
        final MapRenderer2.InverseResult result = getGLSurface().getGLMapView()
                .inverse(
                        new PointD(x, y, 0d),
                        lla,
                        MapRenderer2.InverseMode.RayCast,
                        0, // don't ignore anything
                        MapRenderer2.DisplayOrigin.UpperLeft);

        if (result == MapRenderer2.InverseResult.None)
            return p;

        // wrap result longitude
        lla.set(lla.getLatitude(),
                GeoCalculations.wrapLongitude(lla.getLongitude()),
                lla.getAltitude(), lla.getAltitudeReference(), lla.getCE(),
                lla.getLE());
        if (result == MapRenderer2.InverseResult.SurfaceMesh) {
            p.set(lla); // interssected with model
            // designate the source from the 3-D model
            p.setAltitudeSource("3DModel");
            return p;
        }

        // terrain or model, query for local elevation
        ElevationManager.getElevation(lla.getLatitude(),
                lla.getLongitude(), null, p);
        return p;
    }

    /**
     * Method called by LocationMapComponent to register the self marker with the rest of the 
     * system.   Once the self marker is initialized, it will not be possible to reinitialize
     * it or set it to another value.  
     * @param m the self marker.
     */
    public void setSelfMarker(final Marker m) {
        synchronized (this) {
            if (self == null)
                self = m;
        }
    }

    /**
     * Obtain the self marker for the system.  
     * @return the self marker for the system.
     */
    public Marker getSelfMarker() {
        return self;
    }

    public GLAntiMeridianHelper getIDLHelper() {
        return getGLSurface().getGLMapView().idlHelper;
    }

    /***
     * Obtains the centerpoint of the MapView with the associated Elevation.
     * @return the center point of the map with the best available DTED elevation for the 
     * altitude/elevation.
     */
    public GeoPointMetaData getPointWithElevation() {
        final GeoPointMetaData center = this.getPoint();
        final double _latitude = center.get().getLatitude();
        final double _longitude = center.get().getLongitude();

        try {
            return ElevationManager.getElevationMetadata(_latitude, _longitude,
                    null);
        } catch (Exception e) {
            // error occurred, just get the point without elevation.
            return getPoint();
        }
    }

    public GeoPointMetaData getCenterPoint() {
        return this.getPoint();
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw,
            final int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != 0 && h != 0)
            this.invalidateWidgets();
    }

    private void invalidateWidgets() {
        LayoutWidget rootWidget = (LayoutWidget) getComponentExtra(
                "rootLayoutWidget");
        if (rootWidget != null)
            rootWidget.orientationChanged();
    }

    public boolean isPortrait() {
        return (((Activity) getContext())
                .getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    /**
     * Run a task only if/when the map activity is active
     * For more info see {@link MapActivity#executeOnActive(Runnable)}
     * @param task Task to run
     */
    public void executeOnActive(Runnable task) {
        ((MapActivity) getContext()).executeOnActive(task);
    }

    /**
     * Post a task only if/when the map activity is active
     * For more info see {@link MapActivity#postOnActive(Runnable)}
     * @param task Task to run if the activity is active (ie onStart has been called).
     */
    public void postOnActive(Runnable task) {
        ((MapActivity) getContext()).postOnActive(task);
    }

    /**************************************************************************/

    /**
     * At any given zoom level, there may be a point in which there is a minimal tilt 
     * level.   Currently will just return 0.
     * @return the minimum tilt level.
     */
    public double getMinMapTilt() {
        return 0d;
    }

    /**
     * At any given zoom level, there may be a point in which there is a minimal tilt 
     * level.   Currently will just return 0.
     * @param  mapScale for which to return the value for the mininum map tilt level.
     * @return the minimum tilt level for the provided mapScale
     */
    public double getMinMapTilt(double mapScale) {
        return this.getMinMapTilt();
    }

    /**
     * Returns the maximum allowed tilt level for the system.
     * @return a double representing the maximum tilt for the system.
     */
    public double getMaxMapTilt() {
        if (Double.isNaN(maximumTilt))
            maximumTilt = com.atakmap.map.MapSceneModel
                    .isPerspectiveCameraEnabled()
                            ? 89d
                            : 75d;
        return maximumTilt;
    }

    /**
     * Returns the maximum allowed tilt level for the system at a specific map scale.
     * @return a double representing the maximum tilt for the current scale value not
     * exceeding the current maximum tilt for the system.
     */
    public double getMaxMapTilt(double mapScale) {
        return this.getMaxMapTilt();
    }

    @Override
    public void updateView(double latitude,
            double longitude,
            double scale,
            double rotation,
            double tilt,
            boolean animate) {

        if (Double.isNaN(tilt)) {
            tilt = 0d;
        } else if (tilt > 0d) {
            final double minTilt = this.getMinMapTilt(this.getMapScale());
            final double maxTilt = this.getMaxMapTilt(this.getMapScale());

            tilt = MathUtils.clamp(tilt, minTilt, maxTilt);
        }

        super.updateView(latitude, longitude, scale, rotation, tilt, animate);
    }

    @Override
    public synchronized boolean setProjection(Projection proj) {
        if ((this.preferredProjection == null)
                || (proj.getSpatialReferenceID() != this.preferredProjection
                        .getSpatialReferenceID())) {
            this.preferredProjection = proj;
            return this.setProjectionImpl(proj);
        } else {
            return false;
        }
    }

    private boolean setProjectionImpl(Projection proj) {
        return super.setProjection(proj);
    }
}
