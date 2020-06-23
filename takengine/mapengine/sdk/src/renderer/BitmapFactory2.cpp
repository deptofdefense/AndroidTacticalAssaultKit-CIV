#include "renderer/BitmapFactory2.h"

#include <algorithm>
#include <sstream>

#include "formats/gdal/GdalBitmapReader.h"
#include "util/DataOutput2.h"
#include "util/Memory.h"
#include "util/Logging2.h"

#include "gdal_priv.h"

using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Formats::GDAL;

TAKErr TAK::Engine::Renderer::BitmapFactory2_decode(BitmapPtr &result, DataInput2 &input, const BitmapDecodeOptions *opts) NOTHROWS
{
    TAKErr code(TE_Ok);
    DynamicOutput membuf;
    code = membuf.open(64u * 1024u);
    TE_CHECKRETURN_CODE(code);
    code = IO_copy(membuf, input);
    TE_CHECKRETURN_CODE(code);

    const uint8_t *data;
    std::size_t dataLen;
    code = membuf.get(&data, &dataLen);
    TE_CHECKRETURN_CODE(code);

    std::ostringstream os;
    os << "/vsimem/";
    os << &input;

    const std::string gdalMemoryFile(os.str().c_str());

    VSILFILE *fpMem = VSIFileFromMemBuffer(gdalMemoryFile.c_str(), (GByte*) data , (vsi_l_offset) dataLen, FALSE);
    if (nullptr == fpMem)
        return TE_IllegalState;

    int gdalCode = VSIFCloseL(fpMem);
    code = 0 == gdalCode ?
        BitmapFactory2_decode(result, gdalMemoryFile.c_str(), opts) :
        TE_IllegalState;

    VSIUnlink(gdalMemoryFile.c_str());

    return code;
}


TAKErr TAK::Engine::Renderer::BitmapFactory2_decode(BitmapPtr &result, const char *bitmapFilePath, const BitmapDecodeOptions *opts) NOTHROWS
{
    TAKErr code(TE_Ok);
    GdalBitmapReader reader(bitmapFilePath);
    if (nullptr == reader.getDataset())
        return TE_IllegalState;

    const int64_t srcWidth = reader.getWidth();
    const int64_t srcHeight = reader.getHeight();
    std::size_t dstWidth = srcWidth;
    std::size_t dstHeight = srcHeight;

#if 1
    if (dstWidth > 2048 || dstHeight > 2048) {
        double sampleX = 2048.0 / (double)srcWidth;
        double sampleY = 2048.0 / (double)srcHeight;
        double sample = std::min(sampleX, sampleY);
        dstWidth = (std::size_t)((double)dstWidth * sample);
        dstHeight = (std::size_t)((double)dstHeight * sample);
    }
#endif

    int numDataElements(0);
    code = reader.getPixelSize(numDataElements);
    TE_CHECKRETURN_CODE(code);
    const int64_t byteCount = numDataElements * dstWidth * dstHeight;

    Bitmap2::DataPtr data(new uint8_t[byteCount], Util::Memory_array_deleter_const<uint8_t>);
    code = reader.read(0, 0, srcWidth, srcHeight, dstWidth, dstHeight, data.get(), byteCount);
    if (TE_Ok == code) {
        Bitmap2::Format bitmapFormat;
        switch(reader.getFormat()) {
        case GdalBitmapReader::MONOCHROME:
            bitmapFormat = Bitmap2::MONOCHROME;
            break;
        case GdalBitmapReader::MONOCHROME_ALPHA:
            bitmapFormat = Bitmap2::MONOCHROME_ALPHA;
            break;
        case GdalBitmapReader::RGB:
            bitmapFormat = Bitmap2::RGB24;
            break;
        case GdalBitmapReader::RGBA:
            bitmapFormat = Bitmap2::RGBA32;
            break;
        case GdalBitmapReader::ARGB:
            bitmapFormat = Bitmap2::ARGB32;
            break;
        default:
            return TE_IllegalState;
        }
        result = BitmapPtr(new Bitmap2(std::move(data), dstWidth, dstHeight, bitmapFormat), Memory_deleter_const<Bitmap2>);
    }

    return code;
}

