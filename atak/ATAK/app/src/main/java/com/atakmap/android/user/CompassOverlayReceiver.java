
package com.atakmap.android.user;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapItem.OnGroupChangedListener;
import com.atakmap.android.maps.MapItem.OnVisibleChangedListener;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.PointMapItem.OnPointChangedListener;
import com.atakmap.android.toolbars.BullseyeDropDownReceiver;
import com.atakmap.android.toolbars.BullseyeTool;
import com.atakmap.android.widgets.AutoSizeAngleOverlayShape;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.List;

public class CompassOverlayReceiver extends DropDownReceiver {
    private static final String UID_PREFIX = "compassOverlay.";

    private MapItem hostileItem;

    final MapView _mapView;

    /**
     * Initialize the receiver
     */
    public CompassOverlayReceiver(MapView mapView) {
        super(mapView);
        _mapView = getMapView();
    }

    @Override
    public void disposeImpl() {
    }

    /**
     * called when user selects the circle option from the HARP radial menu
     */
    @Override
    public void onReceive(Context context, Intent intent) {

        // find the group that the harp icon is in
        String uid = intent.getStringExtra("uid");
        List<MapItem> itemList = MapView._mapView.getRootGroup().deepFindItems(
                "uid", uid);
        if (itemList.size() != 1) {
            Toast.makeText(context, R.string.cant_find_item,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        //check if it is for a compass overlay
        if (intent.hasExtra("compassOverlay")) {
            hostileItem = itemList.get(0);
            showBearingCircle();
        } else if (intent.hasExtra("bullseye")) {
            hostileItem = itemList.get(0);
            showBullseye();
        }
    }

    private void showBullseye() {
        if (hostileItem instanceof Marker) {
            //check to see if a bullseye is already showing and toggle off
            if (hostileItem.hasMetaValue("bullseyeOverlay")) {
                BullseyeTool.removeOverlay((Marker) hostileItem, false);
                return;
            }

            //check to see if a bearing circle is already showing
            MapItem bearingCircleMI = MapGroup.findItemWithMetaString(
                    MapView._mapView.getRootGroup(), "uid", UID_PREFIX
                            + hostileItem.getUID());
            if (bearingCircleMI != null) {
                MapView._mapView.getRootGroup().removeItem(bearingCircleMI);
                hostileItem.removeMetaData("hostileBearingCircle");
                removeListeners(hostileItem);
            }

            MapGroup bullseyeGroup = _mapView.getRootGroup().findMapGroup(
                    "Range & Bearing");
            if (bullseyeGroup != null) {
                MapItem mi = bullseyeGroup.findItem("uid",
                        hostileItem.getMetaString("bullseyeUID", ""));
                if (mi != null) {
                    Intent bullseyeIntent = new Intent();
                    bullseyeIntent
                            .setAction(
                                    BullseyeDropDownReceiver.DROPDOWN_TOOL_IDENTIFIER);
                    bullseyeIntent.putExtra("edit", true);
                    bullseyeIntent.putExtra("marker_uid", hostileItem.getUID());
                    AtakBroadcast.getInstance().sendBroadcast(bullseyeIntent);
                    return;
                }
            }

            //Show details view
            Intent bullseyeIntent = new Intent();
            bullseyeIntent
                    .setAction(
                            BullseyeDropDownReceiver.DROPDOWN_TOOL_IDENTIFIER);
            bullseyeIntent.putExtra("marker_uid", hostileItem.getUID());
            AtakBroadcast.getInstance().sendBroadcast(bullseyeIntent);
        }
    }

    private void showBearingCircle() {
        if (hostileItem instanceof Marker) {
            MapItem mi = MapGroup.findItemWithMetaString(
                    MapView._mapView.getRootGroup(), "uid", UID_PREFIX
                            + hostileItem.getUID());
            if (mi != null) {
                MapView._mapView.getRootGroup().removeItem(mi);
                hostileItem.removeMetaData("hostileBearingCircle");
                removeListeners(hostileItem);
                return;
            }

            //check to see if a bullseye is already showing
            if (hostileItem.hasMetaValue("bullseyeOverlay"))
                BullseyeTool.removeOverlay((Marker) hostileItem, false);

            Marker hostileMarker = (Marker) hostileItem;
            GeoPointMetaData center = hostileMarker.getGeoPointMetaData();

            final AutoSizeAngleOverlayShape compassDial = new AutoSizeAngleOverlayShape(
                    UID_PREFIX + hostileItem.getUID());
            compassDial.setVisible(true);
            compassDial.setCenter(center);
            compassDial.setProjectionProportion(true);
            compassDial.setTitle("HostileBearingCompass");
            compassDial.setMagneticAzimuth();
            compassDial.setStrokeColor(0xFFFFFFFF);
            compassDial.setFillColor(0xFFFFFFFF);
            hostileItem.setMetaBoolean("hostileBearingCircle", true);

            MapView._mapView.getRootGroup().addItem(compassDial);

            //add the listeners for changes in the Hostile marker so the compass 
            //overlay can be changed accordingly 
            hostileItem.addOnGroupChangedListener(hostileRemovedListener);
            ((Marker) hostileItem)
                    .addOnPointChangedListener(hostileMoveListener);
            hostileItem
                    .addOnVisibleChangedListener(hostileVisibilityListener);

        }
    }

    /**
     * Remove all the listeners for the Compass overlay on the map item
     *  
     * @param hostileItem - the item to remove the listeners from
     */
    private void removeListeners(MapItem hostileItem) {
        hostileItem.removeOnGroupChangedListener(hostileRemovedListener);
        ((Marker) hostileItem)
                .removeOnPointChangedListener(hostileMoveListener);
        hostileItem
                .removeOnVisibleChangedListener(hostileVisibilityListener);
    }

    private final OnGroupChangedListener hostileRemovedListener = new MapItem.OnGroupChangedListener() {
        @Override
        public void onItemAdded(MapItem item, MapGroup group) {
            //do nothing
        }

        @Override
        public void onItemRemoved(MapItem item, MapGroup group) {
            final MapItem mi = MapGroup.findItemWithMetaString(
                    MapView._mapView.getRootGroup(), "uid",
                    UID_PREFIX + item.getUID());
            if (mi instanceof AutoSizeAngleOverlayShape) {
                MapView._mapView.getRootGroup().removeItem(mi);
                removeListeners(item);
            }
        }
    };

    private final OnPointChangedListener hostileMoveListener = new OnPointChangedListener() {
        @Override
        public void onPointChanged(PointMapItem item) {
            //move the angle overlay
            final MapItem mi = MapGroup.findItemWithMetaString(
                    MapView._mapView.getRootGroup(), "uid",
                    UID_PREFIX + item.getUID());
            if (mi instanceof AutoSizeAngleOverlayShape) {
                AutoSizeAngleOverlayShape compassDial = (AutoSizeAngleOverlayShape) mi;
                compassDial.setCenter(item.getGeoPointMetaData());
                compassDial.save();
            }
        }
    };

    private final OnVisibleChangedListener hostileVisibilityListener = new OnVisibleChangedListener() {
        @Override
        public void onVisibleChanged(MapItem item) {
            //move the angle overlay
            final MapItem mi = MapGroup.findItemWithMetaString(
                    MapView._mapView.getRootGroup(), "uid",
                    UID_PREFIX + item.getUID());
            if (mi instanceof AutoSizeAngleOverlayShape) {
                AutoSizeAngleOverlayShape compassDial = (AutoSizeAngleOverlayShape) mi;
                compassDial.setVisible(item.getVisible());
                compassDial.save();
            }
        }
    };

}
