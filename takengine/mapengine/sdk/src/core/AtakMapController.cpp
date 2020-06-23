#include "core/AtakMapController.h"

#include <algorithm>
#include <cmath>

#include "core/AtakMapView.h"
#include "core/GeoPoint.h"
#include "core/MapSceneModel.h"
#include "core/MapSceneModel2.h"
#include "core/Projection.h"
#include "core/ProjectionFactory.h"
#include "math/Matrix.h"
#include "math/Vector4.h"

#include "thread/Lock.h"

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

atakmap::core::AtakMapController::AtakMapController(atakmap::core::AtakMapView *mv) :
    mapView(mv),
    defaultFocusPoint(0, 0),
    focusPointOffset(0,0),
    focusChangedListeners(),
    focusPointQueue(),
    controllerMutex(TEMT_Recursive)
{}

atakmap::core::AtakMapController::~AtakMapController()
{
    focusChangedListeners.clear();
}

void atakmap::core::AtakMapController::panTo(const GeoPoint *point, const bool animate)
{
    panZoomRotateTo (point,
                     mapView->getMapScale (),
                     mapView->getMapRotation (),
                     animate);
}

void atakmap::core::AtakMapController::panZoomTo(const GeoPoint *point, const double scale, const bool animate)
{
    panZoomRotateTo (point, scale, mapView->getMapRotation (), animate);
}

void atakmap::core::AtakMapController::panZoomRotateTo(const GeoPoint *point, const double scale, const double rotation, const bool animate)
{
    using namespace std;

    if (!point || ::isnan(point->latitude) ||  ::isnan(point->longitude) || ::isnan (scale))
        return;

    mapView->updateView (*point,
                         scale,
                         rotation,
                         mapView->getMapTilt(),
                         mapView->focusAltitude,
                         mapView->focusAltTerminalSlant,
                         animate);
}

void atakmap::core::AtakMapController::panTo(const GeoPoint *point, const float viewx, const float viewy, const bool animate)
{
    using namespace std;

    if (point && (::isnan(point->latitude) || ::isnan(point->longitude)))
        return;

    atakmap::math::Point<float> focusPoint = getFocusPointInternal ();

    panTo (point, animate);
    panBy (focusPoint.x - viewx, viewy - focusPoint.y, animate);
}

void atakmap::core::AtakMapController::panByAtScale(const float tx, const float ty, const double scale, const bool animate)
{
    panBy (tx * mapView->getMapScale () / scale,
           ty * mapView->getMapScale () / scale,
           animate);
}

void atakmap::core::AtakMapController::panBy(const float tx, const float ty, const bool animate)
{
    atakmap::math::Point<float> p;
    GeoPoint tgt;

    getFocusPoint(&p);

    p.x += tx;
    p.y += ty;

    mapView->inverse (&p, &tgt);

    if (::isnan(tgt.latitude) || ::isnan(tgt.longitude))
        return;

    //Enable this upon switching to GLMapView2, GLMapView lack the boolean to check.
    //if (mapView->continuousScrollEnabled) {
    //    if (tgt.longitude < -180.0)
    //        tgt.set(tgt.latitude, tgt.longitude + 360.0);
    //    else if (tgt.longitude > 180.0)
    //        tgt.set(tgt.latitude, tgt.longitude - 360.0);
    //}
    mapView->updateView (tgt,
                         mapView->getMapScale (),
                         mapView->getMapRotation (),
                         mapView->getMapTilt(),
                         mapView->focusAltitude,
                         mapView->focusAltTerminalSlant,
                         animate);
}

void atakmap::core::AtakMapController::rotateBy(double theta,
    float xpos,
    float ypos,
    bool animate) {

    double newRotation = mapView->getMapRotation() + theta;

    // Don't zoom to NaN
    if (::isnan(newRotation))
        return;

    updateBy(mapView->getMapScale(),
        newRotation,
        mapView->getMapTilt(),
        xpos, ypos,
        animate);
}

void atakmap::core::AtakMapController::rotateTo(double theta,
    const GeoPoint &point,
    bool animate) {

    // Don't zoom to NaN
    if (::isnan(theta))
        return;

    GeoPoint2 point2;
    GeoPoint_adapt(&point2, point);

    atakmap::math::Point<float> focusPoint = getFocusPointInternal();

    // construct model to 'point' at surface with no rotate/no tilt
    MapSceneModel2 nadirSurface(mapView->getDisplayDpi(),
                                mapView->getWidth(),
                                mapView->getHeight(),
                                mapView->getProjection(),
                                GeoPoint2(point2.latitude, point2.longitude),
                                focusPoint.x,
                                focusPoint.y,
                                0.0,
                                0.0,
                                mapView->getMapResolution());
    // compute cam->tgt range
    double rangeSurface;
    Vector2_length<double>(&rangeSurface,
                           Point2<double>(nadirSurface.camera.location.x-nadirSurface.camera.target.x,
                                          nadirSurface.camera.location.y-nadirSurface.camera.target.y,
                                          nadirSurface.camera.location.z-nadirSurface.camera.target.z));
    Point2<double> point2Proj;
    nadirSurface.projection->forward(&point2Proj, point2);
    double rangeElevated;
    Vector2_length<double>(&rangeElevated,
                           Point2<double>(nadirSurface.camera.location.x-point2Proj.x,
                                          nadirSurface.camera.location.y-point2Proj.y,
                                          nadirSurface.camera.location.z-point2Proj.z));
    // scale resolution by cam->'point' distance
    const double resolutionAtElevated = mapView->getMapResolution() * (rangeSurface / rangeElevated);

    // construct model to 'point' at altitude with scaled resolution with rotate/tilt
    MapSceneModel2 scene(mapView->getDisplayDpi(),
                          mapView->getWidth(),
                          mapView->getHeight(),
                          mapView->getProjection(),
                          point2,
                          focusPoint.x,
                          focusPoint.y,
                          theta,
                          mapView->getMapTilt(),
                          resolutionAtElevated);

    // obtain new center
    GeoPoint2 focusGeo2;
    scene.inverse(&focusGeo2, Point2<float>(focusPoint.x, focusPoint.y));

    // obtain new tilt
    double mapTilt = scene.camera.elevation + 90;

    // obtain new rotation
    double mapRotation = scene.camera.azimuth;

    Point2<double> focusGeo2Proj;
    scene.projection->forward(&focusGeo2Proj, focusGeo2);

    double terminalSlant;
    Vector2_length<double>(&terminalSlant, Point2<double>(scene.camera.target.x - focusGeo2Proj.x,
                                                          scene.camera.target.y - focusGeo2Proj.y,
                                                          scene.camera.target.z - focusGeo2Proj.z));

    mapView->updateView(GeoPoint(focusGeo2),
                        mapView->getMapScale(),
                        mapRotation,
                        mapTilt,
                        point2.altitude,
                        terminalSlant,
                        animate);
}

void atakmap::core::AtakMapController::zoomTo(const double scale, const bool animate)
{
    using namespace std;

    // Don't zoom to NaN
    if (::isnan(scale))
        return;

    GeoPoint mapCenter;
    mapView->getPoint(&mapCenter);

    mapView->updateView (mapCenter,
                         scale,
                         mapView->getMapRotation (),
                         mapView->getMapTilt(),
                         mapView->focusAltitude,
                         mapView->focusAltTerminalSlant,
                         animate);
}

void atakmap::core::AtakMapController::zoomBy(const double scaleFactor, const float xpos, const float ypos, const bool animate)
{
    double newScale = mapView->getMapScale() * scaleFactor;

    // don't zoom to NAN
    if (::isnan(newScale))
        return;

    newScale = std::max(std::min(newScale, mapView->getMaxMapScale()), mapView->getMinMapScale());

    this->updateBy(newScale,
                   mapView->getMapRotation(),
                   mapView->getMapTilt(),
                   xpos,
                   ypos,
                   animate);
}

void atakmap::core::AtakMapController::rotateTo(const double rotation, const bool animate)
{
    GeoPoint mapCenter;
    mapView->getPoint(&mapCenter);
    mapView->updateView (mapCenter,
                         mapView->getMapScale (),
                         rotation,
                         mapView->getMapTilt(),
                         mapView->focusAltitude,
                         mapView->focusAltTerminalSlant,
                         animate);
}

void atakmap::core::AtakMapController::tiltTo(const double tilt, const bool animate)
{
    GeoPoint mapCenter;
    mapView->getPoint(&mapCenter);
    mapView->updateView(mapCenter,
                        mapView->getMapScale(),
                        mapView->getMapRotation(),
                        tilt,
                        mapView->focusAltitude,
                        mapView->focusAltTerminalSlant,
                        animate);
}

void atakmap::core::AtakMapController::tiltBy(const double tilt, const float xpos, const float ypos, const bool animate)
{
    double newTilt = mapView->getMapTilt() + tilt;
    if (::isnan(newTilt))
        return;
    this->updateBy(mapView->getMapScale(),
                   mapView->getMapRotation(),
                   newTilt,
                   xpos, ypos,
                   animate);
}

void atakmap::core::AtakMapController::tiltTo(double theta,
    const GeoPoint &point,
    bool animate) {

    // Don't zoom to NaN
    if (::isnan(theta))
        return;
    if (theta > mapView->getMaxMapTilt(mapView->getMapResolution()))
        theta = mapView->getMaxMapTilt(mapView->getMapResolution());
    if (theta < mapView->getMinMapTilt(mapView->getMapResolution()))
        theta = mapView->getMinMapTilt(mapView->getMapResolution());

    GeoPoint2 point2;
    GeoPoint_adapt(&point2, point);

    atakmap::math::Point<float> focusPoint = getFocusPointInternal();

    // construct model to 'point' at surface with no rotate/no tilt
    MapSceneModel2 nadirSurface(mapView->getDisplayDpi(),
                                mapView->getWidth(),
                                mapView->getHeight(),
                                mapView->getProjection(),
                                GeoPoint2(point2.latitude, point2.longitude),
                                focusPoint.x,
                                focusPoint.y,
                                0.0,
                                0.0,
                                mapView->getMapResolution());
    // compute cam->tgt range
    double rangeSurface;
    Vector2_length<double>(&rangeSurface,
                           Point2<double>(nadirSurface.camera.location.x-nadirSurface.camera.target.x,
                                          nadirSurface.camera.location.y-nadirSurface.camera.target.y,
                                          nadirSurface.camera.location.z-nadirSurface.camera.target.z));
    Point2<double> point2Proj;
    nadirSurface.projection->forward(&point2Proj, point2);
    double rangeElevated;
    Vector2_length<double>(&rangeElevated,
                           Point2<double>(nadirSurface.camera.location.x-point2Proj.x,
                                          nadirSurface.camera.location.y-point2Proj.y,
                                          nadirSurface.camera.location.z-point2Proj.z));
    // scale resolution by cam->'point' distance
    const double resolutionAtElevated = mapView->getMapResolution() * (rangeSurface / rangeElevated);

    // construct model to 'point' at altitude with scaled resolution with rotate/tilt
    MapSceneModel2 scene(mapView->getDisplayDpi(),
                          mapView->getWidth(),
                          mapView->getHeight(),
                          mapView->getProjection(),
                          point2,
                          focusPoint.x,
                          focusPoint.y,
                          mapView->getMapRotation(),
                          theta,
                          resolutionAtElevated);

    // obtain new center
    GeoPoint2 focusGeo2;
    scene.inverse(&focusGeo2, Point2<float>(focusPoint.x, focusPoint.y));

    // obtain new tilt
    double mapTilt = scene.camera.elevation + 90;

    // obtain new rotation
    double mapRotation = scene.camera.azimuth;

    Point2<double> focusGeo2Proj;
    scene.projection->forward(&focusGeo2Proj, focusGeo2);

    double terminalSlant;
    Vector2_length<double>(&terminalSlant, Point2<double>(scene.camera.target.x - focusGeo2Proj.x,
                                                          scene.camera.target.y - focusGeo2Proj.y,
                                                          scene.camera.target.z - focusGeo2Proj.z));

    mapView->updateView(GeoPoint(focusGeo2),
                        mapView->getMapScale(),
                        mapRotation,
                        mapTilt,
                        point2.altitude,
                        terminalSlant,
                        animate);
}

void atakmap::core::AtakMapController::updateBy(const double scale, const double rotation, const double tilt, const float xpos, const float ypos, const bool animate)
{
    GeoPoint mapCenter;
    mapView->getPoint(&mapCenter);
    atakmap::math::Point<float> focusPoint = getFocusPointInternal();
    //This is of type projection in ATAK
    int proj = mapView->getProjection();
    MapSceneModel sm(mapView, proj, &mapCenter, focusPoint.x, focusPoint.y, rotation, tilt, scale);
    atakmap::math::Point<float> holderPoint(xpos, ypos);
    GeoPoint focusLatLng;
    mapView->inverse(&holderPoint, &focusLatLng);
    GeoPoint focusLatLng2;

    atakmap::math::Point<float> holderPoint2(xpos, ypos);
    sm.inverse(&holderPoint2, &focusLatLng2, true);
    if (focusLatLng.isValid() && focusLatLng2.isValid())
    {
        double latDiff = focusLatLng2.latitude - focusLatLng.latitude;
        double lonDiff = focusLatLng2.longitude - focusLatLng.longitude;
        mapView->updateView(GeoPoint(mapCenter.latitude - latDiff, mapCenter.longitude - lonDiff),
                            scale,
                            rotation,
                            tilt,
                            mapView->focusAltitude,
                            mapView->focusAltTerminalSlant,
                            animate);
    }
}

void atakmap::core::AtakMapController::addFocusPointChangedListener(MapControllerFocusPointChangedListener *listener)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, controllerMutex);
    focusChangedListeners.insert(listener);
}

void atakmap::core::AtakMapController::removeFocusPointChangedListener(MapControllerFocusPointChangedListener *listener)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, controllerMutex);
    focusChangedListeners.erase(listener);
}

void atakmap::core::AtakMapController::setDefaultFocusPoint(const atakmap::math::Point<float> *defaultFocus)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, controllerMutex);

    defaultFocusPoint.x = (float)defaultFocus->x;
    defaultFocusPoint.y = (float)defaultFocus->y;

    if(focusPointQueue.empty())
        dispatchFocusPointChanged();
}

void atakmap::core::AtakMapController::setFocusPoint(const atakmap::math::Point<float> *focus)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, controllerMutex);

    focusPointQueue.push(atakmap::math::Point<float>((float)focus->x, (float)focus->y));
    dispatchFocusPointChanged();
}

void atakmap::core::AtakMapController::popFocusPoint()
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, controllerMutex);

    if(!focusPointQueue.empty()) {
        focusPointQueue.pop();
    }
    dispatchFocusPointChanged();
}

void atakmap::core::AtakMapController::resetFocusPoint()
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, controllerMutex);

    while(!focusPointQueue.empty()) {
        focusPointQueue.pop();
    }
    dispatchFocusPointChanged();
}

void atakmap::core::AtakMapController::getFocusPoint(atakmap::math::Point<float> *focus)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, controllerMutex);

    atakmap::math::Point<float> internal = getFocusPointInternal();
    focus->x = internal.x;
    focus->y = internal.y;
}

void atakmap::core::AtakMapController::dispatchFocusPointChanged()
{
    atakmap::math::Point<float> focus = getFocusPointInternal();
    std::set<MapControllerFocusPointChangedListener *>::iterator it;
    for(it = focusChangedListeners.begin(); it != focusChangedListeners.end(); it++)
    {
        (*it)->mapControllerFocusPointChanged(this, &focus);
    }
}

atakmap::math::Point<float> atakmap::core::AtakMapController::getFocusPointInternal() const
{
    if(focusPointQueue.empty())
        return atakmap::math::Point<float>(defaultFocusPoint.x - focusPointOffset.x, defaultFocusPoint.y - focusPointOffset.y, defaultFocusPoint.z - focusPointOffset.z);
    else
    {
        const atakmap::math::Point<float>& top = focusPointQueue.top();
        return atakmap::math::Point<float>(top.x - focusPointOffset.x, top.y - focusPointOffset.y, top.z - focusPointOffset.z);
    }
}

void atakmap::core::AtakMapController::setFocusPointOffset(const atakmap::math::Point<float> *focusOffset)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, controllerMutex);

    focusPointOffset = *focusOffset;
    dispatchFocusPointChanged();
}

atakmap::core::MapControllerFocusPointChangedListener::~MapControllerFocusPointChangedListener() throw ()
{}
