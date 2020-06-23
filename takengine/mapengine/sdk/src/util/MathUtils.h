#ifndef TAK_ENGINE_UTIL_MATHUTILS_H_INCLUDED
#define TAK_ENGINE_UTIL_MATHUTILS_H_INCLUDED

#include <cstdint>

#include "port/Platform.h"

namespace TAK {
    namespace Engine {
        namespace Util {
            bool MathUtils_isPowerOf2(const int value) NOTHROWS;
            int MathUtils_nextPowerOf2(const int value) NOTHROWS;
            bool MathUtils_hasBits(const int64_t value, const int64_t mask) NOTHROWS;

            template<class T>
            inline T MathUtils_clamp(T val, T min, T max) NOTHROWS
            {
                if (val < min)      return min;
                else if (val > max) return max;
                else                return val;

            }
        }
    }
}
#endif
