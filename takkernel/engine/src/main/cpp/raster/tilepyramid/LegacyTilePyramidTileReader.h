
#ifndef ATAKMAP_RASTER_TILEPYRAMID_LEGACYTILEPYRAMIDTILEREADER_H_INCLUDED
#define ATAKMAP_RASTER_TILEPYRAMID_LEGACYTILEPYRAMIDTILEREADER_H_INCLUDED

#include <cmath>
#include <vector>

#include "base/RefCount.hh"

#include "raster/tilereader/TileReaderFactory.h"
#include "raster/tilepyramid/TilesetSupport.h"

#include "renderer/Bitmap.h"

namespace atakmap {
    
    namespace renderer {
        class AsyncBitmapLoader;
    }
    
    namespace raster {
        namespace tilepyramid {
            
            class TilesetInfo;
            
            class LegacyTilePyramidTileReader : public tilereader::TileReader {
            public:
                class Spi : public tilereader::TileReaderSpi {
                public:
                    virtual ~Spi() { }
                    
                    virtual const char *getName() const {
                        return "tileset";
                    }
                    
                    virtual TileReader *create(const char *uri, const tilereader::TileReaderFactory::Options *options);
                };
                
                
            private:
                const static bool DEBUG_DRAW = false;
                
                static TAK::Engine::Thread::Mutex staticMutex;
                
                //const static String TAG = "LegacyTilePyramidTileReader";
                
                struct RegisterValue {
                    RegisterValue(renderer::AsyncBitmapLoader *loader, TilesetInfo *tsInfo)
                    : bitmapLoader(loader), tilesetInfo(tsInfo), bitmapLoaderRelease(nullptr), tilesetInfoRelease(nullptr) { }
                    
                    ~RegisterValue();
                    
                    renderer::AsyncBitmapLoader *bitmapLoader;
                    TilesetInfo *tilesetInfo;
                    void (* bitmapLoaderRelease)(renderer::AsyncBitmapLoader *);
                    void (* tilesetInfoRelease)(TilesetInfo *);
                    
                };
                
                typedef std::map<PGSC::String, RegisterValue> RegisterMap;
                static RegisterMap registerMap;
                
                /*TODO--final static Map<String, ReferenceCount<Pair<GLBitmapLoader, WeakReference<TilesetInfo>>>> register = new HashMap<String, ReferenceCount<Pair<GLBitmapLoader, WeakReference<TilesetInfo>>>>();*/
                
                // for layer converters that spit out stuff based on 32bit floats...
                const static double EPSILON;
                
                std::unique_ptr<TilesetSupport> support;
                std::vector<int> tileArgb;
                
                int tileWidth;
                int tileHeight;
                int width;
                int height;
                
                int levelCount;
                int gridOffsetX;
                int gridOffsetY;
                int gridWidth;
                int gridHeight;
                
                util::Future<renderer::Bitmap> pending;
                
            public:
                LegacyTilePyramidTileReader(atakmap::raster::tilepyramid::TilesetInfo *info, renderer::AsyncBitmapLoader *loader);
                
                LegacyTilePyramidTileReader(TilesetInfo *info, TilesetSupport *support, PGSC::RefCountableIndirectPtr<AsynchronousIO> io);
                
                virtual ~LegacyTilePyramidTileReader();
                
            public:
                void start();
                
            public:
                void stop();
                
                /**************************************************************************/
                // Tile Reader
                
                virtual void disposeImpl();
                
                virtual int64_t getWidth() const {
                    return this->width;
                }
                
            protected:
                virtual void cancelAsyncRead(int id);
                
                /*virtual void cancel() {
                    PGSC::Lock lock(mutex);
                    
                    if(this->pending.valid())
                        this->pending.cancel(true);
                }*/
                
            public:
                virtual bool isMultiResolution() const {
                    return true;
                }
                
                virtual
                int64_t getHeight() const {
                    return this->height;
                }
                
                virtual
                int getTileWidth() const {
                    return this->tileWidth;
                }
                
                virtual
                int getTileHeight() const {
                    return this->tileHeight;
                }
                
            private:
                ReadResult getTileImpl(int level, int64_t tileColumn, int64_t tileRow, renderer::Bitmap *bitmap);
                
            public:
                ReadResult read(int level, int64_t tileColumn, int64_t tileRow, void *data, size_t dataSize);
                
                virtual
                ReadResult read(int64_t srcX, int64_t srcY, int64_t srcW, int64_t srcH, int dstW,
                                int dstH,  void *data, size_t dataSize);
                
            public:
                virtual
                Format getFormat() const {
                    return TileReader::RGBA;
                }
                
                virtual
                Interleave getInterleave() const {
                    return TileReader::BIP;
                }
                
                /**************************************************************************/
                
            private:
                inline static double _alignMin(double o, double v, double a) {
                    double n = (v - o) / a;
                    return (floor(n) * a) + o;
                }
                
            private:
                inline static double _alignMax(double o, double v, double a) {
                    double n = (v - o) / a;
                    return (ceil(n) * a) + o;
                }
                
                /**************************************************************************/
                
            private:
                static std::string generateKey(renderer::AsyncBitmapLoader *loader, TilesetInfo *info);
                
            public:
                static PGSC::String registerTilesetInfo(renderer::AsyncBitmapLoader *loader, TilesetInfo *tsInfo);
                
                static void unregisterLayer(TilesetInfo *info);
            };
            
        }
    }
}

#endif