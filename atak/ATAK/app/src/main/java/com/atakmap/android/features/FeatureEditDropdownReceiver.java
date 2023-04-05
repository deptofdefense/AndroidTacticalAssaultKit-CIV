
package com.atakmap.android.features;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.widget.Toast;

import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.feature.Adapters;
import com.atakmap.map.layer.feature.DataSourceFeatureDataStore;
import com.atakmap.map.layer.feature.FeatureDataStore2;

import java.util.Arrays;
import java.util.List;

import androidx.annotation.Nullable;

public class FeatureEditDropdownReceiver extends DropDownReceiver
        implements DropDown.OnStateListener {

    private static final String TAG = "FeatureEditDropdownReceiver";
    public static final String SHOW_EDIT = "com.atakmap.android.features.SHOW_EDIT";

    @Nullable
    private final FeatureDataStore2 _db;
    private final FeatureEditDetailsView _view;

    FeatureEditDropdownReceiver(MapView mapView) {
        this(mapView, (FeatureDataStore2) null);
    }

    public FeatureEditDropdownReceiver(MapView mapView, FeatureDataStore2 db) {
        super(mapView);
        _db = db;
        LayoutInflater inflater = (LayoutInflater) getMapView().getContext()
                .getSystemService(Service.LAYOUT_INFLATER_SERVICE);
        _view = (FeatureEditDetailsView) inflater
                .inflate(R.layout.edit_feature_view, mapView, false);
    }

    public FeatureEditDropdownReceiver(MapView mapView,
            DataSourceFeatureDataStore db) {
        this(mapView, Adapters.adapt(db));
    }

    /**
     * Show the feature style editor for a list of feature sets
     * @param title Title to show
     * @param featureSetIds Feature set IDs
     */
    public void show(String title, long[] featureSetIds) {
        if (_db == null)
            return;

        _view.setItems(featureSetIds, title, _db);
        if (_view.isClear()) {
            Log.e(TAG, "Failed to edit feature set IDs: "
                    + Arrays.toString(featureSetIds));
            Toast.makeText(_view.getContext(),
                    R.string.unable_to_edit_feature_set,
                    Toast.LENGTH_LONG).show();
            return;
        }

        show();
    }

    /**
     * Show the feature style editor for a specific feature
     * @param title Title to show
     * @param fid Feature ID
     */
    public void show(String title, long fid) {
        if (_db == null)
            return;

        _view.setItem(fid, title, _db);
        if (_view.isClear()) {
            Log.e(TAG, "Failed to edit feature ID: " + fid);
            Toast.makeText(_view.getContext(),
                    R.string.unable_to_edit_feature,
                    Toast.LENGTH_LONG).show();
            return;
        }

        show();
    }

    /**
     * Show the feature style editor for a number of features
     * @param title Title to display
     * @param requests List of requests
     */
    void show(String title, List<FeatureEditRequest> requests) {
        _view.setRequests(title, requests);
        if (_view.isClear()) {
            Log.e(TAG, "Failed to bulk edit features");
            Toast.makeText(_view.getContext(),
                    R.string.unable_to_edit_feature_set,
                    Toast.LENGTH_LONG).show();
            return;
        }

        show();
    }

    private void show() {
        setRetain(true);
        showDropDown(_view, THIRD_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                HALF_HEIGHT, this);
    }

    @Override
    protected void disposeImpl() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        long fid = intent.getLongExtra("fid", -1);
        long[] fsids = intent.getLongArrayExtra("fsids");
        String title = intent.getStringExtra("title");

        if (action == null)
            return;

        if (action.equals(SHOW_EDIT)) {
            if (fsids != null)
                show(title, fsids);
            else
                show(title, fid);
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
