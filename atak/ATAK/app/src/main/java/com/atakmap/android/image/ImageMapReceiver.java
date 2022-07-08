
package com.atakmap.android.image;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;

import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.annotations.ModifierApi;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;

public class ImageMapReceiver extends DropDownReceiver implements
        OnStateListener {

    public static final String IMAGE_DETAILS = "com.atakmap.android.image.IMAGE_DETAILS";

    private ImageDetailsView _genDetailsView;

    // starts off with mgrs
    private Marker _item;

    @ModifierApi(since = "4.5", target = "4.8", modifiers = {})
    public ImageMapReceiver(MapView mapView) {
        super(mapView);
    }

    @Override
    public void disposeImpl() {
        _genDetailsView = null;
        _item = null;
    }

    public void closeDrawingDropDown() {
        closeDropDown();
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();
        Bundle extras = intent.getExtras();

        if (IMAGE_DETAILS.equals(action) && extras != null) {
            String uid = extras.getString("uid");
            if (!FileSystemUtils.isEmpty(uid)) {
                MapItem item = getMapView().getMapItem(uid);
                if (item instanceof Marker) {
                    // Close other details that are open first, otherwise we'll have
                    // overwritten the info it needs to shut down when OnClose is called!
                    if (_item != null && this.isVisible()) {
                        closeDropDown();
                    }
                    _item = (Marker) item;
                    _showPointDetails(_item);
                }
            }
        }
    }

    private void _showPointDetails(Marker point) {
        LayoutInflater inflater = LayoutInflater
                .from(getMapView().getContext());

        _genDetailsView = (ImageDetailsView) inflater.inflate(
                R.layout.image_details_view, null);

        _genDetailsView.setPoint(getMapView(), point);
        _genDetailsView.setDropDownMapReceiver(this);
        setRetain(true);
        setSelected(point, "asset:/icons/outline.png");
        showDropDown(_genDetailsView, THREE_EIGHTHS_WIDTH, FULL_HEIGHT,
                FULL_WIDTH, THREE_EIGHTHS_HEIGHT, this);
    }

    @Override
    public void onDropDownSelectionRemoved() {
        cleanup(false);
    }

    private void cleanup(boolean persist) {
        if (_genDetailsView != null) {
            _genDetailsView.onClose();
            _genDetailsView = null;

            if (_item != null && persist) {
                _item.setMetaString("callsign", _item.getTitle());
                _item.persist(getMapView().getMapEventDispatcher(), null,
                        this.getClass());
            }
        }
    }

    @Override
    public void onDropDownVisible(boolean v) {
        if (v && _genDetailsView != null) {
            _genDetailsView.updateVisual();
        }
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
        cleanup(true);
    }
}
