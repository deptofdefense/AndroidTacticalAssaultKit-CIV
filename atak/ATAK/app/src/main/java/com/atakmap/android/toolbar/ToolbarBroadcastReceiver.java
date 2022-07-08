
package com.atakmap.android.toolbar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.navigation.views.NavView;
import com.atakmap.android.navigation.views.buttons.NavButton;
import com.atakmap.android.navigation.views.buttons.NavButtonChildView;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.tools.ActionBarView;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;
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

    public static final String SET_TOOLBAR = "com.atakmap.android.maps.toolbar.SET_TOOLBAR";
    public static final String UNSET_TOOLBAR = "com.atakmap.android.maps.toolbar.UNSET_TOOLBAR";
    public static final String OPEN_TOOLBAR = "com.atakmap.android.maps.toolbar.OPEN_TOOLBAR";

    private static ToolbarBroadcastReceiver _instance = null;

    protected MapView _mapView;

    protected final HashMap<String, IToolbarExtension> toolbars = new HashMap<>();

    protected String activeToolbar = "";

    private ToolManagerBroadcastReceiver toolManager;
    private NavButtonChildView childView;

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

    /**
     * Close the open toolbar in the given position
     * @param position Position
     */
    public void closeToolbar(int position) {
        if (position == ActionBarView.TOP_LEFT)
            removeChildView();
        else if (position == ActionBarView.TOP_RIGHT)
            ActionBarReceiver.getInstance().setToolView(null);
    }

    /**
     * Reposition the toolbar
     */
    public void repositionToolbar() {
        if (childView != null)
            childView.reposition();
    }

    public String getActive() {
        return activeToolbar;
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

                if (getActive().equals(toolbar)) {
                    setToolbar("");
                    return;
                }

                setToolbar(toolbar);

                if (!toolbar.equals("")) {
                    Log.d(TAG,
                            "show the toolbar: "
                                    + intent.getExtras().getString("toolbar"));
                } else {
                    Log.d(TAG, "*DEPRECATED* hide the toolbar: " + toolbar);
                    // notify the new tool bar is visible
                    Log.e(TAG, "deprecated call to hide the toolbar at: ",
                            new Exception());
                }

                break;
            case UNSET_TOOLBAR:
                // notify the new tool bar is visible
                ToolManagerBroadcastReceiver.getInstance().endCurrentTool();
                setToolbar(""); // sets it to nothing
                break;
            case OPEN_TOOLBAR: // Open is the same as SET except it
                // doesn't end a tool, useful if
                // you're a tool that wants to set a
                // toolbar.

                if (!toolbar.equals("")) {
                    // actually change the toolbar
                    setToolbar(toolbar);
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
                } else if (intent.hasExtra("point")) { // This is a map-click menu, so there won't
                    // be an
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
                    // Closing the dropdown calls HIDE_MENU, which closes our just opened child menu
                    // removeChildView();
                }

                break;
            case "com.atakmap.android.maps.toolbar.END_TOOL":
            case "com.atakmap.android.maps.toolbar.BEGIN_TOOL":
                // Do nothing - stop displaying an error in the logcat (the else statement)
                break;
            default:
                Log.w(TAG,
                        "received unrecognized intent action: "
                                + intent.getAction() + " in "
                                + intent);
                break;
        }
    }

    protected void setToolbar(String toolbarIdentifier) {

        if (toolbarIdentifier == null)
            toolbarIdentifier = "";

        IToolbarExtension ext = toolbars.get(toolbarIdentifier);
        IToolbarExtension active = getActiveToolbar();

        if (ext != active) {
            if (active != null) {
                closeToolbar(active);
                active.onToolbarVisible(false);
            }

            activeToolbar = "";
            if (ext != null && ext.hasToolbar()) {
                ActionBarView abv = ext.getToolbarView();
                if (abv != null) {
                    activeToolbar = toolbarIdentifier;
                    openToolbar(toolbarIdentifier);
                    ext.onToolbarVisible(true);
                }
            }
        } else {
            // Refresh the toolbar layout
            openToolbar(toolbarIdentifier);
        }
    }

    protected IToolbarExtension getActiveToolbar() {
        if (activeToolbar == null || activeToolbar.contentEquals("")) {
            return null;
        } else {
            IToolbarExtension ite = toolbars.get(activeToolbar);
            if (ite != null)
                Log.d(TAG, "active toolbar: " + activeToolbar);
            return ite;
        }
    }

    private void closeToolbar(IToolbarExtension ext) {
        ActionBarView abv = ext.getToolbarView();
        if (abv == null)
            return;

        switch (abv.getPosition()) {
            case ActionBarView.TOP_LEFT:
                removeChildView();
                break;
            case ActionBarView.TOP_RIGHT:
                ActionBarReceiver.getInstance().setToolView(null, false);
                break;
        }
    }

    private void openToolbar(String toolbar) {
        IToolbarExtension tbInterface = toolbars.get(toolbar);
        if (tbInterface == null || !tbInterface.hasToolbar())
            return;

        ActionBarView abv = tbInterface.getToolbarView();
        if (abv == null)
            return;

        switch (abv.getPosition()) {
            case ActionBarView.TOP_LEFT: {
                NavView navView = NavView.getInstance();
                View menuButton = navView
                        .findViewById(R.id.tak_nav_menu_button);
                NavButton takButton = navView.findToolbarButton(toolbar);
                if (childView == null)
                    childView = new NavButtonChildView(_mapView.getContext());
                View anchor = takButton != null ? takButton : menuButton;
                childView.layoutForToolbarView(anchor, abv);
                if (childView.getParent() == null)
                    navView.addView(childView);
                break;
            }
            case ActionBarView.TOP_RIGHT:
                ActionBarReceiver.getInstance().setToolView(abv);
                break;
        }
    }

    private void removeChildView() {
        if (childView != null) {
            ViewGroup parent = (ViewGroup) childView.getParent();
            if (parent != null) {
                parent.removeView(childView);
                childView = null;
            }
        }
    }
}
