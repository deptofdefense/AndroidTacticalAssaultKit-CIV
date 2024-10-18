
package com.atakmap.android.plugintemplate.plugin;


import com.atak.plugins.impl.AbstractPluginLifecycle;
import com.atakmap.android.plugintemplate.PluginTemplateMapComponent;
import android.content.Context;


/**
 * Please note:
 *     Support for versions prior to 4.5.1 can make use of a copy of AbstractPluginLifeCycle shipped with
 *     the plugin.
 */
public class PluginTemplateLifecycle extends AbstractPluginLifecycle {

    private final static String TAG = "PluginTemplateLifecycle";

    public PluginTemplateLifecycle(Context ctx) {
        super(ctx, new PluginTemplateMapComponent());
        PluginNativeLoader.init(ctx);
    }

}
