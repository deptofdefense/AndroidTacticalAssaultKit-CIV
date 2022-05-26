
package com.atakmap.android.user.icon;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;

import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.icons.UserIcon;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.user.PlacePointTool;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoPoint;

/**
 * Manage Spot Map intents
 */
public class SpotMapReceiver extends DropDownReceiver implements
        OnStateListener {

    private static final String TAG = "SpotMapReceiver";

    public static final String SPOT_DETAILS = "com.atakmap.android.user.icon.SPOT_DETAILS";
    public static final String PLACE_SPOT = "com.atakmap.android.user.icon.PLACE_SPOT";
    public static final String TOGGLE_LABEL = "com.atakmap.android.maps.TOGGLE_LABEL";

    public static final String SPOT_MAP_POINT_COT_TYPE = "b-m-p-s-m";

    private static MapGroup _spotGroup;
    private SpotMapPointDetailsView _genDetailsView;

    // starts off with mgrs
    private Marker _item;

    public SpotMapReceiver(MapView mapView, MapGroup spotGroup) {
        super(mapView);
        _spotGroup = spotGroup;
    }

    public static MapGroup getSpotGroup() {
        return _spotGroup;
    }

    @Override
    public void disposeImpl() {
        _spotGroup = null;
        _genDetailsView = null;
        _item = null;
    }

    /**
     * @deprecated Use {@link #closeDropDown()} instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.5", forRemoval = true, removeAt = "4.8")
    public void closeDrawingDropDown() {
        closeDropDown();
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();
        String uid = intent.getStringExtra("uid");
        MapItem item = !FileSystemUtils.isEmpty(uid) ? _spotGroup
                .deepFindUID(uid) : null;
        Marker marker = item instanceof Marker ? (Marker) item : null;

        // Open spot map details
        if (SPOT_DETAILS.equals(action) && marker != null) {
            // Close other details that are open first, otherwise we'll have
            // overwritten the info it needs to shut down when OnClose is called!
            boolean close = false;
            if (DropDownManager.getInstance().isTopDropDown(this)) {
                if (_item != null && _item != marker)
                    close = true;
                else if (_item == marker) {
                    if (!isVisible())
                        DropDownManager.getInstance().unHidePane();
                    return;
                }
            } else if (!isClosed())
                close = true;

            if (close) {
                // Close the drop-down and show the updated details the next frame
                final Marker m = marker;
                closeDropDown();
                getMapView().post(new Runnable() {
                    @Override
                    public void run() {
                        _showPointDetails(_item = m);
                    }
                });
            } else {
                // Show point details immediately
                _showPointDetails(_item = marker);
            }
        }

        // Toggle marker label
        else if (TOGGLE_LABEL.equals(action) && marker != null) {
            if (FileSystemUtils.isEquals(marker.getMetaString(
                    UserIcon.IconsetPath, null),
                    SpotMapPalletFragment.LABEL_ONLY_ICONSETPATH))
                return; // Don't toggle label on label-only markers
            marker.setShowLabel(marker.hasMetaValue("hideLabel"));
            marker.persist(getMapView().getMapEventDispatcher(), null,
                    SpotMapReceiver.class);
        }

        // Place spot map marker
        else if (PLACE_SPOT.equals(action)) { //TODO: AS.  This probably could be reworked
            //Based largely on PlaceBroadcastReceiver
            String pointString = intent.getStringExtra("point");
            if (pointString != null) {
                boolean addToGroup = marker == null;
                String callsign = PlacePointTool
                        .genCallsign(null);
                if (intent.hasExtra("callsign")) {
                    callsign = intent.getStringExtra("callsign");
                }
                if (intent.hasExtra("prefix")) {
                    String prefix = intent.getStringExtra("prefix");
                    int count = PlacePointTool.getCount(prefix,
                            _spotGroup.deepFindItems("type",
                                    SPOT_MAP_POINT_COT_TYPE));
                    callsign = prefix + " " + count;
                }

                String how = "h-g-i-g-o";
                if (intent.hasExtra("how")) {
                    how = intent.getStringExtra("how");
                }

                GeoPoint location = GeoPoint.parseGeoPoint(pointString);
                if (marker == null) {
                    marker = new Marker(location, uid);
                    marker.setStyle(marker.getStyle()
                            | Marker.STYLE_MARQUEE_TITLE_MASK);
                    marker.setMetaString("how", how);
                    marker.setType(SPOT_MAP_POINT_COT_TYPE);
                    marker.setMetaString("entry", "user");

                    // Full mutability
                    marker.setMetaBoolean("editable", true);
                    marker.setMovable(true);
                    marker.setMetaBoolean("removable", true);
                } else {
                    marker.setPoint(location);
                }

                marker.setTitle(callsign);
                marker.setMetaString("callsign", callsign);
                marker.setMetaBoolean("readiness",
                        intent.getBooleanExtra("readiness", true));
                marker.setMetaBoolean("archive", true);

                int color = intent.getIntExtra("color", Color.WHITE);
                marker.setMetaInteger("color", color);

                if (intent.hasExtra(UserIcon.IconsetPath)) {
                    marker.setMetaString(UserIcon.IconsetPath,
                            intent.getStringExtra(UserIcon.IconsetPath));
                }

                if (addToGroup) {
                    _spotGroup.addItem(marker);
                }

                boolean showEditor = true;
                if (intent.hasExtra("show_cot_details")) {
                    String details = intent.getStringExtra("show_cot_details");
                    if (details.equals("false")) {
                        showEditor = false;
                    }
                }

                // dispatch a refresh
                marker.refresh(getMapView().getMapEventDispatcher(), null,
                        this.getClass());
                marker.persist(getMapView().getMapEventDispatcher(), null,
                        this.getClass());

                if (showEditor) {
                    _showPointDetails(marker);
                }

                // Added by Tim Lopes: Send out an intent when placement is completed so others may do
                // things with the new object
                Intent new_cot_intent = new Intent();
                new_cot_intent.setAction("com.atakmap.android.maps.COT_PLACED");
                new_cot_intent.putExtra("uid", uid);
                AtakBroadcast.getInstance().sendBroadcast(new_cot_intent);
            }
        }
    }

    private void _showPointDetails(Marker point) {
        LayoutInflater inflater = (LayoutInflater) getMapView().getContext()
                .getSystemService(
                        Service.LAYOUT_INFLATER_SERVICE);
        _genDetailsView = (SpotMapPointDetailsView) inflater.inflate(
                R.layout.spot_point_details_view, null);

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
