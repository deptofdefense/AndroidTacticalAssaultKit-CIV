#ifndef TAK_ENGINE_RASTER_MOSAIC_MULTIPLEXINGMOSAICDATABASECURSOR2_H_INCLUDED
#define TAK_ENGINE_RASTER_MOSAIC_MULTIPLEXINGMOSAICDATABASECURSOR2_H_INCLUDED

#include <list>
#include <set>

#include "raster/mosaic/MosaicDatabase2.h"

namespace TAK {
    namespace Engine {
        namespace Raster {
            namespace Mosaic {
                class MultiplexingMosaicDatabaseCursor2 : public MosaicDatabase2::Cursor
                {
                private :
                    class Impl;
                    class Entry;
                public :
                    MultiplexingMosaicDatabaseCursor2() NOTHROWS;
                public :
                    Util::TAKErr add(MosaicDatabase2::CursorPtr &&cursor) NOTHROWS;
                public :
                    virtual Util::TAKErr getUpperLeft(Core::GeoPoint2 *value) NOTHROWS;
                    virtual Util::TAKErr getUpperRight(Core::GeoPoint2 *value) NOTHROWS;
                    virtual Util::TAKErr getLowerRight(Core::GeoPoint2 *value) NOTHROWS;
                    virtual Util::TAKErr getLowerLeft(Core::GeoPoint2 *value) NOTHROWS;
                    virtual Util::TAKErr getMinLat(double *value) NOTHROWS;
                    virtual Util::TAKErr getMinLon(double *value) NOTHROWS;
                    virtual Util::TAKErr getMaxLat(double *value) NOTHROWS;
                    virtual Util::TAKErr getMaxLon(double *value) NOTHROWS;
                    virtual Util::TAKErr getPath(const char **value) NOTHROWS;
                    virtual Util::TAKErr getType(const char **value) NOTHROWS;
                    virtual Util::TAKErr getMinGSD(double *value) NOTHROWS;
                    virtual Util::TAKErr getMaxGSD(double *value) NOTHROWS;
                    virtual Util::TAKErr getWidth(int *value) NOTHROWS;
                    virtual Util::TAKErr getHeight(int *value) NOTHROWS;
                    virtual Util::TAKErr getId(int *value) NOTHROWS;
                    virtual Util::TAKErr getSrid(int *value) NOTHROWS;
                    virtual Util::TAKErr isPrecisionImagery(bool *value) NOTHROWS;
                public : // RowIterator
                    virtual Util::TAKErr moveToNext() NOTHROWS;
                private :
                    std::unique_ptr<Impl> impl;
                };

                class MultiplexingMosaicDatabaseCursor2::Impl : public  DB::RowIterator
                {
                public :
                    Impl() NOTHROWS;
                public :
                    virtual Util::TAKErr moveToNext() NOTHROWS;
                public :
                    std::list<MosaicDatabase2::CursorPtr> cursors;
                    MosaicDatabase2::Cursor *current;

                    std::set<Entry> pendingResults;
                    std::set<MosaicDatabase2::Cursor *> invalid;

                };

                class MultiplexingMosaicDatabaseCursor2::Entry
                {
                public :
                    Entry(MosaicDatabase2::Cursor *cursor) NOTHROWS;
                public :
                    bool operator<(const Entry &other) const;
                public :
                    double gsd;
                    Port::String path;
                    MosaicDatabase2::Cursor *cursor;
                };
            }
        }
    }
}

#endif
