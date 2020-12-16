
#include "GdalBitmapReader.h"

#include <algorithm>
#include "util/Logging2.h"


using namespace TAK::Engine::Formats::GDAL;
using namespace TAK::Engine::Util;

namespace
{
    class ColorTableInfo
    {
    public:
        std::vector<GDALColorEntry> lut;
        int transparentPixel;
        GdalBitmapReader::Format format;
        GdalBitmapReader::Format alphaOrder;
        bool identity;

        ColorTableInfo(const GDALColorEntry *lut, size_t count);
    };

    class ReaderImpl
    {
    public:
        virtual CPLErr read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH, uint8_t *data) = 0;
        virtual ~ReaderImpl() = default;
    };

    class AbstractReaderImpl : public ReaderImpl
    {
    protected:
        int nbpp;
        int abpp;

        double scaleMaxValue;

        GdalBitmapReader *gdalReader;

        std::vector<int> *bandRequest;

        AbstractReaderImpl(GdalBitmapReader *gdalReader, std::vector<int> *bandRequest);

    public:
        CPLErr read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH, uint8_t *data) override = 0;
        virtual ~AbstractReaderImpl() = default;
    };

    class ByteReaderImpl : public AbstractReaderImpl
    {
    public:
        ByteReaderImpl(GdalBitmapReader *gdalReader, std::vector<int> *bandRequest);

        CPLErr read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                    uint8_t *data) override;
    };

    class ShortReaderImpl : public AbstractReaderImpl
    {
    public:
        ShortReaderImpl(GdalBitmapReader *gdalReader, std::vector<int> *bandRequest);

        CPLErr read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                            uint8_t *data) override;
    };

    class IntReaderImpl : public AbstractReaderImpl
    {
    public:
        IntReaderImpl(GdalBitmapReader *gdalReader, std::vector<int> *bandRequest);

        CPLErr read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                            uint8_t *data) override;
    };

    class FloatReaderImpl : public AbstractReaderImpl
    {
    public:
        FloatReaderImpl(GdalBitmapReader *gdalReader, std::vector<int> *bandRequest);

        CPLErr read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                            uint8_t *data) override;
    };

    class DoubleReaderImpl : public AbstractReaderImpl
    {
    public:
        DoubleReaderImpl(GdalBitmapReader *gdalReader, std::vector<int> *bandRequest);

        CPLErr read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                            uint8_t *data) override;
    };

    class UnsupportedReaderImpl : public AbstractReaderImpl
    {
    public:
        UnsupportedReaderImpl(GdalBitmapReader *gdalReader, std::vector<int> *bandRequest);

        CPLErr read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                            uint8_t *data) override;
    };

    int getPixelSpacing(int dstW, int dstH, int numDataElements, int dataElementSizeBytes);
    int getLineSpacing(int dstW, int dstH, int numDataElements, int dataElementSizeBytes);
    int getBandSpacing(int dstW, int dstH, int numDataElements, int dataElementSizeBytes);

    void scaleABPP(uint8_t *arr, int len, double max);

    void scaleABPP(int16_t *src, uint8_t *dst, int len, double max);

    void scaleABPP(int32_t *src, uint8_t *dst, int len, double max);

    void scale(int16_t *src, uint8_t *dst, int len);

    void scale(int32_t *src, uint8_t *dst, int len);

    void scale(float *src, uint8_t *dst, int len);

    void scale(double *src, uint8_t *dst, int len);

    TAKErr getPixelSize(int &pixelSize, GdalBitmapReader::Format format);

    std::vector<GDALColorEntry> getPalette(GDALColorTable *colorTable);
}

GdalBitmapReader::GdalBitmapReader(const char *uri) NOTHROWS :
    dataset((GDALDataset *) GDALOpen(uri, GA_ReadOnly)),
    width(0),
    height(0),
    numDataElements(0),
    alphaOrder(Format::ARGB),
    format(RGB), // guess, will be assigned
    impl(nullptr),
    colorTable(nullptr)
{
    if (nullptr != this->dataset) {

        this->width = this->dataset->GetRasterXSize();
        this->height = this->dataset->GetRasterYSize();

        const int numBands = GDALGetRasterCount(this->dataset);

        int alphaBand = 0;
        for (int i = 1; i <= numBands; i++) {
            if (this->dataset->GetRasterBand(i)->GetColorInterpretation() == GCI_AlphaBand) {
                alphaBand = i;
                break;
            }
        }

        if ((alphaBand == 0 && numBands >= 3) || (alphaBand != 0 && numBands > 3)) {

            if (alphaBand != 0) {
                this->bandRequest.insert(this->bandRequest.end(), {0, 0, 0, alphaBand});
                this->format = Format::RGBA;
            }
            else {
                this->bandRequest.insert(this->bandRequest.end(), {0, 0, 0});
                this->format = Format::RGB;
            }

            GDALColorInterp gci;
            for (int i = 1; i <= numBands; i++) {
                gci = this->dataset->GetRasterBand(i)->GetColorInterpretation();
                if (gci == GCI_RedBand)
                    this->bandRequest[0] = i;
                else if (gci == GCI_GreenBand)
                    this->bandRequest[1] = i;
                else if (gci == GCI_BlueBand)
                    this->bandRequest[2] = i;
            }

            // XXX - trying to fill in other bands, data may not always be RGB
            // so this needs to be smarter
            for (std::size_t i = 0u; i < this->bandRequest.size(); i++) {
                if (this->bandRequest[i] == 0) {
                    bool inUse;
                    for (int j = 1; j <= numBands; j++) {
                        inUse = false;
                        for (std::size_t k = 0u; k < this->bandRequest.size(); k++)
                            inUse |= (this->bandRequest[k] == j);
                        if (!inUse) {
                            this->bandRequest[i] = j;
                            break;
                        }
                    }
                }
            }
        }
        else if (alphaBand != 0) {
            this->format = Format::MONOCHROME_ALPHA;
            this->bandRequest.insert(this->bandRequest.end(), {
                    0, alphaBand
                });
            for (int i = 1; i <= numBands; i++) {
                if (this->dataset->GetRasterBand(i)->GetColorInterpretation() != GCI_AlphaBand) {
                    alphaBand = i;
                    break;
                }
            }
        }
        else if (this->dataset->GetRasterBand(1)->GetColorInterpretation() == GCI_PaletteIndex) {
            GDALRasterBand *b = this->dataset->GetRasterBand(1);
            GDALColorTable *ct = b->GetColorTable();
            this->bandRequest.insert(this->bandRequest.end(), {
                    1
                });

            if (ct->GetPaletteInterpretation() == GPI_RGB) {
                std::vector<GDALColorEntry> ctEntries = getPalette(ct);
                this->colorTable = new ColorTableInfo(&ctEntries[0], ctEntries.size());
                this->format = this->colorTable->format;
                this->alphaOrder = this->colorTable->alphaOrder;
                this->numDataElements = 1;
            }
            else {
                this->format = Format::MONOCHROME;
            }
        }
        else {
            this->format = Format::MONOCHROME;
            this->bandRequest.push_back(1);
        }

        if (this->numDataElements == 0) {
            if (TE_InvalidArg == this->getPixelSize(this->numDataElements))
                return;
        }

        GDALDataType dataType = this->dataset->GetRasterBand(1)->GetRasterDataType();
        if (dataType == GDT_Byte) {
            this->impl = new ByteReaderImpl(this, &(this->bandRequest));
        }
        else if (dataType == GDT_UInt16 || dataType == GDT_Int16) {
            this->impl = new ShortReaderImpl(this, &(this->bandRequest));
        }
        else if (dataType == GDT_UInt32 || dataType == GDT_Int32) {
            this->impl = new IntReaderImpl(this, &(this->bandRequest));
        }
        else if (dataType == GDT_Float32) {
            this->impl = new FloatReaderImpl(this, &(this->bandRequest));
        }
        else if (dataType == GDT_Float64) {
            this->impl = new DoubleReaderImpl(this, &(this->bandRequest));
        }
        else {
            this->impl = new UnsupportedReaderImpl(this, &(this->bandRequest));
        }
    }
}

GdalBitmapReader::~GdalBitmapReader() NOTHROWS
{
    if (nullptr != this->dataset)
        GDALClose(this->dataset);
    if (nullptr != this->colorTable) {
        delete this->colorTable;
        this->colorTable = nullptr;
    }
    if (nullptr != this->impl) {
        delete this->impl;
        this->impl = nullptr;
    }
}

GDALDataset *GdalBitmapReader::getDataset() NOTHROWS {
    return this->dataset;
}

bool GdalBitmapReader::hasAlphaBitmask() NOTHROWS {
    return (this->colorTable != nullptr && this->colorTable->transparentPixel != -1);
}

TAKErr GdalBitmapReader::setAlphaOrder(GdalBitmapReader::Format bmp_format) NOTHROWS {
    TAKErr code(TE_Ok);

    switch (bmp_format) {
    case GdalBitmapReader::Format::RGBA:
    case GdalBitmapReader::Format::ARGB:
        if (nullptr != this->colorTable)
            this->colorTable->alphaOrder = bmp_format;
        this->alphaOrder = bmp_format;
        break;
    default:
        code = TE_IllegalState;
        break;
    }

    return code;
}

int GdalBitmapReader::getDataTypeSize() const NOTHROWS {
    return GDALGetDataTypeSize(this->dataset->GetRasterBand(1)->GetRasterDataType());
}

TAKErr GdalBitmapReader::getPixelSize(int &pixelSize) const NOTHROWS {
    return ::getPixelSize(pixelSize, this->getFormat());
}

int GdalBitmapReader::getNumDataElements() const NOTHROWS {
    return this->numDataElements;
}

GdalBitmapReader::Format GdalBitmapReader::getFormat() const NOTHROWS {
    return this->format;
}

int GdalBitmapReader::getWidth() const NOTHROWS {
    return this->width;
}

int GdalBitmapReader::getHeight() const NOTHROWS {
    return this->height;
}

TAKErr GdalBitmapReader::read(int srcX, int srcY, int srcW, int srcH, int dstW,
                              int dstH, void *buf, size_t byteCount) NOTHROWS {

    auto *data = static_cast<uint8_t *>(buf);

    CPLErr success = this->impl->read(srcX, srcY, srcW, srcH, dstW, dstH, data);

    // expand lookup table values
    if (this->colorTable != nullptr
        && !(this->colorTable->identity && this->format == Format::MONOCHROME)) {

        int pixelSize(0);
        const TAKErr code = ::getPixelSize(pixelSize, this->format);
        TE_CHECKRETURN_CODE(code);

        if (this->format == Format::MONOCHROME) {

            for (int i = (dstW * dstH) - 1; i >= 0; i--)
                data[i] = (uint8_t)(this->colorTable->lut[data[i] & 0xFF].c1 & 0xFF);
        }
        else if (this->format == Format::MONOCHROME_ALPHA
                 && this->colorTable->transparentPixel != -1) {

            int expandedIdx = ((dstW * dstH) * pixelSize) - 1;
            int p;
            for (int i = (dstW * dstH) - 1; i >= 0; i--) {
                p = (this->colorTable->lut[data[i] & 0xFF].c1 & 0xFF);

                data[expandedIdx--] = (p == this->colorTable->transparentPixel) ? 0x00
                    : (uint8_t)0xFF;
                data[expandedIdx--] = (uint8_t)p;
            }
        }
        else {

            int expandedIdx = ((dstW * dstH) * pixelSize) - 1;
            int p;
            int shifts[4];
            int shiftsLen = 0;
            if (this->format == Format::RGBA) {
                shifts[0] = 24;
                shifts[1] = 0;
                shifts[2] = 8;
                shifts[3] = 16;
                shiftsLen = 4;
            }
            else if (this->format == Format::ARGB) {
                shifts[0] = 0;
                shifts[1] = 8;
                shifts[2] = 16;
                shifts[3] = 24;
                shiftsLen = 4;
            }
            else if (this->format == Format::MONOCHROME_ALPHA) {
                shifts[0] = 24;
                shifts[1] = 0;
                shiftsLen = 2;
            }
            else if (this->format == Format::RGB) {
                shifts[0] = 0;
                shifts[1] = 8;
                shifts[2] = 16;
                shiftsLen = 3;
            }
            else {
                return TE_Err;
            }

            const int numShifts = shiftsLen;
            for (int i = (dstW * dstH) - 1; i >= 0; i--) {
                GDALColorEntry ce = this->colorTable->lut[(data[i] & 0xFF)];
                //p = ce->c4 | ce->c1 << 8 | ce->c2 << 16 | ce->c3 << 24;
                p = ce.c4 << 24 | ce.c1 << 16 | ce.c2 << 8 | ce.c3;
                for (int j = 0; j < numShifts; j++)
                    data[expandedIdx--] = (uint8_t)((p >> shifts[j]) & 0xFF);
            }
        }
    }

    return (success == CE_None) ? TE_Ok : TE_Err;
}


namespace
{
    ColorTableInfo::ColorTableInfo(const GDALColorEntry *lut, size_t count)
        : transparentPixel(-999),
          format(GdalBitmapReader::ARGB),
          alphaOrder(GdalBitmapReader::ARGB),
          identity(true)
    {
        this->lut.reserve(std::max(static_cast<size_t>(256), count));
        this->lut.insert(this->lut.end(), lut, lut + count);

        bool alpha = false;
        bool color = false;
        int a;
        int r;
        int g;
        int b;
        for (std::size_t i = 0u; i < count; i++) {
            r = lut[i].c1;
            g = lut[i].c2;
            b = lut[i].c3;
            a = lut[i].c4;

            this->identity &= (r == i);
            alpha |= (a != 0xFF);

            if (r != g || g != b) {
                // the image is not monochrome
                color = true;

                // the transparent pixel mask requires that R, G and B all
                // share the same value for transparent pixels
                if (a != 0xFF)
                    this->transparentPixel = -1;
            }
            else if (a == 0) { // fully transparent pixel
                if (this->transparentPixel == -999) {
                    // set the transparent pixel mask
                    this->transparentPixel = r;
                }
                else if (this->transparentPixel != -1 && this->transparentPixel != r) {
                    // another pixel with full transparency is observed,
                    // there is no mask
                    this->transparentPixel = -1;
                }
            }
            else if (a != 0xFF && this->transparentPixel != -1) {
                // partial transparency, do not use transparent pixel mask
                this->transparentPixel = -1;
            }
        }

        if (color) {
            if (alpha)
                this->format = this->alphaOrder;
            else
                this->format = GdalBitmapReader::Format::RGB;
        }
        else {
            if (alpha)
                this->format = GdalBitmapReader::Format::MONOCHROME_ALPHA;
            else
                this->format = GdalBitmapReader::Format::MONOCHROME;
        }
    }

    TAKErr getPixelSize(int &pixelSize, GdalBitmapReader::Format format) {
        TAKErr code(TE_Ok);
        switch (format) {
        case GdalBitmapReader::MONOCHROME:
            pixelSize = 1;
            break;
        case GdalBitmapReader::MONOCHROME_ALPHA:
            pixelSize = 2;
            break;
        case GdalBitmapReader::RGB:
            pixelSize = 3;
            break;
        case GdalBitmapReader::ARGB:
        case GdalBitmapReader::RGBA:
            pixelSize = 4;
            break;
        default:
            code = TE_InvalidArg;
            break;
        }
        return code;
    }

    std::vector<GDALColorEntry> getPalette(GDALColorTable *colorTable) {
        std::vector<GDALColorEntry> retval;
        retval.reserve(colorTable->GetColorEntryCount());
        for (int i = 0; i < colorTable->GetColorEntryCount(); i++)
            retval.push_back(*colorTable->GetColorEntry(i));
        return retval;
    }

    AbstractReaderImpl::AbstractReaderImpl(GdalBitmapReader *gdalReader, std::vector<int> *bandRequest)
        : gdalReader(gdalReader),
          bandRequest(bandRequest) {

        this->nbpp = GDALGetDataTypeSize(gdalReader->getDataset()->GetRasterBand(1)->GetRasterDataType());
        if (strcmp(gdalReader->getDataset()->GetDriver()->GetDescription(), "NITF") == 0) {
            this->abpp = atoi(gdalReader->getDataset()->GetMetadataItem("NITF_ABPP", ""));
        }
        else {
            this->abpp = this->nbpp;
        }

        this->scaleMaxValue = (double)(0xFFFFFFFFUL >> (32 - this->abpp));
    }

    ByteReaderImpl::ByteReaderImpl(GdalBitmapReader *gdalReader, std::vector<int> *bandRequest)
        : AbstractReaderImpl(gdalReader, bandRequest) { }

    CPLErr ByteReaderImpl::read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                                uint8_t *data) {

        const int numDataElements = gdalReader->getNumDataElements();
        const int nPixelSpace = getPixelSpacing(dstW, dstH, numDataElements, 1);
        const int nLineSpace = getLineSpacing(dstW, dstH, numDataElements, 1);
        const int nBandSpace = getBandSpacing(dstW, dstH, numDataElements, 1);

        /*
          CPLErr GDALDataset::RasterIO  (   GDALRWFlag  eRWFlag,
          int   nXOff,
          int   nYOff,
          int   nXSize,
          int   nYSize,
          void *    pData,
          int   nBufXSize,
          int   nBufYSize,
          GDALDataType  eBufType,
          int   nBandCount,
          int *     panBandMap,
          GSpacing  nPixelSpace,
          GSpacing  nLineSpace,
          GSpacing  nBandSpace,
          GDALRasterIOExtraArg *    psExtraArg
          )
        */

        CPLErr success = gdalReader->getDataset()->RasterIO(GF_Read,
                                                            srcX, srcY, srcW, srcH, data,
                                                            dstW, dstH,
                                                            GDT_Byte,
                                                            static_cast<int>(this->bandRequest->size()), this->bandRequest->data(),
                                                            nPixelSpace, nLineSpace, nBandSpace);
        // scale if necessary
        if (success == CE_None && this->abpp < this->nbpp)
            scaleABPP(data, dstW * dstH * numDataElements, this->scaleMaxValue);
        return success;
    }

    ShortReaderImpl::ShortReaderImpl(GdalBitmapReader *gdalReader, std::vector<int> *bandRequest)
        : AbstractReaderImpl(gdalReader, bandRequest) { }

    CPLErr ShortReaderImpl::read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                                 uint8_t *data) {

        const int numDataElements = gdalReader->getNumDataElements();
        const int nPixelSpace = getPixelSpacing(dstW, dstH, numDataElements, 2);
        const int nLineSpace = getLineSpacing(dstW, dstH, numDataElements, 2);
        const int nBandSpace = getBandSpacing(dstW, dstH, numDataElements, 2);

        const int numSamples = (dstW * dstH * numDataElements);

        std::vector<int16_t> arr(numSamples, 0);

        CPLErr success = gdalReader->getDataset()->RasterIO(GF_Read, srcX, srcY, srcW, srcH, data,
                                                            dstW, dstH,
                                                            GDT_Int16,
                                                            static_cast<int>(this->bandRequest->size()), this->bandRequest->data(),
                                                            nPixelSpace, nLineSpace, nBandSpace);
        if (success == CE_None) {
            if (this->abpp < this->nbpp)
                scaleABPP(&arr[0], data, numSamples, this->scaleMaxValue);
            else
                scale(&arr[0], data, numSamples);
        }
        return success;
    }

    IntReaderImpl::IntReaderImpl(GdalBitmapReader *gdalReader, std::vector<int> *bandRequest)
        : AbstractReaderImpl(gdalReader, bandRequest) { }

    CPLErr IntReaderImpl::read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                               uint8_t *data) {

        const int numDataElements = gdalReader->getNumDataElements();
        const int nPixelSpace = getPixelSpacing(dstW, dstH, numDataElements, 4);
        const int nLineSpace = getLineSpacing(dstW, dstH, numDataElements, 4);
        const int nBandSpace = getBandSpacing(dstW, dstH, numDataElements, 4);

        const int numSamples = (dstW * dstH * numDataElements);

        std::vector<int32_t> arr(numSamples);

        CPLErr success = gdalReader->getDataset()->RasterIO(GF_Read, srcX, srcY, srcW, srcH, data,
                                                            dstW, dstH,
                                                            GDT_Int32,
                                                            static_cast<int>(this->bandRequest->size()), this->bandRequest->data(),
                                                            nPixelSpace, nLineSpace, nBandSpace);
        if (success == CPLErr::CE_None) {
            if (this->abpp < this->nbpp)
                scaleABPP(&arr[0], data, numSamples, this->scaleMaxValue);
            else
                scale(&arr[0], data, numSamples);
        }
        return success;
    }

    FloatReaderImpl::FloatReaderImpl(GdalBitmapReader *gdalReader, std::vector<int> *bandRequest)
        : AbstractReaderImpl(gdalReader, bandRequest) { }

    CPLErr FloatReaderImpl::read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                                 uint8_t *data) {

        const int numDataElements = gdalReader->getNumDataElements();
        const int nPixelSpace = getPixelSpacing(dstW, dstH, numDataElements, 4);
        const int nLineSpace = getLineSpacing(dstW, dstH, numDataElements, 4);
        const int nBandSpace = getBandSpacing(dstW, dstH, numDataElements, 4);

        const int numSamples = (dstW * dstH * numDataElements);

        std::vector<float> arr(numSamples, 0.f);

        CPLErr success = gdalReader->getDataset()->RasterIO(GF_Read, srcX, srcY, srcW, srcH, data,
                                                            dstW, dstH,
                                                            GDT_Float32,
                                                            static_cast<int>(this->bandRequest->size()), this->bandRequest->data(),
                                                            nPixelSpace, nLineSpace, nBandSpace);
        if (success == CPLErr::CE_None)
            scale(&arr[0], data, numSamples);
        return success;
    }

    DoubleReaderImpl::DoubleReaderImpl(GdalBitmapReader *gdalReader, std::vector<int> *bandRequest)
        : AbstractReaderImpl(gdalReader, bandRequest) { }

    CPLErr DoubleReaderImpl::read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                                  uint8_t *data) {

        const int numDataElements = gdalReader->getNumDataElements();
        const int nPixelSpace = getPixelSpacing(dstW, dstH, numDataElements, 8);
        const int nLineSpace = getLineSpacing(dstW, dstH, numDataElements, 8);
        const int nBandSpace = getBandSpacing(dstW, dstH,numDataElements, 8);

        const int numSamples = (dstW * dstH * numDataElements);

        std::vector<double> arr(numSamples, 0.0);

        CPLErr success = gdalReader->getDataset()->RasterIO(GF_Read, srcX, srcY, srcW, srcH, data,
                                                            dstW, dstH,
                                                            GDT_Float64,
                                                            static_cast<int>(this->bandRequest->size()), this->bandRequest->data(),
                                                            nPixelSpace, nLineSpace, nBandSpace);
        if (success == CPLErr::CE_None)
            scale(&arr[0], data, numSamples);
        return success;
    }

    UnsupportedReaderImpl::UnsupportedReaderImpl(GdalBitmapReader *gdalReader, std::vector<int> *bandRequest)
        : AbstractReaderImpl(gdalReader, bandRequest) {
        Logger_log(LogLevel::TELL_Warning, "Unsupported data type; no data can be read");
    }

    CPLErr UnsupportedReaderImpl::read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                                  uint8_t *data) {
        return CPLErr::CE_Warning;
    }

    int getPixelSpacing(int dstW, int dstH, int numDataElements, int dataElementSizeBytes) {
        return (dataElementSizeBytes * numDataElements);
    }

    int getLineSpacing(int dstW, int dstH, int numDataElements, int dataElementSizeBytes) {
        return (dstW * numDataElements * dataElementSizeBytes);
    }

    int getBandSpacing(int dstW, int dstH, int numDataElements, int dataElementSizeBytes) {
        return dataElementSizeBytes;
    }

    void scaleABPP(uint8_t *arr, int len, double max) {
        for (int i = 0; i < len; i++)
            arr[i] = (uint8_t)(((double)(arr[i] & 0xFF) / max) * 255.0);
    }

    void scaleABPP(int16_t *src, uint8_t *dst, int len, double max) {
        for (int i = 0; i < len; i++)
            dst[i] = (uint8_t)(((double)(src[i] & 0xFFFF) / max) * 255.0);
    }

    void scaleABPP(int32_t *src, uint8_t *dst, int len, double max) {
        for (int i = 0; i < len; i++)
            dst[i] = (uint8_t)(((double)(src[i] & 0xFFFFFFFFL) / max) * 255.0);
    }

    void scale(int16_t *src, uint8_t *dst, int len) {
        for (int i = 0; i < len; i++)
            dst[i] = (uint8_t)(((double)(src[i] & 0xFFFF) / (double)0xFFFF) * 255.0);
    }


    void scale(int32_t *src, uint8_t *dst, int len) {
        for (int i = 0; i < len; i++)
            dst[i] = (uint8_t)(((double)(src[i] & 0xFFFFFFFFL) / (double)0xFFFFFFFFL) * 255.0);
    }


    void scale(float *src, uint8_t *dst, int len) {
        for (int i = 0; i < len; i++)
            dst[i] = (uint8_t)(src[i] * 255.0);
    }


    void scale(double *src, uint8_t *dst, int len) {
        for (int i = 0; i < len; i++)
            dst[i] = (uint8_t)(src[i] * 255.0);
    }


}
