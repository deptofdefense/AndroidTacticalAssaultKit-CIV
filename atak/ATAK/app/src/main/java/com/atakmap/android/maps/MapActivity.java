
package com.atakmap.android.maps;

import com.atakmap.android.hashtags.HashtagMapComponent;
import com.atakmap.android.metrics.activity.MetricFragmentActivity;
import com.atakmap.android.rubbersheet.RubberSheetMapComponent;
import com.atakmap.android.vehicle.VehicleMapComponent;
import com.atakmap.app.BuildConfig;

import android.annotation.SuppressLint;
import android.os.SystemClock;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.atakmap.android.action.MapAction;
import com.atakmap.android.action.MapActionFactory;

import com.atakmap.android.config.ConfigEnvironment;
import com.atakmap.android.maps.assets.MapAssets;

import com.atakmap.android.maps.graphics.GLMapComponent;
import com.atakmap.android.network.ContentResolverURIStreamHandler;
import com.atakmap.android.network.URIStreamHandlerFactory;
import com.atakmap.app.preferences.json.JSONPreferenceControl;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.Layer;

import com.atakmap.map.opengl.GLMapSurface;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Abstract base Activity for applications using the map engine
 * 
 * 
 */
public abstract class MapActivity extends MetricFragmentActivity {

    public static final String TAG = "MapActivity";

    private boolean _isActive;
    private MapAssets _mapAssets;
    private final ConcurrentLinkedQueue<MapComponent> _observers = new ConcurrentLinkedQueue<>();

    private final Object startlock = new Object();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
        } catch (Exception e) {
            //Log.d(TAG, "error restoring activity", e);
        }

        _isActive = false;

        final ContentResolverURIStreamHandler defaultHandler = new ContentResolverURIStreamHandler(
                this.getContentResolver());
        URIStreamHandlerFactory.registerHandler("content", defaultHandler);
        URIStreamHandlerFactory.registerHandler("android.resource",
                defaultHandler);
        URIStreamHandlerFactory.registerHandler("file", defaultHandler);

    }

    public MapAssets getMapAssets() {
        if (_mapAssets == null) {
            _mapAssets = new MapAssets(this);
        }
        return _mapAssets;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        MapView view = getMapView();

        MapComponent[] list = _observers.toArray(new MapComponent[0]);
        for (int i = list.length; i > 0; i--) {
            try {
                list[i - 1].onDestroy(this, view);
                //Log.e("shb", "calling onDestroy for: " + list[i-1].getClass());
            } catch (Exception e) {
                Log.e(TAG, "error calling onDestroy for: " + list[i - 1]);
                Log.e(TAG, "error: ", e);
            }
        }
        _observers.clear();

        // check to see if any layers are still attached to the map and warn on
        // leak

        final MapView.RenderStack[] stacks = new MapView.RenderStack[] {
                MapView.RenderStack.BASEMAP,
                MapView.RenderStack.MAP_LAYERS,
                MapView.RenderStack.MAP_SURFACE_OVERLAYS,
                MapView.RenderStack.POINT_OVERLAYS,
                MapView.RenderStack.RASTER_OVERLAYS,
                MapView.RenderStack.TARGETING,
                MapView.RenderStack.VECTOR_OVERLAYS,
                MapView.RenderStack.WIDGETS,
        };

        // NOTE: this block is always going to warn on the layer, "Map Items".
        // This layer is internal to the MapView and not inserted through the
        // MapComponent framework; the warning may be ignored.
        if (view != null) {
            for (MapView.RenderStack stack : stacks) {
                List<Layer> bin = view.getLayers(stack);
                if (bin.isEmpty())
                    continue;

                for (Layer layer : bin)
                    Log.w(TAG,
                            "Layer "
                                    + layer.getName()
                                    + " was not removed from map during component destruction");
            }

        }
    }

    @Override
    public void onStart() {
        super.onStart();
        synchronized (startlock) {
            _isActive = true;

            MapView view = getMapView();
            for (MapComponent c : _observers) {
                try {
                    c.onStart(this, view);
                    //Log.d("shb", "onStart called at the right time: " + _observers.getClass());
                } catch (Exception e) {
                    Log.e(TAG, "error calling onStart for: " + c);
                }
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        synchronized (startlock) {
            _isActive = false;

            MapView view = getMapView();
            for (MapComponent c : _observers) {
                try {
                    c.onStop(this, view);
                } catch (Exception e) {
                    Log.e(TAG, "error calling onStop for: " + c);
                }
            }
        }
    }

    @Override
    protected void onPause() {
        // XXX: According to my observation, this is part of the MapComponent collection
        // _glMapComponent.onPause(this, getMapView());
        MapView view = getMapView();
        for (MapComponent c : _observers) {
            try {
                c.onPause(this, view);
            } catch (Exception e) {
                Log.e(TAG, "error calling onPause for: " + c);
            }
        }

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // XXX: According to my observation, this is part of the MapComponent collection
        // _glMapComponent.onResume(this, getMapView());
        MapView view = getMapView();
        for (MapComponent c : _observers) {
            try {
                c.onResume(this, view);
            } catch (Exception e) {
                Log.e(TAG, "error calling onResume for: " + c);
            }
        }

    }

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        for (MapComponent c : _observers) {
            try {
                c.onConfigurationChanged(newConfig);
            } catch (Exception e) {
                Log.e(TAG, "error calling onResume for: " + c);
            }
        }

    }

    public boolean isActive() {
        synchronized (startlock) {
            return _isActive;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        try {
            return getMapView() != null
                    && getMapView().onGenericMotionEvent(event)
                    || super.onGenericMotionEvent(event);
        } catch (Exception e) {
            Log.e(TAG, "error", e);
            return false;
        }
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        try {
            return getMapView() != null && getMapView().dispatchKeyEvent(event)
                    || super.dispatchKeyEvent(event);
        } catch (Exception e) {
            Log.e(TAG, "error", e);
            return false;
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();

        MapView mapView = getMapView();
        GLMapSurface surface = mapView.getGLSurface();
        if (surface != null) {
            surface.tryToFreeUnusedMemory();
        }
    }

    /**
     * Add a MapComponent to the mapping engine.
     * 
     * @see MapComponent
     * @param observer the MapComponent
     */
    public synchronized void registerMapComponent(final MapComponent observer) {

        long s = SystemClock.elapsedRealtime();
        if (observer != null) {
            synchronized (startlock) {
                _observers.add(observer);
                observer.onCreate(this, getIntent(), getMapView());
                if (_isActive) {
                    observer.onStart(this, getMapView());
                    //Log.d("shb", "onStart called at the delayed time: " + _observers.getClass());
                }
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG,
                        "initialization of: " + observer.getClass() + " took= "
                                + (SystemClock.elapsedRealtime() - s) + "ms");
            }
        }
    }

    /**
     * Remove a MapComponent from the mapping enging
     *
     * @param observer the map component to remove
     */
    public synchronized void unregisterMapComponent(MapComponent observer) {
        if (!_observers.remove(observer))
            return;

        MapView view = getMapView();
        observer.onPause(this, view);
        observer.onStop(this, view);
        observer.onDestroy(this, view);
    }

    /**
     * Given a class, find the instance of a specific map component.
     * @param clazz the class file to find.
     * @return the instance of the map component.
     */
    public MapComponent getMapComponent(Class<? extends MapComponent> clazz) {
        if (clazz != null) {
            for (MapComponent o : _observers) {
                if (clazz.isInstance(o))
                    return o;
            }
        }
        return null;
    }

    /**
     * Based on the classname string, return the appropriate class.
     * @param className the stringified class anme
     * @return the map component if it exists.
     */
    public MapComponent getMapComponent(String className) {
        if (className != null) {
            for (MapComponent o : _observers) {
                if (className.equals(o.getClass().getName()))
                    return o;
            }
        }
        return null;
    }

    boolean requiredAssetsLoaded = false;

    synchronized protected void loadRequiredAssets() {
        if (!requiredAssetsLoaded) {
            registerMapComponent(new GLMapComponent());
            registerMapComponent(
                    new com.atakmap.android.maps.graphics.widgets.GLWidgetsMapComponent());
            requiredAssetsLoaded = true;
        }
    }

    protected void loadAssets() throws IOException, SAXException {

        if (!requiredAssetsLoaded) {
            Log.e(TAG,
                    "error will occur with rendering layers if the required assets are not loaded ahead of time");
        }

        // location needs to be first
        registerMapComponent(
                new com.atakmap.android.location.LocationMapComponent());

        // common communications next
        registerMapComponent(new com.atakmap.comms.CommsMapComponent());

        // JSON preference file serialization and reading
        JSONPreferenceControl.getInstance().initDefaults(getMapView());

        registerMapComponent(
                new com.atakmap.android.brightness.BrightnessComponent());
        registerMapComponent(
                new com.atakmap.android.data.DataMgmtMapComponent());
        registerMapComponent(
                new com.atakmap.android.importexport.ImportExportMapComponent());
        // UserMapComponent & CotMapComponent create MapGroups used by other components
        registerMapComponent(new com.atakmap.android.user.UserMapComponent());
        registerMapComponent(new com.atakmap.android.cot.CotMapComponent());
        registerMapComponent(new com.atakmap.android.menu.MenuMapComponent());
        registerMapComponent(
                new com.atakmap.android.elev.ElevationMapComponent());
        registerMapComponent(
                new com.atakmap.android.targetbubble.TargetBubbleMapComponent());
        registerMapComponent(
                new com.atakmap.android.munitions.DangerCloseMapComponent());
        registerMapComponent(
                new com.atakmap.android.warning.WarningMapComponent());

        FlavorComponentLoader.loadFires(this);

        registerMapComponent(new com.atakmap.android.video.VideoMapComponent());
        // ChatManagerMapComponent after CotMapComponent
        registerMapComponent(
                new com.atakmap.android.chat.ChatManagerMapComponent());
        registerMapComponent(
                new com.atakmap.android.toolbar.ToolbarMapComponent());
        registerMapComponent(new com.atakmap.android.icons.IconsMapComponent());
        registerMapComponent(new com.atakmap.android.image.ImageMapComponent());
        registerMapComponent(
                new com.atakmap.android.cotdetails.CoTInfoMapComponent());
        registerMapComponent(
                new com.atakmap.android.fires.HostileManagerMapComponent());
        registerMapComponent(
                new com.atakmap.android.offscreenindicators.OffScreenIndicatorsMapComponent());

        // Layer SPI / API components

        registerMapComponent(
                new com.atakmap.android.maps.tilesets.TilesetMapComponent());
        registerMapComponent(
                new com.atakmap.android.gdal.NativeRenderingMapComponent());

        // The LayersMapComponent should be created after all layer SPI components
        // as it will kick off a layer scan.

        registerMapComponent(
                new com.atakmap.android.layers.LayersMapComponent());

        registerMapComponent(new com.atakmap.android.lrf.LRFMapComponent());
        registerMapComponent(new com.atakmap.android.fires.FiresMapComponent());
        registerMapComponent(
                new com.atakmap.android.coordoverlay.CoordOverlayMapComponent());
        registerMapComponent(
                new com.atakmap.android.warning.WarningComponent());
        registerMapComponent(
                new com.atakmap.android.gridlines.GridLinesMapComponent());
        registerMapComponent(
                new com.atakmap.android.jumpbridge.JumpBridgeMapComponent());
        registerMapComponent(
                new com.atakmap.android.elev.ElevationOverlaysMapComponent());
        registerMapComponent(
                new com.atakmap.android.dropdown.DropDownManagerMapComponent());
        registerMapComponent(
                new com.atakmap.android.pairingline.PairingLineMapComponent());
        registerMapComponent(
                new com.atakmap.android.routes.RouteMapComponent());
        registerMapComponent(
                new com.atakmap.android.compassring.CompassRingMapComponent());
        registerMapComponent(
                new com.atakmap.android.track.TrackHistoryComponent());
        registerMapComponent(new com.atakmap.spatial.wkt.WktMapComponent());
        registerMapComponent(
                new com.atakmap.android.viewshed.ViewshedMapComponent());
        registerMapComponent(
                new com.atakmap.android.hierarchy.HierarchyMapComponent());
        registerMapComponent(
                new com.atakmap.android.toolbars.RangeAndBearingMapComponent());
        registerMapComponent(
                new com.atakmap.android.bloodhound.BloodHoundMapComponent());
        registerMapComponent(
                new com.atakmap.android.medline.MedicalLineMapComponent());
        registerMapComponent(
                new com.atakmap.android.drawing.DrawingToolsMapComponent());
        registerMapComponent(
                new com.atakmap.android.mapcompass.CompassArrowMapComponent());
        registerMapComponent(
                new com.atakmap.android.radiolibrary.RadioMapComponent());
        registerMapComponent(
                new com.atakmap.android.missionpackage.MissionPackageMapComponent());
        registerMapComponent(
                new com.atakmap.android.image.quickpic.QuickPicMapComponent());
        registerMapComponent(
                new com.atakmap.android.maps.MapCoreIntentsComponent());
        registerMapComponent(
                new com.atakmap.android.update.ApkUpdateComponent());
        registerMapComponent(
                new com.atakmap.android.geofence.component.GeoFenceComponent());
        registerMapComponent(
                new com.atakmap.android.emergency.EmergencyAlertComponent());
        registerMapComponent(
                new com.atakmap.android.emergency.tool.EmergencyLifecycleListener());
        registerMapComponent(new com.atakmap.android.wfs.WFSMapComponent());
        registerMapComponent(
                new com.atakmap.android.maps.MultiplePairingLineMapComponent());

        FlavorComponentLoader.loadSlant(this);

        // Night Vision Component must be placed after Location
        registerMapComponent(
                new com.atakmap.android.nightvision.NightVisionMapWidgetComponent());
        registerMapComponent(
                new com.atakmap.android.resection.ResectionMapComponent());
        registerMapComponent(
                new com.atakmap.android.metricreport.MetricReportMapComponent());

        // Fire up the state saver last for the internal components.

        registerMapComponent(new com.atakmap.android.statesaver.StateSaver());

        registerMapComponent(
                new com.atakmap.android.gpkg.GeopackageMapComponent());
        registerMapComponent(new com.atakmap.android.model.ModelMapComponent());
        registerMapComponent(new RubberSheetMapComponent());
        registerMapComponent(new HashtagMapComponent());

        // Vehicle shapes and overhead markers
        registerMapComponent(new VehicleMapComponent());

        // Load up all of the external components, when this is complete it will trigger the
        // state saver to unroll all of the map components.

        registerMapComponent(new com.atak.plugins.impl.PluginMapComponent());

        ConfigEnvironment.Builder configBuilder = new ConfigEnvironment.Builder();
        ConfigEnvironment config = configBuilder.setMapAssets(getMapAssets())
                .build();
        loadListeners("map_lngpress", "actions/map_click.xml", config);
        loadListeners("item_click", "actions/item_click.xml", config);

    }

    public void loadListeners(String eventType, String actionUriStr,
            ConfigEnvironment config) throws IOException, SAXException {
        try {
            Uri actionUri = Uri.parse(actionUriStr);
            MapAction action = MapActionFactory.createFromUri(actionUri,
                    config);
            _installMapEventAction(eventType, action);
        } catch (ParserConfigurationException e) {
            Log.e(TAG, "error: ", e);
        }
    }

    private void _installMapEventAction(final String eventType,
            final MapAction mapAction) {
        MapView mapView = getMapView();
        MapEventDispatcher d = mapView.getMapEventDispatcher();
        d.addMapEventListener(eventType,
                new MapEventDispatcher.MapEventDispatchListener() {
                    @Override
                    public void onMapEvent(MapEvent event) {
                        MapView mapView = getMapView();
                        mapAction.performAction(mapView, event.getItem());
                    }
                });
    }

    /**
     * Should return the MapView from the client defined layout
     * 
     * @return the MapView
     */
    public abstract MapView getMapView();

}
