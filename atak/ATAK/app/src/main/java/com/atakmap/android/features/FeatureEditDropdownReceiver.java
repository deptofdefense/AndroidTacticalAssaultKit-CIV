
package com.atakmap.android.features;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;

import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.map.layer.feature.DataSourceFeatureDataStore;

public class FeatureEditDropdownReceiver extends DropDownReceiver
        implements DropDown.OnStateListener {

    private static final String TAG = "FeatureEditDropdownReceiver";
    public static final String SHOW_EDIT = "com.atakmap.android.features.SHOW_EDIT";
    private final DataSourceFeatureDataStore _spatialDb;
    private final FeatureEditDetailsView _view;

    public FeatureEditDropdownReceiver(MapView mapView,
            DataSourceFeatureDataStore spatialDb) {
        super(mapView);
        _spatialDb = spatialDb;
        LayoutInflater inflater = (LayoutInflater) getMapView().getContext()
                .getSystemService(Service.LAYOUT_INFLATER_SERVICE);
        _view = (FeatureEditDetailsView) inflater
                .inflate(R.layout.edit_feature_view, null);
    }

    @Override
    protected void disposeImpl() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        long fid = intent.getLongExtra("fid", 0);
        String[] fsids = intent.getStringArrayExtra("fsids");
        String title = intent.getStringExtra("title");
        if (action == null)
            return;
        if (action.equals(SHOW_EDIT)) {
            if (fsids != null) {
                _view.setItems(fsids, title, _spatialDb);
            } else {
                _view.setItem(fid, title, _spatialDb);
            }
            setRetain(true);
            showDropDown(_view, THIRD_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                    HALF_HEIGHT, this);
        }
    }

    @Override
    public void onDropDownSelectionRemoved() {

    }

    @Override
    public void onDropDownClose() {

    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {

    }

    @Override
    public void onDropDownVisible(boolean v) {

    }
}
