#ifndef TAK_ENGINE_RASTER_TILEMATRIX_TILEMATRIXTILEREADER_H_INCLUDED
#define TAK_ENGINE_RASTER_TILEMATRIX_TILEMATRIXTILEREADER_H_INCLUDED

#include "raster/tilematrix/TileMatrix.h"
#include "raster/tilereader/TileReader2.h"

namespace TAK {
    namespace Engine {
        namespace Raster {
            namespace TileMatrix {
                class ENGINE_API TileMatrixTileReader : public TAK::Engine::Raster::TileReader::TileReader2
                {
                public:
                    TileMatrixTileReader(const char *uri, TileMatrixPtr &&tiles) NOTHROWS;
                public:
                    Util::TAKErr getWidth(int64_t *value) NOTHROWS override;
                    Util::TAKErr getHeight(int64_t *value) NOTHROWS override;
                    Util::TAKErr getTileWidth(size_t *value) NOTHROWS override;
                    Util::TAKErr getTileHeight(size_t *value) NOTHROWS override;
                    Util::TAKErr read(uint8_t *buf, const int64_t srcX, const int64_t srcY, const int64_t srcW, const int64_t srcH,
                                              const size_t dstW, const size_t dstH) NOTHROWS override;
                    Util::TAKErr getFormat(Renderer::Bitmap2::Format *format) NOTHROWS override;
                    Util::TAKErr isMultiResolution(bool *value) NOTHROWS override;
                    Util::TAKErr getTileVersion(int64_t *value, const size_t level, const int64_t tileColumn, const int64_t tileRow) NOTHROWS override;
                private:
                    TileMatrixPtr impl;
                    bool compatible;
                    struct {
                        int64_t minX{ -1LL };
                        int64_t minY{ -1LL };
                        int64_t maxX{ -1LL };
                        int64_t maxY{ -1LL };
                        TileMatrix::ZoomLevel zoom;
                    } r0;
                    std::vector<TileMatrix::ZoomLevel> levels;
                };
            }
        }
    }
}
#endif
