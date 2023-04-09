#ifndef TAK_ENGINE_RENDERER_SHADER_H_INCLUDED
#define TAK_ENGINE_RENDERER_SHADER_H_INCLUDED

#include <string>

#include "renderer/GL.h"

#include "core/RenderContext.h"
#include "port/Platform.h"
#include "renderer/RenderAttributes.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            struct ENGINE_API Shader2
            {
                GLuint handle {GL_NONE};
                GLint uMVP {-1};
                GLint uTexture {-1};
                GLint uSunPosition {-1};
                GLint uColor {-1};
                GLint uInvModelView {-1};
                GLint aTexCoords {-1};
                GLint aVertexCoords {-1};
                GLint aNormals {-1};
                GLint uNormalMatrix {-1};
                friend TAK::Engine::Util::TAKErr Shader_get(std::shared_ptr<const Shader2>&, const TAK::Engine::Core::RenderContext&, const RenderAttributes& attrs) NOTHROWS;
            };

            class ENGINE_API Shader
            {
            private :
                Shader(const unsigned flags) NOTHROWS;
            public :
                GLuint handle{GL_NONE};

                GLint uProjection {-1};
                GLint uModelView {-1};
                GLint uTextureMx {-1};
                GLint uTexture {-1};
                GLint uAlphaDiscard {-1};
                GLint uColor {-1};
                GLint aVertexCoords {-1};
                GLint aTextureCoords {-1};
                GLint aColorPointer {-1};
                GLint aNormals {-1};
                GLint uPointSize {-1};

                bool textured;
                bool alphaDiscard;
                bool colorPointer;
                bool lighting;
                bool points;
                std::size_t numAttribs;
            private :
                std::string vsh_;
                std::string fsh_;

                friend void Shader_get(std::shared_ptr<const Shader> &, const TAK::Engine::Core::RenderContext &, const RenderAttributes &attrs) NOTHROWS;
            };

            constexpr float TE_GL_DEFAULT_POINT_SIZE = 8.0f;

            /**
                * <P>MUST be invoked on render thread
                *
                * @param ctx
                * @param flags
                * @return
                */
            void Shader_get(std::shared_ptr<const Shader> &value, const TAK::Engine::Core::RenderContext &ctx, const RenderAttributes &attrs) NOTHROWS;


            TAK::Engine::Util::TAKErr Shader_get(std::shared_ptr<const Shader2>&, const TAK::Engine::Core::RenderContext& ctx, const RenderAttributes& attrs) NOTHROWS;
            TAK::Engine::Util::TAKErr Shader_compile(std::shared_ptr<const Shader2> &result, const TAK::Engine::Core::RenderContext& ctx, const char* vert, const char* frag) NOTHROWS;
        }
    }
}
#endif
