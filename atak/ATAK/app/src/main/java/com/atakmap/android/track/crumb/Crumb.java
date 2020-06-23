
package com.atakmap.android.track.crumb;

import com.atakmap.android.maps.DefaultMetaDataHolder;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MetaDataHolder;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * TODO: Migrate to CrumbPoint where a map item instance is not necessary
 */
public class Crumb extends PointMapItem {

    private int color;
    private int size = 10;
    public long timestamp;
    public float speed, bearing;
    public int trackDBID, crumbDBID;
    public Crumb prev;
    public Crumb next;
    private boolean drawLineToSurface = false;

    private final ConcurrentLinkedQueue<OnCrumbColorChangedListener> colorListeners = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnCrumbSizeChangedListener> sizeListeners = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnCrumbDirectionChangedListener> dirListeners = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnCrumbDrawLineToSurfaceChangedListener> lineSurfaceListeners = new ConcurrentLinkedQueue<>();

    public Crumb(final long serialId,
            final MetaDataHolder metadata,
            final String uid) {
        this(serialId, metadata, POINT_DEFAULT, 0xFF0000FF, 0f, uid);
    }

    public Crumb(final GeoPoint point,
            final String uid) {
        this(point, 0xFF0000FF, 0f, uid);
    }

    public Crumb(final GeoPoint point, final int color, final String uid) {
        this(point, color, 0f, uid);
    }

    public Crumb(final GeoPoint point,
            final int color,
            final float dir,
            final String uid) {
        this(MapItem.createSerialId(),
                new DefaultMetaDataHolder(), point, color, dir, uid);
    }

    private Crumb(final long serialId,
            final MetaDataHolder metadata,
            final GeoPoint point,
            final int color,
            final float dir,
            final String uid) {
        super(serialId, metadata, point, uid);
        timestamp = new CoordinatedTime().getMilliseconds();
        this.color = color;
        this.bearing = dir;
        this.setMetaBoolean("addToObjList", false);
        this.setZOrder(-100000d);
    }

    public boolean getDrawLineToSurface() {
        return drawLineToSurface;
    }

    /**
     * For the crumbs, draw a line from the crumb to the surface to help visualize the elevation.
     * @param drawLineToSurface true if the line should be drawn.
     */
    public void setDrawLineToSurface(final boolean drawLineToSurface) {
        this.drawLineToSurface = drawLineToSurface;
        for (OnCrumbDrawLineToSurfaceChangedListener l : lineSurfaceListeners) {
            l.onCrumbDrawLineToSurfaceChanged(this);
        }
    }

    /**
     * Get the direction of this crumb
     * @return Crumb direction in true degrees
     */
    public double getDirection() {
        return this.bearing;
    }

    /**
     * Set the direction of this crumb
     * @param ndir Crumb direction in true degrees
     */
    public void setDirection(double ndir) {
        if (Double.compare(bearing, ndir) != 0) {
            this.bearing = (float) ndir;
            for (OnCrumbDirectionChangedListener l : dirListeners) {
                l.onCrumbDirectionChanged(this);
            }
        }
    }

    public void setColor(final int ncolor) {
        if (ncolor != color) {
            this.color = ncolor;
            for (OnCrumbColorChangedListener l : colorListeners) {
                l.onCrumbColorChanged(this);
            }
        }
    }

    public void setSize(final int s) {
        if (s != size) {
            this.size = s;
            for (OnCrumbSizeChangedListener l : sizeListeners) {
                l.onCrumbSizeChanged(this);
            }
        }
    }

    public int getSize() {
        return size;
    }

    public int getColor() {
        return color;
    }

    @Override
    public String toString() {
        return getPoint().getLongitude() + "," + getPoint().getLatitude() + ","
                + getPoint().getAltitude();
    }

    public void setTimestamp(final long timestamp) {
        this.timestamp = timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void addCrumbColorListener(OnCrumbColorChangedListener l) {
        colorListeners.add(l);
    }

    public void removeCrumbColorListener(OnCrumbColorChangedListener l) {
        colorListeners.remove(l);
    }

    public void addCrumbSizeListener(OnCrumbSizeChangedListener l) {
        sizeListeners.add(l);
    }

    public void removeCrumbSizeListener(OnCrumbSizeChangedListener l) {
        sizeListeners.remove(l);
    }

    public void addCrumbDirectionListener(OnCrumbDirectionChangedListener l) {
        dirListeners.add(l);
    }

    public void removeCrumbDirectionListener(
            OnCrumbDirectionChangedListener l) {
        dirListeners.remove(l);
    }

    public void addCrumbDrawLineToSurfaceListener(
            OnCrumbDrawLineToSurfaceChangedListener l) {
        lineSurfaceListeners.add(l);
    }

    public void removeCrumbDrawLineToSurfaceListener(
            OnCrumbDrawLineToSurfaceChangedListener l) {
        lineSurfaceListeners.remove(l);
    }

    public interface OnCrumbColorChangedListener {
        void onCrumbColorChanged(Crumb crumb);
    }

    public interface OnCrumbDirectionChangedListener {
        void onCrumbDirectionChanged(Crumb crumb);
    }

    public interface OnCrumbDrawLineToSurfaceChangedListener {
        void onCrumbDrawLineToSurfaceChanged(Crumb crumb);
    }

    public interface OnCrumbSizeChangedListener {
        void onCrumbSizeChanged(Crumb crumb);
    }

}
