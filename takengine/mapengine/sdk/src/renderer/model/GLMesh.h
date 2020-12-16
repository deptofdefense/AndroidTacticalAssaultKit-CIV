#ifndef TAK_ENGINE_RENDERER_MODEL_GLMESH_H_INCLUDED
#define TAK_ENGINE_RENDERER_MODEL_GLMESH_H_INCLUDED

#include "core/GeoPoint2.h"
#include "core/RenderContext.h"
#include "core/MapSceneModel2.h"
#include "feature/AltitudeMode.h"
#include "math/Matrix2.h"
#include "math/Point2.h"
#include "model/Mesh.h"
#include "model/SceneInfo.h"
#include "port/Platform.h"
#include "renderer/RenderState.h"
#include "renderer/Shader.h"
#include "renderer/core/ColorControl.h"
#include "renderer/core/GLMapRenderable2.h"
#include "renderer/model/GLMaterial.h"
#include "renderer/model/MaterialManager.h"
#include "util/Error.h"
#include "util/MemBuffer2.h"
#include "renderer/GLDepthSampler.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Model {
                class GLSceneNode;
                class GLProgressiveScene;

                class ENGINE_API GLMesh : public Core::GLMapRenderable2, public GLDepthSamplerDrawable
                {
                public:
                    GLMesh(TAK::Engine::Core::RenderContext& ctx, const Math::Matrix2* localFrame, const TAK::Engine::Feature::AltitudeMode altitudeMode, const std::shared_ptr<const TAK::Engine::Model::Mesh>& subject, const Math::Point2<double>& anchor, const std::shared_ptr<MaterialManager>& matmgr) NOTHROWS;
                    GLMesh(TAK::Engine::Core::RenderContext& ctx, Math::Matrix2Ptr_const&& localFrame, const TAK::Engine::Feature::AltitudeMode altitudeMode, const std::shared_ptr<const TAK::Engine::Model::Mesh>& subject, const Math::Point2<double>& anchor, const std::shared_ptr<MaterialManager>& matmgr) NOTHROWS;
                private:
                    Util::TAKErr initMaterials() NOTHROWS;
                public:
                    const TAK::Engine::Model::Mesh& getSubject() const NOTHROWS;
                private:
                    Util::TAKErr resolveMaterials(bool* value) NOTHROWS;
                private:
                    void setMatrices(const Renderer::Shader& shader, const bool mv, const bool p, const bool t) const NOTHROWS;
                    void setMatrices(const Renderer::Shader2& shader, const bool mvp, const bool imv) const NOTHROWS;
                    void updateBindVbo() NOTHROWS;
                public:
                    void draw(const Core::GLMapView2& view, const int renderPass) NOTHROWS;
                public: // GLDepthSamplerDrawable
                    virtual Util::TAKErr gatherDepthSamplerDrawables(std::vector<GLDepthSamplerDrawable*>& result, int levelDepth, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS;
                    virtual void depthSamplerDraw(GLDepthSampler& sampler, const TAK::Engine::Core::MapSceneModel2& sceneModel) NOTHROWS;
                private:
                    struct ViewState_ {
                        const TAK::Engine::Core::MapSceneModel2& scene;
                        double elevationScaleFactor;
                        int drawSrid;
                        int terrainVersion;
                        Util::TAKErr (*getTerrainMeshElevation)(double* value, const double latitude, const double longitude, const void *opaque) NOTHROWS;
                        const void *opaque;
                    };
                    void draw(const Core::GLMapView2 &view, Renderer::RenderState &state, const int renderPass) NOTHROWS;
                    void draw(const ViewState_ &args, Renderer::RenderState& state, const int renderPass) NOTHROWS;
                    void draw(/*const Core::GLMapView2& view,*/ const Renderer::Shader& shader, GLMaterial& material, const bool reset)  const NOTHROWS;
                    void draw(const Shader2 &shader, const ViewState_ &viewState, Renderer::RenderState &state, bool wireframe) NOTHROWS;
                    void prepareTransform(const ViewState_& viewState/*, Renderer::RenderState& state*/) NOTHROWS;
                public:
                    void release() NOTHROWS;
                public:
                    int getRenderPass() NOTHROWS;
                    void start() NOTHROWS;
                    void stop() NOTHROWS;
                public:
                    /**
                     * Performs a hit-test on the mesh using the specified scene model at the specified location.
                     *
                     * <P>This call is not thread-safe and must be externally synchronized by the caller
                     *
                     * @param sceneModel    The parameters of the scene rendering
                     * @param x             The screen X location
                     * @param y             The screen Y location
                     * @param value         The hit location, if succesful
                     * @return  <code>TE_Ok</code> if a hit occurred, <code>TE_Done</code> if no hit occurred, various codes on failure
                     */
                    Util::TAKErr hitTest(TAK::Engine::Core::GeoPoint2* value, const TAK::Engine::Core::MapSceneModel2& sceneModel, const float x, const float y) NOTHROWS;
                public:
                    /**
                     * Call when the model's local frame has been modified
                     * Currently this triggers recalculation of the LLA -> ECEF matrix
                     * Should only be called on the GL thread
                     */
                    void refreshLocalFrame() NOTHROWS;
                public:
                    Util::TAKErr getControl(void** ctrl, const char* type) const NOTHROWS;
                private: // SceneObjectControl
                    Util::TAKErr setLocation(const TAK::Engine::Core::GeoPoint2& location, const Math::Matrix2* localFrame, const int srid, const TAK::Engine::Feature::AltitudeMode altitudeMode) NOTHROWS;
                public: // ColorControl
                    Util::TAKErr setColor(const Core::ColorControl::Mode mode, const unsigned int argb) NOTHROWS;
                private:
                    static Util::TAKErr getShader(std::shared_ptr<const Renderer::Shader>& value, TAK::Engine::Core::RenderContext& ctx, const TAK::Engine::Model::VertexDataLayout& layout, GLMaterial& material) NOTHROWS;
                    static Util::TAKErr getMeshTerrainMeshElevation(double* value, const double latitude, const double longitude, const void* opaque) NOTHROWS;
                private:
                    TAK::Engine::Core::RenderContext& ctx_;
                    std::shared_ptr<const TAK::Engine::Model::Mesh> subject_;
                    std::vector<GLMaterial*> materials_;
                    std::vector<bool> material_initialized_;
                    Math::Matrix2Ptr_const local_frame_;
                    TAK::Engine::Feature::AltitudeMode altitude_mode_;
                    Math::Point2<double> model_anchor_point_;
                    double model_z_offset_;
                    int offset_terrain_version_;
                    Util::MemBuffer2Ptr wireframe_;
                    bool allow_texture_;
                    GLuint vbo_;
                    bool vbo_dirty_;
                    std::vector<std::shared_ptr<const Renderer::Shader>> shader_;
                    std::shared_ptr<const Renderer::Shader> wireframe_shader_;
                    std::shared_ptr<const Renderer::Shader2> wireframe_shader2_;
                    Math::Matrix2Ptr_const lla2ecef_;
                    //MaterialManager &matmgr;
                    std::shared_ptr<MaterialManager> matmgr_;
                    Core::ColorControl::Mode color_mode_;
                    float r_;
                    float g_;
                    float b_;
                    float a_;
                    bool use_vbo_;
                    int srid_;

                    mutable struct {
                        Math::Matrix2 projection;
                        Math::Matrix2 modelView;
                        Math::Matrix2 texture;
                    } transform_;

                    friend class TAK::Engine::Renderer::Model::GLSceneNode;
                    friend class TAK::Engine::Renderer::Model::GLProgressiveScene;
                };
            }
        }
    }
}
#endif
