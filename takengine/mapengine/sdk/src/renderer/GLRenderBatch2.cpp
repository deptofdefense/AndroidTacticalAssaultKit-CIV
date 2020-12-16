#include "renderer/GL.h"
#include "GLRenderBatch2.h"

#include <sstream>

#include <cmath>

#ifndef __ANDROID__
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/core/GLMapRenderGlobals.h"
#endif

#include "port/StringBuilder.h"
#include "renderer/GLSLUtil.h"
#include "util/Memory.h"

using namespace TAK::Engine::Renderer;

#ifndef __ANDROID__
using namespace TAK::Engine::Renderer::Core;
#endif

using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

#ifndef __ANDROID__
using namespace atakmap::renderer;
#endif

#define UNTEXTURED_VERTEX_SHADER_2D_SRC \
            "uniform mat4 uProjection;\n" \
            "uniform mat4 uModelView;\n" \
            "attribute vec2 aVertexCoords;\n" \
            "attribute vec4 aColor;\n" \
            "varying vec4 vColor;\n" \
            "void main() {\n" \
            "  vColor = aColor;\n" \
            "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xy, 0.0, 1.0);\n" \
            "}"

#define UNTEXTURED_VERTEX_SHADER_3D_SRC \
            "uniform mat4 uProjection;\n" \
            "uniform mat4 uModelView;\n" \
            "attribute vec3 aVertexCoords;\n" \
            "attribute vec4 aColor;\n" \
            "varying vec4 vColor;\n" \
            "void main() {\n" \
            "  vColor = aColor;\n" \
            "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xyz, 1.0);\n" \
            "}"

#define UNTEXTURED_FRAGMENT_SHADER_SRC \
            "precision mediump float;\n" \
            "varying vec4 vColor;\n" \
            "void main(void) {\n" \
            "  gl_FragColor = vColor;\n" \
            "}\n"

#define SPRITE_BATCH_VERTEX_SHADER_2D_SRC \
    "uniform mat4 uProjection;\n" \
    "uniform mat4 uModelView;\n" \
    "attribute vec2 aVertexCoords;\n" \
    "attribute vec2 aTextureCoords;\n" \
    "attribute vec4 aColor;\n" \
    "attribute float aTexUnit;\n" \
    "varying vec2 vTexPos;\n" \
    "varying vec4 vColor;\n" \
    "varying float vTexUnit;\n" \
    "void main() {\n" \
    "  vTexPos = aTextureCoords;\n" \
    "  vColor = aColor;\n" \
    "  vTexUnit = aTexUnit;\n" \
    "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xy, 0.0, 1.0);\n" \
    "}"

#define SPRITE_BATCH_VERTEX_SHADER_3D_SRC \
    "uniform mat4 uProjection;\n" \
    "uniform mat4 uModelView;\n" \
    "attribute vec3 aVertexCoords;\n" \
    "attribute vec2 aTextureCoords;\n" \
    "attribute vec4 aColor;\n" \
    "attribute float aTexUnit;\n" \
    "varying vec2 vTexPos;\n" \
    "varying vec4 vColor;\n" \
    "varying float vTexUnit;\n" \
    "void main() {\n" \
    "  vTexPos = aTextureCoords;\n" \
    "  vColor = aColor;\n" \
    "  vTexUnit = aTexUnit;\n" \
    "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xyz, 1.0);\n" \
    "}"

#define INTERNAL_TEXTURE_UNIT_LIMIT 32u

#define VERTEX_SIZE_2D 24u // 12 vertex coord
                           // 8 texture coord
                           // 4 rgba
                           // 4 texture unit
#define VERTEX_SIZE_3D 28u // 12 vertex coord
                           // 8 texture coord
                           // 4 rgba
                           // 4 texture unit
#define VERTICES_PER_SPRITE 6u

#if 1
#define NEEDS_STRIDE(c) \
    needsToUseStrideHere \
    c
#else
#define NEEDS_STRIDE(c) \
    c
#endif

namespace
{
    bool hasBits(int v, int m);

    const int GL_TEXTURE_UNITS[] = {
        GL_TEXTURE0,
        GL_TEXTURE1,
        GL_TEXTURE2,
        GL_TEXTURE3,
        GL_TEXTURE4,
        GL_TEXTURE5,
        GL_TEXTURE6,
        GL_TEXTURE7,
        GL_TEXTURE8,
        GL_TEXTURE9,
        GL_TEXTURE10,
        GL_TEXTURE11,
        GL_TEXTURE12,
        GL_TEXTURE13,
        GL_TEXTURE14,
        GL_TEXTURE15,
        GL_TEXTURE16,
        GL_TEXTURE17,
        GL_TEXTURE18,
        GL_TEXTURE19,
        GL_TEXTURE20,
        GL_TEXTURE21,
        GL_TEXTURE22,
        GL_TEXTURE23,
        GL_TEXTURE24,
        GL_TEXTURE25,
        GL_TEXTURE26,
        GL_TEXTURE27,
        GL_TEXTURE28,
        GL_TEXTURE29,
        GL_TEXTURE30,
        GL_TEXTURE31,
    };

    const float MX_IDENTITY[16] =
    {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1,
    };

    template<typename T>
    bool isDegenerate1d(const T *triangle, const std::size_t stride);
    template<typename T>
    bool isDegenerate2d(const T *triangle, const std::size_t stride) NOTHROWS;
    template<typename T>
    bool isDegenerate3d(const T *triangle, const std::size_t stride) NOTHROWS;

    TAKErr addLine2d_2dImpl(MemBuffer2 &pfRenderBuffer, MemBuffer2 &indexBuffer, const std::size_t indexOff, const uint64_t vertexConst, const float widthX, const float widthY, const float x0, const float y0, const float x1, const float y1) NOTHROWS;
    TAKErr addLine2d_3dImpl(MemBuffer2 &pfRenderBuffer, MemBuffer2 &indexBuffer, const std::size_t indexOff, const uint64_t vertexConst, const float widthX, const float widthY, const float x0, const float y0, const float x1, const float y1) NOTHROWS;
    TAKErr addLine3dImpl(MemBuffer2 &pfRenderBuffer, MemBuffer2 &indexBuffer, const std::size_t indexOff, const uint64_t vertexConst, const float widthX, const float widthY, const float x0, const float y0, const float z0, const float x1, const float y1, const float z1) NOTHROWS;

    TAKErr addTexturedVertex2d_2d(MemBuffer2 &pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const uint64_t vertexConst, const float *mx) NOTHROWS;
    TAKErr addTexturedVertex2d_3d(MemBuffer2 &pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const uint64_t vertexConst, const float *mx) NOTHROWS;
    TAKErr addTexturedVertex3d(MemBuffer2 &pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const uint64_t vertexConst, const float *mx) NOTHROWS;
    TAKErr addTexturedVertex2d_2dTransform(MemBuffer2 &pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const uint64_t vertexConst, const float *mx) NOTHROWS;
    TAKErr addTexturedVertex2d_3dTransform(MemBuffer2 &pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const uint64_t vertexConst, const float *mx) NOTHROWS;
    TAKErr addTexturedVertex3dTransform(MemBuffer2 &pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const uint64_t vertexConst, const float *mx) NOTHROWS;
    TAKErr addUntexturedVertex2d_2d(MemBuffer2 &pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const uint64_t vertexConst, const float *mx) NOTHROWS;
    TAKErr addUntexturedVertex2d_3d(MemBuffer2 &pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const uint64_t vertexConst, const float *mx) NOTHROWS;
    TAKErr addUntexturedVertex3d(MemBuffer2 &pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const uint64_t vertexConst, const float *mx) NOTHROWS;
    TAKErr addUntexturedVertex2d_2dTransform(MemBuffer2 &pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const uint64_t vertexConst, const float *mx) NOTHROWS;
    TAKErr addUntexturedVertex2d_3dTransform(MemBuffer2 &pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const uint64_t vertexConst, const float *mx) NOTHROWS;
    TAKErr addUntexturedVertex3dTransform(MemBuffer2 &pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const uint64_t vertexConst, const float *mx) NOTHROWS;


    void transform2d_2d(float *dstX, float *dstY, const float *mx, const float srcX, const float srcY) NOTHROWS;
    void transform2d_3d(float *dstX, float *dstY, float *dstZ, const float *mx, const float srcX, const float srcY) NOTHROWS;
    void transform3d(float *dstX, float *dstY, float *dstZ, const float *mx, const float srcX, const float srcY, const float srcZ) NOTHROWS;

    typedef TAKErr(*addVertexFn)(MemBuffer2 &pfRenderBuffer,
                                 const float *pfVertexCoords,
                                 const float *pfTexCoords,
                                 const uint64_t vertexConst,
                                 const float *mx);

    TAKErr getAddVertexFunction(addVertexFn *value, const std::size_t size, const bool texCoords, const int hints) NOTHROWS;

#ifdef __ANDROID__
    std::size_t GLRenderBatch_textureUnitLimit = 32;

    std::size_t glMaxTextureUnits() NOTHROWS;
#endif
}

/*********************************************************************/
// Constructor/Destructor/other init

GLRenderBatch2::GLRenderBatch2(const std::size_t cap) NOTHROWS :
    numActiveTexUnits(0),
    originalTextureUnit(0),
    batchHints(0),
    renderBuffer(std::min((std::size_t)0xFFFFu, cap) * VERTEX_SIZE_3D),
    indexBuffer(std::min((std::size_t)0xFFFFu, cap) * VERTICES_PER_SPRITE * 2u),
    untexturedProgram2d(2u),
    texturedProgram2d(2u),
    untexturedProgram3d(3u),
    texturedProgram3d(3u),
    texUnitIdxToTexId(new int[INTERNAL_TEXTURE_UNIT_LIMIT]),
    projection(2),
    modelView(32),
    texture(16),
    lineWidth(1),
    viewportWidth(1),
    viewportHeight(1),
    mvpDirty(true)
{
    memset(projection.pointer, 0u, sizeof(float)*16);
    projection.pointer[0] = 1;
    projection.pointer[5] = 1;
    projection.pointer[10] = 1;
    projection.pointer[15] = 1;
    memset(modelView.pointer, 0u, sizeof(float)*16);
    modelView.pointer[0] = 1;
    modelView.pointer[5] = 1;
    modelView.pointer[10] = 1;
    modelView.pointer[15] = 1;
    memset(texture.pointer, 0u, sizeof(float)*16);
    texture.pointer[0] = 1;
    texture.pointer[5] = 1;
    texture.pointer[10] = 1;
    texture.pointer[15] = 1;
}

GLRenderBatch2::~GLRenderBatch2() NOTHROWS
{
}

TAKErr GLRenderBatch2::initPrograms() NOTHROWS
{
    TAKErr code(TE_Ok);

    code = batchProgramInit(&untexturedProgram2d,
                            false,
                            UNTEXTURED_VERTEX_SHADER_2D_SRC,
                            UNTEXTURED_FRAGMENT_SHADER_SRC);
    TE_CHECKRETURN_CODE(code);

    code = batchProgramInit(&untexturedProgram3d,
                            false,
                            UNTEXTURED_VERTEX_SHADER_3D_SRC,
                            UNTEXTURED_FRAGMENT_SHADER_SRC);
    TE_CHECKRETURN_CODE(code);

    std::string texturedFragShaderSrc = getFragmentShaderSrc();

    code = batchProgramInit(&texturedProgram2d,
                            true,
                            SPRITE_BATCH_VERTEX_SHADER_2D_SRC,
                            texturedFragShaderSrc.c_str());
    TE_CHECKRETURN_CODE(code);

    code = batchProgramInit(&texturedProgram3d,
                            true,
                            SPRITE_BATCH_VERTEX_SHADER_3D_SRC,
                            texturedFragShaderSrc.c_str());
    TE_CHECKRETURN_CODE(code);

    return code;
}

std::string GLRenderBatch2::getFragmentShaderSrc() NOTHROWS
{
    StringBuilder stringBuilder;
    const std::size_t textureUnitLimit = GLRenderBatch2_getBatchTextureUnitLimit();

    stringBuilder << "precision mediump float;\n";
    for (std::size_t i = 0; i < textureUnitLimit; i++)
        stringBuilder << "uniform sampler2D uTexture" << i << ";\n";

//#define TE_EXP_NOSWITCHBATCHFRAG

#ifdef TE_EXP_NOSWITCHBATCHFRAG
    stringBuilder << "vec4 getContribution(float vbTexId, float fTexId)\n"
            << "{\n"
            << "  float f = vbTexId - fTexId;\n"
            << "  float scale = 1.0 - sign(f*f);\n"
            << "  return vec4(scale, scale, scale, scale);\n"
            << "}\n";
#endif
    stringBuilder << "varying float vTexUnit;\n"
            << "varying vec4 vColor;\n"
            << "varying vec2 vTexPos;\n";

    stringBuilder << "void main(void) {\n";

#ifdef TE_EXP_NOSWITCHBATCHFRAG
    for (std::size_t i = 0; i < textureUnitLimit; i++)
        stringBuilder << "    gl_FragColor += vColor * getContribution(vTexUnit, " << i << ".0) * texture2D(uTexture" << i << ", vTexPos);\n";

    stringBuilder << "    gl_FragColor += vColor * getContribution(vTexUnit, -1.0);\n";
#else
    if (textureUnitLimit) {
        stringBuilder << "    if(vTexUnit == 0.0)\n";
        stringBuilder << "        gl_FragColor = vColor * texture2D(uTexture0, vTexPos);\n";
        for (std::size_t i = 1; i < textureUnitLimit; i++) {
            stringBuilder << "    else if(vTexUnit == " << i << ".0)\n";
            stringBuilder << "        gl_FragColor = vColor * texture2D(uTexture" << i << ", vTexPos);\n";
        }
        stringBuilder << "    else if(vTexUnit == -1.0) \n";
    }

    stringBuilder << "    gl_FragColor = vColor;\n";
#endif
    stringBuilder << "}\n";

    return std::string(stringBuilder.c_str());
}

/*********************************************************************/
// Life-cycle functions

TAKErr GLRenderBatch2::begin() NOTHROWS
{
    return this->begin(0);
}

TAKErr GLRenderBatch2::begin(const int hints) NOTHROWS
{
    TAKErr code(TE_Ok);

    this->batchHints = hints;

#ifndef __ANDROID__
    GLES20FixedPipeline *fixedPipe = GLES20FixedPipeline::getInstance();
    originalTextureUnit = fixedPipe->getActiveTexture();
#else
    glGetIntegerv(GL_ACTIVE_TEXTURE, &originalTextureUnit);
#endif
    renderBuffer.position(0u);
    if (hasBits(this->batchHints, GLRenderBatch2::TwoDimension)) {
        // reset the limit to the maximum possible number of 2D vertices given
        // the buffer capacity
        renderBuffer.limit(std::min(renderBuffer.size() / VERTEX_SIZE_2D, (size_t)0xFFFFu) * VERTEX_SIZE_2D);
    } else {
        // reset the limit to allow for the maximum number of batched 3D
        // vertices
        renderBuffer.limit(renderBuffer.size());
    }
    indexBuffer.position(0u);
    numActiveTexUnits = 0;

    float p[4];
    glGetFloatv(GL_VIEWPORT, p);
    viewportWidth = p[2];
    viewportHeight = p[3];

    if (!untexturedProgram2d.handle) {
        code = initPrograms();
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}

TAKErr GLRenderBatch2::end() NOTHROWS
{
    TAKErr code(TE_Ok);
    code = flush();
    TE_CHECKRETURN_CODE(code);

#ifndef __ANDROID__
    GLES20FixedPipeline::getInstance()->glActiveTexture(originalTextureUnit);
#else
    glActiveTexture(originalTextureUnit);
#endif

    return code;
}

TAKErr GLRenderBatch2::release() NOTHROWS
{
    TAKErr code(TE_Ok);

    if (untexturedProgram2d.handle) {
        glDeleteProgram(untexturedProgram2d.handle);
        untexturedProgram2d.handle = 0;
    }
    if (untexturedProgram3d.handle) {
        glDeleteProgram(untexturedProgram3d.handle);
        untexturedProgram3d.handle = 0;
    }
    if (texturedProgram2d.handle) {
        glDeleteProgram(texturedProgram2d.handle);
        texturedProgram2d.handle = 0;
    }
    if (texturedProgram3d.handle) {
        glDeleteProgram(texturedProgram3d.handle);
        texturedProgram3d.handle = 0;
    }
    return code;
}

TAKErr GLRenderBatch2::flush() NOTHROWS
{
    TAKErr code(TE_Ok);

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    if (renderBuffer.position()) {
        const bool textured = !hasBits(this->batchHints, GLRenderBatch2::Untextured);
        const bool buffer2d = hasBits(this->batchHints, GLRenderBatch2::TwoDimension);

        batch_render_program_t *program;
        if(buffer2d)
            program = textured ? &texturedProgram2d : &untexturedProgram2d;
        else
            program = textured ? &texturedProgram3d : &untexturedProgram3d;

        glUseProgram(program->handle);

        // if hardware rendering,
        if (!hasBits(this->batchHints, GLRenderBatch2::SoftwareTransforms)) {
            glUniformMatrix4fv(program->uProjectionHandle, 1, false, projection.pointer);
            glUniformMatrix4fv(program->uModelViewHandle, 1, false, modelView.pointer);
        } else {
            glUniformMatrix4fv(program->uProjectionHandle, 1, false, MX_IDENTITY);
            glUniformMatrix4fv(program->uModelViewHandle, 1, false, MX_IDENTITY);
        }

        const std::size_t vertexSize = buffer2d ? VERTEX_SIZE_2D : VERTEX_SIZE_3D;

        glVertexAttribPointer(
            program->aVertexCoordsHandle,
            static_cast<GLint>(program->size),
            GL_FLOAT,
            false,
            static_cast<GLsizei>(vertexSize),
            renderBuffer.get());

        if (program->aTextureCoordsHandle+1)
            glVertexAttribPointer(
                program->aTextureCoordsHandle,
                2,
                GL_FLOAT,
                false,
                static_cast<GLsizei>(vertexSize),
                renderBuffer.get() + (program->size*4));

        glVertexAttribPointer(
            program->aColorHandle,
            4,
            GL_UNSIGNED_BYTE,
            true,
            static_cast<GLsizei>(vertexSize),
            renderBuffer.get() + (program->size*4) + 8);

        if (program->aTexUnitHandle+1)
            glVertexAttribPointer(
                program->aTexUnitHandle,
                1,
                GL_FLOAT,
                false,
                static_cast<GLsizei>(vertexSize),
                renderBuffer.get() + (program->size*4) + 12);

        glEnableVertexAttribArray(program->aVertexCoordsHandle);
        if (program->aTextureCoordsHandle+1)
            glEnableVertexAttribArray(program->aTextureCoordsHandle);
        glEnableVertexAttribArray(program->aColorHandle);
        if (program->aTexUnitHandle+1)
            glEnableVertexAttribArray(program->aTexUnitHandle);

        glDrawElements(GL_TRIANGLES,
            static_cast<GLsizei>(indexBuffer.position()/2u),
            GL_UNSIGNED_SHORT,
            indexBuffer.get());

        glDisableVertexAttribArray(program->aVertexCoordsHandle);
        if (program->aTextureCoordsHandle+1)
            glDisableVertexAttribArray(program->aTextureCoordsHandle);
        glDisableVertexAttribArray(program->aColorHandle);
        if (program->aTexUnitHandle+1)
            glDisableVertexAttribArray(program->aTexUnitHandle);
    }

    numActiveTexUnits = 0;

    renderBuffer.position(0u);
    indexBuffer.position(0u);

    glDisable(GL_BLEND);

    return code;
}


TAKErr GLRenderBatch2::setMatrix(const int mode, const float *mx) NOTHROWS
{
    TAKErr code(TE_Ok);

    // if vertices have been added and we were expecting to do HW transforms,
    // flush out the buffer on the current MVP and new buffer using SW
    // transforms
    if (renderBuffer.position() && !hasBits(this->batchHints, GLRenderBatch2::SoftwareTransforms)) {
        code = flush();
        TE_CHECKRETURN_CODE(code);
        this->batchHints |= GLRenderBatch2::SoftwareTransforms;
    }

    switch(mode) {
    case GL_MODELVIEW :
        memcpy(modelView.pointer, mx, 16*sizeof(float));
        mvpDirty = true;
        break;
    case GL_PROJECTION :
        memcpy(projection.pointer, mx, 16*sizeof(float));
        mvpDirty = true;
        break;
    case GL_TEXTURE :
        memcpy(texture.pointer, mx, 16*sizeof(float));
        break;
    default :
        return TE_InvalidArg;
    }

    return code;
}

TAKErr GLRenderBatch2::pushMatrix(const int mode) NOTHROWS
{
    TAKErr code(TE_Ok);

    switch(mode) {
    case GL_MODELVIEW :
        code = modelView.push();
        TE_CHECKBREAK_CODE(code);
        break;
    case GL_PROJECTION :
        code = projection.push();
        TE_CHECKBREAK_CODE(code);
        break;
    case GL_TEXTURE :
        code = texture.push();
        TE_CHECKBREAK_CODE(code);
        break;
    default :
        return TE_InvalidArg;
    }

    return code;
}

TAKErr GLRenderBatch2::popMatrix(const int mode) NOTHROWS
{
    TAKErr code(TE_Ok);

    // if vertices have been added and we were expecting to do HW transforms,
    // flush out the buffer on the current MVP and new buffer using SW
    // transforms
    if (renderBuffer.position() && !hasBits(this->batchHints, GLRenderBatch2::SoftwareTransforms)) {
        code = flush();
        TE_CHECKRETURN_CODE(code);
        this->batchHints |= GLRenderBatch2::SoftwareTransforms;
    }

    switch(mode) {
    case GL_MODELVIEW :
        code = modelView.pop();
        TE_CHECKBREAK_CODE(code);
        mvpDirty = true;
        break;
    case GL_PROJECTION :
        code = projection.pop();
        TE_CHECKBREAK_CODE(code);
        mvpDirty = true;
        break;
    case GL_TEXTURE :
        code = texture.pop();
        TE_CHECKBREAK_CODE(code);
        break;
    default :
        return TE_InvalidArg;
    }

    return code;
}


TAKErr GLRenderBatch2::setLineWidth(float width) NOTHROWS
{
    if(width <= 0)
        return TE_InvalidArg;
    lineWidth = width;
    return TE_Ok;
}

TAKErr GLRenderBatch2::batch(const int texId,
                             const int mode,
                             const std::size_t count,
                             const std::size_t size,
                             const std::size_t vStride,
                             const float *vertices,
                             const std::size_t tcStride,
                             const float *texCoords,
                             const float r,
                             const float g,
                             const float b,
                             const float a) NOTHROWS
{
    TAKErr code(TE_Ok);

    if(size != 3u && size != 2u)
        return TE_Unsupported;

    // empty geometry
    if (!count)
        return TE_Ok;

    if(texCoords && hasBits(this->batchHints, GLRenderBatch2::Untextured)) {
        code = flush();
        TE_CHECKRETURN_CODE(code);

        this->batchHints &= ~GLRenderBatch2::Untextured;
    }

    if(size != 2u && hasBits(this->batchHints, GLRenderBatch2::TwoDimension)) {
        code = flush();
        TE_CHECKRETURN_CODE(code);

        this->batchHints &= ~GLRenderBatch2::TwoDimension;

        // reset the limit on the render buffer to allow the maximum number
        // of batched 3D vertices
        this->renderBuffer.reset();
    }

    switch(mode) {
    case GL_LINES :
        if(texCoords)
            return TE_Unsupported;
        if(lineWidth > 0) {
            code = addLines(size,
                            vStride ? vStride : size*4u,
                            vertices,
                            count/2,
                            lineWidth,
                            r, g, b, a);
            TE_CHECKBREAK_CODE(code);
        }
        break;
    case GL_LINE_STRIP :
        if(texCoords)
            return TE_Unsupported;
        if(lineWidth > 0) {
            code = addLineStrip(size,
                                 vStride ? vStride : size * 4u,
                                 vertices,
                                count*size,
                                 lineWidth,
                                 r, g, b, a);
            TE_CHECKBREAK_CODE(code);
        }
        break;
    case GL_LINE_LOOP :
        if(texCoords)
            return TE_Unsupported;
        if(lineWidth > 0) {
            code = addLineLoop(size,
                               vStride ? vStride : size * 4u,
                               vertices,
                               count*size,
                               lineWidth,
                               r, g, b, a);
            TE_CHECKBREAK_CODE(code);
        }
        break;
    case GL_TRIANGLES :
        if(texCoords) {
            code = addTrianglesSprite(size,
                                      vStride ? vStride : size * 4u,
                                      vertices,
                                      count*size,
                                      tcStride ? tcStride : 8u,
                                      texCoords,
                                      texId,
                                      r, g, b, a);
        } else {
            code = addTriangles(size,
                                vStride ? vStride : size * 4u,
                                vertices,
                                count*size,
                                r, g, b, a);
        }
        TE_CHECKBREAK_CODE(code);

        break;
    case GL_TRIANGLE_STRIP :
        if(texCoords) {
            code = addTriangleStripSprite(size,
                                          vStride ? vStride : size * 4u,
                                          vertices,
                                          count*size,
                                          tcStride ? tcStride : 8u,
                                          texCoords,
                                          texId,
                                          r, g, b, a);
        } else {
            code = addTriangleStrip(size,
                                    vStride ? vStride : size * 4u,
                                    vertices,
                                    count*size,
                                    r, g, b, a);
        }
        TE_CHECKBREAK_CODE(code);

        break;
    case GL_TRIANGLE_FAN :
        if(texCoords) {
            code = addTriangleFanSprite(size,
                                        vStride ? vStride : size * 4u,
                                        vertices,
                                        count*size,
                                        tcStride ? tcStride : 8u,
                                        texCoords,
                                        texId,
                                        r, g, b, a);
        } else {
            code = addTriangleFan(size,
                                  vStride ? vStride : size * 4u,
                                  vertices,
                                  count*size,
                                  r, g, b, a);
        }
        TE_CHECKBREAK_CODE(code);

        break;
    default :
        return TE_InvalidArg;
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr GLRenderBatch2::batch(const int texId,
                             const int mode,
                             const std::size_t count,
                             const std::size_t size,
                             const std::size_t vStride,
                             const float *vertices,
                             const std::size_t tcStride,
                             const float *texCoords,
                             const std::size_t indexCount,
                             const unsigned short *indices,
                             const float r,
                             const float g,
                             const float b,
                             const float a) NOTHROWS
{
    TAKErr code(TE_Ok);

    if(size != 3u && size != 2u)
        return TE_Unsupported;

    if(vStride && vStride != (4*size))
        return TE_Unsupported;
    if(tcStride && tcStride != 8u)
        return TE_Unsupported;

    if(texCoords && hasBits(this->batchHints, GLRenderBatch2::Untextured)) {
        code = flush();
        TE_CHECKRETURN_CODE(code);

        this->batchHints &= ~GLRenderBatch2::Untextured;
    }

    if(size != 2u && hasBits(this->batchHints, GLRenderBatch2::TwoDimension)) {
        code = flush();
        TE_CHECKRETURN_CODE(code);

        this->batchHints &= ~GLRenderBatch2::TwoDimension;
    }

    switch(mode) {
    case GL_LINES :
        if(texCoords)
            return TE_Unsupported;
#if 0
        if(lineWidth > 0) {
            code = addLines(size,
                            vertices,
                            count/2,
                            lineWidth,
                            r, g, b, a);
            TE_CHECKBREAK_CODE(code);
        }
        break;
#else
        return TE_Unsupported;
#endif

    case GL_LINE_STRIP :
        if(texCoords)
            return TE_Unsupported;
#if 0
        if(lineWidth > 0) {
            code = addLineStrip(size,
                                vertices,
                                count*size,
                                lineWidth,
                                r, g, b, a);
            TE_CHECKBREAK_CODE(code);
        }
        break;
#else
        return TE_Unsupported;
#endif
    case GL_LINE_LOOP :
        if(texCoords)
            return TE_Unsupported;
#if 0
        if(lineWidth > 0) {
            code = addLineLoop(size,
                               vertices,
                               count*size,
                               lineWidth,
                               r, g, b, a);
            TE_CHECKBREAK_CODE(code);
        }
        break;
#else
        return TE_Unsupported;
#endif
    case GL_TRIANGLES :
        if(texCoords) {
            code = addTrianglesSprite(size,
                                      vStride ? vStride : size * 4u,
                                      vertices,
                                      count*size,
                                      indices,
                                      indexCount,
                                      tcStride ? tcStride : 8u,
                                      texCoords,
                                      texId,
                                      r, g, b, a);
        } else {
            code = addTriangles(size,
                                vStride ? vStride : size * 4u,
                                vertices,
                                count*size,
                                indices,
                                indexCount,
                                r, g, b, a);

        }
        TE_CHECKBREAK_CODE(code);

        break;
    case GL_TRIANGLE_STRIP :
        if(texCoords) {
            code = addTriangleStripSprite(size,
                                          vStride ? vStride : size * 4u,
                                          vertices,
                                          count*size,
                                          indices,
                                          indexCount,
                                          tcStride ? tcStride : 8u,
                                          texCoords,
                                          texId,
                                          r, g, b, a);
        } else {
            code = addTriangleStrip(size,
                                    vStride ? vStride : size * 4u,
                                    vertices,
                                    count*size,
                                    indices,
                                    indexCount,
                                    r, g, b, a);
        }
        TE_CHECKBREAK_CODE(code);

        break;
    case GL_TRIANGLE_FAN :
        if(texCoords) {
            code = addTriangleFanSprite(size,
                                        vStride ? vStride : size * 4u,
                                        vertices,
                                        count*size,
                                        indices,
                                        indexCount,
                                        tcStride ? tcStride : 8u,
                                        texCoords,
                                        texId,
                                        r, g, b, a);
        } else {
            code = addTriangleFan(size,
                                  vStride ? vStride : size * 4u,
                                  vertices,
                                  count*size,
                                  indices,
                                  indexCount,
                                  r, g, b, a);
        }
        TE_CHECKBREAK_CODE(code);

        break;
    default :
        return TE_InvalidArg;
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr GLRenderBatch2::addLinesImpl(const std::size_t size, const float width, const std::size_t numLines, const std::size_t vStride, const float *lines, const int step, uint64_t vertexConst) NOTHROWS
{
    TAKErr code(TE_Ok);

    if(size != 3u && size != 2u)
        return TE_Unsupported;

    // lines requires SW transform
    if(!hasBits(this->batchHints, GLRenderBatch2::SoftwareTransforms)) {
        code = this->flush();
        TE_CHECKRETURN_CODE(code);

        this->batchHints |= GLRenderBatch2::SoftwareTransforms;
    }

    const bool transformPoints = hasBits(this->batchHints, GLRenderBatch2::SoftwareTransforms);
    if(!transformPoints)
        return TE_IllegalState;

    const bool buffer2d = hasBits(this->batchHints, GLRenderBatch2::TwoDimension);
    const std::size_t vertexSize = buffer2d ? VERTEX_SIZE_2D : VERTEX_SIZE_3D;

    this->validateMVP();

    const auto *linesPtr = reinterpret_cast<const uint8_t *>(lines);
    if(size == 2u && buffer2d) {
        float x0;
        float y0;
        float x1;
        float y1;
        for (std::size_t i = 0u; i < numLines; i++) {
            if (renderBuffer.remaining() < (vertexSize * 4) ||
                indexBuffer.remaining()/2u < VERTICES_PER_SPRITE) {

                code = flush();
                TE_CHECKRETURN_CODE(code);
            }

            x0 = reinterpret_cast<const float *>(linesPtr)[0];
            y0 = reinterpret_cast<const float *>(linesPtr)[1];
            x1 = reinterpret_cast<const float *>(linesPtr+vStride)[0];
            y1 = reinterpret_cast<const float *>(linesPtr+vStride)[1];

            transform2d_2d(&x0, &y0, modelViewProjection, x0, y0);
            transform2d_2d(&x1, &y1, modelViewProjection, x1, y1);

            linesPtr += step*vStride;

            addLine2d_2dImpl(renderBuffer,
                             indexBuffer,
                             renderBuffer.position() / vertexSize,
                             vertexConst,
                             width / viewportHeight,
                             width / viewportWidth,
                             x0, y0,
                             x1, y1);
        }
        TE_CHECKRETURN_CODE(code);
    } else if(size == 2u) {
        float x0;
        float y0;
        float z0;
        float x1;
        float y1;
        float z1;
        for (std::size_t i = 0u; i < numLines; i++) {
            if (renderBuffer.remaining() < (vertexSize * 4) ||
                indexBuffer.remaining()/2u < VERTICES_PER_SPRITE) {

                code = flush();
                TE_CHECKRETURN_CODE(code);
            }

            x0 = reinterpret_cast<const float *>(linesPtr)[0];
            y0 = reinterpret_cast<const float *>(linesPtr)[1];
            x1 = reinterpret_cast<const float *>(linesPtr + vStride)[0];
            y1 = reinterpret_cast<const float *>(linesPtr + vStride)[1];

            transform2d_3d(&x0, &y0, &z0, modelViewProjection, x0, y0);
            transform2d_3d(&x1, &y1, &z1, modelViewProjection, x1, y1);

            linesPtr += step*vStride;

            addLine3dImpl(renderBuffer,
                          indexBuffer,
                          renderBuffer.position() / vertexSize,
                          vertexConst,
                          width / viewportHeight,
                          width / viewportWidth,
                          x0, y0, z0,
                          x1, y1, z1);
        }
        TE_CHECKRETURN_CODE(code);
    } else if(size == 3u) {
        float x0;
        float y0;
        float z0;
        float x1;
        float y1;
        float z1;
        for (std::size_t i = 0u; i < numLines; i++) {
            if (renderBuffer.remaining() < (vertexSize * 4) ||
                indexBuffer.remaining()/2u < VERTICES_PER_SPRITE) {

                code = flush();
                TE_CHECKRETURN_CODE(code);
            }

            x0 = reinterpret_cast<const float *>(linesPtr)[0];
            y0 = reinterpret_cast<const float *>(linesPtr)[1];
            z0 = reinterpret_cast<const float *>(linesPtr)[2];
            x1 = reinterpret_cast<const float *>(linesPtr + vStride)[0];
            y1 = reinterpret_cast<const float *>(linesPtr + vStride)[1];
            z1 = reinterpret_cast<const float *>(linesPtr + vStride)[2];

            transform3d(&x0, &y0, &z0, modelViewProjection, x0, y0, z0);
            transform3d(&x1, &y1, &z1, modelViewProjection, x1, y1, z1);

            linesPtr += step*vStride;

            addLine3dImpl(renderBuffer,
                          indexBuffer,
                          renderBuffer.position() / vertexSize,
                          vertexConst,
                          width / viewportHeight,
                          width / viewportWidth,
                          x0, y0, z0,
                          x1, y1, z1);
        }
        TE_CHECKRETURN_CODE(code);
    } else {
        return TE_IllegalState;
    }

    return code;
}

TAKErr GLRenderBatch2::addTrianglesImpl(const std::size_t size, const std::size_t vStride, const float *vertexCoords, std::size_t count, const std::size_t tcStride, const float *texCoords, int texUnitIdx, float r, float g, float b, float a) NOTHROWS
{
    TAKErr code(TE_Ok);

    const bool transformPoints = hasBits(this->batchHints, GLRenderBatch2::SoftwareTransforms);
    if(transformPoints)
        this->validateMVP();

    addVertexFn addVertex;
    code = getAddVertexFunction(&addVertex, size, !!texCoords, this->batchHints);
    TE_CHECKRETURN_CODE(code);

    const bool buffer2d = hasBits(this->batchHints, GLRenderBatch2::TwoDimension);
    const std::size_t vertexSize = buffer2d ? VERTEX_SIZE_2D : VERTEX_SIZE_3D;

    std::size_t indexOff = renderBuffer.position() / vertexSize;

    uint64_t vertexConst;
    ((unsigned char *)&vertexConst)[0] = (unsigned char)(r*255.0f);
    ((unsigned char *)&vertexConst)[1] = (unsigned char)(g*255.0f);
    ((unsigned char *)&vertexConst)[2] = (unsigned char)(b*255.0f);
    ((unsigned char *)&vertexConst)[3] = (unsigned char)(a*255.0f);
    (*(float *)(((unsigned char *)&vertexConst) + 4)) = (float)texUnitIdx;

    const auto *vertexCoordsPtr = reinterpret_cast<const uint8_t *>(vertexCoords);
    const auto *texCoordsPtr = reinterpret_cast<const uint8_t *>(texCoords);

    std::size_t numTriangles = count / (3u * size);
    for (std::size_t i = 0; i < numTriangles; i++) {
        if (renderBuffer.remaining() < (vertexSize * 3) ||
            indexBuffer.remaining()/2u < 3) {

            const int texId = texCoords ? texUnitIdxToTexId[texUnitIdx] : 0;

            code = flush();
            TE_CHECKBREAK_CODE(code);

            indexOff = 0;
            if(texId) {
                code = bindTexture(&texUnitIdx, texId);
                TE_CHECKBREAK_CODE(code);
                (*(float *)(((unsigned char *)&vertexConst) + 4)) = (float)texUnitIdx;
            }
        }

        // XXX - check for duplicate vertices
        for (int j = 0; j < 3; j++) {
            // vertex
            code = addVertex(renderBuffer,
                reinterpret_cast<const float *>(vertexCoordsPtr),
                reinterpret_cast<const float *>(texCoordsPtr),
                vertexConst,
                modelViewProjection);
            TE_CHECKBREAK_CODE(code);

            vertexCoordsPtr += vStride;
            texCoordsPtr += tcStride;

            // index
            code = indexBuffer.put(static_cast<uint16_t>(indexOff));
            TE_CHECKBREAK_CODE(code);

            indexOff++;
        }
        TE_CHECKRETURN_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr GLRenderBatch2::addIndexedTrianglesImpl(const std::size_t size, const std::size_t vStride, const float *vertexCoords, std::size_t count, const unsigned short *indices, std::size_t indexCount, const std::size_t tcStride, const float *texCoords, int texUnitIdx, float r, float g, float b, float a) NOTHROWS
{
    TAKErr code(TE_Ok);

    const bool transformPoints = hasBits(this->batchHints, GLRenderBatch2::SoftwareTransforms);
    if(transformPoints)
        this->validateMVP();

    const bool buffer2d = hasBits(this->batchHints, GLRenderBatch2::TwoDimension);
    const std::size_t vertexSize = buffer2d ? VERTEX_SIZE_2D : VERTEX_SIZE_3D;

    std::size_t indexOff = renderBuffer.position() / vertexSize;

    if (renderBuffer.remaining() < ((count/size) * vertexSize) ||
        indexBuffer.remaining()/2u < indexCount) {

          const int texId = texCoords ? texUnitIdxToTexId[texUnitIdx] : 0;

          code = flush();
          TE_CHECKRETURN_CODE(code);

          indexOff = 0;
          if(texId) {
              code = bindTexture(&texUnitIdx, texId);
              TE_CHECKRETURN_CODE(code);
          }
    }

    addVertexFn addVertex;
    code = getAddVertexFunction(&addVertex, size, !!texCoords, this->batchHints);
    TE_CHECKRETURN_CODE(code);

    uint64_t vertexConst;
    ((unsigned char *)&vertexConst)[0] = (unsigned char)(r*255.0f);
    ((unsigned char *)&vertexConst)[1] = (unsigned char)(g*255.0f);
    ((unsigned char *)&vertexConst)[2] = (unsigned char)(b*255.0f);
    ((unsigned char *)&vertexConst)[3] = (unsigned char)(a*255.0f);
    (*(float *)(((unsigned char *)&vertexConst) + 4)) = (float)texUnitIdx;

    const auto *vertexCoordsPtr = reinterpret_cast<const uint8_t *>(vertexCoords);
    const auto *texCoordsPtr = reinterpret_cast<const uint8_t *>(texCoords);

    const std::size_t numVerts = count / size;
    for (std::size_t i = 0; i < numVerts; i++) {
        // vertex
        code = addVertex(renderBuffer,
            reinterpret_cast<const float *>(vertexCoordsPtr),
            reinterpret_cast<const float *>(texCoordsPtr),
            vertexConst,
            modelViewProjection);
        TE_CHECKBREAK_CODE(code);

        vertexCoordsPtr += vStride;
        texCoordsPtr += tcStride;
    }
    TE_CHECKRETURN_CODE(code);

    if (indexOff == 0) {
        code = indexBuffer.put(indices, indexCount);
        TE_CHECKRETURN_CODE(code);
    } else {
        for (size_t i = 0; i < indexCount; i++) {
            code = indexBuffer.put(static_cast<uint16_t>(indexOff + indices[i]));
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}

TAKErr GLRenderBatch2::addTriangleStripImpl(const std::size_t size, const std::size_t vStride, const float *vertexCoords, std::size_t count, const std::size_t tcStride, const float *texCoords, int texUnitIdx, float r, float g, float b, float a) NOTHROWS
{
    TAKErr code(TE_Ok);

    std::size_t indexOff = 0u;

    uint64_t vertexConst;
    ((unsigned char *)&vertexConst)[0] = (unsigned char)(r*255.0f);
    ((unsigned char *)&vertexConst)[1] = (unsigned char)(g*255.0f);
    ((unsigned char *)&vertexConst)[2] = (unsigned char)(b*255.0f);
    ((unsigned char *)&vertexConst)[3] = (unsigned char)(a*255.0f);
    (*(float *)(((unsigned char *)&vertexConst) + 4)) = (float)texUnitIdx;

    const bool transformPoints = hasBits(this->batchHints, GLRenderBatch2::SoftwareTransforms);
    if(transformPoints)
        this->validateMVP();

    const bool buffer2d = hasBits(this->batchHints, GLRenderBatch2::TwoDimension);
    const std::size_t vertexSize = buffer2d ? VERTEX_SIZE_2D : VERTEX_SIZE_3D;

    addVertexFn addVertex;
    code = getAddVertexFunction(&addVertex, size, !!texCoords, this->batchHints);
    TE_CHECKRETURN_CODE(code);

    const std::size_t numTriangles = (count / size) - 2;

    const auto *vertexCoordsPtr = reinterpret_cast<const uint8_t *>(vertexCoords);
    const auto *texCoordsPtr = reinterpret_cast<const uint8_t *>(texCoords);

    vertexCoordsPtr += 2 * vStride;
    texCoordsPtr += 2 * tcStride;

    bool(*isDegenerate)(const float *, const std::size_t) NOTHROWS;
    if(size == 2u)
        isDegenerate = isDegenerate2d<float>;
    else if(size == 3u)
        isDegenerate = isDegenerate3d<float>;
    else
        return TE_IllegalState;

    bool isFirstTriangle = true;
    for (std::size_t i = 0u; i < numTriangles; i++) {
        if (isDegenerate(reinterpret_cast<const float *>((vertexCoordsPtr - (2*vStride)) + (i * vStride)), vStride)) {
            isFirstTriangle = true;
            vertexCoordsPtr += vStride;
            texCoordsPtr += tcStride;
            continue;
        }

        if (renderBuffer.remaining() < (vertexSize * 3) ||
            indexBuffer.remaining()/2u < 3) {

            const int texId = texCoords ? texUnitIdxToTexId[texUnitIdx] : 0;

            code = flush();
            TE_CHECKBREAK_CODE(code);

            isFirstTriangle = true;
            if(texId) {
                code = bindTexture(&texUnitIdx, texId);
                TE_CHECKBREAK_CODE(code);
                (*(float *)(((unsigned char *)&vertexConst) + 4)) = (float)texUnitIdx;
            }
        }

        // if this is the first triangle being written to the vertex buffer then
        // we will need to pre-fill it with the last two vertices
        if (isFirstTriangle) {
            code = addVertex(renderBuffer,
                reinterpret_cast<const float *>(vertexCoordsPtr - (2u*vStride)),
                reinterpret_cast<const float *>(texCoordsPtr - (2u*tcStride)),
                vertexConst,
                modelViewProjection);
            TE_CHECKBREAK_CODE(code);

            code = addVertex(renderBuffer,
                reinterpret_cast<const float *>(vertexCoordsPtr - vStride),
                reinterpret_cast<const float *>(texCoordsPtr - tcStride),
                vertexConst,
                modelViewProjection);
            TE_CHECKBREAK_CODE(code);

            // update the index offset
            indexOff = renderBuffer.position() / vertexSize;

            isFirstTriangle = false;
        }

        // add current vertex
        code = addVertex(renderBuffer, reinterpret_cast<const float *>(vertexCoordsPtr), reinterpret_cast<const float *>(texCoordsPtr), vertexConst, modelViewProjection);
        TE_CHECKBREAK_CODE(code);

        vertexCoordsPtr += vStride;
        texCoordsPtr += tcStride;

        // indices
        code = indexBuffer.put(static_cast<uint16_t>(indexOff - 2u));
        TE_CHECKBREAK_CODE(code);
        code = indexBuffer.put(static_cast<uint16_t>(indexOff - 1u));
        TE_CHECKBREAK_CODE(code);
        code = indexBuffer.put(static_cast<uint16_t>(indexOff));
        TE_CHECKBREAK_CODE(code);

        indexOff++;
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}


TAKErr GLRenderBatch2::addIndexedTriangleStripImpl(const std::size_t size, const std::size_t vStride, const float *vertexCoords, std::size_t count, const unsigned short *indices, std::size_t indexCount, const std::size_t tcStride, const float *texCoords, int texUnitIdx, float r, float g, float b, float a) NOTHROWS
{
    TAKErr code(TE_Ok);

    const bool transformPoints = hasBits(this->batchHints, GLRenderBatch2::SoftwareTransforms);
    if(transformPoints)
        this->validateMVP();

    const bool buffer2d = hasBits(this->batchHints, GLRenderBatch2::TwoDimension);
    const std::size_t vertexSize = buffer2d ? VERTEX_SIZE_2D : VERTEX_SIZE_3D;

    addVertexFn addVertex;
    code = getAddVertexFunction(&addVertex, size, !!texCoords, this->batchHints);
    TE_CHECKRETURN_CODE(code);

    std::size_t indexOff = renderBuffer.position() / vertexSize;

    if (renderBuffer.remaining() < ((count/size) * vertexSize) ||
        indexBuffer.remaining()/2u < ((indexCount-2) * 3u)) {

        const int texId = texCoords ? texUnitIdxToTexId[texUnitIdx] : 0;

        code = flush();
        TE_CHECKRETURN_CODE(code);

        indexOff = 0;
        if(texId) {
            code = bindTexture(&texUnitIdx, texId);
            TE_CHECKRETURN_CODE(code);
        }
    }

    uint64_t vertexConst;
    ((unsigned char *)&vertexConst)[0] = (unsigned char)(r*255.0f);
    ((unsigned char *)&vertexConst)[1] = (unsigned char)(g*255.0f);
    ((unsigned char *)&vertexConst)[2] = (unsigned char)(b*255.0f);
    ((unsigned char *)&vertexConst)[3] = (unsigned char)(a*255.0f);
    (*(float *)(((unsigned char *)&vertexConst) + 4)) = (float)texUnitIdx;

    const auto *vertexCoordsPtr = reinterpret_cast<const uint8_t *>(vertexCoords);
    const auto *texCoordsPtr = reinterpret_cast<const uint8_t *>(texCoords);

    const std::size_t numVerts = count / size;
    for (std::size_t i = 0u; i < numVerts; i++) {
        // vertex
        code = addVertex(renderBuffer,
            reinterpret_cast<const float *>(vertexCoordsPtr),
            reinterpret_cast<const float *>(texCoordsPtr),
            vertexConst,
            modelViewProjection);
        TE_CHECKBREAK_CODE(code);

        vertexCoordsPtr += vStride;
        texCoordsPtr += tcStride;
    }

    // advance to the third index
    indices += 2;

    const std::size_t numTriangles = indexCount - 2;
    for (std::size_t i = 0; i < numTriangles; i++) {
        if (isDegenerate1d(indices + i - 2, 1u)) {
            continue;
        }

        code = indexBuffer.put(static_cast<uint16_t>(indexOff + indices[i - 2]));
        TE_CHECKBREAK_CODE(code);
        code = indexBuffer.put(static_cast<uint16_t>(indexOff + indices[i - 1]));
        TE_CHECKBREAK_CODE(code);
        code = indexBuffer.put(static_cast<uint16_t>(indexOff + indices[i]));
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}


TAKErr GLRenderBatch2::addTriangleFanImpl(const std::size_t size, const std::size_t vStride, const float *vertexCoords, std::size_t count, const std::size_t tcStride, const float *texCoords, int texUnitIdx, float r, float g, float b, float a) NOTHROWS
{
    TAKErr code(TE_Ok);

    std::size_t indexOff = 0;
    std::size_t firstOff = 0;

    uint64_t vertexConst;
    ((unsigned char *)&vertexConst)[0] = (unsigned char)(r*255.0f);
    ((unsigned char *)&vertexConst)[1] = (unsigned char)(g*255.0f);
    ((unsigned char *)&vertexConst)[2] = (unsigned char)(b*255.0f);
    ((unsigned char *)&vertexConst)[3] = (unsigned char)(a*255.0f);
    (*(float *)(((unsigned char *)&vertexConst) + 4)) = (float)texUnitIdx;

    const bool transformPoints = hasBits(this->batchHints, GLRenderBatch2::SoftwareTransforms);
    if(transformPoints)
        this->validateMVP();

    const bool buffer2d = hasBits(this->batchHints, GLRenderBatch2::TwoDimension);
    const std::size_t vertexSize = buffer2d ? VERTEX_SIZE_2D : VERTEX_SIZE_3D;

    addVertexFn addVertex;
    code = getAddVertexFunction(&addVertex, size, !!texCoords, this->batchHints);
    TE_CHECKRETURN_CODE(code);

    const std::size_t numTriangles = (count / size) - 2;

    const float *vertexCoords0 = vertexCoords;
    const float *texCoords0 = texCoords;

    const auto *vertexCoordsPtr = reinterpret_cast<const uint8_t *>(vertexCoords);
    const auto *texCoordsPtr = reinterpret_cast<const uint8_t *>(texCoords);

    vertexCoordsPtr += 2 * vStride;
    texCoordsPtr += 2 * tcStride;

    bool isFirstTriangle = true;
    for (std::size_t i = 0; i < numTriangles; i++) {
        if (renderBuffer.remaining() < (vertexSize * 3) ||
            indexBuffer.remaining()/2u < 3) {

            // flush the buffer, taking care to record and rebind the texture
            // ID.
            const int texId = texCoords ? texUnitIdxToTexId[texUnitIdx] : 0;
            code = flush();
            TE_CHECKBREAK_CODE(code);

            isFirstTriangle = true;
            if(texId) {
                code = bindTexture(&texUnitIdx, texId);
                TE_CHECKBREAK_CODE(code);
                // update vertex const data
                (*(float *)(((unsigned char *)&vertexConst) + 4)) = (float)texUnitIdx;
            }
        }

        // if this is the first triangle being written to the vertex buffer then
        // we will need to pre-fill it with the last two vertices
        if (isFirstTriangle) {
            // first vertex for triangle fan
            firstOff = renderBuffer.position() / vertexSize;
            code = addVertex(renderBuffer,
                vertexCoords0,
                texCoords0,
                vertexConst,
                modelViewProjection);
            TE_CHECKBREAK_CODE(code);

            // last vertex used
            code = addVertex(renderBuffer,
                reinterpret_cast<const float *>(vertexCoordsPtr - vStride),
                reinterpret_cast<const float *>(texCoordsPtr - tcStride),
                vertexConst,
                modelViewProjection);
            TE_CHECKBREAK_CODE(code);

            // update the index offset
            indexOff = renderBuffer.position() / vertexSize;

            isFirstTriangle = false;
        }

        // add current vertex
        code = addVertex(renderBuffer, reinterpret_cast<const float *>(vertexCoordsPtr), reinterpret_cast<const float *>(texCoordsPtr), vertexConst, modelViewProjection);
        TE_CHECKBREAK_CODE(code);

        vertexCoordsPtr += vStride;
        texCoordsPtr += tcStride;

        // indices
        code = indexBuffer.put(static_cast<uint16_t>(firstOff));
        TE_CHECKBREAK_CODE(code);
        code = indexBuffer.put(static_cast<uint16_t>(indexOff - 1u));
        TE_CHECKBREAK_CODE(code);
        code = indexBuffer.put(static_cast<uint16_t>(indexOff));
        TE_CHECKBREAK_CODE(code);

        indexOff++;
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr GLRenderBatch2::addIndexedTriangleFanImpl(const std::size_t size, const std::size_t vStride, const float *vertexCoords, const std::size_t count, const unsigned short *indices, const std::size_t indexCount, const std::size_t tcStride, const float *texCoords, int texUnitIdx, const float r, const float g, const float b, const float a) NOTHROWS
{
    TAKErr code(TE_Ok);

    if(indexCount <= 2u)
        return TE_Ok;

    const bool transformPoints = hasBits(this->batchHints, GLRenderBatch2::SoftwareTransforms);
    if(transformPoints)
        this->validateMVP();

    const bool buffer2d = hasBits(this->batchHints, GLRenderBatch2::TwoDimension);
    const std::size_t vertexSize = buffer2d ? VERTEX_SIZE_2D : VERTEX_SIZE_3D;

    addVertexFn addVertex;
    code = getAddVertexFunction(&addVertex, size, !!texCoords, this->batchHints);
    TE_CHECKRETURN_CODE(code);

    std::size_t indexOff = renderBuffer.position() / vertexSize;

    if (renderBuffer.remaining() < ((count/size) * vertexSize) ||
        indexBuffer.remaining()/2u < ((indexCount - 2u) * 3u)) {

        const int texId = texCoords ? texUnitIdxToTexId[texUnitIdx] : 0;

        code = flush();
        TE_CHECKRETURN_CODE(code);

        indexOff = 0;
        if(texId) {
            code = bindTexture(&texUnitIdx, texId);
            TE_CHECKRETURN_CODE(code);
        }
    }

    uint64_t vertexConst;
    ((unsigned char *)&vertexConst)[0] = (unsigned char)(r*255.0f);
    ((unsigned char *)&vertexConst)[1] = (unsigned char)(g*255.0f);
    ((unsigned char *)&vertexConst)[2] = (unsigned char)(b*255.0f);
    ((unsigned char *)&vertexConst)[3] = (unsigned char)(a*255.0f);
    (*(float *)(((unsigned char *)&vertexConst) + 4)) = (float)texUnitIdx;

    const auto *vertexCoordsPtr = reinterpret_cast<const uint8_t *>(vertexCoords);
    const auto *texCoordsPtr = reinterpret_cast<const uint8_t *>(texCoords);

    const std::size_t numVerts = count / size;
    for (std::size_t i = 0u; i < numVerts; i++) {
        // vertex
        code = addVertex(renderBuffer,
            reinterpret_cast<const float *>(vertexCoordsPtr),
            reinterpret_cast<const float *>(texCoordsPtr),
            vertexConst, modelViewProjection);
        TE_CHECKBREAK_CODE(code);

        vertexCoordsPtr += vStride;
        texCoordsPtr += tcStride;
    }

    // advance to the third index
    indices += 2;

    const std::size_t numTriangles = indexCount - 2u;
    for (std::size_t i = 0; i < numTriangles; i++) {
        code = indexBuffer.put(static_cast<uint16_t>(indexOff + indices[0]));
        TE_CHECKBREAK_CODE(code);
        code = indexBuffer.put(static_cast<uint16_t>(indexOff + indices[i - 1u]));
        TE_CHECKBREAK_CODE(code);
        code = indexBuffer.put(static_cast<uint16_t>(indexOff + indices[i]));
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}

/*********************************************************************/
// Public API for Batch additions

TAKErr GLRenderBatch2::addLines(const std::size_t size, const std::size_t vStride, const float *lines, std::size_t lineCount, float width, float r, float g, float b, float a) NOTHROWS
{
    TAKErr code(TE_Ok);

    uint64_t vertexConst;
    ((unsigned char *)&vertexConst)[0] = (unsigned char)(r*255.0f);
    ((unsigned char *)&vertexConst)[1] = (unsigned char)(g*255.0f);
    ((unsigned char *)&vertexConst)[2] = (unsigned char)(b*255.0f);
    ((unsigned char *)&vertexConst)[3] = (unsigned char)(a*255.0f);
    (*(float*)(((unsigned char *)&vertexConst) + 4)) = (float)-1; // untextured

    code = addLinesImpl(size, width, lineCount, vStride, lines, 2u, vertexConst);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr GLRenderBatch2::addLineStrip(const std::size_t size, const std::size_t vStride, const float *linestrip, std::size_t count, float width, float r, float g, float b, float a) NOTHROWS
{
    TAKErr code(TE_Ok);

    uint64_t vertexConst;
    ((unsigned char *)&vertexConst)[0] = (unsigned char)(r*255.0f);
    ((unsigned char *)&vertexConst)[1] = (unsigned char)(g*255.0f);
    ((unsigned char *)&vertexConst)[2] = (unsigned char)(b*255.0f);
    ((unsigned char *)&vertexConst)[3] = (unsigned char)(a*255.0f);
    (*(float*)(((unsigned char *)&vertexConst) + 4)) = (float)-1; // untextured


    const std::size_t numLines = (count / size) - 1;

    code = addLinesImpl(size, width, numLines, vStride, linestrip, 1u, vertexConst);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr GLRenderBatch2::addLineLoop(const std::size_t size, const std::size_t vStride, const float *lineLoop, std::size_t count, float width, float r, float g, float b, float a) NOTHROWS
{
    TAKErr code(TE_Ok);

    if(size != 3u && size != 2u)
        return TE_Unsupported;

    if(!hasBits(this->batchHints, GLRenderBatch2::SoftwareTransforms)) {
        code = this->flush();
        TE_CHECKRETURN_CODE(code);

        this->batchHints |= GLRenderBatch2::SoftwareTransforms;
    }

    if(!hasBits(this->batchHints, GLRenderBatch2::SoftwareTransforms))
        return TE_IllegalState;

    this->validateMVP();

    const bool buffer2d = hasBits(this->batchHints, GLRenderBatch2::TwoDimension);
    const std::size_t vertexSize = buffer2d ? VERTEX_SIZE_2D : VERTEX_SIZE_3D;

    uint64_t vertexConst;
    ((unsigned char *)&vertexConst)[0] = (unsigned char)(r*255.0f);
    ((unsigned char *)&vertexConst)[1] = (unsigned char)(g*255.0f);
    ((unsigned char *)&vertexConst)[2] = (unsigned char)(b*255.0f);
    ((unsigned char *)&vertexConst)[3] = (unsigned char)(a*255.0f);
    (*(float*)(((unsigned char *)&vertexConst) + 4)) = (float)-1; // untextured

    const std::size_t numLines = (count / size) - 1;

    code = addLinesImpl(size, width, numLines, vStride, lineLoop, 1u, vertexConst);
    TE_CHECKRETURN_CODE(code);

    if (numLines > 0) {
        if (renderBuffer.remaining() < (vertexSize * 4) ||
            indexBuffer.remaining()/2u < VERTICES_PER_SPRITE) {

            code = flush();
            TE_CHECKRETURN_CODE(code);
        }

        // last
        float x0, y0, z0;
        const auto *last = reinterpret_cast<const float *>(reinterpret_cast<const uint8_t *>(lineLoop + (numLines*vStride)));
        x0 = last[0];
        y0 = last[1];
        if(size == 3u)
            z0 = last[2];
        else
            z0 = 0;

        // first
        float x1, y1, z1;
        x1 = lineLoop[0];
        y1 = lineLoop[1];
        if(size == 3u)
            z1 = lineLoop[2];
        else
            z1 = 0;

        if(buffer2d) {
            transform2d_2d(&x0, &y0, modelViewProjection, x0, y0);
            transform2d_2d(&x1, &y1, modelViewProjection, x1, y1);

            addLine2d_2dImpl(renderBuffer,
                             indexBuffer,
                             renderBuffer.position() / vertexSize,
                             vertexConst,
                             width / viewportHeight,
                             width / viewportWidth,
                             x0, y0,
                             x1, y1);
        } else {
            transform3d(&x0, &y0, &z0, modelViewProjection, x0, y0, z0);
            transform3d(&x1, &y1, &z1, modelViewProjection, x1, y1, z1);

            addLine3dImpl(renderBuffer,
                          indexBuffer,
                          renderBuffer.position() / vertexSize,
                          vertexConst,
                          width / viewportHeight,
                          width / viewportWidth,
                          x0, y0, z0,
                          x1, y1, z1);
        }
    }

    return code;
}

TAKErr GLRenderBatch2::addTriangles(const std::size_t size, const std::size_t vStride, const float *vertexCoords, std::size_t count, float r, float g, float b, float a) NOTHROWS
{
    TAKErr code(TE_Ok);

    code = addTrianglesImpl(size, vStride, vertexCoords, count, 0u, nullptr, -1, r, g, b, a);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr GLRenderBatch2::addTriangles(const std::size_t size, const std::size_t vStride, const float *vertexCoords, std::size_t count, const unsigned short *indices, std::size_t indexCount, float r, float g, float b, float a) NOTHROWS
{
    TAKErr code(TE_Ok);

    if (indexCount > indexBuffer.limit()/2u) {
        Logger_log(TELL_Error, "Too many indices");
        return TE_InvalidArg;
    }
    const bool buffer2d = hasBits(this->batchHints, GLRenderBatch2::TwoDimension);
    const std::size_t vertexSize = buffer2d ? VERTEX_SIZE_2D : VERTEX_SIZE_3D;
    const std::size_t requiredVertexSize = (count / size * vertexSize);
    if (requiredVertexSize > renderBuffer.limit()) {
        Logger_log(TELL_Error, "Too many vertices");
        return TE_InvalidArg;
    }

    if (renderBuffer.remaining() < requiredVertexSize || indexBuffer.remaining()/2u < indexCount) {
        code = flush();
        TE_CHECKRETURN_CODE(code);
    }

    code = addIndexedTrianglesImpl(size, vStride, vertexCoords, count, indices, indexCount, 0u, nullptr, -1, r, g, b, a);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr GLRenderBatch2::addTriangleStrip(const std::size_t size, const std::size_t vStride, const float *vertexCoords, std::size_t count, float r, float g, float b, float a) NOTHROWS
{
    return addTriangleStripImpl(size, vStride, vertexCoords, count, 0u, nullptr, -1, r, g, b, a);
}

TAKErr GLRenderBatch2::addTriangleStrip(const std::size_t size, const std::size_t vStride, const float *vertexCoords, std::size_t count, const unsigned short *indices, std::size_t indexCount, float r, float g, float b, float a) NOTHROWS
{
    return addIndexedTriangleStripImpl(size, vStride, vertexCoords, count, indices, indexCount, 0u, nullptr, -1, r, g, b, a);
}

TAKErr GLRenderBatch2::addTrianglesSprite(const std::size_t size, const std::size_t vStride, const float *vertexCoords,
    const std::size_t count, const std::size_t tcStride, const float *texCoords, const int textureId,
    const float r, const float g, const float b, const float a) NOTHROWS
{
    TAKErr code(TE_Ok);

    int textureUnitIdx;
    code = bindTexture(&textureUnitIdx, textureId);
    TE_CHECKRETURN_CODE(code);
    code = addTrianglesImpl(size, vStride, vertexCoords, count, tcStride, texCoords, textureUnitIdx, r, g, b, a);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr GLRenderBatch2::addTrianglesSprite(const std::size_t size, const std::size_t vStride, const float *vertexCoords,
    const std::size_t count,
    const unsigned short *indices,
    const std::size_t indexCount,
    const std::size_t tcStride,
    const float *texCoords,
    const int textureId,
    const float r, const float g, const float b, const float a) NOTHROWS
{
    TAKErr code(TE_Ok);

    const std::size_t requiredIndices = indexCount;
    if (requiredIndices > indexBuffer.limit()/2u) {
        Logger_log(TELL_Error, "too many indices specified");
        return TE_InvalidArg;
    }
    const bool buffer2d = hasBits(this->batchHints, GLRenderBatch2::TwoDimension);
    const std::size_t vertexSize = buffer2d ? VERTEX_SIZE_2D : VERTEX_SIZE_3D;
    const std::size_t requiredVertexSize = (count / size * vertexSize);
    if (requiredVertexSize > renderBuffer.limit()) {
        Logger_log(TELL_Error, "too many vertices specified");
        return TE_InvalidArg;
    }

    if (renderBuffer.remaining() < requiredVertexSize || indexBuffer.remaining()/2u < requiredIndices) {
        code = flush();
        TE_CHECKRETURN_CODE(code);
    }

    int textureUnitIdx;
    code = bindTexture(&textureUnitIdx, textureId);

    TE_CHECKRETURN_CODE(code);
    code = addIndexedTrianglesImpl(size, vStride, vertexCoords, count, indices, indexCount, tcStride, texCoords, textureUnitIdx, r, g, b, a);
    TE_CHECKRETURN_CODE(code);
    return code;
}

TAKErr GLRenderBatch2::addTriangleStripSprite(const std::size_t size, const std::size_t vStride, const float *vertexCoords, const std::size_t count,
    const std::size_t tcStride, const float *texCoords,
    const int textureId,
    const float r, const float g, const float b, const float a) NOTHROWS
{
    TAKErr code(TE_Ok);
    int textureUnitIdx;
    code = bindTexture(&textureUnitIdx, textureId);
    TE_CHECKRETURN_CODE(code);

    code = addTriangleStripImpl(size, vStride, vertexCoords, count, tcStride, texCoords, textureUnitIdx, r, g, b, a);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr GLRenderBatch2::addTriangleStripSprite(const std::size_t size, const std::size_t vStride, const float *vertexCoords,
    const std::size_t count,
    const unsigned short *indices,
    const std::size_t indexCount,
    const std::size_t tcStride,
    const float *texCoords,
    const int textureId,
    const float r, const float g, const float b, const float a) NOTHROWS
{
    TAKErr code(TE_Ok);

    const std::size_t requiredIndices = (indexCount - 2) * 3;
    if (requiredIndices > indexBuffer.limit()/2u) {
        Logger_log(TELL_Error, "Too many indices");
        return TE_InvalidArg;
    }
    const bool buffer2d = hasBits(this->batchHints, GLRenderBatch2::TwoDimension);
    const std::size_t vertexSize = buffer2d ? VERTEX_SIZE_2D : VERTEX_SIZE_3D;
    const std::size_t requiredVertexSize = (count / size * vertexSize);
    if (requiredVertexSize > renderBuffer.limit()) {
        Logger_log(TELL_Error, "Too many vertices");
        return TE_InvalidArg;
    }

    if (renderBuffer.remaining() < requiredVertexSize || indexBuffer.remaining()/2u < requiredIndices) {
        code = flush();
        TE_CHECKRETURN_CODE(code);
    }

    int textureUnitIdx;
    code = bindTexture(&textureUnitIdx, textureId);
    TE_CHECKRETURN_CODE(code);

    code = addIndexedTriangleStripImpl(size, vStride, vertexCoords, count, indices, indexCount, tcStride, texCoords, textureUnitIdx, r, g, b, a);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr GLRenderBatch2::addTriangleFan(const std::size_t size, const std::size_t vStride, const float *vertexCoords, const std::size_t count, const float r, const float g, const float b, float a) NOTHROWS
{
    return addTriangleFanImpl(size, vStride, vertexCoords, count, 0u, nullptr, -1, r, g, b, a);
}

TAKErr GLRenderBatch2::addTriangleFan(const std::size_t size, const std::size_t vStride, const float *vertexCoords, const std::size_t count, const unsigned short *indices, std::size_t indexCount, float r, float g, float b, float a) NOTHROWS
{
    TAKErr code(TE_Ok);

    const std::size_t requiredIndices = (count - 2) * 3;
    if (requiredIndices > indexBuffer.limit()/2u) {
        Logger_log(TELL_Error, "Too many indices");
        return TE_InvalidArg;
    }
    const bool buffer2d = hasBits(this->batchHints, GLRenderBatch2::TwoDimension);
    const std::size_t vertexSize = buffer2d ? VERTEX_SIZE_2D : VERTEX_SIZE_3D;
    const std::size_t requiredVertexSize = (count / size * vertexSize);
    if (requiredVertexSize > renderBuffer.limit()) {
        Logger_log(TELL_Error, "Too many vertices");
        return TE_InvalidArg;
    }

    if (renderBuffer.remaining() < requiredVertexSize || indexBuffer.remaining()/2u < requiredIndices) {
        code = flush();
        TE_CHECKRETURN_CODE(code);
    }

    code = addIndexedTriangleFanImpl(size, vStride, vertexCoords, count, indices, indexCount, 0u, nullptr, -1, r, g, b, a);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr GLRenderBatch2::addTriangleFanSprite(const std::size_t size, const std::size_t vStride, const float *vertexCoords, const std::size_t count,
    const std::size_t tcStride, const float *texCoords, const int textureId,
    const float r, const float g, const float b, const float a) NOTHROWS
{
    TAKErr code(TE_Ok);

    int textureUnitIdx;
    code = bindTexture(&textureUnitIdx, textureId);
    TE_CHECKRETURN_CODE(code);

    code = addTriangleFanImpl(size, vStride, vertexCoords, count, tcStride, texCoords, textureUnitIdx, r, g, b, a);
    TE_CHECKRETURN_CODE(code);

    return code;
}


TAKErr GLRenderBatch2::addTriangleFanSprite(const std::size_t size, const std::size_t vStride, const float *vertexCoords,
    const std::size_t count,
    const unsigned short *indices,
    const std::size_t indexCount,
    const std::size_t tcStride, const float *texCoords,
    const int textureId,
    const float r, const float g, const float b, const float a) NOTHROWS
{
    TAKErr code(TE_Ok);

    const std::size_t requiredIndices = (indexCount - 2) * 3;
    if (requiredIndices > indexBuffer.limit()/2u) {
        Logger_log(TELL_Error, "Too many indices");
        return TE_InvalidArg;
    }
    const bool buffer2d = hasBits(this->batchHints, GLRenderBatch2::TwoDimension);
    const std::size_t vertexSize = buffer2d ? VERTEX_SIZE_2D : VERTEX_SIZE_3D;
    const std::size_t requiredVertexSize = (count / size * vertexSize);
    if (requiredVertexSize > renderBuffer.limit()) {
        Logger_log(TELL_Error, "Too many vertices");
        return TE_InvalidArg;
    }

    if (renderBuffer.remaining() < requiredVertexSize || indexBuffer.remaining()/2u < requiredIndices) {
        code = flush();
        TE_CHECKRETURN_CODE(code);
    }

    int textureUnitIdx;
    code = bindTexture(&textureUnitIdx, textureId);
    TE_CHECKRETURN_CODE(code);

    code = addIndexedTriangleFanImpl(size, vStride, vertexCoords, count, indices, indexCount, tcStride, texCoords, textureUnitIdx, r, g, b, a);
    TE_CHECKRETURN_CODE(code);

    return code;
}

/*********************************************************************/
// Other public API

std::size_t TAK::Engine::Renderer::GLRenderBatch2_getBatchTextureUnitLimit() NOTHROWS
{
#ifndef __ANDROID__
    std::size_t batchTextureUnitLimit;
    GLMapRenderGlobals_getTextureUnitLimit(&batchTextureUnitLimit);
    return std::min((std::size_t)batchTextureUnitLimit, static_cast<std::size_t>(INTERNAL_TEXTURE_UNIT_LIMIT));
#else
    static std::size_t glTextureUnitLimit = glMaxTextureUnits();
    return std::min(std::min(GLRenderBatch_textureUnitLimit, glTextureUnitLimit), static_cast<std::size_t>(INTERNAL_TEXTURE_UNIT_LIMIT));
#endif
}

void TAK::Engine::Renderer::GLRenderBatch2_setBatchTextureUnitLimit(const std::size_t limit) NOTHROWS
{
#ifndef __ANDROID__
    GLMapRenderGlobals_setTextureUnitLimit(limit);
#else
    GLRenderBatch_textureUnitLimit = limit;
#endif
}

/*********************************************************************/
// Other private utilties

TAKErr GLRenderBatch2::bindTexture(int *value, const int textureId) NOTHROWS
{
    TAKErr code(TE_Ok);

    // if the Untextured hint was specified, but the client began
    // texturing, end the current batch and restart with the
    // textured program
    if (hasBits(this->batchHints, GLRenderBatch2::Untextured)) {
        code = this->flush();
        TE_CHECKRETURN_CODE(code);

        this->batchHints &= ~GLRenderBatch2::Untextured;
    }

    int * const texUnitTexIds = texUnitIdxToTexId.get();

    for (int i = numActiveTexUnits; i > 0; i--) {
        if (texUnitTexIds[i-1u] == textureId) {
            *value = i-1;
            return code;
        }
    }

    // if all available texture units are bound we need to flush the batch
    // before we can bind the new texture
    if (static_cast<std::size_t>(numActiveTexUnits) >= GLRenderBatch2_getBatchTextureUnitLimit()) {
        code = flush();
        TE_CHECKRETURN_CODE(code);
    }

    int retval = numActiveTexUnits;
#ifndef __ANDROID__
    GLES20FixedPipeline::getInstance()->glActiveTexture(GL_TEXTURE_UNITS[retval]);
#else
    glActiveTexture(GL_TEXTURE_UNITS[retval]);
#endif
    glBindTexture(GL_TEXTURE_2D, textureId);
    texUnitTexIds[numActiveTexUnits++] = textureId;

    *value = retval;
    return code;
}

void GLRenderBatch2::validateMVP() NOTHROWS
{
    if(!mvpDirty)
        return;

    const double m00 = projection.pointer[0];
    const double m10 = projection.pointer[1];
    const double m20 = projection.pointer[2];
    const double m30 = projection.pointer[3];
    const double m01 = projection.pointer[4];
    const double m11 = projection.pointer[5];
    const double m21 = projection.pointer[6];
    const double m31 = projection.pointer[7];
    const double m02 = projection.pointer[8];
    const double m12 = projection.pointer[9];
    const double m22 = projection.pointer[10];
    const double m32 = projection.pointer[11];
    const double m03 = projection.pointer[12];
    const double m13 = projection.pointer[13];
    const double m23 = projection.pointer[14];
    const double m33 = projection.pointer[15];

    const double tm00 = modelView.pointer[0];
    const double tm10 = modelView.pointer[1];
    const double tm20 = modelView.pointer[2];
    const double tm30 = modelView.pointer[3];
    const double tm01 = modelView.pointer[4];
    const double tm11 = modelView.pointer[5];
    const double tm21 = modelView.pointer[6];
    const double tm31 = modelView.pointer[7];
    const double tm02 = modelView.pointer[8];
    const double tm12 = modelView.pointer[9];
    const double tm22 = modelView.pointer[10];
    const double tm32 = modelView.pointer[11];
    const double tm03 = modelView.pointer[12];
    const double tm13 = modelView.pointer[13];
    const double tm23 = modelView.pointer[14];
    const double tm33 = modelView.pointer[15];

    // XXX - concatenate
    modelViewProjection[0] = static_cast<float>(m00*tm00 + m01*tm10 + m02*tm20 + m03*tm30);
    modelViewProjection[1] = static_cast<float>(m10*tm00 + m11*tm10 + m12*tm20 + m13*tm30);
    modelViewProjection[2] = static_cast<float>(m20*tm00 + m21*tm10 + m22*tm20 + m23*tm30);
    modelViewProjection[3] = static_cast<float>(m30*tm00 + m31*tm10 + m32*tm20 + m33*tm30);
    modelViewProjection[4] = static_cast<float>(m00*tm01 + m01*tm11 + m02*tm21 + m03*tm31);
    modelViewProjection[5] = static_cast<float>(m10*tm01 + m11*tm11 + m12*tm21 + m13*tm31);
    modelViewProjection[6] = static_cast<float>(m20*tm01 + m21*tm11 + m22*tm21 + m23*tm31);
    modelViewProjection[7] = static_cast<float>(m30*tm01 + m31*tm11 + m32*tm21 + m33*tm31);
    modelViewProjection[8] = static_cast<float>(m00*tm02 + m01*tm12 + m02*tm22 + m03*tm32);
    modelViewProjection[9] = static_cast<float>(m10*tm02 + m11*tm12 + m12*tm22 + m13*tm32);
    modelViewProjection[10] = static_cast<float>(m20*tm02 + m21*tm12 + m22*tm22 + m23*tm32);
    modelViewProjection[11] = static_cast<float>(m30*tm02 + m31*tm12 + m32*tm22 + m33*tm32);
    modelViewProjection[12] = static_cast<float>(m00*tm03 + m01*tm13 + m02*tm23 + m03*tm33);
    modelViewProjection[13] = static_cast<float>(m10*tm03 + m11*tm13 + m12*tm23 + m13*tm33);
    modelViewProjection[14] = static_cast<float>(m20*tm03 + m21*tm13 + m22*tm23 + m23*tm33);
    modelViewProjection[15] = static_cast<float>(m30*tm03 + m31*tm13 + m32*tm23 + m33*tm33);

    mvpDirty = false;
}

TAKErr GLRenderBatch2::batchProgramInit(batch_render_program_t *program, const bool textured, const char *vertShaderSrc, const char *fragShaderSrc) NOTHROWS
{
    TAKErr code(TE_Ok);

    ShaderProgram p;
    code = GLSLUtil_createProgram(&p, vertShaderSrc, fragShaderSrc);
    TE_CHECKRETURN_CODE(code);

    program->handle = p.program;
    glUseProgram(p.program);

    program->uProjectionHandle = glGetUniformLocation(program->handle, "uProjection");
    program->uModelViewHandle = glGetUniformLocation(program->handle, "uModelView");
    program->aVertexCoordsHandle = glGetAttribLocation(program->handle, "aVertexCoords");
    program->aColorHandle = glGetAttribLocation(program->handle, "aColor");
    if(textured) {
        program->aTextureCoordsHandle = glGetAttribLocation(program->handle, "aTextureCoords");
        program->aTexUnitHandle = glGetAttribLocation(program->handle, "aTexUnit");

        const std::size_t numTextureUnitHandles = GLRenderBatch2_getBatchTextureUnitLimit();
        for (std::size_t i = 0u; i < numTextureUnitHandles; i++) {
            StringBuilder sstream;
            sstream << "uTexture" << i;

            int uTextureHandle = glGetUniformLocation(program->handle, sstream.c_str());

            // set the texture unit uniform
            glUniform1i(uTextureHandle, GL_TEXTURE_UNITS[i] - GL_TEXTURE0);
        }
    }

    glDeleteShader(p.vertShader);
    glDeleteShader(p.fragShader);
    p.program = GL_FALSE;

    return code;
}

/******************************************************************************/

GLRenderBatch2::batch_render_program_t::batch_render_program_t(const std::size_t size_) NOTHROWS :
    size(size_),
    handle(0),
    uProjectionHandle(-1),
    uModelViewHandle(-1),
    aVertexCoordsHandle(-1),
    aColorHandle(-1),
    aTextureCoordsHandle(-1),
    aTexUnitHandle(-1)
{}

/******************************************************************************/

GLRenderBatch2::MatrixStack::MatrixStack(const std::size_t count_) NOTHROWS :
    count(count_),
    buffer(new float[count_*16u], Memory_array_deleter_const<float>),
    pointer(buffer.get())
{}

TAKErr GLRenderBatch2::MatrixStack::push() NOTHROWS
{
    TAKErr code(TE_Ok);
    const std::size_t idx = (pointer-buffer.get()) / 16u;
    if(idx >= (count-1))
        return TE_BadIndex;
    memcpy(pointer+16u, pointer, 16u*sizeof(float));
    pointer += 16u;
    return code;
}

TAKErr GLRenderBatch2::MatrixStack::pop() NOTHROWS
{
    TAKErr code(TE_Ok);
    const std::size_t idx = (pointer-buffer.get()) / 16u;
    if(!idx)
        return TE_BadIndex;
    pointer -= 16u;
    return code;
}

void GLRenderBatch2::MatrixStack::reset() NOTHROWS
{
    pointer = buffer.get();
}

/******************************************************************************/
namespace
{
    bool hasBits(int v, int m)
    {
        return !!(v&m);
    }

    template<typename T>
    bool isDegenerate1d(const T *triangle, const std::size_t stride)
    {
        const auto *trianglePtr = reinterpret_cast<const uint8_t *>(triangle);
        const T *a = triangle;
        const T *b = reinterpret_cast<const T *>(trianglePtr+stride);
        const T *c = reinterpret_cast<const T *>(trianglePtr+(2u*stride));

        return (*a == *b) || // A == B
               (*a == *c) || // A == C
               (*b == *c); // B == C
    }

    template<typename T>
    bool isDegenerate2d(const T *triangle, const std::size_t stride) NOTHROWS
    {
        const auto *trianglePtr = reinterpret_cast<const uint8_t *>(triangle);
        const T *a = triangle;
        const T *b = reinterpret_cast<const T *>(trianglePtr + stride);
        const T *c = reinterpret_cast<const T *>(trianglePtr + (2u * stride));

        return (a[0] == b[0] &&         // A == B
                a[1] == b[1]) ||
               (a[0] == c[0] &&       // A == C
                a[1] == c[1]) ||
               (b[0] == c[0] &&  // B == C
                b[1] == c[1]);
    }

    template<typename T>
    bool isDegenerate3d(const T *triangle, const std::size_t stride) NOTHROWS
    {
        const auto *trianglePtr = reinterpret_cast<const uint8_t *>(triangle);
        const T *a = triangle;
        const T *b = reinterpret_cast<const T *>(trianglePtr + stride);
        const T *c = reinterpret_cast<const T *>(trianglePtr + (2u * stride));

        return (a[0] == b[0] &&         // A == B
                a[1] == b[1] &&
                a[2] == b[2]) ||
               (a[0] == c[0] &&       // A == C
                a[1] == c[1] &&
                a[2] == c[2]) ||
               (b[0] == c[0] &&  // B == C
                b[1] == c[1] &&
                b[2] == c[2]);
    }

    TAKErr addLine2d_2dImpl(MemBuffer2 &pfRenderBuffer,
                            MemBuffer2 &indexBuffer,
                            const std::size_t indexOff,
                            const uint64_t vertexConst,
                            const float normalizedWidthX,
                            const float normalizedWidthY,
                            const float x0,
                            const float y0,
                            const float x1,
                            const float y1) NOTHROWS
    {
        /*
        * Consider the quad for the width expanded line x0,y0 -> x1,y1:
        *
        *             A
        * (x0,y0,z0) / \
        *           .   \
        *          / .   \
        *         D   .   \
        *          \   .   \
        *           \   .   \
        *            \   .   \
        *             \   .   \
        *              \   .   B
        *               \   . /
        *                \   . (x1,y1,z1)
        *                 \ /
        *                  C
        */

        const float dx = (x1 - x0);
        const float dy = (y1 - y0);

        const auto m = (float)sqrt(dx*dx + dy*dy);

        const float adjX = (dx / m)*normalizedWidthX;
        const float adjY = (dy / m)*normalizedWidthY;

        // XXX - line cap styles

#if 0
        // in addition to expanding the line width-wise, we will also lengthen it
        // by the radius to ensure overlap between consecutive segments
        const float Ax = (x0 - adjX) - adjY;
        const float Ay = (y0 - adjY) + adjX;
        const float Bx = (x1 + adjX) - adjY;
        const float By = (y1 + adjY) + adjX;
        const float Cx = (x1 + adjX) + adjY;
        const float Cy = (y1 + adjY) - adjX;
        const float Dx = (x0 - adjX) + adjY;
        const float Dy = (y0 - adjY) - adjX;
#else
        const float Ax = x0 - adjY;
        const float Ay = y0 + adjX;
        const float Bx = x1 - adjY;
        const float By = y1 + adjX;
        const float Cx = x1 + adjY;
        const float Cy = y1 - adjX;
        const float Dx = x0 + adjY;
        const float Dy = y0 - adjX;
#endif

        TAKErr code(TE_Ok);

        // vertices

        code = pfRenderBuffer.put(Ax); // vertex
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(Ay);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.skip(8u); // u,v texture (ignored)
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(vertexConst); // color + tex unit
        TE_CHECKRETURN_CODE(code);

        code = pfRenderBuffer.put(Bx); // vertex
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(By);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.skip(8u); // u,v texture (ignored)
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(vertexConst); // color + tex unit
        TE_CHECKRETURN_CODE(code);

        code = pfRenderBuffer.put(Cx); // vertex
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(Cy);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.skip(8u); // u,v texture (ignored)
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(vertexConst); // color + tex unit
        TE_CHECKRETURN_CODE(code);

        code = pfRenderBuffer.put(Dx); // vertex
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(Dy);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.skip(8u); // u,v texture (ignored)
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(vertexConst); // color + tex unit
        TE_CHECKRETURN_CODE(code);

        // indices

        code = indexBuffer.put(static_cast<uint16_t>(indexOff + 0)); // A
        TE_CHECKRETURN_CODE(code);
        code = indexBuffer.put(static_cast<uint16_t>(indexOff + 3)); // D
        TE_CHECKRETURN_CODE(code);
        code = indexBuffer.put(static_cast<uint16_t>(indexOff + 1)); // B
        TE_CHECKRETURN_CODE(code);

        code = indexBuffer.put(static_cast<uint16_t>(indexOff + 3)); // D
        TE_CHECKRETURN_CODE(code);
        code = indexBuffer.put(static_cast<uint16_t>(indexOff + 1)); // B
        TE_CHECKRETURN_CODE(code);
        code = indexBuffer.put(static_cast<uint16_t>(indexOff + 2)); // C
        TE_CHECKRETURN_CODE(code);

        return code;
    }

    void addLine2d_3dImpl(MemBuffer2 &pfRenderBuffer,
                       MemBuffer2 &indexBuffer,
                       const std::size_t indexOff,
                       const uint64_t vertexConst,
                       const float widthX,
                       const float widthY,
                       const float x0,
                       const float y0,
                       const float z0,
                       const float x1,
                       const float y1,
                       const float z1) NOTHROWS
    {
        addLine3dImpl(pfRenderBuffer,
                      indexBuffer,
                      indexOff,
                      vertexConst,
                      widthX, widthY,
                      x0, y0, 0,
                      x1, y1, 0);
    }

    TAKErr addLine3dImpl(MemBuffer2 &pfRenderBuffer,
                         MemBuffer2 &indexBuffer,
                         const std::size_t indexOff,
                         const uint64_t vertexConst,
                         const float normalizedWidthX,
                         const float normalizedWidthY,
                         const float x0,
                         const float y0,
                         const float z0,
                         const float x1,
                         const float y1,
                         const float z1) NOTHROWS
    {
        /*
        * Consider the quad for the width expanded line x0,y0 -> x1,y1:
        *
        *             A
        * (x0,y0,z0) / \
        *           .   \
        *          / .   \
        *         D   .   \
        *          \   .   \
        *           \   .   \
        *            \   .   \
        *             \   .   \
        *              \   .   B
        *               \   . /
        *                \   . (x1,y1,z1)
        *                 \ /
        *                  C
        */

        const float dx = (x1 - x0);
        const float dy = (y1 - y0);

        const auto m = (float)sqrt(dx*dx + dy*dy);

        const float adjX = (dx / m)*normalizedWidthX;
        const float adjY = (dy / m)*normalizedWidthY;

        // XXX - line cap styles

#if 0
        // in addition to expanding the line width-wise, we will also lengthen it
        // by the radius to ensure overlap between consecutive segments
        const float Ax = (x0 - adjX) - adjY;
        const float Ay = (y0 - adjY) + adjX;
        const float Bx = (x1 + adjX) - adjY;
        const float By = (y1 + adjY) + adjX;
        const float Cx = (x1 + adjX) + adjY;
        const float Cy = (y1 + adjY) - adjX;
        const float Dx = (x0 - adjX) + adjY;
        const float Dy = (y0 - adjY) - adjX;
#else
        const float Ax = x0 - adjY;
        const float Ay = y0 + adjX;
        const float Bx = x1 - adjY;
        const float By = y1 + adjX;
        const float Cx = x1 + adjY;
        const float Cy = y1 - adjX;
        const float Dx = x0 + adjY;
        const float Dy = y0 - adjX;
#endif

        TAKErr code(TE_Ok);

        // vertices

        code = pfRenderBuffer.put(Ax); // vertex
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(Ay);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(z0);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.skip(8u); // u,v texture (ignored)
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(vertexConst); // color + tex unit
        TE_CHECKRETURN_CODE(code);

        code = pfRenderBuffer.put(Bx); // vertex
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(By);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(z1);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.skip(8u); // u,v texture (ignored)
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(vertexConst); // color + tex unit
        TE_CHECKRETURN_CODE(code);

        code = pfRenderBuffer.put(Cx); // vertex
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(Cy);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(z1);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.skip(8u); // u,v texture (ignored)
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(vertexConst); // color + tex unit
        TE_CHECKRETURN_CODE(code);

        code = pfRenderBuffer.put(Dx); // vertex
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(Dy);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(z0);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.skip(8u); // u,v texture (ignored)
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(vertexConst); // color + tex unit
        TE_CHECKRETURN_CODE(code);

        // indices

        code = indexBuffer.put(static_cast<uint16_t>(indexOff + 0)); // A
        TE_CHECKRETURN_CODE(code);
        code = indexBuffer.put(static_cast<uint16_t>(indexOff + 3)); // D
        TE_CHECKRETURN_CODE(code);
        code = indexBuffer.put(static_cast<uint16_t>(indexOff + 1)); // B
        TE_CHECKRETURN_CODE(code);

        code = indexBuffer.put(static_cast<uint16_t>(indexOff + 3)); // D
        TE_CHECKRETURN_CODE(code);
        code = indexBuffer.put(static_cast<uint16_t>(indexOff + 1)); // B
        TE_CHECKRETURN_CODE(code);
        code = indexBuffer.put(static_cast<uint16_t>(indexOff + 2)); // C
        TE_CHECKRETURN_CODE(code);

        return code;
    }

    TAKErr addTexturedVertex2d_2d(MemBuffer2 &pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const uint64_t vertexConst, const float *mx) NOTHROWS {
        TAKErr code(TE_Ok);
        code = pfRenderBuffer.put(pfVertexCoords[0]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(pfVertexCoords[1]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(pfTexCoords[0]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(pfTexCoords[1]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(vertexConst); // color + tex unit
        TE_CHECKRETURN_CODE(code);

        return code;
    }

    TAKErr addTexturedVertex2d_3d(MemBuffer2 &pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const uint64_t vertexConst, const float *mx) NOTHROWS {
        TAKErr code(TE_Ok);
        code = pfRenderBuffer.put(pfVertexCoords[0]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(pfVertexCoords[1]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(static_cast<float>(0));
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(pfTexCoords[0]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(pfTexCoords[1]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(vertexConst); // color + tex unit
        TE_CHECKRETURN_CODE(code);

        return code;
    }

    TAKErr addTexturedVertex3d(MemBuffer2 &pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const uint64_t vertexConst, const float *mx) NOTHROWS {
        TAKErr code(TE_Ok);
        code = pfRenderBuffer.put(pfVertexCoords[0]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(pfVertexCoords[1]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(pfVertexCoords[2]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(pfTexCoords[0]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(pfTexCoords[1]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(vertexConst); // color + tex unit
        TE_CHECKRETURN_CODE(code);

        return code;
    }

    TAKErr addTexturedVertex2d_2dTransform(MemBuffer2 &pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const uint64_t vertexConst, const float *mx) NOTHROWS {
        TAKErr code(TE_Ok);
        float x = pfVertexCoords[0];
        float y = pfVertexCoords[1];
        transform2d_2d(&x, &y, mx, x, y);
        code = pfRenderBuffer.put(x);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(y);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(pfTexCoords[0]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(pfTexCoords[1]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(vertexConst); // color + tex unit
        TE_CHECKRETURN_CODE(code);
        return code;
    }

    TAKErr addTexturedVertex2d_3dTransform(MemBuffer2 &pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const uint64_t vertexConst, const float *mx) NOTHROWS {
        TAKErr code(TE_Ok);
        float x = pfVertexCoords[0];
        float y = pfVertexCoords[1];
        float z = 0;
        transform2d_3d(&x, &y, &z, mx, x, y);
        code = pfRenderBuffer.put(x);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(y);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(z);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(pfTexCoords[0]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(pfTexCoords[1]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(vertexConst); // color + tex unit
        TE_CHECKRETURN_CODE(code);
        return code;
    }

    TAKErr addTexturedVertex3dTransform(MemBuffer2 &pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const uint64_t vertexConst, const float *mx) NOTHROWS {
        TAKErr code(TE_Ok);
        float x = pfVertexCoords[0];
        float y = pfVertexCoords[1];
        float z = pfVertexCoords[2];
        transform3d(&x, &y, &z, mx, x, y, z);
        code = pfRenderBuffer.put(x);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(y);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(z);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(pfTexCoords[0]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(pfTexCoords[1]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(vertexConst); // color + tex unit
        TE_CHECKRETURN_CODE(code);
        return code;
    }

    TAKErr addUntexturedVertex2d_2d(MemBuffer2 &pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const uint64_t vertexConst, const float *mx) NOTHROWS {
        TAKErr code(TE_Ok);
        code = pfRenderBuffer.put(pfVertexCoords[0]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(pfVertexCoords[1]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.skip(8u);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(vertexConst); // color + tex unit
        TE_CHECKRETURN_CODE(code);
        return code;
    }

    TAKErr addUntexturedVertex2d_3d(MemBuffer2 &pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const uint64_t vertexConst, const float *mx) NOTHROWS {
        TAKErr code(TE_Ok);
        code = pfRenderBuffer.put(pfVertexCoords[0]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(pfVertexCoords[1]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(static_cast<float>(0));
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.skip(8u);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(vertexConst); // color + tex unit
        TE_CHECKRETURN_CODE(code);
        return code;
    }

    TAKErr addUntexturedVertex3d(MemBuffer2 &pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const uint64_t vertexConst, const float *mx) NOTHROWS {
        TAKErr code(TE_Ok);
        code = pfRenderBuffer.put(pfVertexCoords[0]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(pfVertexCoords[1]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(pfVertexCoords[2]);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.skip(8u);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(vertexConst); // color + tex unit
        TE_CHECKRETURN_CODE(code);
        return code;
    }

    TAKErr addUntexturedVertex2d_2dTransform(MemBuffer2 &pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const uint64_t vertexConst, const float *mx) NOTHROWS {
        TAKErr code(TE_Ok);
        float x = pfVertexCoords[0];
        float y = pfVertexCoords[1];
        transform2d_2d(&x, &y, mx, x, y);
        code = pfRenderBuffer.put(x);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(y);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.skip(8u);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(vertexConst); // color + tex unit
        TE_CHECKRETURN_CODE(code);
        return code;
    }

    TAKErr addUntexturedVertex2d_3dTransform(MemBuffer2 &pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const uint64_t vertexConst, const float *mx) NOTHROWS {
        TAKErr code(TE_Ok);
        float x = pfVertexCoords[0];
        float y = pfVertexCoords[1];
        float z = 0;
        transform2d_3d(&x, &y, &z, mx, x, y);
        code = pfRenderBuffer.put(x);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(y);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(z);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.skip(8u);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(vertexConst); // color + tex unit
        TE_CHECKRETURN_CODE(code);
        return code;
    }

    TAKErr addUntexturedVertex3dTransform(MemBuffer2 &pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const uint64_t vertexConst, const float *mx) NOTHROWS {
        TAKErr code(TE_Ok);
        float x = pfVertexCoords[0];
        float y = pfVertexCoords[1];
        float z = pfVertexCoords[2];
        transform3d(&x, &y, &z, mx, x, y, z);
        code = pfRenderBuffer.put(x);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(y);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(z);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.skip(8u);
        TE_CHECKRETURN_CODE(code);
        code = pfRenderBuffer.put(vertexConst); // color + tex unit
        TE_CHECKRETURN_CODE(code);

        return code;
    }

    void transform2d_2d(float *dstX, float *dstY, const float *mx, const float srcX, const float srcY) NOTHROWS
    {
        const float x = mx[0]*srcX + mx[4]*srcY + mx[12];
        const float y = mx[1]*srcX + mx[5]*srcY + mx[13];
        const float w = mx[3]*srcX + mx[7]*srcY + mx[15];

        *dstX = (x/w);
        *dstY = (y/w);
    }

    void transform2d_3d(float *dstX, float *dstY, float *dstZ, const float *mx, const float srcX, const float srcY) NOTHROWS
    {
        const float x = mx[0]*srcX + mx[4]*srcY + mx[12];
        const float y = mx[1]*srcX + mx[5]*srcY + mx[13];
        const float z = mx[2]*srcX + mx[6]*srcY + mx[14];
        const float w = mx[3]*srcX + mx[7]*srcY + mx[15];

        *dstX = (x/w);
        *dstY = (y/w);
        *dstZ = (z/w);
    }

    void transform3d(float *dstX, float *dstY, float *dstZ, const float *mx, const float srcX, const float srcY, const float srcZ) NOTHROWS
    {
        const float x = mx[0]*srcX + mx[4]*srcY + mx[8]*srcZ + mx[12];
        const float y = mx[1]*srcX + mx[5]*srcY + mx[9]*srcZ + mx[13];
        const float z = mx[2]*srcX + mx[6]*srcY + mx[10]*srcZ + mx[14];
        const float w = mx[3]*srcX + mx[7]*srcY + mx[11]*srcZ + mx[15];

        *dstX = (x/w);
        *dstY = (y/w);
        *dstZ = (z/w);
    }

    std::size_t glMaxTextureUnits() NOTHROWS
    {
        int i;
        glGetIntegerv(GL_MAX_TEXTURE_IMAGE_UNITS, &i);
        return i;
    }

    TAKErr getAddVertexFunction(addVertexFn *value, const std::size_t size, const bool texCoords, const int hints) NOTHROWS
    {
        TAKErr code(TE_Ok);

        const bool transformPoints = hasBits(hints, GLRenderBatch2::SoftwareTransforms);
        const bool buffer2d = hasBits(hints, GLRenderBatch2::TwoDimension);

        if (texCoords) {
            if(size == 2u && buffer2d)
                *value = transformPoints ? addTexturedVertex2d_2dTransform : addTexturedVertex2d_2d;
            else if(size == 2u)
                *value = transformPoints ? addTexturedVertex2d_3dTransform : addTexturedVertex2d_3d;
            else if(size == 3u && !buffer2d)
                *value = transformPoints ? addTexturedVertex3dTransform : addTexturedVertex3d;
            else if(size == 3u)
                return TE_IllegalState;
            else
                return TE_Unsupported;
        } else {
            if(size == 2u && buffer2d)
                *value = transformPoints ? addUntexturedVertex2d_2dTransform : addUntexturedVertex2d_2d;
            else if(size == 2u)
                *value = transformPoints ? addUntexturedVertex2d_3dTransform : addUntexturedVertex2d_3d;
            else if(size == 3u && !buffer2d)
                *value = transformPoints ? addUntexturedVertex3dTransform : addUntexturedVertex3d;
            else if(size == 3u)
                return TE_IllegalState;
            else
                return TE_Unsupported;
        }

        return code;
    }
}
