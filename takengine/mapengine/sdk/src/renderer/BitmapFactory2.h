#ifndef TAK_ENGINE_RENDERER_BITMAPFACTORY2_H_INCLUDED
#define TAK_ENGINE_RENDERER_BITMAPFACTORY2_H_INCLUDED

#include "port/String.h"
#include "renderer/Bitmap2.h"
#include "util/DataInput2.h"
#include "util/FutureTask.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {

            struct ENGINE_API BitmapDecodeOptions
            {
                inline BitmapDecodeOptions() NOTHROWS
                    : emplaceData(false) { }

                /**
                 * An informal hint as to which image format the input data is. Format hints
                 * should be in the form of a mime-type.
                 */
                Port::String formatHint;

                /**
                 * If true, the decoder will write the decoded bitmap image to the existing
                 * data in the format and size specified within the destination bitmap. The decoder
                 * may return BitmapFactory::Unsupported if this is not supported.
                 */
                bool emplaceData;
            };

            ENGINE_API Util::TAKErr BitmapFactory2_decode(BitmapPtr &result, Util::DataInput2 &input, const BitmapDecodeOptions *opts) NOTHROWS;

            ENGINE_API Util::TAKErr BitmapFactory2_decode(BitmapPtr &result, const char *bitmapFilePath, const BitmapDecodeOptions *opts) NOTHROWS;
        }
    }
}

#endif
