
package com.atakmap.map.layer.raster.gdal;

import android.graphics.Bitmap;

import com.atakmap.coremap.log.Log;
import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.layer.raster.drg.DRGTileReader;
import com.atakmap.map.layer.raster.tilereader.TileCacheData;
import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory;
import com.atakmap.map.layer.raster.tilereader.TileReaderSpi;

import org.gdal.gdal.Band;
import org.gdal.gdal.ColorTable;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;

public class GdalTileReader extends TileReader {

    public final static TileReaderSpi SPI = new TileReaderSpi() {
        @Override
        public String getName() {
            return "gdal";
        }

        @Override
        public TileReader create(String uri, TileReaderFactory.Options opts) {
            // XXX - short circuit is here as moving it further up would
            //       involve unwinding the use of GLGdalMapLayer2 which needs
            //       to be fully traced for potential issues. This achieves
            //       the same net result as an immediate changeset without
            //       introducing any additional risk.

            // if the file is a DRG, return the DRG reader to do the masking
            if(DRGTileReader.SPI.isSupported(uri)) {
                final TileReader drg = DRGTileReader.SPI.create(uri, opts);
                if(drg != null)
                    return drg;
            }

            Throwable error = null;
            try {
                Dataset dataset = GdalLibrary.openDatasetFromPath(uri);
                if(dataset != null) {
                    int tileWidth = 0;
                    int tileHeight = 0;
                    String cacheUri = null;
                    AsynchronousIO asyncIO = null;
                    if(opts != null) {
                        tileWidth = opts.preferredTileWidth;
                        tileHeight = opts.preferredTileHeight;
                        cacheUri = opts.cacheUri;
                        asyncIO = opts.asyncIO;
                    }

                    if(tileWidth <= 0)
                        tileWidth = dataset.GetRasterBand(1).GetBlockXSize();
                    if(tileHeight <= 0)
                        tileHeight = dataset.GetRasterBand(1).GetBlockYSize();

                    return new GdalTileReader(dataset, uri, tileWidth, tileHeight, cacheUri, asyncIO);
                }
            } catch(Throwable t) {
                error = t;
            }
            
            Log.e("GdalTileReader", "Failed to open dataset " + uri, error);
            return null;
        }
        
        @Override
        public boolean isSupported(String uri) {
            if(uri.charAt(0) != '/')
                return false;
            
            Dataset dataset = null;
            try {
                dataset = GdalLibrary.openDatasetFromPath(uri);
                return (dataset != null);
            } catch (Exception e) { 
                Log.e(TAG, "unexpected error opening: " + uri, e);
                return false;
            } finally {
                if(dataset != null)
                    dataset.delete();
            }
        }
    };

    
    private final static int MAX_UNCACHED_READ_LEVEL = 2;

    private static Format paletteRgbaFormat = Format.ARGB;

    /**************************************************************************/

    protected final Dataset dataset;
    protected final int tileWidth;
    protected final int tileHeight;
    protected final int width;
    protected final int height;
    protected final int maxNumResolutionLevels;

    protected final Format format;
    protected final Interleave interleave;

    protected final int[] bandRequest;

    protected ReaderImpl impl;

    protected ColorTableInfo colorTable;

    protected int internalPixelSize;

    protected GdalTileCacheDataSupport cacheSupport;
    
    protected final Object disposalLock;

    protected boolean disposed;

    /**
     * @param dataset
     * @param tileWidth
     * @param tileHeight
     * @param cacheUri
     * @param asynchronousIO If <code>null</code> a new instance will be created for exclusive use
     *            with this tile reader
     */
    public GdalTileReader(Dataset dataset,
                          String uri,
                          int tileWidth,
                          int tileHeight,
                          String cacheUri,
            AsynchronousIO asynchronousIO) {

        super(uri, cacheUri, MAX_UNCACHED_READ_LEVEL, asynchronousIO);
        
        this.dataset = dataset;
        this.width = this.dataset.GetRasterXSize();
        this.height = this.dataset.getRasterYSize();
        this.tileWidth = Math.min(tileWidth, this.width);
        this.tileHeight = Math.min(tileHeight, this.height);
        this.maxNumResolutionLevels = getNumResolutionLevels(this.width, this.height,
                this.tileWidth, this.tileHeight);

        this.internalPixelSize = 0;

        final int numBands = this.dataset.GetRasterCount();

        int alphaBand = 0;
        for (int i = 1; i <= this.dataset.GetRasterCount(); i++) {
            if (this.dataset.GetRasterBand(i).GetColorInterpretation() == gdalconst.GCI_AlphaBand) {
                alphaBand = i;
                break;
            }
        }

        if ((alphaBand == 0 && numBands >= 3) || (alphaBand != 0 && numBands > 3)) {
            if (alphaBand != 0) {
                this.bandRequest = new int[] {
                        0, 0, 0, alphaBand
                };
                this.format = Format.RGBA;
            } else {
                this.bandRequest = new int[] {
                        0, 0, 0
                };
                this.format = Format.RGB;
            }

            int gci;
            for (int i = 1; i <= numBands; i++) {
                gci = this.dataset.GetRasterBand(i).GetColorInterpretation();
                if (gci == gdalconst.GCI_RedBand)
                    this.bandRequest[0] = i;
                else if (gci == gdalconst.GCI_GreenBand)
                    this.bandRequest[1] = i;
                else if (gci == gdalconst.GCI_BlueBand)
                    this.bandRequest[2] = i;
            }

            // XXX - trying to fill in other bands, data may not always be RGB
            // so this needs to be smarter
            for (int i = 0; i < this.bandRequest.length; i++) {
                if (this.bandRequest[i] == 0) {
                    boolean inUse;
                    for (int j = 1; j <= numBands; j++) {
                        inUse = false;
                        for (int k = 0; k < this.bandRequest.length; k++)
                            inUse |= (this.bandRequest[k] == j);
                        if (!inUse) {
                            this.bandRequest[i] = j;
                            break;
                        }
                    }
                }
            }
        } else if (alphaBand != 0) {
            this.format = Format.MONOCHROME_ALPHA;
            this.bandRequest = new int[] {
                    0, alphaBand
            };
            for (int i = 1; i <= numBands; i++) {
                if (this.dataset.GetRasterBand(i).GetColorInterpretation() != gdalconst.GCI_AlphaBand) {
                    alphaBand = i;
                    break;
                }
            }
        } else if (this.dataset.GetRasterBand(1).GetColorInterpretation() == gdalconst.GCI_PaletteIndex) {
            Band b = this.dataset.GetRasterBand(1);
            ColorTable ct = b.GetColorTable();
            this.bandRequest = new int[] {
                    1
            };

            if (ct != null && ct.GetPaletteInterpretation() == gdalconst.GPI_RGB) {
                this.colorTable = new ColorTableInfo(getPalette(ct));
                this.format = this.colorTable.format;
                this.internalPixelSize = 1;
            } else {
                this.format = Format.MONOCHROME;
            }
        } else {
            this.format = Format.MONOCHROME;
            this.bandRequest = new int[] {
                    1
            };
        }

        this.interleave = Interleave.BIP;

        if (this.internalPixelSize == 0)
            this.internalPixelSize = this.getPixelSize();

        final int dataType = this.dataset.GetRasterBand(1).getDataType();
        if (dataType == gdalconst.GDT_Byte) {
            this.impl = new ByteReaderImpl();
        } else if (dataType == gdalconst.GDT_UInt16 || dataType == gdalconst.GDT_Int16) {
            this.impl = new ShortReaderImpl();
        } else if (dataType == gdalconst.GDT_UInt32 || dataType == gdalconst.GDT_Int32) {
            this.impl = new IntReaderImpl();
        } else if (dataType == gdalconst.GDT_Float32) {
            this.impl = new FloatReaderImpl();
        } else if (dataType == gdalconst.GDT_Float64) {
            this.impl = new DoubleReaderImpl();
        } else {
            throw new UnsupportedOperationException();
        }

        if(cacheUri != null)
            this.cacheSupport = new GdalTileCacheDataSupport();

        this.readLock = this.dataset;
        this.disposalLock = new Object();
        this.disposed = false;
    }

    public Dataset getDataset() {
        return this.dataset;
    }

    public boolean hasAlphaBitmask() {
        return (this.colorTable != null && this.colorTable.transparentPixel != -1);
    }

    /**************************************************************************/
    // Tile Source

    // cache support -- next 3
    
    @Override
    public TileCacheData.Allocator getTileCacheDataAllocator() {
        return this.cacheSupport;
    }

    @Override
    public TileCacheData.Compositor getTileCacheDataCompositor() {
        return this.cacheSupport;
    }

    @Override
    public TileCacheData.Serializer getTileCacheDataSerializer() {
        return this.cacheSupport;
    }

    @Override
    public Format getFormat() {
        return this.format;
    }

    @Override
    public Interleave getInterleave() {
        return this.interleave;
    }

    @Override
    public long getWidth() {
        return this.width;
    }

    @Override
    public long getHeight() {
        return this.height;
    }

    @Override
    public int getTileWidth() {
        return this.tileWidth;
    }

    /**
     * Returns the nominal width of a tile at the specified level.
     */
    public int getTileWidth(int level) {
        return this.getTileWidth(level, 0);
    }

    @Override
    public int getTileHeight() {
        return this.tileHeight;
    }

    /**
     * Returns the nominal height of a tile at the specified level.
     */
    public int getTileHeight(int level) {
        return this.getTileHeight(level, 0);
    }

    @Override
    public int getMaxNumResolutionLevels() {
        return this.maxNumResolutionLevels;
    }

    @Override
    public ReadResult read(long srcX, long srcY, long srcW, long srcH, int dstW, int dstH, byte[] data) {
        final int success;
        synchronized (this.readLock) {
            if (!this.valid)
                throw new IllegalStateException();
            this.dataset.ClearInterrupt();
            success = this.impl.read((int)srcX, (int)srcY, (int)srcW, (int)srcH, dstW, dstH, data);
        }
        // expand lookup table values
        if (this.colorTable != null
                && !(this.colorTable.identity && this.format == Format.MONOCHROME)) {
            if (this.format == Format.MONOCHROME) {
                for (int i = (dstW * dstH) - 1; i >= 0; i--)
                    data[i] = (byte) (this.colorTable.lut[data[i] & 0xFF] & 0xFF);
            } else if (this.format == Format.MONOCHROME_ALPHA
                    && this.colorTable.transparentPixel != -1) {
                int expandedIdx = ((dstW * dstH) * getPixelSize(this.format)) - 1;
                int p;
                for (int i = (dstW * dstH) - 1; i >= 0; i--) {
                    p = (this.colorTable.lut[data[i] & 0xFF] & 0xFF);

                    data[expandedIdx--] = (p == this.colorTable.transparentPixel) ? 0x00
                            : (byte) 0xFF;
                    data[expandedIdx--] = (byte) p;
                }
            } else {
                int expandedIdx = ((dstW * dstH) * getPixelSize(this.format)) - 1;
                int p;
                final int[] shifts;
                if (this.format == Format.RGBA)
                    shifts = new int[] {
                            24, 0, 8, 16
                    };
                else if (this.format == Format.ARGB)
                    shifts = new int[] {
                            0, 8, 16, 24
                    };
                else if (this.format == Format.MONOCHROME_ALPHA)
                    shifts = new int[] {
                            24, 0
                    };
                else if (this.format == Format.RGB)
                    shifts = new int[] {
                            0, 8, 16
                    };
                else
                    throw new IllegalStateException("Bad format: " + this.format);
                final int numShifts = shifts.length;
                for (int i = (dstW * dstH) - 1; i >= 0; i--) {
                    p = this.colorTable.lut[(data[i] & 0xFF)];
                    for (int j = 0; j < numShifts; j++)
                        data[expandedIdx--] = (byte) ((p >> shifts[j]) & 0xFF);
                }
            }
        }

        
        return (success == gdalconst.CE_None) ? ReadResult.SUCCESS :
                    (success == gdalconst.CE_Interrupted) ?
                                ReadResult.CANCELED : ReadResult.ERROR;
    }

    @Override
    protected void disposeImpl() {
        super.disposeImpl();
        
        synchronized(this.disposalLock) {
            if(!this.disposed) {
                this.dataset.delete();
                this.disposed = true;
            }
        }
    }

    @Override
    protected void cancel() {
        synchronized(this.disposalLock) {
            if(this.disposed) {
                Log.w(TAG, "Attempting to cancel on a disposed dataset, " + uri);
                return;
            }
            this.dataset.Interrupt();
        }
    }

    /**************************************************************************/

    public static void setPaletteRgbaFormat(Format format) {
        switch (format) {
            case RGBA:
            case ARGB:
                break;
            default:
                throw new IllegalArgumentException();
        }
        paletteRgbaFormat = format;
    }

    private static int[] getPalette(ColorTable colorTable) {
        int[] retval = new int[colorTable.GetCount()];
        for (int i = 0; i < retval.length; i++)
            retval[i] = colorTable.GetColorEntry(i);
        return retval;
    }

    /**************************************************************************/
    // Interleaved Reading

    public static interface ReaderImpl {
        public int read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH, byte[] data);
    }

    static int getPixelSpacing(int dstW, int dstH, int numDataElements,
                                         int dataElementSizeBytes) {
        return (dataElementSizeBytes * numDataElements);
    }

    static int getLineSpacing(int dstW, int dstH, int numDataElements,
                                    int dataElementSizeBytes) {
        return (dstW * numDataElements * dataElementSizeBytes);
    }

    static int getBandSpacing(int dstW, int dstH, int numDataElements,
                                        int dataElementSizeBytes) {
            return dataElementSizeBytes;
    }

    abstract class AbstractReaderImpl implements ReaderImpl {
        final int nbpp;
        final int abpp;

        final double scaleMaxValue;
        final int elemSize;

        protected AbstractReaderImpl(int elemSize) {
            this.elemSize = elemSize;

            this.nbpp = gdal.GetDataTypeSize(GdalTileReader.this.dataset.GetRasterBand(1)
                    .getDataType());

            if (GdalTileReader.this.dataset.GetDriver() != null &&
                GdalTileReader.this.dataset.GetDriver().GetDescription().equals("NITF")) {
                this.abpp = Integer.parseInt(GdalTileReader.this.dataset
                        .GetMetadataItem("NITF_ABPP"));
            } else {
                this.abpp = this.nbpp;
            }

            this.scaleMaxValue = (double) (0xFFFFFFFFL >>> (32 - this.abpp));
        }
    }

    final class ByteReaderImpl extends AbstractReaderImpl {
        ByteReaderImpl() {
            super(1);
        }

        @Override
        public final int read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                              byte[] data) {
            final int numDataElements = GdalTileReader.this.internalPixelSize;
            final int nPixelSpace = getPixelSpacing(dstW, dstH, numDataElements, elemSize);
            final int nLineSpace = getLineSpacing(dstW, dstH, numDataElements, elemSize);
            final int nBandSpace = getBandSpacing(dstW, dstH, numDataElements, elemSize);

            final int success = GdalTileReader.this.dataset.ReadRaster(srcX, srcY, srcW, srcH,
                    dstW, dstH, gdalconst.GDT_Byte, data, GdalTileReader.this.bandRequest,
                    nPixelSpace, nLineSpace, nBandSpace);
            // scale if necessary
            if (success == gdalconst.CE_None && this.abpp < this.nbpp)
                scaleABPP(data, dstW * dstH * numDataElements, this.scaleMaxValue);
            return success;
        }
    }

    final class ShortReaderImpl extends AbstractReaderImpl {
        ShortReaderImpl() {
            super(2);
        }

        @Override
        public final int read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                              byte[] data) {
            final int numDataElements = GdalTileReader.this.internalPixelSize;
            final int nPixelSpace = getPixelSpacing(dstW, dstH, numDataElements, elemSize);
            final int nLineSpace = getLineSpacing(dstW, dstH, numDataElements, elemSize);
            final int nBandSpace = getBandSpacing(dstW, dstH, numDataElements, elemSize);

            final int numSamples = (dstW * dstH * numDataElements);

            short[] arr = new short[numSamples];

            final int success = GdalTileReader.this.dataset.ReadRaster(srcX, srcY, srcW, srcH,
                    dstW, dstH, gdalconst.GDT_Int16, arr, GdalTileReader.this.bandRequest,
                    nPixelSpace, nLineSpace, nBandSpace);
            if (success == gdalconst.CE_None) {
                if (this.abpp < this.nbpp)
                    scaleABPP(arr, data, numSamples, this.scaleMaxValue);
                else
                    scale(arr, data, numSamples);
            }
            return success;
        }
    }

    final class IntReaderImpl extends AbstractReaderImpl {
        IntReaderImpl() {
            super(4);
        }

        @Override
        public final int read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                              byte[] data) {
            final int numDataElements = GdalTileReader.this.internalPixelSize;
            final int nPixelSpace = getPixelSpacing(dstW, dstH, numDataElements, elemSize);
            final int nLineSpace = getLineSpacing(dstW, dstH, numDataElements, elemSize);
            final int nBandSpace = getBandSpacing(dstW, dstH, numDataElements, elemSize);

            final int numSamples = (dstW * dstH * numDataElements);

            int[] arr = new int[numSamples];

            final int success = GdalTileReader.this.dataset.ReadRaster(srcX, srcY, srcW, srcH,
                    dstW, dstH, gdalconst.GDT_Int32, arr, GdalTileReader.this.bandRequest,
                    nPixelSpace, nLineSpace, nBandSpace);
            if (success == gdalconst.CE_None) {
                if (this.abpp < this.nbpp)
                    scaleABPP(arr, data, numSamples, this.scaleMaxValue);
                else
                    scale(arr, data, numSamples);
            }
            return success;
        }
    }

    final class FloatReaderImpl extends AbstractReaderImpl {
        FloatReaderImpl() {
            super(4);
        }

        @Override
        public final int read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                              byte[] data) {
            final int numDataElements = GdalTileReader.this.internalPixelSize;
            final int nPixelSpace = getPixelSpacing(dstW, dstH, numDataElements, elemSize);
            final int nLineSpace = getLineSpacing(dstW, dstH, numDataElements, elemSize);
            final int nBandSpace = getBandSpacing(dstW, dstH, numDataElements, elemSize);

            final int numSamples = (dstW * dstH * numDataElements);

            float[] arr = new float[numSamples];

            final int success = GdalTileReader.this.dataset.ReadRaster(srcX, srcY, srcW, srcH,
                    dstW, dstH, gdalconst.GDT_Float32, arr, GdalTileReader.this.bandRequest,
                    nPixelSpace, nLineSpace, nBandSpace);
            if (success == gdalconst.CE_None)
                scale(arr, data, numSamples);
            return success;
        }
    }

    final class DoubleReaderImpl extends AbstractReaderImpl {
        DoubleReaderImpl() {
            super(8);
        }
        @Override
        public final int read(int srcX, int srcY, int srcW, int srcH, int dstW, int dstH,
                              byte[] data) {
            final int numDataElements = GdalTileReader.this.internalPixelSize;
            final int nPixelSpace = getPixelSpacing(dstW, dstH, numDataElements, elemSize);
            final int nLineSpace = getLineSpacing(dstW, dstH, numDataElements, elemSize);
            final int nBandSpace = getBandSpacing(dstW, dstH, numDataElements, elemSize);

            final int numSamples = (dstW * dstH * numDataElements);

            double[] arr = new double[numSamples];

            final int success = GdalTileReader.this.dataset.ReadRaster(srcX, srcY, srcW, srcH,
                    dstW, dstH, gdalconst.GDT_Float64, arr, GdalTileReader.this.bandRequest,
                    nPixelSpace, nLineSpace, nBandSpace);
            if (success == gdalconst.CE_None)
                scale(arr, data, numSamples);
            return success;
        }
    }

    /**************************************************************************/
    // Tile Cache support

    private static interface TileCacheDataCompositorImpl {
        public void composite(GdalTileCacheData dst, GdalTileCacheData src, int pixelSize,
                int dstX, int dstY);
    }

    private final static TileCacheDataCompositorImpl DIRECT_BSQ = new TileCacheDataCompositorImpl() {
        @Override
        public final void composite(GdalTileCacheData dst, GdalTileCacheData src, int pixelSize,
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

    private final static TileCacheDataCompositorImpl DIRECT_BIP = new TileCacheDataCompositorImpl() {
        @Override
        public final void composite(GdalTileCacheData dst, GdalTileCacheData src, int pixelSize,
                                    int dstX, int dstY) {
            final int dstLineSize = (pixelSize * dst.width);
            final int srcLineSize = (pixelSize * src.width);

            for (int y = 0; y < src.height; y++)
                System.arraycopy(src.data, src.offset + (y * srcLineSize), dst.data, dst.offset
                        + ((dstY + y) * dstLineSize) + (dstX * pixelSize), srcLineSize);
        }
    };

    private final static TileCacheDataCompositorImpl DIRECT_BIL = new TileCacheDataCompositorImpl() {
        @Override
        public final void composite(GdalTileCacheData dst, GdalTileCacheData src, int pixelSize,
                                    int dstX, int dstY) {
            final int dstLineSize = (pixelSize * dst.width);
            final int srcLineSize = (pixelSize * src.width);

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
        private final byte[] data;
        private final int offset;
        private final int length;

        public GdalTileCacheData(byte[] data, int width, int height) {
            this(data, 0, data.length, width, height);
        }

        public GdalTileCacheData(byte[] data, int offset, int length, int width, int height) {
            super();

            this.width = width;
            this.height = height;

            this.data = data;
            this.offset = offset;
            this.length = length;
        }

        @Override
        public int getWidth() {
            return this.width;
        }

        @Override
        public int getHeight() {
            return this.height;
        }

        @Override
        public byte[] getPixelData() {
            return this.data;
        }

        @Override
        public int getPixelDataOffset() {
            return this.offset;
        }

        @Override
        public int getPixelDataLength() {
            return this.length;
        }
    }

    private class GdalTileCacheDataSupport implements TileCacheData.Compositor,
            TileCacheData.Allocator, TileCacheData.Serializer {
        private final TileCacheDataCompositorImpl direct;

        public GdalTileCacheDataSupport() {
            switch (GdalTileReader.this.interleave) {
                case BIP:
                    this.direct = DIRECT_BIP;
                    break;
                case BIL:
                    this.direct = DIRECT_BIL;
                    break;
                case BSQ:
                    this.direct = DIRECT_BSQ;
                    break;
                default:
                    throw new IllegalStateException();
            }
        }

        /**********************************************************************/
        // Allocator

        @Override
        public TileCacheData allocate(int width, int height) {
            return new GdalTileCacheData(new byte[width * height
                    * GdalTileReader.this.getPixelSize()], width, height);
        }

        @Override
        public void deallocate(TileCacheData data) {
            // XXX -
        }

        /**********************************************************************/
        // Compositor

        @Override
        public void composite(TileCacheData dstIface, TileCacheData srcIface, int dstX, int dstY,
                              int dstW, int dstH) {
            GdalTileCacheData dst = (GdalTileCacheData) dstIface;
            GdalTileCacheData src = (GdalTileCacheData) srcIface;

            final boolean direct = (dstW == src.width && dstH == src.height);
            if (direct && dstX == 0 && dstY == 0 && dstW == dst.width && dstH == dst.height) {
                System.arraycopy(src.data, src.offset, dst.data, dst.offset,
                        (dstW * dstH * GdalTileReader.this.getPixelSize()));
            } else if (direct) {
                this.direct.composite(dst, src, GdalTileReader.this.getPixelSize(), dstX, dstY);
            } else {
                this.compositeImpl(dst, src, dstX, dstY, dstW, dstH);
            }
        }

        private void compositeImpl(GdalTileCacheData dst, GdalTileCacheData src, int dstX,
                int dstY, int dstW, int dstH) {
            Bitmap srcBitmap = GdalGraphicUtils.createBitmap(src.data, src.width, src.height,
                    GdalTileReader.this.interleave, GdalTileReader.this.format);
            Bitmap scaledSrcBitmap = Bitmap.createScaledBitmap(srcBitmap, dstW, dstH, false);

            GdalTileCacheData scaledSrc = (GdalTileCacheData) this.allocate(dstW, dstH);
            GdalGraphicUtils.getBitmapData(scaledSrcBitmap, scaledSrc.data, scaledSrc.width,
                    scaledSrc.height, GdalTileReader.this.interleave, GdalTileReader.this.format);
            scaledSrcBitmap.recycle();
            srcBitmap.recycle();
            if (dstX == 0 && dstY == 0 && dstW == dst.width && dstH == dst.height) {
                System.arraycopy(scaledSrc.data, scaledSrc.offset, dst.data, dst.offset, (dstW
                        * dstH * GdalTileReader.this.getPixelSize()));
            } else {
                this.direct.composite(dst, scaledSrc, GdalTileReader.this.getPixelSize(), dstX,
                        dstY);
            }
            this.deallocate(scaledSrc);
        }

        /**********************************************************************/
        // Serializer

        // IMPLEMENTATION NOTE

        // The serialization implementation will write a single byte code
        // identifying the serialized format at the END of the blob. this adds
        // additional overhead during serialization for compressed data where
        // the output length may not be known ahead of time but serialization
        // will occur at most once per tile part for the lifetime of the cache.
        // a code at the beginning of the blob would require a copy of the data
        // on every deserialization even in cases where the part was cached in
        // its uncompressed raw format

        @Override
        public TileCacheData deserialize(byte[] blob, int width, int height) {
            // deserialize based on the format of the data
            switch (blob[blob.length - 1] & 0xFF) {
                case 0x00:
                    return deserialize0(blob, width, height);
                default:
                    throw new UnsupportedOperationException();
            }
        }

        @Override
        public byte[] serialize(TileCacheData partIface) {
            // XXX - select serialization -- JPEG for non-alpha???
            return serialize0(partIface);
        }
    }

    private static byte[] serialize0(TileCacheData partIface) {
        GdalTileCacheData part = (GdalTileCacheData) partIface;
        byte[] retval = new byte[part.length + 1];
        System.arraycopy(part.data, 0, retval, 0, part.length);
        retval[retval.length - 1] = 0x00;
        return retval;
    }

    private static GdalTileCacheData deserialize0(byte[] blob, int width, int height) {
        return new GdalTileCacheData(blob, 0, blob.length - 1, width, height);
    }

    private static class ColorTableInfo {
        public int[] lut;
        public int transparentPixel;
        public Format format;
        public boolean identity;

        public ColorTableInfo(int[] lut) {
            this.lut = new int[Math.max(256, lut.length)];
            System.arraycopy(lut, 0, this.lut, 0, lut.length);

            this.transparentPixel = -999;
            this.identity = true;

            boolean alpha = false;
            boolean color = false;
            int a;
            int r;
            int g;
            int b;
            for (int i = 0; i < lut.length; i++) {
                a = (lut[i] >> 24) & 0xFF;
                r = (lut[i] >> 16) & 0xFF;
                g = (lut[i] >> 8) & 0xFF;
                b = lut[i] & 0xFF;

                this.identity &= (r == i);
                alpha |= (a != 0xFF);

                if (r != g || g != b) {
                    // the image is not monochrome
                    color = true;

                    // the transparent pixel mask requires that R, G and B all
                    // share the same value for transparent pixels
                    if (a != 0xFF)
                        this.transparentPixel = -1;
                } else if (a == 0) { // fully transparent pixel
                    if (this.transparentPixel == -999) {
                        // set the transparent pixel mask
                        this.transparentPixel = r;
                    } else if (this.transparentPixel != -1 && this.transparentPixel != r) {
                        // another pixel with full transparency is observed,
                        // there is no mask
                        this.transparentPixel = -1;
                    }
                } else if (a != 0xFF && this.transparentPixel != -1) {
                    // partial transparency, do not use transparent pixel mask
                    this.transparentPixel = -1;
                }
            }

            if (color) {
                if (alpha)
                    this.format = paletteRgbaFormat;
                else
                    this.format = Format.RGB;
            } else {
                if (alpha)
                    this.format = Format.MONOCHROME_ALPHA;
                else
                    this.format = Format.MONOCHROME;
            }
        }
    }

    /**************************************************************************/

    private final static void scaleABPP(byte[] arr, int len, double max) {
        for (int i = 0; i < len; i++)
            arr[i] = (byte) (((double) (arr[i] & 0xFF) / max) * 255.0);
    }

    private final static void scaleABPP(short[] src, byte[] dst, int len, double max) {
        for (int i = 0; i < len; i++)
            dst[i] = (byte) (((double) (src[i] & 0xFFFF) / max) * 255.0);
    }

    private final static void scaleABPP(int[] src, byte[] dst, int len, double max) {
        for (int i = 0; i < len; i++)
            dst[i] = (byte) (((double) (src[i] & 0xFFFFFFFFL) / max) * 255.0);
    }

    private final static void scale(short[] src, byte[] dst, int len) {
        for (int i = 0; i < len; i++)
            dst[i] = (byte) (((double) (src[i] & 0xFFFF) / (double) 0xFFFF) * 255.0);
    }

    private final static void scale(int[] src, byte[] dst, int len) {
        for (int i = 0; i < len; i++)
            dst[i] = (byte) (((double) (src[i] & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL) * 255.0);
    }

    private final static void scale(float[] src, byte[] dst, int len) {
        for (int i = 0; i < len; i++)
            dst[i] = (byte) (src[i] * 255.0);
    }

    private final static void scale(double[] src, byte[] dst, int len) {
        for (int i = 0; i < len; i++)
            dst[i] = (byte) (src[i] * 255.0);
    }
}
