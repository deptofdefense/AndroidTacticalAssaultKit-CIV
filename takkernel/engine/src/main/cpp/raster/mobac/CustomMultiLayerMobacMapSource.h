#ifndef ATAKMAP_RASTER_MOBAC_CUSTOMMULTILAYERMOBACMAPSOURCE_H_INCLUDED
#define ATAKMAP_RASTER_MOBAC_CUSTOMMULTILAYERMOBACMAPSOURCE_H_INCLUDED

#include <vector>

#include "raster/mobac/MobacMapSource.h"

namespace atakmap {
    namespace raster {
        namespace mobac {

            class CustomMultiLayerMobacMapSource : public MobacMapSource
            {
            public :
                CustomMultiLayerMobacMapSource(const char *name, std::vector<std::pair<MobacMapSource *, float>> &layers, int backgroundColor);
                ~CustomMultiLayerMobacMapSource();
            public :
                virtual void clearAuthFailed();
                virtual const char *getName();
                virtual int getTileSize();
                virtual int getMinZoom();
                virtual int getMaxZoom();
                virtual const char *getTileType();
                virtual bool getBounds(atakmap::feature::Envelope *bnds);
                virtual void checkConnectivity();
                virtual bool loadTile(MobacMapTile *tile, int zoom, int x, int y/*, BitmapFactory.Options opts*/) /*throws IOException*/;
                virtual void setConfig(MobacMapSource::Config c);
            private :
                const char *name;
                std::vector<std::pair<MobacMapSource *, float>> layers;
                const int minZoom;
                const int maxZoom;
                const int backgroundColor;
                MobacMapSource::Config config;
            };
        }
    }
}

#endif // ATAKMAP_RASTER_MOBAC_CUSTOMMULTILAYERMOBACMAPSOURCE_H_INCLUDED
