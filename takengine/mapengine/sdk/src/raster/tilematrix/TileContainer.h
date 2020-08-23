#ifndef TAK_ENGINE_GEOPACKAGE_TILECONTAINER_H_INCLUDED
#define TAK_ENGINE_GEOPACKAGE_TILECONTAINER_H_INCLUDED

#include "raster/tilematrix/TileMatrix.h"

namespace TAK {
    namespace Engine {
        namespace Raster {
            namespace TileMatrix {

                class ENGINE_API TileContainer : public TileMatrix {
                   public:
                    virtual ~TileContainer() NOTHROWS;
                    virtual Util::TAKErr isReadOnly(bool* value) NOTHROWS = 0;
                    virtual Util::TAKErr setTile(const std::size_t level, const std::size_t x, const std::size_t y, const uint8_t* value, const std::size_t len, const int64_t expiration) NOTHROWS = 0;
                    virtual Util::TAKErr setTile(const std::size_t level, const std::size_t x, const std::size_t y, const Renderer::Bitmap2* data, const int64_t expiration) NOTHROWS = 0;

                    virtual bool hasTileExpirationMetadata() NOTHROWS = 0;
                    virtual int64_t getTileExpiration(const std::size_t level, const std::size_t x, const std::size_t y) NOTHROWS = 0;
                };

                typedef std::unique_ptr<TileContainer, void (*)(const TileContainer *)> TileContainerPtr;

            }
        }  // namespace Raster
    }  // namespace Engine
}  // namespace TAK

#endif
