#ifndef TAK_ENGINE_RENDERER_GLSLUTIL_H_INCLUDED
#define TAK_ENGINE_RENDERER_GLSLUTIL_H_INCLUDED

#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            struct ShaderProgram
            {
                int program;
                int vertShader;
                int fragShader;
            };

            Util::TAKErr GLSLUtil_loadShader(int *value, const char *src, const int type) NOTHROWS;
            Util::TAKErr GLSLUtil_createProgram(ShaderProgram *program, const char *vertSrc, const char *fragSrc) NOTHROWS;
            Util::TAKErr GLSLUtil_createProgram(ShaderProgram *program, const int vertShader, const int fragShader) NOTHROWS;
        }
    }
}

#endif // TAK_ENGINE_RENDERER_GLSLUTIL_H_INCLUDED
