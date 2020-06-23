
package com.atakmap.android.icons;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;

import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

/**
 * Contains list of iconsets and custom CoT mappings
 * 
 * 
 */
public class IconManagerDropdown extends DropDownReceiver {

    protected static final String TAG = "IconManagerDropdown";

    public static final String DISPLAY_DROPDOWN = "com.atakmap.android.icons.DISPLAY_DROPDOWN";

    private IconManagerView _view;

    public IconManagerDropdown(MapView mapView) {
        super(mapView);
    }

    @Override
    public void disposeImpl() {
        if (_view != null) {
            _view.dispose();
            _view = null;
        }
    }

    @Override
    public void onReceive(Context arg0, Intent intent) {
        Log.d(TAG, "Processing action: " + intent.getAction());
        if (DISPLAY_DROPDOWN.equals(intent.getAction())) {
            onShow();
        } else if (IconsMapAdapter.ICONSET_ADDED.equals(intent.getAction())
                || IconsMapAdapter.ICONSET_REMOVED.equals(intent.getAction())) {
            if (_view != null && isVisible()) {
                _view.refresh(getMapView());
            }
        }
    }

    public void onShow() {
        showDropDown(getView(), TWO_THIRDS_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                TWO_THIRDS_HEIGHT);
    }

    private IconManagerView getView() {
        if (_view == null) {
            LayoutInflater inflater = LayoutInflater.from(getMapView()
                    .getContext());
            _view = (IconManagerView) inflater.inflate(
                    R.layout.iconset_manager, null);
        }

        _view.refresh(getMapView());
        return _view;
    }
}
