#ifndef ATAKMAP_RASTER_MOBAC_ABSTRACTMOBACMAPSOURCE_H_INCLUDED
#define ATAKMAP_RASTER_MOBAC_ABSTRACTMOBACMAPSOURCE_H_INCLUDED

#include "string/String.hh"
#include "raster/mobac/MobacMapSource.h"

namespace atakmap {
    namespace util {
        class HttpClient;
    }

    namespace raster {
        namespace mobac {

            class AbstractMobacMapSource : public MobacMapSource
            {
            protected :
                AbstractMobacMapSource(const char *name, int tileSize, int minZoom, int maxZoom, const char *tileType);
            public :
                virtual ~AbstractMobacMapSource();
                virtual const char *getName();
                virtual int getMinZoom();
                virtual int getMaxZoom();
                virtual const char *getTileType();
                virtual int getTileSize();
                virtual void setConfig(MobacMapSource::Config c);
                virtual bool getBounds(atakmap::feature::Envelope *bnds);
            protected :
                static bool load(MobacMapTile *tile, atakmap::util::HttpClient *conn, const char *uri/*, BitmapFactory.Options opts*/) /*throws IOException*/;
            private :
//                static const int BUFFER_SIZE = 32 * 1024;
            protected :
                PGSC::String name;
                PGSC::String tileType;
                const int tileSize;
                const int minZoom;
                const int maxZoom;
                MobacMapSource::Config config;
            };
        }
    }
}

#endif // ATAKMAP_CPP_CLI_RASTER_MOBAC_ABSTRACTMOBACMAPSOURCE_H_INCLUDED