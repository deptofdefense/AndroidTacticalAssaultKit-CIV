
package com.atakmap.map.layer.raster;

import java.io.File;

import com.atakmap.coremap.locale.LocaleUtil;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.raster.pfps.PfpsMapTypeFrame;

/**
 * Attempts to determine a given File objects imagery file type. When reasonable logic/libraries
 * specific to each type are used to analyse objects. Otherwise the filename suffix is the default
 * in determining type. Notes - Suffix processing assumes the common imagery zip file naming
 * convention of name+type+zip; "NY_MOSAIC.sid.zip" - MIME types are currently not used but left as
 * a hook; incomplete.
 */
abstract class ImageryFileTypeBase {

    public static final String TAG = "ImageryFileType";

    public static final int GeoTiff = 1;
    public static final int MrSid = 2;
    public static final int JP2 = 3;
    public static final int DTED = 4;
    public static final int ECW = 5;

    public static final int KML = 6;
    public static final int KMZ = 7;

    public static final int GDAL = 8;


    public static final int RPF = 11;
    public static final int GPKG = 12;
    public static final int MBTILES = 13;
    public static final int MOMAP = 14;

    public static final int PDF = 15;

    // order types for best performance; by placing the quick checks first; see getFileType(File)
    private static final AbstractFileType[] fileTypes = new AbstractFileType[] {
            // simple suffix checks
            new MomapFileType(),
            new MbTilesFileType(),
            new GpkgFileType(),
            new GeoTiffFileType(),
            new MrSidFileType(),
            new JP2FileType(),
            new DTEDFileType(),
            new ECWFileType(),
            new KMLFileType(),
            new KMZFileType(),

            new RPFFileType(),
            new GDALFileType(),
            new PDFFileType(),
    };

    public static final AbstractFileType[] getFileTypes() {
        return fileTypes;
    }

    /**
     * Attempts to determine the file type otherwise returns null;
     * 
     * @param source a File object
     */
    public static AbstractFileType getFileType(File source) {
        for (AbstractFileType fileType : fileTypes) {
            if (fileType.isInstance(source)) {
                return fileType;
            }
        }
        return null;
    }

    public static String getDescription(int fileTypeID) {
        for (AbstractFileType fileType : fileTypes) {
            if (fileType.getID() == fileTypeID) {
                return fileType.getDescription();
            }
        }
        return null;
    }

    /**************************************************************************/

    /**
     * Unsupported Operation; Results may be limited.
     */
    public static String[] getMimeTypes(int fileTypeID) {
        for (AbstractFileType fileType : fileTypes) {
            if (fileType.getID() == fileTypeID) {
                return fileType.getMimeTypes();
            }
        }
        return null;
    }

    /**
     * Unsupported Operation; Results may be limited.
     */
    public static String[] getSuffixes(int fileTypeID) {
        for (AbstractFileType fileType : fileTypes) {
            if (fileType.getID() == fileTypeID) {
                return fileType.getSuffixes();
            }
        }
        return null;
    }

    /**************************************************************************/

    public static abstract class AbstractFileType {
        protected String[] mimeTypes = null;
        protected String[] suffixes = null;

        public abstract int getID();

        public abstract String getDescription();

        /**
         * Provides path relative the "atak" data directory or null if its not that simple
         */
        public abstract String getPath(File file);

        /*
         * Subclasses should try to override isInstance method. This simple default method performs
         * weak validity tests; low confidence. Some imagery can NOT be identified by a small set of
         * suffixes or mime types; RPF.
         */
        public boolean isInstance(File source) {
            return validityFromSuffixes(source.getName());
        }

        public String[] getMimeTypes() {
            if (mimeTypes != null) {
                String[] copy = new String[mimeTypes.length];
                System.arraycopy(mimeTypes, 0, copy, 0, mimeTypes.length);
                return copy;
            } else {
                return null;
            }
        }

        public String[] getSuffixes() {
            if (suffixes != null) {
                String[] copy = new String[suffixes.length];
                System.arraycopy(suffixes, 0, copy, 0, suffixes.length);
                return copy;
            } else {
                return null;
            }
        }

        // helper; also default validity test; low confidence
        protected boolean validityFromSuffixes(String filename) {
            if (suffixes != null) {
                // XXX ZIP files - Suffix processing assumes a zip file naming convention of
                // name+type+zip; "NY_MOSAIC_sid.zip"
                for (String suffix : suffixes) {
                    if (filename.toLowerCase(LocaleUtil.getCurrent()).endsWith(suffix.toLowerCase(LocaleUtil.getCurrent()))) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**************************************************************************/

    private static class RPFFileType extends AbstractFileType {
        private GeoPoint[] corners = new GeoPoint[] {
                GeoPoint.createMutable(), GeoPoint.createMutable(), GeoPoint.createMutable(),
                GeoPoint.createMutable()
        };

        @Override
        public int getID() {
            return RPF;
        }

        @Override
        public String getDescription() {
            return "RPF";
        }

        @Override
        public boolean isInstance(File source) {
            corners[0].set(Double.NaN, Double.NaN);
            corners[1].set(Double.NaN, Double.NaN);
            corners[2].set(Double.NaN, Double.NaN);
            corners[3].set(Double.NaN, Double.NaN);

            return PfpsMapTypeFrame.coverageFromFilename(source, corners[0], corners[1],
                    corners[2], corners[3]);
        }

        @Override
        public String getPath(File file) {
            // The root export directory in FalconView is placed in /atak/pfps.
            // This assumes that the folder structure dictated by the FalconView
            // Map Data Manager is in tact

            // Instead of ATAK import code, RPF directory structure should be built
            // out on an SD card via the Falconview Map data manager
            return null;
        }
    }

    private static class GeoTiffFileType extends AbstractFileType {

        /**
         * Max size is 10 MB
         */
        private static final long MAX_GRG_SIZE = 10 * 1024 * 1024;

        public GeoTiffFileType() {
            mimeTypes = new String[] {
                    "image/tif", "image/tiff", "image/geotiff", "image/x-tiff"
            };
            suffixes = new String[] {
                    "tif", "tiff", "gtif", "tif.zip", "tiff.zip", "gtif.zip"
            };
        }

        @Override
        public int getID() {
            return GeoTiff;
        }

        @Override
        public String getDescription() {
            return "GeoTiff";
        }

        /**
         * Small GeoTIFF files are assumed to be GRGs, larger files are assumed to be "native"
         * imagery
         * XXX - or instead let the user choose to import it as a GRG or native
         */
        @Override
        public String getPath(File file) {
            if (file == null || !IOProviderFactory.exists(file))
                return null;

            /*if (IOProviderFactory.length(file) <= MAX_GRG_SIZE)
                return "grg";
            else
                return "native";*/
            return "grg";
        }
    }

    // use the existing template for adding these file types.
    private static class GpkgFileType extends AbstractFileType {
        public GpkgFileType() {
            mimeTypes = new String[] {
                    "image/gpkg"
            };
            suffixes = new String[] {
                    "gpkg"
            };
        }

        @Override
        public int getID() {
            return GPKG;
        }

        @Override
        public String getDescription() {
            return "gpkg";
        }

        @Override
        public String getPath(File file) {
            return "gpkg";
        }
    }

    // use the existing template for adding these file types.
    private static class MbTilesFileType extends AbstractFileType {
        public MbTilesFileType() {
            mimeTypes = new String[] {
                    "image/mbtiles"
            };
            suffixes = new String[] {
                    "mbtiles"
            };
        }

        @Override
        public int getID() {
            return MBTILES;
        }

        @Override
        public String getDescription() {
            return "mbtiles";
        }

        @Override
        public String getPath(File file) {
            return "mbtiles";
        }
    }

    // use the existing template for adding these file types.
    private static class MomapFileType extends AbstractFileType {
        public MomapFileType() {
            mimeTypes = new String[] {
                    "image/momap"
            };
            suffixes = new String[] {
                    "momap"
            };
        }

        @Override
        public int getID() {
            return MOMAP;
        }

        @Override
        public String getDescription() {
            return "momap";
        }

        @Override
        public String getPath(File file) {
            return "momap";
        }
    }

    private static class MrSidFileType extends AbstractFileType {
        public MrSidFileType() {
            mimeTypes = new String[] {
                    "image/x-mrsid", "image/x-mrsid-image"
            };
            suffixes = new String[] {
                    "sid", "sid.zip"
            };
        }

        @Override
        public int getID() {
            return MrSid;
        }

        @Override
        public String getDescription() {
            return "MrSid";
        }

        @Override
        public String getPath(File file) {
            return "mrsid";
        }
    }

    private static class JP2FileType extends AbstractFileType {
        public JP2FileType() {
            mimeTypes = new String[] {
                    "image/jp2", "image/jpeg2000", "image/jpeg2000-image", "image/x-jpeg2000-image"
            };
            suffixes = new String[] {
                    "jp2", "j2k", "jp2.zip", "j2k.zip"
            };
        }

        @Override
        public int getID() {
            return JP2;
        }

        @Override
        public String getDescription() {
            return "JP2";
        }

        @Override
        public String getPath(File file) {
            return "native";
        }
    }

    private static class PDFFileType extends AbstractFileType {
        public PDFFileType() {
            mimeTypes = new String[] {
                    "application/pdf"
            };
            suffixes = new String[] {
                    "pdf"
            };
        }

        @Override
        public int getID() {
            return PDF;
        }

        @Override
        public String getDescription() {
            return "PDF";
        }

        @Override
        public String getPath(File file) {
            return "grg";
        }
    }

    private static class DTEDFileType extends AbstractFileType {
        public DTEDFileType() {
            mimeTypes = new String[] {
                    "image/x-dted"
            };
            suffixes = new String[] {
                    "dt0", "dt1", "dt2", "dt3"
            };
        }

        @Override
        public int getID() {
            return DTED;
        }

        @Override
        public String getDescription() {
            return "DTED";
        }

        @Override
        public String getPath(File file) {
            return "DTED";
        }
    }

    private static class GDALFileType extends AbstractFileType {
        public GDALFileType() {
            mimeTypes = new String[] {
                    "image/x-nitf"
            };
            suffixes = new String[] {
                    "ntf", "nitf", "nsf", "ntf.zip", "nitf.zip", "nsf.zip"
            };
        }

        @Override
        public int getID() {
            return GDAL;
        }

        @Override
        public String getDescription() {
            return "GDAL";
        }

        @Override
        public String getPath(File file) {
            return "native";
        }
    }


    private static class KMLFileType extends AbstractFileType {
        public KMLFileType() {
            mimeTypes = new String[] {
                    "application/vnd.google-earth.kml+xml"
            };
            suffixes = new String[] {
                    "kml", "kml.zip"
            };
        }

        @Override
        public int getID() {
            return KML;
        }

        @Override
        public String getDescription() {
            return "KML";
        }

        @Override
        public String getPath(File file) {
            return FileSystemUtils.OVERLAYS_DIRECTORY;
        }
    }

    private static class KMZFileType extends AbstractFileType {
        public KMZFileType() {
            mimeTypes = new String[] {
                    "application/vnd.google-earth.kmz"
            };
            suffixes = new String[] {
                    "kmz", "kmz.zip"
            };
        }

        @Override
        public int getID() {
            return KMZ;
        }

        @Override
        public String getDescription() {
            return "KMZ";
        }

        @Override
        public String getPath(File file) {
            // Note, could be a GRG... for now user must manually place it in GRG folder
            return FileSystemUtils.OVERLAYS_DIRECTORY;
        }
    }

    private static class ECWFileType extends AbstractFileType {
        public ECWFileType() {
            mimeTypes = new String[] {
                    "image/x-imagewebserver-ecw"
            };
            suffixes = new String[] {
                    "ecw", "ecw.zip"
            };
        }

        @Override
        public int getID() {
            return ECW;
        }

        @Override
        public String getDescription() {
            return "ECW";
        }

        @Override
        public String getPath(File file) {
            return "native";
        }
    }
}
