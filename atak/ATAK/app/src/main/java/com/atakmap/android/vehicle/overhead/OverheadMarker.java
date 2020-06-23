
package com.atakmap.android.vehicle.overhead;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;

import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.hierarchy.filters.FOVFilter;
import com.atakmap.android.imagecapture.Capturable;
import com.atakmap.android.imagecapture.CapturePP;
import com.atakmap.android.imagecapture.PointA;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.coremap.maps.coords.NorthReference;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Overhead image marker
 */
public class OverheadMarker extends PointMapItem implements Exportable,
        Capturable, FOVFilter.Filterable {

    public static final String COT_TYPE = "overhead_marker";
    public static final String MAP_GROUP = "Overhead Markers";

    private OverheadImage _img;
    private double _azimuth = 0;
    private final MutableGeoBounds _bounds = new MutableGeoBounds(0, 0, 0, 0);
    private List<OnChangedListener> _listeners = new ArrayList<>();

    public OverheadMarker(OverheadImage img, GeoPointMetaData point,
            String uid) {
        super(point, uid);
        setType(COT_TYPE);
        setImage(img);
        setClickable(true);
        setMetaBoolean("removable", true);
        setMetaBoolean("movable", true);
        setMetaString("menu", "menus/overhead_marker_menu.xml");
        setMetaBoolean("archive", true);
        setMetaString("parent_callsign", MapView._mapView.getDeviceCallsign());
        setMetaString("parent_uid", MapView.getDeviceUid());
        setMetaString(
                "parent_type",
                MapView._mapView.getMapData()
                        .getString("deviceType", "a-f-G"));
        setMetaString("production_time",
                new CoordinatedTime().toString());

        // Below shapes and markers by default
        setZOrder(3d);
    }

    public void setImage(OverheadImage img) {
        if (img != _img) {
            _img = img;
            setTitle(img.name);
            setMetaString("iconUri", img.imageUri);
            recalcBounds();
            onChanged();
        }
    }

    public OverheadImage getImage() {
        return _img;
    }

    private GeoPoint[] getCornerPoints() {
        GeoPoint center = getPoint();
        double rot = getAzimuth();
        double w2 = _img.width / 2, l2 = _img.length / 2;
        GeoPoint left = DistanceCalculations.computeDestinationPoint(center,
                rot - 90, w2);
        GeoPoint right = DistanceCalculations.computeDestinationPoint(center,
                rot + 90, w2);
        return new GeoPoint[] {
                DistanceCalculations.computeDestinationPoint(left, rot, l2),
                DistanceCalculations.computeDestinationPoint(right, rot, l2),
                DistanceCalculations.computeDestinationPoint(right, rot, -l2),
                DistanceCalculations.computeDestinationPoint(left, rot, -l2)
        };
    }

    private void recalcBounds() {
        if (_img != null) {
            MapView mv = MapView.getMapView();
            _bounds.set(getCornerPoints(), mv != null
                    && mv.isContinuousScrollEnabled());
        } else {
            _bounds.set(0, 0, 0, 0);
        }
    }

    public GeoBounds getBounds() {
        return _bounds;
    }

    public double getLength() {
        return _img != null ? _img.length : 0;
    }

    public double getWidth() {
        return _img != null ? _img.width : 0;
    }

    @Override
    protected void onPointChanged() {
        recalcBounds();
        onChanged();
        super.onPointChanged();
    }

    @Override
    public boolean testOrthoHit(int xpos, int ypos, GeoPoint point,
            MapView view) {
        if (_img != null && _img.hitmap != null) {
            // Get image scale
            Hitmap h = _img.hitmap;
            double[] scales = getScales(view.getMapResolution());

            // Skip precise detection if the scaled image is too small
            PointF pos = view.forward(getPoint());
            PointF tr = new PointF(xpos - pos.x, ypos - pos.y);
            if (scales[0] < 1f || scales[1] < 1f) {
                float dp = view.getContext().getResources()
                        .getDisplayMetrics().density;
                return Math.hypot(tr.x, tr.y) < 32f * dp;
            }

            // Begin precise hit detection based on image alpha
            if (getBounds().contains(point)) {
                // We need the precise scales for proper hit detection
                scales = getPreciseScales(view);
                // Transform point to image coordinates
                double r = Math.toRadians(getAzimuth() - view.getMapRotation());
                tr.set((float) ((tr.x * Math.cos(r) + tr.y * Math.sin(r))
                        / scales[0])
                        + (float) (h.getWidth() / 2),
                        (float) ((tr.y * Math.cos(r)
                                - tr.x * Math.sin(r)) / scales[1])
                                + (float) (h.getHeight() / 2));
                // Test if transformed coordinate is over solid or empty area
                return h.testHit(tr);
            }
        }
        return false;
    }

    @Override
    public boolean accept(FOVFilter.MapState fov) {
        // Ensure the overhead marker has a valid image
        Hitmap h;
        if (_img == null || (h = _img.hitmap) == null)
            return false;

        // Check bounds
        GeoBounds bounds = getBounds();
        if (!fov.intersects(bounds))
            return false;

        // Check center point
        if (fov.contains(getPoint(), true))
            return true;

        // Determine if we should continue with image detection
        // If the marker is too zoomed out then accept
        double[] scales = getScales(fov.drawMapResolution);
        if (scales[0] < 1f || scales[1] < 1f)
            return true;

        // Check if part of the image is in view
        BitSet b = h.getData();
        double r = Math.toRadians(getAzimuth() - fov.rotation);

        // TODO: How do we properly save the map state forward/inverse matrices?
        if (fov.mapView == null)
            return false;
        PointF c = fov.mapView.forward(getPoint(), fov.mapView
                .getIDLHelper().getUnwrap(bounds));

        scales = getPreciseScales(fov.mapView);
        float halfWidth = h.getWidth() / 2f;
        float halfHeight = h.getHeight() / 2f;
        int i = 0;
        for (int y = 0; y < h.getHeight(); y++) {
            for (int x = 0; x < h.getWidth(); x++) {
                if (b.get(i++)) {
                    double trX = (x - halfWidth) * scales[0];
                    double trY = (y - halfHeight) * scales[1];
                    double px = c.x + (float) (trX * Math.cos(r)
                            - trY * Math.sin(r));
                    double py = c.y + (float) (trY * Math.cos(r)
                            + trX * Math.sin(r));
                    if (px >= fov.left && px < fov.right
                            && py >= fov.top && py < fov.bottom)
                        return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets the estimated scale of this marker based on map resolution
     * @param mapResolution Map resolution
     * @return [ x-scale, y-scale ]
     */
    public double[] getScales(double mapResolution) {
        Hitmap h = _img.hitmap;
        if (h == null)
            return new double[] {
                    0, 0
            };
        double mercatorscale = Math.cos(getPoint().getLatitude()
                / ConversionFactors.DEGREES_TO_RADIANS);
        if (mercatorscale < 0.0001)
            mercatorscale = 0.0001;
        double meters = mapResolution * mercatorscale;
        return new double[] {
                _img.width / (meters * h.getWidth()),
                _img.length / (meters * h.getHeight())
        };
    }

    /**
     * Gets the precise scale of this marker based on map forward returns,
     * at the cost of being a more expensive calculation
     * @param mv Map view
     * @return [ x-scale, y-scale ]
     */
    public double[] getPreciseScales(MapView mv) {
        Hitmap h = _img.hitmap;
        if (h == null)
            return new double[] {
                    0, 0
            };

        GeoPoint center = getPoint();
        double rot = getAzimuth();
        double unwrap = mv.getIDLHelper().getUnwrap(getBounds());

        PointF c = mv.forward(center, unwrap);

        PointF p = mv.forward(DistanceCalculations.computeDestinationPoint(
                center, rot + 90, _img.width / 2), unwrap);
        double sizeX = Math.hypot(p.x - c.x, p.y - c.y) * 2;

        p = mv.forward(DistanceCalculations.computeDestinationPoint(
                center, rot, _img.length / 2), unwrap);
        double sizeY = Math.hypot(p.x - c.x, p.y - c.y) * 2;

        return new double[] {
                sizeX / h.getWidth(), sizeY / h.getHeight()
        };
    }

    public void setColor(int color) {
        if (getColor() != color) {
            setMetaInteger("color", color);
            onChanged();
        }
    }

    public int getColor() {
        return getMetaInteger("color", Color.WHITE);
    }

    public void setAzimuth(double heading) {
        if (Double.compare(_azimuth, heading) != 0) {
            _azimuth = heading;
            recalcBounds();
            onChanged();
        }
    }

    public double getAzimuth() {
        return _azimuth;
    }

    public void setAzimuth(double deg, NorthReference ref) {
        double trueDeg = deg;
        if (ref.equals(NorthReference.MAGNETIC))
            trueDeg = ATAKUtilities.convertFromMagneticToTrue(getPoint(), deg);
        setAzimuth((trueDeg % 360) + (trueDeg < 0 ? 360 : 0));
    }

    public double getAzimuth(NorthReference ref) {
        if (ref.equals(NorthReference.MAGNETIC))
            return ATAKUtilities.convertFromTrueToMagnetic(
                    getPoint(), _azimuth);
        return _azimuth;
    }

    public void onChanged() {
        for (OnChangedListener ocl : _listeners)
            ocl.onChanged(this);
    }

    public void addOnChangedListener(OnChangedListener ocl) {
        if (!_listeners.contains(ocl))
            _listeners.add(ocl);
    }

    public void removeOnChangedListener(OnChangedListener ocl) {
        if (_listeners.contains(ocl))
            _listeners.remove(ocl);
    }

    public interface OnChangedListener {
        void onChanged(OverheadMarker marker);
    }

    @Override
    public boolean isSupported(Class target) {
        return CotEvent.class.equals(target) ||
                MissionPackageExportWrapper.class.equals(target);
    }

    @Override
    public Object toObjectOf(Class target, ExportFilters filters) {

        if (filters != null && filters.filter(this))
            return null;

        if (CotEvent.class.equals(target))
            return toCot();
        else if (MissionPackageExportWrapper.class.equals(target))
            return Marker.toMissionPackage(this);

        return null;
    }

    public CotEvent toCot() {

        CotEvent evt = new CotEvent();

        //Set a bunch of details to the COT message
        CoordinatedTime time = new CoordinatedTime();
        evt.setTime(time);
        evt.setStart(time);
        evt.setStale(time.addDays(1));

        evt.setUID(getUID());
        evt.setVersion("2.0");
        evt.setHow("h-e");
        evt.setType(getType());
        evt.setPoint(new CotPoint(getPoint()));

        CotDetail detail = new CotDetail("detail");
        evt.setDetail(detail);

        CotDetail model = new CotDetail("model");
        model.setAttribute("name", _img != null ? _img.name : "");
        detail.addChild(model);

        // Course (true degrees)
        CotDetail track = new CotDetail("track");
        detail.addChild(track);
        track.setAttribute("course", Double.toString(getAzimuth()));

        CotDetailManager.getInstance().addDetails(this, evt);

        return evt;
    }

    @Override
    public Bundle preDrawCanvas(CapturePP cap) {
        Bundle data = new Bundle();
        data.putParcelable("point", new PointA(
                cap.forward(getPoint()), (float) getAzimuth()));

        GeoPoint[] points = getCornerPoints();
        PointF[] corners = new PointF[4];
        for (int i = 0; i < 4; i++)
            corners[i] = cap.forward(points[i]);
        data.putSerializable("corners", corners);
        return data;
    }

    @Override
    public void drawCanvas(CapturePP cap, Bundle data) {
        PointA point = data.getParcelable("point");
        if (point == null)
            return;
        Canvas can = cap.getCanvas();
        Paint paint = cap.getPaint();
        float dr = cap.getResolution();
        Bitmap image = cap.loadBitmap(_img.imageUri);
        if (image == null)
            return;

        paint.setColorFilter(new PorterDuffColorFilter(getColor(),
                PorterDuff.Mode.MULTIPLY));
        paint.setStyle(Paint.Style.FILL);

        PointF[] corners = (PointF[]) data.getSerializable("corners");
        if (corners == null)
            return;
        float[] srcPoints = new float[] {
                0, 0,
                image.getWidth(), 0,
                image.getWidth(), image.getHeight(),
                0, image.getHeight()
        };
        int j = 0;
        float[] dstPoints = new float[8];
        for (int i = 0; i < 4; i++) {
            dstPoints[j] = corners[i].x * dr;
            dstPoints[j + 1] = corners[i].y * dr;
            j += 2;
        }
        Matrix imgMat = new Matrix();
        imgMat.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4);
        can.drawBitmap(image, imgMat, paint);
    }
}
