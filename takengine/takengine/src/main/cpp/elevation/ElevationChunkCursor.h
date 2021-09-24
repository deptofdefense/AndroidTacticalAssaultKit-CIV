#ifndef TAK_ENGINE_ELEVATION_ELEVATIONCHUNKCURSOR_H_INCLUDED
#define TAK_ENGINE_ELEVATION_ELEVATIONCHUNKCURSOR_H_INCLUDED

#include <memory>

#include "db/RowIterator.h"
#include "elevation/ElevationChunk.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Elevation {

            class ENGINE_API ElevationChunkCursor : public DB::RowIterator
            {
            public :
                virtual ~ElevationChunkCursor() NOTHROWS = 0;
            public :
                virtual Util::TAKErr moveToNext() NOTHROWS = 0;
            public :
                virtual Util::TAKErr get(ElevationChunkPtr &value) NOTHROWS = 0;
                virtual Util::TAKErr getResolution(double *value) NOTHROWS = 0;
                virtual Util::TAKErr isAuthoritative(bool *value) NOTHROWS = 0;
                virtual Util::TAKErr getCE(double *value) NOTHROWS = 0;
                virtual Util::TAKErr getLE(double *value) NOTHROWS = 0;
                virtual Util::TAKErr getUri(const char **value) NOTHROWS = 0;
                virtual Util::TAKErr getType(const char **value) NOTHROWS = 0;
                virtual Util::TAKErr getBounds(const Feature::Polygon2 **value) NOTHROWS = 0;
                virtual Util::TAKErr getFlags(unsigned int *value) NOTHROWS = 0;
            };

            typedef std::unique_ptr<ElevationChunkCursor, void(*)(const ElevationChunkCursor *value)> ElevationChunkCursorPtr;
        }
    }
}

#endif
