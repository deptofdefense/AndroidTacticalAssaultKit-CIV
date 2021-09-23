#ifndef TAK_ENGINE_RENDERER_WIREFRAME_H_INCLUDED
#define TAK_ENGINE_RENDERER_WIREFRAME_H_INCLUDED

#include "renderer/GL.h"

#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            /**
             * Returns the required minimum number of output vertices to create a wireframe representation the given geometry with the given vertex count. For `GL_TRIANGLE_STRIP` mode, the actual count may be less due to degenerate triangles.
             */
            ENGINE_API Util::TAKErr GLWireframe_getNumWireframeElements(std::size_t *value, const GLenum mode, const GLuint count) NOTHROWS;

            ENGINE_API Util::TAKErr GLWireframe_deriveIndices(uint16_t *value, const GLenum mode, const GLuint count) NOTHROWS;
            ENGINE_API Util::TAKErr GLWireframe_deriveIndices(uint32_t *value, const GLenum mode, const GLuint count) NOTHROWS;

            ENGINE_API Util::TAKErr GLWireframe_deriveIndices(uint16_t *value, std::size_t *outputCount, const uint16_t *srcIndices, const GLenum mode, const GLuint count) NOTHROWS;
            ENGINE_API Util::TAKErr GLWireframe_deriveIndices(uint32_t *value, std::size_t *outputCount, const uint32_t *srcIndices, const GLenum mode, const GLuint count) NOTHROWS;

            ENGINE_API Util::TAKErr GLWireframe_deriveLines(void *value, std::size_t *outputCount, const GLenum mode, const GLuint stride, const void *data, const GLuint count) NOTHROWS;
        }
    }
}

#endif
