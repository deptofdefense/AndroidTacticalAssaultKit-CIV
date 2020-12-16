/*
 * PFPSUtils.java
 *
 * Created on June 17, 2013, 1:48 PM
 */

package com.atakmap.map.layer.raster.pfps;

import android.database.*;
import android.util.SparseArray;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.layer.raster.gdal.GdalDatasetProjection2;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabaseBuilder2;
import com.atakmap.map.layer.raster.mosaic.MosaicUtils;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.io.ZipVirtualFile;
import com.atakmap.math.PointD;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.locale.LocaleUtil;
import org.gdal.gdal.*;

import java.io.*;
import java.net.URI;
import java.nio.*;
import java.util.*;

/**
 * @author Developer
 */
public class PfpsUtils {

    public static final String TAG = "PfpsUtils";

    private final static Set<PfpsMapType> RPF_MAP_TYPES = new LinkedHashSet<PfpsMapType>();
    static {
        RPF_MAP_TYPES.add(new PfpsMapType("GN", 5000000.0, PfpsMapType.SCALE_UNIT_SCALE, "cgnc",
                "CADRG", "GNC", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("JN", 2000000.0, PfpsMapType.SCALE_UNIT_SCALE, "cjnc",
                "CADRG", "JNC", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("ON", 1000000.0, PfpsMapType.SCALE_UNIT_SCALE, "conc",
                "CADRG", "ONC", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("LF", 500000.0, PfpsMapType.SCALE_UNIT_SCALE, "clfc",
                "CADRG", "LFC Day", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("TP", 500000.0, PfpsMapType.SCALE_UNIT_SCALE, "ctpc",
                "CADRG", "TPC", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("TF", 250000.0, PfpsMapType.SCALE_UNIT_SCALE, "ctfc",
                "CADRG", "TFC", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("JA", 250000.0, PfpsMapType.SCALE_UNIT_SCALE, "cjga",
                "CADRG", "JOG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("TC", 100000.0, PfpsMapType.SCALE_UNIT_SCALE, "ctlm100",
                "CADRG", "TLM", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("TL", 50000.0, PfpsMapType.SCALE_UNIT_SCALE, "ctlm50",
                "CADRG", "TLM", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("I1", 10.0, PfpsMapType.SCALE_UNIT_METER, "cib10", "CIB",
                null, "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("I2", 5.0, PfpsMapType.SCALE_UNIT_METER, "cib5", "CIB",
                null, "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("I4", 1.0, PfpsMapType.SCALE_UNIT_METER, "cib1", "CIB",
                null, "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("CA", 15000.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg15",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("MM", 50000.0, PfpsMapType.SCALE_UNIT_SCALE, "mm50",
                "CADRG", "MM", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("MM", 100000.0, PfpsMapType.SCALE_UNIT_SCALE, "mm100",
                "CADRG", "MM", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("MM", 250000.0, PfpsMapType.SCALE_UNIT_SCALE, "mm250",
                "CADRG", "MM", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("MM", 25000.0, PfpsMapType.SCALE_UNIT_SCALE, "mm25",
                "CADRG", "MM", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("CT", 36000.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg36",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("CS", 35000.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg35",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("CR", 26000.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg26",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("CQ", 25000.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg25",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("CP", 23000.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg23",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("CN", 22000.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg22",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("CL", 21120.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg21120",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("CK", 21000.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg21",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("CJ", 20000.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg20",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("CH", 18000.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg18",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("CF", 17500.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg17500",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("CE", 17000.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg17",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("CD", 16666.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg16666",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("CC", 16000.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg16",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("CB", 15500.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg15500",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("C9", 14700.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg14700",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("C8", 14000.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg14",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("C7", 12800.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg12800",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("C6", 12500.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg12500",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("C5", 12000.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg12",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("C4", 11800.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg11800",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("C3", 11000.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg11",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("C2", 10560.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg10560",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("C1", 10000.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg10",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("MM", 200000.0, PfpsMapType.SCALE_UNIT_SCALE, "mm200",
                "CADRG", "MM", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("MM", 24000.0, PfpsMapType.SCALE_UNIT_SCALE, "mm24",
                "CADRG", "MM", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("TN", 250000.0, PfpsMapType.SCALE_UNIT_SCALE, "ctfn",
                "CADRG", "TFC Night", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("LN", 500000.0, PfpsMapType.SCALE_UNIT_SCALE, "clfn",
                "CADRG", "LFC Night", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("SA", 500000.0, PfpsMapType.SCALE_UNIT_SCALE, "usa-sec",
                "CADRG", "USA Sectional", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("VT", 250000.0, PfpsMapType.SCALE_UNIT_SCALE, "vfr",
                "CADRG", "VFR Terminal Area Chart", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("JO", 250000.0, PfpsMapType.SCALE_UNIT_SCALE, "opg",
                "CADRG", "Operational Planning Graph", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("JG", 250000.0, PfpsMapType.SCALE_UNIT_SCALE, "jogg",
                "CADRG", "JOG-G", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("JR", 250000.0, PfpsMapType.SCALE_UNIT_SCALE, "jogr",
                "CADRG", "JOG-R", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("CG", 1000000.0, PfpsMapType.SCALE_UNIT_SCALE, "ccg1M",
                "CADRG", "CG", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("VH", 125000.0, PfpsMapType.SCALE_UNIT_SCALE, "chrc125",
                "CADRG", "Helicopter Route Chart", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("OW", 1000000.0, PfpsMapType.SCALE_UNIT_SCALE, "chfc1M",
                "CADRG", "High Flying Chart", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("MI", 50000.0, PfpsMapType.SCALE_UNIT_SCALE, "mim50",
                "CADRG", "Military Installation Map", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("OH", 1000000.0, PfpsMapType.SCALE_UNIT_SCALE, "cvfr1M",
                "CADRG", "VFR Helicopter Route Chart", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("UL", 50000.0, PfpsMapType.SCALE_UNIT_SCALE, "tlm50_o",
                "CADRG", "TLM - Other", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("TT", 25000.0, PfpsMapType.SCALE_UNIT_SCALE, "tlm25",
                "CADRG", "TLM", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("TQ", 24000.0, PfpsMapType.SCALE_UNIT_SCALE, "tlm24",
                "CADRG", "TLM", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("TR", 200000.0, PfpsMapType.SCALE_UNIT_SCALE, "tlm200",
                "CADRG", "TLM", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("MM", 145826.0, PfpsMapType.SCALE_UNIT_SCALE, "mm145",
                "CADRG", "MM", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("MM", 500000.0, PfpsMapType.SCALE_UNIT_SCALE, "mm500",
                "CADRG", "MM", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("MM", 1000000.0, PfpsMapType.SCALE_UNIT_SCALE, "mm1m",
                "CADRG", "MM", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("CM", 10000.0, PfpsMapType.SCALE_UNIT_SCALE, "cc10",
                "CADRG", "Combat Chart", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("CM", 25000.0, PfpsMapType.SCALE_UNIT_SCALE, "cc25",
                "CADRG", "Combat Chart", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("CM", 50000.0, PfpsMapType.SCALE_UNIT_SCALE, "cc50",
                "CADRG", "Combat Chart", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("CM", 100000.0, PfpsMapType.SCALE_UNIT_SCALE, "cc100",
                "CADRG", "Combat Chart", "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("I3", 2.0, PfpsMapType.SCALE_UNIT_METER, "cib2", "CIB",
                null, "Raster"));
        RPF_MAP_TYPES.add(new PfpsMapType("I5", 0.5, PfpsMapType.SCALE_UNIT_METER, "cib05", "CIB",
                null, "Raster"));
    }

    private final static Map<String, PfpsMapType> RPF_DIRECTORY_NAMES = new HashMap<String, PfpsMapType>();
    private final static Map<String, PfpsMapType> RPF_FILE_EXTENSIONS = new HashMap<String, PfpsMapType>();
    static {
        Iterator<PfpsMapType> iter = RPF_MAP_TYPES.iterator();
        PfpsMapType t;
        while (iter.hasNext()) {
            t = iter.next();
            RPF_DIRECTORY_NAMES.put(t.folderName, t);
            RPF_FILE_EXTENSIONS.put(t.shortName, t);
            RPF_FILE_EXTENSIONS.put(t.shortName.toLowerCase(LocaleUtil.getCurrent()), t);
        }
    }

    private final static Set<String> PFPS_DATA_SUBDIRS = new HashSet<String>();
    static {
        PFPS_DATA_SUBDIRS.add("geotiff");
        PFPS_DATA_SUBDIRS.add("mrsid");
        PFPS_DATA_SUBDIRS.add("dted");
        PFPS_DATA_SUBDIRS.add("rpf");
    }

    /** Creates a new instance of PFPSUtils */
    private PfpsUtils() {
    }

    public static boolean isPfpsDataDir(File f) {
        return isPfpsDataDir(f, Integer.MAX_VALUE);
    }
    
    public static boolean isPfpsDataDir(File f, int limit){
        if (!IOProviderFactory.isDirectory(f))
            return false;
        String[] c;
        try {
            c = IOProviderFactory.list(f);
        } catch (NullPointerException e) {
            Log.e(TAG, "f: " + f + " " + f.getAbsolutePath());
            throw e;
        }
        int hits = 0;
        
        int checkLength = Math.min( ((c==null)?0:c.length), limit);
        for (int i = 0; i < checkLength; i++)
            if (PFPS_DATA_SUBDIRS.contains(c[i].toLowerCase(LocaleUtil.getCurrent())))
                hits++;
        if (hits == 0)
            return false;

        final boolean hasRpfData = checkRpf(f, limit);
        // XXX - implement mrsid/geotiff sanity check
        final boolean hasGeotiffData = false;
        final boolean hasMrsidData = false;

        // make sure we have data that we can visualize
        return (hasRpfData || hasGeotiffData || hasMrsidData);
    }

    private static boolean checkRpf(File pfpsDataDir, int limit) {
        File rpfDir = new File(pfpsDataDir, "rpf");
        if (!IOProviderFactory.exists(rpfDir))
            rpfDir = new File(pfpsDataDir, "RPF");
        if (!IOProviderFactory.exists(rpfDir) || IOProviderFactory.isFile(rpfDir))
            return false;

        String[] c = IOProviderFactory.list(rpfDir);

        if (c == null)
            return false;

        int hits = 0;
        int checkLength = Math.min(c.length, limit);
        for (int i = 0; i < checkLength; i++)
            if (RPF_DIRECTORY_NAMES.get(c[i].toLowerCase(LocaleUtil.getCurrent())) != null)
                hits++;
        return (hits > 0);
    }

    public static void createRpfDataDatabase2(MosaicDatabaseBuilder2 database, File d)
            throws SQLException {
        File[] c = IOProviderFactory.listFiles(d, new FileFilter() {
            @Override
            public boolean accept(File f) {
                if (!IOProviderFactory.isDirectory(f))
                    return false;
                return RPF_DIRECTORY_NAMES.containsKey(f.getName().toLowerCase(LocaleUtil.getCurrent()));
            }
        });

        // can return null, protect without changing logic below.
        if (c == null) 
             c = new File[0];


        database.beginTransaction();

        try {
            if (d instanceof ZipVirtualFile) {
                try {
                    ((ZipVirtualFile) d).setBatchMode(true);
                } catch (IOException ignored) {
                }
            }

            final URI relativeUri = IOProviderFactory.toURI(d.getParentFile());

            PfpsMapType t;
            File[] subdirs;
            File[] frames;
            int width = 1536;
            int height = 1536;
            double resolution;
            String frameUri;
            GeoPoint[] corners = new GeoPoint[] {
                    GeoPoint.createMutable(), GeoPoint.createMutable(), GeoPoint.createMutable(),
                    GeoPoint.createMutable()
            };
            boolean gdalCoverage;
            ByteBuffer frame = null;
            char[] typeShortName = new char[3];
            SparseArray<File> frameFiles = new SparseArray<File>();
            String frameFileName;
            int frameNumber;
            int frameVersion;
            File latestFrameFile;

            File file;
            String type;
            char[] frameFileNameChars = new char[12];
            for (int i = 0; i < c.length; i++) {
                t = RPF_DIRECTORY_NAMES.get(c[i].getName().toLowerCase(LocaleUtil.getCurrent()));
                subdirs = IOProviderFactory.listFiles(c[i], new FileFilter() {
                    @Override
                    public boolean accept(File f) {
                        return (IOProviderFactory.isDirectory(f) && f.getName().length() == 1);
                    }
                });
                if(subdirs == null)
                    continue;
                typeShortName[0] = t.shortName.charAt(0);
                typeShortName[1] = t.shortName.charAt(1);
                type = PfpsMapTypeFrame.getRpfPrettyName(t.shortName);
                if(type == null)
                    type = t.folderName.toUpperCase(LocaleUtil.getCurrent());
                for (int j = 0; j < subdirs.length; j++) {
                    frames = IOProviderFactory.listFiles(subdirs[j]);
                    if (frames == null)
                       frames = new File[0];

                    typeShortName[2] = subdirs[j].getName().charAt(0);
                    if (typeShortName[2] >= 'a' && typeShortName[2] <= 'z')
                        typeShortName[2] &= ~32;
                    frameFiles.clear();
                    for (int k = 0; k < frames.length; k++) {
                        frameFileName = frames[k].getName();
                        if (frameFileName.length() != 12)
                            continue;
                        frameFileName.getChars(0, 12, frameFileNameChars, 0);
                        if (frameFileNameChars[8] != '.' ||
                                ((frameFileNameChars[9] & ~32) != typeShortName[0]) &&
                                (frameFileNameChars[9] != typeShortName[0]) ||
                                ((frameFileNameChars[10] & ~32) != typeShortName[1]) &&
                                (frameFileNameChars[10] != typeShortName[1]) ||
                                ((frameFileNameChars[11] & ~32) != typeShortName[2]) &&
                                (frameFileNameChars[11] != typeShortName[2])) {

                            continue;
                        }

                        frameNumber = getRpfFrameNumber(t, frameFileNameChars);
                        frameVersion = getRpfFrameVersion(t, frameFileNameChars);

                        latestFrameFile = frameFiles.get(frameNumber);
                        if (latestFrameFile == null
                                || frameVersion > getRpfFrameVersion(t, latestFrameFile.getName())) {
                            frameFiles.put(frameNumber, frames[k]);
                        }
                    }

                    int sSize = frameFiles.size();
                    for (int s = 0; s < sSize; s++) {
                        file = frameFiles.valueAt(s);
                        file.getName().toUpperCase(LocaleUtil.getCurrent())
                                .getChars(0, 12, frameFileNameChars, 0);

                        corners[0].set(Double.NaN, Double.NaN);
                        corners[1].set(Double.NaN, Double.NaN);
                        corners[2].set(Double.NaN, Double.NaN);
                        corners[3].set(Double.NaN, Double.NaN);

                        // try to obtain the frame coverage quickly
                        if (!PfpsMapTypeFrame.coverageFromFilename(frameFileNameChars, corners[0],
                                corners[1], corners[2], corners[3])) {
                            gdalCoverage = true;
                            try {
                                frame = quickFrameCoverage(file, corners, frame);
                                gdalCoverage = false;
                                for (int l = 0; l < corners.length; l++)
                                    gdalCoverage |= (Double.isNaN(corners[l].getLatitude()) || Double
                                            .isNaN(corners[l].getLongitude()));
                            } catch (IOException | RuntimeException e) {
                                frame = null;
                                Log.e(TAG, "error: ", e);
                            }
                            if (gdalCoverage) {
                                Dataset dataset = null;
                                try {
                                    dataset = GdalLibrary.openDatasetFromFile(file);
                                    if (dataset == null)
                                        continue;
                                    final GdalDatasetProjection2 proj = GdalDatasetProjection2
                                            .getInstance(dataset);
                                    width = dataset.GetRasterXSize();
                                    height = dataset.GetRasterYSize();

                                    if (proj != null) { 
                                        proj.imageToGround(new PointD(0, 0), corners[0]);
                                        proj.imageToGround(new PointD(width, 0), corners[1]);
                                        proj.imageToGround(new PointD(width, height), corners[2]);
                                        proj.imageToGround(new PointD(0, height), corners[3]);
                                    }
                                } finally {
                                    if (dataset != null)
                                        dataset.delete();
                                }

                            }
                        }

                        resolution = t.scale;
                        switch (t.scaleUnits) {
                            case PfpsMapType.SCALE_UNIT_METER:
                                break;
                            case PfpsMapType.SCALE_UNIT_SCALE:
                                resolution = cadrgScaleToCibResolution(1.0d / resolution);
                                break;
                            default:
                                throw new IllegalStateException();
                        }

                        database.insertRow(relativeUri.relativize(IOProviderFactory.toURI(file)).getPath(),
                                           type,
                                           false,
                                           corners[0],
                                           corners[1],
                                           corners[2],
                                           corners[3],
                                           resolution * (double)(1<<MosaicUtils.DEFAULT_NUM_LEVELS),
                                           resolution,
                                           width,
                                           height,
                                           4326);
                    }
                }
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();

            if (d instanceof ZipVirtualFile) {
                try {
                    ((ZipVirtualFile) d).setBatchMode(false);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static PfpsMapType getMapType(String frame) {
        if (frame.length() != 12)
            return null;
        if (frame.charAt(8) != '.')
            return null;
        char c;
        for (int i = 0; i < 8; i++) {
            c = frame.charAt(i);
            if (c >= '0' && c <= '9')
                continue;
            c |= 32;
            if (c < 'a' || c > 'z' || c == 'i' || c == 'o')
                return null;
        }
        return RPF_FILE_EXTENSIONS.get(frame.substring(9, 11));
    }

    /**
     * Derived from MIL-C-89041 sec 3.5.1
     */
    public static double cadrgScaleToCibResolution(double scale) {
        return (150.0d * 1e-6) / scale;
    }

    private static ByteBuffer quickFrameCoverage(File f, GeoPoint[] coverage,
            ByteBuffer frame) throws IOException {
        InputStream inputStream = null;
        try {
            if (frame == null || frame.capacity() < IOProviderFactory.length(f))
                frame = ByteBuffer.wrap(new byte[(int)IOProviderFactory.length(f)]);
            frame.clear();
            frame.limit((int)IOProviderFactory.length(f));
            if (f instanceof ZipVirtualFile)
                inputStream = ((ZipVirtualFile) f).openStream();
            else
                inputStream = IOProviderFactory.getInputStream(f);

            
            int r = inputStream.read(frame.array());
            if (r < 1)
               Log.d(TAG, "header read failed");
        } finally {
            if (inputStream != null)
                inputStream.close();
        }

        String s = getString(frame, 9);
        if (s.equals("NITF02.10"))
            quickFrameCoverageNitf21(frame, coverage);
        else if (s.equals("NITF02.00"))
            quickFrameCoverageNitf20(frame, coverage);
        return frame;
    }

    private static boolean quickFrameCoverageNitf20(ByteBuffer buf, GeoPoint[] coverage)
            throws IOException {
        buf.position(360);

        final int numi = Integer.parseInt(getString(buf, 3));
        skip(buf, numi * (6 + 10));
        final int nums = Integer.parseInt(getString(buf, 3));
        skip(buf, nums * (4 + 6));
        final int numx = Integer.parseInt(getString(buf, 3));
        skip(buf, numx * 0);
        final int numt = Integer.parseInt(getString(buf, 3));
        skip(buf, numt * (4 + 5));
        final int numdes = Integer.parseInt(getString(buf, 3));
        skip(buf, numdes * (4 + 9));
        final int numres = Integer.parseInt(getString(buf, 3));
        skip(buf, numres * (4 + 7));
        final int udhdl = Integer.parseInt(getString(buf, 5));
        if (udhdl == 0)
            return false;

        final int udhofl = Integer.parseInt(getString(buf, 3));
        if (udhofl != 0)
            return false;

        while (buf.remaining() > 11 && !"RPFHDR".equals(getString(buf, 6)))
            buf.position(Integer.parseInt(getString(buf, 5)) + buf.position());
        if (buf.remaining() <= 11)
            return false;

        // TRE length
        skip(buf, 5);

        RpfHeaderSection header_section = RpfHeaderSection.parse(buf);

        buf.position(header_section.location_section_location);

        skip(buf, 2);

        final int component_location_table_offset = buf.getInt();
        final int number_of_component_location_records = buf.getShort() & 0xFFFF;

        buf.position(header_section.location_section_location + component_location_table_offset);

        int component_location = -1;
        for (int i = 0; i < number_of_component_location_records; i++) {
            if ((buf.getShort() & 0xFFFF) == 130) {
                skip(buf, 4);
                component_location = buf.getInt();
            } else {
                skip(buf, 8);
            }
        }

        if (component_location < 0)
            return false;

        buf.position(component_location);

        // north west
        coverage[0].set(buf.getDouble(), buf.getDouble());
        // south west
        coverage[3].set(buf.getDouble(), buf.getDouble());
        // north east
        coverage[1].set(buf.getDouble(), buf.getDouble());
        // south east
        coverage[2].set(buf.getDouble(), buf.getDouble());

        // check for IDL crossing
        if (coverage[0].getLongitude() > coverage[1].getLongitude()) {
            coverage[1].set(coverage[1].getLatitude(), 360.0d + coverage[1].getLongitude());
            coverage[2].set(coverage[2].getLatitude(), 360.0d + coverage[2].getLongitude());
        }

        return true;
    }

    private static String getString(ByteBuffer buffer, int len) {
        if (buffer.remaining() < len)
            throw new BufferUnderflowException();
        String retval = new String(buffer.array(), buffer.position(), len, FileSystemUtils.UTF8_CHARSET);
        buffer.position(buffer.position() + len);
        return retval;
    }

    private static void skip(ByteBuffer buffer, int skip) {
        buffer.position(buffer.position() + skip);
    }

    private static boolean quickFrameCoverageNitf21(ByteBuffer buf, GeoPoint[] coverage)
            throws IOException {
        return false;
    }

    // XXX - Consider not assigning/removing unreferenced variables from this class if possible.
    @SuppressWarnings("unused")
    private static class RpfHeaderSection {
        /** true for LE, false for BE */
        public final boolean little_big_endian_indicator;
        public final int header_section_length;
        public final String file_name;
        public final int new_replacement_update_indicator;
        public final String governing_specification_number;
        public final String governing_specification_date;
        public final String security_classification;
        public final String security_country_international_code;
        public final String security_release_marking;
        public final int location_section_location;

        private RpfHeaderSection(boolean little_big_endian_indicator,
                int header_section_length,
                String file_name,
                int new_replacement_update_indicator,
                String governing_specification_number,
                String governing_specification_date,
                String security_classification,
                String security_country_international_code,
                String security_release_marking,
                int location_section_location) {

            this.little_big_endian_indicator = little_big_endian_indicator;
            this.header_section_length = header_section_length;
            this.file_name = file_name;
            this.new_replacement_update_indicator = new_replacement_update_indicator;
            this.governing_specification_number = governing_specification_number;
            this.governing_specification_date = governing_specification_date;
            this.security_classification = security_classification;
            this.security_country_international_code = security_country_international_code;
            this.security_release_marking = security_release_marking;
            this.location_section_location = location_section_location;
        }

        public static RpfHeaderSection parse(ByteBuffer buffer) {
            boolean lbei = ((buffer.get() & 0xFF) == 0xFF);
            if (lbei)
                buffer.order(ByteOrder.LITTLE_ENDIAN);
            else
                buffer.order(ByteOrder.BIG_ENDIAN);
            int hsl = buffer.getShort() & 0xFFFF;
            String fn = getString(buffer, 12);
            int nrui = buffer.get() & 0xFF;
            String gsn = getString(buffer, 15);
            String gsd = getString(buffer, 8);
            String sc = String.valueOf((char) buffer.get());
            String scic = getString(buffer, 2);
            String srm = getString(buffer, 2);
            int lsl = buffer.getInt();

            return new RpfHeaderSection(lbei, hsl, fn, nrui, gsn, gsd, sc, scic, srm, lsl);
        }
    }

    public static int getRpfZone(String frameFileName) {
        return base34Decode(frameFileName.charAt(11));
    }

    /**
     * Returns the successive version number for the frame file. See MIL-C-89038 section 30.6 /
     * MIL-C-89041 section A.3.6.
     * 
     * @param type
     * @param frameFileName
     * @return
     */
    public static int getRpfFrameVersion(PfpsMapType type, String frameFileName) {
        int frameNumberLen;
        if (type.folderName.startsWith("cib"))
            frameNumberLen = 6;
        else
            frameNumberLen = 5;
        return base34Decode(frameFileName.substring(frameNumberLen, 8));
    }

    public static int getRpfFrameVersion(PfpsMapType type, char[] frameFileName) {
        int frameNumberLen;
        if (type.folderName.startsWith("cib"))
            frameNumberLen = 6;
        else
            frameNumberLen = 5;
        return base34Decode(frameFileName, frameNumberLen, 8 - frameNumberLen);
    }

    /**
     * Returns the unique cumulative frame number with the frame's zone. See MIL-C-89038 section
     * 30.6 / MIL-C-89041 section A.3.6.
     * 
     * @param type
     * @param frameFileName
     * @return
     */
    public static int getRpfFrameNumber(PfpsMapType type, String frameFileName) {
        int frameNumberLen;
        if (type.folderName.startsWith("cib"))
            frameNumberLen = 6;
        else
            frameNumberLen = 5;
        return base34Decode(frameFileName.substring(0, frameNumberLen));
    }

    public static int getRpfFrameNumber(PfpsMapType type, char[] frameFileName) {
        int frameNumberLen;
        if (type.folderName.startsWith("cib"))
            frameNumberLen = 6;
        else
            frameNumberLen = 5;
        return base34Decode(frameFileName, 0, frameNumberLen);
    }

    /**
     * Returns the base-34 value corresponding to the specified character.
     * 
     * @param c A character
     * 
     * @return  The base-34 value. A value less than zero is returned if the
     *          character is part of the base-34 character set.
     */
    public static int base34Decode(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        } else if (c >= 'A' && c <= 'z') {
            c &= ~32;
            if (c < 'I') {
                return c - 'A' + 10;
            } else if (c > 'I' && c < 'O') {
                return c - 'A' + 9;
            } else if (c > 'O') {
                return c - 'A' + 8;
            }
        }

        return Integer.MIN_VALUE;
    }

    /**
     * Returns the base-34 value corresponding to the specified string.
     * 
     * @param s A string
     * 
     * @return  The base-34 value. A value less than zero is returned if any of
     *          the characters are part of the base-34 character set.
     */
    public static int base34Decode(String s) {
        int r = 0;
        for (int i = 0; i < s.length(); i++) {
            final int v = base34Decode(s.charAt(i));
            if(v < 0)
                return -1;
            r = (r * 34) + v;
        }
        return r;
    }

    /**
     * Returns the base-34 value corresponding to the specified string.
     * 
     * @param s     A character array
     * @param off   The array offset
     * @param len   The number of characters in the string
     * 
     * @return  The base-34 value. A value less than zero is returned if any of
     *          the characters are part of the base-34 character set.
     */
    public static int base34Decode(char[] s, int off, int len) {
        int r = 0;
        final int limit = off + len;
        for (int i = off; i < limit; i++) {
            final int v = base34Decode(s[i]);
            if(v < 0)
                return -1;
            r = (r * 34) + v;
        }
        return r;
    }
}
