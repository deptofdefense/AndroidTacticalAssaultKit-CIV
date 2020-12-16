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

AtakMapView::AtakMapView(float w, float h, double display_dpi) :
    width_(w),
    height_(h),
    display_dpi_(display_dpi),
    full_equitorial_extent_pixels_(AtakMapView_getFullEquitorialExtentPixels(display_dpi)),
    center_(0, 0),
    focus_altitude_(0.0),
    focus_alt_terminal_slant_(0.0),
    scale_(MIN_MAP_SCALE),
    min_map_scale_(MIN_MAP_SCALE),
    max_map_scale_(MAX_MAP_SCALE),
    rotation_(0.0),
    tilt_(0.0),
    max_tilt_(84.0),
    elevation_exaggeration_factor_(1.0),
    animate_(false),
    projection_(TAK::Engine::Core::ProjectionSpi2::nullProjectionPtr()),
    controller_(nullptr),
    map_mutex_ (TEMT_Recursive),
    compute_focus_point_(true),
    focus_off_x_(0.0),
    focus_off_y_(0.0),
    continue_scroll_enabled_(true)
{
    controller_ = new AtakMapController(this);
    Point<float> focus(w/2 + focus_off_x_, h/2 + focus_off_y_);
    controller_->setDefaultFocusPoint(&focus);

    setProjection(4326);

    static bool densitySet = false;
    if (!densitySet) {
        DENSITY = static_cast<float>(this->display_dpi_ / 160.0f);
        densitySet = true;
    }
}

AtakMapView::~AtakMapView()
{
    if(controller_)
        delete controller_;
}

AtakMapController *AtakMapView::getController() const
{
    return controller_;
}

void AtakMapView::addLayer(Layer *layer)
{
    Lock lock(map_mutex_);

    layers_.push_back(layer);
    dispatchLayerAdded(layer);
}

void AtakMapView::addLayer(int idx, Layer *layer)
{
    Lock lock(map_mutex_);

    if(idx < 0 || (unsigned int)idx >= layers_.size())
        throw 3;

    auto it = layers_.begin();
    std::advance(it, idx);
    layers_.insert(it, layer);

    dispatchLayerAdded(layer);
}

void AtakMapView::removeLayer(Layer *layer)
{
    Lock lock(map_mutex_);

    std::list<Layer *> removed;
    std::list<Layer *>::iterator it;
    for(it = layers_.begin(); it != layers_.end(); it++) {
        if(*it == layer) {
            layers_.erase(it);
            removed.push_back(layer);
            break;
        }
    }
    if(!removed.empty())
        dispatchLayersRemoved(removed);
}

void AtakMapView::removeAllLayers()
{
    Lock lock(map_mutex_);

    std::list<Layer *> removed;
    std::list<Layer *>::iterator it;
    for(it = layers_.begin(); it != layers_.end(); it++)
        removed.push_back(*it);
    layers_.clear();
    if(!removed.empty())
        dispatchLayersRemoved(removed);
}

void AtakMapView::setLayerPosition(Layer *layer, const int position)
{
    Lock lock(map_mutex_);

    std::list<Layer *>::iterator it;
    int oldPos = 0;
    for(it = layers_.begin(); it != layers_.end(); it++) {
        if(*it == layer) {
            break;
        }
        oldPos++;
    }
    // layer was not found or the layer won't move, return
    if(it == layers_.end() || oldPos == position)
        return;
    // delete the layer
    layers_.erase(it);
    // re-insert the layer
    it = layers_.begin();
    std::advance(it, position);
    layers_.insert(it, layer);

    dispatchLayerPositionChanged(layer, oldPos, position);
}

std::size_t AtakMapView::getNumLayers()
{
    Lock lock(map_mutex_);
    return layers_.size();
}

Layer *AtakMapView::getLayer(std::size_t position)
{
    Lock lock(map_mutex_);

    if(position >= layers_.size())
        return nullptr; // XXX - throw exception

    auto it = layers_.begin();
    std::advance(it, position);
    return *it;
}

std::list<Layer *> AtakMapView::getLayers()
{
    Lock lock(map_mutex_);
    return layers_;
}

void AtakMapView::getLayers(std::list<Layer *> &retval)
{
    Lock lock(map_mutex_);
    retval.insert(retval.end(), layers_.begin(), layers_.end());
}

void AtakMapView::addLayersChangedListener(MapLayersChangedListener *listener)
{
    Lock lock(map_mutex_);
    layers_changed_listeners_.insert(listener);
}

void AtakMapView::removeLayersChangedListener(MapLayersChangedListener *listener)
{
    Lock lock(map_mutex_);
    layers_changed_listeners_.erase(listener);
}

void AtakMapView::getPoint(GeoPoint *c) const
{
    getPoint(c, false);
}

void AtakMapView::getPoint(GeoPoint *c, const bool atFocusAlt) const
{
    TAK::Engine::Util::TAKErr code(TAK::Engine::Util::TE_Ok);
    *c = center_;
    if (atFocusAlt) {
        Lock lock(map_mutex_);

        GeoPoint2 point2;
        GeoPoint_adapt(&point2, center_);

        atakmap::math::Point<float> focusPoint;
        controller_->getFocusPoint(&focusPoint);

        // obtain scene model for camera location
        MapSceneModel2 scene(this->getDisplayDpi(),
                             static_cast<int>(this->getWidth()),
                             static_cast<int>(this->getHeight()),
                             this->getProjection(),
                             GeoPoint2(point2.latitude, point2.longitude),
                             focusPoint.x,
                             focusPoint.y,
                             this->rotation_,
                             this->tilt_,
                             this->getMapResolution());

        // XXX - compute projected point at altitude adjusted slant
        Point2<double> tgt2cam;
        code = Vector2_subtract(&tgt2cam, scene.camera.location, scene.camera.target);
        TE_CHECKRETURN(code);
        code = Vector2_normalize(&tgt2cam, tgt2cam);
        TE_CHECKRETURN(code);

        Point2<double> focusProjected;
        code = Vector2_multiply(&focusProjected, tgt2cam, focus_alt_terminal_slant_);
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
    return focus_altitude_;
}

void AtakMapView::getBounds(GeoPoint *upperLeft, GeoPoint *lowerRight)
{
    Lock lock(map_mutex_);
    MapSceneModel sm(this);
    Point<float> p;

    p.x = 0;
    p.y = 0;
    sm.inverse(&p, upperLeft, true);

    p.x = width_;
    p.y = height_;
    sm.inverse(&p, lowerRight, true);
}

double AtakMapView::getMapScale() const
{
    return scale_;
}

double AtakMapView::getMapRotation() const
{
    return rotation_;
}

double AtakMapView::getMapTilt() const
{
    return tilt_;
}

double AtakMapView::getMinMapTilt(double resolution) const
{
    return 0.0;
}
double AtakMapView::getMaxMapTilt(double resolution) const
{
#ifndef __ANDROID__
    const double zoomLevel = log(156543.034*cos(0.0) / resolution) / log(2.0);
    double maxTilt;
#ifndef __ANDROID__
    if (zoomLevel < 6.0) {
        maxTilt = 0.0;
    } else if (zoomLevel < 9.0) {
        maxTilt = 30.0;
    } else if (zoomLevel < 10.0) {
        maxTilt = 30.0 + (48.0 - 30.0) * (zoomLevel / 9.0);
    } else if (zoomLevel < 11.0) {
        maxTilt = 48.0 + (66.0 - 48.0) * (zoomLevel - 10.0);
    } else if (zoomLevel < 12.0) {
        maxTilt = 66.0 + (72.0 - 66.0) * (zoomLevel - 11.0);
    } else if (zoomLevel < 13.0) {
        maxTilt = 72 + (80.0 - 72.0) * (zoomLevel - 12.0);
    } else if (zoomLevel < 14.0) {
        maxTilt = 80.0 + (82.0 - 80.0) * (zoomLevel - 13.0);
    } else if (zoomLevel < 15.0) {
        maxTilt = 82 + (this->max_tilt_ - 82.0) * (zoomLevel - 9.0);
    } else {
        maxTilt = this->max_tilt_;
    }
#else
    if (zoomLevel < 6.0) {
        maxTilt = 0.0;
    } else if (zoomLevel < 10.0) {
        maxTilt = 30.0;
    } else if (zoomLevel < 14.0) {
        maxTilt = 30.0 + ((zoomLevel - 10.0) / 4.0 * 15.0 / 4.0);
    } else if (zoomLevel < 15.5) {
        maxTilt = 45.0 + ((zoomLevel - 14.0) / 1.5 * (this->max_tilt_ - 45.0) / 1.5);
    } else {
        maxTilt = this->max_tilt_;
    }

#endif
    return std::min(maxTilt, this->max_tilt_);
#else
    return this->max_tilt_;
#endif
}
void AtakMapView::setMaxMapTilt(double value)
{
    max_tilt_ = std::max(value, 0.0);
}
double AtakMapView::getMapResolution() const
{
    return getMapResolution(scale_);
}

double AtakMapView::getMapResolution(const double s) const
{
    return AtakMapView_getMapResolution(display_dpi_, s);
}

double AtakMapView::getFullEquitorialExtentPixels() const
{
    return full_equitorial_extent_pixels_;
}

int AtakMapView::getProjection() const
{
    Lock lock(map_mutex_);

    // projection may never be NULL here
    return projection_->getSpatialReferenceID();
}

bool AtakMapView::setProjection(const int srid)
{
    Lock lock(map_mutex_);

    TAK::Engine::Core::ProjectionPtr2 proj = TAK::Engine::Core::ProjectionFactory2_getProjection(srid);
    if(!proj)
        return false;

    if (projection_ == proj
            || (projection_ && projection_->getSpatialReferenceID() ==
                                      proj->getSpatialReferenceID())) {
        return false;
    }

    // delete the old projection and update our reference
    this->projection_ = std::move(proj);

    dispatchMapProjectionChanged();
    return true;
}

void AtakMapView::addMapProjectionChangedListener(MapProjectionChangedListener *listener)
{
    Lock lock(map_mutex_);
    projection_changed_listeners_.insert(listener);
}

void AtakMapView::removeMapProjectionChangedListener(MapProjectionChangedListener *listener)
{
    Lock lock(map_mutex_);
    projection_changed_listeners_.erase(listener);
}

double AtakMapView::mapResolutionAsMapScale(const double resolution) const
{
    return AtakMapView_getMapScale(display_dpi_, resolution);
}

MapSceneModel *AtakMapView::createSceneModel()
{
    Lock lock(map_mutex_);
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
    return projection_->getMinLatitude();
}

double AtakMapView::getMaxLatitude() const
{
    return projection_->getMaxLatitude();
}

double AtakMapView::getMinLongitude() const
{
    return projection_->getMinLongitude();
}

double AtakMapView::getMaxLongitude() const
{
    return projection_->getMaxLongitude();
}

double AtakMapView::getMinMapScale() const
{
    return min_map_scale_;
}

double AtakMapView::getMaxMapScale() const
{
    return max_map_scale_;
}

void AtakMapView::setMinMapScale(const double scale)
{
    min_map_scale_ = scale;
}

void AtakMapView::setMaxMapScale(const double scale)
{
    max_map_scale_ = scale;
}
void AtakMapView::setSize(const float w, const float h)
{
    Lock lock(map_mutex_);

    width_ = w;
    height_ = h;

    Point<float> focus(w/2 + focus_off_x_, h/2 + focus_off_y_);
    controller_->setDefaultFocusPoint(&focus);

    dispatchMapResized();
}

float AtakMapView::getWidth() const
{
    return width_;
}

float AtakMapView::getHeight() const
{
    return height_;
}

void AtakMapView::addMapResizedListener(MapResizedListener *listener)
{
    Lock lock(map_mutex_);
    resized_listeners_.insert(listener);
}

void AtakMapView::removeMapResizedListener(MapResizedListener *listener)
{
    Lock lock(map_mutex_);
    resized_listeners_.erase(listener);
}

void AtakMapView::setDisplayDpi(double dpi)
{
    display_dpi_ = dpi;
}

double AtakMapView::getDisplayDpi() const
{
    return display_dpi_;
}

void AtakMapView::setFocusPointOffset(float x, float y) NOTHROWS
{
    Point<float> focus;
    controller_->getFocusPoint(&focus);
    focus.x -= focus_off_x_;
    focus.y -= focus_off_y_;
    focus_off_x_ = x;
    focus_off_y_ = y;
    focus.x += focus_off_x_;
    focus.y += focus_off_y_;
    controller_->setDefaultFocusPoint(&focus);
}

bool AtakMapView::isContinuousScrollEnabled() const NOTHROWS
{
    return continue_scroll_enabled_;
}
void AtakMapView::setContinuousScrollEnabled(const bool v) NOTHROWS
{
    LockPtr lock(nullptr, nullptr);
    Lock_create(lock, map_mutex_);
    if (continue_scroll_enabled_ != v) {
        continue_scroll_enabled_ = v;
        dispatchContinuousScrollEnabledChanged();
    }
}
bool AtakMapView::isAnimating() const NOTHROWS
{
    return animate_;
}

double AtakMapView::getElevationExaggerationFactor() const
{
    return elevation_exaggeration_factor_;
}
void AtakMapView::setElevationExaggerationFactor(double factor)
{
    if (isnan(factor) || factor < 0.0)
        return;

    Lock lock(map_mutex_);
    if (elevation_exaggeration_factor_ != factor) {
        elevation_exaggeration_factor_ = factor;
        dispatchElevationExaggerationFactorChanged();
    }
}

void AtakMapView::addMapElevationExaggerationFactorListener(MapElevationExaggerationFactorListener *listener)
{
    Lock lock(map_mutex_);
    el_exaggeration_factor_changed_listeners_.insert(listener);
}

void AtakMapView::removeMapElevationExaggerationFactorListener(MapElevationExaggerationFactorListener *listener)
{
    Lock lock(map_mutex_);
    el_exaggeration_factor_changed_listeners_.erase(listener);
}

void AtakMapView::addMapMovedListener(MapMovedListener *listener)
{
    Lock lock(map_mutex_);
    moved_listeners_.insert(listener);
}

void AtakMapView::removeMapMovedListener(MapMovedListener *listener)
{
    Lock lock(map_mutex_);
    moved_listeners_.erase(listener);
}

void AtakMapView::addMapContinuousScrollListener(MapContinuousScrollListener *l)
{
    LockPtr lock(nullptr, nullptr);
    Lock_create(lock, map_mutex_);
    continuous_scroll_listeners_.insert(l);
}
void AtakMapView::removeMapContinuousScrollListener(MapContinuousScrollListener *l)
{
    LockPtr lock(nullptr, nullptr);
    Lock_create(lock, map_mutex_);
    continuous_scroll_listeners_.erase(l);
}

void AtakMapView::updateView(const GeoPoint *c, double mapScale, double rot, bool anim)
{
    updateView(*c, mapScale, rot, tilt_, focus_altitude_, focus_alt_terminal_slant_, anim);
}

//void AtakMapView::updateView(const GeoPoint &c, const double mapScale, const double rot, const double ptilt, const double focusAlt, const bool anim)
void AtakMapView::updateView(const GeoPoint &c, const double mapScale, const double rot, const double ptilt, const double focusAlt, const double focusAltSlant, const bool anim)
{
    Lock lock(map_mutex_);

    center_.latitude = TAK::Engine::Util::MathUtils_clamp(
            isnan(c.latitude) ? 0.0 : c.latitude,
#ifdef __ANDROID__
            std::max(getMinLatitude(), -90.0+(1.0-scale_)),
            std::min(getMaxLatitude(), 90.0-(1.0-scale_))
#else
            getMinLatitude(),
            getMaxLatitude()
#endif
            );
    center_.longitude = TAK::Engine::Util::MathUtils_clamp(isnan(c.longitude) ? 0.0 : c.longitude, getMinLongitude(), getMaxLongitude());
    scale_ = TAK::Engine::Util::MathUtils_clamp(isnan(mapScale) ? 0.0 : mapScale, getMinMapScale(), getMaxMapScale());

    if (isnan(rot))
        rotation_ = 0.0;
    else if (rot < 0.0)
        rotation_ = fmod((360.0 + rot), 360.0);
    else if (rot >= 360.0)
        rotation_ = fmod(rot, 360.0);
    else
        rotation_ = rot;

    
    if (isnan(ptilt))
    {
        tilt_ = 0.0;
    }
    else
    {   
        const double res = getMapResolution(mapScale);
        const double maxTilt = getMaxMapTilt(res);

        tilt_ = TAK::Engine::Util::MathUtils_clamp(ptilt, 0.0, maxTilt);
    }

    animate_ = anim;
    focus_altitude_ = isnan(focusAlt) ? 0.0 : focusAlt;
    focus_alt_terminal_slant_ = isnan(focusAltSlant) ? 0.0 : focusAltSlant;
    dispatchMapMoved();
}

void AtakMapView::dispatchMapResized()
{
    std::set<MapResizedListener *>::iterator it;
    for(it = resized_listeners_.begin(); it != resized_listeners_.end(); it++)
    {
        (*it)->mapResized(this);
    }
}

void AtakMapView::dispatchMapMoved()
{
    std::set<MapMovedListener *>::iterator it;
    for(it = moved_listeners_.begin(); it != moved_listeners_.end(); it++)
    {
        (*it)->mapMoved(this, animate_);
    }
}

void AtakMapView::dispatchMapProjectionChanged()
{
    std::set<MapProjectionChangedListener *>::iterator it;
    for(it = projection_changed_listeners_.begin(); it != projection_changed_listeners_.end(); it++)
    {
        (*it)->mapProjectionChanged(this);
    }
}

void AtakMapView::dispatchElevationExaggerationFactorChanged()
{
    std::set<MapElevationExaggerationFactorListener *>::iterator it;
    for (it = el_exaggeration_factor_changed_listeners_.begin(); it != el_exaggeration_factor_changed_listeners_.end(); it++)
    {
        (*it)->mapElevationExaggerationFactorChanged(this, this->elevation_exaggeration_factor_);
    }
}

void AtakMapView::dispatchLayerAdded(Layer *layer)
{
    std::set<MapLayersChangedListener *>::iterator it;
    for(it = layers_changed_listeners_.begin(); it != layers_changed_listeners_.end(); it++)
    {
        (*it)->mapLayerAdded(this, layer);
    }
}

void AtakMapView::dispatchLayersRemoved(std::list<Layer *> removed)
{
    std::set<MapLayersChangedListener *>::iterator it;
    std::list<Layer *>::iterator layersIt;
    for(it = layers_changed_listeners_.begin(); it != layers_changed_listeners_.end(); it++)
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
    for(it = layers_changed_listeners_.begin(); it != layers_changed_listeners_.end(); it++)
    {
        (*it)->mapLayerPositionChanged(this, layer, oldPos, newPos);
    }
}

void AtakMapView::dispatchContinuousScrollEnabledChanged()
{
    std::set<MapContinuousScrollListener *>::iterator it;
    for (it = continuous_scroll_listeners_.begin(); it != continuous_scroll_listeners_.end(); it++)
    {
        (*it)->mapContinuousScrollEnabledChanged(this, this->continue_scroll_enabled_);
    }
}

AtakMapView::MapLayersChangedListener::~MapLayersChangedListener() NOTHROWS {}
AtakMapView::MapMovedListener::~MapMovedListener() NOTHROWS {}
AtakMapView::MapProjectionChangedListener::~MapProjectionChangedListener() NOTHROWS {}
AtakMapView::MapResizedListener::~MapResizedListener() NOTHROWS {}
AtakMapView::MapElevationExaggerationFactorListener::~MapElevationExaggerationFactorListener() NOTHROWS {}
AtakMapView::MapContinuousScrollListener::~MapContinuousScrollListener() NOTHROWS {}

double atakmap::core::AtakMapView_getFullEquitorialExtentPixels(const double dpi) NOTHROWS
{
    return WGS84_EQUITORIAL_CIRCUMFERENCE * INCHES_PER_METER * dpi;
}
double atakmap::core::AtakMapView_getMapResolution(const double dpi, const double scale) NOTHROWS
{
    const double displayResolution = ((1.0 / dpi) * (1.0 / INCHES_PER_METER));
    return (displayResolution / scale);
}
double atakmap::core::AtakMapView_getMapScale(const double dpi, const double resolution) NOTHROWS
{
    const double displayResolution = ((1.0 / dpi) * (1.0 / INCHES_PER_METER));
    return (displayResolution / resolution);
}
