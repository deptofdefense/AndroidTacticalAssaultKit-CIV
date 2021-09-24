#include "PfpsUtils.h"
#include "PfpsMapTypeFrame.h"
#include "math/Utils.h"
#include <map>
#include <string>
#include "util/IO.h"

using namespace atakmap::math;

namespace {
    const int RPF_FILENAME_LENGTH = 12;
    const int ECRG_FILENAME_LENGTH = 18;
    const char *CADRG_DATA_TYPE = "CADRG";
    const char *CIB_DATA_TYPE = "CIB";

    // MIL-A-89007, Section 70
    const int PIXEL_ROWS_PER_FRAME = 1536;
    const int NORTH_SOUTH_PIXEL_SPACING_CONSTANT = 400384;
    const int EAST_WEST_PIXEL_SPACING_CONSTANT[] = {
        369664, 302592, 245760, 199168, 163328, 137216, 110080, 82432
    };
    const int EQUATORWARD_NOMINAL_BOUNDARY[] = {
        0, 32, 48, 56, 64, 68, 72, 76, 80
    };
    const int POLEWARD_NOMINAL_BOUNDARY[] = {
        32, 48, 56, 64, 68, 72, 76, 80, 90
    };


    double signum(double val) {
        return (0 < val) - (val < 0);
    }

    // zone util
    int indexFor(char zoneCode) {
        const int NUM_START_INDEX = 0;
        const int ALPHA_START_INDEX = 9;

        int index = -1;
        char upperChar = toupper(zoneCode);
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
    bool isZoneInUpperHemisphere(char zoneCode) {
        char upperChar = toupper(zoneCode);
        return (upperChar >= '1' && upperChar <= '9');
    }

    int nominalBoundary(char zoneCode, const int *boundaryArray) {
        int index = indexFor(zoneCode) % 9;
        if (index < 0)
            return -1;

        if (!isZoneInUpperHemisphere(zoneCode))
            return 0 - boundaryArray[index];
        return boundaryArray[index];
    }

    // MIL-C-89038, Section 60.1
    int northSouthPixelConstant_CADRG(double northSouthPixelConstant, double scale) {
        double S = 1000000 / scale;
        double tmp = northSouthPixelConstant * S;
        tmp = 512.0 * (int)::ceil(tmp / 512.0);
        tmp /= 4.0;
        tmp /= (150.0 / 100.0);
        return 256 * (int)round(tmp / 256.0);
    }

    int eastWestPixelConstant_CADRG(double eastWestPixelSpacingConstant, double scale) {
        double S = 1000000.0 / scale;
        double tmp = eastWestPixelSpacingConstant * S;
        tmp = 512.0 * (int)ceil(tmp / 512.0);
        tmp /= (150.0 / 100.0);
        return 256 * (int)round(tmp / 256.0);
    }

    // MIL-PRF-89041A, Section A.5.1.1
    int northSouthPixelConstant_CIB(double northSouthPixelSpacingConstant,
                                    double groundSampleDistance) {
        double S = 100.0 / groundSampleDistance;
        double tmp = northSouthPixelSpacingConstant * S;
        tmp = 512.0 * (int)ceil(tmp / 512.0);
        tmp /= 4.0;
        return 256 * (int)round(tmp / 256.0);
    }

    int eastWestPixelConstant_CIB(double eastWestPixelSpacingConstant,
                                  double groundSampleDistance) {
        double S = 100.0 / groundSampleDistance;
        double tmp = eastWestPixelSpacingConstant * S;
        return 512 * (int)ceil(tmp / 512.0);
    }

    // MIL-C-89038, Section 60.1.5.b
    // MIL-PRF-89041A, Section A.5.1.2.b
    double polewardExtent(double polewardNominalBoundary,
                          double northSouthPixelConstant, double pixelRowsPerFrame) {
        double nsPixelsPerDegree = northSouthPixelConstant / 90.0;
        return signum(polewardNominalBoundary)
            * clamp(ceil(nsPixelsPerDegree * std::abs(polewardNominalBoundary)
            / pixelRowsPerFrame)
            * pixelRowsPerFrame / nsPixelsPerDegree, 0.0, 90.0);
    }

    double equatorwardExtent(double equatorwardNominalBoundary,
                             double northSouthPixelConstant, double pixelRowsPerFrame) {
        double nsPixelsPerDegree = northSouthPixelConstant / 90.0;
        return signum(equatorwardNominalBoundary)
            * clamp((int)(nsPixelsPerDegree * std::abs(equatorwardNominalBoundary) / pixelRowsPerFrame)
            * pixelRowsPerFrame / nsPixelsPerDegree, 0.0, 90.0);
    }

    // MIL-PRF-89038, Section 60.1
    // MIL-PRF-89041A, Section A.5.1
    int latitudinalFrames(double polewardExtentDegrees,
                          double equatorwardExtentDegrees, double northSouthPixelConstant,
                          double pixelRowsPerFrame) {
        double nsPixelsPerDegree = northSouthPixelConstant / 90.0;
        double extent = std::abs(polewardExtentDegrees - equatorwardExtentDegrees);
        return (int)round(extent * nsPixelsPerDegree / pixelRowsPerFrame);
    }

    int longitudinalFrames(double eastWestPixelConstant, double pixelRowsPerFrame) {
        return (int)ceil(eastWestPixelConstant / pixelRowsPerFrame);
    }

    // MIL-C-89038, Section 30.6
    // MIL-PRF-89041A, Section A.3.6
    int maxFrameNumber(int rowFrames, int columnFrames) {
        return (rowFrames * columnFrames) - 1;
    }

    int frameRow(int frameNumber, int columnFrames) {
        return (int)(frameNumber / (double)columnFrames);
    }

    int frameColumn(int frameNumber, int frameRow, int columnFrames) {
        return frameNumber - (frameRow * columnFrames);
    }

    // MIL-C-89038, Section 30.3
    // MIL-PRF-89041A, Section A.3.3
    double frameOriginLatitude(int row, double northSouthPixelConstant,
                               double pixelRowsPerFrame, double zoneOriginLatitude) {
        return (90.0 / northSouthPixelConstant) * pixelRowsPerFrame * (row + 1) + zoneOriginLatitude;
    }

    double frameOriginLongitude(int column, double eastWestPixelConstant,
                                double pixelRowsPerFrame) {
        return (360.0 / eastWestPixelConstant) * pixelRowsPerFrame * column - 180.0;
    }

    double frameDeltaLatitude(double northSouthPixelConstant,
                              double pixelRowsPerFrame) {
        return (90.0 / northSouthPixelConstant) * pixelRowsPerFrame;
    }

    double frameDeltaLongitude(double eastWestPixelConstant, double pixelRowsPerFrame) {
        return (360.0 / eastWestPixelConstant) * pixelRowsPerFrame;
    }


    // MIL-A-89007, Section 70
    int eastWestPixelSpacingConstant(char zoneCode) {
        int index = indexFor(zoneCode) % 8;
        if (index < 0)
            return -1;

        return EAST_WEST_PIXEL_SPACING_CONSTANT[index];
    }

    int northSouthPixelSpacingConstant() {
        return NORTH_SOUTH_PIXEL_SPACING_CONSTANT;
    }

    int equatorwardNominalBoundary(char zoneCode) {
        return nominalBoundary(zoneCode, EQUATORWARD_NOMINAL_BOUNDARY);
    }

    int polewardNominalBoundary(char zoneCode) {
        return nominalBoundary(zoneCode, POLEWARD_NOMINAL_BOUNDARY);
    }



    struct PfpsMapDataType {
        const char *code;
        const char *abbr;
        const char *scaleOrRes;
        const char *descripString;
        const char *rpfDataType;
        const double scaleOrGSD;
        const char *prettyName;

        PfpsMapDataType(const char *code, const char *abbr, const char *scaleOrRes, const char *descripString,
                        const char *rpfDataType, const double scaleOrGSD) :
                        code(code),
                        abbr(abbr),
                        scaleOrRes(scaleOrRes),
                        descripString(descripString),
                        rpfDataType(rpfDataType),
                        scaleOrGSD(scaleOrGSD),
                        prettyName(abbr)
        {
            enumConstantDirectoryMap[code] = this;
        }

        PfpsMapDataType(const char *code, const char *abbr, const char *scaleOrRes, const char *descripString,
                        const char *rpfDataType, const double scaleOrGSD, const char *prettyName) :
                        code(code),
                        abbr(abbr),
                        scaleOrRes(scaleOrRes),
                        descripString(descripString),
                        rpfDataType(rpfDataType),
                        scaleOrGSD(scaleOrGSD),
                        prettyName(prettyName)
        {
            enumConstantDirectoryMap[code] = this;
        }


        static std::map<std::string, const PfpsMapDataType *> enumConstantDirectoryMap;

        static const std::map<std::string, const PfpsMapDataType *> *getEnumConstantDirectory() {
            return &enumConstantDirectoryMap;
        }

        static const PfpsMapDataType *getPfpsMapDataType(const char *code) {
            const PfpsMapDataType *pfpsMapDataType = nullptr;

            std::map<std::string, const PfpsMapDataType *>::iterator entry;
            entry = enumConstantDirectoryMap.find(code);
            if (entry != enumConstantDirectoryMap.end())
                pfpsMapDataType = entry->second;

            return pfpsMapDataType;
        }

        static bool isCADRGDataType(const char *rpfDataType) {
            return strcmp(CADRG_DATA_TYPE, rpfDataType) == 0;
        }

        static bool isCIBDataType(const char *rpfDataType) {
            return strcmp(CIB_DATA_TYPE, rpfDataType) == 0;
        }

        static bool isCIBMapDataType(const char *code) {
            const PfpsMapDataType *pfpsMapDataType = getPfpsMapDataType(code);
            return pfpsMapDataType != nullptr && isCIBDataType(pfpsMapDataType->rpfDataType);
        }

    };

    std::map<std::string, const PfpsMapDataType *> PfpsMapDataType::enumConstantDirectoryMap;




    // MIL-STD-2411-1, Section 5.1.4
#define PFPS_MAP_TYPE(a, b, c, d, e, f) const PfpsMapDataType PFPS_MAP_TYPE_ ## a (#a, b, c, d, e, f);
#define PFPS_MAP_TYPE2(a, b, c, d, e, f, g) const PfpsMapDataType PFPS_MAP_TYPE_##a(#a, b, c, d, e, f, g);
    PFPS_MAP_TYPE2(A1, "CM", "1:10,000", "Combat Charts, 1:10,000 scale", CADRG_DATA_TYPE, 10000, "CM 1:10K");
    PFPS_MAP_TYPE2(A2, "CM", "1:25,000", "Combat Charts, 1:25,000 scale", CADRG_DATA_TYPE, 25000, "CM 1:25K");
    PFPS_MAP_TYPE2(A3, "CM", "1:50,000", "Combat Charts, 1:50,000 scale", CADRG_DATA_TYPE, 50000, "CM 1:50K");
    PFPS_MAP_TYPE2(A4, "CM", "1:100,000", "Combat Charts, 1:100,000 scale", CADRG_DATA_TYPE, 100000, "CM 1:100K");
    PFPS_MAP_TYPE(AT, "ATC", "1:200,000", "Series 200 Air Target Chart", CADRG_DATA_TYPE, 200000);
    PFPS_MAP_TYPE2(C1, "CG", "1:10,000", "City Graphics", CADRG_DATA_TYPE, 10000, "CG 1:10K");
    PFPS_MAP_TYPE2(C2, "CG", "1:10,560", "City Graphics", CADRG_DATA_TYPE, 10560, "CG 1:10.56K");
    PFPS_MAP_TYPE2(C3, "CG", "1:11,000", "City Graphics", CADRG_DATA_TYPE, 11000, "CG 1:11K");
    PFPS_MAP_TYPE2(C4, "CG", "1:11,800", "City Graphics", CADRG_DATA_TYPE, 11800, "CG 1:11.8K");
    PFPS_MAP_TYPE2(C5, "CG", "1:12,000", "City Graphics", CADRG_DATA_TYPE, 12000, "CG 1:12K");
    PFPS_MAP_TYPE2(C6, "CG", "1:12,500", "City Graphics", CADRG_DATA_TYPE, 12500, "CG 1:12.5K");
    PFPS_MAP_TYPE2(C7, "CG", "1:12,800", "City Graphics", CADRG_DATA_TYPE, 12800, "CG 1:12.8K");
    PFPS_MAP_TYPE2(C8, "CG", "1:14,000", "City Graphics", CADRG_DATA_TYPE, 14000, "CG 1:14K");
    PFPS_MAP_TYPE2(C9, "CG", "1:14,700", "City Graphics", CADRG_DATA_TYPE, 14700, "CG 1:14.7K");
    PFPS_MAP_TYPE2(CA, "CG", "1:15,000", "City Graphics", CADRG_DATA_TYPE, 15000, "CG 1:15K");
    PFPS_MAP_TYPE2(CB, "CG", "1:15,500", "City Graphics", CADRG_DATA_TYPE, 15500, "CG 1:15.5K");
    PFPS_MAP_TYPE2(CC, "CG", "1:16,000", "City Graphics", CADRG_DATA_TYPE, 16000, "CG 1:16K");
    PFPS_MAP_TYPE2(CD, "CG", "1:16,666", "City Graphics", CADRG_DATA_TYPE, 16666, "CG 1:16.67K");
    PFPS_MAP_TYPE2(CE, "CG", "1:17,000", "City Graphics", CADRG_DATA_TYPE, 17000, "CG 1:17K");
    PFPS_MAP_TYPE2(CF, "CG", "1:17,500", "City Graphics", CADRG_DATA_TYPE, 17500, "CG 1:17.5K");
    PFPS_MAP_TYPE2(CG, "CG", "Various", "City Graphics", CADRG_DATA_TYPE, -1, "CG Various");
    PFPS_MAP_TYPE2(CH, "CG", "1:18,000", "City Graphics", CADRG_DATA_TYPE, 18000, "CG 1:18K");
    PFPS_MAP_TYPE2(CJ, "CG", "1:20,000", "City Graphics", CADRG_DATA_TYPE, 20000, "CG 1:20K");
    PFPS_MAP_TYPE2(CK, "CG", "1:21,000", "City Graphics", CADRG_DATA_TYPE, 21000, "CG 1:21K");
    PFPS_MAP_TYPE2(CL, "CG", "1:21,120", "City Graphics", CADRG_DATA_TYPE, 21120, "CG 1:21.12K");
    PFPS_MAP_TYPE2(CM, "CM", "Various", "Combat Charts", CADRG_DATA_TYPE, -1, "CM Various");
    PFPS_MAP_TYPE2(CN, "CG", "1:22,000", "City Graphics", CADRG_DATA_TYPE, 22000, "CG 1:22K");
    PFPS_MAP_TYPE(CO, "CO", "Various", "Coastal Charts", CADRG_DATA_TYPE, -1);
    PFPS_MAP_TYPE2(CP, "CG", "1:23,000", "City Graphics", CADRG_DATA_TYPE, 23000, "CG 1:23K");
    PFPS_MAP_TYPE2(CQ, "CG", "1:25,000", "City Graphics", CADRG_DATA_TYPE, 25000, "CG 1:25K");
    PFPS_MAP_TYPE2(CR, "CG", "1:26,000", "City Graphics", CADRG_DATA_TYPE, 26000, "CG 1:26K");
    PFPS_MAP_TYPE2(CS, "CG", "1:35,000", "City Graphics", CADRG_DATA_TYPE, 35000, "CG 1:35K");
    PFPS_MAP_TYPE2(CT, "CG", "1:36,000", "City Graphics", CADRG_DATA_TYPE, 36000, "CG 1:36K");
    PFPS_MAP_TYPE2(D1, "---", "100m", "Elevation Data from DTED level 1", "CDTED", 100.0, "DT1 Elev");
    PFPS_MAP_TYPE2(D2, "---", "30m", "Elevation Data from DTED level 2", "CDTED", 30.0, "DT2 Elev");
    PFPS_MAP_TYPE(EG, "NARC", "1:11,000,000", "North Atlantic Route Chart", CADRG_DATA_TYPE, 11000000);
    PFPS_MAP_TYPE2(ES, "SEC", "1:500,000", "VFR Sectional", CADRG_DATA_TYPE, 500000, "VFR 1:500K");
    PFPS_MAP_TYPE2(ET, "SEC", "1:250,000", "VFR Sectional Insets", CADRG_DATA_TYPE, 250000, "VFR 1:250K");
    PFPS_MAP_TYPE(F1, "TFC-1", "1:250,000", "Transit Flying Chart (TBD #1)", CADRG_DATA_TYPE, 250000);
    PFPS_MAP_TYPE(F2, "TFC-2", "1:250,000", "Transit Flying Chart (TBD #2)", CADRG_DATA_TYPE, 250000);
    PFPS_MAP_TYPE(F3, "TFC-3", "1:250,000", "Transit Flying Chart (TBD #3)", CADRG_DATA_TYPE, 250000);
    PFPS_MAP_TYPE(F4, "TFC-4", "1:250,000", "Transit Flying Chart (TBD #4)", CADRG_DATA_TYPE, 250000);
    PFPS_MAP_TYPE(F5, "TFC-5", "1:250,000", "Transit Flying Chart (TBD #5)", CADRG_DATA_TYPE, 250000);
    PFPS_MAP_TYPE(GN, "GNC", "1:5,000,000", "Global Navigation Chart", CADRG_DATA_TYPE, 5000000.0);
    PFPS_MAP_TYPE(HA, "HA", "Various", "Harbor and Approach Charts", CADRG_DATA_TYPE, -1);
    PFPS_MAP_TYPE2(I1, "---", "10m", "Imagery, 10 meter resolution", CIB_DATA_TYPE, 10.0, "CIB 10m");
    PFPS_MAP_TYPE2(I2, "---", "5m", "Imagery, 5 meter resolution", CIB_DATA_TYPE, 5.0, "CIB 5m");
    PFPS_MAP_TYPE2(I3, "---", "2m", "Imagery, 2 meter resolution", CIB_DATA_TYPE, 2.0, "CIB 2m");
    PFPS_MAP_TYPE2(I4, "---", "1m", "Imagery, 1 meter resolution", CIB_DATA_TYPE, 1.0, "CIB 1m");
    PFPS_MAP_TYPE2(I5, "---", ".5m", "Imagery, .5 (half) meter resolution", CIB_DATA_TYPE, 0.5, "CIB 50cm");
    PFPS_MAP_TYPE2(IV, "---", "Various>10m", "Imagery, greater than 10 meter resolution", CIB_DATA_TYPE, -1, "CIB 10+");
    PFPS_MAP_TYPE(JA, "JOG-A", "1:250,000", "Joint Operations Graphic - Air", CADRG_DATA_TYPE, 250000);
    PFPS_MAP_TYPE(JG, "JOG", "1:250,000", "Joint Operations Graphic", CADRG_DATA_TYPE, 250000);
    PFPS_MAP_TYPE(JN, "JNC", "1:2,000,000", "Jet Navigation Chart", CADRG_DATA_TYPE, 2000000);
    PFPS_MAP_TYPE(JO, "OPG", "1:250,000", "Operational Planning Graphic", CADRG_DATA_TYPE, 250000);
    PFPS_MAP_TYPE(JR, "JOG-R", "1:250,000", "Joint Operations Graphic - Radar", CADRG_DATA_TYPE, 250000);
    PFPS_MAP_TYPE2(K1, "ICM", "1:8,000", "Image City Maps", CADRG_DATA_TYPE, 8000, "ICM 1:8K");
    PFPS_MAP_TYPE2(K2, "ICM", "1:10,000", "Image City Maps", CADRG_DATA_TYPE, 10000, "ICM 1:10K");
    PFPS_MAP_TYPE2(K3, "ICM", "1:10,560", "Image City Maps", CADRG_DATA_TYPE, 10560, "ICM 1:10.56K");
    PFPS_MAP_TYPE2(K7, "ICM", "1:12,500", "Image City Maps", CADRG_DATA_TYPE, 12500, "ICM 1:12.5K");
    PFPS_MAP_TYPE2(K8, "ICM", "1:12,800", "Image City Maps", CADRG_DATA_TYPE, 12800, "ICM 1:12.8K");
    PFPS_MAP_TYPE2(KB, "ICM", "1:15,000", "Image City Maps", CADRG_DATA_TYPE, 15000, "ICM 1:15K");
    PFPS_MAP_TYPE2(KE, "ICM", "1:16,666", "Image City Maps", CADRG_DATA_TYPE, 16666, "ICM 1:16.67K");
    PFPS_MAP_TYPE2(KM, "ICM", "1:21,120", "Image City Maps", CADRG_DATA_TYPE, 21120, "ICM 1:21.12K");
    PFPS_MAP_TYPE2(KR, "ICM", "1:25,000", "Image City Maps", CADRG_DATA_TYPE, 25000, "ICM 1:25K");
    PFPS_MAP_TYPE2(KS, "ICM", "1:26,000", "Image City Maps", CADRG_DATA_TYPE, 26000, "ICM 1:26K");
    PFPS_MAP_TYPE2(KU, "ICM", "1:36,000", "Image City Maps", CADRG_DATA_TYPE, 36000, "ICM 1:36K");
    PFPS_MAP_TYPE(L1, "LFC-1", "1:500,000", "Low Flying Chart (TBD #1)", CADRG_DATA_TYPE, 500000);
    PFPS_MAP_TYPE(L2, "LFC-2", "1:500,000", "Low Flying Chart (TBD #2)", CADRG_DATA_TYPE, 500000);
    PFPS_MAP_TYPE(L3, "LFC-3", "1:500,000", "Low Flying Chart (TBD #3)", CADRG_DATA_TYPE, 500000);
    PFPS_MAP_TYPE(L4, "LFC-4", "1:500,000", "Low Flying Chart (TBD #4)", CADRG_DATA_TYPE, 500000);
    PFPS_MAP_TYPE(L5, "LFC-5", "1:500,000", "Low Flying Chart (TBD #5)", CADRG_DATA_TYPE, 500000);
    PFPS_MAP_TYPE(LF, "LFC-FR (Day)", "1:500,000", "Low Flying Chart (Day) - Host Nation", CADRG_DATA_TYPE, 500000);
    PFPS_MAP_TYPE(LN, "LFC (Night)", "1:500,000", "Low Flying Chart (Night) - Host Nation", CADRG_DATA_TYPE, 500000);
    PFPS_MAP_TYPE(M1, "MIM", "Various", "Military Installation Map (TBD #1)", CADRG_DATA_TYPE, -1);
    PFPS_MAP_TYPE(M2, "MIM", "Various", "Military Installation Map (TBD #2)", CADRG_DATA_TYPE, -1);
    PFPS_MAP_TYPE2(MH, "MIM", "1:25,000", "Military Installation Maps", CADRG_DATA_TYPE, 25000, "MIM 1:25K");
    PFPS_MAP_TYPE2(MI, "MIM", "1:50,000", "Military Installation Maps", CADRG_DATA_TYPE, 50000, "MIM 1:50K");
    PFPS_MAP_TYPE2(MJ, "MIM", "1:100,000", "Military Installation Maps", CADRG_DATA_TYPE, 100000, "MIM 1:100K");
    PFPS_MAP_TYPE2(MM, "---", "Various", "Miscellaneous Maps & Charts", CADRG_DATA_TYPE, -1, "Misc Maps");
    PFPS_MAP_TYPE(OA, "OPAREA", "Various", "Naval Range Operating Area Chart", CADRG_DATA_TYPE, -1);
    PFPS_MAP_TYPE(OH, "VHRC", "1:1,000,000", "VFR Helicopter Route Chart", CADRG_DATA_TYPE, 1000000);
    PFPS_MAP_TYPE(ON, "ONC", "1:1,000,000", "Operational Navigation Chart", CADRG_DATA_TYPE, 1000000);
    PFPS_MAP_TYPE(OW, "WAC", "1:1,000,000", "High Flying Chart - Host Nation", CADRG_DATA_TYPE, 1000000);
    PFPS_MAP_TYPE2(P1, "---", "1:25,000", "Special Military Map - Overlay", CADRG_DATA_TYPE, 25000, "SMM 1:25K");
    PFPS_MAP_TYPE2(P2, "---", "1:25,000", "Special Military Purpose", CADRG_DATA_TYPE, 25000, "SMP 1:25K");
    PFPS_MAP_TYPE2(P3, "---", "1:25,000", "Special Military Purpose", CADRG_DATA_TYPE, 25000, "SMP 1:25K");
    PFPS_MAP_TYPE2(P4, "---", "1:25,000", "Special Military Purpose", CADRG_DATA_TYPE, 25000, "SMP 1:25K");
    PFPS_MAP_TYPE2(P5, "---", "1:50,000", "Special Military Map - Overlay", CADRG_DATA_TYPE, 50000, "SMM 1:50K");
    PFPS_MAP_TYPE2(P6, "---", "1:50,000", "Special Military Purpose", CADRG_DATA_TYPE, 50000, "SMP 1:50K");
    PFPS_MAP_TYPE2(P7, "---", "1:50,000", "Special Military Purpose", CADRG_DATA_TYPE, 50000, "SMP 1:50K");
    PFPS_MAP_TYPE2(P8, "---", "1:50,000", "Special Military Purpose", CADRG_DATA_TYPE, 50000, "SMP 1:50K");
    PFPS_MAP_TYPE2(P9, "---", "1:100,000", "Special Military Map - Overlay", CADRG_DATA_TYPE, 100000, "SMM 1:100K");
    PFPS_MAP_TYPE2(PA, "---", "1:100,000", "Special Military Purpose", CADRG_DATA_TYPE, 100000, "SMP 1:100K");
    PFPS_MAP_TYPE2(PB, "---", "1:100,000", "Special Military Purpose", CADRG_DATA_TYPE, 100000, "SMP 1:100K");
    PFPS_MAP_TYPE2(PC, "---", "1:100,000", "Special Military Purpose", CADRG_DATA_TYPE, 100000, "SMP 1:100K");
    PFPS_MAP_TYPE2(PD, "---", "1:250,000", "Special Military Map - Overlay", CADRG_DATA_TYPE, 250000, "SMM 1:250K");
    PFPS_MAP_TYPE2(PE, "---", "1:250,000", "Special Military Purpose", CADRG_DATA_TYPE, 250000, "SMP 1:250K");
    PFPS_MAP_TYPE2(PF, "---", "1:250,000", "Special Military Purpose", CADRG_DATA_TYPE, 250000, "SMP 1:250K");
    PFPS_MAP_TYPE2(PG, "---", "1:250,000", "Special Military Purpose", CADRG_DATA_TYPE, 250000, "SMP 1:250K");
    PFPS_MAP_TYPE2(PH, "---", "1:500,000", "Special Military Map - Overlay", CADRG_DATA_TYPE, 500000, "SMM 1:500K");
    PFPS_MAP_TYPE2(PI, "---", "1:500,000", "Special Military Purpose", CADRG_DATA_TYPE, 500000, "SMP 1:500K");
    PFPS_MAP_TYPE2(PJ, "---", "1:500,000", "Special Military Purpose", CADRG_DATA_TYPE, 500000, "SMP 1:500K");
    PFPS_MAP_TYPE2(PK, "---", "1:500,000", "Special Military Purpose", CADRG_DATA_TYPE, 500000, "SMP 1:500K");
    PFPS_MAP_TYPE2(PL, "---", "1:1,000,000", "Special Military Map - Overlay", CADRG_DATA_TYPE, 1000000, "SMM 1:1M");
    PFPS_MAP_TYPE2(PM, "---", "1:1,000,000", "Special Military Purpose", CADRG_DATA_TYPE, 1000000, "SMP 1:1M");
    PFPS_MAP_TYPE2(PN, "---", "1:1,000,000", "Special Military Purpose", CADRG_DATA_TYPE, 1000000, "SMP 1:1M");
    PFPS_MAP_TYPE2(PO, "---", "1:1,000,000", "Special Military Purpose", CADRG_DATA_TYPE, 1000000, "SMP 1:1M");
    PFPS_MAP_TYPE2(PP, "---", "1:2,000,000", "Special Military Map - Overlay", CADRG_DATA_TYPE, 2000000, "SMM 1:2M");
    PFPS_MAP_TYPE2(PQ, "---", "1:2,000,000", "Special Military Purpose", CADRG_DATA_TYPE, 2000000, "SMP 1:2M");
    PFPS_MAP_TYPE2(PR, "---", "1:2,000,000", "Special Military Purpose", CADRG_DATA_TYPE, 2000000, "SMP 1:2M");
    PFPS_MAP_TYPE2(PS, "---", "1:5,000,000", "Special Military Map - Overlay", CADRG_DATA_TYPE, 5000000, "SMM 1:5M");
    PFPS_MAP_TYPE2(PT, "---", "1:5,000,000", "Special Military Purpose", CADRG_DATA_TYPE, 5000000, "SMP 1:5M");
    PFPS_MAP_TYPE2(PU, "---", "1:5,000,000", "Special Military Purpose", CADRG_DATA_TYPE, 5000000, "SMP 1:5M");
    PFPS_MAP_TYPE2(PV, "---", "1:5,000,000", "Special Military Purpose", CADRG_DATA_TYPE, 5000000, "SMP 1:5M");
    PFPS_MAP_TYPE2(R1, "---", "1:50,000", "Range Charts", CADRG_DATA_TYPE, 50000, "Range Charts 1:50K");
    PFPS_MAP_TYPE2(R2, "---", "1:100,000", "Range Charts", CADRG_DATA_TYPE, 100000, "Range Charts 1:100K");
    PFPS_MAP_TYPE2(R3, "---", "1:250,000", "Range Charts", CADRG_DATA_TYPE, 250000, "Range Charts 1:250K");
    PFPS_MAP_TYPE2(R4, "---", "1:500,000", "Range Charts", CADRG_DATA_TYPE, 500000, "Range Charts 1:500K");
    PFPS_MAP_TYPE2(R5, "---", "1:1,000,000", "Range Charts", CADRG_DATA_TYPE, 1000000, "Range Charts 1:1M");
    PFPS_MAP_TYPE(RC, "RGS-100", "1:100,000", "Russian General Staff Maps", CADRG_DATA_TYPE, 100000);
    PFPS_MAP_TYPE(RL, "RGS-50", "1:50,000", "Russian General Staff Maps", CADRG_DATA_TYPE, 50000);
    PFPS_MAP_TYPE(RR, "RGS-200", "1:200,000", "Russian General Staff Maps", CADRG_DATA_TYPE, 200000);
    PFPS_MAP_TYPE(RV, "Riverine", "1:50,000", "Riverine Map 1:50,000 scale", CADRG_DATA_TYPE, 50000);
    PFPS_MAP_TYPE(TC, "TLM 100", "1:100,000", "Topographic Line Map 1:100,0000 scale", CADRG_DATA_TYPE, 100000);
    PFPS_MAP_TYPE(TF, "TFC (Day)", "1:250000", "Transit Flying Chart (Day)", CADRG_DATA_TYPE, 250000);
    PFPS_MAP_TYPE(TL, "TLM 50", "1:50,000", "Topographic Line Map", CADRG_DATA_TYPE, 50000);
    PFPS_MAP_TYPE(TN, "TFC (Night)", "1:250,000", "Transit Flying Chart (Night) - Host Nation", CADRG_DATA_TYPE, 250000);
    PFPS_MAP_TYPE(TP, "TPC", "1:500,000", "Tactical Pilotage Chart", CADRG_DATA_TYPE, 500000);
    PFPS_MAP_TYPE(TQ, "TLM24", "1:24,000", "Topographic Line Map 1:24,000 scale", CADRG_DATA_TYPE, 24000);
    PFPS_MAP_TYPE(TR, "TLM200", "1:200,000", "Topographic Line Map 1:200,000 scale", CADRG_DATA_TYPE, 200000);
    PFPS_MAP_TYPE(TT, "TLM25", "1:25,000", "Topographic Line Map 1:25,000 scale", CADRG_DATA_TYPE, 25000);
    PFPS_MAP_TYPE(UL, "TLM50-Other", "1:50,000", "Topographic Line Map (other 1:50,000 scale)", CADRG_DATA_TYPE, 50000);
    PFPS_MAP_TYPE2(V1, "HRC Inset", "1:50,000", "Helicopter Route Chart Inset", CADRG_DATA_TYPE, 50000, "HRC Insert 1:50K");
    PFPS_MAP_TYPE2(V2, "HRC Inset", "1:62,500", "Helicopter Route Chart Inset", CADRG_DATA_TYPE, 62500, "HRC Insert 1:62.5K");
    PFPS_MAP_TYPE2(V3, "HRC Inset", "1:90,000", "Helicopter Route Chart Inset", CADRG_DATA_TYPE, 90000, "HRC Insert 1:90K");
    PFPS_MAP_TYPE2(V4, "HRC Inset", "1:250,000", "Helicopter Route Chart Inset", CADRG_DATA_TYPE, 250000, "HRC Insert 1:250K");
    PFPS_MAP_TYPE(VH, "HRC", "1:125,000", "Helicopter Route Chart", CADRG_DATA_TYPE, 125000);
    PFPS_MAP_TYPE(VN, "VNC", "1:500,000", "Visual Navigation Charts", CADRG_DATA_TYPE, 500000);
    PFPS_MAP_TYPE(VT, "VTAC", "1:250,000", "VFR Terminal Area Chart", CADRG_DATA_TYPE, 250000);
    PFPS_MAP_TYPE2(WA, "---", "1:250,000", "IFR Enroute Low", CADRG_DATA_TYPE, 250000, "IFR Lo 1:250K");
    PFPS_MAP_TYPE2(WB, "---", "1:500,000", "IFR Enroute Low", CADRG_DATA_TYPE, 500000, "IFR Lo 1:500K");
    PFPS_MAP_TYPE2(WC, "---", "1:750,000", "IFR Enroute Low", CADRG_DATA_TYPE, 750000, "IFR Lo 1:750K");
    PFPS_MAP_TYPE2(WD, "---", "1:1,000,000", "IFR Enroute Low", CADRG_DATA_TYPE, 1000000, "IFR Lo 1:1M");
    PFPS_MAP_TYPE2(WE, "---", "1:1,500,000", "IFR Enroute Low", CADRG_DATA_TYPE, 1500000, "IFR Lo 1:1.5M");
    PFPS_MAP_TYPE2(WF, "---", "1:2,000,000", "IFR Enroute Low", CADRG_DATA_TYPE, 2000000, "IFR Lo 1:2M");
    PFPS_MAP_TYPE2(WG, "---", "1:2,500,000", "IFR Enroute Low", CADRG_DATA_TYPE, 2500000, "IFR Lo 1:2.5M");
    PFPS_MAP_TYPE2(WH, "---", "1:3,000,000", "IFR Enroute Low", CADRG_DATA_TYPE, 3000000, "IFR Lo 1:3M");
    PFPS_MAP_TYPE2(WI, "---", "1:3,500,000", "IFR Enroute Low", CADRG_DATA_TYPE, 3500000, "IFR Lo 1:3.5M");
    PFPS_MAP_TYPE2(WK, "---", "1:4,500,000", "IFR Enroute Low", CADRG_DATA_TYPE, 4500000, "IFR Lo 1:4.5M");
    PFPS_MAP_TYPE2(XD, "---", "1:1,000,000", "IFR Enroute Low", CADRG_DATA_TYPE, 1000000, "IFR Hi 1:1M");
    PFPS_MAP_TYPE2(XE, "---", "1:1,500,000", "IFR Enroute Low", CADRG_DATA_TYPE, 1500000, "IFR Hi 1:1.5M");
    PFPS_MAP_TYPE2(XF, "---", "1:2,000,000", "IFR Enroute High", CADRG_DATA_TYPE, 2000000, "IFR Hi 1:2M");
    PFPS_MAP_TYPE2(XG, "---", "1:2,500,000", "IFR Enroute High", CADRG_DATA_TYPE, 2500000, "IFR Hi 1:2.5M");
    PFPS_MAP_TYPE2(XH, "---", "1:3,000,000", "IFR Enroute High", CADRG_DATA_TYPE, 3000000, "IFR Hi 1:3M");
    PFPS_MAP_TYPE2(XI, "---", "1:3,500,000", "IFR Enroute High", CADRG_DATA_TYPE, 3500000, "IFR Hi 1:3.5M");
    PFPS_MAP_TYPE2(XJ, "---", "1:4,000,000", "IFR Enroute High", CADRG_DATA_TYPE, 4000000, "IFR Hi 1:4M");
    PFPS_MAP_TYPE2(XK, "---", "1:4,500,000", "IFR Enroute High", CADRG_DATA_TYPE, 4500000, "IFR Hi 1:4.5M");
    PFPS_MAP_TYPE2(Y9, "---", "1:16,500,000", "IFR Enroute Area", CADRG_DATA_TYPE, 16500000, "IFR Area 1:16.5M");
    PFPS_MAP_TYPE2(YA, "---", "1:250,000", "IFR Enroute Area", CADRG_DATA_TYPE, 250000, "IFR Area 1:250K");
    PFPS_MAP_TYPE2(YB, "---", "1:500,000", "IFR Enroute Area", CADRG_DATA_TYPE, 500000, "IFR Area 1:500K");
    PFPS_MAP_TYPE2(YC, "---", "1:750,000", "IFR Enroute Area", CADRG_DATA_TYPE, 750000, "IFR Area 1:750K");
    PFPS_MAP_TYPE2(YD, "---", "1:1,000,000", "IFR Enroute Area", CADRG_DATA_TYPE, 1000000, "IFR Area 1:1M");
    PFPS_MAP_TYPE2(YE, "---", "1:1,500,000", "IFR Enroute Area", CADRG_DATA_TYPE, 1500000, "IFR Area 1:1.5M");
    PFPS_MAP_TYPE2(YF, "---", "1:2,000,000", "IFR Enroute Area", CADRG_DATA_TYPE, 2000000, "IFR Area 1:2M");
    PFPS_MAP_TYPE2(YI, "---", "1:3,500,000", "IFR Enroute Area", CADRG_DATA_TYPE, 3500000, "IFR Area 1:3.5M");
    PFPS_MAP_TYPE2(YJ, "---", "1:4,000,000", "IFR Enroute Area", CADRG_DATA_TYPE, 4000000, "IFR Area 1:4M");
    PFPS_MAP_TYPE2(YZ, "---", "1:12,000,000", "IFR Enroute Area", CADRG_DATA_TYPE, 12000000, "IFR Area 1:12M");
    PFPS_MAP_TYPE2(Z8, "---", "1:16,000,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 16000000, "IFR Hi/Lo 1:16M");
    PFPS_MAP_TYPE2(ZA, "---", "1:250,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 250000, "IFR Hi/Lo 1:250K");
    PFPS_MAP_TYPE2(ZB, "---", "1:500,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 500000, "IFR Hi/Lo 1:500K");
    PFPS_MAP_TYPE2(ZC, "---", "1:750,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 750000, "IFR Hi/Lo 1:750K");
    PFPS_MAP_TYPE2(ZD, "---", "1:1,000,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 1000000, "IFR Hi/Lo 1:1M");
    PFPS_MAP_TYPE2(ZE, "---", "1:1,500,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 1500000, "IFR Hi/Lo 1:1.5M");
    PFPS_MAP_TYPE2(ZF, "---", "1:2,000,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 2000000, "IFR Hi/Lo 1:2M");
    PFPS_MAP_TYPE2(ZG, "---", "1:2,500,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 2500000, "IFR Hi/Lo 1:2.5M");
    PFPS_MAP_TYPE2(ZH, "---", "1:3,000,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 3000000, "IFR Hi/Lo 1:3M");
    PFPS_MAP_TYPE2(ZI, "---", "1:3,500,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 3500000, "IFR Hi/Lo 1:3.5M");
    PFPS_MAP_TYPE2(ZJ, "---", "1:4,000,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 4000000, "IFR Hi/Lo 1:4M");
    PFPS_MAP_TYPE2(ZK, "---", "1:4,500,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 4500000, "IFR Hi/Lo 1:4.5M");
    PFPS_MAP_TYPE2(ZT, "---", "1:9,000,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 9000000, "IFR Hi/Lo 1:9M");
    PFPS_MAP_TYPE2(ZV, "---", "1:10,000,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 10000000, "IFR Hi/Lo 1:10M");
    PFPS_MAP_TYPE2(ZZ, "---", "1:12,000,000", "IFR Enroute High/Low", CADRG_DATA_TYPE, 12000000, "IFR Hi/Lo 1:12M");

    }


namespace atakmap {
    namespace raster {
        namespace pfps {

            bool PfpsMapTypeFrame::getRpfPrettyName(std::string *str, const char *filename)
            {
                std::string upperCase;
                size_t len = strlen(filename);
                for (size_t i = 0; i < len; ++i)
                    upperCase.push_back(toupper(filename[i]));

                if (len == RPF_FILENAME_LENGTH) {
                    char pfpsMapDataTypeCode[3];
                    pfpsMapDataTypeCode[0] = upperCase[9];
                    pfpsMapDataTypeCode[1] = upperCase[10];
                    pfpsMapDataTypeCode[2] = '\0';

                    const PfpsMapDataType *retval = PfpsMapDataType::getPfpsMapDataType(pfpsMapDataTypeCode);
                    if (retval != nullptr) {
                        *str = retval->prettyName;
                        return true;
                    }
                } else if (len == ECRG_FILENAME_LENGTH) {
                    char pfpsMapDataTypeCode[3];
                    pfpsMapDataTypeCode[0] = upperCase[15];
                    pfpsMapDataTypeCode[1] = upperCase[16];
                    pfpsMapDataTypeCode[2] = '\0';

                    const PfpsMapDataType *retval = PfpsMapDataType::getPfpsMapDataType(pfpsMapDataTypeCode);
                    if (retval != nullptr) {
                        *str = retval->prettyName;
                        return true;
                    }
                }
                
                return false;
            }

            bool PfpsMapTypeFrame::coverageFromFilepath(const char *filepath, core::GeoPoint &ul, core::GeoPoint &ur,
                                                        core::GeoPoint &lr, core::GeoPoint &ll)
            {
                if (filepath == nullptr){
                    return false;
                }

                std::string f = util::getFileName(filepath);

                std::string upperCase;
                size_t len = f.size();
                for (size_t i = 0; i < len; ++i)
                    upperCase.push_back(toupper(f[i]));

                if (len < RPF_FILENAME_LENGTH) {
                    return false;
                }

                upperCase = upperCase.substr(0, len);

                return coverageFromFilename(upperCase.c_str(), ul, ur, lr, ll);
            }

            bool PfpsMapTypeFrame::coverageFromFilename(const char *buffer, core::GeoPoint &ul,
                                                        core::GeoPoint &ur, core::GeoPoint& lr, core::GeoPoint &ll)
            {
                try {
//                    char producerId = buffer[7];
                    char pfpsMapDataTypeCode[3];
                    pfpsMapDataTypeCode[0] = buffer[9];
                    pfpsMapDataTypeCode[1] = buffer[10];
                    pfpsMapDataTypeCode[2] = '\0';
                    char zoneCode = buffer[11];

                    // CADRG
                    int frameChars = 5;
                    int versionChars = 2;
                    if (PfpsMapDataType::isCIBMapDataType(pfpsMapDataTypeCode)) {
                        // CIB
                        frameChars = 6;
                        versionChars = 1;
                    }

                    int frameNumber = PfpsUtils::base34Decode(buffer, frameChars);
                    int version = PfpsUtils::base34Decode(buffer + frameChars, versionChars);

                    if (frameNumber < 0 || version < 0)
                        return false;

                    int northSouthPixelConstantV;
                    int eastWestPixelConstantV;
                    double polewardExtentV;
                    double equatorwardExtentV;
                    int latitudinalFramesV;
                    int longitudinalFramesV;

                    const PfpsMapDataType *ds = PfpsMapDataType::getPfpsMapDataType(pfpsMapDataTypeCode);
                    if (ds == nullptr)
                        return false;

                    const char *rpfDataType = ds->rpfDataType;
                    double resolution = ds->scaleOrGSD;

                    // valid scale / gsd
                    if (ds != nullptr && ds->scaleOrGSD > 0.0) {
                        eastWestPixelConstantV = eastWestPixelSpacingConstant(zoneCode);
                        northSouthPixelConstantV = northSouthPixelSpacingConstant();

                        // scale / gsd specific zone properties
                        if (PfpsMapDataType::isCADRGDataType(rpfDataType)) {
                            northSouthPixelConstantV = northSouthPixelConstant_CADRG(
                                northSouthPixelConstantV, resolution);
                            eastWestPixelConstantV = eastWestPixelConstant_CADRG(eastWestPixelConstantV,
                                                                                resolution);

                        } else if (PfpsMapDataType::isCIBDataType(rpfDataType)) {
                            northSouthPixelConstantV = northSouthPixelConstant_CIB(northSouthPixelConstantV,
                                                                                  resolution);
                            eastWestPixelConstantV = eastWestPixelConstant_CIB(eastWestPixelConstantV,
                                                                              resolution);

                        } else {
                            northSouthPixelConstantV = -1;
                            eastWestPixelConstantV = -1;
                        }

                        polewardExtentV = polewardExtent(polewardNominalBoundary(zoneCode),
                                                        northSouthPixelConstantV, PIXEL_ROWS_PER_FRAME);
                        equatorwardExtentV = equatorwardExtent(equatorwardNominalBoundary(zoneCode),
                                                              northSouthPixelConstantV, PIXEL_ROWS_PER_FRAME);

                        latitudinalFramesV = latitudinalFrames(polewardExtentV, equatorwardExtentV,
                                                              northSouthPixelConstantV, PIXEL_ROWS_PER_FRAME);
                        longitudinalFramesV = longitudinalFrames(eastWestPixelConstantV, PIXEL_ROWS_PER_FRAME);

                        // start calculate bounds

                        int maxFrameNumberV = maxFrameNumber(latitudinalFramesV, longitudinalFramesV);
                        if (frameNumber < 0 || frameNumber > maxFrameNumberV)
                            return false;

                        int row = frameRow(frameNumber, longitudinalFramesV);
                        int col = frameColumn(frameNumber, row, longitudinalFramesV);

                        double zoneLat = isZoneInUpperHemisphere(zoneCode) ? equatorwardExtentV : polewardExtentV;
                        double maxLatitude = frameOriginLatitude(row, northSouthPixelConstantV,
                                                                 PIXEL_ROWS_PER_FRAME, zoneLat);
                        double minLatitude = maxLatitude - frameDeltaLatitude(northSouthPixelConstantV,
                                                                              PIXEL_ROWS_PER_FRAME);

                        double minLongitude = frameOriginLongitude(col, eastWestPixelConstantV,
                                                                   PIXEL_ROWS_PER_FRAME);
                        double maxLongitude = minLongitude + frameDeltaLongitude(eastWestPixelConstantV,
                                                                                 PIXEL_ROWS_PER_FRAME);

                        ul.set(maxLatitude, minLongitude);
                        ur.set(maxLatitude, maxLongitude);
                        lr.set(minLatitude, maxLongitude);
                        ll.set(minLatitude, minLongitude);
                        return true;
                    } else {
                        return false;
                    }
                } catch (...) {
                    //android.util.Log.e(TAG, "error: ", e);
                    return false;
                }

            }

        }
    }
}

