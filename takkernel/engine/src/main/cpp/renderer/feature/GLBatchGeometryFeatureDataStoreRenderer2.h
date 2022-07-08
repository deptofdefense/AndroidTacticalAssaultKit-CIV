#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYFEATUREDATASTORERENDERER2_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYFEATUREDATASTORERENDERER2_H_INCLUDED

#include <list>
#include <map>
#include <memory>

#include "feature/FeatureDataStore2.h"
#include "feature/Envelope2.h"
#include "feature/HitTestService2.h"
#include "feature/SpatialCalculator2.h"
#include "renderer/GLRenderContext.h"
#include "renderer/core/GLAsynchronousMapRenderable3.h"
#include "renderer/feature/DefaultSpatialFilterControl.h"
#include "renderer/feature/GLBatchGeometryRenderer3.h"
#include "feature/SpatialFilter.h"
#include "renderer/feature/SpatialFilterControl.h"

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
                    class SpatialFilterControlImpl;
                public:
                    GLBatchGeometryFeatureDataStoreRenderer2(TAK::Engine::Core::RenderContext &surface, TAK::Engine::Feature::FeatureDataStore2 &subject) NOTHROWS;
                    GLBatchGeometryFeatureDataStoreRenderer2(TAK::Engine::Core::RenderContext &surface, TAK::Engine::Feature::FeatureDataStore2 &subject, const GLBatchGeometryRenderer3::CachePolicy &cachingPolicy) NOTHROWS;
                    GLBatchGeometryFeatureDataStoreRenderer2(TAK::Engine::Core::RenderContext &surface, TAK::Engine::Feature::FeatureDataStore2 &subject, const GLBatchGeometryRenderer3::CachePolicy &cachingPolicy, const GLBatchGeometryFeatureDataStoreRendererOptions2 &opts) NOTHROWS;
                public: // GLAsynchronousMapRenderable
                    void draw(const TAK::Engine::Renderer::Core::GLGlobeBase &view, const int renderPass) NOTHROWS override;
                    int getRenderPass() NOTHROWS override;
                    void start() NOTHROWS override;
                    void stop() NOTHROWS override;
                public:
                    void setVisible(bool visible) NOTHROWS;
                private :
                    Util::TAKErr getRenderables(Port::Collection<TAK::Engine::Renderer::Core::GLMapRenderable2 *>::IteratorPtr &iter) NOTHROWS override;
                protected:
                    void initImpl(const GLGlobeBase &view) NOTHROWS override;
                    Util::TAKErr releaseImpl() NOTHROWS override;
                    Util::TAKErr createQueryContext(QueryContextPtr &value) NOTHROWS override;
                    Util::TAKErr resetQueryContext(QueryContext &pendingData) NOTHROWS override;
                    Util::TAKErr updateRenderableLists(QueryContext &pendingData) NOTHROWS override;
                    Util::TAKErr getBackgroundThreadName(Port::String &value) NOTHROWS override;
                    Util::TAKErr query(QueryContext &result, const TAK::Engine::Renderer::Core::GLMapView2::State &state) NOTHROWS override;
                private:
                    Util::TAKErr queryImpl(QueryContext &result, const TAK::Engine::Renderer::Core::GLMapView2::State &state) NOTHROWS;
                public: // FeatureDataStore2.OnDataStoreContentChangedListener
                    void onDataStoreContentChanged(TAK::Engine::Feature::FeatureDataStore2 &data_store) NOTHROWS override;

                    /**************************************************************************/
                    // Hit Test Service

                public:
                    virtual Util::TAKErr hitTest2(Port::Collection<int64_t> &features, const float screenX, const float screenY, const atakmap::core::GeoPoint &touch, const double resolution, const float radius, const std::size_t limit) NOTHROWS;
                public:
                    Util::TAKErr getControl(void **ctrl, const char *type) const NOTHROWS;
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
                    Thread::Mutex mutex_;

                    TAK::Engine::Feature::FeatureDataStore2 &dataStore;

                    std::unique_ptr<SpatialFilterControlImpl> spatial_filter_control_;
                    std::map<std::string, void *> controls_;

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

                class GLBatchGeometryFeatureDataStoreRenderer2::SpatialFilterControlImpl : public Renderer::Feature::SpatialFilterControl
                {
                public:
                    SpatialFilterControlImpl(GLBatchGeometryFeatureDataStoreRenderer2 &owner) NOTHROWS;
                public:
                    Util::TAKErr setSpatialFilters(Port::Collection<std::shared_ptr<Engine::Feature::SpatialFilter>> *spatial_filters) NOTHROWS override;
                public :
                    DefaultSpatialFilterControl impl;
                private:
                    GLBatchGeometryFeatureDataStoreRenderer2 &owner_;
                };
            }
        }
    }
}

#endif
