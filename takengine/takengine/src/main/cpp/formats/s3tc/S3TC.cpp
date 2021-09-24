#include "formats/s3tc/S3TC.h"

#include <algorithm>

#include "util/DataOutput2.h"
#include "util/Memory.h"

#define STB_DXT_IMPLEMENTATION
#include "formats/s3tc/stb_dxt.h"
#undef STB_DXT_IMPLEMENTATION

using namespace TAK::Engine::Formats::S3TC;

using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Util;

namespace
{
    TAKErr extractBlock(uint8_t *value, const Bitmap2 &bitmap, const std::size_t blockX, const std::size_t blockY, const std::size_t blockSize, const S3TCAlgorithm alg) NOTHROWS;
}

TAKErr TAK::Engine::Formats::S3TC::S3TC_compress(DataOutput2 &value, std::size_t *compressedSize, S3TCAlgorithm *algv, const Bitmap2 &bitmap) NOTHROWS
{
    TAKErr code(TE_Ok);
    S3TCAlgorithm alg;
    std::size_t compressedBlockSize;
    switch (bitmap.getFormat())
    {
    case Bitmap2::ARGB32 :
    case Bitmap2::BGRA32 :
    case Bitmap2::MONOCHROME_ALPHA :
    case Bitmap2::RGBA32 :
    case Bitmap2::RGBA5551 :
        alg = TECA_DXT5;
        compressedBlockSize = 16u;
        break;
    default :
        alg = TECA_DXT1;
        compressedBlockSize = 8u;
        break;
    }
    if (algv)
        *algv = alg;
    
    std::size_t dstW = bitmap.getWidth();
    dstW += (dstW % 4);
    std::size_t dstH = bitmap.getHeight();
    dstH += (dstH % 4);
    const std::size_t numBlocksX = dstW / 4u;
    const std::size_t numBlocksY = dstH / 4u;
    if (compressedSize)
        *compressedSize = (numBlocksX*numBlocksY * compressedBlockSize);

    uint8_t block[64];
    uint8_t block_compressed[16u];
    for (std::size_t blockY = 0u; blockY < numBlocksY; blockY++) {
        for (std::size_t blockX = 0u; blockX < numBlocksX; blockX++) {
            code = extractBlock(block, bitmap, blockX, blockY, 4u, alg);
            TE_CHECKBREAK_CODE(code);
            memset(block_compressed, 0u, 16u);
            stb_compress_dxt_block(block_compressed, block, (alg == TECA_DXT5) ? 1 : 0, STB_DXT_DITHER);
            code = value.write(block_compressed, compressedBlockSize);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

     return code;
}
std::size_t TAK::Engine::Formats::S3TC::S3TC_getCompressedSize(const Bitmap2 &bitmap) NOTHROWS
{
    const S3TCAlgorithm alg = S3TC_getDefaultAlgorithm(bitmap.getFormat());
    std::size_t compressedSize;
    S3TC_getCompressedSize(&compressedSize, alg, bitmap.getWidth(), bitmap.getHeight());
    return compressedSize;
}
TAKErr TAK::Engine::Formats::S3TC::S3TC_getCompressedSize(std::size_t *value, const S3TCAlgorithm alg, const std::size_t width, const std::size_t height) NOTHROWS
{
    if (!value)
        return TE_InvalidArg;
    std::size_t compressedBlockSize;
    switch (alg) {
    case TECA_DXT1 :
        compressedBlockSize = 8u;
        break;
    case TECA_DXT5 :
        compressedBlockSize = 16u;
        break;
    default :
        return TE_InvalidArg;
    }
    const std::size_t numBlocksX = (width+3u) / 4u;
    const std::size_t numBlocksY = (height+3u) / 4u;
    *value = (numBlocksX*numBlocksY*compressedBlockSize);
    return TE_Ok;
}
S3TCAlgorithm TAK::Engine::Formats::S3TC::S3TC_getDefaultAlgorithm(const Bitmap2::Format fmt) NOTHROWS
{
    switch (fmt)
    {
    case Bitmap2::ARGB32 :
    case Bitmap2::BGRA32 :
    case Bitmap2::MONOCHROME_ALPHA :
    case Bitmap2::RGBA32 :
    case Bitmap2::RGBA5551 :
        return TECA_DXT5;
    default :
        return TECA_DXT1;
    }
}

namespace
{
    TAKErr extractBlock(uint8_t *value, const Bitmap2 &bitmap, const std::size_t blockX, const std::size_t blockY, const std::size_t blockSize, const S3TCAlgorithm alg) NOTHROWS
    {
        const std::size_t srcX = blockX * blockSize;
        const std::size_t srcY = blockY * blockSize;
        const std::size_t srcW = std::min(bitmap.getWidth()-(blockX * blockSize), blockSize);
        const std::size_t srcH = std::min(bitmap.getHeight()-(blockY * blockSize), blockSize);

        memset(value, 0xFFu, 64u);
        Bitmap2 block(std::move(Bitmap2::DataPtr(value, Memory_leaker_const<uint8_t>)), blockSize, blockSize, Bitmap2::RGBA32);
        return block.setRegion(bitmap, 0u, 0u, srcX, srcY, srcW, srcH);
    }
}
