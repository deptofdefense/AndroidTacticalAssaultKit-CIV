#ifndef ATAKMAP_RASTER_MOBAC_MOBACMAPSOURCETILESETSUPPORT_H_INCLUDED
#define ATAKMAP_RASTER_MOBAC_MOBACMAPSOURCETILESETSUPPORT_H_INCLUDED

#include "raster/tilepyramid/OnlineTilesetSupport.h"
#include "raster/tilepyramid/TilesetInfo.h"
#include "raster/tilepyramid/TilesetSupport.h"

namespace atakmap {
    namespace raster {
        namespace mobac {
            class MobacMapSource;
            class MobacTileClient;
            
            class MobacMapSourceTilesetSupport : public atakmap::raster::tilepyramid::OnlineTilesetSupport
            {
            public:
                static atakmap::raster::tilepyramid::TilesetSupport::Spi *const SPI;
                
            public:
                
                MobacMapSourceTilesetSupport(atakmap::raster::tilepyramid::TilesetInfo *tsInfo, MobacMapSource *mapSource);
                virtual ~MobacMapSourceTilesetSupport();
            public : // Online Tileset Support
                virtual void setOfflineMode(bool offlineOnly);
                virtual bool isOfflineMode();
            public : // Tileset Support
                void init();
                virtual void release();
                virtual void start();
                virtual void stop();
                virtual atakmap::util::FutureTask<renderer::Bitmap> getTile(int latIndex, int lngIndex, int level/*, BitmapFactory.Options opts*/);
            private :
                static atakmap::raster::tilepyramid::TilesetSupport::Spi *createSpi();
            
            protected:
                std::unique_ptr<MobacMapSource> mapSource;
                std::unique_ptr<atakmap::raster::tilepyramid::TilesetInfo> tsInfo;
                long defaultExpiration;
                bool checkConnectivity;
                std::unique_ptr<MobacTileClient> client;
            };
        }
    }
}

#endif