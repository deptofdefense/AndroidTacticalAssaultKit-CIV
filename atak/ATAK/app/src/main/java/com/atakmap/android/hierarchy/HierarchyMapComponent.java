
package com.atakmap.android.hierarchy;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.hierarchy.HierarchyListReceiver.HIERARCHY_MODE;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.ipc.DocumentedExtra;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.widgets.AbstractWidgetMapComponent;

import java.util.ArrayList;

/**
 * Provides the components required to support the Map Overlay manager.
 */
public class HierarchyMapComponent extends AbstractWidgetMapComponent {

    protected HierarchyListReceiver _hierarchyListReceiver;

    @Override
    public void onCreateWidgets(Context context, Intent intent, MapView view) {
        DocumentedIntentFilter f = new DocumentedIntentFilter();
        f.addAction(HierarchyListReceiver.MANAGE_HIERARCHY,
                "Open the Overlay Manager drop-down",
                new DocumentedExtra[] {
                        new DocumentedExtra("list_item_paths",
                                "List of path UIDs used to navigate to a list",
                                true, ArrayList.class),
                        new DocumentedExtra("hier_mode",
                                "The default mode to use (i.e. SEARCH)",
                                true, HIERARCHY_MODE.class),
                        new DocumentedExtra("isRootList",
                                "True to treat the navigated-to list as root (back-button will close OM instead of navigating up)",
                                true, Boolean.class),
                        new DocumentedExtra("hier_userselect_handler",
                                "User select handler class name",
                                true, String.class),
                        new DocumentedExtra("hier_multiselect",
                                "True to enable multi-select",
                                true, Boolean.class),
                        new DocumentedExtra("hier_usertag",
                                "Tag string that can be read once the select handler is finished",
                                true, String.class),
                        new DocumentedExtra("hier_userselect_mapitems_uids",
                                "Whitelist of map item UIDs to display",
                                true, ArrayList.class),
                        new DocumentedExtra("refresh",
                                "True to refresh if already open re-open, otherwise re-open",
                                true, Boolean.class)
                });
        f.addAction(HierarchyListReceiver.REFRESH_HIERARCHY,
                "Refresh Overlay Manager items and lists",
                new DocumentedExtra[] {
                        new DocumentedExtra("list_item_paths",
                                "List of path UIDs used to navigate to a list",
                                true, ArrayList.class),
                        new DocumentedExtra("hier_mode",
                                "The default mode to use (i.e. SEARCH)",
                                true, HIERARCHY_MODE.class),
                });
        f.addAction(HierarchyListReceiver.CLEAR_HIERARCHY,
                "Clear Overlay Manager user-select handler and sets to default visibility mode");
        f.addAction(HierarchyListReceiver.CLOSE_HIERARCHY,
                "Close Overlay Manager",
                new DocumentedExtra[] {
                        new DocumentedExtra("closeIntent",
                                "Intent to broadcast once OM is closed",
                                true, Intent.class),
                        new DocumentedExtra("closeIntents",
                                "Array of intents to broadcast once OM is closed",
                                true, Intent[].class),
                });
        f.addAction(MapMenuReceiver.HIDE_MENU,
                "Deselect any currently selected item in Overlay Manager");
        this.registerReceiver(context,
                _hierarchyListReceiver = new HierarchyListReceiver(view,
                        context),
                f);
        HierarchyListReceiver.setInstance(_hierarchyListReceiver);
    }

    @Override
    protected void onDestroyWidgets(Context context, MapView view) {
        _hierarchyListReceiver.dispose();
        _hierarchyListReceiver = null;
        HierarchyListReceiver.setInstance(null);
    }
}
