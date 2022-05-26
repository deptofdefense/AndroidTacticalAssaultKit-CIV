#ifndef ATAKMAP_RASTER_OSM_OSMTILESETSUPPORT_H_INCLUDED
#define ATAKMAP_RASTER_OSM_OSMTILESETSUPPORT_H_INCLUDED

#include "raster/tilepyramid/TilesetSupport.h"

namespace atakmap {
    namespace raster {
        namespace osm {
            
            class OSMTilesetSupport : public atakmap::raster::tilepyramid::TilesetSupport
            {
            public:
                OSMTilesetSupport(atakmap::raster::tilepyramid::TilesetInfo *tsInfo, atakmap::renderer::AsyncBitmapLoader *loader);
                virtual ~OSMTilesetSupport();
            public: // TilesetSupport
                virtual tilepyramid::TileBounds getTileBounds(int latIndex, int lngIndex, int level);
                virtual int getTileZeroX(double lng, int gridX, int gridWidth);
                virtual int getTileZeroY(double lat, int gridY, int gridHeight);
                virtual double getTilePixelX(int latIndex, int lngIndex, int level, double lng);
                virtual double getTilePixelY(int latIndex, int lngIndex, int level, double lat);
                virtual double getTilePixelLat(int latIndex, int lngIndex, int level, int y);
                virtual double getTilePixelLng(int latIndex, int lngIndex, int level, int x);
            protected:
                const int levelOffset;
            };
        }
    }
}

#endif // ATAKMAP_CPP_RASTER_OSM_OSMTILESETSUPPORT_H_INCLUDED
