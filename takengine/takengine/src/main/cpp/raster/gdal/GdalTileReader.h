#ifndef ATAKMAP_RASTER_GDALTILEREADER_H_INCLUDED
#define ATAKMAP_RASTER_GDALTILEREADER_H_INCLUDED

#include "gdal_priv.h"
#include "raster/tilereader/TileReader.h"
#include "raster/tilereader/TileReaderFactory.h"

#include <cstdint>

namespace atakmap {
    namespace raster {
        namespace gdal {
            
            class GdalTileReader : public tilereader::TileReader {
            private:
                class ReaderImpl {
                public:
                    virtual CPLErr read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH, uint8_t *data) = 0;
                };
                
                class ColorTableInfo {
                public:
                    std::vector<GDALColorEntry> lut;
                    int transparentPixel;
                    Format format;
                    bool identity;
                    
                    ColorTableInfo(const GDALColorEntry *lut, size_t count);
                };
                
                class GdalTileReaderSpi : public tilereader::TileReaderSpi {
                public:
                    virtual ~GdalTileReaderSpi();
                    virtual const char *getName() const;
                    virtual TileReader *create(const char *uri, const tilereader::TileReaderFactory::Options *options);
                };
                
            public:
                static tilereader::TileReaderSpi * const SPI;
                
            private:
                enum { MAX_UNCACHED_READ_LEVEL = 2 };
                
                static Format paletteRgbaFormat;// = Format::ARGB;
                
                /**************************************************************************/
                
            protected:
                GDALDataset *dataset;
                int tileWidth;
                int tileHeight;
                int width;
                int height;
                int maxNumResolutionLevels;
                
                Format format;
                Interleave interleave;
                
                std::vector<int> bandRequest;
                
                ReaderImpl *impl;
                
                ColorTableInfo *colorTable;
                
                int internalPixelSize;
                
                //TODO:GdalTileCacheDataSupport *cacheSupport;
                
            public:
                /**
                 * @param dataset
                 * @param tileWidth
                 * @param tileHeight
                 * @param cache
                 * @param asynchronousIO If <code>nullptr</code> a gcnew instance will be created for exclusive use
                 *            with this tile reader
                 */
                GdalTileReader(GDALDataset *dataset,
                               const char *uri,
                               int tileWidth,
                               int tileHeight,
                               const char *cacheUri,
                               const std::shared_ptr<TileReader::AsynchronousIO> &asynchronousIO);
                
                GDALDataset *getDataset();
                
                bool hasAlphaBitmask();
                
                /**************************************************************************/
                // Tile Source
                
                // cache support -- next 3
                
                /*TODO: @Override
                 public TileCacheData.Allocator getTileCacheDataAllocator() {
                 return this->cacheSupport;
                 }
                 
                 @Override
                 public TileCacheData.Compositor getTileCacheDataCompositor() {
                 return this->cacheSupport;
                 }
                 
                 @Override
                 public TileCacheData.Serializer getTileCacheDataSerializer() {
                 return this->cacheSupport;
                 }*/
                
                virtual Format getFormat() const override;
                
                virtual Interleave getInterleave() const override;
                
                virtual int64_t getWidth() const override;
                
                virtual int64_t getHeight() const override;
                
                virtual int getTileWidth() const override;
                
                /**
                 * Returns the nominal width of a tile at the specified level.
                 */
                int getTileWidth(int level) const;
                
                int getTileHeight() const override;
                
                /**
                 * Returns the nominal height of a tile at the specified level.
                 */
                int getTileHeight(int level) const;
                
                int getMaxNumResolutionLevels() const;
                
                virtual ReadResult read(int64_t srcX, int64_t srcY, int64_t srcW, int64_t srcH, int dstW,
                        int dstH, void *buf, size_t byteCount) override;
                
            protected:
                void disposeImpl();
                
                void cancel() override;
                
                /**************************************************************************/
                
            public:
                static void setPaletteRgbaFormat(Format format);
                
            private:
                static std::vector<GDALColorEntry> getPalette(GDALColorTable *colorTable);
                
                static Interleave getInterleave(GDALDataset *dataset, Format format, bool hasColorTable);
                
                /**************************************************************************/
                // Interleaved Reading
                
            private:
                
                class InterleaveParams {
                public:
                    virtual int getPixelSpacing(int dstW, int dstH, int numDataElements, int dataElementSizeBytes) = 0;
                    
                    virtual int getLineSpacing(int dstW, int dstH, int numDataElements, int dataElementSizeBytes) = 0;
                    
                    virtual int getBandSpacing(int dstW, int dstH, int numDataElements, int dataElementSizeBytes) = 0;
                };
                
                class BSQInterleaveParams : public InterleaveParams {
                public:
                    static InterleaveParams *const INSTANCE;// = gcnew BSQInterleaveParams();
                    
                private:
                    BSQInterleaveParams();
                    
                    static BSQInterleaveParams *sharedInstance();
                    
                public:
                    virtual int getPixelSpacing(int dstW, int dstH, int numDataElements,
                                                int dataElementSizeBytes);
                    
                    virtual int getLineSpacing(int dstW, int dstH, int numDataElements,
                                               int dataElementSizeBytes);
                    
                    virtual int getBandSpacing(int dstW, int dstH, int numDataElements,
                                               int dataElementSizeBytes);
                };
                
            private:
                class BIPInterleaveParams : public InterleaveParams {
                public:
                    static InterleaveParams * const INSTANCE;// = gcnew BIPInterleaveParams();
                    
                private:
                    BIPInterleaveParams();
                    
                    static BIPInterleaveParams *sharedInstance();
                    
                public:
                    virtual int getPixelSpacing(int dstW, int dstH, int numDataElements,
                                                int dataElementSizeBytes);
                    
                    virtual int getLineSpacing(int dstW, int dstH, int numDataElements,
                                               int dataElementSizeBytes);
                    
                    virtual int getBandSpacing(int dstW, int dstH, int numDataElements,
                                               int dataElementSizeBytes);
                };
                
                class BILInterleaveParams : public InterleaveParams {
                public:
                    static InterleaveParams *const INSTANCE;// = gcnew BILInterleaveParams();
                    
                private:
                    BILInterleaveParams();
                    
                    static BILInterleaveParams *sharedInstance();
                    
                public:
                    virtual int getPixelSpacing(int dstW, int dstH, int numDataElements,
                                                int dataElementSizeBytes);
                    
                    virtual int getLineSpacing(int dstW, int dstH, int numDataElements,
                                               int dataElementSizeBytes);
                    
                    virtual int getBandSpacing(int dstW, int dstH, int numDataElements,
                                               int dataElementSizeBytes);
                };
                
                class AbstractReaderImpl : public ReaderImpl {
                protected:
                    int nbpp;
                    int abpp;
                    
                    double scaleMaxValue;
                    
                    InterleaveParams *interleaveParams;
                    
                    GdalTileReader *gdalTileReader;
                    
                    AbstractReaderImpl(GdalTileReader *gdalTileReader);
                    
                public:
                    virtual CPLErr read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH, uint8_t *data) = 0;
                };
                
            private:
                class ByteReaderImpl : public AbstractReaderImpl {
                public:
                    ByteReaderImpl(GdalTileReader *gdalTileReader);
                    
                    CPLErr read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                                             uint8_t *data) override;
                };
                
            private:
                class ShortReaderImpl : public AbstractReaderImpl {
                public:
                    ShortReaderImpl(GdalTileReader *gdalTileReader);
                    
                    virtual CPLErr read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                                                     uint8_t *data) override;
                };
                
            private:
                class IntReaderImpl : public AbstractReaderImpl {
                public:
                    IntReaderImpl(GdalTileReader *gdalTileReader);
                    
                    virtual CPLErr read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                                                     uint8_t *data) override;
                };
                
            private:
                class FloatReaderImpl : public AbstractReaderImpl {
                public:
                    FloatReaderImpl(GdalTileReader *gdalTileReader);
                    
                    virtual CPLErr read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                                                     uint8_t *data) override;
                };
                
            private:
                class DoubleReaderImpl : public AbstractReaderImpl {
                public:
                    DoubleReaderImpl(GdalTileReader *gdalTileReader);
                    
                    virtual CPLErr read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                                                     uint8_t *data) override;
                };
                
                /**************************************************************************/
                // Tile Cache support
                
            private:
                /*class TileCacheDataCompositorImpl {
                public:
                    virtual void composite(GdalTileCacheData dst, GdalTileCacheData src, int pixelSize, int dstX, int dstY) = 0;
                };
                 
                const static TileCacheDataCompositorImpl *const DIRECT_BSQ;// = gcnew TileCacheDataCompositorImpl(){
                    public const void composite(GdalTileCacheData dst, GdalTileCacheData src, int pixelSize,
                 int dstX, int dstY) {
                 int dstBandOffset;
                 int srcBandOffset;
                 
                 for (int i = 0; i < pixelSize; i++) {
                 dstBandOffset = (i * dst.width * dst.height);
                 srcBandOffset = (i * src.width * src.height);
                 for (int y = 0; y < src.height; y++) {
                 System.arraycopy(src.data, src.offset + srcBandOffset + (y * src.width),
                 dst.data, dst.offset + dstBandOffset + ((dstY + y) * dst.width) + dstX,
                 src.width);
                 }
                 }
                 }
                 };
                 
                 private const static TileCacheDataCompositorImpl DIRECT_BIP = gcnew TileCacheDataCompositorImpl(){
                 public const void composite(GdalTileCacheData dst, GdalTileCacheData src, int pixelSize,
                 int dstX, int dstY) {
                 const int dstLineSize = (pixelSize * dst.width);
                 const int srcLineSize = (pixelSize * src.width);
                 
                 for (int y = 0; y < src.height; y++)
                 System.arraycopy(src.data, src.offset + (y * srcLineSize), dst.data, dst.offset
                 + ((dstY + y) * dstLineSize) + (dstX * pixelSize), srcLineSize);
                 }
                 };
                 
                 private const static TileCacheDataCompositorImpl DIRECT_BIL = gcnew TileCacheDataCompositorImpl(){
                 public const void composite(GdalTileCacheData dst, GdalTileCacheData src, int pixelSize,
                 int dstX, int dstY) {
                 const int dstLineSize = (pixelSize * dst.width);
                 const int srcLineSize = (pixelSize * src.width);
                 
                 for (int y = 0; y < src.height; y++) {
                 for (int i = 0; i < pixelSize; i++) {
                 System.arraycopy(src.data, src.offset + (y * srcLineSize) + (i * src.width),
                 dst.data, dst.offset + ((dstY + y) * dstLineSize) + (i * dst.width)
                 + dstX, src.width);
                 }
                 }
                 }
                 };
                 
                 private static class GdalTileCacheData extends TileCacheData {
                 private int width;
                 private int height;
                 private const uint8_t[] data;
                 private const int offset;
                 private const int length;
                 
                 public GdalTileCacheData(uint8_t[] data, int width, int height) {
                 this(data, 0, data.length, width, height);
                 }
                 
                 public GdalTileCacheData(uint8_t[] data, int offset, int length, int width, int height) {
                 super();
                 
                 this->width = width;
                 this->height = height;
                 
                 this->data = data;
                 this->offset = offset;
                 this->length = length;
                 }
                 
                 public int getWidth() {
                 return this->width;
                 }
                 
                 public int getHeight() {
                 return this->height;
                 }
                 
                 public uint8_t[] getPixelData() {
                 return this->data;
                 }
                 
                 public int getPixelDataOffset() {
                 return this->offset;
                 }
                 
                 public int getPixelDataLength() {
                 return this->length;
                 }
                 }
                 
                 private class GdalTileCacheDataSupport implements TileCacheData.Compositor,
                 TileCacheData.Allocator, TileCacheData.Serializer{
                 private const TileCacheDataCompositorImpl direct;
                 
                 public GdalTileCacheDataSupport() {
                 switch (gdalTileReader->interleave) {
                 case BIP:
                 this->direct = DIRECT_BIP;
                 break;
                 case BIL:
                 this->direct = DIRECT_BIL;
                 break;
                 case BSQ:
                 this->direct = DIRECT_BSQ;
                 break;
                 default:
                 throw gcnew IllegalStateException();
                 }
                 }
                 
                 
                 public TileCacheData allocate(int width, int height) {
                 return gcnew GdalTileCacheData(new uint8_t[width * height
                 * gdalTileReader->getPixelSize()], width, height);
                 }
                 
                 public void deallocate(TileCacheData data) {
                 // XXX -
                 }
                 
                 
                 public void composite(TileCacheData dstIface, TileCacheData srcIface, int dstX, int dstY,
                 int dstW, int dstH) {
                 GdalTileCacheData dst = (GdalTileCacheData)dstIface;
                 GdalTileCacheData src = (GdalTileCacheData)srcIface;
                 
                 const bool direct = (dstW == src.width && dstH == src.height);
                 if (direct && dstX == 0 && dstY == 0 && dstW == dst.width && dstH == dst.height) {
                 System.arraycopy(src.data, src.offset, dst.data, dst.offset,
                 (dstW * dstH * gdalTileReader->getPixelSize()));
                 }
                 else if (direct) {
                 this->direct.composite(dst, src, gdalTileReader->getPixelSize(), dstX, dstY);
                 }
                 else {
                 this->compositeImpl(dst, src, dstX, dstY, dstW, dstH);
                 }
                 }
                 
                 private void compositeImpl(GdalTileCacheData dst, GdalTileCacheData src, int dstX,
                 int dstY, int dstW, int dstH) {
                 Bitmap srcBitmap = GdalGraphicUtils.createBitmap(src.data, src.width, src.height,
                 gdalTileReader->interleave, gdalTileReader->format);
                 Bitmap scaledSrcBitmap = Bitmap.createScaledBitmap(srcBitmap, dstW, dstH, false);
                 
                 GdalTileCacheData scaledSrc = (GdalTileCacheData) this->allocate(dstW, dstH);
                 GdalGraphicUtils.getBitmapData(scaledSrcBitmap, scaledSrc.data, scaledSrc.width,
                 scaledSrc.height, gdalTileReader->interleave, gdalTileReader->format);
                 scaledSrcBitmap.recycle();
                 srcBitmap.recycle();
                 if (dstX == 0 && dstY == 0 && dstW == dst.width && dstH == dst.height) {
                 System.arraycopy(scaledSrc.data, scaledSrc.offset, dst.data, dst.offset, (dstW
                 * dstH * gdalTileReader->getPixelSize()));
                 }
                 else {
                 this->direct.composite(dst, scaledSrc, gdalTileReader->getPixelSize(), dstX,
                 dstY);
                 }
                 this->deallocate(scaledSrc);
                 }
                 
                 // IMPLEMENTATION NOTE
                 
                 // The serialization implementation will write a single uint8_t code
                 // identifying the serialized format at the END of the blob. this adds
                 // additional overhead during serialization for compressed data where
                 // the output length may not be known ahead of time but serialization
                 // will occur at most once per tile part for the lifetime of the cache.
                 // a code at the beginning of the blob would require a copy of the data
                 // on every deserialization even in cases where the part was cached in
                 // its uncompressed raw format
                 
                 public TileCacheData deserialize(uint8_t[] blob, int width, int height) {
                 // deserialize based on the format of the data
                 switch (blob[blob.length - 1] & 0xFF) {
                 case 0x00:
                 return deserialize0(blob, width, height);
                 default:
                 throw gcnew UnsupportedOperationException();
                 }
                 }
                 
                 public uint8_t[] serialize(TileCacheData partIface) {
                 // XXX - select serialization -- JPEG for non-alpha???
                 return serialize0(partIface);
                 }
                 }
                 
                 private static uint8_t[] serialize0(TileCacheData partIface) {
                 GdalTileCacheData part = (GdalTileCacheData)partIface;
                 uint8_t[] retval = gcnew uint8_t[part.length + 1];
                 System.arraycopy(part.data, 0, retval, 0, part.length);
                 retval[retval.length - 1] = 0x00;
                 return retval;
                 }
                 
                 private static GdalTileCacheData deserialize0(uint8_t[] blob, int width, int height) {
                 return gcnew GdalTileCacheData(blob, 0, blob.length - 1, width, height);
                 }*/
                
            private:
                
                
            private:
                static void scaleABPP(uint8_t *arr, int len, double max);
                
                static void scaleABPP(int16_t *src, uint8_t *dst, int len, double max);
                
                static void scaleABPP(int32_t *src, uint8_t *dst, int len, double max);
                
                static void scale(int16_t *src, uint8_t *dst, int len);
                
                static void scale(int32_t *src, uint8_t *dst, int len);
                
                static void scale(float *src, uint8_t *dst, int len);
                
                static void scale(double *src, uint8_t *dst, int len);
            };
        };
    }
}

#endif
