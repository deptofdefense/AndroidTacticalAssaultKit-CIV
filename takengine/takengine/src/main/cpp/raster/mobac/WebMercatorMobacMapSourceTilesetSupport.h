#ifndef ATAKMAP_RASTER_MOBAC_WEBMERCATORTILESETSUPPORT_H_INCLUDED
#define ATAKMAP_RASTER_MOBAC_WEBMERCATORTILESETSUPPORT_H_INCLUDED

#include "raster/osm/OSMTilesetSupport.h"
#include "raster/tilepyramid/OnlineTilesetSupport.h"

namespace atakmap {
    namespace raster {
        namespace mobac {
            class MobacMapSourceTilesetSupport;
            
            class WebMercatorMobacMapSourceTilesetSupport : public atakmap::raster::osm::OSMTilesetSupport,
                                                                   atakmap::raster::tilepyramid::OnlineTilesetSupport
            {
            public:
                WebMercatorMobacMapSourceTilesetSupport(atakmap::raster::tilepyramid::TilesetInfo *tsInfo, atakmap::renderer::AsyncBitmapLoader *bitmapLoader, MobacMapSourceTilesetSupport *impl);
                
                ~WebMercatorMobacMapSourceTilesetSupport();
            public: // Online Tileset Support
                virtual void setOfflineMode(bool offlineOnly);
                virtual bool isOfflineMode();
            public: // Tileset Support
                virtual void init();
                virtual void release();
                virtual void start();
                virtual void stop();
                virtual atakmap::util::FutureTask<renderer::Bitmap> getTile(int latIndex, int lngIndex, int level/*, Options opts*/);
            private:
                std::unique_ptr<MobacMapSourceTilesetSupport> impl;
            };
        }
    }
}

#endif //ATAKMAP_RASTER_MOBAC_WEBMERCATORTILESETSUPPORT_H_INCLUDED
