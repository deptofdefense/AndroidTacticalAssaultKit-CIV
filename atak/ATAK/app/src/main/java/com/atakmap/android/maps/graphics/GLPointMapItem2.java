
package com.atakmap.android.maps.graphics;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.PointMapItem.OnPointChangedListener;
import com.atakmap.map.layer.control.LollipopControl;
import com.atakmap.coremap.maps.conversion.EGM96;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.feature.Feature.AltitudeMode;
import com.atakmap.map.opengl.GLMapView;

public abstract class GLPointMapItem2 extends AbstractGLMapItem2 implements
        OnPointChangedListener, LollipopControl,
        MapItem.OnAltitudeModeChangedListener {

    public GeoPoint point;
    protected double latitude;
    protected double longitude;
    protected double altitude;
    protected double altHae;
    protected AltitudeMode altMode;
    protected double localTerrainValue;
    protected int terrainVersion = 0;
    private boolean lollipopsVisible;

    public GLPointMapItem2(MapRenderer surface, PointMapItem subject,
            int renderPass) {
        super(surface, subject, renderPass);
        point = subject.getPoint();
        altMode = subject.getAltitudeMode();
        lollipopsVisible = true;

        this.context.queueEvent(new Runnable() {
            @Override
            public void run() {
                synchronized (bounds) {
                    final double N = point.getLatitude() + .0001; // about 10m
                    final double S = point.getLatitude() - .0001;
                    final double E = point.getLongitude() + .0001;
                    final double W = point.getLongitude() - .0001;

                    bounds.set(N, W, S, E);
                    updateBoundsZ();
                }
                dispatchOnBoundsChanged();
            }
        });

    }

    protected final double validateLocalElevation(GLMapView ortho) {
        {
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
        pointItem.addOnAltitudeModeChangedListener(this);
    }

    @Override
    public void stopObserving() {
        final PointMapItem pointItem = (PointMapItem) this.subject;
        super.stopObserving();
        pointItem.removeOnPointChangedListener(this);
        pointItem.removeOnAltitudeModeChangedListener(this);
    }

    /**
     * Rendering style modified by the {@link LollipopControl}
     * @param v True if lollipops should be visible
     */
    @Override
    public void setLollipopsVisible(boolean v) {
        lollipopsVisible = v;
    }

    @Override
    public boolean getLollipopsVisible() {
        return lollipopsVisible;
    }

    @Override
    public void onPointChanged(PointMapItem item) {
        final GeoPoint p = item.getPoint();
        if (!p.isValid())
            return;
        runOnGLThread(new Runnable() {
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
                    updateBoundsZ();
                }
                dispatchOnBoundsChanged();

            }
        });
    }

    @Override
    public void onAltitudeModeChanged(final AltitudeMode altitudeMode) {
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                altMode = altitudeMode;
                terrainVersion = ~terrainVersion;

                synchronized (bounds) {
                    updateBoundsZ();
                }
                dispatchOnBoundsChanged();
            }
        });
    }

    /**
     * Get the current altitude mode
     * @return Altitude mode
     */
    protected AltitudeMode getAltitudeMode() {
        return altMode != null ? altMode : AltitudeMode.Absolute;
    }

    protected void updateBoundsZ() {
        double minAlt = altHae - 10d;
        double maxAlt = altHae + 10d;
        if (Double.isNaN(altHae)) {
            minAlt = Double.NaN;
            maxAlt = Double.NaN;
        } else {
            switch (getAltitudeMode()) {
                case Absolute:
                    //
                    maxAlt = Math.max(DEFAULT_MAX_ALT, altHae);
                    break;
                case Relative:
                    // offset from min/max surface altitudes
                    minAlt += DEFAULT_MIN_ALT;
                    maxAlt += DEFAULT_MAX_ALT;
                    break;
                case ClampToGround:
                    minAlt = DEFAULT_MIN_ALT;
                    maxAlt = DEFAULT_MAX_ALT;
                    break;
                default:
                    minAlt = Double.NaN;
                    maxAlt = Double.NaN;
                    break;
            }
        }
        bounds.setMinAltitude(minAlt);
        bounds.setMaxAltitude(maxAlt);
    }
}
