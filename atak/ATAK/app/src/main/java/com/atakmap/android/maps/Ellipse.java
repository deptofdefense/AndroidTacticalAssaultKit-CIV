
package com.atakmap.android.maps;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableUTMPoint;
import com.atakmap.map.layer.feature.Feature.AltitudeMode;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * An ellipse with a width, length, and angle
 *
 * Angle - The "heading" of the ellipse in true north
 * Length - The diameter of the ellipse that runs parallel to the angle
 * Width - The diameter of the ellipse that runs perpendicular to the angle
 */
public class Ellipse extends Polyline {

    private GeoPointMetaData center = new GeoPointMetaData();
    private double width = 0d;
    private double length = 0d;
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
        setHeightStyle(Polyline.HEIGHT_STYLE_POLYGON
                | Polyline.HEIGHT_STYLE_OUTLINE_SIMPLE);

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
     * Returns the width (perpendicular diameter) of the ellipse
     * @return Width in meters
     */
    public double getWidth() {
        return width;
    }

    /**
     * Sets the width (perpendicular diameter) of the ellipse
     * @param width Width in meters
     */
    public void setWidth(double width) {
        if (setWidthImpl(width))
            recomputePoly();
    }

    private boolean setWidthImpl(double width) {
        if (Double.compare(this.width, width) != 0) {
            this.width = width;
            return true;
        }
        return false;
    }

    /**
     * Returns the length (parallel diameter) of the ellipse
     * @return Length in meters
     */
    public double getLength() {
        return length;
    }

    /**
     * Sets the length (parallel diameter) of the ellipse
     * @param length Length in meters
     */
    public void setLength(double length) {
        if (setLengthImpl(length))
            recomputePoly();
    }

    private boolean setLengthImpl(double length) {
        if (Double.compare(this.length, length) != 0) {
            this.length = length;
            return true;
        }
        return false;
    }

    /**
     * Set the radius of the major axis
     * The major axis is the longer diameter in the ellipse
     *
     * It's recommended to use {@link #setWidth(double)}
     * or {@link #setLength(double)} instead
     *
     * @param radius Radius in meters
     */
    public void setMajorRadius(double radius) {
        if (setMajorRadiusImpl(radius))
            recomputePoly();
    }

    protected boolean setMajorRadiusImpl(double radius) {
        if (Double.compare(radius, getMajorRadius()) != 0) {
            if (width > length)
                width = radius * 2;
            else
                length = radius * 2;
            return true;
        }
        return false;
    }

    /**
     * Get the major axis radius
     * @return Radius in meters
     */
    public double getMajorRadius() {
        return getMajorAxis() / 2;
    }

    /**
     * Get the major axis diameter
     * @return Diameter in meters
     */
    public double getMajorAxis() {
        return Math.max(width, length);
    }

    /**
     * Set the radius of the minor axis
     * The minor axis is the shorter diameter in the ellipse
     *
     * It's recommended to use {@link #setWidth(double)}
     * or {@link #setLength(double)} instead
     *
     * @param radius Radius in meters
     */
    public void setMinorRadius(double radius) {
        if (setMinorRadiusImpl(radius))
            recomputePoly();
    }

    protected boolean setMinorRadiusImpl(double radius) {
        if (Double.compare(radius, getMajorRadius()) != 0) {
            if (width < length)
                width = radius * 2;
            else
                length = radius * 2;
            return true;
        }
        return false;
    }

    /**
     * Get the minor axis radius
     * @return Radius in meters
     */
    public double getMinorRadius() {
        return getMinorAxis() / 2;
    }

    /**
     * Get the minor axis diameter
     * @return Diameter in meters
     */
    public double getMinorAxis() {
        return Math.min(width, length);
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

        boolean minorRadiusChanged = setWidthImpl(minorRadius * 2);
        boolean majorRadiusChanged = setLengthImpl(majorRadius * 2);

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
        boolean minorRadiusChanged = setWidthImpl(minorRadius * 2);
        boolean majorRadiusChanged = setLengthImpl(majorRadius * 2);
        boolean centerChanged = setCenterImpl(center);

        if (minorRadiusChanged || majorRadiusChanged || centerChanged)
            recomputePoly();
    }

    /**
     * Sets the dimensions and location of the ellipse using the minor and major values in meters
     *
     * Based on the CoT Shape Schema, the angle represents the orientation of
     * the major axis. Therefore the major radius in this context always
     * corresponds to the length of the ellipse.
     *
     * @param center the center of the ellipse
     * @param minorRadius the minor radius in meters
     * @param majorRadius the major radius in meters
     * @param angle the angle of the ellipse in degrees clock wise.
     */
    public void setDimensions(GeoPointMetaData center, double minorRadius,
            double majorRadius, double angle) {
        boolean angleChanged = setAngleImpl(angle);
        boolean minorRadiusChanged = setWidthImpl(minorRadius * 2);
        boolean majorRadiusChanged = setLengthImpl(majorRadius * 2);
        boolean centerChanged = setCenterImpl(center);

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

    @Override
    public void setAltitudeMode(AltitudeMode altMode) {
        super.setAltitudeMode(altMode);
        recomputePoly();
    }

    @Override
    public double getArea() {
        return getMinorRadius() * getMajorRadius() * Math.PI;
    }

    @Override
    public double getPerimeterOrLength() {
        return Math.PI * 2 * Math.sqrt((Math.pow(getMinorRadius(), 2)
                + Math.pow(getMajorRadius(), 2)) / 2);
    }

    private void recomputePoly() {
        if (center == null || !center.get().isValid() || width < 0
                || length < 0)
            return;

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

        double alt = center.get().getAltitude();

        double radiusW = width / 2;
        double radiusL = length / 2;
        for (int i = 0; i < coords.length; i++) {
            double angdev = Math.toRadians((i * 6 + start));
            double x = Math.cos(angdev) * radiusL;
            double y = Math.sin(angdev) * radiusW;

            double x2 = angRad_sin * x - angRad_cos * y;
            double y2 = angRad_cos * x + angRad_sin * y;

            centerUTM.offset(x2, y2);
            double[] cor1ll = centerUTM.toLatLng(null);

            coords[i].set(cor1ll[0], cor1ll[1], alt);
            centerUTM.offset(-x2, -y2);

        }

        setPoints(GeoPointMetaData.wrap(coords));

        for (OnEllipsePropertiesChangedListener l : ellipsePropChangedListenerList) {
            l.onEllipsePropertiesChanged(this);
        }
    }

    /* DEPRECATED METHODS - When "height" was used instead of length */

    /**
     * Sets the height and width of the ellipse.   Calling this will reduce the number of full
     * calculations performed when setting height and width separately.
     * @param newW the width in meters.
     * @param newH the height in meters.
     * @deprecated Use {@link #setDimensions(double, double)}
     */
    @Deprecated
    public void setHeightWidth(double newH, double newW) {
        setDimensions(newW / 2, newH / 2);
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
        setDimensions(newCenter, newW / 2, newH / 2);
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
        setDimensions(newCenter, newW / 2, newH / 2, newAngle);
    }
}
