#ifndef ATAKMAP_RENDERER_UTILS_H_INCLUDED
#define ATAKMAP_RENDERER_UTILS_H_INCLUDED

#include "port/Platform.h"

namespace atakmap
{
    namespace renderer
    {
        namespace Utils {

            typedef enum {
                RED = 0,
                GREEN,
                BLUE,
                ALPHA
            } Colors;

            ENGINE_API int colorExtract(int color, Colors c);

            const int WHITE = 0xFFFFFFFF;
        }
    }
}

#endif
