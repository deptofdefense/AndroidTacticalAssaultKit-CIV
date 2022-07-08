#include "core/MapSceneModel2.h"

#include <algorithm>
#include <cmath>
#include <map>

#include "core/Datum2.h"
#include "core/GeoPoint.h"
#include "core/Projection2.h"
#include "core/ProjectionFactory3.h"
#include "elevation/ElevationManager.h"
#include "feature/Envelope2.h"
#include "feature/GeometryTransformer.h"
#include "math/AABB.h"
#include "math/Ellipsoid2.h"
#include "math/Frustum2.h"
#include "math/Plane2.h"
#include "math/Point2.h"
#include "math/Rectangle.h"
#include "raster/osm/OSMUtils.h"
#include "thread/Lock.h"
#include "thread/Mutex.h"
#include "util/Error.h"
#include "util/Logging.h"

using namespace TAK::Engine::Core;

using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

#define TAG "MapSceneModel2"
// half Vertical Field of View
#define HVFOV 22.5
#ifdef __ANDROID__
// 10cm
#define MIN_ZOOM_RANGE 10
#define MAX_ZOOM_RANGE std::numeric_limits<double>::max()
#else
#define MIN_ZOOM_RANGE 10
#define MAX_ZOOM_RANGE   16000000
#endif

namespace
{
    TAKErr computeCameraEllipsoidal(Point2<double> *camera,
        Point2<double> *up,
        const Projection2& proj,
        const MapProjectionDisplayModel &displayModel,
        GeoPoint2& focus,
        const double range,
        const double rotation,
        const double tilt,
        bool fromGeo) NOTHROWS;

    TAKErr computeCameraPlanar(Point2<double> *camera,
        Point2<double> *up,
        const Projection2& proj,
        const MapProjectionDisplayModel &displayModel,
        GeoPoint2& focus,
        const double range,
        const double rotation,
        const double tilt, 
        bool fromGeo) NOTHROWS;

    TAKErr constructFromModel(MapCamera2 *value, double &mapResolution, const size_t mapWidth, const size_t mapHeight,
        const Projection2 &mapProjection, const MapProjectionDisplayModel& displayModel, const GeoPoint2& focusGeo,
        const float focusScreenX, const float focusScreenY,
        const double mapRotation, const double mapTilt,
        const double nearMeters, const double farMeters, const MapCamera2::Mode mode, bool fromGeo) NOTHROWS;

    void setLookAtM(Matrix2& rm, double eyeX, double eyeY, double eyeZ, double centerX, double centerY, double centerZ, double upX, double upY, double upZ);
    void perspectiveM(Matrix2& m, double fovy, double aspect, double zNear, double zFar) NOTHROWS;
    double length(double x, double y, double z) NOTHROWS;

    Mutex &mutex() NOTHROWS;
    std::map<int, std::shared_ptr<MapProjectionDisplayModel>> &computedDisplayModelCache();

    TAKErr rotateAboutImpl(MapSceneModel2Ptr &value, const MapSceneModel2 &scene, const GeoPoint2 &point, const double theta, const double ax, const double ay, const double az) NOTHROWS;
    TAKErr createTargetTransform(Matrix2 *xformTarget, const GeoPoint2 &focusGeo, Projection2 &mapProjection, const double mapRotation, const double mapTilt) NOTHROWS;
    MapCamera2::Mode &defaultCameraMode() NOTHROWS;
}

MapSceneModel2::MapSceneModel2() NOTHROWS :
    earth(nullptr, nullptr),
    projection(nullptr, nullptr),
    displayDpi(NAN),
    width(0),
    height(0),
    focusX(NAN),
    focusY(NAN),
    gsd(NAN)
{}

MapSceneModel2::MapSceneModel2(double displayDPI, std::size_t width, std::size_t height, int srid, const GeoPoint2 &focusGeo, float focusX,
    float focusY, double rotation, double tilt, double resolution) NOTHROWS :
    MapSceneModel2(displayDPI, width, height, srid, focusGeo, focusX, focusY, rotation, tilt, resolution, NAN, NAN, defaultCameraMode())
{}
MapSceneModel2::MapSceneModel2(double displayDPI, std::size_t width, std::size_t height, int srid, const GeoPoint2 &focusGeo, float focusX,
    float focusY, double rotation, double tilt, double resolution, const double nearMeters, const double farMeters) NOTHROWS :
    MapSceneModel2(displayDPI, width, height, srid, focusGeo, focusX, focusY, rotation, tilt, resolution, nearMeters, farMeters, defaultCameraMode())
{}
MapSceneModel2::MapSceneModel2(double displayDPI, std::size_t width, std::size_t height, int srid, const GeoPoint2 &focusGeo, float focusX,
    float focusY, double rotation, double tilt, double resolution, const MapCamera2::Mode mode) NOTHROWS :
    MapSceneModel2(displayDPI, width, height, srid, focusGeo, focusX, focusY, rotation, tilt, resolution, NAN, NAN, mode)
{}
MapSceneModel2::MapSceneModel2(double displayDPI, std::size_t width, std::size_t height, int srid, const GeoPoint2 &focusGeo, float focusX,
    float focusY, double rotation, double tilt, double resolution, bool fromPoint) NOTHROWS :
    MapSceneModel2(displayDPI, width, height, srid, focusGeo, focusX, focusY, rotation, tilt, resolution, NAN, NAN, defaultCameraMode(), fromPoint)

{}

MapSceneModel2::MapSceneModel2(double displayDPI, std::size_t width, std::size_t height, int srid, const GeoPoint2 &focusGeo, float focusX,
    float focusY, double rotation, double tilt, double resolution, const double nearMeters,
    const double farMeters, const MapCamera2::Mode mode, bool fromPoint) NOTHROWS :
    projection(nullptr, nullptr),
    earth(nullptr, nullptr)
{
    init(displayDPI, width, height, srid, focusGeo, focusX, focusY, rotation, tilt, resolution, nearMeters, farMeters, mode, fromPoint);

}

MapSceneModel2::MapSceneModel2(double displayDPI, std::size_t width, std::size_t height, int srid, const GeoPoint2 &focusGeo, float focusX,
    float focusY, double rotation, double tilt, double resolution, const double nearMeters, const double farMeters, const MapCamera2::Mode mode) NOTHROWS :
    MapSceneModel2(displayDPI, width, height, srid, focusGeo, focusX, focusY, rotation, tilt, resolution, nearMeters, farMeters, mode, false)
{
}
MapSceneModel2::MapSceneModel2(const MapSceneModel2 &other) NOTHROWS :
    projection(nullptr, nullptr),
    earth(nullptr, nullptr)
{
    *this = other;
}

MapSceneModel2::~MapSceneModel2() NOTHROWS
{
}

TAKErr MapSceneModel2::forward(Point2<float>* point, const GeoPoint2& geo) const NOTHROWS
{
    TAKErr code(TE_Ok);
    Point2<double> proj;
    if (!projection.get())
        return TE_IllegalState;
    projection->forward(&proj, geo);
    proj = forwardTransform.transform(proj);
    point->x = (float)proj.x;
    point->y = (float)proj.y;
    point->z = (float)proj.z;
    return code;
}

TAKErr MapSceneModel2::forward(Point2<double> *point, const GeoPoint2& geo) const NOTHROWS
{
    TAKErr code(TE_Ok);
    Point2<double> proj;
    if (!projection)
        return TE_IllegalState;
    projection->forward(&proj, geo);
    *point = forwardTransform.transform(proj);
    return code;
}

TAKErr MapSceneModel2::inverse(GeoPoint2 *value, const Point2<float> &point) const NOTHROWS
{
    return this->inverse(value, point, false);
}

TAKErr MapSceneModel2::inverse(GeoPoint2 *value, const Point2<float> &point, bool nearestIfOffWorld) const NOTHROWS
{
    return this->inverse(value, point, *this->earth, nearestIfOffWorld);
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
TAKErr MapSceneModel2::inverse(GeoPoint2 *value, const Point2<float> &point, const GeometryModel2& model) const NOTHROWS
{
    return this->inverse(value, point, model, false);
}

TAKErr MapSceneModel2::inverse(GeoPoint2 *value, const Point2<float> &point,
    const GeometryModel2& model, const bool nearestIfOffWorld) const NOTHROWS
{
    TAKErr code(TE_Ok);

    Point2<double> org;
    if(camera.mode == MapCamera2::Perspective) {
        org = camera.location;
    } else {
        org = Point2<double>(point.x, point.y, -1.0);
        org = inverseTransform.transform(org);
    }

    Point2<double> tgt(point.x, point.y, 1);
    tgt = inverseTransform.transform(tgt);
    TE_CHECKRETURN_CODE(code);

    Vector4<double> dir(tgt.x - org.x, tgt.y - org.y, tgt.z - org.z);
    dir.normalize(&dir);

    Point2<double> pt;

    Ray2<double> onWorld(org, dir);
    bool isect = model.intersect(&pt, onWorld);
    if (!isect && nearestIfOffWorld)
    {
        Vector4<double> owv(-tgt.x, -tgt.y, -tgt.z);
        owv.normalize(&owv);
        Ray2<double> offWorld(tgt, owv);
        isect = model.intersect(&pt, offWorld);
    }
    if (isect)
    {
        projection->inverse(value, pt);
        return TE_Ok;
    }

    return TE_Err;
}

TAKErr MapSceneModel2::init(double display_dpi, std::size_t map_width, std::size_t map_height, int srid, const GeoPoint2 &focusGeo, float focus_x,
    float focus_y, double rotation, double tilt, double resolution, const double nearMeters, const double farMeters, const MapCamera2::Mode mode, bool fromGeo) NOTHROWS 
{
    TAKErr code(TE_Ok);

    // obtain the projection
    code = ProjectionFactory3_create(this->projection, srid);
    TE_CHECKRETURN_CODE(code);

    this->displayModel = getDisplayModel(*this->projection);

    this->displayDpi = display_dpi;
    this->width = map_width;
    this->height = map_height;
    this->focusX = focus_x;
    this->focusY = focus_y;
    this->gsd = resolution;

    // assign the publicly visible 'earth' pointer. we'll use a "leaker" here
    // because the actual memory is owned by 'displayModel'
    this->earth = GeometryModel2Ptr(this->displayModel->earth.get(), Memory_leaker_const<GeometryModel2>);

    constructFromModel(&this->camera, this->gsd, map_width, map_height, *this->projection, *this->displayModel, focusGeo, focus_x, focus_y,
                       rotation, tilt, nearMeters, farMeters, mode, fromGeo);

    // construct the forward transform based on the projection and
    // model-view matrices
    this->forwardTransform.setToIdentity();

    // REMEMBER: the origin of the target coordinate space is the 0,0,0 with the
    //           positive Y-axis pointing up with extents of [-1,1]

    // XXX - pixel centers for target coordinate space ?
    this->forwardTransform.scale((double)map_width / 2.0, -(double)map_height / 2.0);
    this->forwardTransform.translate(1.0, -1.0);
    this->forwardTransform.concatenate(this->camera.projection);
    this->forwardTransform.concatenate(this->camera.modelView);

    code = this->forwardTransform.createInverse(&this->inverseTransform);
    TE_CHECKRETURN_CODE(code);

    return code;
}


TAKErr MapSceneModel2::set(const double display_dpi, const std::size_t map_width, const std::size_t map_height, const int srid, const GeoPoint2 &focus_geo, const float focus_x, const float focus_y, const double rotation, const double tilt, const double resolution) NOTHROWS
{
    return set(display_dpi,
               map_width,
               map_height,
               srid,
               focus_geo,
               focus_x,
               focus_y,
               rotation,
               tilt,
               resolution,
               NAN,
               NAN,
               defaultCameraMode());
}
TAKErr MapSceneModel2::set(const double display_dpi, const std::size_t map_width, const std::size_t map_height, const int srid, const GeoPoint2 &focus_geo, const float focus_x, const float focus_y, const double rotation, const double tilt, const double resolution, const double nearMeters, const double farMeters) NOTHROWS
{
    return set(display_dpi,
               map_width,
               map_height,
               srid,
               focus_geo,
               focus_x,
               focus_y,
               rotation,
               tilt,
               resolution,
               nearMeters,
               farMeters,
               defaultCameraMode());
}
TAKErr MapSceneModel2::set(const double display_dpi, const std::size_t map_width, const std::size_t map_height, const int srid, const GeoPoint2 &focus_geo, const float focus_x, const float focus_y, const double rotation, const double tilt, const double resolution, const MapCamera2::Mode mode) NOTHROWS
{
    return set(display_dpi,
               map_width,
               map_height,
               srid,
               focus_geo,
               focus_x,
               focus_y,
               rotation,
               tilt,
               resolution,
               NAN,
               NAN,
               mode);
}
TAKErr MapSceneModel2::set(const double display_dpi, const std::size_t map_width, const std::size_t map_height, const int srid, const GeoPoint2 &focus_geo, const float focus_x, const float focus_y, const double rotation, const double tilt, const double resolution, const double nearMeters, const double farMeters, const MapCamera2::Mode mode) NOTHROWS
{
    return this->init(display_dpi,
                      map_width,
                      map_height,
                      srid,
                      focus_geo,
                      focus_x,
                      focus_y,
                      rotation,
                      tilt,
                      resolution,
                      nearMeters,
                      farMeters,
                      mode,
                      false);
}

MapSceneModel2 &MapSceneModel2::operator=(const MapSceneModel2 &other) NOTHROWS
{
    // clone the projection and display model
    if (other.projection.get()) {
        ProjectionFactory3_create(this->projection, other.projection->getSpatialReferenceID());
        this->displayModel = getDisplayModel(*this->projection);
    } else {
        this->projection.reset();
        this->displayModel.reset();
    }

    // copy the dimensions
    this->width = other.width;
    this->height = other.height;
    this->focusX = other.focusX;
    this->focusY = other.focusY;
    this->gsd = other.gsd;
    this->displayDpi = other.displayDpi;

    // assign the publicly visible 'earth' pointer. we'll use a "leaker" here
    // because the actual memory is owned by 'displayModel'
    if (this->displayModel)
        this->earth = GeometryModel2Ptr(this->displayModel->earth.get(), Memory_leaker_const<GeometryModel2>);
    else
        this->earth.reset();

    // copy the forward and inverse transforms
    this->forwardTransform = other.forwardTransform;
    this->inverseTransform = other.inverseTransform;

    this->camera = other.camera;

    return *this;
}

std::shared_ptr<MapProjectionDisplayModel> MapSceneModel2::getDisplayModel(Projection2& proj) NOTHROWS
{
    TAKErr code(TE_Ok);

    std::shared_ptr<MapProjectionDisplayModel> retval;
    code = MapProjectionDisplayModel_getModel(retval, proj.getSpatialReferenceID());
    if (code == TE_Ok)
        return retval;

    /*Logger_log(TELL_Warning,
    TAG ": Failed to find MapProjectionDisplayModel for SRID %d, using estimation.",
    proj.getSpatialReferenceID());*/

    Lock lock(mutex());

    std::map<int, std::shared_ptr<MapProjectionDisplayModel>> &displayModelCache = computedDisplayModelCache();

    std::map<int, std::shared_ptr<MapProjectionDisplayModel>>::iterator it;
    it = displayModelCache.find(proj.getSpatialReferenceID());
    if (it != displayModelCache.end())
    {
        return it->second;
    }
    // fall back on legacy model construction
    if (proj.is3D()) {
        // XXX - all of this is adapted from legacy code and assumes 3D
        //       projection is ECEF. This will be broken for any
        //       Projection reporting as 3D that is not ECEF
        Datum2 datum(Datum2::WGS84);

        // REMEMBER: the ECEF coordinate system has positive Z extending
        // through the North Pole
        GeometryModel2Ptr earth(
            new TAK::Engine::Math::Ellipsoid2(
            Point2<double>(0, 0, 0),
            datum.reference.semiMajorAxis,
            datum.reference.semiMajorAxis,
            datum.reference.semiMinorAxis),
            Memory_deleter_const<GeometryModel2, TAK::Engine::Math::Ellipsoid2>);

        retval.reset(
            new MapProjectionDisplayModel(
            proj.getSpatialReferenceID(),
            std::move(earth),
            1.0,
            1.0,
            1.0,
            false));
    } else {
        const double fullEquitorialExtentMeters = Datum2::WGS84.reference.semiMajorAxis*M_PI * 2.0;
        const double fullMeridianalExtentMeters = Datum2::WGS84.reference.semiMinorAxis*M_PI;
        Point2<double> pt;
        Point2<double> pt2;
        GeoPoint2 gp(0, proj.getMaxLongitude());
        proj.forward(&pt, gp);
        gp.latitude = 0;
        gp.longitude = proj.getMinLongitude();
        proj.forward(&pt2, gp);
        const double projectionEquitorialExtent = std::abs(pt.x - pt2.x);

        gp.latitude = proj.getMaxLatitude();
        gp.longitude = 0;
        proj.forward(&pt, gp);
        gp.latitude = proj.getMinLatitude();
        gp.longitude = 0;
        proj.forward(&pt, gp);
        const double projectionMeridianalExtent = std::abs(pt.y - pt2.y);

        GeometryModel2Ptr earth(
            new Plane2(Vector4<double>(0, 0, 1),
            Point2<double>(0.0, 0.0, 0.0)),
            Memory_deleter_const<GeometryModel2, Plane2>);

        retval.reset(
            new MapProjectionDisplayModel(
            proj.getSpatialReferenceID(),
            std::move(earth),
            fullEquitorialExtentMeters / projectionEquitorialExtent,
            fullMeridianalExtentMeters / projectionMeridianalExtent,
            1.0,
            true));
    }

    displayModelCache[proj.getSpatialReferenceID()] = retval;

    return retval;
}

TAKErr TAK::Engine::Core::MapSceneModel2_rotateAbout(MapSceneModel2Ptr &value, const MapSceneModel2 &scene, const GeoPoint2 &point, const double theta) NOTHROWS
{
    return rotateAboutImpl(value, scene, point, theta, 0.0, 0.0, 1.0);
}

TAKErr TAK::Engine::Core::MapSceneModel2_tiltAbout(MapSceneModel2Ptr &value, const MapSceneModel2 &scene, const GeoPoint2 &point, const double theta) NOTHROWS
{
    return rotateAboutImpl(value, scene, point, theta, 1.0, 0.0, 0.0);
}

TAKErr TAK::Engine::Core::MapSceneModel2_setCameraMode(const MapCamera2::Mode mode) NOTHROWS
{
    switch(mode) {
        case MapCamera2::Perspective :
        case MapCamera2::Scale :
            break;
        default :
            return TE_InvalidArg;
    }
    MapCamera2::Mode &dm = defaultCameraMode();
    dm = mode;
    return TE_Ok;
}
MapCamera2::Mode TAK::Engine::Core::MapSceneModel2_getCameraMode() NOTHROWS
{
    return defaultCameraMode();
}

TAKErr TAK::Engine::Core::MapSceneModel2_intersects(bool *value, const MapSceneModel2 &scene, const double mbbMinX_, const double mbbMinY_, const double mbbMinZ_, const double mbbMaxX_, const double mbbMaxY_, const double mbbMaxZ_) NOTHROWS
{
    TAK::Engine::Feature::Envelope2 aabb(mbbMinX_, mbbMinY_, mbbMinZ_, mbbMaxX_, mbbMaxY_, mbbMaxZ_);

    // transform the MBB to the native projection
    const int srid = scene.projection->getSpatialReferenceID();
    if(srid != 4326)
        TAK::Engine::Feature::GeometryTransformer_transform(&aabb, aabb, 4326, srid);

    Matrix2 mx(scene.camera.projection);
    mx.concatenate(scene.camera.modelView);
    Frustum2 frustum(mx);
    *value = frustum.intersects(
            AABB(
                    Point2<double>(aabb.minX, aabb.minY, aabb.minZ),
                    Point2<double>(aabb.maxX, aabb.maxY, aabb.maxZ)));

    // check IDL cross intersect
    if(!(*value) && (srid == 4326) && (scene.camera.location.x*((mbbMinX_+mbbMaxX_)/2.0)) < 0.0) {
        const double hemiShift = (scene.camera.location.x >= 0.0) ? 360.0 : -360.0;
        *value = frustum.intersects(
            AABB(
                    Point2<double>(aabb.minX+hemiShift, aabb.minY, aabb.minZ),
                    Point2<double>(aabb.maxX+hemiShift, aabb.maxY, aabb.maxZ)));
    }

    return TE_Ok;
}

TAKErr TAK::Engine::Core::MapSceneModel2_createOrtho(MapSceneModel2 *value, const std::size_t width, const std::size_t height, const GeoPoint2 &upperLeft, const GeoPoint2 &lowerRight) NOTHROWS
{
    TAKErr code(TE_Ok);
    Projection2Ptr proj(nullptr, nullptr);
    code = ProjectionFactory3_create(proj, 4326);
    TE_CHECKRETURN_CODE(code);
    if(!proj)
        return TE_Err;
    auto displayModel = MapSceneModel2::getDisplayModel(*proj);
    if(!displayModel)
        return TE_Err;
    value->projection = std::move(proj);
    value->displayModel = displayModel;
    value->width = width;
    value->height = height;
    value->gsd = displayModel->projectionYToNominalMeters*(upperLeft.latitude-lowerRight.latitude)/(double)height;
    value->focusX = (float)width / 2.f;
    value->focusY = (float)height / 2.f;
    value->camera.location.x = (upperLeft.longitude+lowerRight.longitude)/2.0;
    value->camera.location.y = (upperLeft.latitude+lowerRight.latitude)/2.0;
    value->camera.location.z = (value->gsd*((float)height / 2.0) / std::tan((HVFOV)*M_PI / 180.0));
    value->camera.target.x = (upperLeft.longitude+lowerRight.longitude)/2.0;
    value->camera.target.y = (upperLeft.latitude+lowerRight.latitude)/2.0;
    value->camera.target.z = 0.0;
    value->camera.azimuth = 0.0;
    value->camera.elevation = -90.0;
    value->camera.roll = 0.0;
    value->camera.aspectRatio = (float)width / (float)height;
    value->camera.near = 1.f;
    value->camera.far = -1.f;
    value->camera.mode = MapCamera2::Scale;

    // XXX -
    double xEyePosLength;
    if (value->displayModel->earth->getGeomClass() == GeometryModel2::PLANE) {
        TAK::Engine::Math::Point2<double> xEyePosMeters(value->camera.location);
        xEyePosMeters.x *= value->displayModel->projectionXToNominalMeters;
        xEyePosMeters.y *= value->displayModel->projectionYToNominalMeters;
        xEyePosMeters.z *= value->displayModel->projectionZToNominalMeters;

        TAK::Engine::Math::Point2<double> _posTargetMeters(value->camera.target);
        _posTargetMeters.x *= value->displayModel->projectionXToNominalMeters;
        _posTargetMeters.y *= value->displayModel->projectionYToNominalMeters;
        _posTargetMeters.z *= value->displayModel->projectionZToNominalMeters;

        TAK::Engine::Math::Point2<double> eye_tgt(xEyePosMeters);
        Vector2_subtract(&eye_tgt, xEyePosMeters, _posTargetMeters);
        Vector2_length(&xEyePosLength, eye_tgt);
        xEyePosLength += TAK::Engine::Core::Datum2::WGS84.reference.semiMajorAxis;
    } else { // ellipsoid
        TAK::Engine::Math::Point2<double> xEyePos(value->camera.location);
        Vector2_length(&xEyePosLength, xEyePos);
    }

    // recompute forward
    value->forwardTransform.setToIdentity();
    value->forwardTransform.scale(1.0, 1.0, -1.0 / xEyePosLength);
    value->forwardTransform.scale((double)value->width / (lowerRight.longitude-upperLeft.longitude), (double)value->height / (upperLeft.latitude-lowerRight.latitude), 1.0);
    value->forwardTransform.translate(-upperLeft.longitude, -lowerRight.latitude, 0.0);

    // recompute inverse
    value->inverseTransform.setToIdentity();
    value->inverseTransform.translate(upperLeft.longitude, lowerRight.latitude, 0.0);
    value->inverseTransform.scale((lowerRight.longitude-upperLeft.longitude) / (double)value->width, (upperLeft.latitude-lowerRight.latitude) / (double)value->height, 1.0);
    value->inverseTransform.scale(1.0, 1.0, -xEyePosLength);

    return TE_Ok;
}
double TAK::Engine::Core::MapSceneModel2_distanceToDisplayHorizon(const double heightMsl, const std::size_t sceneHeightPixels, const int srid) NOTHROWS
{
    if(srid != 4326)
        return GeoPoint2_distanceToHorizon(heightMsl);
#if 1
    const double gsd = (heightMsl*std::tan(HVFOV*M_PI/180.0)) / ((double)sceneHeightPixels / 2.0);
    const double lod = atakmap::raster::osm::OSMUtils::mapnikTileLeveld(gsd, 0.0);

    double adj;
    if(lod <= 9.0) {
        adj = 7.0;
    } else if(lod <= 14.0) {
        adj = (14.0-lod) + 2.0;
    } else if(lod <= 16.0) {
        adj = (16.0-lod) / 2.0 + 1.0;
    } else if(lod <= 19.0) {
        adj = ((19.0-lod) / 3.0)*0.5 + 0.5;
    } else {
        adj = 0.5;
    }

    return GeoPoint2_distanceToHorizon(heightMsl)*adj;
#elif 1
    return 7.0;
#else
    return GeoPoint2_distanceToHorizon(heightMsl);
#endif
}
double TAK::Engine::Core::MapSceneModel2_gsd(const double range, const double vfov, const std::size_t sceneHeightPixels) NOTHROWS
{
    return range*tan((vfov/2.0)*M_PI / 180.0)/((double)sceneHeightPixels/2.0);
}
double TAK::Engine::Core::MapSceneModel2_range(const double gsd, const double vfov, const std::size_t sceneHeightPixels) NOTHROWS
{
    return (gsd*((double)sceneHeightPixels / 2.0) / std::tan((vfov/2.0)*M_PI / 180.0));
}

namespace
{
    TAKErr computeCameraEllipsoidal(Point2<double> *camera,
        Point2<double> *up,
        const Projection2& proj,
        const MapProjectionDisplayModel &displayModel,
        GeoPoint2& focus,
        const double range,
        const double rotation,
        const double tilt,
        bool fromGeo) NOTHROWS
    {
        TAKErr code(TE_Ok);

        Projection2Ptr ecef(nullptr, nullptr);
        code = ProjectionFactory3_create(ecef, 4978);
        TE_CHECKRETURN_CODE(code);

        Point2<double> scratch;
        code = ecef->forward(&scratch, focus);
        TE_CHECKRETURN_CODE(code);

        const double _posTargetx = scratch.x;
        const double _posTargety = scratch.y;
        const double _posTargetz = scratch.z;

        Matrix2 xformTarget;
        xformTarget.setToIdentity();
        xformTarget.translate(_posTargetx, _posTargety, _posTargetz);
        code = xformTarget.rotate((focus.longitude + 90)* M_PI / 180.0, 0, 0, 1);
        TE_CHECKRETURN_CODE(code);
        code = xformTarget.rotate((90 - focus.latitude) * M_PI / 180.0, 1, 0, 0);
        TE_CHECKRETURN_CODE(code);
        code = xformTarget.rotate((-rotation)*M_PI / 180.0, 0, 0, 1);
        TE_CHECKRETURN_CODE(code);
        code = xformTarget.rotate((tilt)*M_PI / 180.0, 1.0, 0.0, 0.0);
        TE_CHECKRETURN_CODE(code);

        Point2<double> xformTargetZ;
        code = xformTarget.get(&xformTargetZ.x, 0, 2);
        TE_CHECKRETURN_CODE(code);
        code = xformTarget.get(&xformTargetZ.y, 1, 2);
        TE_CHECKRETURN_CODE(code);
        code = xformTarget.get(&xformTargetZ.z, 2, 2);
        TE_CHECKRETURN_CODE(code);

        if(!fromGeo)
        {
            xformTargetZ.x *= range;
            xformTargetZ.y *= range;
            xformTargetZ.z *= range;
        }

        Point2<double> xformTargetOffset;
        code = xformTarget.get(&xformTargetOffset.x, 0, 3);
        TE_CHECKRETURN_CODE(code);
        code = xformTarget.get(&xformTargetOffset.y, 1, 3);
        TE_CHECKRETURN_CODE(code);
        code = xformTarget.get(&xformTargetOffset.z, 2, 3);
        TE_CHECKRETURN_CODE(code);

        // compute the location of the camera in the projected coordinate space
        Point2<double> xEyePos(xformTargetZ.x + xformTargetOffset.x,
            xformTargetZ.y + xformTargetOffset.y,
            xformTargetZ.z + xformTargetOffset.z);

        if(fromGeo)
        {
            xformTargetZ.x *= -range;
            xformTargetZ.y *= -range;
            xformTargetZ.z *= -range;

            Point2<double> targetPos(xformTargetZ.x + xformTargetOffset.x, xformTargetZ.y + xformTargetOffset.y,
                xformTargetZ.z + xformTargetOffset.z);
            proj.inverse(&focus, targetPos);
        }

        Point2<double> upPos;
        // compute the up-vector of the camera in the projected coordinate space
        code = xformTarget.get(&upPos.x, 0, 1);
        TE_CHECKRETURN_CODE(code);
        code = xformTarget.get(&upPos.y, 1, 1);
        TE_CHECKRETURN_CODE(code);
        code = xformTarget.get(&upPos.z, 2, 1);
        TE_CHECKRETURN_CODE(code);

        // set the camera location
        camera->x = xEyePos.x;
        camera->y = xEyePos.y;
        camera->z = xEyePos.z;

        // set the up vector
        up->x = upPos.x;
        up->y = upPos.y;
        up->z = upPos.z;

        return code;
    }

    TAKErr computeCameraPlanar(Point2<double> *camera,
        Point2<double> *up,
        const Projection2& proj,
        const MapProjectionDisplayModel &displayModel,
        GeoPoint2& focus,
        const double range,
        const double rotation,
        const double tilt,
        bool fromGeo) NOTHROWS
    {
        TAKErr code(TE_Ok);

        Point2<double> scratch;

        proj.forward(&scratch, focus);
        const double _posTargetx = scratch.x;
        const double _posTargety = scratch.y;
        const double _posTargetz = scratch.z;

        Matrix2 xformTarget;
        xformTarget.setToIdentity();
        xformTarget.translate(_posTargetx*displayModel.projectionXToNominalMeters,
            _posTargety*displayModel.projectionYToNominalMeters,
            _posTargetz*displayModel.projectionZToNominalMeters);
        code = xformTarget.rotate((-rotation)*M_PI / 180.0, 0, 0, 1);
        TE_CHECKRETURN_CODE(code);
        code = xformTarget.rotate((tilt)*M_PI / 180.0, 1.0, 0.0, 0.0);
        TE_CHECKRETURN_CODE(code);
        xformTarget.scale(displayModel.projectionXToNominalMeters,
            displayModel.projectionYToNominalMeters,
            displayModel.projectionZToNominalMeters);

        Point2<double> xformTargetZ;
        code = xformTarget.get(&xformTargetZ.x, 0, 2);
        TE_CHECKRETURN_CODE(code);
        code = xformTarget.get(&xformTargetZ.y, 1, 2);
        TE_CHECKRETURN_CODE(code);
        code = xformTarget.get(&xformTargetZ.z, 2, 2);
        TE_CHECKRETURN_CODE(code);

        if(!fromGeo)
        {
            xformTargetZ.x *= range;
            xformTargetZ.y *= range;
            xformTargetZ.z *= range;
        }

        Point2<double> xformTargetOffset;
        code = xformTarget.get(&xformTargetOffset.x, 0, 3);
        TE_CHECKRETURN_CODE(code);
        code = xformTarget.get(&xformTargetOffset.y, 1, 3);
        TE_CHECKRETURN_CODE(code);
        code = xformTarget.get(&xformTargetOffset.z, 2, 3);
        TE_CHECKRETURN_CODE(code);

        // compute the location of the camera in the projected coordinate space,
        // scaled to meters
        Point2<double> xEyePos(xformTargetZ.x + xformTargetOffset.x,
            xformTargetZ.y + xformTargetOffset.y,
            xformTargetZ.z + xformTargetOffset.z);

        if(fromGeo)
        {
            xformTargetZ.x *= -range;
            xformTargetZ.y *= -range;
            xformTargetZ.z *= -range;

            Point2<double> targetPos(xformTargetZ.x + xformTargetOffset.x, xformTargetZ.y + xformTargetOffset.y,
                xformTargetZ.z + xformTargetOffset.z);

            targetPos.x = targetPos.x / displayModel.projectionXToNominalMeters;
            targetPos.y = targetPos.y / displayModel.projectionYToNominalMeters;
            targetPos.z = targetPos.z / displayModel.projectionZToNominalMeters;

            proj.inverse(&focus, targetPos);
        }

        Point2<double> upPos;
        // compute the up-vector of the camera in the projected coordinate space
        code = xformTarget.get(&upPos.x, 0, 1);
        TE_CHECKRETURN_CODE(code);
        code = xformTarget.get(&upPos.y, 1, 1);
        TE_CHECKRETURN_CODE(code);
        code = xformTarget.get(&upPos.z, 2, 1);
        TE_CHECKRETURN_CODE(code);

        // set the camera location in the projected cooridnate space
        camera->x = xEyePos.x / displayModel.projectionXToNominalMeters;
        camera->y = xEyePos.y / displayModel.projectionYToNominalMeters;
        camera->z = xEyePos.z / displayModel.projectionZToNominalMeters;

        // set the up vector
        up->x = upPos.x;
        up->y = upPos.y;
        up->z = upPos.z;

        return code;
    }

    TAKErr constructFromModel(MapCamera2 *retval, double &mapResolution, const size_t mapWidth, const size_t mapHeight,
        const Projection2 &mapProjection, const MapProjectionDisplayModel& displayModel, const GeoPoint2& focusGeo,
        const float focusScreenX, const float focusScreenY,
        const double mapRotation, const double mapTilt,
        const double nearMeters_, const double farMeters_, const MapCamera2::Mode cameraMode, bool fromGeo) NOTHROWS
    {
        TAKErr code(TE_Ok);

        // compute range based on
        // m_MetersPerPixel=TAN_FOV_ANGLE*_range/_view.Height;
        const auto _viewHeight = static_cast<double>(mapHeight);

        const double gsdRange = MapSceneModel2_range(mapResolution, HVFOV*2.0, mapHeight);
        double offsetRange = gsdRange;

        // XXX - experimental
        if(!TE_ISNAN(focusGeo.altitude)) {
            offsetRange -= focusGeo.altitude;
        }

        const double offsetRange0 = offsetRange;

        if (offsetRange < MIN_ZOOM_RANGE) offsetRange = MIN_ZOOM_RANGE;
        else if (offsetRange > MAX_ZOOM_RANGE) offsetRange = MAX_ZOOM_RANGE;

        mapResolution = (gsdRange+(offsetRange-offsetRange0))*std::tan((HVFOV)*M_PI / 180.0)/(_viewHeight/2.0);


        Point2<double> xEyePos(0.0, 0.0, 0.0);
        Point2<double> up(0.0, 0.0, 0.0);

        GeoPoint2 temp = focusGeo;
        if (displayModel.earth->getGeomClass() == GeometryModel2::PLANE)
        {
            code = computeCameraPlanar(&xEyePos, &up, mapProjection, displayModel, temp, offsetRange, mapRotation, mapTilt, fromGeo);
            TE_CHECKRETURN_CODE(code);
        }
        else if (displayModel.earth->getGeomClass() == GeometryModel2::ELLIPSOID || displayModel.earth->getGeomClass() == GeometryModel2::SPHERE)
        {
            code = computeCameraEllipsoidal(&xEyePos, &up, mapProjection, displayModel, temp, offsetRange, mapRotation, mapTilt, fromGeo);
            TE_CHECKRETURN_CODE(code);
        }
        else
        {
            return TE_Err;
        }

        //Calculate the camera's position based on the target point, range, azimuth, and elevation parameters
        Point2<double> _posTarget;

        mapProjection.forward(&_posTarget, temp);

        Point2<double> xEyePosMeters(
            xEyePos.x*displayModel.projectionXToNominalMeters,
            xEyePos.y*displayModel.projectionYToNominalMeters,
            xEyePos.z*displayModel.projectionZToNominalMeters);
        const Point2<double> _posTargetMeters(
            _posTarget.x*displayModel.projectionXToNominalMeters,
            _posTarget.y*displayModel.projectionYToNominalMeters,
            _posTarget.z*displayModel.projectionZToNominalMeters);

        const auto _viewWidth = static_cast<double>(mapWidth);
        const double aspect = _viewWidth / _viewHeight;

        Matrix2 xmodel;
        xmodel.setToIdentity();
        setLookAtM(xmodel,
            xEyePosMeters.x, xEyePosMeters.y, xEyePosMeters.z,
            _posTargetMeters.x, _posTargetMeters.y, _posTargetMeters.z,
            up.x, up.y, up.z);

        // scale projected coordinates to meters
        xmodel.scale(displayModel.projectionXToNominalMeters,
            displayModel.projectionYToNominalMeters,
            displayModel.projectionZToNominalMeters);

        // compute the far plane

        // obtain the camera position as LLA, then compute the distance to
        // horizon
        GeoPoint2 camLocation;
        mapProjection.inverse(&camLocation, xEyePos);

        // compute camera location/target in meters
        const double camLocMetersX = xEyePos.x*displayModel.projectionXToNominalMeters;
        const double camLocMetersY = xEyePos.y*displayModel.projectionYToNominalMeters;
        const double camLocMetersZ = xEyePos.z*displayModel.projectionZToNominalMeters;
        const double camTgtMetersX = _posTarget.x*displayModel.projectionXToNominalMeters;
        const double camTgtMetersY = _posTarget.y*displayModel.projectionYToNominalMeters;
        const double camTgtMetersZ = _posTarget.z*displayModel.projectionZToNominalMeters;

        // distance from camera to target
        const double dist = length(camLocMetersX-camTgtMetersX, camLocMetersY-camTgtMetersY, camLocMetersZ-camTgtMetersZ);

        // direction of camera pointing
        const double dirMeterX = (camTgtMetersX-camLocMetersX)/dist;
        const double dirMeterY = (camTgtMetersY-camLocMetersY)/dist;
        const double dirMeterZ = (camTgtMetersZ-camLocMetersZ)/dist;

        // use a minimum height of 2m (~person standing)
        double heightMsl = std::max(!TE_ISNAN(camLocation.altitude) ? camLocation.altitude : 0.0, 2.0);
        // convert HAE to MSL
        double mslOffset;
        if(ElevationManager_getGeoidHeight(&mslOffset, camLocation.latitude, camLocation.longitude) == TE_Ok)
            heightMsl -= mslOffset;
        heightMsl = std::max(2.0, heightMsl);
        const double horizonDistance = MapSceneModel2_distanceToDisplayHorizon(heightMsl, mapHeight, mapProjection.getSpatialReferenceID());

        // compute clipping planes
        double nearMeters = nearMeters_;
        double farMeters = farMeters_;

        // the far distance in meters will be the minimm of the distance  to
        // the horizon and the center of the earth -- if the distance to the
        // horizon is less than the eye altitude, simply use the eye altitude
        if(TE_ISNAN(farMeters)) {
#ifndef __ANDROID__
            farMeters = std::max(horizonDistance, TAK::Engine::Core::Ellipsoid2::WGS84.semiMajorAxis);
#else
            farMeters = std::max(horizonDistance, heightMsl);
            // XXX - does not appear to be working -- objects past visible
            //       circumference still briefly visible

            // if we're not using a planar projection, we can establish the far
            // plane through the center of the earth
            if (displayModel.earth->getGeomClass() != GeometryModel2::PLANE)
                farMeters = std::min(farMeters, std::max(Vector2_length(xEyePosMeters), 0.0000001));
#endif
        }
        if(TE_ISNAN(nearMeters)) {
            // specify a default that *should* equate to 32px
            nearMeters = mapResolution*32.0;

            // compute distance between focus and camera
            double xEyePosLength;
            Point2<double> eye_tgt(xEyePosMeters);
            Vector2_subtract(&eye_tgt, xEyePosMeters, _posTargetMeters);
            Vector2_length(&xEyePosLength, eye_tgt);

            if(xEyePosLength <= 0.0)
                xEyePosLength = 0.1;
            if(xEyePosLength < nearMeters)
                nearMeters = xEyePosLength*0.2;
        }

        // compute the projected location, scaled to meters at the computed far
        // distance
        Point2<double> farLocation;
        farLocation.x = camLocMetersX+(farMeters*dirMeterX);
        farLocation.y = camLocMetersY+(farMeters*dirMeterY);
        farLocation.z = camLocMetersZ+(farMeters*dirMeterZ);

        // unscale from meters to original projection units
        farLocation.x /= displayModel.projectionXToNominalMeters;
        farLocation.y /= displayModel.projectionYToNominalMeters;
        farLocation.z /= displayModel.projectionZToNominalMeters;

        // compute the projected location, scaled to meters at the computed far
        // distance
        Point2<double> nearLocation;
        nearLocation.x = camLocMetersX+(nearMeters*dirMeterX);
        nearLocation.y = camLocMetersY+(nearMeters*dirMeterY);
        nearLocation.z = camLocMetersZ+(nearMeters*dirMeterZ);

        // unscale from meters to original projection units
        nearLocation.x /= displayModel.projectionXToNominalMeters;
        nearLocation.y /= displayModel.projectionYToNominalMeters;
        nearLocation.z /= displayModel.projectionZToNominalMeters;

        //double aspect=_view.Width/(double)_view.Height;
        Matrix2 xproj;
        if(cameraMode == MapCamera2::Scale) {
            const double scale = std::tan((HVFOV)*M_PI / 180.0)*offsetRange;
            double xEyePosLength;
            if (displayModel.earth->getGeomClass() == GeometryModel2::PLANE) {
                Point2<double> eye_tgt(xEyePosMeters);
                Vector2_subtract(&eye_tgt, xEyePosMeters, _posTargetMeters);
                Vector2_length(&xEyePosLength, eye_tgt);
                xEyePosLength += Datum2::WGS84.reference.semiMajorAxis;
            } else { // ellipsoid
                Vector2_length(&xEyePosLength, xEyePos);
            }
            xproj.setToScale(1.0 / (aspect*scale),
                1.0 / (scale),
                -1.0 / (xEyePosLength));
        } else if(cameraMode == MapCamera2::Perspective) {
            perspectiveM(xproj, HVFOV*2.0, aspect, nearMeters, farMeters);
        } else {
            return TE_IllegalState;
        }

        // account for focus
        if (focusScreenX != mapWidth / 2.0 ||
            focusScreenY != mapHeight / 2.0) {

            const float mapCenterX = (float)mapWidth / 2.0f;
            const float mapCenterY = (float)mapHeight / 2.0f;
            Matrix2 focusShift;
            focusShift.translate((mapCenterX - focusScreenX) / (mapWidth / 2), (mapCenterY - focusScreenY) / (mapHeight / 2));
            xproj.preConcatenate(focusShift);
        }

        retval->roll = 0.0;
        retval->azimuth = mapRotation;
        retval->elevation = mapTilt-90.0;
        retval->location = xEyePos;
        retval->target = _posTarget;
        retval->modelView = xmodel;
        retval->projection = xproj;
        retval->aspectRatio = aspect;
        retval->fov = HVFOV*2.0;
        retval->mode = cameraMode;

        // obtain the far location in screen space to get the 'z'
        retval->modelView.transform(&farLocation, farLocation);
        retval->projection.transform(&farLocation, farLocation);

        // clamp the far plane to -1f
        //retval->far = std::max(-1.f, -(float)farLocation.z);
        retval->far = -(float)farLocation.z;
        // XXX -
        if(TE_ISNAN(retval->far))
            retval->far = -1.f;
        if (cameraMode == MapCamera2::Perspective) {
            // obtain the near location in screen space to get the 'z'
            retval->modelView.transform(&nearLocation, nearLocation);
            retval->projection.transform(&nearLocation, nearLocation);

            retval->near = std::max(0.0f, -(float)nearLocation.z);
        } else {
            retval->near = 0.075f;
        }

        retval->nearMeters = nearMeters;
        retval->farMeters = farMeters;

        return code;
    }

    void setLookAtM(TAK::Engine::Math::Matrix2& rm, double eyeX, double eyeY, double eyeZ,
        double centerX, double centerY, double centerZ, double upX, double upY, double upZ)
    {
        // See the OpenGL GLUT documentation for gluLookAt for a description
        // of the algorithm. We implement it in a straightforward way:

        double fx = centerX - eyeX;
        double fy = centerY - eyeY;
        double fz = centerZ - eyeZ;

        // Normalize f
        double rlf = 1.0f / length(fx, fy, fz);
        fx *= rlf;
        fy *= rlf;
        fz *= rlf;

        // compute s = f x up (x means "cross product")
        double sx = fy * upZ - fz * upY;
        double sy = fz * upX - fx * upZ;
        double sz = fx * upY - fy * upX;

        // and normalize s
        double rls = 1.0f / length(sx, sy, sz);
        sx *= rls;
        sy *= rls;
        sz *= rls;

        // compute u = s x f
        double ux = sy * fz - sz * fy;
        double uy = sz * fx - sx * fz;
        double uz = sx * fy - sy * fx;

        // col 1
        //rm[rmOffset + 0] = sx;
        rm.set(0, 0, sx);
        //rm[rmOffset + 1] = ux;
        rm.set(1, 0, ux);
        //rm[rmOffset + 2] = -fx;
        rm.set(2, 0, -fx);
        //rm[rmOffset + 3] = 0.0f;
        rm.set(3, 0, 0.0);

        // col 2
        //rm[rmOffset + 4] = sy;
        rm.set(0, 1, sy);
        //rm[rmOffset + 5] = uy;
        rm.set(1, 1, uy);
        //rm[rmOffset + 6] = -fy;
        rm.set(2, 1, -fy);
        //rm[rmOffset + 7] = 0.0f;
        rm.set(3, 1, 0.0);

        // col 3
        //rm[rmOffset + 8] = sz;
        rm.set(0, 2, sz);
        //rm[rmOffset + 9] = uz;
        rm.set(1, 2, uz);
        //rm[rmOffset + 10] = -fz;
        rm.set(2, 2, -fz);
        //rm[rmOffset + 11] = 0.0f;
        rm.set(3, 2, 0.0);

        // col 4
        //rm[rmOffset + 12] = 0.0f;
        rm.set(0, 3, 0.0);
        //rm[rmOffset + 13] = 0.0f;
        rm.set(1, 3, 0.0);
        //rm[rmOffset + 14] = 0.0f;
        rm.set(2, 3, 0.0);
        //rm[rmOffset + 15] = 1.0f;
        rm.set(3, 3, 1.0);

        //translateM(rm, rmOffset, -eyeX, -eyeY, -eyeZ);
        rm.translate(-eyeX, -eyeY, -eyeZ);
    }

    void perspectiveM(TAK::Engine::Math::Matrix2& m, double fovy, double aspect, double zNear,
        double zFar) NOTHROWS
    {
        double f = 1.0 / std::tan(fovy * (M_PI / 360.0));
        double rangeReciprocal = 1.0 / (zNear - zFar);

        // column 1
        //m[offset + 0] = f / aspect;
        m.set(0, 0, f / aspect);
        //m[offset + 1] = 0.0f;
        m.set(1, 0, 0.0);
        //m[offset + 2] = 0.0f;
        m.set(2, 0, 0.0);
        //m[offset + 3] = 0.0f;
        m.set(3, 0, 0.0);

        // column 2
        //m[offset + 4] = 0.0f;
        m.set(0, 1, 0.0);
        //m[offset + 5] = f;
        m.set(1, 1, f);
        //m[offset + 6] = 0.0f;
        m.set(2, 1, 0.0);
        //m[offset + 7] = 0.0f;
        m.set(3, 1, 0.0);

        // column 3
        //m[offset + 8] = 0.0f;
        m.set(0, 2, 0.0);
        //m[offset + 9] = 0.0f;
        m.set(1, 2, 0.0);
        //m[offset + 10] = (zFar + zNear) * rangeReciprocal;
        m.set(2, 2, (zFar + zNear) * rangeReciprocal);
        //m[offset + 11] = -1.0f;
        m.set(3, 2, -1.0);

        // column 4
        //m[offset + 12] = 0.0f;
        m.set(0, 3, 0.0);
        //m[offset + 13] = 0.0f;
        m.set(1, 3, 0.0);
        //m[offset + 14] = 2.0f * zFar * zNear * rangeReciprocal;
        m.set(2, 3, 2.0f * zFar * zNear * rangeReciprocal);
        //m[offset + 15] = 0.0f;
        m.set(3, 3, 0.0);
    }

    double length(double x, double y, double z) NOTHROWS
    {
        return std::sqrt((x*x) + (y*y) + (z*z));
    }

    Mutex &mutex() NOTHROWS
    {
        static Mutex m;
        return m;
    }

    std::map<int, std::shared_ptr<MapProjectionDisplayModel>> &computedDisplayModelCache()
    {
        static std::map<int, std::shared_ptr<MapProjectionDisplayModel>> c;
        return c;
    }

    TAKErr rotateAboutImpl(MapSceneModel2Ptr &value, const MapSceneModel2 &scene, const GeoPoint2 &point, const double theta, const double ax, const double ay, const double az) NOTHROWS
    {
        TAKErr code(TE_Ok);
        const double TAN_FOV_ANGLE = std::tan(HVFOV / 180.0*M_PI);

        const auto mapWidth = static_cast<float>(scene.width);
        const auto mapHeight = static_cast<float>(scene.height);
        //const double mapResolution = TAN_FOV_ANGLE*MathUtils.distance(scene.camera.location.x, scene.camera.location.y, scene.camera.location.z, scene.camera.target.x, scene.camera.target.y, scene.camera.target.z)*1.25d / mapHeight;
        const double mapResolution = TAN_FOV_ANGLE*length(scene.camera.location.x-scene.camera.target.x,
                                                          scene.camera.location.y-scene.camera.target.y,
                                                          scene.camera.location.z-scene.camera.target.z)*2.0 / mapHeight;
        const double mapTilt = scene.camera.elevation + 90.0;
        GeoPoint2 focusGeo;
        scene.projection->inverse(&focusGeo, scene.camera.target);
        const double mapRotation = scene.camera.azimuth;
        const float focusScreenX = scene.focusX;
        const float focusScreenY = scene.focusY;
        Projection2& mapProjection = *scene.projection;

        // compute range based on
        // m_MetersPerPixel=TAN_FOV_ANGLE*_range/_view.Height;
        const double m_MetersPerPixel = mapResolution;
        const double _viewHeight = mapHeight;

        double _range = (m_MetersPerPixel*_viewHeight / 2.0 / TAN_FOV_ANGLE);

        //Calculate the camera's position based on the target point, range, azimuth, and elevation parameters
        Point2<double> _posTarget;
        mapProjection.forward(&_posTarget, focusGeo);

        const double _viewWidth = mapWidth;
        const double aspect = _viewWidth / (double)_viewHeight;
        const double scale = TAN_FOV_ANGLE*_range;

        Matrix2 xformTarget;
        createTargetTransform(&xformTarget,
            focusGeo,
            mapProjection,
            mapRotation/*+theta*/,
            mapTilt);

        // XXX - rotate about focus point on axis
        Point2<double> anchor;
        mapProjection.forward(&anchor, point);
        //xformTarget.rotate(theta, anchor.x, anchor.y, anchor.z, ax, ay, az);
        //xformTarget.rotate(Math.toRadians(-theta), 0d, 0d, 0d, ax, ay, az);

        // undo tilt
        code = xformTarget.rotate((-mapTilt / 180.0 * M_PI), 1.0, 0.0, 0.0);
        TE_CHECKRETURN_CODE(code);
        code = xformTarget.rotate((-theta / 180.0 * M_PI),
            -anchor.x + scene.camera.target.x,
            -anchor.y + scene.camera.target.y,
            -anchor.z + scene.camera.target.z,
            ax, ay, az);
        TE_CHECKRETURN_CODE(code);

        // redo tilt
        code = xformTarget.rotate((mapTilt / 180.0 * M_PI), 1.0, 0.0, 0.0);
        TE_CHECKRETURN_CODE(code);

        Point2<double> xformTargetZ;
        code = xformTarget.get(&xformTargetZ.x, 0, 2);
        TE_CHECKRETURN_CODE(code);
        code = xformTarget.get(&xformTargetZ.y, 1, 2);
        TE_CHECKRETURN_CODE(code);
        code = xformTarget.get(&xformTargetZ.z, 2, 2);
        TE_CHECKRETURN_CODE(code);
        code = Vector2_multiply(&xformTargetZ, xformTargetZ, _range);
        TE_CHECKRETURN_CODE(code);

        //Point2<double> xformTargetOffset(xformTarget.get(0, 3),
        //    xformTarget.get(1, 3),
        //    xformTarget.get(2, 3));
        Point2<double> xformTargetOffset;
        code = xformTarget.get(&xformTargetOffset.x, 0, 3);
        TE_CHECKRETURN_CODE(code);
        code = xformTarget.get(&xformTargetOffset.y, 1, 3);
        TE_CHECKRETURN_CODE(code);
        code = xformTarget.get(&xformTargetOffset.z, 2, 3);
        TE_CHECKRETURN_CODE(code);

        Point2<double> xEyePos;
        code = Vector2_add<double>(&xEyePos, xformTargetZ, xformTargetOffset);
        TE_CHECKRETURN_CODE(code);

        double xEyePosLength;
        Vector2_length<double>(&xEyePosLength, xEyePos);

        //double aspect=_view.Width/(double)_view.Height;
        Matrix2 xproj;
        xproj.scale(2 / (aspect*scale),
                    2 / (scale),
                    -2 / (xEyePosLength));

        Matrix2 xmodel;
        setLookAtM(xmodel,
            xEyePos.x, xEyePos.y, xEyePos.z,
            _posTarget.x, _posTarget.y, _posTarget.z,
            //xformTarget.get(0, 1), xformTarget.get(1, 1), xformTarget.get(2, 1));
            xformTargetZ.x / _range, xformTargetZ.y / _range, xformTargetZ.z / _range);



        // account for focus
        if (focusScreenX != (mapWidth / 2.0) ||
            focusScreenY != (mapHeight / 2.0)) {

            const double mapCenterX = (float)mapWidth / 2.0;
            const double mapCenterY = (float)mapHeight / 2.0;

            Matrix2 tx;
            tx.translate((mapCenterX - focusScreenX) / (mapWidth / 2), (mapCenterY - focusScreenY) / (mapHeight / 2));
            xproj.preConcatenate(tx);
        }

        // create the camera based on the scene model transforms
        MapCamera2 retval;
        retval.roll = 0.0;
        retval.location = xEyePos;
        retval.target = _posTarget;
        retval.modelView = xmodel;
        retval.projection = xproj;
        retval.azimuth = mapRotation;
        retval.elevation = mapTilt - 90.0;

        // model-view-projection matrix
        Matrix2 mvp;
        mvp.concatenate(retval.projection);
        mvp.concatenate(retval.modelView);

        Matrix2 imvp;
        code = mvp.createInverse(&imvp);
        TE_CHECKRETURN_CODE(code);

        // compute the actual look-at center, following the point relative
        // rotation
        const double mapCenterX = (double)mapWidth / 2.0;
        const double mapCenterY = (double)mapHeight / 2.0;
        Point2<double> org((mapCenterX - focusScreenX) / (mapWidth / 2.0), (mapCenterY - focusScreenY) / (mapHeight / 2.0), 0.0);
        Point2<double> tgt(org.x, org.y, 1);

        imvp.transform(&org, org);
        imvp.transform(&tgt, tgt);

        Point2<double> dir(tgt.x - org.x, tgt.y - org.y, tgt.z - org.z);
        code = Vector2_normalize(&dir, dir);
        TE_CHECKRETURN_CODE(code);

        // XXX - needs to be derived from the map projection
        TAK::Engine::Core::Datum2 datum(TAK::Engine::Core::Datum2::WGS84);

        // REMEMBER: the ECEF coordinate system has positive Z extending
        // through the North Pole
        TAK::Engine::Math::Ellipsoid2 earth(Point2<double>(0, 0, 0),
            datum.reference.semiMajorAxis,
            datum.reference.semiMajorAxis,
            datum.reference.semiMinorAxis);

        //const PointD compCtr = earth.intersect(new Ray(org, dir));
        Point2<double> compCtr;
        const bool computedCenter = earth.intersect(&compCtr, Ray2<double>(org, Vector4<double>(dir.x, dir.y, dir.z)));

        Logger_log(TELL_Info, "MapSceneModel2::rotateImpl: ROTATE ABOUT point={%lf,%lf,%lf} theta=%lf axis=%lf,%lf,%lf", point.latitude, point.longitude, point.altitude, theta, ax, ay, az);

        if (!computedCenter)
            Logger_log(TELL_Info, "MapSceneModel2::rotateImpl: estimated center diff=N/A");
        else
            Logger_log(TELL_Info, "MapSceneModel2::rotateImpl: estimated center diff=%lf", length(compCtr.x-retval.target.x, compCtr.y-retval.target.y, compCtr.z-retval.target.z));

        // reset the camera target
        retval.target = compCtr;

        // compute the camera pointing (azimuth and elevation) to the actual
        // target position

        // create an nadir, north-up target transform
        Matrix2 nadirNorthTarget;
        createTargetTransform(&nadirNorthTarget,
            focusGeo,
            mapProjection,
            0.0,
            0.0);

        //Point2<double> nadirNorthTargetZ(nadirNorthTarget.get(0, 2)*_range,
        //    nadirNorthTarget.get(1, 2)*_range,
        //   nadirNorthTarget.get(2, 2)*_range);
        Point2<double> nadirNorthTargetZ;
        code = nadirNorthTarget.get(&nadirNorthTargetZ.x, 0, 2);
        TE_CHECKRETURN_CODE(code);
        code = nadirNorthTarget.get(&nadirNorthTargetZ.y, 1, 2);
        TE_CHECKRETURN_CODE(code);
        code = nadirNorthTarget.get(&nadirNorthTargetZ.z, 2, 2);
        TE_CHECKRETURN_CODE(code);
        code = Vector2_multiply(&nadirNorthTargetZ, nadirNorthTargetZ, _range);
        TE_CHECKRETURN_CODE(code);

        //Point2<double> nadirNorthTargetOffset(nadirNorthTarget.get(0, 3),
        //    nadirNorthTarget.get(1, 3),
        //    nadirNorthTarget.get(2, 3));
        Point2<double> nadirNorthTargetOffset;
        code = nadirNorthTarget.get(&nadirNorthTargetOffset.x, 0, 3);
        TE_CHECKRETURN_CODE(code);
        code = nadirNorthTarget.get(&nadirNorthTargetOffset.y, 1, 3);
        TE_CHECKRETURN_CODE(code);
        code = nadirNorthTarget.get(&nadirNorthTargetOffset.z, 2, 3);
        TE_CHECKRETURN_CODE(code);

        Point2<double> nadirNorthEyePos(nadirNorthTargetZ.x + nadirNorthTargetOffset.x,
            nadirNorthTargetZ.y + nadirNorthTargetOffset.y,
            nadirNorthTargetZ.z + nadirNorthTargetOffset.z);



        // pointing from target to camera
        //Vector3D nadirNorthCameraDir = (new Vector3D(nadirNorthEyePos.x - _posTarget.x, nadirNorthEyePos.y - _posTarget.y, nadirNorthEyePos.z - _posTarget.z)).normalize();
        Point2<double> nadirNorthCameraDir;
        code = Vector2_subtract<double>(&nadirNorthCameraDir, nadirNorthEyePos, _posTarget);
        TE_CHECKRETURN_CODE(code);
        code = Vector2_normalize(&nadirNorthCameraDir, nadirNorthCameraDir);
        TE_CHECKRETURN_CODE(code);

        //Vector3D cameraDir = (new Vector3D(xEyePos.x - _posTarget.x, xEyePos.y - _posTarget.y, xEyePos.z - _posTarget.z)).normalize();
        Point2<double> cameraDir;
        code = Vector2_subtract<double>(&cameraDir, xEyePos, _posTarget);
        TE_CHECKRETURN_CODE(code);
        code = Vector2_normalize(&cameraDir, cameraDir);
        TE_CHECKRETURN_CODE(code);

        double cameraDirDotNadirNorthCameraDir;
        code = Vector2_dot<double>(&cameraDirDotNadirNorthCameraDir, cameraDir, nadirNorthCameraDir);
        TE_CHECKRETURN_CODE(code);

        const double estEl = (std::acos(cameraDirDotNadirNorthCameraDir))/M_PI*180.0;

        Logger_log(TELL_Info, "MapSceneModel2::rotateImpl: el=%lf est=%lf diff=%lf", mapTilt, estEl, (mapTilt - estEl));

        retval.elevation = estEl - 90.0;

        // define the cone based on the

        //const double slantRange = MathUtils.distance(xEyePos.x, xEyePos.y, xEyePos.z, _posTarget.x, _posTarget.y, _posTarget.z);
        const double slantRange = length(xEyePos.x-_posTarget.x,
                                         xEyePos.y-_posTarget.y,
                                         xEyePos.z-_posTarget.z);
        const double coneCenterRange = std::cos(estEl/180.0*M_PI)*slantRange;

        //System.out.println("slant=" + slantRange + " coneCenter=" + coneCenterRange);

        //Vector3D coneCenterPos = nadirNorthCameraDir.multiply(coneCenterRange).add(new Vector3D(_posTarget.x, _posTarget.y, _posTarget.z));
        Point2<double> coneCenterPos;
        code = Vector2_multiply<double>(&coneCenterPos, nadirNorthCameraDir, coneCenterRange);
        TE_CHECKRETURN_CODE(code);
        code = Vector2_add<double>(&coneCenterPos, coneCenterPos, _posTarget);
        TE_CHECKRETURN_CODE(code);

        //Vector3D nadirNorthUp = new Vector3D(nadirNorthTarget.get(0, 1), nadirNorthTarget.get(1, 1), nadirNorthTarget.get(2, 1));
        Point2<double> nadirNorthUp;
        code = nadirNorthTarget.get(&nadirNorthUp.x, 0, 1);
        TE_CHECKRETURN_CODE(code);
        code = nadirNorthTarget.get(&nadirNorthUp.y, 1, 1);
        TE_CHECKRETURN_CODE(code);
        code = nadirNorthTarget.get(&nadirNorthUp.z, 2, 1);
        TE_CHECKRETURN_CODE(code);

        //Vector3D nadirNorthUpDir = nadirNorthUp.normalize();
        Point2<double> nadirNorthUpDir;
        code = Vector2_normalize<double>(&nadirNorthUpDir, nadirNorthUp);
        TE_CHECKRETURN_CODE(code);

        // camera up on cone plane center->camera
        //const double estAz = Math.toDegrees(Math.acos(nadirNorthUpDir.dot((new Vector3D(xEyePos.x, xEyePos.y, xEyePos.z)).subtract(coneCenterPos).normalize())));
        // camera up on cone plane camera->center
        //Vector3D cameraPosRadiiDir = coneCenterPos.subtract((new Vector3D(xEyePos.x, xEyePos.y, xEyePos.z))).normalize();
        Point2<double> cameraPosRadiiDir;
        code = Vector2_subtract<double>(&cameraPosRadiiDir, coneCenterPos, xEyePos);
        TE_CHECKRETURN_CODE(code);
        code = Vector2_normalize<double>(&cameraPosRadiiDir, cameraPosRadiiDir);
        TE_CHECKRETURN_CODE(code);

        double nadirNorthUpDirDotCameraPosRadiiDir;
        code = Vector2_dot<double>(&nadirNorthUpDirDotCameraPosRadiiDir, nadirNorthUpDir, cameraPosRadiiDir);
        TE_CHECKRETURN_CODE(code);

        double estAz = (std::acos(nadirNorthUpDirDotCameraPosRadiiDir))/M_PI*180.0;

        //Vector3D upnnXctrtgt = cross(nadirNorthUp, new Vector3D(_posTarget.x - coneCenterPos.X, _posTarget.y - coneCenterPos.Y, _posTarget.z - coneCenterPos.Z));
        Point2<double> upnnXctrtgt;
        code = Vector2_subtract<double>(&upnnXctrtgt, _posTarget, coneCenterPos);
        TE_CHECKRETURN_CODE(code);
        code = Vector2_cross<double>(&upnnXctrtgt, nadirNorthUp, upnnXctrtgt);
        TE_CHECKRETURN_CODE(code);

        //const double deltaEast = Math.toDegrees(upnnXctrtgt.normalize().dot(cameraPosRadiiDir));
        double deltaEast;
        Point2<double> upnnXctrtgtNormalized;
        code = Vector2_normalize<double>(&upnnXctrtgtNormalized, upnnXctrtgt);
        TE_CHECKRETURN_CODE(code);
        code = Vector2_dot(&deltaEast, upnnXctrtgtNormalized, cameraPosRadiiDir);
        TE_CHECKRETURN_CODE(code);
        deltaEast *= 180.0 / M_PI;

        if (deltaEast > 0.0)
            estAz *= -1;
        if (estAz < 0.0)
            estAz += 360.0;

        Logger_log(TELL_Info, "MapSceneModel2::rotateImpl: az=%lf est=%lf diff=%lf", mapRotation, estAz, (mapRotation - estAz));
        retval.azimuth = estAz;

        const double estGsd = slantRange / (_viewHeight / 2.0 / TAN_FOV_ANGLE);

        //new MapSceneModel2(retval,
        //    scene.width, scene.height,
        //    scene.projection,
        //    scene.focusX, scene.focusY, estGsd)
        value = MapSceneModel2Ptr(new MapSceneModel2(),
                                  Memory_deleter_const<MapSceneModel2>);

        // copy projection, width, height, focus, display model
        *value = scene;

        // update the camera and the transforms
        value->camera = retval;

        // REMEMBER: the origin of the target coordinate space is the 0,0,0 with
        //           the positive Y-axis pointing up

        // XXX - pixel centers for target coordinate space ?
        value->forwardTransform.setToIdentity();
        value->forwardTransform.scale((double)value->width / 2.0, -(double)value->height / 2.0);
        value->forwardTransform.translate(1.0, -1.0);
        value->forwardTransform.concatenate(value->camera.projection);
        value->forwardTransform.concatenate(value->camera.modelView);

        code = value->forwardTransform.createInverse(&value->inverseTransform);
        TE_CHECKRETURN_CODE(code);

        return code;
    }

    TAKErr createTargetTransform(Matrix2 *xformTarget, const GeoPoint2 &focusGeo, Projection2 &mapProjection, const double mapRotation, const double mapTilt) NOTHROWS
    {
        TAKErr code(TE_Ok);
        //Calculate the camera's position based on the target point, range, azimuth, and elevation parameters
        Point2<double> _posTarget;
        mapProjection.forward(&_posTarget, focusGeo);

        xformTarget->setToIdentity();
        xformTarget->translate(_posTarget.x, _posTarget.y, _posTarget.z);
        xformTarget->rotate((90 + focusGeo.longitude) / 180.0 * M_PI, 0, 0, 1);
        xformTarget->rotate((90 - focusGeo.latitude) / 180.0 * M_PI, 1, 0, 0);
        xformTarget->rotate((-mapRotation) / 180.0 * M_PI, 0, 0, 1);
        code = xformTarget->rotate(mapTilt / 180.0 * M_PI, 1.0, 0.0, 0.0);
        TE_CHECKRETURN_CODE(code);

        return code;
    }

    MapCamera2::Mode &defaultCameraMode() NOTHROWS
    {
#ifndef __ANDROID__
        static MapCamera2::Mode m = MapCamera2::Scale;
#else
        static MapCamera2::Mode m = MapCamera2::Perspective;
#endif
        return m;
    }
}
