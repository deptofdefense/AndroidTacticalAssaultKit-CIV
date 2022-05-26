#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYFEATUREDATASTORERENDERER_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYFEATUREDATASTORERENDERER_H_INCLUDED

#include <list>
#include <map>
#include <memory>

#include "feature/FeatureDataStore2.h"
#include "feature/HitTestService2.h"
#include "renderer/GLRenderContext.h"
#include "renderer/core/GLAsynchronousMapRenderable2.h"
#include "renderer/feature/GLBatchGeometryRenderer2.h"
#include "util/NonCopyable.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {
                
                struct ENGINE_API GLBatchGeometryFeatureDataStoreRendererOptions {
                    GLBatchGeometryFeatureDataStoreRendererOptions() NOTHROWS;
                    
                    bool skipSameLodOptimization;
                };
                
                class ENGINE_API GLBatchGeometryFeatureDataStoreRenderer :
                    public TAK::Engine::Renderer::Core::GLAsynchronousMapRenderable2,
                    public TAK::Engine::Feature::FeatureDataStore2::OnDataStoreContentChangedListener,
                    TAK::Engine::Util::NonCopyable
                {
                private:
                    struct GLGeometryRecord;
                    class HitTestImpl;
                public:
                    GLBatchGeometryFeatureDataStoreRenderer(TAK::Engine::Core::RenderContext &surface, TAK::Engine::Feature::FeatureDataStore2 &subject) NOTHROWS;
                    GLBatchGeometryFeatureDataStoreRenderer(TAK::Engine::Core::RenderContext &surface, TAK::Engine::Feature::FeatureDataStore2 &subject, const GLBatchGeometryRenderer2::CachePolicy &cachingPolicy) NOTHROWS;
                    GLBatchGeometryFeatureDataStoreRenderer(TAK::Engine::Core::RenderContext &surface, TAK::Engine::Feature::FeatureDataStore2 &subject, const GLBatchGeometryRenderer2::CachePolicy &cachingPolicy, const GLBatchGeometryFeatureDataStoreRendererOptions &opts) NOTHROWS;
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
                    virtual void onDataStoreContentChanged(TAK::Engine::Feature::FeatureDataStore2 &data_store) NOTHROWS;

                    /**************************************************************************/
                    // Hit Test Service

                public:
                    virtual Util::TAKErr hitTest2(Port::Collection<int64_t> &features, const float screenX, const float screenY, const atakmap::core::GeoPoint &touch, const double resolution, const float radius, const std::size_t limit) NOTHROWS;
                private:
                    //System::Collections::Generic::Dictionary<int, Statistics> queryStats;
                    std::list<atakmap::renderer::map::GLMapRenderable *> renderList;
                    TAK::Engine::Core::RenderContext &surface;

                    std::unique_ptr<TAK::Engine::Renderer::Feature::GLBatchGeometryRenderer2> batchRenderer;

                    std::map<int64_t, GLGeometryRecord> glSpatialItems;

                    TAK::Engine::Feature::FeatureDataStore2 &dataStore;

                protected:
                    std::shared_ptr<atakmap::core::Service> hittest;
                    
                private:
                    GLBatchGeometryFeatureDataStoreRendererOptions options;
                };

                struct GLBatchGeometryFeatureDataStoreRenderer::GLGeometryRecord
                {
                    std::shared_ptr<GLBatchGeometry2> geometry;
                    int64_t touched;
                };

                class GLBatchGeometryFeatureDataStoreRenderer::HitTestImpl : public TAK::Engine::Feature::HitTestService2
                {
                public:
                    HitTestImpl(GLBatchGeometryFeatureDataStoreRenderer &owner);
                public:
                    virtual Util::TAKErr hitTest(Port::Collection<int64_t> &fids, const float screenX, const float screenY, const atakmap::core::GeoPoint &touch, const double resolution, const float radius, const std::size_t limit) NOTHROWS;
                private:
                    GLBatchGeometryFeatureDataStoreRenderer &owner;
                };
            }
        }
    }
}

#endif
