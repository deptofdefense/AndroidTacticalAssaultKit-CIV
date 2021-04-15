#include "raster/mosaic/ATAKMosaicDatabase.h"

#include "thread/Lock.h"
#include "db/SpatiaLiteDB.h"
#include "feature/Polygon.h"
#include "feature/LineString.h"
#include "util/IO.h"
#include <sstream>

using namespace TAK::Engine::Thread;

namespace atakmap {
    namespace raster {
        namespace mosaic {

            class ATAKMosaicDatabase::CoverageDiscovery {
            public:
                double minLat;
                double minLon;
                double maxLat;
                double maxLon;
                double minGSD;
                double maxGSD;

                CoverageDiscovery() : minLat(NAN),
                    minLon(NAN),
                    maxLat(NAN),
                    maxLon(NAN),
                    minGSD(NAN),
                    maxGSD(NAN)
                {
                }

                void addLat(double lat) {
                    if (isnan(minLat) || lat < minLat)
                        minLat = lat;
                    if (isnan(maxLat) || lat > maxLat)
                        maxLat = lat;
                }

                void addLon(double lon) {
                    if (isnan(minLon) || lon < minLon)
                        minLon = lon;
                    if (isnan(maxLon) || lon > maxLon)
                        maxLon = lon;
                }

                void addGSD(double gsd) {
                    if (isnan(minGSD) || gsd > minGSD)
                        minGSD = gsd;
                    if (isnan(maxGSD) || gsd < maxGSD)
                        maxGSD = gsd;
                }
            };

            /**************************************************************************/

            class SynchronizedDB : public ATAKMosaicDatabase {
            private:
                ATAKMosaicDatabase *filter;

            public:
                SynchronizedDB(ATAKMosaicDatabase *filter)
                  : filter (filter)
                  { }

                ~SynchronizedDB() override {
                    delete filter;
                }

                void create(const char *f) override {
                    Lock lock(filter->getMutex());
                    filter->create(f);
                }

                void open(const char *f) override {
                    Lock lock(filter->getMutex());
                    filter->open(f);
                }

                Coverage *getCoverage() const override {
                    Lock lock(filter->getMutex());
                    return filter->getCoverage();
                }

                std::map<std::string, Coverage *> getCoverages() const override {
                    Lock lock(filter->getMutex());
                    return filter->getCoverages();
                }

                Coverage *getCoverage(const std::string &type) const override {
                    Lock lock(filter->getMutex());
                    return filter->getCoverage(type);
                }

                void close() override {
                    Lock lock(filter->getMutex());
                    filter->close();
                }

                void beginTransaction() override {
                    Lock lock(filter->getMutex());
                    filter->beginTransaction();
                }

                void setTransactionSuccessful() override {
                    Lock lock(filter->getMutex());
                    filter->setTransactionSuccessful();
                }

                void endTransaction() override {
                    Lock lock(filter->getMutex());
                    filter->endTransaction();
                }

                void insertRow(const std::string &path, const std::string &type, core::GeoPoint ul, core::GeoPoint ur, core::GeoPoint lr,
                                       core::GeoPoint ll, double minGsd, double maxGsd, int width, int height) override
                {
                    Lock lock(filter->getMutex());
                    filter->insertRow(path, type, ul, ur, lr, ll, minGsd, maxGsd, width, height);
                }

                ATAKMosaicDatabase::Cursor *query(const std::vector<std::string> *columns, const std::string *selection, const std::vector<std::string> *selectionArgs,
                                                      const std::string *groupBy, const std::string *having, const std::string *orderBy, const std::string *limit) const override
                {
                    Lock lock(filter->getMutex());
                    return filter->query(columns, selection, selectionArgs, groupBy, having,
                                         orderBy, limit);
                }
            };

            class SelectionBuilder {
            private:
                std::ostringstream selection;

            public:
                SelectionBuilder() {
                }

                void append(const std::string &s) {
                    if (selection.tellp() > 0)
                        selection << " AND ";
                    selection << s;
                }

                std::string getSelection() throw(int) {
                    if (selection.tellp() < 1)
                        throw 0;
                    return selection.str();
                }
            };



            const char * const ATAKMosaicDatabase::COLUMN_ID = "id";
            const char * const ATAKMosaicDatabase::COLUMN_PATH = "path";
            const char * const ATAKMosaicDatabase::COLUMN_TYPE = "type";
            const char * const ATAKMosaicDatabase::COLUMN_MIN_LAT = "minlat";
            const char * const ATAKMosaicDatabase::COLUMN_MIN_LON = "minlon";
            const char * const ATAKMosaicDatabase::COLUMN_MAX_LAT = "maxlat";
            const char * const ATAKMosaicDatabase::COLUMN_MAX_LON = "maxlon";
            const char * const ATAKMosaicDatabase::COLUMN_UL_LAT = "ullat";
            const char * const ATAKMosaicDatabase::COLUMN_UL_LON = "ullon";
            const char * const ATAKMosaicDatabase::COLUMN_UR_LAT = "urlat";
            const char * const ATAKMosaicDatabase::COLUMN_UR_LON = "urlon";
            const char * const ATAKMosaicDatabase::COLUMN_LR_LAT = "lrlat";
            const char * const ATAKMosaicDatabase::COLUMN_LR_LON = "lrlon";
            const char * const ATAKMosaicDatabase::COLUMN_LL_LAT = "lllat";
            const char * const ATAKMosaicDatabase::COLUMN_LL_LON = "lllon";
            const char * const ATAKMosaicDatabase::COLUMN_MIN_GSD = "mingsd";
            const char * const ATAKMosaicDatabase::COLUMN_MAX_GSD = "maxgsd";
            const char * const ATAKMosaicDatabase::COLUMN_WIDTH = "width";
            const char * const ATAKMosaicDatabase::COLUMN_HEIGHT = "height";

            const char * const ATAKMosaicDatabase::TABLE_MOSAIC_DATA = "mosiacdata";
            const char * const ATAKMosaicDatabase::TABLE_COVERAGE = "coverage";


            ATAKMosaicDatabase::ATAKMosaicDatabase() : database(nullptr),
                insertStatement(nullptr),
                inTransaction(false),
                creation(false),
                coverageDiscovery(),
                coverages(),
                coverage(nullptr),
                mutex(TEMT_Recursive)
            {

            }

            ATAKMosaicDatabase::~ATAKMosaicDatabase()
            {
                try
                  {
                    close();
                  }
                catch (...)
                  { }
            }

            void ATAKMosaicDatabase::create(const char *file)
            {
                if (database != nullptr)
                    throw db::DB_Error("Invalid state: DB already open");

                if (util::pathExists(file) && strlen(file) > 0)
                    throw db::DB_Error("DB file already exists - refusing to create");
                util::deletePath(file);
                database = db::openDatabase(file);
                creation = true;

                std::unique_ptr<db::Cursor> result
                    (database->query("PRAGMA journal_mode=OFF"));

                result.reset (database->query("PRAGMA temp_store=MEMORY"));
                result->moveToNext ();
                result.reset (database->query("PRAGMA synchronous=OFF"));
                result->moveToNext ();
                result.reset (database->query("PRAGMA cache_size=8192"));
                result->moveToNext ();
                result.reset ();

                std::ostringstream str;
                str << "CREATE TABLE " << TABLE_MOSAIC_DATA << " (" <<
                    COLUMN_ID << " INTEGER PRIMARYKEY, " <<
                    COLUMN_TYPE << " TEXT, " <<
                    COLUMN_PATH << " TEXT, " <<
                    COLUMN_MIN_LAT << " REAL, " <<
                    COLUMN_MIN_LON << " REAL, " <<
                    COLUMN_MAX_LAT << " REAL, " <<
                    COLUMN_MAX_LON << " REAL, " <<
                    COLUMN_UL_LAT << " REAL, " <<
                    COLUMN_UL_LON << "  REAL, " <<
                    COLUMN_UR_LAT << " REAL, " <<
                    COLUMN_UR_LON << "  REAL, " <<
                    COLUMN_LR_LAT << " REAL, " <<
                    COLUMN_LR_LON << "  REAL, " <<
                    COLUMN_LL_LAT << " REAL, " <<
                    COLUMN_LL_LON << "  REAL, " <<
                    COLUMN_MIN_GSD << " REAL, " <<
                    COLUMN_MAX_GSD << " REAL, " <<
                    COLUMN_WIDTH << " INTEGER, " <<
                    COLUMN_HEIGHT << " INTEGER)";

                std::string strs = str.str();

                database->execute(strs.c_str());

                str.str("");
                str << "CREATE TABLE " << TABLE_COVERAGE << " (" <<
                    COLUMN_TYPE << " TEXT PRIMARYKEY, " <<
                    COLUMN_MIN_LAT << " REAL, " <<
                    COLUMN_MIN_LON << " REAL, " <<
                    COLUMN_MAX_LAT << " REAL, " <<
                    COLUMN_MAX_LON << " REAL, " <<
                    COLUMN_MIN_GSD << " REAL, " <<
                    COLUMN_MAX_GSD << " REAL)";

                strs = str.str();
                database->execute(strs.c_str());
            }

            void ATAKMosaicDatabase::open(const char *f)
            {
                if (database != nullptr)
                    throw db::DB_Error("A Database is already open - refusing to open new one");

                database = new db::SpatiaLiteDB(f);

                creation = false;

                CoverageDiscovery totalCoverage;

                std::ostringstream str;
                str << "SELECT * from " << TABLE_COVERAGE;
                std::string strs = str.str();
                std::unique_ptr<db::Cursor> result(database->query(strs.c_str()));
                Cursor cursor (std::move(result));
                while (cursor.moveToNext()) {
                    auto *poly = new feature::Polygon(feature::Geometry::_2D);
                    feature::LineString ls(feature::Geometry::_2D);
                    ls.addPoint (cursor.getMinLon (), cursor.getMaxLat ());
                    ls.addPoint (cursor.getMaxLon (), cursor.getMaxLat ());
                    ls.addPoint (cursor.getMaxLon (), cursor.getMinLat ());
                    ls.addPoint (cursor.getMinLon (), cursor.getMinLat ());
                    ls.addPoint (cursor.getMinLon (), cursor.getMaxLat ());
                    poly->addRing(ls);

                    auto *c = new Coverage(poly,
                                               cursor.getMinGSD (),
                                               cursor.getMaxGSD ());

                    feature::Envelope mbb = poly->getEnvelope();

                    totalCoverage.addLat(mbb.minY);
                    totalCoverage.addLon(mbb.minX);
                    totalCoverage.addLat(mbb.maxY);
                    totalCoverage.addLon(mbb.maxX);
                    totalCoverage.addGSD(c->minGSD);
                    totalCoverage.addGSD(c->maxGSD);

                    coverages.insert(std::make_pair (cursor.getType (), c));
                }

                auto *poly = new feature::Polygon(feature::Geometry::_2D);
                feature::LineString ls(feature::Geometry::_2D);
                ls.addPoint(totalCoverage.minLon, totalCoverage.maxLat);
                ls.addPoint(totalCoverage.maxLon, totalCoverage.maxLat);
                ls.addPoint(totalCoverage.maxLon, totalCoverage.minLat);
                ls.addPoint(totalCoverage.minLon, totalCoverage.minLat);
                ls.addPoint(totalCoverage.minLon, totalCoverage.maxLat);
                poly->addRing(ls);

                coverage = new Coverage(poly, totalCoverage.minGSD,
                                              totalCoverage.maxGSD);
            }

            void ATAKMosaicDatabase::close()
            {
                if (database == nullptr)
                    return;

                if (creation) {
                    if (inTransaction)
                        endTransaction();

                    if (insertStatement != nullptr) {
                        delete insertStatement;
                        insertStatement = nullptr;
                    }

                    if (coverageDiscovery.size() > 0) {
                        std::ostringstream str;
                        str << "INSERT into " << TABLE_COVERAGE << " ("
                            << COLUMN_MIN_LAT << ", "
                            << COLUMN_MIN_LON << ", "
                            << COLUMN_MAX_LAT << ", "
                            << COLUMN_MAX_LON << ", "
                            << COLUMN_MIN_GSD << ", "
                            << COLUMN_MAX_GSD << ", "
                            << COLUMN_TYPE << ") VALUES (?, ?, ?, ?, ?, ?, ?)";

                        std::string strs = str.str();
                        db::Database::Transaction trans (*database);
                        std::unique_ptr<db::Statement> stmt
                            (database->compileStatement(strs.c_str()));
                        std::map<std::string, CoverageDiscovery *>::iterator iter;
                        for (iter = coverageDiscovery.begin(); iter != coverageDiscovery.end(); ++ iter) {
                            stmt->clearBindings();
                            stmt->bind(1, iter->second->minLat);
                            stmt->bind(2, iter->second->minLon);
                            stmt->bind(3, iter->second->maxLat);
                            stmt->bind(4, iter->second->maxLon);
                            stmt->bind(5, iter->second->minGSD);
                            stmt->bind(6, iter->second->maxGSD);
                            stmt->bind(7, iter->first.c_str());

                            stmt->execute();
                        }
                        database->setTransactionSuccessful();
                    }

                }
                delete coverage;
                coverage = nullptr;

                std::map<std::string, CoverageDiscovery *>::iterator diter;
                for (diter = coverageDiscovery.begin(); diter != coverageDiscovery.end(); ++diter)
                    delete diter->second;
                coverageDiscovery.clear();

                std::map<std::string, Coverage *>::iterator iter;
                for (iter = coverages.begin(); iter != coverages.end(); ++iter)
                    delete iter->second;
                coverages.clear();

                inTransaction = false;
                creation = false;

                delete database;
                database = nullptr;
            }

            const char *ATAKMosaicDatabase::getType() const
            {
                return "atak";
            }

            MosaicDatabase::Coverage *ATAKMosaicDatabase::getCoverage() const
            {
                return coverage;
            }

            std::map<std::string, MosaicDatabase::Coverage *> ATAKMosaicDatabase::getCoverages() const
            {
                return coverages;
            }

            MosaicDatabase::Coverage *ATAKMosaicDatabase::getCoverage(const std::string &type) const
            {
                Coverage *c = nullptr;
                try {
                    c = coverages.at(type);
                } catch (const std::out_of_range&) {
                }
                if (creation)
                    return nullptr;
                return c;
            }

            Mutex& ATAKMosaicDatabase::getMutex() const
            {
                return mutex;
            }

            void ATAKMosaicDatabase::beginTransaction()
            {
                if (database == nullptr || !creation)
                    throw db::DB_Error("");
                database->beginTransaction();
                inTransaction = true;
            }

            void ATAKMosaicDatabase::setTransactionSuccessful()
            {
                if (database == nullptr || !creation)
                    throw db::DB_Error("");
                database->setTransactionSuccessful();
                inTransaction = false;
            }

            void ATAKMosaicDatabase::endTransaction()
            {
                database->endTransaction();
                inTransaction = false;
            }

            void ATAKMosaicDatabase::insertRow(const std::string &path, const std::string &type, core::GeoPoint ul, core::GeoPoint ur, core::GeoPoint lr,
                                               core::GeoPoint ll, double minGsd, double maxGsd, int width, int height)
            {

            }

            ATAKMosaicDatabase::Cursor *ATAKMosaicDatabase::query() const
            {
                return query(nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr);
            }

            ATAKMosaicDatabase::Cursor *ATAKMosaicDatabase::query(const std::string *path) const
            {
                if (path == nullptr)
                    return query(nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr);

                std::ostringstream str;
                str << COLUMN_PATH << " = \'" << path << "\'";
                std::string strs = str.str();
                return query(nullptr, &strs, nullptr, nullptr, nullptr, nullptr, nullptr);
            }

            ATAKMosaicDatabase::Cursor *ATAKMosaicDatabase::query(const core::GeoPoint *p, double minGSD, double maxGSD) const
            {
                return query((const std::set<std::string> *)nullptr, p, minGSD, maxGSD);
            }

            ATAKMosaicDatabase::Cursor *ATAKMosaicDatabase::query(const std::set<std::string> *types, const core::GeoPoint *p, double minGSD, double maxGSD) const
            {
                if (types == nullptr&& p == nullptr && isnan(minGSD) && isnan(maxGSD))
                    throw std::invalid_argument("invalid query parameters");

                SelectionBuilder selection;
                if (types != nullptr) {
                    std::ostringstream typesArg;
                    typesArg << COLUMN_TYPE << " IN (";
                    std::set<std::string>::iterator iter;
                    iter = types->begin();
                    while (iter != types->end()) {
                        typesArg << "\'" << *iter << "\'";
                        ++iter;
                        if (iter != types->end())
                            typesArg << ", ";
                    }
                    typesArg << ")";

                    std::string str = typesArg.str();
                    selection.append(str);
                }
                if (p != nullptr) {
                    std::ostringstream parg;
                    parg << COLUMN_MIN_LAT << " <= " << p->latitude  << " AND "
                         << COLUMN_MAX_LAT << " >= " << p->latitude  << " AND "
                         << COLUMN_MIN_LON << " <= " << p->longitude << " AND "
                         << COLUMN_MAX_LON << " >= " << p->longitude;
                    selection.append(parg.str());
                }
                if (!isnan(minGSD)) {
                    std::ostringstream gsdarg;
                    gsdarg << COLUMN_MAX_GSD << " <= " << minGSD;
                    selection.append(gsdarg.str());
                }
                if (!isnan(maxGSD)) {
                    std::ostringstream gsdarg;
                    gsdarg << COLUMN_MIN_GSD << " >= " << maxGSD;
                    selection.append(gsdarg.str());
                }

                std::ostringstream sortarg;
                sortarg << COLUMN_MAX_GSD << " ASC";
                std::string selectionstr = selection.getSelection();
                std::string sortargstr = sortarg.str();
                return query(nullptr, &selectionstr, nullptr, nullptr, nullptr, &sortargstr,
                                  nullptr);
            }

            ATAKMosaicDatabase::Cursor *ATAKMosaicDatabase::query(const core::GeoPoint *roiUL, const core::GeoPoint *roiLR, double minGSD, double maxGSD) const
            {
                return query(nullptr, roiUL, roiLR, minGSD, maxGSD);
            }

            ATAKMosaicDatabase::Cursor *ATAKMosaicDatabase::query(const std::set<std::string> *types, const core::GeoPoint *roiUL, const core::GeoPoint *roiLR,
                                              double minGSD, double maxGSD) const
            {
                SelectionBuilder selection;
                std::ostringstream roiarg;
                roiarg << COLUMN_MIN_LAT << " <= " << roiUL->latitude  << " AND "
                       << COLUMN_MAX_LAT << " >= " << roiLR->latitude  << " AND "
                       << COLUMN_MIN_LON << " <= " << roiLR->longitude << " AND "
                       << COLUMN_MAX_LON << " >= " << roiUL->longitude;
                selection.append(roiarg.str());

                if (types != nullptr) {
                    std::ostringstream typesArg;
                    typesArg << COLUMN_TYPE << " IN (";
                    auto iter = types->begin();
                    while (iter != types->end ()) {
                        typesArg << "\'" << *iter << "\'";
                        iter++;
                        if (iter != types->end ())
                            typesArg << ", ";
                    }
                    typesArg << ")";

                    selection.append(typesArg.str());
                }

                if (!isnan(minGSD)) {
                    std::ostringstream gsdarg;
                    gsdarg << COLUMN_MAX_GSD << " <= " << minGSD;
                    selection.append(gsdarg.str());
                }
                if (!isnan(maxGSD)) {
                    std::ostringstream gsdarg;
                    gsdarg << COLUMN_MIN_GSD << " >= " << maxGSD;
                    selection.append(gsdarg.str());
                }

                //System.out.println("sql=" + selection.getSelection());

                std::stringstream sortarg;
                sortarg << COLUMN_MAX_GSD << ", " << COLUMN_TYPE << " ASC";
                std::string selectionstr = selection.getSelection();
                std::string sortargstr = sortarg.str();
                return query(nullptr, &selectionstr, nullptr, nullptr, nullptr,
                             &sortargstr, nullptr);
            }

            ATAKMosaicDatabase::Cursor *ATAKMosaicDatabase::query(const std::vector<std::string> *columns, const std::string *selection, const std::vector<std::string> *selectionArgs,
                                              const std::string *groupBy, const std::string *having, const std::string *orderBy, const std::string *limit) const
            {
                if (database == nullptr || creation)
                    throw db::DB_Error("Database not opened or open in create mode");

                std::unique_ptr<db::Cursor> result(database->query(TABLE_MOSAIC_DATA, columns, selection,
                    selectionArgs, groupBy, having, orderBy, limit));
                return new Cursor(std::move(result));
            }

            ATAKMosaicDatabase *ATAKMosaicDatabase::getSynchronizedInstance(ATAKMosaicDatabase *existing)
            {
                return new SynchronizedDB(existing);
            }

            MosaicDatabase *ATAKMosaicDatabase::newSynchronizedInstance()
            {
                return getSynchronizedInstance(new ATAKMosaicDatabase());
            }

            ATAKMosaicDatabase::Cursor::Cursor(std::unique_ptr<db::Cursor> &&filter)
              : MosaicDatabase::Cursor(std::move(filter))
              { }

            core::GeoPoint
            ATAKMosaicDatabase::Cursor::getUpperLeft ()
                const
              { return getPoint (COLUMN_UL_LAT, COLUMN_UL_LON); }

            core::GeoPoint
            ATAKMosaicDatabase::Cursor::getUpperRight ()
                const
              { return getPoint (COLUMN_UR_LAT, COLUMN_UR_LON); }

            core::GeoPoint
            ATAKMosaicDatabase::Cursor::getLowerRight ()
                const
              { return getPoint (COLUMN_LR_LAT, COLUMN_LR_LON); }

            core::GeoPoint
            ATAKMosaicDatabase::Cursor::getLowerLeft ()
                const
              { return getPoint (COLUMN_LL_LAT, COLUMN_LL_LON); }

            double
            ATAKMosaicDatabase::Cursor::getMinLat ()
                const
              { return getDouble (COLUMN_MIN_LAT); }

            double
            ATAKMosaicDatabase::Cursor::getMinLon ()
                const
              { return getDouble (COLUMN_MIN_LON); }

            double
            ATAKMosaicDatabase::Cursor::getMaxLat ()
                const
              { return getDouble (COLUMN_MAX_LAT); }

            double
            ATAKMosaicDatabase::Cursor::getMaxLon ()
                const
              { return getDouble (COLUMN_MAX_LON); }

            std::string
            ATAKMosaicDatabase::Cursor::getPath ()
                const
              { return getString (COLUMN_PATH); }

            std::string
            ATAKMosaicDatabase::Cursor::getType ()
                const
              { return getString (COLUMN_TYPE); }

            double
            ATAKMosaicDatabase::Cursor::getMinGSD ()
                const
              { return getDouble (COLUMN_MIN_GSD); }

            double
            ATAKMosaicDatabase::Cursor::getMaxGSD ()
                const
              { return getDouble (COLUMN_MAX_GSD); }

            int
            ATAKMosaicDatabase::Cursor::getWidth ()
                const
              { return getInt (COLUMN_WIDTH); }

            int
            ATAKMosaicDatabase::Cursor::getHeight ()
                const
              { return getInt (COLUMN_HEIGHT); }

            int64_t
            ATAKMosaicDatabase::Cursor::getId ()
                const
              { return getLong (COLUMN_ID); }
        }
    }
}
