package com.atakmap.map;

import android.graphics.PointF;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;

import com.atakmap.interop.InteropCleaner;
import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import com.atakmap.map.projection.MapProjectionDisplayModel;
import com.atakmap.map.projection.Projection;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.math.GeometryModel;
import com.atakmap.math.PointD;
import com.atakmap.math.Matrix;
import com.atakmap.lang.ref.Cleaner;
import com.atakmap.util.Disposable;
import com.atakmap.util.ReadWriteLock;

public final class MapSceneModel implements Disposable {

    private final static String TAG = "MapSceneModel";

    final static Interop<Matrix> Matrix_interop = Interop.findInterop(Matrix.class);
    final static Interop<Projection> Projection_interop = Interop.findInterop(Projection.class);
    final static Interop<MapProjectionDisplayModel> MapProjectionDisplayModel_interop = Interop.findInterop(MapProjectionDisplayModel.class);
    final static Interop<GeometryModel> GeometryModel_interop = Interop.findInterop(GeometryModel.class);

    final static NativePeerManager.Cleaner CLEANER = new InteropCleaner(MapSceneModel.class);

    public GeometryModel earth;
    public MapCamera camera;
    public Projection mapProjection;

    public Matrix forward;
    public Matrix inverse;

    public int width;
    public int height;
    public float focusx;
    public float focusy;
    public double gsd;

    private boolean continuousScroll;
    private int hemisphere;
    private double westBound, eastBound;
    private boolean crossesIDL;

    public final MapProjectionDisplayModel displayModel;

    final ReadWriteLock rwlock = new ReadWriteLock();
    Pointer pointer;
    Object owner;
    Cleaner cleaner;

    /**
     * @deprecated Use {@link #MapSceneModel(double, int, int, Projection, GeoPoint, float, float, double, double, double, boolean)}
     * @param view
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public MapSceneModel(AtakMapView view) {
        this(view, view.getProjection(), new GeoPoint(view.getLatitude(), view.getLongitude()),
                view.getMapController().getFocusX(),
                view.getMapController().getFocusY(),
                view.getMapRotation(), view.getMapTilt(), view.getMapScale(),
                view.isContinuousScrollEnabled());

    }

    /**
     * @deprecated Use {@link #MapSceneModel(double, int, int, Projection, GeoPoint, float, float, double, double, double, boolean)}
     * @param view
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public MapSceneModel(AtakMapView view, Projection proj, GeoPoint focusGeo, float focusX, float focusY, double rotation, double scale) {
        this(view, proj, focusGeo, focusX, focusY, rotation, 0d, scale, false);
    }

    /**
     * @deprecated Use {@link #MapSceneModel(double, int, int, Projection, GeoPoint, float, float, double, double, double, boolean)}
     * @param view
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public MapSceneModel(AtakMapView view, Projection proj, GeoPoint focusGeo,
                        float focusX, float focusY, double rotation, double tilt,
                        double scale, boolean continuousScroll) {
        this(view.getDisplayDpi(), view.getWidth(), view.getHeight(),
            proj, focusGeo, focusX, focusY, rotation, tilt,
            Globe.getMapResolution(view.getDisplayDpi(), scale), continuousScroll);
    }

    public MapSceneModel(double displayDPI, int width, int height,
                        Projection proj, GeoPoint focusGeo, float focusX, float focusY,
                        double rotation, double tilt, double resolution,
                        boolean continuousScroll) {
        this(create(displayDPI,
                    width, height,
                    proj.getSpatialReferenceID(),
                    focusGeo.getLatitude(), focusGeo.getLongitude(), focusGeo.getAltitude(), focusGeo.getAltitudeReference() == GeoPoint.AltitudeReference.HAE,
                    focusX, focusY,
                    rotation,
                    tilt,
                    resolution),
      null);

        this.continuousScroll = continuousScroll;
    }

    MapSceneModel(Pointer pointer, Object owner) {
        cleaner = NativePeerManager.register(this, pointer, this.rwlock, null, CLEANER);

        this.pointer = pointer;
        this.owner = owner;

        this.displayModel = MapProjectionDisplayModel.getModel(getProjection(this.pointer.raw));
        this.width = getWidth(this.pointer.raw);
        this.height = getHeight(this.pointer.raw);
        this.mapProjection = ProjectionFactory.getProjection(getProjection(this.pointer.raw));
        this.earth = (this.displayModel != null) ? this.displayModel.earth : null;
        this.forward = Matrix_interop.create(getForward(this.pointer.raw), this);
        this.inverse = Matrix_interop.create(getInverse(this.pointer.raw), this);
        this.gsd = getGsd(this.pointer.raw);
        this.focusx = getFocusX(this.pointer.raw);
        this.focusy = getFocusY(this.pointer.raw);

        this.camera = new MapCamera();
        this.camera.aspectRatio = getCameraAspectRatio(this.pointer.raw);
        this.camera.azimuth = getCameraAzimuth(this.pointer.raw);
        this.camera.elevation = getCameraElevation(this.pointer.raw);
        this.camera.roll = getCameraRoll(this.pointer.raw);
        this.camera.near = getCameraNear(this.pointer.raw);
        this.camera.far = getCameraFar(this.pointer.raw);
        this.camera.fov = getCameraFov(this.pointer.raw);
        this.camera.location = new PointD(0d, 0d, 0d);
        getCameraLocation(this.pointer.raw, this.camera.location);
        this.camera.target = new PointD(0d, 0d, 0d);
        getCameraTarget(this.pointer.raw, this.camera.target);
        this.camera.projection = Matrix_interop.create(getCameraProjection(this.pointer.raw), this);
        this.camera.modelView = Matrix_interop.create(getCameraModelView(this.pointer.raw), this);
        this.camera.perspective = isCameraPerspective(this.pointer.raw);

        if(this.inverse == null)
            return;

        double[] fmx = new double[16];
        double[] imx = new double[16];
        this.forward.get(fmx);
        this.inverse.get(imx);

        GeoPoint focusGeo = inverse(new PointF(focusx, focusy), null);
        if(focusGeo == null)
            focusGeo = GeoPoint.ZERO_POINT;

        this.hemisphere = GeoCalculations.getHemisphere(focusGeo);

        // XXX - 
        this.continuousScroll = true;

        // Calculate east and west bounds for correct forward corrections
        // when crossing the IDL in continuous scroll mode
        GeoPoint west = inverse(new PointF(focusx - (width / 2f),
                height / 2f), null);
        GeoPoint east = inverse(new PointF(focusx + (width / 2f),
                height / 2f), null);
        if (west == null || east == null)
            return;
        this.westBound = west.getLongitude();
        this.eastBound = east.getLongitude();
        if (this.westBound < -180) {
            this.westBound += 360;
            this.crossesIDL = true;
        } else if (this.eastBound > 180) {
            this.eastBound -= 360;
            this.crossesIDL = true;
        }
    }
/*
    private static Matrix createProjection(double mapWidth, double mapHeight, double mapResolution, double mapTilt, double _range) {
        //final double scaleAdj = 1d + (Math.sin(Math.toRadians(mapTilt))*1.5d);
        final double scaleAdj = 1d;

        // XXX - account for tilt

        // http://www.mathopenref.com/sagitta.html
        final double wgs84Radius = Datum.WGS84.referenceEllipsoid.semiMajorAxis/2d;
        final double halfChordLength = Math.min(Datum.WGS84.referenceEllipsoid.semiMajorAxis, mapResolution*mapWidth)/2d;
        //final double far = Math.max(2d, _range+2000+(wgs84Radius-Math.sqrt((wgs84Radius*wgs84Radius)-(halfChordLength*halfChordLength)))+4500)*scaleAdj;
        final double far = Math.max(2d, _range+2000+halfChordLength);
        //final double near = Math.max(1d, _range-25000)/scaleAdj;
        double near=(_range*Math.sin(Math.toRadians(mapTilt)))*0.5+1;
        //final double near = 1d;
        //final double far = _range*4;

        //Calculate the distance to the horizon
        //double h=Math.max(_range-Datum.WGS84.referenceEllipsoid.semiMajorAxis,1.0);
        //double far=Math.sqrt(2.0*Datum.WGS84.referenceEllipsoid.semiMajorAxis*h+h*h);
        //far*=Math.cos(Math.atan(Datum.WGS84.referenceEllipsoid.semiMajorAxis/far)*(mapTilt / -90d));
        //double near=(_range*Math.sin(-mapTilt* Math.PI/180d))*0.5+1;

        if(mapTilt > 0d)
            Log.w(TAG, "MapSceneModel(resolution=" + mapResolution + ",range=" + _range + ",near=" + near + ",far=" + far + ")");

        Matrix xproj = Matrix.getIdentity();
        perspectiveM(xproj,
                     fov,
                     (double)mapWidth/(double)mapHeight,
                     near,
                     far);
        return xproj;
    }
*/


    public PointF forward(GeoPoint geo, PointF point) {
        if (this.continuousScroll && this.crossesIDL) {
            // Unwrap the forward longitude so we get a proper on-screen coordinate
            if (this.hemisphere == GeoCalculations.HEMISPHERE_EAST
                        && geo.getLongitude() < this.eastBound)
                geo = new GeoPoint(geo.getLatitude(), geo.getLongitude() + 360);
            else if (this.hemisphere == GeoCalculations.HEMISPHERE_WEST
                        && geo.getLongitude() > this.westBound)
                geo = new GeoPoint(geo.getLatitude(), geo.getLongitude() - 360);
        }
        PointD pointd = new PointD(0d, 0d, 0d);
        this.forward(geo, pointd);
        if(point == null)
            return new PointF((float)pointd.x, (float)pointd.y);
        point.x = (float)pointd.x;
        point.y = (float)pointd.y;

        return point;
    }

    public void forward(GeoPoint geo, PointD point) {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            forward(this.pointer.raw, geo.getLatitude(), geo.getLongitude(), geo.getAltitude(), geo.getAltitudeReference() == GeoPoint.AltitudeReference.HAE, point);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public GeoPoint inverse(PointF point, GeoPoint geo) {
        return this.inverse(point, geo, false);
    }

    public GeoPoint inverse(PointF point, GeoPoint geo, boolean nearestIfOffWorld) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            if(geo == null)
                geo = new GeoPoint(GeoPoint.UNKNOWN_POINT, GeoPoint.Access.READ_WRITE);
            final boolean success = inverse(this.pointer.raw, point.x, point.y, 0d, nearestIfOffWorld, geo);
            return success ? geo : null;
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Transforms the specified screen coordinate into LLA via a ray
     * intersection against the specified geometry.
     *
     * @param point The screen coordinate
     * @param geo   If non-<code>null</code>, stores the result
     * @param model The geometry, defined in the projected coordinate space
     *
     * @return  The LLA point at the specified screen coordinate or
     *          <code>null</code> if no intersection with the geometry could
     *          be computed.
     */
    public GeoPoint inverse(PointF point, GeoPoint geo, GeometryModel model) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            if(geo == null)
                geo = new GeoPoint(GeoPoint.UNKNOWN_POINT, GeoPoint.Access.READ_WRITE);

            final long ptr = GeometryModel_interop.getPointer(model);
            if(ptr != 0L) {
                final boolean success = inverse(this.pointer.raw, point.x, point.y, 0d, ptr, geo);
                return success ? geo : null;
            } else {
                Pointer wrapped = null;
                try {
                    wrapped = GeometryModel_interop.wrap(model);
                    final boolean success = inverse(this.pointer.raw, point.x, point.y, 0d, wrapped.raw, geo);
                    return success ? geo : null;
                } finally {
                    if(wrapped != null)
                        GeometryModel_interop.destruct(wrapped);
                }
            }
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void dispose() {
        if(this.cleaner != null)
            this.cleaner.clean();
    }

    // XXX - note to self
    /* for perspective
        org.x = this.camera.location.x;
        org.y = this.camera.location.y;
        org.z = this.camera.location.z;

        tgt.z = 0.0001d;
        this.inverse.transform(tgt, tgt);
      */
     /* from camera plane
        // construct plane camera with normal cam->tgt
        Plane cameraP = new Plane(new Vector3D(camera.target.x-camera.location.x,
                                               camera.target.y-camera.location.y,
                                               camera.target.z-camera.location.z).normalize(),
                                  camera.location);
        // XXX - cast ray in scene into camera plane
        PointD p0 = inverse.transform(new PointD(point.x, point.y, 2d), null);
        PointD p1 = inverse.transform(new PointD(point.x, point.y, 1d), null);

        // XXX - org is camera plane isect, tgt unchanged
        org = cameraP.intersect(new Ray(p0, new Vector3D(p1.x-p0.x, p1.y-p0.y, p1.z-p0.z).normalize()));
        this.inverse.transform(tgt, tgt);
      */

     private static com.atakmap.map.layer.feature.geometry.Envelope transform(com.atakmap.map.layer.feature.geometry.Envelope mbb, Projection proj) {
         PointD[] p = new PointD[8];
         int idx = 0;
         p[idx++] = proj.forward(new GeoPoint(mbb.minY, mbb.minX, mbb.minZ), null);
         p[idx++] = proj.forward(new GeoPoint(mbb.minY, mbb.maxX, mbb.minZ), null);
         p[idx++] = proj.forward(new GeoPoint(mbb.maxY, mbb.maxX, mbb.minZ), null);
         p[idx++] = proj.forward(new GeoPoint(mbb.maxY, mbb.minX, mbb.minZ), null);
         p[idx++] = proj.forward(new GeoPoint(mbb.minY, mbb.minX, mbb.maxZ), null);
         p[idx++] = proj.forward(new GeoPoint(mbb.minY, mbb.maxX, mbb.maxZ), null);
         p[idx++] = proj.forward(new GeoPoint(mbb.maxY, mbb.maxX, mbb.maxZ), null);
         p[idx++] = proj.forward(new GeoPoint(mbb.maxY, mbb.minX, mbb.maxZ), null);

         mbb.minX = p[0].x;
         mbb.minY = p[0].y;
         mbb.minZ = p[0].z;
         mbb.maxX = p[0].x;
         mbb.maxY = p[0].y;
         mbb.maxZ = p[0].z;

         for(int i = 1; i < idx; i++) {
             if(p[i].x < mbb.minX)      mbb.minX = p[i].x;
             else if(p[i].x > mbb.minX) mbb.maxX = p[i].x;
             if(p[i].y < mbb.minY)      mbb.minY = p[i].y;
             else if(p[i].y > mbb.minY) mbb.maxY = p[i].y;
             if(p[i].z < mbb.minZ)      mbb.minZ = p[i].z;
             else if(p[i].z > mbb.minZ) mbb.maxZ = p[i].z;
         }

         return mbb;
     }

    /**
     *
     * @param model
     * @param mbbMinX   The minimum longitude of the MBB
     * @param mbbMinY   The minimum latitude of the MBB
     * @param mbbMinZ   The minimum altitude (HAE) of the MBB
     * @param mbbMaxX   The maximum longitude of the MBB
     * @param mbbMaxY   The maximum latitude of the MBB
     * @param mbbMaxZ   The maximum altitude (HAE) of the MBB
     * @return  <code>true</code> if the bounding region intersets the view
     *          frustum, <code>false</code> otherwise.
     *
     */
    public static boolean intersects(MapSceneModel model, double mbbMinX, double mbbMinY, double mbbMinZ, double mbbMaxX, double mbbMaxY, double mbbMaxZ) {
         if(false) {
             return intersectsImpl(model, mbbMinX, mbbMinY, mbbMinZ,
                     mbbMaxX, mbbMaxY, mbbMaxZ);
         }
        return intersects(model.pointer.raw,
                mbbMinX, mbbMinY, mbbMinZ,
                mbbMaxX, mbbMaxY, mbbMaxZ);
    }

    private static boolean intersectsImpl(MapSceneModel scene, double mbbMinX, double mbbMinY, double mbbMinZ, double mbbMaxX, double mbbMaxY, double mbbMaxZ) {
        Matrix xform = scene.forward;

        double minX = Double.NaN;
        double minY = Double.NaN;
        double minZ = Double.NaN;
        double maxX = Double.NaN;
        double maxY = Double.NaN;
        double maxZ = Double.NaN;

        // transform the MBB to the native projection
        if(scene.mapProjection.getSpatialReferenceID() != 4326) {
            GeoPoint[] points = new GeoPoint[8];
            points[0] = new GeoPoint(mbbMinY, mbbMinX, mbbMinZ);
            points[1] = new GeoPoint(mbbMinY, mbbMaxX, mbbMinZ);
            points[2] = new GeoPoint(mbbMaxY, mbbMaxX, mbbMinZ);
            points[3] = new GeoPoint(mbbMaxY, mbbMinX, mbbMinZ);
            points[4] = new GeoPoint(mbbMinY, mbbMinX, mbbMaxZ);
            points[5] = new GeoPoint(mbbMinY, mbbMaxX, mbbMaxZ);
            points[6] = new GeoPoint(mbbMaxY, mbbMaxX, mbbMaxZ);
            points[7] = new GeoPoint(mbbMaxY, mbbMinX, mbbMaxZ);

            int idx = 0;
            for( ; idx < 8; idx++) {
                PointD scratch = new PointD(0d, 0d, 0d);
                if(scene.mapProjection.forward(points[idx], scratch) == null)
                    continue;
                mbbMinX = scratch.x;
                mbbMinY = scratch.y;
                mbbMinZ = scratch.z;
                mbbMaxX = scratch.x;
                mbbMaxY = scratch.y;
                mbbMaxZ = scratch.z;
                break;
            }
            if(idx == 8)
                return false;
            for( ; idx < 8; idx++) {
                PointD scratch = new PointD(0d, 0d, 0d);
                if(scene.mapProjection.forward(points[idx], scratch) == null)
                    continue;
                if(scratch.x < mbbMinX)        mbbMinX = scratch.x;
                else if(scratch.x > mbbMaxX)   mbbMaxX = scratch.x;
                if(scratch.y < mbbMinY)        mbbMinY = scratch.y;
                else if(scratch.y > mbbMaxY)   mbbMaxY = scratch.y;
                if(scratch.z < mbbMinZ)        mbbMinZ = scratch.z;
                else if(scratch.z > mbbMaxZ)   mbbMaxZ = scratch.z;
            }
        }

        PointD[] points = new PointD[8];
        points[0] = new PointD(mbbMinX, mbbMinY, mbbMinZ);
        points[1] = new PointD(mbbMinX, mbbMaxY, mbbMinZ);
        points[2] = new PointD(mbbMaxX, mbbMaxY, mbbMinZ);
        points[3] = new PointD(mbbMaxX, mbbMinY, mbbMinZ);
        points[4] = new PointD(mbbMinX, mbbMinY, mbbMaxZ);
        points[5] = new PointD(mbbMinX, mbbMaxY, mbbMaxZ);
        points[6] = new PointD(mbbMaxX, mbbMaxY, mbbMaxZ);
        points[7] = new PointD(mbbMaxX, mbbMinY, mbbMaxZ);

        int idx = 0;
        for( ; idx < 8; idx++) {
            PointD scratch = new PointD(0d, 0d, 0d);
            if(xform.transform(points[idx], scratch)  == null )
                continue;
            minX = scratch.x;
            minY = scratch.y;
            minZ = scratch.z;
            maxX = scratch.x;
            maxY = scratch.y;
            maxZ = scratch.z;
            break;
        }
        if(idx == 8)
            return false;
        for( ; idx < 8; idx++) {
            PointD scratch = new PointD(0d, 0d, 0d);
            if(xform.transform(points[idx], scratch)  == null )
                continue;
            if(scratch.x < minX)        minX = scratch.x;
            else if(scratch.x > maxX)   maxX = scratch.x;
            if(scratch.y < minY)        minY = scratch.y;
            else if(scratch.y > maxY)   maxY = scratch.y;
            if(scratch.z < minZ)        minZ = scratch.z;
            else if(scratch.z > maxZ)   maxZ = scratch.z;
        }

        return com.atakmap.math.Rectangle.intersects(0, 0, scene.width, scene.height, minX, 0, maxX, scene.height);
    }

    // Interop<MapSceneModel> interface
    static long getPointer(MapSceneModel obj) {
        if(obj == null)
            return 0L;
        obj.rwlock.acquireRead();
        try {
            return obj.pointer.raw;
        } finally {
            obj.rwlock.releaseRead();
        }
    }
    static MapSceneModel create(Pointer pointer, Object owner) {
        return new MapSceneModel(pointer, owner);
    }
    static native Pointer clone(long pointer);
    static native void destruct(Pointer pointer);

    // JNI interface
    static native Pointer create(double displayDPI,
                                 int width,
                                 int height,
                                 int srid,
                                 double focusLat,
                                 double focusLng,
                                 double focusAlt,
                                 boolean focusAltAbsolute,
                                 float focusX,
                                 float focusY,
                                 double rotation,
                                 double tilt,
                                 double resolution);

    static native Pointer getEarth(long pointer);
    static native int getProjection(long pointer);
    static native Pointer getForward(long pointer);
    static native Pointer getInverse(long pointer);
    static native int getWidth(long pointer);
    static native int getHeight(long pointer);
    static native float getFocusX(long pointer);
    static native float getFocusY(long pointer);
    static native double getGsd(long pointer);
    static native Pointer getDisplayModel(long pointer);

    //public Matrix projection;
    static native Pointer getCameraProjection(long pointer);
    //public Matrix modelView;
    static native Pointer getCameraModelView(long pointer);
    //public PointD location;
    static native void getCameraLocation(long pointer, PointD value);
    //public PointD target;
    static native void getCameraTarget(long pointer, PointD value);
    //public double roll;
    static native double getCameraRoll(long pointer);
    //public double elevation;
    static native double getCameraElevation(long pointer);
    //public double azimuth;
    static native double getCameraAzimuth(long pointer);
    //public double fov;
    static native double getCameraFov(long pointer);
    //public double aspectRatio;
    static native double getCameraAspectRatio(long pointer);
    //public double near;
    static native double getCameraNear(long pointer);
    //public double far;
    static native double getCameraFar(long pointer);

    static native boolean isCameraPerspective(long pointer);

    static native void forward(long pointer, double latitude, double longitude, double altitude, boolean altAbsolute, PointD result);
    static native boolean inverse(long pointer, double x, double y, double z, boolean offworld, GeoPoint result);
    static native boolean inverse(long pointer, double x, double y, double z, long geomModelPtr, GeoPoint result);


    public static native void setPerspectiveCameraEnabled(boolean e);
    public static native boolean isPerspectiveCameraEnabled();

    static native boolean intersects(long ptr,
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ);
}