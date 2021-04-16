#ifndef TAK_ENGINE_RENDERER_GLDEPTHSAMPLER_H_INCLUDED
#define TAK_ENGINE_RENDERER_GLDEPTHSAMPLER_H_INCLUDED

//
// NOTE-- intended for internal implementation. Contains STL in method signatures
// and also the interface might change as needed by the engine
//

#include <vector>
#include "util/Error.h"
#include "renderer/Shader.h"
#include "core/RenderContext.h"
#include "renderer/GLOffscreenFramebuffer.h"
#include "math/Matrix2.h"

namespace TAK {
    namespace Engine {
        namespace Core {
            class MapSceneModel2;
            class GeoPoint2;
        }

        namespace Renderer {
            class GLDepthSamplerDrawable;

            /**
             * Provides a depth-sampling procedure for GLDepthSamplerDrawable renderables
             */
            class GLDepthSampler {
            public:
                /**
                 * The method of depth sampling
                 */
                enum Method {
                    /**
                     * Depth value is encoded into the RGBA and read via glReadPixels. This most likely
                     * results in higher precision depth values. DrawId is NOT supported, so requires
                     * a recursive procedure with multiple clears.
                     */
                    ENCODED_DEPTH_ENCODED_ID,

                    /**
                     * DrawId value is encoded into RGBA and depth is sampled at the end from a depth texture 
                     * (GLES 3 support required and a fullscreen render is required to "blit" depth texture to 
                     * RGBA for glReadPixels).
                     */
                    ENCODED_ID_SAMPLED_DEPTH,
                };

                enum {
                    HIGHEST_DRAW_ID_BIT = 24, // for shifting
                    MAX_DRAW_ID = 0x00ffffff // 24-bit (also works as mask)
                };

                /**
                 * Perform a depth sample draw procedure.
                 *
                 * @param resultZ optional output clip space Z value
                 * @param resultDrawable optional output nearest drawable
                 * @param drawable the drawable to sample on
                 * @param sceneModel the desired scene model
                 * @param x the x location in GL pixel space
                 * @param y the y location in GL pixel space (bottom = 0)
                 * 
                 * @return TE_Ok on successful sampling of nearest drawable
                 */
                TAK::Engine::Util::TAKErr performDepthSample(double* resultZ, TAK::Engine::Math::Matrix2* proj, class GLDepthSamplerDrawable** resultDrawable,
                    GLDepthSamplerDrawable &drawable, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS;

                /**
                 * Get the sampling method
                 */
                Method getMethod() const NOTHROWS;

                /**
                 * Get the vertex coordinate attribute
                 */
                GLuint attributeVertexCoords() const NOTHROWS;
                
                /**
                 * Get the uniform for model-view-projection matrix
                 */
                GLuint uniformMVP() const NOTHROWS;

                /**
                 * Get the screen X
                 */
                float getX() const NOTHROWS;

                /**
                 * Get the screen Y
                 */
                float getY() const NOTHROWS;

            private:
                TAK::Engine::Util::TAKErr setDrawId(uint32_t drawId) NOTHROWS;

                void beginProj(const TAK::Engine::Core::MapSceneModel2& sceneModel) NOTHROWS;
                void endProj() NOTHROWS;

                TAK::Engine::Util::TAKErr begin(float x, float y, const TAK::Engine::Core::MapSceneModel2& sceneModel) NOTHROWS;

                TAK::Engine::Util::TAKErr end() NOTHROWS;

                TAK::Engine::Util::TAKErr performSinglePassDepthSample(
                    double* resultZ,
                    class GLDepthSamplerDrawable** resultDrawable,
                    GLDepthSamplerDrawable& drawable,
                    const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS;

                TAK::Engine::Util::TAKErr performDoublePassDepthSample(
                    double* resultZ,
                    class GLDepthSamplerDrawable** resultDrawable,
                    GLDepthSamplerDrawable& drawable,
                    const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS;

            private:
                friend TAK::Engine::Util::TAKErr GLDepthSampler_create(std::unique_ptr<GLDepthSampler, void (*)(GLDepthSampler*)>& result, const TAK::Engine::Core::RenderContext& ctx,
                    Method method) NOTHROWS;

            private:
                void drawDepthToColor() NOTHROWS;
                uint32_t readPixelAsId() const NOTHROWS;
                float readPixelAsDepth() const NOTHROWS;

                void enableEncodingState(const std::shared_ptr<const Shader2>& shader) NOTHROWS;


            private:
                GLDepthSampler();

            public:
                ~GLDepthSampler() NOTHROWS;

            private:
                GLOffscreenFramebufferPtr fbo_;
                GLOffscreenFramebufferPtr fullscreen_quad_fbo_;
                std::shared_ptr<const Shader2> encoding_program_;

                std::shared_ptr<const Shader2> cached_id_program_;
                std::shared_ptr<const Shader2> cached_depth_program_;
                std::shared_ptr<const Shader2> cached_fs_program_;

                Method method_;
                GLuint bound_fbo_;
                int viewport_[4];
                float x_;
                float y_;
                bool blend_enabled_;
            };

            typedef std::unique_ptr<GLDepthSampler, void (*)(GLDepthSampler *)> GLDepthSamplerPtr;

            /**
             * Create a depth sampler with a given method
             */
            TAK::Engine::Util::TAKErr GLDepthSampler_create(GLDepthSamplerPtr &result, const TAK::Engine::Core::RenderContext &ctx,
                GLDepthSampler::Method method) NOTHROWS;

            /**
             * Interface for any renderable that can contribute to the depth sampler process.
             */
            class GLDepthSamplerDrawable {
            public:
                virtual Util::TAKErr gatherDepthSamplerDrawables(std::vector<GLDepthSamplerDrawable*>& result, int levelDepth, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS;

                virtual void depthSamplerDraw(GLDepthSampler& sampler, const TAK::Engine::Core::MapSceneModel2& sceneModel) NOTHROWS;
            };
        }
    }
}

#endif