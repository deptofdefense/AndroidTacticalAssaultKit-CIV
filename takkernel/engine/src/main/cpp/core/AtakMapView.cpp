#include "core/AtakMapView.h"

#include<algorithm>
#include <cmath>

#include "core/AtakMapController.h"
#include "core/Datum.h"
#include "core/GeoPoint.h"
#include "core/GeoPoint2.h"
#include "core/MapRenderer2.h"
#include "core/MapSceneModel.h"
#include "core/ProjectionFactory2.h"
#include "math/Matrix.h"
#include "math/Point2.h"
#include "math/Vector4.h"
#include "raster/osm/OSMUtils.h"
#include "renderer/core/GLGlobe.h"
#include "thread/Lock.h"
#include "thread/Mutex.h"
#include "util/Error.h"
#include "util/MathUtils.h"

using namespace atakmap::core;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::math;

#define INCHES_PER_METER                37.37
#define MIN_MAP_SCALE                   2.5352504279048383e-9
#define MAX_MAP_SCALE                   1.0
#define WGS84_EQUITORIAL_RADIUS         6378137.0
#define WGS84_EQUITORIAL_CIRCUMFERENCE  (2.0*WGS84_EQUITORIAL_RADIUS*M_PI)

namespace
{
    class StateRenderer :
        public MapRenderer3,
        public RenderContext,
        public RenderSurface
    {
    public :
        StateRenderer(const float width, const float height, const double dpi, const double gsd) NOTHROWS;
    public : // MapRenderer
        TAKErr registerControl(const Layer2 &layer, const char *type, void *ctrl) NOTHROWS override;
        TAKErr unregisterControl(const Layer2 &layer, const char *type, void *ctrl) NOTHROWS override;
        TAKErr visitControls(bool *visited, void *opaque, TAKErr(*visitor)(void *opaque, const Layer2 &layer, const Control &ctrl), const Layer2 &layer, const char *type) NOTHROWS override;
        TAKErr visitControls(bool *visited, void *opaque, TAKErr(*visitor)(void *opaque, const Layer2 &layer, const Control &ctrl), const Layer2 &layer) NOTHROWS override;
        TAKErr visitControls(void *opaque, TAKErr(*visitor)(void *opaque, const Layer2 &layer, const Control &ctrl)) NOTHROWS override;
        TAKErr addOnControlsChangedListener(OnControlsChangedListener *l) NOTHROWS override;
        TAKErr removeOnControlsChangedListener(OnControlsChangedListener *l) NOTHROWS override;
        RenderContext &getRenderContext() const NOTHROWS override;
    public : // MapRenderer2
        TAKErr lookAt(const GeoPoint2 &from, const GeoPoint2 &at, const MapRenderer::CameraCollision collision, const bool animate) NOTHROWS override;
        TAKErr lookAt(const GeoPoint2 &at, const double resolution, const double azimuth, const double tilt, const MapRenderer::CameraCollision collision, const bool animate) NOTHROWS override;
        TAKErr lookFrom(const GeoPoint2 &from, const double azimuth, const double elevation, const MapRenderer::CameraCollision collision, const bool animate) NOTHROWS override;
        bool isAnimating() const NOTHROWS override;
        TAKErr setDisplayMode(const TAK::Engine::Core::MapRenderer::DisplayMode mode) NOTHROWS override;
        MapRenderer::DisplayMode getDisplayMode() const NOTHROWS override;
        MapRenderer::DisplayOrigin getDisplayOrigin() const NOTHROWS override;
        TAKErr setFocusPoint(const float focusx, const float focusy) NOTHROWS override;
        TAKErr getFocusPoint(float *focusx, float *focusy) const NOTHROWS override;
        TAKErr setSurfaceSize(const std::size_t width, const std::size_t height) NOTHROWS override;
        TAKErr inverse(MapRenderer::InverseResult *result, GeoPoint2 *value, const MapRenderer::InverseMode mode, const unsigned int hints, const Point2<double> &screen, const MapRenderer::DisplayOrigin) NOTHROWS override;
        TAKErr getMapSceneModel(MapSceneModel2 *value, const bool instant, const DisplayOrigin origin) const NOTHROWS override;
        TAKErr addOnCameraChangedListener(OnCameraChangedListener *l) NOTHROWS override;
        TAKErr removeOnCameraChangedListener(OnCameraChangedListener *l) NOTHROWS override;
        TAKErr addOnCameraChangedListener(OnCameraChangedListener2 *l) NOTHROWS override;
        TAKErr removeOnCameraChangedListener(OnCameraChangedListener2 *l) NOTHROWS override;
    public : // RenderContext
        bool isRenderThread() const NOTHROWS override;
        TAKErr queueEvent(void(*runnable)(void *) NOTHROWS, std::unique_ptr<void, void(*)(const void *)> &&opaque) NOTHROWS override;
        void requestRefresh() NOTHROWS override;
        TAKErr setFrameRate(const float rate) NOTHROWS override;
        float getFrameRate() const NOTHROWS override;
        void setContinuousRenderEnabled(const bool enabled) NOTHROWS override;
        bool isContinuousRenderEnabled() NOTHROWS override;
        bool supportsChildContext() const NOTHROWS override;
        TAKErr createChildContext(std::unique_ptr<RenderContext, void(*)(const RenderContext *)> &value) NOTHROWS override;
        bool isAttached() const NOTHROWS override;
        bool attach() NOTHROWS override;
        bool detach() NOTHROWS override;
        bool isMainContext() const NOTHROWS override;
        RenderSurface *getRenderSurface() const NOTHROWS override;
    public : // RenderSurface
        double getDpi() const NOTHROWS override;
        void setDpi(double dpi) NOTHROWS;
        std::size_t getWidth() const NOTHROWS override;
        std::size_t getHeight() const NOTHROWS override;
        void addOnSizeChangedListener(OnSizeChangedListener *l) NOTHROWS override;
        void removeOnSizeChangedListener(const OnSizeChangedListener &l) NOTHROWS override;
    public :
        bool suppressCallbacks;
    private :
        mutable Mutex mutex_;
        MapSceneModel2 state_;
        float focus_off_x_;
        float focus_off_y_;
        std::set<OnCameraChangedListener*> camera_listeners_;
        std::set<OnCameraChangedListener2*> camera_listeners2_;

    };
}

float AtakMapView::DENSITY = 1.5f;

AtakMapView::AtakMapView(float w, float h, double display_dpi) :
    state_(new StateRenderer(w, h, display_dpi, AtakMapView_getMapResolution(display_dpi, MIN_MAP_SCALE))),
    full_equitorial_extent_pixels_(AtakMapView_getFullEquitorialExtentPixels(display_dpi)),
    min_map_scale_(MIN_MAP_SCALE),
    max_map_scale_(MAX_MAP_SCALE),
    max_tilt_(84.0),
    controller_(nullptr)
{
    controller_ = new AtakMapController(this);

    static bool densitySet = false;
    if (!densitySet) {
        DENSITY = static_cast<float>(display_dpi / 160.0f);
        densitySet = true;
    }

    state_->addOnCameraChangedListener((MapRenderer3::OnCameraChangedListener2*)this);
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
    Lock lock(layers_.mutex);
    layers_.value.push_back(layer);
    dispatchLayerAddedNoSync(layer);
}

void AtakMapView::addLayer(int idx, Layer *layer)
{
    Lock lock(layers_.mutex);

    if(idx < 0 || (unsigned int)idx >= layers_.value.size())
        throw 3;

    auto it = layers_.value.begin();
    std::advance(it, idx);
    layers_.value.insert(it, layer);

    dispatchLayerAddedNoSync(layer);
}

void AtakMapView::removeLayer(Layer *layer)
{
    Lock lock(layers_.mutex);

    std::list<Layer *> removed;
    for(auto it = layers_.value.begin(); it != layers_.value.end(); it++) {
        if(*it == layer) {
            layers_.value.erase(it);
            removed.push_back(layer);
            break;
        }
    }
    if(!removed.empty())
        dispatchLayersRemovedNoSync(removed);
}

void AtakMapView::removeAllLayers()
{
    Lock lock(layers_.mutex);

    std::list<Layer *> removed;
    for(auto it = layers_.value.begin(); it != layers_.value.end(); it++)
        removed.push_back(*it);
    layers_.value.clear();
    if(!removed.empty())
        dispatchLayersRemovedNoSync(removed);
}

void AtakMapView::setLayerPosition(Layer *layer, const int position)
{
    Lock lock(layers_.mutex);

    std::list<Layer *>::iterator it = layers_.value.end();
    int oldPos = 0;
    for(it = layers_.value.begin(); it != layers_.value.end(); it++) {
        if(*it == layer) {
            break;
        }
        oldPos++;
    }
    // layer was not found or the layer won't move, return
    if(it == layers_.value.end() || oldPos == position)
        return;
    // delete the layer
    layers_.value.erase(it);
    // re-insert the layer
    it = layers_.value.begin();
    std::advance(it, position);
    layers_.value.insert(it, layer);

    for(auto lit : layers_.listeners)
    {
        lit->mapLayerPositionChanged(this, layer, oldPos, position);
    }
}

std::size_t AtakMapView::getNumLayers()
{
    Lock lock(layers_.mutex);
    return layers_.value.size();
}

Layer *AtakMapView::getLayer(std::size_t position)
{
    Lock lock(layers_.mutex);

    if(position >= layers_.value.size())
        return nullptr; // XXX - throw exception

    auto it = layers_.value.begin();
    std::advance(it, position);
    return *it;
}

std::list<Layer *> AtakMapView::getLayers()
{
    Lock lock(layers_.mutex);
    return layers_.value;
}

void AtakMapView::getLayers(std::list<Layer *> &retval)
{
    Lock lock(layers_.mutex);
    retval.insert(retval.end(), layers_.value.begin(), layers_.value.end());
}

void AtakMapView::addLayersChangedListener(MapLayersChangedListener *listener)
{
    Lock lock(layers_.mutex);
    layers_.listeners.insert(listener);
}

void AtakMapView::removeLayersChangedListener(MapLayersChangedListener *listener)
{
    Lock lock(layers_.mutex);
    layers_.listeners.erase(listener);
}

void AtakMapView::getPoint(GeoPoint *c) const
{
    getPoint(c, false);
}

void AtakMapView::getPoint(GeoPoint *c, const bool atFocusAlt) const
{
    TAKErr code(TE_Ok);
    MapSceneModel2 scene;
    {
        ReadLock lock(map_mutex_);
        auto &current = state();
        current.getMapSceneModel(&scene, false, MapRenderer::UpperLeft);
    }

    GeoPoint2 c2;
    scene.projection->inverse(&c2, scene.camera.target);
    *c = GeoPoint(c2);
}

double AtakMapView::getFocusAltitude() const
{
    GeoPoint focus;
    getPoint(&focus);
    return TE_ISNAN(focus.altitude) ? 0.0 : focus.altitude;
}

void AtakMapView::getBounds(GeoPoint *upperLeft, GeoPoint *lowerRight)
{
    ReadLock lock(map_mutex_);
    auto &current = state();
    MapSceneModel2 sm;
    current.getMapSceneModel(&sm, false, MapRenderer::UpperLeft);

    GeoPoint2 ul;
    sm.inverse(&ul, Point2<float>(0.f, 0.f), true);
    *upperLeft = GeoPoint(ul);

    GeoPoint2 lr;
    sm.inverse(&lr, Point2<float>((float)sm.width, (float)sm.height), true);
    *lowerRight = GeoPoint(lr);
}

double AtakMapView::getMapScale() const
{
    ReadLock lock(map_mutex_);
    auto &current = state();
    MapSceneModel2 sm;
    current.getMapSceneModel(&sm, false, MapRenderer::UpperLeft);
    return AtakMapView_getMapScale(sm.displayDpi, sm.gsd);
}

double AtakMapView::getMapRotation() const
{
    ReadLock lock(map_mutex_);
    auto &current = state();
    MapSceneModel2 sm;
    current.getMapSceneModel(&sm, false, MapRenderer::UpperLeft);
    return sm.camera.azimuth;
}

double AtakMapView::getMapTilt() const
{
    ReadLock lock(map_mutex_);
    auto &current = state();
    MapSceneModel2 sm;
    current.getMapSceneModel(&sm, false, MapRenderer::UpperLeft);
    return 90.0 + sm.camera.elevation;
}

double AtakMapView::getMinMapTilt(double resolution) const
{
    return 0.0;
}
double AtakMapView::getMaxMapTilt(double resolution) const
{
    if(MapSceneModel2_getCameraMode() == MapCamera2::Scale) {
        const double zoomLevel = log(156543.034*cos(0.0) / resolution) / log(2.0);
        double maxTilt;
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

        return std::min(maxTilt, this->max_tilt_);
    } else {
        return this->max_tilt_;
    }
}
void AtakMapView::setMaxMapTilt(double value)
{
    max_tilt_ = std::max(value, 0.0);
}
double AtakMapView::getMapResolution() const
{
    ReadLock lock(map_mutex_);
    auto &current = state();
    MapSceneModel2 sm;
    current.getMapSceneModel(&sm, false, MapRenderer::UpperLeft);
    return sm.gsd;
}

double AtakMapView::getMapResolution(const double s) const
{
    ReadLock lock(map_mutex_);
    auto &current = state();
    MapSceneModel2 sm;
    current.getMapSceneModel(&sm, false, MapRenderer::UpperLeft);
    return AtakMapView_getMapResolution(sm.displayDpi, s);
}

double AtakMapView::getFullEquitorialExtentPixels() const
{
    return full_equitorial_extent_pixels_;
}

int AtakMapView::getProjection() const
{
    ReadLock lock(map_mutex_);

    // projection may never be NULL here
    auto &current = state();
    MapSceneModel2 sm;
    current.getMapSceneModel(&sm, false, MapRenderer::UpperLeft);
    return sm.projection->getSpatialReferenceID();
}

bool AtakMapView::setProjection(const int srid)
{
    ReadLock lock(map_mutex_);

    auto &current = state();

    MapRenderer::DisplayMode mode;
    switch (srid) {
    case 4326 :
        mode = MapRenderer::Flat;
        break;
    case 4978 :
        mode = MapRenderer::Globe;
        break;
    default :
        return false;
    }

    state_->setDisplayMode(mode);
    for (auto it = renderers_.begin(); it != renderers_.end(); it++)
        (*it)->setDisplayMode(mode);

    // dispatch projection changed
    {
        ReadLock cblock(projection_changed_listeners_.mutex);
        for (auto it : projection_changed_listeners_.value)
        {
            it->mapProjectionChanged(this);
        }
    }

    return true;
}

void AtakMapView::addMapProjectionChangedListener(MapProjectionChangedListener *listener)
{
    WriteLock lock(projection_changed_listeners_.mutex);
    projection_changed_listeners_.value.insert(listener);
}

void AtakMapView::removeMapProjectionChangedListener(MapProjectionChangedListener *listener)
{
    WriteLock lock(projection_changed_listeners_.mutex);
    projection_changed_listeners_.value.erase(listener);
}

double AtakMapView::mapResolutionAsMapScale(const double resolution) const
{
    ReadLock lock(map_mutex_);
    auto &current = state();
    MapSceneModel2 sm;
    current.getMapSceneModel(&sm, false, MapRenderer::UpperLeft);
    return AtakMapView_getMapScale(sm.displayDpi, resolution);
}

MapSceneModel *AtakMapView::createSceneModel()
{
    ReadLock lock(map_mutex_);
    auto &current = state();
    MapSceneModel2 sm;
    current.getMapSceneModel(&sm, false, MapRenderer::UpperLeft);
    return new MapSceneModel(sm);
}

void AtakMapView::forward(const GeoPoint *geo, Point<float> *p)
{
    MapSceneModel2 sm;
    {
        ReadLock lock(map_mutex_);
        auto &current = state();
        current.getMapSceneModel(&sm, false, MapRenderer::UpperLeft);
    }
    GeoPoint2 geo2;
    GeoPoint_adapt(&geo2, *geo);
    Point2<float> p2;
    sm.forward(&p2, geo2);
    p->x = p2.x;
    p->y = p2.y;
    p->z = p2.z;
}

void AtakMapView::inverse(const Point<float> *p, GeoPoint *geo)
{
    MapSceneModel2 sm;
    {
        ReadLock lock(map_mutex_);
        auto &current = state();
        current.getMapSceneModel(&sm, false, MapRenderer::UpperLeft);
    }
    GeoPoint2 geo2;
    if(sm.inverse(&geo2, Point2<float>(p->x, p->y, p->z)) != TE_Ok) {
        geo->latitude = NAN;
        geo->longitude = NAN;
        geo->altitude = NAN;
    } else {
        *geo = GeoPoint(geo2);
    }
}

double AtakMapView::getMinLatitude() const
{
    MapSceneModel2 sm;
    {
        ReadLock lock(map_mutex_);
        auto &current = state();
        current.getMapSceneModel(&sm, false, MapRenderer::UpperLeft);
    }
    return sm.projection->getMinLatitude();
}

double AtakMapView::getMaxLatitude() const
{
    MapSceneModel2 sm;
    {
        ReadLock lock(map_mutex_);
        auto &current = state();
        current.getMapSceneModel(&sm, false, MapRenderer::UpperLeft);
    }
    return sm.projection->getMaxLatitude();
}

double AtakMapView::getMinLongitude() const
{
    MapSceneModel2 sm;
    {
        ReadLock lock(map_mutex_);
        auto &current = state();
        current.getMapSceneModel(&sm, false, MapRenderer::UpperLeft);
    }
    return sm.projection->getMinLongitude();
}

double AtakMapView::getMaxLongitude() const
{
    MapSceneModel2 sm;
    {
        ReadLock lock(map_mutex_);
        auto &current = state();
        current.getMapSceneModel(&sm, false, MapRenderer::UpperLeft);
    }
    return sm.projection->getMaxLongitude();
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
    if (TE_ISNAN(w) || TE_ISNAN(h))
        return;
    if (w <= 0.f || h <= 0.f)
        return;

    ReadLock lock(map_mutex_);
    auto current = static_cast<StateRenderer*>(state_);
    MapSceneModel2 sm;
    current->getMapSceneModel(&sm, false, MapRenderer2::UpperLeft);
    if (sm.width == w && sm.height == h)
        return;

    current->setSurfaceSize((std::size_t)w, (std::size_t)h);

    for(auto it : resized_listeners_)
    {
        it->mapResized(this);
    }
}

float AtakMapView::getWidth() const
{
    ReadLock lock(map_mutex_);
    //auto &current = state();
    auto &current = *static_cast<StateRenderer*>(state_);
    MapSceneModel2 sm;
    current.getMapSceneModel(&sm, false, MapRenderer::UpperLeft);
    return (float)sm.width;
}

float AtakMapView::getHeight() const
{
    ReadLock lock(map_mutex_);
    //auto &current = state();
    auto &current = *static_cast<StateRenderer*>(state_);
    MapSceneModel2 sm;
    current.getMapSceneModel(&sm, false, MapRenderer::UpperLeft);
    return (float)sm.height;
}

void AtakMapView::addMapResizedListener(MapResizedListener *listener)
{
    WriteLock lock(map_mutex_);
    resized_listeners_.insert(listener);
}

void AtakMapView::removeMapResizedListener(MapResizedListener *listener)
{
    WriteLock lock(map_mutex_);
    resized_listeners_.erase(listener);
}

void AtakMapView::setDisplayDpi(double dpi)
{
    if (TE_ISNAN(dpi))
        return;
    if (dpi <= 0.0)
        return;

    ReadLock lock(map_mutex_);
    auto current = static_cast<StateRenderer*>(state_);
    current->setDpi(dpi);
}

double AtakMapView::getDisplayDpi() const
{
    ReadLock lock(map_mutex_);
    auto &current = state();
    MapSceneModel2 sm;
    current.getMapSceneModel(&sm, false, MapRenderer::UpperLeft);
    return sm.displayDpi;
}

void AtakMapView::setFocusPointOffset(float x, float y) NOTHROWS
{
    ReadLock lock(map_mutex_);


#define setFocusPointOffsetImpl(r) \
    { \
        float focusx = (float)(r).getRenderContext().getRenderSurface()->getWidth() / 2.f; \
        float focusy = (float)(r).getRenderContext().getRenderSurface()->getHeight() / 2.f; \
        if((r).getDisplayOrigin() == MapRenderer::LowerLeft) \
            focusy -= y; \
        else \
            focusy += y; \
        (r).setFocusPoint(focusx, focusy); \
    }

    setFocusPointOffsetImpl(*state_);
    for (auto it = renderers_.begin(); it != renderers_.end(); it++)
        setFocusPointOffsetImpl(**it);
}

bool AtakMapView::isContinuousScrollEnabled() const NOTHROWS
{
    return true;
}
void AtakMapView::setContinuousScrollEnabled(const bool v) NOTHROWS
{}
bool AtakMapView::isAnimating() const NOTHROWS
{
    ReadLock lock(map_mutex_);
    auto &current = state();
    return current.isAnimating();
}

double AtakMapView::getElevationExaggerationFactor() const
{
    return elevation_exaggeration_factor_.value;
}
void AtakMapView::setElevationExaggerationFactor(double factor)
{
    if (TE_ISNAN(factor) || factor < 0.0)
        return;

    Lock lock(elevation_exaggeration_factor_.mutex);
    if (elevation_exaggeration_factor_.value != factor) {
        elevation_exaggeration_factor_.value = factor;
        for (auto it : elevation_exaggeration_factor_.listeners)
        {
            it->mapElevationExaggerationFactorChanged(this, factor);
        }
    }
}

void AtakMapView::addMapElevationExaggerationFactorListener(MapElevationExaggerationFactorListener *listener)
{
    Lock lock(elevation_exaggeration_factor_.mutex);
    elevation_exaggeration_factor_.listeners.insert(listener);
}

void AtakMapView::removeMapElevationExaggerationFactorListener(MapElevationExaggerationFactorListener *listener)
{
    Lock lock(elevation_exaggeration_factor_.mutex);
    elevation_exaggeration_factor_.listeners.erase(listener);
}

void AtakMapView::addMapMovedListener(MapMovedListener *listener)
{
    WriteLock lock(moved_listeners_.mutex);
    moved_listeners_.value.insert(listener);
}

void AtakMapView::removeMapMovedListener(MapMovedListener *listener)
{
    WriteLock lock(moved_listeners_.mutex);
    moved_listeners_.value.erase(listener);
}

void AtakMapView::addMapContinuousScrollListener(MapContinuousScrollListener *l)
{
    // no-op
}
void AtakMapView::removeMapContinuousScrollListener(MapContinuousScrollListener *l)
{
    // no-op
}

void AtakMapView::updateView(const GeoPoint *c, double mapScale, double rot, bool anim)
{
    double tilt = 0.0;
    {
        ReadLock lock(map_mutex_);
        auto &current = state();
        MapSceneModel2 sm;
        current.getMapSceneModel(&sm, false, MapRenderer::UpperLeft);
        tilt = 90.0 + sm.camera.elevation;
    }
    updateView(*c, mapScale, rot, tilt, 0.0, 0.0, anim);
}

//void AtakMapView::updateView(const GeoPoint &c, const double mapScale, const double rot, const double ptilt, const double focusAlt, const bool anim)
void AtakMapView::updateView(const GeoPoint &c, const double mapScale, const double rot, const double ptilt, const double focusAlt, const double focusAltSlant, const bool anim)
{
    ReadLock lock(map_mutex_);

    GeoPoint2 updateLLA;
    GeoPoint_adapt(&updateLLA, c);
    double updateScale = mapScale;
    double updateRotation = rot;
    double updateTilt = ptilt;

    const double scale = getMapScale();

    updateLLA.latitude = TAK::Engine::Util::MathUtils_clamp(
            TE_ISNAN(updateLLA.latitude) ? 0.0 : updateLLA.latitude,
#ifdef __ANDROID__
            std::max(getMinLatitude(), -90.0+(1.0-scale)),
            std::min(getMaxLatitude(), 90.0-(1.0-scale))
#else
            getMinLatitude(),
            getMaxLatitude()
#endif
            );
    updateLLA.longitude = c.longitude;
    if(updateLLA.longitude < -180.0)
        updateLLA.longitude += 360.0;
    else if(updateLLA.longitude > 180.0)
        updateLLA.longitude -= 360.0;
    updateLLA.altitude = c.altitude;
    if(TE_ISNAN(updateLLA.altitude))
        updateLLA.altitude = 0.0;
    updateScale = TAK::Engine::Util::MathUtils_clamp(TE_ISNAN(mapScale) ? 0.0 : mapScale, getMinMapScale(), getMaxMapScale());

    if (TE_ISNAN(rot))
        updateRotation = 0.0;
    else if (rot < 0.0)
        updateRotation = fmod((360.0 + rot), 360.0);
    else if (rot >= 360.0)
        updateRotation = fmod(rot, 360.0);
    else
        updateRotation = rot;

    
    if (TE_ISNAN(ptilt))
    {
        updateTilt = 0.0;
    }
    else
    {   
        const double res = getMapResolution(mapScale);
        const double maxTilt = getMaxMapTilt(res);

        updateTilt = TAK::Engine::Util::MathUtils_clamp(ptilt, 0.0, maxTilt);
    }

    double updateGsd = getMapResolution(updateScale);

    auto &current = state();
    current.lookAt(updateLLA, updateGsd, updateRotation, updateTilt, MapRenderer::CameraCollision::Ignore, anim);
}
void AtakMapView::attachRenderer(MapRenderer3& renderer, const bool adoptState) NOTHROWS
{
    WriteLock lock(map_mutex_);
    if (adoptState) {
        auto& current = state();
        MapSceneModel2 sm;
        current.getMapSceneModel(&sm, false, MapRenderer::DisplayOrigin::UpperLeft);
        GeoPoint2 focus;
        sm.projection->inverse(&focus, sm.camera.target);

        // focus offset
        float focusOffsetX = sm.focusX - (float)sm.width / 2.f;
        float focusOffsetY;
        if(renderer.getDisplayOrigin() == MapRenderer::LowerLeft)
            focusOffsetY = (float)sm.height / 2.f - sm.focusY;
        else
            focusOffsetY = sm.focusY - (float)sm.height / 2.f;

        MapSceneModel2 sm_renderer;
        renderer.getMapSceneModel(&sm_renderer, false, MapRenderer::DisplayOrigin::UpperLeft);
        renderer.setFocusPoint((float)sm_renderer.width / 2.f + focusOffsetX, (float)sm_renderer.height / 2.f + focusOffsetY);

        renderer.setDisplayMode(current.getDisplayMode());
        renderer.lookAt(focus, sm.gsd, sm.camera.azimuth, 90.0 + sm.camera.elevation, MapRenderer::Ignore, false);
    }
    renderer.addOnCameraChangedListener((MapRenderer3::OnCameraChangedListener2*)this);
    renderers_.push_back(&renderer);
}
void AtakMapView::detachRenderer(const MapRenderer3& renderer) NOTHROWS
{
    WriteLock lock(map_mutex_);
    if (renderers_.empty())
        return;
    const bool writeBack = (&renderer == renderers_.front());
    for(auto it = renderers_.begin(); it != renderers_.end(); it++) {
        if((*it) == &renderer) {
            (*it)->removeOnCameraChangedListener((MapRenderer3::OnCameraChangedListener2*)this);
            renderers_.erase(it);
            break;
        }
    }
    // if all renderers detached, write back state
    if(writeBack) {
        static_cast<StateRenderer *>(state_)->suppressCallbacks = true;
        MapSceneModel2 sm;
        renderer.getMapSceneModel(&sm, false, MapRenderer::UpperLeft);
        GeoPoint2 focus;
        sm.projection->inverse(&focus, sm.camera.target);

        state_->setSurfaceSize(sm.width, sm.height);
        state_->setFocusPoint(sm.focusX, sm.focusY);
        state_->setDisplayMode(renderer.getDisplayMode());
        state_->lookAt(focus, sm.gsd, sm.camera.azimuth, 90.0 + sm.camera.elevation, MapRenderer::Ignore, false);
        static_cast<StateRenderer *>(state_)->suppressCallbacks = false;
    }
}
TAKErr AtakMapView::onCameraChanged(const TAK::Engine::Core::MapRenderer2 &renderer) NOTHROWS
{
    if(!renderer.isAnimating())
        dispatchMapMoved(false);
    return TE_Ok;
}
TAKErr AtakMapView::onCameraChangeRequested(const MapRenderer2 &renderer) NOTHROWS
{
    dispatchMapMoved(renderer.isAnimating());
    return TE_Ok;
}
MapRenderer3 &AtakMapView::state() const NOTHROWS
{
    if (renderers_.empty())
        return *state_;
    else
        return *renderers_.front();
}

void AtakMapView::dispatchMapMoved(const bool animate)
{
    ReadLock lock(moved_listeners_.mutex);
    for(auto it : moved_listeners_.value)
    {
        it->mapMoved(this, animate);
    }
}

void AtakMapView::dispatchLayerAddedNoSync(Layer *layer)
{
    for(auto it : layers_.listeners)
    {
        it->mapLayerAdded(this, layer);
    }
}

void AtakMapView::dispatchLayersRemovedNoSync(std::list<Layer *> removed)
{
    for(auto it : layers_.listeners)
    {
        for(auto layersIt : removed)
        {
            it->mapLayerRemoved(this, layersIt);
        }
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

namespace
{
    StateRenderer::StateRenderer(const float width, const float height, const double dpi, const double gsd) NOTHROWS :
        state_(dpi, (std::size_t)width, (std::size_t)height, 4326, GeoPoint2(0.0, 0.0), width / 2.f, height / 2.f, 0.0, 0.0, gsd),
        focus_off_x_(0.0),
        focus_off_y_(0.0),
        mutex_(TEMT_Recursive),
        suppressCallbacks(false)
    {}
    
    // MapRenderer
    TAKErr StateRenderer::registerControl(const Layer2 &layer, const char *type, void *ctrl) NOTHROWS
    {
        return TE_Unsupported;
    }
    TAKErr StateRenderer::unregisterControl(const Layer2 &layer, const char *type, void *ctrl) NOTHROWS
    {
        return TE_Unsupported;
    }
    TAKErr StateRenderer::visitControls(bool *visited, void *opaque, TAKErr(*visitor)(void *opaque, const Layer2 &layer, const Control &ctrl), const Layer2 &layer, const char *type) NOTHROWS
    {
        return TE_Unsupported;
    }
    TAKErr StateRenderer::visitControls(bool *visited, void *opaque, TAKErr(*visitor)(void *opaque, const Layer2 &layer, const Control &ctrl), const Layer2 &layer) NOTHROWS
    {
        return TE_Unsupported;
    }
    TAKErr StateRenderer::visitControls(void *opaque, TAKErr(*visitor)(void *opaque, const Layer2 &layer, const Control &ctrl)) NOTHROWS
    {
        return TE_Unsupported;
    }
    TAKErr StateRenderer::addOnControlsChangedListener(OnControlsChangedListener *l) NOTHROWS
    {
        return TE_Unsupported;
    }
    TAKErr StateRenderer::removeOnControlsChangedListener(OnControlsChangedListener *l) NOTHROWS
    {
        return TE_Unsupported;
    }
    RenderContext &StateRenderer::getRenderContext() const NOTHROWS
    {
        return const_cast<StateRenderer &>(*this);
    }
    // MapRenderer2
    TAKErr StateRenderer::lookAt(const GeoPoint2& from, const GeoPoint2& at, const MapRenderer::CameraCollision collision, const bool animate) NOTHROWS
    {
        return TE_Unsupported;
    }
    TAKErr StateRenderer::lookAt(const GeoPoint2 &at, const double resolution, const double azimuth, const double tilt, const MapRenderer::CameraCollision collision, const bool animate) NOTHROWS
    {
        if (TE_ISNAN(at.latitude) || TE_ISNAN(at.longitude))
            return TE_InvalidArg;
        if (TE_ISNAN(resolution))
            return TE_InvalidArg;
        if (TE_ISNAN(azimuth))
            return TE_InvalidArg;
        if (TE_ISNAN(tilt))
            return TE_InvalidArg;

        Lock lock(mutex_);
        state_ = MapSceneModel2(
            state_.displayDpi,
            state_.width,
            state_.height,
            state_.projection->getSpatialReferenceID(),
            at,
            state_.focusX,
            state_.focusY,
            azimuth,
            tilt,
            resolution,
            state_.camera.mode);

        if (!suppressCallbacks) {
            for (auto it : camera_listeners2_)
                it->onCameraChangeRequested(*this);
        }

        return TE_Ok;
    }
    TAKErr StateRenderer::lookFrom(const GeoPoint2 &from, const double azimuth, const double elevation, const MapRenderer::CameraCollision collision, const bool animate) NOTHROWS
    {
        return TE_Unsupported;
    }
    bool StateRenderer::isAnimating() const NOTHROWS
    {
        return false;
    }
    TAKErr StateRenderer::setDisplayMode(const TAK::Engine::Core::MapRenderer::DisplayMode mode) NOTHROWS
    {
        int srid;
        switch(mode) {
        case MapRenderer::Flat :
            srid = 4326;
            break;
        case MapRenderer::Globe :
            srid = 4978;
            break;
        default :
            return TE_InvalidArg;
        }
        Lock lock(mutex_);
        if (state_.projection->getSpatialReferenceID() == srid)
            return TE_Ok;

        GeoPoint2 focus;
        state_.projection->inverse(&focus, state_.camera.target);
        state_ = MapSceneModel2(
            state_.displayDpi,
            state_.width,
            state_.height,
            srid,
            focus,
            state_.focusX,
            state_.focusY,
            state_.camera.azimuth,
            90.0 + state_.camera.elevation,
            state_.gsd,
            state_.camera.nearMeters,
            state_.camera.farMeters,
            state_.camera.mode);
        return TE_Ok;
    }
    MapRenderer::DisplayMode StateRenderer::getDisplayMode() const NOTHROWS
    {
        Lock lock(mutex_);
        switch (state_.projection->getSpatialReferenceID()) {
        case 4326:
            return MapRenderer::Flat;
        case 4978 :
            return MapRenderer::Globe;
        default :
            return MapRenderer::Globe;
        }
    }
    MapRenderer::DisplayOrigin StateRenderer::getDisplayOrigin() const NOTHROWS
    {
        return MapRenderer::UpperLeft;
    }
    TAKErr StateRenderer::setFocusPoint(const float focusx, const float focusy) NOTHROWS
    {
        if (TE_ISNAN(focusx) || TE_ISNAN(focusy))
            return TE_InvalidArg;

        focus_off_x_ = focusx - (float)state_.width / 2.f;
        focus_off_y_ = focusx - (float)state_.width / 2.f;

        Lock lock(mutex_);
        GeoPoint2 focus;
        state_.projection->inverse(&focus, state_.camera.target);
        state_ = MapSceneModel2(
            state_.displayDpi,
            state_.width,
            state_.height,
            state_.projection->getSpatialReferenceID(),
            focus,
            focusx,
            focusy,
            state_.camera.azimuth,
            90.0 + state_.camera.elevation,
            state_.gsd,
            state_.camera.nearMeters,
            state_.camera.farMeters,
            state_.camera.mode);
        return TE_Ok;
    }
    TAKErr StateRenderer::getFocusPoint(float *focusx, float *focusy) const NOTHROWS
    {
        Lock lock(mutex_);
        *focusx = state_.focusX;
        *focusy = state_.focusY;
        return TE_Ok;
    }
    TAKErr StateRenderer::setSurfaceSize(const std::size_t width, const std::size_t height) NOTHROWS
    {
        if (width == state_.width && height == state_.height)
            return TE_Ok;

        if (!width || !height)
            return TE_InvalidArg;

        Lock lock(mutex_);
        GeoPoint2 focus;
        state_.projection->inverse(&focus, state_.camera.target);
        state_ = MapSceneModel2(
            state_.displayDpi,
            width,
            height,
            state_.projection->getSpatialReferenceID(),
            focus,
            (float)width / 2.f + focus_off_x_,
            (float)height / 2.f + focus_off_y_,
            state_.camera.azimuth,
            90.0 + state_.camera.elevation,
            state_.gsd,
            state_.camera.nearMeters,
            state_.camera.farMeters,
            state_.camera.mode);
        return TE_Ok;
    }
    TAKErr StateRenderer::inverse(MapRenderer::InverseResult *result, GeoPoint2 *value, const MapRenderer::InverseMode mode, const unsigned int hints, const Point2<double> &screen, const MapRenderer::DisplayOrigin) NOTHROWS
    {
        return TE_Unsupported;
    }
    TAKErr StateRenderer::getMapSceneModel(MapSceneModel2 *value, const bool instant, const MapRenderer::DisplayOrigin origin) const NOTHROWS
    {
        Lock lock(mutex_);
        *value = state_;
        if(origin == MapRenderer::LowerLeft)
            TAK::Engine::Renderer::Core::GLGlobeBase_glScene(*value);
        return TE_Ok;
    }
    TAKErr StateRenderer::addOnCameraChangedListener(OnCameraChangedListener* l) NOTHROWS
    {
        OnCameraChangedListener2* l2 = dynamic_cast<OnCameraChangedListener2*>(l);
        if (l2)
            return addOnCameraChangedListener(l2);
        Lock lock(mutex_);
        camera_listeners_.insert(l);
        return TE_Ok;
    }
    TAKErr StateRenderer::removeOnCameraChangedListener(OnCameraChangedListener *l) NOTHROWS
    {
        OnCameraChangedListener2* l2 = dynamic_cast<OnCameraChangedListener2*>(l);
        if (l2)
            return removeOnCameraChangedListener(l2);
        Lock lock(mutex_);
        camera_listeners_.erase(l);
        return TE_Ok;
    }
    TAKErr StateRenderer::addOnCameraChangedListener(OnCameraChangedListener2* l) NOTHROWS
    {
        Lock lock(mutex_);
        camera_listeners2_.insert(l);
        return TE_Ok;
    }
    TAKErr StateRenderer::removeOnCameraChangedListener(OnCameraChangedListener2 *l) NOTHROWS
    {
        Lock lock(mutex_);
        camera_listeners2_.erase(l);
        return TE_Ok;
    }
    // RenderContext
    bool StateRenderer::isRenderThread() const NOTHROWS
    {
        return false;
    }
    TAKErr StateRenderer::queueEvent(void(*runnable)(void *) NOTHROWS, std::unique_ptr<void, void(*)(const void *)> &&opaque) NOTHROWS
    {
        return TE_Unsupported;
    }
    void StateRenderer::requestRefresh() NOTHROWS { }
    TAKErr StateRenderer::setFrameRate(const float rate) NOTHROWS
    {
        return TE_Unsupported;
    }
    float StateRenderer::getFrameRate() const NOTHROWS
    {
        return 0.f;
    }
    void StateRenderer::setContinuousRenderEnabled(const bool enabled) NOTHROWS {}
    bool StateRenderer::isContinuousRenderEnabled() NOTHROWS
    {
        return false;
    }
    bool StateRenderer::supportsChildContext() const NOTHROWS
    {
        return false;
    }
    TAKErr StateRenderer::createChildContext(std::unique_ptr<RenderContext, void(*)(const RenderContext*)>& value) NOTHROWS
    {
        return TE_Unsupported;
    }
    bool StateRenderer::isAttached() const NOTHROWS
    {
        return false;
    }
    bool StateRenderer::attach() NOTHROWS
    {
        return false;
    }
    bool StateRenderer::detach() NOTHROWS
    {
        return false;
    }
    bool StateRenderer::isMainContext() const NOTHROWS
    {
        return true;
    }
    RenderSurface *StateRenderer::getRenderSurface() const NOTHROWS
    {
        return const_cast<StateRenderer *>(this);
    }
    // RenderSurface
    double StateRenderer::getDpi() const NOTHROWS
    {
        Lock lock(mutex_);
        return state_.displayDpi;
    }
    void StateRenderer::setDpi(double dpi) NOTHROWS
    {
        Lock lock(mutex_);
        GeoPoint2 focus;
        state_.projection->inverse(&focus, state_.camera.target);
        state_ = MapSceneModel2(
            dpi,
            state_.width,
            state_.height,
            state_.projection->getSpatialReferenceID(),
            focus,
            state_.focusX,
            state_.focusY,
            state_.camera.azimuth,
            90.0 + state_.camera.elevation,
            state_.gsd,
            state_.camera.nearMeters,
            state_.camera.farMeters,
            state_.camera.mode);
    }
    std::size_t StateRenderer::getWidth() const NOTHROWS
    {
        return state_.width;
    }
    std::size_t StateRenderer::getHeight() const NOTHROWS
    {
        return state_.height;

    }
    void StateRenderer::addOnSizeChangedListener(OnSizeChangedListener* l) NOTHROWS {}
    void StateRenderer::removeOnSizeChangedListener(const OnSizeChangedListener& l) NOTHROWS {}

}
