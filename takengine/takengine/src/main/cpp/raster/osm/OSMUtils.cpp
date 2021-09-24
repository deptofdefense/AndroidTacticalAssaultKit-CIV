#include "raster/osm/OSMUtils.h"

#include <climits>
#include <cmath>

#include "math/Utils.h"

namespace atakmap {
    namespace raster{
        namespace osm {

            namespace {
                OSMUtils *singleton = nullptr;
            }

            OSMUtils::OSMUtils() : tableName("tiles")
            {
                colNames.insert("key");
                colNames.insert("provider");
                colNames.insert("tile");
            }

            const OSMUtils& OSMUtils::getInstance()
            {
                if (singleton == nullptr)
                    singleton = new OSMUtils();
                return *singleton;
            }

            const std::string& OSMUtils::getOSMDroidSqliteTilesTableName() const
            {
                return tableName;
            }

            const std::set<std::string>& OSMUtils::getOSMDroidSqliteTilesTableColumns() const
            {
                return colNames;
            }

            bool OSMUtils::isOSMDroidSQLite(db::Database &database) const
            {
                std::vector<TAK::Engine::Port::String> columns =
                    db::getColumnNames(database, tableName.c_str());

                if (columns.size() != colNames.size())
                    return false;

                std::vector<TAK::Engine::Port::String>::iterator iter;
                for (iter = columns.begin(); iter != columns.end(); ++iter) {
                    std::string s(*iter);
                    if (colNames.find(s) == colNames.end())
                        return false;
                }

                return true;
            }

            // next 4 derived from
            // http://wiki.openstreetmap.org/wiki/Slippy_map_tilenames#X_and_Y
            double OSMUtils::mapnikTileLat(int level, int ytile)
            {
                int n = 1 << level;
                return math::toDegrees(atan(sinh(M_PI
                    * (1.0 - (2.0 * (double)ytile / (double)n)))));
            }

            double OSMUtils::mapnikTileLng(int level, int xtile)
            {
                int n = 1 << level;
                return ((double)xtile / (double)n) * 360.0 - 180.0;
            }

            int OSMUtils::mapnikTileX(int level, double lng)
            {
                int n = 1 << level;
                return (int)(n * ((lng + 180.0) / 360.0));
            }

            int OSMUtils::mapnikTileY(int level, double lat)
            {
                int n = 1 << level;
                double lat_rad = math::toRadians(lat);
                return (int)(n
                             * (1.0 - (log(tan(lat_rad) + (1.0 / cos(lat_rad))) / M_PI)) / 2.0);
            }

            double OSMUtils::mapnikPixelLat(int level, int ytile, int y)
            {
                return mapnikTileLat(level + 8, (ytile << 8) + y);
            }

            double OSMUtils::mapnikPixelLng(int level, int xtile, int x)
            {
                return mapnikTileLng(level + 8, (xtile << 8) + x);
            }

            int OSMUtils::mapnikPixelY(int level, int ytile, double lat)
            {
                return mapnikTileY(level + 8, lat) - (ytile << 8);
            }

            int OSMUtils::mapnikPixelX(int level, int xtile, double lng)
            {
                return mapnikTileX(level + 8, lng) - (xtile << 8);
            }

            double OSMUtils::mapnikTileResolution(int level)
            {
                return mapnikTileResolution(level, 0.0);
            }

            double OSMUtils::mapnikTileResolution(int level, double lat)
            {
                if (level >= 32)
                    return 0.0;
                return 156543.034 * cos(lat*M_PI/180.0) / (1 << level);
            }

            int OSMUtils::mapnikTileLevel(double resolution)
            {
                return mapnikTileLevel(resolution, 0.0);
            }

            int OSMUtils::mapnikTileLevel(double resolution, double lat)
            {
                if (resolution == 0.0)
                    return INT_MAX;

                // XXX - not sure whether we want ceil or floor here.....
                return (int)mapnikTileLeveld(resolution, lat);
            }

            double OSMUtils::mapnikTileLeveld(double resolution, double lat)
            {
                if (resolution == 0.0)
                    return INT_MAX;

                return (log(156543.034 * cos(lat) / resolution) / M_LN2);
            }


            /**************************************************************************/
            // OSM Droid SQLite
            int64_t OSMUtils::getOSMDroidSQLiteIndex(int64_t level, int64_t tilex, int64_t tiley)
            {
                return (((level << level) + tilex) << level) + tiley;
            }

            int OSMUtils::getOSMDroidSQLiteZoomLevel(int64_t index)
            {
                for (int i = 0; i < 29; i++)
                    if (index <= getOSMDroidSQLiteMaxIndex(i))
                        return i;
                return -1;
            }

            int OSMUtils::getOSMDroidSQLiteTileX(int64_t index)
            {
                int zoomLevel = getOSMDroidSQLiteZoomLevel(index);
                if (zoomLevel < 0)
                    return -1;
                index >>= zoomLevel;
                index -= (zoomLevel << zoomLevel);
                return (int)index;
            }

            int OSMUtils::getOSMDroidSQLiteTileY(int64_t index)
            {
                int zoomLevel = getOSMDroidSQLiteZoomLevel(index);
                if (zoomLevel < 0)
                    return -1;
                return (int)((~(0xFFFFFFFFFFFFFFFFL << (int64_t)zoomLevel)) & index);
            }

            int64_t OSMUtils::getOSMDroidSQLiteMaxIndex(int zoomLevel)
            {
                return getOSMDroidSQLiteMinIndex(zoomLevel + 1) - 1;
            }

            int64_t OSMUtils::getOSMDroidSQLiteMinIndex(int zoomLevel)
            {
                if (zoomLevel < 0 || zoomLevel > 28)
                    return -1;
                return (int64_t)zoomLevel << (int64_t)(zoomLevel * 2);
            }

        }
    }
}

