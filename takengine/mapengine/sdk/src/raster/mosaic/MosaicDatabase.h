#ifndef ATAKMAP_RASTER_MOSAIC_MOSAICDB_H_INCLUDED
#define ATAKMAP_RASTER_MOSAIC_MOSAICDB_H_INCLUDED

#include <map>
#include <set>
#include <string>
#include <stdint.h>

#include "db/Cursor.h"
#include "core/GeoPoint.h"
#include "feature/Geometry.h"

namespace atakmap {
    namespace raster {
        namespace mosaic {
            class MosaicDatabase {
            public:

                class Frame {
                public:
                    const int64_t id;
                    const std::string path;
                    const std::string type;
                    const double minLat;
                    const double minLon;
                    const double maxLat;
                    const double maxLon;
                    const core::GeoPoint upperLeft;
                    const core::GeoPoint upperRight;
                    const core::GeoPoint lowerRight;
                    const core::GeoPoint lowerLeft;
                    const double minGsd;
                    const double maxGsd;
                    const int width;
                    const int height;

                    Frame(int64_t id, std::string path, std::string type, double minLat, double minLon, double maxLat,
                          double maxLon, core::GeoPoint upperLeft, core::GeoPoint upperRight, core::GeoPoint lowerRight,
                          core::GeoPoint lowerLeft, double minGsd, double maxGsd, int width, int height);
                };

                class Coverage {
                public:
                    const feature::Geometry * const geometry;
                    const double minGSD;
                    const double maxGSD;

                    Coverage(feature::Geometry *geometry, double minGSD, double maxGSD);
                    ~Coverage();
                    std::string toString() const;
                };

                class Cursor : public db::CursorProxy {

                public:
                    Cursor(std::unique_ptr<db::Cursor> &&filter);
                    virtual ~Cursor() NOTHROWS;

                    //
                    // Keep overloads from hiding the existing member functions.
                    //
                    using db::CursorProxy::getDouble;
                    using db::CursorProxy::getInt;
                    using db::CursorProxy::getLong;
                    using db::CursorProxy::getString;
                    using db::CursorProxy::getType;

                protected:
                    double getDouble(const char *col) const
                      { return getDouble (getColumnIndex (col)); }
                    const char *getString(const char *col) const
                      { return getString (getColumnIndex (col)); }
                    int getInt(const char *col) const
                      { return getInt (getColumnIndex (col)); }
                    int64_t getLong (const char* col) const
                      { return getLong (getColumnIndex (col)); }

                    virtual core::GeoPoint getPoint(const char *latCol, const char *lonCol) const;

                public:
                    virtual core::GeoPoint getUpperLeft() const = 0;
                    virtual core::GeoPoint getUpperRight() const = 0;
                    virtual core::GeoPoint getLowerRight() const = 0;
                    virtual core::GeoPoint getLowerLeft() const = 0;
                    virtual double getMinLat() const = 0;
                    virtual double getMinLon() const = 0;
                    virtual double getMaxLat() const = 0;
                    virtual double getMaxLon() const = 0;
                    virtual std::string getPath() const = 0;
                    virtual std::string getType() const = 0;
                    virtual double getMinGSD() const = 0;
                    virtual double getMaxGSD() const = 0;
                    virtual int getWidth() const = 0;
                    virtual int getHeight() const = 0;
                    virtual int64_t getId() const = 0;

                    virtual Frame asFrame() const;
                };


                /**************************************************************************/

                virtual ~MosaicDatabase() {};
                virtual const char *getType() const = 0;

                virtual void open(const char *f) = 0;

                virtual void close() = 0;

                // Coverages are owned by the MosaicDatabase - do not attempt to deallocate
                // the returned values.
                virtual Coverage *getCoverage() const = 0;
                virtual std::map<std::string, Coverage *> getCoverages() const = 0;
                virtual Coverage *getCoverage(const std::string &type) const = 0;

                // Obtained Cursor objects should be delete'd by caller.
                virtual Cursor *query() const = 0;

                virtual Cursor *query(const std::string *path) const = 0;

                virtual Cursor *query(const core::GeoPoint *p, double minGSD, double maxGSD) const = 0;

                virtual Cursor *query(const std::set<std::string> *types, const core::GeoPoint *p, double minGSD, double maxGSD) const = 0;

                virtual Cursor *query(const core::GeoPoint *roiUL, const core::GeoPoint *roiLR, double minGSD, double maxGSD) const = 0;

                virtual Cursor *query(const std::set<std::string> *types, const core::GeoPoint *roiUL, const core::GeoPoint *roiLR,
                                      double minGSD, double maxGSD) const = 0;

            };

        }
    }
}


#endif
