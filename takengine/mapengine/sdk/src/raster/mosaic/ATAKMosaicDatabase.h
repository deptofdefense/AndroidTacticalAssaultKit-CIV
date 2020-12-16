#ifndef ATAKMAP_RASTER_MOSAIC_ATAKMOSAICDB_H_INCLUDED
#define ATAKMAP_RASTER_MOSAIC_ATAKMOSAICDB_H_INCLUDED

#include <memory>

#include "raster/mosaic/MosaicDatabase.h"
#include "db/Database.h"
#include "db/Statement.h"
#include "thread/Mutex.h"

namespace atakmap {
    namespace raster {
        namespace mosaic {
            class ATAKMosaicDatabase : public MosaicDatabase {

            public:
                static const char * const COLUMN_ID;
                static const char * const COLUMN_PATH;
                static const char * const COLUMN_TYPE;
                static const char * const COLUMN_MIN_LAT;
                static const char * const COLUMN_MIN_LON;
                static const char * const COLUMN_MAX_LAT;
                static const char * const COLUMN_MAX_LON;
                static const char * const COLUMN_UL_LAT;
                static const char * const COLUMN_UL_LON;
                static const char * const COLUMN_UR_LAT;
                static const char * const COLUMN_UR_LON;
                static const char * const COLUMN_LR_LAT;
                static const char * const COLUMN_LR_LON;
                static const char * const COLUMN_LL_LAT;
                static const char * const COLUMN_LL_LON;
                static const char * const COLUMN_MIN_GSD;
                static const char * const COLUMN_MAX_GSD;
                static const char * const COLUMN_WIDTH;
                static const char * const COLUMN_HEIGHT;

                static const char * const TABLE_MOSAIC_DATA;
                static const char * const TABLE_COVERAGE;



                class Cursor : public MosaicDatabase::Cursor {
                public:
                    Cursor(std::unique_ptr<db::Cursor> &&filter);

                    virtual core::GeoPoint getUpperLeft() const override;
                    virtual core::GeoPoint getUpperRight() const override;
                    virtual core::GeoPoint getLowerRight() const override;
                    virtual core::GeoPoint getLowerLeft() const override;
                    virtual double getMinLat() const override;
                    virtual double getMinLon() const override;
                    virtual double getMaxLat() const override;
                    virtual double getMaxLon() const override;
                    virtual std::string getPath() const override;
                    virtual std::string getType() const override;
                    virtual double getMinGSD() const override;
                    virtual double getMaxGSD() const override;
                    virtual int getWidth() const override;
                    virtual int getHeight() const override;
                    virtual int64_t getId() const override;
                };

                /**************************************************************************/

            private:
                db::Database *database;
                db::Statement *insertStatement;

                bool inTransaction;
                bool creation;

                class CoverageDiscovery;
                std::map<std::string, CoverageDiscovery *> coverageDiscovery;
                std::map<std::string, Coverage *> coverages;
                Coverage *coverage;

                mutable TAK::Engine::Thread::Mutex mutex;

            public:
                ATAKMosaicDatabase();
                virtual ~ATAKMosaicDatabase();
                virtual void create(const char *file);
                virtual void open(const char *f);
                virtual void close();
                virtual const char *getType() const override;
                virtual MosaicDatabase::Coverage *getCoverage() const override;
                virtual std::map<std::string, Coverage *> getCoverages() const override;
                virtual Coverage *getCoverage(const std::string &type) const override;

                TAK::Engine::Thread::Mutex& getMutex() const;
                virtual void beginTransaction();
                virtual void setTransactionSuccessful();
                virtual void endTransaction();
                virtual void insertRow(const std::string &path, const std::string &type, core::GeoPoint ul, core::GeoPoint ur, core::GeoPoint lr,
                               core::GeoPoint ll, double minGsd, double maxGsd, int width, int height);

                virtual Cursor *query() const override;
                virtual Cursor *query(const std::string *path) const override;
                virtual Cursor *query(const core::GeoPoint *p, double minGSD, double maxGSD) const override;
                virtual Cursor *query(const std::set<std::string> *types, const core::GeoPoint *p, double minGSD, double maxGSD) const override;
                virtual Cursor *query(const core::GeoPoint *roiUL, const core::GeoPoint *roiLR, double minGSD, double maxGSD) const override;
                virtual Cursor *query(const std::set<std::string> *types, const core::GeoPoint *roiUL, const core::GeoPoint *roiLR,
                                      double minGSD, double maxGSD) const override;
                virtual Cursor *query(const std::vector<std::string> *columns, const std::string *selection, const std::vector<std::string> *selectionArgs,
                                      const std::string *groupBy, const std::string *having, const std::string *orderBy, const std::string *limit) const;

                static ATAKMosaicDatabase *getSynchronizedInstance(ATAKMosaicDatabase *existing);
                static MosaicDatabase *newSynchronizedInstance();

            };
        }

    }
}



#endif
