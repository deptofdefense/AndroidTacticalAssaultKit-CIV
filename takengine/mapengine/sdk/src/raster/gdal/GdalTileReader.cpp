
#include <algorithm>
#include "raster/gdal/GdalTileReader.h"

using namespace atakmap::raster;
using namespace atakmap::raster::gdal;

GdalTileReader::ColorTableInfo::ColorTableInfo(const GDALColorEntry *lut, size_t count) {
    this->lut.reserve(std::max(static_cast<size_t>(256), count));
    this->lut.insert(this->lut.end(), lut, lut + count);
    
    this->transparentPixel = -999;
    this->identity = true;
    
    bool alpha = false;
    bool color = false;
    int a;
    int r;
    int g;
    int b;
    for (int i = 0; i < count; i++) {
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
            this->format = paletteRgbaFormat;
        else
            this->format = Format::RGB;
    }
    else {
        if (alpha)
            this->format = Format::MONOCHROME_ALPHA;
        else
            this->format = Format::MONOCHROME;
    }
}

GdalTileReader::GdalTileReaderSpi::~GdalTileReaderSpi() { }

const char *GdalTileReader::GdalTileReaderSpi::getName() const {
    return "gdal";
}

atakmap::raster::tilereader::TileReader *GdalTileReader::GdalTileReaderSpi::create(const char *uri, const tilereader::TileReaderFactory::Options *opts) {
    GDALDatasetH dataset = GDALOpen(uri, GA_ReadOnly);
    if (dataset != nullptr) {
        int tileWidth = 0;
        int tileHeight = 0;
        const char *cacheUri = nullptr;
        PGSC::RefCountableIndirectPtr<TileReader::AsynchronousIO> asyncIO;
        if (opts != nullptr) {
            tileWidth = opts->preferredTileWidth;
            tileHeight = opts->preferredTileHeight;
            cacheUri = opts->cacheUri;
            asyncIO = opts->asyncIO;
        }
        
        int blockXSize = 0, blockYSize = 0;
        GDALGetBlockSize(GDALGetRasterBand(dataset, 1), &blockXSize, &blockYSize);
        if (tileWidth <= 0)
            tileWidth = blockXSize;
        if (tileHeight <= 0)
            tileHeight = blockYSize;
        
        return new GdalTileReader((GDALDataset *)dataset, uri, tileWidth, tileHeight, cacheUri, asyncIO);
    }
    
    //TODO: Log.e("GdalTileReader", "Failed to open dataset " + uri, error);
    return nullptr;
}

GdalTileReader::Format GdalTileReader::paletteRgbaFormat = GdalTileReader::Format::ARGB;

GdalTileReader::GdalTileReader(GDALDataset *dataset,
                               const char *uri,
                               int tileWidth,
                               int tileHeight,
                               const char *cacheUri,
                               const PGSC::RefCountableIndirectPtr<TileReader::AsynchronousIO> &asynchronousIO)
: TileReader(uri, cacheUri, MAX_UNCACHED_READ_LEVEL, asynchronousIO),
width(GDALGetRasterXSize(dataset)),
height(GDALGetRasterYSize(dataset))
{
    this->tileWidth = std::min(tileWidth, this->width),
    this->tileHeight = std::min(tileHeight, this->height);
    
    this->maxNumResolutionLevels = getNumResolutionLevels(this->width, this->height,
                                                          this->tileWidth, this->tileHeight);
    
    this->dataset = dataset;
    
    this->internalPixelSize = 0;
    
    const int numBands = GDALGetRasterCount(this->dataset);
    
    int alphaBand = 0;
    for (int i = 1; i < numBands; i++) {
        if (this->dataset->GetRasterBand(i)->GetColorInterpretation() == GCI_AlphaBand) {
            alphaBand = i;
            break;
        }
    }
    
    if ((alphaBand == 0 && numBands >= 3) || (alphaBand != 0 && numBands > 3)) {
        alphaBand = 0; // XXX - ignoring for now
        
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
        for (int i = 0; i < this->bandRequest.size(); i++) {
            if (this->bandRequest[i] == 0) {
                bool inUse;
                for (int j = 1; j <= numBands; j++) {
                    inUse = false;
                    for (int k = 0; k < this->bandRequest.size(); k++)
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
            std::vector<GDALColorEntry> ctEntries = this->getPalette(ct);
            this->colorTable = new ColorTableInfo(&ctEntries[0], ctEntries.size());
            this->format = this->colorTable->format;
            this->internalPixelSize = 1;
        }
        else {
            this->format = Format::MONOCHROME;
        }
    }
    else {
        this->format = Format::MONOCHROME;
        this->bandRequest.push_back(1);
    }
    
    this->interleave = getInterleave(this->dataset, this->format, (this->colorTable != nullptr));
    
    if (this->internalPixelSize == 0)
        this->internalPixelSize = this->getPixelSize();
    
    GDALDataType dataType = this->dataset->GetRasterBand(1)->GetRasterDataType();
    if (dataType == GDT_Byte) {
        this->impl = new ByteReaderImpl(this);
    }
    else if (dataType == GDT_UInt16 || dataType == GDT_Int16) {
        this->impl = new ShortReaderImpl(this);
    }
    else if (dataType == GDT_UInt32 || dataType == GDT_Int32) {
        this->impl = new IntReaderImpl(this);
    }
    else if (dataType == GDT_Float32) {
        this->impl = new FloatReaderImpl(this);
    }
    else if (dataType == GDT_Float64) {
        this->impl = new DoubleReaderImpl(this);
    }
    else {
        //TODO-- throw new System::Exception("illegal state");
    }
    
    if (cacheUri != nullptr) {
        //TODO:    this->cacheSupport = new GdalTileCacheDataSupport();
    }
    
    this->readLock = new atakmap::util::SyncObject();//this->dataset;
}

GDALDataset *GdalTileReader::getDataset() {
    return this->dataset;
}

bool GdalTileReader::hasAlphaBitmask() {
    return (this->colorTable != nullptr && this->colorTable->transparentPixel != -1);
}

tilereader::TileReader::Format GdalTileReader::getFormat() const {
    return this->format;
}

tilereader::TileReader::Interleave GdalTileReader::getInterleave() const {
    return this->interleave;
}

int64_t GdalTileReader::getWidth() const {
    return this->width;
}

int64_t GdalTileReader::getHeight() const {
    return this->height;
}

int GdalTileReader::getTileWidth() const {
    return this->tileWidth;
}

/**
 * Returns the nominal width of a tile at the specified level.
 */
int GdalTileReader::getTileWidth(int level) const {
    return TileReader::getTileWidth(level, 0);
}

int GdalTileReader::getTileHeight() const {
    return this->tileHeight;
}

/**
 * Returns the nominal height of a tile at the specified level.
 */
int GdalTileReader::getTileHeight(int level) const {
    return TileReader::getTileHeight(level, 0);
}

int GdalTileReader::getMaxNumResolutionLevels() const {
    return this->maxNumResolutionLevels;
}

tilereader::TileReader::ReadResult GdalTileReader::read(int64_t srcX, int64_t srcY, int64_t srcW, int64_t srcH, int dstW,
                        int dstH, void *buf, size_t byteCount) {

    CPLErr success;
    uint8_t *data = static_cast<uint8_t *>(buf);
    {
        atakmap::util::SyncLock monitor(*this->readLock);
        if (!this->valid) {
            //throw new System::Exception("illegal state");
            return Error;
        }
        //TODO--this->dataset->ClearInterrupt();
        success = this->impl->read((int)srcX, (int)srcY, (int)srcW, (int)srcH, dstW, dstH, data);
    }
    // expand lookup table values
    if (this->colorTable != nullptr
        && !(this->colorTable->identity && this->format == Format::MONOCHROME)) {
        if (this->format == Format::MONOCHROME) {
            for (int i = (dstW * dstH) - 1; i >= 0; i--)
                data[i] = (uint8_t)(this->colorTable->lut[data[i] & 0xFF].c1 & 0xFF);
        }
        else if (this->format == Format::MONOCHROME_ALPHA
                 && this->colorTable->transparentPixel != -1) {
            int expandedIdx = ((dstW * dstH) * getPixelSize(this->format)) - 1;
            int p;
            for (int i = (dstW * dstH) - 1; i >= 0; i--) {
                p = (this->colorTable->lut[data[i] & 0xFF].c1 & 0xFF);
                
                data[expandedIdx--] = (p == this->colorTable->transparentPixel) ? 0x00
                : (uint8_t)0xFF;
                data[expandedIdx--] = (uint8_t)p;
            }
        }
        else {
            int expandedIdx = ((dstW * dstH) * getPixelSize(this->format)) - 1;
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
                shiftsLen = 4;
            }
            else if (this->format == Format::RGB) {
                shifts[0] = 0;
                shifts[1] = 8;
                shifts[2] = 16;
                shiftsLen = 3;
            }
            else {
                //throw new System::Exception("Bad format: " + this->format.ToString());
                return Error;
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
    
    return (success == CE_None) ? Success : Error;
}

GdalTileReader::BSQInterleaveParams *GdalTileReader::BSQInterleaveParams::sharedInstance() {
    static GdalTileReader::BSQInterleaveParams inst;
    return &inst;
}


GdalTileReader::BIPInterleaveParams *GdalTileReader::BIPInterleaveParams::sharedInstance() {
    static GdalTileReader::BIPInterleaveParams inst;
    return &inst;
}


GdalTileReader::BILInterleaveParams *GdalTileReader::BILInterleaveParams::sharedInstance() {
    static GdalTileReader::BILInterleaveParams inst;
    return &inst;
}

GdalTileReader::InterleaveParams *const GdalTileReader::BSQInterleaveParams::INSTANCE = GdalTileReader::BSQInterleaveParams::sharedInstance();

GdalTileReader::InterleaveParams * const GdalTileReader::BIPInterleaveParams::INSTANCE = BIPInterleaveParams::sharedInstance();

GdalTileReader::InterleaveParams *const GdalTileReader::BILInterleaveParams::INSTANCE = BILInterleaveParams::sharedInstance();

void GdalTileReader::disposeImpl() {
    TileReader::disposeImpl();
    delete this->dataset;
    this->dataset = nullptr;
}

void GdalTileReader::cancel() {
    //TODO--this->dataset->Interrupt();
}

void GdalTileReader::setPaletteRgbaFormat(Format format) {
    switch (format) {
        case Format::RGBA:
        case Format::ARGB:
            break;
        default:
            //TODO--throw new System::Exception("illegal state");
            break;
    }
    paletteRgbaFormat = format;
}

std::vector<GDALColorEntry> GdalTileReader::getPalette(GDALColorTable *colorTable) {
    std::vector<GDALColorEntry> retval;
    retval.reserve(colorTable->GetColorEntryCount());
    for (int i = 0; i < colorTable->GetColorEntryCount(); i++)
        retval.push_back(*colorTable->GetColorEntry(i));
    return retval;
}

tilereader::TileReader::Interleave GdalTileReader::getInterleave(GDALDataset *dataset, Format format, bool hasColorTable) {
    if (hasColorTable) {
        return Interleave::BIP;
    }
    else if (dataset->GetRasterCount() > 1) {
        const char *interleave = dataset->GetMetadataItem("INTERLEAVE", "IMAGE_STRUCTURE");
        if (interleave != nullptr) {
            if (strcmp(interleave, "PIXEL") == 0) {
                return Interleave::BIP;
            }
            else if (strcmp(interleave, "BAND") == 0) {
                return Interleave::BSQ;
            }
            else if (strcmp(interleave, "LINE") == 0) {
                return Interleave::BIL;
            }
        }
    }
    
    return Interleave::BSQ;
}

GdalTileReader::BSQInterleaveParams::BSQInterleaveParams() {
}

int GdalTileReader::BSQInterleaveParams::getPixelSpacing(int dstW, int dstH, int numDataElements,
                                                         int dataElementSizeBytes) {
    return 0;
}

int GdalTileReader::BSQInterleaveParams::getLineSpacing(int dstW, int dstH, int numDataElements,
                                                        int dataElementSizeBytes) {
    return 0;
}

int GdalTileReader::BSQInterleaveParams::getBandSpacing(int dstW, int dstH, int numDataElements,
                                                        int dataElementSizeBytes) {
    return 0;
}

GdalTileReader::BIPInterleaveParams::BIPInterleaveParams() {
}

int GdalTileReader::BIPInterleaveParams::getPixelSpacing(int dstW, int dstH, int numDataElements,
                                                         int dataElementSizeBytes) {
    return (dataElementSizeBytes * numDataElements);
}

int GdalTileReader::BIPInterleaveParams::getLineSpacing(int dstW, int dstH, int numDataElements,
                                                        int dataElementSizeBytes) {
    return (dstW * numDataElements * dataElementSizeBytes);
}

int GdalTileReader::BIPInterleaveParams::getBandSpacing(int dstW, int dstH, int numDataElements,
                                                        int dataElementSizeBytes) {
    return dataElementSizeBytes;
}

GdalTileReader::BILInterleaveParams::BILInterleaveParams() {
}

int GdalTileReader::BILInterleaveParams::getPixelSpacing(int dstW, int dstH, int numDataElements,
                                                         int dataElementSizeBytes) {
    return dataElementSizeBytes;
}

int GdalTileReader::BILInterleaveParams::getLineSpacing(int dstW, int dstH, int numDataElements,
                                                        int dataElementSizeBytes) {
    return (dstW * dataElementSizeBytes * numDataElements);
}

int GdalTileReader::BILInterleaveParams::getBandSpacing(int dstW, int dstH, int numDataElements,
                                                        int dataElementSizeBytes) {
    return (dstW * dataElementSizeBytes);
}

GdalTileReader::AbstractReaderImpl::AbstractReaderImpl(GdalTileReader *gdalTileReader)
: gdalTileReader(gdalTileReader) {
    switch (gdalTileReader->interleave) {
        case Interleave::BIP:
            this->interleaveParams = BIPInterleaveParams::INSTANCE;
            break;
        case Interleave::BIL:
            this->interleaveParams = BILInterleaveParams::INSTANCE;
            break;
        case Interleave::BSQ:
            this->interleaveParams = BSQInterleaveParams::INSTANCE;
            break;
        default:
            //TODO--throw new System::Exception("illegal state");
            break;
    }
    
    this->nbpp = GDALGetDataTypeSize(gdalTileReader->dataset->GetRasterBand(1)->GetRasterDataType());
    if (strcmp(gdalTileReader->dataset->GetDriver()->GetDescription(), "NITF") == 0) {
        this->abpp = atoi(gdalTileReader->dataset->GetMetadataItem("NITF_ABPP", ""));
    }
    else {
        this->abpp = this->nbpp;
    }
    
    this->scaleMaxValue = (double)(0xFFFFFFFFUL >> (32 - this->abpp));
}

GdalTileReader::ByteReaderImpl::ByteReaderImpl(GdalTileReader *gdalTileReader)
: AbstractReaderImpl(gdalTileReader) { }

CPLErr GdalTileReader::ByteReaderImpl::read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                                                         uint8_t *data) {
    const int numDataElements = gdalTileReader->internalPixelSize;
    const int nPixelSpace = this->interleaveParams->getPixelSpacing(dstW, dstH,
                                                                    numDataElements, 1);
    const int nLineSpace = this->interleaveParams->getLineSpacing(dstW, dstH,
                                                                  numDataElements, 1);
    const int nBandSpace = this->interleaveParams->getBandSpacing(dstW, dstH,
                                                                  numDataElements, 1);
    
    /*
     
     CPLErr GDALDataset::RasterIO	(	GDALRWFlag 	eRWFlag,
     int 	nXOff,
     int 	nYOff,
     int 	nXSize,
     int 	nYSize,
     void * 	pData,
     int 	nBufXSize,
     int 	nBufYSize,
     GDALDataType 	eBufType,
     int 	nBandCount,
     int * 	panBandMap,
     GSpacing 	nPixelSpace,
     GSpacing 	nLineSpace,
     GSpacing 	nBandSpace,
     GDALRasterIOExtraArg * 	psExtraArg 
     )
     
     
     */
    
    CPLErr success = gdalTileReader->dataset->RasterIO(GF_Read,
                                      srcX, srcY, srcW, srcH, data,
                                      dstW, dstH,
                                      GDT_Byte,
                                      static_cast<int>(gdalTileReader->bandRequest.size()), &gdalTileReader->bandRequest[0],
                                      nPixelSpace, nLineSpace, nBandSpace);
    // scale if necessary
    if (success == CE_None && this->abpp < this->nbpp)
        scaleABPP(data, dstW * dstH * numDataElements, this->scaleMaxValue);
    return success;
}

GdalTileReader::ShortReaderImpl::ShortReaderImpl(GdalTileReader *gdalTileReader)
: AbstractReaderImpl(gdalTileReader) { }

CPLErr GdalTileReader::ShortReaderImpl::read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                                                          uint8_t *data) {
    
    const int numDataElements = gdalTileReader->internalPixelSize;
    const int nPixelSpace = this->interleaveParams->getPixelSpacing(dstW, dstH,
                                                                    numDataElements, 2);
    const int nLineSpace = this->interleaveParams->getLineSpacing(dstW, dstH,
                                                                  numDataElements, 2);
    const int nBandSpace = this->interleaveParams->getBandSpacing(dstW, dstH,
                                                                  numDataElements, 2);
    
    const int numSamples = (dstW * dstH * numDataElements);
    
    std::vector<int16_t> arr(numSamples, 0);
    
    CPLErr success = gdalTileReader->dataset->RasterIO(GF_Read, srcX, srcY, srcW, srcH, data,
                                                                dstW, dstH,
                                                                GDT_Int16,
                                                                static_cast<int>(gdalTileReader->bandRequest.size()), &gdalTileReader->bandRequest[0],
                                                                nPixelSpace, nLineSpace, nBandSpace);
    if (success == CE_None) {
        if (this->abpp < this->nbpp)
            scaleABPP(&arr[0], data, numSamples, this->scaleMaxValue);
        else
            scale(&arr[0], data, numSamples);
    }
    return success;
}

GdalTileReader::IntReaderImpl::IntReaderImpl(GdalTileReader *gdalTileReader)
: AbstractReaderImpl(gdalTileReader) { }

CPLErr GdalTileReader::IntReaderImpl::read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                                                        uint8_t *data) {
    const int numDataElements = gdalTileReader->internalPixelSize;
    const int nPixelSpace = this->interleaveParams->getPixelSpacing(dstW, dstH,
                                                                    numDataElements, 4);
    const int nLineSpace = this->interleaveParams->getLineSpacing(dstW, dstH,
                                                                  numDataElements, 4);
    const int nBandSpace = this->interleaveParams->getBandSpacing(dstW, dstH,
                                                                  numDataElements, 4);
    
    const int numSamples = (dstW * dstH * numDataElements);
    
    std::vector<int32_t> arr(numSamples);
    
    CPLErr success = gdalTileReader->dataset->RasterIO(GF_Read, srcX, srcY, srcW, srcH, data,
                                                       dstW, dstH,
                                                       GDT_Int32,
                                                       static_cast<int>(gdalTileReader->bandRequest.size()), &gdalTileReader->bandRequest[0],
                                                       nPixelSpace, nLineSpace, nBandSpace);
    if (success == CPLErr::CE_None) {
        if (this->abpp < this->nbpp)
            scaleABPP(&arr[0], data, numSamples, this->scaleMaxValue);
        else
            scale(&arr[0], data, numSamples);
    }
    return success;
}

GdalTileReader::FloatReaderImpl::FloatReaderImpl(GdalTileReader *gdalTileReader)
: AbstractReaderImpl(gdalTileReader) { }

CPLErr GdalTileReader::FloatReaderImpl::read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                                                          uint8_t *data) {
    const int numDataElements = gdalTileReader->internalPixelSize;
    const int nPixelSpace = this->interleaveParams->getPixelSpacing(dstW, dstH,
                                                                    numDataElements, 4);
    const int nLineSpace = this->interleaveParams->getLineSpacing(dstW, dstH,
                                                                  numDataElements, 4);
    const int nBandSpace = this->interleaveParams->getBandSpacing(dstW, dstH,
                                                                  numDataElements, 4);
    
    const int numSamples = (dstW * dstH * numDataElements);
    
    std::vector<float> arr(numSamples, 0.f);
    
    CPLErr success = gdalTileReader->dataset->RasterIO(GF_Read, srcX, srcY, srcW, srcH, data,
                                                       dstW, dstH,
                                                       GDT_Float32,
                                                       static_cast<int>(gdalTileReader->bandRequest.size()), &gdalTileReader->bandRequest[0],
                                                       nPixelSpace, nLineSpace, nBandSpace);
    if (success == CPLErr::CE_None)
        scale(&arr[0], data, numSamples);
    return success;
}

GdalTileReader::DoubleReaderImpl::DoubleReaderImpl(GdalTileReader *gdalTileReader)
: AbstractReaderImpl(gdalTileReader) { }

CPLErr GdalTileReader::DoubleReaderImpl::read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                                                           uint8_t *data) {
    const int numDataElements = gdalTileReader->internalPixelSize;
    const int nPixelSpace = this->interleaveParams->getPixelSpacing(dstW, dstH,
                                                                    numDataElements, 8);
    const int nLineSpace = this->interleaveParams->getLineSpacing(dstW, dstH,
                                                                  numDataElements, 8);
    const int nBandSpace = this->interleaveParams->getBandSpacing(dstW, dstH,
                                                                  numDataElements, 8);
    
    const int numSamples = (dstW * dstH * numDataElements);
    
    std::vector<double> arr(numSamples, 0.0);
    
    CPLErr success = gdalTileReader->dataset->RasterIO(GF_Read, srcX, srcY, srcW, srcH, data,
                                                       dstW, dstH,
                                                       GDT_Float64,
                                                       static_cast<int>(gdalTileReader->bandRequest.size()), &gdalTileReader->bandRequest[0],
                                                       nPixelSpace, nLineSpace, nBandSpace);
    if (success == CPLErr::CE_None)
        scale(&arr[0], data, numSamples);
    return success;
}

void GdalTileReader::scaleABPP(uint8_t *arr, int len, double max) {
    for (int i = 0; i < len; i++)
        arr[i] = (uint8_t)(((double)(arr[i] & 0xFF) / max) * 255.0);
}

void GdalTileReader::scaleABPP(int16_t *src, uint8_t *dst, int len, double max) {
    for (int i = 0; i < len; i++)
        dst[i] = (uint8_t)(((double)(src[i] & 0xFFFF) / max) * 255.0);
}

void GdalTileReader::scaleABPP(int32_t *src, uint8_t *dst, int len, double max) {
    for (int i = 0; i < len; i++)
        dst[i] = (uint8_t)(((double)(src[i] & 0xFFFFFFFFL) / max) * 255.0);
}

void GdalTileReader::scale(int16_t *src, uint8_t *dst, int len) {
    for (int i = 0; i < len; i++)
        dst[i] = (uint8_t)(((double)(src[i] & 0xFFFF) / (double)0xFFFF) * 255.0);
}


void GdalTileReader::scale(int32_t *src, uint8_t *dst, int len) {
    for (int i = 0; i < len; i++)
        dst[i] = (uint8_t)(((double)(src[i] & 0xFFFFFFFFL) / (double)0xFFFFFFFFL) * 255.0);
}


void GdalTileReader::scale(float *src, uint8_t *dst, int len) {
    for (int i = 0; i < len; i++)
        dst[i] = (uint8_t)(src[i] * 255.0);
}


void GdalTileReader::scale(double *src, uint8_t *dst, int len) {
    for (int i = 0; i < len; i++)
        dst[i] = (uint8_t)(src[i] * 255.0);
}