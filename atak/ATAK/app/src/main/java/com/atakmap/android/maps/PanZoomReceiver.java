
package com.atakmap.android.maps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;

import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.CameraController;
import com.atakmap.map.MapRenderer2;
import com.atakmap.map.MapSceneModel;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.coremap.maps.coords.GeoCalculations;

public class PanZoomReceiver extends BroadcastReceiver {
    public static final String TAG = "PanZoomReceiver";

    private final MapView _mapView;

    public PanZoomReceiver(MapView mapView) {
        _mapView = mapView;
    }

    @Override
    public void onReceive(Context arg0, Intent arg1) {
        final boolean snap = arg1.getBooleanExtra("snap", false);
        final boolean terrainAdj = arg1.getBooleanExtra("adjustForTerrain",
                false);

        GeoPoint panTo = null;
        if (arg1.hasExtra("point"))
            panTo = GeoPoint.parseGeoPoint(arg1.getStringExtra("point"));

        if (arg1.hasExtra("shape")) {
            try {
                String[] spts = arg1.getStringArrayExtra("shape");
                if (spts != null) {
                    GeoPoint[] pts = new GeoPoint[spts.length];
                    for (int i = 0; i < pts.length; i++)
                        pts[i] = GeoPoint.parseGeoPoint(spts[i]);
                    ATAKUtilities.scaleToFit(_mapView, pts,
                            _mapView.getWidth(), _mapView.getHeight());
                }
            } catch (Exception e) {
                // prior to 3.3 we used a Parcelable call to obtain the actual
                // GeoPoint, but this broke when we moved over to 
                // LocalBroadcasts.    Please see bug 5929 for more information.
                Log.d(TAG, "error occurred getting the points", e);
            }
            return;
        }

        final double mapScale = arg1.getDoubleExtra("scale", Double.NaN);

        // in case someone does not pass in panTo point
        if (panTo == null) {
            if (!Double.isNaN(mapScale))
                CameraController.Programmatic.zoomTo(_mapView.getRenderer3(),
                        mapScale, true);
            return;
        }

        // adjust for terrain when doing pan if altitude is not specified
        if (terrainAdj && Double.isNaN(panTo.getAltitude())) {
            panTo = new GeoPoint(panTo, GeoPoint.Access.READ_WRITE);
            panTo.set(ElevationManager.getElevation(panTo.getLatitude(),
                    panTo.getLongitude(), null));
        }

        if (snap) {
            CameraController.Programmatic.panTo(_mapView.getRenderer3(), panTo,
                    true);
            if (!Double.isNaN(mapScale))
                CameraController.Programmatic.zoomTo(_mapView.getRenderer3(),
                        mapScale, false);
        } else if (!Double.isNaN(mapScale)) {
            _mapView.getMapController().panZoomTo(panTo, mapScale, true);
        } else {
            CameraController.Programmatic.panTo(_mapView.getRenderer3(), panTo,
                    true);
        }
    }

    public static double estimateScaleToFitResolution(MapView map,
            GeoPoint[] pts, int widthOnScreen, int heightOnScreen) {
        final int[] e = GeoCalculations.findExtremes(pts, 0, pts.length);
        return estimateScaleToFitResolution(map,
                new Envelope(pts[e[0]].getLongitude(),
                        pts[e[3]].getLatitude(),
                        0d,
                        pts[e[2]].getLongitude(),
                        pts[e[1]].getLatitude(),
                        0d),
                widthOnScreen, heightOnScreen);
    }

    public static double estimateScaleToFitResolution(MapView map, Envelope mbb,
            int widthOnScreen, int heightOnScreen) {
        final MapSceneModel sm = map.getRenderer3().getMapSceneModel(
                false, MapRenderer2.DisplayOrigin.UpperLeft);
        MapSceneModel centered = new MapSceneModel(map.getDisplayDpi(),
                sm.width,
                sm.height,
                sm.mapProjection,
                new GeoPoint((mbb.maxY + mbb.minY) / 2d,
                        (mbb.maxX + mbb.minX) / 2d),
                sm.focusx,
                sm.focusy,
                sm.camera.azimuth,
                90d + sm.camera.elevation,
                sm.gsd,
                true);

        // get the extremes in pixel-size so we can zoom to that size
        PointF northWest = new PointF();
        centered.forward(new GeoPoint(mbb.maxY, mbb.minX), northWest);
        PointF southEast = new PointF();
        centered.forward(new GeoPoint(mbb.minY, mbb.maxX), southEast);

        final double modelWidth = Math.abs(northWest.x - southEast.x);
        final double modelHeight = Math.abs(northWest.y - southEast.y);

        double zoomFactor = widthOnScreen / modelWidth;
        if (zoomFactor * modelHeight > heightOnScreen) {
            zoomFactor = heightOnScreen / modelHeight;
        }

        return map.getMapResolution(map.getMapScale() * zoomFactor);
    }
}
