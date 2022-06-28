
package com.atakmap.android.maps;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.atakmap.android.action.MapAction;
import com.atakmap.android.action.MapActionFactory;
import com.atakmap.android.config.ConfigEnvironment;
import com.atakmap.android.maps.assets.MapAssets;
import com.atakmap.android.maps.graphics.GLMapComponent;
import com.atakmap.android.metrics.activity.MetricFragmentActivity;
import com.atakmap.android.network.ContentResolverURIStreamHandler;
import com.atakmap.android.network.FileSystemUriStreamHandler;
import com.atakmap.android.network.URIStreamHandlerFactory;
import com.atakmap.app.BuildConfig;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.opengl.GLMapSurface;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;

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
    private final List<Runnable> tasks = new ArrayList<>();

    private final Object lifecycleTransitionLock = new Object();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
        } catch (Exception e) {
            //Log.d(TAG, "error restoring activity", e);
        }

        final ContentResolverURIStreamHandler defaultHandler = new ContentResolverURIStreamHandler(
                this.getContentResolver());
        URIStreamHandlerFactory.registerHandler("content", defaultHandler);
        URIStreamHandlerFactory.registerHandler("android.resource",
                defaultHandler);
        URIStreamHandlerFactory.registerHandler("file",
                FileSystemUriStreamHandler.INSTANCE);

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
        synchronized (lifecycleTransitionLock) {
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

            // execute any pending tasks that require the activity to be in a started state
            for (Runnable task : tasks)
                try {
                    Log.d(TAG, "running a queued requested task");
                    task.run();
                } catch (Exception e) {
                    Log.e(TAG, "error running task: " + task);
                }
            tasks.clear();

        }

    }

    @Override
    public void onStop() {
        super.onStop();
        synchronized (lifecycleTransitionLock) {
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
        synchronized (lifecycleTransitionLock) {
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
    public void registerMapComponent(final MapComponent observer) {
        synchronized (lifecycleTransitionLock) {
            long s = SystemClock.elapsedRealtime();
            if (observer != null) {

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
    public void unregisterMapComponent(MapComponent observer) {
        synchronized (lifecycleTransitionLock) {
            if (!_observers.remove(observer))
                return;

            MapView view = getMapView();
            observer.onPause(this, view);
            observer.onStop(this, view);
            observer.onDestroy(this, view);
        }
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
            MapComponentLoader.loadGLWidgets(this);
            requiredAssetsLoaded = true;
        }
    }

    protected void loadAssets() throws IOException, SAXException {

        if (!requiredAssetsLoaded) {
            Log.e(TAG,
                    "error will occur with rendering layers if the required assets are not loaded ahead of time");
        }

        MapComponentLoader.loadMapComponents(this);

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

    /**
     * Run a task only when the activity is started (active state) or enqueue
     * it to be run when the activity is started.
     * @param task Task to run if the activity is active (ie onStart has been called).
     */
    public void executeOnActive(Runnable task) {
        executeOnActive(task, false);
    }

    /**
     * Post a task only when the activity is started (active state) or enqueue
     * it to be run when the activity is started.
     * @param task Task to run if the activity is active (ie onStart has been called).
     */
    public void postOnActive(Runnable task) {
        executeOnActive(task, true);
    }

    /**
     * Run a task only when the activity is started (active state) or enqueue
     * it to be run when the activity is started.
     * @param task Task to run if the activity is active (ie onStart has been called).
     * @param post True to post the task to the map view (executed next frame)
     */
    private void executeOnActive(final Runnable task, final boolean post) {
        synchronized (lifecycleTransitionLock) {
            if (_isActive) {
                try {
                    final MapView mapView = getMapView();
                    if (post && mapView != null) {
                        Log.d(TAG, "Posting requested task");
                        mapView.post(task);
                    } else {
                        Log.d(TAG, "Running requested task immediately");
                        task.run();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error running task: " + task, e);
                }
            } else {
                Log.d(TAG,
                        "Request to run a task but the activity is not active");
                tasks.add(task);
            }
        }
    }
}
