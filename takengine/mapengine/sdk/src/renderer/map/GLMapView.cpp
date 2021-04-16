#include "renderer/map/GLMapView.h"

#include "core/Layer2.h"
#include "core/LegacyAdapters.h"
#include "core/RenderContext.h"
#include "feature/LineString.h"
#include "math/Point.h"
#include "math/Rectangle.h"
#include "math/Ellipsoid.h"
#include "math/Frustum.h"
#include "math/Sphere.h"
#include "renderer/GLES20FixedPipeline.h"
#include "core/ProjectionFactory.h"
#include "renderer/core/GLLayer2.h"
#include "renderer/core/GLLayerFactory2.h"
#include "renderer/core/GLMapView2.h"
#include "renderer/core/LegacyAdapters.h"
#include "renderer/map/layer/GLLayerFactory.h"
#include "util/Memory.h"
#include "math/Matrix.h"
#include "port/Platform.h"

using namespace atakmap::renderer::map;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap;
using namespace atakmap::core;
using namespace atakmap::math;
using namespace atakmap::renderer;
using namespace atakmap::renderer::map::layer;

/******************************************************************/
// Private internal

namespace {
    const double _EPSILON = 0.0001;

    bool isTiny(double v)
    {
        return fabs(v) <= _EPSILON;
    }

    bool hasSettled(double dlat, double dlng, double dscale,
        double drot, double dfocusX, double dfocusY)
    {
        return isTiny(dlat) && isTiny(dlng) && isTiny(dscale)
            && isTiny(drot) && isTiny(dfocusX) && isTiny(dfocusY);
    }

    void asyncSetBaseMap(void *opaque)
    {
        auto *p = (std::pair<GLMapView *, GLMapRenderable *> *)opaque;
        GLMapView *mv = p->first;
        GLMapRenderable *map = p->second;
        delete p;
        mv->setBaseMap(map);
    }

    struct AsyncRefreshRenderablesBundle
    {
        GLMapView *view;
        std::list<std::shared_ptr<GLLayer2>> toRender;
        std::list<std::shared_ptr<GLLayer2>> toRelease;
    };
}


/******************************************************************/
// Public static constants

const double GLMapView::recommendedGridSampleDistance = 0.125;

/******************************************************************/
// Constructor

GLMapView::GLMapView(GLMapView2Ptr &&implPtr_) NOTHROWS :
    implPtr(std::move(implPtr_)),
    impl(implPtr.get()),
    left(impl->left),
    bottom(impl->bottom),
    right(impl->right),
    top(impl->top),
    drawLat(impl->drawLat),
    drawLng(impl->drawLng),
    drawRotation(impl->drawRotation),
    drawMapScale(impl->drawMapScale),
    drawMapResolution(impl->drawMapResolution),
    drawTilt(impl->drawTilt),
    animationFactor(impl->animationFactor),
    drawVersion(impl->drawVersion),
    targeting(impl->targeting),
    westBound(impl->westBound),
    southBound(impl->southBound),
    northBound(impl->northBound),
    eastBound(impl->eastBound),
    drawSrid(impl->drawSrid),
    focusx(impl->focusx),
    focusy(impl->focusy),
    upperLeft(impl->upperLeft),
    upperRight(impl->upperRight),
    lowerRight(impl->lowerRight),
    lowerLeft(impl->lowerLeft),
    settled(impl->settled),
    renderPump(impl->renderPump),
    scene(impl->scene),
    sceneModelForwardMatrix(impl->sceneModelForwardMatrix),
    view(&impl->view),
    sceneModelVersion(impl->drawVersion),
    animationLastTick(impl->animationLastTick),
    animationDelta(impl->animationDelta),
    animationLastUpdate(impl->animationLastUpdate),
    drawHorizon(impl->drawHorizon),
    crossesIDL(impl->crossesIDL),
    continuousSrollEnabled(impl->continuousScrollEnabled),
    privateData(nullptr),
    pixelDensity(impl->pixelDensity)
{}

GLMapView::GLMapView(RenderContext *ctx, AtakMapView *aview,
    int left, int bottom,
    int right, int top) :
    implPtr(new GLMapView2(*ctx, *aview,
        left, bottom,
        right, top),
        Memory_deleter_const<GLMapView2>),
    impl(implPtr.get()),
    left(impl->left),
    bottom(impl->bottom),
    right(impl->right),
    top(impl->top),
    drawLat(impl->drawLat),
    drawLng(impl->drawLng),
    drawRotation(impl->drawRotation),
    drawMapScale(impl->drawMapScale),
    drawMapResolution(impl->drawMapResolution),
    drawTilt(impl->drawTilt),
    animationFactor(impl->animationFactor),
    drawVersion(impl->drawVersion),
    targeting(impl->targeting),
    westBound(impl->westBound),
    southBound(impl->southBound),
    northBound(impl->northBound),
    eastBound(impl->eastBound),
    drawSrid(impl->drawSrid),
    focusx(impl->focusx),
    focusy(impl->focusy),
    upperLeft(impl->upperLeft),
    upperRight(impl->upperRight),
    lowerRight(impl->lowerRight),
    lowerLeft(impl->lowerLeft),
    settled(impl->settled),
    renderPump(impl->renderPump),
    scene(impl->scene),
    sceneModelForwardMatrix(impl->sceneModelForwardMatrix),
    view(&impl->view),
    sceneModelVersion(impl->drawVersion),
    animationLastTick(impl->animationLastTick),
    animationDelta(impl->animationDelta),
    animationLastUpdate(impl->animationLastUpdate),
    drawHorizon(impl->drawHorizon),
    crossesIDL(impl->crossesIDL),
    continuousSrollEnabled(impl->continuousScrollEnabled),
    privateData(nullptr),
    pixelDensity(impl->pixelDensity)
{}


GLMapView::~GLMapView()
{}

void GLMapView::dispose()
{
    // this has become a no-op, destruct does all cleanup
    impl->stop();
}

RenderContext *GLMapView::getRenderContext() const
{
    return &impl->context;
}

AtakMapView *GLMapView::getView() const
{
    return view;
}


void GLMapView::setBaseMap(GLMapRenderable *map)
{
    GLMapRenderable2Ptr mapPtr(nullptr, nullptr);
    if (map)
        LegacyAdapters_adapt(mapPtr, GLMapRenderablePtr(map, Memory_leaker_const<GLMapRenderable>));
    impl->setBaseMap(std::move(mapPtr));
}

void GLMapView::startAnimating(double lat, double lng, double scale, double rotation,
    double animateFactor)
{
    impl->startAnimating(lat, lng, scale, rotation, impl->drawTilt, animateFactor);
}

void GLMapView::startAnimatingFocus(float x, float y, double animateFactor)
{
    impl->startAnimatingFocus(x, y, animateFactor);
}

void GLMapView::getMapRenderables(std::list<GLMapRenderable *> *retval) const
{
    if (!impl->context.isRenderThread())
        throw std::exception();

    // XXX - 
}

void GLMapView::setOnAnimationSettledCallback(OnAnimationSettledCallback *c)
{
    //animator->animCallback = c;
}

math::Point<float> GLMapView::forward(core::GeoPoint p) const
{
    Point2<float> retval;
    GeoPoint2 p2;
    GeoPoint_adapt(&p2, p);
    impl->forward(&retval, p2);
    return Point<float>(retval.x, retval.y, retval.z);
}

math::Point<float> *GLMapView::forward(core::GeoPoint p, math::Point<float> *retval) const
{
    if (retval == nullptr)
        retval = new math::Point<float>();
    *retval = forward(p);
    return retval;
}

math::Point<float> *GLMapView::forward(core::GeoPoint *p, size_t count, math::Point<float> *retval) const
{
    if (retval == nullptr)
        retval = new math::Point<float>[count];

    for (size_t i = 0; i < count; i++) {
        forward(*p, retval + i);
    }
    return retval;
}

void GLMapView::forwardImpl(core::GeoPoint *p, math::Point<float> *retval) const
{
    Point2<float> retval2;
    GeoPoint2 p2;
    GeoPoint_adapt(&p2, *p);
    impl->forward(&retval2, p2);
    retval->x = retval2.x;
    retval->y = retval2.y;
    retval->z = retval2.z;
}

void GLMapView::forward(const float *src, const size_t count, float *dst) const
{
    impl->forward(dst, 2, src, 2, count);
}

void GLMapView::forward(const double *src, const size_t count, float *dst) const
{
    impl->forward(dst, 2, src, 2, count);
}

void GLMapView::inverse(const float *src, const size_t count, float *dst) const
{
    impl->inverse(dst, 2, src, 2, count);
}

void GLMapView::inverse(const float *src, const size_t count, double *dst) const
{
    impl->inverse(dst, 2, src, 2, count);
}

core::GeoPoint GLMapView::inverse(math::Point<float> p) const
{
    GeoPoint2 retval;
    impl->inverse(&retval, Point2<float>(p.x, p.y, p.z));
    return GeoPoint(retval);;
}

core::GeoPoint *GLMapView::inverse(math::Point<float> p, core::GeoPoint *retval) const
{
    if (retval == nullptr)
        retval = new core::GeoPoint();
    *retval = inverse(p);
    return retval;
}

core::GeoPoint *GLMapView::inverse(math::Point<float> *p, size_t count, core::GeoPoint *retval) const
{
    if (retval == nullptr)
        retval = new GeoPoint[count];

    for (size_t i = 0; i < count; i++) {
        inverseImpl(p + i, retval + i);
    }
    return retval;
}

void GLMapView::inverseImpl(math::Point<float> *p, core::GeoPoint *retval) const
{
    GeoPoint2 retval2;
    impl->inverse(&retval2, Point2<float>(p->x, p->y, p->z));
    *retval = retval2;
}

void GLMapView::prepareScene()
{
    impl->prepareScene();

    // update the couple fields that aren't references
    this->scene = impl->scene;
    this->upperLeft = GeoPoint(impl->upperLeft);
    this->upperRight = GeoPoint(impl->upperRight);
    this->lowerLeft = GeoPoint(impl->lowerLeft);
    this->lowerRight = GeoPoint(impl->lowerRight);
}

void GLMapView::render()
{
    this->prepareScene();
    this->drawRenderables();
}

int GLMapView::getLeft() const
{
    return left;
}
int GLMapView::getRight() const
{
    return right;
}
int GLMapView::getTop() const
{
    return top;
}
int GLMapView::getBottom() const
{
    return bottom;
}

void GLMapView::mapMoved(core::AtakMapView *map_view,
    bool animate)
{
    impl->mapMoved(map_view, animate);
}

void GLMapView::mapProjectionChanged(core::AtakMapView *map_view)
{
    impl->mapProjectionChanged(map_view);
}

void GLMapView::mapLayerAdded(core::AtakMapView *mapView, atakmap::core::Layer *layer)
{
    impl->mapLayerAdded(mapView, layer);
}

void GLMapView::mapLayerRemoved(core::AtakMapView *mapView, atakmap::core::Layer *layer)
{
    impl->mapLayerRemoved(mapView, layer);
}

void GLMapView::mapLayerPositionChanged(core::AtakMapView *mapView, atakmap::core::Layer *layer, int oldPosition,
    int newPosition)
{
    impl->mapLayerPositionChanged(mapView, layer, oldPosition, newPosition);
}

void GLMapView::mapControllerFocusPointChanged(core::AtakMapController *controller, const atakmap::math::Point<float> * const focus)
{
    impl->mapControllerFocusPointChanged(controller, focus);
}

void GLMapView::drawRenderables()
{
    impl->drawRenderables();
}
