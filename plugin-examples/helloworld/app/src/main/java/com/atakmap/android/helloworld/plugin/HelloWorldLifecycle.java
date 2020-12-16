
package com.atakmap.android.helloworld.plugin;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import com.atakmap.android.helloworld.HelloWorldMapComponent;
import com.atakmap.android.helloworld.HelloWorldWidget;

import com.atakmap.android.maps.MapComponent;
import com.atakmap.android.maps.MapView;

import transapps.maps.plugin.lifecycle.Lifecycle;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import com.atakmap.coremap.log.Log;

/**
 * In a plugin, the lifecycle and tool components are the only 
 * parts of the NettWarrior plugin architecture that ATAK uses.
 * An ATAK can have zero or more of each of these and they are 
 * defined in the assets folder under the plugins.xml file.
 *
 * A lifecycle roughy maps to the ATAK concept of a MapComponent
 * and is able to add a concrete concept to the ATAK environment.
 * In this case, this lifecycle is responsbile for two 
 * MapComponents.
 */
public class HelloWorldLifecycle implements Lifecycle {

    private final Context pluginContext;
    private final Collection<MapComponent> overlays;
    private MapView mapView;

    private final static String TAG = "HelloWorldLifecycle";

    public HelloWorldLifecycle(Context ctx) {
        this.pluginContext = ctx;
        this.overlays = new LinkedList<>();
        this.mapView = null;
    }

    @Override
    public void onConfigurationChanged(Configuration arg0) {
        for (MapComponent c : this.overlays)
            c.onConfigurationChanged(arg0);
    }

    @Override
    public void onCreate(final Activity arg0,
            final transapps.mapi.MapView arg1) {
        if (arg1 == null || !(arg1.getView() instanceof MapView)) {
            Log.w(TAG, "This plugin is only compatible with ATAK MapView");
            return;
        }
        this.mapView = (MapView) arg1.getView();
        HelloWorldLifecycle.this.overlays.add(new HelloWorldMapComponent());
        HelloWorldLifecycle.this.overlays.add(new HelloWorldWidget());

        // create components
        Iterator<MapComponent> iter = HelloWorldLifecycle.this.overlays
                .iterator();
        MapComponent c;
        while (iter.hasNext()) {
            c = iter.next();
            try {
                c.onCreate(HelloWorldLifecycle.this.pluginContext,
                        arg0.getIntent(),
                        HelloWorldLifecycle.this.mapView);
            } catch (Exception e) {
                Log.w(TAG,
                        "Unhandled exception trying to create overlays MapComponent",
                        e);
                iter.remove();
            }
        }
    }

    @Override
    public void onDestroy() {
        for (MapComponent c : this.overlays)
            c.onDestroy(this.pluginContext, this.mapView);
    }

    @Override
    public void onFinish() {
        // XXX - no corresponding MapComponent method
    }

    @Override
    public void onPause() {
        for (MapComponent c : this.overlays)
            c.onPause(this.pluginContext, this.mapView);
    }

    @Override
    public void onResume() {
        for (MapComponent c : this.overlays)
            c.onResume(this.pluginContext, this.mapView);
    }

    @Override
    public void onStart() {
        for (MapComponent c : this.overlays)
            c.onStart(this.pluginContext, this.mapView);
    }

    @Override
    public void onStop() {
        for (MapComponent c : this.overlays)
            c.onStop(this.pluginContext, this.mapView);
    }

}
