#ifndef ATAKMAP_RASTER_MOBAC_MOBACMAPSOURCE_H_INCLUDED
#define ATAKMAP_RASTER_MOBAC_MOBACMAPSOURCE_H_INCLUDED

namespace atakmap {
    namespace feature {
        class Envelope;
    }

    namespace raster {
        namespace mobac {
            struct MobacMapTile;

            class MobacMapSource
            {
            public :
                struct Config;
            public:
                virtual ~MobacMapSource();
                virtual const char *getName() = 0;
                virtual int getMinZoom() = 0;
                virtual int getMaxZoom() = 0;
                virtual const char *getTileType() = 0;
                virtual int getTileSize() = 0;
                virtual bool loadTile(MobacMapTile *tile, int zoom, int x, int y/*, BitmapFactory.Options opts */) /*throws IOException*/ = 0;
                virtual void checkConnectivity() = 0;
                virtual void setConfig(MobacMapSource::Config c) = 0;
                virtual void clearAuthFailed() = 0;
                virtual bool getBounds(atakmap::feature::Envelope *bnds) = 0;
            };

            /**
            * use this class to specify parameters for the MobacMapSource that is to be created
            */
            struct MobacMapSource::Config
            {
                Config();

                long dnsLookupTimeout;
            };

        }
    }
}

#endif // ATAKMAP_RASTER_MOBAC_MOBACMAPSOURCE_H_INCLUDED
