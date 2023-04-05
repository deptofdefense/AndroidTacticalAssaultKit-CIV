
package com.atakmap.android.dropdown;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Intent;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Toast;

import com.atakmap.android.drawing.tools.TelestrationTool;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapEventDispatcher.MapEventDispatchListener;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.MultiPolyline;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.PointMapItem.OnPointChangedListener;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class DropDownReceiver extends BroadcastReceiver {

    private static final String TAG = "DropDownReceiver";

    private static final double NO_SCREEN = 0d;
    private static final double FIVE_TWELFTHS_SCREEN = 5d / 12d;
    private static final double THIRD_SCREEN = 1d / 3d;
    private static final double THREE_EIGHTHS_SCREEN = 3d / 8d;
    private static final double SEVEN_SIXTEENTH_SCREEN = 7d / 16d;
    private static final double HALF_SCREEN = 1d / 2d;
    private static final double FIVE_EIGHTHS_SCREEN = 5d / 8d;
    private static final double TWO_THIRDS_SCREEN = 2d / 3d;
    private static final double FULL_SCREEN = 1d;

    public static final double NO_WIDTH = NO_SCREEN;
    public static final double FIVE_TWELFTHS_WIDTH = FIVE_TWELFTHS_SCREEN;
    public static final double THIRD_WIDTH = THIRD_SCREEN;
    public static final double THREE_EIGHTHS_WIDTH = THREE_EIGHTHS_SCREEN;
    public static final double SEVEN_SIXTEENTH_WIDTH = SEVEN_SIXTEENTH_SCREEN;
    public static final double HALF_WIDTH = HALF_SCREEN;
    public static final double FIVE_EIGTHS_WIDTH = FIVE_EIGHTHS_SCREEN;
    public static final double TWO_THIRDS_WIDTH = TWO_THIRDS_SCREEN;
    public static final double FULL_WIDTH = FULL_SCREEN;
    public static final double QUARTER_SCREEN = 1d / 4d;

    public static final double NO_HEIGHT = NO_SCREEN;
    public static final double FIVE_TWELFTHS_HEIGHT = FIVE_TWELFTHS_SCREEN;
    public static final double THIRD_HEIGHT = THIRD_SCREEN;
    public static final double THREE_EIGHTHS_HEIGHT = THREE_EIGHTHS_SCREEN;
    public static final double HALF_HEIGHT = HALF_SCREEN;
    public static final double FIVE_EIGTHS_HEIGHT = FIVE_EIGHTHS_SCREEN;
    public static final double TWO_THIRDS_HEIGHT = TWO_THIRDS_SCREEN;
    public static final double FULL_HEIGHT = FULL_SCREEN;

    // TODO: compute as a percentage based on the map size
    public static final double HANDLE_THICKNESS_LANDSCAPE = 0.05d;
    public static final double HANDLE_THICKNESS_PORTRAIT = 0.12d;

    public static final int DROPDOWN_STATE_FULLSCREEN = 2;
    public static final int DROPDOWN_STATE_NORMAL = 1;
    public static final int DROPDOWN_STATE_NONE = 0;

    private final DisplayMetrics displayMetrics;

    private boolean _closed = true;

    private boolean retain = false;
    private boolean _transient = false;
    private DropDown _dropDown;
    private final MapView _mapView;
    private final FragmentManager _fragmentManager;

    /**
     * Allow for the notion of a selected marker within the drop downs, may be null.
     */
    private MapItem selected;
    private boolean _closeOnSelectionDelete = true;
    final private Marker crosshair = new Marker(UUID.randomUUID().toString());
    private double zorder;

    /**
     * Key to associate the drop down with either settings or help menus.
     */
    private String key;

    /**
     * FragmentManager Warning:
     * In ATAK 2.2 we have been seeing alot of
     *      Caused by: java.lang.IllegalStateException: Can not perform this action after onSaveInstanceState
     * specifically when pausing and resuming ATAK.
     * This exception is being called because the Activity is not restored in time an the fragment loses state.
     * There are places in the code where we are now catching the exception and continuing.   Previous attempts
     * to wait for MapActivity to come back alive involved inappropriate threading or timed delays.
     * Threading was used because because waiting for the activity to resume is like a chicken egg
     * problem.    Cannot fully resume until the dropdown is shown, cannot show the dropdown until the
     * previous is closed.
     */

    private int _backstack = 0;

    private final MapEventDispatchListener medl = new MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            if (event.getType().equals(MapEvent.ITEM_REMOVED)) {
                if (!isClosed() && selected != null
                        && (event.getItem() == selected)) {
                    // the selected item has been removed, the DropDown should be notified.
                    Log.d(TAG,
                            "notifying the dropdown that the selected item is removed: "
                                    + selected.getUID());
                    if (_dropDown != null)
                        _dropDown.onDropDownSelectionRemoved();
                    if (_closeOnSelectionDelete)
                        closeDropDown();
                    else if (crosshair != null)
                        crosshair.setVisible(false);
                }
            }
        }
    };

    private final OnPointChangedListener opcl = new OnPointChangedListener() {
        @Override
        public void onPointChanged(final PointMapItem item) {
            if (item != selected) {
                item.removeOnPointChangedListener(this);
            }
            if (selected instanceof PointMapItem)
                crosshair.setPoint(((PointMapItem) selected).getPoint());
        }

    };

    protected DropDownReceiver(final MapView mapView) {
        _mapView = mapView;
        _fragmentManager = ((FragmentActivity) _mapView.getContext())
                .getSupportFragmentManager();
        displayMetrics = new DisplayMetrics();
        ((Activity) mapView.getContext()).getWindowManager()
                .getDefaultDisplay()
                .getMetrics(displayMetrics);

        // an invisible hair if a drop down registers a MapItem to indicate selection.

        crosshair.setClickable(false);
        crosshair.setZOrder(Double.NEGATIVE_INFINITY);
        crosshair.setMetaBoolean("ignoreFocus", false);
        crosshair.setMetaBoolean("toggleDetails", true);
        crosshair.setMetaBoolean("ignoreMenu", true);
        crosshair.setMetaString("entry", "user");
        crosshair.setMetaBoolean("ignoreOffscreen", true);
        crosshair.setMetaBoolean("addToObjList", false);
        crosshair.setMetaBoolean("preciseMove", true);
        crosshair.setClickable(false);
        crosshair.setVisible(false);
        crosshair.setPoint(GeoPoint.ZERO_POINT);
        getMapView().getRootGroup().addItem(crosshair);

        final MapEventDispatcher dispatcher = getMapView()
                .getMapEventDispatcher();
        dispatcher.addMapEventListener(MapEvent.ITEM_REMOVED, medl);

    }

    /**
     * Perform any cleanup activity prior to destruction. The dropdown will no
     * longer be usable after dispose is called.
     *
     * Subclasses will need to implement disposeImpl for any specific
     * disposal steps.
     *
     * Note:  The creator of the drop down is responsible for the disposal.
     */
    final public void dispose() {
        final MapEventDispatcher dispatcher = getMapView()
                .getMapEventDispatcher();
        dispatcher.removeMapEventListener(MapEvent.ITEM_REMOVED, medl);
        if (!isClosed())
            closeDropDown();
        disposeImpl();
    }

    /**
     * Any drop down specific disposal implementation that is performed during the
     * disposal of a drop down.
     */
    abstract protected void disposeImpl();

    public void callResize(double w, double h) {
        this.resize(w, h);
    }

    /**
     * {@code ignoreBackButton} defaults to false.
     *
     * @see DropDownReceiver#showDropDown(Fragment, double, double, double, double, boolean, boolean, OnStateListener)
     */
    public void showDropDown(final View contentView,
            final double lwFraction,
            final double lhFraction,
            final double pwFraction,
            final double phFraction) {
        showDropDown(contentView, lwFraction, lhFraction, pwFraction,
                phFraction, false, null);
    }

    /**
    /**
     * {@code ignoreBackButton} defaults to false.
     *
     * @see DropDownReceiver#showDropDown(Fragment, double, double, double, double, boolean, boolean, OnStateListener)
     */
    public void showDropDown(final View contentView,
            final double lwFraction,
            final double lhFraction,
            final double pwFraction,
            final double phFraction,
            final OnStateListener onCloseListener) {
        showDropDown(contentView, lwFraction, lhFraction, pwFraction,
                phFraction, false, onCloseListener);
    }

    /**
     * Produces a dropdown with the ignoreBackButton set, but without a listener.
     * @see DropDownReceiver#showDropDown(Fragment, double, double, double, double, boolean, boolean, OnStateListener)
     */
    public void showDropDown(final View contentView,
            final double lwFraction,
            final double lhFraction,
            final double pwFraction,
            final double phFraction,
            final boolean ignoreBackButton) {
        showDropDown(contentView, lwFraction, lhFraction, pwFraction,
                phFraction, ignoreBackButton, null);
    }

    /**
     * Display the view in a side pane.
     *
     * @see DropDownReceiver#showDropDown(Fragment, double, double, double, double, boolean, boolean, OnStateListener)
     */
    public void showDropDown(final View contentView,
            final double lwFraction,
            final double lhFraction,
            final double pwFraction,
            final double phFraction,
            final boolean ignoreBackButton,
            final OnStateListener stateListener) {

        showDropDown(contentView,
                lwFraction, lhFraction, pwFraction, phFraction,
                DropDown.DropDownSide.RIGHT_SIDE,
                ignoreBackButton,
                false, stateListener);
    }

    /**
     * Display the view in a side pane.
     *
     * @see DropDownReceiver#showDropDown(Fragment, double, double, double, double, boolean, boolean, OnStateListener)
     */
    public void showDropDown(final View contentView,
            final double lwFraction,
            final double lhFraction,
            final double pwFraction,
            final double phFraction,
            final boolean ignoreBackButton,
            final boolean switchable,
            final OnStateListener stateListener) {

        showDropDown(contentView,
                lwFraction, lhFraction, pwFraction, phFraction,
                DropDown.DropDownSide.RIGHT_SIDE,
                ignoreBackButton,
                switchable,
                stateListener);
    }

    /**
     * Display the view in a side pane.
     * onDropDownVisible will not be called if the stateListener is registered.
     *
     * @param contentView
     *          view to display
     * @param lwFraction
     *          fraction of the screen width-wise the view should take in landscape mode.
     * @param lhFraction
     *          fraction of the screen height-wise the view should take in landscape mode.
     * @param pwFraction
     *          fraction of the screen width-wise the view should take in portrait mode.
     * @param phFraction
     *          fraction of the screen height-wise the view should take in portrait mode.
     * @param side
     *          side to display the panel on
     * @param ignoreBackButton
     *          if true, side pane will not close on back button pressed.
     * @param switchable
     *          if true, side pane can be switched from LEFT_SIDE to RIGHT_SIDE in the
     *          presence of drop down that will ignore the back button.
     * @param stateListener
     *          listens to drop down state changes.
     */
    public void showDropDown(final View contentView,
            final double lwFraction,
            final double lhFraction,
            final double pwFraction,
            final double phFraction,
            final DropDown.DropDownSide side,
            final boolean ignoreBackButton,
            final boolean switchable,
            final OnStateListener stateListener) {

        if (contentView == null)
            Log.d(TAG, "null content view passed in: " + this, new Exception());

        getMapView().postOnActive(new Runnable() {
            @Override
            public void run() {

                // Existing drop-down must be closed first before showing it again
                if (!isClosed())
                    DropDownManager.getInstance().closeDropDown(_dropDown);

                // Make sure layout parameters for the view are set
                // to match_parent by default
                if (contentView != null) {
                    LayoutParams lp = contentView.getLayoutParams();
                    if (lp == null) {
                        lp = new LayoutParams(LayoutParams.MATCH_PARENT,
                                LayoutParams.MATCH_PARENT);
                        contentView.setLayoutParams(lp);
                    }
                }

                _dropDown = new DropDown(contentView, ignoreBackButton,
                        DropDownReceiver.this);
                _dropDown.setSwitchable(switchable);
                _dropDown.setSide(side);
                if (isPortrait()) {
                    _dropDown.setDimensions(pwFraction, phFraction);
                } else {
                    _dropDown.setDimensions(lwFraction, lhFraction);
                }

                _dropDown.setOnStateListener(
                        new StateListenerForwarder(stateListener));
                _closed = false;
                hideToolbar();
                notifyManager();
            }
        });

    }

    /**
     * {@code ignoreBackButton} defaults to false.
     *
     * @see DropDownReceiver#showDropDown(Fragment, double, double, double, double, boolean, boolean, OnStateListener)
     */
    public void showDropDown(Fragment fragment,
            final double lwFraction,
            final double lhFraction,
            final double pwFraction,
            final double phFraction) {

        showDropDown(fragment, lwFraction, lhFraction, pwFraction, phFraction,
                false, false, null);
    }

    /**
     * {@code ignoreBackButton} defaults to false.
     *
     * @see DropDownReceiver#showDropDown(Fragment, double, double, double, double, boolean, boolean, OnStateListener)
     */
    public void showDropDown(Fragment fragment,
            final double lwFraction,
            final double lhFraction,
            final double pwFraction,
            final double phFraction,
            final OnStateListener stateListener) {

        showDropDown(fragment, lwFraction, lhFraction, pwFraction, phFraction,
                false, false, stateListener);
    }

    /**
     * {@code ignoreBackButton} defaults to false.
     *
     * @see DropDownReceiver#showDropDown(Fragment, double, double, double, double, boolean, boolean, OnStateListener)
     */
    public void showDropDown(final Fragment fragment,
            final double lwFraction,
            final double lhFraction,
            final double pwFraction,
            final double phFraction,
            final boolean ignoreBackButton,
            final boolean switchable) {
        showDropDown(fragment, lwFraction, lhFraction, pwFraction, phFraction,
                ignoreBackButton, switchable, null);
    }

    /**
     * Display the fragment in a side pane.
     * onDropDownVisible will not be called if the stateListener is registered.
     *
     * @param fragment
     *          fragment to display.
     * @param lwFraction
     *          fraction of the screen width-wise the fragment should take in landscape mode.
     * @param lhFraction
     *          fraction of the screen height-wise the fragment should take in landscape mode.
     * @param pwFraction
     *          fraction of the screen width-wise the fragment should take in portrait mode.
     * @param phFraction
     *          fraction of the screen height-wise the fragment should take in portrait mode.
     * @param ignoreBackButton
     *          if true, side pane will not close on back button pressed.
     * @param stateListener
     *          listens to drop down state changes.
     */
    public void showDropDown(final Fragment fragment,
            final double lwFraction,
            final double lhFraction,
            final double pwFraction,
            final double phFraction,
            final boolean ignoreBackButton,
            final boolean switchable,
            final OnStateListener stateListener) {

        getMapView().postOnActive(new Runnable() {

            @Override
            public void run() {

                if (!isClosed())
                    DropDownManager.getInstance().closeDropDown(_dropDown);

                _dropDown = new DropDown(fragment, ignoreBackButton,
                        DropDownReceiver.this);
                _dropDown.setSwitchable(switchable);
                if (isPortrait()) {
                    _dropDown.setDimensions(pwFraction, phFraction);
                } else {
                    _dropDown.setDimensions(lwFraction, lhFraction);
                }
                _dropDown.setOnStateListener(
                        new StateListenerForwarder(stateListener));
                _closed = false;
                hideToolbar();
                notifyManager();
            }
        });

    }

    /**
     * Should this drop down be maintained on the stack.  If it is not retained on the stack, then
     * the next drop down that is opened will dismiss this drop down.
     */
    public boolean isRetained() {
        return retain;
    }

    /**
     * Should this drop down be maintained on the stack used in the
     * case of nested drop downs.   If the dropdown should not be maintained on
     * the stack it will be removed when another drop down is opened.
     */
    public void setRetain(final boolean retain) {
        this.retain = retain;
    }

    /**
     * Flags the drop-down as transient to handle a number of specific cases:
     * - {@link DropDownManager#getTopDropDownKey()} ignores this drop-down
     * - Opening this drop-down does not close other drop-downs
     *   (even with {@link #setRetain(boolean)} set to <code>false</code>)
     *
     * @param isTransient True if this drop-down should be flagged as transient
     */
    public void setTransient(boolean isTransient) {
        _transient = isTransient;
    }

    /**
     * Check whether this drop-down is flagged as transient
     * See {@link #setTransient(boolean)} for more info
     * 
     * @return True if this drop-down is flagged as transient
     */
    public boolean isTransient() {
        return _transient;
    }

    /**
     * Sets the associated setting screen in the Tools section so if the settings
     * screen is open while this drop down receiver is open and in the foreground,
     * the associated setting screen will be opened.
     * @param key Settings screen key
     */
    public void setAssociationKey(final String key) {
        this.key = key;
    }

    /**
     * Get the associated settings screen key
     * @return Settings screen key
     */
    public String getAssociationKey() {
        return key;
    }

    private void hideToolbar() {
        Intent intent = new Intent();
        intent.setAction("com.atakmap.android.maps.toolbar.HIDE_TOOLBAR");
        AtakBroadcast.getInstance().sendBroadcast(intent);

    }

    private void hideRadialMenu() {
        Intent i = new Intent();
        i.setAction("com.atakmap.android.maps.HIDE_MENU");
        AtakBroadcast.getInstance().sendBroadcast(i);
    }

    private void hideDetails() {
        Intent i = new Intent();
        i.setAction("com.atakmap.android.maps.HIDE_DETAILS");
        AtakBroadcast.getInstance().sendBroadcast(i);
    }

    /**
     * For a dropdown that has told the drop down manager to ignore the back button.
     * This method will be called in order to inform the dropdown that the back button
     * has been pressed.  If the drop down handled the back button, then return true else
     * return false.   If the method returns false, then the drop down will be closed on
     * pressing the back button.
     */
    protected boolean onBackButtonPressed() {
        return false;
    }

    /**
     * Signal for a request by the handle.
     * @param state
     * DROPDOWN_STATE_FULLSCREEN = 2;
     * DROPDOWN_STATE_NORMAL = 1;
     * DROPDOWN_STATE_NONE = 0;
     */
    protected void onStateRequested(final int state) {
    }

    /**
     * Action to take when a dropdown is already open under the stack.
     */
    protected void dropDownAlreadyExists() {
        getMapView().postOnActive(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(_mapView.getContext(),
                        R.string.tool_tip,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Close the drop down for this Receiver.
     */
    final public void closeDropDown() {
        hideRadialMenu();
        if (_dropDown != null)
            _mapView.postOnActive(new Runnable() {
                @Override
                public void run() {
                    DropDownManager.getInstance().closeDropDown(_dropDown);
                }
            });
    }

    final void _closeDropDown() {
        if (_dropDown != null) {

            _dropDown.setVisibility(false);
            _dropDown.close();

            final int bsc = getBackStackCount();
            for (int i = 0; i < bsc; ++i) {
                popBackStackImmediate();
            }

            hideDetails();
            _dropDown.dispose();
            // XXX - We should also set _dropDown to null here but this may
            // result in other issues such as NPEs
        }

        _closed = true;
    }

    protected void hideDropDown() {
        Log.d(TAG, "hide side panel...");
        DropDownManager.getInstance().hidePane();
    }

    protected void unhideDropDown() {
        Log.d(TAG, "unhide side panel...");
        DropDownManager.getInstance().unHidePane();
    }

    /**
     * To only be called by the DropDownManager
     */
    final protected boolean _showDropDown() {
        int side;
        if (_dropDown.getSide() == DropDown.DropDownSide.RIGHT_SIDE) {
            side = R.id.right_drop_down;
        } else {
            side = R.id.left_drop_down;
        }

        boolean success = fragmentReplaceTransaction(side,
                _dropDown.getFragment());
        if (success)
            _dropDown.setVisibility(true);
        return success;

    }

    /**
     * If the fragment replacement was successful, return true.
     */
    private boolean fragmentReplaceTransaction(final int id,
            final Fragment frag) {
        try {
            _fragmentManager.beginTransaction()
                    .replace(id, frag).addToBackStack(null).commit();

            _backstack++;
            return true;
        } catch (IllegalStateException e) {
            // see the Fragment Manager warning at the top
            Log.d(TAG, "state loss fragment for: " + this, e);
            return false;
        }
    }

    /**
     * Check if the dropdown is showing.
     *
     * @return true if showing, false if otherwise.
     */
    final public boolean isVisible() {
        return !_closed && _dropDown.isVisible();
    }

    /**
     * Has the drop down been closed.
     */
    final public boolean isClosed() {
        return _closed;
    }

    // Added by John Thompson, needed to handle ignoreBackButton dropdowns. Overlaying
    // dropdowns is not optimal.
    // Instead show the current dropdown while hiding the previous one, upon the
    // current dropdown closing the
    // previous dropdown will redisplay.
    private void notifyManager() {
        DropDownManager.getInstance().showDropDown(this);
    }

    /**
     * Get current DropDown.
     *
     * @return current DropDown
     */
    final public DropDown getDropDown() {
        return _dropDown;
    }

    /**
     * The content view for a MapActivity.
     *
     * @return MapView
     */
    final public MapView getMapView() {
        return _mapView;
    }

    /**
     * Because there is only one backstack per application we need to keep track
     * of the number each dropdown has.
     *
     * @return the number of items this dropdown has in the backstack.
     */
    public int getBackStackCount() {
        return _backstack;
    }

    /**
     * Like {@code popBackStack(int, int)}, but performs the operation immediately
     * inside of the call. This is like calling
     * {@code executePendingTransactions()} afterwards.
     *
     * @return true if there was something popped, false if otherwise.
     */
    boolean popBackStackImmediate() {
        try {
            if (_fragmentManager.popBackStackImmediate()) {
                _backstack--;
                return true;
            }
        } catch (IllegalStateException e) {
            // see the Fragment Manager warning at the top
            Log.d(TAG, "state loss fragment for: " + this, e);
        }
        return false;
    }

    /**
     * In the case that the drop down is not on the top of the stack it will continue to exist
     * in the Fragment Manager.    Need to remove it out completely.
     * NOTE:
     */
    void clearFragment() {
        try {

            if (!(_dropDown.getFragment() instanceof GenericFragmentAdapter))
                _fragmentManager.beginTransaction()
                        .remove(_dropDown.getFragment())
                        .commitAllowingStateLoss();
            else
                _fragmentManager.beginTransaction()
                        .detach(_dropDown.getFragment())
                        .commitAllowingStateLoss();
            _backstack--;
        } catch (Exception e) {
            Log.e(TAG,
                    "error removing fragment for: " + _dropDown.getFragment());
        }
    }

    /**
     * Returns true if the drop down receiver is being used on a tablet.
     * @return true if it is a tablet.
     */
    public boolean isTablet() {
        return _mapView.getContext().getResources().getBoolean(R.bool.isTablet);
    }

    /**
     * Returns true if the drop down receiver is being used in portrait mode.
     * @return true if it is in portrait mode.
     */

    public boolean isPortrait() {
        return _mapView.isPortrait();
    }

    /**
     * Resize the dropdown and open the side pane to fit.
     *
     * @param widthFraction
     *          fraction of the screen width-wise the view should take.
     * @param heightFraction
     *          fraction of the screen height-wise the view should take.
     */
    protected void resize(final double widthFraction,
            final double heightFraction) {
        if (_dropDown == null)
            return;
        if (Double.compare(widthFraction, _dropDown.getWidthFraction()) != 0
                ||
                Double.compare(heightFraction,
                        _dropDown.getHeightFraction()) != 0) {
            _dropDown.setDimensions(widthFraction, heightFraction);
            DropDownManager.getInstance().resize(this);
        }
    }

    /**
     * In order to unify management of the concept of a selected icon within the 
     * system, this will manage the selection lifecycle in accordance with the same
     * rules as a drop down.    
     * registration can occur after the drop down is showing.
     * the selection icon will be shown when the drop down is visible
     * the selection icon will be hidden when the drop down is not visible
     * the selection will no longer be managed if the drop down is closed.  
     * if the drop down is opened again, a call to register selection must be made.
     *  
     * @param m the point map item to use as the selected icon.
     * @param icon the location of the asset to use.
     * @param closeOnDelete Close the drop-down when the selected item is deleted
     */
    protected void setSelected(final MapItem m, final String icon,
            boolean closeOnDelete) {
        synchronized (crosshair) {
            // unregister prior selection
            unregisterSelection();

            selected = m;
            _closeOnSelectionDelete = closeOnDelete;

            // make sure the marker for setSelection is not null
            if (selected != null) {
                // XXX: this does not take into account the fact that the zorder can change (please revisit)
                zorder = selected.getZOrder();

                // set the icon to the appropriate requested style.
                Icon.Builder builder = new Icon.Builder();
                builder.setImageUri(0, icon);
                builder.setAnchor(24, 24);
                crosshair.setIcon(builder.build());

                if (selected instanceof PointMapItem) {
                    crosshair.setPoint(((PointMapItem) selected).getPoint());
                    ((PointMapItem) selected).addOnPointChangedListener(opcl);
                }
                selected.setZOrder(Double.NEGATIVE_INFINITY);

                if (selected instanceof PointMapItem)
                    crosshair.setVisible(isVisible());
            }
        }
    }

    /**
     * Sets the icon to be used for the specified map item.
     * @param m the map item to be selected.
     * @param icon the string for the icon to be used.
     */
    protected void setSelected(final MapItem m, final String icon) {
        setSelected(m, icon, true);
    }

    protected void setSelected(MapItem item) {
        setSelected(item, "asset:/icons/outline.png");
    }

    private void showSelection(final boolean visible) {
        synchronized (crosshair) {
            if (selected != null) {
                if (visible) {
                    // bring to front
                    selected.setZOrder(Double.NEGATIVE_INFINITY);

                    // hide the point details
                    Intent intent = new Intent();
                    intent.setAction(
                            "com.atakmap.android.action.HIDE_POINT_DETAILS");
                    intent.putExtra("uid", selected.getUID());
                    AtakBroadcast.getInstance().sendBroadcast(intent);

                    //Hack to disable telestrate toool after they un-hide the details pane for
                    //a multipolyline
                    if (selected instanceof MultiPolyline) {
                        intent = new Intent();
                        intent.setAction(ToolManagerBroadcastReceiver.END_TOOL);
                        intent.putExtra("tool",
                                TelestrationTool.TOOL_IDENTIFIER);
                        AtakBroadcast.getInstance().sendBroadcast(intent);

                    }

                } else {
                    // restore zorder
                    selected.setZOrder(zorder);
                }
            }

            if (selected instanceof PointMapItem)
                crosshair.setVisible(visible);
            else
                crosshair.setVisible(false);

        }

    }

    private void unregisterSelection() {
        synchronized (crosshair) {
            if (selected != null) {
                if (selected instanceof PointMapItem)
                    ((PointMapItem) selected)
                            .removeOnPointChangedListener(opcl);
                selected.setZOrder(zorder);
                selected = null;
            }

            crosshair.setVisible(false);
        }
    }

    /**
     * Forwards drop-down events from {@link DropDown} to internal and
     * external state listeners
     */
    private class StateListenerForwarder implements OnStateListener {

        private final OnStateListener stateListener;

        StateListenerForwarder(OnStateListener listener) {
            this.stateListener = listener;
        }

        @Override
        public void onDropDownSelectionRemoved() {
            for (OnStateListener l : getListeners())
                l.onDropDownSelectionRemoved();
        }

        @Override
        public void onDropDownVisible(boolean v) {
            showSelection(v);
            for (OnStateListener l : getListeners())
                l.onDropDownVisible(v);
        }

        @Override
        public void onDropDownSizeChanged(double width,
                double height) {
            for (OnStateListener l : getListeners())
                l.onDropDownSizeChanged(width, height);
        }

        @Override
        public void onDropDownClose() {
            unregisterSelection();
            // Even if the calling code has it's own close listener, we still want
            // to fix the map when we close
            if (_dropDown != null && _dropDown.getFragment() != null) {
                try {
                    _fragmentManager.beginTransaction()
                            .remove(_dropDown.getFragment())
                            .commit();
                    _backstack = 0;
                } catch (IllegalStateException e) {
                    // see the Fragment Manager warning at the top
                    Log.d(TAG, "state loss fragment for: " + this,
                            e);
                }
            }

            for (OnStateListener l : getListeners())
                l.onDropDownClose();
        }

        /**
         * Get internal and external state listeners
         * @return List of listeners
         */
        private List<OnStateListener> getListeners() {
            List<OnStateListener> listeners = new ArrayList<>();

            // Add the primary state listener first
            if (stateListener != null)
                listeners.add(stateListener);

            // Get association key-based listeners
            String key = getAssociationKey();
            if (!FileSystemUtils.isEmpty(key))
                listeners.addAll(DropDownManager.getInstance()
                        .getListeners(key));

            return listeners;
        }
    }
}
