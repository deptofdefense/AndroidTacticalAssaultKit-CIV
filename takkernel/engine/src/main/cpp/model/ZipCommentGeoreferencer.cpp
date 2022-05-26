#include "model/ZipCommentGeoreferencer.h"
#include "model/SceneInfo.h"
#include "util/IO2.h"
#include "util/Memory.h"
#include "model/ZipCommentInfo.h"

using namespace TAK::Engine::Math;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Core;
using namespace TAK::Engine::Util;

ZipCommentGeoreferencer::ZipCommentGeoreferencer() NOTHROWS {
}

ZipCommentGeoreferencer::~ZipCommentGeoreferencer() NOTHROWS {
}

TAKErr ZipCommentGeoreferencer::locate(SceneInfo &sceneInfo) NOTHROWS {

    bool exists = false;
    IO_existsV(&exists, sceneInfo.uri);
    if (!exists)
        return TE_Unsupported;

    TAKErr code(TE_Ok);
    TE_BEGIN_TRAP() {
        Port::String zipComment;
        code = IO_getZipComment(zipComment, sceneInfo.uri);
        TE_CHECKRETURN_CODE(code);

        if (zipComment == "")
            return TE_Unsupported;

        ZipCommentInfoPtr zipCommentInfo(nullptr, nullptr);
        code = ZipCommentInfo::Create(zipCommentInfo, zipComment.get());
        TE_CHECKRETURN_CODE(code);

        GeoPoint2 location;
        code = zipCommentInfo->GetLocation(location);
        TE_CHECKRETURN_CODE(code);

        std::unique_ptr<Matrix2> localFrame(new Matrix2());
        code = zipCommentInfo->GetLocalFrame(*localFrame.get());
        TE_CHECKRETURN_CODE(code);

        int srid;
        code = zipCommentInfo->GetProjectionSrid(srid);
        TE_CHECKRETURN_CODE(code);

        TAK::Engine::Feature::AltitudeMode altitudeMode;
        code = zipCommentInfo->GetAltitudeMode(altitudeMode);
        TE_CHECKRETURN_CODE(code);

        double maxDisplayResolution;
        double minDisplayResolution;
        code = zipCommentInfo->GetDisplayResolutions(maxDisplayResolution, minDisplayResolution);
        TE_CHECKRETURN_CODE(code);

        double resolution;
        code = zipCommentInfo->GetResolution(resolution);
        TE_CHECKRETURN_CODE(code);

        sceneInfo.location = GeoPoint2Ptr(new GeoPoint2(location), Memory_deleter_const<GeoPoint2>);
        sceneInfo.srid = srid;
        sceneInfo.altitudeMode = altitudeMode;
        sceneInfo.localFrame = Matrix2Ptr_const(localFrame.release(), Memory_deleter_const<Matrix2>);
        sceneInfo.maxDisplayResolution = maxDisplayResolution;
        sceneInfo.minDisplayResolution = minDisplayResolution;
        sceneInfo.resolution = resolution;
    } TE_END_TRAP(code);
    TE_CHECKRETURN_CODE(code);
    
    return TE_Ok;
}

bool ZipCommentGeoreferencer::isGeoReferenced(const char *uri) NOTHROWS {
    bool exists = false;
    IO_existsV(&exists, uri);
    if (!exists)
        return false;

    TAKErr code(TE_Ok);
    TE_BEGIN_TRAP() {
        Port::String zipComment;
        code = IO_getZipComment(zipComment, uri);
        if (code != TE_Ok || zipComment == "")
            return false;

        ZipCommentInfoPtr zipCommentInfo(nullptr, nullptr);
        code = ZipCommentInfo::Create(zipCommentInfo, zipComment.get());
        if (code != TE_Ok)
            return false;
    } TE_END_TRAP(code);

    return true;
}

TAKErr ZipCommentGeoreferencer::removeGeoReference(const char *uri) NOTHROWS {
    bool exists = false;
    IO_existsV(&exists, uri);
    if (!exists)
        return TE_Unsupported;

    TAKErr code(TE_Ok);
    TE_BEGIN_TRAP() {
        code = IO_setZipComment(uri, "");
        TE_CHECKRETURN_CODE(code);
    } TE_END_TRAP(code);
    
    return TE_Ok;
}
