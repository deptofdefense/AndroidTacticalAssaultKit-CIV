#ifndef TAK_ENGINE_FORMATS_OSMDROID_OSMDROIDINFO_H_INCLUDED
#define TAK_ENGINE_FORMATS_OSMDROID_OSMDROIDINFO_H_INCLUDED

#include "db/Database2.h"
#include "port/Platform.h"
#include "port/String.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Formats {
            namespace OSMDroid {
                enum BoundsDiscovery
                {
                    /**
                     * Full bounds discovery will be performed. This will require a full
                     * table scan. This is the slowest option.
                     */
                    TEBD_Full,
                    /**
                     * Bounds are computed based on the minimum and maximum keys for the
                     * minimum level. This will be accurate for datasets with a single,
                     * contiguous extent that is rectangular in shape. The grid min/max Y
                     * will not be accurate in the event that the contained tiles are non-
                     * contiguous or form a non-rectangular extent.
                     */
                    TEBD_Quick,
                    /**
                     * Bounds computation is skipped altogether. Grid extents are populated
                     * with the maximum possible values.
                     */
                    TEBD_Skip,
                };

                struct ENGINE_API OSMDroidInfo
                {
                    int srid{ -1 };
                    int minLevel{ -1 };
                    int maxLevel{ -1 };
                    int minLevelGridMinX{ -1 };
                    int minLevelGridMinY{ -1 };
                    int minLevelGridMaxX{ -1 };
                    int minLevelGridMaxY{ -1 };
                    Port::String provider;
                    std::size_t tileWidth{ 0u };
                    std::size_t tileHeight{ 0u };
                    int gridZeroWidth{ -1 };
                    int gridZeroHeight{ -1 };
                };

                /**
                 * @param value     The parsed `OSMDroidInfo`
                 * @param dbref     If non-`null`, returns the DB associated with the dataset (if successful)
                 * @param path      The path to the dataset
                 * @param bounds    The bounds discovery method
                 */
                ENGINE_API Util::TAKErr OSMDroidInfo_get(OSMDroidInfo *value, DB::DatabasePtr *dbref, const char* path, const BoundsDiscovery bounds) NOTHROWS;
                ENGINE_API Util::TAKErr OSMDroidInfo_get(OSMDroidInfo* value, DB::Database2& database, const BoundsDiscovery bounds) NOTHROWS;
            }
        }
    }
}
#endif
