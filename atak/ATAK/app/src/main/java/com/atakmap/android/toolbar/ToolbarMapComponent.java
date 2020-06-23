
package com.atakmap.android.toolbar;

import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.toolbar.menu.ToolbarMenuManager;
import com.atakmap.android.toolbar.tools.MovePointTool;
import com.atakmap.android.toolbar.tools.SpecifyLockItemTool;
import com.atakmap.android.toolbar.tools.SpecifySelfLocationTool;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.widgets.AbstractWidgetMapComponent;

/**
 * Provides the hooks for Tools to register and be used within the system.
 * Tools, unlike DropDowns, do not have a right side or left side presence.
 */
public class ToolbarMapComponent extends AbstractWidgetMapComponent {

    private ToolbarBroadcastReceiver _toolbarBroadcastReceiver;

    @Override
    protected void onCreateWidgets(Context context, Intent intent,
            MapView view) {
        ToolbarMenuManager.getInstance().initialize(context, view);

        _toolbarBroadcastReceiver = ToolbarBroadcastReceiver.getInstance();
        _toolbarBroadcastReceiver.initialize(view);

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction("com.atakmap.android.maps.toolbar.END_TOOL");
        filter.addAction("com.atakmap.android.maps.toolbar.BEGIN_TOOL");
        filter.addAction("com.atakmap.android.maps.toolbar.BEGIN_SUB_TOOL");
        filter.addAction("com.atakmap.android.maps.toolbar.INVOKE_METHOD_TOOL");
        filter.addAction(ToolbarBroadcastReceiver.SET_TOOLBAR);
        filter.addAction(ToolbarBroadcastReceiver.UNSET_TOOLBAR);
        filter.addAction(ToolbarBroadcastReceiver.OPEN_TOOLBAR);

        filter.addAction("com.atakmap.android.maps.SHOW_MENU");
        filter.addAction("com.atakmap.android.maps.HIDE_MENU");
        AtakBroadcast.getInstance().registerReceiver(_toolbarBroadcastReceiver,
                filter);
        AtakBroadcast.getInstance().registerReceiver(
                ToolManagerBroadcastReceiver.getInstance(),
                filter);

        // Set up core tools used by MapLibrary
        ToolManagerBroadcastReceiver.getInstance().registerTool(
                SpecifySelfLocationTool.TOOL_IDENTIFIER,
                new SpecifySelfLocationTool(view));
        ToolManagerBroadcastReceiver.getInstance().registerTool(
                MovePointTool.TOOL_IDENTIFIER,
                new MovePointTool(view));
        ToolManagerBroadcastReceiver.getInstance().registerTool(
                SpecifyLockItemTool.TOOL_IDENTIFIER,
                new SpecifyLockItemTool(view));
    }

    @Override
    protected void onDestroyWidgets(Context context, MapView view) {
        try {
            AtakBroadcast.getInstance().unregisterReceiver(
                    _toolbarBroadcastReceiver);
        } catch (Exception e) {
            // probably a null pointer resulting from trying to quit before it's initialized
        }
        AtakBroadcast.getInstance().unregisterReceiver(
                ToolManagerBroadcastReceiver.getInstance());

        ToolbarMenuManager.getInstance().dispose();
        TextContainer.getInstance().dispose();

        ToolManagerBroadcastReceiver.getInstance().dispose();

        _toolbarBroadcastReceiver.dispose();

        _toolbarBroadcastReceiver = null;

    }

}
