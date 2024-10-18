
package com.atakmap.android.navigation.views;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.gui.DexSliderComponent;
import com.atakmap.android.gui.DynamicCompass;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.mapcompass.CompassArrowMapComponent;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapMode;
import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.navigation.NavButtonManager;
import com.atakmap.android.navigation.models.LoadoutItemModel;
import com.atakmap.android.navigation.models.NavButtonIntentAction;
import com.atakmap.android.navigation.models.NavButtonModel;
import com.atakmap.android.navigation.views.buttons.CompassButtonChildView;
import com.atakmap.android.navigation.views.buttons.NavButton;
import com.atakmap.android.navigation.views.buttons.NavButtonChildView;
import com.atakmap.android.navigation.views.buttons.NavButtonDrawable;
import com.atakmap.android.navigation.views.buttons.NavButtonsVisibilityListener;
import com.atakmap.android.navigation.views.buttons.NavZoomButton;
import com.atakmap.android.navigation.views.loadout.LoadoutListDropDown;
import com.atakmap.android.navigation.views.buttons.NavButtonShadowBuilder;
import com.atakmap.android.navigation.views.loadout.LoadoutManager;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.ToolbarBroadcastReceiver;
import com.atakmap.android.toolbar.tools.SpecifyLockItemTool;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.user.CamLockerReceiver;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.ATAKActivity;
import com.atakmap.app.DeveloperOptions;
import com.atakmap.app.R;
import com.atakmap.app.preferences.CustomActionBarFragment;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.CameraController;
import com.atakmap.map.projection.ECEFProjection;
import com.atakmap.map.projection.EquirectangularMapProjection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The main class for the top-left navigation and action buttons
 */
public class NavView extends RelativeLayout implements View.OnClickListener,
        View.OnLongClickListener, View.OnDragListener,
        SharedPreferences.OnSharedPreferenceChangeListener,
        AtakMapView.OnMapMovedListener,
        AtakMapView.OnMapViewResizedListener,
        DexSliderComponent.DexSliderListener,
        NavButtonManager.OnModelListChangedListener,
        NavButtonManager.OnModelChangedListener,
        LoadoutManager.OnLoadoutChangedListener,
        MapEventDispatcher.MapEventDispatchListener {

    private static final String TAG = "NavView";
    public static final String NAV_SELECTED_STATE = "com.atakmap.android.navigation.NAV_SELECTED_STATE";
    public static final String DELETE_TOOL = "com.atakmap.android.navigation.DELETE_TOOL";
    public static final String HIDE_TOOL = "com.atakmap.android.navigation.HIDE_TOOL";
    public static final String SHOW_TOOL = "com.atakmap.android.navigation.SHOW_TOOL";
    public static final String TOGGLE_BUTTONS = "com.atakmap.android.navigation.TOGGLE_BUTTONS";
    public static final String LOCK_BUTTONS = "com.atakmap.android.navigation.LOCK_BUTTONS";
    public static final String REFRESH_BUTTONS = "com.atakmap.android.navigation.REFRESH_BUTTONS";

    public static final String PREF_NAV_ORIENTATION_RIGHT = "nav_orientation_right";
    public static final String NAV_ID_KEY = "nav_id_key";
    public static final String NAV_SELECTED_KEY = "nav_selected_key";
    public static final String NAV_EDITING_KEY = "nav_editing_key";
    public static final String ZOOM_ENABLE_KEY = "map_zoom_visible";
    public static final String GLOBE_MODE_ENABLE_KEY = "atakGlobeModeEnabled";
    public static final String SHOW_MENU = "com.atakmap.android.maps.SHOW_MENU";

    // Drag and drop clip descriptions
    public static final String DRAG_ADD_TOOL = "nav_drag_add_tool";
    public static final String DRAG_MOVE_TOOL = "nav_drag_move_tool";

    public static final float DEFAULT_SCALE_FACTOR = 1.5f;

    private static NavView _instance;

    public static NavView getInstance() {
        return _instance;
    }

    private MapView _mapView;
    private final AtakPreferences _prefs = new AtakPreferences(getContext());
    private final NavButtonManager _buttonManager = new NavButtonManager(
            getContext());
    private final LoadoutManager _loadouts = new LoadoutManager(getContext());

    // The list of toolbar buttons at the top left of the screen
    private final List<NavButton> navButtons = new ArrayList<>();
    private final List<NavButton> topButtons = new ArrayList<>();

    // Views
    private LinearLayout sideLayout;
    private ImageButton lockOnButton;
    private NavZoomButton zoomButton;
    private DynamicCompass compass;
    private ImageView menuButton;
    private ViewGroup embeddedToolbar;
    private View deleteDragArea;
    private View tooltipView;
    private TextView tooltipTextView;
    private DexSliderComponent verticalSliderComponent;
    private DexSliderComponent horizontalSliderComponent;
    private NavButton buttonToDelete = null;
    private NavButtonChildView childView;
    private CompassButtonChildView compassButtonChildView;
    private boolean initialLayout = true;

    // Map states
    private MapMode _mapMode = MapMode.NORTH_UP;
    private double _restoreRotation, _lastRotation;
    private double _restoreTilt, _lastTilt;
    private boolean _rotationLocked, _tiltLocked;

    private boolean _portraitMode;
    private String _lockUID;
    private boolean _editingNav;
    private boolean _showDexControls;
    private Runnable _gpsLockCallback;

    // Preferred icon color and size
    private int userIconColor;
    private int userIconShadow;
    private float userIconScale;

    // Nav button visibility and locking
    private boolean buttonsVisible = true;
    private boolean buttonsLocked = false;
    private String buttonsLockedReason;

    // XXX - ViewGroup does not support more than one hierarchy changed listener
    // per view so we need to add support for multiple listeners here
    private final ConcurrentLinkedQueue<OnHierarchyChangeListener> hierarchyListeners = new ConcurrentLinkedQueue<>();

    // Listeners and handlers related to nav button visibility
    private final ConcurrentLinkedQueue<NavButtonsVisibilityListener> buttonVizListeners = new ConcurrentLinkedQueue<>();

    public NavView(Context context) {
        super(context);
        init();
    }

    public NavView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        if (isInEditMode())
            return;

        _instance = this;
        _prefs.registerListener(this);
        _showDexControls = _prefs.get("dexControls", false);

        NavSelectedReceiver receiver = new NavSelectedReceiver();
        DocumentedIntentFilter navSelectedIntentFilter = new DocumentedIntentFilter();
        navSelectedIntentFilter.addAction(NAV_SELECTED_STATE);
        AtakBroadcast.getInstance().registerReceiver(receiver,
                navSelectedIntentFilter);

        DocumentedIntentFilter editingNavFilter = new DocumentedIntentFilter();
        editingNavFilter.addAction(NAV_EDITING_KEY);
        AtakBroadcast.getInstance().registerReceiver(navEditReceiver,
                editingNavFilter);

        DocumentedIntentFilter editFilter = new DocumentedIntentFilter();
        editFilter.addAction(DELETE_TOOL);
        editFilter.addAction(HIDE_TOOL);
        editFilter.addAction(SHOW_TOOL);
        AtakBroadcast.getInstance().registerReceiver(navEditReceiver,
                editFilter);
        DocumentedIntentFilter intentFilter = new DocumentedIntentFilter();
        intentFilter.addAction(CamLockerReceiver.LOCK_CAM);
        intentFilter.addAction(CamLockerReceiver.UNLOCK_CAM);
        // Disable lock if another marker is selected
        intentFilter.addAction("com.atakmap.android.maps.SHOW_MENU");
        intentFilter.addAction(CamLockerReceiver.TOGGLE_LOCK);
        intentFilter.addAction(CamLockerReceiver.TOGGLE_LOCK_LONG_CLICK);
        AtakBroadcast.getInstance().registerReceiver(
                new LockBroadcastReceiver(),
                intentFilter);

        intentFilter = new DocumentedIntentFilter();
        intentFilter.addAction(CompassArrowMapComponent.TOGGLE_3D);
        intentFilter.addAction(CompassArrowMapComponent.LOCK_TILT);
        for (MapMode mode : MapMode.values())
            intentFilter.addAction(mode.getIntent());

        AtakBroadcast.getInstance().registerReceiver(orientationReceiver,
                intentFilter);

        intentFilter = new DocumentedIntentFilter();
        intentFilter.addAction(TOGGLE_BUTTONS);
        intentFilter.addAction(ActionBarReceiver.TOGGLE_ACTIONBAR);
        intentFilter.addAction(LOCK_BUTTONS);
        intentFilter.addAction(ActionBarReceiver.DISABLE_ACTIONBAR);
        intentFilter.addAction(REFRESH_BUTTONS);
        intentFilter.addAction(ActionBarReceiver.REFRESH_ACTION_BAR);
        AtakBroadcast.getInstance().registerReceiver(_buttonReceiver,
                intentFilter);

        // Support multiply hierarchy changed listeners
        setOnHierarchyChangeListener(new OnHierarchyChangeListener() {
            @Override
            public void onChildViewAdded(View parent, View child) {
                for (OnHierarchyChangeListener l : hierarchyListeners)
                    l.onChildViewAdded(parent, child);
            }

            @Override
            public void onChildViewRemoved(View parent, View child) {
                for (OnHierarchyChangeListener l : hierarchyListeners)
                    l.onChildViewRemoved(parent, child);
            }
        });
    }

    /**
     * Add a listener for children updates in this view
     * @param l Listener
     */
    public void addOnHierarchyChangedListener(OnHierarchyChangeListener l) {
        hierarchyListeners.add(l);
    }

    /**
     * Remove hierarchy listener
     * @param l Listener
     */
    public void removeOnHierarchyChangedListener(OnHierarchyChangeListener l) {
        hierarchyListeners.remove(l);
    }

    /**
     * Set the current compass map mode
     * @param mapMode Map mode action
     */
    public synchronized void setMapMode(MapMode mapMode) {
        if (_mapMode == mapMode)
            return;

        _mapMode = mapMode;

        // Set rotation value zero
        if (_mapMode == MapMode.NORTH_UP) {
            _restoreRotation = _mapView.getMapRotation();
            CameraController.Programmatic.rotateTo(_mapView.getRenderer3(),
                    0, true);
        } else if (_mapMode == MapMode.USER_DEFINED_UP) {
            CameraController.Programmatic.rotateTo(_mapView.getRenderer3(),
                    _restoreRotation, true);
            setRotationLocked(_rotationLocked);
        }

        // Turn off user-controller rotation when the mode isn't set
        if (_mapMode != MapMode.USER_DEFINED_UP)
            _mapView.getMapTouchController().setUserOrientation(false);

        // Store map mode preference
        _prefs.set("compass_mapmode", Integer.toString(_mapMode.getValue()));

        updateDexControls();
        updateCompassIcon();
    }

    /**
     * Get the current map mode.
     */
    public synchronized MapMode getMapMode() {
        return _mapMode;
    }

    /**
     * Set whether 3D tilt is enabled
     * @param tiltEnabled True to enable
     */
    public void setTiltEnabled(boolean tiltEnabled) {
        setTiltState(tiltEnabled ? MapTouchController.STATE_TILT_ENABLED
                : MapTouchController.STATE_TILT_DISABLED);
    }

    /**
     * Set whether DEX controls are enabled
     * Note: If the preference is set to true, controls will always be enabled
     * @param dexEnabled True if DEX controls enabled
     */
    public void setDexControlsEnabled(boolean dexEnabled) {
        _showDexControls = dexEnabled || _prefs.get("dexControls", false);
        updateDexControls();
    }

    /**
     * Set the gps lock symbol if the callback r is not null.
     * currently only meched out to allow for the location to receive this dismissal but
     * in the future attempt to do something else.
     * @param r The behavior to invoke when the GPS lock is dimissed.
     *
     */
    synchronized public void setGPSLockAction(final Runnable r) {
        _gpsLockCallback = r;
        updateCompassIcon();
    }

    /**
     * Get the current user icon color
     * @return User icon color integer
     */
    public int getUserIconColor() {
        return userIconColor;
    }

    /**
     * Get the current user icon shadow color
     * @return Icon color integer
     */
    public int getUserIconShadowColor() {
        return userIconShadow;
    }

    /**
     * Get the user icon scale value
     * @return Scale (1.0 to 2.0)
     */
    public float getUserIconScale() {
        return userIconScale;
    }

    /**
     * Set whether a give button should be selected
     * @param buttonId Button's reference ID
     * @param selected True to select
     */
    public void setButtonSelected(String buttonId, boolean selected) {
        NavButtonModel mdl = _buttonManager.getModelByReference(buttonId);
        if (mdl != null) {
            mdl.setSelected(selected);
            updateButton(mdl);
        }
    }

    /**
     * Update the button given its model
     * @param model Button model
     */
    public void updateButton(NavButtonModel model) {
        NavButtonManager.getInstance().notifyModelChanged(model);
    }

    /**
     * Get a nav button given its button model
     * @param model Button model
     * @return Nav button or null if not found
     */
    public NavButton findButtonByModel(NavButtonModel model) {
        for (NavButton button : navButtons) {
            if (button.getModel() == model)
                return button;
        }
        return null;
    }

    /***
     * Get a nav button given its toolbar action
     * @param toolbarId Toolbar identifier
     * @return Nav button or null if not found
     */
    public NavButton findToolbarButton(String toolbarId) {
        for (NavButton button : navButtons) {
            Intent intent = button.getModel().getActionIntent();
            if (intent != null && intent.hasExtra("toolbar")
                    && FileSystemUtils.isEquals(toolbarId,
                            intent.getStringExtra("toolbar")))
                return button;
        }
        return null;
    }

    /**
     * Toggle the visibility of the main nav buttons
     * @param visible True if visible
     */
    public void toggleButtons(boolean visible) {
        if (buttonsVisible != visible) {
            buttonsVisible = visible;
            updateButtonLayout();
            for (NavButtonsVisibilityListener l : buttonVizListeners)
                l.onNavButtonsVisible(visible);
        }
    }

    /**
     * Toggle the visibility of the main nav buttons
     */
    public void toggleButtons() {
        toggleButtons(!buttonsVisible);
    }

    /**
     * Check if the tool buttons are visible
     */
    public boolean buttonsVisible() {
        return buttonsVisible;
    }

    /**
     * Lock the tool buttons with a reason
     * @param locked True if locked
     * @param reason Reason (null to use a default)
     */
    public void lockButtons(boolean locked, String reason) {
        buttonsLocked = locked;
        buttonsLockedReason = reason;
        Log.d(TAG, "LOCK_BUTTONS: " + locked + ", " + reason);
    }

    /**
     * Check if the tool buttons are currently locked (no interaction permitted)
     * @return True if locked
     */
    public boolean buttonsLocked() {
        return buttonsLocked;
    }

    /**
     * Get the set reason the buttons are locked
     * @return Message
     */
    public String getButtonsLockedReason() {
        return buttonsLockedReason != null ? buttonsLockedReason
                : getContext().getString(R.string.tool_buttons_locked_msg);
    }

    /**
     * Add button visibility listener
     * @param l Listener
     */
    public void addButtonVisibilityListener(NavButtonsVisibilityListener l) {
        buttonVizListeners.add(l);
    }

    /**
     * Remove button visibility listener
     * @param l Listener
     */
    public void removeButtonVisibilityListener(NavButtonsVisibilityListener l) {
        buttonVizListeners.remove(l);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        if (changed) {
            if (_mapView == null) {
                _mapView = MapView.getMapView();
                _mapView.addOnMapMovedListener(this);
                _mapView.addOnMapViewResizedListener(this);
                _mapView.getMapEventDispatcher().addMapEventListener(this);
                _buttonManager.addModelListChangedListener(this);
                _buttonManager.addModelChangedListener(this);
                _loadouts.addListener(this);
            }
            _portraitMode = getContext().getResources()
                    .getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;

            if (initialLayout) {
                initButtons();
                initialLayout = false;
                setupMap();
            }
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        _portraitMode = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT;
        if (!initialLayout)
            updateDexControls();
    }

    private void initButtons() {
        embeddedToolbar = findViewById(R.id.embedded_toolbar);
        deleteDragArea = findViewById(R.id.tak_nav_delete_drag_area);
        deleteDragArea.setOnDragListener(this);
        tooltipView = findViewById(R.id.tak_nav_tooltip_area);
        tooltipTextView = findViewById(R.id.tak_nav_tooltip_text);

        // Layout that contains the dex slider controls
        ATAKActivity act = (ATAKActivity) getContext();
        horizontalSliderComponent = act.findViewById(R.id.horizontal_slider);
        horizontalSliderComponent.setDexSliderListener(this);
        verticalSliderComponent = act.findViewById(R.id.vertical_slider);
        verticalSliderComponent.setDexSliderListener(this);

        sideLayout = findViewById(R.id.side_layout);

        compass = findViewById(R.id.compass);
        compass.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (compassButtonChildView != null) {
                    removeCompassChildView();
                    return;
                }

                synchronized (this) {
                    // tapped to unlock the GPS lock
                    if (_gpsLockCallback != null) {
                        _gpsLockCallback.run();
                        _gpsLockCallback = null;
                        //compass.setMode(mapMode, lockedHeading);
                        updateCompassIcon();
                        return;
                    }
                }
                toggleTrackUp();
            }
        });

        compass.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (_editingNav)
                    return false;
                if (compassButtonChildView == null) {
                    // Make sure the top-left toolbar is hidden
                    AtakBroadcast.getInstance().sendBroadcast(new Intent(
                            ToolbarBroadcastReceiver.UNSET_TOOLBAR));
                    addCompassChildView();
                } else
                    removeCompassChildView();
                return true;
            }
        });

        lockOnButton = findViewById(R.id.tak_nav_center);
        lockOnButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(CamLockerReceiver.LOCK_CAM);
                if (!FileSystemUtils.isEmpty(_lockUID)) {
                    // Unlock the currently locked item
                    intent.putExtra("uid", _lockUID);
                } else {
                    // Lock to the self marker
                    Marker self = ATAKUtilities.findSelf(_mapView);
                    if (self == null) {
                        // Toast that tells the user to wait for GPS lock
                        Toast.makeText(_mapView.getContext(),
                                R.string.bloodhound_tip2,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    intent.putExtra("uid", self.getUID());
                }

                // Close radial menu
                AtakBroadcast.getInstance().sendBroadcast(
                        new Intent(MapMenuReceiver.HIDE_MENU));

                // Close coordinate overlay
                AtakBroadcast.getInstance().sendBroadcast(new Intent(
                        "com.atakmap.android.maps.HIDE_DETAILS"));

                // Unfocus (stops map from auto-panning to marker)
                AtakBroadcast.getInstance().sendBroadcast(new Intent(
                        "com.atakmap.android.maps.UNFOCUS"));

                AtakBroadcast.getInstance().sendBroadcast(intent);
            }
        });

        zoomButton = findViewById(R.id.tak_nav_zoom);
        zoomButton.setOnClickListener(zoomButton);

        menuButton = findViewById(R.id.tak_nav_menu_button);

        this.menuButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                // Make sure the menu is showing when we open the loadout editor
                AtakBroadcast.getInstance().sendBroadcast(
                        new Intent(LoadoutListDropDown.TOGGLE_LOADOUT));
            }
        });

        menuButton.setOnLongClickListener(new OnLongClickListener() {
            public boolean onLongClick(View v) {
                toggleButtons();
                return true;
            }
        });

        // Update button layout if the toolbar position changes
        embeddedToolbar.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b,
                    int ol, int ot, int or, int ob) {
                if (l != ol)
                    updateButtonLayout();
            }
        });

        // Initialize and update all views
        initLoadoutButtons();
        onUserIconScaleChanged();
        onUserIconColorChanged();
        updateButtonLoadout();
    }

    /**
     * Get the maximum number of buttons to allow on a device based on its
     * screen size
     * @return Maximum button count
     */
    private int getMaxButtons() {
        Resources r = getResources();
        DisplayMetrics dm = r.getDisplayMetrics();
        int btnSize = r.getDimensionPixelSize(R.dimen.nav_button_size)
                + r.getDimensionPixelSize(R.dimen.padding_small);
        int minSize = Math.min(dm.widthPixels, dm.heightPixels);
        int maxSize = Math.max(dm.widthPixels, dm.heightPixels);
        int sizeAvailable = Math.min(minSize, maxSize / 2);
        int numButtons = (sizeAvailable / btnSize) - 1;
        return Math.max(6, numButtons);
    }

    /**
     * Initialize the tool button layout
     */
    private void initLoadoutButtons() {
        navButtons.clear();
        topButtons.clear();
        Resources r = getResources();
        int size = r.getDimensionPixelSize(R.dimen.nav_button_size);

        int numButtons = getMaxButtons();
        for (int i = 0; i < numButtons; i++) {
            // Remove existing placeholder buttons
            String id = "tak_nav_button_" + i;
            NavButton existing = findViewById(r.getIdentifier(id, "id",
                    ATAKConstants.getPackageName()));
            if (existing != null)
                removeView(existing);

            // Add the new buttons
            NavButton button = new NavButton(getContext());
            button.setPadding(0, 0, 0, 0);
            button.setLayoutParams(new LayoutParams(size, size));
            button.setTag(ATAKConstants.getPackageName() + ":id/" + id);
            button.setOnClickListener(this);
            addView(button);
            topButtons.add(button);
            navButtons.add(button);
        }

        // Include the zoom button
        navButtons.add(zoomButton);

        // Setup long-press and drag listeners
        for (NavButton btn : navButtons) {
            btn.setOnLongClickListener(this);
            btn.setOnDragListener(btn);
        }
    }

    /**
     * Updates the top-left loadout display
     */
    private void updateButtonLayout() {

        float iconScale = 1.5f;
        if (!_prefs.get("largeActionBar", false))
            iconScale = 1.0f;

        final int size = (int) (getResources().getDimension(
                R.dimen.nav_button_size) * iconScale);
        int zoomHeight = (int) (getResources().getDimension(
                R.dimen.nav_zoom_button_height) * iconScale);
        int compassHeight = (int) (getResources().getDimension(
                R.dimen.dynamic_compass_height) * iconScale);

        // Update the button sizes first
        for (NavButton button : topButtons)
            setIconSize(button, size);
        setIconSize(menuButton, size);
        setIconSize(compass, size, compassHeight);
        setIconSize(lockOnButton, size);
        setIconSize(zoomButton, size, zoomHeight);
        compass.update();

        // Update orientation of menu button
        LayoutParams menuLP = (LayoutParams) menuButton.getLayoutParams();
        final boolean alignRight = _prefs.get(PREF_NAV_ORIENTATION_RIGHT,
                true);
        final boolean ddVisible = _mapView.getWidth() < getWidth();
        if (alignRight) {
            menuButton.setImageResource(ddVisible
                    ? R.drawable.ic_hamburger
                    : R.drawable.ic_hamburger_right);
            menuLP.addRule(ALIGN_PARENT_END);
        } else {
            menuButton.setImageResource(R.drawable.ic_hamburger_left);
            menuLP.removeRule(ALIGN_PARENT_END);
        }
        menuButton.setLayoutParams(menuLP);

        // Hide the menu button if we're right-aligned and the buttons are
        // turned off while a drop-down is open
        menuButton.setVisibility(alignRight && ddVisible && !buttonsVisible
                ? View.GONE
                : View.VISIBLE);

        // Update positions of buttons
        // The buttons are displayed in their natural order when left-aligned
        // and reversed when right-aligned
        final int fullSize = getResources().getDimensionPixelSize(
                R.dimen.padding_small) + size;
        final int rightSide = getWidth() - size;
        for (int i = 0; i < topButtons.size(); i++) {
            int margin = fullSize * (i + 1);
            NavButton btn = topButtons.get(i);
            LayoutParams lp = (LayoutParams) btn.getLayoutParams();
            lp.setMarginStart(alignRight ? (rightSide - margin) : margin);
            btn.setLayoutParams(lp);
        }

        // Then on the next frame update visibility once we have the updated
        // positions and sizes
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                final int mapWidth = _mapView.getWidth();
                final boolean tbVisible = embeddedToolbar.getChildCount() > 0;
                final int tbLeft = embeddedToolbar.getLeft();

                // Set the top button visibility based on available width
                boolean buttonVizChanged = false;
                for (NavButton button : topButtons) {
                    int right = button.getRight();
                    boolean wasVisible = button.getVisibility() == VISIBLE;
                    button.setOnScreen(buttonsVisible
                            && (alignRight || right <= mapWidth)
                            && (!tbVisible || right <= tbLeft));
                    boolean isVisible = button.getVisibility() == VISIBLE;
                    buttonVizChanged |= wasVisible != isVisible;
                }

                // Zoom button visibility
                updateZoomButton();

                // Hide the side layout if the drop-down is overlapping it
                View sideHandle = ((ATAKActivity) getContext()).findViewById(
                        R.id.sidepanehandle_background);
                int handleSize = sideHandle != null ? sideHandle.getWidth() : 0;
                sideLayout.setVisibility(size <= mapWidth - handleSize
                        ? VISIBLE
                        : GONE);

                // Move the side layout up or down depending on if there's
                // space available for it
                updateTopLeftView(sideLayout);
                updateTopLeftView(tooltipView);

                // Reset the sub-toolbar and compass toolbar
                resetChildViews();

                // If any of the buttons' visibility was changed then we
                // should notify the loadout listeners
                if (buttonVizChanged)
                    LoadoutManager.getInstance().notifyLoadoutChanged(
                            _loadouts.getCurrentLoadout());
            }
        });
    }

    /**
     * Update placement for the top-left view based on the placement of the
     * tool buttons and the overflow menu button
     * @param v Top-left view to update
     */
    private void updateTopLeftView(View v) {
        int right = v.getRight();
        int buttonsLeft = Integer.MAX_VALUE;
        for (NavButton btn : topButtons) {
            if (btn.getVisibility() == VISIBLE)
                buttonsLeft = Math.min(buttonsLeft, btn.getLeft());
        }
        LayoutParams lp = (LayoutParams) v.getLayoutParams();
        if (menuButton.getLeft() < right || buttonsLeft < right)
            lp.addRule(BELOW, R.id.tak_nav_menu_button);
        else
            lp.removeRule(BELOW);
        v.setLayoutParams(lp);
    }

    /**
     * Refresh the tool buttons using the current loadout
     */
    private void updateButtonLoadout() {
        LoadoutItemModel loadout = _loadouts.getCurrentLoadout();

        // Top aligned buttons
        for (NavButton button : navButtons) {
            NavButtonModel model = loadout.getButton(button.getKey());
            button.setModel(model);
            button.setEditing(_editingNav);
        }

        // Zoom button
        updateZoomButton();
    }

    /**
     * Update the zoom button state
     */
    private void updateZoomButton() {
        // Zoom button is visible when:
        // 1) The zoom button preference is set to true (usually always)
        // 2) The button has a valid model set OR we're in editing mode
        // 3) The bottom isn't cut off by the screen
        int zoomHeight = (int) (getResources().getDimension(
                R.dimen.nav_zoom_button_height) * userIconScale);
        int zoomBottom = sideLayout.getBottom()
                + (zoomButton.getVisibility() == GONE ? zoomHeight : 0);
        NavButtonModel zoomMdl = zoomButton.getModel();
        boolean zoomVisible = _prefs.get(ZOOM_ENABLE_KEY, true) && (_editingNav
                || zoomMdl != null && zoomMdl != NavButtonModel.NONE)
                && zoomBottom <= _mapView.getHeight();
        zoomButton.setVisibility(zoomVisible ? VISIBLE : GONE);
    }

    /**
     * User icon color preference has been changed
     */
    private void onUserIconColorChanged() {
        // Parse user icon color
        String colorStr = _prefs.get(
                CustomActionBarFragment.ACTIONBAR_ICON_COLOR_KEY, "#FFFFFF");
        String shadowStr = _prefs.get(
                CustomActionBarFragment.ACTIONBAR_BACKGROUND_COLOR_KEY,
                "#000000");
        try {
            userIconColor = Color.parseColor(colorStr);
            userIconShadow = Color.parseColor(shadowStr);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read icon color: " + colorStr);
            userIconColor = Color.WHITE;
            userIconShadow = Color.BLACK;
        }

        // Update button colors
        updateCompassIcon();
        updateLockOnButton();
        compass.update();
        for (NavButton btn : navButtons) {
            btn.setDefaultIconColor(userIconColor);
            btn.setShadowColor(userIconShadow);
        }
    }

    private void onUserIconScaleChanged() {
        String option = _prefs.get("relativeOverlaysScalingRadioList", "1.0");

        try {
            userIconScale = (float) DeveloperOptions.getDoubleOption(
                    "overlays-relative-scale-" + option,
                    Float.parseFloat(option));
        } catch (Exception e) {
            Log.e(TAG, "Failed to read icon scale: " + userIconScale);
            userIconScale = 1f;
        }

        updateButtonLayout();
    }

    private void setupMap() {

        boolean rotLocked = _prefs.get("compass_rotation_locked", false);
        boolean tiltLocked = _prefs.get("compass_tilt_locked", false);
        int tiltState = _prefs.get("compass_tilt_state",
                MapTouchController.STATE_TILT_DISABLED);

        MapMode mm = MapMode.NORTH_UP;
        try {
            String modeStr = _prefs.get("compass_mapmode", null);
            if (modeStr != null)
                mm = MapMode.findFromValue(Integer.parseInt(modeStr));
        } catch (Exception e) {
            Log.e(TAG, "error restoring compass_mapmode");
        }

        setMapMode(mm);

        // set the current state of globe vs flat
        onSharedPreferenceChanged(_prefs.getSharedPrefs(),
                "atakGlobeModeEnabled");

        // Update focus point offset to account for the nav buttons on smaller devices
        if (!getContext().getResources().getBoolean(R.bool.isTablet))
            _mapView.setDefaultActionBarHeight(getResources()
                    .getDimensionPixelSize(R.dimen.nav_button_size));

        // Restore rotation lock state
        setRotationLocked(rotLocked);

        // Restore tilt state
        setTiltState(tiltState);
        setTiltLocked(tiltLocked);

        // need to notify the rest of the system components
        // so they are not out of sync.
        Intent sync = new Intent(mm.getIntent());
        sync.putExtra("map_mode_initial_setup", true);
        AtakBroadcast.getInstance().sendBroadcast(sync);
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    @Override
    public void onClick(View view) {
        NavButton button = (NavButton) view;
        if (button.getModel() == null || button.isEditing())
            return;
        if (buttonsLocked()) {
            Toast.makeText(getContext(), getButtonsLockedReason(),
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (button.getModel().hasChildren()) {
            if (childView == null) {
                addChildView(button);
            } else {
                AtakBroadcast.getInstance().sendBroadcast(new Intent(
                        ToolbarBroadcastReceiver.UNSET_TOOLBAR));
            }

        } else {
            NavButtonIntentAction action = button.getModel().getAction();
            if (action != null) {
                if (action.shouldDismissMenu())
                    AtakBroadcast.getInstance().sendBroadcast(
                            new Intent(LoadoutListDropDown.CLOSE_LOADOUT));
                removeChildView();
                AtakBroadcast.getInstance().sendBroadcast(action.getIntent());
            }
        }
    }

    @Override
    public boolean onLongClick(View view) {
        NavButton button = (NavButton) view;
        NavButtonModel mdl = button.getModel();

        if (_editingNav) {

            // Make sure the button being dragged is in the loadout
            if (mdl == NavButtonModel.NONE || !navButtons.contains(button))
                return false;

            deleteDragArea.setVisibility(VISIBLE);
            buttonToDelete = button;
            ClipData dragData = mdl.createClipboardData(DRAG_MOVE_TOOL);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                view.startDragAndDrop(
                        dragData,
                        new NavButtonShadowBuilder(button),
                        null,
                        0);
            } else {
                view.startDrag(
                        dragData,
                        new NavButtonShadowBuilder(button),
                        null,
                        0);
            }
        } else {
            // Check if the buttons are currently locked
            if (buttonsLocked()) {
                Toast.makeText(getContext(), getButtonsLockedReason(),
                        Toast.LENGTH_LONG).show();
                return true;
            }

            NavButtonIntentAction action = mdl.getActionLong();
            if (action != null) {
                // Invoke the long-press action
                if (action.shouldDismissMenu())
                    AtakBroadcast.getInstance().sendBroadcast(
                            new Intent(LoadoutListDropDown.CLOSE_LOADOUT));
                removeChildView();
                AtakBroadcast.getInstance().sendBroadcast(action.getIntent());
            } else if (mdl != NavButtonModel.NONE) {
                // Show the name of this tool if there is no action
                Toast.makeText(getContext(), mdl.getName(),
                        Toast.LENGTH_SHORT).show();
                return true;
            }
        }
        removeChildView();
        return true;
    }

    @Override
    public boolean onDrag(View view, DragEvent dragEvent) {

        int action = dragEvent.getAction();

        // Always make sure to hide the trash view when ending a drag
        if (action == DragEvent.ACTION_DRAG_ENDED && buttonToDelete != null) {
            buttonToDelete = null;
            deleteDragArea.setVisibility(GONE);
            return true;
        }

        ClipDescription desc = dragEvent.getClipDescription();
        if (desc == null)
            return false;

        // Only respond to tool removal content label
        CharSequence label = desc.getLabel();
        if (label == null || buttonToDelete == null
                || !DRAG_MOVE_TOOL.contentEquals(label))
            return false;

        LoadoutItemModel loadout = _loadouts.getCurrentLoadout();
        if (loadout == null)
            return false;

        // Drag when deleting
        switch (action) {
            case DragEvent.ACTION_DROP: {
                loadout.removeButton(buttonToDelete.getModel());
                _loadouts.notifyLoadoutChanged(loadout);
                buttonToDelete.setModel(NavButtonModel.NONE);
                break;
            }
            case DragEvent.ACTION_DRAG_STARTED:
                tooltipTextView.setText(createTooltipText());
                tooltipView.setVisibility(VISIBLE);
                break;
        }

        return true;
    }

    private void removeChildView() {
        if (childView != null) {
            this.removeView(childView);
            this.childView = null;
        }
        removeCompassChildView();
    }

    private void removeCompassChildView() {
        if (compassButtonChildView != null) {
            this.removeView(compassButtonChildView);
            this.compassButtonChildView = null;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {

        if (key == null)
            return;

        switch (key) {
            case "largeActionBar":
            case "relativeOverlaysScalingRadioList": {
                // Need to wait until the main activity is active again before
                // updating icon sizes and visibility
                _mapView.executeOnActive(new Runnable() {
                    @Override
                    public void run() {
                        onUserIconScaleChanged();
                    }
                });
                break;
            }
            case ZOOM_ENABLE_KEY:
                updateZoomButton();
                break;
            case "dexControls":
                _showDexControls = _prefs.get("dexControls", false);
                updateDexControls();
                break;
            case GLOBE_MODE_ENABLE_KEY:
                if (_prefs.get(key, true)) {
                    _mapView.setProjection(ECEFProjection.INSTANCE);
                } else {
                    _mapView.setProjection(
                            EquirectangularMapProjection.INSTANCE);
                }
                break;

            // Whether the tool buttons are left-justified or right-justified
            case PREF_NAV_ORIENTATION_RIGHT:
                _mapView.executeOnActive(new Runnable() {
                    @Override
                    public void run() {
                        updateButtonLayout();
                    }
                });
                break;

            // Nav button default icon color
            case CustomActionBarFragment.ACTIONBAR_ICON_COLOR_KEY:
            case CustomActionBarFragment.ACTIONBAR_BACKGROUND_COLOR_KEY:
                onUserIconColorChanged();
                break;
        }
    }

    /**
     * Update the DEX sliders (if they're enabled)
     */
    private void updateDexControls() {
        boolean freeLook = _mapView.getMapTouchController()
                .isFreeForm3DEnabled();
        boolean showTilt = _mapView.getMapTouchController()
                .getTiltEnabledState() == MapTouchController.STATE_TILT_ENABLED;
        boolean showRotation = _mapMode == MapMode.USER_DEFINED_UP
                && _mapView.getMapTouchController().isUserOrientationEnabled();
        DexSliderComponent slider = _portraitMode ? horizontalSliderComponent
                : verticalSliderComponent;
        slider.setVisibility((freeLook || showRotation || showTilt)
                && _showDexControls ? VISIBLE : GONE);
        slider.setDirectionSliderVisible(freeLook || showRotation);
        slider.setDirection(_mapView.getMapRotation());
        slider.setTiltSliderVisible(freeLook || showTilt);
        slider.setTilt(_mapView.getMapTilt());
        slider.setMaxTilt(_mapView.getMaxMapTilt());
        if (_portraitMode)
            verticalSliderComponent.setVisibility(GONE);
        else
            horizontalSliderComponent.setVisibility(GONE);
    }

    /**
     * Update the compass icon/button
     */
    private void updateCompassIcon() {
        float rotation = (float) _mapView.getMapRotation();
        boolean showHeading = _mapMode != MapMode.NORTH_UP;
        boolean showTilt = _mapView.getMapTouchController()
                .getTiltEnabledState() != MapTouchController.STATE_TILT_DISABLED;
        compass.setRotateVisible(_mapMode == MapMode.USER_DEFINED_UP);
        compass.setHeadingVisible(showHeading);
        compass.setHeading(rotation);
        compass.setTilt(_mapView.getMapTilt());
        compass.setTiltVisible(showTilt);
        compass.setGPSLock(_mapMode == MapMode.TRACK_UP
                && _gpsLockCallback != null);
        compass.update();
    }

    /**
     * Update the focus lock button
     */
    private void updateLockOnButton() {
        boolean selected = _lockUID != null;
        lockOnButton.setImageDrawable(getContext().getDrawable(selected
                ? R.drawable.nav_lock_on_selected
                : R.drawable.nav_lock_on));
        setIconColor(lockOnButton, selected
                ? getResources().getColor(R.color.maize)
                : userIconColor);
    }

    private SpannableStringBuilder createTooltipText() {
        int highlight = getResources().getColor(R.color.heading_yellow);
        SpannableStringBuilder spam = new SpannableStringBuilder();
        spam.append(getContext().getString(R.string.add_tool)).append(" ");
        spam.setSpan(new ForegroundColorSpan(highlight), 0, spam.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spam.append(getContext().getString(R.string.add_tool_text));
        spam.append(" ");

        SpannableStringBuilder spam2 = new SpannableStringBuilder();
        spam2.append(getContext().getString(R.string.remove_tool)).append(" ");
        spam2.setSpan(new ForegroundColorSpan(highlight), 0, spam2.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spam2.append(getContext().getString(R.string.remove_tool_text));
        spam.append(spam2);
        return spam;
    }

    /**
     * Set whether the user-rotation is locked
     * This is persisted regardless of the current map mode
     * @param locked True if locked
     */
    private void setRotationLocked(boolean locked) {
        _rotationLocked = locked;
        _mapView.getMapTouchController().setUserOrientation(
                _mapMode == MapMode.USER_DEFINED_UP && !locked);
        _prefs.set("compass_rotation_locked", locked);
    }

    /**
     * Set the tilt state as invoked by this class
     * The tilt state may be changed outside this class, however we're only
     * concerned with changes made with the compass
     * @param state Tilt locked state
     */
    private void setTiltState(int state) {
        _mapView.getMapTouchController().setTiltEnabledState(state);
        _prefs.set("compass_tilt_state", state);
    }

    /**
     * Set whether the compass tilt is locked or not
     * This is persisted regardless of the current tilt state
     * @param locked True if locked
     */
    private void setTiltLocked(boolean locked) {
        _tiltLocked = locked;
        _prefs.set("compass_tilt_locked", locked);

        // Update tilt state if needed
        int tiltState = _mapView.getMapTouchController().getTiltEnabledState();
        if (tiltState != MapTouchController.STATE_TILT_DISABLED) {
            setTiltState(_tiltLocked
                    ? MapTouchController.STATE_MANUAL_TILT_DISABLED
                    : MapTouchController.STATE_TILT_ENABLED);
        }
    }

    @Override
    public void onMapEvent(MapEvent event) {
        String type = event.getType();

        switch (type) {
            case MapEvent.MAP_TILT_LOCK:
            case MapEvent.MAP_ROTATE_LOCK:
                updateCompassIcon();
                updateDexControls();
                break;
        }
    }

    private final class NavSelectedReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String buttonId = intent.getStringExtra(NAV_ID_KEY);
            NavButtonModel mdl = _buttonManager.getModelByReference(buttonId);
            if (mdl != null) {
                if (intent.hasExtra(NAV_SELECTED_KEY))
                    mdl.setSelected(
                            intent.getBooleanExtra(NAV_SELECTED_KEY, false));
                updateButton(mdl);
            }
        }
    }

    private final BroadcastReceiver navEditReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null)
                return;

            final LoadoutItemModel loadout = _loadouts.getCurrentLoadout();
            final String name = intent.getStringExtra("name");
            final String reference = intent.getStringExtra("reference");

            switch (action) {

                // Flag whether loadout editing is active
                case NAV_EDITING_KEY:
                    _editingNav = intent.getBooleanExtra("editing", false);
                    if (_editingNav) {
                        tooltipView.setVisibility(VISIBLE);
                        tooltipTextView.setText(createTooltipText());
                        removeCompassChildView();
                    } else
                        tooltipView.setVisibility(GONE);
                    updateButtonLayout();
                    updateButtonLoadout();
                    break;

                // Remove a tool from the current loadout
                case DELETE_TOOL: {

                    // Remove the tool from the loadout
                    NavButtonModel model = NavButtonManager.getInstance()
                            .getModelByReference(reference);
                    if (model == null)
                        return;

                    loadout.removeButton(model);
                    _loadouts.notifyLoadoutChanged(loadout);
                    break;
                }

                // Show/hide a tool in the current loadout
                case SHOW_TOOL:
                case HIDE_TOOL: {
                    loadout.setToolVisible(reference, action.equals(SHOW_TOOL));
                    _loadouts.notifyLoadoutChanged(loadout);
                    break;
                }
            }
        }
    };

    private final BroadcastReceiver _buttonReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null)
                return;

            switch (action) {

                // Toggle the nav buttons on or off
                case TOGGLE_BUTTONS:
                case ActionBarReceiver.TOGGLE_ACTIONBAR: {
                    if (intent.hasExtra("show")) {
                        boolean show = intent.getBooleanExtra("show", true);
                        toggleButtons(show);
                    } else
                        toggleButtons();
                    break;
                }

                // Disable the nav buttons with a reason
                case LOCK_BUTTONS:
                case ActionBarReceiver.DISABLE_ACTIONBAR: {
                    boolean locked = intent.getBooleanExtra("lock",
                            intent.getBooleanExtra("disable", true));
                    String reason = intent.getStringExtra("message");
                    lockButtons(locked, reason);
                    break;
                }

                // Refresh the button models
                case REFRESH_BUTTONS: {
                    updateButtonLoadout();
                    break;
                }
            }
        }
    };

    private class LockBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null)
                return;

            switch (action) {
                case CamLockerReceiver.LOCK_CAM:
                    if (!intent.getBooleanExtra("toolbarGenerated", false)) {
                        String uid = intent.getStringExtra("uid");
                        if (FileSystemUtils.isEquals(uid, _lockUID))
                            setLockUID(null);
                        else
                            setLockUID(uid);
                    }
                    break;
                case CamLockerReceiver.UNLOCK_CAM:
                    setLockUID(null);
                    break;
                case CamLockerReceiver.TOGGLE_LOCK:
                    toggleLock(intent.getBooleanExtra("self", true));
                    break;
                case CamLockerReceiver.TOGGLE_LOCK_LONG_CLICK:
                    toggleLock(false);
                    break;

                case SHOW_MENU:
                    // Turn it off if a marker was touched
                    String uid = intent.getStringExtra("uid");
                    if (uid != null && !uid.equals(_lockUID))
                        setLockUID(null);
                    break;
            }
        }
    }

    private void setLockUID(String lockUID) {
        _lockUID = lockUID;
        updateLockOnButton();
    }

    private void toggleLock(boolean bSelf) {
        if (_lockUID != null) {
            AtakBroadcast.getInstance().sendBroadcast(
                    new Intent(CamLockerReceiver.UNLOCK_CAM));
            return;
        }

        if (bSelf) {
            final MapItem item = _mapView.getSelfMarker();
            Intent camlock = new Intent();
            camlock.setAction(CamLockerReceiver.LOCK_CAM);
            camlock.putExtra("uid", item.getUID());
            // Mark it, so that we don't disable it because the listener
            // above will hear it immediatly. AS.
            camlock.putExtra("toolbarGenerated", true);
            AtakBroadcast.getInstance().sendBroadcast(camlock);
            setLockUID(item.getUID());
        } else {
            ToolManagerBroadcastReceiver.getInstance().startTool(
                    SpecifyLockItemTool.TOOL_IDENTIFIER, new Bundle());
        }
    }

    protected final BroadcastReceiver orientationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            String action = intent.getAction();
            if (FileSystemUtils.isEmpty(action))
                return;

            boolean toggle = FileSystemUtils.isEquals(
                    intent.getStringExtra("toggle"), "true");
            boolean toggleLock = FileSystemUtils.isEquals(
                    intent.getStringExtra("toggleLock"), "true");

            int tiltState = _mapView.getMapTouchController()
                    .getTiltEnabledState();

            // Hide compass widget
            switch (action) {

                // Toggle 3D tilt widget
                case CompassArrowMapComponent.TOGGLE_3D: {
                    double tilt;
                    if (tiltState != MapTouchController.STATE_TILT_DISABLED) {
                        tiltState = MapTouchController.STATE_TILT_DISABLED;
                        tilt = 0;
                        _restoreTilt = _mapView.getMapTilt();
                    } else {
                        tiltState = MapTouchController.STATE_TILT_ENABLED;
                        tilt = _restoreTilt;
                    }
                    setTiltState(tiltState);
                    setTiltLocked(_tiltLocked);
                    CameraController.Programmatic.tiltTo(
                            _mapView.getRenderer3(), tilt, true);
                    break;
                }

                // Lock/toggle 3D tilt interactions
                case CompassArrowMapComponent.LOCK_TILT: {
                    setTiltLocked(!_tiltLocked);
                    break;
                }

                // Set/toggle map rotation mode
                default: {
                    MapMode mode = MapMode.findFromIntent(intent.getAction());

                    // Toggle between user-defined mode and normal
                    if (toggle) {
                        if (mode == MapMode.USER_DEFINED_UP
                                && _mapMode == MapMode.USER_DEFINED_UP)
                            mode = MapMode.NORTH_UP;
                    }

                    // Toggle rotation lock if we're in user-defined mode
                    if (toggleLock && mode == MapMode.USER_DEFINED_UP)
                        setRotationLocked(!_rotationLocked);

                    // Update the map mode
                    setMapMode(mode);
                    return;
                }
            }

            updateDexControls();
            updateCompassIcon();
        }
    };

    /**
     * Toggle between track-up and north-up
     */
    private void toggleTrackUp() {
        MapMode newMode;
        switch (_mapMode) {
            default:
            case NORTH_UP:
                newMode = MapMode.TRACK_UP;
                break;
            case TRACK_UP:
            case MAGNETIC_UP:
            case USER_DEFINED_UP:
                newMode = MapMode.NORTH_UP;
                break;
        }
        AtakBroadcast.getInstance()
                .sendBroadcast(new Intent(newMode.getIntent()));

    }

    @Override
    public void onMapMoved(AtakMapView view, boolean animate) {
        // Update compass degree display
        // handle change of direction/heading
        final double newRotation = _mapView.getMapRotation();
        boolean updateRotation = Double.compare(newRotation,
                _lastRotation) != 0;
        _lastRotation = newRotation;

        // handle tilt/maxTilt change
        final double newTilt = _mapView.getMapTilt();
        boolean updateTilt = Double.compare(newTilt, _lastTilt) != 0;
        _lastTilt = newTilt;

        // Update needs to be made to UI
        if (updateTilt || updateRotation) {
            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    updateDexControls();
                    updateCompassIcon();
                }
            });
        }
    }

    @Override
    public void onMapViewResized(AtakMapView view) {
        if (sideLayout == null || zoomButton == null)
            return;

        // This callback is not run on the UI thread so we need to post any
        // view layout updates
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                updateButtonLayout();
            }
        });
    }

    @Override
    public void onDexTiltChanged(float tilt) {
        CameraController.Programmatic.tiltTo(_mapView.getRenderer3(),
                tilt, false);
    }

    @Override
    public void onDexRotationChanged(float direction) {
        CameraController.Programmatic.rotateTo(_mapView.getRenderer3(),
                direction, false);
    }

    @Override
    public void onModelListChanged() {
        // Update loadouts in case a new model was added/removed
        for (LoadoutItemModel loadout : _loadouts.getLoadouts())
            loadout.refreshButtons();

        // Update buttons
        updateButtonLoadout();
    }

    @Override
    public void onModelChanged(NavButtonModel model) {
        updateButtonLoadout();
    }

    @Override
    public void onLoadoutAdded(LoadoutItemModel loadout) {
    }

    @Override
    public void onLoadoutModified(LoadoutItemModel loadout) {
        if (loadout == _loadouts.getCurrentLoadout())
            updateButtonLoadout();
    }

    @Override
    public void onLoadoutRemoved(LoadoutItemModel loadout) {
    }

    @Override
    public void onLoadoutSelected(LoadoutItemModel loadout) {
        updateButtonLoadout();
    }

    private void addChildView(NavButton button) {
        childView = new NavButtonChildView(this.getContext());
        childView.layoutForButton(button, this, true);
        this.addView(childView);
    }

    private void addCompassChildView() {
        int tiltState = _mapView.getMapTouchController().getTiltEnabledState();
        boolean tiltEnabled = tiltState != MapTouchController.STATE_TILT_DISABLED;
        boolean tiltLocked = tiltState == MapTouchController.STATE_MANUAL_TILT_DISABLED;
        boolean rotationEnabled = _mapMode == MapMode.USER_DEFINED_UP;
        boolean rotationLocked = !_mapView.getMapTouchController()
                .isUserOrientationEnabled();

        compassButtonChildView = (CompassButtonChildView) LayoutInflater
                .from(getContext()).inflate(
                        R.layout.dynamic_compass_child_view, this, false);

        compassButtonChildView.setStates(tiltEnabled, tiltLocked,
                rotationEnabled, rotationLocked);

        compassButtonChildView.layoutForToolbarView(sideLayout);
        addView(compassButtonChildView);
    }

    /**
     * Reset the child views (toolbars) so the layouts can be recalculated
     */
    private void resetChildViews() {
        if (compassButtonChildView != null)
            compassButtonChildView.layoutForToolbarView(sideLayout);
        if (childView != null)
            childView.reposition();
        ToolbarBroadcastReceiver.getInstance().repositionToolbar();
    }

    private void setIconColor(ImageView v, int color) {
        Drawable dr = v.getDrawable();
        if (dr != null)
            v.setImageDrawable(setIconColor(dr, color));
    }

    private NavButtonDrawable setIconColor(Drawable dr, int color) {
        NavButtonDrawable navDr;
        if (dr instanceof NavButtonDrawable)
            navDr = (NavButtonDrawable) dr;
        else
            navDr = new NavButtonDrawable(getContext(), dr);
        if (color == 0)
            navDr.setColorFilter(null);
        else
            navDr.setColor(color);
        navDr.setShadowColor(userIconShadow);
        return navDr;
    }

    private static void setIconSize(View view, int width, int height) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        lp.width = width;
        lp.height = height;
        view.setLayoutParams(lp);
    }

    private static void setIconSize(View view, int size) {
        setIconSize(view, size, size);
    }
}
