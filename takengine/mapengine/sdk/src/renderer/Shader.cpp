#include "renderer/Shader.h"

#include <map>
#include <sstream>

#include "renderer/GLSLUtil.h"
#include "thread/Lock.h"
#include "thread/Mutex.h"

using namespace TAK::Engine::Renderer;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace
{
    enum ShaderFlags {
        FLAG_TEXTURED = 0x01u,
        FLAG_ALPHA_DISCARD = 0x02u,
        FLAG_COLOR_POINTER = 0x04u,
        FLAG_NORMAL = 0x08u,
    };

    std::map<const RenderContext *, std::map<unsigned, std::shared_ptr<const Shader>>> &shaders() NOTHROWS
    {
        static std::map<const RenderContext *, std::map<unsigned, std::shared_ptr<const Shader>>> m;
        return m;
    }
    std::map<const RenderContext*, std::map<unsigned, std::shared_ptr<const Shader2>>>& shaders2() NOTHROWS
    {
        static std::map<const RenderContext*, std::map<unsigned, std::shared_ptr<const Shader2>>> m;
        return m;
    }
    Mutex &shadersMutex() NOTHROWS
    {
        static Mutex m;
        return m;
    }

    TAKErr compileShader2(Shader2* value, const char* vert, const char* frag) {

        TAKErr code(TE_Ok);
        ShaderProgram program{ GL_NONE, GL_NONE, GL_NONE };
        code = TAK::Engine::Renderer::GLSLUtil_createProgram(&program, vert, frag);
        TE_CHECKRETURN_CODE(code);

        value->handle = program.program;
        if (program.vertShader != GL_NONE)
            glDeleteShader(program.vertShader);
        if (program.fragShader != GL_NONE)
            glDeleteShader(program.fragShader);

        value->uMVP = glGetUniformLocation(value->handle, "uMVP");
        value->uTexture = glGetUniformLocation(value->handle, "uTexture");
        value->uSunPosition = glGetUniformLocation(value->handle, "uSunPosition");
        value->uColor = glGetUniformLocation(value->handle, "uColor");
        value->uInvModelView = glGetAttribLocation(value->handle, "uInvModelView");
        value->aTexCoords = glGetAttribLocation(value->handle, "aTexCoords");
        value->aVertexCoords = glGetAttribLocation(value->handle, "aVertexCoords");
        value->aNormals = glGetAttribLocation(value->handle, "aNormals");

        //numAttribs = 1 + (colorPointer ? 1 : 0) + (textured ? 1 : 0) + (lighting ? 1 : 0);

        return TE_Ok;
    }

    TAKErr makeDefaultShader2(Shader2* value, bool textured, bool lighting) {
        // vertex shader source
        std::ostringstream vshsrc;
        vshsrc << "uniform mat4 uMVP;\n";
        if (textured) {
            vshsrc << "attribute vec2 aTexCoords;\n";
            vshsrc << "varying vec2 vTexPos;\n";
        }
        vshsrc << "attribute vec3 aVertexCoords;\n";
        if (lighting) {
            vshsrc << "attribute vec3 aNormals;\n";
            vshsrc << "varying vec3 vNormal;\n";
        }
        vshsrc << "void main() {\n";
        if (textured) {
            vshsrc << "  vec4 texCoords = vec4(aTexCoords.xy, 0.0, 1.0);\n";
            vshsrc << "  vTexPos = texCoords.xy;\n";
        }
        if (lighting)
            vshsrc << "  vNormal = normalize(mat3(uMVP) * aNormals);\n";
        vshsrc << "  gl_Position = uMVP * vec4(aVertexCoords.xyz, 1.0);\n";
        vshsrc << "}";

        // fragment shader source
        std::ostringstream fshsrc;
        fshsrc << "precision mediump float;\n";
        if (textured) {
            fshsrc << "uniform sampler2D uTexture;\n";
            fshsrc << "varying vec2 vTexPos;\n";
        }
        fshsrc << "uniform vec4 uColor;\n";
        if (lighting)
            fshsrc << "varying vec3 vNormal;\n";
        fshsrc << "void main(void) {\n";
        if (textured)
            fshsrc << "  vec4 color = texture2D(uTexture, vTexPos);\n";
        else
            fshsrc << "  vec4 color = vec4(1.0, 1.0, 1.0, 1.0);\n";
        if (lighting) {
            // XXX - next two as uniforms
            fshsrc << "  vec3 sun_position = vec3(3.0, 10.0, -5.0);\n";
            fshsrc << "  vec3 sun_color = vec3(1.0, 1.0, 1.0);\n";
            fshsrc << "  float lum = max(dot(vNormal, normalize(sun_position)), 0.0);\n";
            fshsrc << "  color = color * vec4((0.6 + 0.4 * lum) * sun_color, 1.0);\n";
        }
        fshsrc << "  gl_FragColor = uColor * color;\n";
        fshsrc << "}";

        if (compileShader2(value, vshsrc.str().c_str(), fshsrc.str().c_str()) != TE_Ok)
            return TE_Err;

        return TE_Ok;
    }

    void deleteShader2(Shader2* value) {
        if (!value)
            return;
        if (value->handle) {
            glDeleteProgram(value->handle);
            // incase dtor added to Shader2 that does the same
            value->handle = 0;
        }
        delete value;
    }
}

Shader::Shader(const unsigned flags) NOTHROWS
{
    this->textured = (flags & ShaderFlags::FLAG_TEXTURED) != 0;
    this->alphaDiscard = (flags & ShaderFlags::FLAG_ALPHA_DISCARD) != 0;
    this->colorPointer = (flags & ShaderFlags::FLAG_COLOR_POINTER) != 0;
    this->lighting = (flags & ShaderFlags::FLAG_NORMAL) != 0;

    // vertex shader source
    std::ostringstream vshsrc;
    vshsrc << "uniform mat4 uProjection;\n";
    vshsrc << "uniform mat4 uModelView;\n";
    if(textured) {
        vshsrc << "uniform mat4 uTextureMx;\n";
        vshsrc << "attribute vec2 aTextureCoords;\n";
        vshsrc << "varying vec2 vTexPos;\n";
    }
    vshsrc << "attribute vec3 aVertexCoords;\n";
    if(colorPointer) {
        vshsrc << "attribute vec4 aColorPointer;\n";
        vshsrc << "varying vec4 vColor;\n";
    }
    if(lighting) {
        vshsrc << "attribute vec3 aNormals;\n";
        vshsrc << "varying vec3 vNormal;\n";
    }
    vshsrc << "void main() {\n";
    if(textured) {
        vshsrc << "  vec4 texCoords = uTextureMx * vec4(aTextureCoords.xy, 0.0, 1.0);\n";
        vshsrc << "  vTexPos = texCoords.xy;\n";
    }
    if(colorPointer)
        vshsrc << "  vColor = aColorPointer;\n";
    if(lighting)
        vshsrc << "  vNormal = normalize(mat3(uProjection * uModelView) * aNormals);\n";
    vshsrc << "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xyz, 1.0);\n";
    vshsrc << "}";

    // fragment shader source
    std::ostringstream fshsrc;
    fshsrc << "precision mediump float;\n";
    if(textured) {
        fshsrc << "uniform sampler2D uTexture;\n";
        fshsrc << "varying vec2 vTexPos;\n";
    }
    fshsrc << "uniform vec4 uColor;\n";
    if(alphaDiscard)
        fshsrc << "uniform float uAlphaDiscard;\n";
    if(colorPointer)
        fshsrc << "varying vec4 vColor;\n";
    if(lighting)
        fshsrc << "varying vec3 vNormal;\n";
    fshsrc << "void main(void) {\n";
    // XXX - color pointer is NOT working with modulation, don't use it
    //       with texturing right now either until issues can be tested
    //       and resolved
    if(textured && colorPointer)
        fshsrc << "  vec4 color = texture2D(uTexture, vTexPos) * vColor;\n";
    else if(textured && !colorPointer)
        fshsrc << "  vec4 color = texture2D(uTexture, vTexPos);\n";
    else if(colorPointer)
        fshsrc << "  vec4 color = vColor;\n";
    else
        fshsrc << "  vec4 color = vec4(1.0, 1.0, 1.0, 1.0);\n";
    // XXX - should discard be before or after modulation???
    if(alphaDiscard) {
        fshsrc << "  if(color.a < uAlphaDiscard)\n";
        fshsrc << "    discard;\n";
    }
    if(lighting) {
        // XXX - next two as uniforms
        fshsrc << "  vec3 sun_position = vec3(3.0, 10.0, -5.0);\n";
        fshsrc << "  vec3 sun_color = vec3(1.0, 1.0, 1.0);\n";
        fshsrc << "  float lum = max(dot(vNormal, normalize(sun_position)), 0.0);\n";
        fshsrc << "  color = color * vec4((0.6 + 0.4 * lum) * sun_color, 1.0);\n";
    }
    fshsrc << "  gl_FragColor = uColor * color;\n";
    fshsrc << "}";

    this->fsh_ = fshsrc.str();
    this->vsh_ = vshsrc.str();

    ShaderProgram program { GL_NONE, GL_NONE, GL_NONE };
    GLSLUtil_createProgram(&program, vshsrc.str().c_str(), fshsrc.str().c_str());
    handle = program.program;

    if(program.vertShader != GL_NONE)
        glDeleteShader(program.vertShader);
    if(program.fragShader != GL_NONE)
        glDeleteShader(program.fragShader);

    uProjection = glGetUniformLocation(handle, "uProjection");
    uModelView = glGetUniformLocation(handle, "uModelView");
    uTextureMx = glGetUniformLocation(handle, "uTextureMx");
    uTexture = glGetUniformLocation(handle, "uTexture");
    uAlphaDiscard = glGetUniformLocation(handle, "uAlphaDiscard");
    uColor = glGetUniformLocation(handle, "uColor");
    aVertexCoords = glGetAttribLocation(handle, "aVertexCoords");
    aTextureCoords = glGetAttribLocation(handle, "aTextureCoords");
    aColorPointer = glGetAttribLocation(handle, "aColorPointer");
    aNormals = glGetAttribLocation(handle, "aNormals");

    numAttribs = 1 + (colorPointer ? 1 : 0) + (textured ? 1 : 0) + (lighting ? 1 : 0);
}

void TAK::Engine::Renderer::Shader_get(std::shared_ptr<const Shader> &value, const RenderContext &ctx, const RenderAttributes &attrs) NOTHROWS
{
    unsigned int flags = 0u;
    if (attrs.colorPointer)
        flags |= ShaderFlags::FLAG_COLOR_POINTER;
    if (!attrs.opaque)
        flags |= ShaderFlags::FLAG_ALPHA_DISCARD;
    for (std::size_t i = 0u; i < 8u; i++) {
        if (!attrs.textureIds[i])
            continue;
        flags |= ShaderFlags::FLAG_TEXTURED;
        break;
    }
    if (attrs.normals)
        flags |= ShaderFlags::FLAG_NORMAL;

    do {
        Lock lock(shadersMutex());
        TE_CHECKBREAK_CODE(lock.status);

        // obtain or insert the shaders for the specified context
        std::map<unsigned, std::shared_ptr<const Shader>> &ctxShaders = shaders()[&ctx];

        auto retval = ctxShaders.find(flags);
        if (retval == ctxShaders.end()) {
            value.reset(new Shader(flags));
            ctxShaders[flags] = std::shared_ptr<const Shader>(value);
        } else {
            value = retval->second;
        }

        return;
    } while (false);

    // failed lock acquisition, create and return a shader instance
    value = std::shared_ptr<const Shader>(new(std::nothrow) Shader(flags));
}

TAKErr TAK::Engine::Renderer::Shader_get(std::shared_ptr<const Shader2> &value, const TAK::Engine::Core::RenderContext& ctx, const RenderAttributes& attrs) NOTHROWS {

    bool textured = false;
    bool lighting = false;
    unsigned int flags = 0u;
    if (attrs.colorPointer)
        return TE_Unsupported;
    if (!attrs.opaque)
        return TE_Unsupported;
    for (std::size_t i = 0u; i < 8u; i++) {
        if (!attrs.textureIds[i])
            continue;
        textured = true;
        flags |= ShaderFlags::FLAG_TEXTURED;
        break;
    }
    if (attrs.normals) {
        lighting = true;
        flags |= ShaderFlags::FLAG_NORMAL;
    }

    do {
        Lock lock(shadersMutex());
        TE_CHECKBREAK_CODE(lock.status);

        // obtain or insert the shaders for the specified context
        std::map<unsigned, std::shared_ptr<const Shader2>>& ctxShaders = shaders2()[&ctx];

        auto retval = ctxShaders.find(flags);
        if (retval == ctxShaders.end()) {
            std::shared_ptr<Shader2> shader(new Shader2(), deleteShader2);
            TAKErr code = makeDefaultShader2(shader.get(), textured, lighting);
            if (code != TE_Ok)
                return code;
            value = shader;
            ctxShaders[flags] = std::shared_ptr<const Shader2>(value);
        }
        else {
            value = retval->second;
        }

        return TE_Ok;
    } while (false);

    return TE_Err;
}

TAKErr TAK::Engine::Renderer::Shader_compile(std::shared_ptr<const Shader2>& result, const TAK::Engine::Core::RenderContext& ctx, const char* vert, const char* frag) NOTHROWS {
    if (!vert || !frag)
        return TE_InvalidArg;
    std::shared_ptr<Shader2> shader(new Shader2(), deleteShader2);
    TAKErr code = compileShader2(shader.get(), vert, frag);
    if (code == TE_Ok)
        result = shader;
    return code;
}

