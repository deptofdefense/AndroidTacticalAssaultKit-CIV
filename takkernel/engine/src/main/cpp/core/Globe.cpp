#include "core/Globe.h"

#include <cmath>

#include "core/Datum2.h"
#include "core/GeoPoint2.h"
#include "core/MapSceneModel2.h"
#include "math/Matrix2.h"
#include "thread/Lock.h"
#include "util/MathUtils.h"
#include "util/Error.h"

using namespace TAK::Engine::Core;

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

#define INCHES_PER_METER                37.37
#define MIN_MAP_SCALE                   2.5352504279048383e-9
#define MAX_MAP_SCALE                   1.0
#define WGS84_EQUITORIAL_RADIUS         6378137.0
#define WGS84_EQUITORIAL_CIRCUMFERENCE  (2.0*WGS84_EQUITORIAL_RADIUS*M_PI)


namespace
{
    double &defaultDisplayDPI()
    {
        static double dpi = 0.0;
        return dpi;
    }
}

Globe::Globe(const std::size_t w, const std::size_t h, const double displayDPI) NOTHROWS :
    width_(w),
    height_(h),
    focus_x_(0.0),
    focus_y_(0.0),
    display_resolution_((1.0 / displayDPI) * (1.0 / INCHES_PER_METER)),
    full_equitorial_extent_pixels_(WGS84_EQUITORIAL_CIRCUMFERENCE * INCHES_PER_METER * displayDPI),
    center_(0, 0),
    scale_(MIN_MAP_SCALE),
    min_map_scale_(MIN_MAP_SCALE),
    max_map_scale_(MAX_MAP_SCALE),
    rotation_(0.0),
    tilt_(0.0),
    dpi_(displayDPI),
    animate_(false),
    projection_(TAK::Engine::Core::ProjectionSpi2::nullProjectionPtr()),
    map_mutex_(TEMT_Recursive)
{
    setProjection(4326);

    double &defaultDpi = defaultDisplayDPI();
    if (!defaultDpi)
        defaultDpi = dpi_;
}
Globe::~Globe() NOTHROWS
{}

TAKErr Globe::addLayer(const std::shared_ptr<Layer2> &layer) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(nullptr, nullptr);
    code = Lock_create(lock, map_mutex_);
    TE_CHECKRETURN_CODE(code);

    layers_.push_back(layer);
    dispatchLayerAdded(layer);
    return code;
}
TAKErr Globe::insertLayer(const std::size_t idx, const std::shared_ptr<Layer2> &layer) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(nullptr, nullptr);
    code = Lock_create(lock, map_mutex_);

    if (idx >= layers_.size())
        return TE_BadIndex;

    layers_.insert(layers_.begin() + idx, layer);

    dispatchLayerAdded(layer);

    return code;
}
TAKErr Globe::removeLayer(const Layer2 &layer) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(nullptr, nullptr);
    code = Lock_create(lock, map_mutex_);
    TE_CHECKRETURN_CODE(code);

    code = TE_InvalidArg;
    std::list<std::shared_ptr<Layer2>> removed;
    for(std::size_t i = 0u; i < this->layers_.size(); i++) {
        if (this->layers_[i].get() == &layer) {
            removed.push_back(this->layers_[i]);
            layers_.erase(layers_.begin()+i);
            code = TE_Ok;
            break;
        }
    }
    TE_CHECKRETURN_CODE(code);

    if (!removed.empty())
        dispatchLayersRemoved(removed);

    return code;
}
TAKErr Globe::removeAllLayers() NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(nullptr, nullptr);
    code = Lock_create(lock, map_mutex_);
    TE_CHECKRETURN_CODE(code);

    std::list<std::shared_ptr<Layer2>> removed;
    for (std::size_t i = 0u; i < layers_.size(); i++)
        removed.push_back(layers_[i]);
    layers_.clear();
    if (!removed.empty())
        dispatchLayersRemoved(removed);
    return code;
}
TAKErr Globe::setLayerPosition(const Layer2 &layer, const std::size_t position) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(nullptr, nullptr);
    code = Lock_create(lock, map_mutex_);
    TE_CHECKRETURN_CODE(code);

    if (position >= this->layers_.size())
        return TE_BadIndex;

    std::size_t oldPos = this->layers_.size();
    for (std::size_t i = 0; i < this->layers_.size(); i++) {
        if (this->layers_[i].get() == &layer) {
            oldPos = i;
            break;
        }
    }
    // the specified layer is not tracked
    if (oldPos == this->layers_.size())
        return TE_InvalidArg;

    // no-op
    if(position == oldPos)
        return code;

    std::shared_ptr<Layer2> layerPtr(this->layers_[oldPos]);

    this->layers_.erase(this->layers_.begin()+oldPos);
    if(position > oldPos) {
        this->layers_.insert(this->layers_.begin() + (position-1), layerPtr);
    } else if(position < oldPos) {
        this->layers_.insert(this->layers_.begin() + position, layerPtr);
    } else {
        return TE_IllegalState;
    }
        
    this->dispatchLayerPositionChanged(layerPtr, oldPos, position);
    return code;
}
std::size_t Globe::getNumLayers() const NOTHROWS
{
    LockPtr lock(nullptr, nullptr);
    Lock_create(lock, map_mutex_);
    return layers_.size();
}
TAKErr Globe::getLayer(std::shared_ptr<Layer2> &value, const std::size_t position) const NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(nullptr, nullptr);
    code = Lock_create(lock, map_mutex_);
    TE_CHECKRETURN_CODE(code);

    if (position >= layers_.size())
        return TE_BadIndex;

    value = this->layers_[position];
    return code;
}
TAKErr Globe::getLayers(TAK::Engine::Port::Collection<std::shared_ptr<Layer2>> &retLayers) const NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(nullptr, nullptr);
    code = Lock_create(lock, map_mutex_);
    TE_CHECKRETURN_CODE(code);

    for (std::size_t i = 0u; i < this->layers_.size(); i++) {
        code = retLayers.add(this->layers_[i]);
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}
TAKErr Globe::addLayersChangedListener(LayersChangedListener &listener) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(nullptr, nullptr);
    code = Lock_create(lock, map_mutex_);
    TE_CHECKRETURN_CODE(code);
    layers_changed_listeners_.insert(&listener);
    return code;
}
TAKErr Globe::removeLayersChangedListener(LayersChangedListener &listener) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(nullptr, nullptr);
    code = Lock_create(lock, map_mutex_);
    TE_CHECKRETURN_CODE(code);
    layers_changed_listeners_.erase(&listener);
    return code;
}
GeoPoint2 Globe::getPoint() const NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(nullptr, nullptr);
    code = Lock_create(lock, map_mutex_);

    return center_;
}
double Globe::getMapScale() const NOTHROWS
{
    return scale_;
}
double Globe::getMapRotation() const NOTHROWS
{
    return rotation_;
}
double Globe::getMapTilt() const NOTHROWS
{
    return tilt_;
}
double Globe::getMapResolution() const NOTHROWS
{
    return getMapResolution(scale_);
}
double Globe::getMapResolution(const double s) const NOTHROWS
{
    return (display_resolution_ / s);
}
double Globe::getFullEquitorialExtentPixels() const NOTHROWS
{
    return full_equitorial_extent_pixels_;
}
int Globe::getProjection() const NOTHROWS
{
    LockPtr lock(nullptr, nullptr);
    Lock_create(lock, map_mutex_);

    // projection may never be NULL here
    return projection_->getSpatialReferenceID();
}
TAKErr Globe::setProjection(const int srid) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(nullptr, nullptr);
    code = Lock_create(lock, map_mutex_);
    TE_CHECKRETURN_CODE(code);

    ProjectionPtr2 proj = TAK::Engine::Core::ProjectionFactory2_getProjection(srid);
    if (!proj)
        return TE_InvalidArg;

    if (projection_ == proj
        || (projection_ && projection_->getSpatialReferenceID() ==
            proj->getSpatialReferenceID())) {

        return code;
    }

    // delete the old projection and update our reference
    this->projection_ = std::move(proj);

    dispatchMapProjectionChanged(srid);
    return code;
}
TAKErr Globe::addMapProjectionChangedListener(ProjectionChangedListener &listener) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(nullptr, nullptr);
    code = Lock_create(lock, map_mutex_);
    TE_CHECKRETURN_CODE(code);
    projection_changed_listeners_.insert(&listener);
    return code;
}
TAKErr Globe::removeMapProjectionChangedListener(ProjectionChangedListener &listener) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(nullptr, nullptr);
    code = Lock_create(lock, map_mutex_);
    TE_CHECKRETURN_CODE(code);
    projection_changed_listeners_.erase(&listener);
    return code;
}

double Globe::mapResolutionAsMapScale(const double resolution) const NOTHROWS
{
    return (display_resolution_ / resolution);
}

TAKErr Globe::createSceneModel(MapSceneModel2Ptr &value) const NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(nullptr, nullptr);
    code = Lock_create(lock, map_mutex_);
    TE_CHECKRETURN_CODE(code);

    value = MapSceneModel2Ptr(new MapSceneModel2(this->dpi_,
                                                 this->width_, this->height_,
                                                 this->projection_->getSpatialReferenceID(),
                                                 this->center_,
                                                 this->focus_x_, this->focus_y_,
                                                 this->rotation_,
                                                 this->tilt_,
                                                 this->display_resolution_),
                              Memory_deleter_const<MapSceneModel2>);

    return code;
}

TAKErr Globe::forward(Point2<float> *p, const GeoPoint2 &geo) const NOTHROWS
{
    TAKErr code(TE_Ok);
    MapSceneModel2Ptr sm(nullptr, nullptr);
    code = createSceneModel(sm);
    TE_CHECKRETURN_CODE(code);

    return sm->forward(p, geo);
}

TAKErr Globe::inverse(GeoPoint2 *geo, const Point2<float> &p) const NOTHROWS
{
    TAKErr code(TE_Ok);
    MapSceneModel2Ptr sm(nullptr, nullptr);
    code = createSceneModel(sm);
    TE_CHECKRETURN_CODE(code);

    return sm->inverse(geo, p);
}

double Globe::getMinLatitude() const NOTHROWS
{
    return projection_->getMinLatitude();
}

double Globe::getMaxLatitude() const NOTHROWS
{
    return projection_->getMaxLatitude();
}

double Globe::getMinLongitude() const NOTHROWS
{
    return projection_->getMinLongitude();
}

double Globe::getMaxLongitude() const NOTHROWS
{
    return projection_->getMaxLongitude();
}

double Globe::getMinMapScale() const NOTHROWS
{
    return min_map_scale_;
}

double Globe::getMaxMapScale() const NOTHROWS
{
    return max_map_scale_;
}

TAKErr Globe::setMinMapScale(const double scale) NOTHROWS
{
    min_map_scale_ = scale;
    return TE_Ok;
}

TAKErr Globe::setMaxMapScale(const double scale) NOTHROWS
{
    max_map_scale_ = scale;
    return TE_Ok;
}
TAKErr Globe::setSize(const std::size_t w, const std::size_t h) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(nullptr, nullptr);
    code = Lock_create(lock, map_mutex_);

    if (!w || !h)
        return TE_InvalidArg;

    width_ = w;
    height_ = h;

    Point2<float> focus((float)w / 2.f, (float)h / 2.f);
    focus_x_ = (float)w / 2.f;
    focus_y_ = (float)h / 2.f;

    dispatchMapResized(w, h);
    return code;
}

std::size_t Globe::getWidth() const NOTHROWS
{
    return width_;
}

std::size_t Globe::getHeight() const NOTHROWS
{
    return height_;
}

TAKErr Globe::addViewResizedListener(ViewResizedListener &listener) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(nullptr, nullptr);
    code = Lock_create(lock, map_mutex_);
    TE_CHECKRETURN_CODE(code);
    resized_listeners_.insert(&listener);
    return code;
}

TAKErr Globe::removeViewResizedListener(ViewResizedListener &listener) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(nullptr, nullptr);
    code = Lock_create(lock, map_mutex_);
    TE_CHECKRETURN_CODE(code);
    resized_listeners_.erase(&listener);
    return code;
}

TAKErr Globe::addViewChangedListener(ViewChangedListener &listener) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(nullptr, nullptr);
    code = Lock_create(lock, map_mutex_);
    TE_CHECKRETURN_CODE(code);
    moved_listeners_.insert(&listener);
    return code;
}

TAKErr Globe::removeViewChangedListener(ViewChangedListener &listener) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(nullptr, nullptr);
    code = Lock_create(lock, map_mutex_);
    TE_CHECKRETURN_CODE(code);
    moved_listeners_.erase(&listener);
    return code;
}

double Globe::getDisplayDPI() const NOTHROWS
{
    return this->dpi_;
}

void Globe::updateView(const GeoPoint2 &c, double mapScale, double rot, double ptilt, bool anim) NOTHROWS
{
    LockPtr lock(nullptr, nullptr);
    Lock_create(lock, map_mutex_);

    /*if (!(TE_ISNAN(c->latitude) || TE_ISNAN(c->longitude))) {
    center.latitude = c->latitude;
    center.longitude = c->longitude;
    }*/
    center_.latitude = TAK::Engine::Util::MathUtils_clamp(TE_ISNAN(c.latitude) ? 0.0 : c.latitude, getMinLatitude(), getMaxLatitude());
    center_.longitude = TAK::Engine::Util::MathUtils_clamp(TE_ISNAN(c.longitude) ? 0.0 : c.longitude, getMinLongitude(), getMaxLongitude());
    scale_ = TAK::Engine::Util::MathUtils_clamp(TE_ISNAN(mapScale) ? 0.0 : mapScale, getMinMapScale(), getMaxMapScale());
    /*if (!TE_ISNAN(mapScale))
    scale = mapScale;*/
    //if (!TE_ISNAN(rotation))
    //    rotation = fmod(rot, 360.0);
    if (TE_ISNAN(rot))
    {
        rot = 0.0;
    }
    else if (rot < 0.0)
    {
        rot = fmod((360.0 + rot), 360.0);
    }
    else if (rot >= 360.0)
    {
        rot = fmod(rot, 360.0);
    }
    rotation_ = rot;

    if (TE_ISNAN(ptilt))
    {
        ptilt = 0.0;
    }
    else
    {
        if (mapScale == max_map_scale_)
            ptilt = TAK::Engine::Util::MathUtils_clamp(ptilt, 0.0, 85.0);
        else
            ptilt = TAK::Engine::Util::MathUtils_clamp(ptilt, 0.0, 80.0);
    }
    animate_ = anim;
    tilt_ = ptilt;
    dispatchMapMoved(anim);
}

void Globe::dispatchMapResized(const std::size_t newWidth, const std::size_t newHeight) const NOTHROWS
{
    std::set<ViewResizedListener *>::iterator it;
    for (it = resized_listeners_.begin(); it != resized_listeners_.end(); it++)
    {
        (*it)->viewResized(*this, newWidth, newHeight);
    }
}

void Globe::dispatchMapMoved(const bool anim) const NOTHROWS
{
    std::set<ViewChangedListener *>::iterator it;
    for (it = moved_listeners_.begin(); it != moved_listeners_.end(); it++)
    {
        (*it)->viewChanged(*this, anim);
    }
}

void Globe::dispatchMapProjectionChanged(const int srid) const NOTHROWS
{
    std::set<ProjectionChangedListener *>::iterator it;
    for (it = projection_changed_listeners_.begin(); it != projection_changed_listeners_.end(); it++)
    {
        (*it)->projectionChanged(*this, srid);
    }
}

void Globe::dispatchLayerAdded(const std::shared_ptr<Layer2> &layer) const NOTHROWS
{
    std::set<LayersChangedListener *>::iterator it;
    for (it = layers_changed_listeners_.begin(); it != layers_changed_listeners_.end(); it++)
    {
        (*it)->layerAdded(*this, layer);
    }
}

void Globe::dispatchLayersRemoved(const std::list<std::shared_ptr<Layer2>> &removed) const NOTHROWS
{
    std::set<LayersChangedListener *>::iterator it;
    std::list<std::shared_ptr<Layer2>>::const_iterator layersIt;
    for (it = layers_changed_listeners_.begin(); it != layers_changed_listeners_.end(); it++)
    {
        for (layersIt = removed.begin(); layersIt != removed.end(); layersIt++)
        {
            (*it)->layerRemoved(*this, *layersIt);
        }
    }
}

void Globe::dispatchLayerPositionChanged(const std::shared_ptr<Layer2> &layer, const std::size_t oldPos, const std::size_t newPos) const NOTHROWS
{
    std::set<LayersChangedListener *>::iterator it;
    for (it = layers_changed_listeners_.begin(); it != layers_changed_listeners_.end(); it++)
    {
        (*it)->layerPositionChanged(*this, layer, oldPos, newPos);
    }
}

Globe::LayersChangedListener::~LayersChangedListener()
{}

Globe::ViewChangedListener::~ViewChangedListener()
{}

Globe::ProjectionChangedListener::~ProjectionChangedListener()
{}

Globe::ViewResizedListener::~ViewResizedListener()
{}

Globe::FocusPointListener::~FocusPointListener()
{}

double TAK::Engine::Core::Globe_getDefaultDisplayDPI()
{
    return defaultDisplayDPI();
}
