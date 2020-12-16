#ifndef ATAKMAP_RASTER_TILEPYRAMID_SIMPLEURITILESETSUPPORT_H_INCLUDED
#define ATAKMAP_RASTER_TILEPYRAMID_SIMPLEURITILESETSUPPORT_H_INCLUDED

#include "raster/tilepyramid/TilesetSupport.h"

namespace atakmap {
        namespace raster {
            namespace tilepyramid {
                class SimpleUriTilesetSupport
                {
                public:
                    SimpleUriTilesetSupport();
                    virtual ~SimpleUriTilesetSupport();
                private:
                    static TilesetSupport::Spi *createSpi();
                public:
                    static TilesetSupport::Spi *const SPI;
                };
            }
        }
}

#endif // ATAKMAP_RASTER_TILEPYRAMID_SIMPLEURITILESETSUPPORT_H_INCLUDED
