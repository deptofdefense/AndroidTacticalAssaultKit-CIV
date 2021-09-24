#ifndef TAK_ENGINE_FEATURE_GEOMETRYTRANSFORMER_H_INCLUDED
#define TAK_ENGINE_FEATURE_GEOMETRYTRANSFORMER_H_INCLUDED

#include "feature/Geometry2.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            ENGINE_API Util::TAKErr GeometryTransformer_transform(Geometry2Ptr &value, const Geometry2 &src, const int srcSrid, const int dstSrid) NOTHROWS;
            ENGINE_API Util::TAKErr GeometryTransformer_transform(Geometry2Ptr_const &value, const Geometry2 &src, const int srcSrid, const int dstSrid) NOTHROWS;
            ENGINE_API Util::TAKErr GeometryTransformer_transform(Envelope2 *value, const Envelope2 &src, const int srcSrid, const int dstSrid) NOTHROWS;
        }
    }
}
#endif
