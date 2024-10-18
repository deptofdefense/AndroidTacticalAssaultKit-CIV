#ifdef MSVC
#include "formats/osmdroid/OSMDroidContainer.h"

#include <vector>
#include <unordered_map>

#include "db/DatabaseFactory.h"
#include "db/Query.h"
#include "db/Statement2.h"
#include "core/Projection2.h"
#include "core/ProjectionFactory3.h"
#include "formats/osmdroid/OSMDroidInfo.h"
#include "port/STLVectorAdapter.h"
#include "raster/osm/OSMUtils.h"
#include "renderer/BitmapFactory2.h"
#include "thread/Lock.h"
#include "thread/Mutex.h"
#include "thread/Thread.h"
#include "util/DataOutput2.h"
#include "util/IO2.h"
#include "util/MathUtils.h"
#include "util/Memory.h"

using namespace TAK::Engine::Formats::OSMDroid;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::DB;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Raster::TileMatrix;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

#define MAX_NUM_LEVELS 30u

namespace
{
    struct PrecompiledStatements
    {
        ThreadID tid;
        QueryPtr queryTileExpiration{ QueryPtr(nullptr, nullptr) };
        QueryPtr queryTileData{ QueryPtr(nullptr, nullptr) };
        QueryPtr queryTileExists{ QueryPtr(nullptr, nullptr) };
        StatementPtr insertTile{ StatementPtr(nullptr, nullptr) };
        StatementPtr updateTile{ StatementPtr(nullptr, nullptr) };
        StatementPtr insertExpiration{ StatementPtr(nullptr, nullptr) };
        StatementPtr updateExpiration{ StatementPtr(nullptr, nullptr) };
    };

    class OSMDroidContainer : public TileContainer
    {
    public :
        OSMDroidContainer(const OSMDroidInfo &info, DatabasePtr &&database) NOTHROWS;
    public : // TileMatrix
        const char* getName() const NOTHROWS override;
        int getSRID() const NOTHROWS override;
        TAKErr getZoomLevel(Collection<TAK::Engine::Raster::TileMatrix::TileMatrix::ZoomLevel>& value) const NOTHROWS override;
        double getOriginX() const NOTHROWS override;
        double getOriginY() const NOTHROWS override;
        TAKErr getTile(BitmapPtr& result, const std::size_t zoom, const std::size_t x, const std::size_t y) NOTHROWS override;
        TAKErr getTileData(std::unique_ptr<const uint8_t, void (*)(const uint8_t*)>& value, std::size_t* len,
                                            const std::size_t zoom, const std::size_t x, const std::size_t y) NOTHROWS override;
        TAKErr getBounds(Envelope2 *value) const NOTHROWS override;
    public : // TileContainer
        TAKErr isReadOnly(bool* value) NOTHROWS override;
        TAKErr setTile(const std::size_t level, const std::size_t x, const std::size_t y, const uint8_t* value, const std::size_t len, const int64_t expiration) NOTHROWS override;
        TAKErr setTile(const std::size_t level, const std::size_t x, const std::size_t y, const Bitmap2* data, const int64_t expiration) NOTHROWS override;
        bool hasTileExpirationMetadata() NOTHROWS override;
        int64_t getTileExpiration(const std::size_t level, const std::size_t x, const std::size_t y) NOTHROWS override;
    private :
        PrecompiledStatements &getPrecompiledStmts() NOTHROWS;
        bool hasTile(const std::size_t level, const std::size_t x, const std::size_t y) NOTHROWS;
        TAKErr getTileDataImpl(const uint8_t **value, std::size_t* len,
                               const std::size_t zoom, const std::size_t x, const std::size_t y) NOTHROWS;
    private :
        DatabasePtr db;
        bool hasAtakMetadata;
        OSMDroidInfo info;
        Envelope2 bounds;
        PrecompiledStatements precompiledStmts[16u];
        std::list<PrecompiledStatements> precompiledStmtOverflow;
        Mutex mutex;
        std::vector<TileMatrix::ZoomLevel> zoomLevels;
    };

    class SpiImpl : public TileContainerSpi
    {
    public:
        const char *getName() const NOTHROWS override;
        const char *getDefaultExtension() const NOTHROWS override;
        TAKErr create(TileContainerPtr &result, const char *name, const char *path, const TileMatrix *spec) const NOTHROWS override;
        TAKErr open(TileContainerPtr &result, const char *path, const TileMatrix *spec, bool readOnly) const NOTHROWS override;
        TAKErr isCompatible(bool *result, const TileMatrix *spec) const NOTHROWS override;
    };

    Envelope2 getMatrixBounds(const int srid) NOTHROWS
    {
        Projection2Ptr proj(nullptr, nullptr);
        if (ProjectionFactory3_create(proj, srid) != TE_Ok)
            return Envelope2();

        TAK::Engine::Math::Point2<double> ul;
        proj->forward(&ul, GeoPoint2(proj->getMaxLatitude(), proj->getMinLongitude()));
        TAK::Engine::Math::Point2<double> ur;
        proj->forward(&ur, GeoPoint2(proj->getMaxLatitude(), proj->getMaxLongitude()));
        TAK::Engine::Math::Point2<double> lr;
        proj->forward(&lr, GeoPoint2(proj->getMinLatitude(), proj->getMaxLongitude()));
        TAK::Engine::Math::Point2<double> ll;
        proj->forward(&ll, GeoPoint2(proj->getMinLatitude(), proj->getMinLongitude()));
        
        return Envelope2(MathUtils_min(ul.x, ur.x, lr.x, ll.x),
                         MathUtils_min(ul.y, ur.y, lr.y, ll.y),
                         0.0,
                         MathUtils_max(ul.x, ur.x, lr.x, ll.x),
                         MathUtils_max(ul.y, ur.y, lr.y, ll.y),
                         0.0);
    }

    Envelope2 bounds3857() NOTHROWS
    {
        static const Envelope2 b3857 = getMatrixBounds(3857);
        return b3857;
    }
    Envelope2 bounds4326() NOTHROWS
    {
        static const Envelope2 b4326 = getMatrixBounds(3857);
        return b4326;
    }

    TileMatrix::ZoomLevel createLevel0(const int srid, const std::size_t gridCols, const std::size_t gridRows) NOTHROWS
    {
        TileMatrix::ZoomLevel retval;
        retval.level = 0;

        Projection2Ptr proj(nullptr, nullptr);
        if (ProjectionFactory3_create(proj, srid) != TE_Ok)
            return retval;

        TAK::Engine::Math::Point2<double> upperLeft;
        proj->forward(&upperLeft, GeoPoint2(proj->getMaxLatitude(), proj->getMinLongitude()));
        TAK::Engine::Math::Point2<double> lowerRight;
        proj->forward(&lowerRight, GeoPoint2(proj->getMinLatitude(), proj->getMaxLongitude()));
        
        // XXX - better resolution for 4326???
        retval.resolution = atakmap::raster::osm::OSMUtils::mapnikTileResolution(retval.level);
        retval.tileWidth = 256;
        retval.tileHeight = 256;
        retval.pixelSizeX = (lowerRight.x-upperLeft.x) / (retval.tileWidth*gridCols);
        retval.pixelSizeY = (upperLeft.y-lowerRight.y) / (retval.tileHeight*gridRows);
        return retval;
    }

    std::vector<TileMatrix::ZoomLevel> zoomLevels(const int srid, std::size_t gridCols, std::size_t gridRows) NOTHROWS
    {
        std::vector<TileMatrix::ZoomLevel> levels;
        STLVectorAdapter<TileMatrix::ZoomLevel> levels_a(levels);
        TileMatrix_createQuadtree(&levels_a, createLevel0(srid, gridCols, gridRows), MAX_NUM_LEVELS);
        return levels;
    }
            
    const std::vector<TileMatrix::ZoomLevel>& osmDroidZoomLevels3857() NOTHROWS
    {
        static std::vector<TileMatrix::ZoomLevel> z3857 = zoomLevels(3857, 1, 1);
        return z3857;
    }
    const std::vector<TileMatrix::ZoomLevel>& osmDroidZoomLevels4326() NOTHROWS
    {
        static std::vector<TileMatrix::ZoomLevel> z4326 = zoomLevels(4326, 2, 1);
        return z4326;
    }

    bool contains(const std::vector<String> &v, const char *s) NOTHROWS
    {
        return std::any_of(v.begin(), v.end(), [s](const String& e) { return e == s; });
    }

    bool isCompatibleSchema(Database2 &db, const bool atakMetadata) NOTHROWS
    {
        std::vector<String> columnNames;
        STLVectorAdapter<String> columnNames_a;
        bool hasAll;

        if (Databases_getColumnNames(columnNames_a, db, "tiles") != TE_Ok)
            return false;
        hasAll = !columnNames.empty();
        hasAll &= contains(columnNames, "key");
        hasAll &= contains(columnNames, "provider");
        hasAll &= contains(columnNames, "tile");
        columnNames.clear();
        if (!hasAll)
            return false;

        if(atakMetadata) {
            if (Databases_getColumnNames(columnNames_a, db, "ATAK_metadata") != TE_Ok)
                return false;
            hasAll = !columnNames.empty();
            hasAll &= contains(columnNames, "key");
            hasAll &= contains(columnNames, "value");
            columnNames.clear();
            if (!hasAll)
                return false;

            if (Databases_getColumnNames(columnNames_a, db, "ATAK_catalog") != TE_Ok)
                return false;
            hasAll = !columnNames.empty();
            hasAll &= contains(columnNames, "key");
            hasAll &= contains(columnNames, "access");
            hasAll &= contains(columnNames, "expiration");
            hasAll &= contains(columnNames, "size");
            columnNames.clear();
            if (!hasAll)
                return false;
        }

        return true;
    }
    
    TAKErr createTables(Database2 &db, const int srid_, const bool atakMetadataAlways) NOTHROWS
    {
        TAKErr code(TE_Ok);
        int srid = srid_;
        std::vector<String> tables;
        STLVectorAdapter<String> tables_a(tables);
        code = Databases_getTableNames(tables_a, db);
        TE_CHECKRETURN_CODE(code);

        if (!contains(tables, "tiles")) {
            code = db.execute("CREATE TABLE tiles (key INTEGER PRIMARY KEY, provider TEXT, tile BLOB)", nullptr, 0u);
            TE_CHECKRETURN_CODE(code);
        }
        if(srid < 0)
            srid = 3857;
        if((srid != 3857) || atakMetadataAlways) {
            if (!contains(tables, "ATAK_catalog")) {
                code = db.execute("CREATE TABLE ATAK_catalog (key INTEGER PRIMARY KEY, access INTEGER, expiration INTEGER, size INTEGER)", nullptr, 0u);
                TE_CHECKRETURN_CODE(code);
            }
            if (!contains(tables, "ATAK_metadata")) {
                code = db.execute("CREATE TABLE ATAK_metadata (key TEXT, value TEXT)", nullptr, 0u);
                TE_CHECKRETURN_CODE(code);
                if(srid >= 0) {
                    StatementPtr stmt(nullptr, nullptr);
                    code = db.compileStatement(stmt, "INSERT INTO ATAK_metadata (key, value) VALUES(?, ?)");
                    TE_CHECKRETURN_CODE(code);
                    code = stmt->bindString(1, "srid");
                    TE_CHECKRETURN_CODE(code);
                    code = stmt->bindInt(2, srid);
                    TE_CHECKRETURN_CODE(code);
                    code = stmt->execute();
                    TE_CHECKRETURN_CODE(code);
                }
            }
        }

        return code;
    }
    
    bool isCompatible(const TileMatrix &spec) NOTHROWS
    {
        // verify compatible SRID and origin
        const std::vector<TileMatrix::ZoomLevel> *zoomLevels = nullptr;
        Envelope2 bnds;
        switch(spec.getSRID()) {
            case 4326 :
                zoomLevels = &osmDroidZoomLevels4326();
                bnds = bounds4326();
                break;
            case 900913 :
            case 3857 :
                zoomLevels = &osmDroidZoomLevels3857();
                bnds = bounds3857();
                break;
            default :
                return false;
        }
        if(spec.getOriginX() != bnds.minX || spec.getOriginY() != bnds.maxY)
            return false;
        
        // check compatibility of tiling
        std::vector<TileMatrix::ZoomLevel> specLevels;
        STLVectorAdapter<TileMatrix::ZoomLevel> specLevels_a;
        if (spec.getZoomLevel(specLevels_a) != TE_Ok)
            return false;
        const std::size_t limit = zoomLevels->size()-1;
        for(int i = 0; i < specLevels.size(); i++) {
            // check for out of bounds level
            if(specLevels[i].level < 0 || specLevels[i].level > limit)
                return false;
            
            // NOTE: resolution is only 'informative' so we aren't going to
            // check it
            const TileMatrix::ZoomLevel &level = (*zoomLevels)[specLevels[i].level];
            if(specLevels[i].pixelSizeX != level.pixelSizeX ||
               specLevels[i].pixelSizeY != level.pixelSizeY ||
               specLevels[i].tileWidth != level.tileWidth ||
               specLevels[i].tileHeight != level.tileHeight) {

                return false;
            }
        }
        
        return true;
    }
}

TAKErr TAK::Engine::Formats::OSMDroid::OSMDroidContainer_openOrCreate(TileContainerPtr& value, const char *path, const char *provider, const int srid) NOTHROWS
{
    TAKErr code(TE_Ok);
    bool exists;
    code = IO_exists(&exists, path);
    TE_CHECKRETURN_CODE(code);
    // try open
    if(exists) {
        if (OSMDroidContainer_spi()->open(value, path, nullptr, false) == TE_Ok)
            return TE_Ok;
        code = IO_delete(path);
        TE_CHECKRETURN_CODE(code);
    }

    // create
    DatabasePtr db(nullptr, nullptr);
    code = DatabaseFactory_create(db, DatabaseInformation(path, nullptr, 0));
    TE_CHECKRETURN_CODE(code);

    code = createTables(*db, srid, true);
    TE_CHECKRETURN_CODE(code);

    OSMDroidInfo info;
    info.srid = srid;
    info.provider = provider;
    info.minLevel = 0;
    info.maxLevel = MAX_NUM_LEVELS;
    info.gridZeroWidth = (info.srid == 4326) ? 2 : 1;
    info.gridZeroHeight = 1;
    info.minLevelGridMinX = 0;
    info.minLevelGridMinY = 0;
    info.minLevelGridMaxX = info.gridZeroWidth - 1;
    info.minLevelGridMaxY = info.gridZeroHeight - 1;
    info.tileWidth = 256;
    info.tileHeight = 256;

    value = TileContainerPtr(new(std::nothrow) OSMDroidContainer(info, std::move(db)), Memory_deleter_const<TileContainer, OSMDroidContainer>);
    if (!value)
        return TE_OutOfMemory;

    return TE_Ok;
}
std::shared_ptr<TileContainerSpi> TAK::Engine::Formats::OSMDroid::OSMDroidContainer_spi() NOTHROWS
{
    static std::shared_ptr<TileContainerSpi> spi(TileContainerSpiPtr(new SpiImpl(), Memory_deleter_const<TileContainerSpi, SpiImpl>));
    return spi;
}

namespace
{
    OSMDroidContainer::OSMDroidContainer(const OSMDroidInfo &info_, DatabasePtr &&database) NOTHROWS :
        info(info_),
        db(std::move(database))
    {
        switch(info.srid) {
            case 4326 :
                this->zoomLevels = osmDroidZoomLevels4326();
                this->bounds = bounds4326();
                break;
            case 900913 :
                info.srid = 3857;
            case 3857 :
                this->zoomLevels = osmDroidZoomLevels3857();
                this->bounds = bounds3857();
                break;
            default :
                // XXX - 
                break;
        }

        std::vector<String> tableNames;
        STLVectorAdapter<String> tableNames_a(tableNames);
        Databases_getTableNames(tableNames_a, *this->db);
        this->hasAtakMetadata = contains(tableNames, "ATAK_metadata") && contains(tableNames, "ATAK_catalog");        
    }

    const char* OSMDroidContainer::getName() const NOTHROWS
    {
        return info.provider;
    }
    int OSMDroidContainer::getSRID() const NOTHROWS
    {
        return info.srid;
    }
    TAKErr OSMDroidContainer::getZoomLevel(Collection<TAK::Engine::Raster::TileMatrix::TileMatrix::ZoomLevel>& value) const NOTHROWS
    {
         TAKErr code(TE_Ok);
        code = value.clear();
        TE_CHECKRETURN_CODE(code);

        for(const auto &z : this->zoomLevels) {
            code = value.add(z);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);

        return code;
    }
    double OSMDroidContainer::getOriginX() const NOTHROWS
    {
        return bounds.minX;
    }
    double OSMDroidContainer::getOriginY() const NOTHROWS
    {
        return bounds.maxY;
    }
    TAKErr OSMDroidContainer::getTile(BitmapPtr& result, const std::size_t zoom, const std::size_t x, const std::size_t y) NOTHROWS
    {
        TAKErr code(TE_Ok);
        Lock lock(mutex);
        const uint8_t *blob_const;
        std::size_t len;
        code = getTileDataImpl(&blob_const, &len, zoom, x, y);
        TE_CHECKRETURN_CODE(code);
        if (!len)
            return TE_Ok;
        code = BitmapFactory2_decode(result, blob_const, len, nullptr);
        TE_CHECKRETURN_CODE(code);

        return code;
    }
    TAKErr OSMDroidContainer::getTileData(std::unique_ptr<const uint8_t, void (*)(const uint8_t*)>& value, std::size_t* len,
                                        const std::size_t zoom, const std::size_t x, const std::size_t y) NOTHROWS
    {
        TAKErr code(TE_Ok);

        Lock lock(mutex);
        const uint8_t *blob_const;
        code = getTileDataImpl(&blob_const, len, zoom, x, y);
        if (code == TE_Done) // skip logging on common, non-error case
            return TE_Done;
        // check for error
        TE_CHECKRETURN_CODE(code);
        if (!len)
            return TE_Ok;

        array_ptr<uint8_t> blob(new(std::nothrow) uint8_t[*len]);
        if (!blob.get())
            return TE_OutOfMemory;
        memcpy(blob.get(), blob_const, *len);

        value = std::unique_ptr<const uint8_t, void(*)(const uint8_t*)>(blob.release(), Memory_array_deleter_const<uint8_t>);
        return code;
    }
    TAKErr OSMDroidContainer::getBounds(Envelope2 *value) const NOTHROWS
    {
        *value = bounds;
        return TE_Ok;
    }
    TAKErr OSMDroidContainer::isReadOnly(bool* value) NOTHROWS
    {
        return db->isReadOnly(value);
    }
    TAKErr OSMDroidContainer::setTile(const std::size_t level, const std::size_t x, const std::size_t y, const uint8_t* value, const std::size_t len, const int64_t expiration) NOTHROWS
    {
        TAKErr code(TE_Ok);
        bool readOnly;
        code = this->isReadOnly(&readOnly);
        TE_CHECKRETURN_CODE(code);

        if(readOnly)
            return TE_Unsupported;
        
        Lock lock(mutex);
        PrecompiledStatements &stmts = getPrecompiledStmts();

        const bool update = hasTile(level, x, y);

        Statement2 *stmt;
        
        stmt = nullptr;
        if(update) {
            if (!stmts.updateTile) {
                code = this->db->compileStatement(stmts.updateTile, "UPDATE tiles SET provider = ?, tile = ? WHERE key = ?");
                TE_CHECKRETURN_CODE(code);
            }
            stmt = stmts.updateTile.get();
        } else {
            if (!stmts.insertTile) {
                code = this->db->compileStatement(stmts.insertTile, "INSERT INTO tiles (provider, tile, key) VALUES(?, ?, ?)");
                TE_CHECKRETURN_CODE(code);
            }
            stmt = stmts.insertTile.get();
        }
        code = stmt->clearBindings();
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindString(1, this->info.provider);
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindBlob(2, value, len);
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindLong(3, atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteIndex(level, x, y));
        TE_CHECKRETURN_CODE(code);
            
        code = stmt->execute();
        TE_CHECKRETURN_CODE(code);
        
        if(this->hasAtakMetadata) {
            stmt = nullptr;
            if(update) {
                if (!stmts.updateExpiration) {
                    code = this->db->compileStatement(stmts.updateExpiration, "UPDATE ATAK_catalog SET expiration = ? WHERE key = ?");
                    TE_CHECKRETURN_CODE(code);
                }
                stmt = stmts.updateExpiration.get();
            } else {
                if (!stmts.insertExpiration) {
                    code = this->db->compileStatement(stmts.insertExpiration, "INSERT INTO ATAK_catalog (expiration, key) VALUES(?, ?)");
                    TE_CHECKRETURN_CODE(code);
                }
                stmt = stmts.insertExpiration.get();
            }
            code = stmt->clearBindings();
            TE_CHECKRETURN_CODE(code);
            code = stmt->bindLong(1, expiration);
            TE_CHECKRETURN_CODE(code);
            code = stmt->bindLong(2, atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteIndex(level, x, y));
            TE_CHECKRETURN_CODE(code);
                
            code = stmt->execute();
            TE_CHECKRETURN_CODE(code);
        }

        return code;
    }
    TAKErr OSMDroidContainer::setTile(const std::size_t level, const std::size_t x, const std::size_t y, const Bitmap2* data, const int64_t expiration) NOTHROWS
    {
        if(!data) {
            return setTile(level, x, y, nullptr, 0u, expiration);
        } else {
            TAKErr code(TE_Ok);
            DynamicOutput encoded;
            code = encoded.open(data->getWidth() * data->getHeight() * 2);
            TE_CHECKRETURN_CODE(code);

            BitmapEncodeOptions opts;
            switch(data->getFormat()) {
            case Bitmap2::BGR24 :
            case Bitmap2::MONOCHROME :
            case Bitmap2::RGB24 :
            case Bitmap2::RGB565 :
                opts.format = TEBF_JPEG;
                break;
            default :
                opts.format = TEBF_PNG;
                break;
            }
            code = BitmapFactory2_encode(encoded, *data, opts);
            TE_CHECKRETURN_CODE(code);

            const uint8_t *blob;
            std::size_t blobLen;
            code = encoded.get(&blob, &blobLen);
            TE_CHECKRETURN_CODE(code);

            code = setTile(level, x, y, blob, blobLen, expiration);
            TE_CHECKRETURN_CODE(code);

            return code;
        }
    }
    bool OSMDroidContainer::hasTileExpirationMetadata() NOTHROWS
    {
        return hasAtakMetadata;
    }
    int64_t OSMDroidContainer::getTileExpiration(const std::size_t level, const std::size_t x, const std::size_t y) NOTHROWS
    {
        if(!this->hasAtakMetadata)
            return -1L;

        Lock lock(mutex);
        PrecompiledStatements &stmts = getPrecompiledStmts();
        
        if (!stmts.queryTileExpiration) {
            if (this->db->compileQuery(stmts.queryTileExpiration, "SELECT expiration FROM ATAK_catalog WHERE key = ? LIMIT 1") != TE_Ok)
                return -1LL;
        }

        Query& result = *stmts.queryTileExpiration;
        result.clearBindings();
        result.bindLong(1, atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteIndex(level, x, y));
        if(result.moveToNext() != TE_Ok)
            return -1L;
        int64_t retval;
        result.getLong(&retval, 0);
        return retval;
    }
    TAKErr OSMDroidContainer::getTileDataImpl(const uint8_t **value, std::size_t* len,
                                              const std::size_t zoom, const std::size_t x, const std::size_t y) NOTHROWS
    {
        TAKErr code(TE_Ok);
        PrecompiledStatements &stmts = getPrecompiledStmts();
        
        if (!stmts.queryTileData) {
            code = this->db->compileQuery(stmts.queryTileData, "SELECT tile FROM tiles WHERE key = ? LIMIT 1");
            TE_CHECKRETURN_CODE(code);
        }
        auto &result = *stmts.queryTileData;

        code = result.clearBindings();
        TE_CHECKRETURN_CODE(code);
        code = result.bindLong(1, atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteIndex(zoom, x, y));
        TE_CHECKRETURN_CODE(code);
            
        code = result.moveToNext();
        // check for this explicitly to minimize logging for a common, non-error case for sparse pyramids
        if (code == TE_Done) 
            return code;
        // handle errors
        TE_CHECKRETURN_CODE(code);

        code = result.getBlob(value, len, 0);
        TE_CHECKRETURN_CODE(code);

        return code;
    }
    PrecompiledStatements &OSMDroidContainer::getPrecompiledStmts() NOTHROWS
    {
        static ThreadID notid = ThreadID();
        const ThreadID tid = Thread_currentThreadID();
        const std::size_t limit = 16u;
        for(std::size_t i = 0; i < limit; i++) {
            if(this->precompiledStmts[i].tid == notid) {
                this->precompiledStmts[i].tid = tid;
            } else if(this->precompiledStmts[i].tid != tid) {
                continue;
            }
            
            return this->precompiledStmts[i];
        }

        // bias on assumption that most recently added is most likely to be subsequently accessed
        for(auto it = this->precompiledStmtOverflow.rbegin(); it != this->precompiledStmtOverflow.rend(); it++) {
            if ((*it).tid == tid)
                return *it;
        }
        this->precompiledStmtOverflow.push_back(PrecompiledStatements());
        this->precompiledStmtOverflow.back().tid = tid;
        return this->precompiledStmtOverflow.back();
    }
    bool OSMDroidContainer::hasTile(const std::size_t level, const std::size_t x, const std::size_t y) NOTHROWS
    {
        auto &stmts = getPrecompiledStmts();
        
        if (!stmts.queryTileExists) {
            if (this->db->compileQuery(stmts.queryTileExists, "SELECT 1 FROM tiles WHERE key = ? LIMIT 1") != TE_Ok)
                return false;
        }
        auto &result = *stmts.queryTileExists;
            
        result.clearBindings();
        result.bindLong(1, atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteIndex(level, x, y));
        return result.moveToNext() == TE_Ok;
    }

    const char *SpiImpl::getName() const NOTHROWS
    {
        return "OSMDroid";
    }
    const char *SpiImpl::getDefaultExtension() const NOTHROWS
    {
        return ".sqlite";
    }
    TAKErr SpiImpl::create(TileContainerPtr &result, const char *name, const char *path, const TileMatrix *spec) const NOTHROWS
    {
        TAKErr code(TE_Ok);

        if (!spec)
            return TE_InvalidArg;

        // verify compatibility
        bool compatible;
        code = isCompatible(&compatible, spec);
        TE_CHECKRETURN_CODE(code);
        if(!compatible)
            return TE_InvalidArg;
            
        // since we are creating, if the file exists delete it to overwrite
        bool exists;
        code = IO_exists(&exists, path);
        TE_CHECKRETURN_CODE(code);
        if(exists) {
            code = IO_delete(path);
            TE_CHECKRETURN_CODE(code);
        }
            
        // adopt the name from the spec if not defined
        if(!name)
            name = spec->getName();

        DatabasePtr db(nullptr, nullptr);

        code = DatabaseFactory_create(db, DatabaseInformation(path, nullptr, 0));
        TE_CHECKRETURN_CODE(code);

        code = createTables(*db, spec->getSRID(), true);
        TE_CHECKRETURN_CODE(code);
        
        OSMDroidInfo info;
        info.srid = spec->getSRID();
        info.provider = name;
        info.minLevel = 0;
        info.maxLevel = MAX_NUM_LEVELS;
        info.gridZeroWidth = (info.srid == 4326) ? 2 : 1;
        info.gridZeroHeight = 1;
        info.minLevelGridMinX = 0;
        info.minLevelGridMinY = 0;
        info.minLevelGridMaxX = info.gridZeroWidth - 1;
        info.minLevelGridMaxY = info.gridZeroHeight - 1;
        info.tileWidth = 256;
        info.tileHeight = 256;

        result = TileContainerPtr(new(std::nothrow) OSMDroidContainer(info, std::move(db)), Memory_deleter_const<TileContainer, OSMDroidContainer>);
        if (!result)
            return TE_OutOfMemory;

        return code;
    }
    TAKErr SpiImpl::open(TileContainerPtr &result, const char *path, const TileMatrix *spec, bool readOnly) const NOTHROWS
    {
        TAKErr code(TE_Ok);

        bool exists;
        code = IO_exists(&exists, path);
        TE_CHECKRETURN_CODE(code);
        if (!exists)
            return TE_InvalidArg;
            
        DatabasePtr db(nullptr, nullptr);

        code = DatabaseFactory_create(db, DatabaseInformation(path, nullptr, readOnly ? DATABASE_OPTIONS_READONLY : 0));
        TE_CHECKRETURN_CODE(code);

        OSMDroidInfo info;
        code = OSMDroidInfo_get(&info, *db, TEBD_Skip);
        TE_CHECKRETURN_CODE(code);

        // if a spec is specified, verify compatibility
        if(spec) {
            // ensure spec shares same SRID
            if(info.srid != spec->getSRID())
                return TE_InvalidArg;
            // check compatibility
            bool compatible;
            code = isCompatible(&compatible, spec);
            TE_CHECKRETURN_CODE(code);
            if(!compatible)
                return TE_InvalidArg;
        }
            
        result =  TileContainerPtr(new(std::nothrow) OSMDroidContainer(info, std::move(db)), Memory_deleter_const<TAK::Engine::Raster::TileMatrix::TileContainer, OSMDroidContainer>);
        if (!result)
            return TE_OutOfMemory;

        return code;
    }
    TAKErr SpiImpl::isCompatible(bool *result, const TileMatrix *spec) const NOTHROWS
    {
        *result = ::isCompatible(*spec);
        return TE_Ok;
    }
}
#endif