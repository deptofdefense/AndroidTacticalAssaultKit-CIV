#include "renderer/Bitmap2.h"

#include "util/IO.h"
#include "util/Memory.h"

/*
ARGB32, 4
RGBA32, 4
RGB24,  3
RGB565,  2
RGB5551, 2
MONOCHROME, 1
MONOCHROME_ALPHA, 2
BGRA32 4
BGR32 3
*/

using namespace TAK::Engine::Renderer;

using namespace TAK::Engine::Util;

#define NUM_FORMATS 9

namespace
{
    // format conversion functions
    typedef void(*FormatConverter)(uint8_t *, const std::size_t, const uint8_t *, const std::size_t, const std::size_t, const std::size_t);

#define CONVERSION_FN_DECL(srcFmt, dstFmt) \
    void srcFmt##to##dstFmt(uint8_t *dst, const std::size_t dstStride, const uint8_t *src, const std::size_t srcStride, const std::size_t w, const std::size_t h);

#define CONVERSION_FN_DECLS(srcFmt) \
    CONVERSION_FN_DECL(srcFmt, ARGB32) \
    CONVERSION_FN_DECL(srcFmt, RGBA32) \
    CONVERSION_FN_DECL(srcFmt, RGB24) \
    CONVERSION_FN_DECL(srcFmt, RGB565) \
    CONVERSION_FN_DECL(srcFmt, RGBA5551) \
    CONVERSION_FN_DECL(srcFmt, MONOCHROME) \
    CONVERSION_FN_DECL(srcFmt, MONOCHROME_ALPHA) \
    CONVERSION_FN_DECL(srcFmt, BGRA32) \
    CONVERSION_FN_DECL(srcFmt, BGR24)

    CONVERSION_FN_DECLS(ARGB32);
    CONVERSION_FN_DECLS(RGBA32);
    CONVERSION_FN_DECLS(RGB24);
    CONVERSION_FN_DECLS(RGB565);
    CONVERSION_FN_DECLS(RGBA5551);
    CONVERSION_FN_DECLS(MONOCHROME);
    CONVERSION_FN_DECLS(MONOCHROME_ALPHA);
    CONVERSION_FN_DECLS(BGRA32);
    CONVERSION_FN_DECLS(BGR24);

#undef CONVERSION_FN_DECLS
#undef CONVERSION_FN_DECL

    void resize(uint8_t *dst, const std::size_t dstW, const std::size_t dstH, const std::size_t dstStride, const uint8_t *src, const std::size_t srcW, const std::size_t srcH, const std::size_t srcStride, const std::size_t pixelBytes) NOTHROWS;

    std::size_t formatSize[NUM_FORMATS] =
    { 4,  // ARGB32
      4,  // RGBA32
      3,  // RGB24
      2,  // RGB565
      2,  // RGBA5551
      1,  // MONOCHROME
      2,  // MONOCHROME_ALPHA
      4,  // BGRA32
      3,  // BGR24
    };

    FormatConverter conversionFunctions[NUM_FORMATS *NUM_FORMATS] =
    {
#define CONVERSIONS(fmt) \
    fmt##toARGB32, fmt##toRGBA32,fmt##toRGB24, fmt##toRGB565, fmt##toRGBA5551, fmt##toMONOCHROME, fmt##toMONOCHROME_ALPHA, fmt##toBGRA32, fmt##toBGR24,

        CONVERSIONS(ARGB32)
        CONVERSIONS(RGBA32)
        CONVERSIONS(RGB24)
        CONVERSIONS(RGB565)
        CONVERSIONS(RGBA5551)
        CONVERSIONS(MONOCHROME)
        CONVERSIONS(MONOCHROME_ALPHA)
        CONVERSIONS(BGRA32)
        CONVERSIONS(BGR24)
#undef CONVERSIONS
    };
}

Bitmap2::Bitmap2() NOTHROWS :
    width(0),
    height(0),
    format(ARGB32),
    stride(0),
    dataPtr(nullptr, nullptr)
{}

Bitmap2::Bitmap2(const std::size_t width_, const std::size_t height_, const Format format_) NOTHROWS :
    width(width_),
    height(height_),
    format(format_),
    stride(width_*formatSize[format_]),
    dataPtr(nullptr, nullptr)
{
    Bitmap2_createBuffer(dataPtr, width, height, format);
}

Bitmap2::Bitmap2(DataPtr &&data_, const std::size_t width_, const std::size_t height_, const Format format_) NOTHROWS :
    width(width_),
    height(height_),
    format(format_),
    stride(width_*formatSize[format_]),
    dataPtr(std::move(data_))
{}

Bitmap2::Bitmap2(DataPtr &&data_, const std::size_t width_, const std::size_t height_, const std::size_t stride_, const Format format_) NOTHROWS :
    width(width_),
    height(height_),
    format(format_),
    stride(stride_),
    dataPtr(std::move(data_))
{}

Bitmap2::Bitmap2(const Bitmap2 &other) NOTHROWS :
    width(other.width),
    height(other.height),
    format(other.format),
    stride(other.width*formatSize[other.format]),
    dataPtr(nullptr, nullptr)
{
    Bitmap2_createBuffer(dataPtr, width, height, format);

    conversionFunctions[other.format*NUM_FORMATS + this->format](this->dataPtr.get(), this->stride, other.dataPtr.get(), other.stride, this->width, this->height);
}

Bitmap2::Bitmap2(const Bitmap2 &other, const std::size_t width_, const std::size_t height_) NOTHROWS :
    width(width_),
    height(height_),
    format(other.format),
    stride(width_*formatSize[other.format]),
    dataPtr(nullptr, nullptr)
{
    Bitmap2_createBuffer(dataPtr, width, height, format);

    // resize
    resize(this->dataPtr.get(),
           this->width,
           this->height,
           this->stride,
           other.dataPtr.get(),
           other.width,
           other.height,
           other.stride,
           formatSize[this->format]);
}

Bitmap2::Bitmap2(const Bitmap2 &other, const std::size_t width_, const std::size_t height_, const Format format_) NOTHROWS :
    width(width_),
    height(height_),
    format(format_),
    stride(width_*formatSize[format_]),
    dataPtr(nullptr, nullptr)
{
    Bitmap2_createBuffer(dataPtr, width, height, format);

    // resize other
    Bitmap2 otherResized(other, this->width, this->height);

    // format conversion
    conversionFunctions[otherResized.format*NUM_FORMATS + this->format](this->dataPtr.get(), this->stride, otherResized.dataPtr.get(), otherResized.stride, this->width, this->height);
}

Bitmap2::Bitmap2(const Bitmap2 &other, const Format format_) NOTHROWS :
    width(other.width),
    height(other.height),
    format(format_),
    stride(other.width*formatSize[format_]),
    dataPtr(nullptr, nullptr)
{
    Bitmap2_createBuffer(dataPtr, width, height, format);

    conversionFunctions[other.format*NUM_FORMATS + this->format](this->dataPtr.get(), this->stride, other.dataPtr.get(), other.stride, this->width, this->height);
}

TAKErr Bitmap2::subimage(BitmapPtr &value, const std::size_t x, const std::size_t y, const std::size_t w, const std::size_t h, const bool share) NOTHROWS
{
    if ((x + w) > this->width)
        return TE_InvalidArg;
    if ((y + h) > this->height)
        return TE_InvalidArg;

    std::size_t off = (y*this->stride) + (x*formatSize[this->format]);

    if (share) {
        DataPtr subdata(this->dataPtr.get() + off, Memory_leaker_const<uint8_t>);
        value = BitmapPtr(new Bitmap2(std::move(subdata), w, h, this->stride, this->format), Memory_deleter_const<Bitmap2>);
    } else {
        DataPtr subdata(nullptr, nullptr);
        Bitmap2_createBuffer(subdata, w, h, this->format);

        const std::size_t dstStride = w*formatSize[this->format];
        for (std::size_t i = 0; i < h; i++) {
            memcpy(subdata.get() + (i*dstStride), this->dataPtr.get() + (((y + i)*this->stride) + (x*formatSize[this->format])), dstStride);
        }

        value = BitmapPtr(new Bitmap2(std::move(subdata), w, h, this->format), Memory_deleter_const<Bitmap2>);
    }

    return TE_Ok;
}

TAKErr Bitmap2::setRegion(const Bitmap2 &other, const std::size_t x, const std::size_t y) NOTHROWS
{
    return setRegion(other, x, y, 0, 0, other.width, other.height);
}

TAKErr Bitmap2::setRegion(const Bitmap2 &other, const std::size_t dstX, const std::size_t dstY, const std::size_t srcX, const std::size_t srcY, const std::size_t w, const std::size_t h) NOTHROWS
{
    if ((srcX + w) > other.width)
        return TE_InvalidArg;
    if ((srcY + h) > other.height)
        return TE_InvalidArg;

    if ((dstX + w) > this->width)
        return TE_InvalidArg;
    if ((dstY + h) > this->height)
        return TE_InvalidArg;

    if (other.format == this->format) {
        const std::size_t pxsz = formatSize[this->format];
        for (std::size_t i = 0; i < h; i++) {
            memcpy(this->dataPtr.get() + ((dstY + i)*this->stride) + (dstX*pxsz),
                   other.dataPtr.get() + ((srcY + i)*other.stride) + (srcX*pxsz),
                   w*pxsz);
        }
    } else {
        // use format conversion as copy
        const std::size_t dstOff = (dstY*this->stride) + (dstX*formatSize[this->format]);
        const std::size_t srcOff = (srcY*other.stride) + (srcX*formatSize[other.format]);
        conversionFunctions[other.format*NUM_FORMATS + this->format](this->dataPtr.get() + dstOff, this->stride, other.dataPtr.get() + srcOff, other.stride, w, h);
    }

    return TE_Ok;
}

uint8_t *Bitmap2::getData() NOTHROWS
{
    return this->dataPtr.get();
}
const uint8_t *Bitmap2::getData() const NOTHROWS
{
    return this->dataPtr.get();
}
std::size_t Bitmap2::getWidth() const NOTHROWS
{
    return this->width;
}
std::size_t Bitmap2::getHeight() const NOTHROWS
{
    return this->height;
}
std::size_t Bitmap2::getStride() const NOTHROWS
{
    return this->stride;
}
Bitmap2::Format Bitmap2::getFormat() const NOTHROWS
{
    return this->format;
}

Bitmap2 &Bitmap2::operator=(const Bitmap2 &other)
{
    this->width = other.width;
    this->height = other.height;
    this->format = other.format;
    this->stride = this->width*formatSize[this->format];
    Bitmap2_createBuffer(this->dataPtr, this->width, this->height, this->format);
    conversionFunctions[(other.format*NUM_FORMATS) + this->format](this->dataPtr.get(), this->stride, other.dataPtr.get(), other.stride, this->width, this->height);

    return *this;
}

TAKErr TAK::Engine::Renderer::Bitmap2_createBuffer(Bitmap2::DataPtr &value, std::size_t width, std::size_t height, Bitmap2::Format format)
{
    if (width == 0 && height == 0) {
        value = Bitmap2::DataPtr(nullptr, nullptr);
        return TE_Ok;
    } else if (width == 0 || height == 0) {
        return TE_InvalidArg;
    } else {
        value = Bitmap2::DataPtr(new uint8_t[width*height*formatSize[format]], Memory_array_deleter_const<uint8_t>);
        return TE_Ok;
    }
}

TAKErr TAK::Engine::Renderer::Bitmap2_formatPixelSize(std::size_t *value, const Bitmap2::Format format) NOTHROWS
{
    if ((int)format < 0 || (int)format >= NUM_FORMATS)
        return TE_InvalidArg;
    *value = formatSize[format];
    return TE_Ok;
}

#ifdef _MSC_VER
#pragma warning(push)
#pragma warning(disable : 4101)
#endif

namespace
{
#define CONVERSION_FN_DEFN(srcFmt, dstFmt, conv) \
    void srcFmt##to##dstFmt(uint8_t *dst, const std::size_t dstStride, const uint8_t *src, const std::size_t srcStride, const std::size_t w, const std::size_t h) \
    { \
        uint8_t *pDst; \
        const uint8_t *pSrc; \
        const std::size_t srcStep = formatSize[Bitmap2::srcFmt]; \
        const std::size_t dstStep = formatSize[Bitmap2::dstFmt]; \
        uint8_t r, g, b, a, mono; \
        const uint8_t endianXor = (atakmap::util::PlatformEndian == atakmap::util::LITTLE_ENDIAN) ? 0x01u : 0x00u; \
        if(dstStep <= srcStep) { \
            for(std::size_t y = 0; y < h; y++) { \
                pDst = dst+(y*dstStride); \
                pSrc = src+(y*srcStride); \
                for (std::size_t x = 0; x < w; x++) { \
                    conv; \
                    pSrc += srcStep; \
                    pDst += dstStep; \
                } \
            } \
        } else { \
            for(std::size_t y = h; y > 0; y--) { \
                pDst = dst+((y-1u)*dstStride) + ((w-1u)*dstStep); \
                pSrc = src+((y-1u)*srcStride) + ((w-1u)*srcStep); \
                for (std::size_t x = w; x > 0; x--) { \
                    conv; \
                    pSrc -= srcStep; \
                    pDst -= dstStep; \
                } \
            } \
        } \
    }

#define CONVERSION_FN_DEFN_COPY(fmt) \
    void fmt##to##fmt(uint8_t *dst, const std::size_t dstStride, const uint8_t *src, const std::size_t srcStride, const std::size_t w, const std::size_t h) \
    { \
        const std::size_t copySize = w*formatSize[Bitmap2::fmt]; \
        for(std::size_t y = 0; y < h; y++) { \
            memcpy(dst+(y*dstStride), src+(y*srcStride), copySize); \
        } \
    }

#define CONVERSION_FN_TO_MONO() \
    mono = (uint8_t)((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);

#define CONVERSION_FN_DEFN_TO_ARGB32(srcFmt, get_r, get_g, get_b, get_a) \
    CONVERSION_FN_DEFN(srcFmt, ARGB32, { get_r; get_g; get_b; get_a; pDst[0] = a; pDst[1] = r; pDst[2] = g; pDst[3] = b; } );
#define CONVERSION_FN_DEFN_TO_RGBA32(srcFmt, get_r, get_g, get_b, get_a) \
    CONVERSION_FN_DEFN(srcFmt, RGBA32, { get_r; get_g; get_b; get_a; pDst[0] = r; pDst[1] = g; pDst[2] = b; pDst[3] = a; } );
#define CONVERSION_FN_DEFN_TO_RGB24(srcFmt, get_r, get_g, get_b, get_a) \
    CONVERSION_FN_DEFN(srcFmt, RGB24, { get_r; get_g; get_b; pDst[0] = r; pDst[1] = g; pDst[2] = b; } );
#define CONVERSION_FN_DEFN_TO_RGB565(srcFmt, get_r, get_g, get_b, get_a) \
    CONVERSION_FN_DEFN(srcFmt, RGB565, { get_r; get_g; get_b; pDst[0^endianXor] = (uint8_t)((r << 3) | (g >> 3)); pDst[1^endianXor] = (uint8_t)((g << 5) | b); } );
#define CONVERSION_FN_DEFN_TO_RGBA5551(srcFmt, get_r, get_g, get_b, get_a) \
    CONVERSION_FN_DEFN(srcFmt, RGBA5551, { get_r; get_g; get_b; get_a; pDst[0^endianXor] = (uint8_t)((r << 3) | (g >> 2)); pDst[1^endianXor] = (uint8_t)((g << 6) | (b << 1) | a); } );
#define CONVERSION_FN_DEFN_TO_MONOCHROME(srcFmt, get_r, get_g, get_b, get_a) \
    CONVERSION_FN_DEFN(srcFmt, MONOCHROME, { get_r; get_g; get_b; CONVERSION_FN_TO_MONO(); pDst[0] = mono; } );
#define CONVERSION_FN_DEFN_TO_MONOCHROME_ALPHA(srcFmt, get_r, get_g, get_b, get_a) \
    CONVERSION_FN_DEFN(srcFmt, MONOCHROME_ALPHA, { get_r; get_g; get_b; get_a; CONVERSION_FN_TO_MONO(); pDst[0] = mono; pDst[1] = a; } );
#define CONVERSION_FN_DEFN_TO_BGRA32(srcFmt, get_r, get_g, get_b, get_a) \
    CONVERSION_FN_DEFN(srcFmt, BGRA32, { get_r; get_g; get_b; get_a; pDst[0] = b; pDst[1] = g; pDst[2] = r; pDst[3] = a; } );
#define CONVERSION_FN_DEFN_TO_BGR24(srcFmt, get_r, get_g, get_b, get_a) \
    CONVERSION_FN_DEFN(srcFmt, BGR24, { get_r; get_g; get_b; pDst[0] = b; pDst[1] = g; pDst[2] = r; } );

#define CONVERSION_FN_DEFNS(srcFmt, get_r_impl, get_g_impl, get_b_impl, get_a_impl, dstFmta, dstFmtb, dstFmtc, dstFmtd, dstFmte, dstFmtf, dstFmtg, dstFmth) \
    CONVERSION_FN_DEFN_COPY(srcFmt) \
    CONVERSION_FN_DEFN_TO_##dstFmta(srcFmt, get_r_impl, get_g_impl, get_b_impl, get_a_impl) \
    CONVERSION_FN_DEFN_TO_##dstFmtb(srcFmt, get_r_impl, get_g_impl, get_b_impl, get_a_impl) \
    CONVERSION_FN_DEFN_TO_##dstFmtc(srcFmt, get_r_impl, get_g_impl, get_b_impl, get_a_impl) \
    CONVERSION_FN_DEFN_TO_##dstFmtd(srcFmt, get_r_impl, get_g_impl, get_b_impl, get_a_impl) \
    CONVERSION_FN_DEFN_TO_##dstFmte(srcFmt, get_r_impl, get_g_impl, get_b_impl, get_a_impl) \
    CONVERSION_FN_DEFN_TO_##dstFmtf(srcFmt, get_r_impl, get_g_impl, get_b_impl, get_a_impl) \
    CONVERSION_FN_DEFN_TO_##dstFmtg(srcFmt, get_r_impl, get_g_impl, get_b_impl, get_a_impl) \
    CONVERSION_FN_DEFN_TO_##dstFmth(srcFmt, get_r_impl, get_g_impl, get_b_impl, get_a_impl) \

CONVERSION_FN_DEFNS(ARGB32,
                    r = pSrc[1],
                    g = pSrc[2],
                    b = pSrc[3],
                    a = pSrc[0],
                    RGBA32, RGB24, RGB565, RGBA5551, MONOCHROME, MONOCHROME_ALPHA, BGRA32, BGR24)
CONVERSION_FN_DEFNS(RGBA32,
                    r = pSrc[0],
                    g = pSrc[1],
                    b = pSrc[2],
                    a = pSrc[3],
                    ARGB32, RGB24, RGB565, RGBA5551, MONOCHROME, MONOCHROME_ALPHA, BGRA32, BGR24)
CONVERSION_FN_DEFNS(RGB24,
                    r = pSrc[0],
                    g = pSrc[1],
                    b = pSrc[2],
                    a = 0xFF,
                    ARGB32, RGBA32, RGB565, RGBA5551, MONOCHROME, MONOCHROME_ALPHA, BGRA32, BGR24)
CONVERSION_FN_DEFNS(RGB565,
                    r = pSrc[0^endianXor]&0xF8,
                    g = ((pSrc[0^endianXor]&0x07)<<5)|((pSrc[1^endianXor]&0xE0)>>3),
                    b = (pSrc[1^endianXor]&0x1F)<<3,
                    a = 0xFF,
                    ARGB32, RGBA32, RGB24, RGBA5551, MONOCHROME, MONOCHROME_ALPHA, BGRA32, BGR24)
CONVERSION_FN_DEFNS(RGBA5551,
                    r = pSrc[0^endianXor]&0xF8,
                    g = ((pSrc[0^endianXor]&0x07)<<5)|((pSrc[1^endianXor]&0xC0)>>3),
                    b = (pSrc[1^endianXor]&0x1E)<<3,
                    a = (pSrc[1^endianXor]&0x1) ? 0xFF : 0x00,
                    ARGB32, RGBA32, RGB24, RGB565, MONOCHROME, MONOCHROME_ALPHA, BGRA32, BGR24)
CONVERSION_FN_DEFNS(MONOCHROME,
                    r = pSrc[0],
                    g = pSrc[0],
                    b = pSrc[0],
                    a = 0xFF,
                    ARGB32, RGBA32, RGB24, RGB565, RGBA5551, MONOCHROME_ALPHA, BGRA32, BGR24)
CONVERSION_FN_DEFNS(MONOCHROME_ALPHA,
                    r = pSrc[0],
                    g = pSrc[0],
                    b = pSrc[0],
                    a = pSrc[1],
                    ARGB32, RGBA32, RGB24, RGB565, RGBA5551, MONOCHROME, BGRA32, BGR24)
CONVERSION_FN_DEFNS(BGRA32,
                    r = pSrc[2],
                    g = pSrc[1],
                    b = pSrc[0],
                    a = pSrc[3],
                    ARGB32, RGBA32, RGB24, RGB565, RGBA5551, MONOCHROME, MONOCHROME_ALPHA, BGR24)
CONVERSION_FN_DEFNS(BGR24,
                    r = pSrc[2],
                    g = pSrc[1],
                    b = pSrc[0],
                    a = 0xFF,
                    ARGB32, RGBA32, RGB24, RGB565, RGBA5551, MONOCHROME, MONOCHROME_ALPHA, BGRA32)

#undef CONVERSION_FN_DEFN_TO_RGBA32
#undef CONVERSION_FN_DEFN_TO_RGB24
#undef CONVERSION_FN_DEFN_TO_RGB565_LE
#undef CONVERSION_FN_DEFN_TO_RGB565_BE
#undef CONVERSION_FN_DEFN_TO_RGBA5551_LE
#undef CONVERSION_FN_DEFN_TO_RGBA5551_BE
#undef CONVERSION_FN_DEFN_TO_MONOCHROME
#undef CONVERSION_FN_DEFN_TO_MONOCHROME_ALPHA
#undef CONVERSION_FN_DEFN_TO_BGRA32
#undef CONVERSION_FN_DEFN_TO_BGR24

#undef CONVERSION_FN_DEFN

    void resize(uint8_t *dst, const std::size_t dstW, const std::size_t dstH, const std::size_t dstStride, const uint8_t *src, const std::size_t srcW, const std::size_t srcH, const std::size_t srcStride, const std::size_t pixelBytes) NOTHROWS
    {
        uint8_t *pDst;
        std::size_t srcX;
        std::size_t srcY;
        for (std::size_t dstY = 0; dstY < dstH; dstY++) {
            pDst = dst + (dstY*dstStride);

            // compute srcY
            srcY = static_cast<std::size_t>((((double)dstY / (double)dstH) * (double)srcH + 0.5));
            //srcY = dstY;
            if (srcY < 0) srcY = 0;
            else if (srcY >= srcH) srcY = srcH - 1;

            for (std::size_t dstX = 0; dstX < dstW; dstX++) {
                // compute srcX
                srcX = static_cast<std::size_t>((((double)dstX / (double)dstW) * (double)srcW + 0.5));
                //srcX = dstX;
                if (srcX < 0) srcX = 0;
                else if (srcX >= srcW) srcX = srcW - 1;

                memcpy(pDst, src + ((srcY*srcStride) + (srcX*pixelBytes)), pixelBytes);
                pDst += pixelBytes;
            }
        }
    }
}

#ifdef _MSC_VER
#pragma warning(pop)
#endif