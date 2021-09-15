
package com.atakmap.android.widgets;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.coremap.maps.conversion.GeomagneticField;

import com.atakmap.android.maps.DefaultMetaDataHolder;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.MetaDataHolder;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.coremap.maps.coords.NorthReference;
import com.atakmap.coremap.maps.time.CoordinatedTime;

public class AutoSizeAngleOverlayShape extends Shape
        implements AnchoredMapItem {

    protected NorthReference _azimuth = NorthReference.TRUE;

    private Marker centerMarker;
    protected GeoPointMetaData center;
    private GeoPoint ellipseTestX;
    private GeoPoint ellipseTestY;
    protected double offset = 0;

    private boolean showEdgeToCenterDirection = false;
    private boolean showProjectionProportition = false;

    private boolean showMils = false;

    public interface OnPropertyChangedListener {
        void onPropertyChanged();
    }

    private final List<OnPropertyChangedListener> onPropertyChangedListeners = new CopyOnWriteArrayList<>();

    public AutoSizeAngleOverlayShape(final String uid) {
        this(MapItem.createSerialId(), new DefaultMetaDataHolder(), uid);
    }

    public AutoSizeAngleOverlayShape(final long serialId,
            final MetaDataHolder metadata,
            final String uid) {
        super(serialId, metadata, uid);
    }

    public void setCenterMarker(Marker centerMarker) {
        this.centerMarker = centerMarker;
    }

    @Override
    public PointMapItem getAnchorItem() {
        return this.centerMarker;
    }

    public void addOnPropertyChangedListener(
            OnPropertyChangedListener listener) {
        if (!onPropertyChangedListeners.contains(listener)) {
            onPropertyChangedListeners.add(listener);
        }
    }

    public void removeOnPropertyChangedListener(
            OnPropertyChangedListener listener) {
        onPropertyChangedListeners.remove(listener);
    }

    protected void firePropertyChangedEvent() {
        for (OnPropertyChangedListener listener : onPropertyChangedListeners) {
            listener.onPropertyChanged();
        }
    }

    /**
     * Important - See Span.ENGLISH, Span.METRIC, Span.NM
     */
    public void setTrueAzimuth() {
        _azimuth = NorthReference.TRUE;
        offset = 0;
        if (showProjectionProportition)
            computeEllipseTestVerts();
        super.onPointsChanged();
        firePropertyChangedEvent();
    }

    public void setMagneticAzimuth() {
        _azimuth = NorthReference.MAGNETIC;
        if (center != null) {
            //get declination at center
            Date d = CoordinatedTime.currentDate();
            GeomagneticField gmf;
            gmf = new GeomagneticField((float) center.get().getLatitude(),
                    (float) center.get().getLongitude(), 0,
                    d.getTime());

            offset = gmf.getDeclination();
            if (showProjectionProportition)
                computeEllipseTestVerts();
            super.onPointsChanged();
            firePropertyChangedEvent();
        }
    }

    public void setGridAzimuth() {
        _azimuth = NorthReference.GRID;
        if (center != null) {
            //get Grid Convergence using center and point on the bullseye
            GeoPoint test0 = GeoCalculations.pointAtDistance(center.get(), 0,
                    100);
            GeoPoint test180 = GeoCalculations.pointAtDistance(center.get(),
                    180, 100);
            offset = ATAKUtilities.computeGridConvergence(test0, test180);
            if (showProjectionProportition)
                computeEllipseTestVerts();
            super.onPointsChanged();
            firePropertyChangedEvent();
        }
    }

    public void setProjectionProportion(final boolean projectionProportition) {
        showProjectionProportition = projectionProportition;
        if (showProjectionProportition)
            computeEllipseTestVerts();
    }

    public GeoPoint getXTestOffset() {
        return ellipseTestX;
    }

    public GeoPoint getYTestOffset() {
        return ellipseTestY;
    }

    protected void computeEllipseTestVerts() {
        if (center != null) {
            ellipseTestX = GeoCalculations.pointAtDistance(center.get(), 90,
                    100);
            ellipseTestY = GeoCalculations.pointAtDistance(center.get(), 0,
                    100);
        }
    }

    public boolean getProjectionProportition() {
        return showProjectionProportition;
    }

    public void save() {
        if (centerMarker != null)
            centerMarker.persist(MapView.getMapView().getMapEventDispatcher(),
                    null, this.getClass());
    }

    public void setEdgeToCenterDirection(boolean edgeToCenter) {
        if (showEdgeToCenterDirection != edgeToCenter) {
            showEdgeToCenterDirection = edgeToCenter;
            firePropertyChangedEvent();
        }
    }

    public boolean isShowingEdgeToCenter() {
        return showEdgeToCenterDirection;
    }

    public void setBearingUnits(boolean showDegrees) {
        showMils = !showDegrees;
    }

    public boolean isShowingMils() {
        return showMils;
    }

    public void setCenter(GeoPointMetaData gp) {
        synchronized (this) {
            center = gp;

            if (_azimuth == NorthReference.MAGNETIC) {
                if (center != null) {
                    //get declination at center
                    Date d = CoordinatedTime.currentDate();
                    GeomagneticField gmf;
                    gmf = new GeomagneticField(
                            (float) center.get().getLatitude(),
                            (float) center.get().getLongitude(), 0,
                            d.getTime());

                    offset = gmf.getDeclination();
                }
            }

            if (showProjectionProportition)
                computeEllipseTestVerts();
        }
        super.onPointsChanged();
    }

    @Override
    public GeoPointMetaData getCenter() {
        synchronized (this) {
            return center;
        }
    }

    public double getOffsetAngle() {
        return offset;
    }

    public NorthReference getNorthRef() {
        return _azimuth;
    }

    @Override
    public GeoPoint[] getPoints() {
        if (center == null)
            return new GeoPoint[0];
        else
            return new GeoPoint[] {
                    center.get()
            };
    }

    @Override
    public GeoPointMetaData[] getMetaDataPoints() {
        if (center == null)
            return new GeoPointMetaData[0];
        else
            return new GeoPointMetaData[] {
                    center
            };
    }

    @Override
    public GeoBounds getBounds(MutableGeoBounds bounds) {
        if (bounds != null) {
            bounds.set(this.getPoints());
            return bounds;
        } else {
            return GeoBounds.createFromPoints(
                    this.getPoints());
        }
    }

}
