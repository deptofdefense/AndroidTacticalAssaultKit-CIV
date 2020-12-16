#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYFEATUREDATASTORERENDERER2_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYFEATUREDATASTORERENDERER2_H_INCLUDED

#include <list>
#include <map>
#include <memory>

#include "feature/FeatureDataStore2.h"
#include "feature/HitTestService2.h"
#include "renderer/GLRenderContext.h"
#include "renderer/core/GLAsynchronousMapRenderable3.h"
#include "renderer/feature/GLBatchGeometryRenderer3.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {
                
                struct ENGINE_API GLBatchGeometryFeatureDataStoreRendererOptions2 {
                    GLBatchGeometryFeatureDataStoreRendererOptions2() NOTHROWS;
                    
                    bool skipSameLodOptimization;
                };
                
                class ENGINE_API GLBatchGeometryFeatureDataStoreRenderer2 :
                    public TAK::Engine::Renderer::Core::GLAsynchronousMapRenderable3,
                    public TAK::Engine::Feature::FeatureDataStore2::OnDataStoreContentChangedListener,
                    TAK::Engine::Util::NonCopyable
                {
                private:
                    struct GLGeometryRecord;
                    class HitTestImpl;
                public:
                    GLBatchGeometryFeatureDataStoreRenderer2(TAK::Engine::Core::RenderContext &surface, TAK::Engine::Feature::FeatureDataStore2 &subject) NOTHROWS;
                    GLBatchGeometryFeatureDataStoreRenderer2(TAK::Engine::Core::RenderContext &surface, TAK::Engine::Feature::FeatureDataStore2 &subject, const GLBatchGeometryRenderer3::CachePolicy &cachingPolicy) NOTHROWS;
                    GLBatchGeometryFeatureDataStoreRenderer2(TAK::Engine::Core::RenderContext &surface, TAK::Engine::Feature::FeatureDataStore2 &subject, const GLBatchGeometryRenderer3::CachePolicy &cachingPolicy, const GLBatchGeometryFeatureDataStoreRendererOptions2 &opts) NOTHROWS;
                public: // GLAsynchronousMapRenderable
                    virtual void draw(const TAK::Engine::Renderer::Core::GLMapView2 &view, const int renderPass) NOTHROWS;
                    virtual int getRenderPass() NOTHROWS;
                    virtual void start() NOTHROWS;
                    virtual void stop() NOTHROWS;
                private :
                    virtual Util::TAKErr getRenderables(Port::Collection<TAK::Engine::Renderer::Core::GLMapRenderable2 *>::IteratorPtr &iter) NOTHROWS;
                protected:
                    virtual void initImpl(const TAK::Engine::Renderer::Core::GLMapView2 &view) NOTHROWS;
                    virtual Util::TAKErr releaseImpl() NOTHROWS;
                    virtual Util::TAKErr createQueryContext(QueryContextPtr &value) NOTHROWS;
                    virtual Util::TAKErr resetQueryContext(QueryContext &pendingData) NOTHROWS;
                    virtual Util::TAKErr updateRenderableLists(QueryContext &pendingData) NOTHROWS;
                    virtual Util::TAKErr getBackgroundThreadName(Port::String &value) NOTHROWS;
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
                    std::list<TAK::Engine::Renderer::Core::GLMapRenderable2 *> renderList;
                    TAK::Engine::Core::RenderContext &surface;
                    float renderHeightPump;

                    std::unique_ptr<TAK::Engine::Renderer::Feature::GLBatchGeometryRenderer3> batchRenderer1;
                    std::unique_ptr<TAK::Engine::Renderer::Feature::GLBatchGeometryRenderer3> batchRenderer2;

                    GLBatchGeometryRenderer3 *front;
                    GLBatchGeometryRenderer3 *back;

                    std::map<int64_t, GLGeometryRecord> glSpatialItems;

                    TAK::Engine::Feature::FeatureDataStore2 &dataStore;

                protected:
                    std::shared_ptr<atakmap::core::Service> hittest;
                    
                private:
                    GLBatchGeometryFeatureDataStoreRendererOptions2 options;
                };

                struct GLBatchGeometryFeatureDataStoreRenderer2::GLGeometryRecord
                {
                    std::shared_ptr<GLBatchGeometry3> geometry;
                    int64_t touched {0};
                    bool visible {false};
                };

                class GLBatchGeometryFeatureDataStoreRenderer2::HitTestImpl : public TAK::Engine::Feature::HitTestService2
                {
                public:
                    HitTestImpl(GLBatchGeometryFeatureDataStoreRenderer2 &owner);
                public:
                    virtual Util::TAKErr hitTest(Port::Collection<int64_t> &fids, const float screenX, const float screenY, const atakmap::core::GeoPoint &touch, const double resolution, const float radius, const std::size_t limit) NOTHROWS;
                private:
                    GLBatchGeometryFeatureDataStoreRenderer2 &owner;
                };
            }
        }
    }
}

#endif
