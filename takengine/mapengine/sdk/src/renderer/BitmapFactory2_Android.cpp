#ifdef __ANDROID__

#include "renderer/BitmapFactory2.h"

using namespace TAK::Engine::Renderer;

using namespace TAK::Engine::Util;

TAKErr TAK::Engine::Renderer::BitmapFactory2_decode(BitmapPtr &result, DataInput2 &input, const BitmapDecodeOptions *opts) NOTHROWS
{
    return TE_Unsupported;
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
