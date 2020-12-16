#include "renderer/impl/BitmapAdapter_MSVC.h"

#ifdef MSVC

#include <Windows.h>
#include <ObjIdl.h>

#pragma warning(push)
#pragma warning(disable : 4458)
#ifdef NOMINMAX
#define min(a, b) ((a)<(b) ? (a) : (b))
#define max(a, b) ((a)<(b) ? (a) : (b))
#include <gdiplus.h>
#pragma comment (lib, "Gdiplus.lib")
#include <gdiplusheaders.h>
#include <gdipluspixelformats.h>
#undef min
#undef max
#else
#include <gdiplus.h>
#pragma comment (lib, "Gdiplus.lib")
#include <gdiplusheaders.h>
#include <gdipluspixelformats.h>
#endif
#pragma warning(pop)

// SDK includes
#include "util/IO2.h"
#include "util/Memory.h"

using namespace TAK::Engine::Renderer::Impl;

using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Util;

namespace
{
    class ImageBitLocker
    {
    public:
        ImageBitLocker(Gdiplus::Bitmap &bitmap_) NOTHROWS;
        ~ImageBitLocker() NOTHROWS;
    public:
        void *lock() NOTHROWS;
        int stride() NOTHROWS;
        void unlock() NOTHROWS;
    private:
        Gdiplus::Bitmap &bitmap;
        Gdiplus::BitmapData data;
        bool locked;
    };
}

TAKErr TAK::Engine::Renderer::Impl::BitmapAdapter_adapt(BitmapPtr &value, Gdiplus::Bitmap &srcBitmap) NOTHROWS
{
    // XXX - implement a limit to workaround GDI+ OOM pending GDAL impl
    std::unique_ptr<Gdiplus::Bitmap, void(*)(const Gdiplus::Bitmap *)> bitmap(&srcBitmap, Memory_leaker_const<Gdiplus::Bitmap>);
#define BITMAP_ADAPT_LIMIT 2048.0
    if (bitmap->GetWidth() > BITMAP_ADAPT_LIMIT || bitmap->GetHeight() > BITMAP_ADAPT_LIMIT) {
        const double sx = BITMAP_ADAPT_LIMIT / (double)bitmap->GetWidth();
        const double sy = BITMAP_ADAPT_LIMIT / (double)bitmap->GetHeight();
        const double scaleFactor = (sx < sy) ? sx : sy;
        std::unique_ptr<Gdiplus::Bitmap> resized(new Gdiplus::Bitmap((int)ceil(scaleFactor*bitmap->GetWidth()), (int)ceil(scaleFactor*bitmap->GetHeight()), bitmap->GetPixelFormat()));

        std::unique_ptr<Gdiplus::Graphics> g(Gdiplus::Graphics::FromImage(resized.get()));
        g->DrawImage(bitmap.get(), 0, 0, resized->GetWidth(), resized->GetHeight());
        g.reset();

        bitmap = std::unique_ptr<Gdiplus::Bitmap, void(*)(const Gdiplus::Bitmap *)>(resized.release(), Memory_deleter_const<Gdiplus::Bitmap>);
    }

    // lock the bits and copy access the data
    Gdiplus::PixelFormat srcFmt = bitmap->GetPixelFormat();
    Bitmap2::Format dstFmt;
    std::size_t bytes;
    bool endianFlip = (TE_PlatformEndian == TE_LittleEndian);



    switch (srcFmt) {
        // Good reference: http://bobpowell.net/lockingbits.aspx
    //case System::Drawing::Imaging::PixelFormat::Format32bppArgb:
    case PixelFormat32bppARGB:
        // BGRA in memory on little endian
        if (TE_PlatformEndian == TE_LittleEndian)
            dstFmt = Bitmap2::BGRA32;
        else
            dstFmt = Bitmap2::ARGB32;
        bytes = 4u;
        endianFlip = false;
        break;
        //case System::Drawing::Imaging::PixelFormat::Format24bppRgb:
    case PixelFormat24bppRGB:
        // BGR in memory on little endian
        if (TE_PlatformEndian == TE_LittleEndian)
            dstFmt = Bitmap2::BGR24;
        else
            dstFmt = Bitmap2::RGB24;
        bytes = 3u;
        endianFlip = false;
        break;
        //case System::Drawing::Imaging::PixelFormat::Format16bppRgb565:
    case PixelFormat16bppRGB565:
        // BGR565 in memory on little endian
        dstFmt = Bitmap2::RGB565;
        bytes = 2u;
        break;
    default:
        std::unique_ptr<Gdiplus::Bitmap> argb(new Gdiplus::Bitmap(bitmap->GetWidth(), bitmap->GetHeight(), PixelFormat32bppARGB));

        std::unique_ptr<Gdiplus::Graphics> g(Gdiplus::Graphics::FromImage(argb.get()));
        g->DrawImage(bitmap.get(), 0, 0);

        // recurse and return
        return BitmapAdapter_adapt(value, *argb);
    }



    ImageBitLocker bitlocker(*bitmap);

    Bitmap2::DataPtr srcData((uint8_t *)bitlocker.lock(), Memory_leaker_const<uint8_t>);
    if (!srcData.get())
        return TE_Err;
    if (endianFlip) {
        array_ptr<uint8_t> flipped(new uint8_t[bitmap->GetWidth()*bitmap->GetHeight()*bytes]);
        uint8_t *pSrc;
        uint8_t *pFlipped = flipped.get();
        if (bytes == 2) {
            for (unsigned int y = 0; y < bitmap->GetHeight(); y++) {
                // reset source to start of stride
                pSrc = srcData.get() + (y*bitlocker.stride());
                for (unsigned int x = 0; x < bitmap->GetWidth(); x++) {
                    pFlipped[0] = pSrc[1];
                    pFlipped[1] = pSrc[0];

                    // advance 'src' and 'flipped' pointers
                    pSrc += 2;
                    pFlipped += 2;
                }
            }
        }
        else if (bytes == 4) {
            for (unsigned int y = 0; y < bitmap->GetHeight(); y++) {
                // reset source to start of stride
                pSrc = srcData.get() + (y*bitlocker.stride());
                for (unsigned int x = 0; x < bitmap->GetWidth(); x++) {
                    pFlipped[0] = pSrc[3];
                    pFlipped[1] = pSrc[2];
                    pFlipped[2] = pSrc[1];
                    pFlipped[3] = pSrc[0];

                    // advance 'src' and 'flipped' pointers
                    pSrc += 4;
                    pFlipped += 4;
                }
            }
        }
        else {
            return TE_IllegalState;
        }

        srcData = Bitmap2::DataPtr(flipped.release(), Memory_array_deleter_const<uint8_t>);
    }
    else {
        array_ptr<uint8_t> data(new uint8_t[bitlocker.stride()*bitmap->GetHeight()]);
        memcpy(data.get(), srcData.get(), (bitmap->GetHeight()*bitlocker.stride()));
        srcData = Bitmap2::DataPtr(data.release(), Memory_array_deleter_const<uint8_t>);
    }

    value = BitmapPtr(new Bitmap2(std::move(srcData),
        bitmap->GetWidth(),
        bitmap->GetHeight(),
        bitlocker.stride(),
        dstFmt),
        Memory_deleter_const<Bitmap2>);
    bitlocker.unlock();

    return TE_Ok;
}

namespace
{
    ImageBitLocker::ImageBitLocker(Gdiplus::Bitmap &bitmap_) NOTHROWS :
        bitmap(bitmap_),
        data(),
        locked(false)
    {}
    ImageBitLocker::~ImageBitLocker() NOTHROWS
    {
        unlock();
    }
    void *ImageBitLocker::lock() NOTHROWS
    {
        if (locked)
            return data.Scan0;
        Gdiplus::Rect region(0, 0, bitmap.GetWidth(), bitmap.GetHeight());
        locked = (bitmap.LockBits(&region, Gdiplus::ImageLockModeRead, bitmap.GetPixelFormat(), &data) == Gdiplus::Ok);
        if (!locked)
            return nullptr;
        return data.Scan0;
    }
    int ImageBitLocker::stride() NOTHROWS
    {
        lock();
        return data.Stride;
    }
    void ImageBitLocker::unlock() NOTHROWS
    {
        if (!locked)
            return;
        bitmap.UnlockBits(&data);
        locked = false;
    }
}

#endif
