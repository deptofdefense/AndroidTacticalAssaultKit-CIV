#ifndef ATAKMAP_RASTER_MOBAC_MOBACMAPTILE_H_INCLUDED
#define ATAKMAP_RASTER_MOBAC_MOBACMAPTILE_H_INCLUDED

#include <cstdint>

#include "renderer/Bitmap.h"

namespace atakmap {
    namespace raster {
        namespace mobac {

            struct MobacMapTile
            {
                MobacMapTile();

                atakmap::renderer::Bitmap bitmap;
                uint8_t *data;
                size_t dataLength;
                int64_t expiration;

                void (*releaseData)(MobacMapTile tile);
            };
        }
    }
}

#endif // ATAKMAP_RASTER_MOBAC_MOBACMAPTILE_H_INCLUDED
