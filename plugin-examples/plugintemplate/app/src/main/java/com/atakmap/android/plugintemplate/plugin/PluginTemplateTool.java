
package com.atakmap.android.plugintemplate.plugin;

import com.atak.plugins.impl.AbstractPluginTool;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.plugintemplate.PluginTemplateDropDownReceiver;
import com.atakmap.util.Disposable;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.ViewGroup;
import transapps.mapi.MapView;
import transapps.maps.plugin.tool.Group;
import transapps.maps.plugin.tool.Tool;
import transapps.maps.plugin.tool.ToolDescriptor;

/**
 * Please note:
 *     Support for versions prior to 4.5.1 can make use of a copy of AbstractPluginTool shipped with
 *     the plugin.
 */
public class PluginTemplateTool extends AbstractPluginTool implements Disposable {

    public PluginTemplateTool(Context context) {
        super(context,
                context.getString(R.string.app_name),
                context.getString(R.string.app_name),
                context.getResources().getDrawable(R.drawable.ic_launcher),
                PluginTemplateDropDownReceiver.SHOW_PLUGIN);
    }

    @Override
    public void dispose() {
    }
}
