#ifndef TAK_ENGINE_RENDERER_GLTEXTURE2_H_INCLUDED
#define TAK_ENGINE_RENDERER_GLTEXTURE2_H_INCLUDED

#include "renderer/GL.h"

#include "port/Platform.h"
#include "renderer/Bitmap2.h"
#include "math/Point.h"
#include "util/MemBuffer2.h"

#include "renderer/Bitmap.h"
#include "util/Memory.h"

namespace TAK
{
    namespace Engine
    {
        namespace Renderer
        {
            class ENGINE_API GLTexture2
            {
            public:
                GLTexture2(const size_t width, const size_t height, int format, int type) NOTHROWS;
                GLTexture2(const size_t width, const size_t height, Bitmap2::Format format) NOTHROWS;
                ~GLTexture2() NOTHROWS;
            public :
                int getType() const NOTHROWS;
                int getFormat() const NOTHROWS;
                bool isCompressed() const NOTHROWS;

                /**
                 * Returns the texture ID, possibly applying any outstanding state changes
                 * @return
                 */
                int getTexId() NOTHROWS;
                /**
                 * Returns the texture ID.
                 * @return
                 */
                int getTexId() const NOTHROWS;
                size_t getTexWidth() const NOTHROWS;
                size_t getTexHeight() const NOTHROWS;
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
                void load(const void *data, const int x, const int y, const size_t w, const size_t h) NOTHROWS;

                // XXX - 
                void load(const atakmap::renderer::Bitmap &bitmap, const int x, const int y) NOTHROWS;
                void load(const atakmap::renderer::Bitmap &bitmap) NOTHROWS;

                void release() NOTHROWS;

                void draw(const std::size_t numCoords, const int type, const void *textureCoordinates, const void *vertexCoordinates) const NOTHROWS;
                void draw(const std::size_t numCoords, const int texType, const void *textureCoordinates, const int vertType, const void *vertexCoordinates) const NOTHROWS;
            private :
                bool initInternal() NOTHROWS;
            private:
                GLuint id_;
                int format_;
                int type_;
                std::size_t width_;
                std::size_t height_;
                int min_filter_;
                int mag_filter_;
                int wrap_s_;
                int wrap_t_;
                bool needs_apply_;
                bool compressed_;

                void apply() NOTHROWS;

            //    friend ENGINE_API Util::TAKErr GLTexture2_createCompressedTexture(std::unique_ptr<GLTexture2, void(*)(const GLTexture2 *)> &, const Bitmap2 &) NOTHROWS;
				friend ENGINE_API Util::TAKErr GLTexture2_createCompressedTexture(std::unique_ptr<GLTexture2, void(*)(const GLTexture2 *)> &value, const struct GLCompressedTextureData &data) NOTHROWS;
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
                                 const std::size_t texSize, const int texType, const void *textureCoordinates,
                                 const std::size_t vertSize, const int vertType, const void *vertexCoordinates,
                                 const float red, const float green, const float blue, const float alpha) NOTHROWS;

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

			ENGINE_API
            void GLTexture2_draw(const int texId,
                                 const int mode,
                                 const std::size_t numCoords,
                                 const std::size_t texSize, const int texType, const void *textureCoordinates,
                                 const std::size_t vertSize, const int vertType, const void *vertexCoordinates,
                                 const int idxType, const void *indices,
                                 const float red, const float green,
                                 const float blue, const float alpha) NOTHROWS;

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
			ENGINE_API Util::TAKErr GLTexture2_createQuadMeshIndexBuffer(Util::MemBuffer2 &value, const int type, const std::size_t numCellsX, const std::size_t numCellsY) NOTHROWS;

			ENGINE_API Util::TAKErr GLTexture2_getFormatAndDataType(int *format, int *dataType, const Bitmap2::Format fmt) NOTHROWS;

			ENGINE_API Util::TAKErr GLTexture2_getBitmapFormat(Bitmap2::Format *value, const int format, const int dataType) NOTHROWS;

			struct GLCompressedTextureData {
				TAK::Engine::Util::array_ptr<unsigned char> compressedData;
				std::size_t compressedSize {0};
				std::size_t alignedW {0};
				std::size_t alignedH {0};
				GLenum glalg {0};
				Bitmap2::Format cbfmt {};
			};

			ENGINE_API Util::TAKErr GLTexture2_createCompressedTextureData(std::unique_ptr<GLCompressedTextureData, void(*)(GLCompressedTextureData *)> &data, const Bitmap2 &bitmap) NOTHROWS;
			ENGINE_API Util::TAKErr GLTexture2_createCompressedTexture(GLTexture2Ptr &value, const GLCompressedTextureData &data) NOTHROWS;

            /**
             * <P>Must be invoked on thread with valid GL context
             */
            ENGINE_API Util::TAKErr GLTexture2_createCompressedTexture(GLTexture2Ptr &value, const Bitmap2 &bitmap) NOTHROWS;
        }
    }
}
#endif
