
package com.atakmap.android.dropdown;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import androidx.annotation.NonNull;

/**
 * All DropDownReceivers should use this class in order to register.
 */
public class DropDownManager extends BroadcastReceiver {

    static final public String TAG = "DropDownManager";

    private DropDownReceiver rightSide; // currently active right side drop down receiver
    private DropDownReceiver leftSide; // currently active left side drop down receiver

    // right side stack of drop down receivers
    private final List<DropDownReceiver> rightSideStack = new ArrayList<>();

    // State listeners mapped by association key
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<OnStateListener>> keyListeners = new ConcurrentHashMap<>();

    private final Object ddrListsLock = new Object();
    private SidePane sidePane;
    private static DropDownManager _instance = null;

    public static final String CLOSE_DROPDOWN = "com.android.arrowmaker.BACK_PRESS";
    public static final String BACK_PRESS_NOT_HANDLED = "com.android.arrowmaker.BACK_PRESS_NOT_HANDLED";
    public static final String SHOW_DROPDOWN = "com.android.arrowmaker.SHOW_DROPDOWN";
    public static final String SHOW_DROPDOWN_DIVIDER = "com.android.atak.SHOW_DROPDOWN_DIVIDER";
    public static final String HIDE_DROPDOWN_DIVIDER = "com.android.atak.HIDE_DROPDOWN_DIVIDER";

    protected DropDownManager() {
    }

    synchronized public static DropDownManager getInstance() {
        if (_instance == null) {
            _instance = new DropDownManager();
        }
        return _instance;
    }

    /**
     * Return the top right side drop down key.   Primary use case:
     * Used by the Setting Screen so that if the top dropdown has an 
     * associated key in the Tool Preferences then when setting is launched 
     * it will launch the appropriate sub menu.
     *
     * Note: Drop-downs marked as transient will be ignored
     */
    synchronized public String getTopDropDownKey() {
        if (rightSide != null && !rightSide.isTransient())
            return rightSide.getAssociationKey();
        for (DropDownReceiver ddr : rightSideStack) {
            if (!ddr.isTransient())
                return ddr.getAssociationKey();
        }
        return null;
    }

    /**
     * Check if the supplied drop-down is currently on top (not necessarily visible)
     * @param dropDown Drop down
     * @return True if this drop-down is on top
     */
    synchronized public boolean isTopDropDown(DropDownReceiver dropDown) {
        return rightSide != null && rightSide == dropDown;
    }

    /**
     * Whether any active drop-down has the "close before tool" flag
     * @return True if the active drop-down should close first on back-button
     */
    synchronized public boolean shouldCloseFirst() {
        return rightSide != null
                && rightSide.getDropDown() != null
                && rightSide.getDropDown().closeBeforeTool()
                || leftSide != null
                        && leftSide.getDropDown() != null
                        && leftSide.getDropDown().closeBeforeTool();
    }

    synchronized public void dispose() {

        rightSideStack.clear();
        rightSide = null;
        leftSide = null;
    }

    private boolean compareDimensions(final DropDown dd) {
        return sidePane != null
                && dd != null
                &&
                Double.compare(dd.getWidthFraction(), sidePane.getWidth()) == 0
                &&
                Double.compare(dd.getHeightFraction(),
                        sidePane.getHeight()) == 0;
    }

    synchronized boolean showDropDown(final DropDownReceiver ddr) {
        if (ddr == null) {
            //something went wrong
            return false;
        }

        final DropDown dd = ddr.getDropDown();

        if (dd == null) {
            //something went wrong
            return false;
        }

        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(SHOW_DROPDOWN).putExtra("clazz",
                        ddr.getClass().getName()));

        if (isSpecialCase(dd)) {
            Log.d(TAG, "drop down selected side is left: " + ddr);
            dd.setSide(DropDown.DropDownSide.LEFT_SIDE);
        } else {
            Log.d(TAG, "drop down selected side is right: " + ddr);
            dd.setSide(DropDown.DropDownSide.RIGHT_SIDE);
        }

        //Left Side is not Stack Managed, right side is.
        if (dd.getSide() == DropDown.DropDownSide.LEFT_SIDE) {

            // Remove the drop-down if it exists in the right-side stack
            if (rightSideStack.contains(ddr))
                closeDropDown(ddr.getDropDown());

            Log.d(TAG, "unhide any existing dropdown");
            unHidePane();
            Log.d(TAG, "calling show on (left side): " + ddr);
            closeLeftDropDown();
            leftSide = ddr;
            leftSide._showDropDown();
            sendHideToolbarHandleIntent();
            sidePane.setLeftDropDown(dd);
            sidePane.open();
        } else {

            Log.d(TAG, "calling show on (right side): " + ddr);

            // this should never happen //
            if (leftSide == ddr) {
                Log.d(TAG, "drop down show called twice (showing on left): "
                        + leftSide + "==" + ddr);
                return true;

            } else if (rightSide != null) {

                // changing out the right side drop down which means the 
                // left side drop down should no longer be active.
                closeLeftDropDown();

                if (rightSide == ddr) {
                    Log.d(TAG,
                            "drop down show called twice (showing on right): "
                                    + rightSide + "==" + ddr);
                    closeRightDropDown(false, true);
                    return true;
                } else if (rightSideStack.contains(ddr)) {
                    Log.d(TAG,
                            "drop down show called already on the stack, moving to the front: "
                                    + ddr);
                    closeDropDown(ddr.getDropDown());

                    // XXX - This behavior doesn't aid the user in any way
                    // Much more helpful to automatically close the old
                    // drop-down and re-open it on top
                    /*Log.d(TAG,
                            "drop down show called already on the stack, ignoring"
                                    + ddr);
                    try {
                        ddr.dropDownAlreadyExists();
                    } catch (Exception e) {
                        Log.d(TAG, "error in implementatio of: " + ddr);
                    }
                    return true;*/
                }

                // Check if this drop-down should be retained or not
                boolean retained = rightSide.isRetained();

                // If the drop-down we're opening is transient then don't
                // close the non-retained drop-down
                if (ddr.isTransient())
                    retained = true;

                // If the existing drop-down is transient then it should never
                // be retained, but we also need to check if the drop-down
                // below it isn't meant to be retained
                DropDownReceiver needsClose = null;
                if (rightSide.isTransient()) {
                    retained = false;
                    if (!rightSideStack.isEmpty()) {
                        DropDownReceiver under = rightSideStack.get(0);
                        if (!under.isRetained())
                            needsClose = under;
                    }
                }

                Log.d(TAG, "rightSided retained: " + retained +
                        " or ignoreBackButton: "
                        + rightSide.getDropDown().ignoreBackButton());
                Log.d(TAG, "incoming right side ignoreBackButton: "
                        + ddr.getDropDown().ignoreBackButton());

                // retain 
                if ((rightSide != null)
                        && (retained || rightSide.getDropDown()
                                .ignoreBackButton())) {
                    synchronized (ddrListsLock) {
                        Log.d(TAG, "retaining the drop down on the stack: "
                                + rightSide);
                        rightSideStack.add(0, rightSide);

                        // need to notify that current drop down that is being moved into a retained state is no 
                        // longer visible.
                        rightSide.getDropDown().setVisibility(false);
                    }
                } else if (rightSide != null) {

                    Log.d(TAG,
                            "not retaining the drop down on the stack, closing: "
                                    + rightSide);

                    // calling close drop down here is a dreadful mistake as it will mash the queue.
                    // if A is retained and B, C not retained.
                    // show A, show B, show C
                    // B is not retained so if you call closeDropDown on B, A is put into rightSide and 
                    // promptly overwritten.                      
                    // closeDropDown(rightSide.getDropDown());  
                    // the below line is not the same as closeDropDown
                    rightSide._closeDropDown();
                    rightSide = null; // set rightSide to null for readability.
                }

                // Close additional non-retained drop-down that was open
                // under a transient drop-down
                if (needsClose != null)
                    closeDropDown(needsClose.getDropDown());
            }
            rightSide = ddr;

            if (rightSide != null)
                Log.d(TAG, "showing drop down: " + rightSide + " size: "
                        + rightSide.getDropDown().getWidthFraction() + "x"
                        + rightSide.getDropDown().getHeightFraction());
            else
                Log.d(TAG,
                        "calling to show a rightside dropdown, but it is null");

            boolean success = false;
            if (rightSide != null)
                success = rightSide._showDropDown();
            if (success) {
                // side pane (aka sliding drawer) management is reserved 
                // for the right side of the screen.
                final DropDown cdd = rightSide.getDropDown();

                if (sidePane != null && sidePane.isOpen()) {
                    if (cdd.getSide() == DropDown.DropDownSide.LEFT_SIDE) {
                        cdd.setDimensions(DropDownReceiver.HALF_WIDTH,
                                DropDownReceiver.FULL_HEIGHT);
                    }
                    openPane(rightSide);
                } else {
                    openPane(rightSide);
                }
                unHidePane();
            } else {
                Log.d(TAG, "unsuccessful open of: " + rightSide);
                if (rightSide != null)
                    rightSide._closeDropDown();
                rightSide = null; // set rightSide to null for readability.
            }

        }

        return true;
    }

    /**
     * Register a DropDownReceiver to be run in the main activity thread. 
     * The receiver will be called with any broadcast Intent that matches filter, in the main application thread. 
     *  @param receiver The DropDownReceiver to handle the broadcast
     * @param filter Selects the Intent broadcasts to be received.
     */
    public void registerDropDownReceiver(final DropDownReceiver receiver,
            final DocumentedIntentFilter filter) {
        if (receiver != null) {
            AtakBroadcast.getInstance().registerReceiver(receiver, filter);
        }
    }

    /**
     * Unregister a DropDownReceiver. This will stop the receiver from being called by a broadcast Intent.
     * This is the same as  {@code com.atakmap.android.ipc.AtakBroadcast.getInstance().unregisterReceiver(receiver)}
     *
     * @param receiver The DropDownReceiver to stop handling the broadcast.
     */
    public void unregisterDropDownReceiver(final DropDownReceiver receiver) {
        AtakBroadcast.getInstance().unregisterReceiver(receiver);
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        String action = intent.getAction();
        if (action == null)
            return;

        if (action.equals(CLOSE_DROPDOWN)) {
            final DropDownReceiver rs = rightSide;
            if ((rs != null) && (rs.getDropDown() != null) &&
                    rs.getDropDown().isPromptEnabled()) {
                AlertDialog.Builder alertBuilder = new AlertDialog.Builder(
                        rs.getMapView().getContext());
                alertBuilder.setTitle(R.string.discard_changes);
                alertBuilder.setMessage(R.string.discard_unsaved);
                alertBuilder.setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                Log.d(TAG, "request to close: " + rs);
                                if (!rs.isClosed())
                                    closeRightDropDown(true, true);
                            }
                        });
                alertBuilder.setNegativeButton(R.string.cancel, null);
                alertBuilder.create().show();
            } else if (leftSide != null) {
                if (!leftSide.onBackButtonPressed())
                    closeLeftDropDown();
            } else if (rightSide != null) {
                if (!rightSide.onBackButtonPressed())
                    closeRightDropDown(false, true);
            } else {
                //if there are no active dropdowns don't do anything
                AtakBroadcast.getInstance().sendBroadcast(
                        new Intent(BACK_PRESS_NOT_HANDLED));
            }
        }
    }

    /**
     * Close all drop downs.   Should only be used when changing orientations or quitting
     * the application.
     */
    public void closeAllDropDowns() {
        MapView mv = MapView.getMapView();
        mv.postOnActive(new Runnable() {
            @Override
            public void run() {
                if (leftSide != null)
                    closeLeftDropDown();

                while (rightSide != null)
                    closeRightDropDown(true, true);

                if (sidePane != null) {
                    if (sidePane.isOpen())
                        sidePane.close();
                    sidePane.dispose();
                }

                sidePane = null;
            }
        });
    }

    /**
     * Close a specific drop down even if it is not the currently visible drop down.
     */
    synchronized void closeDropDown(final DropDown dd) {
        if (leftSide != null && leftSide.getDropDown() == dd) {
            Log.d(TAG, "closing a left side drop down that is visible: "
                    + leftSide);
            closeLeftDropDown();
        } else if (rightSide != null && rightSide.getDropDown() == dd) {
            Log.d(TAG, "closing a right side drop down that is visible: "
                    + rightSide);
            closeRightDropDown(dd.ignoreBackButton(), true);
        } else {
            DropDownReceiver remove = null;
            for (int i = 0; i < rightSideStack.size() && remove == null; ++i) {
                if (rightSideStack.get(i).getDropDown() == dd) {
                    remove = rightSideStack.get(i);
                    Log.d(TAG, "found the first hidden drop down to remove: "
                            + remove);
                    remove._closeDropDown();
                }
            }
            if (remove != null) {
                Log.d(TAG,
                        "removing a drop down that was not visible, but on the stack: "
                                + remove);
                remove.clearFragment();
                boolean b = rightSideStack.remove(remove);
                if (!b) {
                    Log.d(TAG,
                            "did not successfully remove the dropdown: "
                                    + remove);
                }

            } else {
                Log.d(TAG,
                        "call to close dropdown, but the dropdown was not open: "
                                + dd,
                        new Exception());
                //closeRightDropDown(dd.ignoreBackButton(), true);
            }
        }
    }

    /**
     * Pop the BackStack. If the BackStack is empty, close the current dropdown and open the next one. 
     *
     */
    synchronized void closeLeftDropDown() {
        if (leftSide != null) {
            Log.d(TAG, "closing the leftSide dropdown: " + leftSide
                    + " with backstack count: " + leftSide.getBackStackCount());
            final int bsc = leftSide.getBackStackCount();
            for (int i = 0; i < bsc; ++i) {
                leftSide.popBackStackImmediate();
                Log.d(TAG,
                        "draining the back stack: "
                                + leftSide.getBackStackCount());
            }
            leftSide._closeDropDown();
            sendShowToolbarHandleIntent();
            leftSide = null;
            sidePane.setLeftDropDown(null);
        }
    }

    /**
     * Pop the BackStack. If the BackStack is empty, close the current dropdown and open the next one. 
     * 
     * @param ignoreBackButtonFlag If true, proceed regardless of state ignoreBackButton flag.
     * @param completely if true, this will close all fragments within the dropdown and ultimately
     * close the dropdown.   if false, this will just step one fragment backwards.
     */
    synchronized void closeRightDropDown(final boolean ignoreBackButtonFlag,
            final boolean completely) {

        synchronized (ddrListsLock) {

            if (rightSide != null
                    && (rightSide.getDropDown() != null)
                    &&
                    (!rightSide.getDropDown().ignoreBackButton()
                            || ignoreBackButtonFlag)) {

                if (completely) {
                    Log.d(TAG,
                            "closing the rightside dropdown: " + rightSide +
                                    " with backstack count: "
                                    + rightSide.getBackStackCount());
                    final int bsc = rightSide.getBackStackCount();
                    for (int i = 0; i < bsc; ++i) {
                        rightSide.popBackStackImmediate();
                        Log.d(TAG,
                                "draining the back stack: "
                                        + rightSide.getBackStackCount());
                    }
                } else {

                    rightSide.popBackStackImmediate();
                }

                if (rightSide.getBackStackCount() < 1) {
                    closeLeftDropDown();
                    rightSide._closeDropDown();
                    if (rightSideStack.size() > 0) {
                        Log.d(TAG, "found retained drop down on the stack: "
                                + rightSide);
                        rightSide = null;
                        showDropDown(rightSideStack.remove(0));
                    } else {
                        sendShowToolbarHandleIntent();
                        rightSide = null;
                        Log.d(TAG,
                                "no retained drop down found, close the left side");
                        if (sidePane != null)
                            sidePane.close();
                    }
                }
            }
        }
    }

    private boolean isSpecialCase(final DropDown dd) {
        if (rightSide == null) {
            Log.d(TAG, "right side is empty");
            return false;
        } else if (rightSide.getDropDown().ignoreBackButton()
                && dd.isSwitchable()) {
            Log.d(TAG, "right side in use and the new drop down is switchable");
            if (rightSide.getDropDown()
                    .getWidthFraction() > DropDownReceiver.HALF_WIDTH
                    && rightSide.getDropDown()
                            .getHeightFraction() > DropDownReceiver.HALF_HEIGHT) {
                Log.d(TAG,
                        "not enough room to use the new drop down on the left");
            } else
                return true;
        }
        Log.d(TAG, "right side in use and the new drop down is not switchable");

        return false;
    }

    /**
     * Resize the current sidepane.
     * 
     * @param ddr dropdown to get dimensions from.
     */
    void resize(final DropDownReceiver ddr) {
        if (!compareDimensions(ddr.getDropDown())) {
            openPane(ddr);
        }
    }

    /**
     * PUBLIC ACCESS TO THESE WILL BE REMOVED IN THE NEXT RELEASE.
     * THERE ARE MANY WORDS THAT CAN BE USED TO DESCRIBE MY STATE RIGHT NOW.
     */
    public void hidePane() {
        if (sidePane != null && sidePane.isOpen())
            sidePane.hide();
    }

    /**
     * PUBLIC ACCESS TO THESE WILL BE REMOVED IN THE NEXT RELEASE.
     * THERE ARE MANY WORDS THAT CAN BE USED TO DESCRIBE MY STATE RIGHT NOW.
     */
    public void unHidePane() {
        if (sidePane != null && !sidePane.isOpen()) {
            if (rightSide != null) {
                sidePane.open();
            } else {
                Log.d(TAG,
                        "a call has been made by some code incorrectly trying to open a sidepane that will not be filled",
                        new Exception());
            }

        }
    }

    /**
     * Send intent to show the toolbar handle.
     */
    void sendShowToolbarHandleIntent() {
        Intent show = new Intent(
                "com.atakmap.android.maps.toolbar.SHOW_TOOLBAR_HANDLE");
        AtakBroadcast.getInstance().sendBroadcast(show);
    }

    /**
     * Send intent to hide the toolbar handle.
     */
    void sendHideToolbarHandleIntent() {
        Intent hide = new Intent(
                "com.atakmap.android.maps.toolbar.HIDE_TOOLBAR_HANDLE");
        AtakBroadcast.getInstance().sendBroadcast(hide);
    }

    private void openPane(final DropDownReceiver ddr) {

        if (sidePane == null)
            sidePane = new SidePane(ddr.getMapView());

        if (ddr == null)
            return;

        final DropDown dd = ddr.getDropDown();

        if (sidePane != null && dd != null) {
            // XXX:   this was severely insane that the sidePane had absolutely no
            // idea about the dropdown it was associated with prior to the creation
            // of this method.
            sidePane.setDropDown(dd);
            sidePane.open();
        }

        // hide the tool bar
        if (dd != null
                &&
                Double.compare(dd.getWidthFraction(),
                        DropDownReceiver.FULL_WIDTH) == 0
                &&
                Double.compare(dd.getHeightFraction(),
                        DropDownReceiver.FULL_HEIGHT) == 0) {
            sendHideToolbarHandleIntent();
        } else {
            sendShowToolbarHandleIntent();
        }
    }

    /**
     * Add a drop-down state listener for a given association key
     * @param assocKey Association key
     * @param listener Drop-down state listener
     */
    public void addStateListener(@NonNull String assocKey,
            OnStateListener listener) {
        ConcurrentLinkedQueue<OnStateListener> listeners = keyListeners
                .get(assocKey);
        if (listeners == null)
            keyListeners.put(assocKey,
                    listeners = new ConcurrentLinkedQueue<>());
        listeners.add(listener);
    }

    /**
     * Remove a drop-down state listener for a given association key
     * @param assocKey Association key
     * @param listener Drop-down state listener
     */
    public void removeStateListener(@NonNull String assocKey,
            OnStateListener listener) {
        ConcurrentLinkedQueue<OnStateListener> listeners = keyListeners
                .get(assocKey);
        if (listeners != null)
            listeners.remove(listener);
    }

    /**
     * Get external state listeners for a given association key
     * @param associationKey Drop-down association key
     * @return State listeners
     */
    List<OnStateListener> getListeners(@NonNull String associationKey) {
        ConcurrentLinkedQueue<OnStateListener> listeners = keyListeners
                .get(associationKey);
        return listeners != null ? new ArrayList<>(listeners)
                : new ArrayList<>();
    }
}
