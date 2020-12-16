#ifndef ATAKMAP_RASTER_MOBAC_CUSTOMMOBACMAPSOURCE_H_INCLUDED
#define ATAKMAP_RASTER_MOBAC_CUSTOMMOBACMAPSOURCE_H_INCLUDED

#include "raster/mobac/AbstractMobacMapSource.h"

#include "thread/Mutex.h"

namespace atakmap {
    namespace raster {
        namespace mobac {

            class CustomMobacMapSource : public AbstractMobacMapSource
            {
            public :
                CustomMobacMapSource(const char *name, int tileSize, int minZoom, int maxZoom, const char *type, const char *url, const char **serverParts, size_t numServerParts, int backgroundColor, bool invertYCoordinate);
                virtual ~CustomMobacMapSource();
            protected :
                virtual size_t getUrl(char *urlOut, int zoom, int x, int y);
            private :
                /** 'key' must be sufficient to string coded value, plus NULL terminator; coded length returned. */
                static size_t getQuadKey(char *key, int zoom, int x, int y);
            protected :
                void configureConnection(atakmap::util::HttpClient *conn);
            public :
                virtual void clearAuthFailed();
                virtual void checkConnectivity();
                virtual bool loadTile(MobacMapTile *tile, int zoom, int x, int y/*, Options opts*/) /*throws IOException*/;
            private :
                //AsynchronousInetAddressResolver ^ dnsCheck;
            protected :
                const char *url;
            private :
                const char **serverParts;
                size_t numServerParts;
                const bool invertYCoordinate;
                int serverPartIdx;
                bool needToCheckConnectivity;
                bool disconnected;
                bool authFailed;

                bool firstDnsLookup;

                TAK::Engine::Thread::Mutex mutex;
            };
        }
    }
}

#endif // ATAKMAP_RASTER_MOBAC_CUSTOMMOBACMAPSOURCE_H_INCLUDED
