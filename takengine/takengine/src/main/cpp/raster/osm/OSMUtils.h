#ifndef ATAKMAP_RASTER_OSM_OSMUTILS_H_INCLUDED
#define ATAKMAP_RASTER_OSM_OSMUTILS_H_INCLUDED

#include <string>
#include <set>
#include "db/Database.h"
#include "util/NonCopyable.h"
#include "port/Platform.h"
#include "util/NonCopyable.h"

namespace atakmap {
    namespace raster{
        namespace osm {

            class ENGINE_API OSMUtils : TAK::Engine::Util::NonCopyable {

            public:
                static const OSMUtils& getInstance();
                const std::string& getOSMDroidSqliteTilesTableName() const;
                const std::set<std::string>& getOSMDroidSqliteTilesTableColumns() const;
                bool isOSMDroidSQLite(db::Database &database) const;

                // next 4 derived from
                // http://wiki.openstreetmap.org/wiki/Slippy_map_tilenames#X_and_Y
                static double mapnikTileLat(int level, int ytile);
                static double mapnikTileLng(int level, int xtile);
                static int mapnikTileX(int level, double lng);
                static int mapnikTileY(int level, double lat);
                static double mapnikPixelLat(int level, int ytile, int y);
                static double mapnikPixelLng(int level, int xtile, int x);
                static int mapnikPixelY(int level, int ytile, double lat);
                static int mapnikPixelX(int level, int xtile, double lng);
                static double mapnikTileResolution(int level);
                static double mapnikTileResolution(int level, double lat);
                static int mapnikTileLevel(double resolution);
                static int mapnikTileLevel(double resolution, double lat);
                static double mapnikTileLeveld(double resolution, double lat);


                /**************************************************************************/
                // OSM Droid SQLite
                static int64_t getOSMDroidSQLiteIndex(int64_t level, int64_t tilex, int64_t tiley);
                static int getOSMDroidSQLiteZoomLevel(int64_t index);
                static int getOSMDroidSQLiteTileX(int64_t index);
                static int getOSMDroidSQLiteTileY(int64_t index);
                static int64_t getOSMDroidSQLiteMaxIndex(int zoomLevel);
                static int64_t getOSMDroidSQLiteMinIndex(int zoomLevel);

            private:
                OSMUtils();
                std::set<std::string> colNames;
                std::string tableName;
            };
        }
    }
}


#endif
