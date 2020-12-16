
package com.atakmap.map.layer.raster.gdal;

import java.io.File;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Vector;

import com.atakmap.coremap.log.Log;

import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.opengl.GLES20FixedPipeline;

import android.graphics.Bitmap;

import org.gdal.gdal.Dataset;
import org.gdal.gdal.TranslateOptions;
import org.gdal.gdalconst.gdalconst;

public class GdalGraphicUtils {

    public final static String TAG = "GdalGraphicUtils";

    private GdalGraphicUtils() {
    }

    public static boolean resizeBitmap(String srcPath, String dstPath, String dstFmt, int dstWidth, int dstHeight) {
        Dataset src = null;
        Dataset dst = null;
        try {
            src = GdalLibrary.openDatasetFromFile(new File(srcPath), gdalconst.GA_ReadOnly);
            if(src == null)
                return false;
            Vector<String> args = new Vector<String>();
            args.add("-of");
            args.add(dstFmt);
            args.add("-outsize");
            args.add(String.valueOf(dstWidth));
            args.add(String.valueOf(dstHeight));
            // XXX - this is very strange, but using the bilinear sampling
            //       method for resampling yields a 25% performance increase
            args.add("-r");
            args.add("bilinear");
            TranslateOptions opts = new TranslateOptions(args);
            dst = org.gdal.gdal.gdal.Translate(dstPath, src, opts);
            return (dst != null);
        } finally {
            if(dst != null)
                dst.delete();
            if(src != null)
                src.delete();
        }
    }

    public static int getBufferFormat(TileReader.Format format) {
        switch (format) {
            case MONOCHROME:
                return GLES20FixedPipeline.GL_LUMINANCE;
            case MONOCHROME_ALPHA:
                return GLES20FixedPipeline.GL_LUMINANCE_ALPHA;
            case RGB:
                return GLES20FixedPipeline.GL_RGB;
            case RGBA:
                return GLES20FixedPipeline.GL_RGBA;
            case ARGB:
                return GLES20FixedPipeline.GL_RGBA;
            default:
                throw new UnsupportedOperationException();
        }
    }

    public static int getBufferType(TileReader.Format format) {
        switch (format) {
            case MONOCHROME:
                return GLES20FixedPipeline.GL_UNSIGNED_BYTE;
            case MONOCHROME_ALPHA:
                return GLES20FixedPipeline.GL_UNSIGNED_BYTE;
            case RGB:
                return GLES20FixedPipeline.GL_UNSIGNED_BYTE;
            case RGBA:
                return GLES20FixedPipeline.GL_UNSIGNED_BYTE;
            case ARGB:
                return GLES20FixedPipeline.GL_UNSIGNED_BYTE;
            default:
                throw new UnsupportedOperationException();
        }
    }

    public static int getBufferSize(int glTexFormat, int glTexType, int width,
                                    int height) {
        int bytesPerPixel;
        if (glTexFormat == GLES20FixedPipeline.GL_LUMINANCE) {
            bytesPerPixel = 1;
        } else if (glTexFormat == GLES20FixedPipeline.GL_LUMINANCE_ALPHA
                || glTexType == GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_5_5_1
                || glTexType == GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_6_5) {
            bytesPerPixel = 2;
        } else if (glTexFormat == GLES20FixedPipeline.GL_RGB) {
            bytesPerPixel = 3;
        } else if (glTexFormat == GLES20FixedPipeline.GL_RGBA) {
            bytesPerPixel = 4;
        } else {
            throw new IllegalStateException();
        }
        return bytesPerPixel * (width * height);
    }

    public static Buffer createBuffer(byte[] data, int width, int height,
            TileReader.Interleave interleave, TileReader.Format format,
            int glFormat, int glType) {
        ByteBuffer retval = com.atakmap.lang.Unsafe.allocateDirect(getBufferSize(glFormat,
                glType, width, height));
        retval.order(ByteOrder.nativeOrder());

        fillBuffer(retval, data, width, height, interleave, format, glFormat,
                glType);
        return retval;
    }

    public static void fillBuffer(ByteBuffer retval, byte[] data, int width,
            int height, TileReader.Interleave interleave,
            TileReader.Format format, int glFormat, int glType) {
        switch (format) {
            case MONOCHROME:
                switch (glFormat) {
                    case GLES20FixedPipeline.GL_LUMINANCE:
                        switch (glType) {
                            case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                MonoToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                        retval, data, width, height);
                                break;
                            default:
                                throw new UnsupportedOperationException();
                        }
                        break;
                    case GLES20FixedPipeline.GL_LUMINANCE_ALPHA:
                        switch (glType) {
                            case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                MonoToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                        retval, data, width, height);
                                break;
                            default:
                                throw new UnsupportedOperationException();
                        }
                        break;
                    case GLES20FixedPipeline.GL_RGB:
                        switch (glType) {
                            case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                MonoToBuffer__GL_RGB__GL_UNSIGNED_BYTE(retval,
                                        data, width, height);
                                break;
                            case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_6_5:
                                MonoToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                        retval, data, width, height);
                                break;
                            default:
                                throw new UnsupportedOperationException();
                        }
                        break;
                    case GLES20FixedPipeline.GL_RGBA:
                        switch (glType) {
                            case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                MonoToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(retval,
                                        data, width, height);
                                break;
                            case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_5_5_1:
                                MonoToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                        retval, data, width, height);
                                break;
                            default:
                                throw new UnsupportedOperationException();
                        }
                        break;
                    default:
                        throw new IllegalStateException();
                }
                break;
            case MONOCHROME_ALPHA:
                switch (interleave) {
                    case BIP:
                        switch (glFormat) {
                            case GLES20FixedPipeline.GL_LUMINANCE:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        MonoAlphaBIPToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_LUMINANCE_ALPHA:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        MonoAlphaBIPToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_RGB:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        MonoAlphaBIPToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_6_5:
                                        MonoAlphaBIPToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_RGBA:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        MonoAlphaBIPToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_5_5_1:
                                        MonoAlphaBIPToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            default:
                                throw new IllegalStateException();
                        }
                        break;
                    case BSQ:
                        switch (glFormat) {
                            case GLES20FixedPipeline.GL_LUMINANCE:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        MonoAlphaBSQToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_LUMINANCE_ALPHA:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        MonoAlphaBSQToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_RGB:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        MonoAlphaBSQToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_6_5:
                                        MonoAlphaBSQToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_RGBA:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        MonoAlphaBSQToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_5_5_1:
                                        MonoAlphaBSQToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            default:
                                throw new IllegalStateException();
                        }
                        break;
                    case BIL:
                        switch (glFormat) {
                            case GLES20FixedPipeline.GL_LUMINANCE:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        MonoAlphaBILToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_LUMINANCE_ALPHA:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        MonoAlphaBILToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_RGB:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        MonoAlphaBILToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_6_5:
                                        MonoAlphaBILToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_RGBA:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        MonoAlphaBILToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_5_5_1:
                                        MonoAlphaBILToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            default:
                                throw new IllegalStateException();
                        }
                        break;
                }
                break;
            case RGB:
                switch (interleave) {
                    case BIP:
                        switch (glFormat) {
                            case GLES20FixedPipeline.GL_LUMINANCE:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        RGB_BIPToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_LUMINANCE_ALPHA:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        RGB_BIPToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_RGB:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        RGB_BIPToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_6_5:
                                        RGB_BIPToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_RGBA:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        RGB_BIPToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_5_5_1:
                                        RGB_BIPToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            default:
                                throw new IllegalStateException();
                        }
                        break;
                    case BSQ:
                        switch (glFormat) {
                            case GLES20FixedPipeline.GL_LUMINANCE:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        RGB_BSQToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_LUMINANCE_ALPHA:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        RGB_BSQToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_RGB:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        RGB_BSQToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_6_5:
                                        RGB_BSQToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_RGBA:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        RGB_BSQToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_5_5_1:
                                        RGB_BSQToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            default:
                                throw new IllegalStateException();
                        }
                        break;
                    case BIL:
                        switch (glFormat) {
                            case GLES20FixedPipeline.GL_LUMINANCE:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        RGB_BILToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_LUMINANCE_ALPHA:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        RGB_BILToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_RGB:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        RGB_BILToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_6_5:
                                        RGB_BILToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_RGBA:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        RGB_BILToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_5_5_1:
                                        RGB_BILToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            default:
                                throw new IllegalStateException();
                        }
                        break;
                }
                break;
            case RGBA:
                switch (interleave) {
                    case BIP:
                        switch (glFormat) {
                            case GLES20FixedPipeline.GL_LUMINANCE:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        RGBA_BIPToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_LUMINANCE_ALPHA:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        RGBA_BIPToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_RGB:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        RGBA_BIPToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_6_5:
                                        RGBA_BIPToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_RGBA:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        RGBA_BIPToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_5_5_1:
                                        RGBA_BIPToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            default:
                                throw new IllegalStateException();
                        }
                        break;
                    case BSQ:
                        switch (glFormat) {
                            case GLES20FixedPipeline.GL_LUMINANCE:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        RGBA_BSQToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_LUMINANCE_ALPHA:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        RGBA_BSQToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_RGB:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        RGBA_BSQToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_6_5:
                                        RGBA_BSQToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_RGBA:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        RGBA_BSQToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_5_5_1:
                                        RGBA_BSQToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            default:
                                throw new IllegalStateException();
                        }
                        break;
                    case BIL:
                        switch (glFormat) {
                            case GLES20FixedPipeline.GL_LUMINANCE:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        RGBA_BILToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_LUMINANCE_ALPHA:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        RGBA_BILToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_RGB:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        RGBA_BILToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_6_5:
                                        RGBA_BILToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_RGBA:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        RGBA_BILToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_5_5_1:
                                        RGBA_BILToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            default:
                                throw new IllegalStateException();
                        }
                        break;
                }
                break;
            case ARGB:
                switch (interleave) {
                    case BIP:
                        switch (glFormat) {
                            case GLES20FixedPipeline.GL_LUMINANCE:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        ARGB_BIPToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_LUMINANCE_ALPHA:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        ARGB_BIPToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_RGB:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        ARGB_BIPToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_6_5:
                                        ARGB_BIPToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_RGBA:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        ARGB_BIPToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_5_5_1:
                                        ARGB_BIPToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            default:
                                throw new IllegalStateException();
                        }
                        break;
                    case BSQ:
                        switch (glFormat) {
                            case GLES20FixedPipeline.GL_LUMINANCE:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        ARGB_BSQToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_LUMINANCE_ALPHA:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        ARGB_BSQToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_RGB:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        ARGB_BSQToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_6_5:
                                        ARGB_BSQToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_RGBA:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        ARGB_BSQToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_5_5_1:
                                        ARGB_BSQToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            default:
                                throw new IllegalStateException();
                        }
                        break;
                    case BIL:
                        switch (glFormat) {
                            case GLES20FixedPipeline.GL_LUMINANCE:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        ARGB_BILToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_LUMINANCE_ALPHA:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        ARGB_BILToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_RGB:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        ARGB_BILToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_6_5:
                                        ARGB_BILToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            case GLES20FixedPipeline.GL_RGBA:
                                switch (glType) {
                                    case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                                        ARGB_BILToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                retval, data, width, height);
                                        break;
                                    case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_5_5_1:
                                        ARGB_BILToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                retval, data, width, height);
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                                break;
                            default:
                                throw new IllegalStateException();
                        }
                        break;
                }
                break;
            default:
                throw new IllegalStateException();
        }
        retval.flip();
    }

    public static Bitmap.Config getBitmapConfig(TileReader.Format format) {
        switch (format) {
            case MONOCHROME:
            case RGB:
                // XXX - opengl is skewing texture subimages that don't have
                // power of 2 dimensions for non-argb32 bitmaps
                // return Bitmap.Config.RGB_565;
            case MONOCHROME_ALPHA:
            case RGBA:
            case ARGB:
            default:
                return Bitmap.Config.ARGB_8888;
        }
    }

    public static Bitmap createBitmap(byte[] data, int width, int height,
            TileReader.Interleave interleave, TileReader.Format format) {
        return createBitmap(data, width, height, interleave, format,
                getBitmapConfig(format));
    }

    public static Bitmap createBitmap(byte[] data, int width, int height,
            TileReader.Interleave interleave, TileReader.Format format,
            Bitmap.Config config) {
        int[] argb = new int[width * height];

        switch (format) {
            case MONOCHROME:
                MonoToBitmap(argb, data, width, height);
                break;
            case MONOCHROME_ALPHA:
                switch (interleave) {
                    case BIP:
                        MonoAlphaBIPtoBitmap(argb, data, width, height);
                        break;
                    case BIL:
                        MonoAlphaBILtoBitmap(argb, data, width, height);
                        break;
                    case BSQ:
                        MonoAlphaBSQtoBitmap(argb, data, width, height);
                        break;
                    default:
                        throw new IllegalStateException();
                }
                break;
            case RGB:
                switch (interleave) {
                    case BIP:
                        RGB_BIPtoBitmap(argb, data, width, height);
                        break;
                    case BIL:
                        RGB_BILtoBitmap(argb, data, width, height);
                        break;
                    case BSQ:
                        RGB_BSQtoBitmap(argb, data, width, height);
                        break;
                    default:
                        throw new IllegalStateException();
                }
                break;
            case RGBA:
                switch (interleave) {
                    case BIP:
                        RGBA_BIPtoBitmap(argb, data, width, height);
                        break;
                    case BIL:
                        RGBA_BILtoBitmap(argb, data, width, height);
                        break;
                    case BSQ:
                        RGBA_BSQtoBitmap(argb, data, width, height);
                        break;
                    default:
                        throw new IllegalStateException();
                }
                break;
            case ARGB:
                switch (interleave) {
                    case BIP:
                        ARGB_BIPtoBitmap(argb, data, width, height);
                        break;
                    case BIL:
                        ARGB_BILtoBitmap(argb, data, width, height);
                        break;
                    case BSQ:
                        ARGB_BSQtoBitmap(argb, data, width, height);
                        break;
                    default:
                        throw new IllegalStateException();
                }
                break;
            default:
                throw new IllegalStateException();
        }

        return Bitmap.createBitmap(argb, width, height, config);
    }

    private static void MonoToBitmap(int[] argb, byte[] data, int width,
            int height) {
        final int numPixels = (width * height);
        int b;
        for (int i = 0; i < numPixels; i++) {
            b = (data[i] & 0xFF);
            argb[i] = 0xFF000000 | (b << 16) | (b << 8) | b;
        }
    }

    private static void MonoAlphaBIPtoBitmap(int[] argb, byte[] data,
            int width, int height) {
        final int numPixels = (width * height);
        int b;
        int idx = 0;
        for (int i = 0; i < numPixels; i++) {
            b = (data[idx++] & 0xFF);
            argb[i] = (b << 16) | (b << 8) | b;
            b = (data[idx++] & 0xFF);
            argb[i] |= (b << 24);
        }
    }

    private static void MonoAlphaBSQtoBitmap(int[] argb, byte[] data,
            int width, int height) {
        final int numPixels = (width * height);
        int b;
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        for (int i = 0; i < numPixels; i++) {
            b = (data[idx0++] & 0xFF);
            argb[i] = (b << 16) | (b << 8) | b;
            b = (data[idx1++] & 0xFF);
            argb[i] |= (b << 24);
        }
    }

    private static void MonoAlphaBILtoBitmap(int[] argb, byte[] data,
            int width, int height) {
        int b;
        int idx0;
        int idx1;
        for (int i = 0; i < height; i++) {
            idx0 = (i * width * 2);
            idx1 = idx0 + width;
            for (int j = 0; j < width; j++) {
                b = (data[idx0++] & 0xFF);
                argb[i] = (b << 16) | (b << 8) | b;
                b = (data[idx1++] & 0xFF);
                argb[i] |= (b << 24);
            }
        }
    }

    private static void RGB_BIPtoBitmap(int[] argb, byte[] data, int width,
            int height) {
        final int numPixels = (width * height);
        int b;
        int idx = 0;
        for (int i = 0; i < numPixels; i++) {
            argb[i] = 0xFF000000;

            b = (data[idx++] & 0xFF);
            argb[i] |= (b << 16);
            b = (data[idx++] & 0xFF);
            argb[i] |= (b << 8);
            b = (data[idx++] & 0xFF);
            argb[i] |= b;
        }
    }

    private static void RGB_BSQtoBitmap(int[] argb, byte[] data, int width,
            int height) {
        final int numPixels = (width * height);
        int b;
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        for (int i = 0; i < numPixels; i++) {
            argb[i] = 0xFF000000;

            b = (data[idx0++] & 0xFF);
            argb[i] |= (b << 16);
            b = (data[idx1++] & 0xFF);
            argb[i] |= (b << 8);
            b = (data[idx2++] & 0xFF);
            argb[i] |= b;
        }
    }

    private static void RGB_BILtoBitmap(int[] argb, byte[] data, int width,
            int height) {
        int b;
        int idx0;
        int idx1;
        int idx2;
        for (int i = 0; i < height; i++) {
            idx0 = (i * width * 3);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            for (int j = 0; j < width; j++) {
                argb[i] = 0xFF000000;

                b = (data[idx0++] & 0xFF);
                argb[i] |= (b << 16);
                b = (data[idx1++] & 0xFF);
                argb[i] |= (b << 8);
                b = (data[idx2++] & 0xFF);
                argb[i] |= b;
            }
        }
    }

    private static void RGBA_BIPtoBitmap(int[] argb, byte[] data, int width,
            int height) {
        final int numPixels = (width * height);
        int b;
        int idx = 0;
        for (int i = 0; i < numPixels; i++) {
            b = (data[idx++] & 0xFF);
            argb[i] = (b << 16);
            b = (data[idx++] & 0xFF);
            argb[i] |= (b << 8);
            b = (data[idx++] & 0xFF);
            argb[i] |= b;
            b = (data[idx++] & 0xFF);
            argb[i] |= (b << 24);
        }
    }

    private static void RGBA_BSQtoBitmap(int[] argb, byte[] data, int width,
            int height) {
        final int numPixels = (width * height);
        int b;
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int idx3 = idx2 + (width * height);
        for (int i = 0; i < numPixels; i++) {
            b = (data[idx0++] & 0xFF);
            argb[i] = (b << 16);
            b = (data[idx1++] & 0xFF);
            argb[i] |= (b << 8);
            b = (data[idx2++] & 0xFF);
            argb[i] |= b;
            b = (data[idx3++] & 0xFF);
            argb[i] |= (b << 24);
        }
    }

    private static void RGBA_BILtoBitmap(int[] argb, byte[] data, int width,
            int height) {
        int b;
        int idx0;
        int idx1;
        int idx2;
        int idx3;
        for (int i = 0; i < height; i++) {
            idx0 = (i * width * 3);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            idx3 = idx2 + width;
            for (int j = 0; j < width; j++) {
                b = (data[idx0++] & 0xFF);
                argb[i] = (b << 16);
                b = (data[idx1++] & 0xFF);
                argb[i] |= (b << 8);
                b = (data[idx2++] & 0xFF);
                argb[i] |= b;
                b = (data[idx3++] & 0xFF);
                argb[i] |= (b << 24);
            }
        }
    }

    private static void ARGB_BIPtoBitmap(int[] argb, byte[] data, int width,
            int height) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.asIntBuffer().get(argb);
    }

    private static void ARGB_BSQtoBitmap(int[] argb, byte[] data, int width,
            int height) {
        final int numPixels = (width * height);
        int b;
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int idx3 = idx2 + (width * height);
        for (int i = 0; i < numPixels; i++) {
            b = (data[idx0++] & 0xFF);
            argb[i] = (b << 24);
            b = (data[idx1++] & 0xFF);
            argb[i] |= (b << 16);
            b = (data[idx2++] & 0xFF);
            argb[i] |= (b << 8);
            b = (data[idx3++] & 0xFF);
            argb[i] |= b;
        }
    }

    private static void ARGB_BILtoBitmap(int[] argb, byte[] data, int width,
            int height) {
        int b;
        int idx0;
        int idx1;
        int idx2;
        int idx3;
        for (int i = 0; i < height; i++) {
            idx0 = (i * width * 3);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            idx3 = idx2 + width;
            for (int j = 0; j < width; j++) {
                b = (data[idx0++] & 0xFF);
                argb[i] = (b << 24);
                b = (data[idx1++] & 0xFF);
                argb[i] |= (b << 16);
                b = (data[idx2++] & 0xFF);
                argb[i] |= (b << 8);
                b = (data[idx3++] & 0xFF);
                argb[i] |= b;
            }
        }
    }

    /**************************************************************************/

    public static void getBitmapData(Bitmap bitmap, byte[] data, int width,
            int height, TileReader.Interleave interleave,
            TileReader.Format format) {
        int[] argb = new int[width * height];
        bitmap.getPixels(argb, 0, width, 0, 0, width, height);

        switch (format) {
            case MONOCHROME:
                ARGBtoMono(argb, data, width, height);
                break;
            case MONOCHROME_ALPHA:
                switch (interleave) {
                    case BIP:
                        ARGBtoMonoAlphaBIP(argb, data, width, height);
                        break;
                    case BIL:
                        ARGBtoMonoAlphaBIL(argb, data, width, height);
                        break;
                    case BSQ:
                        ARGBtoMonoAlphaBSQ(argb, data, width, height);
                        break;
                    default:
                        throw new IllegalStateException();
                }
                break;
            case RGB:
                switch (interleave) {
                    case BIP:
                        ARGBtoRGB_BIP(argb, data, width, height);
                        break;
                    case BIL:
                        ARGBtoRGB_BIL(argb, data, width, height);
                        break;
                    case BSQ:
                        ARGBtoRGB_BSQ(argb, data, width, height);
                        break;
                    default:
                        throw new IllegalStateException();
                }
                break;
            case RGBA:
                switch (interleave) {
                    case BIP:
                        ARGBtoRGBA_BIP(argb, data, width, height);
                        break;
                    case BIL:
                        ARGBtoRGBA_BIL(argb, data, width, height);
                        break;
                    case BSQ:
                        ARGBtoRGBA_BSQ(argb, data, width, height);
                        break;
                    default:
                        throw new IllegalStateException();
                }
                break;
            case ARGB:
                switch (interleave) {
                    case BIP:
                        ARGBtoARGB_BIP(argb, data, width, height);
                        break;
                    case BIL:
                        ARGBtoARGB_BIL(argb, data, width, height);
                        break;
                    case BSQ:
                        ARGBtoARGB_BSQ(argb, data, width, height);
                        break;
                    default:
                        throw new IllegalStateException();
                }
                break;
            default:
                throw new IllegalStateException();
        }
    }

    private static void ARGBtoMono(int[] argb, byte[] data, int width,
            int height) {
        final int numPixels = (width * height);
        for (int i = 0; i < numPixels; i++)
            data[i] = (byte) (argb[i] & 0xFF);
    }

    private static void ARGBtoMonoAlphaBIP(int[] argb, byte[] data, int width,
            int height) {
        final int numPixels = (width * height);
        int idx = 0;
        for (int i = 0; i < numPixels; i++) {
            data[idx++] = (byte) (argb[i] & 0xFF);
            data[idx++] = (byte) ((argb[i] >> 24) & 0xFF);
        }
    }

    private static void ARGBtoMonoAlphaBSQ(int[] argb, byte[] data, int width,
            int height) {
        final int numPixels = (width * height);
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        for (int i = 0; i < numPixels; i++) {
            data[idx0++] = (byte) (argb[i] & 0xFF);
            data[idx1++] = (byte) ((argb[i] >> 24) & 0xFF);
        }
    }

    private static void ARGBtoMonoAlphaBIL(int[] argb, byte[] data, int width,
            int height) {
        int idx0;
        int idx1;
        for (int i = 0; i < height; i++) {
            idx0 = (i * width * 2);
            idx1 = idx0 + width;
            for (int j = 0; j < width; j++) {
                data[idx0++] = (byte) (argb[i] & 0xFF);
                data[idx1++] = (byte) ((argb[i] >> 24) & 0xFF);
            }
        }
    }

    private static void ARGBtoRGB_BIP(int[] argb, byte[] data, int width,
            int height) {
        final int numPixels = (width * height);
        int idx = 0;
        for (int i = 0; i < numPixels; i++) {
            data[idx++] = (byte) ((argb[i] >> 16) & 0xFF);
            data[idx++] = (byte) ((argb[i] >> 8) & 0xFF);
            data[idx++] = (byte) (argb[i] & 0xFF);
        }
    }

    private static void ARGBtoRGB_BSQ(int[] argb, byte[] data, int width,
            int height) {
        final int numPixels = (width * height);
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        for (int i = 0; i < numPixels; i++) {
            data[idx0++] = (byte) ((argb[i] >> 16) & 0xFF);
            data[idx1++] = (byte) ((argb[i] >> 8) & 0xFF);
            data[idx2++] = (byte) (argb[i] & 0xFF);
        }
    }

    private static void ARGBtoRGB_BIL(int[] argb, byte[] data, int width,
            int height) {
        int idx0;
        int idx1;
        int idx2;
        for (int i = 0; i < height; i++) {
            idx0 = (i * width * 3);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            for (int j = 0; j < width; j++) {
                data[idx0++] = (byte) ((argb[i] >> 16) & 0xFF);
                data[idx1++] = (byte) ((argb[i] >> 8) & 0xFF);
                data[idx2++] = (byte) (argb[i] & 0xFF);
            }
        }
    }

    private static void ARGBtoRGBA_BIP(int[] argb, byte[] data, int width,
            int height) {
        final int numPixels = (width * height);
        int idx = 0;
        for (int i = 0; i < numPixels; i++) {
            data[idx++] = (byte) ((argb[i] >> 16) & 0xFF);
            data[idx++] = (byte) ((argb[i] >> 8) & 0xFF);
            data[idx++] = (byte) (argb[i] & 0xFF);
            data[idx++] = (byte) ((argb[i] >> 24) & 0xFF);
        }
    }

    private static void ARGBtoRGBA_BSQ(int[] argb, byte[] data, int width,
            int height) {
        final int numPixels = (width * height);
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int idx3 = idx2 + (width * height);
        for (int i = 0; i < numPixels; i++) {
            data[idx0++] = (byte) ((argb[i] >> 16) & 0xFF);
            data[idx1++] = (byte) ((argb[i] >> 8) & 0xFF);
            data[idx2++] = (byte) (argb[i] & 0xFF);
            data[idx3++] = (byte) ((argb[i] >> 24) & 0xFF);
        }
    }

    private static void ARGBtoRGBA_BIL(int[] argb, byte[] data, int width,
            int height) {
        int idx0;
        int idx1;
        int idx2;
        int idx3;
        for (int i = 0; i < height; i++) {
            idx0 = (i * width * 3);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            idx3 = idx2 + width;
            for (int j = 0; j < width; j++) {
                data[idx0++] = (byte) ((argb[i] >> 16) & 0xFF);
                data[idx1++] = (byte) ((argb[i] >> 8) & 0xFF);
                data[idx2++] = (byte) (argb[i] & 0xFF);
                data[idx3++] = (byte) ((argb[i] >> 24) & 0xFF);
            }
        }
    }

    private static void ARGBtoARGB_BIP(int[] argb, byte[] data, int width,
            int height) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.asIntBuffer().put(argb);
    }

    private static void ARGBtoARGB_BSQ(int[] argb, byte[] data, int width,
            int height) {
        final int numPixels = (width * height);
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int idx3 = idx2 + (width * height);
        for (int i = 0; i < numPixels; i++) {
            data[idx0++] = (byte) ((argb[i] >> 24) & 0xFF);
            data[idx1++] = (byte) ((argb[i] >> 16) & 0xFF);
            data[idx2++] = (byte) ((argb[i] >> 8) & 0xFF);
            data[idx3++] = (byte) (argb[i] & 0xFF);
        }
    }

    private static void ARGBtoARGB_BIL(int[] argb, byte[] data, int width,
            int height) {
        int idx0;
        int idx1;
        int idx2;
        int idx3;
        for (int i = 0; i < height; i++) {
            idx0 = (i * width * 3);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            idx3 = idx2 + width;
            for (int j = 0; j < width; j++) {
                data[idx0++] = (byte) ((argb[i] >> 24) & 0xFF);
                data[idx1++] = (byte) ((argb[i] >> 16) & 0xFF);
                data[idx2++] = (byte) ((argb[i] >> 8) & 0xFF);
                data[idx3++] = (byte) (argb[i] & 0xFF);
            }
        }
    }

    /*************************************************************************/

    private static void MonoToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        buffer.put(data, 0, width * height);
    }

    private static void MonoToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx = 0;
        int sidx;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[didx++];
                scanline[sidx++] = (byte) 0xFF;
            }
            buffer.put(scanline);
        }
    }

    private static void MonoToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 3];
        int didx = 0;
        int sidx;
        byte p;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                p = data[didx++];
                scanline[sidx++] = p;
                scanline[sidx++] = p;
                scanline[sidx++] = p;
            }
            buffer.put(scanline);
        }
    }

    private static void MonoToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 4];
        int didx = 0;
        int sidx;
        byte p;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                p = data[didx++];
                scanline[sidx++] = p;
                scanline[sidx++] = p;
                scanline[sidx++] = p;
                scanline[sidx++] = (byte) 0xFF;
            }
            buffer.put(scanline);
        }
    }

    private static void MonoToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx = 0;
        int sidx;
        int p;

        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    p = (data[didx++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((p << 6) | p);
                    scanline[sidx++] = (byte) ((p << 3) | (p >> 2));
                }
                buffer.put(scanline);
            }
        } else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    p = (data[didx++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((p << 3) | (p >> 2));
                    scanline[sidx++] = (byte) ((p << 6) | p);
                }
                buffer.put(scanline);
            }
        }
    }

    private static void MonoToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx = 0;
        int sidx;
        int p;

        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    p = (data[didx++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((p << 6) | (p << 1) | 0x01);
                    scanline[sidx++] = (byte) ((p << 3) | (p >> 2));

                }
                buffer.put(scanline);
            }
        } else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    p = (data[didx++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((p << 3) | (p >> 2));
                    scanline[sidx++] = (byte) ((p << 6) | (p << 1) | 0x01);

                }
                buffer.put(scanline);
            }
        }
    }

    private static void MonoAlphaBIPToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width];
        int idx = 0;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                scanline[j] = data[idx];
                idx += 2;
            }
            buffer.put(scanline);
        }
    }

    private static void MonoAlphaBIPToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        buffer.put(data, 0, width * height * 2);
    }

    private static void MonoAlphaBIPToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 3];
        int didx = 0;
        int sidx;
        byte p;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                p = data[didx];
                didx += 2;
                scanline[sidx++] = p;
                scanline[sidx++] = p;
                scanline[sidx++] = p;
            }
            buffer.put(scanline);
        }
    }

    private static void MonoAlphaBIPToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 4];
        int didx = 0;
        int sidx;
        byte p;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                p = data[didx++];
                scanline[sidx++] = p;
                scanline[sidx++] = p;
                scanline[sidx++] = p;
                scanline[sidx++] = data[didx++];
            }
            buffer.put(scanline);
        }
    }

    private static void MonoAlphaBIPToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx = 0;
        int sidx;
        int p;

        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    p = (data[didx] & 0xFF) >> 3;
                    didx += 2;
                    scanline[sidx++] = (byte) ((p << 6) | p);
                    scanline[sidx++] = (byte) ((p << 3) | (p >> 2));
                }
                buffer.put(scanline);
            }
        } else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    p = (data[didx] >> 3) & 0xFF;
                    didx += 2;
                    scanline[sidx++] = (byte) ((p << 3) | (p >> 2));
                    scanline[sidx++] = (byte) ((p << 6) | p);
                }
                buffer.put(scanline);
            }
        }
    }

    private static void MonoAlphaBIPToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx = 0;
        int sidx;
        int p;
        int alpha;
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    p = (data[didx++] & 0xFF) >> 3;
                    alpha = ((data[didx++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    scanline[sidx++] = (byte) ((p << 6) | (p << 1) | alpha);
                    scanline[sidx++] = (byte) ((p << 3) | (p >> 2));
                }
                buffer.put(scanline);
            }
        } else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    p = (data[didx++] & 0xFF) >> 3;
                    alpha = ((data[didx++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    scanline[sidx++] = (byte) ((p << 3) | (p >> 2));
                    scanline[sidx++] = (byte) ((p << 6) | (p << 1) | alpha);
                }
                buffer.put(scanline);
            }
        }
    }

    private static void MonoAlphaBSQToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        buffer.put(data, 0, width * height);
    }

    private static void MonoAlphaBSQToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx0 = 0;
        int didx1 = (width * height);
        int sidx;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[didx0++];
                scanline[sidx++] = data[didx1++];
                ;
            }
            buffer.put(scanline);
        }
    }

    private static void MonoAlphaBSQToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        MonoToBuffer__GL_RGB__GL_UNSIGNED_BYTE(buffer, data, width, height);
    }

    private static void MonoAlphaBSQToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 3];
        int didx0 = 0;
        int didx1 = (width * height);
        int sidx;
        byte p;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                p = data[didx0++];
                scanline[sidx++] = p;
                scanline[sidx++] = p;
                scanline[sidx++] = p;
                scanline[sidx++] = data[didx1++];
            }
            buffer.put(scanline);
        }
    }

    private static void MonoAlphaBSQToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
            ByteBuffer buffer, byte[] data, int width, int height) {
        MonoToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(buffer, data, width,
                height);
    }

    private static void MonoAlphaBSQToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx0 = 0;
        int didx1 = (width * height);
        int sidx;
        int p;
        int alpha;
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    p = (data[didx0++] & 0xFF) >> 3;
                    alpha = ((data[didx1++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    scanline[sidx++] = (byte) ((p << 6) | (p << 1) | alpha);
                    scanline[sidx++] = (byte) ((p << 3) | (p >> 2));
                }
                buffer.put(scanline);
            }
        } else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    p = (data[didx0++] & 0xFF) >> 3;
                    alpha = ((data[didx1++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    scanline[sidx++] = (byte) ((p << 3) | (p >> 2));
                    scanline[sidx++] = (byte) ((p << 6) | (p << 1) | alpha);
                }
                buffer.put(scanline);
            }
        }
    }

    private static void MonoAlphaBILToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        for (int i = 0; i < height; i++)
            for (int j = 0; j < width; j++)
                buffer.put(data, (i * width) * 2, width);
    }

    private static void MonoAlphaBILToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx0;
        int didx1;
        int sidx;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            didx0 = i * width * 2;
            didx1 = didx0 + width;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[didx0++];
                scanline[sidx++] = data[didx1++];
                ;
            }
            buffer.put(scanline);
        }
    }

    private static void MonoAlphaBILToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 3];
        int didx;
        int sidx;
        byte p;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            didx = i * width * 2;
            for (int j = 0; j < width; j++) {
                p = data[didx++];
                scanline[sidx++] = p;
                scanline[sidx++] = p;
                scanline[sidx++] = p;
            }
            buffer.put(scanline);
        }
    }

    private static void MonoAlphaBILToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 3];
        int didx0 = 0;
        int didx1 = (width * height);
        int sidx;
        byte p;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            didx0 = i * width * 2;
            didx1 = didx0 + width;
            for (int j = 0; j < width; j++) {
                p = data[didx0++];
                scanline[sidx++] = p;
                scanline[sidx++] = p;
                scanline[sidx++] = p;
                scanline[sidx++] = data[didx1++];
            }
            buffer.put(scanline);
        }
    }

    private static void MonoAlphaBILToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx;
        int sidx;
        int p;
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx = i * width * 2;
                for (int j = 0; j < width; j++) {
                    p = (data[didx++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((p << 6) | p);
                    scanline[sidx++] = (byte) ((p << 3) | (p >> 2));
                }
                buffer.put(scanline);
            }
        } else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx = i * width * 2;
                for (int j = 0; j < width; j++) {
                    p = (data[didx++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((p << 3) | (p >> 2));
                    scanline[sidx++] = (byte) ((p << 6) | p);
                }
                buffer.put(scanline);
            }
        }
    }

    private static void MonoAlphaBILToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx0;
        int didx1;
        int sidx;
        int p;
        int alpha;
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * width * 2;
                didx1 = didx0 + width;
                for (int j = 0; j < width; j++) {
                    p = (data[didx0++] & 0xFF) >> 3;
                    alpha = ((data[didx1++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    scanline[sidx++] = (byte) ((p << 6) | (p << 1) | alpha);
                    scanline[sidx++] = (byte) ((p << 3) | (p >> 2));
                }
                buffer.put(scanline);
            }
        } else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * width * 2;
                didx1 = didx0 + width;
                for (int j = 0; j < width; j++) {
                    p = (data[didx0++] & 0xFF) >> 3;
                    alpha = ((data[didx1++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    scanline[sidx++] = (byte) ((p << 3) | (p >> 2));
                    scanline[sidx++] = (byte) ((p << 6) | (p << 1) | alpha);
                }
                buffer.put(scanline);
            }
        }
    }

    private static void RGB_BIPToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width];
        int idx = 0;
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                r = data[idx++] & 0xFF;
                g = data[idx++] & 0xFF;
                b = data[idx++] & 0xFF;
                scanline[j] = (byte) ((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
            }
            buffer.put(scanline);
        }
    }

    private static void RGB_BIPToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx = 0;
        int sidx = 0;
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                r = data[didx++] & 0xFF;
                g = data[didx++] & 0xFF;
                b = data[didx++] & 0xFF;
                scanline[sidx++] = (byte) ((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
                scanline[sidx++] = (byte) 0xFF;
            }
            buffer.put(scanline);
        }
    }

    private static void RGB_BIPToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        buffer.put(data, 0, (width * height * 3));
    }

    private static void RGB_BIPToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 4];
        int didx = 0;
        int sidx = 0;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[didx++];
                scanline[sidx++] = data[didx++];
                scanline[sidx++] = data[didx++];
                scanline[sidx++] = (byte) 0xFF;
            }
            buffer.put(scanline);
        }
    }

    private static void RGB_BIPToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
            ByteBuffer buffer, byte[] data, int width, int height) {
        Log.d(TAG, "RGB_BIPToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5");
        byte[] scanline = new byte[width * 2];
        int didx = 0;
        int sidx;
        int r;
        int g;
        int b;

        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx++] & 0xFF) >> 3;
                    g = (data[didx++] & 0xFF) >> 2;
                    b = (data[didx++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((g << 5) | b);
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 3));
                }
                buffer.put(scanline);
            }
        } else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx++] & 0xFF) >> 3;
                    g = (data[didx++] & 0xFF) >> 2;
                    b = (data[didx++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 3));
                    scanline[sidx++] = (byte) ((g << 5) | b);
                }
                buffer.put(scanline);
            }
        }
    }

    private static void RGB_BIPToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx = 0;
        int sidx;
        int r;
        int g;
        int b;

        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx++] & 0xFF) >> 3;
                    g = (data[didx++] & 0xFF) >> 3;
                    b = (data[didx++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 2));
                    scanline[sidx++] = (byte) ((g << 6) | (b << 1) | 0x01);
                }
                buffer.put(scanline);
            }
        } else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx++] & 0xFF) >> 3;
                    g = (data[didx++] & 0xFF) >> 3;
                    b = (data[didx++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((g << 6) | (b << 1) | 0x01);
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 2));
                }
                buffer.put(scanline);
            }
        }
    }

    private static void RGB_BSQToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width];
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                r = data[idx0++] & 0xFF;
                g = data[idx1++] & 0xFF;
                b = data[idx2++] & 0xFF;
                scanline[j] = (byte) ((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
            }
            buffer.put(scanline);
        }
    }

    private static void RGB_BSQToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int sidx;
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                r = data[idx0++] & 0xFF;
                g = data[idx1++] & 0xFF;
                b = data[idx2++] & 0xFF;
                scanline[sidx++] = (byte) ((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
                scanline[sidx++] = (byte) 0xFF;
            }
            buffer.put(scanline);
        }
    }

    private static void RGB_BSQToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 3];
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int sidx;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[idx0++];
                scanline[sidx++] = data[idx1++];
                scanline[sidx++] = data[idx2++];
            }
            buffer.put(scanline);
        }
    }

    private static void RGB_BSQToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 4];
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int sidx;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[idx0++];
                scanline[sidx++] = data[idx1++];
                scanline[sidx++] = data[idx2++];
                scanline[sidx++] = (byte) 0xFF;
            }
            buffer.put(scanline);
        }
    }

    private static void RGB_BSQToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx0 = 0;
        int didx1 = didx0 + (width * height);
        int didx2 = didx1 + (width * height);
        int sidx;
        int r;
        int g;
        int b;

        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 2;
                    b = (data[didx2++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((g << 5) | b);
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 3));
                }
                buffer.put(scanline);
            }
        } else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 2;
                    b = (data[didx2++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 3));
                    scanline[sidx++] = (byte) ((g << 5) | b);
                }
                buffer.put(scanline);
            }
        }
    }

    private static void RGB_BSQToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx0 = 0;
        int didx1 = didx0 + (width * height);
        int didx2 = didx1 + (width * height);
        int sidx;
        int r;
        int g;
        int b;

        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 3;
                    b = (data[didx2++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((g << 6) | (b << 1) | 0x01);
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 2));
                }
                buffer.put(scanline);
            }
        } else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 3;
                    b = (data[didx2++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 2));
                    scanline[sidx++] = (byte) ((g << 6) | (b << 1) | 0x01);
                }
                buffer.put(scanline);
            }
        }
    }

    private static void RGB_BILToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width];
        int idx0 = 0;
        int idx1;
        int idx2;
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            idx0 = i * (width * 3);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            for (int j = 0; j < width; j++) {
                r = data[idx0++] & 0xFF;
                g = data[idx1++] & 0xFF;
                b = data[idx2++] & 0xFF;
                scanline[j] = (byte) ((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
            }
            buffer.put(scanline);
        }
    }

    private static void RGB_BILToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int idx0 = 0;
        int idx1;
        int idx2;
        int sidx;
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            idx0 = i * (width * 3);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            sidx = 0;
            for (int j = 0; j < width; j++) {
                r = data[idx0++] & 0xFF;
                g = data[idx1++] & 0xFF;
                b = data[idx2++] & 0xFF;
                scanline[sidx++] = (byte) ((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
                scanline[sidx++] = (byte) 0xFF;
            }
            buffer.put(scanline);
        }
    }

    private static void RGB_BILToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width];
        int idx0 = 0;
        int idx1;
        int idx2;
        int sidx;
        for (int i = 0; i < height; i++) {
            idx0 = i * (width * 3);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[idx0++];
                scanline[sidx++] = data[idx1++];
                scanline[sidx++] = data[idx2++];
            }
            buffer.put(scanline);
        }
    }

    private static void RGB_BILToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width];
        int idx0 = 0;
        int idx1;
        int idx2;
        int sidx;
        for (int i = 0; i < height; i++) {
            idx0 = i * (width * 3);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[idx0++];
                scanline[sidx++] = data[idx1++];
                scanline[sidx++] = data[idx2++];
                scanline[sidx++] = (byte) 0xFF;
            }
            buffer.put(scanline);
        }
    }

    private static void RGB_BILToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx0;
        int didx1;
        int didx2;
        int sidx;
        int r;
        int g;
        int b;
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * (width * 3);
                didx1 = didx0 + width;
                didx2 = didx1 + width;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 2;
                    b = (data[didx2++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((g << 5) | b);
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 3));
                }
                buffer.put(scanline);
            }
        } else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * (width * 3);
                didx1 = didx0 + width;
                didx2 = didx1 + width;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 2;
                    b = (data[didx2++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 3));
                    scanline[sidx++] = (byte) ((g << 5) | b);
                }
                buffer.put(scanline);
            }
        }
    }

    private static void RGB_BILToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx0;
        int didx1;
        int didx2;
        int sidx;
        int r;
        int g;
        int b;
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * (width * 3);
                didx1 = didx0 + width;
                didx2 = didx1 + width;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 3;
                    b = (data[didx2++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((g << 6) | (b << 1) | 0x01);
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 2));
                }
                buffer.put(scanline);
            }
        } else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * (width * 3);
                didx1 = didx0 + width;
                didx2 = didx1 + width;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 3;
                    b = (data[didx2++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 2));
                    scanline[sidx++] = (byte) ((g << 6) | (b << 1) | 0x01);
                }
                buffer.put(scanline);
            }
        }
    }

    private static void RGBA_BIPToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width];
        int idx = 0;
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                r = data[idx++] & 0xFF;
                g = data[idx++] & 0xFF;
                b = data[idx++] & 0xFF;
                idx++; // alpha
                scanline[j] = (byte) ((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
            }
            buffer.put(scanline);
        }
    }

    private static void RGBA_BIPToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx = 0;
        int sidx = 0;
        int r;
        int g;
        int b;
        byte a;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                r = data[didx++] & 0xFF;
                g = data[didx++] & 0xFF;
                b = data[didx++] & 0xFF;
                a = data[didx++]; // alpha
                scanline[sidx++] = (byte) ((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
                scanline[sidx++] = a;
            }
            buffer.put(scanline);
        }
    }

    private static void RGBA_BIPToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx = 0;
        int sidx = 0;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[didx++];
                scanline[sidx++] = data[didx++];
                scanline[sidx++] = data[didx++];
                didx++; // alpha
            }
            buffer.put(scanline);
        }
    }

    private static void RGBA_BIPToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        buffer.put(data, 0, width * height * 4);
    }

    private static void RGBA_BIPToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx = 0;
        int sidx;
        int r;
        int g;
        int b;

        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx++] & 0xFF) >> 3;
                    g = (data[didx++] & 0xFF) >> 2;
                    b = (data[didx++] & 0xFF) >> 3;
                    didx++; // alpha
                    scanline[sidx++] = (byte) ((g << 5) | b);
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 3));
                }
                buffer.put(scanline);
            }
        } else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx++] & 0xFF) >> 3;
                    g = (data[didx++] & 0xFF) >> 2;
                    b = (data[didx++] & 0xFF) >> 3;
                    didx++; // alpha
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 3));
                    scanline[sidx++] = (byte) ((g << 5) | b);
                }
                buffer.put(scanline);
            }
        }
    }

    private static void RGBA_BIPToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx = 0;
        int sidx;
        int r;
        int g;
        int b;
        int a;
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx++] & 0xFF) >> 3;
                    g = (data[didx++] & 0xFF) >> 3;
                    b = (data[didx++] & 0xFF) >> 3;
                    a = ((data[didx++] & 0xFF) != 0xFF) ? 0x00 : 0xFF;
                    scanline[sidx++] = (byte) ((g << 6) | (b << 1) | a);
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 2));
                }
                buffer.put(scanline);
            }
        } else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx++] & 0xFF) >> 3;
                    g = (data[didx++] & 0xFF) >> 3;
                    b = (data[didx++] & 0xFF) >> 3;
                    a = ((data[didx++] & 0xFF) != 0xFF) ? 0x00 : 0xFF;
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 2));
                    scanline[sidx++] = (byte) ((g << 6) | (b << 1) | a);
                }
                buffer.put(scanline);
            }
        }
    }

    private static void RGBA_BSQToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        RGB_BSQToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(buffer, data, width,
                height);
    }

    private static void RGBA_BSQToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int idx3 = idx2 + (width * height);
        int sidx;
        int r;
        int g;
        int b;
        byte a;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                r = data[idx0++] & 0xFF;
                g = data[idx1++] & 0xFF;
                b = data[idx2++] & 0xFF;
                a = data[idx3++];
                scanline[sidx++] = (byte) ((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
                scanline[sidx++] = a;
            }
            buffer.put(scanline);
        }
    }

    private static void RGBA_BSQToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        RGB_BSQToBuffer__GL_RGB__GL_UNSIGNED_BYTE(buffer, data, width, height);
    }

    private static void RGBA_BSQToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 4];
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int idx3 = idx2 + (width * height);
        int sidx;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[idx0++];
                scanline[sidx++] = data[idx1++];
                scanline[sidx++] = data[idx2++];
                scanline[sidx++] = data[idx3++];
            }
            buffer.put(scanline);
        }
    }

    private static void RGBA_BSQToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
            ByteBuffer buffer, byte[] data, int width, int height) {
        RGB_BSQToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(buffer, data, width,
                height);
    }

    private static void RGBA_BSQToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx0 = 0;
        int didx1 = didx0 + (width * height);
        int didx2 = didx1 + (width * height);
        int didx3 = didx2 + (width * height);
        int sidx;
        int r;
        int g;
        int b;
        int a;

        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 3;
                    b = (data[didx2++] & 0xFF) >> 3;
                    a = ((data[didx3++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    scanline[sidx++] = (byte) ((g << 6) | (b << 1) | a);
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 2));
                }
                buffer.put(scanline);
            }
        } else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 3;
                    b = (data[didx2++] & 0xFF) >> 3;
                    a = ((data[didx3++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 2));
                    scanline[sidx++] = (byte) ((g << 6) | (b << 1) | a);
                }
                buffer.put(scanline);
            }
        }
    }

    private static void RGBA_BILToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width];
        int idx0 = 0;
        int idx1;
        int idx2;
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            idx0 = i * (width * 4);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            for (int j = 0; j < width; j++) {
                r = data[idx0++] & 0xFF;
                g = data[idx1++] & 0xFF;
                b = data[idx2++] & 0xFF;
                scanline[j] = (byte) ((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
            }
            buffer.put(scanline);
        }
    }

    private static void RGBA_BILToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int idx0 = 0;
        int idx1;
        int idx2;
        int idx3;
        int sidx;
        int r;
        int g;
        int b;
        byte a;
        for (int i = 0; i < height; i++) {
            idx0 = i * (width * 3);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            idx3 = idx2 + width;
            sidx = 0;
            for (int j = 0; j < width; j++) {
                r = data[idx0++] & 0xFF;
                g = data[idx1++] & 0xFF;
                b = data[idx2++] & 0xFF;
                a = data[idx3++];
                scanline[sidx++] = (byte) ((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
                scanline[sidx++] = a;
            }
            buffer.put(scanline);
        }
    }

    private static void RGBA_BILToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 3];
        int idx0;
        int idx1;
        int idx2;
        int sidx;
        for (int i = 0; i < height; i++) {
            idx0 = i * (width * 4);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[idx0++];
                scanline[sidx++] = data[idx1++];
                scanline[sidx++] = data[idx2++];
            }
            buffer.put(scanline);
        }
    }

    private static void RGBA_BILToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 4];
        int idx0;
        int idx1;
        int idx2;
        int idx3;
        int sidx;
        for (int i = 0; i < height; i++) {
            idx0 = i * (width * 3);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            idx3 = idx2 + width;
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[idx0++];
                scanline[sidx++] = data[idx1++];
                scanline[sidx++] = data[idx2++];
                scanline[sidx++] = data[idx3++];
            }
            buffer.put(scanline);
        }
    }

    private static void RGBA_BILToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx0;
        int didx1;
        int didx2;
        int sidx;
        int r;
        int g;
        int b;
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * (width * 4);
                didx1 = didx0 + width;
                didx2 = didx1 + width;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 2;
                    b = (data[didx2++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((g << 5) | b);
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 3));
                }
                buffer.put(scanline);
            }
        } else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * (width * 4);
                didx1 = didx0 + width;
                didx2 = didx1 + width;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 2;
                    b = (data[didx2++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 3));
                    scanline[sidx++] = (byte) ((g << 5) | b);
                }
                buffer.put(scanline);
            }
        }
    }

    private static void RGBA_BILToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx0;
        int didx1;
        int didx2;
        int didx3;
        int sidx;
        int r;
        int g;
        int b;
        int a;
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * (width * 4);
                didx1 = didx0 + width;
                didx2 = didx1 + width;
                didx3 = didx2 + width;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 3;
                    b = (data[didx2++] & 0xFF) >> 3;
                    a = ((data[didx3++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    scanline[sidx++] = (byte) ((g << 6) | (b << 1) | a);
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 2));
                }
                buffer.put(scanline);
            }
        } else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * (width * 4);
                didx1 = didx0 + width;
                didx2 = didx1 + width;
                didx3 = didx2 + width;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 3;
                    b = (data[didx2++] & 0xFF) >> 3;
                    a = ((data[didx3++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 2));
                    scanline[sidx++] = (byte) ((g << 6) | (b << 1) | a);
                }
                buffer.put(scanline);
            }
        }
    }

    private static void ARGB_BIPToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width];
        int idx = 0;
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                idx++; // alpha
                r = data[idx++] & 0xFF;
                g = data[idx++] & 0xFF;
                b = data[idx++] & 0xFF;
                scanline[j] = (byte) ((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
            }
            buffer.put(scanline);
        }
    }

    private static void ARGB_BIPToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx = 0;
        int sidx = 0;
        byte a;
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                a = data[didx++];
                r = data[didx++] & 0xFF;
                g = data[didx++] & 0xFF;
                b = data[didx++] & 0xFF;
                scanline[sidx++] = (byte) ((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
                scanline[sidx++] = a;
            }
            buffer.put(scanline);
        }
    }

    private static void ARGB_BIPToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 3];
        int didx = 0;
        int sidx = 0;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                didx++; // alpha
                scanline[sidx++] = data[didx++];
                scanline[sidx++] = data[didx++];
                scanline[sidx++] = data[didx++];
            }
            buffer.put(scanline);
        }
    }

    private static void ARGB_BIPToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 4];
        int didx = 0;
        int sidx = 0;
        byte a;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                a = data[didx++];
                scanline[sidx++] = data[didx++];
                scanline[sidx++] = data[didx++];
                scanline[sidx++] = data[didx++];
                scanline[sidx++] = a;
            }
            buffer.put(scanline);
        }
    }

    private static void ARGB_BIPToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx = 0;
        int sidx;
        int r;
        int g;
        int b;

        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    didx++; // alpha
                    r = (data[didx++] & 0xFF) >> 3;
                    g = (data[didx++] & 0xFF) >> 2;
                    b = (data[didx++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((g << 5) | b);
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 3));
                }
                buffer.put(scanline);
            }
        } else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    didx++; // alpha
                    r = (data[didx++] & 0xFF) >> 3;
                    g = (data[didx++] & 0xFF) >> 2;
                    b = (data[didx++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 3));
                    scanline[sidx++] = (byte) ((g << 5) | b);
                }
                buffer.put(scanline);
            }
        }
    }

    private static void ARGB_BIPToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx = 0;
        int sidx;
        int r;
        int g;
        int b;
        int a;
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    a = ((data[didx++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    r = (data[didx++] & 0xFF) >> 3;
                    g = (data[didx++] & 0xFF) >> 3;
                    b = (data[didx++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((g << 6) | (b << 1) | a);
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 2));
                }
                buffer.put(scanline);
            }
        } else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    a = ((data[didx++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    r = (data[didx++] & 0xFF) >> 3;
                    g = (data[didx++] & 0xFF) >> 3;
                    b = (data[didx++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 2));
                    scanline[sidx++] = (byte) ((g << 6) | (b << 1) | a);
                }
                buffer.put(scanline);
            }
        }
    }

    private static void ARGB_BSQToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width];
        int idx0 = (width * height);
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                r = data[idx0++] & 0xFF;
                g = data[idx1++] & 0xFF;
                b = data[idx2++] & 0xFF;
                scanline[j] = (byte) ((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
            }
            buffer.put(scanline);
        }
    }

    private static void ARGB_BSQToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int idx0 = (width * height);
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int idx3 = 0;
        int sidx;
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                r = data[idx0++] & 0xFF;
                g = data[idx1++] & 0xFF;
                b = data[idx2++] & 0xFF;
                scanline[sidx++] = (byte) ((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
                scanline[sidx++] = data[idx3++];
            }
            buffer.put(scanline);
        }
    }

    private static void ARGB_BSQToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 3];
        int idx0 = (width * height);
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int sidx;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[idx0++];
                scanline[sidx++] = data[idx1++];
                scanline[sidx++] = data[idx2++];
            }
            buffer.put(scanline);
        }
    }

    private static void ARGB_BSQToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 4];
        int idx0 = (width * height);
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int idx3 = 0;
        int sidx;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[idx0++];
                scanline[sidx++] = data[idx1++];
                scanline[sidx++] = data[idx2++];
                scanline[sidx++] = data[idx3++];
            }
            buffer.put(scanline);
        }
    }

    private static void ARGB_BSQToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx0 = (width * height);
        int didx1 = didx0 + (width * height);
        int didx2 = didx1 + (width * height);
        int sidx;
        int r;
        int g;
        int b;

        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 2;
                    b = (data[didx2++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((g << 5) | b);
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 3));
                }
                buffer.put(scanline);
            }
        } else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 2;
                    b = (data[didx2++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 3));
                    scanline[sidx++] = (byte) ((g << 5) | b);
                }
                buffer.put(scanline);
            }
        }
    }

    private static void ARGB_BSQToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx0 = (width * height);
        int didx1 = didx0 + (width * height);
        int didx2 = didx1 + (width * height);
        int didx3 = 0;
        int sidx;
        int r;
        int g;
        int b;
        int a;

        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 3;
                    b = (data[didx2++] & 0xFF) >> 3;
                    a = ((data[didx3++] & 0xFF) != 0xFF) ? 0x00 : 0xFF;
                    scanline[sidx++] = (byte) ((g << 6) | (b << 1) | a);
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 2));
                }
                buffer.put(scanline);
            }
        } else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 3;
                    b = (data[didx2++] & 0xFF) >> 3;
                    a = ((data[didx3++] & 0xFF) != 0xFF) ? 0x00 : 0xFF;
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 2));
                    scanline[sidx++] = (byte) ((g << 6) | (b << 1) | a);
                }
                buffer.put(scanline);
            }
        }
    }

    private static void ARGB_BILToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width];
        int idx0 = 0;
        int idx1;
        int idx2;
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            idx0 = i * (width * 4) + width;
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            for (int j = 0; j < width; j++) {
                r = data[idx0++] & 0xFF;
                g = data[idx1++] & 0xFF;
                b = data[idx2++] & 0xFF;
                scanline[j] = (byte) ((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
            }
            buffer.put(scanline);
        }
    }

    private static void ARGB_BILToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int idx0 = 0;
        int idx1;
        int idx2;
        int idx3;
        int sidx;
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            idx0 = i * (width * 4) + width;
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            idx3 = i * (width * 4);
            sidx = 0;
            for (int j = 0; j < width; j++) {
                r = data[idx0++] & 0xFF;
                g = data[idx1++] & 0xFF;
                b = data[idx2++] & 0xFF;
                scanline[sidx++] = (byte) ((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
                scanline[sidx++] = data[idx3++];
            }
            buffer.put(scanline);
        }
    }

    private static void ARGB_BILToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width];
        int idx0 = 0;
        int idx1;
        int idx2;
        int sidx;
        for (int i = 0; i < height; i++) {
            idx0 = i * (width * 4) + width;
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[idx0++];
                scanline[sidx++] = data[idx1++];
                scanline[sidx++] = data[idx2++];
            }
            buffer.put(scanline);
        }
    }

    private static void ARGB_BILToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width];
        int idx0 = 0;
        int idx1;
        int idx2;
        int idx3;
        int sidx;
        for (int i = 0; i < height; i++) {
            idx0 = i * (width * 4) + width;
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            idx3 = i * (width * 4);
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[idx0++];
                scanline[sidx++] = data[idx1++];
                scanline[sidx++] = data[idx2++];
                scanline[sidx++] = data[idx3++];
            }
            buffer.put(scanline);
        }
    }

    private static void ARGB_BILToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx0;
        int didx1;
        int didx2;
        int sidx;
        int r;
        int g;
        int b;
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * (width * 4) + width;
                didx1 = didx0 + width;
                didx2 = didx1 + width;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 2;
                    b = (data[didx2++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((g << 5) | b);
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 3));
                }
                buffer.put(scanline);
            }
        } else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * (width * 4) + width;
                didx1 = didx0 + width;
                didx2 = didx1 + width;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 2;
                    b = (data[didx2++] & 0xFF) >> 3;
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 3));
                    scanline[sidx++] = (byte) ((g << 5) | b);
                }
                buffer.put(scanline);
            }
        }
    }

    private static void ARGB_BILToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
            ByteBuffer buffer, byte[] data, int width, int height) {
        byte[] scanline = new byte[width * 2];
        int didx0;
        int didx1;
        int didx2;
        int didx3;
        int sidx;
        int r;
        int g;
        int b;
        int a;
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * (width * 4) + width;
                didx1 = didx0 + width;
                didx2 = didx1 + width;
                didx3 = i * (width * 4);
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 3;
                    b = (data[didx2++] & 0xFF) >> 3;
                    a = ((data[didx3++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    scanline[sidx++] = (byte) ((g << 6) | (b << 1) | a);
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 2));
                }
                buffer.put(scanline);
            }
        } else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * (width * 4) + width;
                didx1 = didx0 + width;
                didx2 = didx1 + width;
                didx3 = i * (width * 4);
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 3;
                    b = (data[didx2++] & 0xFF) >> 3;
                    a = ((data[didx3++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    scanline[sidx++] = (byte) ((r << 3) | (g >> 2));
                    scanline[sidx++] = (byte) ((g << 6) | (b << 1) | a);
                }
                buffer.put(scanline);
            }
        }
    }
}
