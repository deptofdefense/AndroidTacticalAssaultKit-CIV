#ifdef MSVC

#include "renderer/BitmapFactory2.h"

#include <Windows.h>
#include <ObjIdl.h>
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

// SDK includes
#include "renderer/impl/BitmapAdapter_MSVC.h"
#include "util/DataOutput2.h"
#include "util/Memory.h"

using namespace TAK::Engine::Renderer;

using namespace TAK::Engine::Renderer::Impl;
using namespace TAK::Engine::Util;

namespace
{
    void handle_deleter(HGLOBAL handle)
    {
        ::GlobalFree(handle);
    }
    void istream_deleter(IStream *stream)
    {
        stream->Release();
    }

}

TAKErr TAK::Engine::Renderer::BitmapFactory2_decode(BitmapPtr &result, DataInput2 &input, const BitmapDecodeOptions *opts) NOTHROWS
{
    TAKErr code(TE_Ok);
    DynamicOutput membuf;
    int64_t len = input.length();
    code = membuf.open(len > 0 ? len : 64*1024);
    TE_CHECKRETURN_CODE(code);
    code = IO_copy(membuf, input);
    TE_CHECKRETURN_CODE(code);

    const uint8_t *data;
    std::size_t dataLen;
    code = membuf.get(&data, &dataLen);
    TE_CHECKRETURN_CODE(code);

    std::unique_ptr<void, void(*)(HGLOBAL)> bufferHandle(::GlobalAlloc(GMEM_MOVEABLE, dataLen), handle_deleter);
    if (!bufferHandle.get())
        return TE_Err;

    class HandleLock
    {
    public :
        HandleLock(HGLOBAL handle_) :
            handle(handle_),
            data(::GlobalLock(handle_))
        {}
        ~HandleLock() NOTHROWS
        {
            ::GlobalUnlock(handle);

        }
    public :
        void *get() NOTHROWS
        {
            return data;
        }
    private :
        HGLOBAL handle;
        void *data;
    };

    {
        HandleLock lock(bufferHandle.get());
        if (!lock.get())
            return TE_Err;
        CopyMemory(lock.get(), data, dataLen);
        IStream *stream = NULL;
        if (::CreateStreamOnHGlobal(bufferHandle.get(), FALSE, &stream) != S_OK)
            return TE_Err;
        std::unique_ptr<IStream, void(*)(IStream *)> streamPtr(stream, istream_deleter);
        std::unique_ptr<Gdiplus::Bitmap> bitmap(Gdiplus::Bitmap::FromStream(streamPtr.get()));
        if (!bitmap.get())
            return TE_Err;
        if (bitmap->GetLastStatus() != Gdiplus::Ok)
            return TE_Err;

        code = BitmapAdapter_adapt(result, *bitmap);
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}


TAKErr TAK::Engine::Renderer::BitmapFactory2_decode(BitmapPtr &result, const char *bitmapFilePath, const BitmapDecodeOptions *opts) NOTHROWS
{
    TAKErr code(TE_Ok);

    Util::FileInput2 input;
    code = input.open(bitmapFilePath);
    TE_CHECKRETURN_CODE(code);

    code = BitmapFactory2_decode(result, input, opts);
    TE_CHECKRETURN_CODE(code);

    code = input.close();

    return code;
}


#endif
