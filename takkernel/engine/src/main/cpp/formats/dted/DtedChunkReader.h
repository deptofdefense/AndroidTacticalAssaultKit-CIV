#ifndef TAK_ENGINE_FORMATS_DTED_DTEDCHUNKREADER_H_INCLUDED
#define TAK_ENGINE_FORMATS_DTED_DTEDCHUNKREADER_H_INCLUDED

#include "port/Platform.h"
#include "util/DataInput2.h"
#include "util/Error.h"
#include "util/Memory.h"

namespace TAK {
    namespace Engine {
        namespace Formats {
            namespace DTED {
                class DtedSampler;

                //A carry over from the Dt2ElevationData.java, based on GLHeatMap.java, since Elevation manager is made
                //supposed to replace the other implementations for getting height this should be fine to have here.
                //Heavily influenced by the GLHeatMap.cs file.
                class ENGINE_API DtedChunkReader {
                public:
                    DtedChunkReader(const std::size_t size, const double cellLat, const double cellLng) NOTHROWS;
                public :
                    double chunkLongitudeToPixelX(std::size_t chunkX, double lng) const NOTHROWS;
                    double chunkLatitudeToPixelY(std::size_t chunkY, double lat) const NOTHROWS;
                    Util::TAKErr getChunkX(std::size_t *value, double leftLng, double longitude) const NOTHROWS;
                    Util::TAKErr getChunkY(std::size_t* value, double upperLat, double latitude) const NOTHROWS;
                    Util::TAKErr readHeader(Util::FileInput2 &stream) NOTHROWS;
                    Util::TAKErr loadChunk(Util::FileInput2 &stream, const std::size_t chunkX, const std::size_t chunkY) NOTHROWS;
                    std::size_t getChunkColumns() const NOTHROWS;
                    std::size_t getChunkRows() const NOTHROWS;
                    const uint8_t* getChunk() const NOTHROWS;
                private:
                    Util::array_ptr<uint8_t> _chunk;
                    std::size_t _bufferLength = 8;
                    std::size_t _extentX;
                    std::size_t _extentY;
                    std::size_t _dataRecSize;
                    double _cellLat;
                    double _cellLng;
                    std::size_t _chunkSize;
                    std::size_t _chunkBufferLength;
                    std::size_t _numChunksX;
                    std::size_t _numChunksY;
                    std::size_t _chunkRows;
                    std::size_t _chunkCols;

                    friend class DtedSampler;
                };
            }
        }
    }
}

#endif