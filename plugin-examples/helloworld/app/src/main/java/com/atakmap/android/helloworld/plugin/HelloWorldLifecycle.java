
package com.atakmap.android.helloworld.plugin;


import android.content.Context;

import com.atak.plugins.impl.AbstractPluginLifecycle;
import com.atakmap.android.helloworld.HelloWorldMapComponent;

public class HelloWorldLifecycle extends AbstractPluginLifecycle {
    public HelloWorldLifecycle(Context ctx) {
        super(ctx, new HelloWorldMapComponent());
    }
}
