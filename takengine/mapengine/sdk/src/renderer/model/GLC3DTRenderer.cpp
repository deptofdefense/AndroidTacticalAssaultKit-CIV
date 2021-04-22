
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

/*
    TODO-LIST:
        (polish)
        - profile and tweak optimal worker counts/config
*/

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

#define C3DT_RENDERER_DEBUG_OUTPUT   0
#define C3DT_DEBUG_RESOURCE_RELEASE  0


// extern of a helper to avoid double parsing of B3DM data
namespace TAK {
    namespace Engine {
        namespace Model {
            extern TAKErr C3DT_parseB3DMScene(ScenePtr& scenePtr, SceneInfoPtr& sceneInfoPtr, DataInput2* input, 
                const char* baseURI, const char* URI) NOTHROWS;
        }
    }
}

namespace {
    class GLC3DTTileset;
    class GLC3DTTile;

    constexpr double maxScreenSpaceError = 16.0;
    constexpr size_t loadingDescendantLimit = 20;
    constexpr bool forbidHoles = true;
    static size_t load_count = 0;

    int64_t getConfiguredCacheSizeLimit() NOTHROWS;
    int64_t getConfiguredCacheDurationSeconds() NOTHROWS;

    TAKErr loadBitmapTask(std::shared_ptr<TAK::Engine::Renderer::Bitmap2>& result, TAK::Engine::Port::String& URI, const std::shared_ptr<URIOfflineCache>& cache,
        int64_t cacheDurationSeconds) NOTHROWS;
    TAKErr loadMeshBufferBitmap(std::shared_ptr<TAK::Engine::Renderer::Bitmap2>& result, const std::shared_ptr<const Mesh>& mesh, size_t bufferIndex) NOTHROWS;

    class GLC3DTContentRenderable : public GLMapRenderable2, 
        public GLDepthSamplerDrawable {
    public:
        virtual void setParentTile(const GLC3DTTile* parent) NOTHROWS {}
        virtual void setParentRenderer(GLC3DTRenderer* renderer) NOTHROWS {}
    };

    class GLB3DM : public GLC3DTContentRenderable {
    public:
        GLB3DM(std::shared_ptr<GLBatch> batch) NOTHROWS;
        virtual ~GLB3DM() NOTHROWS;
        void draw(const GLGlobeBase& view, const int renderPass) NOTHROWS;
        void release() NOTHROWS {}
        int getRenderPass() NOTHROWS { return 0; }
        void start() NOTHROWS {}
        void stop() NOTHROWS {}

        virtual TAKErr gatherDepthSamplerDrawables(std::vector<GLDepthSamplerDrawable*>& result, int levelDepth, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS;
        virtual void depthSamplerDraw(GLDepthSampler& sampler, const TAK::Engine::Core::MapSceneModel2& sceneModel) NOTHROWS;

        std::shared_ptr<GLBatch> batch_;
    };

    struct UpdateResult {
        uint32_t notYetRenderableCount = 0;
        bool allRenderable = true;
        bool anyRenderedPreviously = false;
        bool canceled = false;
    };

    struct UpdateChanges {
        std::vector<GLC3DTTile*> visible_list;
        std::vector<GLC3DTTile*> unload_list;
        std::vector<GLC3DTTile*> high_priority_load;
        std::vector<GLC3DTTile*> medium_priority_load;
        std::vector<GLC3DTTile*> low_priority_load;

        void clear() {
            visible_list.clear();
            unload_list.clear();
            high_priority_load.clear();
            medium_priority_load.clear();
            low_priority_load.clear();
        }
    };

    struct CameraInfo {
        CameraInfo(const MapSceneModel2& scene)
            : frustum(scene.camera.projection, scene.camera.modelView),
            position(scene.camera.location),
            sseDenom(tan(0.5 * scene.camera.fov * M_PI / 180.0) * 2.0),
            viewportHeight((double)scene.height)
        {}

        Frustum2 frustum;
        Point2<double> position;
        double sseDenom;
        double viewportHeight;
    };

    

    class GLC3DTTile {
    public:
        enum State {
            NONE,
            CULLED,
            RENDERED,
            REFINED,
            RENDERED_AND_KICKED,
            REFINED_AND_KICKED
        };

        GLC3DTTile(GLC3DTTileset* tileset, const TAK::Engine::Formats::Cesium3DTiles::C3DTTile& tile, GLC3DTTile* parentGLTile);

        ~GLC3DTTile() NOTHROWS {
            GLC3DTTile* c = first_child_;
            while (c != last_child_) {
                c->~GLC3DTTile();
                c++;
            }
        }

        void draw(const GLGlobeBase& view, const int renderPass, GLContentContext& content_context) NOTHROWS;
        void drawAABB(const GLGlobeBase& view);
        void startLoad() NOTHROWS;
        void unload() NOTHROWS;

        TAKErr gatherDepthSamplerDrawables(std::vector<GLDepthSamplerDrawable*>& result, int levelDepth, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS;
        
        static std::shared_ptr<const Shader> wireframe_shader_;

        double geometric_error_;
        uint64_t last_state_frame_;

        GLC3DTTileset* tileset_;
        GLC3DTTile* parent_;
        String content_uri_;

        //std::vector<std::unique_ptr<GLC3DTTile>> children_;
        //std::vector<GLC3DTTile*> children_;
        GLC3DTTile* first_child_;
        GLC3DTTile* last_child_;
        GLContentHolder content_;

        Matrix2 transform_;
        C3DTVolume boundingVolume_;
        uint8_t refine_;
        uint8_t last_state_;
        
        inline size_t childCount() const NOTHROWS {
            return static_cast<size_t>(last_child_ - first_child_);
        }

        bool isRenderable() const NOTHROWS;
        double calcSSE(const CameraInfo& camera) const NOTHROWS;

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

        inline bool wasKicked(uint64_t frame) const NOTHROWS {
            State state = this->getState(frame);
            return state == RENDERED_AND_KICKED || state == REFINED_AND_KICKED;
        }

        inline void setKicked() NOTHROWS {
            if (last_state_ == RENDERED) last_state_ = RENDERED_AND_KICKED;
            else if (last_state_ == REFINED) last_state_ = REFINED_AND_KICKED;
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
    //
    //

    std::shared_ptr<const Shader> GLC3DTTile::wireframe_shader_;

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
        virtual void setParentTile(const GLC3DTTile* parent) NOTHROWS;
        virtual void setParentRenderer(GLC3DTRenderer* renderer) NOTHROWS;

        C3DTAllocator allocator_; // allocator must come first

        // Non-transient state
        GLC3DTRenderer* renderer_;
        String base_uri_;
        std::unique_ptr<GLC3DTTile> root_tile_;
        SharedWorkerPtr view_update_worker_;
        const uint64_t* desired_view_update_num_;

        // Only touched by the GLThread
        GLContentContext content_context_;
        MapCamera2 current_camera_;
        std::unique_ptr<UpdateChanges> front_state_;
        std::unique_ptr<UpdateChanges> recycle_state_;
        Future<bool> update_task_;

        // Only touched by the view update worker
        uint64_t pending_view_update_number_;
        uint64_t last_completed_frame_num_;
        std::unique_ptr<UpdateChanges> pending_state_;

        inline bool updateIsCanceled(uint64_t update_number) const { return *desired_view_update_num_ != update_number; }
        static TAKErr updateViewTask(std::unique_ptr<UpdateChanges>& result, 
            GLC3DTTileset* ts, 
            uint64_t update_number,
            const MapSceneModel2& sceneModel,
            std::unique_ptr<UpdateChanges>& recycle) NOTHROWS;
        static TAKErr receiveUpdateTask(bool&, std::unique_ptr<UpdateChanges>& result, GLC3DTTileset* ts) NOTHROWS;
        bool updateView(const CameraInfo& camera, uint64_t update_number) NOTHROWS;
        UpdateResult updateTileIfVisible(GLC3DTTile& tile, const CameraInfo& camera, bool ancestorMeetsSse, uint64_t update_number) NOTHROWS;
        UpdateResult updateTile(GLC3DTTile& tile, const CameraInfo& camera, bool ancestorMeetsSse, uint64_t update_number) NOTHROWS;

        UpdateResult updateChildren(GLC3DTTile& tile, const CameraInfo& camera, bool ancestorMeetsSse, uint64_t update_number) NOTHROWS;
        void markTileNonRendered(GLC3DTTile& tile) NOTHROWS;
        void markChildrenNonRendered(GLC3DTTile& tile) NOTHROWS;

        void unloadRenderedChildren(GLC3DTTile& tile) NOTHROWS;
    };

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
    explicit LoaderImpl(TAK::Engine::Core::RenderContext& ctx, std::shared_ptr<URIOfflineCache> cache) NOTHROWS
        : cache_(cache),
        parent_mm(std::make_shared<MaterialManager>(ctx, MaterialManager::TextureLoaderPtr(this, Memory_leaker_const<MaterialManager::TextureLoader>))),
        cache_dur_sec_(getConfiguredCacheDurationSeconds())
    {}

    virtual ~LoaderImpl() NOTHROWS {}

    virtual std::shared_ptr<GLContentContext::Loader> makeChildContentLoader(TAK::Engine::Core::RenderContext& ctx) NOTHROWS {
        return std::make_shared<LoaderImpl>(ctx, cache_);
    }

    virtual TAKErr open(DataInput2Ptr& input, const char* URI) NOTHROWS {
        if (cache_)
            cache_->open(input, URI, cache_dur_sec_);
        return URI_open(input, URI);
    }
    TAKErr load(TAK::Engine::Renderer::Core::GLMapRenderable2Ptr&, TAK::Engine::Core::RenderContext&, const TAK::Engine::Port::String&) NOTHROWS override;

    TAKErr load(TAK::Engine::Util::FutureTask<std::shared_ptr<Bitmap2>>& value, const char* uri) NOTHROWS override;

    TAKErr processB3DM(TAK::Engine::Renderer::Core::GLMapRenderable2Ptr& output, TAK::Engine::Core::RenderContext& ctx, ScenePtr& scene, const String& baseURI, const char* URI, bool isStreaming) NOTHROWS;

    std::shared_ptr<MaterialManager> parent_mm;
    std::shared_ptr<URIOfflineCache> cache_;
    int64_t cache_dur_sec_;

    TAK::Engine::Thread::Mutex mutex_;
    std::unordered_map<std::string, std::weak_ptr<GLBatch::Texture>> resident_textures_;
};

GLC3DTRenderer::GLC3DTRenderer(TAK::Engine::Core::RenderContext& ctx, const TAK::Engine::Model::SceneInfo& info) NOTHROWS 
 : GLC3DTRenderer(ctx, info, nullptr) { }

GLC3DTRenderer::GLC3DTRenderer(TAK::Engine::Core::RenderContext& ctx, const TAK::Engine::Model::SceneInfo& info, const char* cacheDir) NOTHROWS
    : loader_impl_(std::make_shared<LoaderImpl>(ctx, cacheDir ? std::make_shared<URIOfflineCache>(cacheDir, getConfiguredCacheSizeLimit()) : nullptr)),
    root_content_(new GLContentHolder()),
    info_(info),
    uri_(info.uri),
    scene_control_(new SceneControlImpl(*this)) {
    content_context_.reset(new GLContentContext(ctx, tilesetLoadWorker(), loader_impl_));
}

GLC3DTRenderer::~GLC3DTRenderer() NOTHROWS {
    this->content_context_->cancelAll();
}


TAKErr rootContentDidLoad(bool&, GLMapRenderable2* input, GLC3DTRenderer* renderer) {
    // All C3DT content renderables share this common base
    GLC3DTContentRenderable* content = static_cast<GLC3DTContentRenderable*>(input);

    // apply once for content whose parent tile has a transform
    content->setParentRenderer(renderer);
    return TE_Ok;
}

namespace {
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
            .thenOn(GeneralWorkers_immediate(), rootContentDidLoad, this);
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
    return GLGlobeBase::Sprites;
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
#if 1
    TAKErr code = TE_Unsupported;
#else
    TAKErr code = TE_Ok;
    TAKErr awaitCode = Task_begin(GLWorkers_glThread(), depthTestTask, this, sceneModel, screenX, screenY)
        .await(hitGeo, code);

    if (awaitCode != TE_Ok)
        return awaitCode;

    if (code == TE_Ok)
        *value = hitGeo;
#endif
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

    //
    // GLC3DTTileset
    //

    GLC3DTTileset::GLC3DTTileset(TAK::Engine::Core::RenderContext& ctx, const char* baseURI, std::shared_ptr<GLContentContext::Loader> loader)
        : base_uri_(baseURI),
        content_context_(ctx, tilesetLoadWorker(), loader),
        last_completed_frame_num_(0),
        pending_view_update_number_(0) {

        // ensures that only 1 view update is happening at a time and that each 
        // scheduled update increments desired_view_update_num_ which can safely be read in updateView()
        // to determine if the update is OBE.
        Worker_createOverrideTasker(this->view_update_worker_, &this->desired_view_update_num_, updateViewWorker());
    }

    GLC3DTTileset::~GLC3DTTileset() NOTHROWS {
        if (this->update_task_) {
            this->update_task_.cancel();
            bool result;
            TAKErr code;
            this->update_task_.await(result, code);
        }
    }

    TAKErr GLC3DTTileset::gatherDepthSamplerDrawables(std::vector<GLDepthSamplerDrawable*>& result, int levelDepth, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS {
        if (this->front_state_) {
            for (GLC3DTTile* tile : this->front_state_->visible_list)
                tile->gatherDepthSamplerDrawables(result, levelDepth, sceneModel, x, y);
        }
        return TE_Ok;
    }

    TAKErr GLC3DTTileset::updateViewTask(std::unique_ptr<UpdateChanges>& result, GLC3DTTileset* ts, 
        uint64_t update_number,
        const MapSceneModel2& sceneModel,
        std::unique_ptr<UpdateChanges>& recycle) NOTHROWS {

        if (recycle)
            ts->pending_state_ = std::move(recycle);

        CameraInfo camera(sceneModel);
        bool complete = ts->updateView(camera, update_number);
        if (complete) {

            for (auto& p : ts->pending_state_->unload_list) {
                // mark each as culled
                p->setState(GLC3DTTile::CULLED, ts->last_completed_frame_num_);
            }

            result = std::move(ts->pending_state_);
        }

        return TE_Ok;
    }

    TAKErr GLC3DTTileset::receiveUpdateTask(bool& changed, std::unique_ptr<UpdateChanges>& result, GLC3DTTileset* ts) NOTHROWS {

        ts->update_task_.detach();

        changed = false;
        if (result) {
            ts->recycle_state_ = std::move(ts->front_state_);
            ts->front_state_ = std::move(result);

            // do loads/unloads
            for (GLC3DTTile* tile : ts->front_state_->unload_list)
                tile->unload();
            for (GLC3DTTile* tile : ts->front_state_->high_priority_load)
                tile->startLoad();
            for (GLC3DTTile* tile : ts->front_state_->medium_priority_load)
                tile->startLoad();
            for (GLC3DTTile* tile : ts->front_state_->low_priority_load)
                tile->startLoad();

            changed = true;
        }

        return TE_Ok;
    }

    void GLC3DTTileset::setParentTile(const GLC3DTTile* parent) NOTHROWS {
        // content should always have parent, but check anyway
        if (parent) {
            this->renderer_ = parent->tileset_->renderer_;
        }
    }

    void GLC3DTTileset::setParentRenderer(GLC3DTRenderer* renderer) NOTHROWS {
        this->renderer_ = renderer;
        if (renderer) {
            TAK::Engine::Feature::Envelope2 aabb;
            C3DTVolume_toRegionAABB(&aabb, &root_tile_->boundingVolume_);
            renderer->updateBounds(aabb);
        }
    }

    bool GLC3DTTileset::updateView(const CameraInfo& camera, uint64_t update_number) NOTHROWS {

        if (!root_tile_)
            return false;

        if (!this->pending_state_)
            this->pending_state_.reset(new UpdateChanges());

        this->pending_state_->clear();
        UpdateResult result = this->updateTileIfVisible(*root_tile_, camera, false, update_number);

        if (!result.canceled) {
            this->last_completed_frame_num_++;

            for (auto tile : this->pending_state_->visible_list) {
                // mark for needing load or verified as loaded
                if (tile->getState(this->last_completed_frame_num_ - 1) != GLC3DTTile::RENDERED) {
                    this->pending_state_->medium_priority_load.push_back(tile);
                }
                tile->setState(GLC3DTTile::RENDERED, this->last_completed_frame_num_);
            }

            for (auto tile : this->pending_state_->unload_list) {
                tile->setState(GLC3DTTile::CULLED, this->last_completed_frame_num_);
            }

#if C3DT_RENDERER_DEBUG_OUTPUT
            const char* lines =
                "\n"
                "frame= %d, canceled= %d\n"
                "first_tile_sse= %f\n"
                "num_render= %d\n"
                "num_unload= %d\n"
                "low_load= %d\n"
                "med_load= %d\n"
                "high_load= %d\n"
                "pending_load= %d\n"
                "cam.pos= <%f, %f, %f>\n"
                "cam.sseDenom= %f\n"
                ;
            int canceled = result.canceled ? 1 : 0;
            double ftsse = pending_state_ && pending_state_->visible_list.size() ? pending_state_->visible_list[0]->calcSSE(camera) : INFINITY;
            size_t num_rend = pending_state_ ? pending_state_->visible_list.size() : 0;
            size_t num_unload = pending_state_ ? pending_state_->unload_list.size() : 0;
            size_t low_load = pending_state_ ? pending_state_->low_priority_load.size() : 0;
            size_t med_load = pending_state_ ? pending_state_->medium_priority_load.size() : 0;
            size_t high_load = pending_state_ ? pending_state_->high_priority_load.size() : 0;
            Logger_log(TELL_Debug, lines,
                (unsigned int)this->last_completed_frame_num_ + 1,
                canceled,
                ftsse,
                (int)num_rend,
                (int)num_unload,
                (int)low_load,
                (int)med_load,
                (int)high_load,
                (int)load_count,
                camera.position.x, camera.position.y, camera.position.z,
                camera.sseDenom
            );
#endif
        }

        return !result.canceled;
    }

    bool testTileVisibility(const Frustum2& frustum, const GLC3DTTile& tile) NOTHROWS {

        // this is calculated as AABB currently
        C3DTBox box = tile.boundingVolume_.object.region_aux.aux.boundingBox;
        AABB mbb(
            Point2<double>(box.center.x - box.xDirHalfLen.x,
                box.center.y - box.yDirHalfLen.y,
                box.center.z - box.zDirHalfLen.z),
            Point2<double>(box.center.x + box.xDirHalfLen.x,
                box.center.y + box.yDirHalfLen.y,
                box.center.z + box.zDirHalfLen.z)
        );

        return const_cast<TAK::Engine::Math::Frustum2&>(frustum).intersects(mbb);
    }

    UpdateResult GLC3DTTileset::updateTileIfVisible(GLC3DTTile& tile, const CameraInfo& camera, bool ancestorMeetsSse, uint64_t update_number) NOTHROWS {
        bool vis = testTileVisibility(const_cast<Frustum2&>(camera.frustum), tile);
        if (vis) {
            return updateTile(tile, camera, ancestorMeetsSse, update_number);
        } else {
            markTileNonRendered(tile);
            markChildrenNonRendered(tile);
        }
        return UpdateResult();
    }

    TAKErr contentDidLoadTask(bool&, GLMapRenderable2* input, GLC3DTTile* parentTile) {
        // All C3DT content renderables share this common base

        --load_count;

        // nothing to do
        if (!input)
            return TE_Ok;

        GLC3DTContentRenderable* content = static_cast<GLC3DTContentRenderable*>(input);

        // apply once for content whose parent tile has a transform
        content->setParentTile(parentTile);
        return TE_Ok;
    }

    TAKErr startContentLoadTask(bool&, GLC3DTTile* tile) {
        if (tile->content_.getLoadState() == GLContentHolder::EMPTY) {
            String fullURI;
            TAKErr code = URI_combine(&fullURI, tile->tileset_->base_uri_, tile->content_uri_);
            if (code != TE_Ok)
                return code;
            tile->content_.load(tile->tileset_->content_context_, fullURI)
                // GeneralWorkers_immediate() will invoke on the worker it completes on (no scheduling to a queue).
                // In this case that is the GLThread right after GLContentHolder is updated, and before it has ever had
                // a chance to draw.
                .thenOn(GeneralWorkers_immediate(), contentDidLoadTask, tile);
        }
        return TE_Ok;
    }

    void GLC3DTTileset::markTileNonRendered(GLC3DTTile& tile) NOTHROWS {
        if (tile.getState(this->pending_view_update_number_) == GLC3DTTile::RENDERED)
            this->pending_state_->unload_list.push_back(&tile);
    }

    void GLC3DTTileset::markChildrenNonRendered(GLC3DTTile& tile) NOTHROWS {
        if (tile.getState(this->pending_view_update_number_) == GLC3DTTile::REFINED) {
            size_t numChildren = tile.childCount();
            for (size_t i = 0; i < numChildren; ++i) {
                GLC3DTTile& child = tile.first_child_[i];
                markTileNonRendered(tile);
                markChildrenNonRendered(tile);
            }
        }
    }

    UpdateResult GLC3DTTileset::updateTile(GLC3DTTile& tile, const CameraInfo& camera, bool ancestorMeetsSse, uint64_t update_number) NOTHROWS {

        // check for cancel
        if (this->updateIsCanceled(update_number)) {
            UpdateResult cancelResult;
            cancelResult.canceled = true;
            return cancelResult;
        }

        uint64_t pendingFrameNum = this->last_completed_frame_num_ + 1;
        GLC3DTTile::State lastTileState = tile.getState(this->last_completed_frame_num_);
        bool alreadyQueued = false;
        UpdateResult result;

        // SSE check for this tile
        double sse = tile.calcSSE(camera);
        bool sseOK = sse < maxScreenSpaceError;

        // sse is out of range, but tile is NOT renderable means we are forced to use any children, but we should render
        // this one when we can and follow up with another update

        bool proceed = sseOK;

        if (!sseOK && !tile.isRenderable()) {
            proceed = true;
            //TODO-- requires follow up
        }

        if (proceed) {

            size_t tileIndex = this->pending_state_->visible_list.size();
            bool addedTile = false;
            if (tile.hasPossibleContent()) {
                this->pending_state_->visible_list.push_back(&tile);
                addedTile = true;
            }

            UpdateResult childrenResult;
            size_t numChildren = tile.childCount();
            for (size_t i = 0; i < numChildren; ++i) {
                GLC3DTTile& child = tile.first_child_[i];
                if (testTileVisibility(camera.frustum, child)) {
                    UpdateResult childResult = updateTile(child, camera, sseOK, update_number);
                    childrenResult.allRenderable &= childResult.allRenderable;
                    childrenResult.canceled = childResult.canceled;
                    childrenResult.notYetRenderableCount += childResult.notYetRenderableCount;
                    childrenResult.anyRenderedPreviously |= childResult.anyRenderedPreviously;
                    if (childrenResult.canceled)
                        break;
                } else {
                    childrenResult.allRenderable = false;
                    if (child.getState(this->last_completed_frame_num_) == GLC3DTTile::RENDERED) {
                        // this tile is no longer visible-- move towards unloading
                        this->pending_state_->unload_list.push_back(&child);
                        unloadRenderedChildren(child);
                    }
                }
            }

            result.allRenderable &= childrenResult.allRenderable;
            result.canceled = childrenResult.canceled;
            result.notYetRenderableCount += childrenResult.notYetRenderableCount;
            result.anyRenderedPreviously |= childrenResult.anyRenderedPreviously;
            if (!result.canceled) {

                // if refinement is REPLACE and all visible children are renderable,
                // remove the tile from being rendered
                if (addedTile &&
                    tile.getRefine() == C3DTRefine::Replace &&
                    childrenResult.allRenderable) {

                    // unload it too if previously rendered-- no longer needed
                    if (lastTileState == GLC3DTTile::RENDERED) {
                        this->pending_state_->unload_list.push_back(&tile);
                    }

                    this->pending_state_->visible_list.erase(
                        this->pending_state_->visible_list.begin() + tileIndex);
                }
            }
        }

        return result;
    }

    void GLC3DTTileset::unloadRenderedChildren(GLC3DTTile& tile) NOTHROWS {
        size_t numChildren = tile.childCount();
        for (size_t i = 0; i < numChildren; ++i) {
            GLC3DTTile& child = tile.first_child_[i];
            if (child.getState(this->last_completed_frame_num_) == GLC3DTTile::RENDERED) {
                this->pending_state_->unload_list.push_back(&child);
                unloadRenderedChildren(child);
            }
        }
    }

#if 0
    UpdateResult GLC3DTTileset::updateTile(GLC3DTTile& tile, const CameraInfo& camera, bool ancestorMeetsSse, uint64_t update_number) NOTHROWS {

        // check for cancel
        if (this->updateIsCanceled(update_number)) {
            UpdateResult cancelResult;
            cancelResult.canceled = true;
            return cancelResult;
        }

        uint64_t pendingFrameNum = this->last_completed_frame_num_ + 1;
        GLC3DTTile::State lastTileState = tile.getState(this->last_completed_frame_num_);
        bool alreadyQueued = false;

        // leaf case, no children or refinement, already visible
        if (tile.children_.size() == 0) {
            this->pending_state_->visible_list.push_back(&tile);

            UpdateResult singleResult;
            singleResult.allRenderable = tile.isRenderable();
            singleResult.anyRenderedPreviously = lastTileState == GLC3DTTile::RENDERED;
            singleResult.notYetRenderableCount = singleResult.allRenderable ? 0 : 1;
            return singleResult;
        }

        // SSE check for this tile
        double sse = tile.calcSSE(camera);
        bool sseOK = sse < maxScreenSpaceError;

        bool loadingChildren = false;
        if (forbidHoles) {
            for (auto& child : tile.children_) {
                if (!child->isRenderable() && child->hasPossibleContent()
                    && testTileVisibility(camera.frustum, *child)) {
                    loadingChildren = true;
                    this->pending_state_->medium_priority_load.push_back(child.get());
                }
            }
        }

        if (sseOK || ancestorMeetsSse || loadingChildren) {

            // should render again or can finally
            if ((lastTileState == GLC3DTTile::RENDERED || lastTileState == GLC3DTTile::CULLED || 
                lastTileState == GLC3DTTile::NONE || tile.isRenderable()) && tile.hasPossibleContent()) {
                
                markChildrenNonRendered(tile);
                tile.setState(GLC3DTTile::RENDERED, pendingFrameNum);
                this->pending_state_->visible_list.push_back(&tile);
                if (sseOK)
                    this->pending_state_->medium_priority_load.push_back(&tile);

                UpdateResult singleResult;
                singleResult.allRenderable = tile.isRenderable();
                singleResult.anyRenderedPreviously = lastTileState == GLC3DTTile::RENDERED;
                singleResult.notYetRenderableCount = singleResult.allRenderable ? 0 : 1;
                return singleResult;
            }

            // this is now the ancestor, but need to load
            ancestorMeetsSse = true;
            if (sseOK && tile.hasPossibleContent()) {
                this->pending_state_->high_priority_load.push_back(&tile);
                alreadyQueued = true;
            }
        }

        if (tile.refine_ == C3DTRefine::Add && tile.hasPossibleContent()) {
            this->pending_state_->visible_list.push_back(&tile);
            this->pending_state_->medium_priority_load.push_back(&tile);
            alreadyQueued = true;
        }

        size_t preChildUpdateVisibleCount = this->pending_state_->visible_list.size();
        size_t preChildUpdateLowLoadCount = this->pending_state_->low_priority_load.size();
        size_t preChildUpdateMedLoadCount = this->pending_state_->medium_priority_load.size();
        size_t preChildUpdateHighLoadCount = this->pending_state_->high_priority_load.size();

        UpdateResult childrenResult = this->updateChildren(tile, camera, ancestorMeetsSse, update_number);

        if (preChildUpdateVisibleCount == this->pending_state_->visible_list.size()) {
            UpdateResult emptyResult;

            if (tile.refine_ == C3DTRefine::Add) {
                emptyResult.allRenderable = tile.isRenderable();
                emptyResult.anyRenderedPreviously = lastTileState == GLC3DTTile::RENDERED;
                emptyResult.notYetRenderableCount = childrenResult.allRenderable ? 0 : 1;
            } else {
                markTileNonRendered(tile);
            }

            tile.setState(GLC3DTTile::REFINED, pendingFrameNum);
            return emptyResult;
        }

        if (!childrenResult.allRenderable && !childrenResult.anyRenderedPreviously) {
            
            // all children must be ready before we move on
            std::vector<GLC3DTTile*>& visList = this->pending_state_->visible_list;
            for (size_t i = preChildUpdateVisibleCount; i < visList.size(); ++i) {

                GLC3DTTile* descTile = visList[i];
                while (descTile != nullptr && !descTile->wasKicked(pendingFrameNum) && descTile != &tile) {
                    descTile->setKicked();
                    descTile = descTile->parent_;
                }
            }

            // remove all beyond this point from visList and load queues

            visList.erase(visList.begin() + preChildUpdateVisibleCount, visList.end());
            visList.push_back(&tile);
            tile.setState(GLC3DTTile::RENDERED, pendingFrameNum);

            if ((lastTileState != GLC3DTTile::RENDERED || !tile.isRenderable()) && childrenResult.notYetRenderableCount > loadingDescendantLimit) {
                this->pending_state_->low_priority_load.erase(this->pending_state_->low_priority_load.begin() + preChildUpdateLowLoadCount, this->pending_state_->low_priority_load.end());
                this->pending_state_->medium_priority_load.erase(this->pending_state_->medium_priority_load.begin() + preChildUpdateMedLoadCount, this->pending_state_->medium_priority_load.end());
                this->pending_state_->high_priority_load.erase(this->pending_state_->high_priority_load.begin() + preChildUpdateHighLoadCount, this->pending_state_->high_priority_load.end());

                if (!alreadyQueued) {
                    this->pending_state_->medium_priority_load.push_back(&tile);
                }
                childrenResult.notYetRenderableCount = tile.isRenderable() ? 0 : 1;
                alreadyQueued = true;
            }

            childrenResult.allRenderable = tile.isRenderable();
            childrenResult.anyRenderedPreviously = lastTileState == GLC3DTTile::RENDERED;
        }
        else {
            if (tile.refine_ != C3DTRefine::Add) {
                markTileNonRendered(tile);
            }
            tile.setState(GLC3DTTile::REFINED, pendingFrameNum);
        }

#if 0
        if (!alreadyQueued) {
            this->pending_state_->low_priority_load.push_back(&tile);
        }
#endif

        return childrenResult;
    }
#endif

    UpdateResult GLC3DTTileset::updateChildren(GLC3DTTile& tile, const CameraInfo& camera, bool ancestorMeetsSse, uint64_t update_number) NOTHROWS {
        UpdateResult result;
        size_t numChildren = tile.childCount();
        for (size_t i = 0; i < numChildren; ++i) {
            GLC3DTTile& child = tile.first_child_[i];
            UpdateResult childResult = updateTileIfVisible(child, camera, ancestorMeetsSse, update_number);
            result.allRenderable &= childResult.allRenderable;
            result.anyRenderedPreviously |= childResult.anyRenderedPreviously;
            result.notYetRenderableCount += childResult.notYetRenderableCount;
        }
        return result;
    }

    void GLC3DTTileset::draw(const GLGlobeBase& view, const int renderPass) NOTHROWS {

        if (current_camera_.location != view.renderPass->scene.camera.location ||
            current_camera_.target != view.renderPass->scene.camera.target) {

            current_camera_ = view.renderPass->scene.camera;

            // Switch to the next view update
            this->pending_view_update_number_++;
            this->update_task_ = Task_begin(this->view_update_worker_, updateViewTask, this, this->pending_view_update_number_, view.renderPass->scene, std::move(this->recycle_state_))
                .thenOn(GLWorkers_glThread(), receiveUpdateTask, this);
        }

        if (this->front_state_) {
            for (GLC3DTTile* tile : this->front_state_->visible_list)
                tile->draw(view, renderPass, content_context_);
        }
    }

    //
    // GLC3DTTile
    //

    GLC3DTTile::GLC3DTTile(GLC3DTTileset* tileset, const TAK::Engine::Formats::Cesium3DTiles::C3DTTile& tile, GLC3DTTile* parentGLTile)
        : tileset_(tileset),
        parent_(parentGLTile),
        transform_(/*tile.transform*/),
        geometric_error_(tile.geometricError),
        refine_(static_cast<uint8_t>(tile.refine)),
        boundingVolume_(tile.boundingVolume),
        content_uri_(tile.content.uri),
        last_state_frame_(0),
        last_state_(NONE)
    {
        if (parentGLTile)
            transform_ = parentGLTile->transform_;
        transform_.concatenate(tile.transform);
        C3DTVolume_transform(&boundingVolume_, tile.boundingVolume, transform_);
        
        first_child_ = tileset->allocator_.allocTiles(tile.childCount);
        last_child_ = first_child_;

        // pre-calculate aux for region
        if (boundingVolume_.type == C3DTVolume::Region) {
            C3DTRegion_calcAux(&boundingVolume_.object.region_aux.aux, boundingVolume_.object.region);
            boundingVolume_.type = C3DTVolume::RegionAux;
        }
    }

    TAKErr GLC3DTTile::gatherDepthSamplerDrawables(std::vector<GLDepthSamplerDrawable*>& result, int levelDepth, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS {

        // add content which bedrocks in GLMeshes, which can depth sample draw
        if (content_.getLoadState() == GLContentHolder::LOADED) {
            GLC3DTContentRenderable* renderable = static_cast<GLC3DTContentRenderable*>(content_.getMapRenderable());
            if (renderable)
                renderable->gatherDepthSamplerDrawables(result, levelDepth, sceneModel, x, y);
        }

        // ignoring levelDepth-- considering the whole Cesium visible scene as 1 level
        size_t numChildren = this->childCount();
        for (size_t i = 0; i < numChildren; ++i) {
            GLC3DTTile& child = this->first_child_[i];
            child.gatherDepthSamplerDrawables(result, levelDepth, sceneModel, x, y);
        }

        return TE_Ok;
    }

    void GLC3DTTile::draw(const GLGlobeBase& view, const int renderPass, GLContentContext& content_context) NOTHROWS {
        GLMapRenderable2* renderable = content_.getMapRenderable();
        if (renderable)
            renderable->draw(view, renderPass);
#if 0
        this->drawAABB(view);
#endif
    }

    void GLC3DTTile::drawAABB(const GLGlobeBase& view) {

        // only for regions right now
        if (boundingVolume_.type != C3DTVolume::RegionAux) {
            return;
        }


        if (!wireframe_shader_.get()) {
            Shader_get(wireframe_shader_, view.context, RenderAttributes());
        }

        glUseProgram(wireframe_shader_->handle);

        Matrix2 t(view.renderPass->scene.forwardTransform);
        t.concatenate(transform_);

        double mxd[16];
        t.get(mxd, Matrix2::COLUMN_MAJOR);
        float mxf[16];
        for (std::size_t i = 0u; i < 16u; i++)
            mxf[i] = static_cast<float>(mxd[i]);
        glUniformMatrix4fv(wireframe_shader_->uModelView, 1, false, mxf);
        
        atakmap::renderer::GLES20FixedPipeline::getInstance()->readMatrix(atakmap::renderer::GLES20FixedPipeline::MM_GL_PROJECTION, mxf);
        glUniformMatrix4fv(wireframe_shader_->uProjection, 1, false, mxf);
        
        glEnableVertexAttribArray(wireframe_shader_->aVertexCoords);

        const C3DTBox &box = this->boundingVolume_.object.region_aux.aux.boundingBox;

        Point2<float> min((float)(box.center.x - box.xDirHalfLen.x),
            (float)(box.center.y - box.yDirHalfLen.y),
            (float)(box.center.z - box.zDirHalfLen.z));

        Point2<float> max((float)(box.center.x + box.xDirHalfLen.x),
            (float)(box.center.y + box.yDirHalfLen.y),
            (float)(box.center.z + box.zDirHalfLen.z));

        float verts[] = {
            min.x, min.y, min.z,
            max.x, min.y, min.z,

            max.x, min.y, min.z,
            max.x, max.y, min.z,

            max.x, max.y, min.z,
            min.x, max.y, min.z,

            min.x, max.y, min.z,
            min.x, min.y, min.z,

            //
            min.x, min.y, max.z,
            max.x, min.y, max.z,

            max.x, min.y, max.z,
            max.x, max.y, max.z,

            max.x, max.y, max.z,
            min.x, max.y, max.z,

            min.x, max.y, max.z,
            min.x, min.y, max.z,

            //
            min.x, min.y, min.z,
            min.x, min.y, max.z,

            max.x, min.y, min.z,
            max.x, min.y, max.z,

            max.x, max.y, min.z,
            max.x, max.y, max.z,

            min.x, max.y, min.z,
            min.x, max.y, max.z
        };

        GLsizei stride = 3 * 4;
        GLsizei count =  24;

        glVertexAttribPointer(wireframe_shader_->aVertexCoords, 3, GL_FLOAT, false, stride, verts);
        glUniform4f(this->wireframe_shader_->uColor, 0.6f, 0.0f, 0.0f, 1.0f);
        glDrawArrays(GL_LINES, 0, count);
        glDisableVertexAttribArray(this->wireframe_shader_->aVertexCoords);
    }

    bool GLC3DTTile::isRenderable() const NOTHROWS {
        return content_uri_.get() && 
            content_.getLoadState() == GLContentHolder::LOADED;
    }

    double GLC3DTTile::calcSSE(const CameraInfo& camera) const NOTHROWS {
        double dist = 0;
        C3DTVolume_distanceSquaredToPosition(&dist, boundingVolume_, camera.position);
        return (geometric_error_ * camera.viewportHeight) / (std::max(dist, 1e-7) * camera.sseDenom);
    }

    void GLC3DTTile::startLoad() NOTHROWS {
        GLContentHolder::LoadState loadState = content_.getLoadState();
        if (loadState == GLContentHolder::EMPTY && this->content_uri_.get()) {
            String fullURI;
            TAKErr code = URI_combine(&fullURI, tileset_->base_uri_, content_uri_);
            if (code != TE_Ok)
                return;
            ++load_count;
            content_.load(tileset_->content_context_, fullURI)
                // GeneralWorkers_immediate() will invoke on the worker it completes on (no scheduling to a queue).
                // In this case that is the GLThread right after GLContentHolder is updated, and before it has ever had
                // a chance to draw.
                .thenOn(GeneralWorkers_immediate(), contentDidLoadTask, this);
        }
    }

    void GLC3DTTile::unload() NOTHROWS {
        if (content_.getLoadState() != GLContentHolder::EMPTY) {
            content_.unload();
        }
    }

    //
    // GLB3DM
    //
    
    GLB3DM::GLB3DM(std::shared_ptr<GLBatch> batch) NOTHROWS
    : batch_(batch)
    { }

    GLB3DM::~GLB3DM() NOTHROWS {
    }

    void GLB3DM::draw(const GLGlobeBase& view, const int renderPass) NOTHROWS {
        RenderState state(RenderState_getCurrent());
        RenderState stored(state);

        float proj[16];
        atakmap::renderer::GLES20FixedPipeline::getInstance()->readMatrix(atakmap::renderer::GLES20FixedPipeline::MM_GL_PROJECTION, proj);
        batch_->execute(state, view.renderPass->scene.forwardTransform, proj);

        RenderState_makeCurrent(stored);
    }

    TAKErr GLB3DM::gatherDepthSamplerDrawables(std::vector<GLDepthSamplerDrawable*>& result, int levelDepth, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS {
        result.push_back(this);
        return TE_Ok;
    }

    void GLB3DM::depthSamplerDraw(GLDepthSampler& sampler, const TAK::Engine::Core::MapSceneModel2& sceneModel) NOTHROWS {
        RenderState state(RenderState_getCurrent());
        RenderState stored(state);

        float proj[16];
        atakmap::renderer::GLES20FixedPipeline::getInstance()->readMatrix(atakmap::renderer::GLES20FixedPipeline::MM_GL_PROJECTION, proj);
        batch_->execute(state, sceneModel.forwardTransform, proj, GLBatch::SHADER_OVERRIDE_BIT);

        RenderState_makeCurrent(stored);
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
        if (!tileset->root_tile_) {
            tileset->root_tile_.reset(new GLC3DTTile(tileset.get(), tile, par));
            glTile = tileset->root_tile_.get();
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
        //TODO-- determine if streaming
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

TAKErr GLC3DTRenderer::LoaderImpl::processB3DM(TAK::Engine::Renderer::Core::GLMapRenderable2Ptr& output, TAK::Engine::Core::RenderContext& ctx, ScenePtr &scenePtr, const String& baseURI, const char* URI, bool isStreaming) NOTHROWS {

    TAKErr code = TE_Ok;

    // we need to share the scene to the loader workers
    std::shared_ptr<Scene> scene(scenePtr.release(), scenePtr.get_deleter());

    // making a bunch of assumptions because this is known to be B3DM, but has sanity checks
    SceneNode& node = scene->getRootNode();

    Collection<std::shared_ptr<SceneNode>>::IteratorPtr iter(nullptr, nullptr);
    node.getChildren(iter);
    std::shared_ptr<SceneNode> item;

    GLBatchBuilder batchBuilder;
    if (node.getLocalFrame())
        batchBuilder.setLocalFrame(*node.getLocalFrame());

    // sanity check
    if (!iter)
        return TE_Err;

    while (iter->get(item) == TE_Ok) {
        if (item && item->hasMesh()) { // should be the case
            std::shared_ptr<const Mesh> mesh;
            item->loadMesh(mesh);
            
            GLenum front_face = GL_CCW;
            
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
            VertexStreamConfig streams[3];
            size_t streamIndex2 = 0;

            if (handleConfigAttr(streams[streamIndex2], vdl, vdl.position, TEVA_Position)) ++streamIndex2;
            if (handleConfigAttr(streams[streamIndex2], vdl, vdl.texCoord0, TEVA_TexCoord0)) ++streamIndex2;
            if (handleConfigAttr(streams[streamIndex2], vdl, vdl.color, TEVA_Color)) ++streamIndex2;

            if (vdl.interleaved) {
#if C3DT_DEBUG_RESOURCE_RELEASE
                GLBatch::BufferPtr buf = std::shared_ptr<GLBatch::Buffer>(new GLBatch::Buffer(), SafeResourceDeleter(ctx));
#else
                GLBatch::BufferPtr buf = std::make_shared<GLBatch::Buffer>();
#endif
                for (size_t i = 0; i < streamIndex2; ++i)
                    streams[i].buffer = buf;
            } else {
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
                        URI_getRelative(&relative, baseURI, URI);
                        StringBuilder sb;
                        StringBuilder_combine(sb, relative.get(), "#", mat.textureUri);
                        texKey = sb.c_str();
                    } else if (URI_getRelative(&relative, baseURI, texKey) == TE_Ok) {
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
                            loadMeshBufferBitmap(bitmap, mesh, bufferIndex);
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
                batchBuilder.drawElements(draw_mode, num_indices, indexBufferIndex);
            else
                batchBuilder.drawArrays(draw_mode, num_verts);

            // draw call adds buffers?
            for (size_t i = batchBufferIndex; i < batchBuilder.bufferCount(); ++i) {


                if (i == indexBufferIndex) {
                    batchBuilder.bufferAt(i)->load(mesh->getIndices(), 
                        num_indices * indexSize, false);
                } else {
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
                    } else {
                        // safe to call from load thread (NO GL calls invoked)
                        batchBuilder.bufferAt(i)->load(verts, bufSize, false);
                    }
                }                
            }
        }
        iter->next();
    }

    // Load shader tasks
    SharedGLBatchPtr batch;
    batchBuilder.buildShared(batch);
    for (size_t i = 0; i < batch->shaderCount(); ++i) {
        RenderAttributes attrs;
        batchBuilder.shaderRenderAttributes(&attrs, i);
        Task_begin(GLWorkers_resourceLoad(), loadShaderTask, &ctx, batch, i, attrs);
    }
    
    if (code == TE_Ok) {
        output = GLMapRenderable2Ptr(new GLB3DM(batch), Memory_deleter_const<GLMapRenderable2, GLB3DM>);
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
                TilesetParser args(ctx, URI, this->makeChildContentLoader(ctx));
                code = C3DTTileset_parse(input.get(), &args, TilesetParser::visitor);
                if (code == TE_Ok)
                    tileset = std::move(args.tileset);
            } else if (type == C3DTFileType_B3DM) {
                code = C3DT_parseB3DMScene(scene, sceneInfo, input.get(), baseURI, fileURI);
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
                code = processB3DM(output, ctx, scene, baseURI, URI, isStreaming);
            }
        }
    }

    if (input)
        input->close();

    return code;
}
