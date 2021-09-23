
package com.atakmap.map.layer.raster.pfps;

import java.io.File;
import java.util.HashMap;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Map;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;


/* 
 * See method comments for MIL references.
 */
public class PfpsMapTypeFrame {

    public static final String TAG = "PfpsMapTypeFrame";

    private static final int RPF_FILENAME_LENGTH = 12;
    private static final int ECRG_FILENAME_LENGTH = 18;
    private static final String CADRG_DATA_TYPE = "CADRG";
    private static final String CIB_DATA_TYPE = "CIB";

    // MIL-A-89007, Section 70
    private static final int PIXEL_ROWS_PER_FRAME = 1536;
    private static final int NORTH_SOUTH_PIXEL_SPACING_CONSTANT = 400384;
    private static final int[] EAST_WEST_PIXEL_SPACING_CONSTANT = {
            369664, 302592, 245760, 199168, 163328, 137216, 110080, 82432
    };
    private static final int[] EQUATORWARD_NOMINAL_BOUNDARY = {
            0, 32, 48, 56, 64, 68, 72, 76, 80
    };
    private static final int[] POLEWARD_NOMINAL_BOUNDARY = {
            32, 48, 56, 64, 68, 72, 76, 80, 90
    };

    public static String getRpfPrettyName(File f) {
        final String name = f.getName();
        final int len = name.length();
        if(len == RPF_FILENAME_LENGTH)
            return getRpfPrettyName(f.getName().substring(9, 11).toUpperCase(LocaleUtil.getCurrent()));
        else if(len == ECRG_FILENAME_LENGTH)
            return getRpfPrettyName(f.getName().substring(15, 17).toUpperCase(LocaleUtil.getCurrent()));
        else
            return null;
    }
    
    public static String getRpfPrettyName(String typeCode) {
        final String pfpsMapDataTypeCode = typeCode;

        PfpsMapDataType retval = PfpsMapDataType.getPfpsMapDataType(pfpsMapDataTypeCode);
        if(retval != null)
            return retval.prettyName;
        return null;
    }

    public static boolean coverageFromFilename(File f, GeoPoint ul, GeoPoint ur,
                                               GeoPoint lr, GeoPoint ll) {
        if(f == null){
            return false; 
        }

        String name = f.getName();
        final int len = name.length();
        if(len < RPF_FILENAME_LENGTH)
            return false;
        name = name.toUpperCase(LocaleUtil.getCurrent());

        char[] buffer = new char[len];
        name.getChars(0, len, buffer, 0);

        return coverageFromFilename(buffer, ul, ur, lr, ll);
    }

    @SuppressWarnings("unused")
    public static boolean coverageFromFilename(char[] buffer, GeoPoint ul,
            GeoPoint ur, GeoPoint lr, GeoPoint ll) {
        try {
            final char producerId = buffer[buffer.length-5];
            final String pfpsMapDataTypeCode = String.valueOf(buffer, buffer.length-3, 2);
            final char zoneCode = buffer[buffer.length-1];

            // CADRG
            int frameChars = 5;
            int versionChars = 2;
            if(buffer.length == ECRG_FILENAME_LENGTH) {
                frameChars = 10;
                versionChars = 3;
            } else if (PfpsMapDataType.isCIBMapDataType(pfpsMapDataTypeCode)) {
                // CIB
                frameChars = 6;
                versionChars = 1;
            }

            final int frameNumber = PfpsUtils.base34Decode(buffer, 0, frameChars);
            final int version = PfpsUtils.base34Decode(buffer, frameChars, versionChars);
            
            if(frameNumber < 0 || version < 0)
                return false;

            int northSouthPixelConstant;
            int eastWestPixelConstant;
            double polewardExtent;
            double equatorwardExtent;
            int latitudinalFrames;
            int longitudinalFrames;

            PfpsMapDataType ds = PfpsMapDataType.getPfpsMapDataType(pfpsMapDataTypeCode);
            if(ds == null)
                return false;

            String rpfDataType = ds.rpfDataType;
            double resolution = ds.scaleOrGSD;

            // valid scale / gsd
            if (ds != null && ds.scaleOrGSD > 0d) {
                eastWestPixelConstant = eastWestPixelSpacingConstant(zoneCode);
                northSouthPixelConstant = northSouthPixelSpacingConstant();

                // scale / gsd specific zone properties
                if (PfpsMapDataType.isCADRGDataType(rpfDataType)) {
                    northSouthPixelConstant = northSouthPixelConstant_CADRG(
                            northSouthPixelConstant, resolution);
                    eastWestPixelConstant = eastWestPixelConstant_CADRG(eastWestPixelConstant,
                            resolution);

                } else if (PfpsMapDataType.isCIBDataType(rpfDataType)) {
                    northSouthPixelConstant = northSouthPixelConstant_CIB(northSouthPixelConstant,
                            resolution);
                    eastWestPixelConstant = eastWestPixelConstant_CIB(eastWestPixelConstant,
                            resolution);

                } else {
                    northSouthPixelConstant = -1;
                    eastWestPixelConstant = -1;
                }

                polewardExtent = polewardExtent(polewardNominalBoundary(zoneCode),
                        northSouthPixelConstant, PIXEL_ROWS_PER_FRAME);
                equatorwardExtent = equatorwardExtent(equatorwardNominalBoundary(zoneCode),
                        northSouthPixelConstant, PIXEL_ROWS_PER_FRAME);

                latitudinalFrames = latitudinalFrames(polewardExtent, equatorwardExtent,
                        northSouthPixelConstant, PIXEL_ROWS_PER_FRAME);
                longitudinalFrames = longitudinalFrames(eastWestPixelConstant, PIXEL_ROWS_PER_FRAME);

                // start calculate bounds

                int maxFrameNumber = maxFrameNumber(latitudinalFrames, longitudinalFrames);
                if (frameNumber < 0 || frameNumber > maxFrameNumber)
                    return false;

                int row = frameRow(frameNumber, longitudinalFrames);
                int col = frameColumn(frameNumber, row, longitudinalFrames);

                double zoneLat = PfpsMapTypeFrame.isZoneInUpperHemisphere(zoneCode) ?
                        equatorwardExtent : polewardExtent;
                double maxLatitude = frameOriginLatitude(row, northSouthPixelConstant,
                        PIXEL_ROWS_PER_FRAME, zoneLat);
                double minLatitude = maxLatitude - frameDeltaLatitude(northSouthPixelConstant,
                        PIXEL_ROWS_PER_FRAME);

                double minLongitude = frameOriginLongitude(col, eastWestPixelConstant,
                        PIXEL_ROWS_PER_FRAME);
                double maxLongitude = minLongitude + frameDeltaLongitude(eastWestPixelConstant,
                        PIXEL_ROWS_PER_FRAME);

                ul.set(maxLatitude, minLongitude);
                ur.set(maxLatitude, maxLongitude);
                lr.set(minLatitude, maxLongitude);
                ll.set(minLatitude, minLongitude);
                return true;
            } else {
                return false;
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "error: ", e);
            return false;
        }
    }

    // MIL-A-89007, Section 70
    private static int eastWestPixelSpacingConstant(char zoneCode) {
        int index = indexFor(zoneCode) % 9;
        if (index < 0 || index >= EAST_WEST_PIXEL_SPACING_CONSTANT.length)
            return -1;

        return EAST_WEST_PIXEL_SPACING_CONSTANT[index];
    }

    private static int northSouthPixelSpacingConstant() {
        return NORTH_SOUTH_PIXEL_SPACING_CONSTANT;
    }

    private static int equatorwardNominalBoundary(char zoneCode) {
        return nominalBoundary(zoneCode, EQUATORWARD_NOMINAL_BOUNDARY);
    }

    private static int polewardNominalBoundary(char zoneCode) {
        return nominalBoundary(zoneCode, POLEWARD_NOMINAL_BOUNDARY);
    }

    private static int nominalBoundary(char zoneCode, int[] boundaryArray) {
        int index = indexFor(zoneCode) % 9;
        if (index < 0)
            return -1;

        if (!isZoneInUpperHemisphere(zoneCode))
            return 0 - boundaryArray[index];
        return boundaryArray[index];
    }

    // MIL-C-89038, Section 60.1
    private static int northSouthPixelConstant_CADRG(double northSouthPixelConstant, double scale) {
        double S = 1000000d / scale;
        double tmp = northSouthPixelConstant * S;
        tmp = 512d * (int) Math.ceil(tmp / 512d);
        tmp /= 4d;
        tmp /= (150d / 100d);
        return 256 * (int) Math.round(tmp / 256d);
    }

    private static int eastWestPixelConstant_CADRG(double eastWestPixelSpacingConstant, double scale) {
        double S = 1000000d / scale;
        double tmp = eastWestPixelSpacingConstant * S;
        tmp = 512d * (int) Math.ceil(tmp / 512d);
        tmp /= (150d / 100d);
        return 256 * (int) Math.round(tmp / 256d);
    }

    // MIL-PRF-89041A, Section A.5.1.1
    private static int northSouthPixelConstant_CIB(double northSouthPixelSpacingConstant,
            double groundSampleDistance) {
        double S = 100d / groundSampleDistance;
        double tmp = northSouthPixelSpacingConstant * S;
        tmp = 512d * (int) Math.ceil(tmp / 512d);
        tmp /= 4d;
        return 256 * (int) Math.round(tmp / 256d);
    }

    private static int eastWestPixelConstant_CIB(double eastWestPixelSpacingConstant,
            double groundSampleDistance) {
        double S = 100d / groundSampleDistance;
        double tmp = eastWestPixelSpacingConstant * S;
        return 512 * (int) Math.ceil(tmp / 512d);
    }

    // MIL-C-89038, Section 60.1.5.b
    // MIL-PRF-89041A, Section A.5.1.2.b
    private static double polewardExtent(double polewardNominalBoundary,
            double northSouthPixelConstant, double pixelRowsPerFrame) {
        double nsPixelsPerDegree = northSouthPixelConstant / 90d;
        return Math.signum(polewardNominalBoundary)
                * clamp(Math.ceil(nsPixelsPerDegree * Math.abs(polewardNominalBoundary)
                        / pixelRowsPerFrame)
                        * pixelRowsPerFrame / nsPixelsPerDegree, 0, 90);
    }

    private static double equatorwardExtent(double equatorwardNominalBoundary,
            double northSouthPixelConstant, double pixelRowsPerFrame) {
        double nsPixelsPerDegree = northSouthPixelConstant / 90d;
        return Math.signum(equatorwardNominalBoundary)
                * clamp((int) (nsPixelsPerDegree * Math.abs(equatorwardNominalBoundary) / pixelRowsPerFrame)
                        * pixelRowsPerFrame / nsPixelsPerDegree, 0, 90);
    }

    // MIL-PRF-89038, Section 60.1
    // MIL-PRF-89041A, Section A.5.1
    private static int latitudinalFrames(double polewardExtentDegrees,
            double equatorwardExtentDegrees, double northSouthPixelConstant,
            double pixelRowsPerFrame) {
        double nsPixelsPerDegree = northSouthPixelConstant / 90d;
        double extent = Math.abs(polewardExtentDegrees - equatorwardExtentDegrees);
        return (int) Math.round(extent * nsPixelsPerDegree / pixelRowsPerFrame);
    }

    private static int longitudinalFrames(double eastWestPixelConstant, double pixelRowsPerFrame) {
        return (int) Math.ceil(eastWestPixelConstant / pixelRowsPerFrame);
    }

    private static double clamp(double x, double min, double max) {
        return (x < min) ? min : ((x > max) ? max : x);
    }

    // MIL-C-89038, Section 30.6
    // MIL-PRF-89041A, Section A.3.6
    private static int maxFrameNumber(int rowFrames, int columnFrames) {
        return (rowFrames * columnFrames) - 1;
    }

    private static int frameRow(int frameNumber, int columnFrames) {
        return (int) (frameNumber / (double) columnFrames);
    }

    private static int frameColumn(int frameNumber, int frameRow, int columnFrames) {
        return frameNumber - (frameRow * columnFrames);
    }

    // MIL-C-89038, Section 30.3
    // MIL-PRF-89041A, Section A.3.3
    private static double frameOriginLatitude(int row, double northSouthPixelConstant,
            double pixelRowsPerFrame, double zoneOriginLatitude) {
        return (90d / northSouthPixelConstant) * pixelRowsPerFrame * (row + 1) + zoneOriginLatitude;
    }

    private static double frameOriginLongitude(int column, double eastWestPixelConstant,
            double pixelRowsPerFrame) {
        return (360d / eastWestPixelConstant) * pixelRowsPerFrame * column - 180d;
    }

    private static double frameDeltaLatitude(double northSouthPixelConstant,
            double pixelRowsPerFrame) {
        return (90d / northSouthPixelConstant) * pixelRowsPerFrame;
    }

    private static double frameDeltaLongitude(double eastWestPixelConstant, double pixelRowsPerFrame) {
        return (360d / eastWestPixelConstant) * pixelRowsPerFrame;
    }

    // zone util
    private static int indexFor(char zoneCode) {
        final int NUM_START_INDEX = 0;
        final int ALPHA_START_INDEX = 9;

        int index = -1;
        char upperChar = Character.toUpperCase(zoneCode);
        if (upperChar >= '1' && upperChar <= '9') {
            index = NUM_START_INDEX + upperChar - '1';
        } else if (upperChar >= 'A' && upperChar <= 'H') {
            index = ALPHA_START_INDEX + upperChar - 'A';
        } else if (upperChar == 'J') {
            index = ALPHA_START_INDEX + upperChar - 'A' - 1;
        }

        return index;
    }

    // zone util
    private static boolean isZoneInUpperHemisphere(char zoneCode) {
        char upperChar = Character.toUpperCase(zoneCode);
        return (upperChar >= '1' && upperChar <= '9');
    }

    // MIL-STD-2411-1 Change 3, Section 5.1.4
    @SuppressWarnings("unused")
    private enum PfpsMapDataType {
        PFPS_MAP_TYPE_A1("A1", "CM", "1:10,000", "Combat Charts, 1:10,000 scale", CADRG_DATA_TYPE, 10000, "CM 1:10K"),
        PFPS_MAP_TYPE_A2("A2", "CM", "1:25,000", "Combat Charts, 1:25,000 scale", CADRG_DATA_TYPE, 25000, "CM 1:25K"),
        PFPS_MAP_TYPE_A3("A3", "CM", "1:50,000", "Combat Charts, 1:50,000 scale", CADRG_DATA_TYPE, 50000, "CM 1:50K"),
        PFPS_MAP_TYPE_A4("A4", "CM", "1:100,000", "Combat Charts, 1:100,000 scale", CADRG_DATA_TYPE, 100000, "CM 1:100K"),
        PFPS_MAP_TYPE_AT("AT", "ATC", "1:200,000", "Series 200 Air Target Chart", CADRG_DATA_TYPE, 200000),
        PFPS_MAP_TYPE_C1("C1", "CG", "1:10,000", "City Graphics", CADRG_DATA_TYPE, 10000, "CG 1:10K"),
        PFPS_MAP_TYPE_C2("C2", "CG", "1:10,560", "City Graphics", CADRG_DATA_TYPE, 10560, "CG 1:10.56K"),
        PFPS_MAP_TYPE_C3("C3", "CG", "1:11,000", "City Graphics", CADRG_DATA_TYPE, 11000, "CG 1:11K"),
        PFPS_MAP_TYPE_C4("C4", "CG", "1:11,800", "City Graphics", CADRG_DATA_TYPE, 11800, "CG 1:11.8K"),
        PFPS_MAP_TYPE_C5("C5", "CG", "1:12,000", "City Graphics", CADRG_DATA_TYPE, 12000, "CG 1:12K"),
        PFPS_MAP_TYPE_C6("C6", "CG", "1:12,500", "City Graphics", CADRG_DATA_TYPE, 12500, "CG 1:12.5K"),
        PFPS_MAP_TYPE_C7("C7", "CG", "1:12,800", "City Graphics", CADRG_DATA_TYPE, 12800, "CG 1:12.8K"),
        PFPS_MAP_TYPE_C8("C8", "CG", "1:14,000", "City Graphics", CADRG_DATA_TYPE, 14000, "CG 1:14K"),
        PFPS_MAP_TYPE_C9("C9", "CG", "1:14,700", "City Graphics", CADRG_DATA_TYPE, 14700, "CG 1:14.7K"),
        PFPS_MAP_TYPE_CA("CA", "CG", "1:15,000", "City Graphics", CADRG_DATA_TYPE, 15000, "CG 1:15K"),
        PFPS_MAP_TYPE_CB("CB", "CG", "1:15,500", "City Graphics", CADRG_DATA_TYPE, 15500, "CG 1:15.5K"),
        PFPS_MAP_TYPE_CC("CC", "CG", "1:16,000", "City Graphics", CADRG_DATA_TYPE, 16000, "CG 1:16K"),
        PFPS_MAP_TYPE_CD("CD", "CG", "1:16,666", "City Graphics", CADRG_DATA_TYPE, 16666, "CG 1:16.67K"),
        PFPS_MAP_TYPE_CE("CE", "CG", "1:17,000", "City Graphics", CADRG_DATA_TYPE, 17000, "CG 1:17K"),
        PFPS_MAP_TYPE_CF("CF", "CG", "1:17,500", "City Graphics", CADRG_DATA_TYPE, 17500, "CG 1:17.5K"),
        PFPS_MAP_TYPE_CG("CG", "CG", "Various", "City Graphics", CADRG_DATA_TYPE, -1, "CG Various"),
        PFPS_MAP_TYPE_CH("CH", "CG", "1:18,000", "City Graphics", CADRG_DATA_TYPE, 18000, "CG 1:18K"),
        PFPS_MAP_TYPE_CJ("CJ", "CG", "1:20,000", "City Graphics", CADRG_DATA_TYPE, 20000, "CG 1:20K"),
        PFPS_MAP_TYPE_CK("CK", "CG", "1:21,000", "City Graphics", CADRG_DATA_TYPE, 21000, "CG 1:21K"),
        PFPS_MAP_TYPE_CL("CL", "CG", "1:21,120", "City Graphics", CADRG_DATA_TYPE, 21120, "CG 1:21.12K"),
        PFPS_MAP_TYPE_CM("CM", "CM", "Various", "Combat Charts", CADRG_DATA_TYPE, -1, "CM Various"),
        PFPS_MAP_TYPE_CN("CN", "CG", "1:22,000", "City Graphics", CADRG_DATA_TYPE, 22000, "CG 1:22K"),
        PFPS_MAP_TYPE_CO("CO", "CO", "Various", "Coastal Charts", CADRG_DATA_TYPE, -1),
        PFPS_MAP_TYPE_CP("CP", "CG", "1:23,000", "City Graphics", CADRG_DATA_TYPE, 23000, "CG 1:23K"),
        PFPS_MAP_TYPE_CQ("CQ", "CG", "1:25,000", "City Graphics", CADRG_DATA_TYPE, 25000, "CG 1:25K"),
        PFPS_MAP_TYPE_CR("CR", "CG", "1:26,000", "City Graphics", CADRG_DATA_TYPE, 26000, "CG 1:26K"),
        PFPS_MAP_TYPE_CS("CS", "CG", "1:35,000", "City Graphics", CADRG_DATA_TYPE, 35000, "CG 1:35K"),
        PFPS_MAP_TYPE_CT("CT", "CG", "1:36,000", "City Graphics", CADRG_DATA_TYPE, 36000, "CG 1:36K"),
        PFPS_MAP_TYPE_D1("D1", "---", "100m", "Elevation Data from DTED level 1", "CDTED", 100.0, "DT1 Elev"),
        PFPS_MAP_TYPE_D2("D2", "---", "30m", "Elevation Data from DTED level 2", "CDTED", 30.0, "DT2 Elev"),
        PFPS_MAP_TYPE_EG("EG", "NARC", "1:11,000,000", "North Atlantic Route Chart", CADRG_DATA_TYPE, 11000000),
        PFPS_MAP_TYPE_ES("ES", "SEC", "1:500,000", "VFR Sectional", CADRG_DATA_TYPE, 500000, "VFR 1:500K"),
        PFPS_MAP_TYPE_ET("ET", "SEC", "1:250,000", "VFR Sectional Insets", CADRG_DATA_TYPE, 250000, "VFR 1:250K"),
        PFPS_MAP_TYPE_F1("F1", "TFC-1", "1:250,000", "Transit Flying Chart (TBD #1)", CADRG_DATA_TYPE, 250000),
        PFPS_MAP_TYPE_F2("F2", "TFC-2", "1:250,000", "Transit Flying Chart (TBD #2)", CADRG_DATA_TYPE, 250000),
        PFPS_MAP_TYPE_F3("F3", "TFC-3", "1:250,000", "Transit Flying Chart (TBD #3)", CADRG_DATA_TYPE, 250000),
        PFPS_MAP_TYPE_F4("F4", "TFC-4", "1:250,000", "Transit Flying Chart (TBD #4)", CADRG_DATA_TYPE, 250000),
        PFPS_MAP_TYPE_F5("F5", "TFC-5", "1:250,000", "Transit Flying Chart (TBD #5)", CADRG_DATA_TYPE, 250000),
        PFPS_MAP_TYPE_GN("GN", "GNC", "1:5,000,000", "Global Navigation Chart", CADRG_DATA_TYPE, 5000000),
        PFPS_MAP_TYPE_HA("HA", "HA", "Various", "Harbor and Approach Charts", CADRG_DATA_TYPE, -1),
        PFPS_MAP_TYPE_I1("I1", "---", "10m", "Imagery, 10 meter resolution", CIB_DATA_TYPE, 10.0, "CIB 10m"),
        PFPS_MAP_TYPE_I2("I2", "---", "5m", "Imagery, 5 meter resolution", CIB_DATA_TYPE, 5.0, "CIB 5m"),
        PFPS_MAP_TYPE_I3("I3", "---", "2m", "Imagery, 2 meter resolution", CIB_DATA_TYPE, 2.0, "CIB 2m"),
        PFPS_MAP_TYPE_I4("I4", "---", "1m", "Imagery, 1 meter resolution", CIB_DATA_TYPE, 1.0, "CIB 1m"),
        PFPS_MAP_TYPE_I5("I5", "---", ".5m", "Imagery, .5 (half) meter resolution", CIB_DATA_TYPE, 0.5, "CIB 50cm"),
        PFPS_MAP_TYPE_IV("IV", "---", "Various>10m", "Imagery, greater than 10 meter resolution", CIB_DATA_TYPE, -1, "CIB 10+"),
        PFPS_MAP_TYPE_JA("JA", "JOG-A", "1:250,000", "Joint Operations Graphic - Air", CADRG_DATA_TYPE, 250000),
        PFPS_MAP_TYPE_JG("JG", "JOG", "1:250,000", "Joint Operations Graphic", CADRG_DATA_TYPE, 250000),
        PFPS_MAP_TYPE_JN("JN", "JNC", "1:2,000,000", "Jet Navigation Chart", CADRG_DATA_TYPE, 2000000),
        PFPS_MAP_TYPE_JO("JO", "OPG", "1:250,000", "Operational Planning Graphic", CADRG_DATA_TYPE, 250000),
        PFPS_MAP_TYPE_JR("JR", "JOG-R", "1:250,000", "Joint Operations Graphic - Radar", CADRG_DATA_TYPE, 250000),
        PFPS_MAP_TYPE_K1("K1", "ICM", "1:8,000", "Image City Maps", CADRG_DATA_TYPE, 8000, "ICM 1:8K"),
        PFPS_MAP_TYPE_K2("K2", "ICM", "1:10,000", "Image City Maps", CADRG_DATA_TYPE, 10000, "ICM 1:10K"),
        PFPS_MAP_TYPE_K3("K3", "ICM", "1:10,560", "Image City Maps", CADRG_DATA_TYPE, 10560, "ICM 1:10.56K"),
        PFPS_MAP_TYPE_K7("K7", "ICM", "1:12,500", "Image City Maps", CADRG_DATA_TYPE, 12500, "ICM 1:12.5K"),
        PFPS_MAP_TYPE_K8("K8", "ICM", "1:12,800", "Image City Maps", CADRG_DATA_TYPE, 12800, "ICM 1:12.8K"),
        PFPS_MAP_TYPE_KB("KB", "ICM", "1:15,000", "Image City Maps", CADRG_DATA_TYPE, 15000, "ICM 1:15K"),
        PFPS_MAP_TYPE_KE("KE", "ICM", "1:16,666", "Image City Maps", CADRG_DATA_TYPE, 16666, "ICM 1:16.67K"),
        PFPS_MAP_TYPE_KM("KM", "ICM", "1:21,120", "Image City Maps", CADRG_DATA_TYPE, 21120, "ICM 1:21.12K"),
        PFPS_MAP_TYPE_KR("KR", "ICM", "1:25,000", "Image City Maps", CADRG_DATA_TYPE, 25000, "ICM 1:25K"),
        PFPS_MAP_TYPE_KS("KS", "ICM", "1:26,000", "Image City Maps", CADRG_DATA_TYPE, 26000, "ICM 1:26K"),
        PFPS_MAP_TYPE_KU("KU", "ICM", "1:36,000", "Image City Maps", CADRG_DATA_TYPE, 36000, "ICM 1:36K"),
        PFPS_MAP_TYPE_L1("L1", "LFC-1", "1:500,000", "Low Flying Chart (TBD #1)", CADRG_DATA_TYPE, 500000),
        PFPS_MAP_TYPE_L2("L2", "LFC-2", "1:500,000", "Low Flying Chart (TBD #2)", CADRG_DATA_TYPE, 500000),
        PFPS_MAP_TYPE_L3("L3", "LFC-3", "1:500,000", "Low Flying Chart (TBD #3)", CADRG_DATA_TYPE, 500000),
        PFPS_MAP_TYPE_L4("L4", "LFC-4", "1:500,000", "Low Flying Chart (TBD #4)", CADRG_DATA_TYPE, 500000),
        PFPS_MAP_TYPE_L5("L5", "LFC-5", "1:500,000", "Low Flying Chart (TBD #5)", CADRG_DATA_TYPE, 500000),
        PFPS_MAP_TYPE_LF("LF", "LFC-FR (Day)", "1:500,000", "Low Flying Chart (Day) - Host Nation", CADRG_DATA_TYPE, 500000),
        PFPS_MAP_TYPE_LN("LN", "LFC (Night)", "1:500,000", "Low Flying Chart (Night) - Host Nation", CADRG_DATA_TYPE, 500000),
        PFPS_MAP_TYPE_M1("M1", "MIM", "Various", "Military Installation Map (TBD #1)", CADRG_DATA_TYPE, -1),
        PFPS_MAP_TYPE_M2("M2", "MIM", "Various", "Military Installation Map (TBD #2)", CADRG_DATA_TYPE, -1),
        PFPS_MAP_TYPE_MH("MH", "MIM", "1:25,000", "Military Installation Maps", CADRG_DATA_TYPE, 25000, "MIM 1:25K"),
        PFPS_MAP_TYPE_MI("MI", "MIM", "1:50,000", "Military Installation Maps", CADRG_DATA_TYPE, 50000, "MIM 1:50K"),
        PFPS_MAP_TYPE_MJ("MJ", "MIM", "1:100,000", "Military Installation Maps", CADRG_DATA_TYPE, 100000, "MIM 1:100K"),
        PFPS_MAP_TYPE_MM("MM", "---", "Various", "Miscellaneous Maps & Charts", CADRG_DATA_TYPE, -1, "Misc Maps"),
        PFPS_MAP_TYPE_OA("OA", "OPAREA", "Various", "Naval Range Operating Area Chart", CADRG_DATA_TYPE, -1),
        PFPS_MAP_TYPE_OH("OH", "VHRC", "1:1,000,000", "VFR Helicopter Route Chart", CADRG_DATA_TYPE, 1000000),
        PFPS_MAP_TYPE_ON("ON", "ONC", "1:1,000,000", "Operational Navigation Chart", CADRG_DATA_TYPE, 1000000),
        PFPS_MAP_TYPE_OW("OW", "WAC", "1:1,000,000", "High Flying Chart - Host Nation", CADRG_DATA_TYPE, 1000000),
        PFPS_MAP_TYPE_P1("P1", "---", "1:25,000", "Special Military Map - Overlay", CADRG_DATA_TYPE, 25000, "SMM 1:25K"),
        PFPS_MAP_TYPE_P2("P2", "---", "1:25,000", "Special Military Purpose", CADRG_DATA_TYPE, 25000, "SMP 1:25K"),
        PFPS_MAP_TYPE_P3("P3", "---", "1:25,000", "Special Military Purpose", CADRG_DATA_TYPE, 25000, "SMP 1:25K"),
        PFPS_MAP_TYPE_P4("P4", "---", "1:25,000", "Special Military Purpose", CADRG_DATA_TYPE, 25000, "SMP 1:25K"),
        PFPS_MAP_TYPE_P5("P5", "---", "1:50,000", "Special Military Map - Overlay", CADRG_DATA_TYPE, 50000, "SMM 1:50K"),
        PFPS_MAP_TYPE_P6("P6", "---", "1:50,000", "Special Military Purpose", CADRG_DATA_TYPE, 50000, "SMP 1:50K"),
        PFPS_MAP_TYPE_P7("P7", "---", "1:50,000", "Special Military Purpose", CADRG_DATA_TYPE, 50000, "SMP 1:50K"),
        PFPS_MAP_TYPE_P8("P8", "---", "1:50,000", "Special Military Purpose", CADRG_DATA_TYPE, 50000, "SMP 1:50K"),
        PFPS_MAP_TYPE_P9("P9", "---", "1:100,000", "Special Military Map - Overlay", CADRG_DATA_TYPE, 100000, "SMM 1:100K"),
        PFPS_MAP_TYPE_PA("PA", "---", "1:100,000", "Special Military Purpose", CADRG_DATA_TYPE, 100000, "SMP 1:100K"),
        PFPS_MAP_TYPE_PB("PB", "---", "1:100,000", "Special Military Purpose", CADRG_DATA_TYPE, 100000, "SMP 1:100K"),
        PFPS_MAP_TYPE_PC("PC", "---", "1:100,000", "Special Military Purpose", CADRG_DATA_TYPE, 100000, "SMP 1:100K"),
        PFPS_MAP_TYPE_PD("PD", "---", "1:250,000", "Special Military Map - Overlay", CADRG_DATA_TYPE, 250000, "SMM 1:250K"),
        PFPS_MAP_TYPE_PE("PA", "---", "1:250,000", "Special Military Purpose", CADRG_DATA_TYPE, 250000, "SMP 1:250K"),
        PFPS_MAP_TYPE_PF("PB", "---", "1:250,000", "Special Military Purpose", CADRG_DATA_TYPE, 250000, "SMP 1:250K"),
        PFPS_MAP_TYPE_PG("PC", "---", "1:250,000", "Special Military Purpose", CADRG_DATA_TYPE, 250000, "SMP 1:250K"),
        PFPS_MAP_TYPE_PH("PH", "---", "1:500,000", "Special Military Map - Overlay", CADRG_DATA_TYPE, 500000, "SMM 1:500K"),
        PFPS_MAP_TYPE_PI("PI", "---", "1:500,000", "Special Military Purpose", CADRG_DATA_TYPE, 500000, "SMP 1:500K"),
        PFPS_MAP_TYPE_PJ("PJ", "---", "1:500,000", "Special Military Purpose", CADRG_DATA_TYPE, 500000, "SMP 1:500K"),
        PFPS_MAP_TYPE_PK("PK", "---", "1:500,000", "Special Military Purpose", CADRG_DATA_TYPE, 500000, "SMP 1:500K"),
        PFPS_MAP_TYPE_PL("PL", "---", "1:1,000,000", "Special Military Map - Overlay", CADRG_DATA_TYPE, 1000000, "SMM 1:1M"),
        PFPS_MAP_TYPE_PM("PM", "---", "1:1,000,000", "Special Military Purpose", CADRG_DATA_TYPE, 1000000, "SMP 1:1M"),
        PFPS_MAP_TYPE_PN("PN", "---", "1:1,000,000", "Special Military Purpose", CADRG_DATA_TYPE, 1000000, "SMP 1:1M"),
        PFPS_MAP_TYPE_PO("PO", "---", "1:1,000,000", "Special Military Purpose", CADRG_DATA_TYPE, 1000000, "SMP 1:1M"),
        PFPS_MAP_TYPE_PP("PP", "---", "1:2,000,000", "Special Military Map - Overlay", CADRG_DATA_TYPE, 2000000, "SMM 1:2M"),
        PFPS_MAP_TYPE_PQ("PQ", "---", "1:2,000,000", "Special Military Purpose", CADRG_DATA_TYPE, 2000000, "SMP 1:2M"),
        PFPS_MAP_TYPE_PR("PR", "---", "1:2,000,000", "Special Military Purpose", CADRG_DATA_TYPE, 2000000, "SMP 1:2M"),
        PFPS_MAP_TYPE_PS("PS", "---", "1:5,000,000", "Special Military Map - Overlay", CADRG_DATA_TYPE, 5000000, "SMM 1:5M"),
        PFPS_MAP_TYPE_PT("PT", "---", "1:5,000,000", "Special Military Purpose", CADRG_DATA_TYPE, 5000000, "SMP 1:5M"),
        PFPS_MAP_TYPE_PU("PU", "---", "1:5,000,000", "Special Military Purpose", CADRG_DATA_TYPE, 5000000, "SMP 1:5M"),
        PFPS_MAP_TYPE_PV("PV", "---", "1:5,000,000", "Special Military Purpose", CADRG_DATA_TYPE, 5000000, "SMP 1:5M"),
        PFPS_MAP_TYPE_R1("R1", "---", "1:50,000", "Range Charts", CADRG_DATA_TYPE, 50000, "Range Charts 1:50K"),
        PFPS_MAP_TYPE_R2("R2", "---", "1:100,000", "Range Charts", CADRG_DATA_TYPE, 100000, "Range Charts 1:100K"),
        PFPS_MAP_TYPE_R3("R3", "---", "1:250,000", "Range Charts", CADRG_DATA_TYPE, 250000, "Range Charts 1:250K"),
        PFPS_MAP_TYPE_R4("R4", "---", "1:500,000", "Range Charts", CADRG_DATA_TYPE, 500000, "Range Charts 1:500K"),
        PFPS_MAP_TYPE_R5("R5", "---", "1:1,000,000", "Range Charts", CADRG_DATA_TYPE, 1000000, "Range Charts 1:1M"),
        PFPS_MAP_TYPE_RC("RC", "RGS-100", "1:100,000", "Russian General Staff Maps", CADRG_DATA_TYPE, 100000),
        PFPS_MAP_TYPE_RL("RL", "RGS-50", "1:50,000", "Russian General Staff Maps", CADRG_DATA_TYPE, 50000),
        PFPS_MAP_TYPE_RR("RR", "RGS-200", "1:200,000", "Russian General Staff Maps", CADRG_DATA_TYPE, 200000),
        PFPS_MAP_TYPE_RV("RV", "Riverine", "1:50,000", "Riverine Map 1:50,000 scale", CADRG_DATA_TYPE, 50000),
        PFPS_MAP_TYPE_TC("TC", "TLM 100", "1:100,000", "Topographic Line Map 1:100,0000 scale", CADRG_DATA_TYPE, 100000),
        PFPS_MAP_TYPE_TF("TF", "TFC (Day)", "1:250000", "Transit Flying Chart (Day)", CADRG_DATA_TYPE, 250000),
        PFPS_MAP_TYPE_TL("TL", "TLM50", "1:50,000", "Topographic Line Map", CADRG_DATA_TYPE, 50000),
        PFPS_MAP_TYPE_TN("TN", "TFC (Night)", "1:250,000", "Transit Flying Chart (Night) - Host Nation", CADRG_DATA_TYPE, 250000),
        PFPS_MAP_TYPE_TP("TP", "TPC", "1:500,000", "Tactical Pilotage Chart", CADRG_DATA_TYPE, 500000),
        PFPS_MAP_TYPE_TQ("TQ", "TLM24", "1:24,000", "Topographic Line Map 1:24,000 scale", CADRG_DATA_TYPE, 24000),
        PFPS_MAP_TYPE_TR("TR", "TLM200", "1:200,000", "Topographic Line Map 1:200,000 scale", CADRG_DATA_TYPE, 200000),
        PFPS_MAP_TYPE_TT("TT", "TLM25", "1:25,000", "Topographic Line Map 1:25,000 scale", CADRG_DATA_TYPE, 25000),
        PFPS_MAP_TYPE_UL("UL", "TLM50-Other", "1:50,000", "Topographic Line Map (other 1:50,000 scale)", CADRG_DATA_TYPE, 50000),
        PFPS_MAP_TYPE_V1("V1", "HRC Inset", "1:50,000", "Helicopter Route Chart Inset", CADRG_DATA_TYPE, 50000, "HRC Insert 1:50K"),
        PFPS_MAP_TYPE_V2("V2", "HRC Inset", "1:62,500", "Helicopter Route Chart Inset", CADRG_DATA_TYPE, 62500, "HRC Insert 1:62.5K"),
        PFPS_MAP_TYPE_V3("V3", "HRC Inset", "1:90,000", "Helicopter Route Chart Inset", CADRG_DATA_TYPE, 90000, "HRC Insert 1:90K"),
        PFPS_MAP_TYPE_V4("V4", "HRC Inset", "1:250,000", "Helicopter Route Chart Inset", CADRG_DATA_TYPE, 250000, "HRC Insert 1:250K"),
        PFPS_MAP_TYPE_VH("VH", "HRC", "1:125,000", "Helicopter Route Chart", CADRG_DATA_TYPE, 125000),
        PFPS_MAP_TYPE_VN("VN", "VNC", "1:500,000", "Visual Navigation Charts", CADRG_DATA_TYPE, 500000),
        PFPS_MAP_TYPE_VT("VT", "VTAC", "1:250,000", "VFR Terminal Area Chart", CADRG_DATA_TYPE, 250000),
        PFPS_MAP_TYPE_WA("WA", "---", "1:250,000", "IFR Enroute Low", CADRG_DATA_TYPE, 250000, "IFR Lo 1:250K"),
        PFPS_MAP_TYPE_WB("WB", "---", "1:500,000", "IFR Enroute Low", CADRG_DATA_TYPE, 500000, "IFR Lo 1:500K"),
        PFPS_MAP_TYPE_WC("WC", "---", "1:750,000", "IFR Enroute Low", CADRG_DATA_TYPE, 750000, "IFR Lo 1:750K"),
        PFPS_MAP_TYPE_WD("WD", "---", "1:1,000,000", "IFR Enroute Low", CADRG_DATA_TYPE, 1000000, "IFR Lo 1:1M"),
        PFPS_MAP_TYPE_WE("WE", "---", "1:1,500,000", "IFR Enroute Low", CADRG_DATA_TYPE, 1500000, "IFR Lo 1:1.5M"),
        PFPS_MAP_TYPE_WF("WF", "---", "1:2,000,000", "IFR Enroute Low", CADRG_DATA_TYPE, 2000000, "IFR Lo 1:2M"),
        PFPS_MAP_TYPE_WG("WG", "---", "1:2,500,000", "IFR Enroute Low", CADRG_DATA_TYPE, 2500000, "IFR Lo 1:2.5M"),
        PFPS_MAP_TYPE_WH("WH", "---", "1:3,000,000", "IFR Enroute Low", CADRG_DATA_TYPE, 3000000, "IFR Lo 1:3M"),
        PFPS_MAP_TYPE_WI("WI", "---", "1:3,500,000", "IFR Enroute Low", CADRG_DATA_TYPE, 3500000, "IFR Lo 1:3.5M"),
        PFPS_MAP_TYPE_WK("WK", "---", "1:4,500,000", "IFR Enroute Low", CADRG_DATA_TYPE, 4500000, "IFR Lo 1:4.5M"),
        PFPS_MAP_TYPE_XD("XD", "---", "1:1,000,000", "IFR Enroute Low", CADRG_DATA_TYPE, 1000000, "IFR Hi 1:1M"),
        PFPS_MAP_TYPE_XE("XE", "---", "1:1,500,000", "IFR Enroute Low", CADRG_DATA_TYPE, 1500000, "IFR Hi 1:1.5M"),
        PFPS_MAP_TYPE_XF("XF", "---", "1:2,000,000", "IFR Enroute High", CADRG_DATA_TYPE, 2000000, "IFR Hi 1:2M"),
        PFPS_MAP_TYPE_XG("XG", "---", "1:2,500,000", "IFR Enroute High", CADRG_DATA_TYPE, 2500000, "IFR Hi 1:2.5M"),
        PFPS_MAP_TYPE_XH("XH", "---", "1:3,000,000", "IFR Enroute High", CADRG_DATA_TYPE, 3000000, "IFR Hi 1:3M"),
        PFPS_MAP_TYPE_XI("XI", "---", "1:3,500,000", "IFR Enroute High", CADRG_DATA_TYPE, 3500000, "IFR Hi 1:3.5M"),
        PFPS_MAP_TYPE_XJ("XJ", "---", "1:4,000,000", "IFR Enroute High", CADRG_DATA_TYPE, 4000000, "IFR Hi 1:4M"),
        PFPS_MAP_TYPE_XK("XK", "---", "1:4,500,000", "IFR Enroute High", CADRG_DATA_TYPE, 4500000, "IFR Hi 1:4.5M"),
        PFPS_MAP_TYPE_Y9("Y9", "---", "1:16,500,000", "IFR Enroute Area", CADRG_DATA_TYPE, 16500000, "IFR Area 1:16.5M"),
        PFPS_MAP_TYPE_YA("YA", "---", "1:250,000", "IFR Enroute Area", CADRG_DATA_TYPE, 250000, "IFR Area 1:250K"),
        PFPS_MAP_TYPE_YB("YB", "---", "1:500,000", "IFR Enroute Area", CADRG_DATA_TYPE, 500000, "IFR Area 1:500K"),
        PFPS_MAP_TYPE_YC("YC", "---", "1:750,000", "IFR Enroute Area", CADRG_DATA_TYPE, 750000, "IFR Area 1:750K"),
        PFPS_MAP_TYPE_YD("YD", "---", "1:1,000,000", "IFR Enroute Area", CADRG_DATA_TYPE, 1000000, "IFR Area 1:1M"),
        PFPS_MAP_TYPE_YE("YE", "---", "1:1,500,000", "IFR Enroute Area", CADRG_DATA_TYPE, 1500000, "IFR Area 1:1.5M"),
        PFPS_MAP_TYPE_YF("YF", "---", "1:2,000,000", "IFR Enroute Area", CADRG_DATA_TYPE, 2000000, "IFR Area 1:2M"),
        PFPS_MAP_TYPE_YI("YI", "---", "1:3,500,000", "IFR Enroute Area", CADRG_DATA_TYPE, 3500000, "IFR Area 1:3.5M"),
        PFPS_MAP_TYPE_YJ("YJ", "---", "1:4,000,000", "IFR Enroute Area", CADRG_DATA_TYPE, 4000000, "IFR Area 1:4M"),
        PFPS_MAP_TYPE_YZ("YZ", "---", "1:12,000,000", "IFR Enroute Area", CADRG_DATA_TYPE, 12000000, "IFR Area 1:12M"),
        PFPS_MAP_TYPE_Z8("Z8", "---", "1:16,000,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 16000000, "IFR Hi/Lo 1:16M"),
        PFPS_MAP_TYPE_ZA("ZA", "---", "1:250,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 250000, "IFR Hi/Lo 1:250K"),
        PFPS_MAP_TYPE_ZB("ZB", "---", "1:500,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 500000, "IFR Hi/Lo 1:500K"),
        PFPS_MAP_TYPE_ZC("ZC", "---", "1:750,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 750000, "IFR Hi/Lo 1:750K"),
        PFPS_MAP_TYPE_ZD("ZD", "---", "1:1,000,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 1000000, "IFR Hi/Lo 1:1M"),
        PFPS_MAP_TYPE_ZE("ZE", "---", "1:1,500,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 1500000, "IFR Hi/Lo 1:1.5M"),
        PFPS_MAP_TYPE_ZF("ZF", "---", "1:2,000,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 2000000, "IFR Hi/Lo 1:2M"),
        PFPS_MAP_TYPE_ZG("ZG", "---", "1:2,500,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 2500000, "IFR Hi/Lo 1:2.5M"),
        PFPS_MAP_TYPE_ZH("ZH", "---", "1:3,000,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 3000000, "IFR Hi/Lo 1:3M"),
        PFPS_MAP_TYPE_ZI("ZI", "---", "1:3,500,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 3500000, "IFR Hi/Lo 1:3.5M"),
        PFPS_MAP_TYPE_ZJ("ZJ", "---", "1:4,000,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 4000000, "IFR Hi/Lo 1:4M"),
        PFPS_MAP_TYPE_ZK("ZK", "---", "1:4,500,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 4500000, "IFR Hi/Lo 1:4.5M"),
        PFPS_MAP_TYPE_ZT("ZT", "---", "1:9,000,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 9000000, "IFR Hi/Lo 1:9M"),
        PFPS_MAP_TYPE_ZV("ZV", "---", "1:10,000,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 10000000, "IFR Hi/Lo 1:10M"),
        PFPS_MAP_TYPE_ZZ("ZZ", "---", "1:12,000,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 12000000, "IFR Hi/Lo 1:12M");

        private final String code;
        private final String abbr;
        private final String scaleOrRes;
        private final String descripString;
        private final String rpfDataType;
        private final double scaleOrGSD;
        private final String prettyName;

        private PfpsMapDataType(String code, String abbr, String scaleOrRes, String descripString,
                String rpfDataType, double scaleOrGSD) {
            this(code, abbr, scaleOrRes, descripString, rpfDataType, scaleOrGSD, abbr);
        }

        private PfpsMapDataType(String code, String abbr, String scaleOrRes, String descripString,
                String rpfDataType, double scaleOrGSD, String prettyName) {
            this.code = code;
            this.abbr = abbr;
            this.scaleOrRes = scaleOrRes;
            this.descripString = descripString;
            this.rpfDataType = rpfDataType;
            this.scaleOrGSD = scaleOrGSD;
            this.prettyName = prettyName;
        }

        private static Map<String, PfpsMapDataType> enumConstantDirectory = null;

        private static synchronized Map<String, PfpsMapDataType> enumConstantDirectory() {
            if (enumConstantDirectory == null) {
                PfpsMapDataType[] universe = PfpsMapDataType.class.getEnumConstants();
                if(universe != null) {
                    enumConstantDirectory = new HashMap<String, PfpsMapDataType>(2 * universe.length);
                    for (PfpsMapDataType pfpsMapDataType : universe) {
                        enumConstantDirectory.put(pfpsMapDataType.code, pfpsMapDataType);
                    }
                } else {
                    Log.e(TAG, "Unable to get enum constants for class PfpsMapDataType");
                }
            }
            return enumConstantDirectory;
        }

        private static PfpsMapDataType getPfpsMapDataType(String code) {
            PfpsMapDataType pfpsMapDataType = enumConstantDirectory().get(code);
            return pfpsMapDataType;
        }

        private static boolean isCADRGDataType(String rpfDataType) {
            return CADRG_DATA_TYPE.equals(rpfDataType);
        }

        private static boolean isCIBDataType(String rpfDataType) {
            return CIB_DATA_TYPE.equals(rpfDataType);
        }

        private static boolean isCIBMapDataType(String code) {
            PfpsMapDataType pfpsMapDataType = getPfpsMapDataType(code);
            return pfpsMapDataType != null && isCIBDataType(pfpsMapDataType.rpfDataType);
        }
    }

}
