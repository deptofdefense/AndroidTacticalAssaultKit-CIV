#ifndef TAK_ENGINE_RENDERER_GLTEXTURE2_H_INCLUDED
#define TAK_ENGINE_RENDERER_GLTEXTURE2_H_INCLUDED

#include "renderer/GL.h"

#include "port/Platform.h"
#include "renderer/Bitmap2.h"
#include "math/Point.h"

#include "renderer/Bitmap.h"

namespace TAK
{
    namespace Engine
    {
        namespace Renderer
        {
            class ENGINE_API GLTexture2
            {
            public:
                GLTexture2(int width, int height, int format, int type) NOTHROWS;
                GLTexture2(int width, int height, Bitmap2::Format format) NOTHROWS;
                ~GLTexture2() NOTHROWS;
            public :
                int getType() const NOTHROWS;
                int getFormat() const NOTHROWS;
                bool isCompressed() const NOTHROWS;

                int getTexId() NOTHROWS;
                int getTexWidth() const NOTHROWS;
                int getTexHeight() const NOTHROWS;
                int getWrapT() const NOTHROWS;
                int getWrapS() const NOTHROWS;
                int getMagFilter() const NOTHROWS;
                int getMinFilter() const NOTHROWS;

                void setWrapS(int wrapS) NOTHROWS;
                void setWrapT(int wrapT) NOTHROWS;
                void setMinFilter(int minFilter) NOTHROWS;
                void setMagFilter(int magFilter) NOTHROWS;

                void init() NOTHROWS;

                Util::TAKErr load(const Bitmap2 &bitmap, const int x, const int y) NOTHROWS;
                Util::TAKErr load(const Bitmap2 &bitmap) NOTHROWS;
                void load(const void *data, const int x, const int y, const int w, const int h) NOTHROWS;

                // XXX - 
                void load(const atakmap::renderer::Bitmap &bitmap, const int x, const int y) NOTHROWS;
                void load(const atakmap::renderer::Bitmap &bitmap) NOTHROWS;

                void release() NOTHROWS;

                void draw(const std::size_t numCoords, const int type, const void *textureCoordinates, const void *vertexCoordinates) const NOTHROWS;
                void draw(const std::size_t numCoords, const int texType, const void *textureCoordinates, const int vertType, const void *vertexCoordinates) const NOTHROWS;
            private :
                bool initInternal() NOTHROWS;
            private:
                GLuint id;
                int format, type;
                std::size_t width, height;
                int minFilter;
                int magFilter;
                int wrapS;
                int wrapT;
                bool needsApply;
                bool compressed;

                void apply() NOTHROWS;

                friend ENGINE_API Util::TAKErr GLTexture2_createCompressedTexture(std::unique_ptr<GLTexture2, void(*)(const GLTexture2 *)> &, const Bitmap2 &) NOTHROWS;
            };

            typedef std::unique_ptr<GLTexture2, void(*)(const GLTexture2 *)> GLTexture2Ptr;

			ENGINE_API
            void GLTexture2_draw(const int texId,
                                 const int mode,
                                 const std::size_t numCoords,
                                 const int texType, const void *textureCoordinates,
                                 const int vertType, const void *vertexCoordinates) NOTHROWS;

			ENGINE_API
            void GLTexture2_draw(const int texId,
                                 const int mode,
                                 const std::size_t numCoords,
                                 const std::size_t texSize, const int texType, const void *textureCoordinates,
                                 const std::size_t vertSize, const int vertType, const void *vertexCoordinates) NOTHROWS;

			ENGINE_API
            void GLTexture2_draw(const int texId,
                                 const int mode,
                                 const std::size_t numCoords,
                                 const int texType, const void *textureCoordinates,
                                 const int vertType, const void *vertexCoordinates,
                                 const int idxType, const void *indices) NOTHROWS;

			ENGINE_API
            void GLTexture2_draw(const int texId,
                                 const int mode,
                                 const std::size_t numCoords,
                                 const std::size_t texSize, const int texType, const void *textureCoordinates,
                                 const std::size_t vertSize, const int vertType, const void *vertexCoordinates,
                                 const int idxType, const void *indices) NOTHROWS;

			ENGINE_API std::size_t GLTexture2_getNumQuadMeshVertices(const std::size_t numCellsX, const std::size_t numCellsY) NOTHROWS;

			ENGINE_API
            Util::TAKErr GLTexture2_createQuadMeshTexCoords(float *buffer,
                                                            const atakmap::math::Point<float> &upperLeft,
                                                            const atakmap::math::Point<float> &upperRight,
                                                            const atakmap::math::Point<float> &lowerRight,
                                                            const atakmap::math::Point<float> &lowerLeft,
                                                            const std::size_t numCellsX, const std::size_t numCellsY) NOTHROWS;

			ENGINE_API
            Util::TAKErr GLTexture2_createQuadMeshTexCoords(float *buffer,
                                                            const float width, const float height,
                                                            const std::size_t numCellsX, const std::size_t numCellsY) NOTHROWS;

			ENGINE_API std::size_t GLTexture2_getNumQuadMeshIndices(const std::size_t numCellsX, const std::size_t numCellsY) NOTHROWS;

			ENGINE_API void GLTexture2_createQuadMeshIndexBuffer(uint16_t *buffer, const std::size_t numCellsX, const std::size_t numCellsY) NOTHROWS;

			ENGINE_API Util::TAKErr GLTexture2_getFormatAndDataType(int *format, int *dataType, const Bitmap2::Format fmt) NOTHROWS;

			ENGINE_API Util::TAKErr GLTexture2_getBitmapFormat(Bitmap2::Format *value, const int format, const int dataType) NOTHROWS;

            /**
             * <P>Must be invoked on thread with valid GL context
             */
            ENGINE_API Util::TAKErr GLTexture2_createCompressedTexture(GLTexture2Ptr &value, const Bitmap2 &bitmap) NOTHROWS;
        }
    }
}
#endif
