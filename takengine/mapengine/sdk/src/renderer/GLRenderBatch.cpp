#include "renderer/GL.h"
#include "renderer/GLRenderBatch.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/core/GLMapRenderGlobals.h"
#include <sstream>

#include <cmath>

#ifdef MSVC
#include <algorithm>
#endif

using namespace atakmap::renderer;

using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Core;

const int GLRenderBatch::INTERNAL_TEXTURE_UNIT_LIMIT = 32;

GLRenderBatch::GLRenderBatch(int cap) :
    impl(cap * 4)
{}

GLRenderBatch::~GLRenderBatch()
{}

/*********************************************************************/
// Life-cycle functions

void GLRenderBatch::begin()
{
    GLES20FixedPipeline *fixedPipe = GLES20FixedPipeline::getInstance();

    float tmpMatrix[16];

    impl.begin(GLRenderBatch2::TwoDimension);

    fixedPipe->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION, tmpMatrix);
    impl.setMatrix(GL_PROJECTION, tmpMatrix);
    fixedPipe->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW, tmpMatrix);
    impl.setMatrix(GL_MODELVIEW, tmpMatrix);
}

void GLRenderBatch::end()
{
    impl.end();
}


/*********************************************************************/
// Public API for Batch additions



void GLRenderBatch::addLine(float x0, float y0, float x1, float y1, float width, float r, float g, float b, float a)
{
    float line[4];
    line[0] = x0;
    line[1] = y0;
    line[2] = x1;
    line[3] = y1;

    impl.setLineWidth(width);
    impl.batch(-1,
               GL_LINES,
               2,
               2,
               0, line,
               0, nullptr,
               r, g, b, a);
}


void GLRenderBatch::addLines(const float *lines, size_t lineCount, float width, float r, float g, float b, float a)
{
    impl.setLineWidth(width);
    impl.batch(-1,
               GL_LINES,
               lineCount,
               2,
               0, lines,
               0, nullptr,
               r, g, b, a);
}

void GLRenderBatch::addLineStrip(const float *linestrip, size_t count, float width, float r, float g, float b, float a)
{
    impl.setLineWidth(width);
    impl.batch(-1,
               GL_LINE_STRIP,
               count / 2,
               2,
               0, linestrip,
               0, nullptr,
               r, g, b, a);
}

void GLRenderBatch::addLineLoop(const float *lineLoop, size_t count, float width, float r, float g, float b, float a)
{
    impl.setLineWidth(width);
    impl.batch(-1,
               GL_LINE_LOOP,
               count / 2,
               2,
               0, lineLoop,
               0, nullptr,
               r, g, b, a);
}

void GLRenderBatch::addTriangles(const float *vertexCoords, size_t count, float r, float g, float b, float a)
{
    impl.batch(NULL,
               GL_TRIANGLES,
               count / 2,
               2,
               0, vertexCoords,
               0, nullptr,
               r, g, b, a);
}

void GLRenderBatch::addTriangles(const float *vertexCoords, size_t count, const short *indices, size_t indexCount, float r, float g, float b, float a) throw (std::invalid_argument)
{
    impl.batch(-1,
               GL_TRIANGLES,
               count / 2,
               2,
               0, vertexCoords,
               0, nullptr,
               indexCount, reinterpret_cast<const unsigned short *>(indices),
               r, g, b, a);
}

void GLRenderBatch::addTriangleStrip(const float *vertexCoords, size_t count, float r, float g, float b, float a)
{
    impl.batch(-1,
               GL_TRIANGLE_STRIP,
               count / 2,
               2,
               0, vertexCoords,
               0, nullptr,
               r, g, b, a);
}

void GLRenderBatch::addTriangleStrip(const float *vertexCoords, size_t count, const short *indices, size_t indexCount, float r, float g, float b, float a)
{
    impl.batch(-1,
               GL_TRIANGLE_STRIP,
               count / 2,
               2,
               0, vertexCoords,
               0, nullptr,
               indexCount, reinterpret_cast<const unsigned short *>(indices),
               r, g, b, a);
}

void GLRenderBatch::addTrianglesSprite(const float *vertexCoords, 
                        size_t count, const float *texCoords, int textureId,
                        float r, float g, float b, float a)
{
    impl.batch(textureId,
               GL_TRIANGLES,
               count / 2,
               2,
               0, vertexCoords,
               0, texCoords,
               r, g, b, a);
}

void GLRenderBatch::addTrianglesSprite(const float *vertexCoords,
                        size_t count,
                        const short *indices,
                        size_t indexCount,
                        const float *texCoords,
                        int textureId,
                        float r, float g, float b, float a) throw (std::invalid_argument)
{
    impl.batch(textureId,
               GL_TRIANGLES,
               count / 2,
               2,
               0, vertexCoords,
               0, texCoords,
               indexCount, reinterpret_cast<const unsigned short *>(indices),
               r, g, b, a);
}

void GLRenderBatch::addTriangleStripSprite(const float *vertexCoords, size_t count,
                                            const float *texCoords, 
                                            int textureId, 
                                            float r, float g, float b, float a)
{
    impl.batch(textureId,
               GL_TRIANGLE_STRIP,
               count / 2,
               2,
               0, vertexCoords,
               0, texCoords,
               r, g, b, a);
}

void GLRenderBatch::addTriangleStripSprite(const float *vertexCoords,
                                            size_t count,
                                            const short *indices,
                                            size_t indexCount,
                                            const float *texCoords,
                                            int textureId,
                                            float r, float g, float b, float a) throw (std::invalid_argument)
{
    impl.batch(textureId,
               GL_TRIANGLE_STRIP,
               count / 2,
               2,
               0, vertexCoords,
               0, texCoords,
               indexCount, reinterpret_cast<const unsigned short *>(indices),
               r, g, b, a);
}

void GLRenderBatch::addTriangleFan(const float *vertexCoords, size_t count, float r, float g, float b, float a)
{
    impl.batch(-1,
               GL_TRIANGLE_FAN,
               count / 2,
               2,
               0, vertexCoords,
               0, nullptr,
               r, g, b, a);
}

void GLRenderBatch::addTriangleFan(const float *vertexCoords, size_t count, const short *indices, size_t indexCount, float r, float g, float b, float a)
{
    impl.batch(-1,
               GL_TRIANGLE_FAN,
               count / 2,
               2,
               0, vertexCoords,
               0, nullptr,
               indexCount, reinterpret_cast<const unsigned short *>(indices),
               r, g, b, a);
}

void GLRenderBatch::addTriangleFanSprite(const float *vertexCoords, size_t count,
                                            const float *texCoords, int textureId, 
                                            float r, float g, float b, float a)
{
    impl.batch(textureId,
               GL_TRIANGLE_FAN,
               count / 2,
               2,
               0, vertexCoords,
               0, texCoords,
               r, g, b, a);
}


void GLRenderBatch::addTriangleFanSprite(const float *vertexCoords,
                                            size_t count,
                                            const short *indices,
                                            size_t indexCount,
                                            const float *texCoords,
                                            int textureId,
                                            float r, float g, float b, float a) throw (std::invalid_argument)
{
    impl.batch(textureId,
               GL_TRIANGLE_FAN,
               count / 2,
               2,
               0, vertexCoords,
               0, texCoords,
               indexCount, reinterpret_cast<const unsigned short *>(indices),
               r, g, b, a);
}


void GLRenderBatch::addSprite(float x1, float y1, float x2, float y2, float u1,
                                float v1, float u2, float v2,
                                int textureId, float r, float g, float b, float a)
{
    addSprite(x1, y1,
              x2, y1,
              x2, y2,
              x1, y2,
              u1, v1,
              u2, v2,
              textureId, r, g, b, a);
}



void GLRenderBatch::addSprite(float x1, float y1, // ul
                              float x2, float y2, // ur
                              float x3, float y3, // lr
                              float x4, float y4, // ll
                              float u1, float v1, // ul
                              float u2, float v2, // lr
                              int textureId,
                              float r, float g, float b, float a)
{
    float verts[12];
    float texCoords[12];

    verts[0] = x4;      // ll
    verts[1] = y4;
    texCoords[0] = u1;
    texCoords[1] = v2;

    verts[2] = x1;      // ul
    verts[3] = y1;
    texCoords[2] = u1;
    texCoords[3] = v1;

    verts[4] = x3;      // lr
    verts[5] = y3;
    texCoords[4] = u2;
    texCoords[5] = v2;

    verts[6] = x1;      // ul
    verts[7] = y1;
    texCoords[6] = u1;
    texCoords[7] = v1;

    verts[8] = x3;      // lr
    verts[9] = y3;
    texCoords[8] = u2;
    texCoords[9] = v2;

    verts[10] = x2;      // ur
    verts[11] = y2;
    texCoords[10] = u2;
    texCoords[11] = v1;

    impl.batch(textureId, GL_TRIANGLES, 6, 2, 0, verts, 0, texCoords, r, g, b, a);
}

/*********************************************************************/
// Other public API

int GLRenderBatch::getBatchTextureUnitLimit()
{
    return static_cast<int>(GLRenderBatch2_getBatchTextureUnitLimit());
}

void GLRenderBatch::setBatchTextureUnitLimit(int limit)
{
    GLRenderBatch2_setBatchTextureUnitLimit(limit);
}

GLRenderBatch2 &atakmap::renderer::GLRenderBatch_adapt(GLRenderBatch &legacy) NOTHROWS
{
    return legacy.impl;
}
