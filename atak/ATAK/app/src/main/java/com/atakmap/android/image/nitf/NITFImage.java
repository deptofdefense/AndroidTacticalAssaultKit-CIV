
package com.atakmap.android.image.nitf;

import com.atakmap.map.layer.raster.gdal.GdalTileReader;
import com.atakmap.map.layer.raster.tilereader.TileReader;

import org.gdal.gdal.Band;
import org.gdal.gdal.ColorTable;
import org.gdal.gdal.Dataset;
import org.gdal.gdalconst.gdalconst;

public class NITFImage {

    /**************************************************************************/

    private static final TileReader.Format paletteRgbaFormat = TileReader.Format.ARGB;

    protected final Dataset nitfDataset;

    public final int width;
    public final int height;

    protected final TileReader.Format format;
    protected final TileReader.Interleave interleave;

    protected final int[] bandRequest;

    protected ColorTableInfo colorTable;

    protected int internalPixelSize;

    /**********************************************************/

    public NITFImage(Dataset dataset) {
        nitfDataset = dataset;

        width = nitfDataset.GetRasterXSize();
        height = nitfDataset.getRasterYSize();

        final int numBands = nitfDataset.GetRasterCount();

        int alphaBand = 0;
        for (int i = 1; i < nitfDataset.GetRasterCount(); i++) {
            if (nitfDataset.GetRasterBand(i)
                    .GetColorInterpretation() == gdalconst.GCI_AlphaBand) {
                alphaBand = i;
                break;
            }
        }

        if ((alphaBand == 0 && numBands >= 3)
                || (alphaBand != 0 && numBands > 3)) {
            if (alphaBand != 0) {
                bandRequest = new int[] {
                        0, 0, 0, alphaBand
                };
                format = TileReader.Format.RGBA;
            } else {
                bandRequest = new int[] {
                        0, 0, 0
                };
                format = TileReader.Format.RGB;
            }

            int gci;
            for (int i = 1; i <= numBands; i++) {
                gci = nitfDataset.GetRasterBand(i).GetColorInterpretation();
                if (gci == gdalconst.GCI_RedBand)
                    bandRequest[0] = i;
                else if (gci == gdalconst.GCI_GreenBand)
                    bandRequest[1] = i;
                else if (gci == gdalconst.GCI_BlueBand)
                    bandRequest[2] = i;
            }

            // XXX - trying to fill in other bands, data may not always be RGB
            // so this needs to be smarter
            for (int i = 0; i < bandRequest.length; i++) {
                if (bandRequest[i] == 0) {
                    boolean inUse;
                    for (int j = 1; j <= numBands; j++) {
                        inUse = false;
                        for (int aBandRequest : bandRequest)
                            inUse |= (aBandRequest == j);
                        if (!inUse) {
                            bandRequest[i] = j;
                            break;
                        }
                    }
                }
            }
        } else if (alphaBand != 0) {
            format = TileReader.Format.MONOCHROME_ALPHA;
            for (int i = 1; i <= numBands; i++) {
                if (nitfDataset.GetRasterBand(i)
                        .GetColorInterpretation() != gdalconst.GCI_AlphaBand) {
                    alphaBand = i;
                    break;
                }
            }
            bandRequest = new int[] {
                    0, alphaBand
            };
        } else if (nitfDataset.GetRasterBand(1)
                .GetColorInterpretation() == gdalconst.GCI_PaletteIndex) {
            Band b = nitfDataset.GetRasterBand(1);
            ColorTable ct = b.GetColorTable();
            bandRequest = new int[] {
                    1
            };

            if (ct != null
                    && ct.GetPaletteInterpretation() == gdalconst.GPI_RGB) {
                colorTable = new ColorTableInfo(getPalette(ct));
                format = this.colorTable.format;
                internalPixelSize = 1;
            } else {
                format = TileReader.Format.MONOCHROME;
            }
        } else {
            format = TileReader.Format.MONOCHROME;
            bandRequest = new int[] {
                    1
            };
        }

        interleave = getInterleave(nitfDataset, format, (colorTable != null));

        if (internalPixelSize == 0)
            internalPixelSize = getPixelSize(this.format);
    }

    public byte[] getImage(GdalTileReader.ReaderImpl reader) {
        return getTile(reader, 0, 0, width, height);
    }

    public byte[] getTile(GdalTileReader.ReaderImpl reader,
            int x, int y, int width, int height) {
        this.nitfDataset.ClearInterrupt();
        byte[] data = new byte[width * height * getPixelSize(this.format)];
        final int success = reader.read(x, y, width, height, width, height,
                data);
        if (success == gdalconst.CE_None) {
            // expand lookup table values
            if (this.colorTable != null
                    && !(this.colorTable.identity
                            && this.format == TileReader.Format.MONOCHROME)) {
                if (this.format == TileReader.Format.MONOCHROME) {
                    for (int i = (width * height) - 1; i >= 0; i--)
                        data[i] = (byte) (this.colorTable.lut[data[i] & 0xFF]
                                & 0xFF);
                } else if (this.format == TileReader.Format.MONOCHROME_ALPHA
                        && this.colorTable.transparentPixel != -1) {
                    int expandedIdx = ((width * height)
                            * getPixelSize(this.format)) - 1;
                    int p;
                    for (int i = (width * height) - 1; i >= 0; i--) {
                        p = (this.colorTable.lut[data[i] & 0xFF] & 0xFF);

                        data[expandedIdx--] = (p == this.colorTable.transparentPixel)
                                ? 0x00
                                : (byte) 0xFF;
                        data[expandedIdx--] = (byte) p;
                    }
                } else {
                    int expandedIdx = ((width * height)
                            * getPixelSize(this.format)) - 1;
                    int p;
                    final int[] shifts;
                    if (this.format == TileReader.Format.RGBA)
                        shifts = new int[] {
                                24, 0, 8, 16
                        };
                    else if (this.format == TileReader.Format.ARGB)
                        shifts = new int[] {
                                0, 8, 16, 24
                        };
                    else if (this.format == TileReader.Format.MONOCHROME_ALPHA)
                        shifts = new int[] {
                                24, 0
                        };
                    else if (this.format == TileReader.Format.RGB)
                        shifts = new int[] {
                                0, 8, 16
                        };
                    else
                        throw new IllegalStateException("Bad format: "
                                + this.format);
                    final int numShifts = shifts.length;
                    for (int i = (width * height) - 1; i >= 0; i--) {
                        p = this.colorTable.lut[(data[i] & 0xFF)];
                        for (int shift : shifts)
                            data[expandedIdx--] = (byte) ((p >> shift) & 0xFF);
                    }
                }
            }
        }
        return data;
    }

    public TileReader.Format getFormat() {
        return this.format;
    }

    public TileReader.Interleave getInterleave() {
        return this.interleave;
    }

    private static TileReader.Interleave getInterleave(Dataset dataset,
            TileReader.Format format, boolean hasColorTable) {
        if (hasColorTable) {
            return TileReader.Interleave.BIP;
        } else if (dataset.GetRasterCount() > 1) {
            String interleave = dataset.GetMetadataItem("INTERLEAVE",
                    "IMAGE_STRUCTURE");
            if (interleave != null) {
                switch (interleave) {
                    case "PIXEL":
                        return TileReader.Interleave.BIP;
                    case "BAND":
                        return TileReader.Interleave.BSQ;
                    case "LINE":
                        return TileReader.Interleave.BIL;
                }
            }
        }
        return TileReader.Interleave.BSQ;
    }

    private static int[] getPalette(ColorTable colorTable) {
        int[] retval = new int[colorTable.GetCount()];
        for (int i = 0; i < retval.length; i++)
            retval[i] = colorTable.GetColorEntry(i);
        return retval;
    }

    /**
     * Returns the pixel size of the specified format, in bytes.
     *
     * @param format    The pixel format
     *
     * @return  The pixel size of the specified format, in bytes.
     */
    public static int getPixelSize(TileReader.Format format) {
        switch (format) {
            case MONOCHROME:
                return 1;
            case MONOCHROME_ALPHA:
                return 2;
            case RGB:
                return 3;
            case ARGB:
            case RGBA:
                return 4;
            default:
                throw new IllegalStateException();
        }
    }

    /**************************************************************************/

    private static class ColorTableInfo {
        public int[] lut;
        public int transparentPixel;
        public TileReader.Format format;
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
                    } else if (this.transparentPixel != -1
                            && this.transparentPixel != r) {
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
                    this.format = TileReader.Format.RGB;
            } else {
                if (alpha)
                    this.format = TileReader.Format.MONOCHROME_ALPHA;
                else
                    this.format = TileReader.Format.MONOCHROME;
            }
        }
    }

    /**************************************************************************/
}
