
package com.atakmap.android.plugintemplate.plugin.support;

import com.atakmap.android.ipc.AtakBroadcast;

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
 * Do not use unless deploying your plugin with a version of ATAK less than 4.5.1.
 * @deprecated
 */
@Deprecated
abstract public class AbstractPluginTool extends Tool implements ToolDescriptor {

    private final Context context;
    private final String shortDescription;
    private final String description;
    private final Drawable icon;
    private final String action;

    /**
     * Construct an abstract PluginTool
     * @param context the context to use
     * @param shortDescription the short dscription
     * @param description the description
     * @param icon the icon
     * @param action the action
     */
    public AbstractPluginTool(Context context,
                              String shortDescription,
                              String description,
                              Drawable icon,
                              String action) {
        this.context = context;
        this.shortDescription = shortDescription;
        this.description = description;
        this.icon = icon;
        this.action = action;
    }

    @Override
    final public String getDescription() {
        return description;
    }

    @Override
    final public Drawable getIcon() {
        return icon;
    }

    @Override
    final public Group[] getGroups() {
        return new Group[] {
                Group.GENERAL
        };
    }

    @Override
    final public String getShortDescription() {
        return shortDescription;
    }

    @Override
    final public Tool getTool() {
        return this;
    }

    @Override
    final public void onActivate(Activity arg0,
                                 MapView arg1,
                                 ViewGroup arg2,
                                 Bundle arg3,
                                 ToolCallback arg4) {

        // Hack to close the dropdown that automatically opens when a tool
        // plugin is activated.
        if (arg4 != null) {
            arg4.onToolDeactivated(this);
        }
        // Intent to launch the dropdown or tool

        //arg2.setVisibility(ViewGroup.INVISIBLE);
        Intent i = new Intent(action);
        AtakBroadcast.getInstance().sendBroadcast(i);
    }

    @Override
    final public void onDeactivate(ToolCallback arg0) {
    }
}
