
package com.atakmap.android.toolbar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.log.Log;

import java.util.HashMap;
import java.util.List;

/**
 * Manages the current active tool bar based on intents received which request the active toolbar to
 * change and calls to registerToolbar. Locks and unlocks the toolbar as other menus open.
 * 
 */
public class ToolbarBroadcastReceiver extends BroadcastReceiver {

    public static final String TAG = "ToolbarBroadcastReceiver";

    private MapView _mapView;

    private ToolManagerBroadcastReceiver toolManager;

    protected ToolbarBroadcastReceiver() {
    }

    public void requestLayout() {
        if (_mapView != null) {
            toolManager = null;
            initialize(_mapView);
            //if (getActiveToolbar() != null)
            //    ActionBarReceiver.getInstance().setToolView(getActiveToolbar().getToolbarView());
        }

    }

    synchronized public void initialize(MapView mapView) {
        if (toolManager != null)
            return;

        _mapView = mapView;

        toolManager = ToolManagerBroadcastReceiver.getInstance();

    }

    synchronized public static ToolbarBroadcastReceiver getInstance() {
        if (_instance == null) {
            _instance = new ToolbarBroadcastReceiver();
        }
        return _instance;
    }

    public static synchronized ToolbarBroadcastReceiver checkInstance() {
        return _instance;
    }

    public void dispose() {
        for (IToolbarExtension toolbarComponent : toolbars.values()) {
            List<Tool> tools = toolbarComponent.getTools();
            if (tools != null && !tools.isEmpty()) {
                for (Tool t : toolbarComponent.getTools()) {
                    toolManager.unregisterTool(t.getIdentifier());
                }
            }
        }
        toolbars.clear();
        _instance = null;
    }

    /**
     * @deprecated Use the ToolManagerBroadcastReceiver to register your tools
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public void registerToolbarComponent(String toolbarIdentifier,
            IToolbarExtension toolbarComponent) {
        toolbars.put(toolbarIdentifier, toolbarComponent);
        // TODO: if it's the active one, refresh it's view

        final List<Tool> tools = toolbarComponent.getTools();
        if (tools != null && !tools.isEmpty()) {
            for (Tool t : tools) {
                toolManager.registerTool(t.getIdentifier(), t);
            }
        }
    }

    public void unregisterToolbarComponent(String toolbarIdentifier) {
        IToolbarExtension toolbarComponent = toolbars.remove(toolbarIdentifier);
        if (toolbarComponent != null) {
            final List<Tool> tools = toolbarComponent.getTools();
            if (tools != null && !tools.isEmpty()) {
                for (Tool t : tools) {
                    toolManager.unregisterTool(t.getIdentifier());
                }
            }
        }

    }

    public void registerToolbar(String toolbarIdentifier,
            IToolbarExtension toolbarComponent) {
        toolbars.put(toolbarIdentifier, toolbarComponent);
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        Log.d(TAG, intent.getAction()
                + ": "
                + ((intent.getExtras() != null) ? intent.getExtras()
                        .getString("toolbar") : ""));

        String toolbar = "";
        if (intent.hasExtra("toolbar")
                && intent.getExtras().getString("toolbar") != null) {
            toolbar = intent.getExtras().getString("toolbar");
        }

        switch (intent.getAction()) {
            case SET_TOOLBAR:
                ToolManagerBroadcastReceiver.getInstance().endCurrentTool();
                setToolbar(toolbar);

                if (!toolbar.equals("")) {
                    Log.d(TAG,
                            "show the toolbar: "
                                    + intent.getExtras().getString("toolbar"));
                    if (getActiveToolbar() != null) {
                        ActionBarReceiver.getInstance().setToolView(
                                getActiveToolbar().getToolbarView());
                    }
                } else {
                    Log.d(TAG, "*DEPRECATED* hide the toolbar: " + toolbar);
                    // notify the new tool bar is visible
                    ActionBarReceiver.getInstance().setToolView(null);
                    Log.e(TAG, "deprcrated call to hide the toolbar at: ",
                            new Exception());
                }

                break;
            case UNSET_TOOLBAR:
                // notify the new tool bar is visible
                ToolManagerBroadcastReceiver.getInstance().endCurrentTool();
                setToolbar(""); // sets it to nothing

                if (intent.getBooleanExtra("setToolView", true))
                    ActionBarReceiver.getInstance().setToolView(null);
                break;
            case OPEN_TOOLBAR: // Open is the same as SET except it
                // doesn't end a tool, useful if
                // you're a tool that wants to set a
                // toolbar.

                if (intent.hasExtra("toolbar")
                        && intent.getExtras().getString("toolbar") != null) {

                    // actually change the toolbar
                    setToolbar(toolbar);

                    if (getActiveToolbar() != null)
                        ActionBarReceiver.getInstance().setToolView(
                                getActiveToolbar().getToolbarView());

                }

                break;
            case "com.atakmap.android.maps.SHOW_MENU":

                String uid = intent.getStringExtra("uid");
                if (uid != null && !uid.equals("_SELECT_POINT_")) {// short-circuit for the "X"
                    MapItem item = _mapView.getRootGroup().deepFindItem("uid",
                            uid);
                    if (item != null && item.getMetaString("menu", null) != null
                            && !item.getMetaBoolean("ignoreMenu", false)) {
                        // it has a uid, a menu, and no "ignoreMenu" so lock it
                    }
                } else if (intent.hasExtra("point")) { // This is a map-click menu, so there won't be an
                    // item but we still want to lock
                }

                break;
            case "com.atakmap.android.maps.HIDE_MENU":
                if (intent.hasExtra("fromActionBar")
                        && intent.getBooleanExtra("fromActionBar", false)) {
                    // do NOTHING
                } else {
                    // Not sure why we would have to close it again?
                    // We close it again because this also gets called when you hit the back button.
                }

                break;
            case "com.atakmap.android.maps.toolbar.END_TOOL":
            case "com.atakmap.android.maps.toolbar.BEGIN_TOOL":
                //Do nothing - stop displaying an error in the logcat (the else statement)
                break;
            default:
                Log.w(TAG,
                        "received unrecognized intent action: "
                                + intent.getAction() + " in "
                                + intent);
                break;
        }
    }

    private void setToolbar(final String toolbarIdentifier) {

        if (toolbars.get(toolbarIdentifier) != null
                && toolbars.get(toolbarIdentifier).hasToolbar()) {

            // Previous active toolbar extension
            IToolbarExtension activeToolbarExt = getActiveToolbar();

            // Keep track of the last active unique toolbar
            if (activeToolbarExt != null) {

                // notify the old tool bar is not visible
                activeToolbarExt.onToolbarVisible(false);
            }

            // Set the new toolbar
            activeToolbar = toolbarIdentifier;

            activeToolbarExt = getActiveToolbar();
            if (activeToolbarExt != null) {

                ActionBarReceiver.getInstance().setToolView(
                        activeToolbarExt.getToolbarView());

                // notify the new tool bar is visible
                activeToolbarExt.onToolbarVisible(true);
            }

        } else {

            if (getActiveToolbar() != null)
                getActiveToolbar().onToolbarVisible(false);
            activeToolbar = "";

        }
    }

    private IToolbarExtension getActiveToolbar() {
        if (activeToolbar == null || activeToolbar.contentEquals("")) {
            return null;
        } else {
            IToolbarExtension ite = toolbars.get(activeToolbar);
            if (ite != null)
                Log.d(TAG, "active toolbar: " + activeToolbar);
            return ite;
        }
    }

    public String getActive() {
        return activeToolbar;
    }

    public static final String SET_TOOLBAR = "com.atakmap.android.maps.toolbar.SET_TOOLBAR";
    public static final String UNSET_TOOLBAR = "com.atakmap.android.maps.toolbar.UNSET_TOOLBAR";
    public static final String OPEN_TOOLBAR = "com.atakmap.android.maps.toolbar.OPEN_TOOLBAR";

    private final HashMap<String, IToolbarExtension> toolbars = new HashMap<>();

    private String activeToolbar = "";

    private static ToolbarBroadcastReceiver _instance = null;

}
