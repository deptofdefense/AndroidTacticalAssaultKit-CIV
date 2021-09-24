#ifndef ATAKMAP_RASTER_MOBAC_CUSTOMWMSMOBACMAPSOURCE_H_INCLUDED
#define ATAKMAP_RASTER_MOBAC_CUSTOMWMSMOBACMAPSOURCE_H_INCLUDED

#include <string>

#include "feature/Envelope.h"
#include "raster/mobac/CustomMobacMapSource.h"

namespace atakmap {
    namespace raster {
        namespace mobac {

            class CustomWmsMobacMapSource : public CustomMobacMapSource
            {
            private :
                //static CustomWmsMobacMapSource();
            public :
                CustomWmsMobacMapSource(const char *name, int srid, int tileSize, int minZoom, int maxZoom, const char *type, const char *url, const char *layers, const char *style, const char *version, const char *additionalParameters, int backgroundColor, atakmap::feature::Envelope *bounds);
            public :
                int getSRID();
                virtual bool getBounds(atakmap::feature::Envelope *bnds);
            protected :
                virtual size_t getUrl(char *urlOut, int zoom, int x, int y);
            private :
                //static System::Collections::Generic::IDictionary<const char *, const char *> ^ const TILE_FORMAT_TO_WMS_FORMAT_MIME = gcnew System::Collections::Generic::Dictionary<const char *, const char *>();
            private :
                std::string layers;
                std::string style;
                const int srid;
                const char *formatMime;
                std::string additionalParameters;
                std::string version;
                atakmap::feature::Envelope bounds;
            };
        }
    }
}

#endif // ATAKMAP_CPP_CLI_RASTER_MOBAC_CUSTOMWMSMOBACMAPSOURCE_H_INCLUDED
