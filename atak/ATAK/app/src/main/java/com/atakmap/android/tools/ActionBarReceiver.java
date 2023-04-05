
package com.atakmap.android.tools;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.navigation.views.NavView;
import com.atakmap.android.navigation.views.buttons.NavButtonsVisibilityListener;
import com.atakmap.android.toolbar.ToolbarBroadcastReceiver;
import com.atakmap.android.tools.menu.ActionMenuData;
import com.atakmap.android.tools.menu.AtakActionBarListData;
import com.atakmap.android.widgets.LayoutHelper;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;
import com.atakmap.app.preferences.CustomActionBarFragment;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class ActionBarReceiver extends BroadcastReceiver implements
        NavButtonsVisibilityListener {

    private static final String TAG = "ActionBarReceiver";

    public static String SETTINGS_TOOL = "Settings";
    public static String LAYOUT_MGR = "Toolbar Manager";
    public static String QUIT_TOOL = "Quit";

    public static final float SCALE_FACTOR = NavView.DEFAULT_SCALE_FACTOR;

    public final static int QuerySpec = View.MeasureSpec.makeMeasureSpec(0,
            View.MeasureSpec.UNSPECIFIED);

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

    protected static Activity mActivity;

    private static SharedPreferences _prefs;

    private static ActionBarReceiver _instance;

    // These are used by the top-right floating toolbar (NOT part of the action bar)
    private ActionBarView _activeToolView = null;
    private final LinearLayout toolbarDrawer;
    private final ViewGroup dropDownToolbar;
    private final View ddHandle;
    private RootLayoutWidget.OnLayoutChangedListener toolbarLayoutListener;
    private boolean overflowVisible;

    public ActionBarReceiver(Activity activity) {

        _instance = this;
        mActivity = activity;
        _prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);

        // Drawer displayed top-right under action bar
        toolbarDrawer = mActivity.findViewById(R.id.toolbar_drawer);

        // Toolbar displayed above the right-side drop-down
        dropDownToolbar = mActivity.findViewById(R.id.embedded_toolbar);

        // Drop-down toolbar depends on overflow menu visibility
        DropDownManager.getInstance().addStateListener("overflow_menu",
                new DropDown.OnStateListener() {
                    @Override
                    public void onDropDownVisible(boolean v) {
                        overflowVisible = v;
                        refreshToolbar();
                    }

                    @Override
                    public void onDropDownSelectionRemoved() {
                    }

                    @Override
                    public void onDropDownClose() {
                    }

                    @Override
                    public void onDropDownSizeChanged(double width,
                            double height) {
                    }
                });

        // Landscape drop-down handle
        ddHandle = mActivity.findViewById(R.id.sidepanehandle_background);

        // Embedded toolbar tracks nav button visibility in certain situations
        NavView.getInstance().addButtonVisibilityListener(this);
    }

    /**
     * Ability to look up a menu item based on a name
     * @deprecated No longer used and will always return null - see {@link NavView}
     */
    @Deprecated
    @DeprecatedApi(since = "4.5", forRemoval = true, removeAt = "4.8")
    static public ActionMenuData getMenuItem(final String name) {
        return null;
    }

    /**
     * Get an instance of the receiver.
     */
    public static ActionBarReceiver getInstance() {
        return _instance;
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
        }
        refreshToolbar();
    }

    synchronized public void setToolView(final ActionBarView v) {
        setToolView(v, true);
    }

    synchronized public ActionBarView getToolView() {
        return _activeToolView;
    }

    public boolean hasActionBars() {
        return false;
    }

    public AtakActionBarListData getActionBars() {
        return null;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
    }

    public void dispose() {
        mActivity = null;
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

    @Deprecated
    @DeprecatedApi(since = "4.5", removeAt = "4.8", forRemoval = true)
    public static Drawable getScaledIcon(Context context, boolean bLargeIcons,
            Drawable icon, boolean cache) {
        return null;
    }

    @Deprecated
    @DeprecatedApi(since = "4.5", removeAt = "4.8", forRemoval = true)
    public boolean isDisabled() {
        return false;
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

    private void refreshToolbar() {

        MapView mv = MapView.getMapView();
        if (mv == null)
            return;

        // Lazy initialize root layout listener
        // Any time the root layout changes we need to recalculate
        // the toolbar position
        if (toolbarLayoutListener == null) {
            RootLayoutWidget root = (RootLayoutWidget) mv
                    .getComponentExtra("rootLayoutWidget");
            if (root != null) {
                toolbarLayoutListener = new RootLayoutWidget.OnLayoutChangedListener() {
                    @Override
                    public void onLayoutChanged() {
                        refreshToolbar();
                    }
                };
                root.addOnLayoutChangedListener(toolbarLayoutListener);
            }
        }

        // No toolbar - hide everything
        if (_activeToolView == null) {
            hideToolbar();
            return;
        }

        // The embedded toolbar is shown if:
        // 1) The right-drop down is visible
        // 2) The overflow menu is NOT visible
        // 3) The top tool buttons ARE visible
        boolean embedded = _activeToolView
                .getEmbedState() == ActionBarView.EMBEDDED;
        boolean ddVisible = mv.getWidth() < NavView.getInstance().getWidth();
        boolean buttonsVisible = NavView.getInstance().buttonsVisible();
        if (embedded && ddVisible && buttonsVisible) {
            if (!overflowVisible)
                refreshDropDownToolbar();
            else
                hideToolbar();
        } else
            refreshFloatingToolbar();
    }

    /**
     * Hide all toolbar views
     */
    private void hideToolbar() {
        toolbarDrawer.setVisibility(View.GONE);
        toolbarDrawer.removeAllViews();
        dropDownToolbar.removeAllViews();
    }

    /**
     * Refresh the display for the drop-down toolbar
     */
    private void refreshDropDownToolbar() {
        toolbarDrawer.removeAllViews();
        addToolbarView(dropDownToolbar, _activeToolView);

        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) dropDownToolbar
                .getLayoutParams();

        // Set the height of the toolbar to match the nav bar
        float size = toolbarDrawer.getResources().getDimension(
                R.dimen.nav_button_size);
        lp.height = (int) (NavView.getInstance().getUserIconScale() * size);

        // Align the embedded toolbar against the menu button or right side
        // of the screen
        boolean navRight = _prefs.getBoolean(
                NavView.PREF_NAV_ORIENTATION_RIGHT, true);
        if (navRight) {
            lp.removeRule(RelativeLayout.ALIGN_PARENT_END);
            lp.addRule(RelativeLayout.START_OF, R.id.tak_nav_menu_button);
        } else {
            lp.addRule(RelativeLayout.ALIGN_PARENT_END);
            lp.removeRule(RelativeLayout.START_OF);
        }
        dropDownToolbar.setLayoutParams(lp);
    }

    /**
     * Refresh the position of the floating toolbar
     */
    private void refreshFloatingToolbar() {
        MapView mv = MapView.getMapView();
        if (mActivity == null || mv == null)
            return;

        // Add the tool view to the drawer
        dropDownToolbar.removeAllViews();
        addToolbarView(toolbarDrawer, _activeToolView);

        // Set margin so we're not overlapping the drop-down handle
        final Rect abb = getToolbarBounds();
        boolean floating = abb.right < mv.getWidth();

        // Use completely or semi-rounded background depending on the margin
        int bgColor = NavView.getInstance().getUserIconShadowColor();
        Drawable bg = mActivity.getDrawable(floating
                ? R.drawable.toolbar_container_rounded
                : R.drawable.toolbar_container_half_rounded).mutate();
        bg.setColorFilter(new PorterDuffColorFilter(bgColor,
                PorterDuff.Mode.MULTIPLY));
        toolbarDrawer.setBackground(bg);
        toolbarDrawer.setVisibility(View.VISIBLE);

        // XXX - Need to post the top margin call or else it doesn't
        // work for some reason
        mv.post(new Runnable() {
            @Override
            public void run() {
                setPosition(toolbarDrawer, abb);
            }
        });
    }

    /**
     * Helper method to add a toolbar to a container
     * @param container Container view
     * @param view Toolbar view
     */
    private void addToolbarView(ViewGroup container, ActionBarView view) {
        View child = container.getChildAt(0);
        if (child == view)
            return;

        container.removeAllViews();
        ViewParent parent = view.getParent();
        if (parent instanceof ViewGroup)
            ((ViewGroup) parent).removeView(view);
        container.addView(view);

        // Add the close button if needed
        if (view.showCloseButton() && view.findViewById(R.id.close) == null) {
            LayoutInflater inflater = LayoutInflater
                    .from(container.getContext());
            ImageButton closeBtn = (ImageButton) inflater.inflate(
                    R.layout.toolbar_close_btn, container, false);
            container.addView(closeBtn);
            closeBtn.setOnClickListener(activeToolCloseListener);
        }
    }

    private static void setPosition(View v, Rect rect) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) lp)
                    .setMargins(rect.left, rect.top, 0, 0);
            v.setLayoutParams(lp);
        }
    }

    /**
     * Get the bounds for each of the {@link NavView} views
     * @return List of rectangle bounds
     */
    private List<Rect> getNavViewBounds() {
        NavView navView = NavView.getInstance();
        List<Rect> rects = new ArrayList<>(navView.getChildCount());
        for (int i = 0; i < navView.getChildCount(); i++)
            addBounds(navView.getChildAt(i), rects);
        addBounds(ddHandle, rects);
        return rects;
    }

    /**
     * Get the bounds of the top-right toolbar
     * @return Bounds rectangle
     */
    private Rect getToolbarBounds() {
        MapView mv = MapView.getMapView();
        if (mv == null)
            return new Rect();

        Rect mapBounds = new Rect(0, 0, mv.getWidth(), mv.getHeight());
        LayoutHelper lh = new LayoutHelper(mapBounds, getNavViewBounds());

        // Measure action bar bounds
        toolbarDrawer.measure(QuerySpec, QuerySpec);

        int aWidth = toolbarDrawer.getMeasuredWidth();
        int aHeight = toolbarDrawer.getMeasuredHeight();
        Rect aRect = new Rect(0, 0, aWidth, aHeight);

        return lh.findBestPosition(aRect, RootLayoutWidget.TOP_RIGHT);
    }

    private void addBounds(View view, List<Rect> rects) {
        if (view == null || view.getVisibility() != View.VISIBLE)
            return;
        rects.add(new Rect(view.getLeft(), view.getTop(),
                view.getRight(), view.getBottom()));
    }

    private final View.OnClickListener activeToolCloseListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (NavView.getInstance().buttonsLocked()) {
                String reason = NavView.getInstance().getButtonsLockedReason();
                Log.d(TAG, "Toolbar is locked: " + reason);
                Toast.makeText(mActivity, reason, Toast.LENGTH_LONG).show();
                return;
            }
            setToolView(null);
        }
    };

    @Override
    public void onNavButtonsVisible(boolean visible) {
        refreshToolbar();
    }
}
