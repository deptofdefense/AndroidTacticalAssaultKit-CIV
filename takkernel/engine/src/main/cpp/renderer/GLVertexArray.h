#ifndef TAK_ENGINE_RENDERER_GLVERTEXARRAY_H_INCLUDED
#define TAK_ENGINE_RENDERER_GLVERTEXARRAY_H_INCLUDED

#include "port/Platform.h"
#include "renderer/GL.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            struct ENGINE_API GLVertexArray
            {
                GLVertexArray() NOTHROWS;
                GLVertexArray(const GLint size, const GLenum type, const GLboolean normalized, const GLsizei stride, const GLsizei offset) NOTHROWS;

                GLint size{ 0 };
                GLenum type{ GL_NONE };
                GLboolean normalized{ GL_FALSE };
                GLsizei stride{ 0u };
                GLsizei offset{ 0u };
            };

            void glVertexAttribPointer(GLuint index, const GLVertexArray &layout) NOTHROWS;
        }
    }
}

#endif
