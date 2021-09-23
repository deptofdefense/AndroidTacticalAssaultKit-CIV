#include "raster/mosaic/MosaicDatabase.h"

#include <sstream>

namespace atakmap {
    namespace raster {
        namespace mosaic {

            MosaicDatabase::Frame::Frame(int64_t id, std::string path, std::string type, double minLat, double minLon, double maxLat,
                  double maxLon, core::GeoPoint upperLeft, core::GeoPoint upperRight, core::GeoPoint lowerRight,
                  core::GeoPoint lowerLeft, double minGsd, double maxGsd, int width, int height) :
                  id(id),
                  path(path),
                  type(type),
                  minLat(minLat),
                  minLon(minLon),
                  maxLat(maxLat),
                  maxLon(maxLon),
                  upperLeft(upperLeft),
                  upperRight(upperRight),
                  lowerRight(lowerRight),
                  lowerLeft(lowerLeft),
                  minGsd(minGsd),
                  maxGsd(maxGsd),
                  width(width),
                  height(height)
            {
            }

            MosaicDatabase::Coverage::Coverage(feature::Geometry *geometry, double minGSD, double maxGSD) :
                geometry(geometry),
                minGSD(minGSD),
                maxGSD(maxGSD)
            {
            }

            MosaicDatabase::Coverage::~Coverage()
            {
                delete geometry;
            }

            std::string MosaicDatabase::Coverage::toString() const
            {
                feature::Envelope mbb = geometry->getEnvelope();
                std::ostringstream str;
                str << "Coverage {minLat=" << mbb.minY << ",minLon=" << mbb.minX
                    << ",maxLat=" << mbb.maxY << ",maxLon=" << mbb.maxX
                    << ",minGSD=" << minGSD << ",maxGSD=" << maxGSD << "}";
                return str.str();
            }



            MosaicDatabase::Cursor::Cursor(std::unique_ptr<db::Cursor> &&filter) : db::CursorProxy(std::move(filter))
            {

            }

            MosaicDatabase::Cursor::~Cursor() NOTHROWS
            {

            }

            core::GeoPoint MosaicDatabase::Cursor::getPoint(const char *latCol, const char *lonCol) const
            {
                double lat = getDouble(latCol);
                double lon = getDouble(lonCol);
                return core::GeoPoint(lat, lon);
            }

            MosaicDatabase::Frame MosaicDatabase::Cursor::asFrame() const
            {
                return Frame(getId(),
                                 getPath(),
                                 getType(),
                                 getMinLat(),
                                 getMinLon(),
                                 getMaxLat(),
                                 getMaxLon(),
                                 getUpperLeft(),
                                 getUpperRight(),
                                 getLowerRight(),
                                 getLowerLeft(),
                                 getMinGSD(),
                                 getMaxGSD(),
                                 getWidth(),
                                 getHeight());
            }

        }
    }
}
