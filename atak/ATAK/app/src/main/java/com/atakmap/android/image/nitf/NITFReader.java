
package com.atakmap.android.image.nitf;

import com.atakmap.map.layer.raster.gdal.GdalTileReader;

import org.gdal.gdal.Driver;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;

public class NITFReader {

    public NITFReader() {
    }

    public GdalTileReader.ReaderImpl GetImageReader(NITFImage img) {
        final int dataType = img.nitfDataset.GetRasterBand(1).getDataType();
        if (dataType == gdalconst.GDT_Byte) {
            return new ByteReaderImpl(img);
        } else if (dataType == gdalconst.GDT_UInt16
                || dataType == gdalconst.GDT_Int16) {
            return new ShortReaderImpl(img);
        } else if (dataType == gdalconst.GDT_UInt32
                || dataType == gdalconst.GDT_Int32) {
            return new IntReaderImpl(img);
        } else if (dataType == gdalconst.GDT_Float32) {
            return new FloatReaderImpl(img);
        } else if (dataType == gdalconst.GDT_Float64) {
            return new DoubleReaderImpl(img);
        } else {
            return null;
        }
    }

    /**************************************************************************/
    // Interleaved Reading

    private interface InterleaveParams {
        int getPixelSpacing(int dstW, int dstH, int numDataElements,
                int dataElementSizeBytes);

        int getLineSpacing(int dstW, int dstH, int numDataElements,
                int dataElementSizeBytes);

        int getBandSpacing(int dstW, int dstH, int numDataElements,
                int dataElementSizeBytes);
    }

    private final static class BSQInterleaveParams implements InterleaveParams {
        public final static InterleaveParams INSTANCE = new BSQInterleaveParams();

        private BSQInterleaveParams() {
        }

        @Override
        public final int getPixelSpacing(int dstW, int dstH,
                int numDataElements,
                int dataElementSizeBytes) {
            return 0;
        }

        @Override
        public final int getLineSpacing(int dstW, int dstH,
                int numDataElements,
                int dataElementSizeBytes) {
            return 0;
        }

        @Override
        public final int getBandSpacing(int dstW, int dstH,
                int numDataElements,
                int dataElementSizeBytes) {
            return 0;
        }
    }

    private final static class BIPInterleaveParams implements InterleaveParams {
        public final static InterleaveParams INSTANCE = new BIPInterleaveParams();

        private BIPInterleaveParams() {
        }

        @Override
        public final int getPixelSpacing(int dstW, int dstH,
                int numDataElements,
                int dataElementSizeBytes) {
            return (dataElementSizeBytes * numDataElements);
        }

        @Override
        public final int getLineSpacing(int dstW, int dstH,
                int numDataElements,
                int dataElementSizeBytes) {
            return (dstW * numDataElements * dataElementSizeBytes);
        }

        @Override
        public final int getBandSpacing(int dstW, int dstH,
                int numDataElements,
                int dataElementSizeBytes) {
            return dataElementSizeBytes;
        }
    }

    private final static class BILInterleaveParams implements InterleaveParams {
        public final static InterleaveParams INSTANCE = new BILInterleaveParams();

        private BILInterleaveParams() {
        }

        @Override
        public final int getPixelSpacing(int dstW, int dstH,
                int numDataElements,
                int dataElementSizeBytes) {
            return dataElementSizeBytes;
        }

        @Override
        public final int getLineSpacing(int dstW, int dstH,
                int numDataElements,
                int dataElementSizeBytes) {
            return (dstW * dataElementSizeBytes * numDataElements);
        }

        @Override
        public final int getBandSpacing(int dstW, int dstH,
                int numDataElements,
                int dataElementSizeBytes) {
            return (dstW * dataElementSizeBytes);
        }
    }

    /**************************************************************************/

    private static abstract class AbstractReaderImpl implements
            GdalTileReader.ReaderImpl {
        protected final int nbpp;
        protected final int abpp;
        protected NITFImage img;

        protected final double scaleMaxValue;

        protected final InterleaveParams interleaveParams;

        protected AbstractReaderImpl(NITFImage image) {
            img = image;
            switch (img.getInterleave()) {
                case BIP:
                    this.interleaveParams = BIPInterleaveParams.INSTANCE;
                    break;
                case BIL:
                    this.interleaveParams = BILInterleaveParams.INSTANCE;
                    break;
                case BSQ:
                    this.interleaveParams = BSQInterleaveParams.INSTANCE;
                    break;
                default:
                    throw new IllegalStateException();
            }

            this.nbpp = gdal.GetDataTypeSize(img.nitfDataset.GetRasterBand(1)
                    .getDataType());
            Driver imgDriver = img.nitfDataset.GetDriver();
            if (imgDriver != null
                    && imgDriver.GetDescription().equals("NITF")) {
                this.abpp = Integer.parseInt(img.nitfDataset
                        .GetMetadataItem("NITF_ABPP"));
            } else {
                this.abpp = this.nbpp;
            }

            this.scaleMaxValue = (double) (0xFFFFFFFFL >>> (32 - this.abpp));
        }
    }

    public static final class ByteReaderImpl extends AbstractReaderImpl {

        public ByteReaderImpl(NITFImage image) {
            super(image);
        }

        @Override
        public final int read(int srcX, int srcY, int srcW, int srcH, int dstW,
                int dstH,
                byte[] data) {
            final int numDataElements = img.internalPixelSize;
            final int nPixelSpace = this.interleaveParams.getPixelSpacing(dstW,
                    dstH,
                    numDataElements, 1);
            final int nLineSpace = this.interleaveParams.getLineSpacing(dstW,
                    dstH,
                    numDataElements, 1);
            final int nBandSpace = this.interleaveParams.getBandSpacing(dstW,
                    dstH,
                    numDataElements, 1);

            final int success = img.nitfDataset.ReadRaster(srcX, srcY, srcW,
                    srcH,
                    dstW, dstH, gdalconst.GDT_Byte, data, img.bandRequest,
                    nPixelSpace, nLineSpace, nBandSpace);
            // scale if necessary
            if (success == gdalconst.CE_None && this.abpp < this.nbpp)
                scaleABPP(data, dstW * dstH * numDataElements,
                        this.scaleMaxValue);
            return success;
        }
    }

    public static final class ShortReaderImpl extends AbstractReaderImpl {

        public ShortReaderImpl(NITFImage image) {
            super(image);
        }

        @Override
        public final int read(int srcX, int srcY, int srcW, int srcH, int dstW,
                int dstH,
                byte[] data) {
            final int numDataElements = img.internalPixelSize;
            final int nPixelSpace = this.interleaveParams.getPixelSpacing(dstW,
                    dstH,
                    numDataElements, 2);
            final int nLineSpace = this.interleaveParams.getLineSpacing(dstW,
                    dstH,
                    numDataElements, 2);
            final int nBandSpace = this.interleaveParams.getBandSpacing(dstW,
                    dstH,
                    numDataElements, 2);

            final int numSamples = (dstW * dstH * numDataElements);

            short[] arr = new short[numSamples];

            final int success = img.nitfDataset.ReadRaster(srcX, srcY, srcW,
                    srcH,
                    dstW, dstH, gdalconst.GDT_Int16, arr, img.bandRequest,
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

    private static final class IntReaderImpl extends AbstractReaderImpl {

        public IntReaderImpl(NITFImage image) {
            super(image);
        }

        @Override
        public final int read(int srcX, int srcY, int srcW, int srcH, int dstW,
                int dstH,
                byte[] data) {
            final int numDataElements = img.internalPixelSize;
            final int nPixelSpace = this.interleaveParams.getPixelSpacing(dstW,
                    dstH,
                    numDataElements, 4);
            final int nLineSpace = this.interleaveParams.getLineSpacing(dstW,
                    dstH,
                    numDataElements, 4);
            final int nBandSpace = this.interleaveParams.getBandSpacing(dstW,
                    dstH,
                    numDataElements, 4);

            final int numSamples = (dstW * dstH * numDataElements);

            int[] arr = new int[numSamples];

            final int success = img.nitfDataset.ReadRaster(srcX, srcY, srcW,
                    srcH,
                    dstW, dstH, gdalconst.GDT_Int32, arr, img.bandRequest,
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

    private static final class FloatReaderImpl extends AbstractReaderImpl {

        public FloatReaderImpl(NITFImage image) {
            super(image);
        }

        @Override
        public final int read(int srcX, int srcY, int srcW, int srcH, int dstW,
                int dstH,
                byte[] data) {
            final int numDataElements = img.internalPixelSize;
            final int nPixelSpace = this.interleaveParams.getPixelSpacing(dstW,
                    dstH,
                    numDataElements, 4);
            final int nLineSpace = this.interleaveParams.getLineSpacing(dstW,
                    dstH,
                    numDataElements, 4);
            final int nBandSpace = this.interleaveParams.getBandSpacing(dstW,
                    dstH,
                    numDataElements, 4);

            final int numSamples = (dstW * dstH * numDataElements);

            float[] arr = new float[numSamples];

            final int success = img.nitfDataset.ReadRaster(srcX, srcY, srcW,
                    srcH,
                    dstW, dstH, gdalconst.GDT_Float32, arr, img.bandRequest,
                    nPixelSpace, nLineSpace, nBandSpace);
            if (success == gdalconst.CE_None)
                scale(arr, data, numSamples);
            return success;
        }
    }

    private static final class DoubleReaderImpl extends AbstractReaderImpl {

        public DoubleReaderImpl(NITFImage image) {
            super(image);
        }

        @Override
        public final int read(int srcX, int srcY, int srcW, int srcH, int dstW,
                int dstH,
                byte[] data) {
            final int numDataElements = img.internalPixelSize;
            final int nPixelSpace = this.interleaveParams.getPixelSpacing(dstW,
                    dstH,
                    numDataElements, 8);
            final int nLineSpace = this.interleaveParams.getLineSpacing(dstW,
                    dstH,
                    numDataElements, 8);
            final int nBandSpace = this.interleaveParams.getBandSpacing(dstW,
                    dstH,
                    numDataElements, 8);

            final int numSamples = (dstW * dstH * numDataElements);

            double[] arr = new double[numSamples];

            final int success = img.nitfDataset.ReadRaster(srcX, srcY, srcW,
                    srcH,
                    dstW, dstH, gdalconst.GDT_Float64, arr, img.bandRequest,
                    nPixelSpace, nLineSpace, nBandSpace);
            if (success == gdalconst.CE_None)
                scale(arr, data, numSamples);
            return success;
        }
    }

    /**************************************************************************/

    private static void scaleABPP(byte[] arr, int len, double max) {
        for (int i = 0; i < len; i++)
            arr[i] = (byte) (((double) (arr[i] & 0xFF) / max) * 255.0);
    }

    private static void scaleABPP(short[] src, byte[] dst, int len,
            double max) {
        for (int i = 0; i < len; i++)
            dst[i] = (byte) (((double) (src[i] & 0xFFFF) / max) * 255.0);
    }

    private static void scaleABPP(int[] src, byte[] dst, int len,
            double max) {
        for (int i = 0; i < len; i++)
            dst[i] = (byte) (((double) (src[i] & 0xFFFFFFFFL) / max) * 255.0);
    }

    private static void scale(short[] src, byte[] dst, int len) {
        for (int i = 0; i < len; i++)
            dst[i] = (byte) (((double) (src[i] & 0xFFFF) / (double) 0xFFFF)
                    * 255.0);
    }

    private static void scale(int[] src, byte[] dst, int len) {
        for (int i = 0; i < len; i++)
            dst[i] = (byte) (((double) (src[i] & 0xFFFFFFFFL)
                    / (double) 0xFFFFFFFFL) * 255.0);
    }

    private static void scale(float[] src, byte[] dst, int len) {
        for (int i = 0; i < len; i++)
            dst[i] = (byte) (src[i] * 255.0);
    }

    private static void scale(double[] src, byte[] dst, int len) {
        for (int i = 0; i < len; i++)
            dst[i] = (byte) (src[i] * 255.0);
    }
}
