#ifdef MSVC
#include "formats/osmdroid/OSMDroidInfo.h"

#include <vector>

#include "db/DatabaseFactory.h"
#include "db/Query.h"
#include "port/STLVectorAdapter.h"
#include "raster/osm/OSMUtils.h"
#include "renderer/BitmapFactory2.h"

using namespace TAK::Engine::Formats::OSMDroid;

using namespace TAK::Engine::DB;
using namespace TAK::Engine::Util;

TAKErr TAK::Engine::Formats::OSMDroid::OSMDroidInfo_get(OSMDroidInfo *value, DatabasePtr *dbref, const char* path, const BoundsDiscovery bounds) NOTHROWS
{
    TAKErr code(TE_Ok);

    DatabasePtr database(nullptr, nullptr);
    code = DatabaseFactory_create(database, DatabaseInformation(path, nullptr, DATABASE_OPTIONS_READONLY));
    TE_CHECKRETURN_CODE(code);

    code = OSMDroidInfo_get(value, *database, bounds);
    TE_CHECKRETURN_CODE(code);

    if(dbref)
        (*dbref) = std::move(database);
    
    return code;
}

TAKErr TAK::Engine::Formats::OSMDroid::OSMDroidInfo_get(OSMDroidInfo* value, Database2& database, const BoundsDiscovery bounds) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (!atakmap::raster::osm::OSMUtils::isOSMDroidSQLite(database))
        return TE_InvalidArg;
            
    // OSM Droid SQLite
    QueryPtr result(nullptr, nullptr);

    int64_t minKey;
            
    // min key discovery
    result.reset();
    code = database.query(result, "SELECT min(key) FROM tiles");
    do {
        code = result->moveToNext();
        TE_CHECKBREAK_CODE(code);
        bool isNull;
        code = result->isNull(&isNull, 0);
        TE_CHECKBREAK_CODE(code);
        if (isNull)
            return TE_InvalidArg;
        
        code = result->getLong(&minKey, 0);
        TE_CHECKBREAK_CODE(code);

        value->minLevel = atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteZoomLevel(minKey);
    } while (false);
    TE_CHECKRETURN_CODE(code);

    // max key discovery
    result.reset();
    code = database.query(result, "SELECT max(key) FROM tiles");
    do {
        code = result->moveToNext();
        TE_CHECKBREAK_CODE(code);
        bool isNull;
        code = result->isNull(&isNull, 0);
        TE_CHECKBREAK_CODE(code);
        if (isNull)
            return TE_InvalidArg;
        
        int64_t maxKey;
        code = result->getLong(&maxKey, 0);
        TE_CHECKBREAK_CODE(code);

        value->maxLevel = atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteZoomLevel(maxKey);
    } while (false);
    TE_CHECKRETURN_CODE(code);

    // obtain the SRID
    value->srid = 3857;
    value->gridZeroWidth = 1;
    value->gridZeroHeight = 1;

    std::vector<TAK::Engine::Port::String> atakMetadataColumnNames;
    TAK::Engine::Port::STLVectorAdapter<TAK::Engine::Port::String> atakMetadataColumnNames_a(atakMetadataColumnNames);
    if (Databases_getColumnNames(atakMetadataColumnNames_a, database, "ATAK_metadata") == TE_Ok && !atakMetadataColumnNames.empty()) {
        result.reset();
        do {
            if (database.query(result, "SELECT value FROM ATAK_metadata WHERE key = \'srid\'") != TE_Ok)
                break;
            if (result->moveToNext() != TE_Ok)
                break;
            const char *srid;
            if (result->getString(&srid, 0) != TE_Ok)
                break;
            if (TAK::Engine::Port::String_parseInteger(&value->srid, srid) != TE_Ok)
                break;
            if (value->srid == 4326) {
                value->gridZeroWidth = 2;
                value->gridZeroHeight = 1;
            }
        } while (false);
    }
            
    // bounds discovery
    switch(bounds) {
        case TEBD_Full :
        {
            // find the min/max grid x -- this can be done with 2 single
            // queries. assign initial min/max grid y.
            result.reset();
            {
                code = database.compileQuery(result, "SELECT min(key) FROM tiles WHERE key <= ?");
                TE_CHECKRETURN_CODE(code);
                code = result->bindLong(1, atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteMaxIndex(value->minLevel));
                TE_CHECKRETURN_CODE(code);

                code = result->moveToNext();
                TE_CHECKRETURN_CODE(code);
                int64_t tileKey;
                code = result->getLong(&tileKey, 0);
                value->minLevelGridMinX = atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteTileX(tileKey);

                const int y = atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteTileY(tileKey);
                value->minLevelGridMinY = y;
                value->minLevelGridMaxY = y;
            }
                    
            result.reset();
            {
                code = database.compileQuery(result, "SELECT max(key) FROM tiles WHERE key <= ?");
                TE_CHECKRETURN_CODE(code);
                code = result->bindLong(1, atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteMaxIndex(value->minLevel));
                TE_CHECKRETURN_CODE(code);

                code = result->moveToNext();
                TE_CHECKRETURN_CODE(code);
                int64_t tileKey;
                code = result->getLong(&tileKey, 0);
                TE_CHECKRETURN_CODE(code);
                value->minLevelGridMaxX = atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteTileX(tileKey);

                const int y = atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteTileY(tileKey);
                if (y < value->minLevelGridMinY)
                    value->minLevelGridMinY = y;
                if (y > value->minLevelGridMaxY)
                    value->minLevelGridMaxY = y;
            }
                    
            // this method is quicker than brute force, but is not
            // logarithmic time. iterate between the min/max x values
            // doing min/max y discovery on each grid column, shrinking
            // the search based on the current min/max y values
            for(int x = value->minLevelGridMinX; x <= value->minLevelGridMaxX; x++) {
                result.reset();
                do {
                    code = database.compileQuery(result, "SELECT min(key) FROM tiles WHERE key >= ? AND key < ?");
                    TE_CHECKRETURN_CODE(code);
                    result->bindLong(1, atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteIndex(value->minLevel, x, 0));
                    result->bindLong(2, atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteIndex(value->minLevel, x, value->minLevelGridMinY));
                            
                    if (result->moveToNext() != TE_Ok)
                        break;
                    bool isNull = true;
                    if (result->isNull(&isNull, 0) != TE_Ok || isNull)
                        break;

                    int64_t tileKey;
                    if (result->getLong(&tileKey, 0) != TE_Ok)
                        break;
                    const int y = atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteTileY(tileKey);
                    if(y < value->minLevelGridMinY)
                        value->minLevelGridMinY = y;
                } while (false);
                        
                result.reset();
                do {
                    code = database.compileQuery(result, "SELECT max(key) FROM tiles WHERE key > ? AND key <= ?");
                    TE_CHECKBREAK_CODE(code);
                    result->bindLong(1, atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteIndex(value->minLevel, x, value->minLevelGridMaxY));
                    result->bindLong(2, atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteIndex(value->minLevel, x, (1<<value->minLevel)-1));

                    if (result->moveToNext() != TE_Ok)
                        break;
                    bool isNull = true;
                    if (result->isNull(&isNull, 0) != TE_Ok || isNull)
                        break;

                    int64_t tileKey;
                    if (result->getLong(&tileKey, 0) != TE_Ok)
                        break;
                    const int y = atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteTileY(tileKey);
                    if(y > value->minLevelGridMaxY)
                        value->minLevelGridMaxY = y;
                } while (false);
            }
            break;
        }
        case TEBD_Quick :
        {
            result.reset();
            {
                const int mask = ~(0xFFFFFFFF<<value->minLevel);
                code = database.compileQuery(result, "SELECT min(key) FROM tiles WHERE key <= ?");
                TE_CHECKRETURN_CODE(code);
                code = result->bindLong(1, atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteMaxIndex(value->minLevel));
                TE_CHECKRETURN_CODE(code);
                        
                code = result->moveToNext();
                TE_CHECKRETURN_CODE(code);
                
                int64_t tileKey;
                code = result->getLong(&tileKey, 0);
                TE_CHECKRETURN_CODE(code);

                value->minLevelGridMinX = atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteTileX(tileKey);
                value->minLevelGridMinY = atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteTileY(tileKey);
            }
                    
            result.reset();
            {
                const int mask = ~(0xFFFFFFFF<<value->minLevel);
                code = database.compileQuery(result, "SELECT max(key) FROM tiles WHERE key <= ?");
                TE_CHECKRETURN_CODE(code);
                code = result->bindLong(1, atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteMaxIndex(value->minLevel));
                TE_CHECKRETURN_CODE(code);
                        
                code = result->moveToNext();
                TE_CHECKRETURN_CODE(code);

                int64_t tileKey;
                code = result->getLong(&tileKey, 0);
                TE_CHECKRETURN_CODE(code);

                value->minLevelGridMaxX = atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteTileX(tileKey);
                value->minLevelGridMaxY = atakmap::raster::osm::OSMUtils::getOSMDroidSQLiteTileY(tileKey);
            }
            break;
        }
        case TEBD_Skip :
            value->minLevelGridMinX = 0;
            value->minLevelGridMinY = 0;
            value->minLevelGridMaxX = (value->gridZeroWidth << value->minLevel);
            value->minLevelGridMaxY = (value->gridZeroHeight << value->minLevel);
            break;
        default :
            return TE_InvalidArg;
    }

    // obtain provider and tile dimensions
    value->provider = nullptr;
    value->tileWidth = 256;
    value->tileHeight = 256;
            
    result.reset();
    {
        code = database.query(result, "SELECT provider, tile FROM tiles LIMIT 1");
        TE_CHECKRETURN_CODE(code);
        if (result->moveToNext() == TE_Ok) {
            const char* provider;
            code = result->getString(&provider, 0);
            TE_CHECKRETURN_CODE(code);
            value->provider = provider;
                    

            const uint8_t *blob;
            std::size_t blobLen;
            code = result->getBlob(&blob, &blobLen, 1);
            TE_CHECKRETURN_CODE(code);

            TAK::Engine::Renderer::BitmapPtr tile(nullptr, nullptr);
            code = TAK::Engine::Renderer::BitmapFactory2_decode(tile, blob, blobLen, nullptr);
            TE_CHECKRETURN_CODE(code);

            value->tileWidth = tile->getWidth();
            value->tileHeight = tile->getHeight();
        } else {
            return TE_IllegalState;
        }
    }
            
    return code;
}
#endif