
package com.atakmap.android.maps.graphics;

import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.PointMapItem.OnPointChangedListener;
import com.atakmap.coremap.maps.conversion.EGM96;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.opengl.GLMapView;

public abstract class GLPointMapItem2 extends AbstractGLMapItem2 implements
        OnPointChangedListener {

    public GeoPoint point;
    protected double latitude;
    protected double longitude;
    protected double altitude;
    protected double altHae;
    protected double localTerrainValue;
    protected int terrainVersion = 0;

    public GLPointMapItem2(MapRenderer surface, PointMapItem subject,
            int renderPass) {
        super(surface, subject, renderPass);
        point = subject.getPoint();

        this.context.queueEvent(new Runnable() {
            @Override
            public void run() {
                synchronized (bounds) {
                    final double N = point.getLatitude() + .0001; // about 10m
                    final double S = point.getLatitude() - .0001;
                    final double E = point.getLongitude() + .0001;
                    final double W = point.getLongitude() - .0001;

                    bounds.set(N, W, S, E);
                }
                dispatchOnBoundsChanged();
            }
        });

    }

    protected final double validateLocalElevation(GLMapView ortho) {
        if (ortho.drawTilt > 0d) {
            final int renderTerrainVersion = ortho.getTerrainVersion();
            if (this.terrainVersion != renderTerrainVersion) {
                this.localTerrainValue = ortho
                        .getTerrainMeshElevation(this.latitude, this.longitude);
                this.terrainVersion = renderTerrainVersion;
            }
        }
        return this.localTerrainValue;
    }

    @Override
    public void startObserving() {
        final PointMapItem pointItem = (PointMapItem) this.subject;
        super.startObserving();
        this.onPointChanged(pointItem);
        pointItem.addOnPointChangedListener(this);
    }

    @Override
    public void stopObserving() {
        final PointMapItem pointItem = (PointMapItem) this.subject;
        super.stopObserving();
        pointItem.removeOnPointChangedListener(this);
    }

    @Override
    public void onPointChanged(PointMapItem item) {
        final GeoPoint p = item.getPoint();
        if (!p.isValid())
            return;
        this.context.queueEvent(new Runnable() {
            @Override
            public void run() {
                point = p;
                latitude = point.getLatitude();
                longitude = point.getLongitude();
                if (point.isAltitudeValid()) {
                    altHae = EGM96.getHAE(point);
                    altitude = altHae;
                } else {
                    altitude = Double.NaN;
                    altHae = GeoPoint.UNKNOWN;
                }
                // invalidate cached terrain value
                terrainVersion = ~terrainVersion;
                synchronized (bounds) {
                    final double N = point.getLatitude() + .0001; // about 10m
                    final double S = point.getLatitude() - .0001;
                    final double E = point.getLongitude() + .0001;
                    final double W = point.getLongitude() - .0001;

                    bounds.set(N, W, S, E);
                }
                dispatchOnBoundsChanged();

            }
        });
    }
}
