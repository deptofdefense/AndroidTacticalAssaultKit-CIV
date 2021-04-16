#include "renderer/GLLinesEmulation.h"

#include <cinttypes>
#include <cmath>
#include <cstdlib>

#include "renderer/GL.h"


using namespace atakmap::renderer;

#define VERT_SHADER_VERTIMPL_2D \
                "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xy, 0.0, 1.0);\n" \
                "  vec4 next_gl_Position = uProjection * uModelView * vec4(aNextVertexCoords.xy, 0.0, 1.0);\n"
#define VERT_SHADER_VERTIMPL_3D \
                "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xyz, 1.0);\n" \
                "  vec4 next_gl_Position = uProjection * uModelView * vec4(aNextVertexCoords.xyz, 1.0);\n"

#define VERT_SHADER_SRC_1(vertImpl) \
                "uniform mat4 uProjection;\n" \
                "uniform mat4 uModelView;\n" \
                "attribute vec3 aVertexCoords;\n" \
                "attribute vec3 aNextVertexCoords;\n" \
                "attribute float aNormalDir;\n" \
                "uniform float uWidth;\n" \
                "uniform vec2 uViewport;\n" \
                "void main() {\n" \
                vertImpl \
                "  float radius = uWidth;\n" \
                "  float dx = next_gl_Position.x - gl_Position.x;\n" \
                "  float dy = next_gl_Position.y - gl_Position.y;\n" \
                "  float m = sqrt(dx*dx + dy*dy);\n" \
                "  float adjX = aNormalDir*(dx/m)*(radius/uViewport.y);\n" \
                "  float adjY = aNormalDir*(dy/m)*(radius/uViewport.x);\n" \
                "  gl_Position.x = gl_Position.x - adjY;\n" \
                "  gl_Position.y = gl_Position.y + adjX;\n" \
                "}"

#define VERT_SHADER_SRC_2D_1 VERT_SHADER_SRC_1(VERT_SHADER_VERTIMPL_2D)
#define VERT_SHADER_SRC_3D_1 VERT_SHADER_SRC_1(VERT_SHADER_VERTIMPL_3D)

#define FRAG_SHADER_SRC \
                "precision mediump float;\n" \
                "uniform vec4 uColor;\n" \
                "void main(void) {\n" \
                "  gl_FragColor = uColor;\n" \
                "}"

#define MAX_INDEX_VALUE 0xFFFFu

namespace
{
    typedef struct lines_emulation_program_t {
        GLuint handle;
        GLuint uProjection;
        GLuint uModelView;
        GLuint aVertexCoords;
        GLuint aNextVertexCoords;
        GLuint aNormalDir;
        GLuint uWidth;
        GLuint uViewport;
        GLuint uColor;
    } lines_emulation_program_t;

    lines_emulation_program_t program2d{ 0, 0, 0, 0, 0, 0, 0, 0 };
    lines_emulation_program_t program3d{ 0, 0, 0, 0, 0, 0, 0, 0 };

    // 4 vertices per quad; xyz0 + xyz1 + normal dir
    uint8_t vertsBuffer[MAX_INDEX_VALUE*(sizeof(float) * 7)];

    // XXX - consider static VBO for index buffer

    // 6 indices per quad
    uint16_t linesIdxBuffer[6 * MAX_INDEX_VALUE];

    void initProgram(lines_emulation_program_t *prog, const char *vertShaderSrc, const char *fragShaderSrc);

    template<class V>
    void emulateDrawArraysImpl(GLES20FixedPipeline *pipeline, const atakmap::renderer::ArrayPointer *pointer, const float width, int mode, int first, int count);

    template<class V, class I>
    void emulateLineDrawElementsImpl(GLES20FixedPipeline *pipeline, const ArrayPointer *pointer, float width, int mode, int count, int type, const I *srcIndices);

    bool initIdxBuffer(uint16_t *idxBuffer, const size_t step);

    const bool linesIdxInit = initIdxBuffer(linesIdxBuffer, 4);
}

GLLinesEmulation::GLLinesEmulation()
{}

GLLinesEmulation::~GLLinesEmulation()
{}

void GLLinesEmulation::emulateLineDrawArrays(const int mode, const int first, const int count, GLES20FixedPipeline *pipeline, const float width, const ArrayPointer *vertexPointer, const ArrayPointer *texCoordsPointer)
{
    if (vertexPointer && !texCoordsPointer)
    {
        switch (vertexPointer->type) {
#define CASE_DEF(l, t) \
    case l :  \
            { \
        emulateDrawArraysImpl<t>(pipeline, vertexPointer, width, mode, first, count); \
        break; \
            }
            CASE_DEF(GL_UNSIGNED_BYTE, uint8_t)
                CASE_DEF(GL_BYTE, int8_t)
                CASE_DEF(GL_UNSIGNED_SHORT, unsigned short)
                CASE_DEF(GL_SHORT, short)
                CASE_DEF(GL_UNSIGNED_INT, unsigned int)
                CASE_DEF(GL_INT, int)
                CASE_DEF(GL_FLOAT, float)
#undef CASE_DEF
        }
    }
}

void GLLinesEmulation::emulateLineDrawArrays(const int mode, const int first, const int count, const float *proj, const float *modelView, const float *texture, const float r, const float g, const float b, const float a, const float width, const ArrayPointer *vertexPointer, const ArrayPointer *texCoordsPointer)
{
}

void GLLinesEmulation::emulateLineDrawElements(const int mode, const int count, const int type, const void *indices, GLES20FixedPipeline *pipeline, const float width, const ArrayPointer *vertexPointer, const ArrayPointer *texCoordsPointer)
{
    switch (vertexPointer->type) {
#define IDX_CASE_DEF(l, tv, ti) \
    case l : \
        { \
        emulateLineDrawElementsImpl<tv, ti>(pipeline, vertexPointer, width, mode, count, type, reinterpret_cast<const ti *>(indices)); \
        } \
    break;
#define VTX_CASE_DEF(l, t) \
    case l :  \
        { \
        switch(type) \
                    { \
        IDX_CASE_DEF(GL_UNSIGNED_BYTE, t, uint8_t) \
        IDX_CASE_DEF(GL_UNSIGNED_SHORT, t, unsigned short) \
        IDX_CASE_DEF(GL_UNSIGNED_INT, t, unsigned int) \
                    } \
        } \
    break;

        VTX_CASE_DEF(GL_UNSIGNED_BYTE, uint8_t)
            VTX_CASE_DEF(GL_BYTE, int8_t)
            VTX_CASE_DEF(GL_UNSIGNED_SHORT, unsigned short)
            VTX_CASE_DEF(GL_SHORT, short)
            VTX_CASE_DEF(GL_UNSIGNED_INT, unsigned int)
            VTX_CASE_DEF(GL_INT, int)
            VTX_CASE_DEF(GL_FLOAT, float)
#undef VTX_CASE_DEF
#undef IDX_CASE_DEF
    }
}

#if 0
void GLLinesEmulation::emulateLineDrawElements(const int mode, const int count, const int type, const void *indices, const float *proj, const float *modelView, const float *texture, const float r, const float g, const float b, const float a, const float width, const ArrayPointer *vertexPointer, const ArrayPointer *texCoordsPointer)
{
}
#endif

namespace
{
    void initProgram(lines_emulation_program_t *prog, const char *vertShaderSrc, const char *fragShaderSrc)
    {
        Program p;
        if (p.create(vertShaderSrc, fragShaderSrc)) {
            glDeleteShader(p.vertShader);
            glDeleteShader(p.fragShader);

            prog->handle = p.program;
            p.program = GL_FALSE;

            prog->uProjection = glGetUniformLocation(prog->handle, "uProjection");
            prog->uModelView = glGetUniformLocation(prog->handle, "uModelView");
            prog->aVertexCoords = glGetAttribLocation(prog->handle, "aVertexCoords");
            prog->aNextVertexCoords = glGetAttribLocation(prog->handle, "aNextVertexCoords");
            prog->aNormalDir = glGetAttribLocation(prog->handle, "aNormalDir");
            prog->uWidth = glGetUniformLocation(prog->handle, "uWidth");
            prog->uViewport = glGetUniformLocation(prog->handle, "uViewport");
            prog->uColor = glGetUniformLocation(prog->handle, "uColor");
        }
    }

    void flush(const size_t numSegments, const uint16_t *idxBuffer) {
        glDrawElements(GL_TRIANGLES, static_cast<GLsizei>(numSegments * 6), GL_UNSIGNED_SHORT, idxBuffer);
    }

    void vertex(uint8_t *vertices, const void *p0, const void *p1, const size_t vertexCoordSize, const float dir)
    {
        memcpy(vertices, p0, vertexCoordSize);
        vertices += vertexCoordSize;
        memcpy(vertices, p1, vertexCoordSize);
        vertices += vertexCoordSize;
        reinterpret_cast<float *>(vertices)[0] = dir;
        vertices += 4;
    }

    template<class V>
    void emulateDrawArraysImpl(GLES20FixedPipeline *pipeline, const atakmap::renderer::ArrayPointer *pointer, const float width, int mode, int first, int count)
    {
        using namespace atakmap::renderer;

        // need at least 2 points for a line
        if (count < 2)
            return;

        uint8_t *pVertexBuffer = vertsBuffer;
        const size_t vertCoordSize = sizeof(V)*pointer->size;
        size_t vertSize = (2 * vertCoordSize) + 4u;
        const size_t vertCoordStride = pointer->stride ? pointer->stride : vertCoordSize;

        lines_emulation_program_t *prog;
        switch (pointer->size)
        {
        case 2:
            prog = &program2d;
            if (!prog->handle)
                initProgram(prog, VERT_SHADER_SRC_2D_1, FRAG_SHADER_SRC);
            break;
        case 3:
            prog = &program3d;
            if (!prog->handle)
                initProgram(prog, VERT_SHADER_SRC_3D_1, FRAG_SHADER_SRC);
            break;
        default:
            return;
        }

        // XXX - init program

        float p[16];

        glUseProgram(prog->handle);

        pipeline->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION, p);
        glUniformMatrix4fv(prog->uProjection, 1, false, p);
        pipeline->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW, p);
        glUniformMatrix4fv(prog->uModelView, 1, false, p);

        glVertexAttribPointer(prog->aVertexCoords, pointer->size, pointer->type, false, static_cast<GLsizei>(vertSize), pVertexBuffer);
        glEnableVertexAttribArray(prog->aVertexCoords);
        glVertexAttribPointer(prog->aNextVertexCoords, pointer->size, pointer->type, false, static_cast<GLsizei>(vertSize), pVertexBuffer + vertCoordSize);
        glEnableVertexAttribArray(prog->aNextVertexCoords);
        glVertexAttribPointer(prog->aNormalDir, 1, GL_FLOAT, false, static_cast<GLsizei>(vertSize), pVertexBuffer + (2 * vertCoordSize));
        glEnableVertexAttribArray(prog->aNormalDir);

        glUniform1f(prog->uWidth, width);

        pipeline->getColor(p);
        glUniform4f(prog->uColor, p[0], p[1], p[2], p[3]);
        glGetFloatv(GL_VIEWPORT, p);
        glUniform2f(prog->uViewport, p[2], p[3]);


        const uint8_t *pPointer = static_cast<const uint8_t *>(pointer->pointer) + (vertCoordStride*first);

        uint16_t *idxBuffer = linesIdxBuffer;

        bool loop;
        size_t pointerStep;
        size_t numSegments;
        switch (mode) {
        case GL_LINES:
            loop = false;
            pointerStep = 2;
            numSegments = count / 2;
            break;
        case GL_LINE_STRIP:
            loop = false;
            pointerStep = 1;
            numSegments = count - 1;
            break;
        case GL_LINE_LOOP:
            loop = true;
            pointerStep = 1;
            numSegments = count - 1;
            break;
        default:
            return;
        }

        size_t bufferedVerts = 0;
        for (size_t i = 0; i < numSegments; i++) {
            if ((bufferedVerts + 4) >= MAX_INDEX_VALUE) {
                flush(bufferedVerts / 4, idxBuffer);
                pVertexBuffer = vertsBuffer;
                bufferedVerts = 0;
            }

            // ABAB
            vertex(pVertexBuffer, pPointer, pPointer + vertCoordStride, vertCoordSize, 1);
            pVertexBuffer += vertSize;
            vertex(pVertexBuffer, pPointer, pPointer + vertCoordStride, vertCoordSize, -1);
            pVertexBuffer += vertSize;

            // BABA
            vertex(pVertexBuffer, pPointer + vertCoordStride, pPointer, vertCoordSize, -1);
            pVertexBuffer += vertSize;
            vertex(pVertexBuffer, pPointer + vertCoordStride, pPointer, vertCoordSize, 1);
            pVertexBuffer += vertSize;

            bufferedVerts += 4;
            pPointer += vertCoordStride*pointerStep;
        }

        // close the loop if there are more than 2 points
        if (loop && count > 2) {
            if ((bufferedVerts + 4) >= MAX_INDEX_VALUE) {
                flush(bufferedVerts / 2, idxBuffer);
                pVertexBuffer = vertsBuffer;
                bufferedVerts = 0;
            }

            // ABAB
            vertex(pVertexBuffer, pPointer, pointer->pointer, vertCoordSize, 1);
            pVertexBuffer += vertSize;
            vertex(pVertexBuffer, pPointer, pointer->pointer, vertCoordSize, -1);
            pVertexBuffer += vertSize;

            // BABA
            vertex(pVertexBuffer, pointer->pointer, pPointer, vertCoordSize, -1);
            pVertexBuffer += vertSize;
            vertex(pVertexBuffer, pointer->pointer, pPointer, vertCoordSize, 1);
            pVertexBuffer += vertSize;

            bufferedVerts += 4;
        }

        // flush anything remaining
        if (bufferedVerts)
            flush(bufferedVerts / 4, idxBuffer);

        glDisableVertexAttribArray(prog->aVertexCoords);
        glDisableVertexAttribArray(prog->aNextVertexCoords);
        glDisableVertexAttribArray(prog->aNormalDir);
    }

    template<class V, class I>
    void emulateLineDrawElementsImpl(GLES20FixedPipeline *pipeline, const ArrayPointer *pointer, float width, int mode, int count, int type, const I *srcIndices)
    {
        using namespace atakmap::renderer;

        // need at least 2 points for a line
        if (count < 2)
            return;

        uint8_t *pVertexBuffer = vertsBuffer;
        const size_t vertCoordSize = sizeof(V)*pointer->size;
        size_t vertSize = (2 * vertCoordSize) + 4u;
        const size_t vertCoordStride = pointer->stride ? pointer->stride : vertCoordSize;

        lines_emulation_program_t *prog;
        switch (pointer->size)
        {
        case 2:
            prog = &program2d;
            if (!prog->handle)
                initProgram(prog, VERT_SHADER_SRC_2D_1, FRAG_SHADER_SRC);
            break;
        case 3:
            prog = &program3d;
            if (!prog->handle)
                initProgram(prog, VERT_SHADER_SRC_3D_1, FRAG_SHADER_SRC);
            break;
        default:
            return;
        }

        // XXX - init program

        float p[16];

        glUseProgram(prog->handle);

        pipeline->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION, p);
        glUniformMatrix4fv(prog->uProjection, 1, false, p);
        pipeline->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW, p);
        glUniformMatrix4fv(prog->uModelView, 1, false, p);

        glVertexAttribPointer(prog->aVertexCoords, pointer->size, pointer->type, false, static_cast<GLsizei>(vertSize), pVertexBuffer);
        glEnableVertexAttribArray(prog->aVertexCoords);
        glVertexAttribPointer(prog->aNextVertexCoords, pointer->size, pointer->type, false, static_cast<GLsizei>(vertSize), pVertexBuffer + vertCoordSize);
        glEnableVertexAttribArray(prog->aNextVertexCoords);
        glVertexAttribPointer(prog->aNormalDir, 1, GL_FLOAT, false, static_cast<GLsizei>(vertSize), pVertexBuffer + (2 * vertCoordSize));
        glEnableVertexAttribArray(prog->aNormalDir);

        glUniform1f(prog->uWidth, width);

        pipeline->getColor(p);
        glUniform4f(prog->uColor, p[0], p[1], p[2], p[3]);
        glGetFloatv(GL_VIEWPORT, p);
        glUniform2f(prog->uViewport, p[2], p[3]);


        const auto *basePointer = static_cast<const uint8_t *>(pointer->pointer);

        uint16_t *idxBuffer = linesIdxBuffer;

        bool loop;
        size_t pointerStep;
        size_t numSegments;
        switch (mode) {
        case GL_LINES:
            loop = false;
            pointerStep = 2;
            numSegments = count / 2;
            break;
        case GL_LINE_STRIP:
            loop = false;
            pointerStep = 1;
            numSegments = count - 1;
            break;
        case GL_LINE_LOOP:
            loop = true;
            pointerStep = 1;
            numSegments = count - 1;
            break;
        default:
            return;
        }

        const I *pSrcIndices = srcIndices;

        size_t bufferedVerts = 0;
        const uint8_t *pA;
        const uint8_t *pB;
        for (size_t i = 0; i < numSegments; i++) {
            if ((bufferedVerts + 4) >= MAX_INDEX_VALUE) {
                flush(bufferedVerts / 4, idxBuffer);
                pVertexBuffer = vertsBuffer;
                bufferedVerts = 0;
            }

            pA = basePointer+(pSrcIndices[0]*vertCoordStride);
            pB = basePointer+(pSrcIndices[1]*vertCoordStride);

            // ABAB
            vertex(pVertexBuffer, pA, pB, vertCoordSize, 1);
            pVertexBuffer += vertSize;
            vertex(pVertexBuffer, pA, pB, vertCoordSize, -1);
            pVertexBuffer += vertSize;

            // BABA
            vertex(pVertexBuffer, pB, pA, vertCoordSize, -1);
            pVertexBuffer += vertSize;
            vertex(pVertexBuffer, pB, pA, vertCoordSize, 1);
            pVertexBuffer += vertSize;

            bufferedVerts += 4;
            pSrcIndices += pointerStep;
        }

        // close the loop if there are more than 2 points
        if (loop && count > 2) {
            if ((bufferedVerts + 4) >= MAX_INDEX_VALUE) {
                flush(bufferedVerts / 2, idxBuffer);
                pVertexBuffer = vertsBuffer;
                bufferedVerts = 0;
            }

            pA = basePointer+(pSrcIndices[0]*vertCoordStride);
            pB = basePointer+(srcIndices[0]*vertCoordStride);

            // ABAB
            vertex(pVertexBuffer, pA, pB, vertCoordSize, 1);
            pVertexBuffer += vertSize;
            vertex(pVertexBuffer, pA, pB, vertCoordSize, -1);
            pVertexBuffer += vertSize;

            // BABA
            vertex(pVertexBuffer, pB, pA, vertCoordSize, -1);
            pVertexBuffer += vertSize;
            vertex(pVertexBuffer, pB, pA, vertCoordSize, 1);
            pVertexBuffer += vertSize;

            bufferedVerts += 4;
        }

        // flush anything remaining
        if (bufferedVerts)
            flush(bufferedVerts / 4, idxBuffer);

        glDisableVertexAttribArray(prog->aVertexCoords);
        glDisableVertexAttribArray(prog->aNextVertexCoords);
        glDisableVertexAttribArray(prog->aNormalDir);
    }

    bool initIdxBuffer(uint16_t *idxBuffer, const size_t step)
    {
        uint16_t *pIdxBuffer = idxBuffer;
        
        uint16_t idx = 0;
        for (size_t i = 0; i < MAX_INDEX_VALUE; i++)
        {
            *pIdxBuffer++ = idx;
            *pIdxBuffer++ = idx + 1;
            *pIdxBuffer++ = idx + 2;
            *pIdxBuffer++ = idx + 1;
            *pIdxBuffer++ = idx + 2;
            *pIdxBuffer++ = idx + 3;

            idx += static_cast<uint16_t>(step);


        }

        return true;
    }
}
