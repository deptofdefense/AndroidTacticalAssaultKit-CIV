
package com.atakmap.android.vehicle.overhead;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;

import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.vehicle.VehicleMapComponent;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

public class OverheadDetailsReceiver extends DropDownReceiver implements
        OnStateListener {

    private static final String TAG = "OverheadDetailsReceiver";
    public static final String ACTION = "com.atakmap.android.vehicle.overhead.DETAILS";

    private String _prevUID;
    private OverheadDetailsView _view;
    private int _ignoreClose = 0;
    private OverheadMarker _marker;
    private final MapGroup _group;

    public OverheadDetailsReceiver(MapView mapView) {
        super(mapView);
        _group = VehicleMapComponent.getOverheadGroup(mapView);
    }

    @Override
    public void disposeImpl() {
    }

    @Override
    public void onReceive(Context ignoreCtx, Intent intent) {
        final String action = intent.getAction();
        final Bundle extras = intent.getExtras();
        if (action == null)
            return;

        if (action.equals(ACTION)) {
            if (extras == null)
                return;

            String uid = extras.getString("uid");
            if (uid != null) {
                MapItem item = _group.deepFindItem("uid", uid);
                if (item instanceof OverheadMarker) {
                    if (DropDownManager.getInstance().isTopDropDown(this)
                            && _prevUID != null) {
                        if (!_prevUID.equals(uid))
                            closeDropDown();
                        else {
                            if (!isVisible())
                                DropDownManager.getInstance().unHidePane();
                            return;
                        }
                    } else if (!isClosed())
                        closeDropDown();
                    showDetails((OverheadMarker) item);
                    _prevUID = uid;
                } else {
                    Log.d(TAG, "uid: " + uid + " not found in drawing group.");
                }
            }
        }
    }

    private void showDetails(final OverheadMarker marker) {
        if (_view != null) {
            _ignoreClose++;
            cleanup(true);
        }

        _marker = marker;

        LayoutInflater inflater = LayoutInflater
                .from(getMapView().getContext());

        OverheadDetailsView odv = (OverheadDetailsView) inflater.inflate(
                R.layout.overhead_marker_details, null);
        odv.setItem(getMapView(), _marker);
        setSelected(_marker, "");
        odv.setDropDownMapReceiver(this);

        showDropDown(odv, THREE_EIGHTHS_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                HALF_HEIGHT, this);

        _view = odv;
    }

    @Override
    public void onDropDownSelectionRemoved() {
        cleanup(false);
    }

    @Override
    public void onDropDownVisible(boolean v) {
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
        if (_ignoreClose > 0)
            _ignoreClose--;
        else
            cleanup(true);
    }

    private void cleanup(boolean persist) {

        if (_view != null) {
            _view.onClose();
            _view = null;
        }

        if (persist && _marker != null)
            _marker.persist(getMapView().getMapEventDispatcher(), null,
                    this.getClass());

        _prevUID = null;
        _marker = null;
    }
}
