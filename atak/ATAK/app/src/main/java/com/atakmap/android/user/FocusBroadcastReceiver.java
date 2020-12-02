
package com.atakmap.android.user;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.MetaMapPoint;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.PointMapItem.OnPointChangedListener;
import com.atakmap.android.maps.Shape;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.AtakMapController;
import com.atakmap.math.Rectangle;

public class FocusBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "FocusBroadcastReceiver";

    public static final String FOCUS = "com.atakmap.android.maps.FOCUS";

    // zoom ~2000
    private static final double FOCUS_MAP_SCALE_VALUE = 1.0d / 197219.1758d;

    public FocusBroadcastReceiver(MapView mapView) {
        _mapView = mapView;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null) {
            switch (action) {
                case "com.atakmap.android.maps.FOCUS":
                    _focus(context, intent);
                    break;
                case "com.atakmap.android.maps.UNFOCUS":
                case "com.atakmap.android.maps.UNFOCUS_FOR_FINE_ADJUST":
                    _unfocus(context, intent);
                    break;
                case "com.atakmap.android.maps.FOCUS_UNFOCUS": { // Courtesy of
                    // Andrew
                    Intent i = new Intent();
                    i.setAction("com.atakmap.android.maps.CLOSE_DROPDOWN");
                    AtakBroadcast.getInstance().sendBroadcast(i);

                    _focus_unfocus(context, intent);
                    break;
                }
                case "com.atakmap.android.maps.FOCUS_DISPLAY": {
                    _focus_unfocus(context, intent);
                    Intent i = new Intent();
                    i.setAction("com.atakmap.android.maps.CLOSE_DROPDOWN");
                    AtakBroadcast.getInstance().sendBroadcast(i);
                    Intent i2 = intent;
                    i2.setAction("com.atakmap.maps.images.DISPLAY");
                    AtakBroadcast.getInstance().sendBroadcast(i2);
                    break;
                }
            }
        }
    }

    private void _focus(Context context, Intent intent) {
        boolean useTightZoom = false;
        if (intent.hasExtra("useTightZoom")) {
            useTightZoom = intent.getBooleanExtra("useTightZoom", false);
        }

        if (intent.hasExtra("point")) {
            try {
                GeoPoint point = GeoPoint.parseGeoPoint(intent
                        .getStringExtra("point"));
                PointMapItem mapFocusPoint = _getMapFocusPoint();
                mapFocusPoint.setPoint(point);
                _setFocusPoint(mapFocusPoint, useTightZoom);
            } catch (Exception ex) {
                Log.e(TAG, "error: ", ex);
            }
        } else if (intent.hasExtra("uid")) {
            String uid = intent.getStringExtra("uid");
            if (uid != null) {

                // first see if this is a point item
                PointMapItem pointItem = _getPointItemFocusPoint(uid);
                if (pointItem != null
                        && !pointItem.getMetaBoolean("ignoreFocus", false)) {
                    _setFocusPoint(pointItem, useTightZoom);
                } else {
                    // next check if a menu point is set on the item
                    MapItem mapItem = _mapView.getMapItem(uid);
                    if (mapItem == null) {
                        // slower way - kml/shp
                        mapItem = _mapView.getRootGroup().deepFindItem("uid",
                                uid);
                    }
                    if (mapItem == null) {
                        Log.w(TAG, "Unable to focus on: " + uid);
                        return;
                    }

                    String mp = mapItem.getMetaString("menu_point", null);
                    if (!FileSystemUtils.isEmpty(mp)) {
                        GeoPoint gp = GeoPoint.parseGeoPoint(mp);
                        PointMapItem mapFocusPoint = _getMapFocusPoint();
                        mapFocusPoint.setPoint(gp);
                        _setFocusPoint(mapFocusPoint, useTightZoom);
                        return;
                    }

                    // finally see if we can find a center point
                    GeoPoint center = null;
                    if (mapItem instanceof Shape) {
                        center = ((Shape) mapItem).getCenter().get();
                    }

                    // check for shape center point
                    if (center != null) {
                        // similar to _focus_unfocus()
                        _mapView.getMapController().panTo(center, true);
                        return;
                    }

                    Log.w(TAG, "Unable to focus (2) on: " + uid);
                }
            }
        }
    }

    private void _unfocus(Context context, Intent intent) {
        _setFocusPoint(null, false);
    }

    private void _focus_unfocus(Context context, Intent intent) {
        GeoPoint gp = null;

        if (intent.hasExtra("uid")) {
            MapItem mapItem = _mapView.getMapItem(intent.getStringExtra("uid"));
            if (mapItem instanceof Marker)
                gp = ((Marker) mapItem).getPoint();
        }
        if (gp == null && intent.hasExtra("lat") && intent.hasExtra("lon")) {
            gp = new GeoPoint(intent.getDoubleExtra("lat", 0),
                    intent.getDoubleExtra("lon", 0));
        }

        if (gp != null)
            _mapView.getMapController().panTo(gp, true);
    }

    private PointMapItem _getPointItemFocusPoint(String uid) {
        MapItem mapItem = _mapView.getMapItem(uid);
        PointMapItem pointItem = null;
        if (mapItem instanceof PointMapItem) {
            pointItem = (PointMapItem) mapItem;
        }
        return pointItem;
    }

    private MetaMapPoint _getMapFocusPoint() {
        class _Indirect {
            MetaMapPoint p;
        }
        final _Indirect indirect = new _Indirect();
        MapGroup.deepMapItems(_mapView.getRootGroup(),
                new MapGroup.OnItemCallback<MetaMapPoint>(
                        MetaMapPoint.class) {
                    @Override
                    public boolean onMapItem(MetaMapPoint item) {
                        // XXX - should be looking for "MAP_FOCUS" ???
                        if (item.hasMetaValue("uid") &&
                                item.getUID().equals("FOCUS")) {
                            indirect.p = item;
                            return true;
                        }
                        return false;
                    }

                });

        if (indirect.p == null) {
            indirect.p = new MetaMapPoint(
                    GeoPointMetaData.wrap(GeoPoint.ZERO_POINT), "MAP_FOCUS");
        }
        return indirect.p;
    }

    private void _setFocusPoint(PointMapItem pointItem, boolean useTightZoom) {
        if (_focusPoint != null) {
            _focusPoint.removeOnPointChangedListener(_pointChangedListener);
        }
        _focusPoint = pointItem;
        if (_focusPoint != null) {
            _focusPoint.addOnPointChangedListener(_pointChangedListener);
            AtakMapController ctrl = _mapView.getMapController();
            GeoPoint panTo = _focusPoint.getPoint();
            panTo = _mapView.getRenderElevationAdjustedPoint(panTo,
                    pointItem.getHeight());
            // if the map is tilted and the point is in view, do a relative
            // pan/zoom
            PointF focusXY = _mapView.forward(panTo);
            if (_mapView.getMapTilt() > 0d &&
                    Rectangle.contains(0, 0,
                            _mapView.getWidth(), _mapView.getHeight(),
                            focusXY.x, focusXY.y)) {

                // XXX - can this be combined into a single animation?
                ctrl.panBy(focusXY.x - ctrl.getFocusX(),
                        focusXY.y - ctrl.getFocusY(),
                        false);

                // pan/zoom
                if (useTightZoom) {
                    double scale = _mapView.getMapScale();
                    if (scale < FOCUS_MAP_SCALE_VALUE)
                        scale = FOCUS_MAP_SCALE_VALUE;
                    final double minItemGsd = pointItem.getMetaDouble(
                            "minMapGsd",
                            Double.NaN);
                    if (!Double.isNaN(minItemGsd)
                            && _mapView.mapResolutionAsMapScale(minItemGsd)
                                    * 2d > scale)
                        scale = _mapView.mapResolutionAsMapScale(minItemGsd)
                                * 2d;
                    ctrl.zoomTo(scale, true);
                }
            } else {
                // clear the tilt
                ctrl.tiltTo(0d, false);

                //do not notify/unlock if this is the currently locked item
                boolean bNotify = !isFocusItemLocked();

                // pan/zoom
                if (useTightZoom) {
                    double scale = _mapView.getMapScale();
                    if (scale < FOCUS_MAP_SCALE_VALUE)
                        scale = FOCUS_MAP_SCALE_VALUE;
                    final double minItemGsd = pointItem.getMetaDouble(
                            "minMapGsd",
                            Double.NaN);
                    if (!Double.isNaN(minItemGsd)
                            && _mapView.mapResolutionAsMapScale(minItemGsd)
                                    * 2d > scale)
                        scale = _mapView.mapResolutionAsMapScale(minItemGsd)
                                * 2d;
                    ctrl.panZoomTo(panTo, scale, true, bNotify);
                } else {
                    ctrl.panTo(panTo, true, bNotify);
                }
            }
        }
    }

    private boolean isFocusItemLocked() {
        return _focusPoint != null
                && _focusPoint.getMetaBoolean("camLocked", false);
    }

    private final OnPointChangedListener _pointChangedListener = new OnPointChangedListener() {
        @Override
        public void onPointChanged(PointMapItem item) {
            GeoPoint panTo = item.getPoint();
            panTo = _mapView.getRenderElevationAdjustedPoint(panTo);
            _mapView.getMapController().panTo(panTo, true,
                    !isFocusItemLocked());
        }
    };

    private final MapView _mapView;
    private PointMapItem _focusPoint;
}
