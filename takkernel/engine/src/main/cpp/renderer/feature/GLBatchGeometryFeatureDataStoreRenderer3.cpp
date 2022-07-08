#include "renderer/feature/GLBatchGeometryFeatureDataStoreRenderer3.h"

#include "core/ProjectionFactory3.h"
#include "elevation/ElevationManager.h"
#include "feature/FeatureCursor2.h"
#include "math/Mesh.h"
#include "math/Vector4.h"
#include "port/STLListAdapter.h"
#include "port/STLVectorAdapter.h"
#include "port/StringBuilder.h"
#include "raster/osm/OSMUtils.h"
#include "renderer/GL.h"
#include "renderer/GLTextureAtlas2.h"
#include "renderer/core/GLGlobe.h"
#include "renderer/core/GLGlobeBase.h"
#include "renderer/core/GLMapRenderGlobals.h"
#include "renderer/elevation/TerrainRenderService.h"
#include "renderer/feature/GLGeometryBatchBuilder.h"
#include "util/ConfigOptions.h"
#include "util/MathUtils.h"
#include "util/Memory.h"
#include "util/PoolAllocator.h"

using namespace TAK::Engine::Renderer::Feature;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::core;
using namespace atakmap::feature;
using namespace atakmap::raster::osm;
using namespace atakmap::renderer;
using namespace atakmap::util;

namespace
{
    struct IconLoader
    {
        std::map<std::string, AsyncBitmapLoader2::Task> loading;
        std::set<std::string> failed;
        TAK::Engine::Port::String defaultIconUri;
    };
    struct {
        std::map<const RenderContext*, IconLoader> value;
        Mutex mutex;
    } loaders;


    struct TextureAtlasEntry
    {
        GLuint texid{ GL_NONE };
        float u0{ 0.f };
        float v0{ 0.f };
        float u1{ 0.f };
        float v1{ 0.f };
        std::size_t width{ 0u };
        std::size_t height{ 0u };
        float rotation{ 0.0f };
        bool isAbsoluteRotation{ false };
    };
    struct CallbackExchange
    {
        Monitor monitor;
        bool terminated{ false };
    };

    struct FeatureRecord
    {
        int64_t fid{ FeatureDataStore2::FEATURE_ID_NONE };
        int64_t version{ FeatureDataStore2::FEATURE_VERSION_NONE };
        std::size_t touch{ 0u };
        struct {
            uint32_t id{ 0u };
            std::vector<uint32_t> overflow;
        } labels;
        uint32_t hitid{ 0u };
    };

    class BuilderQueryContext : public GLAsynchronousMapRenderable3::QueryContext,
                             public GLGeometryBatchBuilder::Callback
    {
    public :
        BuilderQueryContext(RenderContext &ctx, std::size_t &ctxid);
    public :
        void startFeature(const int64_t fid, const int64_t version) NOTHROWS;
        void endFeature() NOTHROWS;
    public :
        TAKErr mapBuffer(GLuint *handle, void **buffer, const std::size_t size) NOTHROWS override;
        TAKErr unmapBuffer(const GLuint handle) NOTHROWS override;
        TAKErr getElevation(double *value, const double latitude, const double longitude) NOTHROWS override;
        TAKErr getIcon(GLuint *id, float *u0, float *v0, float *u1, float *v1, std::size_t *w, std::size_t *h, float *rotation, bool *isAbsoluteRotation, const char* uri) NOTHROWS override;
        TAKErr addLabel(const GLLabel &label) NOTHROWS override;
        uint32_t reserveHitId() NOTHROWS override;
    public :
        RenderContext &ctx;
        const std::size_t initCtxId;
        std::size_t &activeCtxId;
        GLGeometryBatchBuilder builder;
        std::map<std::string, TextureAtlasEntry> iconEntryCache;
        std::shared_ptr<CallbackExchange> cbex;
        std::size_t queryCount{ 0u };
        uint32_t nextHitId{ 0u };
        
        std::map<int64_t, FeatureRecord> featureRecords;

        FeatureRecord* current{ nullptr };
        GLLabelManager* labelManager{ nullptr };
        struct {
            std::vector<std::shared_ptr<const TAK::Engine::Renderer::Elevation::TerrainTile>> value;
            std::shared_ptr<const TAK::Engine::Renderer::Elevation::TerrainTile> last;
        } terrainTiles;
    };

    struct BuilderCallbackBundle
    {
        GLuint handle{ GL_NONE };
        void *buffer{ nullptr };
        std::size_t bufsize{ 0u };
        const char *iconUri;
        TextureAtlasEntry icon;
        RenderContext *ctx;
        bool done{ false };
    };

    struct CallbackOpaque
    {
        BuilderCallbackBundle *bundle{ nullptr };
        std::shared_ptr<CallbackExchange> cbex;
    };

    PoolAllocator<CallbackOpaque> cb_alloc(64u);

    struct HitTestParams 
    {
        CallbackExchange callbackExchange;
        GLBatchGeometryRenderer4* renderer{ nullptr };
        std::vector<uint32_t>* featureIds{ nullptr };
        const Core::GLGlobeBase* view{ nullptr };
        float screenX; 
        float screenY; 
        bool done{ false };
    };

    PoolAllocator<HitTestParams> hitTestAllocator(64u);

    void hitTest(void *params) NOTHROWS;
}

GLBatchGeometryFeatureDataStoreRenderer3::GLBatchGeometryFeatureDataStoreRenderer3(TAK::Engine::Core::RenderContext &surface_, FeatureDataStore2 &subject_) NOTHROWS :
    GLAsynchronousMapRenderable3(),
    surface(surface_),
    dataStore(subject_),
    validContext(0u),
    renderer(surface_),
    spatialFilterControl(&invalid_)
{}

/**************************************************************************/
// GL Asynchronous Map Renderable

void GLBatchGeometryFeatureDataStoreRenderer3::draw(const GLGlobeBase &view, const int renderPass) NOTHROWS
{
    {
        Lock lock(loaders.mutex);
        const auto &entry = loaders.value.find(&surface);
        bool doInvalidate = false;
        if (entry != loaders.value.end()) {
            auto it = entry->second.loading.begin();
            while (it != entry->second.loading.end()) {
                if (it->second->getFuture().getState() == atakmap::util::SharedState::Complete) {
                    GLTextureAtlas2 *atlas;
                    GLMapRenderGlobals_getTextureAtlas2(&atlas, surface);
                        // upload bitmap to atlas
                    int64_t key;
                    if(atlas->addImage(&key, it->first.c_str(), *it->second->getFuture().get()) != TE_Ok) {
                        entry->second.failed.insert(it->first);
                    }
                    it = entry->second.loading.erase(it);

                    // mark invalid to refresh buffers
                    invalid_ |= true;
                } else if (it->second->getFuture().getState() == atakmap::util::SharedState::Error) {
                    entry->second.failed.insert(it->first);
                    it = entry->second.loading.erase(it);

                    // mark invalid to refresh buffers
                    invalid_ |= true;
                } else {
                    it++;
                }
            }
        }
    }
    currentView = &view;
    GLAsynchronousMapRenderable3::draw(view, renderPass);
}
TAKErr GLBatchGeometryFeatureDataStoreRenderer3::getControl(void **ctrl, const char *type) const NOTHROWS
{
    if (!type)
        return TE_InvalidArg;
    if (!ctrl)
        return TE_InvalidArg;
    if (strcmp(type, SpatialFilterControl_getType()) == 0) {
        const SpatialFilterControl& iface = spatialFilterControl;
        *ctrl = (void*)&iface;
        return TE_Ok;
    }
    return TE_InvalidArg;
}
void GLBatchGeometryFeatureDataStoreRenderer3::release() NOTHROWS
{
    validContext++;
    GLAsynchronousMapRenderable3::release();
}
int GLBatchGeometryFeatureDataStoreRenderer3::getRenderPass() NOTHROWS
{
    return GLMapView2::Surface|GLMapView2::Sprites;
}
void GLBatchGeometryFeatureDataStoreRenderer3::start() NOTHROWS
{
    this->dataStore.addOnDataStoreContentChangedListener(this);
}
void GLBatchGeometryFeatureDataStoreRenderer3::stop() NOTHROWS
{
    this->dataStore.removeOnDataStoreContentChangedListener(this);
}
void GLBatchGeometryFeatureDataStoreRenderer3::initImpl(const GLGlobeBase &view) NOTHROWS {
    
}
TAKErr GLBatchGeometryFeatureDataStoreRenderer3::releaseImpl() NOTHROWS
{
    renderer.release();
    return TE_Ok;
}

TAKErr GLBatchGeometryFeatureDataStoreRenderer3::getRenderables(Collection<GLMapRenderable2 *>::IteratorPtr &iter) NOTHROWS
{
    if (this->renderList.empty())
        return TE_Done;
    STLListAdapter<GLMapRenderable2 *> adapter(this->renderList);
    return adapter.iterator(iter);
}

TAKErr GLBatchGeometryFeatureDataStoreRenderer3::resetQueryContext(QueryContext &ctx) NOTHROWS
{
    return TE_Ok;
}

TAKErr GLBatchGeometryFeatureDataStoreRenderer3::createQueryContext(QueryContextPtr &result) NOTHROWS
{
    std::unique_ptr<BuilderQueryContext> queryContext(new(std::nothrow) BuilderQueryContext(surface, validContext));
    if (!queryContext)
        return TE_OutOfMemory;

    if (currentView)
        queryContext->labelManager = currentView->getLabelManager();

    result = QueryContextPtr(queryContext.release(), Memory_deleter_const<QueryContext, BuilderQueryContext>);
    return TE_Ok;
}

TAKErr GLBatchGeometryFeatureDataStoreRenderer3::updateRenderableLists(QueryContext &opaque) NOTHROWS
{
    auto& ctx = static_cast<BuilderQueryContext &>(opaque);

    // mark all current buffers for release
    this->renderer.markForRelease();
    // flush new buffers to renderer
    ctx.builder.setBatch(renderer);

    this->renderList.clear();
    this->renderList.push_back(&renderer);

    this->hitids.clear();
    for (const auto& record : ctx.featureRecords)
        this->hitids[record.second.hitid] = record.first;
    return TE_Ok;
}

TAKErr GLBatchGeometryFeatureDataStoreRenderer3::getBackgroundThreadName(TAK::Engine::Port::String &value) NOTHROWS
{
    StringBuilder strm;
    strm << "GLBatchGeometryFeatureDataStoreRenderer3-";
    strm << (uintptr_t)this;

    value = strm.c_str();
    return TE_Ok;
}

TAKErr GLBatchGeometryFeatureDataStoreRenderer3::query(QueryContext &opaque, const GLMapView2::State &state) NOTHROWS
{
    TAKErr code(TE_Ok);

    auto& ctx = static_cast<BuilderQueryContext&>(opaque);
    ctx.queryCount++;

    if (currentView) {
        STLVectorAdapter<std::shared_ptr<const TAK::Engine::Renderer::Elevation::TerrainTile>> tiles_a(ctx.terrainTiles.value);
        currentView->getTerrainRenderService().lock(tiles_a);
    }
    if(!ctx.labelManager && currentView)
        ctx.labelManager = currentView->getLabelManager();

    const bool crossIdl = (state.westBound > state.eastBound);
    if (crossIdl) {
        // XXX - SpatiaLite will not correctly perform intersection (at least
        //       when the using spatial index) if the geometry provided is a
        //       GeometryCollection that is divided across the IDL. Two queries
        //       must be performed, one for each hemisphere.

        GLMapView2::State stateE;
        stateE = state;
        stateE.eastBound = 180;
        GLMapView2::State stateW;
        stateW = state;
        stateW.westBound = -180;

        code = this->queryImpl(ctx, stateE);
        TE_CHECKRETURN_CODE(code);

        code = this->queryImpl(ctx, stateW);
        TE_CHECKRETURN_CODE(code);
    } else {
        code = this->queryImpl(ctx, state);
        TE_CHECKRETURN_CODE(code);
    }

    
    // evict stale records
    auto it = ctx.featureRecords.begin();
    while(it != ctx.featureRecords.end()) {
        const auto& record = it->second;
        // feature was not included with current query, evict
        if(record.touch != ctx.queryCount) {
            // remove associated label(s)
            if (ctx.labelManager) {
                if (record.labels.id)
                    ctx.labelManager->removeLabel(record.labels.id);
                for (const auto& id : record.labels.overflow) {
                    ctx.labelManager->removeLabel(id);
                }
            }

            // evict
            it = ctx.featureRecords.erase(it);
        } else {
            it++;
        }
    }

    if (currentView) {
        STLVectorAdapter<std::shared_ptr<const TAK::Engine::Renderer::Elevation::TerrainTile>> tiles_a(ctx.terrainTiles.value);
        currentView->getTerrainRenderService().unlock(tiles_a);
        ctx.terrainTiles.value.clear();

        ctx.terrainTiles.last.reset();
    }

    return TE_Ok;
}

TAKErr GLBatchGeometryFeatureDataStoreRenderer3::queryImpl(QueryContext &opaque, const GLMapView2::State &state) NOTHROWS
{
    TAKErr code(TE_Ok);

    auto &ctx = static_cast<BuilderQueryContext &>(opaque);

    // XXX - SRIDs
    struct
    {
        int surface;
        int sprites;
    } srid;
    srid.surface = state.drawSrid;
    srid.sprites = state.drawSrid;
    if (surface_ctrl_ || state.drawTilt)
        srid.surface = 4326;
    ctx.builder.reset(srid.surface, srid.sprites, GeoPoint2(state.drawLat, state.drawLng), state.drawMapResolution, ctx);

    double simplifyFactor;
    if (state.scene.camera.mode == TAK::Engine::Core::MapCamera2::Perspective) {
        simplifyFactor = (state.drawMapResolution /
            TAK::Engine::Core::GeoPoint2_approximateMetersPerDegreeLatitude(state.drawLat)) * 2.0;
    } else {
        simplifyFactor = Vector2_length(TAK::Engine::Math::Point2<double>(
            state.upperLeft.longitude-state.lowerRight.longitude, state.upperLeft.latitude-state.lowerRight.latitude)) /
        Vector2_length(TAK::Engine::Math::Point2<double>(
            state.right-state.left, state.top-state.bottom)) * 2;
    }

    FeatureDataStore2::FeatureQueryParameters params;
    params.visibleOnly = true;

    LineString mbb(atakmap::feature::Geometry::_2D);
    mbb.addPoint(state.westBound, state.northBound);
    mbb.addPoint(state.eastBound, state.northBound);
    mbb.addPoint(state.eastBound, state.southBound);
    mbb.addPoint(state.westBound, state.southBound);
    mbb.addPoint(state.westBound, state.northBound);

    {
        const auto include_spatial_filter_envelope = spatialFilterControl.getIncludeMinimumBoundingBox();
        auto mbb_envelope = mbb.getEnvelope();

        mbb_envelope.minX = std::max(mbb_envelope.minX, include_spatial_filter_envelope.minX);
        mbb_envelope.minY = std::max(mbb_envelope.minY, include_spatial_filter_envelope.minY);
        mbb_envelope.maxX = std::min(mbb_envelope.maxX, include_spatial_filter_envelope.maxX);
        mbb_envelope.maxY = std::min(mbb_envelope.maxY, include_spatial_filter_envelope.maxY);

        mbb.setX(0, mbb_envelope.minX);
        mbb.setY(0, mbb_envelope.maxY);
        mbb.setX(1, mbb_envelope.maxX);
        mbb.setY(1, mbb_envelope.maxY);
        mbb.setX(2, mbb_envelope.maxX);
        mbb.setY(2, mbb_envelope.minY);
        mbb.setX(3, mbb_envelope.minX);
        mbb.setY(3, mbb_envelope.minY);
        mbb.setX(4, mbb_envelope.minX);
        mbb.setY(4, mbb_envelope.maxY);
    }

    atakmap::feature::Polygon spatialFilter(mbb);

    params.spatialFilter = GeometryPtr_const(&spatialFilter, Memory_leaker_const<atakmap::feature::Geometry>);
    params.maxResolution = state.drawMapResolution;
	params.minResolution = state.drawMapResolution * 0.5;

    FeatureDataStore2::FeatureQueryParameters::SpatialOp simplify;
    simplify.type = FeatureDataStore2::FeatureQueryParameters::SpatialOp::Simplify;
    simplify.args.simplify.distance = simplifyFactor;
    params.ops->add(simplify);

    params.ignoredFields = FeatureDataStore2::FeatureQueryParameters::AttributesField;

    FeatureCursorPtr cursor(nullptr, nullptr);
        
    int64_t s = Platform_systime_millis();
    std::size_t n = 0u;
    code = this->dataStore.queryFeatures(cursor, params);
    TE_CHECKRETURN_CODE(code);
    do {
        code = cursor->moveToNext();
        TE_CHECKBREAK_CODE(code);

        bool matchesFilter;
        code = spatialFilterControl.accept(&matchesFilter, *cursor);
        TE_CHECKBREAK_CODE(code);
        if (!matchesFilter)
            continue;

        int64_t id = 0;
        code = cursor->getId(&id);
        TE_CHECKBREAK_CODE(code);

        int64_t version = 0;
        code = cursor->getVersion(&version);
        TE_CHECKBREAK_CODE(code);

        ctx.startFeature(id, version);
        code = ctx.builder.push(*cursor, !!ctx.labelManager && !ctx.current->labels.id);
        ctx.endFeature();

        TE_CHECKBREAK_CODE(code);
        n++;
    } while (true);
    if (code == TE_Done)
        code = TE_Ok;
    TE_CHECKRETURN_CODE(code);

    cursor.reset();

    ctx.builder.flush();

    int64_t e = Platform_systime_millis();
#if 0
    if(n)
        Logger_log(TELL_Info, "Processed %u features in %ums", (unsigned)n, (unsigned)(e - s));
#endif


    return code;
}

void GLBatchGeometryFeatureDataStoreRenderer3::onDataStoreContentChanged(FeatureDataStore2 &data_store) NOTHROWS
{
    this->invalidate();
}

/**************************************************************************/
// Hit Test Service

TAKErr GLBatchGeometryFeatureDataStoreRenderer3::hitTest2(Collection<int64_t> &fids, const float screenX, const float screenY, const GeoPoint &touch, const double resolution, const float radius, const std::size_t limit) NOTHROWS
{
    TAKErr code(TE_Ok);
    std::vector<uint32_t> ids;
    if (surface.isRenderThread()) {
        TAK::Engine::Port::STLVectorAdapter<uint32_t> htids_a(ids);
        const bool current = surface.isAttached();
        if (!current)
            surface.attach();
        renderer.hitTest(htids_a, *currentView, screenX, currentView->renderPass->scene.height - screenY);
        if (!current)
            surface.detach();
    } else {

        std::unique_ptr<void, void(*)(const void*)> params(nullptr, nullptr);
        code = hitTestAllocator.allocate(params);
        auto *hitTestParams = static_cast<HitTestParams*>(params.get());
        hitTestParams->renderer = &renderer;
        hitTestParams->featureIds = &ids;
        hitTestParams->view = currentView;
        hitTestParams->screenX = screenX;
        // Need to flip the y-coordinate, so that the origin is at the bottom left instead of top left
        hitTestParams->screenY = currentView->renderPass->scene.height - screenY;
        hitTestParams->done = false;

        TE_CHECKRETURN_CODE(code);
        surface.queueEvent(hitTest, std::move(params));
        {
            Monitor::Lock lock(hitTestParams->callbackExchange.monitor);
            while (!hitTestParams->done) {
                lock.wait(500u);
            }
        }
    }

    Monitor::Lock lock(monitor_);
    for (auto& htid : ids) {
        const auto fid = hitids.find(htid);
        if (fid != hitids.end())
            fids.add(fid->second);
    }


    return code;
}

namespace
{
    void cb_mapBufferImpl(void* opaque) NOTHROWS
    {
        auto arg = static_cast<CallbackOpaque *>(opaque);
        Monitor::Lock lock(arg->cbex->monitor);
        if (arg->cbex->terminated)
            return;

        glGenBuffers(1u, &arg->bundle->handle);
        glBindBuffer(GL_ARRAY_BUFFER, arg->bundle->handle);
        glBufferData(GL_ARRAY_BUFFER, arg->bundle->bufsize, nullptr, GL_STATIC_DRAW);
        arg->bundle->buffer = glMapBufferRange(GL_ARRAY_BUFFER, 0, arg->bundle->bufsize, GL_MAP_WRITE_BIT);
        glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);
        arg->bundle->done = true;

        lock.signal();
    }
    void cb_unmapBufferImpl(void* opaque) NOTHROWS
    {
        auto arg = static_cast<CallbackOpaque *>(opaque);
        Monitor::Lock lock(arg->cbex->monitor);
        if (arg->cbex->terminated)
            return;


        glBindBuffer(GL_ARRAY_BUFFER, arg->bundle->handle);
        glUnmapBuffer(GL_ARRAY_BUFFER);
        glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);
        arg->bundle->done = true;

        lock.signal();
    }
    void cb_getIconImpl(void* opaque) NOTHROWS
    {
        auto arg = static_cast<CallbackOpaque *>(opaque);
        Monitor::Lock cblock(arg->cbex->monitor);
        if (arg->cbex->terminated)
            return;

        arg->bundle->icon.texid = GL_NONE;

        do {
            const char* iconUri = arg->bundle->iconUri;
            Lock lock(loaders.mutex);
            auto &loader = loaders.value[arg->bundle->ctx];
            if (loader.failed.find(arg->bundle->iconUri) != loader.failed.end()) {
                // icon won't load, try default
                if (!loader.defaultIconUri) {
                    ConfigOptions_getOption(loader.defaultIconUri, "defaultIconUri");
                    if (!loader.defaultIconUri)
                        break;
                }
                iconUri = loader.defaultIconUri;
            }
            // check atlas
            GLTextureAtlas2 *atlas;
            GLMapRenderGlobals_getTextureAtlas2(&atlas, *arg->bundle->ctx);
            if (atlas) {
                int64_t key;
                if (atlas->getTextureKey(&key, iconUri) == TE_Ok) {
                    int texid;
                    atlas->getTexId(&texid, key);
                    arg->bundle->icon.texid = texid;
                    atakmap::math::Rectangle<float> rect;
                    atlas->getImageRect(&rect, key, true);
                    arg->bundle->icon.u0 = rect.x;
                    arg->bundle->icon.v0 = rect.y;
                    arg->bundle->icon.u1 = rect.width;
                    arg->bundle->icon.v1 = rect.height;
                    atlas->getImageWidth(&arg->bundle->icon.width, key);
                    atlas->getImageHeight(&arg->bundle->icon.height, key);
                    break;
                }
            }

            // already queued for load
            const auto& loading = loader.loading.find(iconUri);
            if (loading != loader.loading.end()) {
                // XXX - check complete
                break;
            }

            // queue load
            AsyncBitmapLoader2 *bitmapLoader;
            GLMapRenderGlobals_getBitmapLoader(&bitmapLoader, *arg->bundle->ctx);

            AsyncBitmapLoader2::Task task;
            bitmapLoader->loadBitmapUri(task, iconUri);
            loader.loading[iconUri] = task;
        } while (false);

        arg->bundle->done = true;

        cblock.signal();
    }

    BuilderQueryContext::BuilderQueryContext(RenderContext &ctx_, std::size_t &ctxid_) :
        ctx(ctx_),
        initCtxId(ctxid_),
        activeCtxId(ctxid_),
        cbex(std::make_shared<CallbackExchange>())
    {}
    void BuilderQueryContext::startFeature(const int64_t fid, const int64_t version) NOTHROWS
    {
        current = &featureRecords[fid];
        if(current->fid == FeatureDataStore2::FEATURE_ID_NONE) {
            // new record
            current->fid = fid;
            current->hitid = 0xFF000000u | (nextHitId+1u);

            // 24-bit ID, allows for ~16m IDs before reuse
            nextHitId = (nextHitId + 1) % 0xFFFFFEu;
        } else if(current->version != version) {
            if (labelManager) {
                // feature version changed; remove all old labels
                if (current->labels.id)
                    labelManager->removeLabel(current->labels.id);
                current->labels.id = 0u;
                for (const auto& ofid : current->labels.overflow)
                    labelManager->removeLabel(ofid);
                current->labels.overflow.clear();
            }
        }
        current->version = version;
        current->touch = queryCount;
    }
    void BuilderQueryContext::endFeature() NOTHROWS
    {
        current = nullptr;
    }
    TAKErr BuilderQueryContext::mapBuffer(GLuint* handle, void** buffer, const std::size_t size) NOTHROWS
    {        
        BuilderCallbackBundle arg;
        arg.bufsize = size;

        CallbackOpaque opaque;
        opaque.bundle = &arg;
        opaque.cbex = cbex;

        if (ctx.isRenderThread()) {
            cb_mapBufferImpl(&opaque);
        } else {
            std::unique_ptr<void, void(*)(const void*)> glopaque(nullptr, nullptr);
            const TAKErr code = cb_alloc.allocate(glopaque);
            TE_CHECKRETURN_CODE(code);
            *(static_cast<CallbackOpaque*>(glopaque.get())) = opaque;
            ctx.queueEvent(cb_mapBufferImpl, std::move(glopaque));
            {
                Monitor::Lock lock(cbex->monitor);
                do {
                    if (arg.done)
                        break;
                    cbex->terminated = (initCtxId != activeCtxId);
                    if (cbex->terminated)
                        break;
                    lock.wait(500LL);
                } while(true);
            }
        }

        if (arg.done) {
            *handle = arg.handle;
            *buffer = arg.buffer;
        }
        return arg.done ? TE_Ok : TE_Interrupted;
    }
    TAKErr BuilderQueryContext::unmapBuffer(const GLuint handle) NOTHROWS
    {
        BuilderCallbackBundle arg;
        arg.handle = handle;

        CallbackOpaque opaque;
        opaque.bundle = &arg;
        opaque.cbex = cbex;

        if (ctx.isRenderThread()) {
            cb_unmapBufferImpl(&opaque);
        } else {
            std::unique_ptr<void, void(*)(const void*)> glopaque(nullptr, nullptr);
            const TAKErr code = cb_alloc.allocate(glopaque);
            TE_CHECKRETURN_CODE(code);
            *(static_cast<CallbackOpaque*>(glopaque.get())) = opaque;
            ctx.queueEvent(cb_unmapBufferImpl, std::move(glopaque));
            {
                Monitor::Lock lock(cbex->monitor);
                do {
                    if (arg.done)
                        break;
                    cbex->terminated = (initCtxId != activeCtxId);
                    if (cbex->terminated)
                        break;
                    lock.wait(500LL);
                } while(true);
            }
        }
        return arg.done ? TE_Ok : TE_Interrupted;
    }

    TAKErr getElevation(double* value, const double latitude, const double longitude_, const TAK::Engine::Renderer::Elevation::TerrainTile &tile) NOTHROWS
    {
        TAKErr code(TE_InvalidArg);

        *value = NAN;

        // wrap the longitude if necessary
        double longitude = longitude_;
        if(longitude > 180.0)
            longitude -= 360.0;
        else if(longitude < -180.0)
            longitude += 360.0;

        double elevation = NAN;
        const double altAboveSurface = 30000.0;

        // AABB/bounds check
        const TAK::Engine::Feature::Envelope2 aabb_wgs84 = tile.aabb_wgs84;
        if (!atakmap::math::Rectangle<double>::contains(aabb_wgs84.minX,
                                                        aabb_wgs84.minY,
                                                        aabb_wgs84.maxX,
                                                        aabb_wgs84.maxY,
                                                        longitude, latitude)) {
            return TE_Done;
        }

        // the tile has no elevation data values...
        if (!tile.hasData) {
            // if on a border, continue as other border tile may have data, else break
            if(aabb_wgs84.minX < longitude && aabb_wgs84.maxX > longitude &&
                aabb_wgs84.minY < latitude && aabb_wgs84.maxY > latitude) {

                elevation = 0.0;
                return TE_Ok;
            } else {
                return TE_Done;
            }
        }
        if(!tile.heightmap) {
            // if there's no heightmap, shoot a nadir ray into the
            // terrain tile mesh and obtain the height at the
            // intersection
            Projection2Ptr proj(nullptr, nullptr);
            code = ProjectionFactory3_create(proj, tile.data.srid);
            TE_CHECKRETURN_CODE(code);

            Matrix2 invLocalFrame;
            tile.data.localFrame.createInverse(&invLocalFrame);

            // obtain the ellipsoid surface point
            TAK::Engine::Math::Point2<double> surface;
            code = proj->forward(&surface, GeoPoint2(latitude, longitude));
            TE_CHECKRETURN_CODE(code);

            invLocalFrame.transform(&surface, surface);

            // obtain the point at altitude
            TAK::Engine::Math::Point2<double> above;
            code = proj->forward(&above, GeoPoint2(latitude, longitude, 30000.0, TAK::Engine::Core::AltitudeReference::HAE));
            TE_CHECKRETURN_CODE(code);

            invLocalFrame.transform(&above, above);

            // construct the geometry model and compute the intersection
            TAK::Engine::Math::Mesh model(tile.data.value, nullptr);

            TAK::Engine::Math::Point2<double> isect;
            if (!model.intersect(&isect, Ray2<double>(above, Vector4<double>(surface.x - above.x, surface.y - above.y, surface.z - above.z))))
                return TE_Done;

            tile.data.localFrame.transform(&isect, isect);
            GeoPoint2 geoIsect;
            code = proj->inverse(&geoIsect, isect);
            TE_CHECKRETURN_CODE(code);

            elevation = geoIsect.altitude;
            code = TE_Ok;
        } else {
            // do a heightmap lookup
            const double postSpaceX = (aabb_wgs84.maxX-aabb_wgs84.minX) / (tile.posts_x-1u);
            const double postSpaceY = (aabb_wgs84.maxY-aabb_wgs84.minY) / (tile.posts_y-1u);

            const double postX = (longitude-aabb_wgs84.minX)/postSpaceX;
            const double postY = tile.invert_y_axis ?
                (latitude-aabb_wgs84.minY)/postSpaceY :
                (aabb_wgs84.maxY-latitude)/postSpaceY ;

            const auto postL = static_cast<std::size_t>(MathUtils_clamp((int)postX, 0, (int)(tile.posts_x-1u)));
            const auto postR = static_cast<std::size_t>(MathUtils_clamp((int)ceil(postX), 0, (int)(tile.posts_x-1u)));
            const auto postT = static_cast<std::size_t>(MathUtils_clamp((int)postY, 0, (int)(tile.posts_y-1u)));
            const auto postB = static_cast<std::size_t>(MathUtils_clamp((int)ceil(postY), 0, (int)(tile.posts_y-1u)));

            TAK::Engine::Math::Point2<double> p;

            // obtain the four surrounding posts to interpolate from
            tile.data.value->getPosition(&p, (postT*tile.posts_x)+postL);
            const double ul = p.z;
            tile.data.value->getPosition(&p, (postT*tile.posts_x)+postR);
            const double ur = p.z;
            tile.data.value->getPosition(&p, (postB*tile.posts_x)+postR);
            const double lr = p.z;
            tile.data.value->getPosition(&p, (postB*tile.posts_x)+postL);
            const double ll = p.z;

            // interpolate the height
            p.z = MathUtils_interpolate(ul, ur, lr, ll,
                    MathUtils_clamp(postX-(double)postL, 0.0, 1.0),
                    MathUtils_clamp(postY-(double)postT, 0.0, 1.0));
            // transform the height back to HAE
            tile.data.localFrame.transform(&p, p);
            elevation = p.z;
            code = TE_Ok;
        }

        *value = elevation;

        return code;
    }
    TAKErr BuilderQueryContext::getElevation(double* value, const double latitude, const double longitude) NOTHROWS
    {
        *value = NAN;
        if (terrainTiles.last && ::getElevation(value, latitude, longitude, *terrainTiles.last) == TE_Ok)
            return TE_Ok;
        else if(terrainTiles.last)
            terrainTiles.last.reset();
        for (const auto &tile : terrainTiles.value) {
            if (::getElevation(value, latitude, longitude, *tile) == TE_Ok) {
                terrainTiles.last = tile;
                return TE_Ok;
            }
        }

        return TE_Done;
    }
    TAKErr BuilderQueryContext::getIcon(GLuint *id, float *u0, float *v0, float *u1, float *v1, std::size_t *w, std::size_t *h, float *rotation, bool *isAbsoluteRotation, const char* uri) NOTHROWS
    {
        if (!uri)
            return TE_InvalidArg;

        const auto& entry = iconEntryCache.find(uri);
        if (entry != iconEntryCache.end()) {
            *id = entry->second.texid;
            *u0 = entry->second.u0;
            *v0 = entry->second.v0;
            *u1 = entry->second.u1;
            *v1 = entry->second.v1;
            *w = entry->second.width;
            *h = entry->second.height;
            *rotation = entry->second.rotation;
            *isAbsoluteRotation = entry->second.isAbsoluteRotation;
            return TE_Ok;
        }
        BuilderCallbackBundle arg;
        arg.iconUri = uri;
        arg.ctx = &ctx;

        CallbackOpaque opaque;
        opaque.bundle = &arg;
        opaque.cbex = cbex;

        if (ctx.isRenderThread()) {
            cb_getIconImpl(&opaque);
        } else {
            std::unique_ptr<void, void(*)(const void*)> glopaque(nullptr, nullptr);
            const TAKErr code = cb_alloc.allocate(glopaque);
            TE_CHECKRETURN_CODE(code);
            *(static_cast<CallbackOpaque*>(glopaque.get())) = opaque;
            ctx.queueEvent(cb_getIconImpl, std::move(glopaque));
            {
                Monitor::Lock lock(cbex->monitor);
                do {
                    if (arg.done)
                        break;
                    cbex->terminated = (initCtxId != activeCtxId);
                    if (cbex->terminated)
                        break;
                    lock.wait(500LL);
                } while(true);
            }
        }
        if (!arg.done)
            return TE_Interrupted;
        if (!arg.icon.texid)
            return TE_Busy;
        *id = arg.icon.texid;
        *u0 = arg.icon.u0;
        *v0 = arg.icon.v0;
        *u1 = arg.icon.u1;
        *v1 = arg.icon.v1;
        *w = arg.icon.width;
        *h = arg.icon.height;
        *rotation = arg.icon.rotation;
        *isAbsoluteRotation = arg.icon.isAbsoluteRotation;
        iconEntryCache[uri] = arg.icon;
        return TE_Ok;
    }
    TAKErr BuilderQueryContext::addLabel(const GLLabel &lbl_) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if (!current)
            return TE_IllegalState;
        if (!labelManager)
            return TE_IllegalState;

        GLLabel lbl(lbl_);
        const auto lblid = labelManager->addLabel(lbl);
        if (!current->labels.id)
            current->labels.id = lblid;
        else
            current->labels.overflow.push_back(lblid);
        return TE_Ok;
    }
    uint32_t BuilderQueryContext::reserveHitId() NOTHROWS
    {
        return current ? current->hitid : 0u;
    }

    void hitTest(void *params) NOTHROWS {
        auto *hitTestParams = static_cast<HitTestParams*>(params);

        Monitor::Lock lock(hitTestParams->callbackExchange.monitor);
        if (hitTestParams->callbackExchange.terminated)
            return;
        TAK::Engine::Port::STLVectorAdapter<uint32_t> htids_a(*hitTestParams->featureIds);
        hitTestParams->renderer->hitTest(htids_a, *hitTestParams->view, hitTestParams->screenX, hitTestParams->screenY);
        hitTestParams->done = true;
        lock.signal();
    }
}
