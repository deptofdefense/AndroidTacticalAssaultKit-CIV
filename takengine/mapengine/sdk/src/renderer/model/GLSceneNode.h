#ifndef TAK_ENGINE_RENDERER_MODEL_GLSCENENODE_H_INCLUDED
#define TAK_ENGINE_RENDERER_MODEL_GLSCENENODE_H_INCLUDED

#include <atomic>
#include "core/RenderContext.h"
#include "feature/AltitudeMode.h"
#include "feature/Envelope2.h"
#include "model/SceneInfo.h"
#include "model/SceneNode.h"
#include "port/Platform.h"
#include "renderer/RenderState.h"
#include "renderer/core/ColorControl.h"
#include "renderer/core/GLMapRenderable2.h"
#include "renderer/core/GLMapView2.h"
#include "renderer/model/MaterialManager.h"
#include "thread/Mutex.h"
#include "util/Error.h"
#include "renderer/GLDepthSampler.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Model {
                class GLScene;
                class GLSceneNodeLoader;
                class GLProgressiveScene;
                class GLMesh;

                class GLSceneNode : public Core::GLMapRenderable2,
                    public GLDepthSamplerDrawable
                {
                private :
                    enum RenderVisibility
                    {
                        None,
                        Prefetch,
                        Draw,
                    };
                private :
                    struct LoadContext
                    {
                        LoadContext() NOTHROWS;
                        LoadContext(const LoadContext &other) NOTHROWS;

                        /** content centroid */
                        TAK::Engine::Core::GeoPoint2 centroid;
                        /** content bounding sphere radius, in meters */
                        double boundingSphereRadius;
                        /** content nominal resolution, in meters */
                        double gsd;

                        std::size_t id;

                        //XXX-- unique_ptr made LoadContext movable only which interferes with 
                        //      GLSceneNodeLoader's interface of passing const references to loader impl.
                        //      Is shared_ptr<void> acceptable?
                        std::shared_ptr<void> opaque;
                    };

                    class LODMeshes;
                private :
                    GLSceneNode(TAK::Engine::Core::RenderContext &ctx, TAK::Engine::Model::SceneNodePtr &&subject, const TAK::Engine::Model::SceneInfo &info) NOTHROWS;
                    GLSceneNode(TAK::Engine::Core::RenderContext &ctx, const std::shared_ptr<TAK::Engine::Model::SceneNode> &subject, const TAK::Engine::Model::SceneInfo &info) NOTHROWS;
                    GLSceneNode(TAK::Engine::Core::RenderContext &ctx, TAK::Engine::Model::SceneNodePtr &&subject, const TAK::Engine::Model::SceneInfo &info, MaterialManagerPtr &&matmgr) NOTHROWS;
                    GLSceneNode(TAK::Engine::Core::RenderContext &ctx, const std::shared_ptr<TAK::Engine::Model::SceneNode> &subject, const TAK::Engine::Model::SceneInfo &info, MaterialManagerPtr &&matmgr) NOTHROWS;
                    GLSceneNode(TAK::Engine::Core::RenderContext &ctx, TAK::Engine::Model::SceneNodePtr &&subject, const TAK::Engine::Model::SceneInfo &info, const std::shared_ptr<MaterialManager> &matmgr) NOTHROWS;
                    GLSceneNode(TAK::Engine::Core::RenderContext &ctx, const std::shared_ptr<TAK::Engine::Model::SceneNode> &subject, const TAK::Engine::Model::SceneInfo &info, const std::shared_ptr<MaterialManager> &matmgr) NOTHROWS;
                private :
                    Util::TAKErr asyncLoad(LoadContext &ctx, bool *cancelToken) NOTHROWS;
                    bool isLoaded(const Core::GLMapView2  &view) const NOTHROWS;
                    Util::TAKErr prepareLoadContext(LoadContext *ctx, const Core::GLMapView2 &view) const NOTHROWS;
                    Util::TAKErr prepareLoadContext(LoadContext* ctx, const TAK::Engine::Core::MapSceneModel2& scene, double drawMapResolution) const NOTHROWS;
                    RenderVisibility isRenderable(const Core::GLMapView2 &view) const NOTHROWS;
                    Util::TAKErr unloadLODs() NOTHROWS;
                    bool hasLODs() const NOTHROWS;
                    Util::TAKErr hitTest(TAK::Engine::Core::GeoPoint2 *value, const TAK::Engine::Core::MapSceneModel2 &sceneModel, const float x, const float y) NOTHROWS;

                    Util::TAKErr gatherDepthSampleMeshes(std::vector<GLMesh*>& result, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS;


                    Util::TAKErr refreshAABB(const TAK::Engine::Feature::Envelope2 &aabb_mesh_local) NOTHROWS;
                private : // SceneObjectControl
                    Util::TAKErr setLocation(const TAK::Engine::Core::GeoPoint2 &location, const Math::Matrix2 *localFrame, const int srid, const TAK::Engine::Feature::AltitudeMode altitudeMode) NOTHROWS;
                private : // ColorControl
                    Util::TAKErr setColor(const Core::ColorControl::Mode mode, const unsigned int argb) NOTHROWS;
                public: // GLMapRenderable2
                    void draw(const Core::GLMapView2 &view, const int renderPass) NOTHROWS;
                    void release() NOTHROWS;
                    int getRenderPass() NOTHROWS;
                    void start() NOTHROWS;
                    void stop() NOTHROWS;
                public: // GLDepthSamplerDrawable
                    virtual Util::TAKErr gatherDepthSamplerDrawables(std::vector<GLDepthSamplerDrawable*>& result, int levelDepth, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS;
                    virtual void depthSamplerDraw(GLDepthSampler& sampler, const TAK::Engine::Core::MapSceneModel2& sceneModel) NOTHROWS;
                private :
                    void draw(const Core::GLMapView2 &view, Renderer::RenderState &state, const int renderPass) NOTHROWS;
                private :
                    std::vector<std::unique_ptr<LODMeshes>> lodMeshes;

                    TAK::Engine::Model::SceneInfo info;
                    TAK::Engine::Core::RenderContext &ctx;

                    std::shared_ptr<TAK::Engine::Model::SceneNode> subject;

                    std::shared_ptr<Model::MaterialManager> matmgr;

                    int lastRenderIdx = -1;
                    //int fadeCountdown;
                    //int fadeIdx;

                    std::vector<void *> controls;

                    Thread::Mutex mutex;

                    TAK::Engine::Feature::Envelope2 mbb;

                    unsigned int color;
                    Core::ColorControl::Mode colorMode;

                    friend class TAK::Engine::Renderer::Model::GLScene;
                    friend class TAK::Engine::Renderer::Model::GLProgressiveScene;
                    friend class TAK::Engine::Renderer::Model::GLSceneNodeLoader;
                };
            }
        }
    }
}
#endif

