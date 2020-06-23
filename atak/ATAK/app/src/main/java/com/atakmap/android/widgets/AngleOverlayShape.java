
package com.atakmap.android.widgets;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Bundle;

import java.text.DecimalFormat;

import java.util.ArrayList;
import java.util.List;

import com.atakmap.android.imagecapture.CanvasHelper;
import com.atakmap.android.imagecapture.CapturePP;
import com.atakmap.android.imagecapture.PointA;
import com.atakmap.android.imagecapture.TextRect;
import com.atakmap.android.maps.DefaultMetaDataHolder;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MetaDataHolder;
import com.atakmap.android.util.Rings;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

public class AngleOverlayShape extends AutoSizeAngleOverlayShape {

    private double radius; //the radius in meters
    private Span _units = Span.METER;
    private double arrowCalcRad = 0;
    private GeoPoint arrowCalcCenter = GeoPoint.ZERO_POINT;
    private final GeoPoint[] innerArrowPoints = new GeoPoint[4];

    private boolean simpleSpoke = false;

    public AngleOverlayShape(final String uid) {
        this(MapItem.createSerialId(), new DefaultMetaDataHolder(), uid);
    }

    public AngleOverlayShape(final long serialId,
            final MetaDataHolder metadata,
            final String uid) {
        super(serialId, metadata, uid);
    }

    public void setSimpleSpokeView(boolean simpleSpokeView) {
        simpleSpoke = simpleSpokeView;
        if (simpleSpokeView)
            setMetaString("bullseye", "true");
        else
            removeMetaData("bullseye");
    }

    public boolean showSimpleSpokeView() {
        return simpleSpoke;
    }

    @Override
    public void setCenter(GeoPointMetaData gp) {
        super.setCenter(gp);
        calcInnerArrowlocations();
        computeEllipseTestVerts();
    }

    public double getRadius() {
        return radius;
    }

    /**
     * @return - the radius of the rings in the currently specified units
     */
    public String getFormattedRadius() {

        DecimalFormat radiusFormat;
        double convertedRadius = getConvertedRadius();

        if (convertedRadius < 100) {
            radiusFormat = Rings._format_pt2;
        } else if (convertedRadius < 1000) {
            radiusFormat = Rings._format_pt1;
        } else {
            radiusFormat = Rings._format_pt0;
        }

        return radiusFormat.format(convertedRadius);

    }

    /**
     * @return - the radius of the rings in the currently specified units
     */
    private double getConvertedRadius() {
        double returnValue = radius;
        switch (_units) {
            case KILOMETER:
                returnValue *= ConversionFactors.METERS_TO_KM;
                break;
            case METER:
                //do nothing, the current radius value is already specified in meters
                break;
            case MILE:
                returnValue *= ConversionFactors.METERS_TO_MILES;
                break;
            case FOOT:
                returnValue *= ConversionFactors.METERS_TO_FEET;
                break;
            case YARD:
                returnValue *= ConversionFactors.METERS_TO_YARDS;
                break;
            case NAUTICALMILE:
                returnValue *= ConversionFactors.METERS_TO_NM;
                break;
        }
        return returnValue;
    }

    /**
     * @param radius - the desired radius in Meters
     */
    public void setRadius(double radius) {
        this.radius = radius;
        calcInnerArrowlocations();
        computeEllipseTestVerts();
        super.onPointsChanged();
        super.firePropertyChangedEvent();
    }

    public void setRadius(double radius, Span units) {
        double r = radius;
        switch (units) {
            case KILOMETER:
                r *= ConversionFactors.KM_TO_METERS;
                break;
            case METER:
                //do nothing, the current radius value is already specified in meters
                break;
            case MILE:
                r *= ConversionFactors.MILES_TO_METERS;
                break;
            case FOOT:
                r *= ConversionFactors.FEET_TO_METERS;
                break;
            case YARD:
                r *= ConversionFactors.YARDS_TO_METERS;
                break;
            case NAUTICALMILE:
                r *= ConversionFactors.NM_TO_METERS;
                break;
        }
        _units = units;
        setRadius(r);
    }

    public Span getRadiusUnits() {
        return _units;
    }

    private void calcInnerArrowlocations() {
        synchronized (innerArrowPoints) {
            if (center != null) {
                innerArrowPoints[0] = DistanceCalculations.metersFromAtBearing(
                        center.get(), radius, 0);
                innerArrowPoints[1] = DistanceCalculations.metersFromAtBearing(
                        center.get(), radius / 3, 90);
                innerArrowPoints[2] = DistanceCalculations.metersFromAtBearing(
                        center.get(), radius / 3, 180);
                innerArrowPoints[3] = DistanceCalculations.metersFromAtBearing(
                        center.get(), radius / 3, 270);
            }
            arrowCalcRad = radius;
            if (center == null)
                arrowCalcCenter = null;
            else
                arrowCalcCenter = center.get();
        }
    }

    public GeoPoint[] getInnerArrowPoints() {
        if (arrowCalcCenter != center.get() || arrowCalcRad != radius)
            calcInnerArrowlocations();
        synchronized (innerArrowPoints) {
            return innerArrowPoints;
        }
    }

    @Override
    public void setTitle(String title) {
        final String oldTitle = getTitle();
        if (oldTitle != null && !oldTitle.equals(title)) {
            super.setTitle(title);
            super.firePropertyChangedEvent();
            return;
        }
        super.setTitle(title);
    }

    @Override
    public GeoPoint[] getPoints() {
        if (center == null)
            return new GeoPoint[0];
        return new GeoPoint[] {
                center.get(),
                DistanceCalculations.metersFromAtBearing(center.get(), radius,
                        0),
                DistanceCalculations.metersFromAtBearing(center.get(), radius,
                        90),
                DistanceCalculations.metersFromAtBearing(center.get(), radius,
                        180),
                DistanceCalculations.metersFromAtBearing(center.get(), radius,
                        270)
        };
    }

    @Override
    public GeoPointMetaData[] getMetaDataPoints() {
        return GeoPointMetaData.wrap(getPoints());
    }

    @Override
    public int getStrokeColor() {
        return isShowingEdgeToCenter() ? Color.argb(204, 255, 0, 0)
                : Color.argb(204, 0, 255, 0);
    }

    @Override
    public Bundle preDrawCanvas(CapturePP cap) {
        // Store forward returns for center
        Bundle data = new Bundle();
        data.putParcelable("center", cap.forward(getCenter().get()));

        // Spoke ends
        List<PointF> points = new ArrayList<>();
        for (int i = 0; i < 360; i += 30) {
            GeoPoint endPoint = DistanceCalculations.computeDestinationPoint(
                    getCenter().get(), getOffsetAngle() + i, getRadius());
            points.add(cap.forward(endPoint));
        }
        data.putSerializable("points",
                points.toArray(new PointF[0]));
        return data;
    }

    @Override
    public void drawCanvas(CapturePP cap, Bundle data) {
        PointF center = data.getParcelable("center");
        PointF[] points = (PointF[]) data.getSerializable("points");
        if (center == null || points == null || points.length < 1)
            return;
        boolean toCenter = isShowingEdgeToCenter();
        Canvas can = cap.getCanvas();
        Paint paint = cap.getPaint();
        Path path = cap.getPath();
        float dr = cap.getResolution();
        float lineWeight = cap.getLineWeight();
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(getStrokeColor());
        paint.setStrokeWidth(lineWeight * 5f);

        // Setup spokes
        PointF c = new PointF(dr * center.x, dr * center.y);
        for (PointF p : points) {
            if (p == null)
                continue;
            path.moveTo(c.x, c.y);
            path.lineTo(dr * p.x, dr * p.y);
        }

        // Setup arrow
        PointF end = new PointF(dr * points[0].x, dr * points[0].y);
        float radius = (float) Math.hypot(end.x - c.x, end.y - c.y);
        PointF tail = CanvasHelper.degOffset(c, getOffsetAngle(),
                radius * (toCenter ? 0.85f : 0.75f));
        PointF head = CanvasHelper.degOffset(c, getOffsetAngle(),
                radius * (toCenter ? 0.75f : 0.85f));
        CanvasHelper.buildArrow(path, tail, head, radius * 0.1f, 145);

        // Draw lines
        can.drawPath(path, paint);
        path.reset();

        // Now draw labels
        PointF p = new PointF();
        PointA lp = new PointA();
        String unit = (isShowingMils() ? "mils" : Angle.DEGREE_SYMBOL)
                + _azimuth.getAbbrev();
        paint.setTextSize(cap.getLabelSize());
        for (int i = 0; i < points.length; i++) {

            // Get label text
            int deg = (int) CanvasHelper.deg360(i * 30 + (toCenter ? 180 : 0));
            if (deg == 0)
                deg = 360;
            String label = (isShowingMils() ? AngleUtilities
                    .formatNoUnitsNoDecimal(
                            deg, Angle.DEGREE, Angle.MIL)
                    : String.valueOf(deg))
                    + (deg == 360 ? unit : "");
            TextRect labelRect = new TextRect(paint, 0, label);
            float lpad = Math.max(labelRect.width(), labelRect.height());
            float maxX = cap.getWidth() - lpad, maxY = cap.getHeight() - lpad;

            // Get label position (clamp if off-screen)
            p.set(dr * points[i].x, dr * points[i].y);
            double m = (p.y - c.y) / (p.x - c.x);
            double b = c.y - (m * c.x);
            lp.set(p.x, p.y);
            lp.angle = (float) ((i * 30) + getOffsetAngle());
            if (lp.x < lpad) {
                lp.x = lpad;
                lp.y = CanvasHelper.validate((float) (b + lpad * m), p.y);
            } else if (lp.x > maxX) {
                lp.x = maxX;
                lp.y = CanvasHelper.validate((float) (b + maxX * m), p.y);
            }
            if (lp.y < lpad) {
                lp.x = CanvasHelper.validate((float) ((lpad - b) / m), p.x);
                lp.y = lpad;
            } else if (lp.y > maxY) {
                lp.x = CanvasHelper.validate((float) ((maxY - b) / m), p.x);
                lp.y = maxY;
            }
            CanvasHelper.clampToLine(lp, c, p);

            // Draw label
            lp.set(lp.x / dr, lp.y / dr);
            cap.drawLabel(label, lp);
        }
    }
}
