
package com.atakmap.android.tools;

import android.app.ActionBar;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.toolbar.ToolbarBroadcastReceiver;
import com.atakmap.android.tools.menu.ActionBroadcastData;
import com.atakmap.android.tools.menu.ActionClickData;
import com.atakmap.android.tools.menu.ActionMenuData;
import com.atakmap.android.tools.menu.ActionMenuData.PreferredMenu;
import com.atakmap.android.tools.menu.AtakActionBarListData;
import com.atakmap.android.tools.menu.AtakActionBarMenuData;
import com.atakmap.android.tools.menu.AtakActionBarMenuData.Orientation;
import com.atakmap.app.ATAKActivity;
import com.atakmap.app.R;
import com.atakmap.app.preferences.CustomActionBarFragment;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.atakmap.annotations.DeprecatedApi;

/**
 * 
 */
public class ActionBarReceiver extends BroadcastReceiver {

    private static final String TAG = "ActionBarReceiver";
    private static ActionBarReceiver instance;

    public static String SETTINGS_TOOL = "Settings";
    public static String LAYOUT_MGR = "Toolbar Manager";
    public static String QUIT_TOOL = "Quit";

    public static final float SCALE_FACTOR = 1.5f;

    public final static int QuerySpec = MeasureSpec.makeMeasureSpec(0,
            MeasureSpec.UNSPECIFIED);
    //public final static int QuerySpec = MeasureSpec.makeMeasureSpec(160, MeasureSpec.EXACTLY);
    //    public final static int DefaultPluginIconSize = 32; //32x32 pixels
    public final static int ActionItemPaddingLR = 16; // change this to control spacing b/t actions
    public final static int ActionItemPaddingTB = 5; // change this to control spacing b/t actions

    public static final String RELOAD_ACTION_BAR = "com.atakmap.android.tools.RELOAD_ACTION_BAR";
    public static final String ADD_NEW_TOOL = "com.atakmap.android.tools.ADD_NEW_TOOL";
    public static final String ADD_NEW_TOOLS = "com.atakmap.android.tools.ADD_NEW_TOOLS";
    public static final String REMOVE_TOOLS = "com.atakmap.android.tools.REMOVE_TOOLS";
    public static final String REFRESH_ACTION_BAR = "com.atakmap.android.tools.REFRESH_ACTION_BAR";

    public static final String DISABLE_ACTIONBAR = "com.atakmap.android.tools.DISABLE_ACTIONBAR";
    public static final String TOGGLE_ACTIONBAR = "com.atakmap.android.tools.TOGGLE_ACTIONBAR";
    public static final String DISABLE_ACTIONBAR_DEFAULT_REASON = "Action Bar is currently locked...";

    private static final int ACTIVE_TOOLBAR_BACKGROUND = Color.argb(70, 255,
            255, 255);

    private static Activity mActivity;
    private static SharedPreferences _prefs;

    private AtakActionBarListData mActionBars;
    private AtakActionBarListData apkMenus;

    private static List<ActionMenuData> latestPluginData;

    private boolean _disableActionBar;
    private String _actionBarDisabledReason;

    private boolean atakTapToggleActionBar;

    private int _lastActionBarHeight = 0;
    private Drawable originalOverflowDrawable;

    private static ImageView overflowView;

    /**
     * Allows a tool/plugin to specify a view for an ongoing user workflow
     * This view is allocated a portion of the action, with other tools filling in the
     * remaining available space
     */
    private ActionBarView _activeToolView = null;

    private static ActionBarReceiver _instance;
    private final View actionBarHandle;
    private final LinearLayout actionBarDrawer;
    private boolean showFloatingActionBar = true;

    private final List<String> customActionBarIntents = new ArrayList<>();
    private static final List<ActionBarChangeListener> listeners = new ArrayList<>();

    /**
     * Specifically for deployment of ATAK 3.3 on a NettWarrior device running CM 11.
     */
    @Deprecated
    @DeprecatedApi(since = "4.2", forRemoval = true, removeAt = "4.5")
    public static boolean nwTimSortFix = false;

    public ActionBarReceiver(Activity activity) {

        _instance = this;
        mActivity = activity;

        SETTINGS_TOOL = mActivity.getString(R.string.actionbar_settings);
        LAYOUT_MGR = mActivity.getString(R.string.actionbar_toolbarmanager);
        QUIT_TOOL = mActivity.getString(R.string.actionbar_quit);

        _disableActionBar = false;
        _actionBarDisabledReason = null;

        mActionBars = loadActionBars(mActivity);

        _prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        _prefs.registerOnSharedPreferenceChangeListener(_prefListener);
        setPrefs();

        //store the original drawable used as the 3 dots overflow icon for some
        //reason if we get reference to this later on its never recolors
        originalOverflowDrawable = mActivity.getResources().getDrawable(
                R.drawable.ic_action_overflow);

        // Top handle used to toggle action bar
        actionBarHandle = mActivity.findViewById(R.id.tophandle_background);
        actionBarHandle.setOnTouchListener(new OnTouchListener() {
            double tap;

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if (event.getAction() == MotionEvent.ACTION_UP) {
                    final double val = event.getY();

                    //Log.d(TAG, "val: " + val + " tap: " + tap);
                    if (val > tap) {
                        //Log.d(TAG, "pullout action bar");
                        AtakBroadcast.getInstance().sendBroadcast(
                                new Intent(ActionBarReceiver.TOGGLE_ACTIONBAR)
                                        .putExtra(
                                                "show", true));
                    } else {
                        //Log.d(TAG, "pullin action bar");
                        AtakBroadcast.getInstance().sendBroadcast(
                                new Intent(ActionBarReceiver.TOGGLE_ACTIONBAR)
                                        .putExtra(
                                                "show", false));
                    }
                    return true;
                } else if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    tap = event.getY();
                    return true;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    return true;
                } else {
                    return false;
                }
            }
        });

        // Drawer displayed top-right under action bar
        actionBarDrawer = mActivity.findViewById(
                R.id.action_bar_drawer);

        //check Action Bar version in APK, if not same as local version, then
        //throw local version away
        AtakActionBarListData apkMenus = getApkActionBars(activity);
        if (apkMenus != null
                && apkMenus.isValid()
                &&
                (mActionBars == null || !apkMenus.getVersion().equals(
                        mActionBars.getVersion()))) {
            Log.d(TAG, "Updating menu version: "
                    + (mActionBars == null ? "null" : mActionBars.getVersion())
                    + " to " + apkMenus.getVersion());
            mActionBars = apkMenus;
            mActionBars.save();
        }
        instance = this;
    }

    public interface ActionBarChangeListener {
        /**
         * Indicated to the listener that the actionBarChanged.    Reports back
         * true if the button did not match the current state of the tool and the
         * button had to be updated.
         */
        boolean actionBarChanged();
    }

    /**
     * Ability to look up a menu item based on a name
     */
    static public ActionMenuData getMenuItem(final String name) {

        if (instance == null) {
            return null;
        }

        AtakActionBarMenuData actionMenu = instance
                .getActionBars()
                .getActionBar(_prefs, mActivity);
        if (actionMenu != null) {
            return actionMenu.getAction(name);
        }
        return null;
    }

    static public void registerActionBarChangeListener(
            ActionBarChangeListener cl) {
        synchronized (listeners) {
            if (cl != null) {
                listeners.add(cl);
            }
        }
    }

    /**
     * Tells the listeners that the actionBar has changed.   If the any of the
     * buttons needed to be visually updated, this call will return true and it is
     * up to the callee to call invalidate on the state of the entire toolbar.
     * This cuts down on the number of possible calls to invalidate.
     */
    public boolean onChange() {
        synchronized (listeners) {
            boolean anyoneChanged = false;
            for (ActionBarChangeListener cl : listeners) {
                try {
                    boolean change = cl.actionBarChanged();
                    Log.d(TAG, cl + " reported: " + change);
                    anyoneChanged = anyoneChanged || change;
                } catch (Exception e) {
                    Log.d(TAG, "", e);
                }
            }
            return anyoneChanged;
        }
    }

    /**
     * adds a intent string to a arraylist of
     * intents to fire when the config actionbar is modified
     * allowing the intent to fire to a receiver in the plugins that
     * updates their config actionbar file
     *
     * @param intent the custom intent to fire.
     */
    public void addCustomActionBarIntent(String intent) {
        boolean add = true;
        for (String string : customActionBarIntents) {
            if (string.equals(intent)) {
                add = false;
                break;
            }
        }
        if (add) {
            customActionBarIntents.add(intent);
        }
        Log.d(TAG, "adding " + intent + " to list");
    }

    /**
     * send out every intent through the ATAK broadcast intent
     * that was bound when creating and binding the path values to the
     * current action bar see onReceive()
     */
    public void updatePluginActionBars() {
        Log.d(TAG, customActionBarIntents.size() + " SIZE OF ACTION BARS ");
        for (int i = 0; i < customActionBarIntents.size(); i++) {
            Log.d(TAG,
                    "Sending Update intent " + customActionBarIntents.get(i));
            AtakBroadcast.getInstance().sendBroadcast(
                    new Intent(customActionBarIntents.get(i)));
        }
    }

    /**
     * Get an instance of the receiver.
     */
    public static ActionBarReceiver getInstance() {
        return _instance;
    }

    private void clearToolView() {
        setToolView(null);
    }

    private void clear(final View v) {
        Log.d(TAG, "clearing tool view: " + v.getClass().getSimpleName());
        ViewParent vg = v.getParent();
        if (vg instanceof ViewGroup) {
            Log.d(TAG, "viewgroup clear");
            ((ViewGroup) vg).removeAllViews();
        } else {
            Log.d(TAG, "parent is not a viewgroup");
        }
    }

    /**
     * Provided an ActionBarView, show the tool as embedded and with an x to close the tool.
     *
     * @param v       the action bar view to show.
     * @param endTool Whether to send out an UNSET_TOOLBAR (end tool) intent when v is null
     */
    synchronized public void setToolView(final ActionBarView v,
            boolean endTool) {
        boolean active = false;
        if (_activeToolView != null) {
            active = true;
            Log.d(TAG, "removing: " + _activeToolView);
            clear(_activeToolView);
            _activeToolView = null;
        }

        if (v == null) {
            if (active && endTool) {
                Intent i = new Intent(ToolbarBroadcastReceiver.UNSET_TOOLBAR);
                // Don't call setToolView again when this is received
                // Otherwise an intent loop may occur
                i.putExtra("setToolView", false);
                AtakBroadcast.getInstance().sendBroadcast(i);
            }
        } else {
            _activeToolView = v;
            Log.d(TAG, "adding: " + v);

            //when toolbar is set, go ahead and show
            if (v.getEmbedState() != ActionBarView.FLOATING
                    && mActivity != null && mActivity.getActionBar() != null) {
                mActivity.getActionBar().show();
            }
        }
        refreshFloatingActionBar();

        if (mActivity != null) {
            mActivity.invalidateOptionsMenu();
        }
    }

    synchronized public void setToolView(final ActionBarView v) {
        setToolView(v, true);
    }

    synchronized public ActionBarView getToolView() {
        return _activeToolView;
    }

    private final OnClickListener activeToolCloseListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            if (isDisabled()) {
                String reason = getDisabledMessage();
                Log.d(TAG, "Action Bar is disabled: " + reason);
                Toast.makeText(mActivity, reason, Toast.LENGTH_LONG).show();
                return;
            }

            clearToolView();
        }
    };

    public boolean hasActionBars() {
        return mActionBars != null && mActionBars.isValid();
    }

    /**
     * Returns an array of boundaries of views which are top-aligned
     * Used to position text widgets properly
     */
    public List<Rect> getTopAlignedBounds() {
        List<Rect> rects = new ArrayList<>();
        MapView mv = MapView.getMapView();
        if (mv == null) {
            return rects;
        }

        // Action bar
        int abHeight = mv.getActionBarHeight();
        if (abHeight > 0) {
            rects.add(new Rect(0, 0, mv.getWidth(), abHeight));
        }

        // Floating toolbar
        if (_activeToolView != null
                && _activeToolView.getEmbedState() == ActionBarView.FLOATING
                && actionBarDrawer.getVisibility() == View.VISIBLE) {
            actionBarDrawer.measure(QuerySpec, QuerySpec);
            Rect r = new Rect();
            r.right = mv.getWidth();
            r.left = r.right - actionBarDrawer.getMeasuredWidth();
            r.top = mv.getActionBarHeight();
            r.bottom = r.top + actionBarDrawer.getMeasuredHeight();
            rects.add(r);
        }

        // Action bar handle
        if (actionBarHandle.getVisibility() == View.VISIBLE) {
            rects.add(new Rect(actionBarHandle.getLeft(),
                    actionBarHandle.getTop(),
                    actionBarHandle.getRight(),
                    actionBarHandle.getBottom()));
        }

        return rects;
    }

    public AtakActionBarListData getActionBars() {
        return mActionBars;
    }

    public static AtakActionBarListData loadActionBars(Context context) {
        AtakActionBarListData actionBars = AtakActionBarListData
                .loadActionBars(context);

        if (latestPluginData != null) {
            //try to re-add the loaded plugin menu data after reload
            replacePlugins(latestPluginData, actionBars);
        }

        return actionBars;
    }

    private static AtakActionBarListData getApkActionBars(Context context) {
        return AtakActionBarListData.loadActionBars(context);
    }

    public void reloadActionBars() {
        // reload from file system and re-populate (with currently selected action bar)
        //Log.d(TAG, "Reloading Action Bar");

        if (mActivity != null) {
            mActionBars = loadActionBars(mActivity);
            onChange();
            mActivity.invalidateOptionsMenu();
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action == null)
            return;

        Log.d(TAG, "Received intent: " + action);
        switch (action) {
            case RELOAD_ACTION_BAR:
                reloadActionBars();
                break;
            case REFRESH_ACTION_BAR:
                Log.d(TAG, "Refreshing Action Bar");
                if (!nwTimSortFix && mActivity != null) {
                    mActivity.invalidateOptionsMenu();
                }
                break;
            case ADD_NEW_TOOL: {
                ActionMenuData actionMenuData = null;
                try {
                    String ref = intent.getStringExtra("ref");
                    String title = intent.getStringExtra("title");
                    String iconPath = intent.getStringExtra("iconPath");
                    String enabledIconPath = intent
                            .getStringExtra("enabledIconPath");
                    String selectedIconPath = intent
                            .getStringExtra("selectedIconPath");
                    String preferredMenu = intent
                            .getStringExtra("preferredMenu");
                    boolean hideable = intent.getBooleanExtra("hideable",
                            false);
                    String actionToBroadcast = intent
                            .getStringExtra("actionToBroadcast");
                    boolean selected = intent.getBooleanExtra("selected",
                            false);
                    boolean enabled = intent.getBooleanExtra("enabled", false);
                    ArrayList<ActionClickData> temp = new ArrayList<>();
                    temp.add(new ActionClickData(new ActionBroadcastData(
                            actionToBroadcast, null), ActionClickData.CLICK));
                    actionMenuData = new ActionMenuData(
                            ref,
                            title,
                            iconPath,
                            enabledIconPath,
                            selectedIconPath,
                            preferredMenu,
                            hideable,
                            temp,
                            /*null,*/
                            selected,
                            enabled,
                            false);
                } catch (Exception e) {
                    Log.w(TAG, "Unable to decode intent into ActionMenuData "
                            + intent);
                }

                if (actionMenuData == null || !actionMenuData.isValid()) {
                    Log.w(TAG,
                            "Ignoring invalid plugin menu: "
                                    + (actionMenuData == null ? "null"
                                            : actionMenuData.toString()));
                    return;
                }

                List<ActionMenuData> list = new ArrayList<>();
                list.add(actionMenuData);
                addPlugins(list, mActionBars);
                break;
            }
            case ADD_NEW_TOOLS: {
                Parcelable[] pl = intent.getParcelableArrayExtra("menus");

                List<ActionMenuData> list = new ArrayList<>();
                if (pl != null && pl.length > 0) {
                    for (Parcelable p : pl) {
                        if (p instanceof ActionMenuData) {
                            list.add((ActionMenuData) p);
                        } else {
                            Log.w(TAG,
                                    "Ignoring invalid plugin menu of type");
                        }
                    }
                }

                addPlugins(list, mActionBars);
                break;
            }
            case REMOVE_TOOLS: {
                Parcelable[] pl = intent.getParcelableArrayExtra("menus");

                List<ActionMenuData> list = new ArrayList<>();
                if (pl != null && pl.length > 0) {
                    for (Parcelable p : pl) {
                        if (p instanceof ActionMenuData) {
                            list.add((ActionMenuData) p);
                        } else {
                            Log.w(TAG,
                                    "Ignoring invalid plugin menu of type");
                        }
                    }
                }

                removePlugins(list, mActionBars);
                break;
            }
            case DISABLE_ACTIONBAR:
                _disableActionBar = intent.getBooleanExtra("disable", false);
                _actionBarDisabledReason = intent.getStringExtra("message");
                if (FileSystemUtils.isEmpty(_actionBarDisabledReason)) {
                    _actionBarDisabledReason = DISABLE_ACTIONBAR_DEFAULT_REASON;
                }
                Log.d(TAG, "DISABLE_ACTIONBAR: " + _disableActionBar + ", "
                        + _actionBarDisabledReason);
                break;
            case TOGGLE_ACTIONBAR:
                final ActionBar ab;
                if (mActivity == null
                        || (ab = mActivity.getActionBar()) == null) {
                    return;
                }
                //see if tool wants to set a specific state
                if (intent.hasExtra("show")) {
                    boolean show = intent.getBooleanExtra("show", true);
                    if (!show) {
                        if (!atakTapToggleActionBar
                                || (_activeToolView != null && !_activeToolView
                                        .isClosable())) {
                            Log.d(TAG,
                                    "Not hiding action bar, toggling disabled");
                            return;
                        }

                        Log.d(TAG, "Hiding action bar");
                        ab.hide();
                        refreshFloatingActionBar();
                        MapView.getMapView().onActionBarToggled(0);
                        showActionBarHandle(
                                intent.getBooleanExtra("handle", true));
                        if (intent.hasExtra("toolbar")) {
                            showFloatingActionBar(intent.getBooleanExtra(
                                    "toolbar", true));
                        }
                    } else {
                        Log.d(TAG, "Showing action bar");
                        showActionBarHelper(ab);
                    }
                } else {
                    //just toggle from current state
                    boolean showing = ab.isShowing();
                    if (showing) {
                        if (!atakTapToggleActionBar
                                || (_activeToolView != null && !_activeToolView
                                        .isClosable())) {
                            Log.d(TAG,
                                    "Not hiding action bar, toggling disabled");
                            return;
                        }

                        Log.d(TAG, "Hiding action bar");
                        ab.hide();
                        refreshFloatingActionBar();
                        MapView.getMapView().onActionBarToggled(0);
                        showActionBarHandle(true);
                    } else {
                        Log.d(TAG, "Showing action bar");
                        showActionBarHelper(ab);
                    }
                }
                break;
        }
    }

    private void showActionBarHelper(final ActionBar ab) {
        ab.show();
        refreshFloatingActionBar();
        Log.d(TAG, "action bar height: " + ab.getHeight());
        MapView.getMapView().onActionBarToggled(
                ab.getHeight());

        showFloatingActionBar(true);
        showActionBarHandle(false);
        Log.d(TAG, "Refreshing Action Bar");
        if (_activeToolView != null) {
            _activeToolView.invalidate();
        }
        if (!nwTimSortFix && mActivity != null) {
            mActivity.invalidateOptionsMenu();
        }
    }

    private void showActionBarHandle(final boolean enable) {
        final MapView mv = MapView.getMapView();
        mv.post(new Runnable() {
            @Override
            public void run() {
                actionBarHandle.setVisibility(enable
                        ? View.VISIBLE
                        : View.GONE);
                // Refresh widget position calculations
                ((ATAKActivity) mv.getContext()).fireActionBarListeners();
            }
        });
    }

    private void showFloatingActionBar(final boolean enable) {
        final MapView mv = MapView.getMapView();
        mv.post(new Runnable() {
            @Override
            public void run() {
                if (showFloatingActionBar != enable) {
                    showFloatingActionBar = enable;
                    actionBarDrawer.setVisibility(enable
                            || (_activeToolView != null
                                    && !_activeToolView.isClosable())
                                            ? View.VISIBLE
                                            : View.GONE);
                    // Refresh action bar position if needed
                    refreshFloatingActionBar();
                    // Refresh widget position calculations
                    ((ATAKActivity) mv.getContext()).fireActionBarListeners();
                }
            }
        });
    }

    /**
     * Add menus for the specified plugins
     *
     * @param menus      plugin menu data
     * @param actionBars the action bar data to compare to
     */
    private static void addPlugins(List<ActionMenuData> menus,
            AtakActionBarListData actionBars) {
        if (menus == null) {
            Log.w(TAG, "adding plugins invalid");
            return;
        }

        //combine existing plugins, with new plugins
        Log.d(TAG, "adding plugins: " + menus.size());
        List<ActionMenuData> plugins = new ArrayList<>();
        if (!FileSystemUtils.isEmpty(latestPluginData)) {
            plugins.addAll(latestPluginData);
        }

        //TODO be sure this .equals works e.g. preferredMenu may have been changed
        for (ActionMenuData plugin : menus) {
            if (!plugins.contains(plugin)) {
                plugins.add(plugin);
            }
        }

        //now replace plugins
        replacePlugins(plugins, actionBars);
    }

    /**
     * Remove menus for the specified plugins
     *
     * @param menus      plugin menu data
     * @param actionBars the action bar data to compare to
     */
    private static void removePlugins(List<ActionMenuData> menus,
            AtakActionBarListData actionBars) {
        if (FileSystemUtils.isEmpty(menus)
                || FileSystemUtils.isEmpty(latestPluginData)) {
            Log.w(TAG, "removing plugins empty");
            return;
        }

        Log.d(TAG, "removing plugins: " + menus.size());
        if (!FileSystemUtils.isEmpty(latestPluginData)) {
            //TODO be sure this .equals works e.g. preferredMenu may have been changed
            for (ActionMenuData plugin : menus) {
                latestPluginData.remove(plugin);
            }
        }

        //now replace plugins
        replacePlugins(latestPluginData, actionBars);
    }

    /**
     * Add menus for the specified plugins. Also removes menus for plugins not in the list
     *
     * @param plugins    plugin menu data
     * @param actionBars the action bar data to compare to
     */
    private static void replacePlugins(List<ActionMenuData> plugins,
            AtakActionBarListData actionBars) {
        if (plugins != null) {
            Log.d(TAG, "replace plugins: " + plugins.size());
        }

        if (plugins != null && actionBars != null) {
            boolean bSave = false;
            for (ActionMenuData menu : plugins) {
                if (menu == null || !menu.isValid()) {
                    Log.w(TAG,
                            "Ignoring invalid plugin menu: "
                                    + (menu == null ? "null"
                                            : menu.toString()));
                    continue;
                }

                for (AtakActionBarMenuData m : actionBars
                        .getActionBars()) {
                    if (m.hasAction(menu)) {
                        m.replaceAction(menu);
                        Log.d(TAG,
                                "replacing existing tool: "
                                        + menu.toString()
                                        + " for " + m.toString());
                    } else {
                        ActionMenuData placeholder = m.findPlaceholder(menu);
                        if (placeholder != null) {
                            Log.d(TAG,
                                    "replacing a placeholder: "
                                            + menu.toString()
                                            + " to " + m.toString());
                            m.replaceAction(placeholder, menu);
                        } else {
                            Log.d(TAG,
                                    "Adding a new tool: "
                                            + menu.toString()
                                            + " to " + m.toString());
                            m.addLast(menu);
                            bSave = true;
                        }
                    }
                }
            } //end menu loop

            if (fixup(plugins, actionBars)) {
                bSave = true;
            }

            //save plugin data
            if (bSave) {
                actionBars.save();
            }

            latestPluginData = plugins;

            Log.d(TAG, "Invalidating options menu (add)");
            mActivity.invalidateOptionsMenu();
        }
    }

    /**
     * Remove any plugins no longer installed and/or loaded by the user
     * Promote from other menus to replace any action bar plugins that were removed
     *
     * @param plugins    the plugin menu data
     * @param actionBars the action bar data to compare to
     * @return true if any plugins were purged
     */
    private static boolean fixup(List<ActionMenuData> plugins,
            AtakActionBarListData actionBars) {
        Log.d(TAG, "Purging plugins...");
        boolean bChanged = false;

        //wrap a container for compare plugins against action bars
        AtakActionBarMenuData pluginContainer = new AtakActionBarMenuData();
        pluginContainer.setLabel("Plugin Container");
        pluginContainer
                .setOrientation(AtakActionBarMenuData.Orientation.landscape
                        .toString());
        pluginContainer.add(plugins);

        //loop all action bars' action
        for (AtakActionBarMenuData actionBar : actionBars.getActionBars()) {
            //loop all action for this action bar
            for (ActionMenuData action : new ArrayList<>(
                    actionBar.getActions())) {
                //now see if any plugins need to be removed
                if (!action.isBaseline()
                        && !pluginContainer.hasAction(action)) {
                    //                    toRemove.add(action);
                    bChanged = true;
                    if (action.getPreferredMenu().equals(
                            PreferredMenu.actionBar)) {
                        //                        removedFromActionBar++;
                        actionBar.replaceAction(action,
                                ActionMenuData.createPlaceholder(
                                        String.valueOf(action.getId())));
                    } else {
                        actionBar.remove(action);

                    }
                }
            }

            //now purge this actionBar
            //            for (ActionMenuData t : toRemove) {
            //                Log.d(TAG,
            //                        "Removing: " + t.toString() + ", from "
            //                                + actionBar.toString());
            //                actionBar.remove(t);
            //            }

            //            if (removedFromActionBar > 0) {
            //                int promoted = 0;
            //                Log.d(TAG, "Promoting: " + removedFromActionBar
            //                        + ", items to action bar: " + actionBar.toString());
            //
            //                int i = 0;
            //                while (i < removedFromActionBar
            //                        && promoted < removedFromActionBar) {
            //                    for (ActionMenuData action : actionBar.getActions()) {
            //                        //now see if any plugins need to be removed
            //                        if (!action.getPreferredMenu().equals(
            //                                PreferredMenu.actionBar)) {
            //                            Log.d(TAG,
            //                                    "Promoting: " + action
            //                                            + ", items to action bar: "
            //                                            + actionBar.toString());
            //                            action.setPreferredMenu(PreferredMenu.actionBar);
            //                            promoted++;
            //
            //                            if (promoted >= removedFromActionBar) {
            //                                break;
            //                            }
            //                        }
            //                    }
            //
            //                    i++;
            //                }
            //
            //                Log.d(TAG, "Promoted: " + promoted + ", items to action bar: "
            //                        + actionBar.toString());
            //            }
        } //end actionBar loop

        return bChanged;
    }

    public void dispose() {
        mActivity = null;
        mActionBars = null;
    }

    /**
     * Populate the action bar menu with the specified action bar layout
     *
     * @param actionBar
     * @param context
     * @param menu
     * @param actionBarListener
     * @param actionBarLongClickListener Long click listener for view
     * @return
     */
    public boolean onPrepareOptionsMenu(AtakActionBarMenuData actionBar,
            Context context,
            Menu menu,
            OnClickListener actionBarListener,
            OnLongClickListener actionBarLongClickListener) {

        boolean bLargeIcons = _prefs.getBoolean("largeActionBar", false);

        //cache the current state
        final ActionBarView activeToolView = _activeToolView;

        if (actionBar == null || !actionBar.isValid()) {
            return false;
        }

        menu.clear();
        int itemCount = 0;
        int actionCount = 0;

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int widthRemaining = Math
                .min(metrics.widthPixels, metrics.heightPixels);

        Orientation orientation = AtakActionBarListData
                .getOrientation(mActivity);

        if (orientation == Orientation.landscape) {
            int widthPixels = metrics.widthPixels;
            // Essential PH-1 has a hidden 144px border on the left side that
            // gets included in display metrics despite being off screen
            if (android.os.Build.MODEL.equals("PH-1")) {
                widthPixels -= 144;
            }
            widthRemaining = Math.max(widthPixels, metrics.heightPixels);
        }

        //Log.d(TAG, "Initial width (pixels)=" + widthRemaining);

        // take out room for overflow menu
        try {
            // Note, this is not always exactly correct since our ic_action_overflow drawable is
            // typically wider than the icon/view provided by Android for the overflow. But if
            // anything it gives us a little extra buffer, so not a big issue at the moment

            if (overflowView == null) {
                Drawable overflow = context.getResources().getDrawable(
                        R.drawable.ic_action_overflow);
                overflowView = new ImageView(context);
                overflowView.setImageDrawable(overflow);
                overflowView.measure(ActionBarReceiver.QuerySpec,
                        ActionBarReceiver.QuerySpec);
            }
            widthRemaining -= overflowView.getMeasuredWidth();
            //Log.d(TAG, "Width remaining after overflow: " + overflowView.getMeasuredWidth() + " is " + widthRemaining);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load action overflow icon", e);
            widthRemaining -= 64;
        }

        // see if we need to add active tool view
        actionBarDrawer.removeAllViews();
        if (activeToolView != null) {
            try {
                //scale size if configured
                if (bLargeIcons) {
                    activeToolView.setScaleX(SCALE_FACTOR);
                    activeToolView.setScaleY(SCALE_FACTOR);
                    activeToolView.setPivotX(0);
                    activeToolView.setPivotY(0);
                } else {
                    activeToolView.setScaleX(1.0f);
                    activeToolView.setScaleY(1.0f);
                    activeToolView.setPivotX(0);
                    activeToolView.setPivotY(0);
                }

                //add the close option to the subtool view if it doesnt already have one
                if (activeToolView.showCloseButton() && activeToolView
                        .findViewById(R.id.close) == null) {
                    LayoutInflater inflater = LayoutInflater.from(MapView
                            .getMapView().getContext());
                    ImageButton closeBtn = (ImageButton) inflater.inflate(
                            R.layout.toolbar_close_btn, null);
                    activeToolView.addView(closeBtn);
                    closeBtn.setOnClickListener(activeToolCloseListener);
                }

                // Remove action tool view from any other parent
                if (activeToolView.getParent() != null) {
                    ((ViewGroup) activeToolView.getParent())
                            .removeView(activeToolView);
                }

                int embedState = activeToolView.getEmbedState();
                if (embedState == ActionBarView.EMBEDDED
                        || embedState == ActionBarView.FULLSIZE) {
                    //get measurements of active tool view
                    activeToolView.measure(ActionBarReceiver.QuerySpec,
                            ActionBarReceiver.QuerySpec);
                    int activeToolWidth = activeToolView.getMeasuredWidth();

                    int myWidth = activeToolWidth;
                    //                //wrap & measure separator
                    ImageView sepView = new ImageView(context);
                    sepView.setImageResource(R.drawable.thin_gray_line);
                    sepView.setScaleType(ImageView.ScaleType.FIT_XY);
                    sepView.setLayoutParams(new LinearLayout.LayoutParams(3,
                            MapView.getMapView().getActionBarHeight()));

                    sepView.measure(ActionBarReceiver.QuerySpec,
                            ActionBarReceiver.QuerySpec);
                    activeToolWidth += sepView.getMeasuredWidth();
                    //Log.d(TAG, "Width for separator: " + sepView.getMeasuredWidth());

                    if (widthRemaining < activeToolWidth) {
                        myWidth = widthRemaining -
                                (sepView.getMeasuredWidth());
                    }

                    //wrap & measure close button
                    widthRemaining -= activeToolWidth;
                    Log.d(TAG, "Adding active tool view: " + activeToolView
                            + ", with width: " + activeToolWidth
                            + ", remaining: "
                            + widthRemaining);

                    //add toolview at lower priority so they display after the tool menus
                    //which we will add based on available space and user layout

                    //add separator view

                    MenuItem menuItem;
                    if (embedState == ActionBarView.EMBEDDED) {
                        menuItem = menu.add(Menu.NONE,
                                "Separator".hashCode(), 100,
                                "Separator");
                        menuItem.setActionView(sepView);
                        menuItem.setShowAsAction(
                                MenuItem.SHOW_AS_ACTION_ALWAYS);
                    }

                    //add active tool view
                    menuItem = menu.add(Menu.NONE, "Toolbar".hashCode(), 101,
                            "Toolbar");

                    final int fWidth = myWidth;
                    final int fHeight = MapView.getMapView()
                            .getActionBarHeight();

                    final HorizontalScrollView sv = new HorizontalScrollView(
                            context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec,
                                int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec,
                                    heightMeasureSpec);
                            setMeasuredDimension(fWidth, fHeight - 20);
                        }
                    };
                    //sv.setBackgroundColor(Color.BLUE);
                    //sv.setPadding(0,0,0,0);

                    sv.setLayoutParams(new HorizontalScrollView.LayoutParams(
                            fWidth,
                            fHeight - 20));

                    if (activeToolView.getParent() != null) {
                        ((ViewGroup) activeToolView.getParent())
                                .removeView(activeToolView);
                    }
                    //activeToolView.setBackgroundColor(Color.RED);  // red on blue for debuging
                    sv.setBackgroundColor(ACTIVE_TOOLBAR_BACKGROUND);
                    sv.addView(activeToolView);

                    menuItem.setActionView(sv);

                    menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                } else {
                    // Not embedded in action bar - show below it
                    actionBarDrawer.addView(activeToolView, 0);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load active tool view", e);
            }
        }

        // Log.d(TAG, "Width after overflow=" + widthRemaining);

        int hiddenCount = actionBar.getActions(PreferredMenu.hidden).size();

        // Refresh icon cache whenever the action bar height changes
        int actionBarHeight = ((ATAKActivity) mActivity).getActionBarHeight();
        if (actionBarHeight != _lastActionBarHeight) {
            Log.d(TAG, "Action bar height changed from " + _lastActionBarHeight
                    + " to " + actionBarHeight + ", refreshing icon cache...");
            regularIconCache.clear();
            largeIconCache.clear();
            _lastActionBarHeight = actionBarHeight;
        }

        // Move action view drawer below action bar
        refreshFloatingActionBar();

        // now loop all configured menu items
        if (activeToolView == null
                || activeToolView.getEmbedState() != ActionBarView.FULLSIZE) {
            for (ActionMenuData action : actionBar.getActions()) {
                PreferredMenu preferredMenu = action.getPreferredMenu();
                if (preferredMenu == PreferredMenu.hidden) {
                    continue;
                }

                // Log.d(TAG, "Placing action=" + action.toString());
                int showAs = action
                        .getPreferredMenu() == PreferredMenu.actionBar
                                ? MenuItem.SHOW_AS_ACTION_ALWAYS
                                : MenuItem.SHOW_AS_ACTION_NEVER;

                ImageView actionView;
                Drawable icon;
                try {
                    // attempt to create image view for tool icon
                    icon = action.getIcon(context);
                    if (icon == null && !action.isBaseline()) {
                        //use placeholder icon until the plugin has finished loading
                        Log.w(TAG,
                                "Failed to load plugin icon: "
                                        + action.getIcon());
                        icon = mActivity.getResources().getDrawable(
                                R.drawable.ic_menu_tools);
                    }
                    icon = getScaledIcon(context, bLargeIcons, icon);

                    //see if the icon is a layer drawable we need to create a version to not interfere with
                    // the icon that is stored in the layer drawable because we reference that layer in other ATAk components
                    PorterDuffColorFilter filter = new PorterDuffColorFilter(
                            getUserIconColor(), PorterDuff.Mode.MULTIPLY);
                    if (icon instanceof LayerDrawable) {
                        // get current icon's badge number
                        AtakLayerDrawableUtil inst = AtakLayerDrawableUtil
                                .getInstance(context);
                        int badgeInt = inst.getBadgeInt(action.getIcon());
                        LayerDrawable iconLayers = (LayerDrawable) icon;
                        List<Drawable> layers = new ArrayList<>();
                        for (int i = 0; i < iconLayers
                                .getNumberOfLayers(); i++) {
                            int id = iconLayers.getId(i);
                            Drawable dr = getScaledIcon(context, bLargeIcons,
                                    iconLayers.getDrawable(i), false);
                            // don't modify badge color
                            if (id != R.id.ic_badge) {
                                //drawable.mutate(); //only change settings and keep settings on this drawable only
                                dr.setColorFilter(filter);
                            } else if (badgeInt == 0)
                            // skip badge if number is 0 (not drawn)
                            {
                                continue;
                            }
                            layers.add(dr);
                        }
                        icon = new LayerDrawable(layers.toArray(
                                new Drawable[0]));
                    } else if (icon != null) {
                        icon.setColorFilter(filter);
                    }

                    actionView = new ImageView(context);
                    //int size = MapView.getMapView().getActionBarHeight();
                    // debug to fix the size of the icons - tracking down why the number of icons
                    // changes when going out and into ATAK by way of the quick pic.
                    // when this is set, it will keep the icons a fixed size and force the icons off
                    // the screen
                    //actionView.setLayoutParams(new LinearLayout.LayoutParams( (int) (size / 1.1), (int) (size / 1.1)));
                    actionView.setImageDrawable(icon);
                    setPadding(actionView, metrics);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load icon: " + action.getIcon(), e);
                    showAs = MenuItem.SHOW_AS_ACTION_NEVER;
                    actionView = null;
                    icon = mActivity.getResources().getDrawable(
                            R.drawable.ic_menu_tools);
                }

                // create action bar menu item for this tool
                ++itemCount;
                MenuItem menuItem = menu.add(Menu.NONE, action.getId(),
                        itemCount,
                        action.getTitle());
                if (actionView != null && icon != null) {
                    // see if user wants this in action bar (vs overflow)
                    if (showAs == MenuItem.SHOW_AS_ACTION_ALWAYS) {
                        // see if room left in action bar
                        actionView.measure(ActionBarReceiver.QuerySpec,
                                ActionBarReceiver.QuerySpec);
                        int actionWidth = actionView.getMeasuredWidth()
                                + ((nwTimSortFix) ? 10 : 0);

                        if (widthRemaining >= actionWidth) {
                            widthRemaining -= actionWidth;
                            menuItem.setActionView(actionView);

                            actionCount++;
                            Log.d(TAG,
                                    "Adding action: " + action.toString()
                                            + " with size: " + actionWidth
                                            + ", remaining: " + widthRemaining);
                        } else {
                            // no room in action bar, just set icon and let Android/SDK
                            // handle the default view for overflow menu
                            if (action.isPlaceholder()) {
                                // Action bar fixup should have handled this...
                                Log.w(TAG,
                                        "Not overflowing placeholder action");
                                //                                menu.removeItem(menuItem.getItemId());
                                Log.w(TAG, "wtf:" + menuItem.getItemId() + ","
                                        + action.toString());
                                menu.removeItem(action.getId());
                                continue;
                            }

                            Log.d(TAG,
                                    "All full, overflowing action="
                                            + action.toString() + " of size: "
                                            + actionWidth + ", remaining "
                                            + widthRemaining);
                            showAs = MenuItem.SHOW_AS_ACTION_NEVER;
                            menuItem.setIcon(icon);
                        }
                    } else {
                        // headed for overflow, either by configuration, or failed to load icon
                        Log.d(TAG, "Adding overflow: " + action.toString());
                        showAs = MenuItem.SHOW_AS_ACTION_NEVER;
                        menuItem.setIcon(icon);
                    }
                } else {
                    // no icon, headed for overflow
                    Log.d(TAG,
                            "Adding overflow w/no icon: " + action.toString());
                    showAs = MenuItem.SHOW_AS_ACTION_NEVER;
                    menuItem.setIcon(icon);
                }
                menuItem.setShowAsAction(showAs);
                View view = menuItem.getActionView();
                if (view != null) {
                    view.setOnClickListener(actionBarListener);
                    view.setOnLongClickListener(actionBarLongClickListener);
                    view.setTag(R.string.actionbar_TITLE_TAG,
                            action.getTitle());
                    // set Item ID as tag to look up in onClick listener
                    view.setTag(menuItem.getItemId());
                }
                Log.d(TAG, "" + itemCount + " Width after action="
                        + widthRemaining);
            } // end action loop
        }
        applyColorToOverflow();
        Log.d(TAG, actionBar.toString() + ". Added " + actionCount
                + " actions, " +
                (actionBar.getActions().size() - actionCount - hiddenCount)
                + " overflow, and " +
                hiddenCount + " hidden");
        return true;
    }

    /**
     * get the drawable used as the overflow icon see styles.xml
     * change color of drawable in order to bleed color to already
     * referenced  overflow image in action bar -SA
     */
    private void applyColorToOverflow() {
        if (originalOverflowDrawable == null) {
            originalOverflowDrawable = mActivity.getResources().getDrawable(
                    R.drawable.ic_action_overflow);
        }
        if (originalOverflowDrawable == null) {
            return;
        }
        originalOverflowDrawable
                .setColorFilter(new PorterDuffColorFilter(getUserIconColor(),
                        PorterDuff.Mode.SRC_ATOP));
    }

    public static void setPadding(ImageView actionView,
            DisplayMetrics metrics) {
        int pxToDPconversion = metrics == null ? 1
                : Math
                        .round(metrics.densityDpi / 160f);

        actionView.setPadding(ActionItemPaddingLR,
                ActionItemPaddingTB * pxToDPconversion,
                ActionItemPaddingLR, ActionItemPaddingTB * pxToDPconversion);
    }

    static private final Map<Drawable, Drawable> regularIconCache = new HashMap<>();
    static private final Map<Drawable, Drawable> largeIconCache = new HashMap<>();

    public static Drawable getScaledIcon(Context context, boolean bLargeIcons,
            Drawable icon, boolean cache) {

        final Drawable orig = icon;

        if (bLargeIcons) {
            Drawable d = largeIconCache.get(orig);
            if (d != null) {
                //Log.d(TAG, "large scale cache hit");
                return d;
            }
        } else {
            Drawable d = regularIconCache.get(orig);
            if (d != null) {
                //Log.d(TAG, "regular scale cache hit");
                return d;
            }
        }

        ATAKActivity actA = (ATAKActivity) mActivity;
        int height = (int) (actA.getActionBarHeight() / 1.6f);

        if (height != 0 && icon instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) icon).getBitmap();
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, height,
                    height, true);
            BitmapDrawable bd = new BitmapDrawable(context.getResources(),
                    scaledBitmap);

            // TODO - figure out why the BitmapDrawable is currently trying to render at a
            // targetDensity and ends up scaling up the bitmap which is only supposed to be
            // 75-90x75-90
            bd.setTargetDensity(scaledBitmap.getDensity());

            icon = bd;

            // do not cache icons with badges
            if (cache && !(orig instanceof LayerDrawable)) {
                regularIconCache.put(orig, icon);
            }
        }

        if (!bLargeIcons || icon == null) {
            return icon;
        }

        if (icon instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) icon).getBitmap();
            int size = bitmap.getWidth();
            //match the action bar height scaling done by ATAKActivity
            int scaledsize = Math.round(((float) size) * SCALE_FACTOR);
            if (scaledsize > size) {
                icon = new BitmapDrawable(context.getResources(),
                        Bitmap.createScaledBitmap(bitmap, scaledsize,
                                scaledsize,
                                true));
            }
            // do not cache icons with badges
            if (cache && !(orig instanceof LayerDrawable)) {
                largeIconCache.put(orig, icon);
            }
        }
        return icon;
    }

    public static Drawable getScaledIcon(Context context, boolean bLargeIcons,
            Drawable icon) {
        return getScaledIcon(context, bLargeIcons, icon, true);
    }

    /**
     * Grabs the user defined string color from settings
     * See: CustomActionBarFragment
     *
     * @return int Color object
     */
    public static int getUserIconColor() {
        int color = 0;
        if (_prefs != null) {
            color = Color.parseColor(_prefs.getString(
                    CustomActionBarFragment.ACTIONBAR_ICON_COLOR_KEY,
                    "#FFFFFF"));
        }

        if (color == 0) {
            return Color.WHITE;
        }
        return color;
    }

    /**
     * Finds the selected saved color value from the preferences
     * parses the color and apply a 99% alpha to allow for transparency under the action bar
     * if parsing fails , default original action bar is used
     *
     * @return int Color to be used for the action bar
     */
    public int getActionBarColor() {

        int color = 0;
        if (_prefs != null) {
            color = Color.parseColor(_prefs.getString(
                    CustomActionBarFragment.ACTIONBAR_BACKGROUND_COLOR_KEY,
                    "#99000000"));
        }

        if (color != 0) {
            //apply the alpha value so map items can be see under the actionbar
            int red = Color.red(color);
            int green = Color.green(color);
            int blue = Color.blue(color);
            return Color.argb(99, red, green, blue);
        }

        Log.d(TAG,
                "Defaulting to original color parsing failed on action bar color");
        return Color.argb(99, 0, 0, 0);
    }

    /**
     * Calculate how many actions will fit the in the action bar on this device. Assumes icons for
     * all actions are the same size (as the "Placeholder" icon)
     *
     * @param context the application context
     * @return the max number of actions that can fit in the action bar.
     */
    public static int calculateMax(final Context context) {
        boolean bLargeIcons = false;

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int widthRemaining = metrics.widthPixels;
        // Log.d(TAG, "Initial width (pixels)=" + widthRemaining);

        // take out room for overflow menu
        try {
            // Note, this is not always exactly correct since our ic_action_overflow drawable is
            // typically wider than the icon/view provided by Android for the overflow. But if
            // anything it gives us a little extra buffer, so not a big issue at the moment
            if (overflowView == null) {
                Drawable overflow = context.getResources().getDrawable(
                        R.drawable.ic_action_overflow);
                overflowView = new ImageView(context);
                overflowView.setImageDrawable(overflow);
                overflowView.measure(ActionBarReceiver.QuerySpec,
                        ActionBarReceiver.QuerySpec);
            }
            widthRemaining -= overflowView.getMeasuredWidth();
        } catch (Exception e) {
            Log.e(TAG, "Failed to load action overflow icon", e);
            widthRemaining -= 64;
        }

        // get size of each placeholder - assumption is this is same size as
        // a tool that would get added
        try {
            ActionMenuData placeholderAction = ActionMenuData
                    .createPlaceholder();
            Drawable placeholder = getScaledIcon(context, bLargeIcons,
                    placeholderAction.getIcon(context));
            ImageView placeholderView = new ImageView(context);
            placeholderView.setImageDrawable(placeholder);
            setPadding(placeholderView, null);
            placeholderView.measure(ActionBarReceiver.QuerySpec,
                    ActionBarReceiver.QuerySpec);
            int placeholderWidth = placeholderView.getMeasuredWidth();

            // now see how many placeholders we should add
            if (widthRemaining < placeholderWidth) {
                Log.w(TAG, "No room for any placeholders of size: "
                        + placeholderWidth);
                return 0;
            }

            double roomFor = ((double) widthRemaining
                    / (double) placeholderWidth);
            Log.d(TAG, "Max room for " + roomFor + " placeholders of width: "
                    + placeholderWidth);
            return (int) Math.floor(roomFor);
        } catch (Exception e) {
            Log.w(TAG, "Error calculating max action bar icons", e);
            return 0;
        }
    }

    public boolean isDisabled() {
        return _disableActionBar;
    }

    public String getDisabledMessage() {
        if (FileSystemUtils.isEmpty(_actionBarDisabledReason)) {
            _actionBarDisabledReason = DISABLE_ACTIONBAR_DEFAULT_REASON;
        }

        return _actionBarDisabledReason;
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener _prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(
                SharedPreferences sharedPreferences, String key) {
            if (key.equals("atakTapToggleActionBar")) {
                setPrefs();

                if (!atakTapToggleActionBar) {
                    //single tap was disable, be sure action bar is visible
                    if (mActivity != null
                            && mActivity.getActionBar() != null) {
                        mActivity.getActionBar().show();
                    }
                }
            } else if (key.equals("largeActionBar")) {
                regularIconCache.clear();
                largeIconCache.clear();
            } else if (key
                    .equalsIgnoreCase(
                            CustomActionBarFragment.ACTIONBAR_BACKGROUND_COLOR_KEY)) {
                Log.d(TAG, "Change in AB BG Color");
                AtakBroadcast.getInstance().sendBroadcast(
                        new Intent(RELOAD_ACTION_BAR));
            } else if (key
                    .equals(CustomActionBarFragment.ACTIONBAR_ICON_COLOR_KEY)) {
                Log.d(TAG, "Change in Icon Color");
                AtakBroadcast.getInstance().sendBroadcast(
                        new Intent(RELOAD_ACTION_BAR));
            }
        }
    };

    private void setPrefs() {
        atakTapToggleActionBar = _prefs.getBoolean("atakTapToggleActionBar",
                true);
    }

    /**
     * Refresh the position of the floating toolbar
     */
    private void refreshFloatingActionBar() {
        if (mActivity == null) {
            return;
        }
        float dp = mActivity.getResources().getDisplayMetrics().density;
        if ((showFloatingActionBar || (_activeToolView != null
                && !_activeToolView.isClosable()))
                && actionBarDrawer.getChildCount() > 0) {
            // Floating toolbar showing
            ActionBar ab = mActivity.getActionBar();
            final int abHeight = ab != null && ab.isShowing()
                    ? ab.getHeight()
                    : 0;
            setTopMargin(actionBarDrawer, abHeight);
            actionBarDrawer.setVisibility(View.VISIBLE);
            GradientDrawable bg = (GradientDrawable) mActivity.getResources()
                    .getDrawable(R.drawable.floating_toolbar_rounded);
            bg.setColorFilter(getActionBarColor(), PorterDuff.Mode.MULTIPLY);
            actionBarDrawer.setBackground(bg);
            actionBarDrawer.measure(ActionBarReceiver.QuerySpec,
                    ActionBarReceiver.QuerySpec);
            int topMargin = abHeight + actionBarDrawer
                    .getMeasuredHeight() - (int) (4 * dp);
            setTopMargin(actionBarHandle, topMargin);
        } else {
            // Floating toolbar not showing
            actionBarDrawer.setVisibility(View.GONE);
            setTopMargin(actionBarHandle, (int) (-4 * dp));
        }
    }

    private static void setTopMargin(View v, int margin) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (!(lp instanceof RelativeLayout.LayoutParams)) {
            lp = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        ((RelativeLayout.LayoutParams) lp).topMargin = margin;
        v.setLayoutParams(lp);
    }
}
