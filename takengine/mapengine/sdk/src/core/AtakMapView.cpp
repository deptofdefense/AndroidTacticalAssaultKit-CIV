#include "core/AtakMapView.h"

#include<algorithm>
#include <cmath>

#include "core/AtakMapController.h"
#include "core/Datum.h"
#include "core/GeoPoint.h"
#include "core/GeoPoint2.h"
#include "core/MapSceneModel.h"
#include "core/ProjectionFactory2.h"
#include "math/Matrix.h"
#include "math/Point2.h"
#include "math/Vector4.h"
#include "raster/osm/OSMUtils.h"
#include "util/Error.h"
#include "util/MathUtils.h"

#include "thread/Lock.h"

using namespace atakmap::core;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Thread;

using namespace atakmap::math;

#define INCHES_PER_METER                37.37
#define MIN_MAP_SCALE                   2.5352504279048383e-9
#define MAX_MAP_SCALE                   1.0
#define WGS84_EQUITORIAL_RADIUS         6378137.0
#define WGS84_EQUITORIAL_CIRCUMFERENCE  (2.0*WGS84_EQUITORIAL_RADIUS*M_PI)

float AtakMapView::DENSITY = 1.5f;

AtakMapView::AtakMapView(float w_, float h_, double displayDPI_) :
    width(w_),
    height(h_),
    displayDpi(displayDPI_),
    displayResolution((1.0 / displayDPI_) * (1.0 / INCHES_PER_METER)),
    fullEquitorialExtentPixels(WGS84_EQUITORIAL_CIRCUMFERENCE * INCHES_PER_METER * displayDPI_),
    center(0, 0),
    focusAltitude(0.0),
    focusAltTerminalSlant(0.0),
    scale(MIN_MAP_SCALE),
    minMapScale(MIN_MAP_SCALE),
    maxMapScale(MAX_MAP_SCALE),
    rotation(0.0),
    tilt(0.0),
    maxTilt(84.0),
    elevationExaggerationFactor(1.0),
    animate(false),
    projection(TAK::Engine::Core::ProjectionSpi2::nullProjectionPtr()),
    controller(NULL),
    mapMutex (TEMT_Recursive),
    computeFocusPoint(true)
{
    controller = new AtakMapController(this);

    setProjection(4326);

    static bool densitySet = false;
    if (!densitySet) {
        DENSITY = this->displayDpi / 160.0f;
        densitySet = true;
    }
}

AtakMapView::~AtakMapView()
{
    if(controller)
        delete controller;
}

AtakMapController *AtakMapView::getController() const
{
    return controller;
}

void AtakMapView::addLayer(Layer *layer)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);

    layers.push_back(layer);
    dispatchLayerAdded(layer);
}

void AtakMapView::addLayer(int idx, Layer *layer)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);

    if(idx < 0 || (unsigned int)idx >= layers.size())
        throw 3;

    std::list<Layer *>::iterator it = layers.begin();
    std::advance(it, idx);
    layers.insert(it, layer);

    dispatchLayerAdded(layer);
}

void AtakMapView::removeLayer(Layer *layer)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);

    std::list<Layer *> removed;
    std::list<Layer *>::iterator it;
    for(it = layers.begin(); it != layers.end(); it++) {
        if(*it == layer) {
            layers.erase(it);
            removed.push_back(layer);
            break;
        }
    }
    if(!removed.empty())
        dispatchLayersRemoved(removed);
}

void AtakMapView::removeAllLayers()
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);

    std::list<Layer *> removed;
    std::list<Layer *>::iterator it;
    for(it = layers.begin(); it != layers.end(); it++)
        removed.push_back(*it);
    layers.clear();
    if(!removed.empty())
        dispatchLayersRemoved(removed);
}

void AtakMapView::setLayerPosition(Layer *layer, const int position)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);

    std::list<Layer *>::iterator it;
    int oldPos = 0;
    for(it = layers.begin(); it != layers.end(); it++) {
        if(*it == layer) {
            break;
        }
        oldPos++;
    }
    // layer was not found or the layer won't move, return
    if(it == layers.end() || oldPos == position)
        return;
    // delete the layer
    layers.erase(it);
    // re-insert the layer
    int adjust = (position > oldPos) ? -1 : 0;

    it = layers.begin();
    std::advance(it, position+adjust);
    layers.insert(it, layer);

    dispatchLayerPositionChanged(layer, oldPos, position);
}

std::size_t AtakMapView::getNumLayers()
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);
    return layers.size();
}

Layer *AtakMapView::getLayer(std::size_t position)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);

    if(position >= layers.size())
        return NULL; // XXX - throw exception

    std::list<Layer *>::iterator it = layers.begin();
    std::advance(it, position);
    return *it;
}

std::list<Layer *> AtakMapView::getLayers()
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);
    return layers;
}

void AtakMapView::getLayers(std::list<Layer *> &retval)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);
    retval.insert(retval.end(), layers.begin(), layers.end());
}

void AtakMapView::addLayersChangedListener(MapLayersChangedListener *listener)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);
    layersChangedListeners.insert(listener);
}

void AtakMapView::removeLayersChangedListener(MapLayersChangedListener *listener)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);
    layersChangedListeners.erase(listener);
}

void AtakMapView::getPoint(GeoPoint *c) const
{
    getPoint(c, false);
}

void AtakMapView::getPoint(GeoPoint *c, const bool atFocusAlt) const
{
    TAK::Engine::Util::TAKErr code(TAK::Engine::Util::TE_Ok);
    *c = center;
    if (atFocusAlt) {
        LockPtr lock(NULL, NULL);
        Lock_create(lock, mapMutex);

        GeoPoint2 point2;
        GeoPoint_adapt(&point2, center);

        atakmap::math::Point<float> focusPoint;
        controller->getFocusPoint(&focusPoint);

        // obtain scene model for camera location
        MapSceneModel2 scene(this->getDisplayDpi(),
                             this->getWidth(),
                             this->getHeight(),
                             this->getProjection(),
                             GeoPoint2(point2.latitude, point2.longitude),
                             focusPoint.x,
                             focusPoint.y,
                             this->rotation,
                             this->tilt,
                             this->getMapResolution());

        // XXX - compute projected point at altitude adjusted slant
        Point2<double> tgt2cam;
        code = Vector2_subtract(&tgt2cam, scene.camera.location, scene.camera.target);
        TE_CHECKRETURN(code);
        code = Vector2_normalize(&tgt2cam, tgt2cam);
        TE_CHECKRETURN(code);

        Point2<double> focusProjected;
        code = Vector2_multiply(&focusProjected, tgt2cam, focusAltTerminalSlant);
        TE_CHECKRETURN(code);
        code = Vector2_add(&focusProjected, scene.camera.target, focusProjected);
        TE_CHECKRETURN(code);

        // unproject point
        code = scene.projection->inverse(&point2, focusProjected);
        TE_CHECKRETURN(code);
        *c = GeoPoint(point2);
    }
}

double AtakMapView::getFocusAltitude() const
{
    return focusAltitude;
}

void AtakMapView::getBounds(GeoPoint *upperLeft, GeoPoint *lowerRight)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);
    MapSceneModel sm(this);
    Point<float> p;

    p.x = 0;
    p.y = 0;
    sm.inverse(&p, upperLeft, true);

    p.x = width;
    p.y = height;
    sm.inverse(&p, lowerRight, true);
}

double AtakMapView::getMapScale() const
{
    return scale;
}

double AtakMapView::getMapRotation() const
{
    return rotation;
}

double AtakMapView::getMapTilt() const
{
    return tilt;
}

double AtakMapView::getMinMapTilt(double resolution) const
{
    return 0.0;
}
double AtakMapView::getMaxMapTilt(double resolution) const
{
    const double zoomLevel = log(156543.034*cos(0.0) / resolution) / log(2.0);
    double maxTilt;

    if (zoomLevel < 4.0) {
        maxTilt = 30.0 + (48.0 - 30.0) * (zoomLevel / 4.0);
    } else if (zoomLevel < 5.0) {
        maxTilt = 48.0 + (66.0 - 48.0) * (zoomLevel - 4.0);
    } else if (zoomLevel < 6.0) {
        maxTilt = 66.0 + (72.0 - 66.0) * (zoomLevel - 5.0);
    } else if (zoomLevel < 7.0) {
        maxTilt = 72 + (80.0 - 72.0) * (zoomLevel - 6.0);
    } else if (zoomLevel < 9.0) {
        maxTilt = 80.0 + (82.0 - 80.0) * ((zoomLevel - 7.0) / (9.0 - 7.0));
    } else if (zoomLevel < 10.0) {
        maxTilt = 82 + (84.0 - 82.0) * (zoomLevel - 9.0);
    }
#if 0
    else if (zoomLevel < 11.0) {
        maxTilt = 84 + (86.0 - 84.0) * (zoomLevel - 10.0);
    }
    else if (zoomLevel < 16.0) {
        maxTilt = 86 + (89.0 - 86.0) * ((zoomLevel - 11.0) / (16.0 - 11.0));
    }
    else {
        maxTilt = 89.0;
    }
#else
    else {
        maxTilt = 84.0;
    }
#endif

    return std::min(maxTilt, this->maxTilt);
}
void AtakMapView::setMaxMapTilt(double value)
{
    maxTilt = std::max(value, 0.0);
}
double AtakMapView::getMapResolution() const
{
    return getMapResolution(scale);
}

double AtakMapView::getMapResolution(const double s) const
{
    return (displayResolution / s);
}

double AtakMapView::getFullEquitorialExtentPixels() const
{
    return fullEquitorialExtentPixels;
}

int AtakMapView::getProjection() const
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);

    // projection may never be NULL here
    return projection->getSpatialReferenceID();
}

bool AtakMapView::setProjection(const int srid)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);

    TAK::Engine::Core::ProjectionPtr2 proj = TAK::Engine::Core::ProjectionFactory2_getProjection(srid);
    if(!proj)
        return false;

    if (projection == proj
            || (projection && projection->getSpatialReferenceID() ==
                                      proj->getSpatialReferenceID())) {
        return false;
    }

    // delete the old projection and update our reference
    this->projection = std::move(proj);

    dispatchMapProjectionChanged();
    return true;
}

void AtakMapView::addMapProjectionChangedListener(MapProjectionChangedListener *listener)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);
    projectionChangedListeners.insert(listener);
}

void AtakMapView::removeMapProjectionChangedListener(MapProjectionChangedListener *listener)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);
    projectionChangedListeners.erase(listener);
}

double AtakMapView::mapResolutionAsMapScale(const double resolution) const
{
    return (displayResolution / resolution);
}

MapSceneModel *AtakMapView::createSceneModel()
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);
    return new MapSceneModel(this);
}

void AtakMapView::forward(const GeoPoint *geo, Point<float> *p)
{
    MapSceneModel *sm = createSceneModel();
    sm->forward(geo, p);
    delete sm;
}

void AtakMapView::inverse(const Point<float> *p, GeoPoint *geo)
{
    MapSceneModel *sm = createSceneModel();
    if(!sm->inverse(p, geo, false)) {
        geo->latitude = NAN;
        geo->longitude = NAN;
        geo->altitude = NAN;
    }
    delete sm;
}

double AtakMapView::getMinLatitude() const
{
    return projection->getMinLatitude();
}

double AtakMapView::getMaxLatitude() const
{
    return projection->getMaxLatitude();
}

double AtakMapView::getMinLongitude() const
{
    return projection->getMinLongitude();
}

double AtakMapView::getMaxLongitude() const
{
    return projection->getMaxLongitude();
}

double AtakMapView::getMinMapScale() const
{
    return minMapScale;
}

double AtakMapView::getMaxMapScale() const
{
    return maxMapScale;
}

void AtakMapView::setMinMapScale(const double scale)
{
    minMapScale = scale;
}

void AtakMapView::setMaxMapScale(const double scale)
{
    maxMapScale = scale;
}
void AtakMapView::setSize(const float w, const float h)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);

    width = w;
    height = h;

    Point<float> focus(w/2, h/2);
    controller->setDefaultFocusPoint(&focus);

    dispatchMapResized();
}

float AtakMapView::getWidth() const
{
    return width;
}

float AtakMapView::getHeight() const
{
    return height;
}

void AtakMapView::addMapResizedListener(MapResizedListener *listener)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);
    resizedListeners.insert(listener);
}

void AtakMapView::removeMapResizedListener(MapResizedListener *listener)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);
    resizedListeners.erase(listener);
}

double AtakMapView::getDisplayDpi() const
{
    return displayDpi;
}

double AtakMapView::getElevationExaggerationFactor() const
{
    return elevationExaggerationFactor;
}
void AtakMapView::setElevationExaggerationFactor(double factor)
{
    if (isnan(factor) || factor < 0.0)
        return;

    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);
    if (elevationExaggerationFactor != factor) {
        elevationExaggerationFactor = factor;
        dispatchElevationExaggerationFactorChanged();
    }
}

void AtakMapView::addMapElevationExaggerationFactorListener(MapElevationExaggerationFactorListener *listener)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);
    elExaggerationFactorChangedListeners.insert(listener);
}

void AtakMapView::removeMapElevationExaggerationFactorListener(MapElevationExaggerationFactorListener *listener)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);
    elExaggerationFactorChangedListeners.erase(listener);
}

void AtakMapView::addMapMovedListener(MapMovedListener *listener)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);
    movedListeners.insert(listener);
}

void AtakMapView::removeMapMovedListener(MapMovedListener *listener)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);
    movedListeners.erase(listener);
}

void AtakMapView::updateView(const GeoPoint *c, double mapScale, double rot, bool anim)
{
    updateView(*c, mapScale, rot, tilt, focusAltitude, focusAltTerminalSlant, anim);
}

//void AtakMapView::updateView(const GeoPoint &c, const double mapScale, const double rot, const double ptilt, const double focusAlt, const bool anim)
void AtakMapView::updateView(const GeoPoint &c, const double mapScale, const double rot, const double ptilt, const double focusAlt, const double focusAltSlant, const bool anim)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mapMutex);

    center.latitude = TAK::Engine::Util::MathUtils_clamp(isnan(c.latitude) ? 0.0 : c.latitude, getMinLatitude(), getMaxLatitude());
    center.longitude = TAK::Engine::Util::MathUtils_clamp(isnan(c.longitude) ? 0.0 : c.longitude, getMinLongitude(), getMaxLongitude());
    scale = TAK::Engine::Util::MathUtils_clamp(isnan(mapScale) ? 0.0 : mapScale, getMinMapScale(), getMaxMapScale());

    if (isnan(rot))
        rotation = 0.0;
    else if (rot < 0.0)
        rotation = fmod((360.0 + rot), 360.0);
    else if (rot >= 360.0)
        rotation = fmod(rot, 360.0);
    else
        rotation = rot;

    
    if (isnan(ptilt))
    {
        tilt = 0.0;
    }
    else
    {   
        const double res = getMapResolution(mapScale);
        const double maxTilt = getMaxMapTilt(res);

        tilt = TAK::Engine::Util::MathUtils_clamp(ptilt, 0.0, maxTilt);
    }

    animate = anim;
    focusAltitude = isnan(focusAlt) ? 0.0 : focusAlt;
    focusAltTerminalSlant = isnan(focusAltSlant) ? 0.0 : focusAltSlant;
    dispatchMapMoved();
}

void AtakMapView::dispatchMapResized()
{
    std::set<MapResizedListener *>::iterator it;
    for(it = resizedListeners.begin(); it != resizedListeners.end(); it++)
    {
        (*it)->mapResized(this);
    }
}

void AtakMapView::dispatchMapMoved()
{
    std::set<MapMovedListener *>::iterator it;
    for(it = movedListeners.begin(); it != movedListeners.end(); it++)
    {
        (*it)->mapMoved(this, animate);
    }
}

void AtakMapView::dispatchMapProjectionChanged()
{
    std::set<MapProjectionChangedListener *>::iterator it;
    for(it = projectionChangedListeners.begin(); it != projectionChangedListeners.end(); it++)
    {
        (*it)->mapProjectionChanged(this);
    }
}

void AtakMapView::dispatchElevationExaggerationFactorChanged()
{
    std::set<MapElevationExaggerationFactorListener *>::iterator it;
    for (it = elExaggerationFactorChangedListeners.begin(); it != elExaggerationFactorChangedListeners.end(); it++)
    {
        (*it)->mapElevationExaggerationFactorChanged(this, this->elevationExaggerationFactor);
    }
}

void AtakMapView::dispatchLayerAdded(Layer *layer)
{
    std::set<MapLayersChangedListener *>::iterator it;
    for(it = layersChangedListeners.begin(); it != layersChangedListeners.end(); it++)
    {
        (*it)->mapLayerAdded(this, layer);
    }
}

void AtakMapView::dispatchLayersRemoved(std::list<Layer *> removed)
{
    std::set<MapLayersChangedListener *>::iterator it;
    std::list<Layer *>::iterator layersIt;
    for(it = layersChangedListeners.begin(); it != layersChangedListeners.end(); it++)
    {
        for(layersIt = removed.begin(); layersIt != removed.end(); layersIt++)
        {
            (*it)->mapLayerRemoved(this, *layersIt);
        }
    }
}

void AtakMapView::dispatchLayerPositionChanged(Layer *layer, const int oldPos, const int newPos)
{
    std::set<MapLayersChangedListener *>::iterator it;
    for(it = layersChangedListeners.begin(); it != layersChangedListeners.end(); it++)
    {
        (*it)->mapLayerPositionChanged(this, layer, oldPos, newPos);
    }
}

AtakMapView::MapLayersChangedListener::~MapLayersChangedListener() throw() {}
AtakMapView::MapMovedListener::~MapMovedListener() throw() {}
AtakMapView::MapProjectionChangedListener::~MapProjectionChangedListener() throw() {}
AtakMapView::MapResizedListener::~MapResizedListener() throw() {}
AtakMapView::MapElevationExaggerationFactorListener::~MapElevationExaggerationFactorListener() throw() {}
