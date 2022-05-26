#ifndef ATAKMAP_RENDERER_GLRENDERBATCH2_H_INCLUDED
#define ATAKMAP_RENDERER_GLRENDERBATCH2_H_INCLUDED

#include <cinttypes>
#include <string>
#include <memory>

#include "port/Platform.h"
#include "renderer/GL.h"
#include "util/Error.h"
#include "util/MemBuffer2.h"
#include "util/Memory.h"

#ifndef GL_MODELVIEW
#define GL_MODELVIEW 0x00001700
#endif

#ifndef GL_PROJECTION
#define GL_PROJECTION 0x00001701
#endif

namespace TAK {
    namespace Engine {
        namespace Renderer {


            class ENGINE_API GLRenderBatch2
            {
            private :
                struct batch_render_program_t
                {
                public :
                    batch_render_program_t(const std::size_t size) NOTHROWS;
                public :
                    /** vertex size; number of elements per vertex coord */
                    std::size_t size;

                    // The GL program-related handles
                    int handle;
                    // attribute/uniform handles are -1 if not defined
                    int uProjectionHandle;
                    int uModelViewHandle;
                    int aVertexCoordsHandle;
                    int aColorHandle;
                    int aTextureCoordsHandle;
                    int aTexUnitHandle;
                };
                class MatrixStack
                {
                public :
                    MatrixStack(const std::size_t count) NOTHROWS;
                public :
                    Util::TAKErr push() NOTHROWS;
                    Util::TAKErr pop() NOTHROWS;
                    void reset() NOTHROWS;
                private :
                    const std::size_t count;
                    std::unique_ptr<float, void(*)(const float *)> buffer;
                public :
                    float *pointer;
                };
            public :
                enum Hints
                {
                    Untextured          = 0x00000001,
                    SoftwareTransforms  = 0x00000002,
                    Lines               = 0x00000004,
                    TwoDimension        = 0x00000008,
                };

            public:
                GLRenderBatch2(const std::size_t capacity) NOTHROWS;
                ~GLRenderBatch2() NOTHROWS;
            private :
                GLRenderBatch2(const GLRenderBatch2 &) NOTHROWS;
            public :
                Util::TAKErr begin() NOTHROWS;
                Util::TAKErr begin(const int hints) NOTHROWS;
                Util::TAKErr end() NOTHROWS;
                Util::TAKErr release() NOTHROWS;
            public : // transform operations
                Util::TAKErr setMatrix(const int mode, const float *mx) NOTHROWS;
                Util::TAKErr pushMatrix(const int mode) NOTHROWS;
                Util::TAKErr popMatrix(const int mode) NOTHROWS;
            public :
                Util::TAKErr setLineWidth(const float width) NOTHROWS;
            public :
                Util::TAKErr batch(const int texId,
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
                                   const float a) NOTHROWS;
                Util::TAKErr batch(const int texId,
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
                                   const float a) NOTHROWS;
            private :
                Util::TAKErr addLines(const std::size_t size, const std::size_t stride, const float *lines, const std::size_t lineCount, const float width, const float r, const float g, const float b, const float a) NOTHROWS;
                Util::TAKErr addLineStrip(const std::size_t size, const std::size_t stride, const float *linestrip, const std::size_t count, const float width, const float r, const float g, const float b, const float a) NOTHROWS;
                Util::TAKErr addLineLoop(const std::size_t size, const std::size_t stride, const float *lineLoop, const std::size_t count, const float width, const float r, const float g, const float b, const float a) NOTHROWS;
                Util::TAKErr addTriangles(const std::size_t size, const std::size_t vertexStride, const float *vertexCoords, const std::size_t count, const float r, const float g, const float b, const float a) NOTHROWS;
                Util::TAKErr addTriangles(const std::size_t size, const std::size_t vertexStride, const float *vertexCoords, const std::size_t count, const unsigned short *indices, const std::size_t indexCount, const float r, const float g, const float b, const float a) NOTHROWS;
                Util::TAKErr addTriangleStrip(const std::size_t size, const std::size_t vertexStride, const float *vertexCoords, const std::size_t count, const float r, const float g, const float b, const float a) NOTHROWS;
                Util::TAKErr addTriangleStrip(const std::size_t size, const std::size_t vertexStride, const float *vertexCoords, const std::size_t count, const unsigned short *indices, const std::size_t indexCount, const float r, const float g, const float b, const float a) NOTHROWS;
                Util::TAKErr addTriangleFan(const std::size_t size, const std::size_t vertexStride, const float *vertexCoords, const std::size_t count, const float r, const float g, const float b, const float a) NOTHROWS;
                Util::TAKErr addTriangleFan(const std::size_t size, const std::size_t vertexStride, const float *vertexCoords, const std::size_t count, const unsigned short *indices, const std::size_t indexCount, const float r, const float g, const float b, const float a) NOTHROWS;
                Util::TAKErr addTrianglesSprite(const std::size_t size, const std::size_t vertexStride, const float *vertexCoords, const std::size_t count,
                    const std::size_t texCoordStride, const float *texCoords,
                    const int textureId, const float r, const float g, const float b, const float a) NOTHROWS;
                Util::TAKErr addTrianglesSprite(const std::size_t size, const std::size_t vertexStride, const float *vertexCoords,
                    const std::size_t count,
                    const unsigned short *indices,
                    const std::size_t indexCount,
                    const std::size_t texCoordStride, const float *texCoords,
                    const int textureId,
                    const float r, const float g, const float b, const float a) NOTHROWS;
                Util::TAKErr addTriangleStripSprite(const std::size_t size, const std::size_t vertexStride, const float *vertexCoords, const std::size_t count,
                    const std::size_t texCoordStride, const float *texCoords, const int textureId,
                    const float r, const float g, const float b, const float a) NOTHROWS;
                Util::TAKErr addTriangleStripSprite(const std::size_t size, const std::size_t vertexStride, const float *vertexCoords,
                    std::size_t count,
                    const unsigned short *indices,
                    const std::size_t indexCount,
                    const std::size_t texCoordStride, const float *texCoords,
                    const int textureId,
                    const float r, const float g, const float b, const float a) NOTHROWS;
                Util::TAKErr addTriangleFanSprite(const std::size_t size, const std::size_t vertexStride, const float *vertexCoords, const std::size_t count,
                    const std::size_t texCoordStride, const float *texCoords, int textureId,
                    const float r, const float g, const float b, const float a) NOTHROWS;
                Util::TAKErr addTriangleFanSprite(const std::size_t size, const std::size_t vertexStride, const float *vertexCoords,
                    const std::size_t count,
                    const unsigned short *indices,
                    const std::size_t indexCount,
                    const std::size_t texCoordStride, const float *texCoords,
                    const int textureId,
                    const float r, const float g, const float b, const float a) NOTHROWS;
            private :
                Util::TAKErr initPrograms() NOTHROWS;
                std::string getFragmentShaderSrc() NOTHROWS;

                Util::TAKErr flush() NOTHROWS;

                Util::TAKErr bindTexture(int *value, const int textureId) NOTHROWS;
                Util::TAKErr addLinesImpl(const std::size_t size, const float width, const std::size_t numLines, const std::size_t vStride, const float *lines, const int step, const uint64_t vertexConst) NOTHROWS;
                Util::TAKErr addTrianglesImpl(const std::size_t size, const std::size_t vStride, const float *vertexCoords, const std::size_t count, const std::size_t tcStride, const float *texCoords, int texUnitIdx, const float r, const float g, const float b, const float a) NOTHROWS;
                Util::TAKErr addIndexedTrianglesImpl(const std::size_t size, const std::size_t vStride, const float *vertexCoords, const std::size_t count, const unsigned short *indices, const std::size_t indexCount, const std::size_t tcStride, const float *texCoords, int texUnitIdx, const float r, const float g, const float b, const float a) NOTHROWS;
                Util::TAKErr addTriangleStripImpl(const std::size_t size, const std::size_t vStride, const float *vertexCoords, const std::size_t count, const std::size_t tcStride, const float *texCoords, int texUnitIdx, const float r, const float g, const float b, const float a) NOTHROWS;
                Util::TAKErr addIndexedTriangleStripImpl(const std::size_t size, const std::size_t vStride, const float *vertexCoords, const std::size_t count, const unsigned short *indices, const std::size_t indexCount, const std::size_t tcStride, const float *texCoords, int texUnitIdx, const float r, const float g, const float b, const float a) NOTHROWS;
                Util::TAKErr addTriangleFanImpl(const std::size_t size, const std::size_t vStride, const float *vertexCoords, const std::size_t count, const std::size_t tcStride, const float *texCoords, int texUnitIdx, const float r, const float g, const float b, const float a) NOTHROWS;
                Util::TAKErr addIndexedTriangleFanImpl(const std::size_t size, const std::size_t vStride, const float *vertexCoords, const std::size_t count, const unsigned short *indices, const std::size_t indexCount, const std::size_t tcStride, const float *texCoords, int texUnitIdx, const float r, const float g, const float b, const float a) NOTHROWS;
                void validateMVP() NOTHROWS;
            private :
                static Util::TAKErr batchProgramInit(batch_render_program_t *program, const bool textured, const char *vertShaderSrc, const char *fragShaderSrc) NOTHROWS;
            private:
                Util::MemBuffer2 renderBuffer;
                Util::MemBuffer2 indexBuffer;
                MatrixStack projection;
                MatrixStack modelView;
                MatrixStack texture;
                float lineWidth;
                float viewportWidth;
                float viewportHeight;

                batch_render_program_t untexturedProgram2d;
                batch_render_program_t texturedProgram2d;
                batch_render_program_t untexturedProgram3d;
                batch_render_program_t texturedProgram3d;

                Util::array_ptr<int> texUnitIdxToTexId;
                int numActiveTexUnits;
                int originalTextureUnit;

                int batchHints;

                float modelViewProjection[16];
                bool mvpDirty;
            };

            ENGINE_API std::size_t GLRenderBatch2_getBatchTextureUnitLimit() NOTHROWS;
            ENGINE_API void GLRenderBatch2_setBatchTextureUnitLimit(const std::size_t limit) NOTHROWS;
        }
    }
}

#endif
