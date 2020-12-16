#include "GLRenderBatch3D.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/core/GLMapRenderGlobals.h"
#include "renderer/GL.h"
#include <sstream>

#include <cmath>

#ifdef MSVC
#include <algorithm>
#endif

using namespace TAK::Engine::Renderer::Core;

#define UNTEXTURED_VERTEX_SHADER_SRC \
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

namespace
{
    bool hasBits(int v, int m);
}

namespace atakmap {
    namespace renderer {

        /*********************************************************************/
        // Anonymous constants and helper functions

        namespace {
            const int VERTEX_SIZE = 12 + // vertex coord
                8 + // texture coord
                4 + // rgba
                4;  // texture unit
            const int VERTICES_PER_SPRITE = 6;

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


            const char SPRITE_BATCH_VERTEX_SHADER_SRC[] =
                "uniform mat4 uProjection;\n"
                "uniform mat4 uModelView;\n"
                "attribute vec3 aVertexCoords;\n"
                "attribute vec2 aTextureCoords;\n"
                "attribute vec4 aColor;\n"
                "attribute float aTexUnit;\n"
                "varying vec2 vTexPos;\n"
                "varying vec4 vColor;\n"
                "varying float vTexUnit;\n"
                "void main() {\n"
                "  vTexPos = aTextureCoords;\n"
                "  vColor = aColor;\n"
                "  vTexUnit = aTexUnit;\n"
                "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xyz, 1.0);\n"
                "}";


            template<typename T>
            int isDegenerate(const T *triangle, const int stride)
            {
                return (triangle[0] == triangle[stride] &&         // A == B
                    triangle[1] == triangle[1 + stride]) ||
                    (triangle[0] == triangle[stride * 2] &&       // A == C
                    triangle[1] == triangle[1 + stride * 2]) ||
                    (triangle[stride] == triangle[stride * 2] &&  // B == C
                    triangle[1 + stride] == triangle[1 + stride * 2]);
            }
        }

        const int GLRenderBatch3D::INTERNAL_TEXTURE_UNIT_LIMIT = 32;

        /*********************************************************************/
        // Constructor/Destructor/other init

        GLRenderBatch3D::GLRenderBatch3D(int cap) :
            untexturedProgram(untextured_batch_render_program_t {0, 0, 0, 0, 0}),
            texturedProgram(textured_batch_render_program_t{0, 0, 0, 0, 0, 0, 0, NULL, 0}),
            numActiveTexUnits(0),
            originalTextureUnit(0),
            batchHints(0)
        {
            // constrain the buffers to the maximum possible index value
            if ((cap*VERTICES_PER_SPRITE) > 0xFFFF)
                cap = (0xFFFF / VERTICES_PER_SPRITE);

            renderBuffer = new OffsetBuffer<uint8_t>(cap * 4 * VERTEX_SIZE);
            indexBuffer = new OffsetBuffer<short>(cap * VERTICES_PER_SPRITE);
            texUnitIdxToTexId = new int[INTERNAL_TEXTURE_UNIT_LIMIT];
        }

        GLRenderBatch3D::~GLRenderBatch3D()
        {
            delete renderBuffer;
            delete indexBuffer;
            delete[] texturedProgram.uTextureHandles;
            delete[] texUnitIdxToTexId;
        }

        void GLRenderBatch3D::initPrograms()
        {
            // untextured program
            {
                Program p;
                if (p.create(UNTEXTURED_VERTEX_SHADER_SRC, UNTEXTURED_FRAGMENT_SHADER_SRC)) {
                    untexturedProgram.handle = p.program;
                    glUseProgram(p.program);

                    untexturedProgram.uProjectionHandle = glGetUniformLocation(untexturedProgram.handle, "uProjection");
                    untexturedProgram.uModelViewHandle = glGetUniformLocation(untexturedProgram.handle, "uModelView");
                    untexturedProgram.aVertexCoordsHandle = glGetAttribLocation(untexturedProgram.handle, "aVertexCoords");
                    untexturedProgram.aColorHandle = glGetAttribLocation(untexturedProgram.handle, "aColor");

                    glDeleteShader(p.vertShader);
                    glDeleteShader(p.fragShader);
                    p.program = GL_FALSE;
                }
            }

            // textured program
            {
                std::string fragShaderSrc = getFragmentShaderSrc();
                Program p;
                if (p.create(SPRITE_BATCH_VERTEX_SHADER_SRC, fragShaderSrc.c_str())) {
                    texturedProgram.handle = p.program;
                    glUseProgram(p.program);

                    texturedProgram.uProjectionHandle = glGetUniformLocation(texturedProgram.handle, "uProjection");
                    texturedProgram.uModelViewHandle = glGetUniformLocation(texturedProgram.handle, "uModelView");
                    texturedProgram.aVertexCoordsHandle = glGetAttribLocation(texturedProgram.handle, "aVertexCoords");
                    texturedProgram.aTextureCoordsHandle = glGetAttribLocation(texturedProgram.handle, "aTextureCoords");
                    texturedProgram.aColorHandle = glGetAttribLocation(texturedProgram.handle, "aColor");
                    texturedProgram.aTexUnitHandle = glGetAttribLocation(texturedProgram.handle, "aTexUnit");

                    texturedProgram.numTextureUnitHandles = getBatchTextureUnitLimit();
                    texturedProgram.uTextureHandles = new int[texturedProgram.numTextureUnitHandles];
                    for (int i = 0; i < texturedProgram.numTextureUnitHandles; i++) {
                        std::ostringstream sstream;
                        sstream << "uTexture" << i;
                        std::string s = sstream.str();
                        texturedProgram.uTextureHandles[i] = glGetUniformLocation(texturedProgram.handle, s.c_str());
                    }

                    glDeleteShader(p.vertShader);
                    glDeleteShader(p.fragShader);
                    p.program = GL_FALSE;
                }
            }
        }

        std::string GLRenderBatch3D::getFragmentShaderSrc()
        {
            std::ostringstream sstream;
            int textureUnitLimit = getBatchTextureUnitLimit();

            sstream << "precision mediump float;\n";
            for (int i = 0; i < textureUnitLimit; i++) {
                sstream << "uniform sampler2D uTexture" << i << ";\n";
            }

            sstream << "varying float vTexUnit;\n"
                << "varying vec4 vColor;\n"
                << "varying vec2 vTexPos;\n"
                << "void main(void) {\n";

            if (textureUnitLimit > 0) {
                sstream << "  if(vTexUnit == 0.0)\n"
                    << "    gl_FragColor = vColor * texture2D(uTexture0, vTexPos);\n";

                for (int i = 1; i < textureUnitLimit; i++) {
                    sstream << "  else if(vTexUnit == " << i << ".0)\n"
                        << "    gl_FragColor = vColor * texture2D(uTexture" << i << ", vTexPos);\n";
                }

                sstream << "  else\n";
            }

            sstream << "    gl_FragColor = vColor;\n"
                << "}\n";

            return sstream.str();
        }




        /*********************************************************************/
        // Life-cycle functions

        void GLRenderBatch3D::begin()
        {
            this->begin(0);
        }

        void GLRenderBatch3D::begin(int hints)
        {
            this->batchHints = hints;

            GLES20FixedPipeline *fixedPipe = GLES20FixedPipeline::getInstance();
            originalTextureUnit = fixedPipe->getActiveTexture();
            renderBuffer->reset();
            indexBuffer->reset();
            numActiveTexUnits = 0;

            if (!untexturedProgram.handle)
                initPrograms();
            

            if (hasBits(this->batchHints, Hints::Untextured)) {
                ::glUseProgram(untexturedProgram.handle);

                float tmpMatrix[16];
                fixedPipe->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION, tmpMatrix);
                glUniformMatrix4fv(untexturedProgram.uProjectionHandle, 1, false, tmpMatrix);

                fixedPipe->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW, tmpMatrix);
                glUniformMatrix4fv(untexturedProgram.uModelViewHandle, 1, false, tmpMatrix);
            } 
            else {
                ::glUseProgram(texturedProgram.handle);

                float tmpMatrix[16];
                fixedPipe->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION, tmpMatrix);
                glUniformMatrix4fv(texturedProgram.uProjectionHandle, 1, false, tmpMatrix);

                fixedPipe->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW, tmpMatrix);
                glUniformMatrix4fv(texturedProgram.uModelViewHandle, 1, false, tmpMatrix);

                int textureUnitLimit = getBatchTextureUnitLimit();
                if (texturedProgram.numTextureUnitHandles < textureUnitLimit)
                    textureUnitLimit = texturedProgram.numTextureUnitHandles;
                for (int i = 0; i < textureUnitLimit; ++i)
                    glUniform1i(texturedProgram.uTextureHandles[i], GL_TEXTURE_UNITS[i] - GL_TEXTURE0);
            }
        }

        void GLRenderBatch3D::end()
        {
            flush();
            GLES20FixedPipeline::getInstance()->glActiveTexture(originalTextureUnit);
        }

        void GLRenderBatch3D::release()
        {
            if (untexturedProgram.handle) {
                glDeleteProgram(untexturedProgram.handle);
                untexturedProgram.handle = 0;
            }
            if (texturedProgram.handle) {
                glDeleteProgram(texturedProgram.handle);
                texturedProgram.handle = 0;
            }
            if (texturedProgram.numTextureUnitHandles) {
                delete [] texturedProgram.uTextureHandles;
                texturedProgram.uTextureHandles = NULL;
                texturedProgram.numTextureUnitHandles = 0;
            }
        }

        void GLRenderBatch3D::flush()
        {
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            if (renderBuffer->used()) {
                const bool textured = !hasBits(this->batchHints, Hints::Untextured);
                const int aVertexCoordsHandle = textured ? texturedProgram.aVertexCoordsHandle : untexturedProgram.aVertexCoordsHandle;
                const int aTextureCoordsHandle = textured ? texturedProgram.aTextureCoordsHandle : 0;
                const int aColorHandle = textured ? texturedProgram.aColorHandle : untexturedProgram.aColorHandle;
                const int aTexUnitHandle = textured ? texturedProgram.aTexUnitHandle : 0;

                glVertexAttribPointer(
                    aVertexCoordsHandle,
                    3,
                    GL_FLOAT,
                    false,
                    VERTEX_SIZE,
                    renderBuffer->base);

                if (textured)
                    glVertexAttribPointer(
                        aTextureCoordsHandle,
                        2,
                        GL_FLOAT,
                        false,
                        VERTEX_SIZE,
                        renderBuffer->base + 12);

                glVertexAttribPointer(
                    aColorHandle,
                    4,
                    GL_UNSIGNED_BYTE,
                    true,
                    VERTEX_SIZE,
                    renderBuffer->base + 20);

                if (textured)
                    glVertexAttribPointer(
                        aTexUnitHandle,
                        1,
                        GL_FLOAT,
                        false,
                        VERTEX_SIZE,
                        renderBuffer->base + 24);

                glEnableVertexAttribArray(aVertexCoordsHandle);
                if (textured)
                    glEnableVertexAttribArray(aTextureCoordsHandle);
                glEnableVertexAttribArray(aColorHandle);
                if (textured)
                    glEnableVertexAttribArray(aTexUnitHandle);

                glDrawElements(GL_TRIANGLES,
                    static_cast<GLsizei>(indexBuffer->used()),
                    GL_UNSIGNED_SHORT,
                    indexBuffer->base);

                glDisableVertexAttribArray(aVertexCoordsHandle);
                if (textured)
                    glDisableVertexAttribArray(aTextureCoordsHandle);
                glDisableVertexAttribArray(aColorHandle);
                if (textured)
                    glDisableVertexAttribArray(aTexUnitHandle);
            }

            numActiveTexUnits = 0;

            renderBuffer->reset();
            indexBuffer->reset();

            glDisable(GL_BLEND);
        }




        /*********************************************************************/
        // Private utility for additions to rendering

        namespace {
            void addLineImpl(float *pfRenderBuffer, short *indexBuffer, size_t indexOff, unsigned char *vertexConst, float width, float x0, float y0, float z0, float x1, float y1, float z1) {
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

                const float m = (float)sqrt(dx*dx + dy*dy);

                const float radius = width / 2;
                const float adjX = (dx / m)*radius;
                const float adjY = (dy / m)*radius;

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

                // vertices

                *pfRenderBuffer++ = Ax; // vertex
                *pfRenderBuffer++ = Ay;
                *pfRenderBuffer++ = z0;
                pfRenderBuffer += 2; // u,v texture (ignored)
                memcpy(pfRenderBuffer, vertexConst, 8); // color + tex unit
                pfRenderBuffer += 2;

                *pfRenderBuffer++ = Bx; // vertex
                *pfRenderBuffer++ = By;
                *pfRenderBuffer++ = z1;
                pfRenderBuffer += 2; // u,v texture (ignored)
                memcpy(pfRenderBuffer, vertexConst, 8); // color + tex unit
                pfRenderBuffer += 2;

                *pfRenderBuffer++ = Cx; // vertex
                *pfRenderBuffer++ = Cy;
                *pfRenderBuffer++ = z1;
                pfRenderBuffer += 2; // u,v texture (ignored)
                memcpy(pfRenderBuffer, vertexConst, 8); // color + tex unit
                pfRenderBuffer += 2;

                *pfRenderBuffer++ = Dx; // vertex
                *pfRenderBuffer++ = Dy;
                *pfRenderBuffer++ = z0;
                pfRenderBuffer += 2; // u,v texture (ignored)
                memcpy(pfRenderBuffer, vertexConst, 8); // color + tex unit
                pfRenderBuffer += 2;

                // indices

                *indexBuffer++ = indexOff + 0; // A
                *indexBuffer++ = indexOff + 3; // D
                *indexBuffer++ = indexOff + 1; // B

                *indexBuffer++ = indexOff + 3; // D
                *indexBuffer++ = indexOff + 1; // B
                *indexBuffer++ = indexOff + 2; // C
            }

            void addTexturedVertex(float *pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const unsigned char *vertexConst) {
                *pfRenderBuffer++ = pfVertexCoords[0];
                *pfRenderBuffer++ = pfVertexCoords[1];
                *pfRenderBuffer++ = pfVertexCoords[2];
                *pfRenderBuffer++ = pfTexCoords[0];
                *pfRenderBuffer++ = pfTexCoords[1];

                memcpy(pfRenderBuffer, vertexConst, 8);
            }

            void addUntexturedVertex(float *pfRenderBuffer, const float *pfVertexCoords, const float *pfTexCoords, const unsigned char *vertexConst) {
                *pfRenderBuffer++ = pfVertexCoords[0];
                *pfRenderBuffer++ = pfVertexCoords[1];
                *pfRenderBuffer++ = pfVertexCoords[2];
                pfRenderBuffer += 2;
                memcpy(pfRenderBuffer, vertexConst, 8);
            }


            typedef void(*addVertexFn)(float *pfRenderBuffer,
                const float *pfVertexCoords,
                const float *pfTexCoords,
                const unsigned char *vertexConst);

        }


        void GLRenderBatch3D::addLinesImpl(const float width, const size_t numLines, const float *lines, const int step, unsigned char *vertexConst) {
            float x0;
            float y0;
            float z0;
            float x1;
            float y1;
            float z1;
            for (int i = 0; i < numLines; i++) {
                if (renderBuffer->remaining() < (VERTEX_SIZE * 4) ||
                    indexBuffer->remaining() < VERTICES_PER_SPRITE) {

                    flush();
                }

                x0 = lines[0];
                y0 = lines[1];
                z0 = lines[2];
                x1 = lines[3];
                y1 = lines[4];
                z1 = lines[5];

                lines += step;

                addLineImpl((float *)renderBuffer->ptr, indexBuffer->ptr, static_cast<int>(renderBuffer->used()) / VERTEX_SIZE, vertexConst, width, x0, y0, z0, x1, y1, z1);
                renderBuffer->ptr += (VERTEX_SIZE * 4);
                indexBuffer->ptr += VERTICES_PER_SPRITE;
            }
        }

        void GLRenderBatch3D::addTrianglesImpl(const float *vertexCoords, size_t count, const float *texCoords, int texUnitIdx, float r, float g, float b, float a)
        {
            addVertexFn addVertex;
            if (texCoords)
                addVertex = addTexturedVertex;
            else
                addVertex = addUntexturedVertex;

            size_t indexOff = renderBuffer->used() / VERTEX_SIZE;

            unsigned char vertexConst[8];
            vertexConst[0] = (unsigned char)(r*255.0f);
            vertexConst[1] = (unsigned char)(g*255.0f);
            vertexConst[2] = (unsigned char)(b*255.0f);
            vertexConst[3] = (unsigned char)(a*255.0f);
            (*(float *)(vertexConst + 4)) = (float)texUnitIdx;

            size_t numTriangles = count / (3 * 3);
            for (int i = 0; i < numTriangles; i++) {
                if (renderBuffer->remaining() < (VERTEX_SIZE * 3) ||
                    indexBuffer->remaining() < 3) {

                    flush();
                    indexOff = 0;
                }

                // XXX - check for duplicate vertices
                for (int j = 0; j < 3; j++) {
                    // vertex
                    addVertex((float *)renderBuffer->ptr,
                        vertexCoords,
                        texCoords,
                        vertexConst);

                    vertexCoords += 3;
                    texCoords += 2;
                    renderBuffer->ptr += VERTEX_SIZE;

                    // index
                    *indexBuffer->ptr = indexOff++;
                    indexBuffer->ptr++;
                }
            }

        }

        void GLRenderBatch3D::addIndexedTrianglesImpl(const float *vertexCoords, size_t count, const short *indices, size_t indexCount, const float *texCoords, int texUnitIdx, float r, float g, float b, float a)
        {
            addVertexFn addVertex;
            if (texCoords)
                addVertex = addTexturedVertex;
            else
                addVertex = addUntexturedVertex;

            size_t indexOff = renderBuffer->used() / VERTEX_SIZE;

            unsigned char vertexConst[8];
            vertexConst[0] = (unsigned char)(r*255.0f);
            vertexConst[1] = (unsigned char)(g*255.0f);
            vertexConst[2] = (unsigned char)(b*255.0f);
            vertexConst[3] = (unsigned char)(a*255.0f);
            *(float *)(vertexConst + 4) = (float)texUnitIdx;

            const size_t numVerts = count / 3;
            for (int i = 0; i < numVerts; i++) {
                // vertex
                addVertex((float *)renderBuffer->ptr,
                    vertexCoords,
                    texCoords,
                    vertexConst);

                renderBuffer->ptr += VERTEX_SIZE;
                vertexCoords += 3;
                texCoords += 2;
            }

            if (indexOff == 0) {
                memcpy(indexBuffer->ptr, indices, indexCount * sizeof(short));
            }
            else {
                for (size_t i = 0; i < indexCount; i++)
                    indexBuffer->ptr[i] = indexOff + indices[i];
            }
            indexBuffer->ptr += indexCount;
        }

        void GLRenderBatch3D::addTriangleStripImpl(const float *vertexCoords, size_t count, const float *texCoords, int texUnitIdx, float r, float g, float b, float a)
        {
            size_t indexOff = 0;

            unsigned char vertexConst[8];
            vertexConst[0] = (unsigned char)(r*255.0f);
            vertexConst[1] = (unsigned char)(g*255.0f);
            vertexConst[2] = (unsigned char)(b*255.0f);
            vertexConst[3] = (unsigned char)(a*255.0f);
            (*(float *)(vertexConst + 4)) = (float)texUnitIdx;

            addVertexFn addVertex;
            if (texCoords)
                addVertex = addTexturedVertex;
            else
                addVertex = addUntexturedVertex;

            const size_t numTriangles = (count / 3) - 2;

            vertexCoords += 6;
            texCoords += 4;

            bool isFirstTriangle = true;
            for (int i = 0; i < numTriangles; i++) {
                if (isDegenerate(vertexCoords + (i * 2), 2)) {
                    isFirstTriangle = true;
                    vertexCoords += 3;
                    texCoords += 2;
                    continue;
                }

                if (renderBuffer->remaining() < (VERTEX_SIZE * 3) ||
                    indexBuffer->remaining() < 3) {

                    flush();

                    isFirstTriangle = true;
                }

                // if this is the first triangle being written to the vertex buffer then
                // we will need to pre-fill it with the last two vertices
                if (isFirstTriangle) {
                    addVertex((float *)renderBuffer->ptr,
                        vertexCoords - 6,
                        texCoords - 4,
                        vertexConst);

                    renderBuffer->ptr += VERTEX_SIZE;

                    addVertex((float *)renderBuffer->ptr,
                        vertexCoords - 3,
                        texCoords - 2,
                        vertexConst);

                    renderBuffer->ptr += VERTEX_SIZE;

                    // update the index offset
                    indexOff = renderBuffer->used() / VERTEX_SIZE;

                    isFirstTriangle = false;
                }

                // add current vertex
                addVertex((float *)renderBuffer->ptr, vertexCoords, texCoords, vertexConst);

                vertexCoords += 3;
                texCoords += 2;

                renderBuffer->ptr += VERTEX_SIZE;

                // indices
                *indexBuffer->ptr++ = indexOff - 2;
                *indexBuffer->ptr++ = indexOff - 1;
                *indexBuffer->ptr++ = indexOff;

                indexOff++;
            }
        }


        void GLRenderBatch3D::addIndexedTriangleStripImpl(const float *vertexCoords, size_t count, const short *indices, size_t indexCount, const float *texCoords, int texUnitIdx, float r, float g, float b, float a)
        {
            addVertexFn addVertex;
            if (texCoords)
                addVertex = addTexturedVertex;
            else
                addVertex = addUntexturedVertex;

            size_t indexOff = renderBuffer->used() / VERTEX_SIZE;

            unsigned char vertexConst[8];
            vertexConst[0] = (unsigned char)(r*255.0f);
            vertexConst[1] = (unsigned char)(g*255.0f);
            vertexConst[2] = (unsigned char)(b*255.0f);
            vertexConst[3] = (unsigned char)(a*255.0f);
            (*(float *)(vertexConst + 4)) = (float)texUnitIdx;

            const size_t numVerts = count / 3;
            for (size_t i = 0; i < numVerts; i++) {
                // vertex
                addVertex((float *)renderBuffer->ptr,
                    vertexCoords,
                    texCoords,
                    vertexConst);

                renderBuffer->ptr += VERTEX_SIZE;
                vertexCoords += 3;
                texCoords += 2;
            }

            // advance to the third index
            indices += 2;

            const size_t numTriangles = indexCount - 2;
            for (int i = 0; i < numTriangles; i++) {
                if (isDegenerate(indices + i, 2)) {
                    continue;
                }

                *indexBuffer->ptr++ = indexOff + indices[i - 2];
                *indexBuffer->ptr++ = indexOff + indices[i - 1];
                *indexBuffer->ptr++ = indexOff + indices[i];
            }

        }


        void GLRenderBatch3D::addTriangleFanImpl(const float *vertexCoords, size_t count, const float *texCoords, int texUnitIdx, float r, float g, float b, float a)
        {
            size_t indexOff = 0;
            size_t firstOff = 0;

            unsigned char vertexConst[8];
            vertexConst[0] = (unsigned char)(r*255.0f);
            vertexConst[1] = (unsigned char)(g*255.0f);
            vertexConst[2] = (unsigned char)(b*255.0f);
            vertexConst[3] = (unsigned char)(a*255.0f);
            (*(float *)(vertexConst + 4)) = (float)texUnitIdx;

            addVertexFn addVertex;
            if (texCoords)
                addVertex = addTexturedVertex;
            else
                addVertex = addUntexturedVertex;

            const size_t numTriangles = (count / 3) - 2;

            const float *vertexCoords0 = vertexCoords;
            const float *texCoords0 = texCoords;

            vertexCoords += 6;
            texCoords += 4;

            bool isFirstTriangle = true;
            for (int i = 0; i < numTriangles; i++) {
                if (renderBuffer->remaining() < (VERTEX_SIZE * 3) ||
                    indexBuffer->remaining() < 3) {

                    flush();

                    isFirstTriangle = true;
                }

                // if this is the first triangle being written to the vertex buffer then
                // we will need to pre-fill it with the last two vertices
                if (isFirstTriangle) {
                    // first vertex for triangle fan
                    firstOff = renderBuffer->used() / VERTEX_SIZE;
                    addVertex((float *)renderBuffer->ptr,
                        vertexCoords0,
                        texCoords0,
                        vertexConst);

                    renderBuffer->ptr += VERTEX_SIZE;

                    // last vertex used
                    addVertex((float *)renderBuffer->ptr,
                        vertexCoords - 3,
                        texCoords - 2,
                        vertexConst);

                    renderBuffer->ptr += VERTEX_SIZE;

                    // update the index offset
                    indexOff = renderBuffer->used() / VERTEX_SIZE;

                    isFirstTriangle = false;
                }

                // add current vertex
                addVertex((float *)renderBuffer->ptr, vertexCoords, texCoords, vertexConst);

                vertexCoords += 3;
                texCoords += 2;

                renderBuffer->ptr += VERTEX_SIZE;

                // indices
                *indexBuffer->ptr++ = firstOff;
                *indexBuffer->ptr++ = indexOff - 1;
                *indexBuffer->ptr++ = indexOff;

                indexOff++;
            }
        }

        void GLRenderBatch3D::addIndexedTriangleFanImpl(const float *vertexCoords, size_t count, const short *indices, size_t indexCount, const float *texCoords, int texUnitIdx, float r, float g, float b, float a)
        {
            addVertexFn addVertex;
            if (texCoords)
                addVertex = addTexturedVertex;
            else
                addVertex = addUntexturedVertex;

            size_t indexOff = renderBuffer->used() / VERTEX_SIZE;

            unsigned char vertexConst[8];
            vertexConst[0] = (unsigned char)(r*255.0f);
            vertexConst[1] = (unsigned char)(g*255.0f);
            vertexConst[2] = (unsigned char)(b*255.0f);
            vertexConst[3] = (unsigned char)(a*255.0f);
            (*(float *)(vertexConst + 4)) = (float)texUnitIdx;

            const size_t numVerts = count / 3;
            for (int i = 0; i < numVerts; i++) {
                // vertex
                addVertex((float *)renderBuffer->ptr,
                    vertexCoords,
                    texCoords,
                    vertexConst);

                renderBuffer->ptr += VERTEX_SIZE;
                vertexCoords += 3;
                texCoords += 2;
            }

            // advance to the third index
            indices += 2;

            const size_t numTriangles = indexCount - 2;
            for (int i = 0; i < numTriangles; i++) {
                *indexBuffer->ptr++ = indexOff + indices[0];
                *indexBuffer->ptr++ = indexOff + indices[i - 1];
                *indexBuffer->ptr++ = indexOff + indices[i];
            }
        }





        /*********************************************************************/
        // Public API for Batch additions



        void GLRenderBatch3D::addLine(const float x0, const float y0, const float z0, const float x1, const float y1, const float z1, const float width, const float r, const float g, const float b, const float a)
        {
            if (renderBuffer->remaining() < (VERTEX_SIZE * 4) ||
                indexBuffer->remaining() < VERTICES_PER_SPRITE) {

                flush();
            }

            uint8_t *rbufPtr = renderBuffer->ptr;
            short *indexPtr = indexBuffer->ptr;

            float *fPtr = (float *)rbufPtr;

            unsigned char vertexConst[8];
            vertexConst[0] = (unsigned char)(r*255.0f);
            vertexConst[1] = (unsigned char)(g*255.0f);
            vertexConst[2] = (unsigned char)(b*255.0f);
            vertexConst[3] = (unsigned char)(a*255.0f);
            (*(float *)(vertexConst + 4)) = -1; // untextured

            addLineImpl(fPtr, indexPtr, renderBuffer->used() / VERTEX_SIZE, vertexConst, width, x0, y0, z0, x1, y1, z1);
            renderBuffer->ptr += (VERTEX_SIZE * 4);
            indexBuffer->ptr += VERTICES_PER_SPRITE;
        }


        void GLRenderBatch3D::addLines(const float *lines, size_t lineCount, float width, float r, float g, float b, float a)
        {
            unsigned char vertexConst[8];
            vertexConst[0] = (unsigned char)(r*255.0f);
            vertexConst[1] = (unsigned char)(g*255.0f);
            vertexConst[2] = (unsigned char)(b*255.0f);
            vertexConst[3] = (unsigned char)(a*255.0f);
            (*(float *)(vertexConst + 4)) = -1; // untextured

            addLinesImpl(width, lineCount, lines, 6, vertexConst);
        }

        void GLRenderBatch3D::addLineStrip(const float *linestrip, size_t count, float width, float r, float g, float b, float a)
        {
            unsigned char vertexConst[8];
            vertexConst[0] = (unsigned char)(r*255.0f);
            vertexConst[1] = (unsigned char)(g*255.0f);
            vertexConst[2] = (unsigned char)(b*255.0f);
            vertexConst[3] = (unsigned char)(a*255.0f);
            (*(float *)(vertexConst + 4)) = -1; // untextured


            const size_t numLines = (count / 3) - 1;

            addLinesImpl(width, numLines, linestrip, 3, vertexConst);
        }

        void GLRenderBatch3D::addLineLoop(const float *lineLoop, size_t count, float width, float r, float g, float b, float a)
        {
            unsigned char vertexConst[8];
            vertexConst[0] = (unsigned char)(r*255.0f);
            vertexConst[1] = (unsigned char)(g*255.0f);
            vertexConst[2] = (unsigned char)(b*255.0f);
            vertexConst[3] = (unsigned char)(a*255.0f);
            (*(float *)(vertexConst + 4)) = -1; // untextured

            const size_t numLines = (count / 3) - 1;

            addLinesImpl(width, numLines, lineLoop, 3, vertexConst);
            if (numLines > 0) {
                if (renderBuffer->remaining() < (VERTEX_SIZE * 4) ||
                    indexBuffer->remaining() < VERTICES_PER_SPRITE) {

                    flush();
                }

                addLineImpl((float *)renderBuffer->ptr,
                    indexBuffer->ptr,
                    renderBuffer->used() / VERTEX_SIZE,
                    vertexConst,
                    width,
                    lineLoop[numLines * 3], lineLoop[numLines * 3 + 1], lineLoop[numLines * 3 + 2], // last
                    lineLoop[0], lineLoop[1], lineLoop[2]); // first

                renderBuffer->ptr += (VERTEX_SIZE * 4);
                indexBuffer->ptr += VERTICES_PER_SPRITE;
            }
        }

        void GLRenderBatch3D::addTriangles(const float *vertexCoords, size_t count, float r, float g, float b, float a)
        {
            addTrianglesImpl(vertexCoords, count, NULL, -1, r, g, b, a);
        }

        void GLRenderBatch3D::addTriangles(const float *vertexCoords, size_t count, const short *indices, size_t indexCount, float r, float g, float b, float a) throw (std::invalid_argument)
        {
            if (indexCount > indexBuffer->size)
                throw std::invalid_argument("Too many indices");
            size_t requiredVertexSize = (count / 3 * VERTEX_SIZE);
            if (requiredVertexSize > renderBuffer->size)
                throw std::invalid_argument("Too many vertices");

            if (renderBuffer->remaining() < requiredVertexSize || indexBuffer->remaining() < indexCount)
                flush();

            addIndexedTrianglesImpl(vertexCoords, count, indices, indexCount, NULL, -1, r, g, b, a);
        }

        void GLRenderBatch3D::addTriangleStrip(const float *vertexCoords, size_t count, float r, float g, float b, float a)
        {
            addTriangleStripImpl(vertexCoords, count, NULL, -1, r, g, b, a);
        }

        void GLRenderBatch3D::addTriangleStrip(const float *vertexCoords, size_t count, const short *indices, size_t indexCount, float r, float g, float b, float a)
        {
            addIndexedTriangleStripImpl(vertexCoords, count, indices, indexCount, NULL, -1, r, g, b, a);
        }

        void GLRenderBatch3D::addTrianglesSprite(const float *vertexCoords,
            size_t count, const float *texCoords, int textureId,
            float r, float g, float b, float a)
        {
            int textureUnitIdx = bindTexture(textureId);
            addTrianglesImpl(vertexCoords, count, texCoords, textureUnitIdx, r, g, b, a);
        }

        void GLRenderBatch3D::addTrianglesSprite(const float *vertexCoords,
            size_t count,
            const short *indices,
            size_t indexCount,
            const float *texCoords,
            int textureId,
            float r, float g, float b, float a) throw (std::invalid_argument)
        {
            size_t requiredIndices = indexCount;
            if (requiredIndices > indexBuffer->size)
                throw std::invalid_argument("too many indices specified");
            size_t requiredVertexSize = (count / 3 * VERTEX_SIZE);
            if (requiredVertexSize > renderBuffer->size)
                throw std::invalid_argument("too many vertices specified");

            if (renderBuffer->remaining() < requiredVertexSize || indexBuffer->remaining() < requiredIndices)
                flush();

            int textureUnitIdx = bindTexture(textureId);
            addIndexedTrianglesImpl(vertexCoords, count, indices, indexCount, texCoords, textureUnitIdx, r, g, b, a);
        }

        void GLRenderBatch3D::addTriangleStripSprite(const float *vertexCoords, size_t count,
            const float *texCoords,
            int textureId,
            float r, float g, float b, float a)
        {
            int textureUnitIdx = bindTexture(textureId);

            addTriangleStripImpl(vertexCoords, count, texCoords, textureUnitIdx, r, g, b, a);
        }

        void GLRenderBatch3D::addTriangleStripSprite(const float *vertexCoords,
            size_t count,
            const short *indices,
            size_t indexCount,
            const float *texCoords,
            int textureId,
            float r, float g, float b, float a) throw (std::invalid_argument)
        {
            size_t requiredIndices = (indexCount - 2) * 3;
            if (requiredIndices > indexBuffer->size)
                throw std::invalid_argument("Too many indices");
            size_t requiredVertexSize = (count / 3 * VERTEX_SIZE);
            if (requiredVertexSize > renderBuffer->size)
                throw std::invalid_argument("Too many vertices");

            if (renderBuffer->remaining() < requiredVertexSize || indexBuffer->remaining() < requiredIndices)
                flush();

            int textureUnitIdx = bindTexture(textureId);
            addIndexedTriangleStripImpl(vertexCoords, count, indices, indexCount, texCoords, textureUnitIdx, r, g, b, a);
        }

        void GLRenderBatch3D::addTriangleFan(const float *vertexCoords, size_t count, float r, float g, float b, float a)
        {
            addTriangleFanImpl(vertexCoords, count, NULL, -1, r, g, b, a);
        }

        void GLRenderBatch3D::addTriangleFan(const float *vertexCoords, size_t count, const short *indices, size_t indexCount, float r, float g, float b, float a)
        {
            size_t requiredIndices = (count - 2) * 3;
            if (requiredIndices > indexBuffer->size)
                throw std::invalid_argument("Too many indices");
            size_t requiredVertexSize = (count / 3 * VERTEX_SIZE);
            if (requiredVertexSize > renderBuffer->size)
                throw std::invalid_argument("Too many vertices");

            if (renderBuffer->remaining() < requiredVertexSize || indexBuffer->remaining() < requiredIndices)
                flush();

            addIndexedTriangleFanImpl(vertexCoords, count, indices, indexCount, NULL, -1, r, g, b, a);
        }

        void GLRenderBatch3D::addTriangleFanSprite(const float *vertexCoords, size_t count,
            const float *texCoords, int textureId,
            float r, float g, float b, float a)
        {
            int textureUnitIdx = bindTexture(textureId);

            addTriangleFanImpl(vertexCoords, count, texCoords, textureUnitIdx, r, g, b, a);
        }


        void GLRenderBatch3D::addTriangleFanSprite(const float *vertexCoords,
            size_t count,
            const short *indices,
            size_t indexCount,
            const float *texCoords,
            int textureId,
            float r, float g, float b, float a) throw (std::invalid_argument)
        {
            size_t requiredIndices = (indexCount - 2) * 3;
            if (requiredIndices > indexBuffer->size)
                throw std::invalid_argument("Too many indices");
            size_t requiredVertexSize = (count / 3 * VERTEX_SIZE);
            if (requiredVertexSize > renderBuffer->size)
                throw std::invalid_argument("Too many vertices");

            if (renderBuffer->remaining() < requiredVertexSize || indexBuffer->remaining() < requiredIndices)
                flush();

            int textureUnitIdx = bindTexture(textureId);

            addIndexedTriangleFanImpl(vertexCoords, count, indices, indexCount, texCoords, textureUnitIdx, r, g, b, a);
        }


        void GLRenderBatch3D::addSprite(const float x1, const float y1, const float z1,
                                        const float x2, float y2, const float z2,
                                        const float u1, const float v1,
                                        const float u2, const float v2,
                                        const int textureId,
                                        const float r, const float g, const float b, const float a)
        {
            // XXX - z coords lie on plane and unspecified corners should be
            //       interpolated
            addSprite(x1, y1, z1,
                      x2, y1, z1,
                      x2, y2, z2,
                      x1, y2, z2,
                      u1, v1,
                      u2, v2,
                      textureId,
                      r, g, b, a);
        }



        void GLRenderBatch3D::addSprite(const float x1, const float y1, const float z1,
                                        const float x2, const float y2, const float z2,
                                        const float x3, const float y3, const float z3,
                                        const float x4, const float y4, const float z4,
                                        const float u1, const float v1,
                                        const float u2, const float v2,
                                        const int textureId,
                                        const float r, const float g, const float b, const float a)
        {
            if (renderBuffer->remaining() < (VERTEX_SIZE * 4) ||
                indexBuffer->remaining() < VERTICES_PER_SPRITE) {

                flush();
            }

            int textureUnitIdx = bindTexture(textureId);

            const size_t indexOff = renderBuffer->used() / VERTEX_SIZE;

            unsigned char vertexConst[8];
            vertexConst[0] = (unsigned char)(r*255.0f);
            vertexConst[1] = (unsigned char)(g*255.0f);
            vertexConst[2] = (unsigned char)(b*255.0f);
            vertexConst[3] = (unsigned char)(a*255.0f);
            (*(float *)(vertexConst + 4)) = (float)textureUnitIdx;

            // vertices

            float *pfRenderBuffer = reinterpret_cast<float *>(renderBuffer->ptr);
            *pfRenderBuffer++ = x1; // vertex
            *pfRenderBuffer++ = y1;
            *pfRenderBuffer++ = z1;
            *pfRenderBuffer++ = u1; // texture
            *pfRenderBuffer++ = v1;
            memcpy(pfRenderBuffer, vertexConst, 8); // color + tex unit
            pfRenderBuffer += 2;

            *pfRenderBuffer++ = x2; // vertex
            *pfRenderBuffer++ = y2;
            *pfRenderBuffer++ = z2;
            *pfRenderBuffer++ = u2; // texture
            *pfRenderBuffer++ = v1;
            memcpy(pfRenderBuffer, vertexConst, 8); // color + tex unit
            pfRenderBuffer += 2;

            *pfRenderBuffer++ = x3; // vertex
            *pfRenderBuffer++ = y3;
            *pfRenderBuffer++ = z3;
            *pfRenderBuffer++ = u2; // texture
            *pfRenderBuffer++ = v2;
            memcpy(pfRenderBuffer, vertexConst, 8); // color + tex unit
            pfRenderBuffer += 2;

            *pfRenderBuffer++ = x4; // vertex
            *pfRenderBuffer++ = y4;
            *pfRenderBuffer++ = z4;
            *pfRenderBuffer++ = u1; // texture
            *pfRenderBuffer++ = v2;
            memcpy(pfRenderBuffer, vertexConst, 8); // color + tex unit
            pfRenderBuffer += 2;

            renderBuffer->ptr += (VERTEX_SIZE * 4);

            // indices
            short *psIndexBuffer = indexBuffer->ptr;
            *psIndexBuffer++ = indexOff + 3; // 4
            *psIndexBuffer++ = indexOff + 0; // 1
            *psIndexBuffer++ = indexOff + 2; // 3

            *psIndexBuffer++ = indexOff + 0; // 1
            *psIndexBuffer++ = indexOff + 2; // 3
            *psIndexBuffer++ = indexOff + 1; // 2

            indexBuffer->ptr += VERTICES_PER_SPRITE;
        }





        /*********************************************************************/
        // Other public API

        int GLRenderBatch3D::getBatchTextureUnitLimit()
        {
            std::size_t batchTextureUnitLimit;
            GLMapRenderGlobals_getTextureUnitLimit(&batchTextureUnitLimit);
            return std::min((int)batchTextureUnitLimit, INTERNAL_TEXTURE_UNIT_LIMIT);
        }

        void GLRenderBatch3D::setBatchTextureUnitLimit(int limit)
        {
            GLMapRenderGlobals_setTextureUnitLimit(limit);
        }

        /*********************************************************************/
        // Other private utilties

        int GLRenderBatch3D::bindTexture(int textureId) {
            // if the Untextured hint was specified, but the client began
            // texturing, end the current batch and restart with the
            // textured program
            if (hasBits(this->batchHints, Hints::Untextured)) {
                this->end();
                this->begin(this->batchHints&~Hints::Untextured);
            }

            for (int i = numActiveTexUnits - 1; i >= 0; i--)
                if (texUnitIdxToTexId[i] == textureId)
                    return i;
            // if all available texture units are bound we need to flush the batch
            // before we can bind the new texture
            if (numActiveTexUnits >= getBatchTextureUnitLimit())
                flush();

            int retval = numActiveTexUnits;
            GLES20FixedPipeline::getInstance()->glActiveTexture(GL_TEXTURE_UNITS[retval]);
            glBindTexture(GL_TEXTURE_2D, textureId);
            texUnitIdxToTexId[numActiveTexUnits++] = textureId;

            return retval;
        }


        /*********************************************************************/
        // OffsetBuffer utility class

        template<class T>
        OffsetBuffer<T>::OffsetBuffer(size_t size) : size(size)
        {
            base = new T[size];
            ptr = base;
        }

        template <class T>
        OffsetBuffer<T>::~OffsetBuffer()
        {
            delete[] base;
        }

        template <class T>
        void OffsetBuffer<T>::reset()
        {
            ptr = base;
        }

        template <class T>
        size_t OffsetBuffer<T>::used()
        {
            return ptr - base;
        }

        template <class T>
        size_t OffsetBuffer<T>::remaining()
        {
            return size - used();
        }




    }
}

namespace
{
    bool hasBits(int v, int m)
    {
        return !!(v&m);
    }
}

