
package com.atakmap.android.model;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;

import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.items.MapItemDetailsView;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.feature.DataStoreException;
import com.atakmap.map.layer.feature.FeatureDataStore2;

public class ModelDetailsDropdownReceiver extends DropDownReceiver implements
        OnStateListener {
    private final FeatureDataStore2 spatialDb;
    private final MapItemDetailsView itemViewer;
    private MapItem item;
    private double currWidth = HALF_WIDTH;
    private double currHeight = HALF_HEIGHT;

    public static final String TAG = "FeaturesDetailsDropdownReceiver";
    public static final String SHOW_DETAILS = "com.atakmap.android.model.SHOW_DETAILS";
    public static final String TOGGLE_VISIBILITY = "com.atakmap.android.model.TOGGLE_VISIBILITY";

    public ModelDetailsDropdownReceiver(MapView mapView,
            FeatureDataStore2 spatialDb) {
        super(mapView);
        this.spatialDb = spatialDb;

        LayoutInflater inflater = LayoutInflater
                .from(getMapView().getContext());

        this.itemViewer = (MapItemDetailsView) inflater.inflate(
                R.layout.map_item_view, null);
        this.itemViewer.setDropDown(this);
        this.itemViewer.setGalleryAsAttachments(false);
    }

    @Override
    public void disposeImpl() {
    }

    @Override
    protected void onStateRequested(int state) {
        if (state == DROPDOWN_STATE_FULLSCREEN) {
            if (!isPortrait()) {
                if (Double.compare(currWidth, HALF_WIDTH) == 0) {
                    resize(FULL_WIDTH - HANDLE_THICKNESS_LANDSCAPE,
                            FULL_HEIGHT);
                }
            } else {
                if (Double.compare(currHeight, HALF_HEIGHT) == 0) {
                    resize(FULL_WIDTH, FULL_HEIGHT - HANDLE_THICKNESS_PORTRAIT);
                }
            }
        } else if (state == DROPDOWN_STATE_NORMAL) {
            if (!isPortrait()) {
                resize(HALF_WIDTH, FULL_HEIGHT);
            } else {
                resize(FULL_WIDTH, HALF_HEIGHT);
            }
        }
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        final String action = intent.getAction();
        if (action == null)
            return;

        final MapItem item = findTarget(intent.getStringExtra("targetUID"));
        if (action.equals(SHOW_DETAILS)) {
            if (this.isVisible() && item == null) {
                this.closeDropDown();
            } else if (item != null) {

                this.item = item;
                setRetain(true);

                this.itemViewer.setItem(getMapView(), item);

                setSelected(item, "asset:/icons/outline.png");

                if (!this.isVisible()) {
                    showDropDown(itemViewer,
                            HALF_WIDTH,
                            FULL_HEIGHT,
                            FULL_WIDTH,
                            HALF_HEIGHT, this);
                }
            }
        } else if (action.equals(TOGGLE_VISIBILITY)) {
            if (item == null || !item.hasMetaValue("fid"))
                return;
            long fid = item.getMetaLong("fid", 0);
            try {
                this.spatialDb.setFeatureVisible(fid, false);
            } catch (DataStoreException ignored) {
            }
        }
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
        Log.d(TAG, "resizing width=" + width + " height=" + height);
        currWidth = width;
        currHeight = height;
    }

    @Override
    public void onDropDownClose() {
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownVisible(boolean v) {
    }

    private MapItem findTarget(final String targetUID) {
        if (targetUID != null) {
            MapGroup rootGroup = getMapView().getRootGroup();
            return rootGroup.deepFindItem("uid", targetUID);
        } else {
            return null;
        }
    }
}
