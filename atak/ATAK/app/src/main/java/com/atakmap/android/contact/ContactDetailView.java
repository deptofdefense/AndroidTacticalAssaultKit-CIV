
package com.atakmap.android.contact;

import android.content.SharedPreferences;
import androidx.fragment.app.Fragment;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;

public abstract class ContactDetailView extends Fragment {

    protected PointMapItem _marker;
    protected IndividualContact _contact;

    protected MapView _mapView;
    protected SharedPreferences _prefs;
    protected ContactDetailDropdown _parent;

    public void refresh(PointMapItem marker, IndividualContact contact) {
        cleanup();

        //TODO need IndividualContact
        _marker = marker;
        _contact = contact;

        //TODO look at CotInfoView.clean, set marker null, remove on point changed listener
        refresh();
    }

    protected abstract void refresh();

    protected abstract void cleanup();

    public void updateVisual() {
        refresh(_marker, _contact);
    }

    public void init(MapView mapView, SharedPreferences prefs,
            ContactDetailDropdown parent) {
        _mapView = mapView;
        _prefs = prefs;
        _parent = parent;
    }

    public PointMapItem getMarker() {
        return _marker;
    }
}
