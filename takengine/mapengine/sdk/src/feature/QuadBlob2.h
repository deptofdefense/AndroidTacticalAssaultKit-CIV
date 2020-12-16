#ifndef TAK_ENGINE_FEATURE_QUADBLOB2_H_INCLUDED
#define TAK_ENGINE_FEATURE_QUADBLOB2_H_INCLUDED

#include <cstdint>

#include "port/Platform.h"

#include "util/Error.h"
#include "util/IO2.h"

#define TE_QUADBLOB_SIZE 132u

namespace TAK {
    namespace Engine {
        namespace Feature {

            class ENGINE_API Point2;

            /**
             * Provides the SpatiaLite geometry equivalent to the quadrilateral specified
             * by the four points. The provided array is <I>live</I> and must be copied
             * if this method is expected to be called again before use of the data is
             * complete.
             */

            ENGINE_API Util::TAKErr QuadBlob2_get(uint8_t *value, const std::size_t &len, const Util::TAKEndian order, const Point2 &a, const Point2 &b, const Point2 &c, const Point2 &d) NOTHROWS;
        }
    }
}
#endif // TAK_ENGINE_FEATURE_QUADBLOB2_H_INCLUDED
