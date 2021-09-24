#include "renderer/GLSLUtil.h"

#include "renderer/GL.h"

#include "util/Logging2.h"
#include "util/Memory.h"

using namespace TAK::Engine::Renderer;

using namespace TAK::Engine::Util;

// Use the following for debugging
//#define CHECKERRS() checkGLErrors(__LINE__)
// Use this for production
#define CHECKERRS()

namespace
{
    void checkGLErrors(int line) NOTHROWS;
}

TAKErr TAK::Engine::Renderer::GLSLUtil_loadShader(int *value, const char *src, const int type) NOTHROWS
{
    TAKErr code(TE_Ok);

    int n = glCreateShader(type);
    CHECKERRS();
    if (n == GL_FALSE)
        return TE_Err;
    glShaderSource(n, 1, &src, nullptr);
    CHECKERRS();
    glCompileShader(n);
    CHECKERRS();
    int rc = 0;
    glGetShaderiv(n, GL_COMPILE_STATUS, &rc);
    CHECKERRS();
    if (rc == 0) {
        array_ptr<char> msg(nullptr);
        int msgLen;
        glGetShaderiv(n, GL_INFO_LOG_LENGTH, &msgLen);
        if(msgLen) {
            msg.reset(new char[msgLen+1]);
            glGetShaderInfoLog(n, msgLen+1, nullptr, msg.get());
        }
        Logger_log(LogLevel::TELL_Error, "Failed to compile shader %d, msg: %s", n, msg.get());
        Logger_log(LogLevel::TELL_Error, "Failed to compile shader %d, src:\n%s", n, src);
        msg.reset();
        glDeleteShader(n);
        return TE_Err;
    }
    *value = n;
    return code;
}

TAKErr TAK::Engine::Renderer::GLSLUtil_createProgram(ShaderProgram *program, const char *vertSrc, const char *fragSrc) NOTHROWS
{
    TAKErr code(TE_Ok);

    int vertShader;
    code = GLSLUtil_loadShader(&vertShader, vertSrc, GL_VERTEX_SHADER);
    TE_CHECKRETURN_CODE(code);

    int fragShader;
    code = GLSLUtil_loadShader(&fragShader, fragSrc, GL_FRAGMENT_SHADER);
    TE_CHECKRETURN_CODE(code);

    code = GLSLUtil_createProgram(program, vertShader, fragShader);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr TAK::Engine::Renderer::GLSLUtil_createProgram(ShaderProgram *program, const int vertShader, const int fragShader) NOTHROWS
{
    TAKErr code(TE_Ok);

    int n = glCreateProgram();
    CHECKERRS();
    if (n == GL_FALSE)
        return TE_Err;
    glAttachShader(n, vertShader);
    CHECKERRS();
    glAttachShader(n, fragShader);
    CHECKERRS();
    glLinkProgram(n);
    CHECKERRS();
    int ok = 0;
    glGetProgramiv(n, GL_LINK_STATUS, &ok);
    CHECKERRS();
    if (ok == 0) {
        array_ptr<char> msg(nullptr);
        int msgLen;
        glGetProgramiv(n, GL_INFO_LOG_LENGTH, &msgLen);
        if(msgLen) {
            msg.reset(new char[msgLen+1]);
            glGetProgramInfoLog(n, msgLen+1, nullptr, msg.get());
        }
        Logger_log(TELL_Error, "Failed to create program, vertShader=%d fragShader=%d\nmsg: %s", vertShader, fragShader, msg.get());
        msg.reset();
        glDeleteProgram(n);
        return TE_Err;
    }

    program->program = n;
    program->vertShader = vertShader;
    program->fragShader = fragShader;
    return code;
}

namespace
{
    void checkGLErrors(int line) NOTHROWS
    {
        int n = 0;
        static int lastok = 0;
        while (true) {
            GLenum e = glGetError();
            if (e != GL_NO_ERROR) {
                Logger_log(LogLevel::TELL_Error, "GL ERROR was pending = %d at %d last ok at %d", e, line, lastok);
                n++;
            } else {
                if (n == 0)
                    lastok = line;
                break;
            }
        }
    }
}
