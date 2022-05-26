#ifndef TAK_ENGINE_UTIL_MATHUTILS_H_INCLUDED
#define TAK_ENGINE_UTIL_MATHUTILS_H_INCLUDED

#include <cstdint>

#include "port/Platform.h"

namespace TAK {
    namespace Engine {
        namespace Util {
            bool MathUtils_isPowerOf2(const int value) NOTHROWS;
            bool MathUtils_isPowerOf2(const std::size_t value) NOTHROWS;
            int MathUtils_nextPowerOf2(const int value) NOTHROWS;
            std::size_t MathUtils_nextPowerOf2(const std::size_t value) NOTHROWS;
            bool MathUtils_hasBits(const int64_t value, const int64_t mask) NOTHROWS;

            template<class T>
            inline T MathUtils_clamp(T val, T min, T max) NOTHROWS
            {
                if (val < min)      return min;
                else if (val > max) return max;
                else                return val;

            }

            template<class T>
            inline T MathUtils_interpolate(const T ul, const T ur, const T lr, const T ll, const double weightx, const double weighty) NOTHROWS
            {
                const double ul_v = static_cast<double>(ul);
                const double ur_v = static_cast<double>(ur);
                const double lr_v = static_cast<double>(lr);
                const double ll_v = static_cast<double>(ll);

                return static_cast<T>(
                    (ul_v * (1.0-weightx)*(1.0-weighty)) +
                    (ur_v * (weightx)*(1.0-weighty)) +
                    (ll_v * (1.0-weightx)*(weighty)) +
                    (lr_v * (weightx)*(weighty))
                        );
            }
        }
    }
}
#endif
