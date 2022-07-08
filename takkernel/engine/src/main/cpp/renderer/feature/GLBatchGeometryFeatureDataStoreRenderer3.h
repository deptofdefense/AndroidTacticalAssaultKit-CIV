#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYFEATUREDATASTORERENDERER3_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYFEATUREDATASTORERENDERER3_H_INCLUDED

#include <list>
#include <map>
#include <memory>

#include "feature/FeatureDataStore2.h"
#include "feature/FeatureHitTestControl.h"
#include "renderer/GLRenderContext.h"
#include "renderer/core/GLAsynchronousMapRenderable3.h"
#include "renderer/feature/DefaultSpatialFilterControl.h"
#include "renderer/feature/GLBatchGeometryRenderer4.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {
                class ENGINE_API GLBatchGeometryFeatureDataStoreRenderer3 :
                    public TAK::Engine::Renderer::Core::GLAsynchronousMapRenderable3,
                    public TAK::Engine::Feature::FeatureDataStore2::OnDataStoreContentChangedListener,
                    TAK::Engine::Util::NonCopyable
                {
                public:
                    GLBatchGeometryFeatureDataStoreRenderer3(TAK::Engine::Core::RenderContext &surface, TAK::Engine::Feature::FeatureDataStore2 &subject) NOTHROWS;
                public:
                    Util::TAKErr getControl(void **ctrl, const char *type) const NOTHROWS;
                public: // GLAsynchronousMapRenderable
                    void draw(const TAK::Engine::Renderer::Core::GLGlobeBase &view, const int renderPass) NOTHROWS override;
                    int getRenderPass() NOTHROWS override;
                    void start() NOTHROWS override;
                    void stop() NOTHROWS override;
                    void release() NOTHROWS override;
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
                private:
                    std::list<TAK::Engine::Renderer::Core::GLMapRenderable2 *> renderList;
                    TAK::Engine::Core::RenderContext &surface;
                    GLBatchGeometryRenderer4 renderer;
                    TAK::Engine::Feature::FeatureDataStore2 &dataStore;
                    std::shared_ptr<TAK::Engine::Feature::FeatureHitTestControl> hittest;
                    std::size_t validContext;
                    const Renderer::Core::GLGlobeBase *currentView = nullptr;
                    std::map<uint32_t, int64_t> hitids;

                    DefaultSpatialFilterControl spatialFilterControl;
                };
            }
        }
    }
}

#endif
