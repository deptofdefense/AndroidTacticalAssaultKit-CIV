
package com.atakmap.android.dropdown;

import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.fragment.app.FragmentActivity;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;

import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class SidePane implements OnTouchListener {

    public static final String TAG = "SidePane";

    private int state;

    private final FrameLayout mapLayout;
    private final ViewGroup rightLayout, leftLayout;
    private final ViewGroup rightContainer;
    private final RelativeLayout parent;

    final private View sideHandle;
    final private View bottomHandle; // for portrait mode
    private final MapView mapView;
    private final boolean layoutIsPortrait;
    private DropDown dd, leftDD;

    private double heightFraction = DropDownReceiver.FULL_HEIGHT;
    private double widthFraction = DropDownReceiver.HALF_WIDTH;

    private final int pHeight;
    private final int pWidth;

    private double tap;

    /**
     * A SidePane is what a dropdown needs to be open in order to display.
     * 
     * @param view ATAK's content view.
     */
    SidePane(MapView view) {
        FragmentActivity act = (FragmentActivity) view.getContext();
        mapLayout = act.findViewById(R.id.main_map_container);
        rightLayout = act.findViewById(R.id.right_drop_down);
        leftLayout = act.findViewById(R.id.left_drop_down);
        rightContainer = act.findViewById(R.id.right_drop_down_container);
        parent = act.findViewById(R.id.map_parent);

        pHeight = parent.getHeight();
        pWidth = parent.getWidth();

        sideHandle = act.findViewById(R.id.sidepanehandle_background);
        bottomHandle = act
                .findViewById(R.id.sidepanehandle_background_portrait);

        sideHandle.setOnTouchListener(this);
        bottomHandle.setOnTouchListener(this);

        // switching between portrait and landscape is handled by disposing the side pane and rebuilding it.
        // needs a restart so just check for it once
        layoutIsPortrait = PreferenceManager
                .getDefaultSharedPreferences(view.getContext())
                .getBoolean("atakControlForcePortrait", false);

        state = DropDownReceiver.DROPDOWN_STATE_NONE;
        mapView = view;
        showHandle();

    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {

            final double val = layoutIsPortrait ? event.getY() : event.getX();

            //Log.d(TAG, "val: " + val + " tap: " + tap);
            if (state == DropDownReceiver.DROPDOWN_STATE_FULLSCREEN) {
                //Log.d(TAG, "pushin from full");
                unhidePane();
                state = DropDownReceiver.DROPDOWN_STATE_NORMAL;
            } else if (state == DropDownReceiver.DROPDOWN_STATE_NONE) {
                //Log.d(TAG, "pullout from none");
                unhidePane();
                state = DropDownReceiver.DROPDOWN_STATE_NORMAL;
            } else if (val < tap) {
                //Log.d(TAG, "pullout from normal");
                state = DropDownReceiver.DROPDOWN_STATE_FULLSCREEN;
            } else {
                //Log.d(TAG, "pushin from normal");
                hide();
                state = DropDownReceiver.DROPDOWN_STATE_NONE;
            }
            if (dd != null)
                dd.notifyStateRequested(state);
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_DOWN) {
            tap = layoutIsPortrait ? event.getY() : event.getX();
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Relate this SidePane with a current DropDown, so that the dropDown knows if it is visible or not.
     * This is package private so nobody can mess with it.
     */
    void setDropDown(final DropDown dd) {
        this.dd = dd;
        if (dd == null)
            this.leftDD = null;
        updatePaneVisibility();
    }

    void setLeftDropDown(final DropDown dd) {
        this.leftDD = this.dd != null ? dd : null;
        updatePaneVisibility();
    }

    /**
     * Open the pane to the given fractions.
     * 
     */
    public void open() {

        if (dd == null)
            return;

        if (state == DropDownReceiver.DROPDOWN_STATE_NONE)
            state = DropDownReceiver.DROPDOWN_STATE_NORMAL;

        int prevValue = layoutIsPortrait ? mapView.getHeight()
                : mapView
                        .getWidth();

        LayoutParams mapPanelLP;
        LayoutParams rightPanelLP, leftPanelLP;

        // make sure that the drop downs width fraction falls between
        // NO_WIDTH and FULL_WIDTH
        double widthFraction = Math.max(DropDownReceiver.NO_WIDTH,
                Math.min(dd.getWidthFraction(), DropDownReceiver.FULL_WIDTH));

        // make sure that the drop downs width fraction falls between
        // NO_HEIGHT and FULL_HEIGHT
        double heightFraction = Math.max(DropDownReceiver.NO_HEIGHT,
                Math.min(dd.getHeightFraction(), DropDownReceiver.FULL_HEIGHT));

        double leftWidth, leftHeight;

        final int width = pWidth;
        final int height = pHeight;

        if (!layoutIsPortrait) {
            int x, y, w, h;
            if (Double.compare(heightFraction,
                    DropDownReceiver.FULL_HEIGHT) == 0) {
                mapPanelLP = new LayoutParams(
                        (int) (width - (width * widthFraction)),
                        height);
                x = (int) (width - (width * widthFraction));
                y = 0;
                w = (int) (width * widthFraction);
                h = height;
            } else {
                mapPanelLP = new LayoutParams(width,
                        (int) (height - (height * heightFraction)));
                x = (int) (width - (width * widthFraction));
                y = (int) (height - (height * heightFraction));
                w = (int) (width * widthFraction);
                h = (int) (height * heightFraction);
            }
            rightPanelLP = new LayoutParams(w, h);
            rightPanelLP.leftMargin = x;
            rightPanelLP.topMargin = y;
            leftPanelLP = new LayoutParams(mapPanelLP);
            leftPanelLP.topMargin = y;
            leftWidth = 1 - widthFraction;
            leftHeight = heightFraction;
        } else {
            int x = 0, y, w, h;
            if (Double.compare(heightFraction,
                    DropDownReceiver.FULL_HEIGHT) == 0
                    && Double.compare(widthFraction,
                            DropDownReceiver.NO_WIDTH) != 0
                    && state != DropDownReceiver.DROPDOWN_STATE_NONE) {
                mapPanelLP = new LayoutParams(
                        (int) (width - (width * widthFraction)),
                        height);
                y = 0;
                w = (int) (width * widthFraction);
                h = height;
            } else {
                mapPanelLP = new LayoutParams(width,
                        (int) (height - (height * heightFraction)));
                y = (int) (height - (height * heightFraction));
                w = (int) (width * widthFraction);
                h = (int) (height * heightFraction);
            }
            rightPanelLP = new LayoutParams(w, h);
            rightPanelLP.leftMargin = x;
            rightPanelLP.topMargin = y;
            leftPanelLP = new LayoutParams(mapPanelLP);
            leftPanelLP.leftMargin = x;
            leftWidth = widthFraction;
            leftHeight = 1 - heightFraction;
        }

        this.widthFraction = widthFraction;
        this.heightFraction = heightFraction;

        mapLayout.setLayoutParams(mapPanelLP);
        rightContainer.setLayoutParams(rightPanelLP);
        leftLayout.setLayoutParams(leftPanelLP);

        // XXX - In order to keep the map surface view behaving properly we
        // need to hide the map view when it's no longer visible
        // See ATAK-14582
        mapView.setVisibility(mapPanelLP.width == 0 || mapPanelLP.height == 0
                ? View.GONE
                : View.VISIBLE);

        refresh(prevValue);

        //By shear virtue if the drawer size changes, then we probably want to tell the dropdown.
        if (dd != null)
            dd.setDimensions(widthFraction, heightFraction);
        if (leftDD != null)
            leftDD.setDimensions(leftWidth, leftHeight);

        //By shear virtue of calling open, lets just say for sake of argument, that it might be visible?.
        if (dd != null)
            dd.setVisibility(true);
        if (leftDD != null)
            leftDD.setVisibility(true);

        //Nobody likes a handle on when the widthFraction is something other than 0.   Thats just rude.

        if (widthFraction > 0)
            showHandle();
        updatePaneVisibility();
    }

    public double getWidth() {
        return widthFraction;
    }

    public double getHeight() {
        return heightFraction;
    }

    /**
     * Close the pane and remove all views attached to it. 
     */
    public void close() {
        state = DropDownReceiver.DROPDOWN_STATE_NONE;

        int prevValue = layoutIsPortrait ? mapView.getHeight()
                : mapView
                        .getWidth();

        contractSidePane();
        leftLayout.removeAllViews();
        rightLayout.removeAllViews();

        widthFraction = DropDownReceiver.NO_WIDTH;
        heightFraction = DropDownReceiver.FULL_HEIGHT;

        refresh(prevValue);
        this.dd = null;
        this.leftDD = null;
        showHandle();
        updatePaneVisibility();
    }

    /**
     * Close the pane without removing its attached views.
     */
    public void hide() {
        state = DropDownReceiver.DROPDOWN_STATE_NONE;

        int prevValue = layoutIsPortrait ? mapView.getHeight()
                : mapView
                        .getWidth();

        contractSidePane();

        refresh(prevValue);

        showHandle();

        // By shear virtue if one is hidden, the visibility might be, lets say false.

        if (dd != null) {
            dd.setVisibility(false);
        }
    }

    private void showHandle() {
        if (dd == null) {
            sideHandle.setVisibility(View.GONE);
            bottomHandle.setVisibility(View.GONE);
        } else if (layoutIsPortrait) {
            sideHandle.setVisibility(View.GONE);
            bottomHandle.setVisibility(View.VISIBLE);
        } else {
            bottomHandle.setVisibility(View.GONE);
            sideHandle.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Toggle visibility on both drop-down panes depending on whether there's
     * a set drop-down for either
     */
    private void updatePaneVisibility() {
        rightContainer.setVisibility(dd != null ? View.VISIBLE : View.GONE);
        leftLayout.setVisibility(leftDD != null ? View.VISIBLE : View.GONE);
    }

    private void unhidePane() {
        showHandle();
        open();

        // By shear virtue if one is hidden, the visibility might be, lets say true.
        if (dd != null) {
            dd.setVisibility(true);
        }
    }

    private void contractSidePane() {

        LayoutParams mapPanelLP = (LayoutParams) mapLayout
                .getLayoutParams();
        mapPanelLP.width = LayoutParams.MATCH_PARENT;
        mapPanelLP.height = LayoutParams.MATCH_PARENT;
        mapLayout.setLayoutParams(mapPanelLP);

        // Hide right-side panel
        LayoutParams sidePanelLP = (LayoutParams) rightContainer
                .getLayoutParams();
        sidePanelLP.topMargin = 0;
        sidePanelLP.leftMargin = 0;
        sidePanelLP.width = 0;
        sidePanelLP.height = 0;
        rightContainer.setLayoutParams(sidePanelLP);

        // Hide left-side panel
        sidePanelLP = (LayoutParams) leftLayout.getLayoutParams();
        sidePanelLP.topMargin = 0;
        sidePanelLP.leftMargin = 0;
        sidePanelLP.width = 0;
        sidePanelLP.height = 0;
        leftLayout.setLayoutParams(sidePanelLP);

        // Show map view again in case visibility was turned off
        mapView.setVisibility(View.VISIBLE);
        updatePaneVisibility();
    }

    private void refresh(final int prevValue) {
        /*
         * MapView resize doesn't happen immediately after resizing the layouts.
         * Therefore, we sit on our hands until it finishes before sending our MAP_MOVED event.
         * Limit the time that this thread can run to be 5 seconds.
         */
        ExecutorService pool = Executors.newSingleThreadExecutor();

        pool.execute(new Runnable() {
            @Override
            public void run() {
                int count = 0;
                while (isMapViewTheSame(prevValue) && count < 50) {
                    try {
                        Thread.sleep(100);
                        count++;
                    } catch (InterruptedException e) {
                        // Don't care if interrupted. Display error and continue
                        Log.e(TAG, "error: ", e);
                    }
                }

                MapEvent.Builder b = new MapEvent.Builder(MapEvent.MAP_RESIZED);
                Bundle bundle = new Bundle();
                bundle.putDouble("map_size_fraction", 1 - widthFraction);
                b.setExtras(bundle);
                mapView.getMapEventDispatcher().dispatch(b.build());
            }
        });
    }

    private boolean isMapViewTheSame(int value) {

        if (layoutIsPortrait) {
            return value == mapView.getHeight();
        } else {
            return value == mapView.getWidth();
        }
    }

    /**
     * Indicates whether the side pane is open.
     * 
     * @return true if side pane is open, false if otherwise.
     */
    public boolean isOpen() {
        return DropDownReceiver.DROPDOWN_STATE_NONE != state;
    }
}
