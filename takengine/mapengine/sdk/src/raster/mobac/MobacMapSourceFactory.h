#ifndef ATAKMAP_RASTER_MOBAC_MOBACMAPSOURCEFACTORY_H_INCLUDED
#define ATAKMAP_RASTER_MOBAC_MOBACMAPSOURCEFACTORY_H_INCLUDED

#include "raster/mobac/MobacMapSource.h"

namespace atakmap {
    namespace raster {
        namespace mobac {
            
            class MobacMapSourceFactory
            {
            private:
                MobacMapSourceFactory();
            public:
                static MobacMapSource *create(const char *path) /*throws IOException*/;
                static MobacMapSource *create(const char *path, const MobacMapSource::Config &config) /*throws IOException*/;
            };
        }
    }
}

#endif