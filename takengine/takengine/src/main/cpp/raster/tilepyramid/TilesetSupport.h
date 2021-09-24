
#ifndef ATAKMAP_RASTER_TILEPYRAMID_TILESETSUPPORT_H_INCLUDED
#define ATAKMAP_RASTER_TILEPYRAMID_TILESETSUPPORT_H_INCLUDED

#include <map>

#include "string/String.hh"
#include "thread/Mutex.h"

#include "util/FutureTask.h"

#include "renderer/Bitmap.h"


namespace atakmap {
    namespace renderer {
        class AsyncBitmapLoader;
    }
    
    namespace raster {
        namespace tilepyramid {
            class TilesetInfo;
            
            struct TileBounds {
                
                inline TileBounds() {
                    vals[0] = 0.0;
                    vals[1] = 0.0;
                    vals[2] = 0.0;
                    vals[3] = 0.0;
                }
                
                inline TileBounds(double s, double w, double n, double e) {
                    vals[0] = s;
                    vals[1] = w;
                    vals[2] = n;
                    vals[3] = e;
                }
                
                inline double operator[](int index) const { return vals[index]; }
                inline double &operator[](int index) { return vals[index]; }
                
            private:
                double vals[4];
            };
            
            class TilesetSupport
            {
            public:
                class Spi;
            
                virtual ~TilesetSupport();
                
            protected:
                TilesetSupport(atakmap::renderer::AsyncBitmapLoader *bitmapLoader);
            
            public:
                virtual void start() = 0;
                virtual void stop() = 0;
                virtual TileBounds getTileBounds(int latIndex, int lngIndex, int level) = 0;
                virtual int getTileZeroX(double lng, int gridX, int gridWidth) = 0;
                virtual int getTileZeroY(double lat, int gridY, int gridHeight) = 0;
                virtual double getTilePixelX(int latIndex, int lngIndex, int level, double lng) = 0;
                virtual double getTilePixelY(int latIndex, int lngIndex, int level, double lat) = 0;
                virtual double getTilePixelLat(int latIndex, int lngIndex, int level, int y) = 0;
                virtual double getTilePixelLng(int latIndex, int lngIndex, int level, int x) = 0;
                virtual atakmap::util::FutureTask<atakmap::renderer::Bitmap> getTile(int latIndex, int lngIndex, int level/*, BitmapFactory.Options opts*/) = 0;
                virtual void init() = 0;
                virtual void release() = 0;
            public:
                virtual int getTilesVersion(int latIndex, int lngIndex, int level);
            public:
                static void registerSpi(TilesetSupport::Spi *spi);
                static void unregisterSpi(TilesetSupport::Spi *spi);
                static TilesetSupport *create(TilesetInfo *info, atakmap::renderer::AsyncBitmapLoader *loader);
                
            private:
                static TilesetSupport *createImpl(TilesetInfo *info, atakmap::renderer::AsyncBitmapLoader *loader);
                static TAK::Engine::Thread::Mutex mutex;
                typedef std::map<PGSC::String, TilesetSupport::Spi *> SpiMap;
                static SpiMap spis;
                
            protected:
                atakmap::renderer::AsyncBitmapLoader *const bitmapLoader;
            };
            
            class TilesetSupport::Spi
            {
            public:
                virtual const char *getName() const = 0;
                virtual TilesetSupport *create(TilesetInfo *tsInfo, atakmap::renderer::AsyncBitmapLoader *bitmapLoader) = 0;
            };
        }
    }
}

#endif