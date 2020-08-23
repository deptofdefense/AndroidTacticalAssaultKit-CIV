#ifndef TAK_ENGINE_FORMATS_GDAL_GDALBITMAPREADER_H_INCLUDED
#define TAK_ENGINE_FORMATS_GDAL_GDALBITMAPREADER_H_INCLUDED

#include "gdal_priv.h"

#include <cstdint>

#include "port/Platform.h"
#include "util/Error.h"

namespace {
    class ReaderImpl;
    class ColorTableInfo;
}

namespace TAK {
    namespace Engine {
        namespace Formats {
            namespace GDAL {

                class ENGINE_API GdalBitmapReader
                {
                public:
                enum Format {
                    MONOCHROME,
                    MONOCHROME_ALPHA,
                    RGB,
                    RGBA,
                    ARGB,
                };

                public:

                GdalBitmapReader(const char *uri) NOTHROWS;
                ~GdalBitmapReader() NOTHROWS;

                GDALDataset *getDataset() NOTHROWS;

                bool hasAlphaBitmask() NOTHROWS;

                Util::TAKErr setAlphaOrder(Format bmp_format) NOTHROWS;

                int getNumDataElements() const NOTHROWS;

                int getDataTypeSize() const NOTHROWS;

                Util::TAKErr getPixelSize(int &pixelSize) const NOTHROWS;

                Format getFormat() const NOTHROWS;

                int getWidth() const NOTHROWS;

                int getHeight() const NOTHROWS;

                Util::TAKErr read(int srcX, int srcY, int srcW, int srcH, int dstW,
                                  int dstH, void *buf, size_t byteCount) NOTHROWS;

                protected:
                GDALDataset *dataset;
                int width;
                int height;
                int numDataElements;

                Format alphaOrder;

                Format format;

                std::vector<int> bandRequest;

                ReaderImpl *impl;

                ColorTableInfo *colorTable;

                };
            }
        }
    }
}

#endif
