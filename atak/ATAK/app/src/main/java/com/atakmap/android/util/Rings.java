
package com.atakmap.android.util;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.atakmap.android.drawing.mapItems.DrawingCircle;
import com.atakmap.android.preference.UnitPreferences;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.text.DecimalFormat;

import com.atakmap.coremap.locale.LocaleUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * @deprecated Use {@link DrawingCircle} instead.
 */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public class Rings extends DrawingCircle {

    private static final String TAG = "Rings";

    //Max out the rings at 25
    public static final int MAX_NUM_RINGS = 25;
    public static final double MAX_RADIUS = Circle.MAX_RADIUS;
    public static final DecimalFormat _format_pt2 = LocaleUtil
            .getDecimalFormat("#.##");
    public static final DecimalFormat _format_pt1 = LocaleUtil
            .getDecimalFormat("#.#");
    public static final DecimalFormat _format_pt0 = LocaleUtil
            .getDecimalFormat("#");

    private final List<RingsChangedListener> ringsChangedListeners = new ArrayList<>();
    private UnitPreferences _prefs;

    public Rings(String uid) {
        super(MapView.getMapView(), uid);
    }

    public Rings(MapView mapView, MapGroup mapGroup, String centerMarkerType,
            Span span, GeoPointMetaData centerPoint, Marker centerMarker,
            double radius, Marker radiusItem, int numRing, int color,
            double strokeWeight) {
        super(mapView, mapGroup.getFriendlyName(), centerMarkerType);
        _prefs = new UnitPreferences(mapView);
        if (centerPoint != null)
            setCenterPoint(centerPoint);

        setCenterMarker(centerMarker);
        setRadius(radius);
        setNumRings(numRing);
        setRadiusMarker(radiusItem);
        setStrokeWeight(strokeWeight);
        setColor(color, true);
    }

    protected Rings(final MapView mapView, final MapGroup mapGroup,
            final String centerMarkerType) {

        this(mapView, mapGroup, centerMarkerType, Span.METER, null, null, 0d,
                null, 1, Color.RED, 2d);
    }

    /**
     * Add an outer ring
     * @param radius Radius of outer ring
     */
    public void addRing(double radius) {
        setNumRings(getNumRings() + 1);
        setRadius(radius);
    }

    /**
     * Set the radius of each outer ring
     * Adds or updates circles as appropriate
     * @param radii Radius of each ring
     */
    public void setRings(double[] radii) {
        if (radii == null || radii.length == 0)
            return;
        setNumRings(radii.length);
        setRadius(radii[0]);
    }

    private Circle createUpdateRing(Circle c, int index, double radius) {
        return createRing(radius);
    }

    public void updateCenterPoint(GeoPointMetaData point) {
        setCenterPoint(point);
    }

    public Span getCurrentUnits() {
        return _prefs.getRangeUnits(getRadius());
    }

    public void setCurrentUnits(final Span s) {
        _prefs.setRangeSystem(s.getType());
    }

    public MapGroup getMapGroup() {
        return getGroup();
    }

    private Drawable getPointIcon(String pointName) {
        return null;
    }

    public String getLabel() {
        return getTitle();
    }

    public String getLabel(int ring) {
        Circle c = getRing(ring);
        return c != null ? c.getLineLabel() : null;
    }

    public void clearMarkerListeners() {
    }

    public Marker getMarker() {
        return getCenterMarker();
    }

    public String getCenterTitle(CoordinateFormat format) {
        Marker center = getCenterMarker();
        if (center != null && !isCenterShapeMarker())
            return center.getTitle();
        return CoordinateFormatUtilities.formatToString(
                getCenterPoint(), format);
    }

    public Drawable getCenterIcon() {
        Marker marker = getCenterMarker();
        if (marker == null)
            return null;
        Bitmap icon = ATAKUtilities.getIconBitmap(marker);
        if (icon == null)
            return null;
        return new BitmapDrawable(_mapView.getResources(), icon);
    }

    public String getRadiusTitle() {
        Marker radius = getRadiusMarker();
        if (radius != null)
            return radius.getTitle();
        return "No Marker";
    }

    public Drawable getRadiusIcon() {
        Marker radius = getRadiusMarker();
        if (radius == null)
            return null;
        Bitmap icon = ATAKUtilities.getIconBitmap(radius);
        if (icon == null)
            return null;
        return new BitmapDrawable(_mapView.getResources(), icon);
    }

    public void setShapeName(final String name) {
        setTitle(name);
    }

    /**
     * Set the radius of the initial circle
     * @param radius in units specified
     * @param units new unit value
     */
    public boolean setRadius(double radius, Span units) {
        setRadius(SpanUtilities.convert(radius, units, Span.METER));
        return true;
    }

    /**
     * The radius provided is the radius in meters
     * @param radius radius in meters specified
     * @param units Span value for new units
     */

    public void setRadiusActual(double radius, Span units) {
        setRadius(radius);
    }

    /**
     * @return - the radius of the rings in the currently specified units
     */
    public String getFormattedRadius() {

        DecimalFormat radiusFormat;
        double convertedRadius = getConvertedRadius();

        if (convertedRadius < 100) {
            radiusFormat = _format_pt2;
        } else if (convertedRadius < 1000) {
            radiusFormat = _format_pt1;
        } else {
            radiusFormat = _format_pt0;
        }

        return radiusFormat.format(convertedRadius);

    }

    /**
     * @return - the radius of the rings in the currently specified units
     */
    private double getConvertedRadius() {
        return SpanUtilities.convert(getRadius(), Span.METER,
                getCurrentUnits());
    }

    public int build() {
        return getNumRings();
    }

    public boolean setLabel(int index, String text) {
        Circle c = getRing(index);
        if (c == null)
            return false;
        c.setLineLabel(text);
        return true;
    }

    @Override
    public void setStrokeWeight(final double weight) {
        super.setStrokeWeight(weight);
        updateRingsChangedListeners();
    }

    @Override
    public void setStyle(final int style) {
        super.setStyle(style);
        updateRingsChangedListeners();
    }

    /**
     * Set the color for the rings, but use the alpha valued color for the rings, too.
     *
     * @param color color specified with alpha value.  Alpha value will help with fill but be
     *              stripped for line color.
     */

    public void setColorWithAlphaRings(int color) {
        setColor(color, true);
    }

    @Override
    public void setFillColor(int color) {
        super.setFillColor(color);
        updateRingsChangedListeners();
    }

    public void addRingsChangedListener(RingsChangedListener l) {
        if (ringsChangedListeners != null) {
            ringsChangedListeners.add(l);
        }
    }

    public void removeRingsChangedListener(RingsChangedListener l) {
        if (ringsChangedListeners != null
                && ringsChangedListeners.contains(l)) {
            ringsChangedListeners.remove(l);
        }
    }

    /**
     * If something about the circle changes, alert the listeners.
     */
    protected void updateRingsChangedListeners() {
        if (ringsChangedListeners != null) {
            for (RingsChangedListener l : ringsChangedListeners)
                l.onRingsChanged();
        }
    }

    public void setCirclesVisible(boolean visible) {
        super.setVisible(visible);
    }

    public static Rings getRingsFromMarker(final Marker marker) {
        return null;
    }

    public static Rings getRingsFromMarker(MapView mapView,
            final Marker marker, final Builder builder) {
        return null;
    }

    public void updateCircleFromMarker(Marker marker) {
    }

    public void labelRings() {
    }

    public void predispose() {
    }

    public static class Builder {
        private static final String TAG = "Rings Builder";

        //required
        private final MapView mapView;
        private final MapGroup group;
        private final String centerMarkerType;

        private GeoPointMetaData centerPoint = null;
        private Marker centerMarker = null;
        private GeoPointMetaData radiusPoint = null;
        private Marker radiusItem = null;
        private double radius = 0d;

        //optional
        private int ringNum = 1;
        private Span span = Span.METER;
        private int color = Color.argb(0, 255, 255, 255);
        private double strokeWeight = 2d;

        private boolean built = false;

        public Builder(MapView mapView, MapGroup mapGroup,
                String centerMarkerType) {
            this.mapView = mapView;
            this.group = mapGroup;
            this.centerMarkerType = centerMarkerType;
        }

        public Builder setColor(int color) {
            this.color = color;
            return this;
        }

        public Builder setStrokeWeight(double weight) {
            this.strokeWeight = weight;
            return this;
        }

        public Builder setRadius(double radius) {
            Log.d(TAG, "set radius: " + radius);
            this.radius = radius;
            return this;
        }

        public Builder setRadiusPoint(GeoPointMetaData point) {
            this.radiusPoint = point;
            if (this.centerPoint != null) {
                this.setRadius(
                        getDistance(point.get(), this.centerPoint.get()));
            }

            return this;
        }

        public Builder setRadiusItem(Marker item) {

            this.radiusItem = item;
            this.setRadiusPoint(item.getGeoPointMetaData());
            return this;
        }

        public Builder setUnits(Span s) {
            Log.d(TAG, "set units: " + s);
            this.span = s;
            return this;
        }

        public Builder setCenterPoint(GeoPointMetaData point) {
            Log.d(TAG, "set center: " + point.toString());
            this.centerPoint = point;
            if (this.radiusPoint != null) {
                this.setRadius(
                        getDistance(point.get(), this.radiusPoint.get()));
            }
            return this;
        }

        public double computePossibleRadius(GeoPoint point) {
            double[] da = DistanceCalculations.computeDirection(
                    centerPoint.get(), point);
            return da[0];
        }

        public Builder setCenterItem(Marker center) {
            this.centerMarker = center;
            this.setCenterPoint(center.getGeoPointMetaData());
            return this;
        }

        public Builder setNumRings(int num) {
            Log.d(TAG, "set number of rings: " + num);
            this.ringNum = num;
            return this;
        }

        public Rings build() {
            built = true;
            return new Rings(mapView, group, centerMarkerType, span,
                    centerPoint, centerMarker, radius, radiusItem, ringNum,
                    color, strokeWeight);
        }

        private double getDistance(GeoPoint point1, GeoPoint point2) {
            double[] da = DistanceCalculations.computeDirection(
                    point1, point2);

            return da[0];

        }

        public boolean isBuilt() {
            return built;
        }
    }

    private OnHeightChangedListener ohcl;

    public void setOnHeightChangedListener(OnHeightChangedListener ohcl) {
        this.ohcl = ohcl;
    }

    public interface RingsChangedListener {
        void onRingsChanged();
    }

    public interface OnHeightChangedListener {
        void onHeightChanged();
    }

    public class MovePointAction extends EditAction {
        private final GeoPointMetaData _oldPoint, _newPoint;

        public MovePointAction(GeoPointMetaData oldPoint,
                GeoPointMetaData newPoint) {
            _oldPoint = oldPoint;
            _newPoint = newPoint;
        }

        @Override
        public boolean run() {
            PointMapItem centerToMove = getCenterMarker();
            if (centerToMove != null)
                centerToMove.setPoint(_newPoint);
            return true;
        }

        @Override
        public void undo() {
            PointMapItem centerToMove = getCenterMarker();
            if (centerToMove != null)
                centerToMove.setPoint(_oldPoint);
        }

        @Override
        public String getDescription() {
            return null;
        }
    }

    public class EditRadiusAction extends EditAction {
        private final double _oldRadius, _newRadius;

        public EditRadiusAction(double oldRadius, double newRadius) {
            _oldRadius = oldRadius;
            _newRadius = newRadius;
        }

        @Override
        public boolean run() {
            setRadius(_newRadius);
            return true;
        }

        @Override
        public void undo() {
            setRadius(_oldRadius);
        }

        @Override
        public String getDescription() {
            return null;
        }
    }
}
