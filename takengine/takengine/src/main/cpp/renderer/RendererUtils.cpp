#include "RendererUtils.h"

namespace atakmap {
    namespace renderer {
        namespace Utils {
            namespace {
                const int ColorBitOffsets[] = {
                    16,
                    8,
                    0,
                    24
                };
            }

            int colorExtract(int color, Colors c) {
                return (color >> ColorBitOffsets[c]) & 0xff;
            }
        }
    }
}
