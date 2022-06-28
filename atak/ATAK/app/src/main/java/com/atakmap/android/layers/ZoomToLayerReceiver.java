
package com.atakmap.android.layers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.CameraController;

import java.util.List;

public class ZoomToLayerReceiver extends BroadcastReceiver {
    private final MapView _mapView;
    private final LayerSelectionAdapter _layersAdapter;

    public ZoomToLayerReceiver(MapView mapView, LayerSelectionAdapter mobile) {
        _mapView = mapView;
        _layersAdapter = mobile;
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        GeoPoint point = null;

        if (intent.hasExtra("point")) {
            String pointStr = intent.getStringExtra("point");
            point = GeoPoint.parseGeoPoint(pointStr);
        } else if (intent.hasExtra("uid")) {
            String uid = intent.getStringExtra("uid");
            if (uid != null) {
                MapItem item = _mapView.getMapItem(uid);
                if (item instanceof PointMapItem) {
                    point = ((PointMapItem) item).getPoint();
                }
            }
        }

        if (point != null) {

            List<LayerSelection> selections = _layersAdapter
                    .getAllSelectionsAt(point);
            boolean isLockedOnLayer = _layersAdapter.isLocked();
            LayerSelection best = null;

            if (isLockedOnLayer) {
                best = _layersAdapter.getSelected();
            } else {
                for (LayerSelection sl : selections) {
                    if (best == null
                            || sl.getMinRes() < best.getMinRes()) {
                        best = sl;
                    }
                }
            }

            if (best != null && intent.getExtras() != null
                    && !intent.getExtras().containsKey("noZoom")) {
                this.zoomToSelection(best, point,
                        intent.getBooleanExtra("noNegativeZoom", false));
            }

            CameraController.Programmatic.panTo(
                    _mapView.getRenderer3(), point, true);
        }
    }

    private void zoomToSelection(LayerSelection layerSelection,
            GeoPoint specificPoint,
            boolean noNegativeZoom) {

        _layersAdapter.setSelected(layerSelection);

        // If the user provided a point, we'll zoom all the way in on that point.
        // If not, zoom out to provide an overview of the entire layer.
        boolean pointProvided = specificPoint != null;
        if (specificPoint == null) {

            specificPoint = _mapView.getPoint().get();
            if (!LayersMapComponent.isInView(_mapView, layerSelection)
                    || _mapView.getMapResolution() > layerSelection
                            .getMinRes()) {

                specificPoint = LayerSelection.boundsGetCenterNearest(
                        layerSelection, _mapView.getCenterPoint().get());
            }
        }

        // XXX - determine if there is online tiles
        if (layerSelection != null/* && !layerSelection.isRemote()*/) {
            CameraController.Programmatic.panTo(
                    _mapView.getRenderer3(), specificPoint, true);
            _zoomToNaturalLevel(_mapView, layerSelection, noNegativeZoom,
                    !pointProvided);
        }

        _layersAdapter.setSelected(layerSelection);

        _layersAdapter.sort();
    }

    /*
     * @param zoomToOverview true to zoom to the lowest-resolution layer of the tileset, false to
     * zoom all the way in to the highest res.
     */
    private static void _zoomToNaturalLevel(MapView mapView,
            LayerSelection tsInfo, boolean noNegativeZoom,
            boolean zoomToOverview) {
        final double targetGsd = zoomToOverview ? tsInfo.getMinRes()
                : tsInfo.getMaxRes();
        final double scale = mapView.mapResolutionAsMapScale(targetGsd);

        // don't zoom negative
        if (noNegativeZoom && scale < mapView.getMapScale()) {
            return;
        }

        CameraController.Programmatic.zoomTo(mapView.getRenderer3(),
                scale, true);
    }

}
