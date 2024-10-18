
package com.atakmap.android.maps;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.concurrent.ConcurrentLinkedQueue;

import gov.tak.api.annotation.DeprecatedApi;

/**
 * A MapItem with a GeoPoint
 */
public abstract class PointMapItem extends MapItem {

    public static final GeoPoint POINT_DEFAULT = GeoPoint.ZERO_POINT;

    private final ConcurrentLinkedQueue<OnPointChangedListener> _onPointChanged = new ConcurrentLinkedQueue<>();

    // these two fields are effectively joined.  _point is the raw _point, 
    // possibly mutable and gpm is the geopoint with metadata.   This is 
    // always copied when returned.
    private GeoPoint _point;
    private GeoPointMetaData gpm = new GeoPointMetaData();

    private double _height, _radius;

    // Breadcrumb trails
    private CrumbTrail crumbTrail;
    private PersistentCircleCrumbTrail persistentCircleCrumbTrail;

    public interface OnPointChangedListener {
        void onPointChanged(PointMapItem item);
    }

    /**
     * Create a PointMapItem given a GeoPoint and the unique identifier
     *
     * @param point the GeoPoint value
     * @throws IllegalArgumentException if {@code point} is null
     */
    public PointMapItem(final GeoPoint point, final String uid) {
        this(MapItem.createSerialId(), point, uid);
    }

    /**
     * Create a PointMapItem given a GeoPoint and the unique identifier
     *
     * @param point the GeoPointMetaData value
     * @throws IllegalArgumentException if {@code point} is null
     */
    public PointMapItem(final GeoPointMetaData point, final String uid) {
        this(MapItem.createSerialId(), point.get(), uid);
        gpm.set(point);
        copyMetaData(point.getMetaData());
    }

    protected PointMapItem(final long serialId, final MetaDataHolder metadata,
            final String uid) {
        this(serialId, metadata, POINT_DEFAULT, uid);
    }

    protected PointMapItem(final long serialId, final GeoPoint point,
            final String uid) {
        this(serialId, new DefaultMetaDataHolder(), point, uid);
    }

    protected PointMapItem(final long serialId,
            final MetaDataHolder metadata,
            final GeoPoint point,
            final String uid) {
        super(serialId, metadata, uid);

        if (point == null)
            throw new IllegalArgumentException("GeoPoint cannot be null");
        _point = point;
        gpm.set(_point);
    }

    /**
     * Add a point property listener.   If the listener is alreadyed added,
     * no action is performed.
     *
     * @param listener the listener
     */
    public void addOnPointChangedListener(OnPointChangedListener listener) {
        if (!_onPointChanged.contains(listener))
            _onPointChanged.add(listener);
    }

    /**
     * Remove a point property listener.  If the point listener is not found
     * no action is taken.
     *
     * @param listener the listener
     */
    public void removeOnPointChangedListener(OnPointChangedListener listener) {
        _onPointChanged.remove(listener);
    }

    /**
     * Get the point value.
     *
     * @return the point value for the PointMapItem
     */
    public GeoPoint getPoint() {
        return _point;
    }

    /**
     * @return the height in meters
     */
    public double getVolumeHeight() {
        return _height;
    }

    /**
     * The radius in meters
     */
    public double getVolumeRadius() {
        return _radius;
    }

    /**
     * Set the point property value, and notify all relevant listeners that the PointMapItem has
     * changed.
     *
     * @param point the GeoPoint
     * @throws IllegalArgumentException if {@code point} is null
     */
    public void setPoint(final GeoPoint point) {
        if (point == null)
            throw new IllegalArgumentException("GeoPoint cannot be null");

        setPoint(GeoPointMetaData.wrap(point));
    }

    /**
     * Convenience function for setting a point with the associated metadata from the
     * GeoPointMetaData object.
     * @param point
     */
    public void setPoint(final GeoPointMetaData point) {
        if (point == null)
            throw new IllegalArgumentException("GeoPoint cannot be null");

        // Remove old point metadata
        for (String k : gpm.getMetaData().keySet())
            removeMetaData(k);

        // Copy in new point metadata
        gpm = new GeoPointMetaData(point);
        this.copyMetaData(point.getMetaData());

        if (_point != point.get() && !point.get().equals(_point)) {
            _point = point.get();
            // do not notify the change has occurred until all of the bookkeeping has occurred
            onPointChanged();
        }
    }

    /**
     * Convenience method for retrieving the metadata enhanced GeoPoint for
     * a specific PointMapItem.
     * @return a copy of the Point with metadata.
     */
    public GeoPointMetaData getGeoPointMetaData() {
        return new GeoPointMetaData(gpm);
    }

    /**
     * Sets both the radius and the height as one call.
     * @param radius the radius in meters
     * @param height the height in meters
     */
    public void setVolume(double radius, double height) {
        if (_radius != radius || _height != height) {
            _radius = radius;
            _height = height;
            setMetaDouble("volume_radius", _radius);
            setMetaDouble("volume_height", _height);
            onPointChanged();
        }
    }

    /**
     * Invoked when the point property changes
     */
    protected void onPointChanged() {
        for (OnPointChangedListener l : _onPointChanged) {
            l.onPointChanged(this);
        }
    }

    public CrumbTrail getCrumbTrail() {
        return crumbTrail;
    }

    public void setCrumbTrail(CrumbTrail trail) {
        crumbTrail = trail;
    }

    /**
     * @deprecated
     */
    @Deprecated
    @DeprecatedApi(since = "4.5", forRemoval = true, removeAt = "4.8")
    public PersistentCircleCrumbTrail getPersistentCircleCrumbTrail() {
        return persistentCircleCrumbTrail;
    }

    /**
     * @deprecated
     */
    @Deprecated
    @DeprecatedApi(since = "4.5", forRemoval = true, removeAt = "4.8")
    public void setPersistentCircleCrumbTrail(
            PersistentCircleCrumbTrail persistentCircleCrumbTrail) {
        this.persistentCircleCrumbTrail = persistentCircleCrumbTrail;
    }

    @Override
    public synchronized void dispose() {
        super.dispose();
        _onPointChanged.clear();
    }

}
