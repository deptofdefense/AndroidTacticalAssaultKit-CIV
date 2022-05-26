

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

namespace {

    // returns (srid, priority)
    // lesser priority is better
    std::pair<int, int> gleanSrid(LASVLRH vlr);
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

TAK::Engine::Util::TAKErr TAK::Engine::Model::LASSceneInfo_create(TAK::Engine::Port::Collection<SceneInfoPtr>& scenes, const char* path) NOTHROWS {

    TAKErr result = TE_Unsupported;

    LASReaderH reader = LASReader_Create(path);
    LASHeaderH header = LASReader_GetHeader(reader);
    if (reader && header) {

        unsigned int recordsCount = LASHeader_GetRecordsCount(header);

        // SRID, priority pair. The VLRs theoretically can contain WKT AND GeoTIFF defined projection info, so this allows prioritization
        std::pair<int, int> sridPair(-1, INT_MAX);

        // TAK's liblas is not compiled with libGeoTiff or GDAL (due to proj and GDAL version issues), so pull SRID from the VLR section
        for (unsigned int i = 0; i < recordsCount; ++i) {

            LASVLRH vlr = LASHeader_GetVLR(header, i);

            // unlikely-- just to be safe
            if (!vlr)
                continue;

            std::pair<int, int> possibleSridPair = gleanSrid(vlr);

            // lower priority value is better
            if (possibleSridPair.first != -1 && possibleSridPair.second < sridPair.second)
                sridPair = possibleSridPair;

            LASVLR_Destroy(vlr);
        }

        TAK::Engine::Feature::Envelope2 aabb(
            LASHeader_GetMinX(header),
            LASHeader_GetMinY(header),
            LASHeader_GetMinZ(header),
            LASHeader_GetMaxX(header),
            LASHeader_GetMaxY(header),
            LASHeader_GetMaxZ(header));

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
        model->srid = sridPair.first;
        model->aabb = TAK::Engine::Feature::Envelope2Ptr(new TAK::Engine::Feature::Envelope2(aabb), Memory_deleter_const<TAK::Engine::Feature::Envelope2>);
        model->localFrame = TAK::Engine::Math::Matrix2Ptr_const(new TAK::Engine::Math::Matrix2(localTransform), Memory_deleter_const<TAK::Engine::Math::Matrix2>);
        model->altitudeMode = TAK::Engine::Feature::TEAM_Absolute;

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
        GeoTIFFGeoKeyID_ProjectedCSTypeGeoKey = 3072
    };

    enum GeoTIFFModelType {
        GeoTIFFModelType_Proj = 1,
        GeoTIFFModelType_LatLng = 2,
        GeoTIFFModelType_ECEF = 3,
    };

    inline unsigned short getShort(unsigned short sh, TAKEndian sourceEndian) {
        return TE_PlatformEndian != sourceEndian ?
            (sh >> 8) | (sh << 8) : sh;
    }

    std::pair<int, int> gleanGeoKeysSrid(const std::vector<unsigned char>& bytes) {

        std::pair<int, int> result(-1, INT_MAX);

        const unsigned short* sp = reinterpret_cast<const unsigned short*>(bytes.data());
        const unsigned short* sp_end = reinterpret_cast<const unsigned short*>(bytes.data() + bytes.size());

        if (sp + 4 > sp_end)
            return result;

        GeoKeys geoKeys;

        //XXX-- Don't know if this is always the case, but appears so
        const TAKEndian sourceEndian = TE_LittleEndian;
        
        geoKeys.wKeyDirectoryVersion = getShort(*sp++, sourceEndian);
        geoKeys.wKeyRevision = getShort(*sp++, sourceEndian);
        geoKeys.wMinorRevision = getShort(*sp++, sourceEndian);
        geoKeys.wNumberOfKeys = getShort(*sp++, sourceEndian);

        // quick validation
        if (geoKeys.wKeyDirectoryVersion != 1 || geoKeys.wKeyRevision != 1 || geoKeys.wMinorRevision != 0)
            return result;

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
                        result = std::make_pair(4326, 0);
                        break;
                    case GeoTIFFModelType_ECEF:
                        result = std::make_pair(4978, 0);
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
                        result = std::make_pair(srid, 1);
                    }
                }
                break;
            }
        }

        return result;
    }

    enum VLRRecordID {
        VLRRecordID_OGR_WKT = 2112,
        VLRRecordID_GTIFF_GeoKeyDirectory = 34735,
        VLRRecordID_GTIFF_DoubleParams = 34736,
        VLRRecordID_GTIFF_AsciiParams = 34737
    };

    std::pair<int, int> gleanSrid(LASVLRH vlr) {

        const char* const kLASProjID = "LASF_Projection";
        const char* const kLibLasID = "liblas";

        std::pair<int, int> result(-1, INT_MAX);
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
                result = std::make_pair(srid, 0);
            }
        } else if (String_strcasecmp(userId, kLASProjID) == 0) {

            switch (recordId) {
            case VLRRecordID_GTIFF_GeoKeyDirectory: {
                std::vector<unsigned char> bytes;

                size_t length = LASVLR_GetRecordLength(vlr);
                bytes.resize(length);

                LASVLR_GetData(vlr, bytes.data());
                result = gleanGeoKeysSrid(bytes);
            }
                break;
            case VLRRecordID_GTIFF_AsciiParams: /*N/A*/ break;
            case VLRRecordID_GTIFF_DoubleParams: /*N/A*/ break;
            }
        }

        return result;
    }
}