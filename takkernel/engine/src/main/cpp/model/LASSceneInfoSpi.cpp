

#include "raster/gdal/GdalLibrary.h"
#include "model/LASSceneInfoSpi.h"
#include "model/MeshTransformer.h"
#include "util/IO2.h"
#include "util/Memory.h"
#include "core/ProjectionFactory3.h"
#include "util/URI.h"

#define LAS_DLL_IMPORT
#include <liblas/capi/liblas.h>

#include <ogr_spatialref.h>

using namespace TAK::Engine::Model;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Port;

namespace TAK {
    namespace Engine {
        namespace Model {
            double LASSceneInfoSpi_getZScale(LASHeaderH header);
        }
    }
}

namespace {

    enum GeoTIFFLinearUnits {
        GeoTIFFLinearUnits_Undefined,
        GeoTIFFLinearUnits_LinearMeter = 9001,
        GeoTIFFLinearUnits_LinearFoot = 9002,
        GeoTIFFLinearUnits_LinearFootUSSurvey = 9003,
        GeoTIFFLinearUnits_LinearFootModifiedAmerican = 9004,
        GeoTIFFLinearUnits_LinearFootClarke = 9005,
        GeoTIFFLinearUnits_LinearFootIndian = 9006,
        GeoTIFFLinearUnits_LinearLink = 9007,
        GeoTIFFLinearUnits_LinearLinkBenoit = 9008,
        GeoTIFFLinearUnits_LinearLinkSears = 9009,
        GeoTIFFLinearUnits_LinearChainBenoit = 9010,
        GeoTIFFLinearUnits_LinearChainSears = 9011,
        GeoTIFFLinearUnits_LinearYardSears = 9012,
        GeoTIFFLinearUnits_LinearYardIndian = 9013,
        GeoTIFFLinearUnits_LinearFathom = 9014,
        GeoTIFFLinearUnits_LinearMileInternationalNautical = 9015
    };

    struct VLRInfo {

        VLRInfo()
            : sridPair(-1, INT_MAX), vertUnitsPair(GeoTIFFLinearUnits_Undefined, 1.0) {}

        void updateSrid(int srid, int priority);

        void updateVertUnits(short value);

        // (srid, priority)
        std::pair<int, int> sridPair;

        // (GeoTIFFLinearUnits, to-meters-multiplier)
        std::pair<GeoTIFFLinearUnits, double> vertUnitsPair;
    };

    void parseVLRs(VLRInfo& parseInfo, LASHeaderH header);
}

LASSceneInfoSpi::LASSceneInfoSpi() NOTHROWS 
{}

LASSceneInfoSpi::~LASSceneInfoSpi() NOTHROWS {

}

int LASSceneInfoSpi::getPriority() const NOTHROWS {
    return 1;
}

const char *LASSceneInfoSpi::getStaticName() NOTHROWS {
    return "LAS";
}

const char *LASSceneInfoSpi::getName() const NOTHROWS {
    return getStaticName();
}

bool LASSceneInfoSpi::isSupported(const char *path) NOTHROWS {

    const char *ext = strrchr(path, '.');
    if (!ext)
        return false;

    int comp = -1;
    Port::String_compareIgnoreCase(&comp, ext, ".las");
    int comp2 = -1;
    Port::String_compareIgnoreCase(&comp2, ext, ".laz");
    if (comp != 0 && comp2 != 0)
        return false;

    bool exists = false;
    IO_existsV(&exists, path);

    // quick and dirty validation of las file
    LASReaderH reader = LASReader_Create(path);
    LASHeaderH header = LASReader_GetHeader(reader);
    if (!reader || !header)
        exists = false;
    LASHeader_Destroy(header);
    LASReader_Destroy(reader);

    return exists;
}

double TAK::Engine::Model::LASSceneInfoSpi_getZScale(LASHeaderH header) {
    VLRInfo info;
    if (header)
        parseVLRs(info, header);
    return info.vertUnitsPair.second;
}

TAK::Engine::Util::TAKErr TAK::Engine::Model::LASSceneInfo_create(TAK::Engine::Port::Collection<SceneInfoPtr>& scenes, const char* path) NOTHROWS {

    TAKErr result = TE_Unsupported;

    LASReaderH reader = LASReader_Create(path);
    LASHeaderH header = LASReader_GetHeader(reader);
    if (reader && header) {

        VLRInfo info;
        parseVLRs(info, header);

        double zScale = info.vertUnitsPair.second;

        TAK::Engine::Feature::Envelope2 aabb(
            LASHeader_GetMinX(header),
            LASHeader_GetMinY(header),
            LASHeader_GetMinZ(header) * zScale,
            LASHeader_GetMaxX(header),
            LASHeader_GetMaxY(header),
            LASHeader_GetMaxZ(header) * zScale);

        TAK::Engine::Math::Point2<double> center((aabb.minX + aabb.maxX) / 2.0,
            (aabb.minY + aabb.maxY) / 2.0,
            (aabb.minZ + aabb.maxZ) / 2.0);

        TAK::Engine::Math::Matrix2 localTransform;
        TAK::Engine::Math::Matrix2 localTransformInv;

        // frame to AABB center
        localTransform.translate(center.x, center.y, center.z);

        // Unapply LAS translation and scaling
        TAKErr code = localTransform.createInverse(&localTransformInv);
        if (code == TE_Ok) {
            Mesh_transform(&aabb, aabb, MeshTransformOptions(localTransformInv), MeshTransformOptions());
        }

        SceneInfoPtr model(new SceneInfo());
        model->uri = path;
        model->type = LASSceneInfoSpi::getStaticName();
        model->srid = info.sridPair.first;
        model->aabb = TAK::Engine::Feature::Envelope2Ptr(new TAK::Engine::Feature::Envelope2(aabb), Memory_deleter_const<TAK::Engine::Feature::Envelope2>);
        model->localFrame = TAK::Engine::Math::Matrix2Ptr_const(new TAK::Engine::Math::Matrix2(localTransform), Memory_deleter_const<TAK::Engine::Math::Matrix2>);
        model->altitudeMode = TAK::Engine::Feature::TEAM_Absolute;
        // XXX - bounds/altitude adjustment temporarily disabled as reproject to ECEF cause incompatibility with local transform in native planar projection
        model->capabilities = SceneInfo::CapabilitiesType::MaterialColor | SceneInfo::CapabilitiesType::ManualColor |
                              SceneInfo::CapabilitiesType::IntensityColor | SceneInfo::CapabilitiesType::ZValueColor;

        IO_getName(model->name, path);
        if (!model->name)
            model->name = path;

        // Get the SRID projection to find the location
        TAK::Engine::Core::Projection2Ptr proj(nullptr, nullptr);
        TAK::Engine::Core::ProjectionFactory3_create(proj, model->srid);
        if (proj) {
            TAK::Engine::Core::GeoPoint2 geo;
            proj->inverse(&geo, center);
            model->location = TAK::Engine::Core::GeoPoint2Ptr(new TAK::Engine::Core::GeoPoint2(geo), Memory_deleter_const<TAK::Engine::Core::GeoPoint2>);
        }

        model->minDisplayResolution = std::numeric_limits<double>::max();
        model->maxDisplayResolution = 0.0;

        result = scenes.add(model);
    }

    LASHeader_Destroy(header);
    LASReader_Destroy(reader);

    return result;
}

TAK::Engine::Util::TAKErr LASSceneInfoSpi::create(TAK::Engine::Port::Collection<SceneInfoPtr> &scenes, const char *path) NOTHROWS {
    return LASSceneInfo_create(scenes, path);
}

namespace {

    //
    // https://github.com/ASPRSorg/LAS/blob/master/source/03_required_vlrs.txt
    // 
    // also reference GeoTIFF spec for GeoKey IDs and values
    //

    struct GeoKeys {
        unsigned short wKeyDirectoryVersion;
        unsigned short wKeyRevision;
        unsigned short wMinorRevision;
        unsigned short wNumberOfKeys;
    };

    struct GeoKeysEntry {
        unsigned short wKeyID;
        unsigned short wTIFFTagLocation;
        unsigned short wCount;
        unsigned short wValue_Offset;
    };

    enum TIFFTagLocation {
        TIFFTagLocation_ValueOffset = 0,
        TIFFTagLocation_DoubleParamsIndex = 34736,
        TIFFTagLocation_AsciiParamsIndex = 34737
    };

    // only keys we care about
    enum GeoTIFFGeoKeyID {
        GeoTIFFGeoKeyID_ModelType = 1024,
        GeoTIFFGeoKeyID_ProjectedCSTypeGeoKey = 3072,
        GeoTIFFGeoKeyID_VerticalUnitsGeoKey = 4099
    };

    enum GeoTIFFModelType {
        GeoTIFFModelType_Proj = 1,
        GeoTIFFModelType_LatLng = 2,
        GeoTIFFModelType_ECEF = 3,
    };

    void VLRInfo::updateSrid(int srid, int priority) {
        // lower priority value is better
        if (srid != -1 && priority < sridPair.second)
            sridPair = std::make_pair(srid, priority);
    }

    void VLRInfo::updateVertUnits(short value) {
        switch (value) {
        case GeoTIFFLinearUnits_LinearMeter:
            vertUnitsPair = std::make_pair(GeoTIFFLinearUnits_LinearMeter, 1.0);
            break;
        case GeoTIFFLinearUnits_LinearFoot:
            vertUnitsPair = std::make_pair(GeoTIFFLinearUnits_LinearFoot, 0.3048);
            break;
        case GeoTIFFLinearUnits_LinearFootUSSurvey:
            vertUnitsPair = std::make_pair(GeoTIFFLinearUnits_LinearFootUSSurvey, 1200.0 / 3937.0);
            break;
        case GeoTIFFLinearUnits_LinearFootModifiedAmerican:
            vertUnitsPair = std::make_pair(GeoTIFFLinearUnits_LinearFootModifiedAmerican, 0.3048); // ??
            break;
        case GeoTIFFLinearUnits_LinearFootClarke:
            vertUnitsPair = std::make_pair(GeoTIFFLinearUnits_LinearFootClarke, 0.304797265);
            break;
        case GeoTIFFLinearUnits_LinearFootIndian:
            vertUnitsPair = std::make_pair(GeoTIFFLinearUnits_LinearFootIndian, 0.304799514);
            break;
        case GeoTIFFLinearUnits_LinearLink:
            vertUnitsPair = std::make_pair(GeoTIFFLinearUnits_LinearLink, 0.66 * 0.3048);
            break;
        case GeoTIFFLinearUnits_LinearLinkBenoit:
            vertUnitsPair = std::make_pair(GeoTIFFLinearUnits_LinearLinkBenoit, 0.66 * 0.304799735);
            break;
        case GeoTIFFLinearUnits_LinearLinkSears:
            vertUnitsPair = std::make_pair(GeoTIFFLinearUnits_LinearLinkSears, 0.66 * 0.30479947);
            break;
        case GeoTIFFLinearUnits_LinearChainBenoit:
            vertUnitsPair = std::make_pair(GeoTIFFLinearUnits_LinearChainBenoit, 66.0 * 0.304799735);
            break;
        case GeoTIFFLinearUnits_LinearChainSears:
            vertUnitsPair = std::make_pair(GeoTIFFLinearUnits_LinearChainSears, 66.0 * 0.30479947);
            break;
        case GeoTIFFLinearUnits_LinearYardSears:
            vertUnitsPair = std::make_pair(GeoTIFFLinearUnits_LinearYardSears, 3.0 * 0.30479947);
            break;
        case GeoTIFFLinearUnits_LinearYardIndian:
            vertUnitsPair = std::make_pair(GeoTIFFLinearUnits_LinearYardIndian, 3.0 * 0.304799514);
            break;
        case GeoTIFFLinearUnits_LinearFathom:
            vertUnitsPair = std::make_pair(GeoTIFFLinearUnits_LinearFathom, 1.8288);
            break;
        case GeoTIFFLinearUnits_LinearMileInternationalNautical:
            vertUnitsPair = std::make_pair(GeoTIFFLinearUnits_LinearMileInternationalNautical, 1852.0);
            break;
        }
    }

    inline unsigned short getShort(unsigned short sh, TAKEndian sourceEndian) {
        return TE_PlatformEndian != sourceEndian ?
            (sh >> 8) | (sh << 8) : sh;
    }

    void parseGeoKeys(VLRInfo& info, const std::vector<unsigned char>& bytes) {

        const unsigned short* sp = reinterpret_cast<const unsigned short*>(bytes.data());
        const unsigned short* sp_end = reinterpret_cast<const unsigned short*>(bytes.data() + bytes.size());

        if (sp + 4 > sp_end)
            return;

        GeoKeys geoKeys;

        //XXX-- Don't know if this is always the case, but appears so
        const TAKEndian sourceEndian = TE_LittleEndian;
        
        geoKeys.wKeyDirectoryVersion = getShort(*sp++, sourceEndian);
        geoKeys.wKeyRevision = getShort(*sp++, sourceEndian);
        geoKeys.wMinorRevision = getShort(*sp++, sourceEndian);
        geoKeys.wNumberOfKeys = getShort(*sp++, sourceEndian);

        // quick validation
        if (geoKeys.wKeyDirectoryVersion != 1 || geoKeys.wKeyRevision != 1 || geoKeys.wMinorRevision != 0)
            return;

        for (size_t i = 0; i < geoKeys.wNumberOfKeys; ++i) {

            if (sp + 4 > sp_end)
                break;

            GeoKeysEntry entry;
            entry.wKeyID = getShort(*sp++, sourceEndian);
            entry.wTIFFTagLocation = getShort(*sp++, sourceEndian);
            entry.wCount = getShort(*sp++, sourceEndian);
            entry.wValue_Offset = getShort(*sp++, sourceEndian);

            switch (entry.wKeyID) {
            case GeoTIFFGeoKeyID_ModelType:
                // verify the type is in the value offset (should be)
                if (entry.wTIFFTagLocation == TIFFTagLocation_ValueOffset) {
                    switch (entry.wValue_Offset) {
                    case GeoTIFFModelType_LatLng:
                        info.updateSrid(4326, 0);
                        break;
                    case GeoTIFFModelType_ECEF:
                        info.updateSrid(4978, 0);
                        break;
                    }
                }
                break;

            case GeoTIFFGeoKeyID_ProjectedCSTypeGeoKey:

                // verify the EPSG is in the value offset
                if (entry.wTIFFTagLocation == TIFFTagLocation_ValueOffset) {
                    OGRSpatialReference srs;

                    // serves to validate as supported
                    OGRErr err = srs.importFromEPSG(static_cast<int>(entry.wValue_Offset));
                    if (err == OGRERR_NONE) {
                        int srid = atakmap::raster::gdal::GdalLibrary::getSpatialReferenceID(&srs);
                        info.updateSrid(srid, 1);
                    }
                }
                break;

            case GeoTIFFGeoKeyID_VerticalUnitsGeoKey:
                if (entry.wTIFFTagLocation == TIFFTagLocation_ValueOffset) {
                    info.updateVertUnits(entry.wValue_Offset);
                }
                break;
            }
        }

    }

    enum VLRRecordID {
        VLRRecordID_OGR_WKT = 2112,
        VLRRecordID_GTIFF_GeoKeyDirectory = 34735,
        VLRRecordID_GTIFF_DoubleParams = 34736,
        VLRRecordID_GTIFF_AsciiParams = 34737
    };

    void parseVLR(VLRInfo& info, LASVLRH vlr) {

        const char* const kLASProjID = "LASF_Projection";
        const char* const kLibLasID = "liblas";

        unsigned short recordId = LASVLR_GetRecordId(vlr);

        const char* userId = LASVLR_GetUserId(vlr);
        
        if (String_strcasecmp(userId, kLibLasID) == 0 && recordId == VLRRecordID_OGR_WKT) {

            std::vector<unsigned char> wkt;
            
            size_t length = LASVLR_GetRecordLength(vlr);
            wkt.resize(length);

            LASVLR_GetData(vlr, wkt.data());

            // spec says it should be null termintated, but double check
            if (wkt.size() == 0 || wkt.back() != '\0')
                wkt.push_back('\0');

            OGRSpatialReference srs;
            OGRErr err = srs.importFromWkt(reinterpret_cast<const char*>(wkt.data()));
            if (err == OGRERR_NONE) {
                int srid = atakmap::raster::gdal::GdalLibrary::getSpatialReferenceID(&srs);
                info.updateSrid(srid, 0);
            }
        } else if (String_strcasecmp(userId, kLASProjID) == 0) {

            switch (recordId) {
            case VLRRecordID_GTIFF_GeoKeyDirectory: {
                std::vector<unsigned char> bytes;

                size_t length = LASVLR_GetRecordLength(vlr);
                bytes.resize(length);

                LASVLR_GetData(vlr, bytes.data());
                parseGeoKeys(info, bytes);
            }
                break;
            case VLRRecordID_GTIFF_AsciiParams: /*N/A*/ break;
            case VLRRecordID_GTIFF_DoubleParams: /*N/A*/ break;
            }
        }
    }

    void parseVLRs(VLRInfo& parseInfo, LASHeaderH header) {
        unsigned int recordsCount = LASHeader_GetRecordsCount(header);

        // TAK's liblas is not compiled with libGeoTiff or GDAL (due to proj and GDAL version issues), so pull SRID from the VLR section
        for (unsigned int i = 0; i < recordsCount; ++i) {

            LASVLRH vlr = LASHeader_GetVLR(header, i);

            // unlikely-- just to be safe
            if (!vlr)
                continue;

            parseVLR(parseInfo, vlr);

            LASVLR_Destroy(vlr);
        }
    }
}