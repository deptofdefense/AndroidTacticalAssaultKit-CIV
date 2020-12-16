#ifndef TAK_ENGINE_RENDERER_IMPL_BITMAPADAPTER_MSVC_H_INCLUDED
#define TAK_ENGINE_RENDERER_IMPL_BITMAPADAPTER_MSVC_H_INCLUDED

#ifdef MSVC
#include "renderer/Bitmap2.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace Gdiplus {
    class Bitmap;
}

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Impl {
                ENGINE_API Util::TAKErr BitmapAdapter_adapt(BitmapPtr &dst, Gdiplus::Bitmap &src) NOTHROWS;
            }
        }
    }
}
#endif

#endif
