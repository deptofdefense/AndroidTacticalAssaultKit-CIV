#ifndef ATAKMAP_RASTER_TILEPYRAMID_EQUIRECTANGULARTILESETSUPPORT_H_INCLUDED
#define ATAKMAP_RASTER_TILEPYRAMID_EQUIRECTANGULARTILESETSUPPORT_H_INCLUDED

#include "raster/tilepyramid/TilesetSupport.h"

namespace atakmap {
        namespace raster {
            namespace tilepyramid {
                
            class EquirectangularTilesetSupport : public TilesetSupport
            {
            protected:
                EquirectangularTilesetSupport(atakmap::raster::tilepyramid::TilesetInfo *tsInfo, atakmap::renderer::AsyncBitmapLoader *bitmapLoader);
                virtual ~EquirectangularTilesetSupport();
            public:
                virtual TileBounds getTileBounds(int latIndex, int lngIndex, int level);
                virtual int getTileZeroX(double lng, int gridX, int gridWidth);
                virtual int getTileZeroY(double lat, int gridY, int gridHeight);
                virtual double getTilePixelX(int latIndex, int lngIndex, int level, double lng);
                virtual double getTilePixelY(int latIndex, int lngIndex, int level, double lat);
                virtual double getTilePixelLat(int latIndex, int lngIndex, int level, int y);
                virtual double getTilePixelLng(int latIndex, int lngIndex, int level, int x);
            protected:
                const int levelOffset;
                const double zeroWidth;
                const double zeroHeight;
                const double gridOriginLat;
                const double gridOriginLng;
                const int tilePixelHeight;
                const int tilePixelWidth;
            };
        }
    }
}

#endif // ATAKMAP_CPP_CLI_RASTER_TILEPYRAMID_EQUIRECTANGULARTILESETSUPPORT_H_INCLUDED