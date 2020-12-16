#include "raster/mobac/MobacTileClient.h"

#include <cinttypes>

#ifdef WIN32
#include "private/Util.h"
#endif

#include "raster/mobac/MobacMapSource.h"
#include "raster/mobac/MobacMapTile.h"
#include "raster/osm/OSMUtils.h"
#include "renderer/Bitmap.h"
#include "db/Cursor.h"
#include "db/Database.h"
#include "db/Statement.h"
#include "db/SpatiaLiteDB.h"
#include "util/IO.h"
#include "util/Logging.h"

#include "thread/Lock.h"

#include "renderer/BitmapFactory.h"

#ifdef WIN32
#include "renderer/Bitmap_CLI.h"
#endif

#ifdef __APPLE__
#include <sys/time.h> //gettimeofday
#endif

using namespace atakmap::raster::mobac;

using namespace atakmap::db;
using namespace atakmap::raster::osm;
using namespace atakmap::renderer;
using namespace atakmap::util;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

#define TAG "MobacTileClient"
#define ONE_WEEK_MILLIS (7*24*60*60*1000)

namespace 
{
    class MobacMapTileCleaner
    {
    public :
        MobacMapTileCleaner(MobacMapTile *tile);
        ~MobacMapTileCleaner();
    private :
        MobacMapTile *tile;
    };

    class SystemClock
    {
    private :
        SystemClock();
    public :
        static int64_t currentTimeMillis();
    };

    void releaseNativeImpl(atakmap::renderer::Bitmap b) {
#ifdef WIN32
        delete[] b.data;
        gcroot<System::Drawing::Bitmap ^> *managedRef = static_cast<gcroot<System::Drawing::Bitmap ^> *>(b.opaque);
        delete managedRef;
#else
#warning TODO(anyone)-- implement releaseNativeImpl
#endif
    }
}

#if 0
static MobacTileClient::MobacTileClient()
{
#if 0
    CACHE_OPTS.inJustDecodeBounds = true;
#endif
}
#endif

MobacTileClient::MobacTileClient(MobacMapSource *s, const char *offlineCachePath) :
    mapSource(s),
    offlineMode(false),
#if 0
    updateAccessStmt(true),
    updateCatalogStmt(true),
    insertCatalogStmt(true),
    insertTileStmt(true),
    updateTileStmt(true),
    queryTileStmt(true)
#endif
    offlineCache(NULL)
{
    // XXX - if not properly created in SPI, we'll fail
    if (offlineCachePath && pathExists(offlineCachePath))
        this->offlineCache = new SpatiaLiteDB(offlineCachePath);//Database::openDatabase(offlineCachePath);
}

MobacTileClient::~MobacTileClient()
{
    if(this->offlineCache)
        delete this->offlineCache;
}


void MobacTileClient::setOfflineMode(bool offlineOnly)
{
    this->offlineMode = offlineOnly;
}

bool MobacTileClient::isOfflineMode() {
    return this->offlineMode;
}

void MobacTileClient::close() {
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error("MobacTileClient::close: Failed to acquire mutex");
    if (this->offlineCache) {
        delete this->offlineCache;
        this->offlineCache = NULL;
    }

    this->offlineMode = true;
}

bool MobacTileClient::loadTile(Bitmap *retval, int zoom, int x, int y/*, BitmapFactory.Options opts*/, MobacTileClient::DownloadErrorCallback *callback)
{
    bool success = false;
    TileRecord record;
    bool haveCatalogEntry;
    {
        TAKErr code(TE_Ok);
        LockPtr lock(NULL, NULL);
        code = Lock_create(lock, mutex);
        if (code != TE_Ok)
            throw std::runtime_error("MobacTileClient::loadTile: Failed to acquire mutex");
        haveCatalogEntry = this->checkTile(zoom, x, y, &record);
    }

    retval->data = NULL;
    retval->dataLen = 0;
    retval->releaseData = NULL;

    //System::Console::WriteLine("loadTile (" + zoom + "," + x + "," + y + ") exp=" + record.expiration + " | " + SystemClock::currentTimeMillis());

    const int64_t tileIndex = OSMUtils::getOSMDroidSQLiteIndex(zoom, x, y);

    int64_t currentTimeMillis = SystemClock::currentTimeMillis();

    // if data is nullptr or expiration exceeded, load from URL
    if (!this->offlineMode && this->mapSource
            && currentTimeMillis > record.expiration) {
        try {
            MobacMapTile tile;
            bool gotTile = this->mapSource->loadTile(&tile, zoom, x, y/*, opts*/);
            if (gotTile && tile.bitmap.dataLen) {
                *retval = tile.bitmap;
                if (tile.expiration < (currentTimeMillis + ONE_WEEK_MILLIS))
                    record.expiration = (currentTimeMillis + ONE_WEEK_MILLIS);
                else
                    record.expiration = tile.expiration;

                // update cache with downloaded data
                if (this->offlineCache && tile.dataLength) {
                    TAKErr code(TE_Ok);
                    LockPtr lock(NULL, NULL);
                    code = Lock_create(lock, mutex);
                    if (code != TE_Ok)
                        throw std::runtime_error("MobacTileClient::loadTile: Failed to acquire mutex");
                    if (offlineCache)
                        try {
                            this->updateCache(tileIndex, &tile, haveCatalogEntry);
                        }
                        catch (std::exception &e) {
                            Logger::log(Logger::Error, "Exception occurred updating cache, message=%s", e.what());
                        }
                }
            }
            
            if (tile.releaseData) {
                tile.releaseData(tile);
            }
            
        } catch (std::exception &e) {
            Logger::log(Logger::Error, "%s: IO Error during tile download, %s (%d, %d, %d)", TAG, this->mapSource->getName(), zoom, x, y);
            if (callback)
                callback->tileDownloadError(zoom, x, y, e.what());
        }
    }

    if (!retval->dataLen && haveCatalogEntry) {
#if 0
        // XXX - not updating access for performance reasons
        {
            ScopeMonitor lock(this);
            if (offlineCache != nullptr) {
                try {

                    // update the access
                    if (!this->updateAccessStmt.IsValueCreated)
                        this->updateAccessStmt.Value = this->offlineCache->compileStatement("UPDATE ATAK_catalog SET access = ? WHERE key = ?");
                    this->updateAccessStmt.Value->bindLong(1, SystemClock::currentTimeMillis());
                    this->updateAccessStmt.Value->bindLong(2, tileIndex);
                    this->updateAccessStmt.Value->execute();
                    this->updateAccessStmt.Value->clearBindings();
                }
                catch (Exception ^e) {
                    Logger::log(Logger::Error, TAG + ": Tile access update failed, " + this->mapSource->getName() + " (" + zoom + ", " + x + ", " + y + ")");
                }
            }
        }
#endif
        if (record.data)
        {
#ifdef MSVC
            System::IO::Stream ^inputStream = nullptr;
            try {
                array<System::Byte> ^data = gcnew array<System::Byte>(record.dataLength);
                pin_ptr<unsigned char> dataPtr = &data[0];
                memcpy(dataPtr, record.data, record.dataLength);

                inputStream = gcnew System::IO::MemoryStream(data);

                atakmap::cpp_cli::renderer::Bitmap::toNative(gcnew System::Drawing::Bitmap(inputStream), retval);
                retval->releaseData = releaseNativeImpl;
            }
            finally {
                if (inputStream != nullptr)
                    try {
                    inputStream->Close();
                }
                catch (std::exception &) {}
            }
#else
            MemoryInput input;
            input.open(record.data, record.dataLength);
            BitmapFactory::DecodeResult result = BitmapFactory::decode(input, *retval, NULL);
            return result == BitmapFactory::Success;
#endif
        }
    }
    

    // XXX - drop oldest tiles

    // XXX - resize to 256x256. This obviously isn't the most efficient
    // solution, however, it's no worse than doing the flip on the
    // legacy tilesets
    if (retval->dataLen && (retval->width != 256 || retval->height != 256)) {
        Bitmap scaled = retval->getScaledInstance(*retval, 256, 256);
        if (retval->releaseData)
            retval->releaseData(*retval);
        *retval = scaled;
    }

    return !!retval->dataLen;
}

bool MobacTileClient::cacheTile(int zoom, int x, int y, MobacTileClient::DownloadErrorCallback *callback)
{
    const int64_t tileIndex = OSMUtils::getOSMDroidSQLiteIndex(zoom, x, y);
    const int64_t tileIndexBoxed = tileIndex;

    // check for cache entry
    bool haveCacheEntry;
    {
        TAKErr code(TE_Ok);
        LockPtr lock(NULL, NULL);
        code = Lock_create(lock, mutex);
        if (code != TE_Ok)
            throw std::runtime_error("MobacTileClient::cacheTile: Failed to acquire mutex");
        if(this->offlineCache)
            return false;
        if(this->offlineMode)
            return false;
        if(this->mapSource)
            return false;

        // if we are attempting to cache the tile in another thread, return
        if(this->pendingCacheUpdates.find(tileIndexBoxed) != this->pendingCacheUpdates.end())
            return false;

        TileRecord record;
        haveCacheEntry = this->checkTile(zoom, x, y, &record);
        // if the cache entry is valid, return
        if(record.expiration >= SystemClock::currentTimeMillis())
            return true;
        this->pendingCacheUpdates.insert(tileIndexBoxed);
    }

    // download tile
    MobacMapTile tile;
    MobacMapTileCleaner tileCleaner(&tile);
    bool gotTile;
    try {
#if 0
        final BitmapFactory.Options cacheOpts = new BitmapFactory.Options();
        cacheOpts.inJustDecodeBounds = true;
#endif
        gotTile = this->mapSource->loadTile(&tile, zoom, x, y/*, cacheOpts*/);
    } catch(std::exception  &e) {
        {
            TAKErr code(TE_Ok);
            LockPtr lock(NULL, NULL);
            code = Lock_create(lock, mutex);
            if (code != TE_Ok)
                throw std::runtime_error("MobacTileClient::cacheTile: Failed to acquire mutex");
            this->pendingCacheUpdates.erase(tileIndexBoxed);
        }
        if(callback)
            callback->tileDownloadError(zoom, x, y, e.what());
        return false;
    }
        
    // update the cache
    {
        TAKErr code(TE_Ok);
        LockPtr lock(NULL, NULL);
        code = Lock_create(lock, mutex);
        if (code != TE_Ok)
            throw std::runtime_error("MobacTileClient::cacheTile: Failed to acquire mutex");

        if(this->offlineCache)
            return false;
        if(this->mapSource)
            return false;

        // 
        if (gotTile)
            this->updateCache(tileIndex, &tile, haveCacheEntry);
        this->pendingCacheUpdates.erase(tileIndexBoxed);
    }
        
    return true;
}
    
// should always be invoked while holding lock on 'this'
bool MobacTileClient::checkTile(int zoom, int x, int y, TileRecord *record)
{
    const int64_t tileIndex = OSMUtils::getOSMDroidSQLiteIndex(zoom, x, y);

    // query cache for expiration of target tile
    if (this->offlineCache) {
        try {
            char sql[181];
            sprintf(sql, "SELECT ATAK_catalog.expiration, tiles.tile FROM ATAK_catalog LEFT JOIN tiles ON ATAK_catalog.key = tiles.key WHERE ATAK_catalog.key = %" PRId64, tileIndex);
            std::auto_ptr<Cursor> result(offlineCache->query(sql));
               
            // if we have an entry and it's not expired, return
            if (!result->moveToNext())
                return false;
            
            record->expiration = result->getLong(0);
            Cursor::Blob data = result->getBlob(1);
            record->dataLength = (data.second - data.first);
            record->data = new uint8_t[record->dataLength];
            memcpy(record->data, data.first, record->dataLength);

            return true;
        } catch (std::exception &e) {
            Logger::log(Logger::Error, "%s: Offline cache catalog query failed, %s (%d, %d, %d)", TAG, this->mapSource->getName(), zoom, x, y);
        }
    }
    return false;
}

// should always be invoked while holding lock on 'this'
void MobacTileClient::updateCache(int64_t index, MobacMapTile *tile, bool update)
{
    int64_t expiration;
    int64_t currentTimeMillis = SystemClock::currentTimeMillis();
    if (tile->expiration < (currentTimeMillis + ONE_WEEK_MILLIS))
        expiration = (currentTimeMillis + ONE_WEEK_MILLIS);
    else
        expiration = tile->expiration;

    if (update) {
        std::auto_ptr<Statement> updateTileStmt(this->offlineCache->compileStatement("UPDATE tiles SET tile = ? WHERE key = ?"));
        updateTileStmt->clearBindings();
        updateTileStmt->bind(1, Statement::Blob(tile->data, tile->data+tile->dataLength));
        updateTileStmt->bind(2, static_cast<int64_t>(index));
        updateTileStmt->execute();
        updateTileStmt.reset(NULL);

        std::auto_ptr<Statement> updateCatalogStmt(this->offlineCache->compileStatement("UPDATE ATAK_catalog SET access = ?, expiration = ?, size = ? WHERE key = ?"));
        updateCatalogStmt->clearBindings();
        updateCatalogStmt->bind(1, static_cast<int64_t>(currentTimeMillis));
        updateCatalogStmt->bind(2, static_cast<int64_t>(expiration));
        updateCatalogStmt->bind(3, static_cast<int>(tile->dataLength));
        updateCatalogStmt->bind(4, static_cast<int64_t>(index));
        updateCatalogStmt->execute();
        updateCatalogStmt.reset(NULL);
    } else {
        std::auto_ptr<Statement> insertTileStmt(this->offlineCache->compileStatement("INSERT INTO tiles(key, provider, tile) VALUES(?, ?, ?)"));
        insertTileStmt->clearBindings();
        insertTileStmt->bind(1, static_cast<int64_t>(index));
        insertTileStmt->bind(2, this->mapSource->getName());
        insertTileStmt->bind(3, Statement::Blob(tile->data, tile->data+tile->dataLength));
        insertTileStmt->execute();
        insertTileStmt.reset(NULL);

        std::auto_ptr<Statement> insertCatalogStmt(this->offlineCache->compileStatement("INSERT INTO ATAK_catalog(key, access, expiration, size) VALUES(?, ?, ?, ?)"));
        insertCatalogStmt->clearBindings();
        insertCatalogStmt->bind(1, static_cast<int64_t>(index));
        insertCatalogStmt->bind(2, static_cast<int64_t>(currentTimeMillis));
        insertCatalogStmt->bind(3, static_cast<int64_t>(expiration));
        insertCatalogStmt->bind(4, static_cast<int64_t>(tile->dataLength));
        insertCatalogStmt->execute();
        insertCatalogStmt.reset(NULL);
    }
}

MobacTileClient::TileRecord::TileRecord() :
    expiration(-1),
    data(NULL),
    dataLength(0)
{}

MobacTileClient::TileRecord::~TileRecord()
{
    if (data) {
        delete [] data;
        data = NULL;
        dataLength = 0;
    }
}

namespace
{
    MobacMapTileCleaner::MobacMapTileCleaner(MobacMapTile *tile_) :
        tile(tile_)
    {
    }

    MobacMapTileCleaner::~MobacMapTileCleaner()
    {
        if (tile->bitmap.releaseData)
            tile->bitmap.releaseData(tile->bitmap);
        if (tile->releaseData)
            tile->releaseData(*tile);
    }

    SystemClock::SystemClock()
    {}

    int64_t SystemClock::currentTimeMillis()
    {
#ifdef WIN32
        FILETIME now;
        ULARGE_INTEGER now_uli;

        GetSystemTimeAsFileTime(&now);

        now_uli.u.LowPart = now.dwLowDateTime;
        now_uli.u.HighPart = now.dwHighDateTime;

        // Subtract off the number of ticks from Jan 1, 1601 to Jan 1, 1970 so
        // that the value returned will be relative to Jan 1, 1970.
        // We do this conversion for two reasons:
        // (1) our results will be consistent between Windows and Unix.
        // (2) we can't represent seconds since Jan 1, 1601 in a 32-bit integer,
        //     so we need to truncate to something. With (1) in mind, we pick
        //     Jan 1, 1970.
        now_uli.QuadPart -= 116444736000000000LL;

        return (now_uli.QuadPart / 10000);

#else
        struct timeval tval;
        gettimeofday(&tval, NULL);
        return ((int64_t)tval.tv_sec*1000) + (int64_t)tval.tv_usec / 1000;
#endif
    }
}
