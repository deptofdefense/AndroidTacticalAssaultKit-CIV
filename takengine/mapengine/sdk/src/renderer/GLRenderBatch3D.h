#ifndef ATAKMAP_RENDERER_GLRENDERBATCH_H_INCLUDED
#define ATAKMAP_RENDERER_GLRENDERBATCH_H_INCLUDED

#include <cinttypes>
#include <string>

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

        class GLRenderBatch3D
        {
        private :
            struct untextured_batch_render_program_t
            {
                // The GL program-related handles
                int handle;
                int uProjectionHandle;
                int uModelViewHandle;
                int aVertexCoordsHandle;
                int aColorHandle;
            };
            struct textured_batch_render_program_t
            {
                // The GL program-related handles
                int handle;
                int uProjectionHandle;
                int uModelViewHandle;
                int aVertexCoordsHandle;
                int aColorHandle;
                int aTextureCoordsHandle;
                int aTexUnitHandle;
                int *uTextureHandles;
                int numTextureUnitHandles;
            };
            union batch_render_program_u
            {
                textured_batch_render_program_t textured;
                untextured_batch_render_program_t untextured;
            };
        public :
            enum Hints
            {
                Untextured = 0x00000001,
            };

        public:
            static const int INTERNAL_TEXTURE_UNIT_LIMIT;

            GLRenderBatch3D(int capacity);
            ~GLRenderBatch3D();

            void begin();
            void begin(int hints);
            void end();

            void release();

            void addLine(float x0, float y0, float z0, float x1, float y1, float z1, float width, float r, float g, float b, float a);
            void addLines(const float *lines, size_t lineCount, float width, float r, float g, float b, float a);
            void addLineStrip(const float *linestrip, size_t count, float width, float r, float g, float b, float a);
            void addLineLoop(const float *lineLoop, size_t count, float width, float r, float g, float b, float a);
            void addTriangles(const float *vertexCoords, size_t count, float r, float g, float b, float a);
            void addTriangles(const float *vertexCoords, size_t count, const short *indices, size_t indexCount, float r, float g, float b, float a) throw (std::invalid_argument);
            void addTriangleStrip(const float *vertexCoords, size_t count, float r, float g, float b, float a);
            void addTriangleStrip(const float *vertexCoords, size_t count, const short *indices, size_t indexCount, float r, float g, float b, float a);
            void addTriangleFan(const float *vertexCoords, size_t count, float r, float g, float b, float a);
            void addTriangleFan(const float *vertexCoords, size_t count, const short *indices, size_t indexCount, float r, float g, float b, float a);
            void addSprite(const float x1, const float y1, const float z1,
                           const float x2, const float y2, const float z2,
                           const float u1, const float v1,
                           const float u2, const float v2,
                           const int textureId,
                           const float r, const float g, const float b, const float a);
            void addSprite(const float x1, const float y1, const float z1,
                           const float x2, const float y2, const float z2,
                           const float x3, const float y3, const float z3,
                           const float x4, const float y4, const float z4,
                           const float u1, const float v1,
                           const float u2, const float v2,
                           const int textureId,
                           const float r, const float g, const float b, const float a);
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
            OffsetBuffer<uint8_t> *renderBuffer;
            OffsetBuffer<short> *indexBuffer;

            untextured_batch_render_program_t untexturedProgram;
            textured_batch_render_program_t texturedProgram;
            int *texUnitIdxToTexId;
            int numActiveTexUnits;
            int originalTextureUnit;

            int batchHints;

            void initPrograms();
            std::string getFragmentShaderSrc();

            void flush();

            int bindTexture(int textureId);
            void addLinesImpl(const float width, const size_t numLines, const float *lines, const int step, unsigned char *vertexConst);
            void addTrianglesImpl(const float *vertexCoords, size_t count, const float *texCoords, int texUnitIdx, float r, float g, float b, float a);
            void addIndexedTrianglesImpl(const float *vertexCoords, size_t count, const short *indices, size_t indexCount, const float *texCoords, int texUnitIdx, float r, float g, float b, float a);
            void addTriangleStripImpl(const float *vertexCoords, size_t count, const float *texCoords, int texUnitIdx, float r, float g, float b, float a);
            void addIndexedTriangleStripImpl(const float *vertexCoords, size_t count, const short *indices, size_t indexCount, const float *texCoords, int texUnitIdx, float r, float g, float b, float a);
            void addTriangleFanImpl(const float *vertexCoords, size_t count, const float *texCoords, int texUnitIdx, float r, float g, float b, float a);
            void addIndexedTriangleFanImpl(const float *vertexCoords, size_t count, const short *indices, size_t indexCount, const float *texCoords, int texUnitIdx, float r, float g, float b, float a);
        };

    }
}


#endif
