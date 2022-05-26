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

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
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
    public double dpi;

    private boolean continuousScroll;
    private int hemisphere;
    private double westBound, eastBound;
    private boolean crossesIDL;

    public MapProjectionDisplayModel displayModel;

    final ReadWriteLock rwlock = new ReadWriteLock();
    Pointer pointer;
    Object owner;
    Cleaner cleaner;


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

    /**
     * Copy constructor.
     *
     * @param other
     */
    public MapSceneModel(MapSceneModel other) {
        this(clone(other.pointer.raw), null);
    }

    MapSceneModel(Pointer pointer, Object owner) {
        cleaner = NativePeerManager.register(this, pointer, this.rwlock, null, CLEANER);

        this.pointer = pointer;
        this.owner = owner;

        init();
    }

    void init() {
        this.displayModel = MapProjectionDisplayModel.getModel(getProjection(this.pointer.raw));
        this.width = getWidth(this.pointer.raw);
        this.height = getHeight(this.pointer.raw);
        this.mapProjection = ProjectionFactory.getProjection(getProjection(this.pointer.raw));
        this.earth = (this.displayModel != null) ? this.displayModel.earth : null;
        if(this.forward == null)
            this.forward = Matrix_interop.create(getForward(this.pointer.raw), this);
        if(this.inverse == null)
            this.inverse = Matrix_interop.create(getInverse(this.pointer.raw), this);
        this.gsd = getGsd(this.pointer.raw);
        this.dpi = getDpi(this.pointer.raw);
        this.focusx = getFocusX(this.pointer.raw);
        this.focusy = getFocusY(this.pointer.raw);

        if(this.camera == null)
            this.camera = new MapCamera();
        this.camera.aspectRatio = getCameraAspectRatio(this.pointer.raw);
        this.camera.azimuth = getCameraAzimuth(this.pointer.raw);
        this.camera.elevation = getCameraElevation(this.pointer.raw);
        this.camera.roll = getCameraRoll(this.pointer.raw);
        this.camera.near = getCameraNear(this.pointer.raw);
        this.camera.nearMeters = getCameraNearMeters(this.pointer.raw);
        this.camera.far = getCameraFar(this.pointer.raw);
        this.camera.farMeters = getCameraFarMeters(this.pointer.raw);
        this.camera.fov = getCameraFov(this.pointer.raw);
        this.camera.location = new PointD(0d, 0d, 0d);
        getCameraLocation(this.pointer.raw, this.camera.location);
        this.camera.target = new PointD(0d, 0d, 0d);
        getCameraTarget(this.pointer.raw, this.camera.target);
        if(this.camera.projection == null)
            this.camera.projection = Matrix_interop.create(getCameraProjection(this.pointer.raw), this);
        if(this.camera.modelView == null)
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
        this.crossesIDL = false;

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

    public void set(double displayDPI, int width, int height,
                    Projection proj, GeoPoint focusGeo, float focusX, float focusY,
                    double rotation, double tilt, double resolution,
                    boolean continuousScroll) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                return;
            set(this.pointer.raw,
                    displayDPI,
                    width, height,
                    proj.getSpatialReferenceID(),
                    focusGeo.getLatitude(), focusGeo.getLongitude(), focusGeo.getAltitude(), focusGeo.getAltitudeReference() == GeoPoint.AltitudeReference.HAE,
                    focusX, focusY,
                    rotation,
                    tilt,
                    resolution);

            this.continuousScroll = continuousScroll;
            init();
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public void set(MapSceneModel other) {
        other.rwlock.acquireRead();
        try {
            if(other.pointer.raw == 0L)
                throw new IllegalArgumentException();
            this.rwlock.acquireRead();
            try {
                if (this.pointer.raw == 0L)
                    return;
                set(this.pointer.raw, other.pointer.raw);
                this.continuousScroll = other.continuousScroll;
                init();
            } finally {
                this.rwlock.releaseRead();
            }
        } finally {
            other.rwlock.releaseRead();
        }
    }

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

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof MapSceneModel))
            return false;

        final MapSceneModel other = (MapSceneModel)o;
        if(this == other)
            return true;

        // check projection
        if(this.mapProjection.getSpatialReferenceID() != other.mapProjection.getSpatialReferenceID())
            return false;

        if(this.gsd != other.gsd)
            return false;

        if(this.width != other.width || this.height != other.height)
            return false;

        if(this.focusx != other.focusx || this.focusy != other.focusy)
            return false;

        // check camera definition
        return this.camera.azimuth == other.camera.azimuth &&
                this.camera.location.x == other.camera.location.x &&
                this.camera.location.y == other.camera.location.y &&
                this.camera.location.z == other.camera.location.z &&
                this.camera.target.x == other.camera.target.x &&
                this.camera.target.y == other.camera.target.y &&
                this.camera.target.z == other.camera.target.z &&
                this.camera.roll == other.camera.roll &&
                this.camera.fov == other.camera.fov;
    }

    /**
     * AABB intersection test with the viewing frustum.
     *
     * @param model
     * @param mbbMinX   The minimum longitude of the MBB
     * @param mbbMinY   The minimum latitude of the MBB
     * @param mbbMinZ   The minimum altitude (HAE) of the MBB
     * @param mbbMaxX   The maximum longitude of the MBB
     * @param mbbMaxY   The maximum latitude of the MBB
     * @param mbbMaxZ   The maximum altitude (HAE) of the MBB
     *
     * @return  <code>true</code> if the bounding region intersects the view
     *          frustum, <code>false</code> otherwise.
     */
    public static boolean intersects(MapSceneModel model, double mbbMinX, double mbbMinY, double mbbMinZ, double mbbMaxX, double mbbMaxY, double mbbMaxZ) {
        return intersectsAAbbWgs84(model.pointer.raw,
                mbbMinX, mbbMinY, mbbMinZ,
                mbbMaxX, mbbMaxY, mbbMaxZ);
    }

    /**
     * Bounding sphere intersection test with the viewing frustum.
     *
     * @param model
     * @param latitude      Latitude of the center of the sphere
     * @param longitude     Longitude of the center of the sphere
     * @param hae           Altitude of the center of the sphere, meters HAE
     * @param radiusMeters  The radius of the sphere, in meters
     *
     * @return  <code>true</code> if the bounding region intersects the view
     *          frustum, <code>false</code> otherwise.
     */
    public static boolean intersects(MapSceneModel model, double latitude, double longitude, double hae, double radiusMeters) {
        return intersectsSphereWgs84(model.pointer.raw,
                longitude, latitude, hae, radiusMeters);
    }

    public static native double gsd(double range, double vfov, int sceneHeightPixels);
    public static native double range(double gsd, double vfov, int sceneHeightPixels);

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
   static native void set(long pointer,
                          double displayDPI,
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
   static native void set(long ptr, long otherPtr);

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
    static native double getDpi(long ptr);

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
    static native double getCameraNearMeters(long pointer);
    static native double getCameraFarMeters(long pointer);

    static native boolean isCameraPerspective(long pointer);

    static native void forward(long pointer, double latitude, double longitude, double altitude, boolean altAbsolute, PointD result);
    static native boolean inverse(long pointer, double x, double y, double z, boolean offworld, GeoPoint result);
    static native boolean inverse(long pointer, double x, double y, double z, long geomModelPtr, GeoPoint result);


    public static native void setPerspectiveCameraEnabled(boolean e);
    public static native boolean isPerspectiveCameraEnabled();

    static native boolean intersectsAAbbWgs84(long ptr,
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ);
    static native boolean intersectsSphereWgs84(long ptr,
        double cx, double cy, double cz,
        double radius);
}