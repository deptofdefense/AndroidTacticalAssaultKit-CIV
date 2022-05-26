
#include "renderer/model/GLC3DTRenderer.h"
#include "renderer/GLWorkers.h"
#include "util/Tasking.h"
#include "formats/cesium3dtiles/C3DTTileset.h"
#include "formats/cesium3dtiles/B3DM.h"
#include "util/URI.h"
#include "model/Cesium3DTilesSceneSpi.h"
#include "math/Plane2.h"
#include "port/StringBuilder.h"
#include "renderer/model/GLBatch.h"
#include "port/Collection.h"
#include "renderer/BitmapFactory2.h"
#include "thread/Mutex.h"
#include "math/Vector4.h"
#include "math/Rectangle2.h"
#include "util/MathUtils.h"
#include "renderer/core/GLContent.h"
#include "math/Frustum2.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/model/SceneObjectControl.h"
#include "util/URIOfflineCache.h"
#include "util/ConfigOptions.h"
#include "renderer/core/GLContent.h"
#include "core/ProjectionFactory3.h"

using namespace TAK::Engine::Renderer::Model;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Core;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Formats::Cesium3DTiles;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Math;

#define C3DT_DEBUG_RESOURCE_RELEASE  0

// extern of a helper to avoid double parsing of B3DM data
namespace TAK {
    namespace Engine {
        namespace Model {
            extern TAKErr C3DT_parseB3DMScene(ScenePtr& scenePtr, SceneInfoPtr& sceneInfoPtr, DataInput2* input, 
                const char* baseURI, const char* URI) NOTHROWS;
            extern TAKErr C3DT_parsePNTSScene(ScenePtr& scenePtr, SceneInfoPtr& sceneInfoPtr, DataInput2* input,
                const char* baseURI, const char* URI) NOTHROWS;
        }
    }
}

namespace {
    class GLC3DTTileset;
    struct TilesetImpl;
    class GLC3DTTile;

    constexpr double maxScreenSpaceError = 16.0;

    int64_t getConfiguredCacheSizeLimit() NOTHROWS;
    int64_t getConfiguredCacheDurationSeconds() NOTHROWS;

    TAKErr loadBitmapTask(std::shared_ptr<TAK::Engine::Renderer::Bitmap2>& result, TAK::Engine::Port::String& URI, const std::shared_ptr<URIOfflineCache>& cache,
        int64_t cacheDurationSeconds) NOTHROWS;
    TAKErr loadMeshBufferBitmap(std::shared_ptr<TAK::Engine::Renderer::Bitmap2>& result, const std::shared_ptr<const Mesh>& mesh, size_t bufferIndex) NOTHROWS;

    // base for all C3DT Content types
    class GLC3DTContentRenderable : public GLMapRenderable2, public GLDepthSamplerDrawable {
    public:
        virtual void onTileContentLoaded(GLC3DTTile* parentTile) NOTHROWS {}
        virtual void onRootContentLoaded(GLC3DTRenderer* renderer) NOTHROWS {}
    };

    class GLTileset : public GLC3DTContentRenderable {
    public:
        GLTileset(std::shared_ptr<GLBatch> batch) NOTHROWS;
        ~GLTileset() NOTHROWS override;
        void draw(const GLGlobeBase& view, const int renderPass) NOTHROWS override;
        void release() NOTHROWS override {}
        int getRenderPass() NOTHROWS override { return 0; }
        void start() NOTHROWS override {}
        void stop() NOTHROWS override {}

        void onTileContentLoaded(GLC3DTTile* parentTile) NOTHROWS override;

        TAKErr gatherDepthSamplerDrawables(std::vector<GLDepthSamplerDrawable*>& result, int levelDepth, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS override;
        void depthSamplerDraw(GLDepthSampler& sampler, const TAK::Engine::Core::MapSceneModel2& sceneModel) NOTHROWS override;

        std::shared_ptr<GLBatch> batch_;
        GLC3DTTile* parent_ = nullptr;
    };

    struct UpdateLists {
        std::vector<GLC3DTTile*> visible_list;
        std::vector<GLC3DTTile*> unload_list;
        std::vector<GLC3DTTile*> load_list;
        bool follow_up = false;

        void clear() {
            visible_list.clear();
            unload_list.clear();
            load_list.clear();
            follow_up = false;
        }
    };

    struct ViewState {
        ViewState()
            : drawSrid(-1) {}

        ViewState(int drawSrid, const MapSceneModel2& scene)
            : scene(scene),
            drawSrid(drawSrid)
        {}

        MapSceneModel2 scene;
        int drawSrid;
    };

    class GLC3DTTile {
    public:
        enum State {
            NONE,
            CULLED,
            RENDERED
        };

        GLC3DTTile(GLC3DTTileset* tileset, const TAK::Engine::Formats::Cesium3DTiles::C3DTTile& tile, GLC3DTTile* parentGLTile);

        ~GLC3DTTile() NOTHROWS;

        void draw(const GLGlobeBase& view, const int renderPass, GLContentContext& content_context) NOTHROWS;
        void startContentLoad() NOTHROWS;
        void unloadContent() NOTHROWS;

        TAKErr gatherDepthSamplerDrawables(std::vector<GLDepthSamplerDrawable*>& result, int levelDepth, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS;
        
        TAK::Engine::Feature::Envelope2 aabb;
        GeoPoint2 centroid;
        double radius_;
        double paddedRadius_;
        
        double geometric_error_;
        uint64_t last_state_frame_;

        std::weak_ptr<TilesetImpl> tileset_;
        GLC3DTTile* parent_;
        String content_uri_;

        GLC3DTTile* first_child_;
        GLC3DTTile* last_child_;
        GLContentHolder content_;

        Matrix2 transform_;
        uint8_t refine_;
        uint8_t last_state_;
        
        inline size_t childCount() const NOTHROWS {
            return static_cast<size_t>(last_child_ - first_child_);
        }

        bool isRenderable() const NOTHROWS;

        inline State getState(uint64_t frame) const {
            if (last_state_frame_ == frame)
                return static_cast<State>(last_state_);
            return NONE;
        }

        inline C3DTRefine getRefine() const NOTHROWS {
            return static_cast<C3DTRefine>(refine_);
        }

        inline void setState(State state, uint64_t frame) NOTHROWS {
            this->last_state_frame_ = frame;
            this->last_state_ = state;
        }

        inline bool hasPossibleContent() const NOTHROWS {
            return content_uri_.get() != nullptr;
        }
    };

    //
    // C3DTAllocator
    //

    class C3DTAllocator {
    public:
        static const size_t DEFAULT_BLOCK_SIZE_ = sizeof(GLC3DTTile) * 1000;

        C3DTAllocator()
            : blocks_(nullptr), large_blocks_(nullptr) {}

        ~C3DTAllocator() {
            freeBlocks(blocks_);
            freeBlocks(large_blocks_);
        }

        GLC3DTTile* allocTiles(size_t count) NOTHROWS {

            if (!count)
                return nullptr;

            size_t sizeNeeded = sizeof(GLC3DTTile) * count;
            
            GLC3DTTile* r = reinterpret_cast<GLC3DTTile*>(allocContig(sizeNeeded));
            return r;
        }

    private:
        struct Block_ {
            Block_* next;
            uint8_t* pos;
            uint8_t* end;
        };

    private:
        void* allocContig(size_t sizeNeeded) NOTHROWS {
            Block_* block = blocks_;

            // need a new block?
            if (!block || static_cast<size_t>(blocks_->end - blocks_->pos) < sizeNeeded) {
                if (sizeNeeded > DEFAULT_BLOCK_SIZE_) {
                    block = allocBlock(sizeNeeded);
                    block->next = large_blocks_;
                    large_blocks_ = block;
                }
                else {
                    block = allocBlock(DEFAULT_BLOCK_SIZE_);
                    block->next = blocks_;
                    blocks_ = block;
                }
            }

            void* r = block->pos;
            block->pos += sizeNeeded;
            return r;
        }

        Block_* allocBlock(size_t size) NOTHROWS {
            Block_* b = static_cast<Block_*>(malloc(sizeof(Block_) + size));
            b->pos = reinterpret_cast<uint8_t*>(b) + sizeof(Block_);
            b->end = b->pos + size;
            return b;
        }

        void freeBlocks(Block_* b) NOTHROWS {
            while (b) {
                Block_* n = b->next;
                free(b);
                b = n;
            }
        }

    private:
        Block_* blocks_;
        Block_* large_blocks_;
    };

    //
    // TilesetImpl
    //

    struct TilesetImpl {

        TilesetImpl(TAK::Engine::Core::RenderContext& ctx, const char* baseURI, std::shared_ptr<GLContentContext::Loader> loader);

        C3DTAllocator allocator_; // allocator must come first

        // tile that owns the sub tileset content
        GLC3DTTile* owning_tile_;

        // Non-transient state
        GLC3DTRenderer* renderer_;
        String base_uri_;
        std::unique_ptr<GLC3DTTile> root_tile_;
        SharedWorkerPtr view_update_worker_;
        const uint64_t* desired_view_update_num_;

        // Only touched by the GLThread
        GLContentContext content_context_;
        MapCamera2 current_camera_;
        std::unique_ptr<UpdateLists> front_lists_;
        std::unique_ptr<UpdateLists> recycle_state_;

        // Only touched by the view update worker
        uint64_t last_completed_frame_num_;
        std::unique_ptr<UpdateLists> pending_state_;

        bool updateView(const ViewState& view) NOTHROWS;

        bool updateTile(const ViewState& view, GLC3DTTile& tile, bool skipMaxSSESphere) NOTHROWS;
        bool cullTile(const ViewState& view, GLC3DTTile& tile) NOTHROWS;
        void releaseTile(GLC3DTTile& tile) NOTHROWS;
    };

    //
    // GLC3DTTileset
    //

    class GLC3DTTileset : public GLC3DTContentRenderable {
    public:
        GLC3DTTileset(TAK::Engine::Core::RenderContext& ctx, const char* baseURI, std::shared_ptr<GLContentContext::Loader> loader);
        virtual ~GLC3DTTileset() NOTHROWS;

        void draw(const GLGlobeBase& view, const int renderPass) NOTHROWS;
        void release() NOTHROWS {}
        int getRenderPass() NOTHROWS { return 0; }
        void start() NOTHROWS {}
        void stop() NOTHROWS {}
        virtual TAKErr gatherDepthSamplerDrawables(std::vector<GLDepthSamplerDrawable*>& result, int levelDepth, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS;
        virtual void onTileContentLoaded(GLC3DTTile* parentTile) NOTHROWS override;
        virtual void onRootContentLoaded(GLC3DTRenderer* renderer) NOTHROWS override;

        std::shared_ptr<TilesetImpl> state_;

        static TAKErr updateViewTask(
            std::unique_ptr<UpdateLists>& result, 
            const std::shared_ptr<TilesetImpl>& ts,
            const ViewState& view,
            std::unique_ptr<UpdateLists>& recycle) NOTHROWS;

        static TAKErr receiveUpdateTask(bool&, std::unique_ptr<UpdateLists>& result, const std::shared_ptr<TilesetImpl>& ts) NOTHROWS;
    };

    //
    // TilesetParser
    //

    struct TilesetParser {
    
        struct Frame_ {
            GLC3DTTile* glTile;
            const C3DTTile* tile;
        };
        
        TilesetParser(TAK::Engine::Core::RenderContext& ctx, const char* URI, std::shared_ptr<GLContentContext::Loader> loader);

        inline GLC3DTTile* parent() {
            return stack.size() ? stack.back().glTile : nullptr;
        }

        TAKErr handle(const C3DTTile& tile);
        static TAKErr visitor(void* opaque, const C3DTTileset* tileset, const C3DTTile* tile);

        std::unique_ptr<GLC3DTTileset> tileset;
        std::vector<Frame_> stack;
    };

    //
    // C3DTTilesetTextureLoader
    //

    class C3DTTilesetTextureLoader : public MaterialManager::TextureLoader {
    public:
        C3DTTilesetTextureLoader(const std::shared_ptr<const Mesh>& mesh) NOTHROWS;
        ~C3DTTilesetTextureLoader() NOTHROWS override;
        TAKErr load(TAK::Engine::Util::FutureTask<std::shared_ptr<TAK::Engine::Renderer::Bitmap2>>& value, const char* uri) NOTHROWS override;
    private:
        std::shared_ptr<const Mesh> mesh;
    };

    const SharedWorkerPtr createPoolLoadWorker() {
        SharedWorkerPtr worker;
        Worker_createThreadPool(worker, 0, 4, 60 * 1000);
        return worker;
    }

    const SharedWorkerPtr createSingleWorker() {
        SharedWorkerPtr worker;
        Worker_createThread(worker);
        return worker;
    }

    const SharedWorkerPtr& tilesetLoadWorker() NOTHROWS {
#if 0
        return GeneralWorkers_flex();
#else
        static SharedWorkerPtr inst(createPoolLoadWorker());
        return inst;
#endif
    }

    const SharedWorkerPtr& textureLoadWorker() NOTHROWS {
#if 0
        return GeneralWorkers_flex();
#else
        // could tileset load and texture load share a pool???
        // XXX-- experiment with optimal config as a TODO
        static SharedWorkerPtr inst(createPoolLoadWorker());
        return inst;
#endif
    }

    const SharedWorkerPtr& updateViewWorker() NOTHROWS {
        // Very important that this is a serial worker and not a pool
        static SharedWorkerPtr inst(createSingleWorker());
        return inst;
    }
}

//
// GLC3DTilesetRenderer
//

class GLC3DTRenderer::SceneControlImpl : public SceneObjectControl
{
public:
    SceneControlImpl(GLC3DTRenderer& owner) NOTHROWS;
public:
    TAKErr setLocation(const GeoPoint2& location, const Matrix2* localFrame, const int srid, const TAK::Engine::Feature::AltitudeMode altitudeMode) NOTHROWS override;
    TAKErr getInfo(SceneInfo* value) NOTHROWS override;
    TAKErr addUpdateListener(UpdateListener* l) NOTHROWS override;
    TAKErr removeUpdateListener(const UpdateListener& l) NOTHROWS override;
    TAKErr clampToGround() NOTHROWS override;
public:
    void dispatchBoundsChanged(const TAK::Engine::Feature::Envelope2& aabb, const double minGsd, const double maxGsd) NOTHROWS;
    void dispatchClampToGroundOffsetComputed(const double offset) NOTHROWS;
private:
    GLC3DTRenderer& owner_;
    std::set<UpdateListener*> listeners_;
    Mutex mutex_;
};

class GLC3DTRenderer::LoaderImpl : public GLContentContext::Loader, public MaterialManager::TextureLoader {
public:
    explicit LoaderImpl(TAK::Engine::Core::RenderContext& ctx, std::shared_ptr<URIOfflineCache> cache, const char* URI) NOTHROWS
        : cache_(cache),
        parent_mm(std::make_shared<MaterialManager>(ctx, MaterialManager::TextureLoaderPtr(this, Memory_leaker_const<MaterialManager::TextureLoader>))),
        cache_dur_sec_(getConfiguredCacheDurationSeconds()),
        base_uri_(URI)
    {}

    ~LoaderImpl() NOTHROWS override {}

    virtual std::shared_ptr<GLContentContext::Loader> makeChildContentLoader(TAK::Engine::Core::RenderContext& ctx) NOTHROWS {
        return std::make_shared<LoaderImpl>(ctx, cache_, base_uri_);
    }

    virtual TAKErr open(DataInput2Ptr& input, const char* URI) NOTHROWS {
        if (cache_)
            cache_->open(input, URI, cache_dur_sec_);
        return URI_open(input, URI);
    }
    TAKErr load(TAK::Engine::Renderer::Core::GLMapRenderable2Ptr&, TAK::Engine::Core::RenderContext&, const TAK::Engine::Port::String&) NOTHROWS override;

    TAKErr load(TAK::Engine::Util::FutureTask<std::shared_ptr<Bitmap2>>& value, const char* uri) NOTHROWS override;

    TAKErr processTileSet(TAK::Engine::Renderer::Core::GLMapRenderable2Ptr& output, TAK::Engine::Core::RenderContext& ctx, ScenePtr& scene, const String& baseURI, const char* URI, bool isStreaming) NOTHROWS;
    TAKErr processTileSetMesh(const std::shared_ptr<const Mesh>& mesh, GLBatchBuilder& batchBuilder, TAK::Engine::Core::RenderContext& ctx, const std::shared_ptr<Scene>& scene, const String& baseURI, const char* URI, bool isStreaming, const char* anchorPath) NOTHROWS;
    TAKErr processTileSetChildren(SceneNode& node, GLBatchBuilder& batchBuilder, TAK::Engine::Core::RenderContext& ctx, const std::shared_ptr<Scene>& scene, const String& baseURI, const char* URI, bool isStreaming, const char* anchorPath) NOTHROWS;

    std::shared_ptr<MaterialManager> parent_mm;
    std::shared_ptr<URIOfflineCache> cache_;
    int64_t cache_dur_sec_;

    TAK::Engine::Thread::Mutex mutex_;
    std::unordered_map<std::string, std::weak_ptr<GLBatch::Texture>> resident_textures_;

    String base_uri_;
};

GLC3DTRenderer::GLC3DTRenderer(TAK::Engine::Core::RenderContext& ctx, const TAK::Engine::Model::SceneInfo& info) NOTHROWS 
 : GLC3DTRenderer(ctx, info, nullptr) { }

GLC3DTRenderer::GLC3DTRenderer(TAK::Engine::Core::RenderContext& ctx, const TAK::Engine::Model::SceneInfo& info, const char* cacheDir) NOTHROWS
    : loader_impl_(std::make_shared<LoaderImpl>(ctx, cacheDir ? std::make_shared<URIOfflineCache>(cacheDir, getConfiguredCacheSizeLimit()) : nullptr, info.uri)),
    root_content_(new GLContentHolder()),
    info_(info),
    uri_(info.uri),
    scene_control_(new SceneControlImpl(*this)) {
    content_context_.reset(new GLContentContext(ctx, tilesetLoadWorker(), loader_impl_));
}

GLC3DTRenderer::~GLC3DTRenderer() NOTHROWS {
    this->content_context_->cancelAll();
}

namespace {
    TAKErr rootContentDidLoadTask(bool&, GLMapRenderable2* input, GLC3DTRenderer* renderer) {

        // Safe to cast-- all C3DT content renderables share this common base
        GLC3DTContentRenderable* content = static_cast<GLC3DTContentRenderable*>(input);

        // apply once for content whose parent tile has a transform
        content->onRootContentLoaded(renderer);
        return TE_Ok;
    }

    static size_t render_context_slot_ = 0;

    TAKErr setRenderContextTask(bool&, RenderContext* cxt) {
        Worker_allocWorkerLocalStorageSlot(&render_context_slot_);
        Worker_setWorkerLocalStorage(render_context_slot_, cxt);
        return TE_Ok;
    }
}

void GLC3DTRenderer::draw(const GLGlobeBase& view, const int renderPass) NOTHROWS {

    if (!(renderPass & this->getRenderPass()))
        return;
   
    if (!render_context_slot_)
        Task_begin(GLWorkers_resourceLoad(), setRenderContextTask, &this->content_context_->getRenderContext());

    // load content if empty
    if (root_content_->getLoadState() == GLContentHolder::EMPTY) {
        root_content_->load(*content_context_, uri_)
            .thenOn(GeneralWorkers_immediate(), rootContentDidLoadTask, this);
    }

    // draw any pending indicators or loaded content
    GLMapRenderable2* renderable = root_content_->getMapRenderable();
    if (renderable)
        renderable->draw(view, renderPass);
}

/*TAKErr GLC3DTilesetRenderer::setLoadErrorTask(std::shared_ptr<GLC3DTTileset>& result, TAKErr err, GLC3DTilesetRenderer* thiz) NOTHROWS {
    return err;
}*/


void GLC3DTRenderer::release() NOTHROWS {
    this->content_context_->cancelAll();
    this->root_content_->unload();
}

int GLC3DTRenderer::getRenderPass() NOTHROWS {
    return GLMapView2::Scenes;
}

void GLC3DTRenderer::start() NOTHROWS {
    
}

void GLC3DTRenderer::stop() NOTHROWS {

}

TAKErr GLC3DTRenderer::getControl(void** ctrl, const char* type) NOTHROWS {
    if (!type)
        return TE_InvalidArg;
    if (strcmp(type, "TAK.Engine.Renderer.Model.SceneObjectControl") == 0) {
        *ctrl = scene_control_.get();
        return TE_Ok;
    }
    return TE_InvalidArg;
}

TAKErr GLC3DTRenderer::hitTest(GeoPoint2* value, const MapSceneModel2& sceneModel, const float screenX, const float screenY) NOTHROWS {
    GeoPoint2 hitGeo;
    TAKErr code = TE_Ok;

    if (content_context_->getRenderContext().isRenderThread())
    {
        code = depthTestTask(hitGeo, this, sceneModel, screenX, screenY);
        TE_CHECKRETURN_CODE(code);
    }
    else
    {
        TAKErr awaitCode = Task_begin(GLWorkers_glThread(), depthTestTask, this, sceneModel, screenX, screenY)
            .await(hitGeo, code);

        if (awaitCode != TE_Ok)
            return awaitCode;
    }

    if (code == TE_Ok)
        *value = hitGeo;

    return code;
}

TAKErr GLC3DTRenderer::depthTestTask(TAK::Engine::Core::GeoPoint2& value, GLC3DTRenderer* r, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS {

    TAKErr code = TE_Done;

    // Used only by the glThread and therefore statically cached for reuse, but as depth sampling grows in scope
    // in the renderer, should be moved to a shared place.
    static GLDepthSamplerPtr depth_sampler_(nullptr, nullptr);

    if (!depth_sampler_) {
        // For now, use (ENCODED_DEPTH_ENCODED_ID) method, since seems to get a 16-bit only depth buffer
        // the other way
        code = GLDepthSampler_create(depth_sampler_, r->content_context_->getRenderContext(), GLDepthSampler::ENCODED_DEPTH_ENCODED_ID);
        if (code != TE_Ok)
            return code;
    }

    float screenY = y;
    double pointZ = 0.0;
    Matrix2 projection;

    code = depth_sampler_->performDepthSample(&pointZ, &projection, nullptr, *r, sceneModel, x, screenY);
    if (code != TE_Ok)
        return code;

    TAK::Engine::Math::Point2<double> point(x, screenY, 0);
    projection.transform(&point, point);
    point.z = pointZ;

    Matrix2 mat;
    if (projection.createInverse(&mat) != TE_Ok)
        return TE_Done;

    mat.transform(&point, point);

    // ortho -> projection
    sceneModel.inverseTransform.transform(&point, point);
    // projection -> LLA
    code = sceneModel.projection->inverse(&value, point);

    return code;
}

TAKErr GLC3DTRenderer::gatherDepthSamplerDrawables(std::vector<GLDepthSamplerDrawable*>& result, int levelDepth, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS {

    TAKErr code(TE_Ok);
    if (root_content_->getLoadState() == GLContentHolder::LOADED) {
        GLC3DTContentRenderable* renderable = static_cast<GLC3DTContentRenderable*>(root_content_->getMapRenderable());
        if (renderable)
            renderable->gatherDepthSamplerDrawables(result, levelDepth, sceneModel, x, y);
    }
    return code;
}

void GLC3DTRenderer::depthSamplerDraw(TAK::Engine::Renderer::GLDepthSampler& sampler, const TAK::Engine::Core::MapSceneModel2& sceneModel) NOTHROWS {
    TAKErr code(TE_Ok);
    if (root_content_->getLoadState() == GLContentHolder::LOADED) {
        GLC3DTContentRenderable* renderable = static_cast<GLC3DTContentRenderable*>(root_content_->getMapRenderable());
        if (renderable)
            renderable->depthSamplerDraw(sampler, sceneModel);
    }
}

void GLC3DTRenderer::updateBounds(TAK::Engine::Feature::Envelope2& bounds) NOTHROWS {
    SceneInfo info = this->info_;
    this->scene_control_->dispatchBoundsChanged(bounds, info.minDisplayResolution, info.maxDisplayResolution);
}

GLC3DTRenderer::Spi::~Spi() NOTHROWS { }

TAKErr GLC3DTRenderer::Spi::create(GLMapRenderable2Ptr& value, RenderContext& ctx, const SceneInfo& info, const Options& opts) NOTHROWS {
    
    TAKErr code = TE_Unsupported;
    if (info.type != nullptr && strcmp(info.type, "Cesium3DTiles") == 0) {
        value = GLMapRenderable2Ptr(new GLC3DTRenderer(ctx, info, opts.cacheDir), Memory_deleter_const<GLMapRenderable2, GLC3DTRenderer>);
        code = TE_Ok;
    }
    return code;
}

GLC3DTRenderer::SceneControlImpl::SceneControlImpl(GLC3DTRenderer& owner) NOTHROWS
    : owner_(owner)
{}

TAKErr GLC3DTRenderer::SceneControlImpl::setLocation(const GeoPoint2& location, const Matrix2* localFrame, const int srid, const TAK::Engine::Feature::AltitudeMode altitudeMode) NOTHROWS {
    return TE_Unsupported;
}

TAKErr GLC3DTRenderer::SceneControlImpl::getInfo(SceneInfo* info) NOTHROWS {
    if (!info)
        return TE_InvalidArg;
    info = &owner_.info_;
    return TE_Ok;
}

TAKErr GLC3DTRenderer::SceneControlImpl::addUpdateListener(UpdateListener* l) NOTHROWS {
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    listeners_.insert(l);
    return code;
}
TAKErr GLC3DTRenderer::SceneControlImpl::removeUpdateListener(const UpdateListener& l) NOTHROWS {
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    listeners_.erase(&const_cast<UpdateListener&>(l));
    return code;
}
TAKErr GLC3DTRenderer::SceneControlImpl::clampToGround() NOTHROWS {
    return TE_Unsupported;
}

void GLC3DTRenderer::SceneControlImpl::dispatchBoundsChanged(const TAK::Engine::Feature::Envelope2& aabb, const double minGsd, const double maxGsd) NOTHROWS {
    Lock lock(mutex_);
    if (lock.status != TE_Ok)
        return;

    auto it = listeners_.begin();
    while (it != listeners_.end()) {
        if ((*it)->onBoundsChanged(aabb, minGsd, maxGsd) == TE_Done)
            it = listeners_.erase(it);
        else
            it++;
    }
}

void GLC3DTRenderer::SceneControlImpl::dispatchClampToGroundOffsetComputed(const double offset) NOTHROWS {
    Lock lock(mutex_);
    if (lock.status != TE_Ok)
        return;

    auto it = listeners_.begin();
    while (it != listeners_.end()) {
        if ((*it)->onClampToGroundOffsetComputed(offset) == TE_Done)
            it = listeners_.erase(it);
        else
            it++;
    }
}

namespace {

    int64_t getConfiguredCacheSizeLimit() NOTHROWS {
        String value;
        const int64_t defaultValue = 250 * 1024 * 1024; // 250MB
        ConfigOptions_getOption(value, "glc3dtrenderer.cache-size-limit");
        if (!value)
            return defaultValue;
        char* end = nullptr;
        int64_t result = static_cast<int64_t>(strtoll(value, &end, 10));
        if (end == value.get())
            return defaultValue;
        return result;
    }

    int64_t getConfiguredCacheDurationSeconds() NOTHROWS {
        String value;
        const int64_t defaultValue = 60 * 60 * 24 * 10; // 10 days
        ConfigOptions_getOption(value, "glc3dtrenderer.cache-duration-seconds");
        if (!value)
            return defaultValue;
        char* end = nullptr;
        int64_t result = static_cast<int64_t>(strtoll(value, &end, 10));
        if (end == value.get())
            return defaultValue;
        return result;
    }

    static double dist(Point2<double> a, Point2<double> b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dz = b.z - a.z;
        return sqrt(dx * dx + dy * dy + dz * dz);
    }

    double computeMetersPerPixelPerspective(GLC3DTTile& tile, const MapSceneModel2& scene) NOTHROWS {
        // XXX - distance camera to object
        Point2<double> center;
        scene.forward(&center, tile.centroid);

        // distance of the camera to the centroid of the tile
        double dx = center.x * scene.displayModel->projectionXToNominalMeters - scene.camera.location.x * scene.displayModel->projectionXToNominalMeters;
        double dy = center.y * scene.displayModel->projectionYToNominalMeters - scene.camera.location.y * scene.displayModel->projectionYToNominalMeters;
        double dz = center.z * scene.displayModel->projectionZToNominalMeters - scene.camera.location.z * scene.displayModel->projectionZToNominalMeters;
        
        double dcam = sqrt(dx * dx + dy * dy + dz * dz);

        double metersPerPixelAtD = (2.0 * dcam * tan(scene.camera.fov / 2.0) / ((scene.height) / 2.0));
        // if bounding sphere does not contain camera, compute meters-per-pixel at centroid,
        // else use nominal meters-per-pixel
        if (dcam <= tile.radius_) {
            metersPerPixelAtD = std::min(scene.gsd, metersPerPixelAtD);
        }
        return metersPerPixelAtD;
    }

    double computeMetersPerPixelOrtho(GLC3DTTile& tile, const MapSceneModel2& scene) {
        // compute using perspective method up front
        double metersPerPixelAtD = computeMetersPerPixelPerspective(tile, scene);

        // *** this workaround will be OBE after implementing perspective camera ***
        // XXX - the camera location for the ortho projection is not
        //       appropriate for a perspective camera model. as a result, I am
        //       observing bad meter-per-pixel calculations for meshes that are
        //       on screen and close to the camera. The observation is a
        //       resolution "donut" where low resolutions are selected at the
        //       top and bottom of the screen and high resolutions are selected
        //       in the center. the workaround employed below projects the AABB
        //       of the mesh into screen space and checks for intersection with
        //       the bottom half of the viewport -- if intersection we set the
        //       resolution to the nominal GSD.
        //
        //       I tried a number of novel approaches, including calculated
        //       depth values, but was unable to find any other workable
        //       heuristic or metric.
        //
        if (metersPerPixelAtD <= scene.gsd || (tile.radius_ / scene.gsd) < (std::max(scene.height, scene.width) / 2.0))
            return metersPerPixelAtD;

        const double viewCenterY = scene.height / 2.0;

        double screenMinX;
        double screenMinY;
        double screenMaxX;
        double screenMaxY;

        //if projected location of AABB intersects lower half of screen, use nominal GSD, else use perspective method

        // NOTE: this is pretty slow, especially for scenes with many tiles.

        TAK::Engine::Feature::Envelope2 aabb = tile.aabb;
        
        // UL
        GeoPoint2 scratchGeo;
        Point2<double> scratchPoint;

        scratchGeo.latitude = aabb.maxY;
        scratchGeo.longitude = aabb.minX;
        scratchGeo.altitude = aabb.minZ;
        scene.forward(&scratchPoint, scratchGeo);
        screenMinX = scratchPoint.x;
        screenMinY = scratchPoint.y;
        screenMaxX = scratchPoint.x;
        screenMaxY = scratchPoint.y;
        bool rinter = false;
        Rectangle2_intersects(rinter, screenMinX, screenMinY, screenMaxX, screenMaxY, 0.0, 0.0, (double)scene.width, (double)scene.height);
        if (rinter) {
            metersPerPixelAtD = scene.gsd;
            return metersPerPixelAtD;
        }
        scratchGeo.latitude = aabb.maxY;
        scratchGeo.longitude = aabb.minX;
        scratchGeo.altitude = aabb.maxZ;
        scene.forward(&scratchPoint, scratchGeo);
        screenMinX = std::min(screenMinX, scratchPoint.x);
        screenMinY = std::min(screenMinY, scratchPoint.y);
        screenMaxX = std::max(screenMaxX, scratchPoint.x);
        screenMaxY = std::max(screenMaxY, scratchPoint.y);
        rinter = false;
        Rectangle2_intersects(rinter, screenMinX, screenMinY, screenMaxX, screenMaxY, 0.0, 0.0, (double)scene.width, (double)scene.height);
        if (rinter) {
            metersPerPixelAtD = scene.gsd;
            return metersPerPixelAtD;
        }
        // LR
        scratchGeo.latitude = aabb.minY;
        scratchGeo.longitude = aabb.maxX;
        scratchGeo.altitude = aabb.minZ;
        scene.forward(&scratchPoint, scratchGeo);
        screenMinX = std::min(screenMinX, scratchPoint.x);
        screenMinY = std::min(screenMinY, scratchPoint.y);
        screenMaxX = std::max(screenMaxX, scratchPoint.x);
        screenMaxY = std::max(screenMaxY, scratchPoint.y);
        rinter = false;
        Rectangle2_intersects(rinter, screenMinX, screenMinY, screenMaxX, screenMaxY, 0.0, 0.0, (double)scene.width, (double)scene.height);
        if (rinter) {
            metersPerPixelAtD = scene.gsd;
            return metersPerPixelAtD;
        }

        scratchGeo.latitude = aabb.minY;
        scratchGeo.longitude = aabb.maxX;
        scratchGeo.altitude = aabb.maxZ;
        scene.forward(&scratchPoint, scratchGeo);
        screenMinX = std::min(screenMinX, scratchPoint.x);
        screenMinY = std::min(screenMinY, scratchPoint.y);
        screenMaxX = std::max(screenMaxX, scratchPoint.x);
        screenMaxY = std::max(screenMaxY, scratchPoint.y);
        rinter = false;
        Rectangle2_intersects(rinter, screenMinX, screenMinY, screenMaxX, screenMaxY, 0.0, 0.0, (double)scene.width, (double)scene.height);
        if (rinter) {
            metersPerPixelAtD = scene.gsd;
            return metersPerPixelAtD;
        }

        // UR
        scratchGeo.latitude = aabb.maxY;
        scratchGeo.longitude = aabb.maxX;
        scratchGeo.altitude = aabb.minZ;
        scene.forward(&scratchPoint, scratchGeo);
        screenMinX = std::min(screenMinX, scratchPoint.x);
        screenMinY = std::min(screenMinY, scratchPoint.y);
        screenMaxX = std::max(screenMaxX, scratchPoint.x);
        screenMaxY = std::max(screenMaxY, scratchPoint.y);
        rinter = false;
        Rectangle2_intersects(rinter, screenMinX, screenMinY, screenMaxX, screenMaxY, 0.0, 0.0, (double)scene.width, (double)scene.height);
        if (rinter) {
            metersPerPixelAtD = scene.gsd;
            return metersPerPixelAtD;
        }
        scratchGeo.latitude = aabb.maxY;
        scratchGeo.longitude = aabb.maxX;
        scratchGeo.altitude = aabb.maxZ;
        scene.forward(&scratchPoint, scratchGeo);
        screenMinX = std::min(screenMinX, scratchPoint.x);
        screenMinY = std::min(screenMinY, scratchPoint.y);
        screenMaxX = std::max(screenMaxX, scratchPoint.x);
        screenMaxY = std::max(screenMaxY, scratchPoint.y);
        rinter = false;
        Rectangle2_intersects(rinter, screenMinX, screenMinY, screenMaxX, screenMaxY, 0.0, 0.0, (double)scene.width, (double)scene.height);
        if (rinter) {
            metersPerPixelAtD = scene.gsd;
            return metersPerPixelAtD;
        }
        // LL
        scratchGeo.latitude = aabb.minY;
        scratchGeo.longitude = aabb.minX;
        scratchGeo.altitude = aabb.minZ;
        scene.forward(&scratchPoint, scratchGeo);
        screenMinX = std::min(screenMinX, scratchPoint.x);
        screenMinY = std::min(screenMinY, scratchPoint.y);
        screenMaxX = std::max(screenMaxX, scratchPoint.x);
        screenMaxY = std::max(screenMaxY, scratchPoint.y);
        rinter = false;
        Rectangle2_intersects(rinter, screenMinX, screenMinY, screenMaxX, screenMaxY, 0.0, 0.0, (double)scene.width, (double)scene.height);
        if (rinter) {
            metersPerPixelAtD = scene.gsd;
            return metersPerPixelAtD;
        }
        scratchGeo.latitude = aabb.minY;
        scratchGeo.longitude = aabb.minX;
        scratchGeo.altitude = aabb.maxZ;
        scene.forward(&scratchPoint, scratchGeo);
        screenMinX = std::min(screenMinX, scratchPoint.x);
        screenMinY = std::min(screenMinY, scratchPoint.y);
        screenMaxX = std::max(screenMaxX, scratchPoint.x);
        screenMaxY = std::max(screenMaxY, scratchPoint.y);
        rinter = false;
        Rectangle2_intersects(rinter, screenMinX, screenMinY, screenMaxX, screenMaxY, 0.0, 0.0, (double)scene.width, (double)scene.height);
        if (rinter) {
            metersPerPixelAtD = scene.gsd;
            return metersPerPixelAtD;
        }

        return metersPerPixelAtD;
    }

    double computeMetersPerPixel(GLC3DTTile& tile, const MapSceneModel2& scene) NOTHROWS {
        if (scene.camera.mode == MapCamera2::Perspective) {
            return computeMetersPerPixelPerspective(tile, scene);
        }
        return computeMetersPerPixelOrtho(tile, scene);
    }

    //
    // GLC3DTTileset
    //

    GLC3DTTileset::GLC3DTTileset(TAK::Engine::Core::RenderContext& ctx, const char* baseURI, std::shared_ptr<GLContentContext::Loader> loader)
        : state_(new TilesetImpl(ctx, baseURI, loader))
    {}
    
    TilesetImpl::TilesetImpl(TAK::Engine::Core::RenderContext& ctx, const char* baseURI, std::shared_ptr<GLContentContext::Loader> loader)
        : base_uri_(baseURI),
        content_context_(ctx, tilesetLoadWorker(), loader),
        last_completed_frame_num_(0),
        owning_tile_(nullptr),
        renderer_(nullptr) {

        // ensures that only 1 view update is happening at a time and that each 
        // scheduled update increments desired_view_update_num_ which can safely be read in updateView()
        // to determine if the update is OBE.
        Worker_createOverrideTasker(this->view_update_worker_, &this->desired_view_update_num_, updateViewWorker());
    }

    GLC3DTTileset::~GLC3DTTileset() NOTHROWS {
        state_->content_context_.cancelAll();
    }

    TAKErr GLC3DTTileset::gatherDepthSamplerDrawables(std::vector<GLDepthSamplerDrawable*>& result, int levelDepth, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS {
        if (state_->front_lists_) {
            for (GLC3DTTile* tile : state_->front_lists_->visible_list)
                tile->gatherDepthSamplerDrawables(result, levelDepth, sceneModel, x, y);
        }
        return TE_Ok;
    }

    TAKErr GLC3DTTileset::updateViewTask(std::unique_ptr<UpdateLists>& result, const std::shared_ptr<TilesetImpl>& ts, 
        const ViewState& view,
        std::unique_ptr<UpdateLists>& recycle) NOTHROWS {

        if (recycle)
            ts->pending_state_ = std::move(recycle);

        bool complete = ts->updateView(view);
        if (complete) {

            for (auto& p : ts->pending_state_->unload_list) {
                // mark each as culled
                p->setState(GLC3DTTile::CULLED, ts->last_completed_frame_num_);
            }

            result = std::move(ts->pending_state_);
        }

        return TE_Ok;
    }

    TAKErr GLC3DTTileset::receiveUpdateTask(bool& changed, std::unique_ptr<UpdateLists>& result, const std::shared_ptr<TilesetImpl>& ts) NOTHROWS {

        changed = false;
        if (result) {
            ts->recycle_state_ = std::move(ts->front_lists_);
            ts->front_lists_ = std::move(result);

            // do loads/unloads
            for (GLC3DTTile* tile : ts->front_lists_->unload_list)
                tile->unloadContent();
            for (GLC3DTTile* tile : ts->front_lists_->load_list)
                tile->startContentLoad();
            
            changed = true;
        }

        return TE_Ok;
    }

    void GLC3DTTileset::onTileContentLoaded(GLC3DTTile* parent) NOTHROWS {
        if (parent) {

            std::shared_ptr<TilesetImpl> ts = parent->tileset_.lock();
            state_->owning_tile_ = parent;

            // If tileset still active, trigger a follow up after this child/content tileset has loaded
            if (ts) {
                state_->renderer_ = ts->renderer_;
                ts->front_lists_->follow_up = true;
            }
        }
    }

    void GLC3DTTileset::onRootContentLoaded(GLC3DTRenderer* renderer) NOTHROWS {
        state_->renderer_ = renderer;
        if (renderer) {
            renderer->updateBounds(state_->root_tile_->aabb);
        }
    }

    bool TilesetImpl::updateView(const ViewState& view) NOTHROWS {

            if (!this->root_tile_)
            return false;

        if (!this->pending_state_)
            this->pending_state_.reset(new UpdateLists());

        this->pending_state_->clear();
        bool updateResult = this->updateTile(view, *this->root_tile_, false);

        this->last_completed_frame_num_++;

        for (auto tile : this->pending_state_->visible_list) {
            // mark for needing load or verified as loaded
            if (tile->getState(this->last_completed_frame_num_ - 1) != GLC3DTTile::RENDERED) {
                this->pending_state_->load_list.push_back(tile);
            }
            tile->setState(GLC3DTTile::RENDERED, this->last_completed_frame_num_);
        }

        for (auto tile : this->pending_state_->unload_list) {
            tile->setState(GLC3DTTile::CULLED, this->last_completed_frame_num_);
        }

        return true;
    }

    TAKErr contentDidLoadTask(bool&, GLMapRenderable2* input, const std::shared_ptr<TilesetImpl>& ts, GLC3DTTile* parentTile) {

        // nothing to do
        if (!input || !ts)
            return TE_Ok;

        // All C3DT content renderables share this common base
        GLC3DTContentRenderable* content = static_cast<GLC3DTContentRenderable*>(input);

        // apply once for content whose parent tile has a transform
        content->onTileContentLoaded(parentTile);
        return TE_Ok;
    }

    bool TilesetImpl::cullTile(const ViewState& view, GLC3DTTile& tile) NOTHROWS {
        const double hradius = tile.paddedRadius_ / view.scene.gsd;
        const double vradius = (tile.aabb.maxZ - tile.aabb.minZ) / 2.0 / view.scene.gsd;

        Point2<double> scratchPoint;
        view.scene.forward(&scratchPoint, tile.centroid);
        const double cosTilt = cos((90.0 + view.scene.camera.elevation) * M_PI / 180.0);
        const double screenMinX = scratchPoint.x - hradius;
        const double screenMinY = scratchPoint.y - std::min((cosTilt * hradius + (1 - cosTilt) * vradius), hradius);
        const double screenMaxX = scratchPoint.x + hradius;
        const double screenMaxY = scratchPoint.y + std::min((cosTilt * hradius + (1 - cosTilt) * vradius), hradius);

        bool rint = false;
        Rectangle2_intersects(rint,
            0.0, 0.0, (double)view.scene.width, (double)view.scene.height,
            screenMinX, screenMinY, screenMaxX, screenMaxY);
        return !rint;
    }

    void TilesetImpl::releaseTile(GLC3DTTile& tile) NOTHROWS {
        GLC3DTTile::State lastState = tile.getState(this->last_completed_frame_num_);
        if (lastState == GLC3DTTile::RENDERED) {
            this->pending_state_->unload_list.push_back(&tile);
            size_t numChildren = tile.childCount();
            for (size_t i = 0; i < numChildren; ++i) {
                GLC3DTTile& child = tile.first_child_[i];
                releaseTile(child);
            }
        }
    }

    bool TilesetImpl::updateTile(const ViewState& camera, GLC3DTTile& tile, bool skipMaxSSESphere) NOTHROWS {

        // do a quick check up front against nominal resolution
#if 1
        if (((tile.radius_ * 2.0) / camera.scene.gsd) < maxScreenSpaceError && !skipMaxSSESphere) {
            releaseTile(tile);
            return false;
        }
#endif

        // make sure tile is in bounds
        if (cullTile(camera, tile)) {
            releaseTile(tile);
            return false;
        }

        const double metersPerPixelAtD = computeMetersPerPixel(tile, camera.scene);
        const double spherePixelsAtD = (tile.radius_ * 2.0) / metersPerPixelAtD;

        //XXX- chokes out detail ADD refine items a too much
#if 0
        // if sphere at distance is less than max SSE, skip render
        if (spherePixelsAtD < maxScreenSpaceError && !skipMaxSSESphere) {
            releaseTile(tile);
            return false;
        }
#endif

        // estimate screen space error
        const double sse = tile.geometric_error_ / metersPerPixelAtD;

        // if SSE is sufficiently large, draw children
        bool drawSelf = true;
        size_t numChildren = tile.childCount();

        if (sse > maxScreenSpaceError) {
            
            int childrenToDraw = 0;
            int childrenDrawn = 0;

            for (size_t i = 0; i < numChildren; ++i) {
                GLC3DTTile& child = tile.first_child_[i];
                
                // if child does not intersect viewport, skip
                if (cullTile(camera, child)) {
                    releaseTile(child);
                    continue;
                }

                // draw the child, since we passed parent SSE test, the
                // child must draw its content if replace
                childrenToDraw++;
                bool childResult = updateTile(camera, child, (tile.getRefine() == C3DTRefine::Replace));
                if (childResult)
                    childrenDrawn++;
            }

            // we're drawing ourself if:
            // - refine mode is add
            // OR
            // - no children were drawn or not all visible children drew content
            drawSelf = (tile.getRefine() == C3DTRefine::Add) || (childrenToDraw == 0 || childrenDrawn < childrenToDraw);

        } else {
            for (size_t i = 0; i < numChildren; ++i) {
                GLC3DTTile& child = tile.first_child_[i];
                releaseTile(child);
            }
        }

        if (!drawSelf) {
            // content has been drawn, just not this tile's content
            return true;
        }

        bool selfDrawn = false;
        GLContentHolder::LoadState loadState = tile.content_.getLoadState();
        if (loadState == GLContentHolder::LOADED) {
            this->pending_state_->visible_list.push_back(&tile);
            selfDrawn = true;
        } else if (loadState != GLContentHolder::LOADING) {
            this->pending_state_->load_list.push_back(&tile);
        }

        return selfDrawn;

    }

    void GLC3DTTileset::draw(const GLGlobeBase& view, const int renderPass) NOTHROWS {
        if (state_->current_camera_.location != view.renderPass->scene.camera.location ||
            state_->current_camera_.target != view.renderPass->scene.camera.target ||
            (state_->front_lists_ && state_->front_lists_->follow_up)) {

            state_->current_camera_ = view.renderPass->scene.camera;

            ViewState viewState(view.renderPass->drawSrid, view.renderPass->scene);

            Task_begin(state_->view_update_worker_, updateViewTask, state_, viewState, std::move(state_->recycle_state_))
                .thenOn(GLWorkers_glThread(), receiveUpdateTask, state_);
        
            if (state_->front_lists_ && state_->front_lists_->follow_up)
                state_->front_lists_->follow_up = false;
        }

        if (state_->front_lists_) {

            // if any tiles are still loading, find a parent tile that this replaces so it can cover any bare patches
            
            GLC3DTTile* coverTile = nullptr;

            bool needsCover = state_->content_context_.hasPending() ||
                state_->front_lists_->visible_list.size() == 0;

            if (needsCover && state_->owning_tile_ &&
                state_->owning_tile_->getRefine() == C3DTRefine::Replace) {
                
                GLC3DTTile* search = state_->owning_tile_->parent_;
                int depth = 0;

                while (search && search->getRefine() == C3DTRefine::Replace /*&& depth < 3*/) {
                    if (search->content_.getLoadState() == GLContentHolder::LOADED) {
                        coverTile = search;
                        break;
                    }
                    search = search->parent_;
                    ++depth;
                }
            }

            if (coverTile)
                coverTile->draw(view, renderPass, state_->content_context_);

            for (GLC3DTTile* tile : state_->front_lists_->visible_list)
                tile->draw(view, renderPass, state_->content_context_);
        }
    }

    //
    // GLC3DTTile
    //

    GLC3DTTile::GLC3DTTile(GLC3DTTileset* tileset, const TAK::Engine::Formats::Cesium3DTiles::C3DTTile& tile, GLC3DTTile* parentGLTile)
        : tileset_(tileset->state_),
        parent_(parentGLTile),
        geometric_error_(tile.geometricError),
        refine_(static_cast<uint8_t>(tile.refine)),
        content_uri_(tile.content.uri),
        last_state_frame_(0),
        last_state_(NONE)
    {
        if (parentGLTile)
            transform_ = parentGLTile->transform_;
        transform_.concatenate(tile.transform);
        
        first_child_ = tileset->state_->allocator_.allocTiles(tile.childCount);
        last_child_ = first_child_;

        C3DTVolume boundingVolume;
        C3DTVolume_transform(&boundingVolume, tile.boundingVolume, transform_);

        if (boundingVolume.type == C3DTVolume::Region || boundingVolume.type == C3DTVolume::RegionAux) {
            
            const C3DTRegion& region = boundingVolume.object.region;

            const double east = region.east * 180.0 / M_PI;
            const double west = region.west * 180.0 / M_PI;
            const double north = region.north * 180.0 / M_PI;
            const double south = region.south * 180.0 / M_PI;

            this->aabb = TAK::Engine::Feature::Envelope2(west, south, region.minimumHeight, east, north, region.maximumHeight);
            this->centroid = GeoPoint2((north + south) / 2.0, (east + west) / 2.0, (region.maximumHeight + region.minimumHeight) / 2.0, HAE);

            const double metersDegLat = GeoPoint2_approximateMetersPerDegreeLatitude(this->centroid.latitude);
            const double metersDegLng = GeoPoint2_approximateMetersPerDegreeLongitude(this->centroid.latitude);

            const double dx = east * metersDegLng - west * metersDegLng;
            const double dy = north * metersDegLat - south * metersDegLat;
            const double dz = region.maximumHeight - region.minimumHeight;

            this->radius_ = sqrt(dx * dx + dy * dy + dz * dz);

            const double pad = std::max(metersDegLat, metersDegLng) / std::min(metersDegLat, metersDegLng);
            this->paddedRadius_ = radius_ * pad;
        } else {
            Point2<double> center;
            if (boundingVolume.type == C3DTVolume::Sphere) {
                const C3DTSphere& sphere = boundingVolume.object.sphere;
                this->radius_ = sphere.radius;
                center = sphere.center;
            } else if (boundingVolume.type == C3DTVolume::Box) {
                const C3DTBox& box = boundingVolume.object.box;
                radius_ = std::max(std::max(dist(box.xDirHalfLen, TAK::Engine::Math::Point2<double>(0, 0, 0)),
                    dist(box.yDirHalfLen, TAK::Engine::Math::Point2<double>(0, 0, 0))),
                    dist(box.zDirHalfLen, TAK::Engine::Math::Point2<double>(0, 0, 0)));

                center = box.center;
            }

            Projection2Ptr ecefProj(nullptr, nullptr);
            ProjectionFactory3_create(ecefProj, 4978);
            ecefProj->inverse(&this->centroid, center);
            
            const double metersDegLat = GeoPoint2_approximateMetersPerDegreeLatitude(this->centroid.latitude);;
            const double metersDegLng = GeoPoint2_approximateMetersPerDegreeLongitude(this->centroid.latitude);

            const double pad = std::max(metersDegLat, metersDegLng) / std::min(metersDegLat, metersDegLng);
            paddedRadius_ = radius_ * pad;

            aabb.minX = centroid.longitude - (radius_ / metersDegLng);
            aabb.minY = centroid.latitude - (radius_ / metersDegLat);
            aabb.minZ = centroid.altitude - radius_;
            aabb.maxX = centroid.longitude + (radius_ / metersDegLng);
            aabb.maxY = centroid.latitude + (radius_ / metersDegLat);
            aabb.maxZ = centroid.altitude + radius_;
        }
    }

    GLC3DTTile::~GLC3DTTile() NOTHROWS {
        // must call destructors since they are custom allocated
        GLC3DTTile* c = first_child_;
        while (c != last_child_) {
            c->~GLC3DTTile();
            c++;
        }
    }

    TAKErr GLC3DTTile::gatherDepthSamplerDrawables(std::vector<GLDepthSamplerDrawable*>& result, int levelDepth, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS {

        // add content which bedrocks in GLMeshes, which can depth sample draw
        if (content_.getLoadState() == GLContentHolder::LOADED) {
            GLC3DTContentRenderable* renderable = static_cast<GLC3DTContentRenderable*>(content_.getMapRenderable());
            if (renderable)
                renderable->gatherDepthSamplerDrawables(result, levelDepth, sceneModel, x, y);
        }

#if 0
        // ignoring levelDepth-- considering the whole Cesium visible scene as 1 level
        size_t numChildren = this->childCount();
        for (size_t i = 0; i < numChildren; ++i) {
            GLC3DTTile& child = this->first_child_[i];
            child.gatherDepthSamplerDrawables(result, levelDepth, sceneModel, x, y);
        }
#endif

        return TE_Ok;
    }

    void GLC3DTTile::draw(const GLGlobeBase& view, const int renderPass, GLContentContext& content_context) NOTHROWS {
        GLMapRenderable2* renderable = content_.getMapRenderable();
        if (renderable)
            renderable->draw(view, renderPass);
    }

    bool GLC3DTTile::isRenderable() const NOTHROWS {
        return content_uri_.get() && 
            content_.getLoadState() == GLContentHolder::LOADED;
    }

    void GLC3DTTile::startContentLoad() NOTHROWS {

        std::shared_ptr<TilesetImpl> ts = tileset_.lock();
        if (!ts)
            return;

        GLContentHolder::LoadState loadState = content_.getLoadState();
        if (loadState == GLContentHolder::EMPTY && this->content_uri_.get()) {
            String fullURI;
            TAKErr code = URI_combine(&fullURI, ts->base_uri_, content_uri_);
            if (code != TE_Ok)
                return;
            content_.load(ts->content_context_, fullURI)
                // GeneralWorkers_immediate() will invoke on the worker it completes on (no scheduling to a queue).
                // In this case that is the GLThread right after GLContentHolder is updated, and before it has ever had
                // a chance to draw.
                .thenOn(GeneralWorkers_immediate(), contentDidLoadTask, this->tileset_.lock(), this);
        }
    }

    void GLC3DTTile::unloadContent() NOTHROWS {
        if (content_.getLoadState() != GLContentHolder::EMPTY) {
            content_.unload();
        }
    }

    //
    // GLB3DM
    //
    
    GLTileset::GLTileset(std::shared_ptr<GLBatch> batch) NOTHROWS
    : batch_(batch)
    { }

    GLTileset::~GLTileset() NOTHROWS {
    }

    void GLTileset::draw(const GLGlobeBase& view, const int renderPass) NOTHROWS {
        RenderState state(RenderState_getCurrent());
        RenderState stored(state);
        float proj[16];
        atakmap::renderer::GLES20FixedPipeline::getInstance()->readMatrix(atakmap::renderer::GLES20FixedPipeline::MM_GL_PROJECTION, proj);

        if (parent_) {
            Matrix2 matrix = view.renderPass->scene.forwardTransform;
            matrix.concatenate(parent_->transform_);
            batch_->execute(state, matrix, proj);
        }

        RenderState_makeCurrent(stored);
    }

    TAKErr GLTileset::gatherDepthSamplerDrawables(std::vector<GLDepthSamplerDrawable*>& result, int levelDepth, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS {
        result.push_back(this);
        return TE_Ok;
    }

    void GLTileset::depthSamplerDraw(GLDepthSampler& sampler, const TAK::Engine::Core::MapSceneModel2& sceneModel) NOTHROWS {
        RenderState state(RenderState_getCurrent());
        RenderState stored(state);

        float proj[16];
        atakmap::renderer::GLES20FixedPipeline::getInstance()->readMatrix(atakmap::renderer::GLES20FixedPipeline::MM_GL_PROJECTION, proj);
        batch_->execute(sampler, sceneModel.forwardTransform, proj);
        
        RenderState_makeCurrent(stored);
    }

    void GLTileset::onTileContentLoaded(GLC3DTTile* parentTile) NOTHROWS {
        if (parentTile) {
            parent_ = parentTile;
            // if tileset still active, follow up with another list update to incorporate this newly loaded content
            auto ts = parentTile->tileset_.lock();
            if (ts && ts->front_lists_)
                ts->front_lists_->follow_up = true;
        }
    }

    //
    // TilesetParser
    //

    TilesetParser::TilesetParser(TAK::Engine::Core::RenderContext& ctx, const char* URI, std::shared_ptr<GLContentContext::Loader> loader)
        : tileset(new GLC3DTTileset(ctx, URI, loader)) {
        stack.reserve(24);
    }

    TAKErr TilesetParser::handle(const C3DTTile& tile) {

        // pop until parents match
        while (stack.size() && stack.back().tile != tile.parent) {
            stack.pop_back();
        }

        GLC3DTTile* par = parent();
        GLC3DTTile* glTile = nullptr;
        if (!tileset->state_->root_tile_) {
            tileset->state_->root_tile_.reset(new GLC3DTTile(tileset.get(), tile, par));
            glTile = tileset->state_->root_tile_.get();
        } else if (par) {
            ::new(static_cast<void*>(par->last_child_)) GLC3DTTile(tileset.get(), tile, par);
            glTile = par->last_child_;
            ++par->last_child_;
        }

        if (glTile)
            stack.push_back({ glTile, &tile });

        return TE_Ok;
    }

    TAKErr TilesetParser::visitor(void* opaque, const C3DTTileset* tileset, const C3DTTile* tile) {
        TilesetParser* args = static_cast<TilesetParser*>(opaque);
        return args->handle(*tile);
    }

    TAKErr loadBitmapTask(std::shared_ptr<TAK::Engine::Renderer::Bitmap2>& result, TAK::Engine::Port::String& URI, const std::shared_ptr<URIOfflineCache>& cache,
        int64_t cacheDurationSeconds) NOTHROWS {

        DataInput2Ptr input(nullptr, nullptr);
        String scheme;
        URI_parse(&scheme, nullptr, nullptr, nullptr, nullptr, nullptr, URI);
        bool isStreaming = false;
        int cmp = -1;
        TAK::Engine::Port::String_compareIgnoreCase(&cmp, "http", scheme);
        if (cmp != 0)
            TAK::Engine::Port::String_compareIgnoreCase(&cmp, "https", scheme);

        TAKErr code = TE_Err;
        if (cmp == 0 && cache)
            code = cache->open(input, URI, cacheDurationSeconds);
        else
            code = URI_open(input, URI);
        
        if (code != TE_Ok)
            return code;

        BitmapPtr bmp(nullptr, nullptr);
        code = BitmapFactory2_decode(bmp, *input, nullptr);
        if (code != TE_Ok)
            return code;

        input->close();

        result = std::shared_ptr<TAK::Engine::Renderer::Bitmap2>(bmp.release(), bmp.get_deleter());
        return TE_Ok;
    }

    //
    // C3DTTilesetTextureLoader
    //

    C3DTTilesetTextureLoader::C3DTTilesetTextureLoader(const std::shared_ptr<const Mesh>& mesh) NOTHROWS
        : mesh(mesh)
    { }

    C3DTTilesetTextureLoader::~C3DTTilesetTextureLoader() NOTHROWS
    { }

    TAKErr loadMeshBufferBitmap(std::shared_ptr<TAK::Engine::Renderer::Bitmap2>& result, const std::shared_ptr<const Mesh>& mesh, size_t bufferIndex) NOTHROWS {

        const MemBuffer2* buffer = nullptr;
        TAKErr code = mesh->getBuffer(&buffer, bufferIndex);
        if (code != TE_Ok)
            return code;
        if (!buffer)
            return TE_IllegalState;

        TAK::Engine::Util::MemoryInput2 input;
        input.open(buffer->get(), buffer->size());

        TAK::Engine::Renderer::BitmapPtr bmp(nullptr, nullptr);
        code = TAK::Engine::Renderer::BitmapFactory2_decode(bmp, input, nullptr);
        if (code != TE_Ok)
            return code;

        result = std::shared_ptr<TAK::Engine::Renderer::Bitmap2>(bmp.release(), bmp.get_deleter());
        return TE_Ok;
    }

    TAKErr C3DTTilesetTextureLoader::load(TAK::Engine::Util::FutureTask<std::shared_ptr<TAK::Engine::Renderer::Bitmap2>>& value, const char* URI) NOTHROWS {

        TAKErr code = TE_Unsupported;
        size_t bufferIndex = 0;

        if (Material_getBufferIndexTextureURI(&bufferIndex, URI) == TE_Ok) {
            value = Task_begin(textureLoadWorker(), loadMeshBufferBitmap, this->mesh, bufferIndex);
            code = TE_Ok;
        }

        return code;
    }

    TAKErr loadTextureTask(bool& res, const std::shared_ptr<GLBatch::Texture>& tex, const std::shared_ptr<Bitmap2>& bmp) NOTHROWS {
        GLTexture2 glTex(static_cast<int>(bmp->getWidth()), static_cast<int>(bmp->getHeight()), bmp->getFormat());
        TAKErr code = glTex.load(*bmp);
        if (code != TE_Ok)
            return code;
        GLTexture2_orphan(&tex->tex_id, glTex);
        return TE_Ok;
    }

    TAKErr loadCompressedTextureTask(bool&, const std::shared_ptr<GLBatch::Texture>& tex, 
        std::unique_ptr<GLCompressedTextureData, void(*)(GLCompressedTextureData*)>& data) NOTHROWS {

        GLTexture2Ptr glTex(nullptr, nullptr);
        TAKErr code = GLTexture2_createCompressedTexture(glTex, *data);
        if (code != TE_Ok)
            return code;
        GLTexture2_orphan(&tex->tex_id, *glTex);
        return TE_Ok;
    }

    TAKErr loadShaderTask(bool&, TAK::Engine::Core::RenderContext* ctx,
        const std::shared_ptr<GLBatch>& batch, size_t index, const RenderAttributes& attrs) NOTHROWS {

        std::shared_ptr<const Shader> shader;
        Shader_get(shader, *ctx, attrs);
        batch->setShader(index, shader);
        return TE_Ok;
    }

    TAKErr loadVBOTask(bool&,
        const std::shared_ptr<GLBatch::Buffer>& buffer,
        const std::shared_ptr<Scene>& scene,
        const void* verts,
        size_t bufSize) {

        return buffer->load(verts, bufSize, true);
    }
}

TAKErr GLC3DTRenderer::LoaderImpl::load(TAK::Engine::Util::FutureTask<std::shared_ptr<TAK::Engine::Renderer::Bitmap2>>& value, const char* URI) NOTHROWS {
    TAKErr code = TE_Unsupported;
    value = Task_begin(textureLoadWorker(), loadBitmapTask, String(URI), this->cache_, this->cache_dur_sec_);
    code = TE_Ok;
    return code;
}

namespace {
    GLenum dataTypeToGLType(TAK::Engine::Port::DataType dataType) NOTHROWS {
        GLenum type = GL_NONE;
        switch (dataType) {
        case TEDT_UInt8: type = GL_UNSIGNED_BYTE; break;
        case TEDT_Int8: type = GL_BYTE; break;
        case TEDT_UInt16: type = GL_UNSIGNED_SHORT; break;
        case TEDT_Int16: type = GL_SHORT; break;
        case TEDT_UInt32: type = GL_UNSIGNED_INT; break;
        case TEDT_Int32: type = GL_INT; break;
        case TEDT_Float32: type = GL_FLOAT; break;
        default: break;
        }
        return type;
    }

    bool handleConfigAttr(VertexStreamConfig& stream, const VertexDataLayout& vdl, const VertexArray& va, VertexAttribute attr) NOTHROWS {
        if (vdl.attributes & attr) {
            stream.offset = static_cast<GLsizei>(va.offset);
            stream.stride = static_cast<GLsizei>(va.stride);
            stream.type = dataTypeToGLType(va.type);
            stream.attribute = attr;

            stream.size = 1;
            switch (attr) {
            case TEVA_Normal:
            case TEVA_Position: stream.size = 3; break;
            case TEVA_Color: stream.size = 4; break;
            case TEVA_TexCoord0:
            case TEVA_TexCoord1:
            case TEVA_TexCoord2:
            case TEVA_TexCoord3:
            case TEVA_TexCoord4:
            case TEVA_TexCoord5:
            case TEVA_TexCoord6:
            case TEVA_TexCoord7: stream.size = 2; break;
            }
            return true;
        }

        return false;
    }

    struct SafeResourceDeleter {

        SafeResourceDeleter(RenderContext& c) NOTHROWS
            : cxt(c) {}

        static TAKErr releaseTextureTask(bool&, GLuint tex_id) {
            glDeleteTextures(1, &tex_id);
            return TE_Ok;
        }

        static TAKErr releaseBufferTask(bool&, GLuint buf_id) {
            glDeleteBuffers(1, &buf_id);
            return TE_Ok;
        }

        void operator()(GLBatch::Texture* texture) const {

            if (!texture)
                return;

            if (!cxt.isRenderThread() && texture->tex_id != 0) {
                Logger_log(TELL_Warning, "GLBatch::Texture delete detected on non-render thread");
                GLuint tex_id = texture->tex_id;
                texture->tex_id = 0;
                Task_begin(GLWorkers_resourceLoad(), releaseTextureTask, tex_id);
            } 
            delete texture;
        }

        void operator()(GLBatch::Buffer* buffer) const {

            if (!buffer)
                return;

            if (!cxt.isRenderThread() && buffer->is_vbo && buffer->u.vbo_id != 0) {
                Logger_log(TELL_Warning, "GLBatch::Buffer delete detected on non-render thread");
                GLuint release_id = buffer->u.vbo_id;
                buffer->u.vbo_id = 0;
                buffer->is_vbo = 0;
                Task_begin(GLWorkers_resourceLoad(), releaseTextureTask, release_id);
            }
            delete buffer;
        }

        RenderContext& cxt;
    };
}

TAKErr GLC3DTRenderer::LoaderImpl::processTileSetMesh(const std::shared_ptr<const Mesh>& mesh, GLBatchBuilder& batchBuilder, TAK::Engine::Core::RenderContext& ctx, const std::shared_ptr<Scene>& scene, const String& baseURI, const char* URI, bool isStreaming, const char* anchorPath) NOTHROWS {

    GLenum front_face = GL_CCW;
    TAKErr code = TE_Ok;

    if (mesh->getFaceWindingOrder() == TEWO_Clockwise) {
        front_face = GL_CW;
    }

    GLenum draw_mode = GL_NONE;

    switch (mesh->getDrawMode()) {
    case TEDM_Triangles:
        draw_mode = GL_TRIANGLES;
        break;
    case TEDM_TriangleStrip:
        draw_mode = GL_TRIANGLE_STRIP;
        break;
    case TEDM_Points:
        draw_mode = GL_POINTS;
        break;
    default: break;
    }

    const VertexDataLayout& vdl = mesh->getVertexDataLayout();
    VertexStreamConfig streams[4];
    size_t streamIndex2 = 0;

    if (handleConfigAttr(streams[streamIndex2], vdl, vdl.position, TEVA_Position)) ++streamIndex2;
    if (handleConfigAttr(streams[streamIndex2], vdl, vdl.texCoord0, TEVA_TexCoord0)) ++streamIndex2;
    if (handleConfigAttr(streams[streamIndex2], vdl, vdl.color, TEVA_Color)) ++streamIndex2;
    if (handleConfigAttr(streams[streamIndex2], vdl, vdl.normal, TEVA_Normal)) ++streamIndex2;

    if (vdl.interleaved) {
#if C3DT_DEBUG_RESOURCE_RELEASE
        GLBatch::BufferPtr buf = std::shared_ptr<GLBatch::Buffer>(new GLBatch::Buffer(), SafeResourceDeleter(ctx));
#else
        GLBatch::BufferPtr buf = std::make_shared<GLBatch::Buffer>();
#endif
        for (size_t i = 0; i < streamIndex2; ++i)
            streams[i].buffer = buf;
    }
    else {
        for (size_t i = 0; i < streamIndex2; ++i)
#if C3DT_DEBUG_RESOURCE_RELEASE
            GLBatch::BufferPtr buf = std::shared_ptr<GLBatch::Buffer>(new GLBatch::Buffer(), SafeResourceDeleter(ctx));
#else
            GLBatch::BufferPtr buf = std::make_shared<GLBatch::Buffer>();
#endif
    }

    size_t num_verts = mesh->getNumVertices();
    size_t num_textures = 0;

    size_t num_mats = mesh->getNumMaterials();
    for (size_t i = 0; i < num_mats; ++i) {
        Material mat;
        if (mesh->getMaterial(&mat, i) == TE_Ok && mat.textureUri) {
            num_textures++;
        }
    }

    bool indexed = false;
    size_t num_indices = 0;
    size_t indexBufferIndex = SIZE_MAX;
    GLenum indexType = GL_UNSIGNED_SHORT;
    size_t indexSize = 2;

    // mark buffer count to spin up load tasks for new buffers at the end of this mesh
    size_t batchBufferIndex = batchBuilder.bufferCount();
    if (batchBufferIndex > 0)
        int pppp = 0;

    if (mesh->isIndexed()) {
        num_indices = mesh->getNumIndices();
#if C3DT_DEBUG_RESOURCE_RELEASE
        batchBuilder.addBuffer(&indexBufferIndex, std::shared_ptr<GLBatch::Buffer>(new GLBatch::Buffer(), SafeResourceDeleter(ctx)));
#else
        batchBuilder.addBuffer(&indexBufferIndex, std::make_shared<GLBatch::Buffer>());
#endif
        TAK::Engine::Port::DataType dataType;
        mesh->getIndexType(&dataType);
        indexType = dataTypeToGLType(dataType);
        if (indexType == GL_UNSIGNED_BYTE)
            indexSize = 1;
        else if (indexType == GL_UNSIGNED_INT)
            indexSize = 4;
        indexed = true;
    }

    batchBuilder.setStreams(streams, streamIndex2);
    size_t textureIndex = 0;

    for (size_t i = 0; i < num_mats; ++i) {
        Material mat;
        if (mesh->getMaterial(&mat, i) == TE_Ok && mat.textureUri) {

            // only allow supported number of textures for now...
            if (textureIndex == GLBatchBuilder::MAX_TEXTURE_SLOTS)
                break;

            String texKey = mat.textureUri;
            String relative;
            size_t bufferIndex = SIZE_MAX;

            if (Material_getBufferIndexTextureURI(&bufferIndex, mat.textureUri) == TE_Ok) {
                URI_getRelative(&relative, this->base_uri_, URI);
                StringBuilder sb;
                if (!relative.get())
                    relative = "";
                sb.append(relative.get());
                sb.append("#");
                sb.append(anchorPath);
                sb.append("-");
                sb.append(bufferIndex);
                texKey = sb.c_str();
            }
            else if (URI_getRelative(&relative, baseURI, texKey) == TE_Ok) {
                texKey = relative;
            }

            std::shared_ptr<GLBatch::Texture> tex;
            bool doTexLoad = false;

            {
                Lock lock(this->mutex_);
                auto it = this->resident_textures_.find(texKey.get());

                if (it == this->resident_textures_.end()) {
#if C3DT_DEBUG_RESOURCE_RELEASE
                    tex = std::shared_ptr<GLBatch::Texture>(new GLBatch::Texture(), SafeResourceDeleter(ctx));
#else
                    tex = std::make_shared<GLBatch::Texture>();
#endif                            
                    it = this->resident_textures_.insert(std::pair<std::string, std::weak_ptr<GLBatch::Texture>>(
                        std::string(texKey.get()),
                        std::weak_ptr<GLBatch::Texture>(tex))).first;
                    doTexLoad = true;
                }
                else {
                    tex = it->second.lock();
                    if (!tex) {
#if C3DT_DEBUG_RESOURCE_RELEASE
                        tex = std::shared_ptr<GLBatch::Texture>(new GLBatch::Texture(), SafeResourceDeleter(ctx));
#else
                        tex = std::make_shared<GLBatch::Texture>();
#endif
                        it->second = tex;
                        doTexLoad = true;
                    }
                }
            }

            batchBuilder.setTexture(tex);

            if (doTexLoad) {
                std::shared_ptr<Bitmap2> bitmap;

                code = TE_Err;
                if (bufferIndex != SIZE_MAX) {
                    code = loadMeshBufferBitmap(bitmap, mesh, bufferIndex);
                } else {
                    DataInput2Ptr texInput(nullptr, nullptr);

                    if (isStreaming && this->cache_)
                        code = this->cache_->open(texInput, mat.textureUri, this->cache_dur_sec_);
                    else
                        code = URI_open(texInput, mat.textureUri);

                    if (code != TE_Ok)
                        return code;

                    BitmapPtr bmp(nullptr, nullptr);
                    code = BitmapFactory2_decode(bmp, *texInput, nullptr);
                    if (texInput)
                        texInput->close();

                    if (code != TE_Ok)
                        return code;

                    bitmap = std::shared_ptr<TAK::Engine::Renderer::Bitmap2>(bmp.release(), bmp.get_deleter());
                }

                if (bitmap) {
#if 1
                    // load compressed texture
                    std::unique_ptr<GLCompressedTextureData, void(*)(GLCompressedTextureData*)> data(nullptr, nullptr);
                    GLTexture2_createCompressedTextureData(data, *bitmap);
                    Task_begin(GLWorkers_resourceLoad(), loadCompressedTextureTask,
                        tex, std::move(data));
#else
                    // load texture with bitmap on OpenGL resourceLoad worker
                    Task_begin(GLWorkers_resourceLoad(), loadTextureTask,
                        tex, bitmap);
#endif
                }
            }

            ++textureIndex;
        }
    }

    if (indexed)
        batchBuilder.drawElements(draw_mode, indexType, num_indices, indexBufferIndex);
    else
        batchBuilder.drawArrays(draw_mode, num_verts);

    // draw call adds buffers?
    for (size_t i = batchBufferIndex; i < batchBuilder.bufferCount(); ++i) {


        if (i == indexBufferIndex) {
            batchBuilder.bufferAt(i)->load(mesh->getIndices(),
                num_indices * indexSize, false);
        }
        else {
            const void* verts = nullptr;
            mesh->getVertices(&verts, TEVA_Position);

            size_t bufSize = num_verts * vdl.position.stride;

            if (vdl.interleaved)
                VertexDataLayout_requiredInterleavedDataSize(&bufSize, vdl, num_verts);

            bool use_vbo = indexed ? num_verts <= 0xFFFFu : num_verts <= (3u * 0xFFFFu);
            use_vbo &= vdl.interleaved;

            if (use_vbo) {
                GLBatch::BufferPtr buffer = batchBuilder.bufferAt(i);
                Task_begin(GLWorkers_resourceLoad(), loadVBOTask,
                    buffer, scene, verts, bufSize);
            }
            else {
                // safe to call from load thread (NO GL calls invoked)
                batchBuilder.bufferAt(i)->load(verts, bufSize, false);
            }
        }
    }

    return code;
}

static Matrix2 IDENTITY_MATRIX_;

TAKErr GLC3DTRenderer::LoaderImpl::processTileSetChildren(SceneNode& node, GLBatchBuilder& batchBuilder, TAK::Engine::Core::RenderContext& ctx, const std::shared_ptr<Scene>& scene, const String& baseURI, const char* URI, bool isStreaming, const char* anchorPath) NOTHROWS {

    TAKErr code = TE_Ok;

    Collection<std::shared_ptr<SceneNode>>::IteratorPtr iter(nullptr, nullptr);
    node.getChildren(iter);
    std::shared_ptr<SceneNode> item;

    const Matrix2 *localFrame = node.getLocalFrame();
    if (localFrame && !(*localFrame == IDENTITY_MATRIX_)) {
        batchBuilder.setLocalFrame(*node.getLocalFrame());
    }

    // sanity check
    if (!iter)
        return TE_Err;

    size_t nodeIndex = 0;

    while (iter->get(item) == TE_Ok) {
        if (item) {

            StringBuilder subAnchorPath;
            subAnchorPath.append(anchorPath);
            if (subAnchorPath.length() > 0)
                subAnchorPath.append(",");
            subAnchorPath.append(nodeIndex);

            if (item->hasMesh()) { // should be the case
                std::shared_ptr<const Mesh> mesh;
                item->loadMesh(mesh);
                code = processTileSetMesh(mesh, batchBuilder, ctx, scene, baseURI, URI, isStreaming, subAnchorPath.c_str());
            } else if (item->hasChildren()) {
                code = processTileSetChildren(*item, batchBuilder, ctx, scene, baseURI, URI, isStreaming, subAnchorPath.c_str());
            }
        }
        iter->next();
        ++nodeIndex;
    }

    return code;
}

TAKErr GLC3DTRenderer::LoaderImpl::processTileSet(TAK::Engine::Renderer::Core::GLMapRenderable2Ptr& output, TAK::Engine::Core::RenderContext& ctx, ScenePtr &scenePtr, const String& baseURI, const char* URI, bool isStreaming) NOTHROWS {

    TAKErr code = TE_Ok;

    // we need to share the scene to the loader workers
    std::shared_ptr<Scene> scene(scenePtr.release(), scenePtr.get_deleter());

    // making a bunch of assumptions because this is known to be B3DM, but has sanity checks
    SceneNode& node = scene->getRootNode();
    GLBatchBuilder batchBuilder;

    code = processTileSetChildren(node, batchBuilder, ctx, scene, baseURI, URI, isStreaming, "");

    // Load shader tasks
    SharedGLBatchPtr batch;
    batchBuilder.buildShared(batch);
    for (size_t i = 0; i < batch->shaderCount(); ++i) {
        RenderAttributes attrs;
        batchBuilder.shaderRenderAttributes(&attrs, i);
        Task_begin(GLWorkers_resourceLoad(), loadShaderTask, &ctx, batch, i, attrs);
    }
    
    if (code == TE_Ok) {
        output = GLMapRenderable2Ptr(new GLTileset(batch), Memory_deleter_const<GLMapRenderable2, GLTileset>);
    }

    return code;
}

TAKErr GLC3DTRenderer::LoaderImpl::load(TAK::Engine::Renderer::Core::GLMapRenderable2Ptr& output, TAK::Engine::Core::RenderContext& ctx, const TAK::Engine::Port::String& URI) NOTHROWS {
    TAKErr code = TE_Ok;

    C3DTFileType type;
    String fileURI;
    String baseURI;

    bool isStreaming = false;
    code = C3DT_probeSupport(&type, &fileURI, nullptr, &baseURI, &isStreaming, URI);
    if (code != TE_Ok)
        return code;
    if (!fileURI || !baseURI)
        return TE_Err;

    DataInput2Ptr input(nullptr, nullptr);
    if (isStreaming && cache_)
        code = cache_->open(input, fileURI, this->cache_dur_sec_);
    else
        code = URI_open(input, fileURI);
    if (code != TE_Ok)
        return code;

    {
        bool tryAgain = false;
        std::unique_ptr<GLC3DTTileset> tileset;
        ScenePtr scene(nullptr, nullptr);
        SceneInfoPtr sceneInfo;
        do {
            if (type == C3DTFileType_TilesetJSON) {
                TilesetParser args(ctx, baseURI, this->makeChildContentLoader(ctx));
                code = C3DTTileset_parse(input.get(), &args, TilesetParser::visitor);
                if (code == TE_Ok)
                    tileset = std::move(args.tileset);
            } else if (type == C3DTFileType_B3DM) {
                code = C3DT_parseB3DMScene(scene, sceneInfo, input.get(), baseURI, fileURI);
            } else if (type == C3DTFileType_PNTS) {
                code = C3DT_parsePNTSScene(scene, sceneInfo, input.get(), baseURI, fileURI);
            } else {
                code = TE_Unsupported;
                break;
            }

            if (code != TE_Ok && isStreaming && cache_ && !tryAgain) {
                // it is possible that the cached file is corrupt-- try to renew it
                tryAgain = true;

                // force a renew
                code = cache_->open(input, fileURI, this->cache_dur_sec_, true);
                if (code != TE_Ok)
                    return code;
            } else {
                tryAgain = false;
            }
        } while (tryAgain);

        if (code == TE_Ok) {
            if (type == C3DTFileType_TilesetJSON) {
                output = GLMapRenderable2Ptr(tileset.release(), Memory_deleter_const<GLMapRenderable2, GLC3DTTileset>);
            } else if (type == C3DTFileType_B3DM) {
                code = processTileSet(output, ctx, scene, baseURI, URI, isStreaming);
            } else if (type == C3DTFileType_PNTS) {
                code = processTileSet(output, ctx, scene, baseURI, URI, isStreaming);
            }
        }
    }

    if (input)
        input->close();

    return code;
}
