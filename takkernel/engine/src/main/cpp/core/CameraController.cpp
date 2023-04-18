#include "core/CameraController.h"

#include <algorithm>

#include "core/MapSceneModel2.h"
#include "math/Ellipsoid2.h"
#include "math/Frustum2.h"
#include "math/Sphere2.h"
#include "math/Vector4.h"
#include "util/Memory.h"

using namespace TAK::Engine::Core;

using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

#define TAG "CameraController"

namespace {
    class ICamera
    {
    public :
        virtual TAKErr lookAt(const GeoPoint2 &focus, const double resolution, const double azimuth, const double tilt, const bool animate) NOTHROWS = 0;
    };

    class CameraBuilder : public ICamera
    {
    public :
        CameraBuilder(const MapSceneModel2 &sm) NOTHROWS;
    public :
        TAKErr lookAt(const GeoPoint2 &focus, const double resolution, const double azimuth, const double tilt, const bool animate) NOTHROWS override;
        TAKErr dispatch(MapRenderer2 &renderer, const MapRenderer2::CameraCollision collide, const bool animate) NOTHROWS;
    public :
        MapSceneModel2 sm;
    };

    class RendererCamera : public ICamera
    {
    public :
        RendererCamera(MapRenderer2 &renderer);
    public :
        TAKErr lookAt(const GeoPoint2 &focus, const double resolution, const double azimuth, const double tilt, const bool animate) NOTHROWS override;
    private :
        MapRenderer2 &_renderer;
    };
    
    TAKErr panByImpl_perspective(const MapSceneModel2 &currentModel, const float tx, const float ty, const bool poleSmoothScroll, const bool animate, ICamera &camera) NOTHROWS;
    TAKErr panByImpl_ortho(const MapSceneModel2 &currentModel, const float tx, const float ty, const bool animate, ICamera &camera) NOTHROWS;

    /*
     * @param gsdRelativeCurrent    If <code>true</code>, GSD is specified
     *                              relative to _current_ focus, not newly
     *                              specified focus. If <code>false</code> GSD
     *                              is specified as relative to the newly
     *                              specified focus.
     */
    TAKErr lookAtImpl(MapRenderer2 &renderer,
                      const MapSceneModel2 &currentModel,
                      const double gsd,
                      const double rotation,
                      const double tilt,
                      const GeoPoint2 &focus,
                      const float focusx,
                      const float focusy,
                      const bool gsdRelativeCurrent,
                      const MapRenderer::CameraCollision collide,
                      const bool animate) NOTHROWS;
    TAKErr panToImpl_ortho(const MapSceneModel2 &sm, const GeoPoint2 &focus, const float x, const float y, const bool animate, ICamera &camera) NOTHROWS;
    TAKErr panToImpl_perspective(const MapSceneModel2 &sm, const GeoPoint2 &focus_, const float x, const float y, const bool poleSmoothScroll, const bool animate, ICamera &camera) NOTHROWS;

    double adjustOrientationForPoles(const MapSceneModel2 &sm, const GeoPoint2& currentFocus, const GeoPoint2& newFocus);

    const GeoPoint2 northPole(90.0, 0.0);
    const GeoPoint2 southPole(-90.0, 0.0);

    double adjustOrientationThreshold = 37.5;
}

TAKErr TAK::Engine::Core::CameraController_panBy(MapRenderer2 &renderer, const float tx, const float ty, const MapRenderer2::CameraCollision collide, const bool animate) NOTHROWS
{
    return CameraController_panBy(renderer, tx, ty, collide, true, animate);
}
TAKErr TAK::Engine::Core::CameraController_panBy(MapRenderer2 &renderer, const float tx, const float ty, const MapRenderer2::CameraCollision collide, const bool poleSmoothScroll, const bool animate) NOTHROWS
{
    TAKErr code(TE_Ok);
    MapSceneModel2 currentModel;
    code = renderer.getMapSceneModel(&currentModel, false, MapRenderer::DisplayOrigin::UpperLeft);
    TE_CHECKRETURN_CODE(code);
    RendererCamera cam(renderer);
    if(currentModel.camera.mode == MapCamera2::Scale)
        return panByImpl_ortho(currentModel, tx, ty, animate, cam);
    else
        return panByImpl_perspective(currentModel, tx, ty, poleSmoothScroll, animate, cam);
}
TAKErr TAK::Engine::Core::CameraController_panTo(MapRenderer2 &renderer, const GeoPoint2 &focus, const float x, const float y, const MapRenderer2::CameraCollision collide, const bool animate) NOTHROWS
{
    return CameraController_panTo(renderer, focus, x, y, collide, true, animate);
}
TAKErr TAK::Engine::Core::CameraController_panTo(MapRenderer2 &renderer, const GeoPoint2 &focus, const float x, const float y, const MapRenderer2::CameraCollision collide, const bool poleSmoothScroll, const bool animate) NOTHROWS
{
    TAKErr code(TE_Ok);
    MapSceneModel2 sm;
    code = renderer.getMapSceneModel(&sm, false, MapRenderer::DisplayOrigin::UpperLeft);
    TE_CHECKRETURN_CODE(code);
    RendererCamera cam(renderer);
    if(sm.camera.mode == MapCamera2::Scale) {
        return panToImpl_ortho(sm, focus, x, y, animate, cam);
    } else {
        return panToImpl_perspective(sm, focus, x, y, poleSmoothScroll, animate, cam);
    }
}
TAKErr TAK::Engine::Core::CameraController_zoomBy(MapRenderer2 &renderer, const double scaleFactor, const MapRenderer2::CameraCollision collide, const bool animate) NOTHROWS
{
    // Don't zoom to NaN
    if(TE_ISNAN(scaleFactor) || scaleFactor <= 0.0)
        return TE_Ok;
    TAKErr code(TE_Ok);
    MapSceneModel2 sm;
    code = renderer.getMapSceneModel(&sm, false, MapRenderer::DisplayOrigin::UpperLeft);
    TE_CHECKRETURN_CODE(code);
    const double gsdRange = MapSceneModel2_range(sm.gsd, sm.camera.fov, sm.height);
    const double dx = (sm.camera.target.x-sm.camera.location.x)*sm.displayModel->projectionXToNominalMeters;
    const double dy = (sm.camera.target.y-sm.camera.location.y)*sm.displayModel->projectionYToNominalMeters;
    const double dz = (sm.camera.target.z-sm.camera.location.z)*sm.displayModel->projectionZToNominalMeters;

    const double offsetRange0 = Vector2_length<double>(Point2<double>(dx, dy, dz));
    const double offsetRange = offsetRange0 / scaleFactor;
    const double mapResolution = MapSceneModel2_gsd(gsdRange+(offsetRange-offsetRange0), sm.camera.fov, sm.height);
    GeoPoint2 focus;
    code = sm.projection->inverse(&focus, sm.camera.target);
    TE_CHECKRETURN_CODE(code);
    return renderer.lookAt(focus, mapResolution, sm.camera.azimuth, 90.0+sm.camera.elevation, collide, animate);
}
TAKErr TAK::Engine::Core::CameraController_zoomBy(MapRenderer2 &renderer, const double scaleFactor, const GeoPoint2 &focus, const float focusx, const float focusy, const MapRenderer2::CameraCollision collide, const bool animate) NOTHROWS
{
    if(TE_ISNAN(scaleFactor))
        return TE_Ok;
    TAKErr code(TE_Ok);
    MapSceneModel2 sm;
    code = renderer.getMapSceneModel(&sm, false, MapRenderer::DisplayOrigin::UpperLeft);
    TE_CHECKRETURN_CODE(code);
    const double gsdRange = MapSceneModel2_range(sm.gsd, sm.camera.fov, sm.height);
    const double dx = (sm.camera.target.x-sm.camera.location.x)*sm.displayModel->projectionXToNominalMeters;
    const double dy = (sm.camera.target.y-sm.camera.location.y)*sm.displayModel->projectionYToNominalMeters;
    const double dz = (sm.camera.target.z-sm.camera.location.z)*sm.displayModel->projectionZToNominalMeters;

    const double offsetRange0 = Vector2_length<double>(Point2<double>(dx, dy, dz));
    const double offsetRange = offsetRange0 / scaleFactor;
    const double mapResolution = MapSceneModel2_gsd(gsdRange+(offsetRange-offsetRange0), sm.camera.fov, sm.height);
    return lookAtImpl(renderer,
            sm,
            mapResolution,
            sm.camera.azimuth,
            (90.0+sm.camera.elevation),
            focus,
            focusx, focusy,
            true,
            collide,
            animate);
}
TAKErr TAK::Engine::Core::CameraController_zoomTo(MapRenderer2 &renderer, const double gsd, const GeoPoint2 &focus, const float focusx, const float focusy, const MapRenderer2::CameraCollision collide, const bool animate) NOTHROWS
{
    if(TE_ISNAN(gsd))
        return TE_Ok;
    TAKErr code(TE_Ok);
    MapSceneModel2 sm;
    code = renderer.getMapSceneModel(&sm, false, MapRenderer::DisplayOrigin::UpperLeft);
    TE_CHECKRETURN_CODE(code);
    return lookAtImpl(renderer,
            sm,
            gsd,
            sm.camera.azimuth,
            (90.0+sm.camera.elevation),
            focus,
            focusx, focusy,
            true,
            collide,
            animate);
}
TAKErr TAK::Engine::Core::CameraController_rotateBy(MapRenderer2 &renderer,
                            const double theta,
                            const GeoPoint2 &focus,
                            const MapRenderer2::CameraCollision collide,
                            const bool animate)  NOTHROWS{

    if(TE_ISNAN(theta))
        return TE_Ok;
    TAKErr code(TE_Ok);
    MapSceneModel2 sm;
    code = renderer.getMapSceneModel(&sm, false, MapRenderer::DisplayOrigin::UpperLeft);
    TE_CHECKRETURN_CODE(code);
    return renderer.lookAt(focus, sm.gsd, sm.camera.azimuth+theta, 90.0+sm.camera.elevation, collide, animate);
}
TAKErr TAK::Engine::Core::CameraController_rotateTo(MapRenderer2 &renderer,
                            const double theta,
                            const GeoPoint2 &focus,
                            const float focusx,
                            const float focusy,
                            const MapRenderer2::CameraCollision collide,
                            const bool animate) NOTHROWS {

    if(TE_ISNAN(theta))
        return TE_Ok;

    TAKErr code(TE_Ok);
    MapSceneModel2 sm;
    code = renderer.getMapSceneModel(&sm, false, MapRenderer::DisplayOrigin::UpperLeft);
    TE_CHECKRETURN_CODE(code);
    return lookAtImpl(renderer,
            sm,
            sm.gsd,
            theta,
            (90.0+sm.camera.elevation),
            focus,
            focusx, focusy,
            true,
            collide,
            animate);
}
TAKErr TAK::Engine::Core::CameraController_tiltBy(MapRenderer2 &renderer,
                          const double theta,
                          const GeoPoint2 &focus,
                          const MapRenderer2::CameraCollision collide,
                          const bool animate) NOTHROWS{

    TAKErr code(TE_Ok);
    MapSceneModel2 sm;
    code = renderer.getMapSceneModel(&sm, false, MapRenderer::DisplayOrigin::UpperLeft);
    TE_CHECKRETURN_CODE(code);
    return lookAtImpl(renderer,
            sm,
            sm.gsd,
            sm.camera.azimuth,
            theta + (90.0+sm.camera.elevation),
            focus,
            sm.focusX, sm.focusY,
            true,
            collide,
            animate);
}
TAKErr TAK::Engine::Core::CameraController_tiltBy(MapRenderer2 &renderer,
                          const double theta,
                          const GeoPoint2 &focus,
                          const float focusx,
                          const float focusy,
                          const MapRenderer::CameraCollision collide,
                          const bool animate) NOTHROWS
{
    TAKErr code(TE_Ok);
    MapSceneModel2 sm;
    code = renderer.getMapSceneModel(&sm, false, MapRenderer::DisplayOrigin::UpperLeft);
    TE_CHECKRETURN_CODE(code);
    return lookAtImpl(renderer,
            sm,
            sm.gsd,
            sm.camera.azimuth,
            theta + (90.0+sm.camera.elevation),
            focus,
            focusx, focusy,
            true,
            collide,
            animate);
}

TAKErr TAK::Engine::Core::CameraController_tiltTo(MapRenderer2 &renderer,
                          const double theta,
                          const GeoPoint2 &focus,
                          const float focusx,
                          const float focusy,
                          const MapRenderer2::CameraCollision collide,
                          const bool animate) NOTHROWS
{
    TAKErr code(TE_Ok);
    MapSceneModel2 sm;
    code = renderer.getMapSceneModel(&sm, false, MapRenderer::DisplayOrigin::UpperLeft);
    TE_CHECKRETURN_CODE(code);
    return lookAtImpl(renderer,
            sm,
            sm.gsd,
            sm.camera.azimuth,
            theta,
            focus,
            focusx, focusy,
            true,
            collide,
            animate);
}
TAKErr TAK::Engine::Core::CameraController_panTo(MapRenderer2 &renderer, const GeoPoint2 &focus, const bool animate) NOTHROWS
{
    TAKErr code(TE_Ok);
    MapSceneModel2 sm;
    code = renderer.getMapSceneModel(&sm, false, MapRenderer::DisplayOrigin::UpperLeft);
    TE_CHECKRETURN_CODE(code);
    return renderer.lookAt(focus, sm.gsd, sm.camera.azimuth, 90.0+sm.camera.elevation, MapRenderer::AdjustFocus, animate);
}
TAKErr TAK::Engine::Core::CameraController_rotateTo (MapRenderer2 &renderer,
                             const double rotation,
                             const bool animate) NOTHROWS
{

    if(TE_ISNAN(rotation))
        return TE_Ok;
    TAKErr code(TE_Ok);
    MapSceneModel2 sm;
    code = renderer.getMapSceneModel(&sm, false, MapRenderer::DisplayOrigin::UpperLeft);
    TE_CHECKRETURN_CODE(code);
    GeoPoint2 focus;
    code = sm.projection->inverse(&focus, sm.camera.target);
    TE_CHECKRETURN_CODE(code);
    return renderer.lookAt(focus, sm.gsd, rotation, 90.0+sm.camera.elevation, MapRenderer::AdjustFocus, animate);
}
TAKErr TAK::Engine::Core::CameraController_tiltTo(MapRenderer2 &renderer,
                          const double tilt,
                          const bool animate) NOTHROWS
{
    if(TE_ISNAN(tilt))
        return TE_Ok;
    TAKErr code(TE_Ok);
    MapSceneModel2 sm;
    code = renderer.getMapSceneModel(&sm, false, MapRenderer::DisplayOrigin::UpperLeft);
    TE_CHECKRETURN_CODE(code);
    GeoPoint2 focus;
    code = sm.projection->inverse(&focus, sm.camera.target);
    TE_CHECKRETURN_CODE(code);
    return renderer.lookAt (
            focus,
            sm.gsd,
            sm.camera.azimuth,
            tilt,
            MapRenderer::AdjustFocus,
            animate);
}
TAKErr TAK::Engine::Core::CameraController_zoomTo(MapRenderer2 &renderer, const double gsd, const bool animate) NOTHROWS
{
    // Don't zoom to NaN
    if (TE_ISNAN (gsd))
        return TE_Ok;
    TAKErr code(TE_Ok);
    MapSceneModel2 sm;
    code = renderer.getMapSceneModel(&sm, false, MapRenderer::DisplayOrigin::UpperLeft);
    TE_CHECKRETURN_CODE(code);
    GeoPoint2 focus;
    code = sm.projection->inverse(&focus, sm.camera.target);
    TE_CHECKRETURN_CODE(code);
    return renderer.lookAt(focus, gsd, sm.camera.azimuth, 90.0+sm.camera.elevation, MapRenderer::AdjustFocus, animate);
}
TAKErr TAK::Engine::Core::CameraController_tiltTo(MapRenderer2 &renderer,
                          const double theta,
                          const GeoPoint2 &focus,
                          const bool animate) NOTHROWS
{
    if(TE_ISNAN(theta))
        return TE_Ok;
    TAKErr code(TE_Ok);
    MapSceneModel2 sm;
    code = renderer.getMapSceneModel(&sm, false, MapRenderer::DisplayOrigin::UpperLeft);
    TE_CHECKRETURN_CODE(code);
    return renderer.lookAt(focus, sm.gsd, sm.camera.azimuth, theta, MapRenderer::AdjustFocus, animate);
}
// Utility functions
double TAK::Engine::Core::CameraController_computeRelativeDensityRatio(const MapSceneModel2 &sm, const float x, const float y) NOTHROWS
{
    GeoPoint2 focus;
    if(sm.projection->inverse(&focus, sm.camera.target) != TE_Ok)
        return 0.0;

    // intersect the end point with the plane to determine the translation vector in WCS
    GeoPoint2 endgeo;
    if (sm.inverse(&endgeo, Point2<float>(x, y)) != TE_Ok)
        return 0.0;

    Point2<double> endWCS;
    sm.projection->forward(&endWCS, endgeo);

    const double lenEndWCS = Vector2_length(
            Vector2_subtract(
                    Point2<double>(
                        endWCS.x*sm.displayModel->projectionXToNominalMeters,
                        endWCS.y*sm.displayModel->projectionYToNominalMeters,
                        endWCS.z*sm.displayModel->projectionZToNominalMeters),
                    Point2<double>(
                        sm.camera.location.x*sm.displayModel->projectionXToNominalMeters,
                        sm.camera.location.y*sm.displayModel->projectionYToNominalMeters,
                        sm.camera.location.z*sm.displayModel->projectionZToNominalMeters)));
    if(lenEndWCS == 0.0)
        return 0.0;
    const double lenFocus = Vector2_length(
            Vector2_subtract(
                    Point2<double>(
                        sm.camera.target.x*sm.displayModel->projectionXToNominalMeters,
                        sm.camera.target.y*sm.displayModel->projectionYToNominalMeters,
                        sm.camera.target.z*sm.displayModel->projectionZToNominalMeters),
                    Point2<double>(
                        sm.camera.location.x*sm.displayModel->projectionXToNominalMeters,
                        sm.camera.location.y*sm.displayModel->projectionYToNominalMeters,
                        sm.camera.location.z*sm.displayModel->projectionZToNominalMeters)));
    return  lenFocus / lenEndWCS;
}
Plane2 TAK::Engine::Core::CameraController_createTangentPlane(const MapSceneModel2 &sm, const GeoPoint2 &focus_) NOTHROWS
{
    GeoPoint2 focus(focus_);
    if(TE_ISNAN(focus.altitude))
        focus.altitude = 0.0;

    // create plane at press location
    Point2<double> startProj;
    sm.projection->forward(&startProj, focus);
    Point2<double> startProjUp;
    sm.projection->forward(&startProjUp, GeoPoint2(focus.latitude, focus.longitude, focus.altitude+100.0, focus.altitudeRef));

    // compute the normal at the start point
    Vector4<double> startNormal(startProjUp.x-startProj.x, startProjUp.y-startProj.y, startProjUp.z-startProj.z);
    startNormal.x *= sm.displayModel->projectionXToNominalMeters;
    startNormal.y *= sm.displayModel->projectionYToNominalMeters;
    startNormal.z *= sm.displayModel->projectionZToNominalMeters;
    const double startNormalLen = Vector2_length(Point2<double>(startNormal.x, startNormal.y, startNormal.z));
    startNormal.x /= startNormalLen;
    startNormal.y /= startNormalLen;
    startNormal.z /= startNormalLen;

    return Plane2(startNormal, startProj);
}
TAKErr TAK::Engine::Core::CameraController_createFocusAltitudeModel(GeometryModel2Ptr &value, const MapSceneModel2 &sm, const GeoPoint2 &focus) NOTHROWS
{
    if(!sm.earth) {
        return TE_InvalidArg;
    }

    const GeometryModel2::GeometryClass gc = sm.earth->getGeomClass();
    if(gc == GeometryModel2::PLANE) {
        value = GeometryModel2Ptr(new Plane2(CameraController_createTangentPlane(sm, focus)), Memory_deleter_const<GeometryModel2, Plane2>);
    } else if(gc == GeometryModel2::SPHERE) {
        const Sphere2 &s = (const Sphere2 &)*sm.earth;
        const double alt = TE_ISNAN(focus.altitude) ? 0.0 : focus.altitude;
        value = GeometryModel2Ptr(new Sphere2(s.center, s.radius+alt), Memory_deleter_const<GeometryModel2, Sphere2>);
    } else if(gc == GeometryModel2::ELLIPSOID) {
        const Ellipsoid2 &e = (const Ellipsoid2 &)*sm.earth;
        const double alt = TE_ISNAN(focus.altitude) ? 0.0 : focus.altitude;
        value = GeometryModel2Ptr(new Ellipsoid2(e.center, e.radiusX+alt, e.radiusY+alt, e.radiusZ+alt), Memory_deleter_const<GeometryModel2, Ellipsoid2>);
    } else {
        return TE_InvalidArg;
    }

    return TE_Ok;
}

namespace {
    CameraBuilder::CameraBuilder(const MapSceneModel2 &sm_) NOTHROWS :
        sm(sm_)
    {}

    TAKErr CameraBuilder::lookAt(const GeoPoint2 &focus, const double resolution, const double azimuth, const double tilt, const bool animate) NOTHROWS
    {
        sm = MapSceneModel2(sm.displayDpi, sm.width, sm.height, sm.projection->getSpatialReferenceID(), focus, sm.focusX, sm.focusY, azimuth, tilt, resolution, sm.camera.mode);
        return TE_Ok;
    }
    TAKErr CameraBuilder::dispatch(MapRenderer2 &renderer, const MapRenderer2::CameraCollision collide, const bool animate) NOTHROWS
    {
        TAKErr code(TE_Ok);
        GeoPoint2 focus;
        code = sm.projection->inverse(&focus, sm.camera.target);
        TE_CHECKRETURN_CODE(code);
        return renderer.lookAt(
                focus,
                sm.gsd,
                sm.camera.azimuth,
                90.0+sm.camera.elevation,
                collide,
                animate);
    }

    RendererCamera::RendererCamera(MapRenderer2 &renderer) :
        _renderer(renderer)
    {}

    TAKErr RendererCamera::lookAt(const GeoPoint2 &focus, const double resolution, const double azimuth, const double tilt, const bool animate) NOTHROWS
    {
        return _renderer.lookAt(focus, resolution, azimuth, tilt, MapRenderer::AdjustFocus, animate);
    }


     TAKErr panByImpl_perspective(const MapSceneModel2 &currentModel, const float tx, const float ty, const bool poleSmoothScroll, const bool animate, ICamera &camera) NOTHROWS
     {
        // obtain the current focus
        GeoPoint2 focusLLA;
        currentModel.projection->inverse(&focusLLA, currentModel.camera.target);

        // obtain resolution at focus
        const MapSceneModel2 sm = currentModel;

        const double dx = (sm.camera.target.x-sm.camera.location.x)*sm.displayModel->projectionXToNominalMeters;
        const double dy = (sm.camera.target.y-sm.camera.location.y)*sm.displayModel->projectionYToNominalMeters;
        const double dz = (sm.camera.target.z-sm.camera.location.z)*sm.displayModel->projectionZToNominalMeters;

        const double offsetRange = sqrt(dx*dx + dy*dy + dz*dz);
        const double gsdFocus = tan((sm.camera.fov/2.0/180.0*M_PI))*offsetRange/(sm.height / 2.0);

        // compute the translation vector length, in nominal display meters at
        // the focus point
        const double cos_theta = cos(sm.camera.elevation/180.0*M_PI);
        const double translation = sqrt(tx*tx + ty*ty)*std::max(gsdFocus, 0.025*cos_theta*cos_theta);

        // compute UP at focus
        Point2<double> camTargetUp;
        currentModel.projection->forward(&camTargetUp, GeoPoint2(focusLLA.latitude, focusLLA.longitude, focusLLA.altitude+1.0, focusLLA.altitudeRef));
        Vector4<double> focusUp(camTargetUp.x-sm.camera.target.x, camTargetUp.y-sm.camera.target.y, camTargetUp.z-sm.camera.target.z);
        {
            focusUp.x *= sm.displayModel->projectionXToNominalMeters;
            focusUp.y *= sm.displayModel->projectionYToNominalMeters;
            focusUp.z *= sm.displayModel->projectionZToNominalMeters;
            const double focusUpLen = Vector2_length(Point2<double>(focusUp.x, focusUp.y, focusUp.z));
            focusUp.x /= focusUpLen;
            focusUp.y /= focusUpLen;
            focusUp.z /= focusUpLen;
        }
        // compute NORTH at focus
        Point2<double> camTargetNorth;
         currentModel.projection->forward(&camTargetNorth, GeoPoint2(focusLLA.latitude+0.00001, focusLLA.longitude, focusLLA.altitude, focusLLA.altitudeRef));
        Vector4<double> focusNorth(camTargetNorth.x-sm.camera.target.x, camTargetNorth.y-sm.camera.target.y, camTargetNorth.z-sm.camera.target.z);
        {
            focusNorth.x *= sm.displayModel->projectionXToNominalMeters;
            focusNorth.y *= sm.displayModel->projectionYToNominalMeters;
            focusNorth.z *= sm.displayModel->projectionZToNominalMeters;
            const double focusNorthLen = Vector2_length(Point2<double>(focusNorth.x, focusNorth.y, focusNorth.z));
            focusNorth.x /= focusNorthLen;
            focusNorth.y /= focusNorthLen;
            focusNorth.z /= focusNorthLen;
        }

        const double translateDir = (atan2(ty, -tx)/M_PI*180.0)+90.0;

        // create a rotation matrix about the axis formed by the focus UP
        // vector, relative to the focus point
        Matrix2 mx;
        mx.rotate(
                ((-sm.camera.azimuth+translateDir)/180.0*M_PI),
                sm.camera.target.x*sm.displayModel->projectionXToNominalMeters,
                sm.camera.target.y*sm.displayModel->projectionYToNominalMeters,
                sm.camera.target.z*sm.displayModel->projectionZToNominalMeters,
                focusUp.x,
                focusUp.y,
                focusUp.z);

        Point2<double> translated(
                sm.camera.target.x*sm.displayModel->projectionXToNominalMeters+focusNorth.x*translation,
                sm.camera.target.y*sm.displayModel->projectionYToNominalMeters+focusNorth.y*translation,
                sm.camera.target.z*sm.displayModel->projectionZToNominalMeters+focusNorth.z*translation);
        mx.transform(&translated, translated);
        translated.x /= sm.displayModel->projectionXToNominalMeters;
        translated.y /= sm.displayModel->projectionYToNominalMeters;
        translated.z /= sm.displayModel->projectionZToNominalMeters;

        GeoPoint2 translatedLLA;
        currentModel.projection->inverse(&translatedLLA, translated);

        double newAzimuth = poleSmoothScroll ?
                adjustOrientationForPoles(sm, focusLLA, translatedLLA):
                sm.camera.azimuth;

        if(translatedLLA.longitude > 180.0) {
            translatedLLA.longitude -= 360.0;
        } else if(translatedLLA.longitude < -180.0) {
            translatedLLA.longitude += 360.0;
        }
        if (TE_ISNAN(translatedLLA.longitude) || TE_ISNAN(translatedLLA.latitude))
            return TE_Ok;
        translatedLLA.altitude = focusLLA.altitude;

        return camera.lookAt(translatedLLA,
                currentModel.gsd,
                newAzimuth,
                90.0+currentModel.camera.elevation,
                animate);
    }

     TAKErr panByImpl_ortho(const MapSceneModel2 &currentModel, const float tx, const float ty, const bool animate, ICamera &camera) NOTHROWS
     {
        // obtain the current focus
        GeoPoint2 focusLLA;
        currentModel.projection->inverse(&focusLLA, currentModel.camera.target);
        if (TE_ISNAN(focusLLA.altitude))
            focusLLA.altitude = 0.0;

        GeometryModel2Ptr geom(nullptr, nullptr);
        CameraController_createFocusAltitudeModel(geom, currentModel, focusLLA);

        TAK::Engine::Core::GeoPoint2 translatedLLA;
        currentModel.inverse(&translatedLLA, TAK::Engine::Math::Point2<float>(currentModel.focusX + tx, currentModel.focusY + ty), *geom);

        if(translatedLLA.longitude < -180.0)
            translatedLLA.longitude = translatedLLA.longitude+360.0;
        else if(translatedLLA.longitude > 180.0)
            translatedLLA.longitude = translatedLLA.longitude-360.0;

        if (TE_ISNAN(translatedLLA.latitude) && TE_ISNAN(translatedLLA.longitude))
            return TE_Done;

        return camera.lookAt(translatedLLA,
                currentModel.gsd,
                currentModel.camera.azimuth,
                90.0+currentModel.camera.elevation,
                animate);
    }
    /*
     * @param gsdRelativeCurrent    If <code>true</code>, GSD is specified
     *                              relative to _current_ focus, not newly
     *                              specified focus. If <code>false</code> GSD
     *                              is specified as relative to the newly
     *                              specified focus.
     */
    TAKErr lookAtImpl(MapRenderer2 &renderer,
                      const MapSceneModel2 &currentModel,
                      const double gsd_,
                      const double rotation,
                      const double tilt,
                      const GeoPoint2 &focus,
                      const float focusx,
                      const float focusy,
                      const bool gsdRelativeCurrent,
                      const MapRenderer::CameraCollision collide,
                      const bool animate) NOTHROWS
    {
        if(TE_ISNAN(gsd_))
            return TE_Ok;
        if(TE_ISNAN(rotation))
            return TE_Ok;
        if(TE_ISNAN(tilt))
            return TE_Ok;

        TAKErr code(TE_Ok);

        CameraBuilder builder(currentModel);

        double gsd = gsd_;
        if(gsdRelativeCurrent) {
            MapSceneModel2 sm = currentModel;
            // execute zoom first
            if(gsd != sm.gsd) {
                GeoPoint2 sm_focus;
                sm.projection->inverse(&sm_focus, sm.camera.target);
                sm = MapSceneModel2(
                        sm.displayDpi,
                        sm.width, sm.height,
                        sm.projection->getSpatialReferenceID(),
                        sm_focus,
                        sm.focusX, sm.focusY,
                        sm.camera.azimuth,
                        90.0+sm.camera.elevation,
                        gsd,
                        sm.camera.mode);
            }

            // compute the point against the current model that corresponds to
            // the same altitude as the focus point
            GeoPoint2 focusAtGsd;
            GeometryModel2Ptr focusModel(nullptr, nullptr);
            code = CameraController_createFocusAltitudeModel(focusModel, sm, focus);
            TE_CHECKRETURN_CODE(code);
            sm.inverse(&focusAtGsd, Point2<float>(sm.focusX, sm.focusY), *focusModel);

            Point2<double> focusAtGsdProj;
            sm.projection->forward(&focusAtGsdProj, focusAtGsd);

            // compute the range from the current camera to the point along the
            // LOS at focus altitude
            const double camrange = Vector2_length(
                    Vector2_subtract(
                        Point2<double>(
                            sm.camera.location.x*sm.displayModel->projectionXToNominalMeters,
                            sm.camera.location.y*sm.displayModel->projectionYToNominalMeters,
                            sm.camera.location.z*sm.displayModel->projectionZToNominalMeters),
                        Point2<double>(
                            focusAtGsdProj.x*sm.displayModel->projectionXToNominalMeters,
                            focusAtGsdProj.y*sm.displayModel->projectionYToNominalMeters,
                            focusAtGsdProj.z*sm.displayModel->projectionZToNominalMeters)));

            if (TE_ISNAN(camrange))
                return TE_Err;

            const double rangeAltAdj = TE_ISNAN(focus.altitude) ? 0.0 : focus.altitude;
            gsd = MapSceneModel2_gsd(
                            camrange+rangeAltAdj,
                            sm.camera.fov,
                            sm.height);

            if (TE_ISNAN(gsd))
                return TE_Err;
        }

        // perform basic camera orientation about focus point
        builder.lookAt (
            focus,
            gsd,
            rotation,
            tilt,
            false);

        // pan focus geo to focus x,y
        if(focusx != builder.sm.focusX || focusy != builder.sm.focusY) {
            if (builder.sm.camera.mode == MapCamera2::Scale) {
                panToImpl_ortho(builder.sm, focus, focusx, focusy, false, builder);
            } else {
                panToImpl_perspective(builder.sm, focus, focusx, focusy, false, false, builder);
            }
        }

        // execute `lookAt` with the aggregated motion
        return builder.dispatch(renderer, collide, animate);
    }
    TAKErr panToImpl_ortho(const MapSceneModel2 &sm, const GeoPoint2 &focus, const float x, const float y, const bool animate, ICamera &camera) NOTHROWS
    {
        TAKErr code(TE_Ok);
        Point2<float> xy;
        if(sm.forward(&xy, focus) != TE_Ok)
            return TE_Ok;
        panByImpl_ortho(sm, xy.x-sm.focusX, xy.y-sm.focusY, false, camera);
        panByImpl_ortho(sm, sm.focusX-x, sm.focusY-y, animate, camera);
        return TE_Ok;
    }
    TAKErr panToImpl_perspective(const MapSceneModel2 &sm, const GeoPoint2 &focus_, const float x, const float y, const bool poleSmoothScroll, const bool animate, ICamera &camera) NOTHROWS
    {
        TAKErr code(TE_Ok);
        GeoPoint2 focus(focus_);
        if(TE_ISNAN(focus.altitude))
            focus.altitude = 0.0;

        GeometryModel2Ptr panModel(nullptr, nullptr);
        code = CameraController_createFocusAltitudeModel(panModel, sm, focus);
        TE_CHECKRETURN_CODE(code);

        // intersect the end point with the model adjusted to focus altitude to
        // determine the translation vector in WCS
        GeoPoint2 endgeo;
        if (sm.inverse(&endgeo, Point2<float>(x, y), *panModel) != TE_Ok)
            return TE_Ok;

        Point2<double> endWCS;
        sm.projection->forward(&endWCS, endgeo);

        // obtain _current_ focus on model adjusted to focus altitude
        GeoPoint2 curFocusAtAlt;
        if (sm.inverse(&curFocusAtAlt, Point2<float>(sm.focusX, sm.focusY), *panModel) != TE_Ok)
            return TE_Ok;

        Point2<double> curFocusAtAltXYZ;
        sm.projection->forward(&curFocusAtAltXYZ, curFocusAtAlt);

        // compute translation between current and new focus points on the
        // model adjusted to focus altitude
        const double tx = endWCS.x - curFocusAtAltXYZ.x;
        const double ty = endWCS.y - curFocusAtAltXYZ.y;
        const double tz = endWCS.z - curFocusAtAltXYZ.z;

        GeoPoint2 target;
        sm.projection->inverse(&target, sm.camera.target);

        // XXX - translation is not really correct here, though it (seems to)
        //       work well enough as an approximation. We need the magnitude of
        //       the vector, however, direction will be dependent on the points
        //       selected for non-planar projections.

        // new focus is the desired location minus the translation
        Point2<double> focusProj;
        sm.projection->forward(&focusProj, focus);
        GeoPoint2 newFocus;
        if(sm.projection->inverse(&newFocus, Point2<double>(focusProj.x - tx, focusProj.y - ty, focusProj.z - tz)) != TE_Ok)
            return TE_Ok;
        newFocus.altitude = focus.altitude;

        double newAzimuth = poleSmoothScroll ?
                adjustOrientationForPoles(sm, curFocusAtAlt, newFocus) :
                sm.camera.azimuth;

        if(newFocus.longitude > 180.0) {
            newFocus.longitude -= 360.0;
        } else if(newFocus.longitude < 180.0) {
            newFocus.longitude += 360.0;
        }

        // recompute GSD based on range from camera to _current_ focus point on altitude adjusted geometry
        const double rangeAtAlt = Vector2_length(
            Vector2_multiply(
                Vector2_subtract(curFocusAtAltXYZ, sm.camera.location)
                , Point2<double>(sm.displayModel->projectionXToNominalMeters,
                                 sm.displayModel->projectionYToNominalMeters,
                                 sm.displayModel->projectionZToNominalMeters)
            )
        );

        return camera.lookAt(
                newFocus,
                MapSceneModel2_gsd(rangeAtAlt+curFocusAtAlt.altitude, sm.camera.fov, sm.height),
                newAzimuth,
                90.0 + sm.camera.elevation,
                animate);
    }

    double adjustOrientationForPoles(const MapSceneModel2 &sm, const GeoPoint2& currentFocus, const GeoPoint2& newFocus) {
        // planar projection does not adjust for poles
        if (sm.displayModel->earth->getGeomClass() == GeometryModel2::PLANE)
            return sm.camera.azimuth;

        // If the poles are within the camera's frustum, adjust the orientation
        // to rotate around the poles as we go from one N/S hemisphere to another.
        double rotAngle = sm.camera.azimuth;
        GeoPoint2 cameraLocation;
        Point2<double> northPoint;
        Point2<double> southPoint;
        sm.projection->inverse(&cameraLocation, sm.camera.location);
        sm.projection->forward(&northPoint, northPole);
        sm.projection->forward(&southPoint, southPole);

        // determine angle between vector extending through camera focus and
        // vector extending through poles. If angle is greater than threshold,
        // focus is far enough away to utilize normal panning
        
        // NOTE: direction is simply the point as all vectors terminate at 0,0,0
        const double northdot = Vector2_dot(Vector2_normalize(northPoint), Vector2_normalize(sm.camera.target));
        const double southdot = Vector2_dot(Vector2_normalize(southPoint), Vector2_normalize(sm.camera.target));

        const double northAng = acos(northdot) / M_PI * 180.0;
        const double southAng = acos(southdot) / M_PI * 180.0;

        const double threshold = adjustOrientationThreshold;
        if (northAng > threshold && southAng > threshold)
            return sm.camera.azimuth;
        
        double distanceToNorthPole = GeoPoint2_distance(cameraLocation, northPole, true);
        double distanceToSouthPole = GeoPoint2_distance(cameraLocation, southPole, true);
        if (distanceToNorthPole < sm.camera.farMeters) {
            Frustum2 frustum(sm.camera.projection, sm.camera.modelView);
            if (frustum.intersects(Sphere2(northPoint, 1.0))) {
                double shiftedCurLong = currentFocus.longitude + 180.0;
                double shiftedNewLong = newFocus.longitude + 180.0;
                rotAngle = (sm.camera.azimuth - shiftedCurLong) + shiftedNewLong;
            }
        }
        if (distanceToSouthPole < sm.camera.farMeters) {
            Frustum2 frustum(sm.camera.projection, sm.camera.modelView);
            if (frustum.intersects(Sphere2(southPoint, 1.0))) {
                double shiftedCurLong = 360.0 - currentFocus.longitude;
                double shiftedNewLong = 360.0 - newFocus.longitude;
                rotAngle = (sm.camera.azimuth - shiftedCurLong) + shiftedNewLong;
            }
        }
        return rotAngle;
    }
}
