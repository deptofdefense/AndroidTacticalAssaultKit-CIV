#include "core/MapSceneModel.h"

#include "core/AtakMapController.h"
#include "core/Datum.h"
#include "core/Projection.h"
#include "core/ProjectionFactory.h"
#include "core/MapSceneModel2.h"

#include "math/Ellipsoid.h"
#include "math/Plane.h"
#include "math/Sphere.h"
#include "math/Point.h"
#include "math/Ellipsoid2.h"
#include "math/Plane2.h"
#include "math/Point2.h"
#include "math/Sphere2.h"

#include "util/Memory.h"
#include "util/Logging.h"
#include "util/Error.h"

#include <cmath>

using namespace atakmap::core;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

using namespace atakmap::math;

//This code now mainly calls MapSceneModel2's implementation of these functions, with some setting of members of the MapSceneModel class as needed.
namespace
{
#if 0
    //bool initScene(MapSceneModel *model, const AtakMapView *view, int srid, const GeoPoint *focusGeo, float focusX, float focusY, double rotation, double scale);

    void constructCamera3D(MapCamera *camera, int mapWidth, int mapHeight, Projection &mapProjection, const GeoPoint *focusGeo, float focusScreenX, float focusScreenY, double mapRotation, double mapResolution);
    void constructCameraOrtho(MapCamera *camera, int mapWidth, int mapHeight, Projection &proj, double fullEquitorialExtentPixels, const GeoPoint *focusGeo, float focusX, float focusY, double rotation, double mapScale);

#endif
    GeoPoint2 adapt(const GeoPoint &legacy) NOTHROWS
    {
        GeoPoint2 retval;
        GeoPoint_adapt(&retval, legacy);
        return retval;
    }

    float getFocusX(const AtakMapView &view) NOTHROWS
    {
        atakmap::math::Point<float> focus;
        view.getController()->getFocusPoint(&focus);
        return focus.x;
    }

    float getFocusY(const AtakMapView &view) NOTHROWS
    {
        atakmap::math::Point<float> focus;
        view.getController()->getFocusPoint(&focus);
        return focus.y;
    }

    GeoPoint2 getPoint(const AtakMapView &view) NOTHROWS
    {
        atakmap::core::GeoPoint legacy;
        view.getPoint(&legacy);
        return adapt(legacy);
    }
}

MapSceneModel::MapSceneModel() :
    earth(nullptr),
    projection(nullptr, nullptr),
    forwardTransform(std::move(Matrix2Ptr(&impl.forwardTransform, Memory_leaker_const<Matrix2>))),
    inverseTransform(std::move(Matrix2Ptr(&impl.inverseTransform, Memory_leaker_const<Matrix2>)))
{
    this->syncWithImpl();
}

MapSceneModel::MapSceneModel(AtakMapView *view) :
    impl(view->getDisplayDpi(),
         static_cast<int>(view->getWidth()),
         static_cast<int>(view->getHeight()),
         view->getProjection(),
         getPoint(*view),
         getFocusX(*view), getFocusY(*view),
         view->getMapRotation(),
         view->getMapTilt(),
         view->getMapResolution()),
    earth(nullptr),
    projection(nullptr, nullptr),
    forwardTransform(std::move(Matrix2Ptr(&impl.forwardTransform, Memory_leaker_const<Matrix2>))),
    inverseTransform(std::move(Matrix2Ptr(&impl.inverseTransform, Memory_leaker_const<Matrix2>)))
{
    this->syncWithImpl();
}

MapSceneModel::MapSceneModel(const AtakMapView *view, int srid, const GeoPoint *focusGeo, float focusX, float focusY, double rotation, double scale) :
    impl(view->getDisplayDpi(),
         static_cast<int>(view->getWidth()),
         static_cast<int>(view->getHeight()),
         srid,
         adapt(*focusGeo),
         focusX, focusY,
         rotation,
         view->getMapTilt(),
         view->getMapResolution(scale)),
    earth(nullptr),
    projection(nullptr, nullptr),
    forwardTransform(std::move(Matrix2Ptr(&impl.forwardTransform, Memory_leaker_const<Matrix2>))),
    inverseTransform(std::move(Matrix2Ptr(&impl.inverseTransform, Memory_leaker_const<Matrix2>)))
{
    this->syncWithImpl();
}

MapSceneModel::MapSceneModel(const AtakMapView *view, int srid, const GeoPoint *focusGeo, float focusX, float focusY, double rotation, double tilt, double scale) :
    impl(view->getDisplayDpi(),
             static_cast<int>(view->getWidth()),
             static_cast<int>(view->getHeight()),
             srid,
             adapt(*focusGeo),
             focusX, focusY,
             rotation,
             tilt,
             view->getMapResolution(scale)),
    earth(nullptr),
    projection(nullptr, nullptr),
    forwardTransform(std::move(Matrix2Ptr(&impl.forwardTransform, Memory_leaker_const<Matrix2>))),
    inverseTransform(std::move(Matrix2Ptr(&impl.inverseTransform, Memory_leaker_const<Matrix2>)))
{
    this->syncWithImpl();
}

MapSceneModel::MapSceneModel(const MapSceneModel &other) :
    impl(other.impl),
    earth(nullptr),
    projection(nullptr, nullptr),
    forwardTransform(std::move(Matrix2Ptr(&impl.forwardTransform, Memory_leaker_const<Matrix2>))),
    inverseTransform(std::move(Matrix2Ptr(&impl.inverseTransform, Memory_leaker_const<Matrix2>)))
{
    this->syncWithImpl();
}

MapSceneModel::MapSceneModel(const MapSceneModel2 &other) :
    impl(other),
    earth(nullptr),
    projection(nullptr, nullptr),
    forwardTransform(std::move(Matrix2Ptr(&impl.forwardTransform, Memory_leaker_const<Matrix2>))),
    inverseTransform(std::move(Matrix2Ptr(&impl.inverseTransform, Memory_leaker_const<Matrix2>)))
{
    this->syncWithImpl();
}

MapSceneModel::~MapSceneModel()
{
}

bool MapSceneModel::forward(const GeoPoint *geo, Point<float> *point) const
{
    TAKErr code(TAKErr::TE_Ok);
    GeoPoint2 gp2;
    GeoPoint_adapt(&gp2, *geo);
    Point2<float> pt;
    code = impl.forward(&pt, gp2);
    if (code == TE_Ok)
    {
        point->x = pt.x;
        point->y = pt.y;
        point->z = pt.z;
    }
    return (code == TE_Ok) ? true : false;

}

bool MapSceneModel::forward(const GeoPoint *geo, Point<double> *point) const
{
    TAKErr code(TE_Ok);
    GeoPoint2 gp2;
    atakmap::core::GeoPoint_adapt(&gp2, *geo);
    Point2<double> pt;
    code = impl.forward(&pt, gp2);
    if (code == TE_Ok)
    {
        point->x = pt.x;
        point->y = pt.y;
        point->z = pt.z;
    }
    return (code == TE_Ok) ? true : false;

}

bool MapSceneModel::inverse(const Point<float> *point, GeoPoint *geo, const bool nearestIfOffWorld) const
{

    TAKErr code(TE_Ok);
    GeoPoint2 gp2;
    Point2<float> pt(point->x, point->y, point->z);
    code = impl.inverse(&gp2, pt, nearestIfOffWorld);
    if (code == TE_Ok)
        *geo = GeoPoint(gp2);
    return (code == TE_Ok) ? true : false;

}

void MapSceneModel::set(const AtakMapView *view, int srid, const GeoPoint *focusGeo, float focusX, float focusY, double rotation, double tilt, double scale)
{
    TAKErr code(TE_Ok);
    GeoPoint2 focusGeo2;
    GeoPoint_adapt(&focusGeo2, *focusGeo);
    code = impl.set(view->getDisplayDpi(), static_cast<size_t>(view->getWidth()), static_cast<size_t>(view->getHeight()), srid, focusGeo2, focusX, focusY, rotation, tilt, scale);

    this->syncWithImpl();
}

void MapSceneModel::set(const AtakMapView *view, int srid, const GeoPoint *focusGeo, float focusX, float focusY, double rotation, double scale)
{
    set(view, srid, focusGeo, focusX, focusY, rotation, 0.0, scale);
}

MapSceneModel &MapSceneModel::operator=(const MapSceneModel &other)
{
    this->impl = other.impl;
    this->syncWithImpl();
    return *this;
}

MapSceneModel &MapSceneModel::operator=(const MapSceneModel2 &other)
{
    this->impl = other;
    this->syncWithImpl();
    return *this;
}

void MapSceneModel::syncWithImpl()
{
    // expose the underlying projection; use a leaker since 'impl' owns the memory
    this->projection = ProjectionFactory2_getProjection(impl.projection->getSpatialReferenceID());

    // update our pointer; 'impl' continues to own
    //this->earth = impl.earth;
    this->earthPtr.reset();

    if (auto *ellipsoid = dynamic_cast<Ellipsoid2 *>(impl.earth.get())) {
        this->earthPtr.reset(new atakmap::math::Ellipsoid(std::unique_ptr<Ellipsoid2, void(*)(const Ellipsoid2 *)>(ellipsoid, Memory_leaker_const<Ellipsoid2>)));
    } else if (auto *plane = dynamic_cast<Plane2 *>(impl.earth.get())) {
        this->earthPtr.reset(new Plane(std::unique_ptr<Plane2, void (*)(const Plane2 *)>(plane, Memory_leaker_const<Plane2>)));
    } else if (auto *sphere = dynamic_cast<Sphere2 *>(impl.earth.get())) {
        this->earthPtr.reset(new Sphere(std::unique_ptr<Sphere2, void (*)(const Sphere2 *)>(sphere, Memory_leaker_const<Sphere2>)));
    }

    this->earth = earthPtr.get();

    // copy the updated camera
    this->camera.location = Point<double>(impl.camera.location.x, impl.camera.location.y, impl.camera.location.z);
    this->camera.modelView = impl.camera.modelView;
    this->camera.projection = impl.camera.projection;
    this->camera.roll = impl.camera.roll;
    this->camera.target = Point<double>(impl.camera.target.x, impl.camera.target.y, impl.camera.target.z);
}

namespace {
#if 0
    void constructCamera3D(MapCamera *camera, int mapWidth, int mapHeight, Projection &mapProjection, const GeoPoint *focusGeo, float focusScreenX, float focusScreenY, double mapRotation, double mapResolution)
    {
        const double TAN_FOV_ANGLE = 0.8284271247461900976033774484194;

        const double MIN_ZOOM_RANGE = 10;
        const double MAX_ZOOM_RANGE = 1400000000;

        //System::Console::WriteLine("constructCamera3D w=" + mapWidth + " h=" + mapHeight + " focus.lat=" + focusGeo->latitude + " focus.lng=" + focusGeo->longitude + " focus.alt=" + focusGeo->altitude + " focus.x=" + focusScreenX + " focus.y=" + focusScreenY + " rot=" + mapRotation + " res=" + mapResolution);

        // compute range based on
        // m_MetersPerPixel=TAN_FOV_ANGLE*_range/_view.Height;
        const double m_MetersPerPixel = mapResolution;
        const double _viewHeight = mapHeight;

        double _range = (m_MetersPerPixel*_viewHeight / TAN_FOV_ANGLE);

        //Sanity check our azimuth and elevation
        //_azimuth-=_rot_azi*dt;
        //_elevation-=_rot_elv*dt;
        //if (_elevation>-1) _elevation=-1;
        //else if (_elevation<-90) _elevation=-90;
        //_range*=1+_vel->Z*dt;
        if (_range<MIN_ZOOM_RANGE) _range = MIN_ZOOM_RANGE;
        else if (_range>MAX_ZOOM_RANGE) _range = MAX_ZOOM_RANGE;

        //Calculate the camera's position based on the target point, range, azimuth, and elevation parameters
        Point<double> _posTarget;
        mapProjection.forward(focusGeo, &_posTarget);

        const double _viewWidth = mapWidth;
        const double aspect = _viewWidth / (double)_viewHeight;
        const double scale = TAN_FOV_ANGLE*_range;

        //System::Console::WriteLine("_posTarget=" + _posTarget.x + "," + _posTarget.y + "," + _posTarget.z);
        Matrix xformTarget;
        xformTarget.translate(_posTarget.x, _posTarget.y, _posTarget.z);
        xformTarget.rotate((90 + focusGeo->longitude)*M_PI / 180.0, 0, 0, 1);
        xformTarget.rotate((90 - focusGeo->latitude)*M_PI / 180.0, 1, 0, 0);
        xformTarget.rotate((mapRotation*M_PI / 180.0), 0, 0, 1);

        // XXX - camera pointing elevation
        //matTarget=matTarget.Rotate(90+_elevation,Vector3D(1,0,0));

        Point<double> xformTargetZ;
        xformTarget.get(0, 2, &xformTargetZ.x);
        xformTarget.get(1, 2, &xformTargetZ.y);
        xformTarget.get(2, 2, &xformTargetZ.z);

        xformTargetZ.x *= _range;
        xformTargetZ.y *= _range;
        xformTargetZ.z *= _range;

        Point<double> xformTargetOffset;
        xformTarget.get(0, 3, &xformTargetOffset.x);
        xformTarget.get(1, 3, &xformTargetOffset.y);
        xformTarget.get(2, 3, &xformTargetOffset.z);


        Point<double> xEyePos(xformTargetZ.x + xformTargetOffset.x,
            xformTargetZ.y + xformTargetOffset.y,
            xformTargetZ.z + xformTargetOffset.z);

        const double xEyePosLength = sqrt((xEyePos.x*xEyePos.x) + (xEyePos.y*xEyePos.y) + (xEyePos.z*xEyePos.z));

        //double aspect=_view.Width/(double)_view.Height;
        Matrix xproj;
        xproj.setToScale(2 / (aspect*scale),
            2 / (scale),
            -2 / (xEyePosLength * 2));

        //forward = (Vector3D.op_subtract(pos, m_EyePos)).Normalize();
        Point<double> xforward(_posTarget.x - xEyePos.x,
            _posTarget.y - xEyePos.y,
            _posTarget.z - xEyePos.z);
        const double xforwardLength = sqrt((xforward.x*xforward.x) + (xforward.y*xforward.y) + (xforward.z*xforward.z));
        xforward.x /= xforwardLength;
        xforward.y /= xforwardLength;
        xforward.z /= xforwardLength;

        //side=forward.Cross(matTarget.YAxis3D());
        // Y*v.Z-Z*v.Y, Z*v.X-X*v.Z, X*v.Y-Y*v.X
        double xformTargetYx;
        xformTarget.get(0, 1, &xformTargetYx);
        double xformTargetYy;
        xformTarget.get(1, 1, &xformTargetYy);
        double xformTargetYz;
        xformTarget.get(2, 1, &xformTargetYz);
        Point<double> xside((xforward.y*xformTargetYz) - (xforward.z*xformTargetYy),
            (xforward.z*xformTargetYx) - (xforward.x*xformTargetYz),
            (xforward.x*xformTargetYy) - (xforward.y*xformTargetYx));
        //up=side.Cross(forward);
        Point<double> xup((xside.y*xforward.z) - (xside.z*xforward.y),
            (xside.z*xforward.x) - (xside.x*xforward.z),
            (xside.x*xforward.y) - (xside.y*xforward.x));

        // XXX - account for focus...
        Matrix xmodel(xside.x, xside.y, xside.z, 0,
            xup.x, xup.y, xup.z, 0,
            -xforward.x, -xforward.y, -xforward.z, 0,
            0, 0, 0, 1);
        xmodel.translate(-xEyePos.x, -xEyePos.y, -xEyePos.z);

        //System::Console::WriteLine("eyepos=" + xEyePos.x + "," + xEyePos.y + "," + xEyePos.z);

        camera->roll = mapRotation;
        camera->location.x = xEyePos.x;
        camera->location.y = xEyePos.y;
        camera->location.z = xEyePos.z;
        camera->target.x = _posTarget.x;
        camera->target.y = _posTarget.y;
        camera->target.z = _posTarget.z;
        camera->modelView.set(&xmodel);
        camera->projection.set(&xproj);
    }

    void constructCameraOrtho(MapCamera *camera, int mapWidth, int mapHeight, Projection &proj, double fullEquitorialExtentPixels, const GeoPoint *focusGeo, float focusX, float focusY, double rotation, double mapScale)
    {
        GeoPoint scratchGeo1;
        GeoPoint scratchGeo2;
        Point<double> scratchPt1;
        Point<double> scratchPt2;

        scratchGeo1.latitude = 0;
        scratchGeo1.longitude = proj.getMaxLongitude();
        proj.forward(&scratchGeo1, &scratchPt1);
        scratchGeo2.latitude = 0;
        scratchGeo2.longitude = proj.getMinLongitude();
        proj.forward(&scratchGeo2, &scratchPt2);
        const double projectionEquitorialExtent = std::abs(scratchPt1.x - scratchPt2.x);

        scratchGeo1.latitude = proj.getMaxLatitude();
        scratchGeo1.longitude = 0;
        proj.forward(&scratchGeo1, &scratchPt1);
        scratchGeo2.latitude = proj.getMinLatitude();
        scratchGeo2.longitude = 0;
        proj.forward(&scratchGeo2, &scratchPt2);
        const double projectionMeridianalExtent = std::abs(scratchPt1.y - scratchPt2.y);

        const double projectionNominalEquitorialScale = fullEquitorialExtentPixels
            / projectionEquitorialExtent;

        scratchGeo1.latitude = proj.getMaxLatitude();
        scratchGeo1.longitude = proj.getMinLongitude();
        Point<double> ul;
        proj.forward(&scratchGeo1, &ul);

        scratchGeo1.latitude = proj.getMaxLatitude();
        scratchGeo1.longitude = proj.getMaxLongitude();
        Point<double> ur;
        proj.forward(&scratchGeo1, &ur);

        scratchGeo1.latitude = proj.getMinLatitude();
        scratchGeo1.longitude = proj.getMaxLongitude();
        Point<double> lr;
        proj.forward(&scratchGeo1, &lr);

        scratchGeo1.latitude = proj.getMinLatitude();
        scratchGeo1.longitude = proj.getMinLongitude();
        Point<double> ll;
        proj.forward(&scratchGeo1, &ll);

        const double projExtentX = projectionNominalEquitorialScale * projectionEquitorialExtent;
        const double projExtentY = projectionNominalEquitorialScale * projectionMeridianalExtent;

        // XXX - map to centers
        Matrix localProj2pixel;
        Matrix::mapQuads(ul.x, ul.y,
            ur.x, ur.y,
            lr.x, lr.y,
            ll.x, ll.y,
            0, projExtentY,
            projExtentX, projExtentY,
            projExtentX, 0,
            0, 0,
            &localProj2pixel);

        Matrix modelView;

        modelView.setToScale(mapScale);
        modelView.concatenate(&localProj2pixel);

        // compute the scaled focus
        Point<double> p;
        proj.forward(focusGeo, &p);
        modelView.transform(&p, &p);

        // obtain the origin
        p.x -= focusX;
        p.y -= mapHeight - focusY;

        // translate us relative to the origin
        Matrix focusTranslate;
        focusTranslate.setToTranslate(-p.x, -p.y);
        modelView.preConcatenate(&focusTranslate);

        if (rotation != 0.0) {
            Matrix rot;
            rot.setToRotate((rotation*M_PI / 180.0),
                focusX, mapHeight - focusY);
            modelView.preConcatenate(&rot);
        }

        camera->roll = rotation;
        camera->target.x = p.x;
        camera->target.y = p.y;
        camera->target.z = p.z;
        camera->location.x = p.x;
        camera->location.y = p.y;
        camera->location.z = p.z + 1;
        camera->modelView.set(&modelView);
        camera->projection.setToIdentity();

        const double r_width = 1.0 / (double)mapWidth;
        const double r_height = 1.0 / (double)mapHeight;
        const double r_depth = 1.0 / 2;
        const double x = 2.0 * (r_width);
        const double y = 2.0 * (r_height);
        const double z = -2.0 * (r_depth);
        const double tx = -mapWidth * r_width;
        const double ty = -mapHeight * r_height;
        const double tz = -(0) * r_depth;

        camera->projection.set(0, 0, x);
        camera->projection.set(1, 1, y);
        camera->projection.set(2, 2, z);
        camera->projection.set(0, 3, tx);
        camera->projection.set(1, 3, ty);
        camera->projection.set(2, 3, tz);
        camera->projection.set(3, 3, 1);
    }
#endif
}
