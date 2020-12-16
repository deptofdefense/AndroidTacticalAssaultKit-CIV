
package com.atakmap.android.maps;

import android.graphics.PointF;

import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableUTMPoint;
import com.atakmap.coremap.maps.coords.Vector3D;

import java.util.concurrent.ConcurrentLinkedQueue;

public class Ellipse extends Polyline {
    private GeoPointMetaData center = new GeoPointMetaData();
    private double majorRadius = 0d;
    private double minorRadius = 0d;
    private double angle = 0d;
    private int fill = 0;

    private int start = 0;
    private int end = 360;

    private GeoPoint[] coords = new GeoPoint[60];

    private final ConcurrentLinkedQueue<OnEllipsePropertiesChangedListener> ellipsePropChangedListenerList = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnEllipseFillTypeChangedListener> ellipseFillTypeChangedListenerList = new ConcurrentLinkedQueue<>();

    public Ellipse(final String uid) {
        this(MapItem.createSerialId(), new DefaultMetaDataHolder(), uid);
    }

    public Ellipse(long serialId, MetaDataHolder metadata, final String uid) {
        super(serialId, metadata, uid);

        setStyle(getStyle() | Polyline.STYLE_CLOSED_MASK);

        for (int i = 0; i < coords.length; ++i) {
            coords[i] = GeoPoint.createMutable();
        }
    }

    /**
     * Listen for property changes on the Ellipse.
     */
    public interface OnEllipsePropertiesChangedListener {
        void onEllipsePropertiesChanged(Ellipse ellipse);
    }

    /**
     * Listen for property fill changes on the Ellipse.
     */
    public interface OnEllipseFillTypeChangedListener {
        void onEllipseFillTypeChanged(Ellipse shape);
    }

    /**
     * Add a listener to know when any of the ellipse properties are changed.
     * @param l the listener to add
     */
    public void addOnEllipsePropChangedListener(
            OnEllipsePropertiesChangedListener l) {
        if (!ellipsePropChangedListenerList.contains(l))
            ellipsePropChangedListenerList.add(l);
    }

    /**
     * Remove a listener when any of the ellipse properties are changed.
     * @param l the listener to remove
     */
    public void removeOnEllipsePropChangedListener(
            OnEllipsePropertiesChangedListener l) {
        ellipsePropChangedListenerList.remove(l);
    }

    /**
     * Add a listener to know when the fill type for an ellipse is changed.
     * @param l the listener to add
     */
    public void addOnEllipseFillTypeChangedListener(
            OnEllipseFillTypeChangedListener l) {
        if (!ellipseFillTypeChangedListenerList.contains(l))
            ellipseFillTypeChangedListenerList.add(l);
    }

    /**
     * Remove a listener registered to listen when the fill type is changed.
     * @param l the listener to add
     */
    public void removeOnEllipseFillTypeChangedListener(
            OnEllipseFillTypeChangedListener l) {
        ellipseFillTypeChangedListenerList.remove(l);
    }

    /**
     * Set the new angle of rotation for the ellipse
     * @param newAngle the angle of rotation in degrees true north.
     */
    public void setAngle(double newAngle) {
        if (setAngleImpl(newAngle))
            recomputePoly();
    }

    protected boolean setAngleImpl(double newAngle) {
        if (Double.compare(angle, newAngle) != 0) {
            angle = newAngle;
            return true;
        }
        return false;
    }

    /**
     * Returns the angle for the ellipse
     * @return the angle for the ellipse in degrees true north.
     */
    public double getAngle() {
        return angle;
    }

    /**
     * Set the major axis radius
     * @param radius Radius in meters
     */
    public void setMajorRadius(double radius) {
        if (setMajorRadiusImpl(radius))
            recomputePoly();
    }

    protected boolean setMajorRadiusImpl(double radius) {
        if (Double.compare(radius, majorRadius) != 0) {
            majorRadius = radius;
            return true;
        }
        return false;
    }

    /**
     * Get the major axis radius
     * @return Radius in meters
     */
    public double getMajorRadius() {
        return majorRadius;
    }

    /**
     * Set the minor axius radius
     * @param radius Radius in meters
     */
    public void setMinorRadius(double radius) {
        if (setMinorRadiusImpl(radius))
            recomputePoly();
    }

    protected boolean setMinorRadiusImpl(double radius) {
        if (Double.compare(radius, minorRadius) != 0) {
            minorRadius = radius;
            return true;
        }
        return false;
    }

    /**
     * Get the minor axis radius
     * @return Radius in meters
     */
    public double getMinorRadius() {
        return minorRadius;
    }

    /**
     * Set the new center point for the ellipse.
     * @param newCenter the new centerpoint.
     */
    public void setCenter(final GeoPointMetaData newCenter) {
        if (setCenterImpl(newCenter))
            recomputePoly();
    }

    protected boolean setCenterImpl(final GeoPointMetaData newCenter) {
        if (!center.equals(newCenter)) {
            center = newCenter;
            return true;
        }
        return false;
    }

    @Override
    public GeoPointMetaData getCenter() {
        return center;
    }

    /**
     * Sets the dimensions of the ellipse using the minor and major values in meters
     * @param minorRadius the minor radius in meters
     * @param majorRadius the major radius in meters
     */
    public void setDimensions(double minorRadius, double majorRadius) {

        boolean minorRadiusChanged = setMinorRadiusImpl(minorRadius);
        boolean majorRadiusChanged = setMajorRadiusImpl(majorRadius);

        if (minorRadiusChanged || majorRadiusChanged)
            recomputePoly();
    }

    /**
     * Sets the dimensions and location of the ellipse using the minor and major values in meters
     * @param center the center of the elipse
     * @param minorRadius the minor radius in meters
     * @param majorRadius the major radius in meters
     */
    public void setDimensions(GeoPointMetaData center, double minorRadius,
            double majorRadius) {
        boolean minorRadiusChanged = setMinorRadiusImpl(minorRadius);
        boolean majorRadiusChanged = setMajorRadiusImpl(majorRadius);
        boolean centerChanged = setCenterImpl(center);

        if (minorRadiusChanged || majorRadiusChanged || centerChanged)
            recomputePoly();
    }

    /**
     * Sets the dimensions and location of the ellipse using the minor and major values in meters
     * @param center the center of the elipse
     * @param minorRadius the minor radius in meters
     * @param majorRadius the major radius in meters
     * @param angle the angle of the ellipse in degrees clock wise.
     */
    public void setDimensions(GeoPointMetaData center, double minorRadius,
            double majorRadius, double angle) {
        boolean minorRadiusChanged = setMinorRadiusImpl(minorRadius);
        boolean majorRadiusChanged = setMajorRadiusImpl(majorRadius);
        boolean centerChanged = setCenterImpl(center);
        boolean angleChanged = setAngleImpl(angle);

        if (minorRadiusChanged || majorRadiusChanged || centerChanged
                || angleChanged)
            recomputePoly();
    }

    /**
     * Returns the sweep start.  In the case of a full ellipse start will equal end or start = 0 and end = 360.
     */
    public int getSweepStart() {
        return start;
    }

    /**
     * Returns the sweep end.  In the case of a full ellipse start will equal end or start = 0 and end = 360.
     */
    public int getSweepEnd() {
        return end;
    }

    /**
     * Sets a sweep for a ellipse.
     * @param start must be a number ranging from [0, 360].
     * @param end must be a number ranging from [0, 360].
     */
    public void setSweep(final int start, final int end) {

        if ((start < 0 || start > 360) || (end < 0 || end > 360)) {
            return;
        }

        if (this.start != start || this.end != end) {
            this.start = start;
            this.end = end;
            recomputePoly();
        }

    }

    /**
     * Sets the fill style
     * @param newFill the fill style one 1 for filled or 0 for unfilled.
     */
    public void setFillStyle(int newFill) {
        if (newFill != fill) {

            fill = newFill;

            // right now, only does one fill style
            if (start != (end % 360)) {
                setStyle(getStyle() & ~Polyline.STYLE_FILLED_MASK);
            } else if (fill > 0) {
                setStyle(getStyle() | Shape.STYLE_FILLED_MASK);
            } else {
                setStyle(getStyle() ^ Shape.STYLE_FILLED_MASK);
            }

            for (OnEllipseFillTypeChangedListener l : ellipseFillTypeChangedListenerList) {
                l.onEllipseFillTypeChanged(this);
            }

        }

    }

    /**
     * Returns the fill style for this ellipse.
     * @return fill style which can be either 1 for filled or 0 for unfilled.
     */
    public int getFillStyle() {
        return fill;
    }

    /*
     * Returns true if the ellipse has been hit during a touch event.
     */
    @Override
    public boolean testOrthoHit(int xpos, int ypos, GeoPoint point,
            MapView view) {
        if (getCenter() == null || !getClickable())
            return false;

        double hitRadius = getHitRadius(view);
        GeoBounds bounds = view.createHitbox(point, hitRadius);
        if (!bounds.intersects(getBounds(null)))
            return false;

        double hitRadiusSq = hitRadius * hitRadius;

        for (int x = 0; x < coords.length; x++) {
            int y = (x + 1) % coords.length;
            PointF p1 = view.forward(coords[x]);
            Vector3D v1 = new Vector3D(p1.x, p1.y, 0);
            PointF p2 = view.forward(coords[y]);
            Vector3D v2 = new Vector3D(p2.x, p2.y, 0);
            Vector3D v0 = new Vector3D(xpos, ypos, 0);
            if (Vector3D.distanceSqToSegment(v0, v1, v2) < hitRadiusSq) {
                setTouchPoint(point);
                return true;
            }
        }

        return false;
    }

    private void recomputePoly() {
        if (center != null && minorRadius >= 0d && majorRadius >= 0d) {

            if (end < start) {
                int nlen = (end + 360 - start) / 6;
                if (coords.length != nlen) {
                    coords = new GeoPoint[nlen];
                    for (int i = 0; i < coords.length; ++i)
                        coords[i] = GeoPoint.createMutable();
                }
            } else if (end > start) {
                int nlen = (end - start) / 6;
                if (coords.length != nlen) {
                    coords = new GeoPoint[nlen];
                    for (int i = 0; i < coords.length; ++i)
                        coords[i] = GeoPoint.createMutable();
                }
            } else {
                if (coords.length != 60) {
                    coords = new GeoPoint[60];
                    for (int i = 0; i < coords.length; ++i)
                        coords[i] = GeoPoint.createMutable();
                }
            }

            if (start != (end % 360)) {
                setStyle(getStyle() & ~Polyline.STYLE_CLOSED_MASK);
                setStyle(getStyle() & ~Polyline.STYLE_FILLED_MASK);
            } else if ((getStyle() & Polyline.STYLE_CLOSED_MASK) <= 0) {
                setStyle(getStyle() | Polyline.STYLE_CLOSED_MASK);
            }

            MutableUTMPoint centerUTM = new MutableUTMPoint(
                    center.get().getLatitude(),
                    center.get().getLongitude());

            double angRad;
            double angRad_cos = 1;
            double angRad_sin = 0;

            if (angle != 0d) {
                angRad = Math.toRadians(angle);
                angRad_cos = Math.cos(angRad);
                angRad_sin = Math.sin(angRad);
            }

            for (int i = 0; i < coords.length; i++) {
                double angdev = Math.toRadians((i * 6 + start));
                double x = Math.cos(angdev) * majorRadius;
                double y = Math.sin(angdev) * minorRadius;

                double x2 = angRad_sin * x - angRad_cos * y;
                double y2 = angRad_cos * x + angRad_sin * y;

                centerUTM.offset(x2, y2);
                double[] cor1ll = centerUTM.toLatLng(null);

                coords[i].set(cor1ll[0], cor1ll[1]);
                centerUTM.offset(-x2, -y2);

            }

            setPoints(GeoPointMetaData.wrap(coords));

            for (OnEllipsePropertiesChangedListener l : ellipsePropChangedListenerList) {
                l.onEllipsePropertiesChanged(this);
            }

        }

    }

    /* DEPRECATED METHODS - When "width" and "height" were used instead */

    /**
     * Returns the width of the ellipse in meters.
     * @return the width in meters.
     * @deprecated Use {@link #getMajorRadius()}
     */
    @Deprecated
    public double getWidth() {
        return getMajorRadius() * 2;
    }

    /**
     * Sets the width of the ellipse.
     * @param newW the width in meters.
     * @deprecated Use {@link #setMajorRadius(double)}
     */
    @Deprecated
    public void setWidth(double newW) {
        setMajorRadius(newW / 2);
    }

    /**
     * Sets the height and width of the ellipse.   Calling this will reduce the number of full
     * calculations performed when setting height and width separately.
     * @param newW the width in meters.
     * @param newH the height in meters.
     * @deprecated Use {@link #setDimensions(double, double)}
     */
    @Deprecated
    public void setHeightWidth(double newH, double newW) {
        setDimensions(newH / 2, newW / 2);
    }

    /**
     * Sets the height, width and center point of the ellipse.   Calling this will reduce the number of full
     * calculations performed when setting height and width and center separately.
     * @param newCenter the center of the ellipse.
     * @param newW the width in meters.
     * @param newH the height in meters.
     * @deprecated Use {@link #setDimensions(GeoPointMetaData, double, double)}
     */
    @Deprecated
    public void setCenterHeightWidth(GeoPointMetaData newCenter,
            double newH,
            double newW) {
        setDimensions(newCenter, newH / 2, newW / 2);
    }

    /**
     * Sets the height, width and center point of the ellipse.   Calling this will reduce the number of full
     * calculations performed when setting height, width, center and angle separately.
     * @param newCenter the center of the ellipse.
     * @param newW the width in meters.
     * @param newH the height in meters.
     * @param newAngle the angle in degrees
     * @deprecated Use {@link #setDimensions(GeoPointMetaData, double, double, double)}
     */
    @Deprecated
    public void setCenterHeightWidthAngle(GeoPointMetaData newCenter,
            double newH,
            double newW,
            double newAngle) {
        setDimensions(newCenter, newH / 2, newW / 2, newAngle);
    }
}
