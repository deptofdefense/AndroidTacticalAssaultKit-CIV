#ifndef ATAKMAP_RASTER_MOBAC_EQUIRECTANGULARMOBACTILESETSUPPORT_H_INCLUDED
#define ATAKMAP_RASTER_MOBAC_EQUIRECTANGULARMOBACTILESETSUPPORT_H_INCLUDED

#include "raster/tilepyramid/EquirectangularTilesetSupport.h"
#include "raster/tilepyramid/OnlineTilesetSupport.h"
#include "renderer/AsyncBitmapLoader.h"

namespace atakmap {
    namespace raster {
        namespace mobac {
            
            class MobacMapSourceTilesetSupport;
            
            class EquirectangularMobacMapSourceTilesetSupport : public atakmap::raster::tilepyramid::EquirectangularTilesetSupport,
                                                                       atakmap::raster::tilepyramid::OnlineTilesetSupport
            {
            public:
                EquirectangularMobacMapSourceTilesetSupport(atakmap::raster::tilepyramid::TilesetInfo *tsInfo, atakmap::renderer::AsyncBitmapLoader *bitmapLoader, MobacMapSourceTilesetSupport *impl);
                
                ~EquirectangularMobacMapSourceTilesetSupport();
                
            public: // Online Tileset Support
                virtual void setOfflineMode(bool offlineOnly);
                virtual bool isOfflineMode();
                public : // Tileset Support
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

#endif // ATAKMAP_RASTER_MOBAC_EQUIRECTANGULARMOBACTILESETSUPPORT_H_INCLUDED