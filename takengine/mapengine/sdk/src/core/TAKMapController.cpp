#include "core/TAKMapController.h"

#include <algorithm>
#include <cmath>

#include "core/TAKMapView.h"
#include "core/GeoPoint2.h"
#include "core/MapSceneModel2.h"
#include "core/Projection2.h"
#include "core/ProjectionFactory2.h"
#include "math/Matrix2.h"

#include "thread/Lock.h"

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Core;


TAKMapController::TAKMapController(TAKMapView *mv) NOTHROWS :
    mapView(mv),
    focusChangedListeners(),
    controllerMutex(TEMT_Recursive)
{}

TAKMapController::~TAKMapController()
{
    focusChangedListeners.clear();
}

void TAKMapController::panTo(const GeoPoint2 &point, const bool animate) NOTHROWS
{
    panZoomRotateTo(point,
        mapView->getMapScale(),
        mapView->getMapRotation(),
        animate);
}

void TAKMapController::panZoomTo(const GeoPoint2 &point, const double scale, const bool animate) NOTHROWS
{
    panZoomRotateTo(point, scale, mapView->getMapRotation(), animate);
}

void TAKMapController::panZoomRotateTo(const GeoPoint2 &point, const double scale, const double rotation, const bool animate) NOTHROWS
{
    using namespace std;

    if (isnan(point.latitude) || isnan(point.longitude) || isnan(scale))
        return;

    mapView->updateView(point,
        scale,
        rotation,
        mapView->getMapTilt(),
        animate);
}

void TAKMapController::panTo(const GeoPoint2 &point, const float viewx, const float viewy, const bool animate) NOTHROWS
{
    using namespace std;

    if (isnan(point.latitude) || isnan(point.longitude))
        return;

    TAK::Engine::Math::Point2<float> focusPoint = getFocusPointInternal();

    panTo(point, animate);
    panBy(focusPoint.x - viewx, viewy - focusPoint.y, animate);
}

void TAKMapController::panByAtScale(const float tx, const float ty, const double scale, const bool animate) NOTHROWS
{
    panBy(static_cast<float>(tx * mapView->getMapScale() / scale),
        static_cast<float>(ty * mapView->getMapScale() / scale),
        animate);
}

void TAKMapController::panBy(const float tx, const float ty, const bool animate) NOTHROWS
{
    TAK::Engine::Math::Point2<float> p;
    GeoPoint2 tgt;

    getFocusPoint(&p);

    p.x += tx;
    p.y += ty;

    mapView->inverse(&tgt, p);
    //Enable this upon switching to GLMapView2, GLMapView lack the boolean to check.
    //if (mapView->continuousScrollEnabled) {
    //    if (tgt.longitude < -180.0)
    //        tgt.set(tgt.latitude, tgt.longitude + 360.0);
    //    else if (tgt.longitude > 180.0)
    //        tgt.set(tgt.latitude, tgt.longitude - 360.0);
    //}
    mapView->updateView(tgt,
        mapView->getMapScale(),
        mapView->getMapRotation(),
        mapView->getMapTilt(),
        animate);
}

void TAKMapController::rotateBy(double theta,
    float xpos,
    float ypos,
    bool animate) NOTHROWS
{

    double newRotation = mapView->getMapRotation() + theta;

    // Don't zoom to NaN
    if (isnan(newRotation))
        return;

    updateBy(mapView->getMapScale(),
        newRotation,
        mapView->getMapTilt(),
        xpos, ypos,
        animate);
}


void TAKMapController::zoomTo(const double scale, const bool animate) NOTHROWS
{
    using namespace std;

    // Don't zoom to NaN
    if (isnan(scale))
        return;

    GeoPoint2 mapCenter;
    mapCenter = mapView->getPoint();

    double tilt = mapView->getMapTilt();
    mapView->updateView(mapCenter,
        scale,
        mapView->getMapRotation(),
        tilt,
        animate);
}

void TAKMapController::zoomBy(const double scaleFactor, const float xpos, const float ypos, const bool animate) NOTHROWS
{
    using namespace std;

    double newScale;
    TAK::Engine::Math::Matrix2 xform;
    GeoPoint2 mapCenter;
    TAK::Engine::Math::Point2<float> focusPoint;
    double rotation;
    int srid;
    GeoPoint2 focusLatLng;
    GeoPoint2 focuslatLng2;
    TAK::Engine::Math::Point2<float> scratchF;

    TAK::Engine::Math::Point2<float> pos(xpos, ypos);
    double latDiff;
    double lonDiff;

    newScale = mapView->getMapScale() * scaleFactor;

    // Don't zoom to NaN
    if (isnan(newScale))
        return;

    newScale = std::min(std::max(newScale,
        mapView->getMinMapScale()),
        mapView->getMaxMapScale());


    xform.setToIdentity();
    mapCenter = mapView->getPoint();

    focusPoint = getFocusPointInternal();
    rotation = mapView->getMapRotation();

    srid = mapView->getProjection();
    MapSceneModel2 sm(mapView->getDisplayDPI(),
        mapView->getWidth(), 
        mapView->getHeight(),
        srid,
        mapCenter,
        focusPoint.x,
        focusPoint.y,
        rotation,
        mapView->getMapTilt(),
        newScale);

    mapView->inverse(&focusLatLng, pos);


    scratchF.x = xpos;
    scratchF.y = ypos;
    sm.inverse(&focuslatLng2, scratchF);

    latDiff = focuslatLng2.latitude - focusLatLng.latitude;
    lonDiff = focuslatLng2.longitude - focusLatLng.longitude;

    mapCenter.latitude -= latDiff;
    mapCenter.longitude -= lonDiff;
    double tilt = mapView->getMapTilt();

    mapView->updateView(mapCenter,
        newScale,
        rotation,
        tilt,
        animate);
}

void TAKMapController::rotateTo(const double rotation, const bool animate) NOTHROWS
{
    GeoPoint2 mapCenter;
    mapCenter = mapView->getPoint();
    mapView->updateView(mapCenter,
        mapView->getMapScale(),
        rotation,
        mapView->getMapTilt(),
        animate);
}

void TAKMapController::tiltTo(const double tilt, const bool animate) NOTHROWS
{
    GeoPoint2 mapCenter;
    mapCenter = mapView->getPoint();
    mapView->updateView(mapCenter,
        mapView->getMapScale(),
        mapView->getMapRotation(),
        tilt,
        animate);
}

void TAKMapController::tiltBy(const double tilt, const float xpos, const float ypos, const bool animate) NOTHROWS
{
    double newTilt = mapView->getMapTilt() + tilt;
    if (isnan(newTilt))
        return;
    this->updateBy(mapView->getMapScale(),
        mapView->getMapRotation(),
        newTilt,
        xpos, ypos,
        animate);
}

void TAKMapController::updateBy(const double scale, const double rotation, const double tilt, const float xpos, const float ypos, const bool animate) NOTHROWS
{
    GeoPoint2 mapCenter;
    mapCenter = mapView->getPoint();
    TAK::Engine::Math::Point2<float> focusPoint = getFocusPointInternal();
    //This is of type projection in ATAK
    int proj = mapView->getProjection();
    MapSceneModel2 sm(mapView->getDisplayDPI(), mapView->getWidth(), mapView->getHeight(), proj, mapCenter, focusPoint.x, focusPoint.y, rotation, tilt, scale);
    TAK::Engine::Math::Point2<float> holderPoint = TAK::Engine::Math::Point2<float>(xpos, ypos);
    GeoPoint2 focusLatLng;
    mapView->inverse(&focusLatLng, holderPoint);
    GeoPoint2 focusLatLng2;
    //This was null not focusLatLng2 in ATAK
    sm.inverse(&focusLatLng2, TAK::Engine::Math::Point2<float>(xpos, ypos), true);
    if (&focusLatLng != NULL && &focusLatLng2 != NULL)
    {
        double latDiff = focusLatLng2.latitude - focusLatLng.latitude;
        double lonDiff = focusLatLng2.longitude - focusLatLng.longitude;
        GeoPoint2 holderGeoPoint = GeoPoint2(mapCenter.latitude - latDiff, mapCenter.longitude - lonDiff);
        mapView->updateView(holderGeoPoint,
            scale,
            rotation,
            tilt,
            animate);
    }
}

void TAKMapController::addFocusPointChangedListener(MapControllerFocusPointChangedListener *listener) NOTHROWS
{
    Lock lock(controllerMutex);
    focusChangedListeners.insert(listener);
}

void TAKMapController::removeFocusPointChangedListener(MapControllerFocusPointChangedListener *listener) NOTHROWS
{
    Lock lock(controllerMutex);
    focusChangedListeners.erase(listener);
}

void TAKMapController::setDefaultFocusPoint(const TAK::Engine::Math::Point2<float> *defaultFocus) NOTHROWS
{
    Lock lock(controllerMutex);

    mapView->focusX = (float)defaultFocus->x;
    mapView->focusY = (float)defaultFocus->y;

}

void TAKMapController::setFocusPoint(const TAK::Engine::Math::Point2<float> *focus) NOTHROWS
{
    Lock lock(controllerMutex);

    mapView->focusX = focus->x;
    mapView->focusY = focus->y;
    dispatchFocusPointChanged();
}

void TAKMapController::popFocusPoint() NOTHROWS
{
    Lock lock(controllerMutex);

    dispatchFocusPointChanged();
}

void TAKMapController::resetFocusPoint() NOTHROWS
{
    Lock lock(controllerMutex);

    dispatchFocusPointChanged();
}

void TAKMapController::getFocusPoint(TAK::Engine::Math::Point2<float> *focus) NOTHROWS
{
    Lock lock(controllerMutex);

    focus->x = mapView->focusX;
    focus->y = mapView->focusY;
}

void TAKMapController::dispatchFocusPointChanged() NOTHROWS
{
    std::set<MapControllerFocusPointChangedListener *>::iterator it;
    for (it = focusChangedListeners.begin(); it != focusChangedListeners.end(); it++)
    {
        auto focus_point = getFocusPointInternal();
        (*it)->mapControllerFocusPointChanged(this, &focus_point);
    }
}

TAK::Engine::Math::Point2<float> TAKMapController::getFocusPointInternal() NOTHROWS
{
    return TAK::Engine::Math::Point2<float>(mapView->focusX, mapView->focusY);
}

MapControllerFocusPointChangedListener::~MapControllerFocusPointChangedListener() NOTHROWS {}

void MapControllerFocusPointChangedListener::mapControllerFocusPointChanged(TAKMapController *controller, const TAK::Engine::Math::Point2<float> * const focus) NOTHROWS
{
    controller->setFocusPoint(focus);
}