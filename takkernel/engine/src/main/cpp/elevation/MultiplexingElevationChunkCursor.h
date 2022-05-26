#ifndef TAK_ENGINE_ELEVATION_MULTIPLEXINGELEVATIONCHUNKCURSOR_H_INCLUDED
#define TAK_ENGINE_ELEVATION_MULTIPLEXINGELEVATIONCHUNKCURSOR_H_INCLUDED

#include "elevation/ElevationChunkCursor.h"
#include "port/Collection.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Elevation {
            class MultiplexingElevationChunkCursor : public ElevationChunkCursor
            {
            public :
                MultiplexingElevationChunkCursor(Port::Collection<std::shared_ptr<ElevationChunkCursor>> &cursors) NOTHROWS;
                MultiplexingElevationChunkCursor(Port::Collection<std::shared_ptr<ElevationChunkCursor>> &cursors, bool(*order)(ElevationChunkCursor &, ElevationChunkCursor &) NOTHROWS) NOTHROWS;
                MultiplexingElevationChunkCursor(Port::Collection<std::shared_ptr<ElevationChunkCursor>> &cursors, Port::Collection<bool(*)(ElevationChunkCursor &, ElevationChunkCursor &) NOTHROWS> &order) NOTHROWS;
            public :
                Util::TAKErr moveToNext() NOTHROWS;
                Util::TAKErr get(ElevationChunkPtr &value) NOTHROWS;
                Util::TAKErr getResolution(double *value) NOTHROWS;
                Util::TAKErr isAuthoritative(bool *value) NOTHROWS;
                Util::TAKErr getCE(double *value) NOTHROWS;
                Util::TAKErr getLE(double *value) NOTHROWS;
                Util::TAKErr getUri(const char **value) NOTHROWS;
                Util::TAKErr getType(const char **value) NOTHROWS;
                Util::TAKErr getBounds(const Feature::Polygon2 **value) NOTHROWS;
                Util::TAKErr getFlags(unsigned int *value) NOTHROWS;
            private :
                std::vector<std::shared_ptr<ElevationChunkCursor>> cursors;
                std::vector<bool(*)(ElevationChunkCursor &, ElevationChunkCursor &) NOTHROWS> order;
                std::shared_ptr<ElevationChunkCursor> row;
            };
        }
    }
}
#endif
