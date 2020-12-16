#ifndef TAK_ENGINE_FORMATS_MBTILES_MBTILESINFO_H_INCLUDED
#define TAK_ENGINE_FORMATS_MBTILES_MBTILESINFO_H_INCLUDED

#include <cstddef>

#include "db/Database2.h"
#include "port/Platform.h"
#include "port/String.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Formats {
            namespace MBTiles {
                struct ENGINE_API MBTilesInfo
                {
                    std::size_t minLevel {0};
                    std::size_t maxLevel{0};
                    std::size_t minLevelGridMinX {0};
                    std::size_t minLevelGridMinY {0};
                    std::size_t minLevelGridMaxX {0};
                    std::size_t minLevelGridMaxY {0};
                    Port::String name;
                    std::size_t tileWidth {0};
                    std::size_t tileHeight {0};
                    bool hasTileAlpha {false};
                };

                ENGINE_API Util::TAKErr MBTilesInfo_get(MBTilesInfo *value, DB::Database2 &database) NOTHROWS;
            }
        }
    }
}

#endif
