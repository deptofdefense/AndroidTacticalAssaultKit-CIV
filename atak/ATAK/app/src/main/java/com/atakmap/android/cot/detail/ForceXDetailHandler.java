
package com.atakmap.android.cot.detail;

import com.atakmap.android.maps.Ellipse;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.overlay.DefaultMapGroupOverlay;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;

import java.util.UUID;

public class ForceXDetailHandler extends CotDetailHandler {

    public static final String TAG = "ForceXDetailHandler";

    private final MapGroup _group;

    public ForceXDetailHandler(MapView mapView) {
        super("geo_info");
        MapGroup root = mapView.getRootGroup().findMapGroup("Drawing Objects");
        MapGroup group = root.findMapGroup("ForceX");
        if (group == null) {
            String iconUri = "asset://icons/circle.png";
            group = root.addGroup("ForceX");
            mapView.getMapOverlayManager().addShapesOverlay(
                    new DefaultMapGroupOverlay(mapView, group, iconUri));
        }
        _group = group;
    }

    @Override
    public boolean isSupported(MapItem item, CotEvent event, CotDetail detail) {
        return item instanceof Marker;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {

        Marker marker = (Marker) item;
        CotDetail color = detail.getFirstChildByName(0, "color");
        int stroke = 0xFFFFFFFF;

        if (color != null)
            stroke = (Integer.parseInt(color.getAttribute("blue"))) +
                    (Integer.parseInt(color.getAttribute("green")) << 8) +
                    (Integer.parseInt(color.getAttribute("red")) << 16) +
                    (255 << 24);

        String label = "";
        CotDetail labelDetail = detail.getFirstChildByName(0, "label");
        if (labelDetail != null)
            label = labelDetail.getAttribute("string");
        marker.setTitle(label);

        CotDetail ellipse = detail.getFirstChildByName(0, "ellipse");

        if (ellipse != null) {
            Ellipse ell = (Ellipse) _group.deepFindItem("ownerUID",
                    event.getUID());
            if (ell == null) {
                ell = new Ellipse(UUID.randomUUID().toString());
                deleteShapeOnRemoval(marker, _group, ell);
                deleteMarkerOnRemoval(ell, _group, marker);
                ell.setStrokeWeight(3);
                ell.setMetaString("ownerUID", event.getUID());
                ell.setTitle(marker.getTitle());
                ell.setMetaString("iconUri", "asset://icons/circle.png");
                ell.setMetaString("menu", "menus/t-s-v-e.xml");
                ell.setMetaBoolean("removable", true);
                ell.setMetaBoolean("addToObjList", false);

                //marker.setMetaBoolean("addToObjList", false);

                marker.setMovable(false);

                _group.addItem(ell);
                _group.addItem(marker);

                addVisibleListener(ell, marker);
                addVisibleListener(marker, ell);
            }

            if (!marker.hasMetaValue("menu")) {
                marker.setMetaString("menu", "menus/t-s-v-e.xml");
            }

            double major = Double
                    .parseDouble(ellipse.getAttribute("major"));
            double minor = Double
                    .parseDouble(ellipse.getAttribute("minor"));
            double angle = Double
                    .parseDouble(ellipse.getAttribute("angle"));

            ell.setDimensions(marker.getGeoPointMetaData(), minor / 2,
                    major / 2, angle);

            ell.setStrokeColor(stroke);
        }

        return ImportResult.SUCCESS;
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        return false;
    }

    private <T extends Shape> T deleteShapeOnRemoval(final Marker marker,
            final MapGroup group, final T shape) {
        marker.addOnGroupChangedListener(new MapItem.OnGroupChangedListener() {
            @Override
            public void onItemAdded(MapItem item, MapGroup markerGroup) {
                if (item == marker && group != null) {
                    shape.removeFromGroup();
                    group.addItem(shape);
                }
            }

            @Override
            public void onItemRemoved(MapItem item, MapGroup markerGroup) {
                if (shape != null) {
                    Log.d(TAG, "Item " + item.getUID()
                            + " was removed, removing associated "
                            + shape.getClass().getSimpleName());
                    if (item == marker && group != null)
                        group.removeItem(shape);
                    else
                        Log.w(TAG,
                                "Problem removing shape associated with item "
                                        + item.getUID());
                }
            }
        });
        return shape;
    }

    private void deleteMarkerOnRemoval(final Ellipse ell,
            final MapGroup group, final Marker marker) {
        ell.addOnGroupChangedListener(new MapItem.OnGroupChangedListener() {
            @Override
            public void onItemAdded(MapItem item, MapGroup mapGroup) {
            }

            @Override
            public void onItemRemoved(MapItem item, MapGroup mapGroup) {
                if (marker != null) {
                    Log.d(TAG, "Item " + item.getUID()
                            + " was removed, removing associated Marker");
                    if (item == ell && group != null)
                        group.removeItem(marker);
                    else
                        Log.w(TAG,
                                "Problem removing Marker associated with item "
                                        + item.getUID());
                }
            }
        });
    }

    private void addVisibleListener(final MapItem parent, final MapItem child) {
        parent.addOnVisibleChangedListener(
                new MapItem.OnVisibleChangedListener() {
                    @Override
                    public void onVisibleChanged(MapItem item) {
                        child.setVisible(parent.getVisible());
                    }
                });
    }
}
