#ifndef ATAKMAP_FEATURE_QUADBLOB_H_INCLUDED
#define ATAKMAP_FEATURE_QUADBLOB_H_INCLUDED

#include <cstdint>

#include "port/Platform.h"
#include "util/IO_Decls.h"

namespace atakmap {
    namespace feature {
        class ENGINE_API Point;

        /**
        * Convenience class for generating SpatiaLite Geometry blobs for quadrilaterals
        * (geometry type POLYGON).
        *
        * <P>This class is NOT thread-safe.
        *
        * @author Developer
        */
        class ENGINE_API QuadBlob
        {
        public:
            QuadBlob();
            QuadBlob(atakmap::util::Endian endian);
			/*
			* Noncopyable
			*/
			QuadBlob(const QuadBlob&) = delete;
			QuadBlob &operator=(const QuadBlob&) = delete;
        public:
            /**
            * Returns the SpatiaLite geometry equivalent to the quadrilateral specified
            * by the four points. The returned array is <I>live</I> and must be copied
            * if this method is expected to be called again before use of the data is
            * complete.
            */
            uint8_t *getBlob(const Point *a, const Point *b, const Point *c, const Point *d);
        public :
            static size_t getQuadBlobSize();
        private:
            uint8_t quad[132];
            atakmap::util::Endian endian;
        };
    }
}

#endif // ATAKMAP_FEATURE_QUADBLOB_H_INCLUDED
