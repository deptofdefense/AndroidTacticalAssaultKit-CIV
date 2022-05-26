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
    if (input.length() <= 0LL) {
        DynamicOutput membuf;
        code = membuf.open(64u * 1024u);
        TE_CHECKRETURN_CODE(code);
        code = IO_copy(membuf, input);
        TE_CHECKRETURN_CODE(code);

        const uint8_t* data;
        std::size_t dataLen;
        code = membuf.get(&data, &dataLen);
        TE_CHECKRETURN_CODE(code);

        return BitmapFactory2_decode(result, data, dataLen, opts);
    } else {
        const std::size_t dataLen = (std::size_t)input.length();
        array_ptr<uint8_t> data(new uint8_t[dataLen]);
        std::size_t n;
        code = input.read(data.get(), &n, dataLen);
        TE_CHECKRETURN_CODE(code);
        if (n < dataLen)
            return TE_EOF;
        return BitmapFactory2_decode(result, data.get(), dataLen, opts);
    }
}
TAKErr TAK::Engine::Renderer::BitmapFactory2_decode(BitmapPtr &result, const uint8_t *data, const std::size_t dataLen, const BitmapDecodeOptions *opts) NOTHROWS
{
    TAKErr code(TE_Ok);

    std::ostringstream os;
    os << "/vsimem/";
    os << (uintptr_t)(void *)data;

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
    // check if the dataset could be opened
    if (nullptr == reader.getDataset())
        return TE_InvalidArg;

    const int srcWidth = reader.getWidth();
    const int srcHeight = reader.getHeight();
    int dstWidth = srcWidth;
    int dstHeight = srcHeight;

#if 1
    // XXX - force subsampling
    if (dstWidth > 2048 || dstHeight > 2048) {
        double sampleX = 2048.0 / (double)srcWidth;
        double sampleY = 2048.0 / (double)srcHeight;
        double sample = std::min(sampleX, sampleY);
        dstWidth = static_cast<int>((double)dstWidth * sample);
        dstHeight = static_cast<int>((double)dstHeight * sample);
    }
#endif

    int numDataElements(0);
    code = reader.getPixelSize(numDataElements);
    TE_CHECKRETURN_CODE(code);
    const auto byteCount = static_cast<std::size_t>(numDataElements * dstWidth * dstHeight);

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

        if (opts &&
            opts->emplaceData &&
            result.get() &&
            result->getFormat() == bitmapFormat &&
            result->getWidth() == dstWidth &&
            result->getHeight() == dstHeight) {

            // nothing to do
        } else{
            Bitmap2::DataPtr data = Bitmap2::DataPtr(new(std::nothrow) uint8_t[byteCount], Util::Memory_array_deleter_const<uint8_t>);
            if (!data.get())
                return TE_OutOfMemory;
            result = BitmapPtr(new(std::nothrow) Bitmap2(std::move(data), dstWidth, dstHeight, bitmapFormat), Memory_deleter_const<Bitmap2>);
            if (!result.get())
                return TE_OutOfMemory;
        }

        code = reader.read(0, 0, srcWidth, srcHeight, dstWidth, dstHeight, result->getData(), byteCount);
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}

