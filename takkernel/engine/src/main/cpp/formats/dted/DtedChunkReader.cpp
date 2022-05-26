#include "formats/dted/DtedChunkReader.h"

#include <algorithm>
#include <cassert>
#include <cmath>

#include "port/String.h"

using namespace TAK::Engine::Formats::DTED;

using namespace TAK::Engine::Util;

#define _numLngLinesOffset 47
#define DataRecordPrefixSize 8
#define DataRecordSuffixSize 4
#define HeaderOffset 3428

#if 0
                    array<Byte>^ Chunk;
                    int  _bufferLength = 8;
                    int _extentX;
                    int _extentY;
                    long _dataRecSize;
                    int _chunkBufferLength;
                    double _cellLat;
                    double _cellLng;
                public:
                    int ChunkSize;
                    int NumChunksX;
                    int NumChunksY;
                    int ChunkRows;
                    int ChunkCols;
#endif
DtedChunkReader::DtedChunkReader(const std::size_t size, const double cellLat, const double cellLng) NOTHROWS :
    _chunkSize(size),
    _chunkBufferLength((_chunkSize+1u)*(_chunkSize+2u)*2u),
    _cellLat(cellLat),
    _cellLng(cellLng),
    _bufferLength(8u)
{}

double DtedChunkReader::chunkLongitudeToPixelX(std::size_t chunkX, double lng) const NOTHROWS
{
    return ((_extentX - 1) * (lng - _cellLng))
        - (chunkX * _chunkSize);
}
double DtedChunkReader::chunkLatitudeToPixelY(std::size_t chunkY, double lat) const NOTHROWS
{
    return ((_extentY - 1) * (_cellLat - lat))
        - (chunkY * _chunkSize);
}
TAKErr DtedChunkReader::getChunkX(std::size_t *value, double leftLng, double longitude) const NOTHROWS
{
    double lngRatio = longitude - leftLng;
    double xd = lngRatio * (_extentX - 1);
    if (xd < 0)
        return TE_InvalidArg;

    *value = (std::size_t)xd / _chunkSize;
    return TE_Ok;
}
TAKErr DtedChunkReader::getChunkY(std::size_t *value, double upperLat, double latitude) const NOTHROWS
{
    double latRatio = upperLat - latitude;
    double yd = latRatio * (_extentY - 1);
    if (yd < 0)
        return TE_InvalidArg;
    *value = (std::size_t)yd / _chunkSize;
    return TE_Ok;
}
TAKErr DtedChunkReader::readHeader(FileInput2 &stream) NOTHROWS
{
    TAKErr code(TE_Ok);
    code = stream.seek(_numLngLinesOffset);
    TE_CHECKRETURN_CODE(code);

    uint8_t _buffer[8u];
    std::size_t bytesRead;
    code = stream.read(_buffer, &bytesRead, 8u);
    TE_CHECKRETURN_CODE(code);
    if (bytesRead < 8u)
        return TE_EOF;

    char text[5u];

    {
        for(std::size_t i = 0u; i < 4u; i++)
            text[i] = _buffer[i];
        text[4u] = '\0';
        int parsed;
        code = TAK::Engine::Port::String_parseInteger(&parsed, text);
        TE_CHECKRETURN_CODE(code);
        if (parsed < 0)
            return TE_InvalidArg;
        _extentX = (std::size_t)parsed;
    }
    {
        for(std::size_t i = 0u; i < 4u; i++)
            text[i] = _buffer[i+4u];
        text[4u] = '\0';
        int parsed;
        code = TAK::Engine::Port::String_parseInteger(&parsed, text);
        TE_CHECKRETURN_CODE(code);
        if (parsed < 0)
            return TE_InvalidArg;
        _extentY = (std::size_t)parsed;
    }

    _dataRecSize = DataRecordPrefixSize + (_extentY * 2) + DataRecordSuffixSize;

    _numChunksX = (std::size_t)ceil(_extentX / (double)_chunkSize);
    _numChunksY = (std::size_t)ceil(_extentY / (double)_chunkSize);

    return TE_Ok;
}

TAKErr DtedChunkReader::loadChunk(FileInput2 &stream, const std::size_t chunkX, const std::size_t chunkY) NOTHROWS
{
    TAKErr code(TE_Ok);

    if (chunkX >= _numChunksX)
        return TE_InvalidArg;
    if (chunkY >= _numChunksY)
        return TE_InvalidArg;

    const std::size_t dstX = chunkX * _chunkSize;
    if (dstX > _extentX)
        return TE_IllegalState;
    const std::size_t dstY = chunkY * _chunkSize;
    if (dstY > _extentY)
        return TE_IllegalState;

    const std::size_t dstW = std::min(_chunkSize + 1u, _extentX - dstX);
    const std::size_t dstH = std::min(_chunkSize + 1u, _extentY - dstY);

    if (!_chunk.get()) {
        _chunk.reset(new(std::nothrow) uint8_t[_chunkBufferLength]);
        if (!_chunk.get())
            return TE_OutOfMemory;
    }

    std::size_t srcX;
    std::size_t srcY;

    for (std::size_t x = 0; x < dstW; x++) {
        srcX = (_extentY - 1) - (dstY + dstH - 1);
        srcY = (dstX + x);

        code = stream.seek(HeaderOffset + (srcY * _dataRecSize) + DataRecordPrefixSize + (srcX * 2));
        TE_CHECKBREAK_CODE(code);

        const std::size_t limit = _chunkBufferLength;
        const std::size_t position = _chunkBufferLength - (dstH * 2);
        assert(position <= limit);

        std::size_t numRead;
        code = stream.read(_chunk.get()+position, &numRead, limit - position);
        TE_CHECKBREAK_CODE(code);

        for (int y = 0; y < dstH; y++)
        {
            srcX = (dstH - 1) - y;
            _chunk[((y * dstW) + x) * 2] = _chunk[_chunkBufferLength - (dstH * 2) + (2 * srcX)];
            _chunk[(((y * dstW) + x) * 2) + 1] = _chunk[(_chunkBufferLength - (dstH * 2) + (2 * srcX)) + 1];
        }
    }
    TE_CHECKRETURN_CODE(code);

    _chunkCols = dstW;
    _chunkRows = dstH;

    return code;
}
std::size_t DtedChunkReader::getChunkColumns() const NOTHROWS
{
    return _chunkCols;
}
std::size_t DtedChunkReader::getChunkRows() const NOTHROWS
{
    return _chunkRows;
}
const uint8_t *DtedChunkReader::getChunk() const NOTHROWS
{
    return _chunk.get();
}
