#ifndef TAK_ENGINE_RENDERER_BITMAP2_H_INCLUDED
#define TAK_ENGINE_RENDERER_BITMAP2_H_INCLUDED

#include <memory>

#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            class ENGINE_API Bitmap2
            {
            public :
                typedef std::unique_ptr<uint8_t, void(*)(const uint8_t *)> DataPtr;
            public :
                enum Format
                {
                    ARGB32,
                    RGBA32,
                    RGB24,
                    RGB565,
                    RGBA5551,
                    MONOCHROME,
                    MONOCHROME_ALPHA,
                    BGRA32,
                    BGR24,
                };
            public :
                Bitmap2() NOTHROWS;
                Bitmap2(std::size_t width, const std::size_t height, const Format format) NOTHROWS;
                Bitmap2(DataPtr &&data, const std::size_t width, const std::size_t height, const Format format) NOTHROWS;
                Bitmap2(DataPtr &&data, const std::size_t width, const std::size_t height, const std::size_t stride, const Format format) NOTHROWS;
                Bitmap2(const Bitmap2 &other) NOTHROWS;
                Bitmap2(const Bitmap2 &other, const std::size_t width, const std::size_t height) NOTHROWS;
                Bitmap2(const Bitmap2 &other, const std::size_t width, const std::size_t height, const Format format) NOTHROWS;
                Bitmap2(const Bitmap2 &other, const Format format) NOTHROWS;
            public :
                Util::TAKErr subimage(std::unique_ptr<Bitmap2, void(*)(const Bitmap2 *)> &value, const std::size_t x, const std::size_t y, const std::size_t width, const std::size_t h, const bool share = false) NOTHROWS;
                /**
                 * Copies the content of the specified bitmap into the specified location of this bitmap. The full extent of the other bitmap is copied. Any necessary format conversions are performed.
                 */
                Util::TAKErr setRegion(const Bitmap2 &other, const std::size_t x, const std::size_t y) NOTHROWS;
                /**
                 * Copies the specified region of the other bitmap into the pixels at the specified location of this bitmap. Any necessary format conversions are performed.
                 */
                Util::TAKErr setRegion(const Bitmap2 &other, const std::size_t dstX, const std::size_t dstY, const std::size_t srcX, const std::size_t srcY, const std::size_t w, const std::size_t h) NOTHROWS;
            public :
                uint8_t *getData() NOTHROWS;
                const uint8_t *getData() const NOTHROWS;
                std::size_t getWidth() const NOTHROWS;
                std::size_t getHeight() const NOTHROWS;
                std::size_t getStride() const NOTHROWS;
                Format getFormat() const NOTHROWS;
            public :
                Bitmap2 &operator=(const Bitmap2 &other);
            private :
                std::size_t width;
                std::size_t height;
                Format format;
                std::size_t stride;
                DataPtr dataPtr;
            };

            typedef std::unique_ptr<Bitmap2, void(*)(const Bitmap2 *)> BitmapPtr;
            typedef std::unique_ptr<const Bitmap2, void(*)(const Bitmap2 *)> BitmapPtr_const;

            ENGINE_API Util::TAKErr Bitmap2_createBuffer(Bitmap2::DataPtr &value, std::size_t width, std::size_t height, Bitmap2::Format format);
            ENGINE_API Util::TAKErr Bitmap2_formatPixelSize(std::size_t *value, const Bitmap2::Format format) NOTHROWS;
        }
    }
}

#endif
