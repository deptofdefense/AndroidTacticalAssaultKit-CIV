#ifndef TAK_ENGINE_RASTER_MOSAIC_FILTERMOSAICDATABASECURSOR2_H_INCLUDED
#define TAK_ENGINE_RASTER_MOSAIC_FILTERMOSAICDATABASECURSOR2_H_INCLUDED

#include "port/Collection.h"
#include "raster/mosaic/MosaicDatabase2.h"
#include "util/Filter.h"

namespace TAK {
    namespace Engine {
        namespace Raster {
            namespace Mosaic {
                class FilterMosaicDatabaseCursor2 : public MosaicDatabase2::Cursor
                {
                protected :
                    FilterMosaicDatabaseCursor2(MosaicDatabase2::CursorPtr &&impl) NOTHROWS;
                public : // MosaicDatabase2::Cursor
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
                    virtual TAK::Engine::Util::TAKErr moveToNext() NOTHROWS;
                protected :
                    MosaicDatabase2::CursorPtr impl;
                };

                Util::TAKErr FilterMosaicDatabaseCursor2_filter(MosaicDatabase2::CursorPtr &value, MosaicDatabase2::CursorPtr &&ptr, std::unique_ptr<Port::Collection<std::shared_ptr<Util::Filter<MosaicDatabase2::Cursor &>>>, void(*)(const Port::Collection<std::shared_ptr<Util::Filter<MosaicDatabase2::Cursor &>>> *)> &&filter) NOTHROWS;
            }
        }
    }
}

#endif
