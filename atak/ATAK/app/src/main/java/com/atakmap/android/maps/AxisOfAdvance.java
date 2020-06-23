
package com.atakmap.android.maps;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableUTMPoint;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AxisOfAdvance extends Polyline {

    // public static int STYLE_CROSSED_MASK = 8;

    private GeoPointMetaData nock = new GeoPointMetaData();
    private GeoPointMetaData head = new GeoPointMetaData();
    private double widthRatio = 0d;
    private int fill = 0;
    // private Polyline theAxis = new Polyline();
    private boolean crossed = false;

    private final ConcurrentLinkedQueue<OnAxisPropertiesChangedListener> axisPropChangedListenerList = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnAxisFillTypeChangedListener> axisFillTypeChangedListenerList = new ConcurrentLinkedQueue<>();

    public interface OnAxisPropertiesChangedListener {
        void onAxisPropertiesChanged(AxisOfAdvance subject);
    }

    public interface OnAxisFillTypeChangedListener {
        void onAxisFillTypeChanged(AxisOfAdvance subject);
    }

    public AxisOfAdvance(final String uid) {
        super(uid);
    }

    public AxisOfAdvance(final long serialId, final MetaDataHolder metadata,
            final String uid) {
        super(serialId, metadata, uid);
    }

    public void addOnAxisPropChangedListener(
            OnAxisPropertiesChangedListener l) {
        axisPropChangedListenerList.add(l);
    }

    public void removeOnAxisPropChangedListener(
            OnAxisPropertiesChangedListener l) {
        axisPropChangedListenerList.remove(l);
    }

    public void addOnAxisFillTypeChangedListener(
            OnAxisFillTypeChangedListener l) {
        axisFillTypeChangedListenerList.add(l);
    }

    public void removeOnAxisFillTypeChangedListener(
            OnAxisFillTypeChangedListener l) {
        axisFillTypeChangedListenerList.remove(l);
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

    public int getFillStyle() {
        return fill;
    }

    public void setCrossed(boolean cross) {
        crossed = cross;
        recomputeAxis();
    }

    public boolean getCrossed() {
        return crossed;
    }

    public void setHead(GeoPointMetaData head) {
        this.head = head;
        recomputeAxis();
    }

    public GeoPointMetaData getHead() {
        return head;
    }

    public void setNock(GeoPointMetaData nock) {
        this.nock = nock;
        recomputeAxis();
    }

    public GeoPointMetaData getNock() {
        return nock;
    }

    public void setWidthRatio(double widthRatio) {
        this.widthRatio = widthRatio;
        recomputeAxis();
    }

    public double getWidthRatio() {
        return widthRatio;
    }

    private boolean recomputeAxis() {
        if (head != null && nock != null && widthRatio > 0d) {

            // boolean crossed = false;
            // if((this.getStyle() & STYLE_CROSSED_MASK) > 0){
            // crossed = true;
            // }

            setStyle(getStyle() | Polyline.STYLE_CLOSED_MASK);

            double lat1 = Math.toRadians(nock.get().getLatitude());
            double lon1 = Math.toRadians(nock.get().getLongitude());
            double lat2 = Math.toRadians(head.get().getLatitude());
            // double lon2 = Math.toRadians(head.getLongitude());

            // Calculate the point between the nock and head of the arrow
            double dLon = Math.toRadians(head.get().getLongitude()
                    - nock.get().getLongitude());

            double Bx = Math.cos(lat2) * Math.cos(dLon);
            double By = Math.cos(lat2) * Math.sin(dLon);

            double centerLat = Math.toDegrees(Math.atan2(
                    (Math.sin(lat1) + Math.sin(lat2)),
                    (Math.sqrt((Math.cos(lat1) + Bx) * (Math.cos(lat1) + Bx)
                            + By * By))));// +Bx)*Math.cos(nock.getLatitude()),
                                                                                                                                                                                   // x);
            double centerLon = Math.toDegrees(lon1
                    + Math.atan2(By, Math.cos(lat1) + Bx));

            // Calculate the distance between the nock and head of the arrow
            int R = 6371;
            double dLat = Math.toRadians(head.get().getLatitude()
                    - nock.get().getLatitude());
            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                    + Math.sin(dLon / 2)
                            * Math.sin(dLon / 2) * Math.cos(lat1)
                            * Math.cos(lat2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

            double fulldist = (R * c) * 1000;
            double dist = fulldist / 2;

            // do transformations on the coordinates
            double x = Math.sin(dLon) * Math.cos(lat2);
            double y = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1)
                    * Math.cos(lat2)
                    * Math.cos(dLon);
            double brng = Math.atan2(y, x) - (Math.PI / 2);

            double[] coords = new double[2];

            MutableUTMPoint centerUTM = new MutableUTMPoint(centerLat,
                    centerLon);

            GeoPoint center = null;
            if (crossed) {
                centerUTM.toLatLng(coords);
                center = new GeoPoint(coords[0], coords[1]);
            }

            ArrayList<GeoPoint> pts = new ArrayList<>(11);

            double xOff = Math.cos(brng) * (-fulldist * widthRatio)
                    - Math.sin(brng) * (-dist);
            double yOff = Math.sin(brng) * (-fulldist * widthRatio)
                    + Math.cos(brng) * (-dist);

            centerUTM.offset(xOff, yOff);
            centerUTM.toLatLng(coords);
            pts.add(new GeoPoint(coords[0], coords[1]));
            centerUTM.offset(-xOff, -yOff);

            // /////////
            xOff = Math.cos(brng) * ((-fulldist * widthRatio) / 2)
                    - Math.sin(brng) * (-dist / 2);
            yOff = Math.sin(brng) * ((-fulldist * widthRatio) / 2)
                    + Math.cos(brng) * (-dist / 2);

            centerUTM.offset(xOff, yOff);
            centerUTM.toLatLng(coords);
            pts.add(new GeoPoint(coords[0], coords[1]));
            centerUTM.offset(-xOff, -yOff);

            // ///////

            if (crossed) {
                pts.add(center);
            }

            // ///////
            xOff = Math.cos(brng) * ((-fulldist * widthRatio) / 2)
                    - Math.sin(brng) * (dist / 2);
            yOff = Math.sin(brng) * ((-fulldist * widthRatio) / 2)
                    + Math.cos(brng) * (dist / 2);

            centerUTM.offset(xOff, yOff);
            centerUTM.toLatLng(coords);
            pts.add(new GeoPoint(coords[0], coords[1]));
            centerUTM.offset(-xOff, -yOff);

            // /////////
            xOff = Math.cos(brng) * (-fulldist * widthRatio) - Math.sin(brng)
                    * (dist / 2);
            yOff = Math.sin(brng) * (-fulldist * widthRatio) + Math.cos(brng)
                    * (dist / 2);

            centerUTM.offset(xOff, yOff);
            centerUTM.toLatLng(coords);
            pts.add(new GeoPoint(coords[0], coords[1]));
            centerUTM.offset(-xOff, -yOff);

            // ///////
            xOff = -Math.sin(brng) * (dist);
            yOff = Math.cos(brng) * (dist);

            centerUTM.offset(xOff, yOff);
            centerUTM.toLatLng(coords);
            pts.add(new GeoPoint(coords[0], coords[1]));
            centerUTM.offset(-xOff, -yOff);

            // ///////////
            xOff = Math.cos(brng) * (fulldist * widthRatio) - Math.sin(brng)
                    * (dist / 2);
            yOff = Math.sin(brng) * (fulldist * widthRatio) + Math.cos(brng)
                    * (dist / 2);

            centerUTM.offset(xOff, yOff);
            centerUTM.toLatLng(coords);
            pts.add(new GeoPoint(coords[0], coords[1]));
            centerUTM.offset(-xOff, -yOff);

            // ////////
            xOff = Math.cos(brng) * ((fulldist * widthRatio) / 2)
                    - Math.sin(brng) * (dist / 2);
            yOff = Math.sin(brng) * ((fulldist * widthRatio) / 2)
                    + Math.cos(brng) * (dist / 2);

            centerUTM.offset(xOff, yOff);
            centerUTM.toLatLng(coords);
            pts.add(new GeoPoint(coords[0], coords[1]));
            centerUTM.offset(-xOff, -yOff);

            // /////////

            if (crossed) {
                pts.add(center);
            }

            // /////////
            xOff = Math.cos(brng) * ((fulldist * widthRatio) / 2)
                    - Math.sin(brng) * (-dist / 2);
            yOff = Math.sin(brng) * ((fulldist * widthRatio) / 2)
                    + Math.cos(brng) * (-dist / 2);

            centerUTM.offset(xOff, yOff);
            centerUTM.toLatLng(coords);
            pts.add(new GeoPoint(coords[0], coords[1]));
            centerUTM.offset(-xOff, -yOff);

            // ///////
            xOff = Math.cos(brng) * (fulldist * widthRatio) - Math.sin(brng)
                    * (-dist);
            yOff = Math.sin(brng) * (fulldist * widthRatio) + Math.cos(brng)
                    * (-dist);

            centerUTM.offset(xOff, yOff);
            centerUTM.toLatLng(coords);
            pts.add(new GeoPoint(coords[0], coords[1]));
            centerUTM.offset(-xOff, -yOff);

            setPoints(GeoPointMetaData.wrap(pts.toArray(new GeoPoint[0])));

            return true;
        } else {
            return false;
        }
    }

    @Override
    public GeoPointMetaData getCenter() {
        return GeoPointMetaData.wrap(
                GeoCalculations.midPoint(nock.get(), head.get()),
                GeoPointMetaData.CALCULATED, GeoPointMetaData.CALCULATED);
    }
}
