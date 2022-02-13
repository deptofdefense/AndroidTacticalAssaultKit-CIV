#ifndef TAK_ENGINE_RENDERER_FEATURE_GLPERSISTENTFEATUREDATASTORERENDERER_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLPERSISTENTFEATUREDATASTORERENDERER_H_INCLUDED

#include <list>
#include <map>
#include <memory>

#include "feature/FeatureDataStore2.h"
#include "feature/HitTestService2.h"
#include "renderer/GLRenderContext.h"
#include "renderer/core/GLAsynchronousMapRenderable2.h"
#include "renderer/feature/GLBatchGeometryRenderer2.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {
                
                struct GLPersistentFeatureDataStoreRendererOptions {
                    inline GLPersistentFeatureDataStoreRendererOptions() NOTHROWS
                    : skipSameLodOptimization(false) { }
                    
                    bool skipSameLodOptimization;
                };
                
                class GLPersistentFeatureDataStoreRenderer :
                    public TAK::Engine::Renderer::Core::GLAsynchronousMapRenderable2,
                    public TAK::Engine::Feature::FeatureDataStore2::OnDataStoreContentChangedListener,
                    PGSC::NonCopyable
                {
                private:
                    struct GLGeometryRecord;
                    class HitTestImpl;
                public:
                    GLPersistentFeatureDataStoreRenderer(atakmap::renderer::GLRenderContext *surface, TAK::Engine::Feature::FeatureDataStore2 &subject) NOTHROWS;
                    GLPersistentFeatureDataStoreRenderer(atakmap::renderer::GLRenderContext *surface, TAK::Engine::Feature::FeatureDataStore2 &subject, const GLBatchGeometryRenderer2::CachePolicy &cachingPolicy) NOTHROWS;
                    GLPersistentFeatureDataStoreRenderer(atakmap::renderer::GLRenderContext *surface, TAK::Engine::Feature::FeatureDataStore2 &subject, const GLBatchGeometryRenderer2::CachePolicy &cachingPolicy, const GLPersistentFeatureDataStoreRendererOptions &opts) NOTHROWS;
                public: // GLAsynchronousMapRenderable
                    virtual void draw(const atakmap::renderer::map::GLMapView *view);
                    virtual void start();
                    virtual void stop();
                private :
                    virtual Util::TAKErr getRenderables(Port::Collection<atakmap::renderer::map::GLMapRenderable *>::IteratorPtr &iter) NOTHROWS;
                protected:
                    virtual void initImpl(const atakmap::renderer::map::GLMapView *view) NOTHROWS;
                    virtual Util::TAKErr releaseImpl() NOTHROWS;
                    virtual Util::TAKErr createQueryContext(QueryContextPtr &value) NOTHROWS;
                    virtual Util::TAKErr resetQueryContext(QueryContext &pendingData) NOTHROWS;
                    virtual Util::TAKErr updateRenderableLists(QueryContext &pendingData) NOTHROWS;
                    virtual Util::TAKErr setBackgroundThreadName(WorkerThread &worker) NOTHROWS;
                    virtual Util::TAKErr query(QueryContext &result, const ViewState &state) NOTHROWS;
                private:
                    Util::TAKErr queryImpl(QueryContext &result, const ViewState &state) NOTHROWS;
                public: // FeatureDataStore2.OnDataStoreContentChangedListener
                    virtual void onDataStoreContentChanged(TAK::Engine::Feature::FeatureDataStore2 &dataStore) NOTHROWS;

                    /**************************************************************************/
                    // Hit Test Service

                public:
                    virtual Util::TAKErr hitTest2(Port::Collection<int64_t> &features, const float screenX, const float screenY, const atakmap::core::GeoPoint &touch, const double resolution, const float radius, const std::size_t limit) NOTHROWS;
                private:
                    //System::Collections::Generic::Dictionary<int, Statistics> queryStats;
                    std::list<atakmap::renderer::map::GLMapRenderable *> renderList;
                    atakmap::renderer::GLRenderContext * const surface;

                    std::unique_ptr<TAK::Engine::Renderer::Feature::GLBatchGeometryRenderer2> batchRenderer;

                    std::map<int64_t, GLGeometryRecord> glSpatialItems;

                    TAK::Engine::Feature::FeatureDataStore2 &dataStore;

                protected:
                    std::shared_ptr<atakmap::core::Service> hittest;
                    
                private:
                    GLPersistentFeatureDataStoreRendererOptions options;
                };

                struct GLPersistentFeatureDataStoreRenderer::GLGeometryRecord
                {
                    std::shared_ptr<GLBatchGeometry2> geometry;
                    int64_t touched;
                };

                class GLPersistentFeatureDataStoreRenderer::HitTestImpl : public TAK::Engine::Feature::HitTestService2
                {
                public:
                    HitTestImpl(GLPersistentFeatureDataStoreRenderer &owner);
                public:
                    virtual Util::TAKErr hitTest(Port::Collection<int64_t> &fids, const float screenX, const float screenY, const atakmap::core::GeoPoint &touch, const double resolution, const float radius, const std::size_t limit) NOTHROWS;
                private:
                    GLPersistentFeatureDataStoreRenderer &owner;
                };
            }
        }
    }
}

#endif
