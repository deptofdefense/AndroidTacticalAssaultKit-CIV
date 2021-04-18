#ifndef TAK_ENGINE_RENDERER_MODEL_GLSCENE_H_INCLUDED
#define TAK_ENGINE_RENDERER_MODEL_GLSCENE_H_INCLUDED

#include "core/RenderContext.h"
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
#include "renderer/GLDepthSampler.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Model {
                class GLSceneLayer;
                class GLMesh;

                class ENGINE_API GLScene : public Core::GLMapRenderable2,
                    public GLDepthSamplerDrawable,
                    public HitTestControl
                {
                private :
                    class SceneControlImpl;
                public :
                    GLScene(TAK::Engine::Core::RenderContext &ctx, const TAK::Engine::Model::SceneInfo &info, const GLSceneSpi::Options &opts) NOTHROWS;
                    GLScene(TAK::Engine::Core::RenderContext& ctx, const TAK::Engine::Model::SceneInfo& info, TAK::Engine::Model::ScenePtr&& scene, const GLSceneSpi::Options& opts) NOTHROWS;
                    ~GLScene() NOTHROWS;
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
                public: // GLDepthSamplerDrawable
                    virtual Util::TAKErr gatherDepthSamplerDrawables(std::vector<GLDepthSamplerDrawable*>& result, int levelDepth, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS;
                    virtual void depthSamplerDraw(GLDepthSampler& sampler, const TAK::Engine::Core::MapSceneModel2& sceneModel) NOTHROWS;
                private :
                    static void *initializeThread(void *opaque);
                private:
                    static Util::TAKErr depthTestTask(TAK::Engine::Core::GeoPoint2& value, GLScene* scene, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS;
                private :
                    TAK::Engine::Core::RenderContext &ctx_;
                    std::map<std::string, void *> controls_;
                    TAK::Engine::Model::SceneInfo info_;
                    TAK::Engine::Model::ScenePtr scene_;
                    bool location_dirty_;
                    unsigned int xray_color_;

                    std::list<std::shared_ptr<TAK::Engine::Model::SceneNode>> display_nodes_;
                    // XXX - check if cna make unique_ptr
                    std::map<TAK::Engine::Model::SceneNode *, std::shared_ptr<GLSceneNode>> node_renderers_;

                    std::unique_ptr<GLSceneNodeLoader> loader_;

                    GLSceneSpi::Options opts_;
                    Thread::Mutex mutex_;
                    Thread::ThreadPtr initializer;
                    bool cancelInitialize;
                    bool clampRequested;

                    Core::GLContentIndicator indicator;

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

