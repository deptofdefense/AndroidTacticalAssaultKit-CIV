#ifndef TAK_ENGINE_RENDERER_MODEL_GLPROGRESSIVESCENE_H_INCLUDED
#define TAK_ENGINE_RENDERER_MODEL_GLPROGRESSIVESCENE_H_INCLUDED

#include <set>
#include <deque>
#include "core/MapRenderer.h"
#include "model/Scene.h"
#include "model/SceneInfo.h"
#include "port/Platform.h"
#include "renderer/core/GLContentIndicator.h"
#include "renderer/core/GLMapRenderable2.h"
#include "renderer/model/GLSceneNode.h"
#include "renderer/model/GLSceneNodeLoader.h"
#include "renderer/model/GLSceneSpi.h"
#include "renderer/model/HitTestControl.h"
#include "thread/Mutex.h"
#include "thread/Thread.h"
#include "util/Error.h"
#include "util/Tasking.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Model {
                class GLSceneLayer;

                class ENGINE_API GLProgressiveScene : public Core::GLMapRenderable2,
                    public HitTestControl
                {
                private :
                    class SceneControlImpl;
                public :
                    class ENGINE_API Spi : public GLSceneSpi {
                    public:
                        virtual ~Spi() NOTHROWS;

                        virtual Util::TAKErr create(Core::GLMapRenderable2Ptr& value, TAK::Engine::Core::RenderContext& ctx, const TAK::Engine::Model::SceneInfo& info, const Options& opts) NOTHROWS;
                    };

                    GLProgressiveScene(TAK::Engine::Core::RenderContext &ctx, const TAK::Engine::Model::SceneInfo &info, const GLSceneSpi::Options &opts) NOTHROWS;
                    ~GLProgressiveScene() NOTHROWS;
                public: // HitTestControl
                    Util::TAKErr hitTest(TAK::Engine::Core::GeoPoint2 *value, const TAK::Engine::Core::MapSceneModel2 &sceneModel, const float x, const float y) NOTHROWS;
                public :
                    void start() NOTHROWS;
                    void stop() NOTHROWS;
                    int getRenderPass() NOTHROWS;
                    void draw(const Core::GLMapView2 &view, const int renderPass) NOTHROWS;
                    void release() NOTHROWS;
                public :
                    Util::TAKErr getControl(void **ctrl, const char *type) NOTHROWS;
                private :
                    std::map<std::string, void *> controls;

                    bool locationDirty;
                    unsigned int xray_color;

                    struct ViewState {
                        TAK::Engine::Core::MapSceneModel2 sceneModel;
                        double displayResolution {0.0};
                    };

                    struct DrawState2 {
                        DrawState2(size_t pump)
                            : pump(pump) 
                        {}
                        size_t pump;
                        std::map<TAK::Engine::Model::SceneNode*, std::pair<size_t, std::shared_ptr<GLMapRenderable2>>> nodeMap;
                        std::vector<std::shared_ptr<GLMapRenderable2>> drawList;
                    };


                    struct SceneState {
                        SceneState(const TAK::Engine::Model::SceneInfo& info, TAK::Engine::Core::RenderContext& ctx, const GLSceneSpi::Options &opts)
                        : info(info),
                        opts(opts),
                        ctx(ctx),
                        drawState(std::make_shared<DrawState2>(0)),
                        indicator(opts.showIndicator ? std::make_shared<Core::GLContentIndicator>(ctx) : nullptr),
                        resolved(false) { }

                        ~SceneState() NOTHROWS;

                        TAK::Engine::Model::SceneInfo info;
                        std::shared_ptr<TAK::Engine::Model::Scene> scene;
                        std::shared_ptr<DrawState2> drawState;
                        GLSceneSpi::Options opts;
                        TAK::Engine::Core::RenderContext &ctx;
                        std::shared_ptr<Core::GLContentIndicator> indicator;
                        bool resolved;

                        Util::FutureTask<std::shared_ptr<TAK::Engine::Model::Scene>> pendingScene;
                        Util::FutureTask<std::shared_ptr<DrawState2>> pendingDrawState;
                    };

                    
                    std::shared_ptr<SceneState> sceneState;
                    ViewState drawViewState;

                    static Util::TAKErr loadScene(std::shared_ptr<TAK::Engine::Model::Scene> & scene, const TAK::Engine::Model::SceneInfo &info) NOTHROWS;
                    static Util::TAKErr setScene(const std::shared_ptr<TAK::Engine::Model::Scene> &scene, const std::shared_ptr<SceneState> &sceneState) NOTHROWS;
                    static Util::TAKErr updateIndicatorBounds(const TAK::Engine::Feature::Envelope2& bounds, const std::shared_ptr<SceneState>& sceneState) NOTHROWS;
                    static Util::TAKErr buildDrawState(std::shared_ptr<DrawState2> &result, 
                        const std::shared_ptr<SceneState> &sceneState, 
                        const ViewState &viewState,
                        TAK::Engine::Model::SceneNode* subject) NOTHROWS;

                    static Util::TAKErr buildDrawStateChild(std::shared_ptr<DrawState2>& result,
                        const std::shared_ptr<SceneState>& sceneState,
                        const ViewState& viewState,
                        const std::shared_ptr<TAK::Engine::Model::SceneNode> &subject,
                        const Math::Matrix2* transform) NOTHROWS;

                    static Util::TAKErr setDrawState(const std::shared_ptr<DrawState2>& scene, const std::shared_ptr<SceneState>& sceneState) NOTHROWS;
                    static Util::TAKErr setSceneLoadError(std::shared_ptr<TAK::Engine::Model::Scene>& scene, Util::TAKErr err, const std::shared_ptr<SceneState>& sceneState) NOTHROWS;

                    // controls
                    std::unique_ptr<SceneControlImpl> sceneCtrl;

                    friend class SceneControlImpl;
                    friend class TAK::Engine::Renderer::Model::GLSceneLayer;
                };
            }
        }
    }
}

#endif

