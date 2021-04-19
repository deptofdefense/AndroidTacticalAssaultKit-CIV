
#ifndef TAK_ENGINE_RENDERER_MODEL_GLC3DTTILESETRENDERER_H
#define TAK_ENGINE_RENDERER_MODEL_GLC3DTTILESETRENDERER_H

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
#include "renderer/GLDepthSampler.h"
#include "renderer/core/GLContentIndicator.h"

// for some reason CLI can't include this??? compiler error makes no sense...
// moved to forward decl method
//#include "renderer/core/GLContent.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {
                class GLContentContext;
                class GLContentHolder;
            }
            namespace Model {
                /**
                 *
                 */
                class GLC3DTRenderer : public Core::GLMapRenderable2,
                    public HitTestControl,
                    public GLDepthSamplerDrawable {

                public:
                    class ENGINE_API Spi : public GLSceneSpi {
                    public:
                        virtual ~Spi() NOTHROWS;
                        virtual Util::TAKErr create(Core::GLMapRenderable2Ptr& value, TAK::Engine::Core::RenderContext& ctx, const TAK::Engine::Model::SceneInfo& info, const Options& opts) NOTHROWS;
                    };

                public:
                    GLC3DTRenderer(TAK::Engine::Core::RenderContext& ctx, const TAK::Engine::Model::SceneInfo& info) NOTHROWS;
                    GLC3DTRenderer(TAK::Engine::Core::RenderContext& ctx, const TAK::Engine::Model::SceneInfo& info, const char* cacheDir) NOTHROWS;
                    virtual ~GLC3DTRenderer() NOTHROWS;
                public: // GLMapRenderable2
                    virtual void draw(const TAK::Engine::Renderer::Core::GLMapView2& view, const int renderPass) NOTHROWS;
                    virtual void release() NOTHROWS;
                    virtual int getRenderPass() NOTHROWS;
                    virtual void start() NOTHROWS;
                    virtual void stop() NOTHROWS;
                public:
                    Util::TAKErr getControl(void** ctrl, const char* type) NOTHROWS;
                    void updateBounds(TAK::Engine::Feature::Envelope2& bounds) NOTHROWS;
                public: // HitTestControl
                    virtual TAK::Engine::Util::TAKErr hitTest(TAK::Engine::Core::GeoPoint2* result, const TAK::Engine::Core::MapSceneModel2& sceneModel, const float screenX, const float screenY) NOTHROWS;
                public: // GLDepthSamplerDrawable
                    virtual Util::TAKErr gatherDepthSamplerDrawables(std::vector<GLDepthSamplerDrawable*>& result, int levelDepth, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS;
                    virtual void depthSamplerDraw(GLDepthSampler& sampler, const TAK::Engine::Core::MapSceneModel2& sceneModel) NOTHROWS;
                private:
                    static Util::TAKErr depthTestTask(TAK::Engine::Core::GeoPoint2& value, GLC3DTRenderer* r, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS;
                private:
                    class SceneControlImpl;
                    TAK::Engine::Port::String uri_;
                    TAK::Engine::Model::SceneInfo info_;
                    std::unique_ptr<TAK::Engine::Renderer::Core::GLContentContext> content_context_;
                    std::unique_ptr<TAK::Engine::Renderer::Core::GLContentHolder> root_content_;
                    std::unique_ptr<SceneControlImpl> scene_control_;

                    class LoaderImpl;

                    std::shared_ptr<LoaderImpl> loader_impl_;
                };
            }
        }
    }
}

#endif