
package com.atakmap.android.maps;

import com.atakmap.android.editableShapes.Rectangle;
import com.atakmap.android.maps.graphics.GLBackground;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableUTMPoint;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A simplified version of {@link Rectangle}
 */
public class SimpleRectangle extends Polyline {

    private GeoPointMetaData center;
    private double height = -1.0, width = -1.0;
    private double angle = -1.0;
    private int fill = GLBackground.BKGRND_TYPE_SOLID;

    private final ConcurrentLinkedQueue<OnRectPropertiesChangedListener> rectPropChangedListenerList = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnRectFillTypeChangedListener> rectFillTypeChangedListenerList = new ConcurrentLinkedQueue<>();

    public interface OnRectPropertiesChangedListener {
        void onRectPropertiesChanged(SimpleRectangle rectangle,
                int changed);

    }

    public interface OnRectFillTypeChangedListener {
        void onRectFillTypeChanged(SimpleRectangle rectangle);
    }

    public SimpleRectangle(final String uid) {
        this(MapItem.createSerialId(), new DefaultMetaDataHolder(), uid);
    }

    public SimpleRectangle(long serialId, MetaDataHolder metadata, String uid) {
        super(serialId, metadata, uid);
    }

    public void addOnRectPropChangedListener(
            OnRectPropertiesChangedListener l) {
        rectPropChangedListenerList.add(l);
    }

    public void removeOnRectPropChangedListener(
            OnRectPropertiesChangedListener l) {
        rectPropChangedListenerList.remove(l);
    }

    public void addOnRectFillTypeChangedListener(
            OnRectFillTypeChangedListener l) {
        rectFillTypeChangedListenerList.add(l);
    }

    public void removeOnRectFillTypeChangedListener(
            OnRectFillTypeChangedListener l) {
        rectFillTypeChangedListenerList.remove(l);
    }

    public void setCenter(GeoPointMetaData newCenter) {

        if (center != null && center.equals(newCenter))
            return;

        center = newCenter;
        setMetaString("menu_point", center.get().toStringRepresentation());
        recomputeRect();
        OnRectPropertiesChanged(1);
    }

    public void setAngle(double newAngle) {

        if (Double.compare(angle, newAngle) == 0)
            return;

        angle = newAngle;
        recomputeRect();
        OnRectPropertiesChanged(2);
    }

    public double getAngle() {
        return angle;
    }

    public void setHeight(double newH) {

        if (Double.compare(newH, height) == 0)
            return;

        height = newH;
        recomputeRect();
        OnRectPropertiesChanged(4);
    }

    public double getHeight() {
        return height;
    }

    public void setWidth(double newW) {

        if (Double.compare(newW, width) == 0)
            return;

        width = newW;
        recomputeRect();
        OnRectPropertiesChanged(8);
    }

    public double getWidth() {
        return width;
    }

    public void setCenterHeightWidth(GeoPointMetaData newCenter, double newH,
            double newW) {

        if (newCenter.equals(center) &&
                Double.compare(newW, width) == 0 &&
                Double.compare(newH, height) == 0) {
            return;
        }

        center = newCenter;
        setMetaString("menu_point", center.get().toStringRepresentation());
        height = newH;
        width = newW;
        recomputeRect();
        OnRectPropertiesChanged(13);
    }

    public void setCenterHeightWidthAngle(GeoPointMetaData newCenter,
            double newH,
            double newW, double newAngle) {

        if (newCenter.equals(center) &&
                Double.compare(newW, width) == 0 &&
                Double.compare(newH, height) == 0 &&
                Double.compare(newAngle, angle) == 0) {
            return;
        }
        center = newCenter;
        setMetaString("menu_point", center.get().toStringRepresentation());
        height = newH;
        width = newW;
        angle = newAngle;
        recomputeRect();
        OnRectPropertiesChanged(15);
    }

    protected void OnRectPropertiesChanged(int changed) { // 1 = center, 2 =
                                                          // angle, 4 =
                                                          // height, 8 = width
        for (OnRectPropertiesChangedListener l : rectPropChangedListenerList) {
            l.onRectPropertiesChanged(this, changed);
        }
    }

    public int getFillStyle() {
        return fill;
    }

    protected void OnRectFillTypeChanged() {
        for (OnRectFillTypeChangedListener l : rectFillTypeChangedListenerList) {
            l.onRectFillTypeChanged(this);
        }
    }

    public void setFillStyle(int newFill) {
        if (newFill != fill) {
            fill = newFill;

            // right now, only does one fill style
            if (fill > 0) {
                setStyle(getStyle() | Shape.STYLE_FILLED_MASK);
            } else {
                setStyle(getStyle() ^ Shape.STYLE_FILLED_MASK);
            }

        }

    }

    private boolean recomputeRect() {
        if (center != null && height != -1.0 && width != -1.0
                && angle != -1.0) {

            setStyle(getStyle() | Polyline.STYLE_CLOSED_MASK);

            GeoPointMetaData[] corners = new GeoPointMetaData[4];

            // double TWO_PI = Math.PI*2;

            double shalfHeight = height / 2d;
            double shalfWidth = width / 2d;

            double[][] xycoords = {
                    {
                            shalfHeight, shalfWidth
                    },
                    {
                            -shalfHeight, shalfWidth
                    },
                    {
                            -shalfHeight, -shalfWidth
                    }, {
                            shalfHeight, -shalfWidth
                    }
            };

            double angRad = -Math.toRadians(angle);

            MutableUTMPoint centerUTM = new MutableUTMPoint(
                    center.get().getLatitude(), center.get().getLongitude());

            double angRad_cos = Math.cos(angRad);
            double angRad_sin = Math.sin(angRad);

            for (int i = 0; i < xycoords.length; i++) {

                double halfWidth = angRad_cos * xycoords[i][1] - angRad_sin
                        * xycoords[i][0];
                double halfHeight = angRad_sin * xycoords[i][1] + angRad_cos
                        * xycoords[i][0];

                centerUTM.offset(halfWidth, halfHeight);
                double[] cor1ll = centerUTM.toLatLng(null);
                corners[i] = GeoPointMetaData
                        .wrap(new GeoPoint(cor1ll[0], cor1ll[1]));
                centerUTM.offset(-halfWidth, -halfHeight);

            }

            setPoints(corners);

            return true;
        } else {
            return false;
        }
    }

    @Override
    public GeoPointMetaData getCenter() {
        return center;
    }

    @Override
    public boolean testOrthoHit(int xpos, int ypos, GeoPoint point,
            MapView view) {
        boolean retVal = false;
        if (minimumBoundingBox != null) {
            retVal = minimumBoundingBox.contains(point);
        }
        return retVal;
    }

}
