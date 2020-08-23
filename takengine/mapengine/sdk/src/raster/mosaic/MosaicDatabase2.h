#ifndef TAK_ENGINE_RASTER_MOSAIC_MOSAICDATABASE2_H_INCLUDED
#define TAK_ENGINE_RASTER_MOSAIC_MOSAICDATABASE2_H_INCLUDED

#include "core/GeoPoint2.h"
#include "db/RowIterator.h"
#include "feature/Geometry2.h"
#include "port/Platform.h"
#include "port/Set.h"
#include "port/String.h"
#include "raster/ImageInfo.h"
#include "util/Error.h"
#include "util/NonCopyable.h"

namespace TAK {
    namespace Engine {
        namespace Raster {
            namespace Mosaic {
                class ENGINE_API MosaicDatabase2 : TAK::Engine::Util::NonCopyable
                {
                public :
                    class ENGINE_API QueryParameters;
                    class ENGINE_API Cursor;
                    class ENGINE_API Frame;
                    class ENGINE_API Coverage;
                public :
                    typedef std::unique_ptr<Cursor, void(*)(const Cursor *)> CursorPtr;
                    typedef std::unique_ptr<Frame, void(*)(const Frame *)> FramePtr;
                    typedef std::unique_ptr<const Frame, void(*)(const Frame *)> FramePtr_const;
                    typedef std::unique_ptr<Coverage, void(*)(const Coverage *)> CoveragePtr;
                    typedef std::unique_ptr<const Coverage, void(*)(const Coverage *)> CoveragePtr_const;
                protected :
                    virtual ~MosaicDatabase2() NOTHROWS = 0;
                public :
                    virtual const char *getType() NOTHROWS = 0;
                    virtual Util::TAKErr open(const char *path) NOTHROWS = 0;
                    virtual Util::TAKErr close() NOTHROWS = 0;
                    virtual Util::TAKErr getCoverage(std::shared_ptr<const Coverage> &value) NOTHROWS = 0;
                    virtual Util::TAKErr getCoverages(Port::Collection<std::pair<Port::String, std::shared_ptr<const Coverage>>> &coverages) NOTHROWS = 0;
                    virtual Util::TAKErr getCoverage(std::shared_ptr<const Coverage> &value, const char *type) NOTHROWS = 0;
                    virtual Util::TAKErr query(CursorPtr &value, const QueryParameters &params) NOTHROWS = 0;
                };

                typedef std::unique_ptr<MosaicDatabase2, void(*)(const MosaicDatabase2 *)> MosaicDatabase2Ptr;

                class ENGINE_API MosaicDatabase2::QueryParameters
                {
                public :
                    enum ImageFilter
                    {
                        AllImagery = 0,
                        PreciseImagery = 1,
                        ImpreciseImagery = 2,
                    };
                    enum GsdCompare
                    {
                        MinimumGsd,
                        MaximumGsd,
                    };
                    enum Order
                    {
                        MinGsdAsc,
                        MinGsdDesc,
                        MaxGsdAsc,
                        MaxGsdDesc,
                    };

                public :
                    QueryParameters() NOTHROWS;
                    QueryParameters(const QueryParameters &other) NOTHROWS;
                public :
                    ~QueryParameters() NOTHROWS;
                public :
                    Port::String path;
                    Feature::Geometry2Ptr spatialFilter;
                    double minGsd;
                    double maxGsd;
                    std::unique_ptr<Port::Set<Port::String>, void(*)(const Port::Set<Port::String> *)> types;
                    int srid;
                    ImageFilter imagery;
                    GsdCompare minGsdCompare;
                    GsdCompare maxGsdCompare;
                    Order order;
                };

                class ENGINE_API MosaicDatabase2::Cursor : public virtual DB::RowIterator
                {
                protected :
                    virtual ~Cursor() NOTHROWS = 0;
                public :
                    virtual Util::TAKErr getUpperLeft(Core::GeoPoint2 *value) NOTHROWS = 0;
                    virtual Util::TAKErr getUpperRight(Core::GeoPoint2 *value) NOTHROWS = 0;
                    virtual Util::TAKErr getLowerRight(Core::GeoPoint2 *value) NOTHROWS = 0;
                    virtual Util::TAKErr getLowerLeft(Core::GeoPoint2 *value) NOTHROWS = 0;
                    virtual Util::TAKErr getMinLat(double *value) NOTHROWS = 0;
                    virtual Util::TAKErr getMinLon(double *value) NOTHROWS = 0;
                    virtual Util::TAKErr getMaxLat(double *value) NOTHROWS = 0;
                    virtual Util::TAKErr getMaxLon(double *value) NOTHROWS = 0;
                    virtual Util::TAKErr getPath(const char **value) NOTHROWS = 0;
                    virtual Util::TAKErr getType(const char **value) NOTHROWS = 0;
                    virtual Util::TAKErr getMinGSD(double *value) NOTHROWS = 0;
                    virtual Util::TAKErr getMaxGSD(double *value) NOTHROWS = 0;
                    virtual Util::TAKErr getWidth(int *value) NOTHROWS = 0;
                    virtual Util::TAKErr getHeight(int *value) NOTHROWS = 0;
                    virtual Util::TAKErr getId(int *value) NOTHROWS = 0;
                    virtual Util::TAKErr getSrid(int *value) NOTHROWS = 0;
                    virtual Util::TAKErr isPrecisionImagery(bool *value) NOTHROWS = 0;
                };

                class ENGINE_API MosaicDatabase2::Frame : public Raster::ImageInfo
                {
                public :
                    Frame(const int id,
                          const char *path,
                          const char *type,
                          const bool precisionImagery,
                          const Core::GeoPoint2 &upperLeft,
                          const Core::GeoPoint2 &upperRight,
                          const Core::GeoPoint2 &lowerRight,
                          const Core::GeoPoint2 &lowerLeft,
                          const double minGsd,
                          const double maxGsd,
                          const int width,
                          const int height,
                          const int srid) NOTHROWS;
                private :
                    Frame(const int id,
                          const char *path,
                          const char *type,
                          const bool precisionImagery,
                          const double minLat,
                          const double minLon,
                          const double maxLat,
                          const double maxLon,
                          const Core::GeoPoint2 &upperLeft,
                          const Core::GeoPoint2 &upperRight,
                          const Core::GeoPoint2 &lowerRight,
                          const Core::GeoPoint2 &lowerLeft,
                          const double minGsd,
                          const double maxGsd,
                          const int width,
                          const int height,
                          const int srid) NOTHROWS;
                public :
                    static Util::TAKErr createFrame(MosaicDatabase2::FramePtr_const &frame, MosaicDatabase2::Cursor &row) NOTHROWS;
                public :
                    int id;
                    double minLat;
                    double minLon;
                    double maxLat;
                    double maxLon;
                    double minGsd;
                };

                class ENGINE_API MosaicDatabase2::Coverage
                {
                public :
                    Coverage(Feature::Geometry2Ptr_const &&geometry, const double minGSD, const double maxGSD) NOTHROWS;
                    Coverage(const Coverage &other) NOTHROWS;
                public :
                    Feature::Geometry2Ptr_const geometry;
                    double minGSD;
                    double maxGSD;
                };
            }
        }
    }
}

#endif

