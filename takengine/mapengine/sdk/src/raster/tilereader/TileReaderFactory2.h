#ifndef TAK_ENGINE_RASTER_TILEREADERFACTORY2_H_INCLUDED
#define TAK_ENGINE_RASTER_TILEREADERFACTORY2_H_INCLUDED

#include "port/Platform.h"
#include "port/Collection.h"
#include "raster/tilereader/TileReader2.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Raster {
            namespace TileReader {

                struct ENGINE_API TileReaderFactory2Options
                {
                    const char *preferredSpi;
                    size_t preferredTileWidth;
                    size_t preferredTileHeight;
                    const char *cacheUri;
                    std::shared_ptr<TileReader2::AsynchronousIO> asyncIO;

                    TileReaderFactory2Options();
                    TileReaderFactory2Options(const TileReaderFactory2Options &other);
                };

                class ENGINE_API TileReaderSpi2
                {
                   public:
                    virtual ~TileReaderSpi2() NOTHROWS = 0;
                    virtual const char *getName() const NOTHROWS = 0;
                    virtual Util::TAKErr create(TileReader2Ptr &reader, const char *uri,
                                                const TileReaderFactory2Options *options) const NOTHROWS = 0;
                    virtual Util::TAKErr isSupported(const char *uri) const NOTHROWS = 0;
                    virtual int getPriority() const NOTHROWS = 0;
                };
                typedef std::unique_ptr<TileReaderSpi2, void (*)(const TileReaderSpi2 *)> TileReaderSpi2Ptr;

                ENGINE_API Util::TAKErr TileReaderFactory2_create(TileReader2Ptr &reader, const char *uri) NOTHROWS;
                ENGINE_API Util::TAKErr TileReaderFactory2_create(TileReader2Ptr &reader, const char *uri, const TileReaderFactory2Options *options) NOTHROWS;
                ENGINE_API Util::TAKErr TileReaderFactory2_register(const std::shared_ptr<TileReaderSpi2> &spi) NOTHROWS;
                ENGINE_API Util::TAKErr TileReaderFactory2_unregister(const std::shared_ptr<TileReaderSpi2> &spi) NOTHROWS;
                ENGINE_API Util::TAKErr TileReaderFactory2_isSupported(const char *uri) NOTHROWS;
                ENGINE_API Util::TAKErr TileReaderFactory2_isSupported(const char *uri, const char *hint) NOTHROWS;

            }
        }
    }
}  // namespace TAK

#endif