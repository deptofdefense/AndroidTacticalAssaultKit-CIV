
package com.atakmap.android.plugintemplate.plugin.support;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import com.atakmap.android.maps.MapComponent;
import com.atakmap.android.maps.MapView;

import transapps.maps.plugin.lifecycle.Lifecycle;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import com.atakmap.coremap.log.Log;

/**
 * Do not use unless deploying your plugin with a version of ATAK less than 4.5.1.
 * @deprecated
 */
@Deprecated
abstract public class AbstractPluginLifecycle implements Lifecycle {

    private final Context pluginContext;
    private final Collection<MapComponent> overlays;
    private MapView mapView;

    private final static String TAG = "AbstractPluginLifecycle";

    public AbstractPluginLifecycle(Context ctx, MapComponent component) {
        this.pluginContext = ctx;
        this.overlays = new LinkedList<>();
        this.mapView = null;
        this.overlays.add(component);
        //PluginNativeLoader.init(ctx);
    }

    @Override
    final public void onConfigurationChanged(Configuration arg0) {
        for (MapComponent c : this.overlays)
            c.onConfigurationChanged(arg0);
    }

    @Override
    final public void onCreate(final Activity arg0,
            final transapps.mapi.MapView arg1) {
        if (arg1 == null || !(arg1.getView() instanceof MapView)) {
            Log.w(TAG, "This plugin is only compatible with ATAK MapView");
            return;
        }
        this.mapView = (MapView) arg1.getView();

        // create components
        Iterator<MapComponent> iter = this.overlays
                .iterator();
        MapComponent c;
        while (iter.hasNext()) {
            c = iter.next();
            try {
                c.onCreate(pluginContext, arg0.getIntent(), mapView);
            } catch (Exception e) {
                Log.w(TAG,
                        "Unhandled exception trying to create overlays MapComponent",
                        e);
                iter.remove();
            }
        }
    }

    @Override
    final public void onDestroy() {
        for (MapComponent c : this.overlays)
            c.onDestroy(this.pluginContext, this.mapView);
    }

    @Override
    final public void onFinish() {
        // XXX - no corresponding MapComponent method
    }

    @Override
    final public void onPause() {
        for (MapComponent c : this.overlays)
            c.onPause(this.pluginContext, this.mapView);
    }

    @Override
    final public void onResume() {
        for (MapComponent c : this.overlays)
            c.onResume(this.pluginContext, this.mapView);
    }

    @Override
    final public void onStart() {
        for (MapComponent c : this.overlays)
            c.onStart(this.pluginContext, this.mapView);
    }

    @Override
    final public void onStop() {
        for (MapComponent c : this.overlays)
            c.onStop(this.pluginContext, this.mapView);
    }
}
