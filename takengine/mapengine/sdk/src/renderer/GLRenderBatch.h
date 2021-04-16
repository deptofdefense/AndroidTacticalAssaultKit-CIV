#ifndef ATAKMAP_RENDERER_GLRENDERBATCH_H_INCLUDED
#define ATAKMAP_RENDERER_GLRENDERBATCH_H_INCLUDED

#include <inttypes.h>
#include <string>
#include <stdexcept>

#include "renderer/GLRenderBatch2.h"

namespace TAK
{
    namespace Engine
    {
        namespace Renderer
        {
           class ENGINE_API GLText2;
        }
    }
}

namespace atakmap
{
    namespace renderer
    {
        template<class T> struct OffsetBuffer {
            T * ptr;
            T * base;
            const size_t size;
            OffsetBuffer(size_t size);
            ~OffsetBuffer();
            void reset();
            size_t used();
            size_t remaining();
        };

        class ENGINE_API GLRenderBatch {
            friend class ENGINE_API TAK::Engine::Renderer::GLText2;
        public:
            static const int INTERNAL_TEXTURE_UNIT_LIMIT;

            GLRenderBatch(int capacity);
            ~GLRenderBatch();
            
            void begin();
            void end();

            void addLine(float x0, float y0, float x1, float y1, float width, float r, float g, float b, float a);
            void addLines(const float *lines, size_t lineCount, float width, float r, float g, float b, float a);
            void addLineStrip(const float *linestrip, size_t count, float width, float r, float g, float b, float a);
            void addLineLoop(const float *lineLoop, size_t count, float width, float r, float g, float b, float a);
            void addTriangles(const float *vertexCoords, size_t count, float r, float g, float b, float a);
            void addTriangles(const float *vertexCoords, size_t count, const short *indices, size_t indexCount, float r, float g, float b, float a) throw (std::invalid_argument);
            void addTriangleStrip(const float *vertexCoords, size_t count, float r, float g, float b, float a);
            void addTriangleStrip(const float *vertexCoords, size_t count, const short *indices, size_t indexCount, float r, float g, float b, float a);
            void addTriangleFan(const float *vertexCoords, size_t count, float r, float g, float b, float a);
            void addTriangleFan(const float *vertexCoords, size_t count, const short *indices, size_t indexCount, float r, float g, float b, float a);
            void addSprite(float x1, float y1, float x2, float y2, float u1,
                           float v1, float u2, float v2, 
                           int textureId, float r, float g, float b, float a);
            void addSprite(float x1, float y1,
                           float x2, float y2,
                           float x3, float y3,
                           float x4, float y4,
                           float u1, float v1,
                           float u2, float v2,
                           int textureId, 
                           float r, float g, float b, float a);
            void addTrianglesSprite(const float *vertexCoords, size_t count, 
                                    const float *texCoords,
                                    int textureId, float r, float g, float b, float a);
            void addTrianglesSprite(const float *vertexCoords,
                                    size_t count,
                                    const short *indices,
                                    size_t indexCount,
                                    const float *texCoords,
                                    int textureId,
                                    float r, float g, float b, float a) throw (std::invalid_argument);
            void addTriangleStripSprite(const float *vertexCoords, size_t count,
                                        const float *texCoords, int textureId, 
                                        float r, float g, float b, float a);
            void addTriangleStripSprite(const float *vertexCoords,
                                        size_t count,
                                        const short *indices,
                                        size_t indexCount,
                                        const float *texCoords,
                                        int textureId,
                                        float r, float g, float b, float a) throw (std::invalid_argument);
            void addTriangleFanSprite(const float *vertexCoords, size_t count,
                                      const float *texCoords, int textureId, 
                                      float r, float g, float b, float a);
            void addTriangleFanSprite(const float *vertexCoords,
                                      size_t count,
                                      const short *indices,
                                      size_t indexCount,
                                      const float *texCoords,
                                      int textureId,
                                      float r, float g, float b, float a) throw (std::invalid_argument);

            static int getBatchTextureUnitLimit();
            static void setBatchTextureUnitLimit(int limit);

        private:
            TAK::Engine::Renderer::GLRenderBatch2 impl;

            friend TAK::Engine::Renderer::GLRenderBatch2 &GLRenderBatch_adapt(GLRenderBatch &legacy) NOTHROWS;
        };

        TAK::Engine::Renderer::GLRenderBatch2 &GLRenderBatch_adapt(GLRenderBatch &legacy) NOTHROWS;

    }
}


#endif
