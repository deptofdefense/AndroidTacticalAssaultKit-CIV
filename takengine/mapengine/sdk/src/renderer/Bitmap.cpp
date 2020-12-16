#include "renderer/Bitmap.h"

#include <cassert>

#include "util/Memory.h"

using namespace atakmap::renderer;

using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Util;

namespace
{
    void releaseNone(Bitmap ignored)
    {}

    void releaseDefault(Bitmap bitmap)
    {
        delete[] (uint8_t *)(bitmap.data);
    }

    Bitmap scaleDefault(Bitmap bitmap, int w, int h);
    Bitmap adaptImpl(const Bitmap2 &other, bool ownsData) NOTHROWS;
}

Bitmap atakmap::renderer::Bitmap_adapt(const Bitmap2 &other) NOTHROWS
{
    return Bitmap_adapt(std::move(BitmapPtr_const(&other, Memory_leaker_const<Bitmap2>)));
}

Bitmap atakmap::renderer::Bitmap_adapt(BitmapPtr &&other) NOTHROWS
{
    return Bitmap_adapt(std::move(BitmapPtr_const(other.release(), other.get_deleter())));
}

Bitmap atakmap::renderer::Bitmap_adapt(BitmapPtr_const &&other) NOTHROWS
{
    BitmapPtr_const compatible = std::move(other);
    std::size_t pixelSize;
    Bitmap2_formatPixelSize(&pixelSize, compatible->getFormat());
    if (compatible->getFormat() == Bitmap2::ARGB32)
        compatible = BitmapPtr_const(new Bitmap2(*compatible, Bitmap2::RGBA32), Memory_deleter_const<Bitmap2>);
    else if (compatible->getStride() != (compatible->getWidth()*pixelSize))
        compatible = BitmapPtr_const(new Bitmap2(*compatible), Memory_deleter_const<Bitmap2>);

    // create a new data buffer
    Bitmap2::DataPtr data(nullptr, nullptr);
    Bitmap2_createBuffer(data, compatible->getWidth(), compatible->getHeight(), compatible->getFormat());

    // create the copy using a leaker
    Bitmap2 copy(std::move(Bitmap2::DataPtr(data.get(), Memory_leaker_const<uint8_t>)),
                 compatible->getWidth(),
                 compatible->getHeight(),
                 compatible->getWidth()*pixelSize,
                 compatible->getFormat());
    copy.setRegion(*compatible, 0, 0);

    // adapt
    Bitmap retval = adaptImpl(copy, true);
    // unlock the data buffer since the adapter is created
    data.release();
    return retval;
}

Bitmap2 atakmap::renderer::Bitmap_adapt(const Bitmap &legacy) NOTHROWS
{
    Bitmap2::Format srcFmt;
    switch (legacy.format) {
    case GL_RGBA:
        srcFmt = Bitmap2::RGBA32;
        break;
    case GL_RGB:
        srcFmt = Bitmap2::RGB24;
        break;
    case GL_RGB565:
        srcFmt = Bitmap2::RGB565;
        break;
    case GL_RGB5_A1:
        srcFmt = Bitmap2::RGBA5551;
        break;
    case GL_LUMINANCE:
        srcFmt = Bitmap2::MONOCHROME;
        break;
    case GL_LUMINANCE_ALPHA:
        srcFmt = Bitmap2::MONOCHROME_ALPHA;
        break;
    default:
        assert(false);
        return Bitmap2();
    }

    // the source bitmap -- wraps legacy bitmap
    return Bitmap2(
            Bitmap2(std::move(Bitmap2::DataPtr((uint8_t *)legacy.data, Memory_leaker_const<uint8_t>)),
                    legacy.width,
                    legacy.height,
                    srcFmt));
}

namespace {
    Bitmap scaleDefault(Bitmap bitmap, int w, int h)
    {
        Bitmap2::Format srcFmt;
        switch (bitmap.format) {
        case GL_RGBA:
            srcFmt = Bitmap2::RGBA32;
            break;
        case GL_RGB:
            srcFmt = Bitmap2::RGB24;
            break;
        case GL_RGB565:
            srcFmt = Bitmap2::RGB565;
            break;
        case GL_RGB5_A1:
            srcFmt = Bitmap2::RGBA5551;
            break;
        case GL_LUMINANCE:
            srcFmt = Bitmap2::MONOCHROME;
            break;
        case GL_LUMINANCE_ALPHA:
            srcFmt = Bitmap2::MONOCHROME_ALPHA;
            break;
        default:
            assert(false);
            return Bitmap();
        }

        // the source bitmap -- wraps legacy bitmap
        Bitmap2 src(std::move(Bitmap2::DataPtr((uint8_t *)bitmap.data, Memory_leaker_const<uint8_t>)), bitmap.width, bitmap.height, srcFmt);

        return Bitmap_adapt(std::move(BitmapPtr(new Bitmap2(src, w, h), Memory_deleter_const<Bitmap2>)));
    }

    Bitmap adaptImpl(const Bitmap2 &other, bool ownsData) NOTHROWS
    {
        std::size_t pixelSize;
        Bitmap2_formatPixelSize(&pixelSize, other.getFormat());
        if (other.getStride() != (other.getWidth()*pixelSize) || other.getFormat() == Bitmap2::ARGB32) {
            assert(false);
            return Bitmap();
        } else {
            GLenum dataType;
            GLenum format;

            switch (other.getFormat()) {
            case Bitmap2::RGBA32:
                format = GL_RGBA;
                dataType = GL_UNSIGNED_BYTE;
                break;
            case Bitmap2::RGB24:
                format = GL_RGB;
                dataType = GL_UNSIGNED_BYTE;
                break;
            case Bitmap2::RGB565:
                format = GL_RGB565;
                dataType = GL_UNSIGNED_SHORT_5_6_5;
                break;
            case Bitmap2::RGBA5551:
                format = GL_RGB5_A1;
                dataType = GL_UNSIGNED_SHORT_5_5_5_1;
                break;
            case Bitmap2::MONOCHROME:
                format = GL_LUMINANCE;
                dataType = GL_UNSIGNED_BYTE;
                break;
            case Bitmap2::MONOCHROME_ALPHA:
                format = GL_LUMINANCE_ALPHA;
                dataType = GL_UNSIGNED_BYTE;
                break;
            default:
                assert(false);
                return Bitmap();
            }

            return Bitmap{
                static_cast<int>(other.getWidth()),
                static_cast<int>(other.getHeight()),
                const_cast<uint8_t *>(other.getData()),
                other.getHeight()*other.getStride(),
                dataType,
                format,
                nullptr,
                scaleDefault,
                ownsData ? releaseDefault : releaseNone
            };
        }
    }
}