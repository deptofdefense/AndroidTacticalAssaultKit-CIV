#ifndef TAK_ENGINE_RENDERER_MODEL_GLSCENELAYER_H_INCLUDED
#define TAK_ENGINE_RENDERER_MODEL_GLSCENELAYER_H_INCLUDED

#include <list>
#include <map>
#include <set>

#include "model/SceneLayer.h"
#include "renderer/core/GLAsynchronousMapRenderable3.h"
#include "renderer/core/GLLayerSpi2.h"
#include "renderer/model/MaterialManager.h"
#include "renderer/model/HitTestControl.h"
#include "renderer/model/SceneLayerControl.h"
#include "feature/FeatureHitTestControl.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Model {
                class GLSceneLayer : public Core::GLAsynchronousMapRenderable3,
                                     public TAK::Engine::Model::SceneLayer::ContentChangedListener,
                                     public HitTestControl,
                                     public TAK::Engine::Feature::FeatureHitTestControl,
                                     public SceneLayerControl
                {
                private :
                    class SceneRenderer;
                public :
                    GLSceneLayer(TAK::Engine::Core::MapRenderer &ctx, TAK::Engine::Model::SceneLayer &subject) NOTHROWS;
                    ~GLSceneLayer() NOTHROWS;
                public: // HitTestControl
                    virtual Util::TAKErr hitTest(TAK::Engine::Core::GeoPoint2 *value, const TAK::Engine::Core::MapSceneModel2 &sceneModel, const float x, const float y) NOTHROWS override;
                public: // FeatureHitTestControl
                    virtual TAK::Engine::Util::TAKErr hitTest(Port::Collection<int64_t> &fids, float screenX, float screenY, const TAK::Engine::Core::GeoPoint2 &point, double resolution, float radius, int limit) NOTHROWS;
                public :
                    virtual Util::TAKErr getSceneObjectControl(SceneObjectControl **ctrl, const int64_t sid) NOTHROWS;
                private :
                    Util::TAKErr queryImpl(std::set<std::shared_ptr<SceneRenderer>> &result, const Core::GLAsynchronousMapRenderable3::ViewState &state)  NOTHROWS;
                public : // SceneLayer::ContentChangedListener
                    Util::TAKErr contentChanged(const TAK::Engine::Model::SceneLayer &layer) NOTHROWS;
                public : // GLLayer2
                    TAK::Engine::Core::Layer2 &getSubject() NOTHROWS;
                // GLAsynchronousMapRenderable2
                protected :
                    Util::TAKErr createQueryContext(Core::GLAsynchronousMapRenderable3::QueryContextPtr &value) NOTHROWS;
                    Util::TAKErr resetQueryContext(Core::GLAsynchronousMapRenderable3::QueryContext &pendingData) NOTHROWS;
                private:
                    Util::TAKErr getRenderables(Port::Collection<Core::GLMapRenderable2 *>::IteratorPtr &iter) NOTHROWS;
                protected :
                    Util::TAKErr updateRenderableLists(Core::GLAsynchronousMapRenderable3::QueryContext &pendingData) NOTHROWS;
                    Util::TAKErr query(Core::GLAsynchronousMapRenderable3::QueryContext &result, const Core::GLAsynchronousMapRenderable3::ViewState &state)  NOTHROWS;
                public : // GLMapRenderable
                    int getRenderPass() NOTHROWS;
                    void start() NOTHROWS;
                    void stop() NOTHROWS;
                private :
                    TAK::Engine::Core::MapRenderer &renderer;
                    TAK::Engine::Model::SceneLayer &subject;
                    std::list<Core::GLMapRenderable2 *> drawList;
                    /** protected access on sceneMutex */
                    std::map<int64_t, std::shared_ptr<SceneRenderer>> active;
                    /** protected access on sceneMutex */
                    std::map<int64_t, std::shared_ptr<SceneRenderer>> cache;
                    std::map<int64_t, MaterialManagerPtr> materialManagers;

                    // hit testing
                    Thread::RWMutex sceneMutex;
                    
                    struct HitTestItem {
                        int64_t fid;
                        HitTestControl *control;
                        TAK::Engine::Feature::Envelope2 envelope;
                    };

                    friend class SceneRenderer;
                };

                ENGINE_API Core::GLLayerSpi2 &GLSceneLayer_spi() NOTHROWS;
            }
        }
    }
}
#endif
