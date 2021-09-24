
#include <string>
#include <vector>
#include <sstream>
#include <iterator>
#include "port/STLVectorAdapter.h"
#include "util/IO2.h"
#include "model/Pix4dGeoreferencer.h"
#include "util/DataInput2.h"
#include "util/Memory.h"
#include "ogr_spatialref.h"
#include "gdal_priv.h"
#include "raster/gdal/GdalLibrary.h"
#include "core/ProjectionFactory3.h"

using namespace TAK::Engine::Model;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

namespace {

    struct OffsetXYZSuffix {
        static const char *chars() {
            return "_offset.xyz";
        }
    };

    struct XYZSuffix {
        static const char *chars() {
            return ".xyz";
        }
    };

    struct WTKPRJSuffix {
        static const char *chars() {
            return "_wtk.prj";
        }
    };

    struct PRJSuffix {
        static const char *chars() {
            return ".prj";
        }
    };

    template <typename Suffix>
    struct IsFileWithSuffix {
        static bool inst(const char *file);
    };

    TAKErr readDataInputAsString(TAK::Engine::Port::String &result, DataInput2Ptr &dataPtr) NOTHROWS;
}

Pix4dGeoreferencer::Pix4dGeoreferencer() NOTHROWS
{}

Pix4dGeoreferencer::~Pix4dGeoreferencer() NOTHROWS
{}

TAK::Engine::Util::TAKErr Pix4dGeoreferencer::locate(SceneInfo &sceneInfo) NOTHROWS {

    TAKErr code(TE_Ok);

    bool exists = false;
    IO_existsV(&exists, sceneInfo.uri);

    if (!exists)
        return TE_Unsupported;

    Port::String baseFileName;
    code = IO_getName(baseFileName, sceneInfo.uri);
    TE_CHECKRETURN_CODE(code);

    TE_BEGIN_TRAP() {
        std::string baseFileNameStr = baseFileName.get();
        size_t ext = baseFileNameStr.find_last_of('.');
        if (ext != std::string::npos)
            baseFileNameStr = baseFileNameStr.substr(0, baseFileNameStr.find_last_of('.'));

        const char *suffix = "_simplified_3d_mesh";
        size_t suffixLen = strlen(suffix);
        if (TAK::Engine::Port::String_endsWith(baseFileNameStr.c_str(), suffix))
            baseFileNameStr = baseFileNameStr.substr(0, baseFileNameStr.length() - suffixLen);

        Port::String parentFile;
        code = IO_getParentFile(parentFile, sceneInfo.uri);
        TE_CHECKRETURN_CODE(code);

        // check for offset file
        std::vector<Port::String> filesList;
        TAK::Engine::Port::STLVectorAdapter<Port::String> filesListAdapter(filesList);
        code = IO_listFilesV(filesListAdapter, parentFile, Util::TELFM_RecursiveFiles, IsFileWithSuffix<OffsetXYZSuffix>::inst);
        TE_CHECKRETURN_CODE(code);

        if (filesList.size() == 0) {
            // last ditch effort to find a xyz file.   users are not always following the 
            // name convention.
            code = IO_listFilesV(filesListAdapter, parentFile, Util::TELFM_RecursiveFiles, IsFileWithSuffix<XYZSuffix>::inst);
            TE_CHECKRETURN_CODE(code);
        }

        if (filesList.size() == 0) {
            //LOG-- missing _offset.xyz file
            return TE_InvalidArg;
        }

        Port::String offsetFile = filesList.front();

        filesList.clear();
        code = IO_listFilesV(filesListAdapter, parentFile, Util::TELFM_RecursiveFiles, IsFileWithSuffix<WTKPRJSuffix>::inst);
        TE_CHECKRETURN_CODE(code);

        if (filesList.size() == 0) {
            // last ditch effort to find a prj file.   users are not always following the 
            // name convention.
            code = IO_listFilesV(filesListAdapter, parentFile, Util::TELFM_RecursiveFiles, IsFileWithSuffix<PRJSuffix>::inst);
            TE_CHECKRETURN_CODE(code);
        }

        if (filesList.size() == 0) {
            //LOG-- missing _wtk.prj file
            return TE_InvalidArg;
        }

        Port::String projectionFile = filesList.front();

        DataInput2Ptr dataPtr(nullptr, nullptr);
        code = IO_openFileV(dataPtr, offsetFile);
        TE_CHECKRETURN_CODE(code);

        TAK::Engine::Port::String xyz;
        code = readDataInputAsString(xyz, dataPtr);
        dataPtr->close();
        dataPtr.reset();
        TE_CHECKRETURN_CODE(code);

        std::istringstream ss(xyz.get());
        std::vector<std::string> splits;
        std::copy(std::istream_iterator<std::string>(ss),
            std::istream_iterator<std::string>(),
            std::back_inserter(splits));
        
        if (splits.size() != 3)
            return TE_InvalidArg;

        double x = strtod(splits[0].c_str(), nullptr);
        double y = strtod(splits[1].c_str(), nullptr);
        double z = strtod(splits[2].c_str(), nullptr);

        TAK::Engine::Math::Point2<double> localFrameOrigin(x, y, z);

        code = IO_openFileV(dataPtr, projectionFile);
        TE_CHECKRETURN_CODE(code);

        TAK::Engine::Port::String wkt;
        code = readDataInputAsString(wkt, dataPtr);
        dataPtr->close();
        dataPtr.reset();
        TE_CHECKRETURN_CODE(code);
        
        OGRSpatialReference spatialRef(wkt);
        int srid = atakmap::raster::gdal::GdalLibrary::getSpatialReferenceID(&spatialRef);

        std::unique_ptr<TAK::Engine::Math::Matrix2> localFrame(new TAK::Engine::Math::Matrix2());
        localFrame->translate(localFrameOrigin.x, localFrameOrigin.y, localFrameOrigin.z);
        
        // XXX - this should really be the center of the AABB
        TAK::Engine::Core::Projection2Ptr projPtr(nullptr, nullptr);
        code = TAK::Engine::Core::ProjectionFactory3_create(projPtr, srid);
        TE_CHECKRETURN_CODE(code);

        TAK::Engine::Core::GeoPoint2 location;
        code = projPtr->inverse(&location, localFrameOrigin);
        TE_CHECKRETURN_CODE(code);

        sceneInfo.localFrame = TAK::Engine::Math::Matrix2Ptr_const(localFrame.release(), Memory_deleter_const<TAK::Engine::Math::Matrix2>);
        sceneInfo.srid = srid;
        sceneInfo.altitudeMode = TEAM_ClampToGround;

        // XXX - we could compute the convergence angle at the location and then pass through an approximated WGS84

        sceneInfo.location = GeoPoint2Ptr(new GeoPoint2(location), Memory_deleter_const<GeoPoint2>);
        sceneInfo.minDisplayResolution = 5.0;
    } TE_END_TRAP(code);
    TE_CHECKRETURN_CODE(code);
    
    return TE_Ok;
}

namespace {
    template <typename Suffix>
    bool IsFileWithSuffix<Suffix>::inst(const char *file) {
        const char *pos = strstr(file, Suffix::chars());
        if (pos == file + strlen(file) - strlen(Suffix::chars()))
            return true;
        return false;
    }

    TAKErr readDataInputAsString(TAK::Engine::Port::String &result, DataInput2Ptr &dataPtr) NOTHROWS
    {
        TAKErr code(TE_Ok);
        // XXX - charset
        std::ostringstream strm;
        do {
            uint8_t buf[1024];
            std::size_t count;
            code = dataPtr->read(buf, &count, 1024u);
            TE_CHECKBREAK_CODE(code);

            strm << std::string(reinterpret_cast<const char *>(buf), count);
        } while (true);
        if (code == TE_EOF)
            code = TE_Ok;
        result = strm.str().c_str();
        return code;
    }
}