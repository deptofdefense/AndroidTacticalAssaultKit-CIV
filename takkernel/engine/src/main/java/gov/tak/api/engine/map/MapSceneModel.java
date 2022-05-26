package gov.tak.api.engine.map;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import gov.tak.api.engine.map.coords.IProjection;
import gov.tak.api.engine.math.IGeometryModel;
import gov.tak.api.engine.math.IMatrix;
import gov.tak.api.engine.math.PointD;
import gov.tak.api.engine.math.Ray;
import gov.tak.api.marshal.IMarshal;
import gov.tak.api.util.Disposable;
import gov.tak.platform.graphics.PointF;
import gov.tak.platform.marshal.MarshalManager;

public final class MapSceneModel implements Disposable {

    static {
        MarshalManager.registerMarshal(new IMarshal() {
            @Override
            public <T, V> T marshal(V in) {
                if(in == null)  return null;
                return (T)new MapSceneModel((com.atakmap.map.MapSceneModel)in);
            }
        }, com.atakmap.map.MapSceneModel.class, gov.tak.api.engine.map.MapSceneModel.class);
        MarshalManager.registerMarshal(new IMarshal() {
            @Override
            public <T, V> T marshal(V in) {
                if(in == null)
                    return null;
                else
                    return (T)((MapSceneModel)in)._impl;
            }
        }, gov.tak.api.engine.map.MapSceneModel.class, com.atakmap.map.MapSceneModel.class);
    }

    private final static String TAG = "MapSceneModel";

    public MapCamera camera;
    public IProjection mapProjection;

    public IMatrix forward;
    public IMatrix inverse;

    public int width;
    public int height;
    public float focusx;
    public float focusy;
    public double gsd;
    public double dpi;

    public MapProjectionDisplayModel displayModel;

    private final com.atakmap.map.MapSceneModel _impl;

    public MapSceneModel(double displayDPI, int width, int height,
                         IProjection proj, gov.tak.api.engine.map.coords.GeoPoint focusGeo, float focusX, float focusY,
                         double rotation, double tilt, double resolution,
                         boolean continuousScroll) {
        this(new com.atakmap.map.MapSceneModel(
                    displayDPI,
                    width, height,
                    (proj != null) ?
                        com.atakmap.map.projection.ProjectionFactory.getProjection(proj.getSpatialReferenceID()) :
                        null,
                    MarshalManager.marshal(focusGeo, gov.tak.api.engine.map.coords.GeoPoint.class, com.atakmap.coremap.maps.coords.GeoPoint.class),
                    focusX, focusY,
                    rotation,
                    tilt,
                    resolution, true));
    }

    /**
     * Copy constructor.
     *
     * @param other
     */
    public MapSceneModel(MapSceneModel other) {
        this(new com.atakmap.map.MapSceneModel(other._impl));
    }

    MapSceneModel(com.atakmap.map.MapSceneModel impl) {
        _impl = impl;
        init();
    }

    void init() {
        this.displayModel = MarshalManager.marshal(
                _impl.displayModel,
                com.atakmap.map.projection.MapProjectionDisplayModel.class,
                gov.tak.api.engine.map.MapProjectionDisplayModel.class);
        this.width = _impl.width;
        this.height = _impl.height;
        this.mapProjection = (_impl.mapProjection != null) ?
            gov.tak.platform.engine.map.coords.ProjectionFactory.getProjection(_impl.mapProjection.getSpatialReferenceID()) :
            null;
        this.forward = _impl.forward;
        this.inverse = _impl.inverse;
        this.gsd = _impl.gsd;
        this.dpi = _impl.dpi;
        this.focusx = _impl.focusx;
        this.focusy = _impl.focusy;

        if(this.camera == null)
            this.camera = new MapCamera();
        this.camera.aspectRatio = _impl.camera.aspectRatio;
        this.camera.azimuth = _impl.camera.azimuth;
        this.camera.elevation = _impl.camera.elevation;
        this.camera.far = _impl.camera.far;
        this.camera.farMeters = _impl.camera.farMeters;
        this.camera.fov = _impl.camera.fov;
        this.camera.location = new PointD(_impl.camera.location.x, _impl.camera.location.y, _impl.camera.location.z);
        this.camera.modelView = _impl.camera.modelView;
        this.camera.near = _impl.camera.near;
        this.camera.nearMeters = _impl.camera.nearMeters;
        this.camera.perspective = _impl.camera.perspective;
        this.camera.projection = _impl.camera.projection;
        this.camera.roll = _impl.camera.roll;
        this.camera.target = new PointD(_impl.camera.target.x, _impl.camera.target.y, _impl.camera.target.z);
    }

    public void set(double displayDPI, int width, int height,
                    IProjection proj, gov.tak.api.engine.map.coords.GeoPoint focusGeo, float focusX, float focusY,
                    double rotation, double tilt, double resolution,
                    boolean continuousScroll) {
        _impl.set(displayDPI,
                width, height,
                (proj != null) ?
                        com.atakmap.map.projection.ProjectionFactory.getProjection(proj.getSpatialReferenceID()) :
                        null,
                MarshalManager.marshal(focusGeo, gov.tak.api.engine.map.coords.GeoPoint.class, com.atakmap.coremap.maps.coords.GeoPoint.class),
                focusX, focusY,
                rotation,
                tilt,
                resolution,
                continuousScroll);
        init();
    }

    public void set(MapSceneModel other) {
        _impl.set(other._impl);
        init();
    }

    public boolean forward(gov.tak.api.engine.map.coords.GeoPoint geo, gov.tak.api.engine.math.PointD point) {
        com.atakmap.math.PointD result = new com.atakmap.math.PointD();
        _impl.forward(MarshalManager.marshal(geo, gov.tak.api.engine.map.coords.GeoPoint.class, com.atakmap.coremap.maps.coords.GeoPoint.class), result);
        point.x = result.x;
        point.y = result.y;
        point.z = result.z;
        return true;
    }

    public boolean inverse(PointF point, gov.tak.api.engine.map.coords.GeoPoint geo) {
        return inverse(point, geo, displayModel.earth);
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
    public boolean inverse(PointF point, gov.tak.api.engine.map.coords.GeoPoint geo, IGeometryModel model) {
        com.atakmap.coremap.maps.coords.GeoPoint result =
                _impl.inverse(new android.graphics.PointF(point.x, point.y),
                        null,
                        MarshalManager.marshal(model, gov.tak.api.engine.math.IGeometryModel.class, com.atakmap.math.GeometryModel.class));
        if(result == null)  return false;
        geo.set(result.getLatitude(),
                result.getLongitude(),
                result.getAltitude(),
                MarshalManager.marshal(result.getAltitudeReference(), com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference.class, gov.tak.api.engine.map.coords.GeoPoint.AltitudeReference.class),
                result.getCE(),
                result.getLE());
        return true;
    }

    @Override
    public void dispose() {
        _impl.dispose();
    }

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof MapSceneModel))
            return false;

        final MapSceneModel other = (MapSceneModel)o;
        if(this == other)
            return true;

        return _impl.equals(other._impl);
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
        return com.atakmap.map.MapSceneModel.intersects(model._impl,
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
        return com.atakmap.map.MapSceneModel.intersects(model._impl,
                longitude, latitude, hae, radiusMeters);
    }


}