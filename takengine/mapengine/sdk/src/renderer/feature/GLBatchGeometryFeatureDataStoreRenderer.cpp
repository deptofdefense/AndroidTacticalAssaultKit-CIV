#include "renderer/feature/GLBatchGeometryFeatureDataStoreRenderer.h"

#include <exception>

#include "feature/FeatureCursor2.h"
#include "port/STLListAdapter.h"
#include "port/StringBuilder.h"
#include "raster/osm/OSMUtils.h"
#include "renderer/feature/GLBatchGeometryCollection2.h"
#include "renderer/feature/GLBatchLineString2.h"
#include "renderer/feature/GLBatchMultiLineString2.h"
#include "renderer/feature/GLBatchMultiPoint2.h"
#include "renderer/feature/GLBatchMultiPolygon2.h"
#include "renderer/feature/GLBatchPoint2.h"
#include "renderer/feature/GLBatchPolygon2.h"
#include "util/Memory.h"


using namespace TAK::Engine::Renderer::Feature;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::core;
using namespace atakmap::feature;
using namespace atakmap::raster::osm;
using namespace atakmap::renderer;
using namespace atakmap::renderer::map;
using namespace atakmap::util;

#define STALE_LIMIT 5

namespace
{
    double distance(double x1, double y1, double x2, double y2);
    TAKErr getStyle(std::shared_ptr<const Style> &value, FeatureCursor2 &cursor, std::map<std::string, std::shared_ptr<const Style>> &styleMap) NOTHROWS;

    struct HitTestArgs
    {
        Mutex &otherMutex;
        Mutex signalMutex;
        CondVar signalCond;
        GLBatchGeometryRenderer2 &renderer;
        Collection<int64_t> &fids;
        const GeoPoint touch;
        const double resolution;
        const float radius;
        const std::size_t limit;
        bool done;

        HitTestArgs(Mutex &otherMutex, GLBatchGeometryRenderer2 &renderer, Collection<int64_t> &fids, const GeoPoint touch, const double resolution, const float radius, const std::size_t limit);
    };
    void glHitTestRun(void *opaque) NOTHROWS;

    class QueryContextImpl : public GLAsynchronousMapRenderable2::QueryContext
    {
    public :
        QueryContextImpl();
    public :
        std::list<std::shared_ptr<GLBatchGeometry2>> pendingData;
        int64_t queryCount;
    };
}

GLBatchGeometryFeatureDataStoreRenderer::GLBatchGeometryFeatureDataStoreRenderer(RenderContext &surface_, FeatureDataStore2 &subject_) NOTHROWS :
    GLAsynchronousMapRenderable2(),
    surface(surface_),
    dataStore(subject_),
    batchRenderer(new GLBatchGeometryRenderer2())
{
    hittest.reset(new HitTestImpl(*this));
}

GLBatchGeometryFeatureDataStoreRenderer::GLBatchGeometryFeatureDataStoreRenderer(RenderContext &surface_, FeatureDataStore2 &subject_, const GLBatchGeometryRenderer2::CachePolicy &cachePolicy) NOTHROWS  :
    GLAsynchronousMapRenderable2(),
    surface(surface_),
    dataStore(subject_),
    batchRenderer(new GLBatchGeometryRenderer2(cachePolicy))
{
    hittest.reset(new HitTestImpl(*this));
}

GLBatchGeometryFeatureDataStoreRenderer::GLBatchGeometryFeatureDataStoreRenderer(RenderContext &surface_, FeatureDataStore2 &subject_, const GLBatchGeometryRenderer2::CachePolicy &cachePolicy, const GLBatchGeometryFeatureDataStoreRendererOptions &opts) NOTHROWS :
    GLAsynchronousMapRenderable2(),
    surface(surface_),
    dataStore(subject_),
    batchRenderer(new GLBatchGeometryRenderer2(cachePolicy)),
    options(opts)
{
    hittest.reset(new HitTestImpl(*this));
}

/**************************************************************************/
// GL Asynchronous Map Renderable

void GLBatchGeometryFeatureDataStoreRenderer::draw(const GLMapView *view)
{
    if (view->drawHorizon && view->drawMapResolution >= 2538.9415423720125)
        return;

    // XXX - layer visibility

    GLAsynchronousMapRenderable2::draw(view);
}

void GLBatchGeometryFeatureDataStoreRenderer::start()
{
    Lock lock(mutex);
}

void GLBatchGeometryFeatureDataStoreRenderer::stop()
{
    Lock lock(mutex);
}

void GLBatchGeometryFeatureDataStoreRenderer::initImpl(const GLMapView *view) NOTHROWS
{
    this->dataStore.addOnDataStoreContentChangedListener(this);
}

TAKErr GLBatchGeometryFeatureDataStoreRenderer::releaseImpl() NOTHROWS
{
    this->dataStore.removeOnDataStoreContentChangedListener(this);
    this->batchRenderer->release();
    return TE_Ok;
}

TAKErr GLBatchGeometryFeatureDataStoreRenderer::getRenderables(Collection<GLMapRenderable *>::IteratorPtr &iter) NOTHROWS
{
    if (this->renderList.empty())
        return TE_Done;
    STLListAdapter<GLMapRenderable *> adapter(this->renderList);
    return adapter.iterator(iter);
}

TAKErr GLBatchGeometryFeatureDataStoreRenderer::resetQueryContext(QueryContext &ctx) NOTHROWS
{
    static_cast<QueryContextImpl &>(ctx).pendingData.clear();
    return TE_Ok;
}

TAKErr GLBatchGeometryFeatureDataStoreRenderer::createQueryContext(QueryContextPtr &result) NOTHROWS
{
    result = QueryContextPtr(new QueryContextImpl(), Memory_deleter_const<QueryContext, QueryContextImpl>);
    return TE_Ok;
}

TAKErr GLBatchGeometryFeatureDataStoreRenderer::updateRenderableLists(QueryContext &ctx) NOTHROWS
{
    // XXX - check for cancellation
    if (this->renderList.empty())
        this->renderList.push_back(this->batchRenderer.get());
    STLListAdapter<std::shared_ptr<GLBatchGeometry2>> adapter(static_cast<QueryContextImpl &>(ctx).pendingData);
    return this->batchRenderer->setBatch(adapter);
}

TAKErr GLBatchGeometryFeatureDataStoreRenderer::setBackgroundThreadName(WorkerThread &worker) NOTHROWS
{
    StringBuilder strm;
    strm << "GLBatchGeometryRendererThread-";
    strm << (uintptr_t)this;

    worker.setName(strm.c_str());
    return TE_Ok;
}

TAKErr GLBatchGeometryFeatureDataStoreRenderer::query(QueryContext &ctx, const ViewState &state) NOTHROWS
{
    TAKErr code;

    code = TE_Ok;

    const bool crossIdl = (state.westBound > state.eastBound);
    if (crossIdl) {
        // XXX - SpatiaLite will not correctly perform intersection (at least
        //       when the using spatial index) if the geometry provided is a
        //       GeometryCollection that is divided across the IDL. Two queries
        //       must be performed, one for each hemisphere.

        ViewState stateE;
        stateE.copy(state);
        stateE.eastBound = 180;
        ViewState stateW;
        stateW.copy(state);
        stateW.westBound = -180;

        code = this->queryImpl(ctx, stateE);
        TE_CHECKRETURN_CODE(code);

        code = this->queryImpl(ctx, stateW);
        TE_CHECKRETURN_CODE(code);

        return code;
    } else {
        code = this->queryImpl(ctx, state);
        TE_CHECKRETURN_CODE(code);

        return code;
    }
}

TAKErr GLBatchGeometryFeatureDataStoreRenderer::queryImpl(QueryContext &ctx, const ViewState &state) NOTHROWS
{
    TAKErr code;

    code = TE_Ok;

    std::list<std::shared_ptr<GLBatchGeometry2>> &result = static_cast<QueryContextImpl &>(ctx).pendingData;
    static_cast<QueryContextImpl &>(ctx).queryCount++;

    const int lod = OSMUtils::mapnikTileLevel(state.drawMapResolution);

    const bool crossIdl = (state.westBound > state.eastBound);

    double simplifyFactor = distance(state.upperLeft.longitude,
        state.upperLeft.latitude, state.lowerRight.longitude,
        state.lowerRight.latitude) /
        distance(state._left, state._top, state._right, state._bottom) * 2;

    FeatureDataStore2::FeatureQueryParameters params;
    params.visibleOnly = true;

    LineString mbb(atakmap::feature::Geometry::_2D);
    mbb.addPoint(state.westBound, state.northBound);
    mbb.addPoint(state.eastBound, state.northBound);
    mbb.addPoint(state.eastBound, state.southBound);
    mbb.addPoint(state.westBound, state.southBound);
    mbb.addPoint(state.westBound, state.northBound);

    params.spatialFilter = GeometryPtr_const(new atakmap::feature::Polygon(mbb), Memory_deleter_const<atakmap::feature::Geometry>);
    params.maxResolution = state.drawMapResolution;

    FeatureDataStore2::FeatureQueryParameters::SpatialOp simplify;
    simplify.type = FeatureDataStore2::FeatureQueryParameters::SpatialOp::Simplify;
    simplify.args.simplify.distance = simplifyFactor;
    params.ops->add(simplify);

    params.ignoredFields = FeatureDataStore2::FeatureQueryParameters::AttributesField;

    try {
        std::map<std::string, std::shared_ptr<const atakmap::feature::Style>> styleMap;

        int64_t featureId;
        int64_t featureVersion;

        //System::Int64 s = SystemClock::currentTimeMillis();

        FeatureCursorPtr cursor(nullptr, nullptr);

        code = this->dataStore.queryFeatures(cursor, params);
        TE_CHECKRETURN_CODE(code);
        do {
            code = cursor->moveToNext();
            TE_CHECKBREAK_CODE(code);

            code = cursor->getId(&featureId);
            TE_CHECKBREAK_CODE(code);

            code = cursor->getVersion(&featureVersion);
            TE_CHECKBREAK_CODE(code);

            std::shared_ptr<GLBatchGeometry2> glitem;
            std::map<int64_t, GLGeometryRecord>::iterator glitemEntry;
            glitemEntry = this->glSpatialItems.find(featureId);
            if (glitemEntry != this->glSpatialItems.end()) {
                glitem = glitemEntry->second.geometry;

                // the feature is being used on this pump, mark it as touched
                glitemEntry->second.touched = static_cast<QueryContextImpl &>(ctx).queryCount;

                // XXX - this isn't perfect as it doesn't take into
                // account the point scale factor, however
                // performance is probably more important than
                // the small amount of detail we may end up
                // losing
                
                // if we've already simplified the geometry at the
                // current level of detail, just go with what we have.
                if (!this->options.skipSameLodOptimization &&
                    glitem->version == featureVersion &&
                    glitem->lod == lod)
                {
                    result.push_back(glitem);
                    continue;
                }
            }

            int type;
            GLBatchGeometry2::BlobPtr blob(nullptr, nullptr);
            GeometryPtr_const geomPtr(nullptr, nullptr);

            if (cursor->getGeomCoding() == FeatureDefinition2::GeomBlob) {
                FeatureDefinition2::RawData  rawGeometry;
                code = cursor->getRawGeometry(&rawGeometry);
                TE_CHECKBREAK_CODE(code);
                const uint8_t *geom = rawGeometry.binary.value;
                if (!geom || !rawGeometry.binary.len)
                    continue;

                std::unique_ptr<uint8_t, void(*)(const uint8_t *)> blobData(new uint8_t[rawGeometry.binary.len], Memory_array_deleter_const<uint8_t>);
                memcpy(blobData.get(), geom, rawGeometry.binary.len);
                blob = GLBatchGeometry2::BlobPtr(new MemoryInput2(), Memory_deleter_const<MemoryInput2>);
                blob->open(std::move(blobData), rawGeometry.binary.len);

                uint8_t b;
                code = blob->readByte(&b);
                TE_CHECKBREAK_CODE(code);
                if (b != 0x00) // marker byte
                    continue;
                // endian
                code = blob->readByte(&b);
                TE_CHECKBREAK_CODE(code);
                switch (b) {
                case 0x00:
                    blob->setSourceEndian2(TE_BigEndian);
                    break;
                case 0x01:
                    blob->setSourceEndian2(TE_LittleEndian);
                    break;
                default:
                    continue;
                }
                // skip the SRID and MBR
                code = blob->skip(36);
                TE_CHECKBREAK_CODE(code);

                // marker byte
                code = blob->readByte(&b);
                TE_CHECKBREAK_CODE(code);
                if (b != 0x7C)
                    // XXX - drop through and process as Geometry class
                    continue;

                code = blob->readInt(&type);
                TE_CHECKBREAK_CODE(code);
            } else {
                if (cursor->getGeomCoding() == FeatureDefinition2::GeomGeometry) {
                    FeatureDefinition2::RawData rawGeom;
                    code = cursor->getRawGeometry(&rawGeom);
                    TE_CHECKBREAK_CODE(code);

                    geomPtr = GeometryPtr_const(static_cast<const atakmap::feature::Geometry *>(rawGeom.object)->clone(), atakmap::feature::destructGeometry);
                } else {
                    const Feature2 *feature;
                    code = cursor->get(&feature);
                    TE_CHECKBREAK_CODE(code);

                    geomPtr = GeometryPtr_const(static_cast<const atakmap::feature::Geometry *>(feature->getGeometry())->clone(), atakmap::feature::destructGeometry);
                }

                atakmap::feature::Geometry::Type gtype = geomPtr->getType();
                if (gtype == atakmap::feature::Geometry::POINT) {
                    type = 1;
                } else if (gtype == atakmap::feature::Geometry::LINESTRING) {
                    type = 2;
                } else if (gtype == atakmap::feature::Geometry::POLYGON) {
                    type = 3;
                } else if (gtype == atakmap::feature::Geometry::COLLECTION) {
                    type = 7;
                } else if (geomPtr == nullptr) {
                    continue;
                } else {
                    return TE_IllegalState;
                }
            }

            const bool refreshStyle = !glitem.get() || (glitem->version != featureVersion);

            // XXX - compare type against existing item and make sure renderer
            //       impl is still appropriate

            if (!glitem.get()) {
                switch (type % 1000) {
                case 1:
                    glitem.reset(new GLBatchPoint2(this->surface));
                    break;
                case 2:
                    glitem.reset(new GLBatchLineString2(this->surface));
                    break;
                case 3:
                    glitem.reset(new GLBatchPolygon2(this->surface));
                    break;
                case 4:
                    glitem.reset(new GLBatchMultiPoint2(this->surface));
                    break;
                case 5:
                    glitem.reset(new GLBatchMultiLineString2(this->surface));
                    break;
                case 6:
                    glitem.reset(new GLBatchMultiPolygon2(this->surface));
                    break;
                case 7:
                    glitem.reset(new GLBatchGeometryCollection2(this->surface));
                    break;
                default:
                    Logger::log(Logger::Warning, "Geometry type not supported, skipping feature %" PRId64, featureId);
                    continue;
                }

                const char *name;
                code = cursor->getName(&name);
                TE_CHECKBREAK_CODE(code);
                glitem->init(featureId, name);

                this->glSpatialItems[featureId] = { glitem, static_cast<QueryContextImpl &>(ctx).queryCount };

#if 0
                std::shared_ptr<const atakmap::feature::Style> style;
                code = getStyle(style, *cursor, styleMap);
                TE_CHECKBREAK_CODE(code);
                if (style.get())
                    glitem->setStyle(style);
#endif
            } else if(glitem->version != featureVersion) {
                // if the version was updated update the name
                const char *name;
                code = cursor->getName(&name);
                TE_CHECKBREAK_CODE(code);

                glitem->setName(name);
            }

            // XXX - geometry only needs to get set on GLBatchPoint once
            if (blob.get())
                glitem->setGeometry(std::move(blob), type, lod);
            else if (geomPtr.get())
                glitem->setGeometry(*geomPtr);
            else
                continue; // XXX - 

            // XXX - verify this is appropriate on iOS as well, think this
            //       handles the option to ignore LOD optimization
#ifdef MSVC
            if (refreshStyle)
#endif
            {
                std::shared_ptr<const Style> style;
                code = getStyle(style, *cursor, styleMap);
                TE_CHECKBREAK_CODE(code);
                if (style.get())
                    glitem->setStyle(style);
            }

            // update the version to reflect the new style and geometry
            glitem->version = featureVersion;

            // only add the result if it's visible
            result.push_back(glitem);
        } while (true);
        if (code == TE_Done)
            code = TE_Ok;
        TE_CHECKRETURN_CODE(code);

        cursor.reset();

        /*
        long e = System.currentTimeMillis();
        Statistics stats = queryStats.get(lod);
        if (stats == nullptr)
            queryStats.put(lod, stats = new Statistics());
        stats.observe(e - s);

        System.out.println("SPATIAL DB QUERY in " + (e-s) + "ms (avg=" + stats.average +
        ") " + result.size() + " RESULTS lod=" +
        OSMUtils.mapnikTileLevel(state.drawMapResolution) + " simplify factor=" +
        simplifyFactor);
        */
        
        // XXX - do stale check only every N seconds???

        // clean out stale entries
        const int64_t queryCount = static_cast<QueryContextImpl &>(ctx).queryCount;
        auto stale = glSpatialItems.begin();
        while (stale != glSpatialItems.end()) {
            if ((queryCount - stale->second.touched) > STALE_LIMIT) {
                stale = glSpatialItems.erase(stale);
            } else {
                stale++;
            }
        }

        return code;
    } catch(std::exception &e) {
        Logger::log(Logger::Level::Error, "GLBatchGeometryFeatureDataStoreRenderer: Unexpected exception, msg=%s", e.what());
        return TE_Err;
    } catch (...) {
        // XXX - seeing this get raised pretty consistently on shutdown as
        //       result of race between Database.close() and the worker
        //       thread exiting. Worker thread needs to be join()'d as part
        //       of release(), but this will require some significant effort
        //       to ensure query is aborted in non-blocking way across all
        //       asynchronous renderables. Log exception for 2.2 release.

        //throw new RuntimeException(e);
        Logger::log(Logger::Level::Error, "GLBatchGeometryFeatureDataStoreRenderer: Unexpected exception");
        return TE_Err;
    }
}

void GLBatchGeometryFeatureDataStoreRenderer::onDataStoreContentChanged(FeatureDataStore2 &data_store) NOTHROWS
{
    this->invalidate();
}

GLBatchGeometryFeatureDataStoreRendererOptions::GLBatchGeometryFeatureDataStoreRendererOptions() NOTHROWS
    : skipSameLodOptimization(false)
{ }

/**************************************************************************/
// Hit Test Service

TAKErr GLBatchGeometryFeatureDataStoreRenderer::hitTest2(Collection<int64_t> &fids, const float screenX, const float screenY, const GeoPoint &touch, const double resolution, const float radius, const std::size_t limit) NOTHROWS
{
    // geometries are modified on the GL thread so we need to do the
    // hit-testing on that thread
    HitTestArgs args(mutex, *this->batchRenderer, fids, touch, resolution, radius, limit);
    //HitTestArgs(PGSC::Mutex &otherMutex, GLBatchGeometryRenderer2 &renderer, Collection<int64_t> &fids, const GeoPoint touch, const double resolution, const float radius, const std::size_t limit);

    this->surface.queueEvent(glHitTestRun, std::unique_ptr<void, void(*)(const void *)>(&args, Memory_leaker_const<void>));

    {
        Lock lock(args.signalMutex);
        while (!args.done)
            try {
                args.signalCond.wait(lock);
            } catch (...) {} // ignored
    }

    return TE_Ok;
}

GLBatchGeometryFeatureDataStoreRenderer::HitTestImpl::HitTestImpl(GLBatchGeometryFeatureDataStoreRenderer &owner_) :
    owner(owner_)
{}

TAKErr GLBatchGeometryFeatureDataStoreRenderer::HitTestImpl::hitTest(Collection<int64_t> &features, const float screenX, const float screenY, const GeoPoint &touch, const double resolution, const float radius, const std::size_t limit) NOTHROWS
{
    return owner.hitTest2(features, screenX, screenY, touch, resolution, radius, limit);
}

namespace
{
    double distance(double x1, double y1, double x2, double y2)
    {
        double dx = (x1 - x2);
        double dy = (y1 - y2);

        return sqrt(dx * dx + dy * dy);
    }

    TAKErr getStyle(std::shared_ptr<const Style> &value, FeatureCursor2 &cursor, std::map<std::string, std::shared_ptr<const atakmap::feature::Style>> &styleMap) NOTHROWS
    {
        TAKErr code(TE_Ok);

        if (cursor.getStyleCoding() == FeatureDefinition2::StyleOgr) {
            FeatureDefinition2::RawData rawStyle;
            cursor.getRawStyle(&rawStyle);
            const char *styleString = rawStyle.text;
            if (styleString) {
                value.reset();
                std::map<std::string, std::shared_ptr<const atakmap::feature::Style>>::const_iterator entry;
                entry = styleMap.find(styleString);
                if (entry == styleMap.end()) {
                    StylePtr_const parsed(nullptr, nullptr);
                    try {
                        parsed = StylePtr_const(atakmap::feature::Style::parseStyle(styleString), atakmap::feature::Style::destructStyle);
                    } catch (...) {}
                    if (parsed.get()) {
                        value = std::move(parsed);
                        styleMap.insert(std::pair<std::string, std::shared_ptr<const atakmap::feature::Style>>(styleString, value));
                    }
                } else {
                    value = entry->second;
                }
            }
        } else if (cursor.getStyleCoding() == FeatureDefinition2::StyleStyle) {
            FeatureDefinition2::RawData rawStyle;
            code = cursor.getRawStyle(&rawStyle);
            TE_CHECKRETURN_CODE(code);

            if (rawStyle.object) {
                value = StylePtr_const(static_cast<const Style *>(rawStyle.object)->clone(), Style::destructStyle);
            } else {
                value.reset();
            }
        } else {
            const Feature2 *f;
            code = cursor.get(&f);
            TE_CHECKRETURN_CODE(code);

            value = StylePtr_const(f->getStyle()->clone(), Style::destructStyle);
        }

        return code;
    }

    HitTestArgs::HitTestArgs(Mutex &otherMutex_, GLBatchGeometryRenderer2 &renderer_, Collection<int64_t> &fids_, const GeoPoint touch_, const double resolution_, const float radius_, const std::size_t limit_) :
        otherMutex(otherMutex_),
        renderer(renderer_),
        fids(fids_),
        touch(touch_),
        resolution(resolution_),
        radius(radius_),
        limit(limit_),
        done(false)
    {}

    QueryContextImpl::QueryContextImpl() :
        queryCount(0LL)
    {}

    void glHitTestRun(void *opaque) NOTHROWS
    {
        // HT sync, batchrenderer, FIDs, touch GeoPoint, meters, limit
        auto *args = static_cast<HitTestArgs *>(opaque);

        {
            Lock lock(args->otherMutex);
            Point touchPt(args->touch.longitude, args->touch.latitude);
            args->renderer.hitTest2(
                args->fids,
                touchPt,
                args->resolution,
                static_cast<int>(args->radius),
                static_cast<int>(args->limit),
                FeatureDataStore2::FEATURE_ID_NONE);

        }

        {
            Lock lock(args->signalMutex);
            args->done = true;
            args->signalCond.signal(lock);
        }
    }
}
