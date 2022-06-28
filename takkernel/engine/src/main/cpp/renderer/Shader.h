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
                GLuint handle;
                GLint uMVP;
                GLint uTexture;
                GLint uSunPosition;
                GLint uColor;
                GLint uInvModelView;
                GLint aTexCoords;
                GLint aVertexCoords;
                GLint aNormals;
                GLint uNormalMatrix;
                friend TAK::Engine::Util::TAKErr Shader_get(std::shared_ptr<const Shader2>&, const TAK::Engine::Core::RenderContext&, const RenderAttributes& attrs) NOTHROWS;
            };

            class ENGINE_API Shader
            {
            private :
                Shader(const unsigned flags) NOTHROWS;
            public :
                GLuint handle;

                GLint uProjection;
                GLint uModelView;
                GLint uTextureMx;
                GLint uTexture;
                GLint uAlphaDiscard;
                GLint uColor;
                GLint aVertexCoords;
                GLint aTextureCoords;
                GLint aColorPointer;
                GLint aNormals;
                GLint uPointSize;

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
