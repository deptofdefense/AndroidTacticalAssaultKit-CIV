
package com.atakmap.android.vehicle.model;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;

import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.icons.UserIcon;
import com.atakmap.android.imagecapture.CanvasHelper;
import com.atakmap.android.imagecapture.Capturable;
import com.atakmap.android.imagecapture.CapturePP;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.model.opengl.GLModelCaptureCache;
import com.atakmap.android.rubbersheet.data.ModelProjection;
import com.atakmap.android.rubbersheet.data.RubberModelData;
import com.atakmap.android.rubbersheet.data.RubberSheetUtils;
import com.atakmap.android.rubbersheet.maps.LoadState;
import com.atakmap.android.rubbersheet.maps.RubberModel;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.vehicle.VehicleMapItem;
import com.atakmap.android.vehicle.model.icon.VehicleModelCaptureRequest;
import com.atakmap.app.R;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.NorthReference;
import com.atakmap.coremap.maps.coords.Vector2D;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.ModelInfo;

import java.io.File;
import java.io.OutputStream;
import java.util.List;

/**
 * 3D vehicle model
 */
public class VehicleModel extends RubberModel implements Capturable,
        VehicleMapItem, Marker.OnTrackChangedListener {

    private static final String TAG = "VehicleModel";
    public static final String COT_TYPE = "u-d-v-m";

    // In order to satisfy the rubber model constructor we need to pass
    // a struct containing some background info
    private static class VehicleModelData extends RubberModelData {

        String uid;
        VehicleModelInfo info;

        VehicleModelData(VehicleModelInfo info, GeoPointMetaData center,
                String uid) {

            this.info = info;
            this.label = info.name;
            this.center = center;
            this.uid = uid;
            this.rotation = new double[] {
                    0, 0, 0
            };
            this.scale = new double[] {
                    1, 1, 1
            };
            this.projection = ModelProjection.ENU_FLIP_YZ;
            this.dimensions = new double[] {
                    10, 10, 10
            };
            this.file = info.file;
            Model model = info.getModel();
            if (model != null) {
                Envelope e = model.getAABB();
                this.dimensions[0] = Math.abs(e.maxX - e.minX);
                this.dimensions[1] = Math.abs(e.maxY - e.minY);
                this.dimensions[2] = Math.abs(e.maxZ - e.minZ);
            }

            this.points = RubberSheetUtils.computeCorners(center.get(),
                    this.dimensions[1], this.dimensions[0], 0);
        }

        @Override
        public String getUID() {
            return this.uid;
        }
    }

    private VehicleModelInfo _info;
    private double _width, _length, _height;

    public VehicleModel(VehicleModelInfo info, GeoPointMetaData center,
            String uid) {
        this(new VehicleModelData(info, center, uid));
    }

    public VehicleModel(VehicleModelData data) {
        super(data);
        getCenterMarker().setPoint(data.center);
        setType(COT_TYPE);
        setSharedModel(true);
        showLines(false);
        setMetaString("iconUri", ATAKUtilities.getResourceUri(
                R.drawable.pointtype_aircraft));
        removeMetaData("nevercot");
        setMetaBoolean("archive", true);
        setLabelVisibility(true);
        setVehicleInfo(data.info);
    }

    public void setVehicleInfo(VehicleModelInfo info) {
        if (_info == info)
            return;

        _info = info;
        VehicleModelCache.getInstance().registerUsage(info, getUID());
        ModelInfo mInfo = info.getInfo();
        if (mInfo != null)
            mInfo = new ModelInfo(mInfo);
        onLoad(mInfo, info.getModel());
        double[] dim = getModelDimensions(false);
        _width = dim[0];
        _length = dim[1];
        _height = dim[2];
        updateLegacyIconPath();
        setLoadState(LoadState.SUCCESS);

        // Update offscreen indicator icon
        Marker center = getCenterMarker();
        if (center != null)
            center.setMetaString("offscreen_icon_uri", info.getIconURI());

        // Update points
        setPoints(getCenter(), getWidth(), getLength(), getHeading());
    }

    public VehicleModelInfo getVehicleInfo() {
        return _info;
    }

    @Override
    protected String getCotType() {
        return COT_TYPE;
    }

    @Override
    public Drawable getIconDrawable() {
        return _info.getIcon();
    }

    @Override
    protected String getMenuPath() {
        return "menus/vehicle_model_menu.xml";
    }

    @Override
    public double getWidth() {
        return _width;
    }

    @Override
    public double getLength() {
        return _length;
    }

    @Override
    public double getHeight() {
        return _height;
    }

    @Override
    public void setCenterMarker(Marker marker) {
        Marker existing = getCenterMarker();
        if (marker != null && existing == marker)
            return;

        if (existing != null)
            existing.removeOnTrackChangedListener(this);

        super.setCenterMarker(marker);

        if (marker != null && !isCenterShapeMarker()) {
            setHeading(marker.getTrackHeading());
            marker.addOnTrackChangedListener(this);
        }
    }

    public void setCenter(GeoPointMetaData point) {
        setPoints(point, getWidth(), getLength(), getHeading());
    }

    @Override
    public void setStrokeColor(int strokeColor) {
        super.setStrokeColor(strokeColor);
        Marker center = getCenterMarker();
        if (center != null)
            center.setMetaInteger("offscreen_icon_color", strokeColor);
    }

    /**
     * Set the azimuth/heading of this vehicle model
     * @param deg Degrees
     * @param ref Reference the degrees are in
     */
    @Override
    public void setAzimuth(double deg, NorthReference ref) {
        double trueDeg = deg;
        if (ref.equals(NorthReference.MAGNETIC))
            trueDeg = ATAKUtilities.convertFromMagneticToTrue(
                    getCenterPoint(), deg);
        trueDeg = (trueDeg % 360) + (trueDeg < 0 ? 360 : 0);
        if (Double.compare(getAzimuth(NorthReference.TRUE), trueDeg) != 0)
            setHeading(deg);
    }

    /**
     * Get the azimuth/heading of this vehicle model
     * @param ref Desired reference
     * @return Azimuth in degrees
     */
    @Override
    public double getAzimuth(NorthReference ref) {
        double azi = getHeading();
        if (ref.equals(NorthReference.MAGNETIC))
            return ATAKUtilities.convertFromTrueToMagnetic(
                    getCenterPoint(), azi);
        return azi;
    }

    @Override
    protected void onVisibleChanged() {
        super.onVisibleChanged();
        for (PointMapItem pmi : getAnchorMarkers())
            pmi.setVisible(false);
    }

    @Override
    public void onAdded(MapGroup parent) {
        super.onAdded(parent);
        parent.addGroup(getChildMapGroup());
    }

    @Override
    public void onRemoved(MapGroup parent) {
        super.onRemoved(parent);
        parent.removeGroup(getChildMapGroup());
    }

    @Override
    public void onTrackChanged(Marker marker) {
        setHeading(marker.getTrackHeading());
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
        if (item == getCenterMarker()) {
            if (isCenterShapeMarker() || !hasMetaValue("archive"))
                removeFromGroup();
            else
                setCenterMarker(null);
        }
    }

    /**
     * Set whether to show the vehicle outline
     * @param outline True to show outline
     */
    public boolean setShowOutline(boolean outline) {
        if (showOutline() != outline) {
            toggleMetaData("outline", outline);
            notifyMetadataChanged("outline");
            return true;
        }
        return false;
    }

    public boolean showOutline() {
        return hasMetaValue("outline");
    }

    public void updateOffscreenInterest() {
        Marker center = getCenterMarker();
        if (center != null)
            center.setMetaLong("offscreen_interest",
                    SystemClock.elapsedRealtime());
    }

    @Override
    protected boolean isModelVisible() {
        return super.isModelVisible() || showOutline();
    }

    /**
     * For legacy devices receiving this CoT that don't have the ability to
     * show the 3-D model, set a reasonable icon
     */
    private void updateLegacyIconPath() {
        String iconPath = "34ae1613-9645-4222-a9d2-e5f243dea2865/";
        switch (_info.category) {
            case "Aircraft":
                if (_info.name.contains("H-"))
                    iconPath += "Military/LittleBird.png";
                else
                    iconPath += "Transportation/Plane8.png";
                break;
            default:
            case "Automobiles":
                iconPath += "Transportation/Car8.png";
                break;
            case "Maritime":
                iconPath += "Transportation/Ship3.png";
                break;
        }
        setMetaString(UserIcon.IconsetPath, iconPath);
    }

    @Override
    protected boolean orthoHitModel(int x, int y, GeoPoint point,
            MapView view) {
        return showOutline() && orthoHitOutline(x, y, view)
                || super.orthoHitModel(x, y, point, view);
    }

    private boolean orthoHitOutline(int x, int y, MapView view) {
        List<PointF> points = _info.getOutline(null);
        if (points == null || view.getMapTilt() > 0)
            return false;

        // The outline points are in meters offset from the center, so
        // for performance we apply transformations to the touch point
        // rather than the outline
        double res = view.getMapResolution();
        GeoPoint centerGP = getCenterPoint();
        PointF center = view.forward(centerGP);

        // X and Y click point in meters offset from center
        float mx = (float) ((x - center.x) * res);
        float my = (float) ((center.y - y) * res);

        // Apply reverse rotation to click (instead of rotating the entire outline)
        double a = Math.toRadians(getAzimuth(NorthReference.TRUE)
                - view.getMapRotation());
        double aCos = Math.cos(-a), aSin = Math.sin(-a);
        float rmx = (float) (mx * aCos + my * aSin);
        float rmy = (float) (my * aCos - mx * aSin);
        mx = rmx;
        my = rmy;

        // Convert hit radius to meters
        float mr = (float) (getHitRadius(view) * res);
        float mr2 = mr / 2f, mrSq = mr * mr;

        // Perform hit test on vertices and lines
        PointF lp = null;
        Vector2D touch = new Vector2D(mx, my);
        PointF hit = null;
        for (PointF p : points) {
            // Test hit on vertex
            if (Math.abs(p.x - mx) < mr2 && Math.abs(p.y - my) < mr2) {
                hit = p;
                break;
            }
            // Test hit on last line
            if (lp != null) {
                Vector2D nearest = Vector2D.nearestPointOnSegment(touch,
                        new Vector2D(p.x, p.y),
                        new Vector2D(lp.x, lp.y));
                double dist = nearest.distanceSq(touch);
                if (mrSq > dist) {
                    hit = new PointF((float) nearest.x,
                            (float) nearest.y);
                    break;
                }
            }
            lp = p;
        }

        if (hit == null)
            return false;

        // Found a hit
        double azimuth = Math.toDegrees(Math.atan2(hit.x, hit.y) + a)
                + view.getMapRotation();
        double dist = Math.hypot(hit.x, hit.y);
        GeoPoint point = new GeoPoint(GeoCalculations.pointAtDistance(
                centerGP, azimuth, dist),
                GeoPoint.Access.READ_WRITE);
        double alt = ElevationManager.getElevation(
                point.getLatitude(), point.getLongitude(), null);
        point.set(alt);
        setTouchPoint(point);
        setMetaString("menu_point", point.toString());
        return true;
    }

    @Override
    public CotEvent toCot() {
        CotEvent event = new CotEvent();

        CoordinatedTime time = new CoordinatedTime();
        event.setTime(time);
        event.setStart(time);
        event.setStale(time.addDays(1));

        event.setUID(getUID());
        event.setVersion("2.0");
        event.setHow("h-e");

        event.setPoint(new CotPoint(getCenterPoint()));
        event.setType(getCotType());

        CotDetail detail = new CotDetail("detail");
        event.setDetail(detail);

        // Model info
        CotDetail model = new CotDetail("model");
        model.setAttribute("type", "vehicle");
        model.setAttribute("name", _info.name);
        model.setAttribute("category", _info.category);
        model.setAttribute("outline", String.valueOf(showOutline()));
        detail.addChild(model);

        // Course (true degrees)
        CotDetail track = new CotDetail("track");
        track.setAttribute("course",
                Double.toString(getAzimuth(NorthReference.TRUE)));
        detail.addChild(track);

        CotDetailManager.getInstance().addDetails(this, event);

        return event;
    }

    // Maximum width/height for a vehicle capture
    private static final int MAX_RENDER_SIZE = 1024;

    @Override
    public Bundle preDrawCanvas(CapturePP cap) {
        Bundle data = new Bundle();
        PointF[] corners = new PointF[4];
        for (int i = 0; i < 4; i++)
            corners[i] = cap.forward(getPoint(i));
        data.putSerializable("corners", corners);

        if (showOutline()) {
            // Vehicle outline points, in meters offset from center
            List<PointF> points = getOutline();
            if (!FileSystemUtils.isEmpty(points)) {
                // Need to get geo point and forward
                GeoPoint center = getCenterPoint();
                double heading = getHeading();
                PointF[] outline = new PointF[points.size()];
                PointF pCen = new PointF();
                for (int i = 0; i < outline.length; i++) {
                    PointF src = points.get(i);
                    double a = CanvasHelper.angleTo(pCen, src);
                    double d = CanvasHelper.length(pCen, src);
                    a += heading;
                    outline[i] = cap.forward(DistanceCalculations
                            .computeDestinationPoint(center, a, d));
                }
                data.putSerializable("outline", outline);
            }
        }

        return data;
    }

    @Override
    public void drawCanvas(final CapturePP cap, Bundle data) {
        PointF[] corners = (PointF[]) data.getSerializable("corners");
        if (corners == null)
            return;

        Canvas can = cap.getCanvas();
        Paint paint = cap.getPaint();
        Path path = cap.getPath();
        int color = getStrokeColor();
        int alpha = getAlpha();
        float lineWeight = cap.getLineWeight();
        float dr = cap.getResolution();

        // Check if the outline is visible and available
        PointF[] outline = (PointF[]) data.getSerializable("outline");
        if (showOutline() && outline != null) {

            // Draw outline
            for (int i = 0; i < outline.length; i++) {
                PointF p = outline[i];
                if (i == 0)
                    path.moveTo(dr * p.x, dr * p.y);
                else
                    path.lineTo(dr * p.x, dr * p.y);
            }
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f * lineWeight);
            can.drawPath(path, paint);
            path.reset();
        }

        // Check if the vehicle model is visible
        if (getAlpha() > 0) {

            // Get the vehicle render bitmap
            Bitmap bmp = getBitmap();

            // Map vehicle bitmap to on screen coordinates
            int w = bmp.getWidth(), h = bmp.getHeight();
            float[] srcPoints = new float[] {
                    0, 0,
                    w, 0,
                    w, h,
                    0, h
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

            // Draw the vehicle bitmap
            paint.setColorFilter(new PorterDuffColorFilter(color,
                    PorterDuff.Mode.MULTIPLY));
            paint.setAlpha(alpha);
            paint.setStyle(Paint.Style.FILL);
            can.drawBitmap(bmp, imgMat, paint);
        }
    }

    /**
     * Get or create bitmap display of the vehicle
     * If a cached image of the bitmap does not exist, it is created
     * @return Bitmap of the vehicle
     */
    private Bitmap getBitmap() {
        VehicleModelCaptureRequest req = new VehicleModelCaptureRequest(_info);
        req.setOutputSize(MAX_RENDER_SIZE, true);
        req.setLightingEnabled(true);

        GLModelCaptureCache cache = new GLModelCaptureCache(
                _info.category + "/" + _info.name);
        return cache.getBitmap(req);
    }

    private List<PointF> getOutline() {
        List<PointF> points = _info.getOutline(new Runnable() {
            @Override
            public void run() {
                synchronized (VehicleModel.this) {
                    VehicleModel.this.notify();
                }
            }
        });

        if (points != null)
            return points;

        // Wait for points generation to finish if we need
        synchronized (this) {
            while (true) {
                try {
                    this.wait();
                    break;
                } catch (Exception ignored) {
                }
            }
        }

        return _info.getOutline(null);
    }
}
