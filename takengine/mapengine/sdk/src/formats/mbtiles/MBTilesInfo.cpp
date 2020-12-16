#include "formats/mbtiles/MBTilesInfo.h"

#include <set>

#include "db/Query.h"
#include "renderer/Bitmap2.h"
#include "renderer/BitmapFactory2.h"
#include "port/String.h"
#include "port/Collection.h"
#include "port/STLSetAdapter.h"

using namespace TAK::Engine::Formats::MBTiles;

using namespace TAK::Engine::DB;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Util;

#define COLUMN_TILE_ALPHA "tile_alpha"

namespace
{
    TAKErr parseInt(int *value, const char *str) NOTHROWS
    {
        if (!value)
            return TE_InvalidArg;
        if (!str)
            return TE_InvalidArg;
        // XXX - regex match
        *value = atoi(str);
        return TE_Ok;
    }
}

TAKErr TAK::Engine::Formats::MBTiles::MBTilesInfo_get(MBTilesInfo *info, DB::Database2 &database) NOTHROWS
{
    TAKErr code(TE_Ok);

    // validate the schema
    std::set<TAK::Engine::Port::String, TAK::Engine::Port::StringLess> tilesTable;
    TAK::Engine::Port::STLSetAdapter<TAK::Engine::Port::String, TAK::Engine::Port::StringLess> tilesTableAdapter(tilesTable);
    code = Databases_getColumnNames(tilesTableAdapter, database, "tiles");
    TE_CHECKRETURN_CODE(code);

    std::size_t schemaColumnsCount = 4u;
    if (tilesTable.find("zoom_level") == tilesTable.end())
        return TE_Err;
    if (tilesTable.find("tile_column") == tilesTable.end())
        return TE_Err;
    if (tilesTable.find("tile_row") == tilesTable.end())
        return TE_Err;
    if (tilesTable.find("tile_data") == tilesTable.end())
        return TE_Err;
    if (tilesTable.find("tile_alpha") != tilesTable.end())
        schemaColumnsCount++;

    if (tilesTable.size() != schemaColumnsCount)
        return TE_Err;

    info->hasTileAlpha = (tilesTable.find(COLUMN_TILE_ALPHA) != tilesTable.end());

    // discover tile matrix information
    QueryPtr result(nullptr, nullptr);

    int minLevel = -1;
    int maxLevel = -1;
    result.reset();

    code = database.query(result, "SELECT name, value FROM metadata");
    TE_CHECKRETURN_CODE(code);
    do {
        code = result->moveToNext();
        TE_CHECKBREAK_CODE(code);
        const char *key;
        code = result->getString(&key, 0);
        TE_CHECKBREAK_CODE(code);
        if (strcmp(key, "maxZoomLevel") == 0) {
            const char *value;
            if (result->getString(&value, 1) != TE_Ok)
                continue;
            int n;
            if (parseInt(&n, value) == TE_Ok)
                maxLevel = n;
        } else if (strcmp(key, "minZoomLevel") == 0) {
            const char *value;
            if (result->getString(&value, 1) != TE_Ok)
                continue;
            int n;
            if (parseInt(&n, value) == TE_Ok)
                minLevel = n;
        } else if (strcmp(key, "name") == 0) {
            const char *value;
            code = result->getString(&value, 1);
            TE_CHECKBREAK_CODE(code);
            info->name = value;
        }
    } while (true);
    if (code == TE_Done)
        code = TE_Ok;
    result.reset();

    if (minLevel < 0) {
        result.reset();
        // XXX - switch to min(zoom_level)
        code = database
            .query(result,
            "SELECT zoom_level FROM tiles ORDER BY zoom_level ASC LIMIT 1");
        TE_CHECKRETURN_CODE(code);
        if (result->moveToNext() == TE_Ok) {
            int n;
            if (result->getInt(&n, 0) == TE_Ok)
                minLevel = n;
        }
        result.reset();
    }
    if (maxLevel < 0) {
        result.reset();
        // XXX - switch to min(zoom_level)
        code = database
            .query(result,
            "SELECT zoom_level FROM tiles ORDER BY zoom_level DESC LIMIT 1");
        TE_CHECKRETURN_CODE(code);
        if (result->moveToNext() == TE_Ok) {
            int n;
            if (result->getInt(&n, 0) == TE_Ok)
                maxLevel = n;
        }
        result.reset();
    }
    // no tiles present in database
    if (minLevel < 0 || maxLevel < 0)
        return TE_Err;

    info->minLevel = minLevel;
    info->maxLevel = maxLevel;

    // bounds discovery

    // XXX - NEXT 4 -- use min(...) / max(...)
    // do 4 queries for MBB discovery (min x,y / max x,y)
    result.reset();
    {
        code = database.query(result,
            "SELECT tile_column FROM tiles WHERE zoom_level = ? ORDER BY tile_column ASC LIMIT 1");
        TE_CHECKRETURN_CODE(code);
        code = result->bindInt(1, static_cast<int>(info->minLevel));
        TE_CHECKRETURN_CODE(code);
        code = result->moveToNext();
        TE_CHECKRETURN_CODE(code);
        int n;
        code = result->getInt(&n, 0);
        TE_CHECKRETURN_CODE(code);
        if (n < 0)
            return TE_Err;
        info->minLevelGridMinX = n;
    }
    result.reset();

    result.reset();
    {
        code = database.query(result,
            "SELECT tile_column FROM tiles WHERE zoom_level = ? ORDER BY tile_column DESC LIMIT 1");
        TE_CHECKRETURN_CODE(code);
        code = result->bindInt(1, static_cast<int>(info->minLevel));
        TE_CHECKRETURN_CODE(code);
        code = result->moveToNext();
        TE_CHECKRETURN_CODE(code);
        int n;
        code = result->getInt(&n, 0);
        TE_CHECKRETURN_CODE(code);
        if (n < 0)
            return TE_Err;
        info->minLevelGridMaxX = n;
    }
    result.reset();

    result.reset();
    {
        code = database.query(result,
            "SELECT tile_row FROM tiles WHERE zoom_level = ? ORDER BY tile_row ASC LIMIT 1");
        TE_CHECKRETURN_CODE(code);
        code = result->bindInt(1, static_cast<int>(info->minLevel));
        TE_CHECKRETURN_CODE(code);
        code = result->moveToNext();
        TE_CHECKRETURN_CODE(code);
        int n;
        code = result->getInt(&n, 0);
        TE_CHECKRETURN_CODE(code);
        if (n < 0)
            return TE_Err;
        info->minLevelGridMinY = n;
    }
    result.reset();

    result.reset();
    {
        code = database.query(result,
            "SELECT tile_row FROM tiles WHERE zoom_level = ? ORDER BY tile_row DESC LIMIT 1");
        TE_CHECKRETURN_CODE(code);
        code = result->bindInt(1, static_cast<int>(info->minLevel));
        TE_CHECKRETURN_CODE(code);
        code = result->moveToNext();
        TE_CHECKRETURN_CODE(code);
        int n;
        code = result->getInt(&n, 0);
        TE_CHECKRETURN_CODE(code);
        if (n < 0)
            return TE_Err;
        info->minLevelGridMaxY = n;
    }
    result.reset();

    // obtain and tile dimensions
    info->tileWidth = 256u;
    info->tileHeight = 256u;

    result.reset();
    {
        code = database.query(result, "SELECT tile_data FROM tiles LIMIT 1");
        TE_CHECKRETURN_CODE(code);
        if (result->moveToNext() == TE_Ok) {
            const uint8_t *blobData;
            std::size_t blobLen;
            code = result->getBlob(&blobData, &blobLen, 0);
            TE_CHECKRETURN_CODE(code);

            MemoryInput2 input;
            code = input.open(blobData, blobLen);
            TE_CHECKRETURN_CODE(code);
            BitmapPtr tile(nullptr, nullptr);
            code = BitmapFactory2_decode(tile, input, nullptr);
            TE_CHECKRETURN_CODE(code);

            info->tileWidth = tile->getWidth();
            info->tileHeight = tile->getHeight();
        } else {
            return TE_IllegalState;
        }
    }
    result.reset();

    return code;
}

